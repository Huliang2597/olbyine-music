package com.olbyine.music.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.sqrt

@Stable
internal class SpringValue(initial: Float, private val scope: CoroutineScope) {
    var value by mutableFloatStateOf(initial)
        private set
    var target by mutableFloatStateOf(initial)
        private set
    var dragging by mutableStateOf(false)
        private set
    private var job: Job? = null

    fun drag(to: Float) {
        job?.cancel()
        job = null
        dragging = true
        value = to
        target = to
    }

    fun settle(to: Float, velocity: Float = 0f, stiffness: Float = 380f, onEnd: () -> Unit = {}) {
        job?.cancel()
        dragging = false
        target = to
        val start = value
        job = scope.launch {
            animate(
                initialValue = start,
                targetValue = to,
                initialVelocity = velocity,
                animationSpec = spring(dampingRatio = 1f, stiffness = stiffness, visibilityThreshold = 0.0005f),
            ) { current, _ -> value = current }
            value = to
            onEnd()
        }
    }

    fun snap(to: Float) {
        job?.cancel()
        job = null
        dragging = false
        value = to
        target = to
    }
}

@Composable
internal fun rememberSpringValue(initial: Float): SpringValue {
    val scope = rememberCoroutineScope()
    return remember { SpringValue(initial, scope) }
}

internal class GestureExclusions {
    private val items = mutableSetOf<LayoutCoordinates>()
    fun add(coordinates: LayoutCoordinates) { items += coordinates }
    fun remove(coordinates: LayoutCoordinates) { items -= coordinates }
    fun contains(rootPoint: Offset): Boolean =
        items.any { it.isAttached && it.boundsInRoot().contains(rootPoint) }
}

internal val LocalGestureExclusions = staticCompositionLocalOf<GestureExclusions?> { null }

internal fun Modifier.excludeFromSheetGestures(): Modifier = composed {
    val registry = LocalGestureExclusions.current ?: return@composed this
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    DisposableEffect(registry) {
        onDispose { holder[0]?.let(registry::remove); holder[0] = null }
    }
    onGloballyPositioned { coordinates ->
        if (holder[0] !== coordinates) {
            holder[0]?.let(registry::remove)
            holder[0] = coordinates
            registry.add(coordinates)
        }
    }
}

@Stable
internal class SharedArtworkState {
    internal var pageCoordinates: LayoutCoordinates? = null
    var bounds by mutableStateOf<Rect?>(null)
    var scale: State<Float> = mutableFloatStateOf(1f)
    var transitioning: () -> Boolean = { false }
}

private enum class DragAxis { UNDECIDED, SHEET, PAGER, REJECTED }

@Composable
internal fun PlayerSheetHost(
    sheet: SpringValue,
    pager: SpringValue,
    collapsedBounds: () -> Rect,
    lyricsAvailable: Boolean,
    shared: SharedArtworkState,
    onSheetReleased: (open: Boolean, velocity: Float) -> Unit,
    onPagerReleased: (page: Int, velocity: Float) -> Unit,
    background: @Composable () -> Unit,
    miniRow: @Composable () -> Unit,
    artwork: (@Composable () -> Unit)?,
    playerPage: @Composable () -> Unit,
    lyricsPage: (@Composable () -> Unit)?,
    infoPage: (@Composable () -> Unit)? = null,
    overlay: @Composable (BackdropSource) -> Unit = {},
    gesturesEnabled: () -> Boolean = { true },
    split: Boolean = false,
) {
    val exclusions = remember { GestureExclusions() }
    val contentSource = rememberBackdropSource()
    val hostCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val currentBounds by rememberUpdatedState(collapsedBounds)
    val currentLyricsAvailable by rememberUpdatedState(lyricsAvailable)
    val currentInfoAvailable by rememberUpdatedState(infoPage != null)
    val currentSheetReleased by rememberUpdatedState(onSheetReleased)
    val currentPagerReleased by rememberUpdatedState(onPagerReleased)
    val currentGesturesEnabled by rememberUpdatedState(gesturesEnabled)
    val currentSplit by rememberUpdatedState(split)
    val density = LocalDensity.current
    val miniCorner = with(density) { MiniCorner.toPx() }
    val miniArtInset = with(density) { MiniArtInset.toPx() }
    val miniArtSize = with(density) { MiniArtSize.toPx() }
    val miniArtCorner = with(density) { OlbyineTokens.Corner.thumb.toPx() }
    val fullArtCorner = with(density) { OlbyineTokens.Corner.artwork.toPx() }
    shared.transitioning = { sheet.value < 0.999f || sheet.dragging }
    var hostSize by remember { mutableStateOf(Size.Zero) }
    val edgePull = remember { mutableFloatStateOf(0f) }
    val edgeScope = rememberCoroutineScope()
    val edgeJob = remember { arrayOfNulls<Job>(1) }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                hostCoordinates[0] = it
                hostSize = Size(it.size.width.toFloat(), it.size.height.toFloat())
            }
            .pointerInput(Unit) {
                val topRegion = 120.dp.toPx()
                val flingVelocity = 700.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val rootPoint = hostCoordinates[0]?.takeIf { it.isAttached }?.localToRoot(down.position) ?: down.position
                    if (exclusions.contains(rootPoint)) return@awaitEachGesture
                    if (!currentGesturesEnabled()) return@awaitEachGesture
                    val page = kotlin.math.round(pager.value).toInt().coerceIn(-1, 1)
                    val offPlayer = page != 0 || (currentSplit && down.position.x > size.width / 2f)
                    val fromTop = down.position.y < topRegion
                    val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                    var axis = DragAxis.UNDECIDED
                    var total = Offset.Zero
                    var claimedAt = Offset.Zero
                    var startValue = 0f
                    var towardMissing = false
                    var edgeDx = 0f
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed || event.changes.count { it.pressed } > 1) break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        total += change.position - change.previousPosition
                        when (axis) {
                            DragAxis.UNDECIDED -> if (total.getDistance() > viewConfiguration.touchSlop) {
                                val horizontal = abs(total.x) > abs(total.y) * 1.15f
                                val vertical = abs(total.y) > abs(total.x) * 1.15f
                                axis = when {
                                    horizontal && page == 0 && total.x < 0f && currentLyricsAvailable -> DragAxis.PAGER
                                    horizontal && page == 0 && total.x > 0f && currentInfoAvailable -> DragAxis.PAGER
                                    horizontal && page == 1 && total.x > 0f -> DragAxis.PAGER
                                    horizontal && page == -1 && total.x < 0f -> DragAxis.PAGER
                                    vertical && total.y > 0f && (!offPlayer || fromTop) -> DragAxis.SHEET
                                    horizontal && page == 0 && !currentSplit -> {
                                        towardMissing = true
                                        edgeJob[0]?.cancel()
                                        DragAxis.PAGER
                                    }
                                    else -> DragAxis.REJECTED
                                }
                                if (axis == DragAxis.REJECTED) break
                                claimedAt = total
                                startValue = if (axis == DragAxis.SHEET) sheet.value else pager.value
                                change.consume()
                            }
                            DragAxis.SHEET -> {
                                val dy = total.y - claimedAt.y
                                val anchor = currentBounds().top.coerceAtLeast(1f)
                                sheet.drag((startValue - dy / anchor).coerceIn(0f, 1f))
                                change.consume()
                            }
                            DragAxis.PAGER -> {
                                val dx = total.x - claimedAt.x
                                val low = if (page == 0 && currentInfoAvailable) -1f else page.coerceAtLeast(0).toFloat()
                                val high = if (page == 0 && currentLyricsAvailable) 1f else page.coerceAtMost(0).toFloat()
                                if (towardMissing) {
                                    edgeDx = dx
                                    edgePull.floatValue = rubberband(dx, size.width * .3f)
                                } else {
                                    val range = if (page == 0) low..high else minOf(page, 0).toFloat()..maxOf(page, 0).toFloat()
                                    pager.drag((startValue - dx / size.width.coerceAtLeast(1)).coerceIn(range))
                                }
                                change.consume()
                            }
                            DragAxis.REJECTED -> Unit
                        }
                    }
                    val velocity = tracker.calculateVelocity()
                    when (axis) {
                        DragAxis.SHEET -> {
                            val close = velocity.y > flingVelocity ||
                                (sheet.value < 0.74f && velocity.y > -flingVelocity / 3f)
                            currentSheetReleased(!close, -velocity.y / currentBounds().top.coerceAtLeast(1f))
                        }
                        DragAxis.PAGER -> if (towardMissing) {
                            val start = edgePull.floatValue
                            val handoff = velocity.x * rubberbandSlope(edgeDx, size.width * .3f)
                            edgeJob[0] = edgeScope.launch {
                                animate(start, 0f, initialVelocity = handoff, animationSpec = appleSpring(response = .35f)) { value, _ ->
                                    edgePull.floatValue = value
                                }
                            }
                        } else {
                            val perSecond = -velocity.x / size.width.coerceAtLeast(1)
                            val moved = pager.value - page
                            val direction = if (moved >= 0f) 1 else -1
                            val flung = if (direction > 0) velocity.x < -flingVelocity else velocity.x > flingVelocity
                            val flungBack = if (direction > 0) velocity.x > flingVelocity / 3f else velocity.x < -flingVelocity / 3f
                            val target = if (flung || (abs(moved) > 0.35f && !flungBack)) page + direction else page
                            currentPagerReleased(target.coerceIn(-1, 1), perSecond)
                        }
                        else -> Unit
                    }
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = sheet.value.coerceIn(0f, 1f)
                    val rect = cardRect(p, currentBounds(), size)
                    shape = CardShape(rect, miniCorner * sqrt(1f - p))
                    clip = true
                    alpha = (p / 0.08f).coerceIn(0f, 1f)
                }
                .background(Color(0xFF0B0B0E)),
        ) {
            androidx.compose.runtime.CompositionLocalProvider(LocalGestureExclusions provides exclusions) {
                val pagesLayer = Modifier.fillMaxSize().graphicsLayer {
                    val p = sheet.value.coerceIn(0f, 1f)
                    translationY = cardRect(p, currentBounds(), size).top
                    alpha = ((p - 0.28f) / 0.5f).coerceIn(0f, 1f)
                }
                Box(Modifier.fillMaxSize().backdropSource(contentSource)) {
                background()
                Box(pagesLayer) {
                Box(if (split) Modifier.fillMaxHeight().fillMaxWidth(.5f).clipToBounds() else Modifier.fillMaxSize()) {
                    if (infoPage != null) {
                        Box(
                            Modifier.fillMaxSize().graphicsLayer {
                                val q = (-pager.value).coerceIn(0f, 1f)
                                translationX = -(1f - q) * size.width * 0.3f
                                val scale = 0.94f + 0.06f * q
                                scaleX = scale
                                scaleY = scale
                                alpha = q
                            },
                        ) { infoPage() }
                    }
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            val q = pager.value.coerceIn(-1f, 1f)
                            translationX = -q * size.width + edgePull.floatValue
                            alpha = if (abs(q) >= .999f && !pager.dragging) 0f else 1f - abs(q) * 0.75f
                        },
                    ) { playerPage() }
                }
                }
                }
                if (lyricsPage != null) {
                    Box(pagesLayer) {
                        Box(
                            if (split) Modifier.fillMaxHeight().fillMaxWidth(.5f).align(Alignment.CenterEnd)
                            else Modifier.fillMaxSize().graphicsLayer {
                                val q = pager.value.coerceIn(0f, 1f)
                                translationX = (1f - q) * size.width
                                alpha = if (q <= .001f && !pager.dragging) 0f else 0.25f + 0.75f * q
                            },
                        ) { lyricsPage() }
                    }
                }
                overlay(contentSource)
            }
            val bounds = collapsedBounds()
            Box(
                Modifier
                    .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                    .graphicsLayer {
                        val p = sheet.value.coerceIn(0f, 1f)
                        val rect = cardRect(p, currentBounds(), hostSize)
                        translationX = rect.left
                        translationY = rect.top
                        alpha = (1f - p / 0.32f).coerceIn(0f, 1f)
                    },
            ) { miniRow() }
            val full = shared.bounds
            if (artwork != null && full != null && full.width > 0f) {
                Box(
                    Modifier
                        .size(with(density) { full.width.toDp() }, with(density) { full.height.toDp() })
                        .graphicsLayer {
                            val p = sheet.value.coerceIn(0f, 1f)
                            val visible = (sheet.value < 0.999f || sheet.dragging) && abs(pager.value) < 0.5f
                            if (!visible) {
                                alpha = 0f
                                return@graphicsLayer
                            }
                            val mini = currentBounds()
                            val cardTop = cardRect(p, mini, hostSize).top
                            val coverScale = shared.scale.value
                            val targetWidth = full.width * coverScale
                            val targetLeft = full.center.x - targetWidth / 2f
                            val targetTop = full.center.y - full.height * coverScale / 2f + cardTop
                            val startLeft = mini.left + miniArtInset
                            val startTop = mini.center.y - miniArtSize / 2f
                            val width = lerpFloat(miniArtSize, targetWidth, p)
                            val scaleValue = width / full.width
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = scaleValue
                            scaleY = scaleValue
                            translationX = lerpFloat(startLeft, targetLeft, p)
                            translationY = lerpFloat(startTop, targetTop, p)
                            val corner = lerpFloat(miniArtCorner, fullArtCorner, p) / scaleValue
                            shape = RoundedCornerShape(corner)
                            clip = true
                            alpha = (1f - abs(pager.value) * 2f).coerceIn(0f, 1f)
                        },
                ) { artwork() }
            }
        }
    }
}

private fun lerpFloat(start: Float, end: Float, fraction: Float) = start + (end - start) * fraction

private fun cardRect(p: Float, mini: Rect, full: Size) = Rect(
    left = lerpFloat(mini.left, 0f, p),
    top = lerpFloat(mini.top, 0f, p),
    right = lerpFloat(mini.right, full.width, p),
    bottom = lerpFloat(mini.bottom, full.height, p),
)

private class CardShape(private val rect: Rect, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val corner = CornerRadius(radius.coerceAtMost(rect.height / 2f).coerceAtLeast(0f))
        return Outline.Rounded(RoundRect(rect, corner))
    }
}
