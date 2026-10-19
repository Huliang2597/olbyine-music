package com.olbyine.music.data

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset

internal object EmbeddedLyrics {
    private const val MAX_TAG = 16 * 1024 * 1024

    fun read(name: String, open: () -> InputStream?, channel: (() -> FileChannel?)? = null): String? = runCatching {
        val ext = name.substringAfterLast('.', "").lowercase()
        val head = open()?.use { it.readNBytesCompat(10) } ?: return null
        when {
            head.size >= 3 && head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte() ->
                open()?.use { id3(it) }
            head.size >= 4 && String(head, 0, 4, Charsets.ISO_8859_1) == "fLaC" -> open()?.use { flac(it) }
            ext in setOf("m4a", "mp4", "m4b", "alac", "aac") || (head.size >= 8 && String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp") ->
                channel?.invoke()?.use { mp4(it) }
            head.size >= 4 && String(head, 0, 4, Charsets.ISO_8859_1) == "OggS" -> open()?.use { vorbisScan(it) }
            head.size >= 4 && String(head, 0, 4, Charsets.ISO_8859_1) == "FORM" -> open()?.use { aiffId3(it) }
            else -> null
        }?.trim()?.takeIf { it.length >= 2 }
    }.getOrNull()

    private fun id3(input: InputStream): String? {
        val header = input.readNBytesCompat(10)
        if (header.size < 10) return null
        val version = header[3].toInt()
        val size = synchsafe(header, 6)
        if (size <= 0 || size > MAX_TAG) return null
        val tag = input.readNBytesCompat(size)
        return id3Frames(tag, version)
    }

    private fun id3Frames(tag: ByteArray, version: Int): String? {
        var pos = 0
        var unsynced: String? = null
        var synced: String? = null
        val idLength = if (version == 2) 3 else 4
        while (pos + idLength * 2 < tag.size) {
            val id = String(tag, pos, idLength, Charsets.ISO_8859_1)
            if (id[0] == '\u0000') break
            val frameSize = when (version) {
                2 -> ((tag[pos + 3].toInt() and 0xFF) shl 16) or ((tag[pos + 4].toInt() and 0xFF) shl 8) or (tag[pos + 5].toInt() and 0xFF)
                4 -> synchsafe(tag, pos + 4)
                else -> ByteBuffer.wrap(tag, pos + 4, 4).int
            }
            val headerSize = if (version == 2) 6 else 10
            val start = pos + headerSize
            if (frameSize <= 0 || start + frameSize > tag.size) break
            val body = tag.copyOfRange(start, start + frameSize)
            when (id) {
                "USLT", "ULT" -> if (unsynced == null) unsynced = uslt(body)
                "SYLT", "SLT" -> if (synced == null) synced = sylt(body)
                "TXXX", "TXX" -> txxx(body)?.let { (key, value) -> if (key.contains("lyric", true) && unsynced == null) unsynced = value }
            }
            pos = start + frameSize
        }
        return synced?.takeIf { it.isNotBlank() } ?: unsynced
    }

    private fun charset(encoding: Int): Charset = when (encoding) {
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> Charsets.ISO_8859_1
    }

    private fun terminator(encoding: Int) = if (encoding == 1 || encoding == 2) 2 else 1

    private fun stringEnd(data: ByteArray, from: Int, encoding: Int): Int {
        val step = terminator(encoding)
        var i = from
        while (i + step - 1 < data.size) {
            if (step == 1 && data[i] == 0.toByte()) return i
            if (step == 2 && data[i] == 0.toByte() && data[i + 1] == 0.toByte()) return i
            i += step
        }
        return data.size
    }

    private fun uslt(body: ByteArray): String? {
        if (body.size < 5) return null
        val encoding = body[0].toInt()
        val descriptorEnd = stringEnd(body, 4, encoding)
        val textStart = (descriptorEnd + terminator(encoding)).coerceAtMost(body.size)
        return decode(body, textStart, body.size, encoding)
    }

    private fun sylt(body: ByteArray): String? {
        if (body.size < 7) return null
        val encoding = body[0].toInt()
        val format = body[4].toInt()
        if (format != 2) return null
        var pos = (stringEnd(body, 6, encoding) + terminator(encoding)).coerceAtMost(body.size)
        val lines = StringBuilder()
        while (pos < body.size) {
            val end = stringEnd(body, pos, encoding)
            val text = decode(body, pos, end, encoding).orEmpty().trim('\n', '\r')
            pos = end + terminator(encoding)
            if (pos + 4 > body.size) break
            val time = ByteBuffer.wrap(body, pos, 4).int.toLong()
            pos += 4
            if (text.isNotBlank()) lines.append(lrcTime(time)).append(text).append('\n')
        }
        return lines.toString().takeIf { it.isNotBlank() }
    }

    private fun txxx(body: ByteArray): Pair<String, String>? {
        if (body.size < 2) return null
        val encoding = body[0].toInt()
        val keyEnd = stringEnd(body, 1, encoding)
        val key = decode(body, 1, keyEnd, encoding).orEmpty()
        val value = decode(body, (keyEnd + terminator(encoding)).coerceAtMost(body.size), body.size, encoding).orEmpty()
        return key to value
    }

    private fun decode(data: ByteArray, from: Int, to: Int, encoding: Int): String? {
        if (from >= to) return null
        val text = String(data, from, to - from, charset(encoding)).trimEnd('\u0000')
        return text.removePrefix("﻿")
    }

    private fun synchsafe(data: ByteArray, at: Int): Int =
        ((data[at].toInt() and 0x7F) shl 21) or ((data[at + 1].toInt() and 0x7F) shl 14) or
            ((data[at + 2].toInt() and 0x7F) shl 7) or (data[at + 3].toInt() and 0x7F)

    private fun lrcTime(ms: Long): String {
        val minutes = ms / 60_000
        val seconds = (ms % 60_000) / 1000
        val hundredths = (ms % 1000) / 10
        return "[%02d:%02d.%02d]".format(minutes, seconds, hundredths)
    }

    private fun aiffId3(input: InputStream): String? {
        val header = input.readNBytesCompat(12)
        if (header.size < 12) return null
        while (true) {
            val chunk = input.readNBytesCompat(8)
            if (chunk.size < 8) return null
            val id = String(chunk, 0, 4, Charsets.ISO_8859_1)
            val size = ByteBuffer.wrap(chunk, 4, 4).int
            if (size < 0) return null
            if (id == "ID3 " || id == "id3 ") return id3(input.readNBytesCompat(size).inputStream())
            input.skipFully(size.toLong() + (size and 1))
        }
    }

    private fun flac(input: InputStream): String? {
        input.skipFully(4)
        while (true) {
            val header = input.readNBytesCompat(4)
            if (header.size < 4) return null
            val last = header[0].toInt() and 0x80 != 0
            val type = header[0].toInt() and 0x7F
            val length = ((header[1].toInt() and 0xFF) shl 16) or ((header[2].toInt() and 0xFF) shl 8) or (header[3].toInt() and 0xFF)
            if (type == 4) return vorbisComments(input.readNBytesCompat(length))
            input.skipFully(length.toLong())
            if (last) return null
        }
    }

    private fun vorbisComments(block: ByteArray): String? {
        val buffer = ByteBuffer.wrap(block).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        if (buffer.remaining() < 8) return null
        val vendor = buffer.int
        if (vendor < 0 || vendor > buffer.remaining()) return null
        buffer.position(buffer.position() + vendor)
        val count = buffer.int
        val found = HashMap<String, String>()
        repeat(count.coerceAtMost(10_000)) {
            if (buffer.remaining() < 4) return@repeat
            val length = buffer.int
            if (length < 0 || length > buffer.remaining()) return@repeat
            val text = String(block, buffer.position(), length, Charsets.UTF_8)
            buffer.position(buffer.position() + length)
            val key = text.substringBefore('=').uppercase()
            if (key in LYRIC_KEYS) found[key] = text.substringAfter('=')
        }
        return found["SYNCEDLYRICS"] ?: found["LYRICS"] ?: found["UNSYNCEDLYRICS"] ?: found["UNSYNCED LYRICS"]
    }

    private fun vorbisScan(input: InputStream): String? {
        val data = input.readNBytesCompat(2 * 1024 * 1024)
        val text = String(data, Charsets.ISO_8859_1)
        for (key in LYRIC_KEYS) {
            var index = text.indexOf("$key=", ignoreCase = true)
            while (index >= 4) {
                val length = ByteBuffer.wrap(data, index - 4, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
                if (length > key.length && index + length <= data.size) {
                    return String(data, index + key.length + 1, length - key.length - 1, Charsets.UTF_8)
                }
                index = text.indexOf("$key=", index + 1, ignoreCase = true)
            }
        }
        return null
    }

    private val LYRIC_KEYS = listOf("SYNCEDLYRICS", "LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS")

    private fun mp4(channel: FileChannel): String? {
        val path = listOf("moov", "udta", "meta", "ilst", "©lyr", "data")
        return mp4Find(channel, 0, channel.size(), path, 0)
    }

    private fun mp4Find(channel: FileChannel, start: Long, end: Long, path: List<String>, depth: Int): String? {
        var pos = start
        val header = ByteBuffer.allocate(16)
        while (pos + 8 <= end) {
            header.clear().limit(8)
            if (channel.read(header, pos) < 8) return null
            header.flip()
            var size = header.int.toLong() and 0xFFFFFFFFL
            val type = String(ByteArray(4).also { header.get(it) }, Charsets.ISO_8859_1)
            var headerSize = 8L
            if (size == 1L) {
                header.clear().limit(8)
                channel.read(header, pos + 8)
                header.flip()
                size = header.long
                headerSize = 16
            } else if (size == 0L) size = end - pos
            if (size < headerSize) return null
            if (type == path[depth]) {
                var childStart = pos + headerSize
                if (type == "meta") childStart += 4
                if (depth == path.lastIndex) {
                    val length = (size - headerSize - 8).toInt()
                    if (length <= 0 || length > MAX_TAG) return null
                    val text = ByteBuffer.allocate(length)
                    channel.read(text, childStart + 8)
                    return String(text.array(), Charsets.UTF_8)
                }
                return mp4Find(channel, childStart, pos + size, path, depth + 1)
            }
            pos += size
        }
        return null
    }

    private fun InputStream.readNBytesCompat(count: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(minOf(count, 64 * 1024))
        val buffer = ByteArray(64 * 1024)
        var remaining = count
        while (remaining > 0) {
            val read = read(buffer, 0, minOf(buffer.size, remaining))
            if (read < 0) break
            out.write(buffer, 0, read)
            remaining -= read
        }
        return out.toByteArray()
    }

    private fun InputStream.skipFully(count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped <= 0) {
                if (read() < 0) return
                remaining--
            } else remaining -= skipped
        }
    }
}
