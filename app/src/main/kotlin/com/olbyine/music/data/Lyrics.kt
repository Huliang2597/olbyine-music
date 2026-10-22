package com.olbyine.music.data

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

data class LyricLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val translation: String? = null,
    val romanization: String? = null,
    val words: List<LyricWord> = emptyList(),
)

data class LyricsDocument(
    val lines: List<LyricLine>,
    val isSynchronized: Boolean = true,
    val rawTtml: String? = null,
    val trackId: String? = null,
) {
    fun lineIndexAt(positionMs: Long): Int {
        if (!isSynchronized || lines.isEmpty() || positionMs < lines.first().startMs) return -1
        var low = 0
        var high = lines.lastIndex
        var answer = 0
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (lines[middle].startMs <= positionMs) {
                answer = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return answer
    }

    companion object {
        val Empty = LyricsDocument(emptyList(), isSynchronized = false)
    }
}

object LyricsParser {
    private val lrcTimestamp = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]" )
    private val inlineTimestamp = Regex("<(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?>")
    private val yrcLine = Regex("^\\[(\\d+),(\\d+)](.*)$")
    private val yrcWord = Regex("\\((\\d+),(\\d+)(?:,\\d+)?\\)([^()]*)")
    private val htmlTag = Regex("<[^>]+>")

    private val TIMESTAMP_TAG = Regex("""\[\d{1,3}:\d{1,2}""")
    private val QUOTED_TEXT = Regex(""""((?:[^"\\]|\\.)*)"""")

    private val TX_FIELD = Regex(""""tx"\s*:\s*"((?:[^"\\]|\\.)*)"""")

    fun sanitize(raw: String): String {
        val text = raw.trim()
        if (txField(text)) return ""
        if (!text.startsWith("{") && !text.startsWith("[")) return raw
        val looksJson = text.contains("\"code\"") || text.contains("\"msg\"") ||
            text.contains("\"data\"") || text.contains("\"lyric\"")
        if (!looksJson) return raw
        val quoted = QUOTED_TEXT.findAll(text).map { it.groupValues[1] }.toList()
        val withTime = quoted.filter { TIMESTAMP_TAG.containsMatchIn(it) }
        if (withTime.isNotEmpty()) return withTime.joinToString("\n")
        val payload = quoted.firstOrNull { it.length > 20 && !it.startsWith("http") && !TIMESTAMP_TAG.containsMatchIn(it) }
        return payload ?: ""
    }

    private fun txField(text: String): Boolean {
        if (!text.startsWith("{")) return false
        val tx = TX_FIELD.findAll(text).map { it.groupValues[1] }.toList()
        if (tx.size < 2) return false
        val credit = tx.joinToString("").replace("\u00a0", "")
        return !TIMESTAMP_TAG.containsMatchIn(credit) &&
            (credit.contains("作曲") || credit.contains("作词") || credit.contains("编曲") ||
                credit.contains("制作") || credit.contains("混音") || credit.contains("母带"))
    }

    fun parse(raw: String): LyricsDocument {
        if (raw.isBlank()) return LyricsDocument.Empty
        val cleaned = sanitize(raw)
        if (cleaned.isBlank()) return LyricsDocument.Empty
        val normalized = cleaned.replace("\r\n", "\n").replace('\r', '\n').trim()
        val parsed = if (normalized.contains("<tt", ignoreCase = true) ||
            normalized.contains("<p ", ignoreCase = true)
        ) {
            parseTtml(normalized)
        } else {
            parseTimedText(normalized)
        }
        if (parsed.isNotEmpty()) return LyricsDocument(finalizeLines(parsed))
        if (normalized.contains("<tt", ignoreCase = true)) return LyricsDocument.Empty
        val plainLines = normalized.lineSequence()
            .map { it.trim() }
            .filter(String::isNotEmpty)
            .filterNot { it.matches(Regex("^\\[[a-z][a-z0-9_-]*:.*]$", RegexOption.IGNORE_CASE)) }
            .map { decodeEntities(it.replace(htmlTag, "")).trim() }
            .filter(String::isNotEmpty)
            .map { LyricLine(startMs = 0L, endMs = 0L, text = it) }
            .toList()
        return if (plainLines.isEmpty()) LyricsDocument.Empty
        else LyricsDocument(plainLines, isSynchronized = false)
    }

    fun parseBundle(
        lineLyrics: String?,
        wordLyrics: String?,
        translationLyrics: String?,
        romanLyrics: String?,
    ): LyricsDocument {
        val wordDocument = wordLyrics?.takeIf(String::isNotBlank)?.let(::parse)
        val lineDocument = lineLyrics?.takeIf(String::isNotBlank)?.let(::parse)
        val primary = wordDocument?.takeIf { it.isSynchronized && it.lines.any { line -> line.words.isNotEmpty() } }
            ?: lineDocument?.takeIf { it.lines.isNotEmpty() }
            ?: wordDocument?.takeIf { it.lines.isNotEmpty() }
            ?: return LyricsDocument.Empty
        if (!primary.isSynchronized) return primary

        val translations = translationLyrics?.takeIf(String::isNotBlank)?.let(::parse)
        val romanizations = romanLyrics?.takeIf(String::isNotBlank)?.let(::parse)
        val merged = primary.lines.map { line ->
            line.copy(
                translation = line.translation
                    ?: translations?.nearestText(line.startMs),
                romanization = line.romanization
                    ?: romanizations?.nearestText(line.startMs),
            )
        }
        return LyricsDocument(merged, isSynchronized = true)
    }

    private fun LyricsDocument.nearestText(positionMs: Long): String? {
        if (!isSynchronized || lines.isEmpty()) return null
        var low = 0
        var high = lines.lastIndex
        while (low <= high) {
            val middle = (low + high) ushr 1
            when {
                lines[middle].startMs < positionMs -> low = middle + 1
                lines[middle].startMs > positionMs -> high = middle - 1
                else -> return lines[middle].text
            }
        }
        return listOfNotNull(lines.getOrNull(high), lines.getOrNull(low))
            .minByOrNull { kotlin.math.abs(it.startMs - positionMs) }
            ?.takeIf { kotlin.math.abs(it.startMs - positionMs) <= AUXILIARY_TIME_TOLERANCE_MS }
            ?.text
    }

    private fun parseTimedText(raw: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        val globalOffset = Regex("(?im)^\\[offset:([+-]?\\d+)]\\s*$")
            .find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        raw.lineSequence().forEach { original ->
            val line = original.trim()
            if (line.isEmpty()) return@forEach
            Regex("^\\[offset:([+-]?\\d+)]$", RegexOption.IGNORE_CASE).matchEntire(line)?.let {
                return@forEach
            }
            yrcLine.matchEntire(line)?.let { match ->
                val start = match.groupValues[1].toLong() + globalOffset
                val duration = match.groupValues[2].toLong().coerceAtLeast(1L)
                val body = match.groupValues[3]
                val words = yrcWord.findAll(body).map { word ->
                    val wordStart = word.groupValues[1].toLong() + globalOffset
                    val wordDuration = word.groupValues[2].toLong().coerceAtLeast(1L)
                    LyricWord(decodeEntities(word.groupValues[3]), wordStart, wordStart + wordDuration)
                }.filter { it.text.isNotEmpty() }.toList()
                val text = if (words.isNotEmpty()) words.joinToString("") { it.text }
                    else decodeEntities(body.replace(yrcWord, ""))
                if (text.isNotBlank()) result += LyricLine(start, start + duration, text, words = words)
                return@forEach
            }

            val stamps = lrcTimestamp.findAll(line).toList()
            if (stamps.isEmpty()) return@forEach
            val bodyStart = stamps.last().range.last + 1
            val body = line.substring(bodyStart).trim()
            val inlineMatches = inlineTimestamp.findAll(body).toList()
            stamps.forEach { stamp ->
                val start = timestamp(stamp.groupValues[1], stamp.groupValues[2], stamp.groupValues[3]) + globalOffset
                val words = if (inlineMatches.isEmpty()) emptyList() else {
                    inlineMatches.mapIndexedNotNull { index, wordStamp ->
                        val wordStart = timestamp(
                            wordStamp.groupValues[1],
                            wordStamp.groupValues[2],
                            wordStamp.groupValues[3],
                        ) + globalOffset
                        val textStart = wordStamp.range.last + 1
                        val textEnd = inlineMatches.getOrNull(index + 1)?.range?.first ?: body.length
                        val wordText = decodeEntities(body.substring(textStart, textEnd))
                        val nextTimestamp = inlineMatches.getOrNull(index + 1)?.let { next ->
                            timestamp(next.groupValues[1], next.groupValues[2], next.groupValues[3]) + globalOffset
                        }
                        val wordEnd = nextTimestamp?.takeIf { it > wordStart } ?: (wordStart + 1L)
                        if (wordText.isEmpty()) null else LyricWord(wordText, wordStart, wordEnd)
                    }
                }
                val text = if (words.isNotEmpty()) words.joinToString("") { it.text }
                    else decodeEntities(body.replace(inlineTimestamp, "")).trim()
                if (text.isNotEmpty()) result += LyricLine(start, start + 1L, text, words = words)
            }
        }
        return result
    }

    private fun parseTtml(raw: String): List<LyricLine> {
        if (raw.contains("<!DOCTYPE", ignoreCase = true) || raw.contains("<!ENTITY", ignoreCase = true)) {
            return emptyList()
        }
        val document = runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                runCatching { isXIncludeAware = false }
                listOf(
                    "http://apache.org/xml/features/disallow-doctype-decl" to true,
                    "http://xml.org/sax/features/external-general-entities" to false,
                    "http://xml.org/sax/features/external-parameter-entities" to false,
                    "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
                ).forEach { (feature, enabled) -> runCatching { setFeature(feature, enabled) } }
            }
            factory.newDocumentBuilder().parse(InputSource(StringReader(raw)))
        }.getOrNull() ?: return emptyList()

        val root = document.documentElement ?: return emptyList()
        if (root.localTag() != "tt") return emptyList()
        val mode = root.attributeLocal("timing")?.lowercase(Locale.ROOT)
        val auxiliary = parseTtmlAuxiliary(root)
        val lines = mutableListOf<LyricLine>()

        fun visit(element: Element, inheritedStart: Long?, inheritedEnd: Long?, instrumental: Boolean) {
            val local = element.localTag()
            val start = element.timeAttribute("begin") ?: element.timeAttribute("start") ?: inheritedStart
            val end = element.timeAttribute("end")
                ?: element.timeAttribute("dur")?.let { duration -> (start ?: 0L) + duration }
                ?: inheritedEnd
            val part = element.attributeLocal("song-part")?.lowercase(Locale.ROOT)
            val scopedInstrumental = instrumental || part == "instrumental"

            if (local == "p") {
                if (!scopedInstrumental) {
                    parseTtmlParagraph(element, start, end, mode, auxiliary)?.let(lines::add)
                }
                return
            }
            element.childElements().forEach { child ->
                visit(child, start, end, scopedInstrumental)
            }
        }

        root.childElements().firstOrNull { it.localTag() == "body" }?.let { body ->
            visit(body, body.timeAttribute("begin"), body.timeAttribute("end"), false)
        }
        return lines
    }

    private fun parseTtmlParagraph(
        paragraph: Element,
        inheritedStart: Long?,
        inheritedEnd: Long?,
        rootMode: String?,
        auxiliary: TtmlAuxiliary,
    ): LyricLine? {
        val explicitStart = paragraph.timeAttribute("begin") ?: paragraph.timeAttribute("start") ?: inheritedStart
        val explicitEnd = paragraph.timeAttribute("end")
            ?: paragraph.timeAttribute("dur")?.let { (explicitStart ?: 0L) + it }
            ?: inheritedEnd
        val wordMode = rootMode == "word" || (rootMode != "line" && paragraph.hasTimedSpan())
        val content = parseTtmlContent(paragraph, wordMode, explicitStart, explicitEnd)
        val words = content.words
            .filter { it.text.isNotEmpty() }
            .sortedBy(LyricWord::startMs)
        val text = normalizeTtmlText(content.text.toString())
            .ifBlank { normalizeTtmlText(words.joinToString("") { it.text }) }
        if (text.isBlank()) return null

        val firstWordStart = words.firstOrNull()?.startMs
        val start = when {
            explicitStart != null && (explicitEnd == null || firstWordStart == null || explicitEnd >= firstWordStart) -> explicitStart
            firstWordStart != null -> firstWordStart
            else -> return null
        }
        val end = (explicitEnd ?: words.lastOrNull()?.endMs ?: (start + 5_000L))
            .coerceAtLeast(start + 1L)
        val key = paragraph.attributeLocal("key")
        val inlineTranslation = content.translations.map(::normalizeTtmlText).filter(String::isNotBlank)
            .distinct().joinToString("\n").ifBlank { null }
        val inlineRomanization = content.romanizations.map(::normalizeTtmlText).filter(String::isNotBlank)
            .distinct().joinToString(" ").ifBlank { null }
        return LyricLine(
            startMs = start,
            endMs = end,
            text = text,
            translation = inlineTranslation ?: key?.let(auxiliary.translations::get),
            romanization = inlineRomanization ?: key?.let(auxiliary.romanizations::get),
            words = if (wordMode) words else emptyList(),
        )
    }

    private fun parseTtmlContent(
        parent: Element,
        wordMode: Boolean,
        inheritedStart: Long?,
        inheritedEnd: Long?,
    ): TtmlContent {
        val output = TtmlContent()
        val nodes = parent.childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            when (node.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                    val text = normalizeTtmlNodeText(node.nodeValue.orEmpty())
                    output.text.append(text)
                    if (wordMode && text.isNotEmpty() && text.isBlank() && output.words.isNotEmpty()) {
                        val last = output.words.last()
                        if (!last.text.endsWith(" ")) output.words[output.words.lastIndex] = last.copy(text = last.text + " ")
                    }
                }
                Node.ELEMENT_NODE -> {
                    val element = node as Element
                    val role = element.attributeLocal("role")?.lowercase(Locale.ROOT).orEmpty()
                    when {
                        role.contains("translation") -> {
                            normalizeTtmlText(element.textContent.orEmpty()).takeIf(String::isNotBlank)
                                ?.let(output.translations::add)
                        }
                        role.contains("roman") || role.contains("transliteration") -> {
                            normalizeTtmlText(element.textContent.orEmpty()).takeIf(String::isNotBlank)
                                ?.let(output.romanizations::add)
                        }
                        role == "x-bg" || role.endsWith(":x-bg") -> Unit
                        else -> {
                            val start = element.timeAttribute("begin") ?: inheritedStart
                            val ownEnd = element.timeAttribute("end")
                                ?: element.timeAttribute("dur")?.let { (start ?: 0L) + it }
                            val end = ownEnd ?: inheritedEnd
                            val nested = parseTtmlContent(element, wordMode, start, end)
                            output.text.append(nested.text)
                            output.translations += nested.translations
                            output.romanizations += nested.romanizations
                            val hasOwnTiming = element.hasAttributeLocal("begin") ||
                                element.hasAttributeLocal("end") || element.hasAttributeLocal("dur")
                            val nestedText = normalizeTtmlText(nested.text.toString())
                            if (wordMode && element.localTag() == "span" && hasOwnTiming &&
                                start != null && nestedText.isNotBlank()
                            ) {
                                output.words += LyricWord(nestedText, start, ownEnd?.coerceAtLeast(start + 1L) ?: start + 1L)
                            } else {
                                output.words += nested.words
                            }
                        }
                    }
                }
            }
        }
        return output
    }

    private fun parseTtmlAuxiliary(root: Element): TtmlAuxiliary {
        val translations = mutableMapOf<String, RankedText>()
        val romanizations = mutableMapOf<String, RankedText>()
        root.descendantElements().forEach { track ->
            val kind = track.localTag()
            if (kind != "translation" && kind != "transliteration") return@forEach
            val languageRank = if (kind == "translation") translationRank(track.attributeLocal("lang")) else 0
            track.descendantElements().filter { it.localTag() == "text" }.forEach textLoop@ { textElement ->
                val key = textElement.attributeLocal("for") ?: return@textLoop
                val value = normalizeTtmlText(textElement.textContent.orEmpty())
                if (value.isBlank()) return@textLoop
                val target = if (kind == "translation") translations else romanizations
                val current = target[key]
                if (current == null || languageRank < current.rank) target[key] = RankedText(languageRank, value)
            }
        }
        return TtmlAuxiliary(
            translations = translations.mapValues { it.value.text },
            romanizations = romanizations.mapValues { it.value.text },
        )
    }

    private fun translationRank(language: String?): Int = when {
        language == null -> 3
        language.lowercase(Locale.ROOT).matches(Regex("zh-(?:hans|cn|sg).*")) -> 0
        language.lowercase(Locale.ROOT) == "zh" -> 1
        language.lowercase(Locale.ROOT).startsWith("zh-") -> 2
        else -> 4
    }

    private fun Element.hasTimedSpan(): Boolean = descendantElements().any { element ->
        element.localTag() == "span" &&
            element.attributeLocal("role")?.lowercase(Locale.ROOT).orEmpty().let { role ->
                !role.contains("translation") && !role.contains("roman") && role != "x-bg"
            } && (element.hasAttributeLocal("begin") || element.hasAttributeLocal("end") ||
                element.hasAttributeLocal("dur"))
    }

    private fun Element.timeAttribute(name: String): Long? = attributeLocal(name)?.let(::parseTtmlTimeOrNull)

    private fun Element.hasAttributeLocal(name: String): Boolean = attributeLocal(name) != null

    private fun Element.attributeLocal(name: String): String? {
        val attributes = attributes
        for (index in 0 until attributes.length) {
            val attribute = attributes.item(index)
            val local = (attribute.localName ?: attribute.nodeName.substringAfter(':')).lowercase(Locale.ROOT)
            if (local == name.lowercase(Locale.ROOT)) return attribute.nodeValue
        }
        return null
    }

    private fun Element.localTag(): String = (localName ?: tagName.substringAfter(':')).lowercase(Locale.ROOT)

    private fun Element.childElements(): List<Element> = buildList {
        val nodes = childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node.nodeType == Node.ELEMENT_NODE) add(node as Element)
        }
    }

    private fun Element.descendantElements(): List<Element> = buildList {
        childElements().forEach { child ->
            add(child)
            addAll(child.descendantElements())
        }
    }

    private fun normalizeTtmlNodeText(value: String): String = when {
        value.isEmpty() -> ""
        value.isBlank() -> if (value.any { it == '\n' || it == '\r' || it == '\t' }) "" else " "
        else -> value.replace(Regex("\\s+"), " ")
    }

    private fun normalizeTtmlText(value: String): String = value.replace(Regex("\\s+"), " ").trim()

    private fun finalizeLines(input: List<LyricLine>): List<LyricLine> {
        if (input.isEmpty()) return emptyList()
        val grouped = input.sortedBy(LyricLine::startMs).groupBy(LyricLine::startMs)
        val merged = grouped.entries.sortedBy { it.key }.map { (_, sameTime) ->
            val primary = sameTime.firstOrNull { it.words.isNotEmpty() } ?: sameTime.first()
            val translation = primary.translation
                ?: sameTime.firstOrNull { it !== primary && it.text != primary.text }?.text
            primary.copy(translation = translation)
        }
        return merged.mapIndexed { index, line ->
            val nextStart = merged.getOrNull(index + 1)?.startMs
            val end = when {
                line.endMs > line.startMs + 1L -> line.endMs
                    .let { explicit -> if (nextStart == null) explicit else minOf(explicit, nextStart) }
                    .coerceAtLeast(line.startMs + 1L)
                nextStart != null -> maxOf(line.startMs + 1L, nextStart)
                else -> line.startMs + 5_000L
            }
            val finalizedWords = line.words.mapIndexed { wordIndex, word ->
                val wordEnd = when {
                    word.endMs > word.startMs + 1L -> word.endMs
                    line.words.getOrNull(wordIndex + 1) != null -> line.words[wordIndex + 1].startMs
                    else -> end
                }
                word.copy(endMs = wordEnd.coerceIn(word.startMs + 1L, maxOf(word.startMs + 1L, end)))
            }
            line.copy(endMs = end, words = finalizedWords)
        }
    }

    private fun timestamp(minutes: String, seconds: String, fraction: String): Long {
        val fractionMs = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100L
            2 -> fraction.toLong() * 10L
            else -> fraction.take(3).padEnd(3, '0').toLong()
        }
        return minutes.toLong() * 60_000L + seconds.toLong() * 1_000L + fractionMs
    }

    private fun parseTtmlTimeOrNull(value: String): Long? {
        val text = value.trim()
        if (text.isEmpty()) return null
        if (text.endsWith("ms", ignoreCase = true)) {
            return text.dropLast(2).toDoubleOrNull()?.takeIf(Double::isFinite)?.coerceAtLeast(0.0)?.toLong()
        }
        if (text.endsWith("s", ignoreCase = true)) {
            return text.dropLast(1).toDoubleOrNull()?.takeIf(Double::isFinite)
                ?.let { (it.coerceAtLeast(0.0) * 1_000.0).toLong() }
        }
        if (text.endsWith("m", ignoreCase = true)) {
            return text.dropLast(1).toDoubleOrNull()?.takeIf(Double::isFinite)
                ?.let { (it.coerceAtLeast(0.0) * 60_000.0).toLong() }
        }
        if (text.endsWith("h", ignoreCase = true)) {
            return text.dropLast(1).toDoubleOrNull()?.takeIf(Double::isFinite)
                ?.let { (it.coerceAtLeast(0.0) * 3_600_000.0).toLong() }
        }
        val parts = text.split(':')
        return when (parts.size) {
            3 -> {
                val hours = parts[0].toDoubleOrNull() ?: return null
                val minutes = parts[1].toDoubleOrNull() ?: return null
                val seconds = parts[2].toDoubleOrNull() ?: return null
                ((hours * 3_600_000.0) + (minutes * 60_000.0) + (seconds * 1_000.0))
                    .takeIf(Double::isFinite)?.coerceAtLeast(0.0)?.toLong()
            }
            2 -> {
                val minutes = parts[0].toDoubleOrNull() ?: return null
                val seconds = parts[1].toDoubleOrNull() ?: return null
                ((minutes * 60_000.0) + (seconds * 1_000.0))
                    .takeIf(Double::isFinite)?.coerceAtLeast(0.0)?.toLong()
            }
            1 -> text.toDoubleOrNull()?.takeIf(Double::isFinite)
                ?.let { (it.coerceAtLeast(0.0) * 1_000.0).toLong() }
            else -> null
        }
    }

    private fun decodeEntities(value: String): String = value
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")

    private data class TtmlContent(
        val text: StringBuilder = StringBuilder(),
        val words: MutableList<LyricWord> = mutableListOf(),
        val translations: MutableList<String> = mutableListOf(),
        val romanizations: MutableList<String> = mutableListOf(),
    )

    private data class TtmlAuxiliary(
        val translations: Map<String, String>,
        val romanizations: Map<String, String>,
    )

    private data class RankedText(val rank: Int, val text: String)

    private const val AUXILIARY_TIME_TOLERANCE_MS = 750L
}
