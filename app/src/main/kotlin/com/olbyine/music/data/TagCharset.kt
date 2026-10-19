package com.olbyine.music.data

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

internal object TagCharset {
    fun looksMojibake(value: String?): Boolean {
        if (value.isNullOrEmpty()) return false
        var high = false
        for (c in value) {
            if (c.code > 0xFF) return false
            if (c.code >= 0x80) high = true
        }
        return high
    }

    fun repair(values: List<String?>): List<String?> {
        if (values.none(::looksMojibake)) return values
        val raw = values.map { value -> value?.takeIf(::looksMojibake)?.toByteArray(Charsets.ISO_8859_1) }
        val decoded = decode(raw)
        return values.mapIndexed { index, value ->
            if (raw[index] == null) value else decoded[index]?.trim()?.takeIf(String::isNotEmpty) ?: value
        }
    }

    fun decode(fields: List<ByteArray?>): List<String?> {
        val present = fields.mapNotNull { it?.trimNul() }.filter { bytes -> bytes.any { it < 0 } }
        val charset = when {
            present.isEmpty() -> Charsets.UTF_8
            present.all { decodeStrict(it, Charsets.UTF_8) != null } -> Charsets.UTF_8
            else -> legacyCharset(present)
        }
        return fields.map { field ->
            val bytes = field?.trimNul() ?: return@map null
            (charset?.let { decodeStrict(bytes, it) } ?: String(bytes, Charsets.ISO_8859_1)).trim()
        }
    }

    private fun legacyCharset(fields: List<ByteArray>): Charset? {
        val misread = fields.any { bytes -> bytes.any { (it.toInt() and 0xFF) in 0x80..0x9F } }
        val candidates = when {
            misread -> MISREAD_CANDIDATES
            fields.all(::highBytesPaired) -> PAIRED_CANDIDATES
            else -> return null
        }
        var best: Charset? = null
        var bestScore = 0f
        for (name in candidates) {
            val charset = charsetOrNull(name) ?: continue
            val texts = fields.map { decodeStrict(it, charset) ?: "" }
            if (fields.indices.any { texts[it].isEmpty() && fields[it].isNotEmpty() }) continue
            val score = texts.sumOf { score(it).toDouble() }.toFloat()
            if (score > bestScore) {
                best = charset
                bestScore = score
            }
        }
        return best
    }

    private fun score(text: String): Float = text.sumOf { c ->
        when (c.code) {
            in 0x00..0x7F -> 0.0
            in 0x3040..0x30FF -> 3.0
            in 0xAC00..0xD7A3 -> if (koreanLocale) 3.0 else 0.5
            in 0x4E00..0x9FFF -> 1.0
            in 0x3000..0x303F, in 0xFF01..0xFF5E -> 1.0
            in 0xFF61..0xFF9F -> -2.0
            in 0xE000..0xF8FF -> -4.0
            else -> -1.0
        }
    }.toFloat()

    private val koreanLocale: Boolean get() = java.util.Locale.getDefault().language == "ko"

    private fun highBytesPaired(bytes: ByteArray): Boolean {
        var run = 0
        for (b in bytes) {
            if (b < 0) run++
            else {
                if (run % 2 != 0) return false
                run = 0
            }
        }
        return run % 2 == 0
    }

    private val MISREAD_CANDIDATES = listOf("windows-31j", "Shift_JIS", "GB18030", "Big5")

    private val PAIRED_CANDIDATES = listOf("GB18030", "Big5", "x-windows-949", "EUC-KR", "EUC-JP")

    private fun charsetOrNull(name: String): Charset? = runCatching { Charset.forName(name) }.getOrNull()

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (error: CharacterCodingException) {
        null
    }

    private fun ByteArray.trimNul(): ByteArray {
        var end = size
        while (end > 0 && this[end - 1] == 0.toByte()) end--
        return if (end == size) this else copyOf(end)
    }
}
