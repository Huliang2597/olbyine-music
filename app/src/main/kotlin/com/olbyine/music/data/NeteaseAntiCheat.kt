package com.olbyine.music.data

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal class NeteaseAntiCheat(private val context: Context) {
    private var view: WebView? = null
    private var pending: CompletableDeferred<String?>? = null
    private var cached: String? = null
    private var cachedAt = 0L

    suspend fun token(): String? = withContext(Dispatchers.Main.immediate) {
        val now = System.currentTimeMillis()
        cached?.takeIf { it.isNotBlank() && now - cachedAt < 60_000L }?.let { return@withContext it }
        val web = ensure() ?: return@withContext null
        val deferred = CompletableDeferred<String?>()
        pending = deferred
        val loaded = runCatching { web.loadDataWithBaseURL(BASE_URL, PAGE, "text/html", "utf-8", null) }.isSuccess
        if (!loaded) {
            pending = null
            return@withContext null
        }
        val value = withTimeoutOrNull(15_000L) { deferred.await() }
        pending = null
        if (!value.isNullOrBlank()) {
            cached = value
            cachedAt = System.currentTimeMillis()
        }
        value
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensure(): WebView? {
        view?.let { return it }
        return runCatching {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = false
                settings.blockNetworkImage = true
                addJavascriptInterface(Bridge(), "OlbyineAntiCheat")
                setOnLongClickListener { true }
            }
        }.getOrNull()?.also { view = it }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun post(value: String) {
            val target = pending ?: return
            if (!target.isCompleted) target.complete(value)
        }
    }

    private companion object {
        const val BASE_URL = "https://music.163.com/"
        const val PRODUCT_NUMBER = "YD00000558929251"
        const val BUSINESS_ID = "bd5d2f973ef74cd2a61325a412ae54d9"
        const val TOOL_URL = "https://acstatic-dun.126.net/tool.min.js"

        val PAGE = """
            <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width"></head><body>
            <script src="$TOOL_URL"></script>
            <script>
            (function(){
              function done(value){ try { OlbyineAntiCheat.post(value || ''); } catch (e) {} }
              try {
                if (typeof initWatchman !== 'function') { done(''); return; }
                initWatchman({
                  auto: true,
                  productNumber: '$PRODUCT_NUMBER',
                  onload: function(instance){
                    try {
                      instance.getToken('$BUSINESS_ID', function(tk){ done(tk); });
                    } catch (e) { done(''); }
                  },
                  onerror: function(){ done(''); }
                });
              } catch (e) { done(''); }
            })();
            </script>
            </body></html>
        """.trimIndent()
    }
}
