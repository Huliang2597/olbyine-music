package com.olbyine.music.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.olbyine.music.data.UiStyle
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun OlbyineSpinner(modifier: Modifier = Modifier, color: Color = Color.Unspecified, strokeWidth: Dp = 3.dp) {
    val ink = if (color.isSpecified) color else MaterialTheme.colorScheme.onSurfaceVariant
    val style = LocalStyleSpec.current.style
    if (style == UiStyle.MATERIAL) {
        androidx.compose.material3.CircularProgressIndicator(modifier, color = if (color.isSpecified) color else MaterialTheme.colorScheme.primary, strokeWidth = strokeWidth)
        return
    }
    val transition = rememberInfiniteTransition(label = "spinner")
    if (style == UiStyle.IOS) {
        val turn by transition.animateFloat(0f, 8f, infiniteRepeatable(tween(800, easing = LinearEasing)), label = "spokes")
        Canvas(modifier.defaultMinSize(24.dp, 24.dp)) {
            val lead = turn.toInt() % 8
            val r = size.minDimension / 2f
            val width = r * .2f
            for (i in 0 until 8) {
                val angle = (i * 45f - 90f) * (Math.PI / 180f).toFloat()
                val age = (lead - i + 8) % 8
                val alpha = 1f - age / 8f * .8f
                val start = Offset(center.x + cos(angle) * r * .48f, center.y + sin(angle) * r * .48f)
                val end = Offset(center.x + cos(angle) * (r - width / 2f), center.y + sin(angle) * (r - width / 2f))
                drawLine(ink.copy(alpha = ink.alpha * alpha), start, end, strokeWidth = width, cap = StrokeCap.Round)
            }
        }
    } else {
        val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "arc")
        val sweep by transition.animateFloat(40f, 250f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "sweep")
        Canvas(modifier.defaultMinSize(24.dp, 24.dp)) {
            val w = strokeWidth.toPx().coerceAtMost(size.minDimension / 4f)
            rotate(spin) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(ink.copy(alpha = 0f), ink), center),
                    startAngle = 0f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(w / 2f, w / 2f),
                    size = Size(size.width - w, size.height - w),
                    style = Stroke(w, cap = StrokeCap.Round),
                )
            }
        }
    }
}

@Composable
internal fun OlbyineProgressBar(progress: (() -> Float)?, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, trackColor: Color = color.copy(alpha = .16f)) {
    if (LocalStyleSpec.current.style == UiStyle.MATERIAL) {
        if (progress != null) androidx.compose.material3.LinearProgressIndicator(progress = progress, modifier = modifier, color = color, trackColor = trackColor)
        else androidx.compose.material3.LinearProgressIndicator(modifier, color = color, trackColor = trackColor)
        return
    }
    val transition = rememberInfiniteTransition(label = "bar")
    val slide by transition.animateFloat(-.4f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "bar-slide")
    Canvas(modifier.defaultMinSize(minHeight = 4.dp)) {
        val h = size.height
        val radius = CornerRadius(h / 2f)
        drawRoundRect(trackColor, cornerRadius = radius)
        if (progress != null) {
            val f = progress().coerceIn(0f, 1f)
            if (f > 0f) drawRoundRect(color, size = Size((size.width * f).coerceAtLeast(h), h), cornerRadius = radius)
        } else {
            val segment = size.width * .4f
            val x = slide * size.width
            val left = x.coerceAtLeast(0f)
            val right = (x + segment).coerceAtMost(size.width)
            if (right > left) drawRoundRect(color, topLeft = Offset(left, 0f), size = Size(right - left, h), cornerRadius = radius)
        }
    }
}

@Composable
internal fun StyledToast(data: SnackbarData) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance2() < .5f
    val fill = if (dark) Color(0xFF2C2C2E).copy(alpha = .96f) else Color(0xFF1C1C1E).copy(alpha = .92f)
    Row(
        Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            .shadow(14.dp, RoundedCornerShape(22.dp), clip = false)
            .clip(RoundedCornerShape(22.dp))
            .background(fill)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.weight(1f, fill = false))
        data.visuals.actionLabel?.let { label ->
            Spacer(Modifier.width(14.dp))
            Text(
                label,
                Modifier.clip(RoundedCornerShape(8.dp)).pressable(label = label) { data.performAction() }.padding(horizontal = 4.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (LocalStyleSpec.current.style == UiStyle.IOS) Color.White else colors.primary.let { if (it.luminance2() < .35f) Color(0xFF8AB4FF) else it },
            )
        }
    }
}

private fun Color.luminance2(): Float = .2126f * red + .7152f * green + .0722f * blue
