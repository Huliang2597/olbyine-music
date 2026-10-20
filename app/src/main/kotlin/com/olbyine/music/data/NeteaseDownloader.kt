package com.olbyine.music.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class NeteaseDownloadInfo(
    val url: String,
    val type: String,
    val sizeBytes: Long,
    val bitrate: Int,
    val level: String,
    val coverUrl: String = "",
)

data class DownloadedSong(
    val fileName: String,
    val folder: String,
    val bytes: Long,
    val level: String,
    val lyricsSaved: Boolean,
    val audioUri: String,
    val lyricsUri: String?,
    val coverUri: String?,
    val tagged: Boolean = false,
    val coverEmbedded: Boolean = false,
)

class NeteaseDownloader(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun download(
        song: NeteaseSong,
        info: NeteaseDownloadInfo,
        lyrics: StoredLyrics?,
        saveLyrics: Boolean,
        saveCover: Boolean,
        onProgress: (Long, Long) -> Unit,
    ): DownloadedSong = withContext(Dispatchers.IO) {
        val extension = extensionFor(info)
        val extras = saveLyrics || saveCover
        val base = baseName(song)
        val relative = if (extras) "$FOLDER/$base/" else "$FOLDER/"
        val audioName = uniqueName(base, extension, relative)
        val stem = audioName.substringBeforeLast('.')
        val temp = File(workDir(), "audio-$stem.$extension")
        var written = 0L
        try {
            val request = Request.Builder()
                .url(info.url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("下载失败（HTTP ${response.code}）")
                val body = response.body ?: error("下载失败（服务器没有返回内容）")
                val total = info.sizeBytes.takeIf { it > 0L } ?: body.contentLength().takeIf { it > 0L } ?: 0L
                body.byteStream().use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            written += read
                            onProgress(written, total)
                        }
                        output.flush()
                    }
                }
            }
            if (written <= 0L) error("下载到的文件是空的")
            val cover = if (info.coverUrl.isNotBlank()) fetchCover(info.coverUrl) else null
            AudioTagWriter.write(
                temp,
                extension,
                AudioTags(
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    albumArtist = song.artists.firstOrNull().orEmpty(),
                    trackNumber = 0,
                    year = 0,
                    cover = cover,
                ),
            )
            val uri = publishAudio(temp, audioName, extension, song, relative)
            val coverUri = if (saveCover && cover != null) {
                writeAux("$stem.jpg", "image/jpeg", cover, relative)
            } else {
                null
            }
            val lyricsText = if (saveLyrics && lyrics != null) lyricsText(lyrics) else ""
            val lyricsUri = if (lyricsText.isNotBlank()) {
                writeAux("$stem.lrc", MIME_LRC, lyricsText.toByteArray(Charsets.UTF_8), relative)
            } else {
                null
            }
            DownloadedSong(
                fileName = audioName,
                folder = relative.trimEnd('/'),
                bytes = written,
                level = info.level,
                lyricsSaved = lyricsUri != null,
                audioUri = uri.toString(),
                lyricsUri = lyricsUri,
                coverUri = coverUri,
                tagged = AudioTagWriter.supports(extension),
                coverEmbedded = cover != null && AudioTagWriter.supports(extension),
            )
        } finally {
            runCatching { temp.delete() }
            runCatching { File(temp.parentFile, temp.name + ".tag").delete() }
            runCatching { File(temp.parentFile, temp.name + ".bak").delete() }
        }
    }

    private fun publishAudio(file: File, fileName: String, extension: String, song: NeteaseSong, relative: String): Uri {
        val files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val attempts = listOf(
            Triple(files, MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO, true),
            Triple(files, MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO, false),
            Triple(MediaStore.Downloads.EXTERNAL_CONTENT_URI, MediaStore.Files.FileColumns.MEDIA_TYPE_NONE, true),
        )
        var lastError: Throwable? = null
        attempts.forEach { (collection, mediaType, pending) ->
            try {
                return insertAudio(collection, mediaType, pending, file, fileName, extension, song, relative)
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("无法写入下载目录")
    }

    private fun insertAudio(
        collection: Uri,
        mediaType: Int,
        pending: Boolean,
        file: File,
        fileName: String,
        extension: String,
        song: NeteaseSong,
        relative: String,
    ): Uri {
        deleteExisting(collection, fileName, relative)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(extension))
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.TITLE, song.title)
            put(MediaStore.MediaColumns.ARTIST, song.artist)
            put(MediaStore.MediaColumns.ALBUM, song.album)
            put(MediaStore.MediaColumns.DURATION, song.durationMs)
            put(MediaStore.Files.FileColumns.MEDIA_TYPE, mediaType)
            if (pending) put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(collection, values)
            ?: error("无法在下载目录中创建文件")
        try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output, 128 * 1024) }
            } ?: error("无法写入下载文件")
            if (pending) {
                context.contentResolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
            val size = context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L } ?: 0L
            if (size != file.length()) error("写入的文件不完整")
            return uri
        } catch (error: Throwable) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            throw error
        }
    }

    private fun writeAux(displayName: String, mime: String, bytes: ByteArray, relative: String): String? {
        val files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val attempts = listOf(
            Triple(files, relative, true),
            Triple(files, relative, false),
            Triple(MediaStore.Downloads.EXTERNAL_CONTENT_URI, relative, true),
        )
        attempts.forEach { (collection, path, pending) ->
            insertAux(collection, displayName, mime, bytes, path, pending)?.let { return it }
        }
        return null
    }

    private fun insertAux(
        collection: Uri,
        displayName: String,
        mime: String,
        bytes: ByteArray,
        relative: String,
        pending: Boolean,
    ): String? = runCatching {
        deleteExisting(collection, displayName, relative)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.Files.FileColumns.MEDIA_TYPE_NONE)
            if (pending) put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(collection, values) ?: return@runCatching null
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return@runCatching null
        if (pending) {
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        }
        val written = context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L } ?: 0L
        if (written != bytes.size.toLong()) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            return@runCatching null
        }
        uri.toString()
    }.getOrNull()

    private fun deleteExisting(collection: Uri, displayName: String, relative: String) {
        runCatching {
            context.contentResolver.delete(
                collection,
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(displayName, relative),
            )
        }
    }

    private fun fetchCover(url: String): ByteArray? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.bytes()?.takeIf { it.size > 1024 }
        }
    }.getOrNull()

    private fun workDir(): File = File(context.cacheDir, "downloads").apply { mkdirs() }

    private fun uniqueName(base: String, extension: String, relative: String): String {
        val existing = existingNames(relative)
        var candidate = "$base.$extension"
        var index = 2
        while (candidate in existing && index < 100) {
            candidate = "$base ($index).$extension"
            index++
        }
        return candidate
    }

    private fun existingNames(relative: String): Set<String> = runCatching {
        val names = HashSet<String>()
        context.contentResolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(relative),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.let(names::add)
            }
        }
        names
    }.getOrDefault(emptySet())

    private fun baseName(song: NeteaseSong): String {
        val title = song.title.ifBlank { "未知歌曲" }
        val raw = "${song.artist.replace("/", "、").ifBlank { "未知艺术家" }} - $title"
        val cleaned = raw.map { character ->
            if (character in ILLEGAL || character.code < 0x20) '_' else character
        }.joinToString("").trim().trimEnd('.')
        return cleaned.take(96).ifBlank { "netease_${song.id}" }
    }

    private fun extensionFor(info: NeteaseDownloadInfo): String {
        val type = info.type.lowercase()
        return when {
            type.contains("flac") -> "flac"
            type.contains("mp3") -> "mp3"
            type.contains("m4a") || type.contains("aac") || type.contains("mp4") -> "m4a"
            type.contains("wav") -> "wav"
            else -> {
                val path = info.url.substringBefore('?').lowercase()
                when {
                    path.endsWith(".flac") -> "flac"
                    path.endsWith(".mp3") -> "mp3"
                    path.endsWith(".m4a") || path.endsWith(".mp4") -> "m4a"
                    path.endsWith(".wav") -> "wav"
                    info.level == "lossless" || info.level == "hires" || info.level == "jymaster" -> "flac"
                    else -> "mp3"
                }
            }
        }
    }

    private fun mimeFor(extension: String): String = when (extension) {
        "flac" -> "audio/flac"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "wav" -> "audio/wav"
        else -> "audio/*"
    }

    private fun lyricsText(lyrics: StoredLyrics): String {
        val base = lyrics.lyrics.ifBlank { lyrics.wordLyrics }
        val translation = lyrics.translationLyrics
        if (base.isBlank()) return ""
        if (translation.isBlank()) return base
        val translated = HashMap<String, String>()
        translation.lineSequence().forEach { line ->
            val stamp = stampOf(line)
            if (stamp.isNotEmpty()) {
                val text = line.removePrefix(stamp).trim()
                if (text.isNotEmpty()) translated[stamp] = text
            }
        }
        if (translated.isEmpty()) return base
        return buildString {
            base.lineSequence().forEach { line ->
                append(line)
                append('\n')
                translated[stampOf(line)]?.let { text ->
                    append(stampOf(line))
                    append(text)
                    append('\n')
                }
            }
        }
    }

    private fun stampOf(line: String): String {
        if (!line.startsWith("[")) return ""
        val end = line.indexOf(']')
        return if (end > 0) line.substring(0, end + 1) else ""
    }

    companion object {
        const val FOLDER = "Download/Olbyine"
        // text/plain 会让 MediaStore 把 .lrc 补成 .lrc.txt，octet-stream 不做扩展名推断
        const val MIME_LRC = "application/octet-stream"
        private val ILLEGAL = setOf('\\', '/', ':', '*', '?', '"', '<', '>', '|', '\u0000')
    }
}
