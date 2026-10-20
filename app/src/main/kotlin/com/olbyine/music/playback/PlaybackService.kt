package com.olbyine.music.playback

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import androidx.media3.exoplayer.audio.ForwardingAudioOutputProvider
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.olbyine.music.OlbyineApplication
import com.olbyine.music.data.SettingsRepository
import com.olbyine.music.playback.usb.UsbAudioDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.nift4.alacdecoder.AlacRenderer
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var usbBitPerfectAudio: UsbBitPerfectAudio? = null
    private var exoPlayer: ExoPlayer? = null
    private var sessionPlayer: DacVolumePlayer? = null
    private var floatOutput = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var consecutiveErrors = 0
    private val preferences: SharedPreferences by lazy { getSharedPreferences("native_settings", Context.MODE_PRIVATE) }
    private val app: OlbyineApplication get() = application as OlbyineApplication
    private lateinit var notificationProvider: LyricNotificationProvider
    private var lyricOutputs: LyricOutputs? = null
    private var transitions: TransitionController? = null
    private val playCounter = PlayCounter()

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == UsbAudioDriver.PREF_KEY || key == SettingsRepository.KEY_FLOAT_OUTPUT) rebuildIfNeeded()
    }

    private val driverListener: () -> Unit = {
        val player = exoPlayer
        if (player != null && player.mediaItemCount > 0) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val fresh = runCatching { audioManager.generateAudioSessionId() }.getOrDefault(-1)
            if (fresh > 0) player.setAudioSessionId(fresh)
        }
        sessionPlayer?.notifyDeviceVolume()
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED)) {
                app.lyricsHub.setCurrent(player.currentMediaItem?.mediaId)
            }
            if (events.containsAny(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED)) {
                preferences.edit()
                    .putBoolean(SettingsRepository.KEY_PLAY_SHUFFLE, player.shuffleModeEnabled)
                    .putInt(SettingsRepository.KEY_PLAY_REPEAT, player.repeatMode)
                    .apply()
            }
            lyricOutputs?.onPlayerChanged()
            transitions?.onPlayerChanged()
            playCounter.onPlayerChanged()
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                transitions?.onDiscontinuity()
                playCounter.onSeek()
            }
            lyricOutputs?.onPlayerChanged()
        }

        override fun onPlayerError(error: PlaybackException) {
            val player = exoPlayer ?: return
            if (consecutiveErrors >= 3) return
            consecutiveErrors++
            val hasNext = player.hasNextMediaItem() || player.repeatMode == Player.REPEAT_MODE_ALL && player.mediaItemCount > 1
            if (!hasNext) return
            handler.postDelayed({
                if (player.hasNextMediaItem()) player.seekToNextMediaItem() else player.seekTo(0, 0L)
                player.prepare()
                player.play()
            }, 600)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) consecutiveErrors = 0
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        UsbAudioDriver.init(applicationContext)
        notificationProvider = LyricNotificationProvider(this)
        setMediaNotificationProvider(notificationProvider)
        floatOutput = wantsFloatOutput()
        val player = buildPlayer(floatOutput)
        val wrapped = DacVolumePlayer(player)
        exoPlayer = player
        sessionPlayer = wrapped
        mediaSession = MediaSession.Builder(this, wrapped)
            .setSessionActivity(com.olbyine.music.MainActivity.openPlayerIntent(this))
            .build()
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        UsbAudioDriver.addReadyListener(driverListener)
        scope.launch {
            UsbAudioDriver.state.map { it.volume to UsbAudioDriver.isReady }.distinctUntilChanged().drop(1).collect {
                sessionPlayer?.notifyDeviceVolume()
            }
        }
        lyricOutputs = LyricOutputs(this, notificationProvider, ::triggerNotificationUpdate) { exoPlayer }.also { it.start(scope) }
        transitions = TransitionController(
            context = this,
            createTailPlayer = ::buildTailPlayer,
            mainPlayer = { exoPlayer },
            mode = { app.settings.settings.value.transitionMode },
            seconds = { app.settings.settings.value.crossfadeSeconds },
        )
    }

    private fun wantsFloatOutput(): Boolean =
        UsbAudioDriver.enabled || runCatching { preferences.getBoolean(SettingsRepository.KEY_FLOAT_OUTPUT, false) }.getOrDefault(false)

    @OptIn(UnstableApi::class)
    private fun dataSourceFactory(): DataSource.Factory {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(15_000)
        return ResolvingDataSource.Factory(DefaultDataSource.Factory(this, http)) { spec ->
            if (spec.uri.scheme == "http" || spec.uri.scheme == "https") {
                val headers = app.webDav.headersFor(spec.uri.toString())
                return@Factory if (headers.isEmpty()) spec else spec.withAdditionalHeaders(headers)
            }
            if (spec.uri.scheme == "sc") {
                val id = spec.uri.host?.removePrefix("t")?.toLongOrNull() ?: throw IOException("无效的 SoundCloud 曲目")
                val path = spec.uri.encodedPath.orEmpty().removePrefix("/")
                val url = try {
                    when {
                        path.isEmpty() -> app.soundCloud.streamUrl(id, hls = false)
                        path == "playlist.m3u8" -> app.soundCloud.streamUrl(id, hls = true)
                        else -> app.soundCloud.hlsSegment(id, path + (spec.uri.encodedQuery?.let { "?$it" } ?: ""))
                    }
                } catch (error: Exception) {
                    throw IOException(error.message ?: "SoundCloud 无法播放这首歌", error)
                }
                return@Factory spec.withUri(Uri.parse(url))
            }
            if (spec.uri.scheme == "kw") {
                val id = spec.uri.lastPathSegment?.toLongOrNull() ?: throw IOException("无效的歌曲 ID")
                val url = try {
                    resolveKuwoUrl(id)
                } catch (error: Exception) {
                    throw IOException(error.message ?: "洛雪音乐无法播放这首歌", error)
                }
                return@Factory spec.withUri(Uri.parse(url))
            }
            if (spec.uri.scheme != "ncm") return@Factory spec
            val id = spec.uri.lastPathSegment?.toLongOrNull() ?: throw IOException("无效的网易云歌曲")
            val url = try {
                app.netease.playUrl(id, spec.uri.getQueryParameter("q") ?: app.settings.settings.value.neteaseQuality)
            } catch (error: Exception) {
                throw IOException(error.message ?: "网易云无法播放这首歌", error)
            }
            spec.withUri(Uri.parse(url))
        }
    }

    /**
     * 洛雪自定义源优先：脚本声明了 kw 源就交给脚本取地址，失败再退回内置的酷我直链。
     */
    private fun resolveKuwoUrl(id: Long): String {
        val quality = app.settings.settings.value.kuwoQuality
        val song = app.kuwo.song(id)
        val musicInfo = org.json.JSONObject()
            .put("songmid", id.toString())
            .put("songId", id)
            .put("id", id.toString())
            .put("name", song?.title.orEmpty())
            .put("singer", song?.artist.orEmpty())
            .put("albumName", song?.album.orEmpty())
            .put("interval", if ((song?.durationMs ?: 0L) > 0L) "${song!!.durationMs / 1000}s" else "")
            .put("source", "kw")
        val scripts = runCatching { app.lxScripts.enabled() }.getOrDefault(emptyList())
        scripts.forEach { script ->
            val info = runCatching {
                kotlinx.coroutines.runBlocking { app.lxRunner.prepare(script) }
            }.onFailure { error ->
                app.lxScripts.markFailed(script.id, error.message ?: "初始化失败")
            }.getOrNull() ?: return@forEach
            if (!script.initialized) app.lxScripts.markInitialized(script.id, info)
            val kw = info.firstOrNull { it.key == "kw" } ?: return@forEach
            val level = com.olbyine.music.data.KuwoClient.resolveQuality(quality, kw.qualitys) ?: return@forEach
            val url = runCatching {
                kotlinx.coroutines.runBlocking { app.lxRunner.musicUrl(script, "kw", level, musicInfo) }
            }.getOrNull()
            if (!url.isNullOrBlank()) return url
        }
        return app.kuwo.playUrlBlocking(id, quality)
    }

    @OptIn(UnstableApi::class)
    private fun extractorsFactory() = androidx.media3.extractor.ExtractorsFactory {
        arrayOf(*androidx.media3.extractor.DefaultExtractorsFactory().createExtractors(), AiffExtractor())
    }

    @OptIn(UnstableApi::class)
    private fun renderersFactory(float: Boolean): DefaultRenderersFactory {
        val factory = object : DefaultRenderersFactory(this) {
            override fun buildAudioRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                audioSink: AudioSink,
                eventHandler: Handler,
                eventListener: AudioRendererEventListener,
                out: ArrayList<Renderer>,
            ) {
                out.add(AlacRenderer(eventHandler, eventListener, audioSink))
                super.buildAudioRenderers(
                    context,
                    extensionRendererMode,
                    mediaCodecSelector,
                    enableDecoderFallback,
                    audioSink,
                    eventHandler,
                    eventListener,
                    out,
                )
            }
        }
        factory.setEnableAudioFloatOutput(float)
        return factory
    }

    @OptIn(UnstableApi::class)
    private fun buildPlayer(float: Boolean): ExoPlayer {
        val usbAudio = UsbBitPerfectAudio(this)
        val player = ExoPlayer.Builder(this, renderersFactory(float))
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory(), extractorsFactory()))
            .setAudioOutputProvider(usbAudio.outputProvider)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.repeatMode = runCatching { preferences.getInt(SettingsRepository.KEY_PLAY_REPEAT, Player.REPEAT_MODE_OFF) }.getOrDefault(Player.REPEAT_MODE_OFF)
            .takeIf { it in Player.REPEAT_MODE_OFF..Player.REPEAT_MODE_ALL } ?: Player.REPEAT_MODE_OFF
        player.shuffleModeEnabled = runCatching { preferences.getBoolean(SettingsRepository.KEY_PLAY_SHUFFLE, false) }.getOrDefault(false)
        player.addListener(playerListener)
        player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioInputFormatChanged(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                format: androidx.media3.common.Format,
                decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?,
            ) {
                val bits = when (format.pcmEncoding) {
                    C.ENCODING_PCM_8BIT -> 8
                    C.ENCODING_PCM_16BIT -> 16
                    C.ENCODING_PCM_24BIT -> 24
                    C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 32
                    else -> 0
                }
                NowPlayingFormat.update(
                    AudioFormatInfo(
                        mediaId = player.currentMediaItem?.mediaId,
                        mime = format.sampleMimeType,
                        sampleRate = format.sampleRate.takeIf { it > 0 } ?: 0,
                        channels = format.channelCount.takeIf { it > 0 } ?: 0,
                        bitsPerSample = bits,
                        bitrate = listOf(format.averageBitrate, format.bitrate, format.peakBitrate).firstOrNull { it > 0 } ?: 0,
                    ),
                )
            }
        })
        usbBitPerfectAudio?.close()
        usbBitPerfectAudio = usbAudio
        usbAudio.start(player)
        return player
    }

    @OptIn(UnstableApi::class)
    private fun buildTailPlayer(): ExoPlayer {
        val provider = object : ForwardingAudioOutputProvider(AudioTrackAudioOutputProvider.Builder(this).build()) {
            override fun getAudioOutput(config: AudioOutputProvider.OutputConfig): AudioOutput =
                EffectsOutput(super.getAudioOutput(config), config)
        }
        return ExoPlayer.Builder(this, renderersFactory(floatOutput))
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory(), extractorsFactory()))
            .setAudioOutputProvider(provider)
            .setAudioAttributes(
                AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(),
                false,
            )
            .build()
    }

    @OptIn(UnstableApi::class)
    private fun rebuildIfNeeded() {
        val want = wantsFloatOutput()
        if (want == floatOutput) return
        floatOutput = want
        val session = mediaSession ?: return
        val old = exoPlayer ?: return
        val items = (0 until old.mediaItemCount).map(old::getMediaItemAt)
        val index = old.currentMediaItemIndex
        val position = old.currentPosition
        val playWhenReady = old.playWhenReady
        val repeat = old.repeatMode
        val shuffle = old.shuffleModeEnabled
        val parameters = old.playbackParameters
        old.pause()
        old.removeListener(playerListener)
        val fresh = buildPlayer(want)
        if (items.isNotEmpty()) {
            fresh.setMediaItems(items, index.coerceIn(0, items.lastIndex), position)
            fresh.repeatMode = repeat
            fresh.shuffleModeEnabled = shuffle
            fresh.playbackParameters = parameters
            fresh.prepare()
            fresh.playWhenReady = playWhenReady
        }
        val wrapped = DacVolumePlayer(fresh)
        exoPlayer = fresh
        sessionPlayer = wrapped
        session.player = wrapped
        old.release()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
        lyricOutputs?.stop()
        lyricOutputs = null
        transitions?.release()
        transitions = null
        playCounter.report()
        playCounter.stop()
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        UsbAudioDriver.removeReadyListener(driverListener)
        usbBitPerfectAudio?.close()
        usbBitPerfectAudio = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        exoPlayer = null
        sessionPlayer = null
        super.onDestroy()
    }

    private inner class PlayCounter {
        private var mediaId: String? = null
        private var heardMs = 0L
        private var lastPosition = -1L
        private var counted = false
        private var running = false

        private val tick = object : Runnable {
            override fun run() {
                val player = exoPlayer
                if (player == null || !player.isPlaying) {
                    running = false
                    return
                }
                val position = player.currentPosition
                val delta = position - lastPosition
                if (lastPosition >= 0 && delta in 1..3_000) heardMs += delta
                lastPosition = position
                val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                val needed = minOf(30_000L, duration / 2).coerceAtLeast(8_000L)
                val id = mediaId
                if (!counted && id != null && heardMs >= needed) {
                    counted = true
                    when {
                        id.startsWith("nd:") -> app.appScope.launch { runCatching { app.navidrome.scrobble(id.removePrefix("nd:")) } }
                        !id.startsWith("ncm:") && !id.startsWith("sc:") -> scope.launch { runCatching { app.musicLibrary.recordPlay(id) } }
                    }
                }
                handler.postDelayed(this, 1_000L)
            }
        }

        fun report() {
            val id = mediaId ?: return
            val seconds = (heardMs / 1000L).toInt()
            val songId = id.takeIf { it.startsWith("ncm:") }?.removePrefix("ncm:")?.toLongOrNull() ?: return
            if (seconds < 10) return
            heardMs = 0L
            app.appScope.launch { runCatching { app.netease.scrobble(songId, 0L, seconds) } }
        }

        fun onPlayerChanged() {
            val player = exoPlayer ?: return
            val id = player.currentMediaItem?.mediaId
            if (id != mediaId) {
                report()
                mediaId = id
                heardMs = 0L
                counted = false
                lastPosition = player.currentPosition
            }
            if (player.isPlaying && !running) {
                running = true
                lastPosition = player.currentPosition
                handler.postDelayed(tick, 1_000L)
            }
        }

        fun onSeek() {
            lastPosition = exoPlayer?.currentPosition ?: -1L
        }

        fun stop() {
            handler.removeCallbacks(tick)
            running = false
        }
    }
}

@OptIn(UnstableApi::class)
private class DacVolumePlayer(player: Player) : ForwardingPlayer(player) {
    private val listeners = CopyOnWriteArraySet<Player.Listener>()

    private val remote: Boolean get() = UsbAudioDriver.isReady

    private val remoteInfo = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
        .setMinVolume(0)
        .setMaxVolume(100)
        .build()

    override fun addListener(listener: Player.Listener) {
        super.addListener(listener)
        listeners += listener
    }

    override fun removeListener(listener: Player.Listener) {
        super.removeListener(listener)
        listeners -= listener
    }

    override fun getDeviceInfo(): DeviceInfo = if (remote) remoteInfo else super.getDeviceInfo()

    override fun getDeviceVolume(): Int = if (remote) UsbAudioDriver.state.value.volume else super.getDeviceVolume()

    override fun isDeviceMuted(): Boolean = if (remote) UsbAudioDriver.state.value.volume == 0 else super.isDeviceMuted()

    @Deprecated("Deprecated in Java")
    override fun setDeviceVolume(volume: Int) {
        if (remote) UsbAudioDriver.setVolume(volume) else @Suppress("DEPRECATION") super.setDeviceVolume(volume)
    }

    override fun setDeviceVolume(volume: Int, flags: Int) {
        if (remote) UsbAudioDriver.setVolume(volume) else super.setDeviceVolume(volume, flags)
    }

    @Deprecated("Deprecated in Java")
    override fun increaseDeviceVolume() {
        if (remote) UsbAudioDriver.adjustVolume(4) else @Suppress("DEPRECATION") super.increaseDeviceVolume()
    }

    override fun increaseDeviceVolume(flags: Int) {
        if (remote) UsbAudioDriver.adjustVolume(4) else super.increaseDeviceVolume(flags)
    }

    @Deprecated("Deprecated in Java")
    override fun decreaseDeviceVolume() {
        if (remote) UsbAudioDriver.adjustVolume(-4) else @Suppress("DEPRECATION") super.decreaseDeviceVolume()
    }

    override fun decreaseDeviceVolume(flags: Int) {
        if (remote) UsbAudioDriver.adjustVolume(-4) else super.decreaseDeviceVolume(flags)
    }

    override fun getAvailableCommands(): Player.Commands {
        val base = super.getAvailableCommands()
        if (!remote) return base
        return base.buildUpon()
            .addAll(
                Player.COMMAND_GET_DEVICE_VOLUME,
                Player.COMMAND_SET_DEVICE_VOLUME,
                Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
                Player.COMMAND_ADJUST_DEVICE_VOLUME,
                Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
            )
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean = when (command) {
        Player.COMMAND_GET_DEVICE_VOLUME,
        Player.COMMAND_SET_DEVICE_VOLUME,
        Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
        Player.COMMAND_ADJUST_DEVICE_VOLUME,
        Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS -> remote || super.isCommandAvailable(command)
        else -> super.isCommandAvailable(command)
    }

    fun notifyDeviceVolume() {
        val info = deviceInfo
        val volume = deviceVolume
        val muted = isDeviceMuted
        listeners.forEach { listener ->
            listener.onDeviceInfoChanged(info)
            listener.onDeviceVolumeChanged(volume, muted)
            listener.onAvailableCommandsChanged(availableCommands)
        }
    }
}
