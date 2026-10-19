package com.olbyine.music.playback

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.media.AudioRouting
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioTrackAudioOutput
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import androidx.media3.exoplayer.audio.ForwardingAudioOutput
import androidx.media3.exoplayer.audio.ForwardingAudioOutputProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UsbBitPerfectStatus {
    OFF,
    NO_DEVICE,
    UNSUPPORTED_ANDROID,
    UNSUPPORTED_DEVICE,
    WAITING_FOR_AUDIO,
    ACTIVE,
    NATIVE_RATE,
    OUTPUT_MISMATCH,
    FAILED,
}

internal enum class UsbOutputMode { BIT_PERFECT, NATIVE_RATE }

data class UsbBitPerfectState(
    val status: UsbBitPerfectStatus = UsbBitPerfectStatus.NO_DEVICE,
    val deviceName: String? = null,
    val detail: String? = null,
)

internal object UsbBitPerfectRuntime {
    private val mutableState = MutableStateFlow(UsbBitPerfectState())
    val state: StateFlow<UsbBitPerfectState> = mutableState.asStateFlow()

    fun publish(value: UsbBitPerfectState) {
        mutableState.value = value
    }
}

internal class UsbBitPerfectAudio(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val preferences = appContext.getSharedPreferences("native_settings", Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    val outputProvider = BitPerfectOutputProvider(appContext, audioManager, mainHandler)
    private var player: ExoPlayer? = null
    private var selectedDeviceId: Int? = null
    private var selectedMode = UsbOutputMode.BIT_PERFECT
    private var started = false
    private var mixerListener: AudioManager.OnPreferredMixerAttributesChangedListener? = null

    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SETTING_KEY) refresh()
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refresh()
            if (addedDevices.any(::isUsbOutput)) {
                RETRY_DELAYS_MS.forEach { delay -> mainHandler.postDelayed(retryRefresh, delay) }
            }
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }
    private val retryRefresh = Runnable { refresh() }
    private val playbackListener = object : Player.Listener {
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = refresh()
        override fun onVolumeChanged(volume: Float) = refresh()
        override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) = refresh()
    }

    fun start(player: ExoPlayer) {
        check(!started)
        started = true
        this.player = player
        player.addListener(playbackListener)
        preferences.registerOnSharedPreferenceChangeListener(settingsListener)
        audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val listener = AudioManager.OnPreferredMixerAttributesChangedListener { _, device, _ ->
                outputProvider.onMixerPreferenceChanged(device)
            }
            mixerListener = listener
            audioManager.addOnPreferredMixerAttributesChangedListener(
                java.util.concurrent.Executor { command -> mainHandler.post(command) }, listener,
            )
        }
        refresh()
    }

    private fun refresh() {
        if (!started) return
        val enabled = preferences.getBoolean(SETTING_KEY, true)
        val unmodifiedPlayback = player?.let {
            it.playbackParameters.speed == 1f && it.playbackParameters.pitch == 1f &&
                it.volume == 1f && !it.skipSilenceEnabled
        } == true
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter(::isUsbOutput)
        val routedId = outputProvider.routedDeviceId()
        val preferredDevices = devices.sortedBy { if (it.id == routedId) 0 else 1 }
        val deviceName = devices.firstOrNull()?.productName?.toString()
        val capabilities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            preferredDevices.associateWith(::capabilityOf)
        } else emptyMap()
        val target = when {
            !enabled -> null
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> null
            !unmodifiedPlayback -> null
            else -> preferredDevices.firstOrNull { capabilities[it]?.bitPerfect == true }
                ?: preferredDevices.firstOrNull { (capabilities[it]?.defaultFormats ?: 0) > 0 }
        }
        val mode = if (target != null && capabilities[target]?.bitPerfect == true) UsbOutputMode.BIT_PERFECT
            else UsbOutputMode.NATIVE_RATE
        val previousId = selectedDeviceId
        val previousMode = selectedMode
        selectedDeviceId = target?.id
        selectedMode = mode
        val hadOutput = outputProvider.setTarget(target, mode)
        if (previousId != target?.id || (target != null && previousMode != mode)) {
            player?.setPreferredAudioDevice(target)
            if (hadOutput) {
                val freshSessionId = runCatching { audioManager.generateAudioSessionId() }.getOrDefault(-1)
                if (freshSessionId > 0) {
                    player?.setAudioSessionId(freshSessionId)
                } else if (target != null) {
                    UsbBitPerfectRuntime.publish(UsbBitPerfectState(
                        UsbBitPerfectStatus.WAITING_FOR_AUDIO,
                        target.productName?.toString(),
                        "当前播放无法重新建立音频输出；切换歌曲后重试",
                    ))
                }
            }
        }

        val status = when {
            !enabled -> UsbBitPerfectStatus.OFF
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> UsbBitPerfectStatus.UNSUPPORTED_ANDROID
            devices.isEmpty() -> UsbBitPerfectStatus.NO_DEVICE
            !unmodifiedPlayback -> UsbBitPerfectStatus.OUTPUT_MISMATCH
            target == null -> UsbBitPerfectStatus.UNSUPPORTED_DEVICE
            else -> UsbBitPerfectStatus.WAITING_FOR_AUDIO
        }
        if (target == null) {
            val detail = when {
                status == UsbBitPerfectStatus.OUTPUT_MISMATCH && !unmodifiedPlayback ->
                    "倍速、变调、播放器音量或跳过静音会修改音频数据"
                status == UsbBitPerfectStatus.UNSUPPORTED_DEVICE -> {
                    val capability = preferredDevices.firstOrNull()?.let { capabilities[it] }
                    when {
                        capability?.error != null -> "系统查询 USB 输出能力失败：${capability.error}"
                        else -> "系统没有为这个 USB 设备提供可选的输出格式，只能走普通混音输出"
                    }
                }
                else -> null
            }
            UsbBitPerfectRuntime.publish(UsbBitPerfectState(status, deviceName, detail))
        }
    }

    private data class UsbCapability(val bitPerfect: Boolean, val defaultFormats: Int, val error: String?)

    private fun capabilityOf(device: AudioDeviceInfo): UsbCapability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return UsbCapability(false, 0, null)
        return runCatching {
            val attributes = audioManager.getSupportedMixerAttributes(device)
            UsbCapability(
                bitPerfect = attributes.any { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT },
                defaultFormats = attributes.count { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_DEFAULT },
                error = null,
            )
        }.getOrElse { UsbCapability(false, 0, it.message ?: it.javaClass.simpleName) }
    }

    fun close() {
        if (!started) return
        started = false
        mainHandler.removeCallbacks(retryRefresh)
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        preferences.unregisterOnSharedPreferenceChangeListener(settingsListener)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mixerListener?.let { audioManager.removeOnPreferredMixerAttributesChangedListener(it) }
        }
        mixerListener = null
        player?.removeListener(playbackListener)
        player?.setPreferredAudioDevice(null)
        player = null
        selectedDeviceId = null
        outputProvider.close()
        UsbBitPerfectRuntime.publish(UsbBitPerfectState())
    }

    private fun isUsbOutput(device: AudioDeviceInfo): Boolean = device.isSink && when (device.type) {
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> true
        else -> false
    }

    private companion object {
        const val SETTING_KEY = "usb_bit_perfect_enabled"
        val RETRY_DELAYS_MS = longArrayOf(400L, 1_500L, 4_000L)
    }
}

internal class BitPerfectOutputProvider(
    context: Context,
    private val audioManager: AudioManager,
    private val mainHandler: Handler,
) : ForwardingAudioOutputProvider(AudioTrackAudioOutputProvider.Builder(context).build()) {
    private var target: AudioDeviceInfo? = null
    private var mode = UsbOutputMode.BIT_PERFECT
    private var outputGeneration = 0L
    private var activeOutput: UsbOutput? = null
    private var ownedMixer: OwnedMixer? = null

    @Synchronized
    fun setTarget(device: AudioDeviceInfo?, outputMode: UsbOutputMode): Boolean {
        val hadOutput = activeOutput != null
        if (target?.id != device?.id || mode != outputMode) {
            clearMixer()
            if (device != null) {
                UsbBitPerfectRuntime.publish(
                    UsbBitPerfectState(UsbBitPerfectStatus.WAITING_FOR_AUDIO, device.productName?.toString())
                )
            }
        }
        target = device
        mode = outputMode
        return hadOutput
    }

    @Synchronized
    fun routedDeviceId(): Int? = activeOutput?.track?.routedDevice?.id

    @Synchronized
    override fun getAudioOutput(config: AudioOutputProvider.OutputConfig): AudioOutput {
        if (com.olbyine.music.playback.usb.UsbAudioDriver.isReady) {
            val direct = runCatching { com.olbyine.music.playback.usb.UsbDirectAudioOutput.create(config) }.getOrNull()
            if (direct != null) return EnergyTapOutput(EffectsOutput(direct, config), config)
        }
        val generation = ++outputGeneration
        clearMixer()
        if (target != null) activateMixer(config, generation)
        return try {
            val output = super.getAudioOutput(config)
            val wrapped = if (output is AudioTrackAudioOutput) {
                UsbOutput(output, config, generation)
            } else {
                UsbBitPerfectRuntime.publish(
                    UsbBitPerfectState(UsbBitPerfectStatus.FAILED, target?.productName?.toString(), "无法读取音频输出")
                )
                output
            }
            activeOutput = wrapped as? UsbOutput
            EnergyTapOutput(EffectsOutput(wrapped, config), config)
        } catch (error: AudioOutputProvider.InitializationException) {
            clearMixer()
            UsbBitPerfectRuntime.publish(
                UsbBitPerfectState(UsbBitPerfectStatus.FAILED, target?.productName?.toString(), "音频输出初始化失败")
            )
            throw error
        }
    }

    @Synchronized
    fun close() {
        target = null
        activeOutput = null
        clearMixer()
    }

    @Synchronized
    fun onMixerPreferenceChanged(device: AudioDeviceInfo) {
        if (target?.id == device.id) activeOutput?.let(::verify)
    }

    @Synchronized
    private fun activateMixer(config: AudioOutputProvider.OutputConfig, generation: Long) {
        val device = target ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val format = config.toPlatformFormat() ?: run {
            UsbBitPerfectRuntime.publish(
                UsbBitPerfectState(UsbBitPerfectStatus.OUTPUT_MISMATCH, device.productName?.toString(), "输出格式不支持无混音")
            )
            return
        }
        val attributes = config.audioAttributes.platformAudioAttributes
        val candidates = runCatching { audioManager.getSupportedMixerAttributes(device) }.getOrDefault(emptyList())
        val supported = when (mode) {
            UsbOutputMode.BIT_PERFECT -> candidates.firstOrNull { candidate ->
                candidate.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT &&
                    candidate.format.samePcmFormat(format)
            }
            UsbOutputMode.NATIVE_RATE -> candidates
                .filter {
                    it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_DEFAULT &&
                        it.format.sampleRate == format.sampleRate && it.format.channelMask == format.channelMask
                }
                .maxByOrNull { if (it.format.encoding == format.encoding) 100 else it.format.encoding.precisionRank() }
        }
        if (supported == null) {
            val detail = if (mode == UsbOutputMode.BIT_PERFECT) "歌曲输出格式与 USB 设备不匹配"
                else "USB 设备不支持 ${format.sampleRate} Hz，由系统重采样输出"
            UsbBitPerfectRuntime.publish(
                UsbBitPerfectState(UsbBitPerfectStatus.OUTPUT_MISMATCH, device.productName?.toString(), detail)
            )
            return
        }
        val accepted = runCatching {
            audioManager.setPreferredMixerAttributes(attributes, device, supported)
        }.getOrDefault(false)
        if (accepted) {
            ownedMixer = OwnedMixer(device, attributes, supported, generation)
            UsbBitPerfectRuntime.publish(
                UsbBitPerfectState(UsbBitPerfectStatus.WAITING_FOR_AUDIO, device.productName?.toString())
            )
        } else {
            UsbBitPerfectRuntime.publish(
                UsbBitPerfectState(UsbBitPerfectStatus.FAILED, device.productName?.toString(), "系统拒绝了 USB 输出格式设置")
            )
        }
    }

    @Synchronized
    private fun clearMixer() {
        val mixer = ownedMixer ?: return
        ownedMixer = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { audioManager.clearPreferredMixerAttributes(mixer.attributes, mixer.device) }
        }
    }

    @Synchronized
    private fun verify(output: UsbOutput) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val device = target ?: return
        if (output.generation != outputGeneration) return
        val mixer = ownedMixer
        if (mixer == null || mixer.device.id != device.id || mixer.generation != output.generation) return
        val track = output.track
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
            UsbBitPerfectRuntime.publish(UsbBitPerfectState(UsbBitPerfectStatus.WAITING_FOR_AUDIO, device.productName?.toString()))
            return
        }
        val actualFormat = track.format
        val routed = track.routedDevice
        val bitPerfect = mixer.mixer.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
        val configured = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { audioManager.getPreferredMixerAttributes(mixer.attributes, device) }
                .getOrNull()?.let {
                    it.mixerBehavior == mixer.mixer.mixerBehavior && it.format.sampleRate == actualFormat.sampleRate &&
                        (!bitPerfect || it.format.samePcmFormat(actualFormat))
                } == true
        } else false
        val formatOk = if (bitPerfect) actualFormat.samePcmFormat(mixer.mixer.format)
            else actualFormat.sampleRate == mixer.mixer.format.sampleRate
        val status = when {
            !formatOk || !configured -> UsbBitPerfectStatus.FAILED
            routed == null -> UsbBitPerfectStatus.WAITING_FOR_AUDIO
            routed.id != device.id -> UsbBitPerfectStatus.OUTPUT_MISMATCH
            bitPerfect -> UsbBitPerfectStatus.ACTIVE
            else -> UsbBitPerfectStatus.NATIVE_RATE
        }
        val detail = when (status) {
            UsbBitPerfectStatus.ACTIVE ->
                "独占输出：${actualFormat.sampleRate} Hz · ${actualFormat.encoding.pcmName()}"
            UsbBitPerfectStatus.NATIVE_RATE ->
                "按原始 ${actualFormat.sampleRate} Hz · ${mixer.mixer.format.encoding.pcmName()} 输出，未重采样；" +
                    "系统未开放 bit-perfect，仍经过系统混音"
            else -> null
        }
        UsbBitPerfectRuntime.publish(UsbBitPerfectState(status, device.productName?.toString(), detail))
    }

    private inner class UsbOutput(
        output: AudioTrackAudioOutput,
        val config: AudioOutputProvider.OutputConfig,
        val generation: Long,
    ) : ForwardingAudioOutput(output) {
        val track: AudioTrack = output.audioTrack
        private val routeListener = AudioRouting.OnRoutingChangedListener { verify(this) }

        init {
            track.addOnRoutingChangedListener(routeListener, mainHandler)
        }

        override fun play() {
            super.play()
            verify(this)
        }

        override fun pause() {
            super.pause()
            verify(this)
        }

        override fun setPreferredDevice(device: AudioDeviceInfo?) {
            super.setPreferredDevice(device)
            verify(this)
        }

        override fun release() {
            track.removeOnRoutingChangedListener(routeListener)
            super.release()
            synchronized(this@BitPerfectOutputProvider) {
                if (generation == outputGeneration) {
                    activeOutput = null
                    clearMixer()
                    target?.let {
                        UsbBitPerfectRuntime.publish(
                            UsbBitPerfectState(UsbBitPerfectStatus.WAITING_FOR_AUDIO, it.productName?.toString())
                        )
                    }
                }
            }
        }
    }

    private data class OwnedMixer(
        val device: AudioDeviceInfo,
        val attributes: android.media.AudioAttributes,
        val mixer: AudioMixerAttributes,
        val generation: Long,
    )
}

private fun Int.precisionRank(): Int = when (this) {
    AudioFormat.ENCODING_PCM_FLOAT -> 4
    AudioFormat.ENCODING_PCM_32BIT -> 3
    AudioFormat.ENCODING_PCM_24BIT_PACKED -> 2
    AudioFormat.ENCODING_PCM_16BIT -> 1
    else -> 0
}

private fun Int.pcmName(): String = when (this) {
    AudioFormat.ENCODING_PCM_16BIT -> "PCM 16 位"
    AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM 24 位"
    AudioFormat.ENCODING_PCM_32BIT -> "PCM 32 位"
    AudioFormat.ENCODING_PCM_FLOAT -> "PCM 浮点"
    else -> "PCM"
}

private fun AudioOutputProvider.OutputConfig.toPlatformFormat(): AudioFormat? {
    if (isOffload || isTunneling) return null
    val platformEncoding = when (encoding) {
        C.ENCODING_PCM_16BIT -> AudioFormat.ENCODING_PCM_16BIT
        C.ENCODING_PCM_24BIT -> AudioFormat.ENCODING_PCM_24BIT_PACKED
        C.ENCODING_PCM_32BIT -> AudioFormat.ENCODING_PCM_32BIT
        C.ENCODING_PCM_FLOAT -> AudioFormat.ENCODING_PCM_FLOAT
        else -> return null
    }
    return runCatching {
        AudioFormat.Builder().setEncoding(platformEncoding).setChannelMask(channelMask)
            .setSampleRate(sampleRate).build()
    }.getOrNull()
}

private fun AudioFormat.samePcmFormat(other: AudioFormat): Boolean =
    encoding == other.encoding && channelMask == other.channelMask && sampleRate == other.sampleRate
