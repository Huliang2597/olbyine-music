package com.olbyine.music.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ArtworkPaletteRepository {
    private val cache = object : LruCache<String, List<Int>>(48) {}

    suspend fun colorsFor(seed: String?, coverPath: String?): List<Int> = withContext(Dispatchers.IO) {
        val safeSeed = seed.orEmpty().ifBlank { "olbyine" }
        val key = "$safeSeed|${coverPath.orEmpty()}"
        cache.get(key)?.let { return@withContext it }

        val colors = coverPath
            ?.let(::decodeSampled)
            ?.let(::sampleGrid)
            ?.takeIf { it.size == COLOR_COUNT }
            ?: fallbackColors(safeSeed)
        cache.put(key, colors)
        colors
    }

    private fun decodeSampled(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > SAMPLE_EDGE * 2 ||
            bounds.outHeight / sampleSize > SAMPLE_EDGE * 2
        ) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    }

    private fun sampleGrid(bitmap: Bitmap): List<Int>? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            if (width <= 0 || height <= 0) return null
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            buildList(COLOR_COUNT) {
                for (row in 0 until GRID_ROWS) {
                    val top = row * height / GRID_ROWS
                    val bottom = ((row + 1) * height / GRID_ROWS).coerceAtMost(height)
                    for (column in 0 until GRID_COLUMNS) {
                        val left = column * width / GRID_COLUMNS
                        val right = ((column + 1) * width / GRID_COLUMNS).coerceAtMost(width)
                        var red = 0L
                        var green = 0L
                        var blue = 0L
                        var count = 0L
                        var y = top
                        while (y < bottom) {
                            var x = left
                            while (x < right) {
                                val color = pixels[y * width + x]
                                if (Color.alpha(color) >= 160) {
                                    red += Color.red(color)
                                    green += Color.green(color)
                                    blue += Color.blue(color)
                                    count++
                                }
                                x += SAMPLE_STRIDE
                            }
                            y += SAMPLE_STRIDE
                        }
                        val base = if (count == 0L) fallbackColors("$width:$height")[size]
                        else Color.rgb((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
                        add(enhance(base, size))
                    }
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun enhance(color: Int, index: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = (hsv[1] * 1.28f).coerceIn(0.24f, 0.92f)
        hsv[2] = hsv[2].coerceIn(0.20f, 0.90f)

        if (hsv[1] < 0.32f) {
            hsv[0] = (hsv[0] + index * 11f) % 360f
            hsv[1] = 0.32f
        }
        return Color.HSVToColor(hsv)
    }

    private fun fallbackColors(seed: String): List<Int> {
        val baseHue = ((seed.hashCode().toLong() and 0x7fffffffL) % 360L).toFloat()
        val offsets = floatArrayOf(0f, 34f, 78f, 142f, 206f, 292f)
        return offsets.mapIndexed { index, offset ->
            Color.HSVToColor(
                floatArrayOf(
                    (baseHue + offset) % 360f,
                    0.46f + (index % 3) * 0.08f,
                    0.50f + (index % 2) * 0.10f,
                ),
            )
        }
    }

    companion object {
        private const val GRID_COLUMNS = 3
        private const val GRID_ROWS = 2
        private const val COLOR_COUNT = GRID_COLUMNS * GRID_ROWS
        private const val SAMPLE_EDGE = 96
        private const val SAMPLE_STRIDE = 2

        val DefaultColors: List<Int> = listOf(
            0xFF5E315F.toInt(),
            0xFFB04D78.toInt(),
            0xFFCB785D.toInt(),
            0xFF313C66.toInt(),
            0xFF675181.toInt(),
            0xFF283A4F.toInt(),
        )
    }
}
