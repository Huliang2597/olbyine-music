package com.olbyine.music.ui

import android.graphics.ColorMatrixColorFilter
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

internal object GlassMotion {
    var frame by mutableIntStateOf(0)
        private set

    fun bump() {
        frame++
    }

    private val drivers = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun readDrivers() {
        drivers.forEach { it() }
    }

    @Composable
    fun Drive(read: () -> Unit) {
        val current = androidx.compose.runtime.rememberUpdatedState(read)
        androidx.compose.runtime.DisposableEffect(Unit) {
            val driver: () -> Unit = { current.value() }
            drivers.add(driver)
            onDispose { drivers.remove(driver) }
        }
    }
}

@Stable
internal class BackdropSource {
    internal var layer: GraphicsLayer? = null
    internal var coordinates: LayoutCoordinates? = null
    internal var placement by mutableIntStateOf(0)
}

@Composable
internal fun rememberBackdropSource(): BackdropSource {
    val layer = rememberGraphicsLayer()
    return remember(layer) { BackdropSource().also { it.layer = layer } }
}

internal fun Modifier.hiddenBackdropSource(source: BackdropSource): Modifier = this
    .onGloballyPositioned {
        source.coordinates = it
        source.placement++
    }
    .drawWithContent {
        source.layer?.record { this@drawWithContent.drawContent() }
    }

internal fun Modifier.backdropSource(source: BackdropSource): Modifier = this
    .onGloballyPositioned {
        source.coordinates = it
        source.placement++
    }
    .drawWithContent {
        val layer = source.layer
        if (layer == null) {
            drawContent()
        } else {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }
    }

@Stable
internal data class GlassStyle(
    val cornerRadius: Dp,
    val blur: Dp,
    val tint: Color,
    val saturation: Float = 1.5f,
    val lens: Boolean = false,
    val refractionHeight: Dp = 24.dp,
    val refractionAmount: Dp = 24.dp,
    val dispersion: Boolean = false,
    val highlight: Color = Color.Transparent,
    val highlightWidth: Dp = 0.7.dp,
    val shadow: Dp = 0.dp,
    val fallback: Color = tint,
    val fadeEdge: Dp = 0.dp,
    val progressiveEdge: Dp = 0.dp,
    val hairline: Color = Color.Transparent,
    val tintFalloff: Boolean = false,
    val depthEffect: Boolean = false,
    val contrast: Float = 1f,
)

internal class GlassDynamics(
    val visibleHeight: (() -> Float)? = null,
    val materialAlpha: (() -> Float)? = null,
    val lensProgress: (() -> Float)? = null,
    val cornerRadius: (() -> Float)? = null,
    val materialize: (() -> Float)? = null,
)

internal fun Modifier.glass(
    source: BackdropSource?,
    style: GlassStyle,
    extraSources: List<BackdropSource> = emptyList(),
    dynamics: GlassDynamics? = null,
    sharpSources: List<BackdropSource> = emptyList(),
): Modifier = composed {
    val style = style.tuned(LocalGlassTuning.current, androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() < .5f)
    val effectLayer = rememberGraphicsLayer()
    val sharpLayer = rememberGraphicsLayer()
    val edgeLayers = if (style.progressiveEdge > 0.dp) {
        listOf(rememberGraphicsLayer(), rememberGraphicsLayer())
    } else {
        emptyList()
    }
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var placement by remember { mutableIntStateOf(0) }
    val shaders = remember(style.lens, style.dispersion, style.highlight.alpha > 0f) {
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching { GlassShaders(style.lens, style.dispersion, style.highlight.alpha > 0f) }
                .onFailure { Log.w("OlbyineGlass", "Glass shaders unavailable", it) }
                .getOrNull()
        } else null
    }
    val dynamicRadius = dynamics?.cornerRadius
    val shapeModifier = if (dynamicRadius != null) {
        Modifier.graphicsLayer {
            shape = RoundedCornerShape(dynamicRadius().coerceAtLeast(0f))
            clip = true
            if (style.shadow > 0.dp) {
                shadowElevation = style.shadow.toPx() * (dynamics.materialize?.invoke()?.coerceIn(0f, 1f) ?: 1f)
                ambientShadowColor = Color.Black.copy(alpha = .35f)
                spotShadowColor = Color.Black.copy(alpha = .35f)
            }
        }
    } else {
        val shape = RoundedCornerShape(style.cornerRadius)
        Modifier
            .then(if (style.shadow > 0.dp) Modifier.shadow(style.shadow, shape, clip = false,
                ambientColor = Color.Black.copy(alpha = .35f), spotColor = Color.Black.copy(alpha = .35f)) else Modifier)
            .clip(shape)
    }
    this
        .then(shapeModifier)
        .onGloballyPositioned {
            coordinates[0] = it
            placement++
        }
        .drawWithContent {
            placement
            GlassMotion.frame
            GlassMotion.readDrivers()
            source?.placement
            extraSources.forEach { it.placement }
            sharpSources.forEach { it.placement }
            val alpha = dynamics?.materialAlpha?.invoke()?.coerceIn(0f, 1f) ?: 1f
            val visible = (dynamics?.visibleHeight?.invoke() ?: size.height).coerceIn(0f, size.height)
            val materialize = dynamics?.materialize?.invoke()?.coerceIn(0f, 1f) ?: 1f
            val progress = (dynamics?.lensProgress?.invoke()?.coerceIn(0f, 1.5f) ?: 1f) * materialize
            val radius = (dynamicRadius?.invoke() ?: style.cornerRadius.toPx()).coerceIn(0f, size.minDimension / 2f)
            val fadePx = style.fadeEdge.toPx()
            val edgePx = style.progressiveEdge.toPx()
            val layered = alpha < 1f || fadePx > 0f || visible < size.height || edgePx > 0f
            if (alpha > 0.001f && visible > 0.5f && materialize > 0.001f) {
                if (layered) {
                    drawContext.canvas.saveLayer(
                        androidx.compose.ui.geometry.Rect(0f, 0f, size.width, visible),
                        androidx.compose.ui.graphics.Paint().apply { this.alpha = alpha },
                    )
                }
                val own = coordinates[0]
                if (edgePx > 0f && edgeLayers.size == 2) {
                    drawProgressive(
                        levels = listOf(edgeLayers[1], edgeLayers[0], effectLayer),
                        source = source,
                        extraSources = extraSources,
                        own = own,
                        style = style,
                        shaders = shaders,
                        visible = visible,
                        edgePx = edgePx,
                    )
                } else {
                    val full = androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)
                    val sampled = drawSources(effectLayer, source, extraSources, own, style, progress, shaders,
                        region = full, blurScale = materialize, radius = radius)
                    if (sampled && style.tintFalloff) {
                        val tint = style.tint.copy(alpha = style.tint.alpha * materialize)
                        drawRect(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                0f to tint,
                                .55f to tint.copy(alpha = tint.alpha * .7f),
                                1f to tint.copy(alpha = tint.alpha * .35f),
                                startY = 0f,
                                endY = visible,
                            ),
                        )
                    } else if (sampled) {
                        drawRect(style.tint.copy(alpha = style.tint.alpha * materialize))
                    } else {
                        drawRect(style.fallback.copy(alpha = style.fallback.alpha * materialize))
                    }
                    if (sharpSources.isNotEmpty()) {
                        drawSources(sharpLayer, null, sharpSources, own, style.copy(blur = 0.dp, saturation = 1f), progress, shaders,
                            region = full, blurScale = 1f, radius = radius)
                    }
                    if (style.hairline.alpha > 0f) {
                        val line = .6.dp.toPx().coerceAtLeast(1f)
                        drawRect(style.hairline, topLeft = Offset(0f, visible - line), size = androidx.compose.ui.geometry.Size(size.width, line))
                    }
                    if (fadePx > 0f) {
                        drawRect(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                0f to Color.Black,
                                .2f to Color.Black.copy(alpha = .93f),
                                .4f to Color.Black.copy(alpha = .74f),
                                .6f to Color.Black.copy(alpha = .46f),
                                .8f to Color.Black.copy(alpha = .17f),
                                1f to Color.Transparent,
                                startY = visible - fadePx,
                                endY = visible,
                            ),
                            topLeft = Offset(0f, visible - fadePx),
                            size = androidx.compose.ui.geometry.Size(size.width, fadePx),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    val highlight = if (Build.VERSION.SDK_INT >= 33) shaders?.highlight else null
                    if (Build.VERSION.SDK_INT >= 33 && highlight != null && style.highlight.alpha > 0f && progress > 0.01f) {
                        val c = style.highlight
                        val a = (c.alpha * progress).coerceIn(0f, 1f)
                        val ready = runCatching {
                            highlight.setFloatUniform("size", size.width, size.height)
                            highlight.setFloatUniform("radius", radius)
                            highlight.setFloatUniform("lightDir", GlassLight.x, GlassLight.y)
                            highlight.setFloatUniform("falloff", 1f)
                            highlight.setFloatUniform("color", c.red * a, c.green * a, c.blue * a, a)
                        }.isSuccess
                        if (ready) {
                            drawRoundRect(
                                brush = ShaderBrush(highlight),
                                cornerRadius = CornerRadius(radius),
                                style = Stroke(style.highlightWidth.toPx() * 2f),
                                blendMode = BlendMode.Plus,
                            )
                        }
                    }
                }
                if (layered) drawContext.canvas.restore()
            }
            drawContent()
        }
}

internal enum class BlurEdge { TOP, BOTTOM }

internal fun Modifier.gradualBlur(
    source: BackdropSource,
    edge: BlurEdge = BlurEdge.BOTTOM,
    strength: Dp = 24.dp,
    layers: Int = 6,
    tint: Color = Color.Transparent,
    reveal: (() -> Float)? = null,
    solid: Dp = 0.dp,
): Modifier = composed {
    val levels = List(layers) { rememberGraphicsLayer() }
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var placement by remember { mutableIntStateOf(0) }
    val style = remember(strength) { GlassStyle(cornerRadius = 0.dp, blur = strength, tint = Color.Transparent, saturation = 1.08f) }
    this
        .onGloballyPositioned {
            coordinates[0] = it
            placement++
        }
        .drawWithContent {
            drawContent()
            placement
            GlassMotion.frame
            GlassMotion.readDrivers()
            source.placement
            val shown = (reveal?.invoke() ?: 1f).coerceIn(0f, 1f)
            if (shown <= .01f || size.height < 1f) return@drawWithContent
            val full = androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)
            val h = size.height
            val ramp = (h - solid.toPx()).coerceIn(1f, h)
            fun yAt(u: Float) = if (edge == BlurEdge.BOTTOM) u * ramp else h - u * ramp
            if (Build.VERSION.SDK_INT >= 31) {
                for (i in 0 until layers) {
                    val t = (i + 1f) / layers
                    val level = (Math.pow(2.0, 4.0 * t).toFloat() - 1f) / 15f * shown
                    if (style.blur.toPx() * level < .5f) continue
                    drawContext.canvas.saveLayer(full, androidx.compose.ui.graphics.Paint())
                    drawSources(levels[i], source, emptyList(), coordinates[0], style, 0f, null, region = full, blurScale = level, radius = 0f)
                    drawRect(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black,
                            startY = yAt(i.toFloat() / layers),
                            endY = yAt(t),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                    drawContext.canvas.restore()
                }
            }
            if (tint.alpha > 0f) {
                val stops = Array(9) { k ->
                    val u = k / 8f
                    val alpha = u * u * u * (u * (u * 6f - 15f) + 10f)
                    u to tint.copy(alpha = tint.alpha * alpha * shown)
                }
                drawRect(
                    androidx.compose.ui.graphics.Brush.verticalGradient(*stops, startY = yAt(0f), endY = yAt(1f)),
                )
            }
        }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawProgressive(
    levels: List<GraphicsLayer>,
    source: BackdropSource?,
    extraSources: List<BackdropSource>,
    own: LayoutCoordinates?,
    style: GlassStyle,
    shaders: GlassShaders?,
    visible: Float,
    edgePx: Float,
) {
    val bottom = visible
    val top = (visible - edgePx).coerceAtLeast(0f)
    val band = bottom - top
    val yHalf = bottom - band * .5f
    val yQuarter = bottom - band * .25f
    val specs = listOf(
        floatArrayOf(.25f, top, bottom, yQuarter, bottom),
        floatArrayOf(.5f, top, yQuarter, yHalf, yQuarter),
        floatArrayOf(1f, 0f, yHalf, top, yHalf),
    )
    var sampledAny = false
    specs.forEachIndexed { index, spec ->
        val region = androidx.compose.ui.geometry.Rect(0f, spec[1], size.width, spec[2])
        if (region.height < 0.5f) return@forEachIndexed
        drawContext.canvas.saveLayer(region, androidx.compose.ui.graphics.Paint())
        val sampled = drawSources(levels[index], source, extraSources, own, style, 0f, shaders,
            region = region, blurScale = spec[0], radius = 0f)
        sampledAny = sampledAny || sampled
        if (spec[4] > spec[3] + 0.5f) {
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Color.Black,
                    1f to Color.Transparent,
                    startY = spec[3],
                    endY = spec[4],
                ),
                topLeft = Offset(0f, spec[3]),
                size = androidx.compose.ui.geometry.Size(size.width, (region.bottom - spec[3]).coerceAtLeast(0f)),
                blendMode = BlendMode.DstIn,
            )
        }
        drawContext.canvas.restore()
    }
    val veil = if (sampledAny) style.tint else style.fallback
    if (visible > 1f) {
        val start = (top / visible).coerceIn(0f, .98f)
        val mid = ((top + band * .5f) / visible).coerceIn(start + .005f, .995f)
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to veil,
                start to veil,
                mid to veil.copy(alpha = veil.alpha * .42f),
                1f to veil.copy(alpha = 0f),
                startY = 0f,
                endY = visible,
            ),
            size = androidx.compose.ui.geometry.Size(size.width, visible),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSources(
    effectLayer: GraphicsLayer,
    source: BackdropSource?,
    extraSources: List<BackdropSource>,
    own: LayoutCoordinates?,
    style: GlassStyle,
    progress: Float,
    shaders: GlassShaders?,
    region: androidx.compose.ui.geometry.Rect,
    blurScale: Float,
    radius: Float,
): Boolean {
    if (own == null || !own.isAttached) return false
    if (Build.VERSION.SDK_INT < 31) return false
    val layers = (listOfNotNull(source) + extraSources).mapNotNull { candidate ->
        val layer = candidate.layer
        val coordinates = candidate.coordinates
        if (layer != null && coordinates != null && coordinates.isAttached) {
            val origin = coordinates.localPositionOf(own, Offset.Zero)
            val scaleX = (coordinates.localPositionOf(own, Offset(1f, 0f)).x - origin.x).takeIf { it > 0.01f } ?: 1f
            val scaleY = (coordinates.localPositionOf(own, Offset(0f, 1f)).y - origin.y).takeIf { it > 0.01f } ?: 1f
            SampledLayer(layer, origin, scaleX, scaleY)
        } else null
    }
    if (layers.isEmpty()) return false
    val blurPx = style.blur.toPx() * blurScale
    val padding = ceil(maxOf(blurPx * 2f, 2f)).toInt()
    val left = region.left.toInt()
    val top = region.top.toInt()
    val width = ceil(region.width).toInt().coerceAtLeast(1)
    val height = ceil(region.height).toInt().coerceAtLeast(1)
    val lensActive = Build.VERSION.SDK_INT >= 33 && style.lens && shaders?.lens != null && progress > 0.01f
    val down = when {
        lensActive -> 1f
        blurPx >= 24f -> .25f
        blurPx >= 10f -> .5f
        else -> 1f
    }
    if (down < 1f) {
        val scaledWidth = ceil((width + padding * 2) * down).toInt().coerceAtLeast(1)
        val scaledHeight = ceil((height + padding * 2) * down).toInt().coerceAtLeast(1)
        effectLayer.renderEffect = cachedEffect(effectLayer, style, blurPx * down)
        effectLayer.topLeft = IntOffset.Zero
        effectLayer.record(size = IntSize(scaledWidth, scaledHeight)) {
            scale(down, down, pivot = Offset.Zero) {
                layers.forEach { sampled ->
                    translate((padding - left).toFloat(), (padding - top).toFloat()) {
                        scale(1f / sampled.scaleX, 1f / sampled.scaleY, pivot = Offset.Zero) {
                            translate(-sampled.origin.x, -sampled.origin.y) { drawLayer(sampled.layer) }
                        }
                    }
                }
            }
        }
        translate((left - padding).toFloat(), (top - padding).toFloat()) {
            scale(1f / down, 1f / down, pivot = Offset.Zero) { drawLayer(effectLayer) }
        }
        return true
    }
    effectLayer.renderEffect = if (lensActive) {
        glassEffect(style, blurPx, left - padding, top - padding, shaders, progress, radius)
    } else {
        cachedEffect(effectLayer, style, blurPx)
    }
    effectLayer.topLeft = IntOffset(left - padding, top - padding)
    effectLayer.record(size = IntSize(width + padding * 2, height + padding * 2)) {
        layers.forEach { sampled ->
            translate((padding - left).toFloat(), (padding - top).toFloat()) {
                scale(1f / sampled.scaleX, 1f / sampled.scaleY, pivot = Offset.Zero) {
                    translate(-sampled.origin.x, -sampled.origin.y) { drawLayer(sampled.layer) }
                }
            }
        }
    }
    drawLayer(effectLayer)
    return true
}

private val effectCache = java.util.WeakHashMap<GraphicsLayer, CachedEffect>()

private class CachedEffect(val blur: Float, val saturation: Float, val effect: androidx.compose.ui.graphics.RenderEffect)

@androidx.annotation.RequiresApi(31)
private fun cachedEffect(layer: GraphicsLayer, style: GlassStyle, blurPx: Float): androidx.compose.ui.graphics.RenderEffect {
    val rounded = kotlin.math.round(blurPx * 4f) / 4f
    effectCache[layer]?.let { if (it.blur == rounded && it.saturation == style.saturation) return it.effect }
    var effect: android.graphics.RenderEffect = android.graphics.RenderEffect.createColorFilterEffect(
        ColorMatrixColorFilter(saturationMatrix(style.saturation, style.contrast)),
    )
    if (rounded >= 1f) {
        effect = android.graphics.RenderEffect.createBlurEffect(rounded, rounded, effect, Shader.TileMode.CLAMP)
    }
    return effect.asComposeRenderEffect().also { effectCache[layer] = CachedEffect(rounded, style.saturation, it) }
}

private class SampledLayer(val layer: GraphicsLayer, val origin: Offset, val scaleX: Float, val scaleY: Float)

@androidx.annotation.RequiresApi(31)
private fun androidx.compose.ui.graphics.drawscope.DrawScope.glassEffect(
    style: GlassStyle,
    blurPx: Float,
    offsetX: Int,
    offsetY: Int,
    shaders: GlassShaders?,
    progress: Float,
    radius: Float,
): androidx.compose.ui.graphics.RenderEffect {
    var effect: android.graphics.RenderEffect = android.graphics.RenderEffect.createColorFilterEffect(
        ColorMatrixColorFilter(saturationMatrix(style.saturation, style.contrast)),
    )
    if (blurPx >= 1f) {
        effect = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, effect, Shader.TileMode.CLAMP)
    }
    val lens = if (Build.VERSION.SDK_INT >= 33) shaders?.lens else null
    if (style.lens && lens != null && Build.VERSION.SDK_INT >= 33 && progress > 0.01f) {
        val lensEffect = runCatching {
            lens.setFloatUniform("size", size.width, size.height)
            lens.setFloatUniform("offset", offsetX.toFloat(), offsetY.toFloat())
            lens.setFloatUniform("radius", radius)
            lens.setFloatUniform(
                "refractionHeight",
                (style.refractionHeight.toPx() * progress).coerceIn(1f, size.minDimension / 2f),
            )
            lens.setFloatUniform("refractionAmount", -style.refractionAmount.toPx() * progress)
            lens.setFloatUniform("depthEffect", if (style.depthEffect) 1f else 0f)
            if (style.dispersion) lens.setFloatUniform("chromaticAberration", 1f)
            android.graphics.RenderEffect.createRuntimeShaderEffect(lens, "content")
        }.getOrNull()
        if (lensEffect != null) {
            effect = android.graphics.RenderEffect.createChainEffect(lensEffect, effect)
        }
    }
    return effect.asComposeRenderEffect()
}

private fun saturationMatrix(saturation: Float, contrast: Float = 1f): FloatArray {
    val inv = 1f - saturation
    val r = 0.213f * inv * contrast
    val g = 0.715f * inv * contrast
    val b = 0.072f * inv * contrast
    val s = saturation * contrast
    val shift = (0.5f - 0.5f * contrast) * 255f
    return floatArrayOf(
        r + s, g, b, 0f, shift,
        r, g + s, b, 0f, shift,
        r, g, b + s, 0f, shift,
        0f, 0f, 0f, 1f, 0f,
    )
}

private val LIGHT_X = cos(Math.toRadians(45.0)).toFloat()
private val LIGHT_Y = sin(Math.toRadians(45.0)).toFloat()

@androidx.annotation.RequiresApi(33)
private class GlassShaders(lens: Boolean, dispersion: Boolean, highlight: Boolean) {
    val lens: RuntimeShader? = if (lens) RuntimeShader(if (dispersion) LENS_DISPERSION_AGSL else LENS_AGSL) else null
    val highlight: RuntimeShader? = if (highlight) RuntimeShader(HIGHLIGHT_AGSL) else null
}

private const val ROUNDED_RECT_SDF = """
float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}
"""

private const val LENS_UNIFORMS = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float radius;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float chromaticAberration;
uniform float depthEffect;
"""

private const val LENS_AGSL = LENS_UNIFORMS + ROUNDED_RECT_SDF + """
half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));
    return content.eval(coord + d * grad);
}
"""

private const val LENS_DISPERSION_AGSL = LENS_UNIFORMS + ROUNDED_RECT_SDF + """
half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));
    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration *
        ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);
    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;
    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;
    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;
    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;
    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;
    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;
    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;
    return color;
}
"""

private const val HIGHLIGHT_AGSL = """
uniform float2 size;
uniform float radius;
uniform float2 lightDir;
uniform float falloff;
uniform half4 color;

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float d = dot(grad, lightDir);
    float intensity = pow(abs(d), falloff);
    return color * half(intensity);
}
"""
