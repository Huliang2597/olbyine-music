package com.olbyine.music.ui

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Stable
internal class GlassPopupRequest(
    val key: Any,
    val anchor: Rect,
    val onDismiss: () -> Unit,
    val content: @Composable ColumnScope.() -> Unit,
)

@Stable
internal class GlassPopupHostState {
    internal var coordinates: LayoutCoordinates? = null
    internal var view: View? = null
    internal var current by mutableStateOf<GlassPopupRequest?>(null)
    internal var shown by mutableStateOf<GlassPopupRequest?>(null)

    fun open(request: GlassPopupRequest) {
        current = request
        shown = request
    }

    fun close(key: Any) {
        if (current?.key == key) current = null
    }
}

internal val LocalGlassPopupHost = staticCompositionLocalOf<GlassPopupHostState?> { null }

@Composable
internal fun rememberGlassPopupHostState(): GlassPopupHostState = remember { GlassPopupHostState() }

@Composable
internal fun OlbyineDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val host = LocalGlassPopupHost.current
    val view = LocalView.current
    val haptics = rememberHaptics()
    LaunchedEffect(expanded) { if (expanded) haptics.light(com.olbyine.music.data.HapticArea.MENUS) }
    if (host == null || (host.view != null && host.view?.rootView !== view.rootView)) {
        LegacyDropdownMenu(expanded, onDismissRequest, content)
        return
    }
    val key = remember { Any() }
    val anchor = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val currentDismiss by rememberUpdatedState(onDismissRequest)
    val currentContent by rememberUpdatedState(content)
    Box(Modifier.size(0.dp).onPlaced { anchor[0] = it.parentLayoutCoordinates })
    LaunchedEffect(expanded) {
        if (expanded) {
            val parent = anchor[0]
            val hostCoordinates = host.coordinates
            val bounds = if (parent != null && hostCoordinates != null && parent.isAttached && hostCoordinates.isAttached) {
                hostCoordinates.localBoundingBoxOf(parent, clipBounds = false)
            } else null
            if (bounds == null) {
                currentDismiss()
            } else {
                host.open(
                    GlassPopupRequest(
                        key = key,
                        anchor = bounds,
                        onDismiss = { currentDismiss() },
                        content = { currentContent.invoke(this) },
                    ),
                )
            }
        } else {
            host.close(key)
        }
    }
    DisposableEffect(host, key) { onDispose { host.close(key) } }
}

internal fun GlassPopupHostState.openAt(
    key: Any,
    anchor: LayoutCoordinates,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
): Boolean {
    val hostCoordinates = coordinates ?: return false
    if (!hostCoordinates.isAttached || !anchor.isAttached) return false
    open(GlassPopupRequest(key, hostCoordinates.localBoundingBoxOf(anchor, clipBounds = false), onDismiss, content))
    return true
}

@Composable
private fun LegacyDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 12.dp,
        content = content,
    )
}

@Composable
internal fun popupGlassStyle(liquid: Boolean): GlassStyle {
    val colors = MaterialTheme.colorScheme
    val spec = LocalStyleSpec.current
    val dark = colors.background.luminance() < .5f
    return when {
        liquid && spec.popupLiquid -> GlassStyle(
            cornerRadius = spec.popupCorner,
            blur = 14.dp,
            tint = if (dark) Color.Black.copy(alpha = .30f) else Color.White.copy(alpha = .46f),
            saturation = 1.8f,
            lens = true,
            refractionHeight = 16.dp,
            refractionAmount = 22.dp,
            dispersion = true,
            highlight = Color.White.copy(alpha = if (dark) .72f else .9f),
            highlightWidth = 0.8.dp,
            shadow = 18.dp,
            fallback = colors.surfaceContainerHigh,
        )
        spec.style == com.olbyine.music.data.UiStyle.MATERIAL -> GlassStyle(
            cornerRadius = spec.popupCorner,
            blur = 0.dp,
            tint = colors.surfaceContainer.copy(alpha = .98f),
            saturation = 1f,
            shadow = 8.dp,
            fallback = colors.surfaceContainer,
        )
        else -> GlassStyle(
            cornerRadius = spec.popupCorner,
            blur = 30.dp,
            tint = colors.surfaceContainerHigh.copy(alpha = if (dark) .74f else .70f),
            saturation = 1.7f,
            highlight = Color.White.copy(alpha = if (dark) .14f else .5f),
            shadow = 18.dp,
            fallback = colors.surfaceContainerHigh,
        )
    }
}
internal val PopupCorner = 22.dp

@Composable
internal fun GlassPopupLayer(
    state: GlassPopupHostState,
    source: BackdropSource?,
    style: GlassStyle,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    state.view = view
    val shown = state.shown
    val open = state.current != null && state.current === shown
    val progress = remember { Animatable(0f) }
    val lastKey = remember { arrayOfNulls<Any>(1) }
    val stockMaterial = LocalStyleSpec.current.style == com.olbyine.music.data.UiStyle.MATERIAL
    LaunchedEffect(shown, open) {
        if (shown == null) return@LaunchedEffect
        if (open) {
            if (lastKey[0] !== shown.key) progress.snapTo(0f)
            lastKey[0] = shown.key
            progress.animateTo(1f, spring(dampingRatio = if (stockMaterial) 1f else .9f, stiffness = 520f))
        } else {
            progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 900f))
            if (state.current == null && state.shown === shown) state.shown = null
        }
    }
    Box(modifier.fillMaxSize().onGloballyPositioned { state.coordinates = it }) { androidx.compose.runtime.CompositionLocalProvider(LocalOnGlass provides true) {
        if (shown != null) {
            BackHandler(enabled = open) { shown.onDismiss() }
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(shown) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            if (state.current === shown) shown.onDismiss()
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    },
            )
            MorphingPopup(shown, progress, source, style)
        }
    } }
}

private class PopupGeometry {
    var surface = Rect.Zero
    var target = Rect.Zero
    var radius = 0f
}

@Composable
private fun MorphingPopup(
    request: GlassPopupRequest,
    progress: Animatable<Float, *>,
    source: BackdropSource?,
    style: GlassStyle,
) {
    val density = LocalDensity.current
    val geometry = remember(request) { PopupGeometry() }
    val finalRadius = with(density) { style.cornerRadius.toPx() }
    val anchorRadius = request.anchor.minDimension / 2f
    val statusBar = WindowInsets.statusBars.getTop(density)
    val navBar = WindowInsets.navigationBars.getBottom(density)
    val dynamics = remember(request) {
        GlassDynamics(
            cornerRadius = { geometry.radius },
            materialize = { (progress.value * 1.6f).coerceIn(0f, 1f) },
            lensProgress = { progress.value.coerceIn(0f, 1.2f) },
        )
    }
    Layout(
        content = {
            Box(
                Modifier
                    .layoutId("surface")
                    .glass(source, style, dynamics = dynamics),
            )
            Column(
                Modifier
                    .layoutId("items")
                    .graphicsLayer {
                        val t = geometry.target
                        val s = geometry.surface
                        shape = OffsetRoundRect(
                            Rect(s.left - t.left, s.top - t.top, s.right - t.left, s.bottom - t.top),
                            geometry.radius,
                        )
                        clip = true
                        alpha = ((progress.value - .42f) / .45f).coerceIn(0f, 1f)
                    }
                    .width(IntrinsicSize.Max)
                    .widthIn(min = 176.dp, max = 300.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
                content = request.content,
            )
        },
    ) { measurables, constraints ->
        val margin = 12.dp.roundToPx()
        val gap = 8.dp.roundToPx()
        val hostWidth = constraints.maxWidth
        val hostHeight = constraints.maxHeight
        val top = statusBar + margin
        val bottom = hostHeight - navBar - margin
        val itemsMeasurable = measurables.first { it.layoutId == "items" }
        val items = itemsMeasurable.measure(
            Constraints(maxWidth = (hostWidth - margin * 2).coerceAtLeast(0), maxHeight = (bottom - top).coerceAtLeast(0)),
        )
        val anchor = request.anchor
        val width = items.width
        val height = items.height
        val x = (if (anchor.center.x > hostWidth / 2f) anchor.right - width else anchor.left)
            .roundToInt().coerceIn(margin, (hostWidth - margin - width).coerceAtLeast(margin))
        val below = bottom - (anchor.bottom + gap)
        val above = (anchor.top - gap) - top
        val y = if (height <= below || below >= above) {
            (anchor.bottom + gap).roundToInt()
        } else {
            (anchor.top - gap - height).roundToInt()
        }.coerceIn(top, (bottom - height).coerceAtLeast(top))
        val target = Rect(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat())
        val p = progress.value
        val surface = Rect(
            lerp(anchor.left, target.left, p),
            lerp(anchor.top, target.top, p),
            lerp(anchor.right, target.right, p),
            lerp(anchor.bottom, target.bottom, p),
        )
        geometry.target = target
        geometry.surface = surface
        geometry.radius = lerp(anchorRadius, finalRadius, p.coerceIn(0f, 1f))
            .coerceAtMost(minOf(surface.width, surface.height) / 2f).coerceAtLeast(0f)
        val surfacePlaceable = measurables.first { it.layoutId == "surface" }.measure(
            Constraints.fixed(surface.width.roundToInt().coerceAtLeast(1), surface.height.roundToInt().coerceAtLeast(1)),
        )
        layout(hostWidth, hostHeight) {
            surfacePlaceable.place(surface.left.roundToInt(), surface.top.roundToInt())
            items.place(x, y)
        }
    }
}

private fun lerp(start: Float, end: Float, fraction: Float) = start + (end - start) * fraction

private class OffsetRoundRect(private val rect: Rect, private val radius: Float) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = radius.coerceAtMost(minOf(rect.width, rect.height) / 2f).coerceAtLeast(0f)
        return Outline.Rounded(
            androidx.compose.ui.geometry.RoundRect(rect, androidx.compose.ui.geometry.CornerRadius(r)),
        )
    }
}

internal val LocalMenuDense = androidx.compose.runtime.compositionLocalOf { false }

@Composable
internal fun GlassMenuItem(
    text: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    destructive: Boolean = false,
) {
    val haptics = rememberHaptics()
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val dense = LocalMenuDense.current
    val color = when {
        !enabled -> ink.copy(alpha = .38f)
        destructive -> MaterialTheme.colorScheme.error
        selected -> accent
        else -> ink
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(if (dense) 12.dp else 14.dp))
            .background(if (selected) accent.copy(alpha = .12f) else Color.Transparent)
            .clickable(enabled = enabled) { haptics.click(com.olbyine.music.data.HapticArea.MENUS); onClick() }
            .padding(horizontal = 12.dp, vertical = if (dense) 9.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            StyledIcon(icon, null, Modifier.size(if (dense) 18.dp else 20.dp), tint = color)
            Spacer(Modifier.width(if (dense) 10.dp else 12.dp))
        }
        Text(
            text,
            Modifier.weight(1f),
            style = if (dense) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            color = color,
            maxLines = 1,
        )
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        } else if (selected) {
            Spacer(Modifier.width(10.dp))
            StyledIcon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = accent)
        }
    }
}

@Composable
internal fun MenuQuickRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
internal fun androidx.compose.foundation.layout.RowScope.MenuQuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    val ink = MaterialTheme.colorScheme.onSurface
    val color = if (enabled) ink else ink.copy(alpha = .35f)
    androidx.compose.foundation.layout.Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(ink.copy(alpha = .06f))
            .clickable(enabled = enabled) { haptics.click(com.olbyine.music.data.HapticArea.MENUS); onClick() }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StyledIcon(icon, null, Modifier.size(20.dp), tint = color)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

@Composable
internal fun MenuSubmenu(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 90f else 0f, androidx.compose.animation.core.spring(dampingRatio = .8f, stiffness = 600f), label = "submenu-chevron")
    GlassMenuItem(text, { open = !open }, icon = icon, trailing = {
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
            Modifier.size(18.dp).graphicsLayer { rotationZ = turn },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    })
    androidx.compose.animation.AnimatedVisibility(
        visible = open,
        enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 600f)) +
            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(160, delayMillis = 40)),
        exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 800f)) +
            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90)),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.padding(start = 14.dp)) { content() }
    }
}
