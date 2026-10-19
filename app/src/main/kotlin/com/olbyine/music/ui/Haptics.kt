package com.olbyine.music.ui

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch
import com.olbyine.music.data.HapticArea

@Stable
internal class Haptics(context: Context, private val view: android.view.View? = null) {
    var enabled: Boolean = true
    var areas: Int = HapticArea.DEFAULT

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else context.getSystemService(Vibrator::class.java)
    }.getOrNull()?.takeIf { it.hasVibrator() }

    private val primitives = if (Build.VERSION.SDK_INT < 31) false else vibrator?.let {
        it.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_TICK,
            VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
        )
    } ?: false

    private val attributes: VibrationAttributes? =
        if (Build.VERSION.SDK_INT >= 33) VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH) else null

    private val tickEffect = effect(VibrationEffect.Composition.PRIMITIVE_TICK, .85f, VibrationEffect.EFFECT_TICK)
    private val lightEffect = effect(VibrationEffect.Composition.PRIMITIVE_TICK, .55f, VibrationEffect.EFFECT_TICK)
    private val clickEffect = effect(VibrationEffect.Composition.PRIMITIVE_CLICK, .85f, VibrationEffect.EFFECT_CLICK)
    private val heavyEffect = effect(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, VibrationEffect.EFFECT_HEAVY_CLICK)
    private val confirmEffect: VibrationEffect? = runCatching {
        if (primitives && Build.VERSION.SDK_INT >= 31) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, .7f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, .5f, 70)
                .compose()
        } else VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
    }.getOrNull()

    fun tab(area: Int) = play(area, clickEffect, android.view.HapticFeedbackConstants.CONTEXT_CLICK, force = true)

    fun tick(area: Int) = play(area, tickEffect, android.view.HapticFeedbackConstants.CLOCK_TICK)

    fun light(area: Int) = play(area, lightEffect, android.view.HapticFeedbackConstants.CLOCK_TICK)

    fun click(area: Int) = play(area, clickEffect, android.view.HapticFeedbackConstants.CONTEXT_CLICK)

    fun heavy(area: Int) = play(area, heavyEffect, android.view.HapticFeedbackConstants.LONG_PRESS)

    fun confirm(area: Int) = play(area, confirmEffect,
        if (Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM else android.view.HapticFeedbackConstants.CONTEXT_CLICK)

    private var lastPlayed = 0L

    private fun play(area: Int, effect: VibrationEffect?, fallback: Int, force: Boolean = false) {
        if (!enabled || areas and area == 0) return
        val now = android.os.SystemClock.uptimeMillis()
        if (!force && now - lastPlayed < 60) return
        lastPlayed = now
        val target = vibrator
        val played = effect != null && target != null && runCatching {
            if (attributes != null && Build.VERSION.SDK_INT >= 33) target.vibrate(effect, attributes)
            else @Suppress("DEPRECATION") target.vibrate(effect)
        }.isSuccess
        if (!played) view?.performHapticFeedback(fallback)
    }

    private fun effect(primitive: Int, scale: Float, fallback: Int): VibrationEffect? = runCatching {
        if (primitives && Build.VERSION.SDK_INT >= 31) VibrationEffect.startComposition().addPrimitive(primitive, scale).compose()
        else VibrationEffect.createPredefined(fallback)
    }.getOrNull()
}

internal val LocalHaptics = staticCompositionLocalOf<Haptics?> { null }

@Composable
internal fun rememberHaptics(): Haptics {
    val provided = LocalHaptics.current
    val context = LocalContext.current
    return provided ?: remember(context) { Haptics(context).also { it.enabled = false } }
}

private class HapticIndication(
    private val base: androidx.compose.foundation.IndicationNodeFactory,
    private val haptics: Haptics,
) : androidx.compose.foundation.IndicationNodeFactory {
    override fun create(interactionSource: androidx.compose.foundation.interaction.InteractionSource): androidx.compose.ui.node.DelegatableNode =
        HapticIndicationNode(interactionSource, base.create(interactionSource), haptics)

    override fun equals(other: Any?) = other is HapticIndication && other.base == base && other.haptics === haptics
    override fun hashCode() = base.hashCode() * 31 + haptics.hashCode()
}

private class HapticIndicationNode(
    private val source: androidx.compose.foundation.interaction.InteractionSource,
    inner: androidx.compose.ui.node.DelegatableNode,
    private val haptics: Haptics,
) : androidx.compose.ui.node.DelegatingNode(), androidx.compose.ui.node.GlobalPositionAwareModifierNode {
    private var coordinates: androidx.compose.ui.layout.LayoutCoordinates? = null

    init {
        delegate(inner)
    }

    override fun onGloballyPositioned(coordinates: androidx.compose.ui.layout.LayoutCoordinates) {
        this.coordinates = coordinates
    }

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                if (interaction is androidx.compose.foundation.interaction.PressInteraction.Press) {
                    coordinates?.takeIf { it.isAttached }?.let { AppTouchOrigin.press(it.boundsInRoot()) }
                }
                if (interaction is androidx.compose.foundation.interaction.PressInteraction.Release) haptics.light(HapticArea.MENUS)
            }
        }
    }
}

@Composable
internal fun HapticRipple(content: @Composable () -> Unit) {
    val haptics = LocalHaptics.current
    val style = LocalStyleSpec.current.style
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    val ripple = androidx.compose.foundation.LocalIndication.current
    val base: androidx.compose.foundation.Indication = when (style) {
        com.olbyine.music.data.UiStyle.MATERIAL -> ripple
        else -> remember(style, colors.onSurface, dark) {
            StylePressIndication(
                kind = when (style) {
                    com.olbyine.music.data.UiStyle.COLOROS -> StylePress.SINK
                    com.olbyine.music.data.UiStyle.HYPEROS -> StylePress.DARKEN
                    else -> StylePress.HIGHLIGHT
                },
                overlay = when (style) {
                    com.olbyine.music.data.UiStyle.HYPEROS -> if (dark) Color.White.copy(alpha = .10f) else Color.Black.copy(alpha = .07f)
                    com.olbyine.music.data.UiStyle.COLOROS -> colors.onSurface.copy(alpha = if (dark) .08f else .05f)
                    else -> colors.onSurface.copy(alpha = if (dark) .14f else .10f)
                },
            )
        }
    }
    if (haptics == null || base !is androidx.compose.foundation.IndicationNodeFactory || base is HapticIndication) {
        content()
        return
    }
    val wrapped = remember(base, haptics) { HapticIndication(base, haptics) }
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.LocalIndication provides wrapped, content = content)
}

internal enum class StylePress {
    HIGHLIGHT,
    SINK,
    DARKEN,
}

private class StylePressIndication(val kind: StylePress, val overlay: Color) : androidx.compose.foundation.IndicationNodeFactory {
    override fun create(interactionSource: androidx.compose.foundation.interaction.InteractionSource): androidx.compose.ui.node.DelegatableNode =
        StylePressNode(interactionSource, kind, overlay)

    override fun equals(other: Any?) = other is StylePressIndication && other.kind == kind && other.overlay == overlay
    override fun hashCode() = kind.hashCode() * 31 + overlay.hashCode()
}

private class StylePressNode(
    private val source: androidx.compose.foundation.interaction.InteractionSource,
    private val kind: StylePress,
    private val overlay: Color,
) : androidx.compose.ui.Modifier.Node(), androidx.compose.ui.node.DrawModifierNode {
    private val progress = androidx.compose.animation.core.Animatable(0f)
    private var job: kotlinx.coroutines.Job? = null
    private var downAt = 0L

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is androidx.compose.foundation.interaction.PressInteraction.Press -> {
                        downAt = android.os.SystemClock.uptimeMillis()
                        job?.cancel()
                        job = coroutineScope.launch {
                            progress.animateTo(1f, if (kind == StylePress.SINK) appleSpring(.16f) else androidx.compose.animation.core.tween(60)) { invalidateDraw() }
                        }
                    }
                    is androidx.compose.foundation.interaction.PressInteraction.Release,
                    is androidx.compose.foundation.interaction.PressInteraction.Cancel -> {
                        job?.cancel()
                        val held = android.os.SystemClock.uptimeMillis() - downAt
                        job = coroutineScope.launch {
                            if (held < 90) {
                                progress.animateTo(1f, androidx.compose.animation.core.tween(40)) { invalidateDraw() }
                                kotlinx.coroutines.delay(90 - held)
                            }
                            progress.animateTo(
                                0f,
                                when (kind) {
                                    StylePress.SINK -> appleSpring(.28f, damping = .9f)
                                    StylePress.HIGHLIGHT -> androidx.compose.animation.core.tween(280, easing = OlbyineTokens.Motion.easeOut)
                                    StylePress.DARKEN -> androidx.compose.animation.core.tween(180, easing = OlbyineTokens.Motion.easeOut)
                                },
                            ) { invalidateDraw() }
                        }
                    }
                }
            }
        }
    }

    override fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.draw() {
        val p = progress.value
        if (kind == StylePress.SINK && p != 0f) {
            val s = 1f - .02f * p
            scale(s, s) { this@draw.drawContent() }
        } else {
            drawContent()
        }
        if (p > 0.001f) drawRect(overlay.copy(alpha = overlay.alpha * p.coerceIn(0f, 1f)))
    }
}