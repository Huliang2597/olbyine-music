package com.olbyine.music.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioFormatInfo(
    val mediaId: String? = null,
    val mime: String? = null,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val bitsPerSample: Int = 0,
    val bitrate: Int = 0,
)

data class StreamQuality(val level: String, val bitrate: Int, val type: String, val sizeBytes: Long)

object NowPlayingFormat {
    private val _format = MutableStateFlow(AudioFormatInfo())
    val format: StateFlow<AudioFormatInfo> = _format.asStateFlow()

    private val _streams = MutableStateFlow<Map<Long, StreamQuality>>(emptyMap())
    val streams: StateFlow<Map<Long, StreamQuality>> = _streams.asStateFlow()

    internal fun update(info: AudioFormatInfo) {
        _format.value = info
    }

    internal fun served(songId: Long, quality: StreamQuality) {
        _streams.value = _streams.value + (songId to quality)
    }
}
