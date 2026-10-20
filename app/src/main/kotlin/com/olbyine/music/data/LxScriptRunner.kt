package com.olbyine.music.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * 在隐藏的 WebView 里运行洛雪音乐的自定义源脚本（window.lx 契约）。
 * 只用于「取播放地址」，音源列表与歌曲元数据仍由内置的洛雪源提供。
 */
class LxScriptRunner(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private var webView: WebView? = null
    private var loading: CompletableDeferred<Result<List<LxSourceInfo>>>? = null
    @Volatile private var runtimeError: String = ""
    private var pageReady: CompletableDeferred<Unit>? = null
    private var currentScriptId: String? = null
    private var sources: List<LxSourceInfo> = emptyList()
    private val calls = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val httpCalls = ConcurrentHashMap<String, okhttp3.Call>()
    private var sequence = 0

    fun cachedSources(scriptId: String): List<LxSourceInfo>? =
        if (currentScriptId == scriptId && sources.isNotEmpty()) sources else null

    suspend fun prepare(script: LxScript): List<LxSourceInfo> = withContext(Dispatchers.Main) {
        if (currentScriptId == script.id && sources.isNotEmpty()) return@withContext sources
        val ready = CompletableDeferred<Result<List<LxSourceInfo>>>()
        loading = ready
        runtimeError = ""
        sources = emptyList()
        currentScriptId = script.id
        val view = webView ?: createWebView().also { webView = it }
        withTimeoutOrNull(5_000L) { pageReady?.await() }
        view.evaluateJavascript(asset("lx/runner.js"), null)
        view.evaluateJavascript("__lxBoot(" + JSONObject.quote(script.code) + ")", null)
        val result = withTimeoutOrNull(INIT_TIMEOUT_MS) { ready.await() }
        val list = result?.getOrNull()
        if (list.isNullOrEmpty()) {
            currentScriptId = null
            val reason = result?.exceptionOrNull()?.message
                ?: runtimeError.takeIf { it.isNotBlank() }
                ?: "脚本没有发送 inited 事件（可能不是洛雪格式的自定义源）"
            error(reason)
        }
        sources = list
        list
    }

    suspend fun musicUrl(script: LxScript, source: String, type: String, music: JSONObject): String {
        prepare(script)
        val callId = "c${++sequence}"
        val deferred = CompletableDeferred<String>()
        calls[callId] = deferred
        val payload = JSONObject.quote(music.toString())
        withContext(Dispatchers.Main) {
            webView?.evaluateJavascript(
                "__lxCallMusicUrl(" + JSONObject.quote(callId) + "," + JSONObject.quote(source) + "," +
                    JSONObject.quote(type) + "," + payload + ")",
                null,
            )
        }
        val url = withTimeoutOrNull(25_000L) { deferred.await() }
        calls.remove(callId)
        return url?.takeIf { it.startsWith("http") } ?: error("自定义源没有返回有效的播放地址")
    }

    fun release() {
        main.post {
            runCatching {
                webView?.destroy()
            }
            webView = null
            currentScriptId = null
            sources = emptyList()
        }
        httpCalls.values.forEach { runCatching { it.cancel() } }
        httpCalls.clear()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.blockNetworkLoads = false
        addJavascriptInterface(Bridge(), "OlbyineLx")
        webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                pageReady?.complete(Unit)
            }
        }
        pageReady = CompletableDeferred()
        loadDataWithBaseURL("https://lx.local/", "<html><body></body></html>", "text/html", "utf-8", null)
    }

    private fun asset(path: String): String =
        runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrDefault("")

    private inner class Bridge {
        @JavascriptInterface
        fun ready() = Unit

        @JavascriptInterface
        fun inited(sourcesJson: String, ok: Boolean, message: String) {
            val parsed = parseSources(sourcesJson)
            val target = loading
            if (!ok) {
                target?.complete(Result.failure(IllegalStateException(message.ifBlank { "脚本报告初始化失败" })))
                return
            }
            if (parsed.isEmpty()) {
                target?.complete(Result.failure(IllegalStateException("脚本没有声明任何音源")))
                return
            }
            sources = parsed
            target?.complete(Result.success(parsed))
        }

        @JavascriptInterface
        fun bootError(message: String) {
            runtimeError = message
            loading?.complete(Result.failure(IllegalStateException(message)))
        }

        @JavascriptInterface
        fun runtimeError(message: String) {
            if (runtimeError.isBlank()) runtimeError = message
        }

        @JavascriptInterface
        fun send(name: String, data: String) = Unit

        @JavascriptInterface
        fun request(id: Int, url: String, optionsJson: String) {
            val options = runCatching { JSONObject(optionsJson) }.getOrNull() ?: JSONObject()
            val method = options.optString("method", "get").uppercase()
            val builder = Request.Builder().url(url)
            options.optJSONObject("headers")?.let { headers ->
                headers.keys().forEach { key -> runCatching { builder.header(key, headers.optString(key)) } }
            }
            val body = when {
                options.has("form") && options.optJSONObject("form") != null -> {
                    val form = StringBuilder()
                    val obj = options.optJSONObject("form")!!
                    obj.keys().forEach { key ->
                        if (form.isNotEmpty()) form.append('&')
                        form.append(java.net.URLEncoder.encode(key, "UTF-8")).append('=')
                            .append(java.net.URLEncoder.encode(obj.optString(key), "UTF-8"))
                    }
                    form.toString().toRequestBody(FORM)
                }
                options.has("body") -> options.optString("body").toRequestBody(JSON_MEDIA)
                else -> null
            }
            builder.method(method, body ?: if (method == "GET") null else "".toRequestBody(JSON_MEDIA))
            val call = client.newCall(builder.build())
            httpCalls[id.toString()] = call
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, error: java.io.IOException) {
                    httpCalls.remove(id.toString())
                    evaluate("__lxOnHttpError($id, " + JSONObject.quote(error.message ?: "网络请求失败") + ")")
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    httpCalls.remove(id.toString())
                    response.use {
                        val text = runCatching { it.body?.string().orEmpty() }.getOrDefault("")
                        val headers = JSONObject()
                        it.headers.names().forEach { name ->
                            runCatching { headers.put(name, it.headers.get(name).orEmpty()) }
                        }
                        val script = "__lxOnHttp($id, ${it.code}, " + JSONObject.quote(text) + ", " +
                            JSONObject.quote(headers.toString()) + ")"
                        evaluate(script)
                    }
                }
            })
        }

        @JavascriptInterface
        fun cancel(id: Int) {
            httpCalls.remove(id.toString())?.let { runCatching { it.cancel() } }
        }

        @JavascriptInterface
        fun resolve(callId: String, url: String) {
            calls[callId]?.complete(url)
        }

        @JavascriptInterface
        fun reject(callId: String, message: String) {
            calls.remove(callId)?.completeExceptionally(IllegalStateException(message))
        }
    }

    private fun evaluate(script: String) {
        main.post { runCatching { webView?.evaluateJavascript(script, null) } }
    }

    private fun parseSources(json: String): List<LxSourceInfo> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val list = ArrayList<LxSourceInfo>()
        root.keys().forEach { key ->
            val row = root.optJSONObject(key) ?: return@forEach
            val qualitys = row.optJSONArray("qualitys")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
            }.orEmpty()
            list += LxSourceInfo(key = key, name = row.optString("name").ifBlank { key }, qualitys = qualitys)
        }
        return list
    }

    companion object {
        private const val INIT_TIMEOUT_MS = 20_000L
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val FORM = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()
    }
}
