@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.olbyine.music.ui

import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.olbyine.music.data.Album
import com.olbyine.music.data.HapticArea
import com.olbyine.music.data.PlayerFx
import com.olbyine.music.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val PULSE_MS = 560
private const val PULSE_OUT = .4f
private const val PULSE_DOT = .3f
private const val PULSE_OVERSHOOT = 1.7f
private const val PULSE_BEAT = 3f

private fun backOut(k: Float, c: Float): Float {
    val u = k - 1f
    return 1f + (c + 1f) * u * u * u + c * u * u
}

private fun pulseSwell(t: Float): Float = when {
    t <= 0f -> 0f
    t < PULSE_OUT -> 1f - (1f - t / PULSE_OUT).pow(3)
    else -> 1f - backOut(((t - PULSE_OUT) / (1f - PULSE_OUT)).coerceIn(0f, 1f), PULSE_OVERSHOOT)
}

@Composable
internal fun PulseHeart(
    liked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    count: Long? = null,
    likedColor: Color = MaterialTheme.colorScheme.primary,
    idleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    pill: Color = Color.Transparent,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    iconSize: Dp = 24.dp,
    minSize: Dp = 44.dp,
    onLongPress: (() -> Unit)? = null,
) {
    val reduce = LocalReduceMotion.current
    val sparks = playerFx(PlayerFx.HEART_BURST)
    var shownLiked by remember { mutableStateOf(liked) }
    var shownCount by remember { mutableLongStateOf(count ?: 0L) }
    val clock = remember { Animatable(1f) }
    val burst = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(liked, count) {
        if (liked == shownLiked) {
            shownCount = count ?: 0L
            return@LaunchedEffect
        }
        if (reduce) {
            shownLiked = liked
            shownCount = count ?: 0L
            return@LaunchedEffect
        }
        var swapped = false
        clock.snapTo(0f)
        clock.animateTo(1f, tween(PULSE_MS, easing = LinearEasing)) {
            if (!swapped && value >= PULSE_OUT) {
                swapped = true
                shownLiked = liked
                shownCount = count ?: 0L
                if (liked && sparks) scope.launch {
                    burst.snapTo(0f)
                    burst.animateTo(1f, tween(640, easing = LinearEasing))
                }
            }
        }
        if (!swapped) {
            shownLiked = liked
            shownCount = count ?: 0L
        }
    }
    val tint by animateColorAsState(if (shownLiked) likedColor else idleColor, tween(160), label = "pulse-heart")
    val interaction = remember { MutableInteractionSource() }
    val press = rememberPressProgress(interaction)
    val haptics = rememberHaptics()
    val padded = count != null || pill.alpha > 0f
    Box(
        modifier
            .then(if (padded) Modifier else Modifier.size(minSize))
            .graphicsLayer {
                val s = 1f - .03f * press.value
                scaleX = s
                scaleY = s
            }
            .semantics { role = Role.Button; stateDescription = if (liked) "已喜欢" else "未喜欢" }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onLongClick = onLongPress?.let { long -> { haptics.heavy(HapticArea.PLAYBACK); long() } },
            ) {
                haptics.click(HapticArea.PLAYBACK)
                onToggle()
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .graphicsLayer {
                    val s = 1f - PULSE_BEAT / 100f * pulseSwell(clock.value)
                    scaleX = s
                    scaleY = s
                }
                .then(if (padded) Modifier.clip(CircleShape).background(pill) else Modifier)
                .then(if (padded) Modifier.padding(start = iconSize * .3f, top = iconSize * .3f, bottom = iconSize * .3f, end = iconSize * .4f) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(iconSize)
                    .drawBehind { drawHeartBurst(burst.value, likedColor) }
                    .graphicsLayer {
                        val k = 1f - (1f - PULSE_DOT) * pulseSwell(clock.value)
                        scaleX = k
                        scaleY = k
                    },
                contentAlignment = Alignment.Center,
            ) {
                StyledIcon(if (shownLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, Modifier.size(iconSize), tint = tint)
            }
            if (count != null) {
                Spacer(Modifier.width(iconSize * .25f))
                RollingTime(
                    playCountLabel(shownCount),
                    shownCount,
                    MaterialTheme.typography.labelLarge.copy(fontSize = (iconSize.value * .5f).sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                    textColor,
                )
            }
        }
    }
}

internal class TrackActions(
    val addToPlaylist: (Track) -> Unit,
    val playNext: (Track) -> Unit,
    val enqueue: (Track) -> Unit,
)

internal val LocalTrackActions = androidx.compose.runtime.staticCompositionLocalOf<TrackActions?> { null }

@Composable
internal fun HeartWithMenu(
    track: () -> Track?,
    liked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    count: Long? = null,
    likedColor: Color = MaterialTheme.colorScheme.primary,
    idleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    pill: Color = Color.Transparent,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    iconSize: Dp = 24.dp,
    minSize: Dp = 44.dp,
    showQueueActions: Boolean = true,
    onOpenArtist: (() -> Unit)? = null,
    onOpenAlbum: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
) {
    val actions = LocalTrackActions.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        PulseHeart(
            liked, onToggle, Modifier, count, likedColor, idleColor, pill, textColor, iconSize, minSize,
            onLongPress = if (actions != null) ({
                if (showQueueActions) {
                    open = true
                } else {
                    track()?.let(actions.addToPlaylist)
                }
            }) else null,
        )
        if (actions != null && showQueueActions) {
            OlbyineDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                GlassMenuItem("收藏到歌单", { open = false; track()?.let(actions.addToPlaylist) }, icon = Icons.AutoMirrored.Rounded.PlaylistAdd)
                GlassMenuItem("下一首播放", { open = false; track()?.let(actions.playNext) }, icon = Icons.AutoMirrored.Rounded.PlaylistPlay)
                GlassMenuItem("加入播放列表", { open = false; track()?.let(actions.enqueue) }, icon = Icons.AutoMirrored.Rounded.QueueMusic)
                if (onDownload != null) GlassMenuItem("下载", { open = false; onDownload() }, icon = Icons.Rounded.FileDownload)
                if (onOpenArtist != null) GlassMenuItem("打开歌手页", { open = false; onOpenArtist() }, icon = Icons.Rounded.Mic)
                if (onOpenAlbum != null) GlassMenuItem("打开专辑页", { open = false; onOpenAlbum() }, icon = Icons.Rounded.Album)
            }
        }
    }
}

private const val SQUISH_MAX = .4f
private const val SQUISH_GAIN = .36f

@Composable
internal fun SquishSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    trackWidth: Dp,
    trackHeight: Dp,
    thumb: Dp,
    onTrack: Color,
    offTrack: Color,
    thumbColor: Color = Color.White,
    thumbOnColor: Color = Color.White,
) {
    val density = LocalDensity.current
    val reduce = LocalReduceMotion.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val insetPx = with(density) { ((trackHeight - thumb) / 2).toPx() }
    val thumbPx = with(density) { thumb.toPx() }
    val travel = with(density) { trackWidth.toPx() } - thumbPx - insetPx * 2
    val stretchSpeed = with(density) { 600.dp.toPx() }
    val position = remember { Animatable(if (checked) 1f else 0f) }
    val swell = remember { Animatable(1f) }
    val stretch = remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var wake by remember { mutableIntStateOf(0) }
    val currentChecked by rememberUpdatedState(checked)
    val currentChange by rememberUpdatedState(onCheckedChange)
    LaunchedEffect(checked, dragging) {
        if (dragging) return@LaunchedEffect
        wake++
        val target = if (checked) 1f else 0f
        if (reduce) position.snapTo(target) else position.animateTo(target, spring(dampingRatio = .87f, stiffness = 189f))
    }
    LaunchedEffect(wake) {
        if (wake == 0) return@LaunchedEffect
        var smooth = 0f
        var last = position.value
        var lastTime = 0L
        var quiet = 0
        while (quiet < 10) {
            withFrameNanos { now ->
                val dt = if (lastTime == 0L) 1f / 120f else ((now - lastTime) / 1e9f).coerceIn(1f / 240f, 1f / 20f)
                lastTime = now
                val v = (position.value - last) * travel / dt
                last = position.value
                smooth += (v - smooth) * (1f - exp(-dt * 16f))
                stretch.floatValue = if (reduce) 1f else 1f + min(SQUISH_MAX, abs(smooth) / stretchSpeed) * SQUISH_GAIN
                quiet = if (!dragging && abs(smooth) < 4f && !position.isRunning) quiet + 1 else 0
            }
        }
        stretch.floatValue = 1f
    }
    Box(
        Modifier
            .size(trackWidth, trackHeight)
            .semantics {
                role = Role.Switch
                stateDescription = if (checked) "开" else "关"
            }
            .clip(CircleShape)
            .drawBehind {
                drawRoundRect(lerp(offTrack, onTrack, position.value.coerceIn(0f, 1f)), cornerRadius = CornerRadius(size.height / 2f))
            }
            .pointerInput(travel) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    dragging = true
                    wake++
                    scope.launch { swell.animateTo(1.06f, spring(dampingRatio = .85f, stiffness = 600f)) }
                    val grab = down.position.x - (insetPx + position.value * travel)
                    var moved = false
                    var lastOn = currentChecked
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        if (!moved && abs(change.position.x - down.position.x) > viewConfiguration.touchSlop / 2) moved = true
                        if (moved) {
                            change.consume()
                            val next = ((change.position.x - grab - insetPx) / travel).coerceIn(0f, 1f)
                            scope.launch { position.snapTo(next) }
                            val on = next > .5f
                            if (on != lastOn) {
                                lastOn = on
                                haptics.tick(HapticArea.SWITCHES)
                                currentChange(on)
                            }
                        }
                    }
                    scope.launch { swell.animateTo(1f, spring(dampingRatio = .85f, stiffness = 600f)) }
                    if (!moved) {
                        val next = !currentChecked
                        if (next) haptics.confirm(HapticArea.SWITCHES) else haptics.click(HapticArea.SWITCHES)
                        currentChange(next)
                    }
                    dragging = false
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    translationX = insetPx + position.value * travel
                    val h = swell.value
                    val s = stretch.floatValue
                    scaleX = s * h
                    scaleY = h / s
                }
                .size(thumb)
                .shadow(2.dp, CircleShape)
                .drawBehind { drawCircle(lerp(thumbColor, thumbOnColor, position.value.coerceIn(0f, 1f))) },
        )
    }
}

internal object GlassIconColors {
    val blue = listOf(Color.hsl(214f, .95f, .66f), Color.hsl(226f, .80f, .50f))
    val purple = listOf(Color.hsl(276f, .84f, .70f), Color.hsl(262f, .66f, .52f))
    val red = listOf(Color.hsl(354f, .95f, .68f), Color.hsl(342f, .80f, .52f))
    val indigo = listOf(Color.hsl(236f, .90f, .72f), Color.hsl(246f, .64f, .54f))
    val orange = listOf(Color.hsl(38f, 1f, .62f), Color.hsl(20f, .94f, .54f))
    val green = listOf(Color.hsl(146f, .66f, .54f), Color.hsl(162f, .82f, .34f))
    val teal = listOf(Color.hsl(184f, .80f, .56f), Color.hsl(196f, .84f, .40f))
    val pink = listOf(Color.hsl(326f, .92f, .74f), Color.hsl(314f, .70f, .54f))
    val graphite = listOf(Color.hsl(250f, .06f, .46f), Color.hsl(250f, .08f, .22f))
    val silver = listOf(Color.hsl(240f, .06f, .92f), Color.hsl(240f, .05f, .70f))
}

private const val GLASS_TILT = 30f

private fun easeOutCubic(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return 1f - (1f - c).pow(3)
}

private fun fadeBetween(from: Float, to: Float, t: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

@Composable
internal fun GlassIcon(
    icon: ImageVector,
    gradient: List<Color>,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    delayMs: Long = 0L,
    pressed: () -> Float = { 0f },
) {
    val reduce = LocalReduceMotion.current
    val enter = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce && enter.value < 1f) {
            delay(delayMs)
            enter.animateTo(1f, tween(760, easing = LinearEasing))
        }
    }
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val radius = size * .3f
    val shape = RoundedCornerShape(radius)
    val brush = Brush.linearGradient(gradient)
    val blurPx = with(LocalDensity.current) { (size * .16f).toPx() }
    fun swing() = easeOutCubic((enter.value - .18f) / .82f)
    Box(modifier.size(size)) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(1f, 1f)
                    rotationZ = GLASS_TILT * swing()
                    val s = 1f - .08f * pressed()
                    scaleX = s
                    scaleY = s
                    alpha = fadeBetween(0f, .35f, enter.value)
                }
                .clip(shape)
                .background(brush),
        )
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val settle = .92f + .08f * easeOutCubic(enter.value)
                    val s = (1f - .08f * pressed()) * settle
                    scaleX = s
                    scaleY = s
                    alpha = fadeBetween(0f, .55f, enter.value)
                }
                .shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = .25f), spotColor = Color.Black.copy(alpha = .25f))
                .clip(shape),
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        renderEffect = if (Build.VERSION.SDK_INT >= 31 && blurPx >= 1f) BlurEffect(blurPx, blurPx, TileMode.Decal) else null
                    }
                    .drawBehind {
                        rotate(GLASS_TILT * swing(), pivot = Offset(this.size.width, this.size.height)) {
                            drawRoundRect(brush, cornerRadius = CornerRadius(radius.toPx()))
                        }
                    },
            )
            Box(Modifier.matchParentSize().background(Color.White.copy(alpha = if (dark) .14f else .26f)))
            Box(
                Modifier.matchParentSize().background(
                    Brush.linearGradient(0f to Color.White.copy(alpha = .34f), .42f to Color.Transparent, .75f to Color.Transparent, 1f to Color.Black.copy(alpha = .12f)),
                ),
            )
            Box(Modifier.matchParentSize().lightRim(shape, if (dark) .55f else .85f))
            Icon(
                icon,
                null,
                Modifier
                    .align(Alignment.Center)
                    .size(size * .5f)
                    .graphicsLayer {
                        translationY = (1f - easeOutCubic((enter.value - .25f) / .75f)) * .1f * size.toPx()
                        alpha = fadeBetween(.3f, .85f, enter.value)
                    },
                tint = if (gradient.first().luminance() > .6f) Color(0xFF3A3A3C) else Color.White,
            )
        }
    }
}

private val BOUNCE_PATTERN = floatArrayOf(1f, .5f, -.3f, -1f, .2f, .75f, -.6f, .35f, -.85f, .1f)

private class BounceBodies(count: Int) {
    val x = FloatArray(count)
    val y = FloatArray(count)
    val r = FloatArray(count)
    val s = FloatArray(count)
    val vx = FloatArray(count)
    val vy = FloatArray(count)
    val vr = FloatArray(count)
    val vs = FloatArray(count)
    var time = 0f
    var initialised = false
}

@Composable
internal fun BounceCards(
    tracks: List<Track>,
    modifier: Modifier = Modifier,
    cardSize: Dp = 116.dp,
    spread: Dp = 48.dp,
    push: Dp = 70.dp,
    rotation: Float = 10f,
    onCardClick: (Int) -> Unit = {},
) {
    val count = tracks.size
    if (count == 0) return
    val density = LocalDensity.current
    val reduce = LocalReduceMotion.current
    val haptics = rememberHaptics()
    val cardPx = with(density) { cardSize.toPx() }
    val spreadPx = with(density) { spread.toPx() }
    val pushPx = with(density) { push.toPx() }
    val bodies = remember(count) { BounceBodies(count) }
    var hovered by remember { mutableIntStateOf(-1) }
    val tick = remember { mutableIntStateOf(0) }
    var wake by remember { mutableIntStateOf(1) }
    val middle = (count - 1) / 2f
    fun slotX(i: Int) = (i - middle) * spreadPx
    fun slotR(i: Int) = rotation * BOUNCE_PATTERN[i % BOUNCE_PATTERN.size]
    val currentClick by rememberUpdatedState(onCardClick)
    LaunchedEffect(wake, count) {
        if (!bodies.initialised) {
            for (i in 0 until count) {
                bodies.x[i] = slotX(i)
                bodies.r[i] = slotR(i)
                bodies.s[i] = if (reduce) 1f else 0f
            }
            bodies.initialised = true
        }
        var last = 0L
        while (true) {
            var moving = 0f
            withFrameNanos { now ->
                val dt = if (last == 0L) 1f / 60f else ((now - last) / 1e9f).coerceIn(1f / 240f, 1f / 30f)
                last = now
                bodies.time += dt
                val popK = 170f
                val popC = 2f * (1f - .85f * .6f) * sqrt(popK)
                val moveK = 210f
                val moveC = 2f * (if (reduce) 1f else .72f) * sqrt(moveK)
                val steps = kotlin.math.ceil(dt / (1f / 240f)).toInt().coerceAtLeast(1)
                val h = dt / steps
                for (i in 0 until count) {
                    val appeared = reduce || bodies.time >= .35f + i * .06f
                    var tx = slotX(i)
                    var ty = 0f
                    var tr = slotR(i)
                    var ts = if (appeared) 1f else 0f
                    val hover = hovered
                    if (hover >= 0 && appeared) {
                        if (i == hover) {
                            ty = -cardPx * .04f
                            tr = 0f
                            ts = 1.06f
                        } else {
                            tx += kotlin.math.sign((i - hover).toFloat()) * pushPx
                        }
                    }
                    repeat(steps) {
                        bodies.vx[i] += ((tx - bodies.x[i]) * moveK - bodies.vx[i] * moveC) * h
                        bodies.x[i] += bodies.vx[i] * h
                        bodies.vy[i] += ((ty - bodies.y[i]) * moveK - bodies.vy[i] * moveC) * h
                        bodies.y[i] += bodies.vy[i] * h
                        bodies.vr[i] += ((tr - bodies.r[i]) * moveK - bodies.vr[i] * moveC) * h
                        bodies.r[i] += bodies.vr[i] * h
                        if (reduce) {
                            bodies.s[i] = ts
                            bodies.vs[i] = 0f
                        } else {
                            bodies.vs[i] += ((ts - bodies.s[i]) * popK - bodies.vs[i] * popC) * h
                            bodies.s[i] += bodies.vs[i] * h
                        }
                    }
                    moving += abs(bodies.vx[i]) + abs(bodies.vy[i]) + abs(bodies.vr[i]) + abs(bodies.vs[i]) * 100f +
                        abs(tx - bodies.x[i]) + abs(ty - bodies.y[i]) + abs(tr - bodies.r[i]) + abs(ts - bodies.s[i]) * 100f +
                        (if (appeared) 0f else 1f)
                }
                tick.intValue++
            }
            if (moving < .02f) break
        }
    }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(cardSize + 44.dp)
            .pointerInput(count, spreadPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun pick(p: Offset): Int {
                        val px = p.x - size.width / 2f
                        val py = p.y - size.height / 2f
                        var best = -1
                        var distance = Float.MAX_VALUE
                        for (i in 0 until count) {
                            val gap = abs(px - slotX(i))
                            if (gap < distance) {
                                distance = gap
                                best = i
                            }
                        }
                        val half = cardPx / 2f
                        if (abs(py) > half + 24f || distance > half + 24f) return -1
                        return best
                    }
                    hovered = pick(down.position)
                    wake++
                    var travel = 0f
                    var last = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        travel += (change.position - last).getDistance()
                        last = change.position
                        if (!change.pressed) break
                        val next = pick(change.position)
                        if (next != hovered) {
                            hovered = next
                            haptics.tick(HapticArea.SCRUB)
                            wake++
                        }
                    }
                    val tapped = hovered
                    if (travel < viewConfiguration.touchSlop && tapped >= 0) {
                        if (!reduce) bodies.vs[tapped] -= 2.2f
                        haptics.click(HapticArea.MENUS)
                        currentClick(tapped)
                    }
                    hovered = -1
                    wake++
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val border = if (MaterialTheme.colorScheme.background.luminance() < .5f) Color(0xFFF2F2F2) else Color.White
        tracks.forEachIndexed { i, track ->
            key(i) {
                Box(
                    Modifier
                        .zIndex(if (i == hovered) count + 1f else i.toFloat())
                        .size(cardSize)
                        .graphicsLayer {
                            tick.intValue
                            translationX = bodies.x[i]
                            translationY = bodies.y[i]
                            rotationZ = bodies.r[i]
                            val s = max(0f, bodies.s[i])
                            scaleX = s
                            scaleY = s
                        }
                        .shadow(if (i == hovered) 18.dp else 10.dp, RoundedCornerShape(18.dp), clip = false,
                            ambientColor = Color.Black.copy(alpha = .3f), spotColor = Color.Black.copy(alpha = .35f))
                        .clip(RoundedCornerShape(18.dp))
                        .background(border)
                        .padding(4.dp),
                ) {
                    TrackArtwork(track, Modifier.fillMaxSize(), 14.dp, decodeEdge = 512)
                }
            }
        }
    }
}

private class SphereState(val count: Int) {
    val points: Array<FloatArray> = Array(count) { i ->
        val golden = (PI * (3.0 - sqrt(5.0))).toFloat()
        val y = 1f - 2f * (i + .5f) / count
        val ring = sqrt(1f - y * y)
        floatArrayOf(cos(golden * i) * ring, y, sin(golden * i) * ring)
    }
    var q = quatNormalize(floatArrayOf(.08f, -.3f, 0f, 1f))
    val rotated: Array<FloatArray> = Array(count) { FloatArray(3) }
    var axis = floatArrayOf(0f, 1f, 0f)
    var speed = 0f
    var front = 0
    var target = -1
    var camera = 3.6f
    var dragging = false
    var dragX = 0f
    var dragY = 0f
    var moving = true
    var reveal = 0f

    fun refresh() {
        for (i in 0 until count) quatRotate(q, points[i], rotated[i])
    }
}

private fun quatMultiply(a: FloatArray, b: FloatArray) = floatArrayOf(
    a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
    a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
    a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
    a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2],
)

private fun quatAxisAngle(axis: FloatArray, angle: Float): FloatArray {
    val s = sin(angle / 2f)
    return floatArrayOf(axis[0] * s, axis[1] * s, axis[2] * s, cos(angle / 2f))
}

private fun quatNormalize(q: FloatArray): FloatArray {
    val l = sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]).takeIf { it > 0f } ?: 1f
    return floatArrayOf(q[0] / l, q[1] / l, q[2] / l, q[3] / l)
}

private fun quatRotate(q: FloatArray, v: FloatArray, out: FloatArray) {
    val tx = 2f * (q[1] * v[2] - q[2] * v[1])
    val ty = 2f * (q[2] * v[0] - q[0] * v[2])
    val tz = 2f * (q[0] * v[1] - q[1] * v[0])
    out[0] = v[0] + q[3] * tx + (q[1] * tz - q[2] * ty)
    out[1] = v[1] + q[3] * ty + (q[2] * tx - q[0] * tz)
    out[2] = v[2] + q[3] * tz + (q[0] * ty - q[1] * tx)
}

private fun normalize3(v: FloatArray): FloatArray {
    val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).takeIf { it > 1e-6f } ?: return floatArrayOf(0f, 1f, 0f)
    return floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
}

private const val SPHERE_ZOOM = 1.6f
private const val SPHERE_REST = 2.6f
private const val SPHERE_INERTIA = .6f

private class SphereProjection(val x: Float, val y: Float, val scale: Float)

private fun sphereProject(c: FloatArray, camera: Float, radiusPx: Float): SphereProjection {
    val p = (SPHERE_REST - 1f) / (camera - c[2])
    return SphereProjection(
        x = c[0] * radiusPx * SPHERE_ZOOM * p,
        y = -c[1] * radiusPx * SPHERE_ZOOM * p,
        scale = p * (.55f + .45f * c[2]),
    )
}

@Composable
internal fun AlbumSphere(
    albums: List<Album>,
    currentTrackId: String?,
    onAlbum: (Album) -> Unit,
    onPlay: (Album) -> Unit,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    onShuffle: ((Album) -> Unit)? = null,
) {
    if (albums.isEmpty()) {
        EmptyState(
            Icons.Rounded.Album,
            "还没有专辑",
            modifier = Modifier.fillMaxSize().padding(top = topContentPadding, bottom = bottomContentPadding),
            message = "导入歌曲后会按专辑自动归类",
        )
        return
    }
    val reduce = LocalReduceMotion.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val tileCount = albums.size.coerceIn(36, 72)
    val sphere = remember(tileCount) { SphereState(tileCount).also { it.refresh() } }
    val assign = remember(tileCount, albums) { mutableStateListOf<Int>().apply { for (i in 0 until tileCount) add(i % albums.size) } }
    val hiddenSince = remember(tileCount) { BooleanArray(tileCount) }
    var nextAlbum by remember(tileCount, albums) { mutableIntStateOf(tileCount % albums.size) }
    val tick = remember { mutableIntStateOf(0) }
    var wake by remember { mutableIntStateOf(1) }
    var shownFront by remember { mutableIntStateOf(0) }
    var settled by remember { mutableStateOf(false) }
    var zoomTile by remember { mutableIntStateOf(-1) }
    val zoom = remember { Animatable(0f) }
    val currentAlbums by rememberUpdatedState(albums)
    val albumAt: (Int) -> Album = { tile -> currentAlbums[assign[tile.coerceIn(0, tileCount - 1)] % currentAlbums.size] }

    fun nearest(): Int {
        var best = 0
        var bestZ = -2f
        for (i in 0 until sphere.count) if (sphere.rotated[i][2] > bestZ) { bestZ = sphere.rotated[i][2]; best = i }
        return best
    }
    val openZoom: (Int) -> Unit = { tile ->
        haptics.click(HapticArea.MENUS)
        zoomTile = tile
        scope.launch { zoom.animateTo(1f, if (reduce) tween(0) else appleSpring(response = .42f)) }
    }
    val closeZoom: () -> Unit = {
        scope.launch {
            zoom.animateTo(0f, if (reduce) tween(0) else appleSpring(response = .32f))
            zoomTile = -1
        }
    }
    if (zoomTile >= 0) androidx.activity.compose.BackHandler { closeZoom() }

    LaunchedEffect(wake) {
        var last = 0L
        while (true) {
            var busy = false
            withFrameNanos { now ->
                val dt = if (last == 0L) 1f / 60f else ((now - last) / 1e9f).coerceIn(1f / 240f, .05f)
                last = now
                sphere.reveal = min(1f, sphere.reveal + dt / (if (reduce) .01f else .7f))
                if (sphere.dragging) {
                    val dx = sphere.dragX
                    val dy = sphere.dragY
                    if (dx != 0f || dy != 0f) {
                        val length = hypot(dx, dy)
                        val angle = length * 2.6f
                        val axis = normalize3(floatArrayOf(dy, dx, 0f))
                        sphere.q = quatNormalize(quatMultiply(quatAxisAngle(axis, angle), sphere.q))
                        val blend = 1f - exp(-dt * 30f)
                        sphere.axis = normalize3(FloatArray(3) { sphere.axis[it] + (axis[it] - sphere.axis[it]) * blend })
                        sphere.speed += (angle / dt - sphere.speed) * blend
                        sphere.dragX = 0f
                        sphere.dragY = 0f
                    } else sphere.speed *= exp(-dt * 14f)
                } else {
                    if (sphere.speed > .0005f) {
                        sphere.q = quatNormalize(quatMultiply(quatAxisAngle(sphere.axis, sphere.speed * dt), sphere.q))
                        sphere.speed *= exp(-dt * (6f - 5f * SPHERE_INERTIA))
                    } else sphere.speed = 0f
                    sphere.refresh()
                    val goal = if (sphere.target >= 0) sphere.target else nearest()
                    val c = sphere.rotated[goal]
                    val angle = acos(c[2].coerceIn(-1f, 1f))
                    val pull = if (sphere.target >= 0) 1f else (1f - sphere.speed / 1.4f).coerceIn(0f, 1f)
                    if (sphere.target >= 0) sphere.speed *= exp(-dt * 20f)
                    if (angle > .0002f && pull > 0f) {
                        val axis = floatArrayOf(c[1], -c[0], 0f)
                        if (hypot(axis[0], axis[1]) > 1e-6f) {
                            val step = angle * (1f - exp(-dt * (if (sphere.target >= 0) 7f else 8f))) * pull
                            sphere.q = quatNormalize(quatMultiply(quatAxisAngle(normalize3(axis), step), sphere.q))
                        }
                    } else if (sphere.target >= 0 && sphere.speed < .01f) sphere.target = -1
                    sphere.front = goal
                }
                sphere.refresh()
                val frontZ = sphere.rotated[sphere.front][2]
                val moving = sphere.dragging || sphere.speed > .08f || acos(frontZ.coerceIn(-1f, 1f)) > .01f
                sphere.moving = moving
                val pullBack = (if (sphere.dragging) 1f else sphere.speed / 2.2f).coerceIn(0f, 1f)
                val goalCamera = SPHERE_REST + pullBack * 1.25f + (1f - sphere.reveal) * 1.5f
                sphere.camera += (goalCamera - sphere.camera) * (1f - exp(-dt * 4.5f))
                if (currentAlbums.size > tileCount) {
                    for (i in 0 until tileCount) {
                        val z = sphere.rotated[i][2]
                        if (z < -.55f && !hiddenSince[i]) {
                            hiddenSince[i] = true
                            assign[i] = nextAlbum
                            nextAlbum = (nextAlbum + 1) % currentAlbums.size
                        } else if (z > -.3f) hiddenSince[i] = false
                    }
                }
                tick.intValue++
                busy = moving || abs(goalCamera - sphere.camera) > .001f || sphere.reveal < 1f
                if (!moving && shownFront != sphere.front) {
                    shownFront = sphere.front
                    haptics.tick(HapticArea.SCRUB)
                }
                settled = !moving
            }
            if (!busy) break
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(top = topContentPadding, bottom = bottomContentPadding)) {
        val density = LocalDensity.current
        val stageHeight = maxHeight * .76f
        val minSidePx = with(density) { minOf(maxWidth, stageHeight).toPx() }
        val radiusPx = minSidePx * .5f / (SPHERE_ZOOM * (SPHERE_REST - 1f) / SPHERE_REST) * .96f
        val tilePx = radiusPx * sqrt(4f * PI.toFloat() / tileCount) * .78f
        val tileDp = with(density) { tilePx.toDp() }
        val veil = MaterialTheme.colorScheme.background
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(stageHeight)
                    .pointerInput(tileCount, radiusPx, tilePx, minSidePx) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            sphere.dragging = true
                            wake++
                            var moved = 0f
                            val start = down.uptimeMillis
                            var lastPos = down.position
                            var upPos = down.position
                            var upTime = start
                            var spun = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val d = change.position - lastPos
                                lastPos = change.position
                                if (!change.pressed) {
                                    upPos = change.position
                                    upTime = change.uptimeMillis
                                    break
                                }
                                moved += d.getDistance()
                                if (moved > viewConfiguration.touchSlop) {
                                    if (!spun) {
                                        spun = true
                                        sphere.target = -1
                                    }
                                    change.consume()
                                    sphere.dragX += d.x / minSidePx
                                    sphere.dragY += d.y / minSidePx
                                }
                            }
                            sphere.dragging = false
                            if (!spun && upTime - start < 500) {
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                var best = -1
                                var bestZ = -2f
                                for (i in 0 until sphere.count) {
                                    val c = sphere.rotated[i]
                                    if (c[2] < .15f) continue
                                    val at = sphereProject(c, sphere.camera, radiusPx)
                                    val half = tilePx * SPHERE_ZOOM * at.scale * .5f
                                    val halfX = half * sqrt(1f - c[0] * c[0]).coerceAtLeast(.3f)
                                    val halfY = half * sqrt(1f - c[1] * c[1]).coerceAtLeast(.3f)
                                    if (abs(upPos.x - (cx + at.x)) <= halfX && abs(upPos.y - (cy + at.y)) <= halfY && c[2] > bestZ) {
                                        bestZ = c[2]
                                        best = i
                                    }
                                }
                                when {
                                    best < 0 -> Unit
                                    best == sphere.front && acos(sphere.rotated[best][2].coerceIn(-1f, 1f)) < .12f -> openZoom(best)
                                    else -> {
                                        haptics.tick(HapticArea.MENUS)
                                        sphere.target = best
                                        sphere.speed = 0f
                                    }
                                }
                            }
                            wake++
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                for (i in 0 until tileCount) {
                    key(i) {
                        SphereTile(albumAt(i), i, sphere, tick, radiusPx, tileDp, veil, hidden = { zoomTile == i && zoom.value > 0f })
                    }
                }
            }
            val album = albumAt(sphere.front)
            val infoAlpha by androidx.compose.animation.core.animateFloatAsState(if (settled) 1f else 0f, tween(if (settled) 450 else 180), label = "sphere-info")
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp).graphicsLayer {
                    alpha = infoAlpha * (1f - zoom.value)
                    translationY = (1f - infoAlpha) * 12.dp.toPx()
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(album.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text(album.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text("轻点封面放大查看", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("播放", { onPlay(album) }, icon = Icons.Rounded.PlayArrow, enabled = settled, height = 40.dp)
                    PillButton("打开", { onAlbum(album) }, style = PillStyle.TONAL, enabled = settled, height = 40.dp)
                }
            }
        }
        if (zoomTile >= 0) {
            val lifted = albumAt(zoomTile)
            val bigDp = minOf(maxWidth * .74f, stageHeight * .8f)
            val bigPx = with(density) { bigDp.toPx() }
            val startScale = (tilePx * SPHERE_ZOOM / bigPx).coerceIn(.1f, 1f)
            val playing = currentTrackId != null && lifted.tracks.any { it.id == currentTrackId }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = zoom.value.coerceIn(0f, 1f) }
                    .background(veil.copy(alpha = .78f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = closeZoom),
            )
            Column(
                Modifier.fillMaxWidth().padding(top = (stageHeight - bigDp) / 2f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(bigDp)
                        .graphicsLayer {
                            val t = zoom.value
                            val s = startScale + (1f - startScale) * t
                            scaleX = s
                            scaleY = s
                            shadowElevation = 24.dp.toPx() * t.coerceIn(0f, 1f)
                            shape = RoundedCornerShape(bigDp * (.16f - .07f * t.coerceIn(0f, 1f)))
                            clip = true
                        }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            haptics.click(HapticArea.MENUS)
                            onAlbum(lifted)
                        },
                ) {
                    TrackArtwork(lifted.cover, Modifier.fillMaxSize().sharedAlbumCover(lifted.key, RoundedCornerShape(bigDp * .09f)), 0.dp, decodeEdge = 768)
                }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp).graphicsLayer {
                        val t = ((zoom.value - .35f) / .65f).coerceIn(0f, 1f)
                        alpha = t
                        translationY = (1f - t) * 18.dp.toPx()
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(lifted.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    Text(
                        listOfNotNull(
                            lifted.artist,
                            lifted.year.takeIf { it > 0 }?.toString(),
                            "${lifted.tracks.size} 首",
                            formatTotalDuration(lifted.tracks.sumOf { it.durationMs }),
                        ).joinToString(" · ") + if (playing) " · 正在播放" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("播放", { onPlay(lifted) }, icon = Icons.Rounded.PlayArrow, height = 40.dp)
                        if (onShuffle != null) PillButton("随机", { onShuffle(lifted) }, icon = Icons.Rounded.Shuffle, style = PillStyle.TONAL, height = 40.dp)
                        PillButton("打开专辑", { onAlbum(lifted) }, style = PillStyle.TONAL, height = 40.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SphereTile(
    album: Album,
    index: Int,
    sphere: SphereState,
    tick: androidx.compose.runtime.MutableIntState,
    radiusPx: Float,
    tileDp: Dp,
    veil: Color,
    hidden: () -> Boolean,
) {
    val sizePx = with(LocalDensity.current) { tileDp.toPx() } * SPHERE_ZOOM
    val sizeDp = tileDp * SPHERE_ZOOM
    val shape = RoundedCornerShape(sizeDp * .16f)
    Box(
        Modifier
            .size(sizeDp)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    tick.intValue
                    val c = sphere.rotated[index]
                    placeable.placeWithLayer(0, 0, zIndex = c[2]) {
                        if (c[2] < .02f || hidden()) {
                            alpha = 0f
                            return@placeWithLayer
                        }
                        val at = sphereProject(c, sphere.camera, radiusPx)
                        translationX = at.x
                        translationY = at.y
                        rotationY = Math.toDegrees(atan2(c[0], c[2]).toDouble()).toFloat()
                        rotationX = Math.toDegrees(asin(c[1].coerceIn(-1f, 1f)).toDouble()).toFloat()
                        cameraDistance = 12f * sizePx
                        scaleX = at.scale
                        scaleY = at.scale
                        alpha = (sphere.reveal * ((c[2] - .02f) / .2f).coerceIn(0f, 1f)).coerceIn(0f, 1f)
                        this.shape = shape
                        clip = true
                    }
                }
            }
            .drawWithContent {
                drawContent()
                tick.intValue
                val c = sphere.rotated[index]
                val focus = ((c[2] - .8f) / .2f).coerceIn(0f, 1f)
                val sink = (1f - focus) * .55f
                if (sink > .01f) drawRect(veil.copy(alpha = sink))
            },
    ) {
        TrackArtwork(album.cover, Modifier.fillMaxSize(), 0.dp, decodeEdge = 256)
    }
}

private const val SHRED_FOLLOW = 30f
private const val SHRED_SETTLE = 16f
private const val SHRED_TUG = 2.4f
private const val SHRED_TUG_DECAY = .14f

internal class ShredDrag(
    val key: Any,
    val layer: GraphicsLayer,
    val width: Float,
    val height: Float,
    val homeX: Float,
    val homeY: Float,
    val grabX: Float,
    val grabY: Float,
    val onShred: () -> Unit,
) {
    var x = homeX
    var y = homeY
    var vx = 0f
    var vy = 0f
    var fingerX = homeX + grabX
    var fingerY = homeY + grabY
    var tilt = 0f
    var lift = 1f
    var phase = 0
}

internal class ShredFeed(
    val key: Any,
    val layer: GraphicsLayer,
    val width: Float,
    val height: Float,
    var x: Float,
    var y: Float,
    val targetX: Float,
    var tilt: Float,
    var lift: Float,
    val onShred: () -> Unit,
) {
    var velocity = 0f
    var age = 0f
    var consumed = false
    var strips = 0
    var bitmap: android.graphics.Bitmap? = null
}

internal class ShredStrip(val feed: ShredFeed, val x: Float, val w: Float) {
    var free = false
    var curl = 0f
    var amp = 0f
    var freq = 0f
    var wave = 0f
    var rA = 0f
    var rB = 0f
    var speed = 0f
    var splay = 0f
    var rest = 0f
    var alpha = 1f
    var core = 1f
    var th = 0f
    var ax = 0f
    var ay = 0f
    var vx = 0f
    var vy = 0f
    var length = 0f
}

@Stable
internal class ShredderState(internal val scope: CoroutineScope) {
    internal var container: LayoutCoordinates? = null
    internal var drag: ShredDrag? = null
    internal val feeds = mutableListOf<ShredFeed>()
    internal val strips = mutableListOf<ShredStrip>()
    internal val tick = mutableIntStateOf(0)
    var hidden by mutableStateOf<Any?>(null)
        internal set
    internal var wake by mutableIntStateOf(0)
    internal var time = 0f
    internal var width = 0f
    internal var lip = 0f
    internal var slit = 0f
    internal var fall = 0f
    internal var bite = 0f
    internal var stripPx = 0f
    internal var sliverPx = 0f
    internal var feedSpeed = 0f
    internal var inset = 0f
    internal var px = 1f
    var dragging by mutableStateOf(false)
        internal set
    internal var heat by mutableFloatStateOf(0f)
}

@Composable
internal fun rememberShredderState(): ShredderState {
    val scope = rememberCoroutineScope()
    return remember(scope) { ShredderState(scope) }
}

internal fun Modifier.shreddable(state: ShredderState, key: Any, enabled: Boolean, onLongPress: (() -> Unit)? = null, onShred: () -> Unit): Modifier = composed {
    val layer = rememberGraphicsLayer()
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val haptics = rememberHaptics()
    val currentShred by rememberUpdatedState(onShred)
    val currentLongPress by rememberUpdatedState(onLongPress)
    val travel = remember { floatArrayOf(0f) }
    this
        .onGloballyPositioned { coordinates[0] = it }
        .graphicsLayer { alpha = if (state.hidden == key) 0f else 1f }
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }
        .then(
            if (!enabled) Modifier else Modifier.pointerInput(key) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val container = state.container
                        val own = coordinates[0]
                        if (container != null && own != null && own.isAttached && container.isAttached && state.drag == null) {
                            val bounds = container.localBoundingBoxOf(own, clipBounds = false)
                            haptics.heavy(HapticArea.GESTURES)
                            travel[0] = 0f
                            state.drag = ShredDrag(key, layer, bounds.width, bounds.height, bounds.left, bounds.top, offset.x, offset.y) { currentShred() }
                            state.hidden = key
                            state.dragging = true
                            state.wake++
                        }
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        travel[0] += amount.getDistance()
                        state.drag?.let {
                            it.fingerX += amount.x
                            it.fingerY += amount.y
                        }
                    },
                    onDragEnd = {
                        val menu = currentLongPress
                        if (menu != null && travel[0] < 14.dp.toPx()) {
                            releaseShred(state, cancelled = true)
                            menu()
                        } else releaseShred(state, cancelled = false)
                    },
                    onDragCancel = { releaseShred(state, cancelled = true) },
                )
            },
        )
}

private fun releaseShred(state: ShredderState, cancelled: Boolean) {
    val d = state.drag ?: return
    state.dragging = false
    d.phase = if (!cancelled && d.y + d.height > state.lip - d.height * .6f) 1 else 2
    state.wake++
}

private fun spring1(x: Float, v: Float, target: Float, dt: Float, omega: Float): Pair<Float, Float> {
    val a = omega * omega * (target - x) - 2f * omega * v
    val nv = v + a * dt
    return (x + nv * dt) to nv
}

private fun easeTo(value: Float, target: Float, dt: Float, tau: Float) = value + (target - value) * (1f - exp(-dt / tau))

private fun smooth01(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

@Composable
internal fun ShredderLayer(state: ShredderState, modifier: Modifier = Modifier, fall: Dp = 104.dp, slit: Dp = 4.dp) {
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    val reduce = LocalReduceMotion.current
    val ink = MaterialTheme.colorScheme.onSurface
    with(density) {
        state.fall = fall.toPx()
        state.slit = slit.toPx()
        state.bite = 14.dp.toPx()
        state.stripPx = 10.dp.toPx()
        state.sliverPx = 4.dp.toPx()
        state.feedSpeed = 170.dp.toPx()
        state.inset = 18.dp.toPx()
        state.px = this.density
    }
    LaunchedEffect(state.wake) {
        if (state.wake == 0) return@LaunchedEffect
        var last = 0L
        while (true) {
            var busy = false
            withFrameNanos { now ->
                val dt = if (last == 0L) 1f / 60f else ((now - last) / 1e9f).coerceIn(0f, .05f)
                last = now
                state.time += dt
                busy = stepShredder(state, dt, reduce) { haptics.click(HapticArea.GESTURES) }
                state.tick.intValue++
            }
            if (!busy) break
        }
    }
    Box(
        modifier
            .onGloballyPositioned {
                state.container = it
                state.width = it.size.width.toFloat()
                state.lip = it.size.height - state.fall - state.slit
            }
            .drawWithContent {
                drawContent()
                state.tick.intValue
                drawShredder(state, ink)
            },
    ) {
        val hint by androidx.compose.animation.core.animateFloatAsState(if (state.dragging) 1f else 0f, tween(200), label = "shred-hint")
        Text(
            "拖进这里移出队列",
            Modifier.align(Alignment.BottomCenter).padding(bottom = fall + slit + 8.dp).graphicsLayer { alpha = hint * .7f },
            style = MaterialTheme.typography.labelMedium,
            color = ink.copy(alpha = .6f),
        )
    }
}

private fun stepShredder(state: ShredderState, dt: Float, reduce: Boolean, onBite: () -> Unit): Boolean {
    var moving = false
    val d = state.drag
    if (d != null) {
        val ceiling = state.lip + state.bite - d.height
        val (tx, ty) = when (d.phase) {
            0 -> (d.fingerX - d.grabX) to min(d.fingerY - d.grabY, ceiling)
            1 -> ((state.width - d.width) / 2f) to ceiling
            else -> d.homeX to d.homeY
        }
        val omega = if (d.phase == 0) SHRED_FOLLOW else SHRED_SETTLE
        spring1(d.x, d.vx, tx, dt, omega).let { d.x = it.first; d.vx = it.second }
        spring1(d.y, d.vy, ty, dt, omega).let { d.y = it.first; d.vy = it.second }
        val lean = if (d.phase == 0) (d.vx * .012f / state.px).coerceIn(-4f, 4f) else 0f
        d.tilt = easeTo(d.tilt, lean, dt, .09f)
        d.lift = easeTo(d.lift, if (d.phase == 2) 1f else 1.03f, dt, .12f)
        state.heat = easeTo(state.heat, if (d.phase != 2) ((d.y + d.height - (state.lip - d.height)) / d.height).coerceIn(0f, 1f) else 0f, dt, .08f)
        val over = d.y + d.height - state.lip
        if (d.phase != 2 && over >= state.bite - .5f) {
            onBite()
            state.drag = null
            state.dragging = false
            if (reduce) {
                state.hidden = d.key
                d.onShred()
            } else {
                val lo = state.inset - 6f * state.px
                val hi = state.width - state.inset + 6f * state.px - d.width
                val feed = ShredFeed(d.key, d.layer, d.width, d.height, d.x, d.y, d.x.coerceIn(min(lo, hi), max(lo, hi)), d.tilt, d.lift, d.onShred)
                feed.velocity = state.feedSpeed * SHRED_TUG
                val n = max(1, (d.width / state.stripPx).toInt())
                val sw = d.width / n
                feed.strips = n
                for (i in 0 until n) {
                    state.strips += ShredStrip(feed, i * sw, if (i == n - 1) d.width - sw * (n - 1) else sw).apply {
                        val side = if (Math.random() < .5) -1f else 1f
                        curl = side * (.35f + .55f * Math.random().toFloat()) * (PI.toFloat() / 180f) / state.px
                        amp = (1.5f + 2.5f * Math.random().toFloat()) * (PI.toFloat() / 180f)
                        freq = (2f * PI.toFloat()) / ((34f + 26f * Math.random().toFloat()) * state.px)
                        wave = (Math.random() * 2 * PI).toFloat()
                        rA = Math.random().toFloat()
                        rB = Math.random().toFloat()
                    }
                }
                state.feeds += feed
                state.scope.launch {
                    runCatching { feed.bitmap = d.layer.toImageBitmap().asAndroidBitmap() }
                }
            }
        } else if (d.phase == 2 && abs(d.x - d.homeX) < .3f && abs(d.y - d.homeY) < .3f && abs(d.vx) < 3f && abs(d.vy) < 3f) {
            state.drag = null
            state.hidden = null
        }
        moving = true
    } else {
        state.heat = easeTo(state.heat, 0f, dt, .12f)
        if (state.heat > .01f) moving = true
    }
    val iterator = state.feeds.iterator()
    while (iterator.hasNext()) {
        val f = iterator.next()
        if (!f.consumed) {
            f.age += dt
            f.velocity = state.feedSpeed * (1f + (SHRED_TUG - 1f) * exp(-f.age / SHRED_TUG_DECAY))
            f.y += f.velocity * dt
            f.x = easeTo(f.x, f.targetX, dt, .1f)
            f.tilt = easeTo(f.tilt, 0f, dt, .08f)
            f.lift = easeTo(f.lift, 1f, dt, .1f)
            if (f.y >= state.lip) {
                f.consumed = true
                f.onShred()
            }
            moving = true
        } else {
            f.y += f.velocity * dt
            if (f.strips <= 0) iterator.remove()
        }
    }
    if (state.drag == null && state.feeds.isEmpty() && state.hidden != null) state.hidden = null
    val exit = state.lip + state.slit
    val stripIterator = state.strips.iterator()
    while (stripIterator.hasNext()) {
        val st = stripIterator.next()
        val f = st.feed
        if (!st.free) {
            val top = f.y - exit
            val hang = top + f.height
            st.ax = f.x + st.x + st.w / 2f
            st.th = sin(state.time * 6f + st.wave) * .03f * smooth01(hang / (40f * state.px))
            if (hang <= 0f) {
                st.length = 0f
                moving = true
                continue
            }
            if (top >= 0f) {
                st.free = true
                st.ay = top
                st.vy = f.velocity
                st.vx = (-20f + 40f * Math.random().toFloat()) * state.px
                st.speed = (150f + st.rA * 230f) * state.px
                st.splay = (st.rA - .5f) * 320f * state.px
                st.rest = (if (Math.random() < .5) -1f else 1f) * (.15f + .45f * Math.random().toFloat())
                f.strips -= 1
            } else {
                st.ay = 0f
                st.length = hang
            }
        }
        if (st.free) {
            val tear = ((st.ay + f.height / 2f) / state.fall).coerceIn(0f, 1f).pow(1.2f)
            val breath = .85f + .15f * sin(state.time * (1f + st.rB * 2f) + st.rA * 6.2832f)
            st.vy = easeTo(st.vy, st.speed * breath, dt, .25f)
            st.vx = easeTo(st.vx, st.splay * tear + (st.ax - state.width / 2f) * .25f, dt, .4f)
            st.ax += st.vx * dt
            st.ay += st.vy * dt
            st.th = easeTo(st.th, st.rest, dt, .5f)
            st.core = 1f - smooth01(tear / .85f) * (.8f - st.rA * .16f)
            st.alpha = 1f - smooth01((tear - .6f) / .4f)
            st.length = f.height
            if (st.alpha <= .01f || st.ay > state.fall) {
                stripIterator.remove()
                continue
            }
        } else {
            st.core = 1f
            st.alpha = 1f
        }
        moving = true
    }
    return moving
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShredder(state: ShredderState, ink: Color) {
    if (state.width <= 0f) return
    val pulling = state.feeds.any { !it.consumed }
    val jx = if (pulling) sin(state.time * 140f) * .5f * state.px else 0f
    val jy = if (pulling) cos(state.time * 97f) * .35f * state.px else 0f
    val glow = state.heat
    val slitInset = state.inset - 6f * state.px
    drawRoundRect(
        ink.copy(alpha = .22f + .3f * glow),
        topLeft = Offset(slitInset + jx, state.lip + jy),
        size = Size(state.width - slitInset * 2f, state.slit),
        cornerRadius = CornerRadius(state.slit / 2f),
    )
    state.drag?.let { d -> drawGhost(d.layer, d.x, d.y, d.width, d.height, d.tilt, d.lift, clipBottom = null) }
    state.feeds.forEach { f ->
        if (!f.consumed) {
            val jitter = sin(state.time * 150f) * .5f * state.px
            drawGhost(f.layer, f.x + jitter, f.y, f.width, f.height, f.tilt, f.lift, clipBottom = state.lip)
        }
    }
    if (state.strips.isEmpty()) return
    val exit = state.lip + state.slit
    clipRect(0f, exit, size.width, exit + state.fall) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
            val src = android.graphics.Rect()
            val dst = android.graphics.RectF()
            state.strips.forEach { st ->
                val f = st.feed
                val len = if (st.free) f.height else st.length
                if (len <= 0f) return@forEach
                val bitmap = f.bitmap
                val scaleX = if (bitmap != null) bitmap.width / f.width else 1f
                val scaleY = if (bitmap != null) bitmap.height / f.height else 1f
                val wc = st.w * st.core
                val half = wc / 2f
                val v0 = f.height - len
                paint.alpha = (st.alpha * 255).toInt().coerceIn(0, 255)
                var x = st.ax
                var y = st.ay
                var d = 0f
                val tear = if (st.free) ((st.ay + f.height / 2f) / state.fall).coerceIn(0f, 1f).pow(1.2f) else 0f
                val swing = (3f + 9f * st.rA) * tear * state.px
                val pace = state.time * (1.6f + st.rA * 2.2f) + st.rA * 6.2832f
                while (d < len) {
                    val h = min(state.sliverPx, len - d)
                    val a = st.th + st.curl * d + st.amp * sin(d * st.freq + st.wave)
                    val flutter = swing * sin(d * .045f / state.px + pace)
                    val px = x + flutter * cos(a)
                    val py = y - flutter * sin(a)
                    native.save()
                    native.translate(px, exit + py)
                    native.rotate(-Math.toDegrees(a.toDouble()).toFloat())
                    dst.set(-half, 0f, half, h + .6f)
                    if (bitmap != null) {
                        src.set(
                            ((st.x + (st.w - wc) / 2f) * scaleX).toInt(),
                            ((v0 + d) * scaleY).toInt(),
                            ((st.x + (st.w + wc) / 2f) * scaleX).toInt(),
                            ((v0 + d + h) * scaleY).toInt().coerceAtLeast(((v0 + d) * scaleY).toInt() + 1),
                        )
                        native.drawBitmap(bitmap, src, dst, paint)
                    } else {
                        paint.color = android.graphics.Color.argb(paint.alpha, 128, 128, 128)
                        native.drawRect(dst, paint)
                    }
                    native.restore()
                    x += sin(a) * h
                    y += cos(a) * h
                    d += h
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGhost(
    layer: GraphicsLayer,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    tilt: Float,
    lift: Float,
    clipBottom: Float?,
) {
    val draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
        translate(x, y) {
            rotate(tilt, pivot = Offset(width / 2f, height / 2f)) {
                scale(lift, lift, pivot = Offset(width / 2f, height / 2f)) {
                    drawLayer(layer)
                }
            }
        }
    }
    if (clipBottom != null) clipRect(0f, -100_000f, size.width, clipBottom) { draw() } else draw()
}
