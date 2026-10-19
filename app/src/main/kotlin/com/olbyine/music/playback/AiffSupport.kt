package com.olbyine.music.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import java.io.InputStream
import java.nio.ByteBuffer

internal data class AiffFormat(
    val channels: Int,
    val frames: Long,
    val bits: Int,
    val sampleRate: Int,
    val compression: String,
) {
    val durationMs: Long get() = if (sampleRate > 0) frames * 1000 / sampleRate else 0
    val bytesPerFrame: Int get() = channels * ((bits + 7) / 8)

    val pcmEncoding: Int
        @OptIn(UnstableApi::class)
        get() = when {
            compression == "sowt" -> when (bits) { 16 -> C.ENCODING_PCM_16BIT; 24 -> C.ENCODING_PCM_24BIT; 32 -> C.ENCODING_PCM_32BIT; else -> C.ENCODING_INVALID }
            compression == "NONE" || compression == "twos" -> when (bits) {
                16 -> C.ENCODING_PCM_16BIT_BIG_ENDIAN
                24 -> C.ENCODING_PCM_24BIT_BIG_ENDIAN
                32 -> C.ENCODING_PCM_32BIT_BIG_ENDIAN
                else -> C.ENCODING_INVALID
            }
            else -> C.ENCODING_INVALID
        }

    companion object {
        fun parse(body: ByteArray, aifc: Boolean): AiffFormat {
            val buffer = ByteBuffer.wrap(body)
            val channels = buffer.short.toInt()
            val frames = buffer.int.toLong() and 0xFFFFFFFFL
            val bits = buffer.short.toInt()
            val rate = extended(body, 8)
            val compression = if (aifc && body.size >= 22) String(body, 18, 4, Charsets.ISO_8859_1) else "NONE"
            return AiffFormat(channels, frames, bits, rate.toInt(), compression)
        }

        private fun extended(data: ByteArray, at: Int): Double {
            val exponent = ((data[at].toInt() and 0x7F) shl 8) or (data[at + 1].toInt() and 0xFF)
            var mantissa = 0L
            for (i in 0 until 8) mantissa = (mantissa shl 8) or (data[at + 2 + i].toLong() and 0xFF)
            if (exponent == 0 && mantissa == 0L) return 0.0
            val value = (mantissa ushr 1).toDouble() * Math.pow(2.0, (exponent - 16383 - 62).toDouble())
            return if (data[at].toInt() and 0x80 != 0) -value else value
        }
    }
}

internal data class AiffTags(
    val format: AiffFormat?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val year: Int,
    val track: Int,
    val picture: ByteArray?,
) {
    companion object {
        fun read(input: InputStream): AiffTags? {
            val head = input.readExactly(12) ?: return null
            if (String(head, 0, 4, Charsets.ISO_8859_1) != "FORM") return null
            val type = String(head, 8, 4, Charsets.ISO_8859_1)
            if (type != "AIFF" && type != "AIFC") return null
            var format: AiffFormat? = null
            var id3: Map<String, Any>? = null
            while (true) {
                val chunk = input.readExactly(8) ?: break
                val id = String(chunk, 0, 4, Charsets.ISO_8859_1)
                val size = ByteBuffer.wrap(chunk, 4, 4).int.toLong() and 0xFFFFFFFFL
                when (id) {
                    "COMM" -> format = AiffFormat.parse(input.readExactly(size.toInt()) ?: break, type == "AIFC")
                    "ID3 ", "id3 " -> id3 = Id3.frames(input.readExactly(size.toInt().coerceAtMost(32 * 1024 * 1024)) ?: break)
                    else -> input.skipExactly(size)
                }
                if (size and 1L == 1L) input.skipExactly(1)
            }
            if (format == null && id3 == null) return null
            return AiffTags(
                format = format,
                title = id3?.get("TIT2") as? String,
                artist = id3?.get("TPE1") as? String,
                album = id3?.get("TALB") as? String,
                year = ((id3?.get("TDRC") ?: id3?.get("TYER")) as? String)?.take(4)?.toIntOrNull() ?: 0,
                track = (id3?.get("TRCK") as? String)?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0,
                picture = id3?.get("APIC") as? ByteArray,
            )
        }
    }
}

internal object Id3 {
    fun frames(tag: ByteArray): Map<String, Any> {
        if (tag.size < 10 || tag[0] != 'I'.code.toByte()) return emptyMap()
        val version = tag[3].toInt()
        val result = HashMap<String, Any>()
        var pos = 10
        while (pos + 10 < tag.size) {
            val id = String(tag, pos, 4, Charsets.ISO_8859_1)
            if (id[0] == '\u0000') break
            val size = if (version == 4) {
                ((tag[pos + 4].toInt() and 0x7F) shl 21) or ((tag[pos + 5].toInt() and 0x7F) shl 14) or
                    ((tag[pos + 6].toInt() and 0x7F) shl 7) or (tag[pos + 7].toInt() and 0x7F)
            } else ByteBuffer.wrap(tag, pos + 4, 4).int
            val start = pos + 10
            if (size <= 0 || start + size > tag.size) break
            val body = tag.copyOfRange(start, start + size)
            if (id.startsWith("T") && id != "TXXX" && body.isNotEmpty()) {
                result[id] = text(body[0].toInt(), body, 1, body.size)
            } else if (id == "APIC" && body.size > 4 && "APIC" !in result) {
                val encoding = body[0].toInt()
                var p = 1
                while (p < body.size && body[p] != 0.toByte()) p++
                p += 2
                val step = if (encoding == 1 || encoding == 2) 2 else 1
                while (p + step - 1 < body.size && !(body[p] == 0.toByte() && (step == 1 || body[p + 1] == 0.toByte()))) p += step
                p += step
                if (p < body.size) result["APIC"] = body.copyOfRange(p, body.size)
            }
            pos = start + size
        }
        return result
    }

    private fun text(encoding: Int, data: ByteArray, from: Int, to: Int): String {
        val charset = when (encoding) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        return String(data, from, to - from, charset).trim('\u0000', ' ', '﻿')
    }
}

private fun InputStream.readExactly(count: Int): ByteArray? {
    if (count < 0) return null
    val out = ByteArray(count)
    var read = 0
    while (read < count) {
        val n = read(out, read, count - read)
        if (n < 0) return null
        read += n
    }
    return out
}

private fun InputStream.skipExactly(count: Long) {
    var remaining = count
    while (remaining > 0) {
        val skipped = skip(remaining)
        if (skipped <= 0) {
            if (read() < 0) return
            remaining--
        } else remaining -= skipped
    }
}

@OptIn(UnstableApi::class)
class AiffExtractor : Extractor {
    private lateinit var output: ExtractorOutput
    private var track: TrackOutput? = null
    private var format: AiffFormat? = null
    private var aifc = false
    private var dataStart = -1L
    private var dataEnd = -1L
    private var framesWritten = 0L
    private var pendingBytes = 0
    private var parsedHeader = false

    override fun sniff(input: ExtractorInput): Boolean {
        val head = ByteArray(12)
        input.peekFully(head, 0, 12)
        val type = String(head, 8, 4, Charsets.ISO_8859_1)
        return String(head, 0, 4, Charsets.ISO_8859_1) == "FORM" && (type == "AIFF" || type == "AIFC")
    }

    override fun init(output: ExtractorOutput) {
        this.output = output
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        if (!parsedHeader) {
            parseHeader(input)
            parsedHeader = true
            val pcm = format ?: throw androidx.media3.common.ParserException.createForMalformedContainer("AIFF 缺少 COMM", null)
            if (pcm.pcmEncoding == C.ENCODING_INVALID) {
                throw androidx.media3.common.ParserException.createForUnsupportedContainerFeature("不支持的 AIFF 编码：${pcm.compression} ${pcm.bits} 位")
            }
            val trackOutput = output.track(0, C.TRACK_TYPE_AUDIO)
            trackOutput.format(
                Format.Builder()
                    .setSampleMimeType(MimeTypes.AUDIO_RAW)
                    .setChannelCount(pcm.channels)
                    .setSampleRate(pcm.sampleRate)
                    .setPcmEncoding(pcm.pcmEncoding)
                    .setAverageBitrate(pcm.sampleRate * pcm.bytesPerFrame * 8)
                    .build(),
            )
            track = trackOutput
            output.seekMap(AiffSeekMap(pcm, dataStart, dataEnd))
            output.endTracks()
            seekPosition.position = dataStart
            return Extractor.RESULT_SEEK
        }
        val pcm = format ?: return Extractor.RESULT_END_OF_INPUT
        val trackOutput = track ?: return Extractor.RESULT_END_OF_INPUT
        val position = input.position
        if (position >= dataEnd) return flushAndEnd(trackOutput, pcm)
        val frameBytes = pcm.bytesPerFrame
        val target = (pcm.sampleRate / 10).coerceAtLeast(1) * frameBytes
        val wanted = minOf((target - pendingBytes).toLong(), dataEnd - position).toInt()
        val read = trackOutput.sampleData(input, wanted, true)
        if (read == C.RESULT_END_OF_INPUT) return flushAndEnd(trackOutput, pcm)
        pendingBytes += read
        val wholeBytes = pendingBytes / frameBytes * frameBytes
        if (pendingBytes >= target || input.position >= dataEnd) {
            if (wholeBytes > 0) {
                val timeUs = framesWritten * C.MICROS_PER_SECOND / pcm.sampleRate
                trackOutput.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, wholeBytes, pendingBytes - wholeBytes, null)
                framesWritten += wholeBytes / frameBytes
                pendingBytes -= wholeBytes
            }
        }
        return Extractor.RESULT_CONTINUE
    }

    private fun flushAndEnd(trackOutput: TrackOutput, pcm: AiffFormat): Int {
        val wholeBytes = pendingBytes / pcm.bytesPerFrame * pcm.bytesPerFrame
        if (wholeBytes > 0) {
            val timeUs = framesWritten * C.MICROS_PER_SECOND / pcm.sampleRate
            trackOutput.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, wholeBytes, pendingBytes - wholeBytes, null)
            framesWritten += wholeBytes / pcm.bytesPerFrame
        }
        pendingBytes = 0
        return Extractor.RESULT_END_OF_INPUT
    }

    private fun parseHeader(input: ExtractorInput) {
        val head = ByteArray(12)
        input.readFully(head, 0, 12)
        aifc = String(head, 8, 4, Charsets.ISO_8859_1) == "AIFC"
        val chunk = ByteArray(8)
        while (dataStart < 0) {
            if (!input.readFully(chunk, 0, 8, true)) break
            val id = String(chunk, 0, 4, Charsets.ISO_8859_1)
            val size = ByteBuffer.wrap(chunk, 4, 4).int.toLong() and 0xFFFFFFFFL
            when (id) {
                "COMM" -> {
                    val body = ByteArray(size.toInt())
                    input.readFully(body, 0, body.size)
                    format = AiffFormat.parse(body, aifc)
                    if (size and 1L == 1L) input.skipFully(1)
                }
                "SSND" -> {
                    val header = ByteArray(8)
                    input.readFully(header, 0, 8)
                    val offset = ByteBuffer.wrap(header, 0, 4).int.toLong() and 0xFFFFFFFFL
                    input.skipFully(offset.toInt())
                    dataStart = input.position
                    dataEnd = dataStart + size - 8 - offset
                    val pcm = format
                    if (pcm != null && pcm.frames > 0) dataEnd = minOf(dataEnd, dataStart + pcm.frames * pcm.bytesPerFrame)
                }
                else -> input.skipFully((size + (size and 1L)).toInt())
            }
        }
        if (dataStart < 0) throw androidx.media3.common.ParserException.createForMalformedContainer("AIFF 缺少 SSND", null)
    }

    override fun seek(position: Long, timeUs: Long) {
        pendingBytes = 0
        val pcm = format
        framesWritten = if (pcm != null && pcm.bytesPerFrame > 0 && position >= dataStart) (position - dataStart) / pcm.bytesPerFrame else 0
        if (position == 0L) {
            parsedHeader = false
            dataStart = -1
            track = null
        }
    }

    override fun release() = Unit
}

@OptIn(UnstableApi::class)
private class AiffSeekMap(private val format: AiffFormat, private val dataStart: Long, private val dataEnd: Long) : SeekMap {
    override fun isSeekable() = true

    override fun getDurationUs(): Long = format.frames.takeIf { it > 0 }
        ?.let { it * C.MICROS_PER_SECOND / format.sampleRate.coerceAtLeast(1) }
        ?: ((dataEnd - dataStart) / format.bytesPerFrame.coerceAtLeast(1)) * C.MICROS_PER_SECOND / format.sampleRate.coerceAtLeast(1)

    override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
        val totalFrames = (dataEnd - dataStart) / format.bytesPerFrame.coerceAtLeast(1)
        val frame = (timeUs * format.sampleRate / C.MICROS_PER_SECOND).coerceIn(0, (totalFrames - 1).coerceAtLeast(0))
        val position = dataStart + frame * format.bytesPerFrame
        return SeekMap.SeekPoints(SeekPoint(frame * C.MICROS_PER_SECOND / format.sampleRate.coerceAtLeast(1), position))
    }
}
