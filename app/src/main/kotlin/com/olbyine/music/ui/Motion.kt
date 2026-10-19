package com.olbyine.music.ui

import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs

internal fun <T> appleSpring(response: Float, damping: Float = 1f, visibilityThreshold: T? = null): SpringSpec<T> {
    val omega = 2f * PI.toFloat() / response
    return spring(dampingRatio = damping, stiffness = omega * omega, visibilityThreshold = visibilityThreshold)
}

internal fun projectFling(velocityPerSecond: Float, decelerationRate: Float = .998f): Float =
    velocityPerSecond / 1000f * decelerationRate / (1f - decelerationRate)

internal fun rubberband(overshoot: Float, dimension: Float, constant: Float = .55f): Float {
    if (dimension <= 0f) return 0f
    return overshoot * dimension * constant / (dimension + constant * abs(overshoot))
}

internal fun rubberbandSlope(overshoot: Float, dimension: Float, constant: Float = .55f): Float {
    if (dimension <= 0f) return 0f
    val d = dimension + constant * abs(overshoot)
    return dimension * dimension * constant / (d * d)
}

internal fun rubberbandClamp(value: Float, min: Float, max: Float, dimension: Float): Float = when {
    value < min -> min + rubberband(value - min, dimension)
    value > max -> max + rubberband(value - max, dimension)
    else -> value
}

internal val PressInSpring: SpringSpec<Float> = appleSpring(response = .12f)

internal val PressOutSpring: SpringSpec<Float> = appleSpring(response = .24f, damping = .92f)

private const val MIN_PRESS_MS = 90L

@Composable
internal fun rememberPressProgress(interaction: InteractionSource): State<Float> {
    val progress = remember(interaction) { Animatable(0f) }
    LaunchedEffect(interaction) {
        var downAt = 0L
        var job: Job? = null
        interaction.interactions.collect { event ->
            when (event) {
                is PressInteraction.Press -> {
                    downAt = SystemClock.uptimeMillis()
                    job?.cancel()
                    job = launch { progress.animateTo(1f, PressInSpring) }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    job?.cancel()
                    val held = SystemClock.uptimeMillis() - downAt
                    job = launch {
                        if (held < MIN_PRESS_MS) {
                            progress.animateTo(1f, PressInSpring)
                            delay((MIN_PRESS_MS - held).coerceAtLeast(0L))
                        }
                        progress.animateTo(0f, PressOutSpring)
                    }
                }
            }
        }
    }
    return progress.asState()
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun AnimatedVisibilityScope.morphBlur(radius: Dp = 3.dp): Modifier {
    if (Build.VERSION.SDK_INT < 31) return Modifier
    val amount by transition.animateFloat(
        transitionSpec = { tween(OlbyineTokens.Motion.quickMs + 40, easing = OlbyineTokens.Motion.easeOut) },
        label = "morph-blur",
    ) { if (it == EnterExitState.Visible) 0f else 1f }
    return Modifier.graphicsLayer {
        val px = radius.toPx() * amount
        renderEffect = if (px >= 1f) BlurEffect(px, px, TileMode.Decal) else null
    }
}

internal val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
internal fun rememberSystemReduceMotion(): Boolean {
    val resolver = LocalContext.current.applicationContext.contentResolver
    fun read(): Boolean = runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
    var reduce by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = read()
            }
        }
        runCatching {
            resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        }
        onDispose { runCatching { resolver.unregisterContentObserver(observer) } }
    }
    return reduce
}

internal fun Modifier.unlessReducedMotion(motion: Modifier.() -> Modifier): Modifier = composed {
    if (LocalReduceMotion.current) Modifier else Modifier.motion()
}
