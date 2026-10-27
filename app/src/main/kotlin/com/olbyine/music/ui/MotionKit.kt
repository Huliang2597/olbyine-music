package com.olbyine.music.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal object OlbyineMotion {
    val placement: FiniteAnimationSpec<IntOffset> = appleSpring(response = .36f, visibilityThreshold = IntOffset.VisibilityThreshold)

    val itemIn: FiniteAnimationSpec<Float> = tween(220, easing = OlbyineTokens.Motion.easeOut)

    val itemOut: FiniteAnimationSpec<Float> = tween(120, easing = OlbyineTokens.Motion.easeOut)

    val glide: FiniteAnimationSpec<Float> = appleSpring(response = .32f, damping = .9f)

    val resize: FiniteAnimationSpec<IntSize> = appleSpring(response = .3f, visibilityThreshold = IntSize.VisibilityThreshold)
}

@Composable
internal fun LazyItemScope.itemMotion(): Modifier {
    val reduce = LocalReduceMotion.current
    return Modifier.animateItem(
        fadeInSpec = OlbyineMotion.itemIn,
        placementSpec = if (reduce) null else OlbyineMotion.placement,
        fadeOutSpec = OlbyineMotion.itemOut,
    )
}

@Composable
internal fun LazyGridItemScope.itemMotion(placement: Boolean = true): Modifier {
    val reduce = LocalReduceMotion.current
    return Modifier.animateItem(
        fadeInSpec = OlbyineMotion.itemIn,
        placementSpec = if (reduce || !placement) null else OlbyineMotion.placement,
        fadeOutSpec = OlbyineMotion.itemOut,
    )
}

@Composable
internal fun LazyItemScope.MotionItem(content: @Composable () -> Unit) {
    Box(itemMotion(), propagateMinConstraints = true) { content() }
}

@Composable
internal fun LazyGridItemScope.MotionItem(content: @Composable () -> Unit) {
    Box(itemMotion(), propagateMinConstraints = true) { content() }
}

private const val SHIMMER_MS = 1_300

@Composable
private fun shimmerPhase(): State<Float> {
    val phase = remember { mutableFloatStateOf(0f) }
    if (!LocalReduceMotion.current) {
        LaunchedEffect(Unit) {
            while (true) {
                withInfiniteAnimationFrameMillis { frame -> phase.floatValue = (frame % SHIMMER_MS) / SHIMMER_MS.toFloat() }
            }
        }
    }
    return phase
}

internal fun Modifier.skeleton(shape: Shape = RoundedCornerShape(8.dp), phase: State<Float>? = null): Modifier = composed {
    @Suppress("NAME_SHADOWING")
    val phase = phase ?: shimmerPhase()
    val ink = MaterialTheme.colorScheme.onSurface
    val base = ink.copy(alpha = .07f)
    val light = ink.copy(alpha = .13f)
    val sweep = with(LocalDensity.current) { 520.dp.toPx() }
    val origin = remember { FloatArray(1) }
    this
        .clip(shape)
        .onPlaced { origin[0] = it.localToWindow(Offset.Zero).x }
        .drawBehind {
            drawRect(base)
            val center = -sweep * .4f + sweep * 1.8f * phase.value - origin[0]
            drawRect(
                Brush.linearGradient(
                    0f to Color.Transparent,
                    .5f to light,
                    1f to Color.Transparent,
                    start = Offset(center - sweep * .25f, 0f),
                    end = Offset(center + sweep * .25f, size.height * .4f),
                ),
            )
        }
}

@Composable
internal fun SkeletonRow(avatar: Boolean = false, modifier: Modifier = Modifier) {
    val phase = shimmerPhase()
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).skeleton(if (avatar) CircleShape else RoundedCornerShape(OlbyineTokens.Corner.thumb), phase))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(.62f).height(13.dp).skeleton(RoundedCornerShape(6.dp), phase))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(.38f).height(11.dp).skeleton(RoundedCornerShape(6.dp), phase))
        }
    }
}

@Composable
internal fun SkeletonCard(modifier: Modifier = Modifier) {
    val phase = shimmerPhase()
    Column(modifier.padding(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).skeleton(RoundedCornerShape(14.dp), phase))
        Spacer(Modifier.height(9.dp))
        Box(Modifier.fillMaxWidth(.8f).height(12.dp).skeleton(RoundedCornerShape(6.dp), phase))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(.5f).height(10.dp).skeleton(RoundedCornerShape(5.dp), phase))
    }
}

internal fun LazyListScope.skeletonRows(count: Int = 8, avatar: Boolean = false) {
    items(count, key = { "skeleton-row-$it" }) { MotionItem { SkeletonRow(avatar) } }
}

internal fun LazyGridScope.skeletonCards(count: Int = 8) {
    items(count, key = { "skeleton-card-$it" }) { MotionItem { SkeletonCard() } }
}

internal fun LazyGridScope.skeletonRows(count: Int = 8, avatar: Boolean = false) {
    items(count, key = { "skeleton-row-$it" }, span = { GridItemSpan(maxLineSpan) }) { MotionItem { SkeletonRow(avatar) } }
}

@Composable
internal fun rowFill(selected: Boolean, current: Boolean): Color {
    val accent = MaterialTheme.colorScheme.primary
    val fill by androidx.compose.animation.animateColorAsState(
        when {
            selected -> accent.copy(alpha = .14f)
            current -> accent.copy(alpha = .08f)
            else -> accent.copy(alpha = 0f)
        },
        tween(OlbyineTokens.Motion.standardMs, easing = OlbyineTokens.Motion.easeOut),
        label = "row-fill",
    )
    return fill
}

@Composable
internal fun rowInk(current: Boolean, normal: Color = MaterialTheme.colorScheme.onSurface): Color {
    val ink by androidx.compose.animation.animateColorAsState(
        if (current) MaterialTheme.colorScheme.primary else normal,
        tween(OlbyineTokens.Motion.standardMs, easing = OlbyineTokens.Motion.easeOut),
        label = "row-ink",
    )
    return ink
}

@Composable
internal fun PopIn(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(OlbyineTokens.Motion.standardMs, easing = OlbyineTokens.Motion.easeOut)) +
            scaleIn(initialScale = .9f, animationSpec = appleSpring(response = .28f)),
        exit = fadeOut(tween(OlbyineTokens.Motion.quickMs)) +
            androidx.compose.animation.scaleOut(targetScale = .8f, animationSpec = tween(OlbyineTokens.Motion.quickMs, easing = OlbyineTokens.Motion.easeOut)),
    ) { content() }
}

// 列表/表单里整段出现或收起的内容：高度跟着展开，下面的内容不会跳动
@Composable
internal fun RevealIn(visible: Boolean, modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val reduce = LocalReduceMotion.current
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduce) fadeIn(tween(150)) else fadeIn(tween(OlbyineTokens.Motion.standardMs, easing = OlbyineTokens.Motion.easeOut)) +
            androidx.compose.animation.expandVertically(OlbyineMotion.resize, expandFrom = Alignment.Top),
        exit = if (reduce) fadeOut(tween(90)) else fadeOut(tween(OlbyineTokens.Motion.quickMs)) +
            androidx.compose.animation.shrinkVertically(OlbyineMotion.resize, shrinkTowards = Alignment.Top),
    ) { Column { content() } }
}

internal fun Modifier.parallaxHeader(state: androidx.compose.foundation.lazy.LazyListState, factor: Float = .45f): Modifier = composed {
    if (LocalReduceMotion.current) Modifier
    else Modifier.graphicsLayer {
        val scrolled = if (state.firstVisibleItemIndex == 0) state.firstVisibleItemScrollOffset.toFloat() else size.height
        translationY = scrolled * factor
        alpha = 1f - .35f * (scrolled / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
    }
}

internal fun Modifier.parallaxHeader(state: androidx.compose.foundation.lazy.grid.LazyGridState, factor: Float = .45f): Modifier = composed {
    if (LocalReduceMotion.current) Modifier
    else Modifier.graphicsLayer {
        val scrolled = if (state.firstVisibleItemIndex == 0) state.firstVisibleItemScrollOffset.toFloat() else size.height
        translationY = scrolled * factor
        alpha = 1f - .35f * (scrolled / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
    }
}

internal fun Modifier.expandable(): Modifier = composed {
    if (LocalReduceMotion.current) Modifier else Modifier.animateContentSize(OlbyineMotion.resize)
}

private class SkipMemory(var index: Int, var direction: Int = 1)

@Composable
internal fun rememberSkipDirection(index: Int, count: Int): Int {
    val memory = remember { SkipMemory(index) }
    val previous = memory.index
    val direction = when {
        index == previous || index < 0 || previous < 0 -> memory.direction
        count > 1 && previous == count - 1 && index == 0 -> 1
        count > 1 && previous == 0 && index == count - 1 -> -1
        index > previous -> 1
        else -> -1
    }
    SideEffect {
        memory.index = index
        memory.direction = direction
    }
    return direction
}

internal fun <S> AnimatedContentTransitionScope<S>.skipTransition(direction: Int, distancePx: Int, reduce: Boolean): ContentTransform {
    if (reduce) return fadeIn(tween(160)) togetherWith fadeOut(tween(100))
    val travel = distancePx * direction
    return (slideInHorizontally(tween(280, easing = OlbyineTokens.Motion.easeOut)) { travel } + fadeIn(tween(200, easing = OlbyineTokens.Motion.easeOut)))
        .togetherWith(slideOutHorizontally(tween(200, easing = OlbyineTokens.Motion.easeOut)) { -travel } + fadeOut(tween(120)))
        .using(SizeTransform(clip = true))
}

@Composable
internal fun skipDistance(distance: Dp = 28.dp): Int = with(LocalDensity.current) { distance.toPx().roundToInt() }

internal class Entrance(private val clock: Animatable<Float, *>, private val reduce: Boolean, private val risePx: Float) {
    fun item(index: Int, stepMs: Int = 55, scaleFrom: Float = 1f): Modifier = Modifier.graphicsLayer {
        val elapsed = clock.value - index * stepMs
        val p = OlbyineTokens.Motion.easeOut.transform((elapsed / ENTRANCE_ITEM_MS).coerceIn(0f, 1f))
        alpha = p
        if (!reduce) {
            translationY = risePx * (1f - p)
            if (scaleFrom != 1f) {
                val s = scaleFrom + (1f - scaleFrom) * p
                scaleX = s
                scaleY = s
            }
        }
    }
}

private const val ENTRANCE_ITEM_MS = 360f

@Composable
internal fun rememberEntrance(rise: Dp = 12.dp): Entrance {
    val reduce = LocalReduceMotion.current
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) { clock.animateTo(1_000f, tween(1_000, easing = LinearEasing)) }
    val risePx = with(LocalDensity.current) { rise.toPx() }
    return remember(clock, reduce, risePx) { Entrance(clock, reduce, risePx) }
}

@Composable
internal fun AnimatedTick(checked: Boolean, accent: Color, size: Dp = 24.dp, appear: Boolean = false) {
    val outline = MaterialTheme.colorScheme.outline
    val mark = remember { Animatable(if (checked && !appear) 1f else 0f) }
    LaunchedEffect(checked) {
        if (checked) mark.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = .8f, stiffness = 700f))
        else mark.animateTo(0f, tween(100))
    }
    Box(
        Modifier.size(size).clip(CircleShape)
            .drawBehind {
                val m = mark.value.coerceIn(0f, 1f)
                drawRect(accent.copy(alpha = accent.alpha * m))
            }
            .border(1.5.dp, androidx.compose.ui.graphics.lerp(outline, accent, mark.value.coerceIn(0f, 1f)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Rounded.Check,
            null,
            Modifier.size(size * .66f).graphicsLayer {
                val m = mark.value
                val s = .4f + .6f * m
                scaleX = s
                scaleY = s
                alpha = m.coerceIn(0f, 1f)
            },
            tint = Color.White,
        )
    }
}

@Composable
internal fun <T> SwapContent(
    target: T,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable (T) -> Unit,
) {
    val reduce = LocalReduceMotion.current
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        contentAlignment = contentAlignment,
        transitionSpec = {
            if (reduce) fadeIn(tween(150)) togetherWith fadeOut(tween(90))
            else (fadeIn(tween(200, easing = OlbyineTokens.Motion.easeOut)) + scaleIn(initialScale = .96f, animationSpec = tween(220, easing = OlbyineTokens.Motion.easeOut)))
                .togetherWith(fadeOut(tween(100)))
                .using(SizeTransform(clip = false) { _, _ -> OlbyineMotion.resize })
        },
        label = "swap-content",
    ) { shown ->
        Box(morphBlur()) { content(shown) }
    }
}
