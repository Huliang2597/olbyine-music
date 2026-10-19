package com.olbyine.music.data

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class LyricsMatchStatus(val storageValue: String) {
    MATCHED("matched"),
    REVIEW("review"),
    NOT_FOUND("not_found"),
    UPSTREAM_ERROR("upstream_error"),
}

enum class LyricsSearchSource { ALL, QQ_MUSIC, NETEASE, KUGOU, APPLE_MUSIC, AMLL, UNILYRIC }

data class LyricsCandidate(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Double?,
    val instrumental: Boolean = false,
    val lyrics: String = "",
    val format: String = "",
    val source: String,
    val platform: String = source,
    val platformId: String = "",
    val wordLyrics: String = "",
    val wordFormat: String = "",
    val translationLyrics: String = "",
    val romanLyrics: String = "",
    val database: String = platform,
    val coverUrl: String = "",
    val confidence: Double = 0.0,
    val matchConfidence: Double = confidence,
    val recommendationScore: Double = 0.0,
    val databaseSupport: Int = 1,
    val sources: List<String> = listOf(source),
) {
    val hasWordTiming: Boolean
        get() = wordLyrics.isNotBlank()

    fun toStoredLyrics() = StoredLyrics(
        lyrics = lyrics,
        wordLyrics = wordLyrics,
        translationLyrics = translationLyrics,
        romanLyrics = romanLyrics,
        source = sources.distinct().joinToString("+").ifBlank { source },
        format = wordFormat.ifBlank { format.ifBlank { "unknown" } },
        confidence = confidence,
        instrumental = instrumental,
    )
}

data class LyricsMatchResult(
    val status: LyricsMatchStatus,
    val confidence: Double = 0.0,
    val selected: LyricsCandidate? = null,
    val candidates: List<LyricsCandidate> = emptyList(),
    val cached: Boolean = false,
)

internal data class LyricsQuery(
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Double?,
    val filename: String,
)

internal data class ProviderOutcome(
    val completed: Boolean,
    val candidates: List<LyricsCandidate> = emptyList(),
    val errors: List<Throwable> = emptyList(),
)

internal object LyricsMatcher {
    private val unknownMetadata = setOf(
        "", "未知", "未知歌曲", "未知歌手", "未知艺术家", "未知专辑",
        "unknown", "unknown artist", "unknown album",
    )
    private val punctuation = Regex("[\\p{P}\\p{S}\\s_]+")
    private val timestamp = Regex("[\\[<]\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?[\\]>]")
    private val metadataLine = Regex("^\\[(?:ti|ar|al|by|offset|id|hash|total|sign|qq):", RegexOption.IGNORE_CASE)
    private val creditLine = Regex("^(?:作词|作曲|编曲|制作人)\\s*[:：]")
    private val versionPatterns = linkedMapOf(
        "live" to Regex("(?:\\blive\\b|现场|演唱会)", RegexOption.IGNORE_CASE),
        "remix" to Regex("(?:\\bremix(?:ed)?\\b|混音)", RegexOption.IGNORE_CASE),
        "instrumental" to Regex("(?:\\binstrumental\\b|伴奏|纯音乐|无人声)", RegexOption.IGNORE_CASE),
        "cover" to Regex("(?:\\bcover\\b|翻唱)", RegexOption.IGNORE_CASE),
        "remastered" to Regex("(?:\\bremaster(?:ed)?\\b|重制版|数字修复)", RegexOption.IGNORE_CASE),
        "acoustic" to Regex("(?:\\bacoustic\\b|不插电)", RegexOption.IGNORE_CASE),
    )

    fun prepare(track: Track): LyricsQuery {
        val parsed = parseFilename(track.displayName)
        val title = track.title.trim().takeUnless(::isMissing) ?: parsed.second
        val artist = track.artist.trim().takeUnless(::isMissing) ?: parsed.first
        val album = track.album.trim().takeUnless(::isMissing).orEmpty()
        return LyricsQuery(
            title = title,
            artist = artist,
            album = album,
            durationSeconds = track.durationMs.takeIf { it > 0L }?.div(1_000.0),
            filename = track.displayName,
        )
    }

    fun score(query: LyricsQuery, candidate: LyricsCandidate): Double = scored(query, candidate).score

    fun classify(
        query: LyricsQuery,
        candidates: List<LyricsCandidate>,
        includeCandidates: Boolean,
    ): LyricsMatchResult {
        val usable = candidates
            .asSequence()
            .filter { it.instrumental || it.lyrics.isNotBlank() || it.wordLyrics.isNotBlank() }
            .distinctBy { it.id.ifBlank { "${it.source}|${normalize(it.title)}|${normalize(it.artist)}|${it.durationSeconds}" } }
            .map { scored(query, it) }
            .sortedByDescending(Scored::score)
            .toList()

        val clusters = cluster(usable)
        val top = clusters.firstOrNull()
        if (top == null) {
            return LyricsMatchResult(LyricsMatchStatus.NOT_FOUND)
        }
        val manualCandidates = clusters
            .take(MAX_CANDIDATES)
            .map(Clustered::candidate)
        if (top.candidate.confidence < MINIMUM_CONFIDENCE) {
            return if (includeCandidates && manualCandidates.isNotEmpty()) {
                LyricsMatchResult(
                    status = LyricsMatchStatus.REVIEW,
                    confidence = top.candidate.confidence,
                    candidates = manualCandidates,
                )
            } else {
                LyricsMatchResult(LyricsMatchStatus.NOT_FOUND)
            }
        }
        val runnerUp = clusters.getOrNull(1)
        val gap = runnerUp?.let { top.rank - it.rank } ?: 1.0
        val durationIsReliable = query.durationSeconds == null || top.candidate.durationSeconds != null ||
            (normalize(query.artist).isNotBlank() && top.titleScore >= 0.97 && top.artistScore >= 0.97 &&
                top.candidate.confidence >= 0.9)
        val resultCandidates = if (includeCandidates) manualCandidates else clusters.take(5).map { it.candidate }

        return if (top.candidate.confidence >= AUTO_MATCH_CONFIDENCE && gap >= AUTO_MATCH_GAP && durationIsReliable) {
            LyricsMatchResult(
                status = LyricsMatchStatus.MATCHED,
                confidence = top.candidate.confidence,
                selected = top.candidate,
                candidates = resultCandidates,
            )
        } else {
            LyricsMatchResult(
                status = LyricsMatchStatus.REVIEW,
                confidence = top.candidate.confidence,
                candidates = resultCandidates,
            )
        }
    }

    private fun cluster(input: List<Scored>): List<Clustered> {
        val groups = mutableListOf<MutableList<Scored>>()
        input.forEach { candidate ->
            val database = candidate.candidate.database.ifBlank { candidate.candidate.platform }
            val group = groups.firstOrNull { existing ->
                existing.none { member ->
                    member.candidate.database.ifBlank { member.candidate.platform } == database
                } &&
                    sameRecording(existing.first(), candidate)
            }
            if (group == null) groups += mutableListOf(candidate) else group += candidate
        }
        var left = 0
        while (left < groups.size) {
            var right = groups.lastIndex
            while (right > left) {
                val leftDatabases = groups[left].map { member ->
                    member.candidate.database.ifBlank { member.candidate.platform }
                }.toSet()
                val rightDatabases = groups[right].map { member ->
                    member.candidate.database.ifBlank { member.candidate.platform }
                }.toSet()
                if ((leftDatabases.size >= 2 || rightDatabases.size >= 2) &&
                    leftDatabases.intersect(rightDatabases).isEmpty() &&
                    sameRecording(groups[left].first(), groups[right].first())
                ) {
                    groups[left] += groups[right]
                    groups.removeAt(right)
                }
                right--
            }
            left++
        }
        return groups.map(::aggregate).sortedWith(
            compareByDescending<Clustered> { it.rank }
                .thenByDescending { it.candidate.databaseSupport }
                .thenByDescending { lyricsQuality(it.candidate) },
        )
    }

    private fun aggregate(group: List<Scored>): Clustered {
        val primary = group.maxBy(Scored::score)
        val bestDuration = group.mapNotNull(Scored::durationScore).maxOrNull()
        val inTime = if (bestDuration == null) group else group.filter { member ->
            val score = member.durationScore
            score == null || score >= bestDuration - .02
        }.ifEmpty { group }
        val bestLyrics = inTime.maxWithOrNull(
            compareBy<Scored> { lyricsQuality(it.candidate) }.thenBy { it.score },
        ) ?: primary
        val richestAuxiliary = group.maxWithOrNull(
            compareBy<Scored> {
                (if (it.candidate.translationLyrics.isNotBlank()) 1 else 0) +
                    (if (it.candidate.romanLyrics.isNotBlank()) 1 else 0)
            }.thenBy { lyricsQuality(it.candidate) },
        ) ?: primary
        val sources = group.map { it.candidate.source }.distinct()
        val databases = group.map { member ->
            member.candidate.database.ifBlank { member.candidate.platform }
        }.toSet()
        var supportBonus = when {
            databases.size >= 4 -> 0.07
            databases.size == 3 -> 0.05
            databases.size == 2 -> 0.03
            else -> 0.0
        }
        if (databases.contains("amll-ttml")) supportBonus += 0.03
        if (primary.versionConflict || (primary.durationScore != null && primary.durationScore < 0.5)) supportBonus = 0.0
        supportBonus = min(0.09, supportBonus)
        val confidence = rounded(min(1.0, primary.score + supportBonus))
        val selected = bestLyrics.candidate
        val wordBonus = wordTimingBonus(selected, primary)
        val combined = primary.candidate.copy(
            id = selected.id,
            instrumental = selected.instrumental,
            lyrics = selected.lyrics,
            format = selected.format,
            source = selected.source,
            platform = selected.platform,
            platformId = selected.platformId,
            wordLyrics = selected.wordLyrics,
            wordFormat = selected.wordFormat,
            translationLyrics = selected.translationLyrics.ifBlank { richestAuxiliary.candidate.translationLyrics },
            romanLyrics = selected.romanLyrics.ifBlank { richestAuxiliary.candidate.romanLyrics },
            coverUrl = primary.candidate.coverUrl.ifBlank {
                group.firstOrNull { it.candidate.coverUrl.isNotBlank() }?.candidate?.coverUrl.orEmpty()
            },
            confidence = confidence,
            matchConfidence = rounded(primary.score),
            recommendationScore = rounded(supportBonus),
            databaseSupport = databases.size,
            sources = sources,
        )
        return Clustered(
            candidate = combined,
            rank = primary.score + supportBonus + wordBonus,
            titleScore = primary.titleScore,
            artistScore = primary.artistScore,
        )
    }

    private fun scored(query: LyricsQuery, candidate: LyricsCandidate): Scored {
        val title = titleSimilarity(query.title, candidate.title)
        val artist = artistSimilarity(query.artist, candidate.artist)
        val album = if (query.album.isNotBlank() && candidate.album.isNotBlank()) {
            similarity(query.album, candidate.album)
        } else null
        val duration = durationScore(query.durationSeconds, candidate.durationSeconds)
        val weighted = listOf(
            title to 0.45,
            artist to 0.25,
            duration to 0.20,
            album to 0.10,
        ).filter { it.first != null }
        var score = weighted.sumOf { (value, weight) -> value!! * weight } / weighted.sumOf { it.second }
        val conflict = versionFlags(query.title, query.album) != versionFlags(candidate.title, candidate.album)
        if (conflict) score = max(0.0, score - 0.20)
        if (query.album.isNotBlank() && candidate.album.isBlank()) score = max(0.0, score - 0.03)
        if (query.durationSeconds != null && candidate.durationSeconds == null) score = max(0.0, score - 0.05)
        score = rounded(score)
        val rankedCandidate = candidate.copy(confidence = score, matchConfidence = score)
        return Scored(
            candidate = rankedCandidate,
            score = score,
            titleScore = title,
            artistScore = artist,
            albumScore = album,
            durationScore = duration,
            versionConflict = conflict,
            rank = score + wordTimingBonus(rankedCandidate, title, artist, duration, conflict),
        )
    }

    private fun sameRecording(first: Scored, second: Scored): Boolean {
        if (similarity(first.candidate.title, second.candidate.title) < 0.92) return false
        if (!artistsCompatible(first.candidate.artist, second.candidate.artist)) return false
        if (versionFlags(first.candidate.title, first.candidate.album) !=
            versionFlags(second.candidate.title, second.candidate.album)
        ) return false
        val firstDuration = first.candidate.durationSeconds
        val secondDuration = second.candidate.durationSeconds
        if (firstDuration != null && secondDuration != null && abs(firstDuration - secondDuration) > 5.0) return false
        val firstText = comparableLyrics(first.candidate.lyrics)
        val secondText = comparableLyrics(second.candidate.lyrics)
        return firstText.length < 20 || secondText.length < 20 || similarity(firstText, secondText) >= 0.55
    }

    private fun artistsCompatible(first: String, second: String): Boolean {
        if (first.isBlank() || second.isBlank()) return true
        val direct = similarity(first, second)
        if (direct >= 0.88) return true
        val a = normalize(first)
        val b = normalize(second)
        val shorter = if (a.length <= b.length) a else b
        val longer = if (a.length <= b.length) b else a
        return shorter.length >= 2 && longer.contains(shorter) && direct >= 0.82
    }

    private fun comparableLyrics(raw: String): String = raw.lineSequence()
        .filterNot { metadataLine.containsMatchIn(it) }
        .map { it.replace(timestamp, "") }
        .map { it.trim() }
        .filter { it.isNotEmpty() && !creditLine.containsMatchIn(it) }
        .take(40)
        .joinToString(" ")

    private fun lyricsQuality(candidate: LyricsCandidate): Int {
        if (candidate.instrumental) return 1_000
        val base = when {
            candidate.wordLyrics.isNotBlank() -> when (candidate.wordFormat) {
                "ttml" -> 500
                "yrc" -> 450
                "enhanced-lrc" -> 400
                else -> 350
            }
            candidate.format == "lrc-line" -> 200
            else -> 100
        }
        return base + when (candidate.source) {
            "amll-ttml" -> 60
            "lrclib" -> 52
            "netease", "qqmusic" -> 46
            "kugou" -> 45
            "wwoyun" -> 43
            "meting" -> 38
            "lrcapi" -> 30
            else -> 20
        }
    }

    private fun wordTimingBonus(candidate: LyricsCandidate, score: Scored): Double = wordTimingBonus(
        candidate,
        score.titleScore,
        score.artistScore,
        score.durationScore,
        score.versionConflict,
    )

    private fun wordTimingBonus(
        candidate: LyricsCandidate,
        titleScore: Double,
        artistScore: Double,
        durationScore: Double?,
        versionConflict: Boolean,
    ): Double {
        if (candidate.wordLyrics.isBlank() || versionConflict || titleScore < 0.90 || artistScore < 0.84 ||
            (durationScore != null && durationScore < 0.5)
        ) return 0.0
        return when (candidate.wordFormat) {
            "ttml" -> 0.06
            "yrc" -> 0.055
            "enhanced-lrc" -> 0.05
            else -> 0.04
        }
    }

    private fun durationScore(first: Double?, second: Double?): Double? {
        if (first == null || second == null) return null
        val difference = abs(first - second)
        return when {
            difference <= 2.0 -> 1.0
            difference <= 5.0 -> 1.0 - ((difference - 2.0) / 3.0) * 0.15
            difference <= 10.0 -> 0.85 - ((difference - 5.0) / 5.0) * 0.35
            difference <= 15.0 -> 0.5 - ((difference - 10.0) / 5.0) * 0.3
            else -> max(0.0, 0.2 - (difference - 15.0) / 50.0)
        }
    }

    private fun similarity(first: String, second: String): Double {
        val a = normalize(first)
        val b = normalize(second)
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        if (a.contains(b) || b.contains(a)) {
            val ratio = min(a.length, b.length).toDouble() / max(a.length, b.length)
            return max(0.82, min(0.96, 0.82 + ratio * 0.14))
        }
        val left = bigrams(a).toMutableList()
        val right = bigrams(b)
        var matches = 0
        right.forEach { value ->
            val index = left.indexOf(value)
            if (index >= 0) {
                matches++
                left.removeAt(index)
            }
        }
        return if (left.size + matches + right.size == 0) 0.0
        else 2.0 * matches / (bigrams(a).size + right.size)
    }

    private val featSuffix = Regex(
        "\\s*[(（\\[【]\\s*(?:feat\\.?|ft\\.?|featuring|with)\\s[^)）\\]】]*[)）\\]】]|\\s+(?:feat\\.?|ft\\.?|featuring)\\s.*$",
        RegexOption.IGNORE_CASE,
    )
    private val editionSuffix = Regex(
        "\\s*[(（\\[【]\\s*(?:explicit|clean|deluxe(?: edition)?|special edition|bonus track|single version|album version|radio edit)\\s*[)）\\]】]",
        RegexOption.IGNORE_CASE,
    )
    private val anyBrackets = Regex("\\s*[(（\\[【][^)）\\]】]*[)）\\]】]")
    private val artistSeparators = Regex(
        "\\s*(?:/|／|&|＆|,|，|、|;|；|\\+|×|\\s+x\\s+|\\s+and\\s+|\\s+feat\\.?\\s+|\\s+ft\\.?\\s+|\\s+featuring\\s+|\\s+with\\s+)\\s*",
        RegexOption.IGNORE_CASE,
    )

    fun titleSimilarity(first: String, second: String): Double {
        val direct = similarity(first, second)
        if (direct >= 0.99) return direct
        fun clean(value: String) = value.replace(featSuffix, "").replace(editionSuffix, "").replace(" - ", " (").trim()
        val cleaned = similarity(clean(first), clean(second))
        if (cleaned >= 0.99) return max(direct, 0.96)
        fun base(value: String) = value.replace(anyBrackets, "").substringBefore(" - ").trim()
        val baseFirst = base(first)
        val baseSecond = base(second)
        val bare = if (baseFirst.isNotBlank() && baseSecond.isNotBlank()) similarity(baseFirst, baseSecond) else 0.0
        val bracketOnly = if (bare >= 0.99) 0.86 else bare * 0.88
        val a = normalize(first)
        val b = normalize(second)
        val variant = if (a.length == b.length && a.length >= 2) {
            val same = a.indices.count { a[it] == b[it] }.toDouble() / a.length
            if ((same >= 0.8 && a.length >= 4) || (same >= 0.5 && a.length <= 3)) 0.9 else 0.0
        } else 0.0
        return maxOf(direct, cleaned, bracketOnly, variant)
    }

    fun artistSimilarity(first: String, second: String): Double {
        val direct = similarity(first, second)
        if (direct >= 0.99) return direct
        val left = splitArtists(first)
        val right = splitArtists(second)
        if (left.isEmpty() || right.isEmpty()) return direct
        val matched = left.count { a -> right.any { b -> a == b || (min(a.length, b.length) >= 3 && (a.contains(b) || b.contains(a))) } }
        val set = when {
            matched == left.size && left.size == right.size -> 1.0
            matched == left.size -> 0.95
            matched == right.size -> 0.93
            left.size >= 2 && matched + 1 >= left.size -> 0.9
            right.firstOrNull()?.let { it.contains("various") || it.contains("群星") } == true && left.size > 5 -> 0.9
            matched >= 1 -> 0.84
            else -> 0.0
        }
        return max(direct, set)
    }

    private fun splitArtists(value: String): List<String> =
        value.split(artistSeparators).map(::normalize).filter(String::isNotBlank).distinct()

    private fun bigrams(value: String): List<String> = when {
        value.isEmpty() -> emptyList()
        value.length == 1 -> listOf(value)
        else -> (0 until value.lastIndex).map { value.substring(it, it + 2) }
    }

    private fun normalize(value: String): String = punctuation.replace(
        simplified(Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)).lowercase(Locale.CHINA),
        "",
    )

    private val toSimplified by lazy {
        runCatching { android.icu.text.Transliterator.getInstance("Traditional-Simplified") }.getOrNull()
    }
    private val simplifiedMemo = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun simplified(value: String): String {
        if (value.none { it.code in 0x3400..0x9FFF }) return value
        val transliterator = toSimplified ?: return value
        return simplifiedMemo[value] ?: synchronized(transliterator) { transliterator.transliterate(value) }
            .also { if (simplifiedMemo.size < 4_096) simplifiedMemo[value] = it }
    }

    private fun versionFlags(title: String, album: String): Set<String> {
        val text = Normalizer.normalize("$title $album", Normalizer.Form.NFKC)
        return versionPatterns.filterValues { it.containsMatchIn(text) }.keys
    }

    private fun parseFilename(value: String): Pair<String, String> {
        val base = value.substringAfterLast('/').substringBeforeLast('.').trim()
        val parts = base.split(Regex("\\s+(?:-|–|—|－)\\s+"), limit = 2)
        return if (parts.size == 2) parts[0].trim() to parts[1].trim() else "" to base
    }

    private fun isMissing(value: String): Boolean = value.lowercase(Locale.CHINA) in unknownMetadata

    private fun rounded(value: Double): Double = kotlin.math.round(value * 10_000.0) / 10_000.0

    private data class Scored(
        val candidate: LyricsCandidate,
        val score: Double,
        val titleScore: Double,
        val artistScore: Double,
        val albumScore: Double?,
        val durationScore: Double?,
        val versionConflict: Boolean,
        val rank: Double,
    )

    private data class Clustered(
        val candidate: LyricsCandidate,
        val rank: Double,
        val titleScore: Double,
        val artistScore: Double,
    )

    private const val MINIMUM_CONFIDENCE = 0.65
    private const val AUTO_MATCH_CONFIDENCE = 0.85
    private const val AUTO_MATCH_GAP = 0.08
    private const val MAX_CANDIDATES = 10
}
