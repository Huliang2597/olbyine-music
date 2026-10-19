package com.olbyine.music.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.olbyine.music.data.LyricsDocument
import com.olbyine.music.playback.PlaybackState
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
internal fun AmllLyricsView(
    document: LyricsDocument,
    rawTtml: String?,
    playback: PlaybackState,
    fontSize: Float,
    fontWeight: Int,
    fontPath: String?,
    inactiveOpacity: Float,
    offsetMs: Long,
    onLineTap: (Long?) -> Unit,
    onTouching: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    fadeTopDp: Float = 0f,
    fadeBottomDp: Float = 0f,
    lineBlur: Boolean = false,
    showTranslation: Boolean = true,
    showRoman: Boolean = true,
    subScale: Float = .5f,
    doubleTapSeek: Boolean = true,
    alignPosition: Float = .5f,
) {
    val latestLineTap = rememberUpdatedState(onLineTap)
    val latestTouching = rememberUpdatedState(onTouching)
    val host = remember {
        AmllViewHost(
            onLineTap = { latestLineTap.value(it) },
            onTouching = { latestTouching.value(it) },
        )
    }
    val documentJson = remember(document, rawTtml) { amllDocumentJson(document, rawTtml) }
    val foreground = MaterialTheme.colorScheme.onBackground
    val fontScale = LocalDensity.current.fontScale
    val reduceMotion = LocalReduceMotion.current
    val styleJson = remember(fontSize, fontScale, fontWeight, fontPath, inactiveOpacity, foreground, reduceMotion, lineBlur, showTranslation, showRoman, subScale, doubleTapSeek, alignPosition) {
        JSONObject()
            .put("alignPosition", alignPosition.coerceIn(.15f, .85f).toDouble())
            .put("reduceMotion", reduceMotion)
            .put("lineBlur", lineBlur)
            .put("showTranslation", showTranslation)
            .put("showRoman", showRoman)
            .put("subScale", subScale.coerceIn(.3f, 1f).toDouble())
            .put("tapSeek", if (doubleTapSeek) "double" else "single")
            .put("fontSize", (fontSize * fontScale).toDouble())
            .put("fontWeight", fontWeight.coerceIn(100, 900))
            .put("fontVersion", fontPath?.let { File(it).name }.orEmpty())
            .put("inactiveOpacity", inactiveOpacity.coerceIn(0f, 1f).toDouble())
            .put("blendMode", "normal")
            .put("foreground", String.format(
                Locale.US, "rgba(%d,%d,%d,%.3f)",
                (foreground.red * 255).roundToInt(),
                (foreground.green * 255).roundToInt(),
                (foreground.blue * 255).roundToInt(),
                foreground.alpha,
            ))
            .toString()
    }
    SideEffect {
        host.update(documentJson, styleJson, playback, offsetMs, fontPath)
        host.setVisible(active)
        host.setFades(fadeTopDp, fadeBottomDp)
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, host) {
        val observer = LifecycleEventObserver { _, _ ->
            host.setLifecycleResumed(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        lifecycle.addObserver(observer)
        host.setLifecycleResumed(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(host, playback.isPlaying, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (playback.isPlaying) {
                delay(500L)
                host.synchronizeClock()
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    blockNetworkLoads = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                    textZoom = 100
                    cacheMode = WebSettings.LOAD_DEFAULT
                }
                webViewClient = AmllAssetClient(context, host)
                host.attach(this)
                addJavascriptInterface(AmllJavascriptBridge(host), "OlbyineAndroid")
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> host.nativeTouching(true)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> host.nativeTouching(false)
                    }
                    false
                }
                loadUrl(AMLL_PAGE_URL)
            }
        },
        onRelease = { view ->
            host.release(view)
            view.setOnTouchListener(null)
            view.removeJavascriptInterface("OlbyineAndroid")
            view.stopLoading()
            view.webViewClient = WebViewClient()
            view.removeAllViews()
            view.destroy()
        },
        update = { },
    )
}

private class AmllViewHost(
    private val onLineTap: (Long?) -> Unit,
    private val onTouching: (Boolean) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var view: WebView? = null
    private var ready = false
    private var active = true
    private var documentJson = "{}"
    private var styleJson = "{}"
    private var lastDocumentJson: String? = null
    private var lastStyleJson: String? = null
    private var playback = PlaybackState()
    private var offsetMs = 0L
    private var forceSeek = true
    private var nativeHeld = false
    private var scriptHeld = false
    private var reportedHeld = false
    @Volatile private var fontPath: String? = null

    fun attach(webView: WebView) {
        view = webView
        ready = false
        if (!active) webView.onPause()
    }

    fun update(document: String, style: String, state: PlaybackState, offset: Long, font: String?) {
        val now = SystemClock.elapsedRealtime()
        val clockChanged = state != playback || offset != offsetMs
        val discontinuity = state.currentMediaId != playback.currentMediaId ||
            state.pendingSeekMs != playback.pendingSeekMs || offset != offsetMs ||
            abs(state.estimatedPosition(now) - playback.estimatedPosition(now)) > 250L
        forceSeek = forceSeek || discontinuity || documentJson != document
        documentJson = document
        styleJson = style
        playback = state
        offsetMs = offset
        fontPath = font
        flush(clockChanged)
    }

    fun fontFile(directory: File): File? = fontPath?.let { path ->
        runCatching { File(path).canonicalFile.takeIf { it.parentFile == directory && it.isFile } }
            .getOrNull()
    }

    private var lifecycleResumed = true
    private var visible = true
    private var fadeTop = 0f
    private var fadeBottom = 0f
    private var lastMask: String? = null

    fun setLifecycleResumed(value: Boolean) {
        lifecycleResumed = value
        setActive(lifecycleResumed && visible)
    }

    fun setVisible(value: Boolean) {
        if (visible == value) return
        visible = value
        setActive(lifecycleResumed && visible)
    }

    fun setFades(top: Float, bottom: Float) {
        fadeTop = top
        fadeBottom = bottom
        flushMask()
    }

    private fun flushMask() {
        if (!ready) return
        val script = lyricMaskScript(fadeTop, fadeBottom)
        if (script == lastMask) return
        lastMask = script
        view?.evaluateJavascript(script, null)
    }

    private fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (value) {
            view?.onResume()
            forceSeek = true
            flush()
        } else {
            nativeHeld = false
            scriptHeld = false
            publishTouching()
            sendClock()
            view?.onPause()
        }
    }

    fun pageStarted(url: String?) {
        ready = false
        lastDocumentJson = null
        lastStyleJson = null
        lastMask = null
        forceSeek = true
        if (url != AMLL_PAGE_URL) {
            view?.removeJavascriptInterface("OlbyineAndroid")
            view?.stopLoading()
        }
    }

    fun pageReady() = fromPage {
        ready = true
        flush()
    }

    fun seekTo(value: String) = fromPage {
        val milliseconds = value.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
            ?: return@fromPage
        if (milliseconds > Long.MAX_VALUE.toDouble()) return@fromPage
        onLineTap((milliseconds.toLong() - offsetMs).coerceAtLeast(0L))
    }

    fun tap() = fromPage { onLineTap(null) }

    fun scriptTouching(value: Boolean) = fromPage {
        scriptHeld = value
        publishTouching()
    }

    fun nativeTouching(value: Boolean) {
        nativeHeld = value
        publishTouching()
    }

    fun synchronizeClock() {
        if (active) sendClock()
    }

    fun release(webView: WebView) {
        if (view !== webView) return
        ready = false
        view = null
        nativeHeld = false
        scriptHeld = false
        publishTouching()
    }

    private fun fromPage(action: () -> Unit) {
        mainHandler.post {
            val current = view ?: return@post
            if (current.url == AMLL_PAGE_URL) action()
        }
    }

    private fun publishTouching() {
        val held = nativeHeld || scriptHeld
        if (held != reportedHeld) {
            reportedHeld = held
            onTouching(held)
        }
    }

    private fun flush(clockChanged: Boolean = true) {
        if (!ready) return
        flushMask()
        if (lastStyleJson != styleJson) {
            invoke("setStyle", styleJson)
            lastStyleJson = styleJson
        }
        if (lastDocumentJson != documentJson) {
            invoke("setDocument", documentJson)
            lastDocumentJson = documentJson
            forceSeek = true
        }
        if (clockChanged || forceSeek) sendClock()
    }

    private fun sendClock() {
        if (!ready) return
        invoke("setPlayback", JSONObject()
            .put("positionMs", (playback.estimatedPosition() + offsetMs).coerceAtLeast(0L))
            .put("isPlaying", active && playback.isPlaying)
            .put("playbackSpeed", playback.playbackSpeed.toDouble())
            .put("seek", forceSeek)
            .toString())
        forceSeek = false
    }

    private fun invoke(method: String, payload: String) {
        val safeJson = payload.replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
        view?.evaluateJavascript("window.OlbyineLyrics && window.OlbyineLyrics.$method($safeJson);", null)
    }
}

private class AmllJavascriptBridge(private val host: AmllViewHost) {
    @JavascriptInterface fun ready() = host.pageReady()
    @JavascriptInterface fun seekTo(positionMs: String) = host.seekTo(positionMs)
    @JavascriptInterface fun onTap() = host.tap()
    @JavascriptInterface fun touching(value: Boolean) = host.scriptTouching(value)
}

private class AmllAssetClient(context: Context, private val host: AmllViewHost) : WebViewClient() {
    private val assets = context.applicationContext.assets
    private val fontDirectory = File(context.applicationContext.filesDir, "fonts").canonicalFile

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) = host.pageStarted(url)

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
        localAsset(request.url, request.method)

    private fun localAsset(uri: Uri, method: String): WebResourceResponse {
        val path = uri.path.orEmpty()
        if (method != "GET" || uri.scheme != "https" || uri.host != AMLL_ASSET_HOST ||
            uri.port !in listOf(-1, 443) || uri.userInfo != null ||
            !path.startsWith("/assets/amll/") || path.contains('\\') ||
            path.split('/').any { it == ".." || it == "." }
        ) return deniedResponse(403, "Forbidden")
        val assetPath = path.removePrefix("/assets/")
        if (assetPath == "amll/custom-font") {
            val font = host.fontFile(fontDirectory) ?: return deniedResponse(404, "Not Found")
            val type = when (font.extension.lowercase(Locale.ROOT)) {
                "ttf" -> "font/ttf"
                "otf" -> "font/otf"
                "ttc" -> "font/collection"
                "woff" -> "font/woff"
                "woff2" -> "font/woff2"
                else -> return deniedResponse(415, "Unsupported Media Type")
            }
            return try {
                WebResourceResponse(type, null, 200, "OK", mapOf("Cache-Control" to "no-store"), FileInputStream(font))
            } catch (_: Exception) {
                deniedResponse(404, "Not Found")
            }
        }
        val mime = when (assetPath.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "html" -> "text/html"
            "js", "mjs" -> "text/javascript"
            "css" -> "text/css"
            "json", "map" -> "application/json"
            "wasm" -> "application/wasm"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            else -> "application/octet-stream"
        }
        return try {
            val cacheControl = if (AMLL_IMMUTABLE_ASSET.matches(assetPath)) {
                "public, max-age=31536000, immutable"
            } else {
                "no-cache"
            }
            WebResourceResponse(mime, if (mime.startsWith("text/") || mime == "application/json") "UTF-8" else null,
                200, "OK", mapOf(
                    "Cache-Control" to cacheControl,
                    "X-Content-Type-Options" to "nosniff",
                    "Content-Security-Policy" to "default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'self'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'",
                ), assets.open(assetPath))
        } catch (_: Exception) {
            deniedResponse(404, "Not Found")
        }
    }

    private fun deniedResponse(code: Int, reason: String) = WebResourceResponse(
        "text/plain", "UTF-8", code, reason, emptyMap(), ByteArrayInputStream(ByteArray(0)),
    )
}

private fun amllDocumentJson(document: LyricsDocument, rawTtml: String?): String {
    val lines = JSONArray()
    document.lines.forEach { line ->
        val words = JSONArray()
        line.words.forEach { word ->
            words.put(JSONObject().put("word", word.text).put("startTime", word.startMs).put("endTime", word.endMs))
        }
        if (line.words.isEmpty()) {
            words.put(JSONObject().put("word", line.text).put("startTime", line.startMs).put("endTime", line.endMs))
        }
        lines.put(JSONObject()
            .put("text", line.text)
            .put("startTime", line.startMs)
            .put("endTime", line.endMs)
            .put("words", words)
            .put("translatedLyric", line.translation.orEmpty())
            .put("romanLyric", line.romanization.orEmpty())
            .put("isBG", false)
            .put("isDuet", false))
    }
    return JSONObject()
        .put("isSynchronized", document.isSynchronized)
        .put("rawTtml", rawTtml?.takeIf(String::isNotBlank) ?: JSONObject.NULL)
        .put("lines", lines)
        .toString()
}

private fun lyricMaskScript(topDp: Float, bottomDp: Float): String {
    val gradient = "linear-gradient(to bottom,transparent 0,transparent calc(var(--olbyine-fade-top) * .45)," +
        "#000 var(--olbyine-fade-top),#000 calc(100% - var(--olbyine-fade-bottom) - 64px)," +
        "transparent calc(100% - var(--olbyine-fade-bottom)))"
    val css = "@property --olbyine-fade-top{syntax:\"<length>\";inherits:false;initial-value:0px}" +
        "@property --olbyine-fade-bottom{syntax:\"<length>\";inherits:false;initial-value:0px}" +
        "#lyrics{transition:--olbyine-fade-top 280ms cubic-bezier(.23,1,.32,1),--olbyine-fade-bottom 280ms cubic-bezier(.23,1,.32,1);" +
        "-webkit-mask-image:$gradient;mask-image:$gradient}"
    return "(function(){var h=document.getElementById('lyrics');if(!h)return;" +
        "if(!document.getElementById('olbyine-mask')){var s=document.createElement('style');s.id='olbyine-mask';" +
        "s.textContent=${JSONObject.quote(css)};document.head.appendChild(s);}" +
        "h.style.setProperty('--olbyine-fade-top','${String.format(Locale.US, "%.1f", topDp)}px');" +
        "h.style.setProperty('--olbyine-fade-bottom','${String.format(Locale.US, "%.1f", bottomDp)}px');})();"
}

private const val AMLL_ASSET_HOST ="appassets.androidplatform.net"
private const val AMLL_PAGE_URL = "https://$AMLL_ASSET_HOST/assets/amll/index.html"
private val AMLL_IMMUTABLE_ASSET = Regex("""amll/assets/.+-[A-Za-z0-9_-]{8}\.(?:js|css)""")
