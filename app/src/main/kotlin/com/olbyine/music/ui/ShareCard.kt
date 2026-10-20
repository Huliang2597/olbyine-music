package com.olbyine.music.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.olbyine.music.data.Track

internal object ShareCard {
    private const val W = 1080
    private const val H = 1500

    fun render(context: Context, track: Track, lyricLine: String?, link: String?): Bitmap {
        val cover = loadCover(track)
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        if (cover != null) {
            val tiny = Bitmap.createScaledBitmap(cover, 12, 16, true)
            canvas.drawBitmap(Bitmap.createScaledBitmap(tiny, W, H, true), 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            canvas.drawColor(Color.rgb(40, 34, 52))
        }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, H.toFloat(), Color.argb(70, 0, 0, 0), Color.argb(190, 0, 0, 0), Shader.TileMode.CLAMP)
        })
        val size = 760f
        val left = (W - size) / 2f
        val top = 130f
        val rect = RectF(left, top, left + size, top + size)
        canvas.drawRoundRect(RectF(rect.left, rect.top + 18f, rect.right, rect.bottom + 18f), 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 0, 0, 0)
            maskFilter = android.graphics.BlurMaskFilter(36f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        })
        if (cover != null) {
            val save = canvas.save()
            val path = android.graphics.Path().apply { addRoundRect(rect, 40f, 40f, android.graphics.Path.Direction.CW) }
            canvas.clipPath(path)
            val src = android.graphics.Rect(0, 0, cover.width, cover.height).let { r ->
                val edge = minOf(r.width(), r.height())
                android.graphics.Rect((r.width() - edge) / 2, (r.height() - edge) / 2, (r.width() + edge) / 2, (r.height() + edge) / 2)
            }
            canvas.drawBitmap(cover, src, rect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            canvas.restoreToCount(save)
        } else {
            canvas.drawRoundRect(rect, 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) })
        }
        var y = rect.bottom + 70f
        y = drawText(canvas, track.title, 64f, Typeface.DEFAULT_BOLD, Color.WHITE, y, 2)
        y = drawText(canvas, listOf(track.artist, track.album).filter { it.isNotBlank() && it != "未知专辑" }.joinToString(" · "), 36f, Typeface.DEFAULT, Color.argb(200, 255, 255, 255), y + 10f, 1)
        if (!lyricLine.isNullOrBlank()) {
            y = drawText(canvas, "「$lyricLine」", 38f, Typeface.create(Typeface.SERIF, Typeface.NORMAL), Color.argb(235, 255, 255, 255), y + 34f, 2)
        }
        val footer = H - 210f
        if (link != null) {
            val qr = qr(link, 170)
            val qrLeft = W - 90f - 170f
            canvas.drawRoundRect(RectF(qrLeft - 12f, footer - 12f, qrLeft + 182f, footer + 182f), 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            canvas.drawBitmap(qr, qrLeft, footer, null)
        }
        val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 46f; typeface = Typeface.DEFAULT_BOLD }
        canvas.drawText("Olbyine", 90f, footer + 70f, brand)
        val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 255, 255, 255); textSize = 30f }
        canvas.drawText(when {
            link == null -> "正在用 Olbyine 听"
            link.contains("soundcloud.com") -> "扫码在 SoundCloud 收听"
            else -> "扫码在网易云音乐收听"
        }, 90f, footer + 124f, sub)
        return out
    }

    fun save(context: Context, bitmap: Bitmap, title: String): Uri? {
        val name = "Olbyine_" + title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(40) + "_" + System.currentTimeMillis() + ".png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Olbyine")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        val ok = resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } == true
        if (!ok) {
            resolver.delete(uri, null, null)
            return null
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return uri
    }

    private fun drawText(canvas: Canvas, text: String, size: Float, typeface: Typeface, color: Int, top: Float, lines: Int): Float {
        if (text.isBlank()) return top
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; this.typeface = typeface }
        val width = W - 180
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(lines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(90f, top)
        layout.draw(canvas)
        canvas.restore()
        return top + layout.height
    }

    private fun loadCover(track: Track): Bitmap? {
        track.coverPath?.let { path ->
            decode(java.io.File(path).takeIf { it.exists() }?.readBytes())?.let { return it }
        }
        val url = track.artworkUrl ?: return null
        return runCatching {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.inputStream.use { decode(it.readBytes()) }.also { connection.disconnect() }
        }.getOrNull()
    }

    private fun decode(bytes: ByteArray?): Bitmap? {
        if (bytes == null || bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 900 && bounds.outHeight / (sample * 2) >= 900) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun qr(content: String, size: Int): Bitmap {
        val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(
            content,
            com.google.zxing.BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(com.google.zxing.EncodeHintType.MARGIN to 0),
        )
        val pixels = IntArray(size * size) { index -> if (matrix[index % size, index / size]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}
