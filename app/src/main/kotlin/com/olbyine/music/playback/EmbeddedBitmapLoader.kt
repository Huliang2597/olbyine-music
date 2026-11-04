package com.olbyine.music.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executors

@UnstableApi
internal class EmbeddedBitmapLoader(private val context: Context) : BitmapLoader {
    private val executor: ListeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    private val cache = object : android.util.LruCache<String, Bitmap>(24) {}

    private val authority = context.packageName + EmbeddedArtworkProvider.AUTHORITY_SUFFIX

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = executor.submit<Bitmap> {
        BitmapFactory.decodeByteArray(data, 0, data.size) ?: throw IllegalArgumentException("unsupported image")
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = executor.submit<Bitmap> {
        val text = uri.toString()
        cache.get(text)?.let { return@submit it }
        val bytes = when {
            uri.scheme == "content" && uri.authority == authority ->
                embeddedPicture(uri.lastPathSegment.orEmpty())
            uri.scheme == "content" -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            uri.scheme == "file" -> uri.path?.let { path -> java.io.File(path).takeIf { it.exists() }?.readBytes() }
            text.startsWith("http", true) -> download(text)
            else -> null
        }
        if (bytes == null) throw IllegalArgumentException("no artwork for $text")
        val bitmap = scaled(bytes, EDGE) ?: throw IllegalArgumentException("undecodable artwork for $text")
        cache.put(text, bitmap)
        bitmap
    }

    private fun embeddedPicture(source: String): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (source.startsWith("file://")) retriever.setDataSource(source.removePrefix("file://"))
            else retriever.setDataSource(context, Uri.parse(source))
            retriever.embeddedPicture
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun download(url: String): ByteArray? = runCatching {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun scaled(bytes: ByteArray, edge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > edge * 2 || bounds.outHeight / sample > edge * 2) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private companion object {
        const val EDGE = 512
    }
}
