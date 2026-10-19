package com.olbyine.music.playback

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class ExternalMediaListener : NotificationListenerService()

data class ExternalNowPlaying(
    val packageName: String,
    val appName: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val positionMs: Long,
    val sampledAtElapsedMs: Long,
    val speed: Float,
    val isPlaying: Boolean,
    val artworkPath: String?,
) {
    val key: String get() = "$packageName|$title|$artist"

    fun estimatedPosition(now: Long = SystemClock.elapsedRealtime()): Long =
        if (isPlaying) positionMs + ((now - sampledAtElapsedMs) * speed).toLong() else positionMs
}

class ExternalMediaMonitor(private val context: Context) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, ExternalMediaListener::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow<ExternalNowPlaying?>(null)
    val state: StateFlow<ExternalNowPlaying?> = _state.asStateFlow()
    private var controller: MediaController? = null
    private var running = false
    private var artworkKey: String? = null
    private var artworkGeneration: Int = 0
    private var artworkPixels: Int = 0
    private val artworkExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        pick(controllers.orEmpty())
    }
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) = publish()
        override fun onSessionDestroyed() {
            refresh()
        }
    }

    fun hasAccess(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == listenerComponent }
    }

    fun start(): Boolean {
        if (running) return true
        if (!hasAccess()) return false
        return try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
            running = true
            refresh()
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { manager.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(callback)
        controller = null
        _state.value = null
    }

    private fun refresh() {
        val sessions = try {
            manager.getActiveSessions(listenerComponent)
        } catch (_: SecurityException) {
            emptyList()
        }
        pick(sessions)
    }

    private fun pick(sessions: List<MediaController>) {
        val own = context.packageName
        val candidates = sessions.filter { it.packageName != own && it.metadata != null }
        val chosen = candidates.sortedWith(
            compareByDescending<MediaController> { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING }
                .thenBy { KNOWN_PLAYERS.keys.indexOf(it.packageName).let { i -> if (i < 0) Int.MAX_VALUE else i } },
        ).firstOrNull()
        if (chosen?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = chosen
            chosen?.registerCallback(callback, handler)
        }
        publish()
    }

    private fun publish() {
        val current = controller
        val metadata = current?.metadata
        if (current == null || metadata == null) {
            _state.value = null
            return
        }
        val playback = current.playbackState
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty()
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty()
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        val key = "${current.packageName}|$title|$artist"
        if (artworkKey != key) {
            artworkKey = key
            artworkPath = null
            artworkGeneration = 0
            artworkPixels = 0
        }
        val art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (art != null && !art.isRecycled) {
            val pixels = art.width * art.height
            if (art.generationId != artworkGeneration && (artworkPath == null || pixels > artworkPixels)) {
                artworkGeneration = art.generationId
                artworkPixels = pixels
                saveArtwork(key, art)
            }
        } else if (artworkPath == null && artworkGeneration == 0) {
            val uri = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            if (!uri.isNullOrBlank()) {
                artworkGeneration = -1
                loadArtworkUri(key, uri)
            }
        }
        _state.value = ExternalNowPlaying(
            packageName = current.packageName,
            appName = KNOWN_PLAYERS[current.packageName] ?: appLabel(current.packageName),
            title = title,
            artist = artist,
            album = album,
            durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L),
            positionMs = playback?.position?.coerceAtLeast(0L) ?: 0L,
            sampledAtElapsedMs = playback?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = playback?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            isPlaying = playback?.state == android.media.session.PlaybackState.STATE_PLAYING,
            artworkPath = artworkPath,
        )
    }

    private var artworkPath: String? = null

    private fun saveArtwork(key: String, bitmap: Bitmap) {
        val copy = runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: return
        artworkExecutor.execute {
            val path = runCatching {
                val file = File(context.cacheDir, "external-artwork-${SystemClock.elapsedRealtimeNanos()}.jpg")
                file.outputStream().use { copy.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                file.absolutePath
            }.getOrNull()
            copy.recycle()
            handler.post { adoptArtwork(key, path) }
        }
    }

    private fun loadArtworkUri(key: String, uri: String) {
        artworkExecutor.execute {
            val path = runCatching {
                val parsed = android.net.Uri.parse(uri)
                val input = when (parsed.scheme?.lowercase()) {
                    "content", "file", "android.resource" -> context.contentResolver.openInputStream(parsed)
                    "https" -> java.net.URL(uri).openConnection().apply { connectTimeout = 6_000; readTimeout = 8_000 }.getInputStream()
                    else -> null
                } ?: return@runCatching null
                val bytes = input.use { it.readBytes() }
                require(bytes.size in 1..12 * 1024 * 1024)
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
                val file = File(context.cacheDir, "external-artwork-${SystemClock.elapsedRealtimeNanos()}.jpg")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                bitmap.recycle()
                file.absolutePath
            }.getOrNull()
            handler.post { adoptArtwork(key, path) }
        }
    }

    private fun adoptArtwork(key: String, path: String?) {
        if (path == null || key != artworkKey) {
            path?.let { File(it).delete() }
            return
        }
        val previous = artworkPath
        artworkPath = path
        context.cacheDir.listFiles { file -> file.name.startsWith("external-artwork-") && file.absolutePath != path }
            ?.forEach { it.delete() }
        if (previous != path) publish()
    }

    private fun appLabel(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    fun togglePlay() {
        val controls = controller?.transportControls ?: return
        if (_state.value?.isPlaying == true) controls.pause() else controls.play()
    }

    fun next() = controller?.transportControls?.skipToNext()
    fun previous() = controller?.transportControls?.skipToPrevious()
    fun seekTo(positionMs: Long) = controller?.transportControls?.seekTo(positionMs)

    companion object {
        val KNOWN_PLAYERS = linkedMapOf(
            "com.netease.cloudmusic" to "网易云音乐",
            "com.spotify.music" to "Spotify",
            "com.tencent.qqmusic" to "QQ 音乐",
            "com.apple.android.music" to "Apple Music",
            "com.kugou.android" to "酷狗音乐",
            "cn.kuwo.player" to "酷我音乐",
            "com.salt.music" to "Salt Player",
            "com.hiby.music" to "海贝音乐",
        )
    }
}

fun ExternalNowPlaying.asTrack(): com.olbyine.music.data.Track = com.olbyine.music.data.Track(
    id = key,
    uri = "",
    title = title.ifBlank { "未知歌曲" },
    artist = artist.ifBlank { appName },
    album = album,
    durationMs = durationMs,
    displayName = title,
    mimeType = null,
    sizeBytes = 0L,
    coverPath = artworkPath,
    lyrics = null,
    wordLyrics = null,
    translationLyrics = null,
    romanLyrics = null,
    lyricsSource = null,
    lyricsFormat = null,
    lyricsConfidence = null,
    lyricsInstrumental = false,
    lyricsLookupAt = 0L,
    lyricsLookupStatus = null,
    favorite = false,
    addedAt = 0L,
)

fun ExternalNowPlaying.asPlaybackState(): PlaybackState = PlaybackState(
    connected = true,
    currentMediaId = key,
    currentIndex = 0,
    mediaItemCount = 1,
    durationMs = durationMs,
    positionMs = positionMs,
    sampledAtElapsedMs = sampledAtElapsedMs,
    isPlaying = isPlaying,
    playWhenReady = isPlaying,
    playbackState = androidx.media3.common.Player.STATE_READY,
    playbackSpeed = speed,
)