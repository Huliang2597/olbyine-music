package com.olbyine.music.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.olbyine.music.data.HapticArea
import com.olbyine.music.data.UiStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun OlbyineSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    startIcon: ImageVector? = null,
    endIcon: ImageVector? = null,
    quiet: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val style = LocalStyleSpec.current.style
    if (style == UiStyle.MATERIAL) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            if (startIcon != null) {
                Icon(startIcon, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(),
            )
            if (endIcon != null) {
                Spacer(Modifier.width(8.dp))
                Icon(endIcon, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
            }
        }
        return
    }
    val fill = when {
        quiet -> colors.onSurface.copy(alpha = .55f)
        style == UiStyle.IOS -> colors.onSurface
        else -> colors.primary
    }
    val track = colors.onSurface.copy(alpha = if (quiet) .12f else .14f)
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val stretchLimit = with(density) { 26.dp.toPx() }
    val knobPx = with(density) { (if (quiet) 12.dp else 24.dp).toPx() }
    fun fractionAt(x: Float, w: Int) = ((x - knobPx / 2f) / (w - knobPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val currentValue by rememberUpdatedState(value)
    val currentChange by rememberUpdatedState(onValueChange)
    val currentFinished by rememberUpdatedState(onValueChangeFinished)
    var held by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val swell by animateFloatAsState(if (held || dragging) 1f else 0f, spring(dampingRatio = .9f, stiffness = 700f), label = "slider-swell")
    var stretch by remember { mutableFloatStateOf(0f) }
    val jobs = remember { arrayOfNulls<Job>(2) }

    fun snap(raw: Float): Float {
        val clamped = raw.coerceIn(valueRange.start, valueRange.endInclusive)
        if (steps <= 0) return clamped
        val stop = span / (steps + 1)
        return valueRange.start + ((clamped - valueRange.start) / stop).roundToInt() * stop
    }
    fun emit(next: Float) {
        val previous = currentValue
        if (next == previous) return
        val bucket = { v: Float -> if (steps > 0) ((v - valueRange.start) / (span / (steps + 1))).roundToInt() else ((v - valueRange.start) / span * 10f).toInt() }
        if (bucket(next) != bucket(previous) || (next == valueRange.start || next == valueRange.endInclusive)) {
            haptics.tick(HapticArea.SCRUB)
        }
        currentChange(next)
    }
    val springBack: () -> Unit = {
        val from = stretch
        jobs[0]?.cancel()
        jobs[0] = if (from == 0f) null else scope.launch {
            animate(from, 0f, animationSpec = spring(dampingRatio = .42f, stiffness = 420f)) { v, _ -> stretch = v }
        }
    }

    Row(modifier.graphicsLayer { alpha = if (enabled) 1f else .45f }, verticalAlignment = Alignment.CenterVertically) {
        if (startIcon != null) {
            Icon(
                startIcon, null,
                Modifier.size(18.dp).graphicsLayer {
                    val pull = (-stretch / stretchLimit).coerceIn(0f, 1f)
                    translationX = -pull * 10.dp.toPx()
                    val s = 1f + .22f * pull
                    scaleX = s; scaleY = s
                },
                tint = colors.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
        }
        Box(
            Modifier
                .weight(1f)
                .height(if (quiet) 28.dp else 34.dp)
                .excludeFromSheetGestures()
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange, steps)
                    setProgress { target -> currentChange(snap(target)); currentFinished?.invoke(); true }
                }
                .pointerInput(enabled, valueRange, steps) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            held = true
                            tryAwaitRelease()
                            held = false
                        },
                        onTap = { offset ->
                            val target = snap(valueRange.start + fractionAt(offset.x, size.width) * span)
                            haptics.click(HapticArea.SCRUB)
                            jobs[1]?.cancel()
                            jobs[1] = scope.launch {
                                animate(currentValue, target, animationSpec = spring(dampingRatio = .9f, stiffness = 520f)) { v, _ -> currentChange(v) }
                                currentChange(target)
                                currentFinished?.invoke()
                            }
                        },
                    )
                }
                .pointerInput(enabled, valueRange, steps) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            jobs[1]?.cancel()
                            dragging = true
                            haptics.light(HapticArea.SCRUB)
                            emit(snap(valueRange.start + fractionAt(offset.x, size.width) * span))
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val x = change.position.x
                            val over = when {
                                x < 0f -> x
                                x > size.width -> x - size.width
                                else -> 0f
                            }
                            jobs[0]?.cancel()
                            stretch = rubberband(over, stretchLimit)
                            emit(snap(valueRange.start + fractionAt(x, size.width) * span))
                        },
                        onDragEnd = {
                            dragging = false
                            currentFinished?.invoke()
                            springBack()
                        },
                        onDragCancel = {
                            dragging = false
                            currentFinished?.invoke()
                            springBack()
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val idle = if (quiet) 3.dp else 5.dp
            val active = if (quiet) 5.dp else 7.dp
            val knobIdle = if (quiet) 12.dp else 24.dp
            val knobActive = if (quiet) 16.dp else 28.dp
            val knob = Color.White
            val shade = Color.Black
            Canvas(Modifier.fillMaxWidth().height(if (quiet) 28.dp else 34.dp)) {
                val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
                val pulled = stretch
                val thin = 1f - .35f * (abs(pulled) / stretchLimit).coerceIn(0f, 1f)
                val bar = ((idle.toPx() + (active - idle).toPx() * swell) * thin).coerceIn(1f, size.height)
                val r = (knobIdle.toPx() + (knobActive - knobIdle).toPx() * swell) / 2f
                val cy = size.height / 2f
                val top = cy - bar / 2f
                val radius = CornerRadius(bar / 2f)
                val left = minOf(pulled, 0f)
                val width = size.width + abs(pulled)
                val travel = (width - knobIdle.toPx()).coerceAtLeast(1f)
                val cx = left + knobIdle.toPx() / 2f + travel * fraction
                drawRoundRect(color = track, topLeft = Offset(left, top), size = Size(width, bar), cornerRadius = radius)
                val filled = (cx - left).coerceAtLeast(0f)
                if (filled > 0f) drawRoundRect(color = fill, topLeft = Offset(left, top), size = Size(filled, bar), cornerRadius = radius)
                if (steps in 1..20) {
                    for (k in 1..steps) {
                        val x = left + knobIdle.toPx() / 2f + travel * k / (steps + 1f)
                        val on = x <= cx
                        drawCircle(
                            color = if (on) colors.surface.copy(alpha = .55f) else colors.onSurface.copy(alpha = .28f),
                            radius = bar * .22f,
                            center = Offset(x, cy),
                        )
                    }
                }
                drawCircle(shade.copy(alpha = .06f), r + 3.dp.toPx(), Offset(cx, cy + 1.5.dp.toPx()))
                drawCircle(shade.copy(alpha = .12f), r + 1.dp.toPx(), Offset(cx, cy + 1.dp.toPx()))
                drawCircle(knob, r, Offset(cx, cy))
                drawCircle(shade.copy(alpha = .06f), r, Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(.75.dp.toPx()))
            }
        }
        if (endIcon != null) {
            Spacer(Modifier.width(10.dp))
            Icon(
                endIcon, null,
                Modifier.size(18.dp).graphicsLayer {
                    val pull = (stretch / stretchLimit).coerceIn(0f, 1f)
                    translationX = pull * 10.dp.toPx()
                    val s = 1f + .22f * pull
                    scaleX = s; scaleY = s
                },
                tint = colors.onSurfaceVariant,
            )
        }
    }
}
