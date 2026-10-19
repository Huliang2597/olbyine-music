package com.olbyine.music.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun CustomBackground(path: String, blur: Float, dim: Float, scrim: Color, modifier: Modifier = Modifier) {
    val image by produceState<ImageBitmap?>(null, path, blur) {
        value = withContext(Dispatchers.Default) { runCatching { loadBlurred(path, blur) }.getOrNull()?.asImageBitmap() }
    }
    val shown by animateFloatAsState(if (image != null) 1f else 0f, tween(420), label = "custom-bg")
    Box(modifier.fillMaxSize().background(scrim)) {
        image?.let {
            Image(
                it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = shown },
                contentScale = ContentScale.Crop,
            )
        }
        Box(Modifier.fillMaxSize().background(scrim.copy(alpha = dim.coerceIn(0f, .9f))))
    }
}

private fun loadBlurred(path: String, blur: Float): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val targetEdge = if (blur <= 0.01f) 1440 else (720 - 480 * blur).roundToInt().coerceAtLeast(160)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetEdge) sample *= 2
    val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    if (blur <= 0.01f) return decoded
    val scale = targetEdge.toFloat() / maxOf(decoded.width, decoded.height)
    val small = if (scale < 1f) {
        Bitmap.createScaledBitmap(decoded, (decoded.width * scale).roundToInt().coerceAtLeast(1), (decoded.height * scale).roundToInt().coerceAtLeast(1), true)
            .also { if (it !== decoded) decoded.recycle() }
    } else decoded
    val radius = (2 + blur * 22).roundToInt()
    return boxBlur(small, radius)
}

private fun boxBlur(source: Bitmap, radius: Int): Bitmap {
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)
    val buffer = IntArray(pixels.size)
    repeat(3) {
        blurPass(pixels, buffer, width, height, radius, horizontal = true)
        blurPass(buffer, pixels, width, height, radius, horizontal = false)
    }
    val out = if (source.isMutable) source else source.copy(Bitmap.Config.ARGB_8888, true).also { source.recycle() }
    out.setPixels(pixels, 0, width, 0, 0, width, height)
    return out
}

private fun blurPass(input: IntArray, output: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
    val lines = if (horizontal) height else width
    val length = if (horizontal) width else height
    val window = radius * 2 + 1
    for (line in 0 until lines) {
        var r = 0
        var g = 0
        var b = 0
        fun at(i: Int): Int {
            val clamped = i.coerceIn(0, length - 1)
            return if (horizontal) input[line * width + clamped] else input[clamped * width + line]
        }
        for (i in -radius..radius) {
            val c = at(i)
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
        }
        for (i in 0 until length) {
            val index = if (horizontal) line * width + i else i * width + line
            output[index] = (0xFF shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
            val add = at(i + radius + 1)
            val remove = at(i - radius)
            r += ((add shr 16) and 0xFF) - ((remove shr 16) and 0xFF)
            g += ((add shr 8) and 0xFF) - ((remove shr 8) and 0xFF)
            b += (add and 0xFF) - (remove and 0xFF)
        }
    }
}
