package com.olbyine.music.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.olbyine.music.data.Track
import com.olbyine.music.playback.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal fun Modifier.pressable(
    enabled: Boolean = true,
    pressedScale: Float = .9f,
    label: String? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberPressProgress(interaction)
    val haptics = rememberHaptics()
    this
        .graphicsLayer {
            val scale = 1f - (1f - pressedScale) * press.value
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClickLabel = label,
            role = Role.Button,
            onClick = { haptics.click(com.olbyine.music.data.HapticArea.MENUS); onClick() },
        )
}

internal val LocalGlassSource = staticCompositionLocalOf<BackdropSource?> { null }

@Composable
internal fun controlGlass(corner: Dp, accent: Color? = null): GlassStyle? {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    if (LocalOnGlass.current) return null
    return when (LocalStyleSpec.current.style) {
        com.olbyine.music.data.UiStyle.MATERIAL -> null
        com.olbyine.music.data.UiStyle.IOS -> GlassStyle(
            cornerRadius = corner,
            blur = 6.dp,
            tint = accent?.let { it.copy(alpha = it.alpha * .82f) } ?: if (dark) Color.White.copy(alpha = .10f) else Color.White.copy(alpha = .38f),
            saturation = 1.5f,
            contrast = 1.1f,
            lens = true,
            refractionHeight = (corner * .55f).coerceAtMost(16.dp),
            refractionAmount = 14.dp,
            depthEffect = true,
            highlight = Color.White.copy(alpha = if (dark) .5f else .85f),
            highlightWidth = .8.dp,
            shadow = 6.dp,
            fallback = accent ?: colors.surfaceContainerHigh,
        )
        else -> GlassStyle(
            cornerRadius = corner,
            blur = 20.dp,
            tint = accent?.let { it.copy(alpha = it.alpha * .88f) } ?: colors.surfaceContainerHigh.copy(alpha = if (dark) .55f else .62f),
            saturation = 1.6f,
            highlight = Color.White.copy(alpha = if (dark) .14f else .5f),
            shadow = 4.dp,
            fallback = accent ?: colors.surfaceContainerHigh,
        )
    }
}

@Composable
internal fun Modifier.controlSurface(style: GlassStyle, shape: androidx.compose.ui.graphics.Shape): Modifier {
    val source = LocalGlassSource.current
    if (source != null) return this.glass(source, style)
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val tuned = style.tuned(LocalGlassTuning.current, dark)
    return this
        .then(if (tuned.shadow > 0.dp) Modifier.shadow(tuned.shadow, shape, clip = false, ambientColor = Color.Black.copy(alpha = .3f), spotColor = Color.Black.copy(alpha = .3f)) else Modifier)
        .clip(shape)
        .background(tuned.tint)
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) .10f else .26f), Color.Transparent)))
        .lightRim(shape, tuned.highlight.alpha.coerceAtLeast(if (dark) .32f else .6f))
}

@Composable
internal fun OlbyineIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    container: Color? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
) {
    val uiStyle = LocalStyleSpec.current.style
    val burst = rememberHeartBurst(icon)
    val alpha by animateFloatAsState(if (enabled) 1f else .38f, tween(OlbyineTokens.Motion.quickMs), label = "icon-enabled")
    if (uiStyle != com.olbyine.music.data.UiStyle.MATERIAL && container == null) {
        val haptics = rememberHaptics()
        val interaction = remember { MutableInteractionSource() }
        val press = rememberPressProgress(interaction)
        Box(
            modifier
                .size(size)
                .clip(CircleShape)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = androidx.compose.ui.semantics.Role.Button) {
                    haptics.click(com.olbyine.music.data.HapticArea.MENUS)
                    onClick()
                }
                .drawBehind { drawHeartBurst(burst.value, tint) }
                .graphicsLayer { this.alpha = alpha * (1f - .6f * press.value) },
            contentAlignment = Alignment.Center,
        ) {
            IconSwap(icon, contentDescription, iconSize * 1.12f, tint)
        }
        return
    }
    val glass = controlGlass(size / 2, accent = container?.let { if (uiStyle == com.olbyine.music.data.UiStyle.IOS) MaterialTheme.colorScheme.onSurface else it })
    Box(
        modifier
            .size(size)
            .pressable(enabled = enabled, label = contentDescription, onClick = onClick)
            .drawBehind { drawHeartBurst(burst.value, tint) }
            .then(
                if (glass != null) Modifier.controlSurface(glass, CircleShape)
                else Modifier.clip(CircleShape).background(container ?: MaterialTheme.colorScheme.surfaceContainerHigh),
            )
            .clip(CircleShape)
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.Center,
    ) {
        IconSwap(icon, contentDescription, iconSize, tint)
    }
}
@Composable
internal fun GhostIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
) {
    val burst = rememberHeartBurst(icon)
    val alpha by animateFloatAsState(if (enabled) 1f else .38f, tween(OlbyineTokens.Motion.quickMs), label = "icon-enabled")
    Box(
        modifier
            .size(size)
            .drawBehind { drawHeartBurst(burst.value, tint) }
            .clip(CircleShape)
            .pressable(enabled = enabled, label = contentDescription, onClick = onClick)
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.Center,
    ) {
        IconSwap(icon, contentDescription, iconSize, tint)
    }
}

@Composable
internal fun IconSwap(icon: ImageVector, contentDescription: String?, iconSize: Dp, tint: Color) {
    val animatedTint by androidx.compose.animation.animateColorAsState(tint, tween(OlbyineTokens.Motion.standardMs), label = "icon-tint")
    val styled = styledVector(icon)
    val reduce = LocalReduceMotion.current
    AnimatedContent(
        targetState = styled,
        transitionSpec = {
            val heart = targetState == Icons.Rounded.Favorite || glyphOf(targetState)?.first == GlyphKey.HEART_ON
            val enterSpring = if (heart) androidx.compose.animation.core.spring<Float>(dampingRatio = .62f, stiffness = 700f)
            else appleSpring(response = .18f)
            (fadeIn(tween(110, easing = OlbyineTokens.Motion.easeOut)) + scaleIn(initialScale = if (heart) .4f else .6f, animationSpec = enterSpring))
                .togetherWith(fadeOut(tween(70)) + scaleOut(targetScale = .6f, animationSpec = tween(100, easing = OlbyineTokens.Motion.easeOut)))
                .using(SizeTransform(clip = false))
        },
        contentAlignment = Alignment.Center,
        label = "icon-swap",
    ) { shown ->
        val glyph = glyphOf(shown)
        val changing = transition.currentState == androidx.compose.animation.EnterExitState.PreEnter
        val strokes = remember(shown) { if (glyph != null) strokePathNames(shown) else emptyList() }
        val drawOn = strokes.isNotEmpty() && changing && !reduce
        val drawn = remember { androidx.compose.animation.core.Animatable(if (drawOn) 0f else 1f) }
        LaunchedEffect(Unit) { if (drawn.value < 1f) drawn.animateTo(1f, tween(220, easing = OlbyineTokens.Motion.easeOut)) }
        if (drawn.value < 1f) {
            val progress = drawn.value
            val config = remember(progress) {
                object : androidx.compose.ui.graphics.vector.VectorConfig {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T> getOrDefault(property: androidx.compose.ui.graphics.vector.VectorProperty<T>, defaultValue: T): T =
                        if (property == androidx.compose.ui.graphics.vector.VectorProperty.TrimPathEnd) progress as T else defaultValue
                }
            }
            val painter = androidx.compose.ui.graphics.vector.rememberVectorPainter(
                defaultWidth = shown.defaultWidth,
                defaultHeight = shown.defaultHeight,
                viewportWidth = shown.viewportWidth,
                viewportHeight = shown.viewportHeight,
                name = shown.name,
                tintColor = animatedTint,
                autoMirror = shown.autoMirror,
            ) { _, _ -> androidx.compose.ui.graphics.vector.RenderVectorGroup(shown.root, strokes.associateWith { config }) }
            Icon(painter, contentDescription, Modifier.size(iconSize).then(morphBlur()), tint = animatedTint)
        } else {
            Icon(shown, contentDescription, Modifier.size(iconSize).then(morphBlur()), tint = animatedTint)
        }
    }
}
@Composable
internal fun rememberHeartBurst(icon: ImageVector): State<Float> {
    val burst = remember { androidx.compose.animation.core.Animatable(1f) }
    val previous = remember { arrayOf(icon) }
    val enabled = playerFx(com.olbyine.music.data.PlayerFx.HEART_BURST)
    LaunchedEffect(icon) {
        val was = previous[0]
        previous[0] = icon
        if (enabled && was == Icons.Rounded.FavoriteBorder &&
            icon == Icons.Rounded.Favorite
        ) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(640, easing = androidx.compose.animation.core.LinearEasing))
        }
    }
    return burst.asState()
}

internal enum class PillStyle { FILLED, TONAL, OUTLINED }

private class PillLook(
    val fill: Color,
    val border: Color,
    val content: Color,
    val cornerFraction: Float,
    val pressScale: Float,
    val pressDim: Float,
    val pressVeil: Color,
    val sheen: Boolean,
    val shadow: Dp,
    val shadowColor: Color,
    val morph: Boolean,
    val weight: FontWeight,
)

@Composable
private fun pillLook(style: PillStyle): PillLook {
    val ui = LocalStyleSpec.current.style
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    val clear = Color.Transparent
    return when (ui) {
        com.olbyine.music.data.UiStyle.IOS -> when (style) {
            PillStyle.FILLED -> PillLook(colors.primary, clear, colors.onPrimary, .5f, .97f, .75f, clear, false, 0.dp, clear, false, FontWeight.SemiBold)
            PillStyle.TONAL -> PillLook(if (dark) Color(0xFF2C2C2E) else Color.White, if (dark) Color.White.copy(alpha = .10f) else Color.Black.copy(alpha = .07f), colors.onSurface, .5f, .97f, .7f, clear, false,
                if (dark) 0.dp else 3.dp, Color.Black.copy(alpha = .10f), false, FontWeight.SemiBold)
            PillStyle.OUTLINED -> PillLook(clear, colors.outline, colors.primary, .5f, .97f, .7f, clear, false, 0.dp, clear, false, FontWeight.SemiBold)
        }
        com.olbyine.music.data.UiStyle.COLOROS -> when (style) {
            PillStyle.FILLED -> PillLook(colors.primary, clear, colors.onPrimary, .5f, .94f, 1f, Color.Black.copy(alpha = .08f), false,
                if (dark) 0.dp else 10.dp, colors.primary.copy(alpha = .45f), false, FontWeight.Medium)
            PillStyle.TONAL -> PillLook(colors.primary.copy(alpha = if (dark) .20f else .11f), clear, colors.primary, .5f, .94f, 1f, colors.primary.copy(alpha = .08f), false, 0.dp, clear, false, FontWeight.Medium)
            PillStyle.OUTLINED -> PillLook(clear, colors.primary.copy(alpha = .55f), colors.primary, .5f, .94f, 1f, colors.primary.copy(alpha = .06f), false, 0.dp, clear, false, FontWeight.Medium)
        }
        com.olbyine.music.data.UiStyle.HYPEROS -> when (style) {
            PillStyle.FILLED -> PillLook(colors.primary, clear, colors.onPrimary, .34f, .97f, 1f, Color.Black.copy(alpha = .12f), false, 0.dp, clear, false, FontWeight.SemiBold)
            PillStyle.TONAL -> PillLook(colors.surfaceContainerHigh, clear, colors.onSurface, .34f, .97f, 1f, Color.Black.copy(alpha = if (dark) .2f else .06f), false, 0.dp, clear, false, FontWeight.SemiBold)
            PillStyle.OUTLINED -> PillLook(clear, colors.outline, colors.onSurface, .34f, .97f, 1f, Color.Black.copy(alpha = .05f), false, 0.dp, clear, false, FontWeight.SemiBold)
        }
        com.olbyine.music.data.UiStyle.MATERIAL -> when (style) {
            PillStyle.FILLED -> PillLook(colors.primary, clear, colors.onPrimary, .5f, 1f, 1f, clear, false, 0.dp, clear, true, FontWeight.Medium)
            PillStyle.TONAL -> PillLook(colors.secondaryContainer, clear, colors.onSecondaryContainer, .5f, 1f, 1f, clear, false, 0.dp, clear, true, FontWeight.Medium)
            PillStyle.OUTLINED -> PillLook(clear, colors.outline, colors.primary, .5f, 1f, 1f, clear, false, 0.dp, clear, true, FontWeight.Medium)
        }
    }
}

@Composable
internal fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: PillStyle = PillStyle.FILLED,
    enabled: Boolean = true,
    height: Dp = 42.dp,
) {
    val look = pillLook(style)
    val material = LocalStyleSpec.current.style == com.olbyine.music.data.UiStyle.MATERIAL
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val press = rememberPressProgress(interaction)
    val alpha by animateFloatAsState(if (enabled) 1f else .4f, tween(OlbyineTokens.Motion.quickMs), label = "pill-enabled")
    val fill by animateColorAsState(look.fill, tween(OlbyineTokens.Motion.standardMs, easing = OlbyineTokens.Motion.easeOut), label = "pill-fill")
    val border by animateColorAsState(look.border, tween(OlbyineTokens.Motion.standardMs), label = "pill-border")
    val contentColor by animateColorAsState(look.content, tween(OlbyineTokens.Motion.standardMs), label = "pill-content")
    val radius = height * look.cornerFraction
    val shadowColor = look.shadowColor
    Row(
        modifier
            .height(height)
            .graphicsLayer {
                val p = press.value
                val s = 1f - (1f - look.pressScale) * p
                scaleX = s
                scaleY = s
                this.alpha = alpha * (1f - (1f - look.pressDim) * p)
                val r = radius.toPx().let { if (look.morph) it - it * .5f * p else it }
                shape = RoundedCornerShape(r)
                clip = true
                if (look.shadow > 0.dp && enabled) {
                    shadowElevation = look.shadow.toPx() * (1f - .5f * p)
                    ambientShadowColor = shadowColor
                    spotShadowColor = shadowColor
                }
            }
            .drawBehind {
                drawRect(fill)
                if (look.sheen) {
                    drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = .20f), .55f to Color.Transparent))
                }
            }
            .then(
                if (border.alpha > 0f) {
                    Modifier.border(
                        1.dp,
                        if (look.sheen) Brush.verticalGradient(listOf(border, border.copy(alpha = border.alpha * .15f))) else androidx.compose.ui.graphics.SolidColor(border),
                        RoundedCornerShape(radius),
                    )
                } else Modifier,
            )
            .drawWithContent {
                drawContent()
                val veil = look.pressVeil
                if (veil.alpha > 0f) drawRect(veil.copy(alpha = veil.alpha * press.value))
            }
            .clickable(
                interactionSource = interaction,
                indication = if (material) androidx.compose.foundation.LocalIndication.current else null,
                enabled = enabled,
                role = Role.Button,
            ) { haptics.click(com.olbyine.music.data.HapticArea.MENUS); onClick() }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        AnimatedContent(
            targetState = text to icon,
            transitionSpec = {
                (fadeIn(tween(180, easing = OlbyineTokens.Motion.easeOut)) + scaleIn(initialScale = .9f, animationSpec = tween(200, easing = OlbyineTokens.Motion.easeOut)))
                    .togetherWith(fadeOut(tween(90)))
                    .using(SizeTransform(clip = false) { _, _ -> OlbyineMotion.resize })
            },
            contentAlignment = Alignment.Center,
            label = "pill-label",
        ) { (shownText, shownIcon) ->
            Row(Modifier.then(morphBlur()), verticalAlignment = Alignment.CenterVertically) {
                if (shownIcon != null) {
                    StyledIcon(shownIcon, null, Modifier.size(19.dp), tint = contentColor)
                    Spacer(Modifier.width(7.dp))
                }
                Text(shownText, style = MaterialTheme.typography.labelLarge.copy(fontWeight = look.weight), color = contentColor, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    insetStatusBar: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (insetStatusBar) Modifier.statusBarsPadding() else Modifier)
            .padding(start = OlbyineTokens.Spacing.page, end = 16.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            actions()
        }
    }
}

@Composable
internal fun Overline(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), modifier, style = OverlineStyle, color = color, maxLines = 1)
}

@Composable
internal fun <T> PillTabs(
    items: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = OlbyineTokens.Spacing.page),
) {
    val haptics = rememberHaptics()
    val colors = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
    val reduce = LocalReduceMotion.current
    val bounds = remember { androidx.compose.runtime.mutableStateMapOf<Int, Pair<Float, Float>>() }
    val selectedIndex = items.indexOfFirst { it.first == selected }
    val indicatorX = remember { androidx.compose.animation.core.Animatable(0f) }
    val indicatorW = remember { androidx.compose.animation.core.Animatable(0f) }
    var settled by remember { mutableStateOf(false) }
    val target = bounds[selectedIndex]
    val edge = with(LocalDensity.current) { 24.dp.toPx() }
    LaunchedEffect(selectedIndex, target) {
        val (x, width) = target ?: return@LaunchedEffect
        if (!settled || reduce) {
            indicatorX.snapTo(x)
            indicatorW.snapTo(width)
            settled = true
        } else {
            launch { indicatorX.animateTo(x, OlbyineMotion.glide) }
            launch { indicatorW.animateTo(width, OlbyineMotion.glide) }
        }
        val viewport = scroll.viewportSize.toFloat()
        if (viewport > 0f) {
            val start = x - edge
            val end = x + width + edge - viewport
            when {
                start < scroll.value -> scroll.animateScrollTo(start.toInt().coerceAtLeast(0))
                end > scroll.value -> scroll.animateScrollTo(end.toInt().coerceAtMost(scroll.maxValue))
            }
        }
    }
    val track = colors.surfaceContainerHigh
    val ink = colors.onBackground
    Row(
        modifier
            .horizontalScroll(scroll)
            .padding(contentPadding)
            .drawBehind {
                val radius = CornerRadius(size.height / 2f)
                for (index in items.indices) {
                    val (x, width) = bounds[index] ?: continue
                    drawRoundRect(track, topLeft = Offset(x, 0f), size = Size(width, size.height), cornerRadius = radius)
                }
                val shown = if (settled) indicatorX.value to indicatorW.value else target
                if (shown != null && selectedIndex >= 0) {
                    drawRoundRect(ink, topLeft = Offset(shown.first, 0f), size = Size(shown.second, size.height), cornerRadius = radius)
                }
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEachIndexed { index, (key, label) ->
            val isSelected = key == selected
            val foreground by animateColorAsState(
                if (isSelected) colors.background else colors.onSurfaceVariant,
                tween(OlbyineTokens.Motion.standardMs, easing = OlbyineTokens.Motion.easeOut),
                label = "tab-fg",
            )
            Box(
                Modifier
                    .height(36.dp)
                    .onPlaced { bounds[index] = it.positionInParent().x to it.size.width.toFloat() }
                    .clip(CircleShape)
                    .clickable(role = Role.Tab) { haptics.tick(com.olbyine.music.data.HapticArea.MENUS); onSelect(key) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = foreground, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun Badge(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, filled: Boolean = false) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .then(
                if (filled) Modifier.background(color.copy(alpha = .16f))
                else Modifier.border(1.dp, color.copy(alpha = .45f), RoundedCornerShape(6.dp)),
            )
            .padding(horizontal = 6.dp, vertical = 1.5.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp), color = color, maxLines = 1)
    }
}

@Composable
private fun SlideFade(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(OlbyineTokens.Motion.standardMs)) + androidx.compose.animation.slideInHorizontally(tween(OlbyineTokens.Motion.standardMs, easing = OlbyineTokens.Motion.easeOut)) { it / 12 },
        exit = fadeOut(tween(OlbyineTokens.Motion.quickMs)) + androidx.compose.animation.slideOutHorizontally(tween(OlbyineTokens.Motion.quickMs, easing = OlbyineTokens.Motion.easeOut)) { it / 12 },
    ) { content() }
}

@Composable
internal fun OlbyineSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val rim by animateColorAsState(if (focused) colors.primary.copy(alpha = .55f) else colors.primary.copy(alpha = 0f), tween(OlbyineTokens.Motion.standardMs), label = "search-rim")
    val glyph by animateColorAsState(if (focused) colors.primary else colors.onSurfaceVariant, tween(OlbyineTokens.Motion.standardMs), label = "search-glyph")
    val shape = if (LocalStyleSpec.current.style == com.olbyine.music.data.UiStyle.HYPEROS) RoundedCornerShape(16.dp) else CircleShape
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        interactionSource = interaction,
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(shape)
                    .background(colors.surfaceContainerHigh)
                    .border(1.5.dp, rim, shape)
                    .padding(start = 15.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StyledIcon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = glyph)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    SlideFade(visible = value.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1)
                    }
                    inner()
                }
                AnimatedVisibility(value.isNotEmpty(), enter = fadeIn() + scaleIn(initialScale = .7f), exit = fadeOut() + scaleOut(targetScale = .7f)) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).clickable(onClickLabel = "清除搜索") { onValueChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(20.dp).clip(CircleShape).background(colors.onSurfaceVariant.copy(alpha = .3f)), contentAlignment = Alignment.Center) {
                            StyledIcon(Icons.Rounded.Close, "清除", Modifier.size(14.dp), tint = colors.onSurface)
                        }
                    }
                }
            }
        },
    )
}

@Composable
internal fun OlbyineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    minLines: Int = 1,
) = GlassTextField(value, onValueChange, label, modifier, password = password, minLines = minLines)

@Composable
internal fun OlbyineDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    text: @Composable () -> Unit,
) = GlassDialog(onDismissRequest, title, confirmButton, dismissButton, text)
@Composable
internal fun rememberWindowBlurSupported(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(context) {
        android.os.Build.VERSION.SDK_INT >= 31 &&
            context.getSystemService(android.view.WindowManager::class.java)?.isCrossWindowBlurEnabled == true
    }
}

@Composable
internal fun WindowBlurBehind(radius: Dp, progress: (() -> Float)? = null) {
    val view = androidx.compose.ui.platform.LocalView.current
    val radiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { radius.roundToPx() }
    fun apply(px: Int) {
        if (android.os.Build.VERSION.SDK_INT < 31) return
        runCatching {
            val dialogWindow = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            if (dialogWindow != null) {
                dialogWindow.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                dialogWindow.attributes = dialogWindow.attributes.apply { blurBehindRadius = px }
            } else {
                val root = view.rootView
                val params = root.layoutParams as? android.view.WindowManager.LayoutParams
                val manager = view.context.getSystemService(android.view.WindowManager::class.java)
                if (params != null && manager != null && root.isAttachedToWindow) {
                    params.flags = params.flags or android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                    params.blurBehindRadius = px
                    manager.updateViewLayout(root, params)
                }
            }
        }
    }
    androidx.compose.runtime.DisposableEffect(view, radiusPx) {
        apply(if (progress == null) radiusPx else (radiusPx * progress().coerceIn(0f, 1f)).toInt())
        onDispose { }
    }
    if (progress != null) {
        LaunchedEffect(view, radiusPx) {
            var last = -1
            androidx.compose.runtime.snapshotFlow { (radiusPx * progress().coerceIn(0f, 1f)).toInt() }.collect { px ->
                if (px != last) {
                    apply(px)
                    last = px
                }
            }
        }
    }
}

@Composable
internal fun frostedBarStyle(): GlassStyle {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    if (LocalStyleSpec.current.style == com.olbyine.music.data.UiStyle.MATERIAL) {
        if (LocalPageBackdrop.current) {
            return GlassStyle(cornerRadius = 0.dp, blur = 24.dp, tint = colors.background.copy(alpha = if (dark) .66f else .60f),
                saturation = 1.5f, fallback = colors.surfaceContainer)
        }
        return GlassStyle(cornerRadius = 0.dp, blur = 0.dp, tint = colors.surfaceContainer, saturation = 1f, fallback = colors.surfaceContainer)
    }
    return GlassStyle(
        cornerRadius = 0.dp,
        blur = 24.dp,
        tint = colors.background.copy(alpha = if (dark) .70f else .74f),
        saturation = 2.1f,
        fallback = colors.background.copy(alpha = .97f),
        fadeEdge = BarEdge,
        tintFalloff = true,
    )
}

internal val LocalPageBackdrop = staticCompositionLocalOf { false }

@Composable
internal fun rememberBarAlpha(ramp: Dp, scrolled: () -> Float): () -> Float {
    if (LocalStyleSpec.current.style == com.olbyine.music.data.UiStyle.MATERIAL) {
        val under by remember { androidx.compose.runtime.derivedStateOf { scrolled() > 1f } }
        val fraction = animateFloatAsState(
            if (under) 1f else 0f,
            spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
            label = "bar-material",
        )
        return remember(fraction) { { fraction.value } }
    }
    val rampPx = with(androidx.compose.ui.platform.LocalDensity.current) { ramp.toPx() }.coerceAtLeast(1f)
    return remember(rampPx) { { scrolled() / rampPx } }
}

internal val BarEdge: Dp
    @Composable get() = if (LocalStyleSpec.current.style == com.olbyine.music.data.UiStyle.MATERIAL) 0.dp else 44.dp

internal class TopClipShape(private val heightPx: Float) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline =
        androidx.compose.ui.graphics.Outline.Rectangle(
            androidx.compose.ui.geometry.Rect(0f, 0f, size.width, heightPx.coerceIn(0f, size.height)),
        )
}

@Composable
internal fun LibraryLoading(modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OlbyineSpinner(
            Modifier.size(28.dp),
            strokeWidth = 2.5.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(14.dp))
        Text("正在载入音乐库…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val entrance = rememberEntrance()
    Column(
        modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.then(entrance.item(0, scaleFrom = .86f)).size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)),
            contentAlignment = Alignment.Center,
        ) {
            StyledIcon(icon, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(18.dp))
        Text(title, entrance.item(1), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(6.dp))
            Text(message, entrance.item(2), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            Box(entrance.item(3)) { action() }
        }
    }
}

@Composable
internal fun EqualizerBars(playing: Boolean, color: Color, modifier: Modifier = Modifier.size(16.dp)) {
    val levels = remember { FloatArray(4) { EQ_REST[it] } }
    val tick = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    @Suppress("NAME_SHADOWING")
    val playing = playing && !LocalReduceMotion.current
    LaunchedEffect(playing) {
        val start = System.nanoTime()
        var last = 0L
        while (true) {
            var settled = false
            androidx.compose.runtime.withFrameNanos { now: Long ->
                val dt = if (last == 0L) 1f / 60f else ((now - last) / 1e9f).coerceIn(0f, .05f)
                last = now
                val t = (now - start) / 1e9f
                val live = playing && System.nanoTime() - com.olbyine.music.playback.AudioEnergy.stamp < 250_000_000L
                var moving = false
                for (i in 0 until 4) {
                    val target = when {
                        !playing -> EQ_REST[i]
                        live -> (.16f + .84f * com.olbyine.music.playback.AudioEnergy.bands[i].let { it * (1.15f - .15f * it) }).coerceIn(.16f, 1f)
                        else -> {
                            val phase = t * EQ_SPEEDS[i] + i * 1.7f
                            .2f + .8f * (0.5f + 0.5f * kotlin.math.sin(phase)).let { it * it * (3f - 2f * it) }
                        }
                    }
                    val rate = if (target > levels[i]) 38f else 14f
                    levels[i] += (target - levels[i]) * (1f - kotlin.math.exp(-dt * rate))
                    if (kotlin.math.abs(target - levels[i]) > .002f) moving = true
                }
                tick.intValue++
                settled = !playing && !moving
            }
            if (settled) break
        }
    }
    Canvas(modifier) {
        tick.intValue
        val gap = size.width / 11f
        val barWidth = (size.width - gap * 3f) / 4f
        for (index in 0 until 4) {
            val height = (size.height * levels[index]).coerceAtLeast(barWidth)
            drawRoundRect(
                color = color,
                topLeft = Offset((barWidth + gap) * index, size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

private val EQ_SPEEDS = floatArrayOf(7.1f, 9.3f, 6.2f, 8.4f)
private val EQ_REST = floatArrayOf(.42f, .78f, .56f, .34f)

@Composable
internal fun TrackArtwork(
    track: Track,
    modifier: Modifier,
    corner: Dp,
    decodeEdge: Int = SMALL_COVER_EDGE,
) {
    val key = track.coverPath ?: track.artworkUrl
    var bitmap by remember(key, decodeEdge) {
        mutableStateOf<ImageBitmap?>(
            key?.let { coverCache.get(it to decodeEdge)?.asImageBitmap() },
        )
    }
    var previous by remember(track.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(key, decodeEdge) {
        if (bitmap != null || key == null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            coverDecodeSemaphore.withPermit {
                if (track.coverPath != null) decodeCover(track.coverPath, decodeEdge)?.asImageBitmap()
                else runCatching { decodeRemote(key, decodeEdge) }.getOrNull()?.asImageBitmap()
            }
        }
    }
    val current = bitmap
    androidx.compose.runtime.SideEffect { if (current != null && previous !== current) previous = current }
    val shown = current ?: previous
    val fade = remember(track.id) { androidx.compose.animation.core.Animatable(if (shown != null) 1f else 0f) }
    LaunchedEffect(shown != null) {
        if (shown != null && fade.value < 1f) fade.animateTo(1f, tween(OlbyineTokens.Motion.quickMs + 40, easing = OlbyineTokens.Motion.easeOut))
    }
    val placeholder = remember(track.id) { placeholderBrush(track.id) }
    Box(
        modifier = modifier.clip(RoundedCornerShape(corner)).drawBehind {
            if (shown == null || fade.value < 1f) drawRect(placeholder)
        },
        contentAlignment = Alignment.Center,
    ) {
        val image = shown
        if (image != null) {
            androidx.compose.foundation.Image(
                bitmap = image,
                contentDescription = "${track.title} 封面",
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fade.value },
                contentScale = ContentScale.Crop,
            )
        } else {
            StyledIcon(Icons.Rounded.MusicNote, null, Modifier.fillMaxSize(.36f), tint = Color.White.copy(alpha = .9f))
        }
    }
}

private fun placeholderBrush(seed: String): Brush {
    val hue = ((seed.hashCode().toLong() and 0x7fffffffL) % 360L).toFloat()
    val first = Color.hsv(hue, .42f, .62f)
    val second = Color.hsv((hue + 48f) % 360f, .50f, .42f)
    return Brush.linearGradient(listOf(first, second))
}

@Composable
internal fun CoverMosaic(tracks: List<Track>, modifier: Modifier, corner: Dp, seed: String) {
    Box(modifier.clip(RoundedCornerShape(corner))) {
        when {
            tracks.size >= 4 -> Column(Modifier.fillMaxSize()) {
                for (row in 0 until 2) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (column in 0 until 2) {
                            TrackArtwork(tracks[row * 2 + column], Modifier.weight(1f).fillMaxSize(), 0.dp)
                        }
                    }
                }
            }
            tracks.isNotEmpty() -> TrackArtwork(tracks.first(), Modifier.fillMaxSize(), 0.dp, decodeEdge = 512)
            else -> Box(
                Modifier.fillMaxSize().background(placeholderBrush(seed)),
                contentAlignment = Alignment.Center,
            ) {
                StyledIcon(Icons.AutoMirrored.Rounded.QueueMusic, null, Modifier.fillMaxSize(.34f), tint = Color.White.copy(alpha = .9f))
            }
        }
    }
}

@Composable
internal fun PathImage(
    path: String?,
    modifier: Modifier,
    maxEdge: Int = 720,
    contentDescription: String? = null,
    placeholder: @Composable () -> Unit = {},
) {
    var bitmap by remember(path, maxEdge) {
        mutableStateOf(path?.let { coverCache.get(it to maxEdge)?.asImageBitmap() })
    }
    LaunchedEffect(path, maxEdge) {
        if (bitmap != null || path == null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            coverDecodeSemaphore.withPermit { runCatching { decodeCover(path, maxEdge) }.getOrNull()?.asImageBitmap() }
        }
    }
    val fade = remember(path, maxEdge) { androidx.compose.animation.core.Animatable(if (bitmap != null) 1f else 0f) }
    var covered by remember(path, maxEdge) { mutableStateOf(bitmap != null) }
    LaunchedEffect(bitmap) {
        if (bitmap != null) {
            fade.animateTo(1f, tween(OlbyineTokens.Motion.quickMs + 40, easing = OlbyineTokens.Motion.easeOut))
            covered = true
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image == null || !covered) placeholder()
        if (image != null) {
            androidx.compose.foundation.Image(image, contentDescription, Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }, contentScale = ContentScale.Crop)
        }
    }
}

private val remoteImageCache = android.util.LruCache<String, android.graphics.Bitmap>(24)

@Composable
internal fun UrlImage(url: String?, modifier: Modifier, placeholder: @Composable () -> Unit = {}) {
    var bitmap by remember(url) { mutableStateOf(url?.let { remoteImageCache.get(it)?.asImageBitmap() }) }
    LaunchedEffect(url) {
        if (bitmap != null || url == null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 8_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                val bytes = connection.inputStream.use { it.readBytes() }
                connection.disconnect()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.also { remoteImageCache.put(url, it) }
            }.getOrNull()?.asImageBitmap()
        }
    }
    val fade = remember(url) { androidx.compose.animation.core.Animatable(if (bitmap != null) 1f else 0f) }
    var covered by remember(url) { mutableStateOf(bitmap != null) }
    LaunchedEffect(bitmap) {
        if (bitmap != null) {
            fade.animateTo(1f, tween(150, easing = OlbyineTokens.Motion.easeOut))
            covered = true
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image == null || !covered) placeholder()
        if (image != null) {
            androidx.compose.foundation.Image(
                image,
                null,
                Modifier.fillMaxSize().graphicsLayer { alpha = fade.value },
                contentScale = ContentScale.Crop,
            )
        }
    }
}

internal fun invalidateImage(path: String) {
    coverCache.snapshot().keys.filter { it.first == path }.forEach { coverCache.remove(it) }
}

private val remoteLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()

internal fun remoteArtworkFile(url: String): java.io.File? {
    val directory = java.io.File(System.getProperty("java.io.tmpdir") ?: return null, "remote-art").apply { mkdirs() }
    val file = java.io.File(directory, url.hashCode().toUInt().toString(16) + "-" + url.length + ".img")
    if (file.length() > 0L) return file
    synchronized(remoteLocks.getOrPut(url) { Any() }) {
        if (file.length() > 0L) return file
        repeat(3) { attempt ->
            val ok = runCatching {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 8_000
                connection.readTimeout = 12_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                val bytes = try {
                    require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
                    connection.inputStream.use { it.readBytes() }
                } finally {
                    connection.disconnect()
                }
                require(bytes.size in 64..8 * 1024 * 1024)
                val temporary = java.io.File(directory, file.name + ".part")
                temporary.writeBytes(bytes)
                check(temporary.renameTo(file))
            }.isSuccess
            if (ok) return file
            Thread.sleep(400L * (attempt + 1))
        }
    }
    return null
}

internal suspend fun remoteArtworkPath(url: String?): String? =
    url?.let { withContext(Dispatchers.IO) { remoteArtworkFile(it)?.absolutePath } }

private fun decodeRemote(url: String, maxEdge: Int): android.graphics.Bitmap? {
    coverCache.get(url to maxEdge)?.let { return it }
    val file = remoteArtworkFile(url) ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > maxEdge || bounds.outHeight / sampleSize > maxEdge) sampleSize *= 2
    return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        ?.also { coverCache.put(url to maxEdge, it) }
}

private fun decodeCover(path: String, maxEdge: Int): android.graphics.Bitmap? {
    val key = path to maxEdge
    coverCache.get(key)?.let { return it }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > maxEdge || bounds.outHeight / sampleSize > maxEdge) {
        sampleSize *= 2
    }
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        ?.also { coverCache.put(key, it) }
}

internal const val SMALL_COVER_EDGE = 256
internal const val LARGE_COVER_EDGE = 1_024
private val coverDecodeSemaphore = Semaphore(2)
private val coverCache = object : android.util.LruCache<Pair<String, Int>, android.graphics.Bitmap>(32 * 1_024) {
    override fun sizeOf(key: Pair<String, Int>, value: android.graphics.Bitmap): Int = value.byteCount / 1_024
}

@Composable
internal fun rememberPlaybackPosition(
    playback: PlaybackState,
    intervalMs: Long,
    enabled: Boolean = true,
): Long {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var positionMs by remember(playback.currentMediaId) {
        mutableLongStateOf(playback.estimatedPosition())
    }
    LaunchedEffect(
        playback.currentMediaId,
        playback.sampledAtElapsedMs,
        playback.isPlaying,
        playback.pendingSeekMs,
        intervalMs,
        enabled,
        lifecycle,
    ) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            positionMs = playback.estimatedPosition()
            while (enabled && playback.isPlaying && playback.pendingSeekMs == null) {
                delay(intervalMs)
                positionMs = playback.estimatedPosition()
            }
        }
    }
    return positionMs
}

@Composable
internal fun rememberPlaybackClock(
    playback: PlaybackState,
    enabled: Boolean = true,
    intervalMs: Long = 0L,
): State<Long> {
    val clock = remember(playback.currentMediaId) { mutableLongStateOf(playback.estimatedPosition()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(
        playback.currentMediaId,
        playback.sampledAtElapsedMs,
        playback.isPlaying,
        playback.pendingSeekMs,
        playback.playbackSpeed,
        enabled,
        intervalMs,
    ) {
        clock.longValue = playback.estimatedPosition()
        if (!enabled || !playback.isPlaying || playback.pendingSeekMs != null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                if (intervalMs > 0L) delay(intervalMs) else androidx.compose.runtime.withFrameMillis { }
                clock.longValue = playback.estimatedPosition()
            }
        }
    }
    return clock
}

internal fun formatDuration(value: Long): String {
    val totalSeconds = value.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    return if (hours > 0) "%d:%02d:%02d".format(hours, (totalSeconds / 60L) % 60L, totalSeconds % 60L)
    else "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

internal fun formatTotalDuration(value: Long): String {
    val minutes = (value.coerceAtLeast(0L) / 60_000L)
    return if (minutes >= 60) "${minutes / 60} 小时 ${minutes % 60} 分钟" else "$minutes 分钟"
}

internal fun Track.formatLabel(): String? {
    val extension = displayName.substringAfterLast('.', "").lowercase()
    val mime = mimeType.orEmpty().lowercase()
    return when {
        extension == "flac" || "flac" in mime -> "FLAC"
        extension in setOf("m4a", "mp4", "alac") || "mp4" in mime -> "M4A"
        extension == "mp3" || "mpeg" in mime -> "MP3"
        extension in setOf("wav", "wave") || "wav" in mime -> "WAV"
        extension in setOf("dsf", "dff") -> "DSD"
        extension in setOf("ogg", "opus", "ape", "aac", "aiff", "aif", "wma") -> extension.uppercase()
        else -> null
    }
}
