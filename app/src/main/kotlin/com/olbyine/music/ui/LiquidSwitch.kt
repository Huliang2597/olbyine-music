package com.olbyine.music.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.olbyine.music.data.HapticArea
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val TrackWidth = 58.dp
private val TrackHeight = 32.dp
private val ThumbInset = 2.5.dp
private val ThumbHeight = TrackHeight - ThumbInset * 2
private val ThumbWidth = 36.dp

@Composable
internal fun LiquidSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val travel = with(density) { (TrackWidth - ThumbWidth - ThumbInset * 2).toPx() }
    val position = remember { Animatable(if (checked) 1f else 0f) }
    val press = remember { Animatable(0f) }
    val stretch = remember { Animatable(0f) }
    var lifted by remember { mutableStateOf(false) }
    val currentChecked by rememberUpdatedState(checked)
    val currentChange by rememberUpdatedState(onCheckedChange)
    LaunchedEffect(checked) {
        if (!lifted) position.animateTo(if (checked) 1f else 0f, spring(dampingRatio = .68f, stiffness = 420f))
    }
    val onColor = MaterialTheme.colorScheme.primary
    val offColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .14f)
    val thumbOn = if (onColor.luminance() > .6f) Color(0xFF1C1C1E) else Color.White
    val trackSource = rememberBackdropSource()
    val lensStyle = remember {
        GlassStyle(
            cornerRadius = ThumbHeight / 2,
            blur = 0.dp,
            tint = Color.White.copy(alpha = .06f),
            saturation = 1.6f,
            lens = true,
            refractionHeight = 13.dp,
            refractionAmount = 22.dp,
            dispersion = true,
            highlight = Color.White.copy(alpha = .95f),
            highlightWidth = 1.dp,
            fallback = Color.White,
        )
    }
    val lensDynamics = remember { GlassDynamics(lensProgress = { press.value }) }

    Box(
        modifier
            .size(TrackWidth, TrackHeight)
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
                stateDescription = if (checked) "开" else "关"
            }
            .pointerInput(travel) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    lifted = true
                    haptics.light(HapticArea.SWITCHES)
                    scope.launch { press.animateTo(1f, spring(dampingRatio = .58f, stiffness = 560f)) }
                    val start = position.value
                    var moved = false
                    var lastX = down.position.x
                    var lastTime = down.uptimeMillis
                    var crossed = start > .5f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        val dx = change.position.x - down.position.x
                        if (!moved && kotlin.math.abs(dx) > viewConfiguration.touchSlop / 2) moved = true
                        if (moved) {
                            val next = rubberbandClamp(start + dx / travel, 0f, 1f, dimension = .25f)
                            scope.launch { position.snapTo(next) }
                            val dt = (change.uptimeMillis - lastTime).coerceAtLeast(1)
                            val speed = kotlin.math.abs(change.position.x - lastX) / dt
                            scope.launch { stretch.snapTo((speed / 3f).coerceIn(0f, .22f)) }
                            lastX = change.position.x
                            lastTime = change.uptimeMillis
                            if ((next > .5f) != crossed) {
                                crossed = next > .5f
                                haptics.tick(HapticArea.SWITCHES)
                            }
                            change.consume()
                        }
                    }
                    val target = if (moved) position.value > .5f else !currentChecked
                    lifted = false
                    scope.launch { position.animateTo(if (target) 1f else 0f, spring(dampingRatio = .6f, stiffness = 360f)) }
                    scope.launch { press.animateTo(0f, spring(dampingRatio = .7f, stiffness = 300f)) }
                    scope.launch { stretch.animateTo(0f, spring(dampingRatio = .5f, stiffness = 400f)) }
                    if (target != currentChecked) {
                        if (target) haptics.confirm(HapticArea.SWITCHES) else haptics.click(HapticArea.SWITCHES)
                        currentChange(target)
                    }
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .backdropSource(trackSource)
                .drawBehind {
                    val p = position.value.coerceIn(0f, 1f)
                    val base = lerp(offColor, onColor, p)
                    val radius = CornerRadius(size.height / 2)
                    drawRoundRect(base, cornerRadius = radius)
                    drawRoundRect(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to Color.White.copy(alpha = .16f),
                            .5f to Color.White.copy(alpha = .02f),
                            1f to Color.Black.copy(alpha = .08f),
                        ),
                        cornerRadius = radius,
                    )
                    drawRoundRect(
                        Color.Black.copy(alpha = .10f),
                        cornerRadius = radius,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                    )
                },
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = ThumbInset)
                .graphicsLayer {
                    val p = press.value
                    val s = stretch.value
                    translationX = position.value * travel
                    scaleX = 1f + .34f * p + s
                    scaleY = 1f + .42f * p - s * .45f
                }
                .size(ThumbWidth, ThumbHeight)
                .shadow((3 - press.value * 2).coerceAtLeast(1f).dp, RoundedCornerShape(ThumbHeight / 2), clip = false)
                .glass(trackSource, lensStyle, dynamics = lensDynamics)
                .drawBehind {
                    val rest = lerp(Color.White, thumbOn, position.value.coerceIn(0f, 1f))
                    drawRoundRect(rest.copy(alpha = 1f - press.value * .94f), cornerRadius = CornerRadius(size.height / 2))
                },
        )
    }
}
@Composable
internal fun OlbyineSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    when (LocalStyleSpec.current.switch) {
        SwitchKind.LIQUID -> LiquidSwitch(checked, onCheckedChange)
        SwitchKind.MATERIAL -> androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            ),
        )
        SwitchKind.COLOROS -> SquishSwitch(
            checked, onCheckedChange, trackWidth = 46.dp, trackHeight = 26.dp, thumb = 20.dp,
            onTrack = MaterialTheme.colorScheme.primary, offTrack = MaterialTheme.colorScheme.onSurface.copy(alpha = .16f),
        )
        SwitchKind.HYPEROS -> SquishSwitch(
            checked, onCheckedChange, trackWidth = 52.dp, trackHeight = 30.dp, thumb = 22.dp,
            onTrack = MaterialTheme.colorScheme.primary, offTrack = MaterialTheme.colorScheme.onSurface.copy(alpha = .16f),
        )
    }
}

@Composable
private fun PillSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    trackWidth: androidx.compose.ui.unit.Dp,
    trackHeight: androidx.compose.ui.unit.Dp,
    thumb: androidx.compose.ui.unit.Dp,
) {
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.onSurface.copy(alpha = .16f)
    val track by animateColorAsState(if (checked) on else off, label = "pill-track")
    val position by androidx.compose.animation.core.animateFloatAsState(
        if (checked) 1f else 0f,
        spring(dampingRatio = .8f, stiffness = 520f),
        label = "pill-thumb",
    )
    val inset = (trackHeight - thumb) / 2
    val travel = with(LocalDensity.current) { (trackWidth - thumb - inset * 2).toPx() }
    val haptics = rememberHaptics()
    Box(
        Modifier
            .size(trackWidth, trackHeight)
            .clip(CircleShape)
            .background(track)
            .toggleable(
                value = checked,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onValueChange = { value ->
                    if (value) haptics.confirm(HapticArea.SWITCHES) else haptics.click(HapticArea.SWITCHES)
                    onCheckedChange(value)
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(start = inset)
                .graphicsLayer { translationX = position * travel }
                .size(thumb)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}