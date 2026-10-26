package com.olbyine.music.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class TouchOrigin {
    var last: Offset = Offset.Unspecified
    var bounds: androidx.compose.ui.geometry.Rect? = null
    var boundsAt: Long = 0L

    fun press(rect: androidx.compose.ui.geometry.Rect) {
        bounds = rect
        boundsAt = android.os.SystemClock.uptimeMillis()
    }

    fun recentBounds(): androidx.compose.ui.geometry.Rect? =
        bounds?.takeIf { android.os.SystemClock.uptimeMillis() - boundsAt < 900 && it.width > 4f && it.height > 4f }
}

internal val AppTouchOrigin = TouchOrigin()

internal val LocalOverlayMiniInset = staticCompositionLocalOf { 0.dp }

internal fun Modifier.trackTouches(origin: TouchOrigin): Modifier = pointerInput(origin) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { change -> if (change.pressed && !change.previousPressed) origin.last = change.position }
        }
    }
}

private var nextPageId = 0L

internal class PageEntry<T>(val route: T, val origin: Offset?, val rect: androidx.compose.ui.geometry.Rect? = null) {
    val id = nextPageId++
    val progress = Animatable(0f)
    var leaving by mutableStateOf(false)
    var gesture by mutableFloatStateOf(0f)
    var edge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)
}

@Stable
internal class PageStackState<T : Any> {
    internal val entries = mutableStateListOf<PageEntry<T>>()
    val top: T? get() = entries.lastOrNull { !it.leaving }?.route
    val isEmpty: Boolean get() = top == null
    val size: Int get() = entries.count { !it.leaving }

    fun push(route: T, origin: Offset? = null) {
        if (top == route) return
        entries += PageEntry(route, origin?.takeIf { it.isSpecified })
    }

    fun pushFromTouch(route: T) {
        if (top == route) return
        val rect = AppTouchOrigin.recentBounds()
        entries += PageEntry(route, AppTouchOrigin.last.takeIf { it.isSpecified }, rect)
    }

    fun pop() {
        entries.lastOrNull { !it.leaving }?.leaving = true
    }

    fun clear() {
        val visible = entries.lastOrNull { !it.leaving }
        entries.removeAll { it !== visible && !it.leaving }
        visible?.leaving = true
    }

    internal fun remove(entry: PageEntry<T>) {
        entries.remove(entry)
    }
}

@Composable
internal fun <T : Any> rememberPageStack(): PageStackState<T> = remember { PageStackState() }

@Composable
internal fun <T : Any> PageStack(
    state: PageStackState<T>,
    backEnabled: Boolean,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    base: (@Composable () -> Unit)? = null,
    page: @Composable (T) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var position by remember { mutableStateOf(Offset.Zero) }
    val reduceMotion = LocalReduceMotion.current
    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { position = it.positionInRoot() }) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val entries = state.entries
        fun coverAbove(index: Int): Float {
            val above = entries.getOrNull(index + 1) ?: return 0f
            return (above.progress.value * (1f - above.gesture * .6f)).coerceIn(0f, 1f)
        }
        fun pivotAbove(index: Int, density: Float): Offset? =
            entries.getOrNull(index + 1)?.let { revealRect(it, density) }?.translate(-position.x, -position.y)?.center
        if (base != null) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        val t = LayerTransform()
                        if (!reduceMotion) sinkBack(t, coverAbove(-1), pivotAbove(-1, density), w, h)
                        place(t)
                    }
                    .drawWithContent {
                        drawContent()
                        val cover = coverAbove(-1)
                        if (cover > 0f) drawRect(Color.Black.copy(alpha = .22f * cover))
                    },
            ) { base() }
        } else {
            Box(
                Modifier.fillMaxSize().drawWithContent {
                    val cover = coverAbove(-1)
                    if (cover > 0f) drawRect(Color.Black.copy(alpha = .2f * cover))
                },
            )
        }
        entries.forEachIndexed { index, entry ->
            key(entry.id) {
                LaunchedEffect(entry.leaving) {
                    if (entry.leaving) {
                        entry.progress.animateTo(0f, if (reduceMotion) tween(150) else appleSpring(.32f, 1f, .02f))
                        state.remove(entry)
                    } else {
                        entry.progress.animateTo(1f, if (reduceMotion) tween(150) else appleSpring(.38f, 1f, .001f))
                    }
                }
                val buried = index < entries.size - 2 && !entry.leaving
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            if (buried && coverAbove(index) >= .999f) {
                                alpha = 0f
                                return@graphicsLayer
                            }
                            val t = pageTransform(entry, w, h, position, reduceMotion)
                            if (!reduceMotion) sinkBack(t, coverAbove(index), pivotAbove(index, density), w, h)
                            place(t)
                        }
                        .drawWithContent {
                            drawContent()
                            val cover = coverAbove(index)
                            if (cover > 0f) drawRect(Color.Black.copy(alpha = .22f * cover))
                        },
                ) { page(entry.route) }
            }
        }
    }
    key(state.entries.lastOrNull { !it.leaving }?.id) { PredictiveBackHandler(enabled = backEnabled && !state.isEmpty) { events ->
        val entry = state.entries.lastOrNull { !it.leaving } ?: return@PredictiveBackHandler
        try {
            events.collect { event ->
                entry.edge = event.swipeEdge
                entry.gesture = event.progress
            }
            if (onBack != null) onBack() else state.pop()
        } catch (cancelled: CancellationException) {
            scope.launch {
                val back = Animatable(entry.gesture)
                back.animateTo(0f, appleSpring(.30f)) { entry.gesture = value }
            }
        }
    } }
}

private class LayerTransform(var scale: Float = 1f, var x: Float = 0f, var y: Float = 0f) {
    fun scaleAbout(k: Float, px: Float, py: Float) {
        x = px + (x - px) * k
        y = py + (y - py) * k
        scale *= k
    }
}

private fun GraphicsLayerScope.place(t: LayerTransform) {
    transformOrigin = TransformOrigin(0f, 0f)
    scaleX = t.scale
    scaleY = t.scale
    translationX = t.x
    translationY = t.y
}

private fun revealRect(entry: PageEntry<*>, density: Float): androidx.compose.ui.geometry.Rect? =
    entry.rect ?: entry.origin?.let { androidx.compose.ui.geometry.Rect(it, 32f * density) }

private fun <T> GraphicsLayerScope.pageTransform(
    entry: PageEntry<T>,
    w: Float,
    h: Float,
    position: Offset,
    reduceMotion: Boolean,
): LayerTransform {
    val p = entry.progress.value.coerceIn(0f, 1f)
    val t = LayerTransform()
    if (reduceMotion) {
        alpha = p
        return t
    }
    val g = entry.gesture
    val towardEdge = if (entry.edge == BackEventCompat.EDGE_LEFT) 1f else -1f
    val card = entry.rect?.translate(-position.x, -position.y)
    val point = entry.origin?.let { it - position }
    if (card != null || point != null) {
        val window: androidx.compose.ui.geometry.Rect
        val centre: Offset
        val startRadius: Float
        if (card != null) {
            val left = card.left * (1f - p)
            val top = card.top * (1f - p)
            val width = (card.width + (w - card.width) * p).coerceAtLeast(1f)
            val height = (card.height + (h - card.height) * p).coerceAtLeast(1f)
            val s = maxOf(width / w, height / h)
            val clipWidth = width / s
            val cropX = (w - clipWidth) / 2f
            t.scale = s
            t.x = left - cropX * s
            t.y = top
            window = androidx.compose.ui.geometry.Rect(cropX, 0f, cropX + clipWidth, height / s)
            centre = Offset(left + width / 2f, top + height / 2f)
            startRadius = if (card.width > w * .6f) 0f else 12f * density
        } else {
            val at = point!!
            val s = .94f + .06f * p
            t.scale = s
            t.x = at.x * (1f - s)
            t.y = at.y * (1f - s)
            val box = androidx.compose.ui.geometry.Rect(at, 32f * density)
            window = androidx.compose.ui.geometry.Rect(
                box.left * (1f - p),
                box.top * (1f - p),
                box.right + (w - box.right) * p,
                box.bottom + (h - box.bottom) * p,
            )
            centre = Offset(t.x + window.center.x * s, t.y + window.center.y * s)
            startRadius = 20f * density
        }
        val pull = g * p
        if (pull > 0f) {
            t.scaleAbout(1f - .1f * pull, centre.x, centre.y)
            t.x += towardEdge * w * .04f * pull
        }
        val screenRadius = startRadius * (1f - p) + 28f * density * pull
        shape = RevealShape(window.left, window.top, window.right, window.bottom, screenRadius / t.scale)
        clip = p < .999f || pull > 0f
        alpha = ((p - .04f) / .30f).coerceIn(0f, 1f)
        return t
    }
    t.x = (1f - p) * w
    if (g > 0f) {
        t.scaleAbout(1f - .1f * g, t.x + w / 2f, h / 2f)
        t.x += towardEdge * w * .05f * g
        shape = RoundedCornerShape((30f * g).dp)
        clip = true
    }
    return t
}

private fun sinkBack(t: LayerTransform, cover: Float, pivot: Offset?, w: Float, h: Float) {
    if (cover <= 0f) return
    if (pivot != null) {
        t.scaleAbout(1f - .06f * cover, pivot.x, pivot.y)
    } else {
        t.scaleAbout(1f - .05f * cover, w / 2f, h / 2f)
        t.x -= w * .03f * cover
    }
}

private class RevealShape(val l: Float, val t: Float, val r: Float, val b: Float, val radius: Float) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density) =
        androidx.compose.ui.graphics.Outline.Rounded(androidx.compose.ui.geometry.RoundRect(l, t, r.coerceAtLeast(l + 1f), b.coerceAtLeast(t + 1f), androidx.compose.ui.geometry.CornerRadius(radius)))
}
