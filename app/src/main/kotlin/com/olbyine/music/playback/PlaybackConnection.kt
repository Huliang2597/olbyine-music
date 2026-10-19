package com.olbyine.music.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.olbyine.music.data.Track
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.io.File
import java.util.ArrayDeque
import kotlin.math.abs

data class PlaybackState(
    val connected: Boolean = false,
    val currentMediaId: String? = null,
    val currentIndex: Int = -1,
    val mediaItemCount: Int = 0,
    val queueIds: List<String> = emptyList(),
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val sampledAtElapsedMs: Long = SystemClock.elapsedRealtime(),
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val playbackState: Int = Player.STATE_IDLE,
    val playbackSpeed: Float = 1f,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleEnabled: Boolean = false,
    val pendingSeekMs: Long? = null,
    val error: String? = null,
    val currentTitle: String? = null,
    val currentArtist: String? = null,
    val currentAlbum: String? = null,
    val currentArtwork: String? = null,
    val currentUri: String? = null,
) {
    val isBuffering: Boolean get() = playbackState == Player.STATE_BUFFERING

    fun estimatedPosition(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Long {
        val base = pendingSeekMs ?: positionMs
        if (!isPlaying || pendingSeekMs != null) return base.coerceIn(0L, durationMs.coerceAtLeast(base))
        val advanced = base + ((nowElapsedMs - sampledAtElapsedMs).coerceAtLeast(0L) * playbackSpeed).toLong()
        return if (durationMs > 0L) advanced.coerceIn(0L, durationMs) else advanced.coerceAtLeast(0L)
    }
}

class PlaybackConnection(context: Context) {
    private val appContext = context.applicationContext
    private val executor = ContextCompat.getMainExecutor(appContext)
    private val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
    private val controllerFuture: ListenableFuture<MediaController> =
        MediaController.Builder(appContext, token).buildAsync()
    private var controller: MediaController? = null
    private val pendingActions = ArrayDeque<(MediaController) -> Unit>()
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()
    val usbBitPerfectState: StateFlow<UsbBitPerfectState> = UsbBitPerfectRuntime.state

    @OptIn(ExperimentalCoroutinesApi::class)
    fun positionUpdates(intervalMs: Long): Flow<Long> {
        require(intervalMs > 0L)
        return state.flatMapLatest { snapshot ->
            flow {
                emit(snapshot.estimatedPosition())
                while (snapshot.isPlaying) {
                    delay(intervalMs)
                    emit(state.value.estimatedPosition())
                }
            }
        }
    }

    init {
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }
                    .onSuccess { connected ->
                        controller = connected
                        connected.addListener(listener)
                        while (pendingActions.isNotEmpty()) pendingActions.removeFirst()(connected)
                        publish(connected, clearError = true)
                    }
                    .onFailure { error ->
                        Log.e(TAG, "Media session connection failed", error)
                        _state.value = _state.value.copy(
                            connected = false,
                            error = "播放器暂时不可用",
                        )
                    }
            },
            executor,
        )
    }

    fun playQueue(tracks: List<Track>, startIndex: Int, startPositionMs: Long = 0L) {
        if (tracks.isEmpty()) return
        runWhenReady { player ->
            val safeIndex = startIndex.coerceIn(tracks.indices)
            player.setMediaItems(tracks.map(::mediaItem), safeIndex, startPositionMs.coerceAtLeast(0L))
            player.prepare()
            player.play()
        }
    }

    fun restoreQueue(tracks: List<Track>, startIndex: Int, positionMs: Long) {
        if (tracks.isEmpty()) return
        runWhenReady { player ->
            if (player.mediaItemCount != 0) return@runWhenReady
            val safeIndex = startIndex.coerceIn(tracks.indices)
            player.setMediaItems(tracks.map(::mediaItem), safeIndex, positionMs.coerceAtLeast(0L))
            player.prepare()
            player.pause()
        }
    }

    fun togglePlayPause() = runWhenReady { player ->
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
            player.prepare()
            player.play()
        }
    }

    fun play() = runWhenReady { it.play() }

    fun pause() = runWhenReady { it.pause() }

    fun retryCurrent() = runWhenReady { player ->
        val index = player.currentMediaItemIndex
        if (index in 0 until player.mediaItemCount) {
            player.seekTo(index, player.currentPosition.coerceAtLeast(0L))
            player.prepare()
            player.play()
        }
    }

    fun seekTo(positionMs: Long) = runWhenReady { player ->
        val target = if (player.duration > 0L) positionMs.coerceIn(0L, player.duration) else positionMs.coerceAtLeast(0L)
        _state.value = _state.value.copy(pendingSeekMs = target, positionMs = target)
        player.seekTo(target)
    }

    fun skipNext() = runWhenReady { player ->
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
        else if (player.repeatMode != Player.REPEAT_MODE_OFF) player.seekTo(0, 0L)
    }

    fun skipPrevious() = runWhenReady { player ->
        if (player.currentPosition > 3_000L) player.seekTo(0L)
        else if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
        else player.seekTo(0L)
    }

    fun playQueueIndex(index: Int) = runWhenReady { player ->
        if (index in 0 until player.mediaItemCount) {
            player.seekToDefaultPosition(index)
            player.prepare()
            player.play()
        }
    }

    fun cycleRepeatMode() = runWhenReady { player ->
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleShuffle() = runWhenReady { player ->
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun setPlayMode(shuffle: Boolean, repeatMode: Int) = runWhenReady { player ->
        player.shuffleModeEnabled = shuffle
        player.repeatMode = repeatMode
    }

    fun setPlaybackSpeed(speed: Float) = runWhenReady { player ->
        player.setPlaybackSpeed(speed.coerceIn(0.5f, 2f))
    }

    fun insertNext(tracks: List<Track>) = runWhenReady { player ->
        if (tracks.isEmpty()) return@runWhenReady
        val empty = player.mediaItemCount == 0
        val existing = (0 until player.mediaItemCount).filter { i -> i != player.currentMediaItemIndex && tracks.any { it.id == player.getMediaItemAt(i).mediaId } }
        existing.sortedDescending().forEach { player.removeMediaItem(it) }
        val at = if (empty) 0 else player.currentMediaItemIndex + 1
        player.addMediaItems(at, tracks.filter { empty || it.id != player.currentMediaItem?.mediaId }.map(::mediaItem))
        if (empty) {
            player.prepare()
            player.play()
        }
    }

    fun enqueue(tracks: List<Track>) = runWhenReady { player ->
        if (tracks.isEmpty()) return@runWhenReady
        val empty = player.mediaItemCount == 0
        player.addMediaItems(tracks.map(::mediaItem))
        if (empty) {
            player.prepare()
            player.play()
        }
    }

    fun moveQueueItem(from: Int, to: Int) = runWhenReady { player ->
        if (from in 0 until player.mediaItemCount && to in 0 until player.mediaItemCount && from != to) player.moveMediaItem(from, to)
    }

    fun removeQueueIndex(index: Int) = runWhenReady { player ->
        if (index in 0 until player.mediaItemCount && index != player.currentMediaItemIndex) player.removeMediaItem(index)
    }

    fun reloadCurrent(quality: String? = null) = runWhenReady { player ->
        val index = player.currentMediaItemIndex
        val item = player.currentMediaItem ?: return@runWhenReady
        val uri = item.localConfiguration?.uri ?: return@runWhenReady
        val position = player.currentPosition
        val playing = player.playWhenReady
        val fresh = uri.buildUpon().clearQuery()
            .apply { if (quality != null) appendQueryParameter("q", quality) }
            .appendQueryParameter("r", System.currentTimeMillis().toString()).build()
        player.replaceMediaItem(index, item.buildUpon().setUri(fresh).build())
        player.seekTo(index, position)
        player.prepare()
        player.playWhenReady = playing
    }

    fun removeTrack(mediaId: String) = runWhenReady { player ->
        val index = (0 until player.mediaItemCount).firstOrNull {
            player.getMediaItemAt(it).mediaId == mediaId
        } ?: return@runWhenReady
        player.removeMediaItem(index)
    }

    private fun runWhenReady(action: (MediaController) -> Unit) {
        val active = controller
        if (active != null) action(active) else pendingActions.addLast(action)
    }

    private fun publish(player: MediaController, clearError: Boolean = false) {
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        _state.value = PlaybackState(
            connected = true,
            currentMediaId = player.currentMediaItem?.mediaId,
            currentIndex = player.currentMediaItemIndex,
            mediaItemCount = player.mediaItemCount,
            queueIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId },
            durationMs = duration,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
            sampledAtElapsedMs = SystemClock.elapsedRealtime(),
            isPlaying = player.isPlaying,
            playWhenReady = player.playWhenReady,
            playbackState = player.playbackState,
            playbackSpeed = player.playbackParameters.speed,
            repeatMode = player.repeatMode,
            shuffleEnabled = player.shuffleModeEnabled,
            pendingSeekMs = _state.value.pendingSeekMs?.takeUnless {
                abs(player.currentPosition.coerceAtLeast(0L) - it) <= SEEK_SETTLED_TOLERANCE_MS
            },
            error = if (clearError) null else _state.value.error,
            currentTitle = player.currentMediaItem?.mediaMetadata?.let { it.extras?.getString("olbyine.title") ?: it.title?.toString() },
            currentArtist = player.currentMediaItem?.mediaMetadata?.let { it.extras?.getString("olbyine.artist") ?: it.artist?.toString() },
            currentAlbum = player.currentMediaItem?.mediaMetadata?.albumTitle?.toString(),
            currentArtwork = player.currentMediaItem?.mediaMetadata?.artworkUri?.toString(),
            currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString(),
        )
    }

    private fun mediaItem(track: Track): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .apply {
                if (track.durationMs > 0) setDurationMs(track.durationMs)
                val artwork = track.coverPath?.let { Uri.fromFile(File(it)) } ?: track.artworkUrl?.let(Uri::parse)
                artwork?.let(::setArtworkUri)
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(track.uri)
            .apply { if (track.mimeType == androidx.media3.common.MimeTypes.APPLICATION_M3U8) setMimeType(track.mimeType) }
            .setMediaMetadata(metadata)
            .build()
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val active = controller ?: return
            publish(active, clearError = events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION))
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
            ) {
                _state.value = _state.value.copy(pendingSeekMs = null)
            }
            controller?.let(::publish)
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Playback failed: ${error.errorCodeName} (${error.errorCode})", error)
            _state.value = _state.value.copy(error = "无法播放此歌曲")
        }
    }

    private companion object {
        const val TAG = "OlbyinePlayback"
        const val SEEK_SETTLED_TOLERANCE_MS = 350L
    }
}
