package com.olbyine.music.playback

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.provider.Settings
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.media3.common.Player
import com.olbyine.music.data.LyricLine
import com.olbyine.music.data.LyricsDocument
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

internal class FloatingLyrics(private val context: Context, private val player: () -> Player?) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private val prefs: SharedPreferences = context.getSharedPreferences("native_settings", Context.MODE_PRIVATE)
    private var view: FloatingLyricsView? = null
    private var params: WindowManager.LayoutParams? = null

    var document: LyricsDocument = LyricsDocument.Empty
        set(value) {
            field = value
            view?.invalidate()
        }
    private var enabled = false
    private var mode = MODE_KARAOKE
    private var offsetMs = 0L
    private var showTranslation = true
    private var locked = false
    internal var palette = 0
        private set

    fun apply(enabled: Boolean, mode: String, locked: Boolean, offsetMs: Long, showTranslation: Boolean, palette: Int = 0) {
        this.enabled = enabled
        this.palette = palette.coerceIn(0, PALETTES.lastIndex)
        this.mode = mode
        this.offsetMs = offsetMs
        this.showTranslation = showTranslation
        val lockChanged = this.locked != locked
        this.locked = locked
        refresh()
        if (lockChanged) applyLock()
        view?.invalidate()
    }

    fun refresh() {
        val want = enabled && !appInFront && player()?.currentMediaItem != null && canDraw(context)
        if (want && view == null) show() else if (!want && view != null) hide()
        view?.wake()
    }

    fun release() = hide()

    @SuppressLint("RtlHardcoded")
    private fun show() {
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        val width = runCatching { prefs.getInt(KEY_WIDTH, (metrics.widthPixels - 32 * density).toInt()) }.getOrDefault((metrics.widthPixels - 32 * density).toInt())
            .coerceIn((200 * density).toInt(), metrics.widthPixels)
        val layout = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = runCatching { prefs.getInt(KEY_X, (16 * density).toInt()) }.getOrDefault((16 * density).toInt())
            y = runCatching { prefs.getInt(KEY_Y, (metrics.heightPixels * .62f).toInt()) }.getOrDefault((metrics.heightPixels * .62f).toInt())
            title = "Olbyine 桌面歌词"
        }
        val created = FloatingLyricsView(context, this)
        created.scale = runCatching { prefs.getFloat(KEY_SCALE, 1f) }.getOrDefault(1f)
        if (runCatching { windows.addView(created, layout) }.isFailure) return
        view = created
        params = layout
        applyLock()
        created.wake()
    }

    private fun hide() {
        val current = view ?: return
        current.stop()
        runCatching { windows.removeView(current) }
        view = null
        params = null
    }

    private fun applyLock() {
        val layout = params ?: return
        val current = view ?: return
        layout.flags = if (locked) layout.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        else layout.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        current.locked = locked
        runCatching { windows.updateViewLayout(current, layout) }
    }

    internal fun moveBy(dx: Int, dy: Int) {
        val layout = params ?: return
        val metrics = context.resources.displayMetrics
        layout.x = (layout.x + dx).coerceIn(-layout.width / 2, metrics.widthPixels - layout.width / 2)
        layout.y = (layout.y + dy).coerceIn(0, metrics.heightPixels - (view?.height ?: 0))
        view?.let { runCatching { windows.updateViewLayout(it, layout) } }
    }

    internal fun resizeBy(dx: Int) {
        val layout = params ?: return
        val metrics = context.resources.displayMetrics
        layout.width = (layout.width + dx).coerceIn((180 * metrics.density).toInt(), metrics.widthPixels)
        view?.let { runCatching { windows.updateViewLayout(it, layout) } }
    }

    internal fun save(scale: Float) {
        val layout = params ?: return
        prefs.edit().putInt(KEY_X, layout.x).putInt(KEY_Y, layout.y).putInt(KEY_WIDTH, layout.width).putFloat(KEY_SCALE, scale).apply()
    }

    internal fun position(): Long = (player()?.currentPosition ?: 0L) + offsetMs
    internal fun playing(): Boolean = player()?.isPlaying == true
    internal fun karaoke(): Boolean = mode != MODE_SCROLL
    internal fun translations(): Boolean = showTranslation
    internal fun title(): String = player()?.currentMediaItem?.mediaMetadata?.let { meta ->
        meta.extras?.getString(LyricNotificationProvider.EXTRA_TITLE) ?: meta.title?.toString()
    }.orEmpty()

    internal fun togglePlay() {
        val p = player() ?: return
        if (p.isPlaying) p.pause() else { p.prepare(); p.play() }
        view?.wake()
    }

    internal fun next() { player()?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() } }
    internal fun previous() { player()?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() else it.seekTo(0) } }

    internal fun switchMode() {
        mode = if (mode == MODE_SCROLL) MODE_KARAOKE else MODE_SCROLL
        prefs.edit().putString(KEY_MODE_LOCAL, mode).apply()
        onModeChanged?.invoke(mode)
        view?.invalidate()
    }

    internal fun lock() {
        locked = true
        applyLock()
        onLockChanged?.invoke(true)
    }

    internal fun close() {
        enabled = false
        hide()
        onClosed?.invoke()
    }

    var onModeChanged: ((String) -> Unit)? = null
    var onLockChanged: ((Boolean) -> Unit)? = null
    var onClosed: (() -> Unit)? = null

    companion object {
        val PALETTES = listOf(
            intArrayOf(0xFFB5EEFF.toInt(), 0xFF3D9BFF.toInt()),
            intArrayOf(0xFFFFC2DA.toInt(), 0xFFFF4F8B.toInt()),
            intArrayOf(0xFFFFEA9C.toInt(), 0xFFFFA81F.toInt()),
            intArrayOf(0xFFB4FFE0.toInt(), 0xFF1FCF97.toInt()),
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
        )
        val PALETTE_NAMES = listOf("晴空蓝", "樱花粉", "琥珀金", "薄荷绿", "纯白")
        const val MODE_KARAOKE = "karaoke"
        const val MODE_SCROLL = "scroll"
        private const val KEY_X = "float_lyrics_x"
        private const val KEY_Y = "float_lyrics_y"
        private const val KEY_WIDTH = "float_lyrics_width"
        private const val KEY_SCALE = "float_lyrics_scale"
        private const val KEY_MODE_LOCAL = "float_lyrics_mode_local"

        @Volatile var appInFront = false

        @Volatile internal var active: FloatingLyrics? = null

        fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)
    }
}

@SuppressLint("ViewConstructor")
internal class FloatingLyricsView(context: Context, private val owner: FloatingLyrics) : View(context) {
    private val density = resources.displayMetrics.density
    private val scaledDensity = density * resources.configuration.fontScale
    var scale = 1f
        set(value) {
            field = value.coerceIn(.6f, 2.2f)
            requestLayout()
        }
    var locked = false
        set(value) {
            field = value
            invalidate()
        }

    private val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(196, 22, 22, 26) }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density * .8f
        color = Color.argb(46, 255, 255, 255)
    }
    private val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()
    private val path = Path()

    private var controls = 0f
    private var controlsUntil = 0L
    private var scrollLine = -1
    private var scrollProgress = 1f
    private var pan = 0f

    private var running = false
    private var lastFrame = 0L
    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val dt = if (lastFrame == 0L) 1f / 60f else ((frameTimeNanos - lastFrame) / 1e9f).coerceIn(0f, .05f)
            lastFrame = frameTimeNanos
            step(dt)
            invalidate()
            val busy = owner.playing() || controls > .001f || controlsUntil > System.currentTimeMillis() || scrollProgress < 1f
            if (busy) Choreographer.getInstance().postFrameCallback(this) else {
                running = false
                lastFrame = 0L
            }
        }
    }

    fun wake() {
        if (running || !isAttachedToWindow) {
            invalidate()
            return
        }
        running = true
        Choreographer.getInstance().postFrameCallback(frame)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frame)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        wake()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun step(dt: Float) {
        val wantControls = if (!locked && System.currentTimeMillis() < controlsUntil) 1f else 0f
        controls += (wantControls - controls) * (1f - exp(-dt * 14f))
        if (abs(wantControls - controls) < .002f) controls = wantControls
        if (scrollProgress < 1f) scrollProgress = min(1f, scrollProgress + dt / .38f)
        if (wantControls != controls) requestLayout()
    }

    private val mainSize get() = 20f * scaledDensity * scale
    private val subSize get() = 13f * scaledDensity * scale
    private val pad get() = 14f * density
    private val controlsHeight get() = 40f * density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val lines = if (owner.karaoke()) mainSize * 1.35f + subSize * 1.5f else mainSize * 1.35f + subSize * 1.5f * 2f
        val height = pad * 1.6f + lines + controlsHeight * controls
        setMeasuredDimension(width, height.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = 18f * density
        if (!locked) {
            rect.set(0f, 0f, w, h)
            canvas.drawRoundRect(rect, radius, radius, panel)
            rect.inset(rim.strokeWidth / 2f, rim.strokeWidth / 2f)
            canvas.drawRoundRect(rect, radius, radius, rim)
        }
        val document = owner.document
        val position = owner.position()
        val index = if (document.isSynchronized) document.lineIndexAt(position) else -1
        val line = document.lines.getOrNull(index)?.takeIf { it.text.isNotBlank() }
        mainPaint.textSize = mainSize
        subPaint.textSize = subSize
        mainPaint.clearShadowLayer()
        subPaint.clearShadowLayer()
        val textLeft = pad
        val textWidth = w - pad * 2f
        val top = pad * .8f
        canvas.save()
        canvas.clipRect(pad * .5f, 0f, w - pad * .5f, h - controlsHeight * controls)
        if (owner.karaoke()) {
            val text = line?.text ?: if (document.lines.isEmpty()) owner.title() else "♪"
            val baseline = top + mainSize
            drawKaraoke(canvas, line, text, position, textLeft, textWidth, baseline)
            val next = if (index >= 0) document.lines.drop(index + 1).firstOrNull { it.text.isNotBlank() } else null
            val secondary = line?.translation?.takeIf { owner.translations() && it.isNotBlank() } ?: next?.text
            if (secondary != null) {
                drawOutlined(canvas, ellipsize(secondary, subPaint, textWidth), textLeft, baseline + subSize * 1.6f, subPaint, Color.argb(235, 255, 255, 255))
            }
        } else {
            if (index != scrollLine) {
                if (scrollLine >= 0 && index == scrollLine + 1) scrollProgress = 0f
                scrollLine = index
                wake()
            }
            val step = mainSize * 1.35f
            val shift = (1f - easeOut(scrollProgress)) * step
            val center = top + subSize * 1.5f + mainSize
            for (k in -1..1) {
                val l = document.lines.getOrNull(index + k)?.takeIf { it.text.isNotBlank() } ?: continue
                val y = center + k * step + shift
                if (k == 0) drawKaraoke(canvas, l, l.text, position, textLeft, textWidth, y)
                else {
                    subPaint.textSize = subSize * 1.15f
                    drawOutlined(canvas, ellipsize(l.text, subPaint, textWidth), textLeft, y, subPaint, Color.argb(205, 255, 255, 255))
                    subPaint.textSize = subSize
                }
            }
        }
        canvas.restore()
        if (controls > .01f) drawControls(canvas, w, h)
    }

    private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)

    private fun drawKaraoke(canvas: Canvas, line: LyricLine?, text: String, position: Long, left: Float, width: Float, baseline: Float) {
        val full = mainPaint.measureText(text)
        val sung = sungWidth(line, text, position, full)
        val target = if (full > width) (sung - width * .6f).coerceIn(0f, full - width) else 0f
        pan += (target - pan) * .18f
        if (abs(target - pan) < .5f) pan = target
        val x = left - pan
        val colors = FloatingLyrics.PALETTES[owner.palette]
        val white = owner.palette == FloatingLyrics.PALETTES.lastIndex
        mainPaint.shader = null
        drawOutlined(canvas, text, x, baseline, mainPaint, if (white) Color.argb(255, 150, 150, 156) else Color.WHITE)
        if (sung > 0f) {
            val edge = 8f * density
            val fill = LinearGradient(0f, baseline - mainSize * .9f, 0f, baseline + mainSize * .15f, colors[0], colors[1], Shader.TileMode.CLAMP)
            val mask = LinearGradient(x + sung - edge, 0f, x + sung + edge, 0f, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.save()
            canvas.clipRect(x - outline.strokeWidth, baseline - mainSize * 1.3f, x + sung + edge, baseline + mainSize * .5f)
            mainPaint.color = Color.WHITE
            mainPaint.shader = android.graphics.ComposeShader(fill, mask, android.graphics.PorterDuff.Mode.DST_IN)
            mainPaint.setShadowLayer(7f * density, 0f, 0f, (colors[1] and 0x00FFFFFF) or (0xB0 shl 24))
            canvas.drawText(text, x, baseline, mainPaint)
            mainPaint.clearShadowLayer()
            mainPaint.shader = null
            canvas.restore()
        }
    }

    private fun drawOutlined(canvas: Canvas, text: String, x: Float, baseline: Float, paint: Paint, color: Int) {
        outline.textSize = paint.textSize
        outline.strokeWidth = paint.textSize * .13f
        outline.color = Color.argb(185, 0, 0, 0)
        outline.setShadowLayer(paint.textSize * .18f, 0f, density, Color.argb(120, 0, 0, 0))
        canvas.drawText(text, x, baseline, outline)
        paint.shader = null
        paint.color = color
        canvas.drawText(text, x, baseline, paint)
    }

    private fun sungWidth(line: LyricLine?, text: String, position: Long, full: Float): Float {
        line ?: return 0f
        if (position < line.startMs) return 0f
        if (line.words.isEmpty()) {
            val end = if (line.endMs > line.startMs) line.endMs else line.startMs + 3_000L
            return full * ((position - line.startMs).toFloat() / (end - line.startMs)).coerceIn(0f, 1f)
        }
        var cursor = 0
        var width = 0f
        for (word in line.words) {
            val token = word.text
            val start = text.indexOf(token.trim(), cursor).takeIf { it >= 0 && token.isNotBlank() } ?: cursor
            val end = (start + token.trim().length).coerceAtMost(text.length)
            val before = mainPaint.measureText(text, 0, start)
            val after = mainPaint.measureText(text, 0, end)
            cursor = end
            when {
                position >= word.endMs -> width = after
                position >= word.startMs -> {
                    val f = (position - word.startMs).toFloat() / (word.endMs - word.startMs).coerceAtLeast(1)
                    return before + (after - before) * f.coerceIn(0f, 1f)
                }
                else -> return max(width, before)
            }
        }
        return width
    }

    private fun ellipsize(text: String, paint: Paint, width: Float): String {
        if (paint.measureText(text) <= width) return text
        val count = paint.breakText(text, true, width - paint.measureText("…"), null)
        return text.take(count) + "…"
    }

    private val buttons = listOf(CTRL_LOCK, CTRL_PREV, CTRL_PLAY, CTRL_NEXT, CTRL_MODE, CTRL_CLOSE)

    private fun buttonCenters(w: Float, h: Float): List<Pair<Int, Float>> {
        val y = h - controlsHeight * controls / 2f
        val spacing = w / buttons.size
        return buttons.mapIndexed { i, id -> id to spacing * (i + .5f) }.also { lastControlsY = y }
    }

    private var lastControlsY = 0f

    private fun drawControls(canvas: Canvas, w: Float, h: Float) {
        val alpha = (controls * 255).toInt().coerceIn(0, 255)
        iconPaint.alpha = alpha
        strokePaint.alpha = alpha
        strokePaint.strokeWidth = 2f * density
        val s = 9f * density
        for ((id, cx) in buttonCenters(w, h)) {
            val cy = lastControlsY
            path.reset()
            when (id) {
                CTRL_PLAY -> if (owner.playing()) {
                    canvas.drawRoundRect(cx - s * .8f, cy - s, cx - s * .2f, cy + s, s * .2f, s * .2f, iconPaint)
                    canvas.drawRoundRect(cx + s * .2f, cy - s, cx + s * .8f, cy + s, s * .2f, s * .2f, iconPaint)
                } else {
                    path.moveTo(cx - s * .6f, cy - s); path.lineTo(cx + s, cy); path.lineTo(cx - s * .6f, cy + s); path.close()
                    canvas.drawPath(path, iconPaint)
                }
                CTRL_NEXT, CTRL_PREV -> {
                    val d = if (id == CTRL_NEXT) 1f else -1f
                    path.moveTo(cx - d * s * .8f, cy - s * .75f); path.lineTo(cx + d * s * .3f, cy); path.lineTo(cx - d * s * .8f, cy + s * .75f); path.close()
                    path.moveTo(cx - d * s * .05f, cy - s * .75f); path.lineTo(cx + d * s * 1.05f, cy); path.lineTo(cx - d * s * .05f, cy + s * .75f); path.close()
                    canvas.drawPath(path, iconPaint)
                }
                CTRL_LOCK -> {
                    canvas.drawRoundRect(cx - s * .75f, cy - s * .1f, cx + s * .75f, cy + s * .9f, s * .2f, s * .2f, iconPaint)
                    path.moveTo(cx - s * .45f, cy - s * .1f)
                    path.lineTo(cx - s * .45f, cy - s * .45f)
                    path.cubicTo(cx - s * .45f, cy - s * 1.05f, cx + s * .45f, cy - s * 1.05f, cx + s * .45f, cy - s * .45f)
                    path.lineTo(cx + s * .45f, cy - s * .1f)
                    canvas.drawPath(path, strokePaint)
                }
                CTRL_MODE -> {
                    if (owner.karaoke()) {
                        canvas.drawLine(cx - s, cy - s * .55f, cx + s, cy - s * .55f, strokePaint)
                        canvas.drawLine(cx - s, cy, cx + s * .6f, cy, strokePaint)
                        canvas.drawLine(cx - s, cy + s * .55f, cx + s * .2f, cy + s * .55f, strokePaint)
                    } else {
                        canvas.drawLine(cx - s, cy, cx + s, cy, strokePaint)
                        canvas.drawCircle(cx + s * .15f, cy, s * .32f, iconPaint)
                    }
                }
                CTRL_CLOSE -> {
                    canvas.drawLine(cx - s * .7f, cy - s * .7f, cx + s * .7f, cy + s * .7f, strokePaint)
                    canvas.drawLine(cx + s * .7f, cy - s * .7f, cx - s * .7f, cy + s * .7f, strokePaint)
                }
            }
        }
        val g = 6f * density
        strokePaint.strokeWidth = 1.6f * density
        canvas.drawLine(w - g * 2.2f, h - g, w - g, h - g * 2.2f, strokePaint)
        canvas.drawLine(w - g * 1.5f, h - g, w - g, h - g * 1.5f, strokePaint)
    }

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var resizing = false
    private var scaled = false
    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scale *= detector.scaleFactor
            scaled = true
            return true
        }
    })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (locked) return false
        scaler.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX; downY = event.rawY
                lastX = downX; lastY = downY
                dragging = false
                scaled = false
                resizing = controls > .5f && event.x > width - 28f * density && event.y > height - 28f * density
                controlsUntil = System.currentTimeMillis() + 3_500
                wake()
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1 || scaled) return true
                val dx = event.rawX - lastX
                val dy = event.rawY - lastY
                if (!dragging && (abs(event.rawX - downX) > slop || abs(event.rawY - downY) > slop)) dragging = true
                if (dragging) {
                    if (resizing) owner.resizeBy(dx.toInt()) else owner.moveBy(dx.toInt(), dy.toInt())
                    lastX = event.rawX
                    lastY = event.rawY
                    controlsUntil = System.currentTimeMillis() + 3_500
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging || scaled) {
                    owner.save(scale)
                } else if (controls > .5f && event.y > height - controlsHeight * controls) {
                    val slot = (event.x / width * buttons.size).toInt().coerceIn(0, buttons.lastIndex)
                    when (buttons[slot]) {
                        CTRL_PLAY -> owner.togglePlay()
                        CTRL_NEXT -> owner.next()
                        CTRL_PREV -> owner.previous()
                        CTRL_MODE -> { owner.switchMode(); requestLayout() }
                        CTRL_LOCK -> { controlsUntil = 0L; owner.lock() }
                        CTRL_CLOSE -> owner.close()
                    }
                    if (buttons[slot] != CTRL_LOCK) controlsUntil = System.currentTimeMillis() + 3_500
                }
                performClick()
                wake()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private companion object {
        const val CTRL_LOCK = 0
        const val CTRL_PREV = 1
        const val CTRL_PLAY = 2
        const val CTRL_NEXT = 3
        const val CTRL_MODE = 4
        const val CTRL_CLOSE = 5
    }

}
