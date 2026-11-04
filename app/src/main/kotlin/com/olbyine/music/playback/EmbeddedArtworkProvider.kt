package com.olbyine.music.playback

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor

class EmbeddedArtworkProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val source = uri.lastPathSegment ?: return null
        val bytes = readArtwork(source) ?: return null
        val pipe = ParcelFileDescriptor.createPipe()
        val input = pipe[0]
        val output = pipe[1]
        Thread {
            runCatching {
                ParcelFileDescriptor.AutoCloseOutputStream(output).use { it.write(bytes) }
            }
        }.apply { isDaemon = true }.start()
        return input
    }

    private fun readArtwork(source: String): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (source.startsWith("file://")) retriever.setDataSource(source.removePrefix("file://"))
            else retriever.setDataSource(context, Uri.parse(source))
            val raw = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return raw
            var sample = 1
            while (bounds.outWidth / sample > EDGE * 2 || bounds.outHeight / sample > EDGE * 2) sample *= 2
            val decoded = BitmapFactory.decodeByteArray(
                raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: return raw
            val ratio = minOf(1f, EDGE.toFloat() / maxOf(decoded.width, decoded.height))
            val scaled = if (ratio < 1f) Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * ratio).toInt().coerceAtLeast(1),
                (decoded.height * ratio).toInt().coerceAtLeast(1),
                true,
            ) else decoded
            val out = java.io.ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
            out.toByteArray()
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    companion object {
        const val AUTHORITY_SUFFIX = ".artwork"
        private const val EDGE = 512

        fun uriFor(packageName: String, source: String): Uri =
            Uri.parse("content://$packageName$AUTHORITY_SUFFIX/${Uri.encode(source)}")
    }
}
