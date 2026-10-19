package com.olbyine.music.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Immutable
internal data class GlassTuning(
    val highlight: Float = 1f,
    val blur: Float = 1f,
    val opacity: Float = 1f,
    val refraction: Float = 1f,
    val gyro: Boolean = true,
) {
    companion object {
        val PRESETS = listOf(
            "标准" to GlassTuning(),
            "通透" to GlassTuning(highlight = 1.2f, blur = .7f, opacity = .6f, refraction = 1.3f),
            "磨砂" to GlassTuning(highlight = .8f, blur = 1.6f, opacity = 1.3f, refraction = .6f),
            "强折射" to GlassTuning(highlight = 1.3f, blur = 1f, opacity = .9f, refraction = 1.8f),
            "克制" to GlassTuning(highlight = .6f, blur = 1.1f, opacity = 1.15f, refraction = .7f),
        )
    }
}

internal val LocalGlassTuning = staticCompositionLocalOf { GlassTuning() }

internal val LocalOnGlass = compositionLocalOf { false }

internal fun GlassStyle.tuned(tuning: GlassTuning, dark: Boolean): GlassStyle {
    if (blur <= 0.dp) return this
    val blurScale = tuning.blur * if (dark) 1f else 1.2f
    val rim = (highlight.alpha * tuning.highlight * if (dark) 1.25f else 1f).coerceIn(0f, 1f)
    val veil = (tint.alpha * tuning.opacity).coerceIn(0f, 1f)
    return copy(
        blur = blur * blurScale,
        tint = if (dark) tint.copy(alpha = veil) else androidx.compose.ui.graphics.lerp(tint, Color.Black, .04f).copy(alpha = veil),
        highlight = highlight.copy(alpha = rim),
        refractionAmount = refractionAmount * tuning.refraction,
        refractionHeight = refractionHeight * sqrt(tuning.refraction.coerceAtLeast(0f)),
    )
}

internal object GlassLight {
    private const val DEFAULT = -2.2f
    private val angleState = mutableFloatStateOf(DEFAULT)
    private var smoothed = DEFAULT
    private var users = 0
    private var manager: SensorManager? = null

    val angle: Float get() = angleState.floatValue
    val x: Float get() = cos(angle)
    val y: Float get() = sin(angle)

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val gx = event.values[0]
            val gy = event.values[1]
            val tilt = sqrt(gx * gx + gy * gy)
            val target = if (tilt < 1.2f) DEFAULT else atan2(-gy, gx) - 0.6f
            var delta = target - smoothed
            while (delta > Math.PI) delta -= (2 * Math.PI).toFloat()
            while (delta < -Math.PI) delta += (2 * Math.PI).toFloat()
            smoothed += delta * .18f
            if (kotlin.math.abs(smoothed - angleState.floatValue) > .02f) angleState.floatValue = smoothed
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start(context: Context) {
        if (users++ > 0) return
        val sensors = context.getSystemService(SensorManager::class.java) ?: return
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        manager = sensors
        sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (--users > 0) return
        users = 0
        manager?.unregisterListener(listener)
        manager = null
        angleState.floatValue = DEFAULT
        smoothed = DEFAULT
    }
}

@Composable
internal fun TrackGlassLight(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled, context) {
        if (enabled) GlassLight.start(context.applicationContext)
        onDispose { if (enabled) GlassLight.stop() }
    }
}

internal fun Modifier.lightRim(shape: Shape, strength: Float, width: Dp = 1.dp): Modifier = drawWithContent {
    drawContent()
    if (strength <= 0f) return@drawWithContent
    val dir = Offset(GlassLight.x, GlassLight.y)
    val r = size.maxDimension / 2f
    val c = Offset(size.width / 2f, size.height / 2f)
    val bright = strength.coerceIn(0f, 1f)
    val brush = Brush.linearGradient(
        0f to Color.White.copy(alpha = bright),
        .45f to Color.White.copy(alpha = bright * .12f),
        .8f to Color.White.copy(alpha = bright * .05f),
        1f to Color.White.copy(alpha = bright * .35f),
        start = c + dir * r,
        end = c - dir * r,
    )
    drawOutline(shape.createOutline(size, layoutDirection, this), brush, style = Stroke(width.toPx()))
}

internal val com.olbyine.music.data.UserSettings.glassTuning: GlassTuning
    get() = GlassTuning(glassHighlight, glassBlur, glassOpacity, glassRefraction, glassGyro)
