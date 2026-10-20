package com.olbyine.music.data

import java.io.InputStream

internal object ComposerTags {
    private const val MAX_TAG = 16 * 1024 * 1024
    private val SEPARATORS = listOf("/", "、", ";", "；", "&", ",", "，", "|", "／", "feat.", "featuring")

    fun read(open: () -> InputStream?): List<String> {
        val values = runCatching {
            open()?.use { input ->
                val head = input.readFully(10)
                when {
                    head.size >= 3 && head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte() ->
                        id3(input, head)
                    head.size >= 4 && String(head, 0, 4, Charsets.ISO_8859_1) == "fLaC" ->
                        flac(java.io.SequenceInputStream(java.io.ByteArrayInputStream(head), input))
                    else -> emptyList()
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
        return split(values.joinToString(" / "))
    }

    fun split(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        var text = raw.trim()
        text = text.replace(Regex("(?i)^作曲\\s*[:：]\\s*"), "")
        var parts = listOf(text)
        SEPARATORS.forEach { separator ->
            parts = parts.flatMap { part -> part.split(separator) }
        }
        return parts
            .map { it.trim().trim('(', ')', '[', ']', '（', '）', '【', '】', '"', '\'') }
            .filter { it.isNotEmpty() && it != "作曲" && it != "未知作曲人" && !it.equals("null", true) }
            .distinctBy { normalize(it) }
    }

    fun normalize(name: String): String = name.filterNot { it.isWhitespace() }.lowercase()

    private fun id3(input: InputStream, header: ByteArray): List<String> {
        val version = header[3].toInt() and 0xFF
        if (version !in 2..4) return emptyList()
        val size = synchsafe(header, 6)
        if (size <= 0 || size > MAX_TAG) return emptyList()
        val tag = input.readFully(size)
        val idLength = if (version == 2) 3 else 4
        val headerSize = if (version == 2) 6 else 10
        val composerId = if (version == 2) "TCM" else "TCOM"
        val texts = ArrayList<String>()
        var pos = 0
        while (pos + headerSize <= tag.size) {
            val id = String(tag, pos, idLength, Charsets.ISO_8859_1)
            if (id[0] == '\u0000') break
            val frameSize = when (version) {
                2 -> ((tag[pos + 3].toInt() and 0xFF) shl 16) or ((tag[pos + 4].toInt() and 0xFF) shl 8) or (tag[pos + 5].toInt() and 0xFF)
                4 -> synchsafe(tag, pos + 4)
                else -> ((tag[pos + 4].toInt() and 0xFF) shl 24) or ((tag[pos + 5].toInt() and 0xFF) shl 16) or
                    ((tag[pos + 6].toInt() and 0xFF) shl 8) or (tag[pos + 7].toInt() and 0xFF)
            }
            val start = pos + headerSize
            if (frameSize <= 0 || start + frameSize > tag.size) break
            if (id == composerId) {
                val body = tag.copyOfRange(start, start + frameSize)
                if (body.size > 1) decodeText(body)?.let(texts::add)
            }
            pos = start + frameSize
        }
        return texts
    }

    private fun decodeText(body: ByteArray): String? {
        val encoding = body[0].toInt() and 0xFF
        val payload = body.copyOfRange(1, body.size)
        val decoded = TagCharset.decode(listOf(payload)).firstOrNull()
            ?: when (encoding) {
                0 -> String(payload, Charsets.ISO_8859_1)
                1, 2 -> String(payload, Charsets.UTF_16)
                else -> String(payload, Charsets.UTF_8)
            }
        return decoded.split('\u0000').map(String::trim).filter(String::isNotEmpty).joinToString(" / ").ifBlank { null }
    }

    private fun flac(input: InputStream): List<String> {
        val first = input.readFully(4)
        if (first.size < 4 || String(first, Charsets.ISO_8859_1) != "fLaC") return emptyList()
        while (true) {
            val head = input.readFully(4)
            if (head.size < 4) return emptyList()
            val type = head[0].toInt() and 0x7F
            val last = head[0].toInt() and 0x80 != 0
            val length = ((head[1].toInt() and 0xFF) shl 16) or ((head[2].toInt() and 0xFF) shl 8) or (head[3].toInt() and 0xFF)
            if (length < 0 || length > MAX_TAG) return emptyList()
            if (type == 4) return vorbis(input.readFully(length))
            skipFully(input, length.toLong())
            if (last) return emptyList()
        }
    }

    private fun vorbis(block: ByteArray): List<String> {
        var pos = 0
        fun readInt(): Int {
            if (pos + 4 > block.size) return -1
            val value = (block[pos].toInt() and 0xFF) or ((block[pos + 1].toInt() and 0xFF) shl 8) or
                ((block[pos + 2].toInt() and 0xFF) shl 16) or ((block[pos + 3].toInt() and 0xFF) shl 24)
            pos += 4
            return value
        }
        val vendor = readInt()
        if (vendor < 0 || pos + vendor > block.size) return emptyList()
        pos += vendor
        val count = readInt()
        if (count < 0 || count > 4096) return emptyList()
        val result = ArrayList<String>()
        repeat(count) {
            val length = readInt()
            if (length < 0 || pos + length > block.size) return result
            val entry = String(block, pos, length, Charsets.UTF_8)
            pos += length
            val key = entry.substringBefore('=', "")
            if (key.equals("COMPOSER", true)) {
                entry.substringAfter('=', "").takeIf(String::isNotBlank)?.let { result += it }
            }
        }
        return result
    }


    private fun InputStream.readFully(count: Int): ByteArray {
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

    private fun skipFully(input: InputStream, count: Long) {
        var left = count
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped <= 0) break
            left -= skipped
        }
    }

    private fun synchsafe(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0x7F) shl 21) or ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or (bytes[offset + 3].toInt() and 0x7F)
}
