package com.olbyine.music.playback

import androidx.media3.common.C
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.ForwardingAudioOutput
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

object AudioEnergy {
    @Volatile var level = 0f
        internal set
    @Volatile var beat = 0f
        internal set
    @Volatile var stamp = 0L
        internal set
    @JvmField val bands = FloatArray(BANDS)

    const val BANDS = 4

    internal fun silence() {
        level = 0f
        beat = 0f
        bands.fill(0f)
        stamp = System.nanoTime()
    }
}

internal class EnergyTapOutput(
    delegate: AudioOutput,
    config: AudioOutputProvider.OutputConfig,
) : ForwardingAudioOutput(delegate) {
    private val sampleRate = config.sampleRate
    private val channels = Integer.bitCount(config.channelMask).coerceAtLeast(1)
    private val encoding = config.encoding
    private val bytesPerSample = when (encoding) {
        C.ENCODING_PCM_16BIT -> 2
        C.ENCODING_PCM_24BIT -> 3
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
        else -> 0
    }
    private val enabled = !config.isOffload && !config.isTunneling && bytesPerSample > 0 && sampleRate > 0
    private val chunkFrames = (sampleRate / 50).coerceAtLeast(64)
    private val lowPassCoefficient = (1.0 - exp(-2.0 * PI * 140.0 / sampleRate.coerceAtLeast(1))).toFloat()
    private var lowPass = 0f
    private val splitCoefficients = floatArrayOf(150f, 600f, 2500f).map { (1.0 - exp(-2.0 * PI * it / sampleRate.coerceAtLeast(1))).toFloat() }.toFloatArray()
    private val splits = FloatArray(3)
    private val bandSums = FloatArray(AudioEnergy.BANDS)
    private val ringBands = Array(AudioEnergy.BANDS) { FloatArray(RING) }
    private val bandPeaks = FloatArray(AudioEnergy.BANDS) { .01f }
    private var chunkSum = 0f
    private var chunkCount = 0
    private var framesWritten = 0L
    private val ringEnd = LongArray(RING)
    private val ringEnergy = FloatArray(RING)
    private var ringNext = 0
    private var ringSize = 0
    private var peak = 0.02f
    private var average = 0f

    override fun write(buffer: ByteBuffer, encodedAccessUnitCount: Int, presentationTimeUs: Long): Boolean {
        val start = buffer.position()
        val result = super.write(buffer, encodedAccessUnitCount, presentationTimeUs)
        if (enabled) {
            val end = buffer.position()
            if (end > start) runCatching { analyze(buffer, start, end) }
        }
        return result
    }

    override fun getPositionUs(): Long {
        val position = super.getPositionUs()
        if (enabled && position >= 0 && ringSize > 0) publish(position * sampleRate / 1_000_000L)
        return position
    }

    override fun flush() {
        super.flush()
        framesWritten = 0L
        ringNext = 0
        ringSize = 0
        lowPass = 0f
        splits.fill(0f)
        bandSums.fill(0f)
        chunkSum = 0f
        chunkCount = 0
        AudioEnergy.silence()
    }

    override fun pause() {
        super.pause()
        AudioEnergy.silence()
    }

    override fun release() {
        super.release()
        AudioEnergy.silence()
    }

    private fun analyze(buffer: ByteBuffer, start: Int, end: Int) {
        val frameBytes = bytesPerSample * channels
        var index = start
        while (index + frameBytes <= end) {
            var sum = 0f
            for (channel in 0 until channels) sum += sample(buffer, index + channel * bytesPerSample)
            val mono = sum / channels
            lowPass += lowPassCoefficient * (mono - lowPass)
            chunkSum += lowPass * lowPass
            for (s in 0 until 3) splits[s] += splitCoefficients[s] * (mono - splits[s])
            val b0 = splits[0]
            val b1 = splits[1] - splits[0]
            val b2 = splits[2] - splits[1]
            val b3 = mono - splits[2]
            bandSums[0] += b0 * b0
            bandSums[1] += b1 * b1
            bandSums[2] += b2 * b2
            bandSums[3] += b3 * b3
            chunkCount++
            framesWritten++
            if (chunkCount >= chunkFrames) {
                ringEnd[ringNext] = framesWritten
                ringEnergy[ringNext] = sqrt(chunkSum / chunkCount)
                for (b in 0 until AudioEnergy.BANDS) {
                    ringBands[b][ringNext] = sqrt(bandSums[b] / chunkCount)
                    bandSums[b] = 0f
                }
                ringNext = (ringNext + 1) % RING
                ringSize = (ringSize + 1).coerceAtMost(RING)
                chunkSum = 0f
                chunkCount = 0
            }
            index += frameBytes
        }
    }

    private fun sample(buffer: ByteBuffer, at: Int): Float = when (encoding) {
        C.ENCODING_PCM_16BIT -> buffer.getShort(at) / 32768f
        C.ENCODING_PCM_24BIT -> {
            val b0 = buffer.get(at).toInt() and 0xFF
            val b1 = buffer.get(at + 1).toInt() and 0xFF
            val b2 = buffer.get(at + 2).toInt()
            ((b2 shl 16) or (b1 shl 8) or b0) / 8388608f
        }
        C.ENCODING_PCM_32BIT -> buffer.getInt(at) / 2147483648f
        C.ENCODING_PCM_FLOAT -> buffer.getFloat(at)
        else -> 0f
    }

    private fun publish(frame: Long) {
        var energy = -1f
        var heard = -1
        for (step in 1..ringSize) {
            val slot = (ringNext - step + RING) % RING
            if (ringEnd[slot] <= frame + chunkFrames) {
                energy = ringEnergy[slot]
                heard = slot
                if (ringEnd[slot] <= frame) break
            }
        }
        if (energy < 0f) return
        for (b in 0 until AudioEnergy.BANDS) {
            val value = ringBands[b][heard]
            bandPeaks[b] = max(value, bandPeaks[b] * 0.9993f).coerceAtLeast(0.004f)
            AudioEnergy.bands[b] = (value / bandPeaks[b]).coerceIn(0f, 1f)
        }
        peak = max(energy, peak * 0.9995f).coerceAtLeast(0.01f)
        val level = (energy / peak).coerceIn(0f, 1f)
        average += (level - average) * 0.04f
        AudioEnergy.level = level
        AudioEnergy.beat = ((level - average) * 2.6f).coerceIn(0f, 1f)
        AudioEnergy.stamp = System.nanoTime()
    }

    private companion object {
        const val RING = 512
    }
}
