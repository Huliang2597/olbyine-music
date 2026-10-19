package com.olbyine.music.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.olbyine.music.data.PlayerFx
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

internal val LocalPlayerFx = staticCompositionLocalOf { PlayerFx.DEFAULT }

internal val LocalPlayerPageActive = staticCompositionLocalOf { true }

@Composable
internal fun playerFx(bit: Int): Boolean = LocalPlayerFx.current and bit != 0 && !LocalReduceMotion.current

private const val REVEAL_MS = 620

@Composable
internal fun RevealText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    delayMs: Long = 0L,
    textModifier: Modifier = Modifier,
) {
    val enabled = playerFx(PlayerFx.TITLE_REVEAL)
    val progress = remember { Animatable(1f) }
    val shownText = remember { mutableStateOf(text) }
    LaunchedEffect(text) {
        if (shownText.value == text) return@LaunchedEffect
        shownText.value = text
        if (!enabled) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        delay(delayMs)
        progress.animateTo(1f, tween(REVEAL_MS, easing = LinearEasing))
    }
    val animating by remember { derivedStateOf { progress.value < 1f } }
    val density = LocalDensity.current
    val rise = with(density) { 7.dp.toPx() }
    val soft = with(density) { 64.dp.toPx() }
    val blur = with(density) { 9.dp.toPx() }
    fun DrawScope.front(): Float = FastOutSlowInEasing.transform(progress.value) * (size.width + soft)

    Box(
        modifier.graphicsLayer {
            val p = progress.value
            translationY = rise * (1f - FastOutSlowInEasing.transform(p))
            alpha = .35f + .65f * min(1f, p * 3f)
        },
    ) {
        if (animating) {
            Text(
                text,
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        alpha = .7f
                        renderEffect = if (Build.VERSION.SDK_INT >= 31 && blur >= 1f) BlurEffect(blur, blur, TileMode.Decal) else null
                    }
                    .drawWithContent {
                        drawContent()
                        val edge = front()
                        drawRect(
                            Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, startX = edge - soft, endX = edge),
                            blendMode = BlendMode.DstIn,
                        )
                    },
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text,
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    compositingStrategy = if (progress.value < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                }
                .drawWithContent {
                    drawContent()
                    if (progress.value >= 1f) return@drawWithContent
                    val edge = front()
                    drawRect(
                        Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = edge - soft, endX = edge),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .then(textModifier),
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun DrawScope.drawHeartBurst(t: Float, accent: Color) {
    if (t >= 1f) return
    val c = Offset(size.width / 2f, size.height / 2f)
    val unit = size.minDimension
    val out = LinearOutSlowInEasing.transform(t)
    val ringT = min(1f, t / .7f)
    if (ringT < 1f) {
        val ringR = unit * (.16f + .42f * LinearOutSlowInEasing.transform(ringT))
        drawCircle(
            color = accent.copy(alpha = (1f - ringT) * .85f),
            radius = ringR,
            center = c,
            style = Stroke(width = unit * .09f * (1f - ringT) + 1f),
        )
    }
    val light = lerp(accent, Color.White, .45f)
    val fade = (1f - t).pow(1.3f)
    val stroke = 2.2.dp.toPx() * (1f - t * .5f)
    for (i in 0 until 8) {
        val angle = (i * 45f + 22.5f) * (PI.toFloat() / 180f)
        val dir = Offset(cos(angle), sin(angle))
        val reach = if (i % 2 == 0) .82f else .66f
        val r1 = unit * (.30f + (reach - .30f) * out)
        val r2 = r1 + unit * .16f * (1f - out)
        drawLine(
            color = (if (i % 2 == 0) accent else light).copy(alpha = fade),
            start = c + dir * r1,
            end = c + dir * r2,
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

private const val TILT_DEG = 9f

@Stable
internal class TiltGlare(val enabled: Boolean) {
    val x = Animatable(0f)
    val y = Animatable(0f)
    val glare = Animatable(0f)
    var touch by mutableStateOf(Offset.Zero)
}

@Composable
internal fun rememberTiltGlare(enabled: Boolean): TiltGlare = remember(enabled) { TiltGlare(enabled) }

internal fun Modifier.tiltLayer(tilt: TiltGlare): Modifier = if (!tilt.enabled) this else graphicsLayer {
    rotationX = -tilt.y.value * TILT_DEG
    rotationY = tilt.x.value * TILT_DEG
    val lift = 1f + .02f * tilt.glare.value
    scaleX = lift
    scaleY = lift
    cameraDistance = 14f * density
}

internal fun Modifier.tiltOverlay(tilt: TiltGlare): Modifier = if (!tilt.enabled) this else composed {
    val scope = rememberCoroutineScope()
    var jobs by remember { mutableStateOf<List<Job>>(emptyList()) }
    fun aim(position: Offset, size: IntSize) {
        if (size.width <= 0 || size.height <= 0) return
        tilt.touch = position
        val nx = (position.x / size.width * 2f - 1f).coerceIn(-1f, 1f)
        val ny = (position.y / size.height * 2f - 1f).coerceIn(-1f, 1f)
        jobs.forEach { it.cancel() }
        jobs = listOf(
            scope.launch { tilt.x.animateTo(nx, appleSpring(response = .18f)) },
            scope.launch { tilt.y.animateTo(ny, appleSpring(response = .18f)) },
            scope.launch { tilt.glare.animateTo(1f, tween(160, easing = LinearOutSlowInEasing)) },
        )
    }
    fun release() {
        jobs.forEach { it.cancel() }
        jobs = listOf(
            scope.launch { tilt.x.animateTo(0f, appleSpring(response = .4f, damping = .85f)) },
            scope.launch { tilt.y.animateTo(0f, appleSpring(response = .4f, damping = .85f)) },
            scope.launch { tilt.glare.animateTo(0f, tween(320, easing = FastOutSlowInEasing)) },
        )
    }
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                aim(down.position, size)
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if (change.isConsumed && change.positionChanged()) break
                    aim(change.position, size)
                }
                release()
            }
        }
        .drawWithContent {
            drawContent()
            val g = tilt.glare.value
            if (g <= .01f) return@drawWithContent
            val r = size.maxDimension * .8f
            drawRect(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = .30f * g),
                    .45f to Color.White.copy(alpha = .10f * g),
                    1f to Color.Transparent,
                    center = tilt.touch,
                    radius = r,
                ),
            )
        }
}

@Composable
internal fun RollingTime(
    text: String,
    value: Long,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
) {
    if (!playerFx(PlayerFx.ROLLING_TIME)) {
        Text(text, modifier, style = style, color = color, textAlign = if (alignEnd) TextAlign.End else TextAlign.Start, maxLines = 1)
        return
    }
    val last = remember { mutableLongStateOf(value) }
    val up = value >= last.longValue
    SideEffect { last.longValue = value }
    Row(modifier, horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start) {
        text.forEachIndexed { index, char ->
            key(text.length - index) {
                AnimatedContent(
                    targetState = char,
                    modifier = Modifier.clipToBounds(),
                    contentAlignment = Alignment.Center,
                    transitionSpec = {
                        val dir = if (up) 1 else -1
                        (slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it * dir } + fadeIn(tween(200)))
                            .togetherWith(slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { -it * dir } + fadeOut(tween(160)))
                            .using(SizeTransform(clip = true))
                    },
                    label = "rolling-digit",
                ) { shown ->
                    Text(shown.toString(), style = style, color = color, maxLines = 1)
                }
            }
        }
    }
}

private const val SHINE_PERIOD_MS = 3_800

internal fun Modifier.shine(active: Boolean): Modifier = if (!active) this else composed {
    val transition = rememberInfiniteTransition(label = "shine")
    val clock = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SHINE_PERIOD_MS, easing = LinearEasing)),
        label = "shine-clock",
    )
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val phase = clock.value * 3f
            if (phase >= 1f) return@drawWithContent
            val band = size.height * 2.4f
            val x = -band + (size.width + band * 2f) * FastOutSlowInEasing.transform(phase)
            drawRect(
                Brush.linearGradient(
                    0f to Color.Transparent,
                    .5f to Color.White.copy(alpha = .85f),
                    1f to Color.Transparent,
                    start = Offset(x - band / 2f, 0f),
                    end = Offset(x + band / 2f, size.height * .7f),
                ),
                blendMode = BlendMode.SrcAtop,
            )
        }
}

private const val GRAIN_TILE = 192

private fun grainBitmap(): Bitmap {
    val random = Random(0x6A11)
    val pixels = IntArray(GRAIN_TILE * GRAIN_TILE) {
        val v = random.nextInt(256)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    return Bitmap.createBitmap(pixels, GRAIN_TILE, GRAIN_TILE, Bitmap.Config.ARGB_8888)
}

@Composable
internal fun FilmGrain(active: Boolean, modifier: Modifier = Modifier, strength: Float = .11f) {
    val shader = remember { ImageShader(grainBitmap().asImageBitmap(), TileMode.Repeated, TileMode.Repeated) }
    val matrix = remember { Matrix() }
    var deal by remember { mutableIntStateOf(0) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var frames = 0
        while (true) {
            withFrameNanos { }
            if (++frames % 2 == 0) deal++
        }
    }
    Canvas(modifier) {
        val random = Random(deal)
        val scale = (density / 1.6f).coerceAtLeast(1f)
        matrix.setScale(scale, scale)
        matrix.postTranslate(random.nextFloat() * GRAIN_TILE * scale, random.nextFloat() * GRAIN_TILE * scale)
        shader.setLocalMatrix(matrix)
        drawRect(ShaderBrush(shader), alpha = strength, blendMode = BlendMode.Overlay)
    }
}
