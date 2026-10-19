package com.olbyine.music.playback

import androidx.media3.common.C
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.ForwardingAudioOutput
import com.olbyine.music.data.EqBands
import com.olbyine.music.data.UserSettings
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

object AudioEffects {
    class Snapshot(
        val eqEnabled: Boolean,
        val bands: FloatArray,
        val preampDb: Float,
        val balance: Float,
        val mono: Boolean,
    ) {
        val eqActive: Boolean = eqEnabled && (bands.any { it != 0f } || preampDb != 0f)

        val active: Boolean = eqActive || balance != 0f || mono
    }

    @Volatile
    var current: Snapshot = Snapshot(false, FloatArray(EqBands.size), 0f, 0f, false)
        private set

    fun update(settings: UserSettings) {
        val next = Snapshot(
            eqEnabled = settings.eqEnabled,
            bands = settings.eqBands.toFloatArray().copyOf(EqBands.size),
            preampDb = settings.eqPreamp,
            balance = settings.balance,
            mono = settings.monoAudio,
        )
        val previous = current
        if (previous.eqEnabled == next.eqEnabled && previous.bands.contentEquals(next.bands) &&
            previous.preampDb == next.preampDb && previous.balance == next.balance && previous.mono == next.mono
        ) return
        current = next
    }
}

internal class EffectsOutput(
    delegate: AudioOutput,
    config: AudioOutputProvider.OutputConfig,
) : ForwardingAudioOutput(delegate) {
    private val sampleRate = config.sampleRate
    private val channels = Integer.bitCount(config.channelMask).coerceIn(1, 8)
    private val encoding = config.encoding
    private val bytesPerSample = when (encoding) {
        C.ENCODING_PCM_16BIT -> 2
        C.ENCODING_PCM_24BIT -> 3
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
        else -> 0
    }
    private val supported = !config.isOffload && !config.isTunneling && bytesPerSample > 0 && sampleRate > 0
    private var pending: ByteBuffer? = null
    private var applied: AudioEffects.Snapshot? = null
    private var filters: Array<Biquad> = emptyArray()
    private var gain = 1.0
    private val random = java.util.Random()

    override fun write(buffer: ByteBuffer, encodedAccessUnitCount: Int, presentationTimeUs: Long): Boolean {
        val effects = AudioEffects.current
        if (supported && effects.active && buffer !== pending) {
            prepare(effects)
            runCatching { process(buffer, buffer.position(), buffer.limit(), effects) }
        }
        val result = super.write(buffer, encodedAccessUnitCount, presentationTimeUs)
        pending = if (buffer.hasRemaining()) buffer else null
        return result
    }

    override fun flush() {
        super.flush()
        pending = null
        filters.forEach { it.reset() }
    }

    private fun prepare(effects: AudioEffects.Snapshot) {
        if (effects === applied) return
        val bands = EqBands.frequencies
        val wanted = if (effects.eqActive) bands.indices.filter { effects.bands[it] != 0f && bands[it] < sampleRate * .45f } else emptyList()
        val fresh = wanted.map { index -> Biquad.peaking(sampleRate.toDouble(), bands[index].toDouble(), 1.41, effects.bands[index].toDouble(), channels) }
        filters = fresh.mapIndexed { i, filter ->
            filters.getOrNull(i)?.takeIf { it.channels == channels }?.also { it.adopt(filter) } ?: filter
        }.toTypedArray()
        val boost = if (effects.eqActive) (wanted.maxOfOrNull { effects.bands[it] } ?: 0f).coerceAtLeast(0f) else 0f
        val preamp = if (effects.eqActive) effects.preampDb - boost * .6f else 0f
        gain = 10.0.pow(preamp / 20.0)
        applied = effects
    }

    private fun process(buffer: ByteBuffer, start: Int, end: Int, effects: AudioEffects.Snapshot) {
        val frameBytes = bytesPerSample * channels
        val frame = DoubleArray(channels)
        val leftGain = if (effects.balance > 0f) 1.0 - effects.balance else 1.0
        val rightGain = if (effects.balance < 0f) 1.0 + effects.balance else 1.0
        val stereo = channels == 2
        var index = start
        while (index + frameBytes <= end) {
            for (channel in 0 until channels) frame[channel] = read(buffer, index + channel * bytesPerSample)
            if (effects.mono && channels >= 2) {
                val mixed = frame.average()
                for (channel in 0 until channels) frame[channel] = mixed
            }
            for (channel in 0 until channels) {
                var value = frame[channel] * gain
                for (filter in filters) value = filter.process(channel, value)
                if (stereo) value *= if (channel == 0) leftGain else rightGain
                frame[channel] = value
            }
            for (channel in 0 until channels) write(buffer, index + channel * bytesPerSample, frame[channel])
            index += frameBytes
        }
    }

    private fun read(buffer: ByteBuffer, at: Int): Double = when (encoding) {
        C.ENCODING_PCM_16BIT -> buffer.getShort(at) / 32768.0
        C.ENCODING_PCM_24BIT -> {
            val b0 = buffer.get(at).toInt() and 0xFF
            val b1 = buffer.get(at + 1).toInt() and 0xFF
            val b2 = buffer.get(at + 2).toInt()
            ((b2 shl 16) or (b1 shl 8) or b0) / 8388608.0
        }
        C.ENCODING_PCM_32BIT -> buffer.getInt(at) / 2147483648.0
        else -> buffer.getFloat(at).toDouble()
    }

    private fun write(buffer: ByteBuffer, at: Int, value: Double) {
        when (encoding) {
            C.ENCODING_PCM_16BIT -> {
                val dither = (random.nextDouble() - random.nextDouble()) / 32768.0
                buffer.putShort(at, ((value + dither) * 32768.0).roundToInt().coerceIn(-32768, 32767).toShort())
            }
            C.ENCODING_PCM_24BIT -> {
                val sample = (value * 8388608.0).roundToInt().coerceIn(-8388608, 8388607)
                buffer.put(at, sample.toByte())
                buffer.put(at + 1, (sample shr 8).toByte())
                buffer.put(at + 2, (sample shr 16).toByte())
            }
            C.ENCODING_PCM_32BIT -> buffer.putInt(at, (value * 2147483648.0).roundToLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt())
            else -> buffer.putFloat(at, value.coerceIn(-1.0, 1.0).toFloat())
        }
    }
}

internal class Biquad private constructor(
    var b0: Double, var b1: Double, var b2: Double, var a1: Double, var a2: Double,
    val channels: Int,
) {
    private val z1 = DoubleArray(channels)
    private val z2 = DoubleArray(channels)

    fun process(channel: Int, input: Double): Double {
        val output = b0 * input + z1[channel]
        z1[channel] = b1 * input - a1 * output + z2[channel]
        z2[channel] = b2 * input - a2 * output
        return output
    }

    fun adopt(other: Biquad) {
        b0 = other.b0; b1 = other.b1; b2 = other.b2; a1 = other.a1; a2 = other.a2
    }

    fun reset() {
        z1.fill(0.0)
        z2.fill(0.0)
    }

    companion object {
        fun peaking(sampleRate: Double, frequency: Double, q: Double, gainDb: Double, channels: Int): Biquad {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * frequency / sampleRate
            val alpha = sin(w0) / (2.0 * q)
            val cosW = cos(w0)
            val a0 = 1.0 + alpha / a
            return Biquad(
                b0 = (1.0 + alpha * a) / a0,
                b1 = (-2.0 * cosW) / a0,
                b2 = (1.0 - alpha * a) / a0,
                a1 = (-2.0 * cosW) / a0,
                a2 = (1.0 - alpha / a) / a0,
                channels = channels,
            )
        }
    }
}
