package com.olbyine.music.playback.usb

import android.media.AudioDeviceInfo
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArraySet

@UnstableApi
internal class UsbDirectAudioOutput private constructor(
    private val config: AudioOutputProvider.OutputConfig,
    private val stream: UsbAudioDriver.ActiveStream,
    private val encoding: Int,
    private val channels: Int,
) : AudioOutput {
    private val inputFrameBytes = bytesPerSample(encoding) * channels
    private val listeners = CopyOnWriteArraySet<AudioOutput.Listener>()
    private var volume = 1f
    private var playing = false
    private var reportedAdvance = false
    private var lastUnderruns = 0
    private var staging: ByteBuffer? = null
    private var released = false

    override fun play() {
        playing = true
        reportedAdvance = false
        synchronized(stream) { if (!stream.closed) UsbAudioNative.nativeSetPaused(stream.handle, false) }
    }

    override fun pause() {
        playing = false
        synchronized(stream) { if (!stream.closed) UsbAudioNative.nativeSetPaused(stream.handle, true) }
    }

    override fun write(buffer: ByteBuffer, encodedAccessUnitCount: Int, presentationTimeUs: Long): Boolean =
        synchronized(stream) { writeLocked(buffer) }

    private fun writeLocked(buffer: ByteBuffer): Boolean {
        if (stream.closed || UsbAudioNative.nativeFailed(stream.handle)) {
            if (!stream.closed) UsbAudioDriver.reportFailure(stream, UsbAudioNative.nativeError(stream.handle))
            buffer.position(buffer.limit())
            return true
        }
        val length = buffer.remaining() - buffer.remaining() % inputFrameBytes
        if (length <= 0) {
            buffer.position(buffer.limit())
            return true
        }
        val gain = volume * UsbAudioDriver.softwareGain
        val written = if (buffer.isDirect) {
            UsbAudioNative.nativeWrite(stream.handle, buffer, buffer.position(), length, encoding, channels, gain)
        } else {
            val direct = stagingFor(length)
            direct.clear()
            val slice = buffer.duplicate()
            slice.limit(slice.position() + length)
            direct.put(slice)
            UsbAudioNative.nativeWrite(stream.handle, direct, 0, length, encoding, channels, gain)
        }
        if (written < 0) {
            UsbAudioDriver.reportFailure(stream, UsbAudioNative.nativeError(stream.handle))
            buffer.position(buffer.limit())
            return true
        }
        buffer.position(buffer.position() + written)
        return !buffer.hasRemaining()
    }

    private fun stagingFor(length: Int): ByteBuffer {
        val current = staging
        if (current != null && current.capacity() >= length) return current
        return ByteBuffer.allocateDirect(maxOf(length, 64 * 1024)).order(ByteOrder.nativeOrder()).also { staging = it }
    }

    override fun flush() {
        synchronized(stream) { if (!stream.closed) UsbAudioNative.nativeFlush(stream.handle) }
        reportedAdvance = false
    }

    override fun stop() = Unit

    override fun release() {
        if (released) return
        released = true
        UsbAudioDriver.closeStream(stream)
        listeners.forEach { it.onReleased() }
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
    }

    override fun isOffloadedPlayback(): Boolean = false

    override fun getAudioSessionId(): Int = config.audioSessionId

    override fun getSampleRate(): Int = config.sampleRate

    override fun getBufferSizeInFrames(): Long =
        synchronized(stream) { if (stream.closed) 0L else UsbAudioNative.nativeCapacityFrames(stream.handle) }

    override fun getPositionUs(): Long {
        val (frames, underruns) = synchronized(stream) {
            if (stream.closed) return 0L
            UsbAudioNative.nativeFramesPlayed(stream.handle) to UsbAudioNative.nativeUnderruns(stream.handle)
        }
        if (playing && frames > 0 && !reportedAdvance) {
            reportedAdvance = true
            val now = System.currentTimeMillis()
            listeners.forEach { it.onPositionAdvancing(now) }
        }
        if (underruns != lastUnderruns) {
            lastUnderruns = underruns
            listeners.forEach { it.onUnderrun() }
        }
        return frames * 1_000_000L / stream.deviceRate
    }

    override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT

    override fun isStalled(): Boolean = false

    override fun addListener(listener: AudioOutput.Listener) {
        listeners += listener
    }

    override fun removeListener(listener: AudioOutput.Listener) {
        listeners -= listener
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) = Unit

    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) = Unit

    override fun setOffloadEndOfStream() = Unit

    override fun attachAuxEffect(effectId: Int) = Unit

    override fun setAuxEffectSendLevel(level: Float) = Unit

    override fun setPreferredDevice(preferredDevice: AudioDeviceInfo?) = Unit

    companion object {
        fun create(config: AudioOutputProvider.OutputConfig): UsbDirectAudioOutput? {
            if (config.isOffload || config.isTunneling) return null
            val encoding = when (config.encoding) {
                C.ENCODING_PCM_16BIT -> UsbAudioNative.IN_S16
                C.ENCODING_PCM_24BIT -> UsbAudioNative.IN_S24
                C.ENCODING_PCM_32BIT -> UsbAudioNative.IN_S32
                C.ENCODING_PCM_FLOAT -> UsbAudioNative.IN_FLOAT
                else -> return null
            }
            val channels = Integer.bitCount(config.channelMask)
            if (channels !in 1..8) return null
            val sourceBits = when (encoding) {
                UsbAudioNative.IN_S16 -> 16
                UsbAudioNative.IN_S24, UsbAudioNative.IN_FLOAT -> 24
                else -> 32
            }
            val stream = UsbAudioDriver.openStream(config.sampleRate, channels, sourceBits) ?: return null
            return UsbDirectAudioOutput(config, stream, encoding, channels)
        }

        private fun bytesPerSample(encoding: Int) = when (encoding) {
            UsbAudioNative.IN_S16 -> 2
            UsbAudioNative.IN_S24 -> 3
            else -> 4
        }
    }
}
