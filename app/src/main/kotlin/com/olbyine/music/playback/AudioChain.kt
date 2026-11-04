package com.olbyine.music.playback

import androidx.media3.common.C
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioChainState(
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val encoding: String = "",
    val direct: Boolean = false,
    val deviceName: String? = null,
)

object AudioChainRuntime {
    private val mutableState = MutableStateFlow(AudioChainState())
    val state: StateFlow<AudioChainState> = mutableState.asStateFlow()

    internal fun publish(
        sampleRate: Int,
        channels: Int,
        encoding: Int,
        direct: Boolean,
        deviceName: String?,
    ) {
        mutableState.value = AudioChainState(
            sampleRate = sampleRate,
            channels = channels,
            encoding = encodingName(encoding),
            direct = direct,
            deviceName = deviceName,
        )
    }

    internal fun routed(deviceName: String?) {
        val current = mutableState.value
        if (current.deviceName == deviceName) return
        mutableState.value = current.copy(deviceName = deviceName)
    }

    internal fun reset() {
        mutableState.value = AudioChainState()
    }

    private fun encodingName(encoding: Int): String = when (encoding) {
        C.ENCODING_PCM_16BIT -> "PCM 16 位"
        C.ENCODING_PCM_24BIT -> "PCM 24 位"
        C.ENCODING_PCM_32BIT -> "PCM 32 位"
        C.ENCODING_PCM_FLOAT -> "PCM 32 位浮点"
        else -> "PCM"
    }
}
