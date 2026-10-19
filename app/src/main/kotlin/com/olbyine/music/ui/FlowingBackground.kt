package com.olbyine.music.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import android.view.Choreographer
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

@Composable
internal fun FlowingBackground(
    coverPath: String?,
    seed: String,
    fallbackColors: List<Int>,
    modifier: Modifier = Modifier,
    beatReactive: Boolean = false,
    style: String = "ios",
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduceMotion = LocalReduceMotion.current
    val viewRef = remember { arrayOfNulls<FlowingBackgroundView>(1) }
    val key = AlbumTexture.keyFor(coverPath, fallbackColors, seed)
    var texture by remember { mutableStateOf(AlbumTexture.cached(key)) }
    LaunchedEffect(key) {
        if (texture?.key == key) return@LaunchedEffect
        texture = AlbumTexture.cached(key) ?: withContext(Dispatchers.Default) {
            runCatching { AlbumTexture.create(coverPath, fallbackColors, seed) }
                .onFailure { Log.w("OlbyineBackground", "Unable to prepare artwork texture", it) }
                .getOrNull()
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            viewRef[0]?.setWindowActive(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            viewRef[0]?.setWindowActive(false)
            viewRef[0] = null
        }
    }
    AndroidView(
        factory = { context ->
            FlowingBackgroundView(context).also { view ->
                viewRef[0] = view
                view.setWindowActive(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            }
        },
        update = { view ->
            texture?.let(view::setTexture)
            view.beatReactive = beatReactive
            view.iosStyle = style == "ios"
            view.still = reduceMotion
        },
        modifier = modifier,
    )
}

internal class AlbumTexture(val key: String, val bitmap: Bitmap, val warp: FloatArray, val raw: Bitmap) {
    companion object {
        private val cache = android.util.LruCache<String, AlbumTexture>(6)

        fun keyFor(coverPath: String?, fallbackColors: List<Int>, seed: String): String =
            if (coverPath != null) "$seed|$coverPath" else "$seed|colors:${fallbackColors.hashCode()}"

        fun cached(key: String): AlbumTexture? = cache.get(key)

        fun create(coverPath: String?, fallbackColors: List<Int>, seed: String): AlbumTexture {
            val key = keyFor(coverPath, fallbackColors, seed)
            cache.get(key)?.let { return it }
            val raw = coverPath?.let { decodeRaw(it) } ?: Bitmap.createScaledBitmap(colorsBitmap(fallbackColors), RAW_EDGE, RAW_EDGE, true)
            val small = coverPath?.let(::decodeSmall) ?: colorsBitmap(fallbackColors)
            val processed = process(small)
            val random = Random(seed.hashCode())
            fun range(min: Float, max: Float) = min + random.nextFloat() * (max - min)
            val warp = floatArrayOf(
                range(.09f, .17f), range(2.4f, 4.2f), range(0f, 6.28f), range(.035f, .08f),
                range(4.8f, 7.6f), range(0f, 6.28f), range(0f, 6.28f), range(.04f, .075f),
            )
            return AlbumTexture(key, processed, warp, raw).also { cache.put(key, it) }
        }

        private fun decodeRaw(path: String): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= RAW_EDGE && bounds.outHeight / (sample * 2) >= RAW_EDGE) sample *= 2
            val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: return null
            return Bitmap.createScaledBitmap(decoded, RAW_EDGE, RAW_EDGE, true).also { if (it !== decoded) decoded.recycle() }
        }

        private fun decodeSmall(path: String): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 64 && bounds.outHeight / (sample * 2) >= 64) sample *= 2
            val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: return null
            return Bitmap.createScaledBitmap(decoded, SOURCE_EDGE, SOURCE_EDGE, true).also {
                if (it !== decoded) decoded.recycle()
            }
        }

        private fun colorsBitmap(colors: List<Int>): Bitmap {
            val grid = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
            for (index in 0 until 6) {
                grid.setPixel(index % 3, index / 3, colors.getOrNull(index) ?: colors.lastOrNull() ?: 0xFF5E315F.toInt())
            }
            return Bitmap.createScaledBitmap(grid, SOURCE_EDGE, SOURCE_EDGE, true)
        }

        private fun process(source: Bitmap): Bitmap {
            val size = SOURCE_EDGE
            val pixels = IntArray(size * size)
            source.getPixels(pixels, 0, size, 0, 0, size, size)
            val r = FloatArray(pixels.size)
            val g = FloatArray(pixels.size)
            val b = FloatArray(pixels.size)
            for (i in pixels.indices) {
                var red = ((pixels[i] shr 16) and 0xFF).toFloat()
                var green = ((pixels[i] shr 8) and 0xFF).toFloat()
                var blue = (pixels[i] and 0xFF).toFloat()
                red = (red - 128f) * .4f + 128f
                green = (green - 128f) * .4f + 128f
                blue = (blue - 128f) * .4f + 128f
                val gray = red * .3f + green * .59f + blue * .11f
                red = gray * -2f + red * 3f
                green = gray * -2f + green * 3f
                blue = gray * -2f + blue * 3f
                red = (red - 128f) * 1.7f + 128f
                green = (green - 128f) * 1.7f + 128f
                blue = (blue - 128f) * 1.7f + 128f
                r[i] = (red * .75f).coerceIn(0f, 255f)
                g[i] = (green * .75f).coerceIn(0f, 255f)
                b[i] = (blue * .75f).coerceIn(0f, 255f)
            }
            repeat(4) { boxBlur(r, size, 2); boxBlur(g, size, 2); boxBlur(b, size, 2) }
            for (i in pixels.indices) {
                pixels[i] = (0xFF shl 24) or (r[i].toInt() shl 16) or (g[i].toInt() shl 8) or b[i].toInt()
            }
            val small = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
            val large = Bitmap.createScaledBitmap(small, TEXTURE_EDGE, TEXTURE_EDGE, true)
            if (large !== small) small.recycle()
            if (source !== large) source.recycle()
            return large
        }

        private fun boxBlur(channel: FloatArray, size: Int, radius: Int) {
            val temp = FloatArray(channel.size)
            val span = radius * 2 + 1f
            for (y in 0 until size) for (x in 0 until size) {
                var sum = 0f
                for (k in -radius..radius) sum += channel[y * size + (x + k).coerceIn(0, size - 1)]
                temp[y * size + x] = sum / span
            }
            for (y in 0 until size) for (x in 0 until size) {
                var sum = 0f
                for (k in -radius..radius) sum += temp[(y + k).coerceIn(0, size - 1) * size + x]
                channel[y * size + x] = sum / span
            }
        }

        private const val SOURCE_EDGE = 32
        const val TEXTURE_EDGE = 128
        const val RAW_EDGE = 192
    }
}

private class FlowingBackgroundView(context: Context) : View(context), Choreographer.FrameCallback {
    private val choreographer = Choreographer.getInstance()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val runtimeShader = if (Build.VERSION.SDK_INT >= 33) {
        runCatching { RuntimeShader(FLOW_AGSL) }.onFailure { Log.w("OlbyineBackground", "Flow shader unavailable", it) }.getOrNull()
    } else null
    private var shaderFailed = false
    private var current: AlbumTexture? = null
    private var previous: AlbumTexture? = null
    private var currentShader: BitmapShader? = null
    private var previousShader: BitmapShader? = null
    private var transitionStart = 0L
    private var fadeInStart = 0L
    private var flowSeconds = Random.nextFloat() * 40f
    private var lastFrameNanos = 0L
    private var framePosted = false
    private var windowActive = true
    private val matrix = Matrix()
    var beatReactive = false
    var still = false
        set(value) {
            if (field != value) {
                field = value
                pulse = 0f
                energy = 0f
                lastFrameNanos = 0L
                invalidate()
                scheduleFrame()
            }
        }
    var iosStyle = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    private var pulse = 0f
    private var energy = 0f
    private val iosNode = if (Build.VERSION.SDK_INT >= 31) android.graphics.RenderNode("olbyine-ios-bg").apply {
        setUseCompositingLayer(true, Paint(Paint.FILTER_BITMAP_FLAG))
    } else null
    private val iosMaterial = if (Build.VERSION.SDK_INT >= 33) {
        runCatching { RuntimeShader(IOS_MATERIAL_AGSL) }.onFailure { Log.w("OlbyineBackground", "iOS material unavailable", it) }.getOrNull()
    } else null
    private val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val layerRect = android.graphics.RectF()

    private val lowResNode = if (Build.VERSION.SDK_INT >= 29) android.graphics.RenderNode("olbyine-flow").apply {
        setUseCompositingLayer(true, Paint(Paint.FILTER_BITMAP_FLAG))
    } else null

    init {
        if (runtimeShader == null && Build.VERSION.SDK_INT >= 31) {
            setRenderEffect(RenderEffect.createBlurEffect(90f, 90f, Shader.TileMode.MIRROR))
        }
        if (Build.VERSION.SDK_INT >= 35) requestedFrameRate = TARGET_FPS
    }

    fun setTexture(texture: AlbumTexture) {
        if (current?.key == texture.key) return
        fadeInStart = if (current == null && isAttachedToWindow) System.nanoTime() else 0L
        val midFadeEarly = previous != null &&
            (System.nanoTime() - transitionStart) / TRANSITION_NANOS < .5f
        if (!midFadeEarly) {
            previous = current
            previousShader = currentShader
            transitionStart = System.nanoTime()
        }
        current = texture
        currentShader = BitmapShader(texture.bitmap, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR).apply {
            if (Build.VERSION.SDK_INT >= 33) filterMode = BitmapShader.FILTER_MODE_LINEAR
        }
        invalidate()
        scheduleFrame()
    }

    fun setWindowActive(active: Boolean) {
        if (windowActive == active) return
        windowActive = active
        lastFrameNanos = 0L
        if (active) scheduleFrame() else if (framePosted) {
            choreographer.removeFrameCallback(this)
            framePosted = false
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrameNanos = 0L
        scheduleFrame()
    }

    override fun onDetachedFromWindow() {
        if (framePosted) choreographer.removeFrameCallback(this)
        framePosted = false
        super.onDetachedFromWindow()
    }

    override fun doFrame(frameTimeNanos: Long) {
        framePosted = false
        if (!isAttachedToWindow || !windowActive) return
        if (lastFrameNanos != 0L && frameTimeNanos - lastFrameNanos < MIN_FRAME_NANOS) {
            scheduleFrame()
            return
        }
        if (lastFrameNanos != 0L && !still) {
            val delta = (frameTimeNanos - lastFrameNanos).coerceIn(0L, 100_000_000L)
            val seconds = delta / 1_000_000_000f
            val fresh = beatReactive && System.nanoTime() - com.olbyine.music.playback.AudioEnergy.stamp < 300_000_000L
            val targetEnergy = if (fresh) com.olbyine.music.playback.AudioEnergy.level else 0f
            val targetPulse = if (fresh) com.olbyine.music.playback.AudioEnergy.beat else 0f
            energy += (targetEnergy - energy) * (1f - kotlin.math.exp(-seconds / .35f))
            pulse = if (targetPulse > pulse) pulse + (targetPulse - pulse) * (1f - kotlin.math.exp(-seconds / .045f))
                else pulse * kotlin.math.exp(-seconds / .24f)
            flowSeconds += seconds * (1f + .9f * energy)
        }
        lastFrameNanos = frameTimeNanos
        invalidate()
        if (!still || fading()) scheduleFrame()
    }

    private fun fading(): Boolean {
        val now = System.nanoTime()
        return previous != null || (fadeInStart != 0L && now - fadeInStart < TRANSITION_NANOS)
    }

    private fun scheduleFrame() {
        if (framePosted || !isAttachedToWindow || !windowActive || current == null) return
        framePosted = true
        choreographer.postFrameCallback(this)
    }

    private fun transition(): Float {
        if (previous == null) return 1f
        val t = ((System.nanoTime() - transitionStart) / TRANSITION_NANOS).coerceIn(0f, 1f)
        if (t >= 1f) {
            previous = null
            previousShader = null
        }
        return (-(cos(PI * t) - 1) / 2).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val texture = current ?: return
        if (width <= 0 || height <= 0) return
        val mix = transition()
        val fadeIn = if (fadeInStart == 0L) 1f
            else ((System.nanoTime() - fadeInStart) / TRANSITION_NANOS).coerceIn(0f, 1f)
        if (fadeIn >= 1f) fadeInStart = 0L
        paint.alpha = (fadeIn * 255).toInt()
        if (iosStyle && canvas.isHardwareAccelerated && iosNode != null && drawIos(canvas, texture, mix, fadeIn)) return
        val shader = runtimeShader
        if (Build.VERSION.SDK_INT >= 33 && shader != null && !shaderFailed) {
            val rendered = runCatching {
                val newShader = currentShader!!
                val oldShader = previousShader ?: newShader
                val oldWarp = previous?.warp ?: texture.warp
                val node = lowResNode?.takeIf { canvas.isHardwareAccelerated }
                val target: Canvas
                val drawWidth: Float
                val drawHeight: Float
                if (node != null) {
                    drawWidth = ceil(width / RENDER_SCALE)
                    drawHeight = ceil(height / RENDER_SCALE)
                    node.setPosition(0, 0, drawWidth.toInt(), drawHeight.toInt())
                    node.pivotX = 0f
                    node.pivotY = 0f
                    node.scaleX = RENDER_SCALE
                    node.scaleY = RENDER_SCALE
                    target = node.beginRecording(drawWidth.toInt(), drawHeight.toInt())
                } else {
                    drawWidth = width.toFloat()
                    drawHeight = height.toFloat()
                    target = canvas
                }
                shader.setFloatUniform("resolution", drawWidth, drawHeight)
                shader.setFloatUniform("texSize", AlbumTexture.TEXTURE_EDGE.toFloat(), AlbumTexture.TEXTURE_EDGE.toFloat())
                shader.setFloatUniform("time", flowSeconds)
                shader.setFloatUniform("mixT", mix)
                shader.setFloatUniform("pulse", pulse)
                shader.setFloatUniform("warpA", oldWarp[0], oldWarp[1], oldWarp[2], oldWarp[3])
                shader.setFloatUniform("warpA2", oldWarp[4], oldWarp[5], oldWarp[6], oldWarp[7])
                shader.setFloatUniform("warpB", texture.warp[0], texture.warp[1], texture.warp[2], texture.warp[3])
                shader.setFloatUniform("warpB2", texture.warp[4], texture.warp[5], texture.warp[6], texture.warp[7])
                shader.setInputShader("imageA", oldShader)
                shader.setInputShader("imageB", newShader)
                paint.shader = shader
                target.drawRect(0f, 0f, drawWidth, drawHeight, paint)
                paint.shader = null
                if (node != null) {
                    node.endRecording()
                    canvas.drawRenderNode(node)
                }
            }.isSuccess
            if (rendered) return
            shaderFailed = true
            paint.shader = null
        }
        drawSprites(canvas, previous, 1f - mix)
        drawSprites(canvas, texture, if (previous == null) 1f else mix)
        canvas.drawColor(0x33000000)
    }

    private fun drawIos(canvas: Canvas, texture: AlbumTexture, mix: Float, fadeIn: Float): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        val node = iosNode ?: return false
        val w = ceil(width / IOS_SCALE).toInt().coerceAtLeast(8)
        val h = ceil(height / IOS_SCALE).toInt().coerceAtLeast(8)
        return runCatching {
            node.setPosition(0, 0, w, h)
            node.pivotX = 0f
            node.pivotY = 0f
            node.scaleX = width.toFloat() / w
            node.scaleY = height.toFloat() / h
            val blur = RenderEffect.createBlurEffect(w * .085f, w * .085f, Shader.TileMode.MIRROR)
            val material = iosMaterial
            val effect = if (Build.VERSION.SDK_INT >= 33 && material != null) {
                material.setFloatUniform("resolution", w.toFloat(), h.toFloat())
                material.setFloatUniform("time", flowSeconds)
                material.setFloatUniform("scrim", .18f - .08f * pulse)
                RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(material, "content"), blur)
            } else blur
            node.setRenderEffect(effect)
            val target = node.beginRecording(w, h)
            previous?.let { if (mix < 1f) drawIosLayers(target, it, w, h, (1f - mix) * fadeIn) }
            drawIosLayers(target, texture, w, h, (if (previous == null) 1f else mix) * fadeIn)
            node.endRecording()
            canvas.drawRenderNode(node)
            if (material == null) canvas.drawColor(0x2E000000)
        }.isSuccess
    }

    private fun drawIosLayers(target: Canvas, texture: AlbumTexture, w: Int, h: Int, alpha: Float) {
        if (alpha <= 0f) return
        val bitmap = texture.raw
        layerPaint.alpha = (alpha * 255).toInt()
        val unit = max(w, h) / 2f
        val cx = w / 2f
        val cy = h / 2f
        val t = flowSeconds
        val twoPi = (2 * PI).toFloat()
        val zoom = 1f + .06f * pulse
        val a0 = t * twoPi / 120f + texture.warp[2]
        val a1 = t * twoPi / 70f + texture.warp[5]
        val a2 = t * twoPi / 90f + texture.warp[6]
        layerRect.set(cx - unit * 1.2f, cy - unit * 1.2f, cx + unit * 1.2f, cy + unit * 1.2f)
        target.drawBitmap(bitmap, null, layerRect, layerPaint)
        fun layer(offsetX: Float, offsetY: Float, scale: Float, angle: Float, parentAngle: Float?) {
            matrix.reset()
            matrix.postTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
            matrix.postScale(unit * 2f * scale * zoom / bitmap.width, unit * 2f * scale * zoom / bitmap.height)
            matrix.postRotate(Math.toDegrees(angle.toDouble()).toFloat())
            matrix.postTranslate(offsetX * unit, -offsetY * unit)
            if (parentAngle != null) matrix.postRotate(Math.toDegrees(parentAngle.toDouble()).toFloat())
            matrix.postTranslate(cx, cy)
            target.drawBitmap(bitmap, matrix, layerPaint)
        }
        layer(0f, 0f, 1.4f, a0, null)
        layer(-.25f, .15f, .7f, a1, null)
        layer(.7f, .7f, .7f, a2, a0)
        layerPaint.alpha = 255
    }

    private fun drawSprites(canvas: Canvas, texture: AlbumTexture?, alpha: Float) {
        texture ?: return
        if (alpha <= 0f) return
        val bitmap = texture.bitmap
        val maxSize = max(width, height).toFloat()
        val t = flowSeconds
        val fade = if (fadeInStart == 0L) 1f
            else ((System.nanoTime() - fadeInStart) / TRANSITION_NANOS).coerceIn(0f, 1f)
        paint.alpha = (alpha * fade * 255).toInt()
        fun sprite(cx: Float, cy: Float, size: Float, rotation: Float) {
            matrix.reset()
            matrix.postTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
            matrix.postScale(size / bitmap.width, size / bitmap.height)
            matrix.postRotate(Math.toDegrees(rotation.toDouble()).toFloat())
            matrix.postTranslate(cx, cy)
            canvas.drawBitmap(bitmap, matrix, paint)
        }
        sprite(width / 2f, height / 2f, maxSize * sqrt(2f), t * .06f + texture.warp[2])
        sprite(width / 2.5f, height / 2.5f, maxSize * .8f, -t * .12f + texture.warp[5])
        sprite(width / 2f + width / 4f * cos(t * .75f), height / 2f + width / 4f * cos(t * .75f), maxSize * .5f, t * .06f)
        sprite(width / 2f + width / 16f * sin(t * .45f), height / 2f + width / 16f, maxSize * .25f, -t * .08f)
        paint.alpha = 255
    }

    private companion object {
        const val TRANSITION_NANOS = 900_000_000f
        const val TARGET_FPS = 60f
        const val MIN_FRAME_NANOS = 15_500_000L
        const val RENDER_SCALE = 3f
        const val IOS_SCALE = 5f
    }
}

private const val IOS_MATERIAL_AGSL = """
uniform shader content;
uniform float2 resolution;
uniform float time;
uniform float scrim;

half3 saturate3(half3 c, half s) {
    half3 r = half3(0.2126 + 0.7873 * s, 0.2126 - 0.2126 * s, 0.2126 - 0.2126 * s);
    half3 g = half3(0.7152 - 0.7152 * s, 0.7152 + 0.2848 * s, 0.7152 - 0.7152 * s);
    half3 b = half3(0.0722 - 0.0722 * s, 0.0722 - 0.0722 * s, 0.0722 + 0.9278 * s);
    return r * c.r + g * c.g + b * c.b;
}

half4 main(float2 p) {
    float2 uv = p / resolution;
    float phase = acos(sin(time * 3.14159265 / 5.0)) / 3.14159265;
    float breathe = phase * phase * (3.0 - 2.0 * phase);
    float2 centered = uv - float2(0.5);
    float pinch = 0.06 * breathe * (1.0 - dot(centered, centered) * 2.0);
    float2 warped = float2(0.5) + centered * (1.0 - pinch);
    warped += 0.012 * float2(sin(uv.y * 5.0 + time * 0.31), cos(uv.x * 4.0 - time * 0.27));
    half3 c = content.eval(warped * resolution).rgb;
    c = saturate3(c, 1.4);
    c = clamp(c, half3(-0.752941), half3(1.25098));
    c = saturate3(c, 0.7);
    c = mix(c, half3(0.0), half(scrim));
    float noise = fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715)))) - 0.5;
    c += half3(noise / 255.0);
    return half4(clamp(c, half3(0.07), half3(0.97)), 1.0);
}
"""

private const val FLOW_AGSL = """
uniform shader imageA;
uniform shader imageB;
uniform float2 resolution;
uniform float2 texSize;
uniform float time;
uniform float mixT;
uniform float pulse;
uniform float4 warpA;
uniform float4 warpA2;
uniform float4 warpB;
uniform float4 warpB2;

float2 rot(float2 v, float a) {
    float s = sin(a);
    float c = cos(a);
    return float2(c * v.x - s * v.y, s * v.x + c * v.y);
}

float2 flowUV(float2 uv, float4 w, float4 w2, float t) {
    float2 p = uv;
    p += w.x * float2(sin(p.y * w.y + w.z + t * w2.w), sin(p.x * w.y - w.z + t * w2.w * 0.8));
    p += w.w * float2(sin(p.y * w2.x + w2.y - t * w2.w * 0.6), sin(p.x * w2.x + w2.y * 1.3 + t * w2.w * 0.5));
    return rot(p - float2(0.2), t * 0.2 + w2.z) + float2(0.5);
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / resolution;
    float aspect = resolution.x / resolution.y;
    float2 meshUV = uv;
    if (aspect < 1.0) {
        meshUV.x = 0.5 + (uv.x - 0.5) * aspect;
    } else {
        meshUV.y = 0.5 + (uv.y - 0.5) / aspect;
    }
    meshUV = float2(0.5) + (meshUV - float2(0.5)) * (1.0 - 0.07 * pulse);
    half3 a = imageA.eval(flowUV(meshUV, warpA, warpA2, time) * texSize).rgb;
    half3 b = imageB.eval(flowUV(meshUV, warpB, warpB2, time) * texSize).rgb;
    half3 color = mix(a, b, half(mixT));
    float dist = distance(uv, float2(0.5));
    color *= half((0.54 + 0.36 * smoothstep(0.8, 0.3, dist)) * (1.0 + 0.16 * pulse));
    float noise = fract(52.9829189 * fract(dot(fragCoord, float2(0.06711056, 0.00583715))));
    color += half3((noise - 0.5) / 255.0);
    return half4(color, 1.0);
}
"""
