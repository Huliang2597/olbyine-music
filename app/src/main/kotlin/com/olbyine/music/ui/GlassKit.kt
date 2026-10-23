package com.olbyine.music.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.olbyine.music.data.UiStyle

internal val LocalButtonEmphasis = compositionLocalOf { false }

@Composable
private fun paneColor(blur: Boolean): Color {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    return colors.surfaceContainerHigh.copy(alpha = if (!blur) .98f else if (dark) .70f else .76f)
}

@Composable
private fun rimBrush(): Brush {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    return Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) .26f else .75f), Color.White.copy(alpha = .04f)))
}

@Composable
private fun sheenBrush(): Brush {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    return Brush.verticalGradient(0f to Color.White.copy(alpha = if (dark) .07f else .28f), .35f to Color.Transparent)
}

internal val LocalDialogAction = compositionLocalOf { false }

@Composable
private fun DialogWindowLook(blur: Boolean, bottom: Boolean, progress: () -> Float) {
    val view = LocalView.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val window = (view.parent as? DialogWindowProvider)?.window
    val maxBlur = with(density) { 36.dp.roundToPx() }
    val maxDim = if (blur) .22f else .42f
    DisposableEffect(window, blur, bottom) {
        window?.let {
            it.setWindowAnimations(0)
            it.setDimAmount(0f)
            if (bottom) it.setGravity(android.view.Gravity.BOTTOM)
            if (blur && Build.VERSION.SDK_INT >= 31) it.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        }
        onDispose { }
    }
    LaunchedEffect(window, blur) {
        val target = window ?: return@LaunchedEffect
        var lastRadius = -1
        androidx.compose.runtime.snapshotFlow { progress().coerceIn(0f, 1f) }.collect { p ->
            runCatching {
                target.setDimAmount(maxDim * p)
                val radius = (maxBlur * p).toInt()
                if (blur && Build.VERSION.SDK_INT >= 31 && radius != lastRadius) {
                    target.attributes = target.attributes.apply { blurBehindRadius = radius }
                    lastRadius = radius
                }
            }
        }
    }
}

@Composable
internal fun GlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    text: @Composable () -> Unit,
) {
    val style = LocalStyleSpec.current.style
    if (style == UiStyle.MATERIAL) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
            title = { Text(title) },
            text = text,
        )
        return
    }
    val blur = rememberWindowBlurSupported()
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    val reduce = LocalReduceMotion.current
    val hyper = style == UiStyle.HYPEROS
    val appear = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val currentDismiss by androidx.compose.runtime.rememberUpdatedState(onDismissRequest)
    val dismiss: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                appear.animateTo(0f, tween(if (hyper) 220 else 170, easing = OlbyineTokens.Motion.easeOut))
                currentDismiss()
            }
        }
    }
    LaunchedEffect(Unit) {
        appear.animateTo(
            1f,
            when {
                reduce -> tween(160)
                style == UiStyle.COLOROS -> appleSpring(response = .32f, damping = .96f)
                hyper -> appleSpring(response = .38f, damping = .9f)
                else -> appleSpring(response = .3f)
            },
        )
    }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogWindowLook(blur, bottom = hyper) { appear.value }
        val corner = when (style) { UiStyle.COLOROS -> 26.dp; else -> 32.dp }
        val shape = RoundedCornerShape(corner)
        val pane = when {
            style == UiStyle.IOS && blur -> if (dark) Color(0xFF2C2C2E).copy(alpha = .78f) else Color(0xFFF7F7F9).copy(alpha = .82f)
            else -> colors.surfaceContainer
        }
        val density = androidx.compose.ui.platform.LocalDensity.current
        val rise = with(density) { 220.dp.toPx() }
        Column(
            Modifier
                .padding(horizontal = if (hyper) 12.dp else 30.dp, vertical = if (hyper) 12.dp else 24.dp)
                .then(if (hyper) Modifier.navigationBarsPadding() else Modifier)
                .widthIn(max = if (hyper) 520.dp else 400.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val p = appear.value
                    alpha = p.coerceIn(0f, 1f)
                    if (!reduce) {
                        when (style) {
                            UiStyle.IOS -> if (!closing) {
                                val s = 1.12f - .12f * p
                                scaleX = s
                                scaleY = s
                            }
                            UiStyle.COLOROS -> {
                                val s = .9f + .1f * p
                                scaleX = s
                                scaleY = s
                            }
                            else -> translationY = (1f - p) * rise
                        }
                    }
                }
                .shadow(if (style == UiStyle.IOS) 0.dp else 24.dp, shape, ambientColor = Color.Black.copy(alpha = .25f), spotColor = Color.Black.copy(alpha = .25f))
                .clip(shape)
                .background(pane)
                .padding(top = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = if (style == UiStyle.IOS) 17.sp else 18.sp),
                fontWeight = if (style == UiStyle.COLOROS) FontWeight.Bold else FontWeight.SemiBold,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            CompositionLocalProvider(
                LocalContentColor provides colors.onSurfaceVariant,
                LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
            ) {
                Box(Modifier.weight(1f, fill = false).padding(horizontal = 24.dp), contentAlignment = Alignment.TopCenter) {
                    CompositionLocalProvider(LocalOnGlass provides true) { text() }
                }
            }
            Spacer(Modifier.height(if (style == UiStyle.COLOROS) 18.dp else 20.dp))
            CompositionLocalProvider(LocalOnGlass provides true, LocalDialogAction provides true) {
                if (style == UiStyle.COLOROS) {
                    androidx.compose.material3.HorizontalDivider(thickness = .5.dp, color = colors.outlineVariant)
                    Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (dismissButton != null) {
                            Box(Modifier.weight(1f).fillMaxHeight()) { dismissButton() }
                            Box(Modifier.width(.5.dp).height(22.dp).background(colors.outlineVariant))
                        }
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            CompositionLocalProvider(LocalButtonEmphasis provides true) { confirmButton() }
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (dismissButton != null) Box(Modifier.weight(1f)) { dismissButton() }
                        Box(Modifier.weight(1f)) {
                            CompositionLocalProvider(LocalButtonEmphasis provides true) { confirmButton() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GlassTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val style = LocalStyleSpec.current.style
    if (style == UiStyle.MATERIAL) {
        androidx.compose.material3.TextButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
        return
    }
    val emphasis = LocalButtonEmphasis.current
    val inDialog = LocalDialogAction.current
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val press = rememberPressProgress(interaction)
    val textAction = inDialog && style == UiStyle.COLOROS
    val fill = when {
        textAction -> Color.Transparent
        emphasis -> colors.primary
        style == UiStyle.HYPEROS -> colors.surfaceContainerHigh
        else -> colors.onSurface.copy(alpha = if (dark) .14f else .075f)
    }
    val ink = when {
        textAction -> if (emphasis) colors.primary else colors.onSurface
        emphasis -> colors.onPrimary
        style == UiStyle.IOS && !inDialog -> colors.primary
        else -> colors.onSurface
    }
    val corner = when {
        textAction -> 0.dp
        style == UiStyle.HYPEROS -> 16.dp
        else -> 24.dp
    }
    val shape = RoundedCornerShape(corner)
    val alpha by animateFloatAsState(if (enabled) 1f else .4f, tween(OlbyineTokens.Motion.quickMs), label = "text-button-enabled")
    Row(
        modifier
            .then(if (inDialog) Modifier.fillMaxWidth().then(if (textAction) Modifier.fillMaxHeight() else Modifier.height(48.dp)) else Modifier.height(42.dp))
            .graphicsLayer {
                val p = press.value
                val s = 1f - (if (textAction) 0f else .035f) * p
                scaleX = s
                scaleY = s
                this.alpha = alpha * (1f - (if (style == UiStyle.IOS) .18f else 0f) * p)
            }
            .clip(shape)
            .background(fill)
            .then(
                if (style == UiStyle.IOS && emphasis && !textAction) {
                    Modifier.background(Brush.verticalGradient(0f to Color.White.copy(alpha = .18f), .55f to Color.Transparent))
                } else Modifier,
            )
            .drawWithContent {
                drawContent()
                val veil = if (style == UiStyle.IOS) 0f else press.value * (if (dark) .12f else .07f)
                if (veil > 0f) drawRect((if (emphasis && !textAction) Color.Black else colors.onSurface).copy(alpha = veil))
            }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) {
                haptics.click(com.olbyine.music.data.HapticArea.MENUS)
                onClick()
            }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides ink,
            LocalTextStyle provides MaterialTheme.typography.labelLarge.copy(
                fontSize = if (inDialog) 16.sp else 14.sp,
                fontWeight = if (emphasis || style != UiStyle.COLOROS) FontWeight.SemiBold else FontWeight.Medium,
            ),
        ) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlassBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState? = null,
    dismissBlocked: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val blocked = androidx.compose.runtime.rememberUpdatedState(dismissBlocked)
    val ownState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value -> value != androidx.compose.material3.SheetValue.Hidden || !blocked.value },
    )
    val state = sheetState ?: ownState
    val anchored = remember(state) { mutableIntStateOf(0) }
    LaunchedEffect(anchored.intValue, state.isVisible) {
        if (anchored.intValue > 0 && state.isVisible) runCatching { state.expand() }
    }
    if (LocalStyleSpec.current.style == UiStyle.MATERIAL) {
        ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = state) {
            Column(Modifier.onSizeChanged { anchored.intValue = it.height }) { content() }
        }
        return
    }
    val blur = rememberWindowBlurSupported()
    val colors = MaterialTheme.colorScheme
    val shown = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = paneColor(blur),
        contentColor = colors.onSurface,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = .24f),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 5.dp).clip(CircleShape).background(colors.onSurface.copy(alpha = .22f)))
            }
        },
    ) {
        if (blur) WindowBlurBehind(radius = 36.dp) { shown.floatValue }
        Column(
            Modifier
                .onSizeChanged { size -> anchored.intValue = size.height }
                .onGloballyPositioned { coordinates ->
                val window = coordinates.findRootCoordinates().size.height.toFloat()
                val top = coordinates.positionInRoot().y
                val height = coordinates.size.height.toFloat().coerceAtLeast(1f)
                shown.floatValue = ((window - top) / height).coerceIn(0f, 1f)
            },
        ) {
            androidx.compose.runtime.CompositionLocalProvider(LocalOnGlass provides true) { content() }
        }
    }
}

@Composable
internal fun GlassCheck(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    if (LocalStyleSpec.current.style == UiStyle.MATERIAL) {
        androidx.compose.material3.Checkbox(checked, onCheckedChange, modifier)
        return
    }
    val colors = MaterialTheme.colorScheme
    val on by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = .8f, stiffness = 700f), label = "glass-check")
    Box(
        modifier
            .padding(10.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(lerp(colors.onSurface.copy(alpha = .08f), colors.primary, on))
            .border(1.5.dp, lerp(colors.onSurfaceVariant.copy(alpha = .5f), colors.primary, on), CircleShape)
            .clickable(role = Role.Checkbox) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Check,
            null,
            Modifier.size(15.dp).graphicsLayer {
                alpha = on
                scaleX = .4f + .6f * on
                scaleY = .4f + .6f * on
            },
            tint = colors.onPrimary,
        )
    }
}

@Composable
internal fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (minLines > 1) 10 else 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    var reveal by remember { mutableStateOf(false) }
    if (LocalStyleSpec.current.style == UiStyle.MATERIAL) {
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = maxLines == 1,
            minLines = minLines,
            maxLines = maxLines,
            visualTransformation = if (password && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = if (password) keyboardOptions.copy(keyboardType = KeyboardType.Password) else keyboardOptions,
            trailingIcon = if (password) {
                { androidx.compose.material3.IconButton(onClick = { reveal = !reveal }) { StyledIcon(if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (reveal) "隐藏密码" else "显示密码") } }
            } else null,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }
    val colors = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val rim by animateColorAsState(if (focused) colors.primary else colors.onSurface.copy(alpha = .12f), tween(180), label = "field-rim")
    val shape = RoundedCornerShape(16.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        singleLine = maxLines == 1,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        visualTransformation = if (password && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (password) keyboardOptions.copy(keyboardType = KeyboardType.Password) else keyboardOptions,
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .clip(shape)
                    .background(colors.onSurface.copy(alpha = .06f))
                    .border(if (focused) 1.5.dp else 1.dp, rim, shape)
                    .padding(start = 16.dp, end = if (password) 4.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = 9.dp)) {
                    AnimatedVisibility(
                        visible = focused || value.isNotEmpty(),
                        enter = fadeIn(tween(150)) + expandVertically(tween(160)),
                        exit = fadeOut(tween(100)) + shrinkVertically(tween(140)),
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (focused) colors.primary else colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Box {
                        if (value.isEmpty() && !focused) {
                            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant.copy(alpha = .85f), maxLines = 1)
                        }
                        inner()
                    }
                }
                if (password) {
                    GhostIconButton(
                        if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        if (reveal) "隐藏密码" else "显示密码",
                        { reveal = !reveal },
                        size = 40.dp,
                        iconSize = 20.dp,
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
        },
    )
}
