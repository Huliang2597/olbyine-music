package com.olbyine.music.data

import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.InflaterInputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs
import kotlin.math.roundToLong

class OnlineLyricsRepository(private val appleMusic: AppleMusicClient? = null) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val requestGate = Semaphore(8)
    private val qqSessionMutex = Mutex()
    private var qqSession: QqSession? = null
    private val cache = object : LinkedHashMap<String, CacheEntry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean = size > 200
    }

    suspend fun match(
        track: Track,
        includeCandidates: Boolean = false,
        forceSearch: Boolean = false,
        source: LyricsSearchSource = LyricsSearchSource.ALL,
        searchTitle: String? = null,
        searchArtist: String? = null,
    ): LyricsMatchResult = withContext(Dispatchers.IO) {
        val prepared = LyricsMatcher.prepare(track)
        val query = prepared.copy(
            title = searchTitle?.trim() ?: prepared.title,
            artist = searchArtist?.trim() ?: prepared.artist,
        )
        require(query.title.isNotBlank() || query.filename.isNotBlank()) { "歌曲名为空，无法匹配歌词" }
        val cacheKey = cacheKey(query, includeCandidates, source)
        if (!forceSearch) synchronized(cache) {
            cache[cacheKey]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let {
                return@withContext it.result.copy(cached = true)
            }
        }

        if (source == LyricsSearchSource.AMLL) {
            val outcome = amllCatalogSearch(query)
            val result = when {
                outcome.candidates.isNotEmpty() -> LyricsMatchResult(
                    status = LyricsMatchStatus.REVIEW,
                    confidence = outcome.candidates.first().confidence,
                    candidates = outcome.candidates,
                )
                !outcome.completed && outcome.errors.isNotEmpty() ->
                    LyricsMatchResult(LyricsMatchStatus.UPSTREAM_ERROR)
                else -> LyricsMatchResult(LyricsMatchStatus.NOT_FOUND)
            }
            putCache(cacheKey, result)
            return@withContext result
        }

        if (source == LyricsSearchSource.APPLE_MUSIC || source == LyricsSearchSource.QQ_MUSIC ||
            source == LyricsSearchSource.NETEASE || source == LyricsSearchSource.KUGOU
        ) {
            val outcome = when (source) {
                LyricsSearchSource.QQ_MUSIC -> qqMusicSearch(query)
                LyricsSearchSource.NETEASE -> neteaseSearch(query)
                LyricsSearchSource.KUGOU -> kugouSearch(query)
                else -> appleLyricsSearch(query)
            }
            val result = when {
                outcome.candidates.isNotEmpty() -> LyricsMatcher.classify(query, outcome.candidates, includeCandidates)
                !outcome.completed && outcome.errors.isNotEmpty() -> LyricsMatchResult(LyricsMatchStatus.UPSTREAM_ERROR)
                else -> LyricsMatchResult(LyricsMatchStatus.NOT_FOUND)
            }
            putCache(cacheKey, result)
            return@withContext result
        }

        if (source == LyricsSearchSource.UNILYRIC) {
            val outcomes = supervisorScope {
                listOf(
                    async { neteaseSearch(query) },
                    async { qqMusicSearch(query) },
                    async { kugouSearch(query) },
                    async { amllCatalogSearch(query) },
                ).awaitAll()
            }
            val candidates = outcomes.flatMap(ProviderOutcome::candidates)
            val errors = outcomes.flatMap(ProviderOutcome::errors)
            val completed = outcomes.any(ProviderOutcome::completed)
            val result = LyricsMatcher.classify(query, candidates, includeCandidates).let { matched ->
                if (matched.status == LyricsMatchStatus.NOT_FOUND && !completed && errors.isNotEmpty()) {
                    LyricsMatchResult(LyricsMatchStatus.UPSTREAM_ERROR)
                } else matched
            }
            putCache(cacheKey, result)
            return@withContext result
        }

        val allCandidates = mutableListOf<LyricsCandidate>()
        val errors = mutableListOf<Throwable>()
        var completedProviders = 0

        val exact = lrclibExact(query)
        allCandidates += exact.candidates
        errors += exact.errors
        if (exact.completed) completedProviders++
        var classification = LyricsMatcher.classify(query, allCandidates, includeCandidates)
        if (!includeCandidates && classification.status == LyricsMatchStatus.MATCHED &&
            classification.selected?.hasWordTiming == true
        ) {
            putCache(cacheKey, classification)
            return@withContext classification
        }

        val primary = supervisorScope {
            listOf(
                async { lrclibSearch(query) },
                async { neteaseSearch(query) },
                async { qqMusicSearch(query) },
                async { kugouSearch(query) },
                async { amllSearch(query) },
                async { appleLyricsSearch(query) },
            ).awaitAll()
        }
        primary.forEach { outcome ->
            allCandidates += outcome.candidates
            errors += outcome.errors
            if (outcome.completed) completedProviders++
        }
        val primaryEnrichment = if (allCandidates.any {
                it.source == "amll-ttml" && it.hasWordTiming && LyricsMatcher.score(query, it) >= 0.9
            }
        ) {
            ProviderOutcome(completed = true)
        } else {
            amllEnrich(query, allCandidates)
        }
        allCandidates += primaryEnrichment.candidates
        errors += primaryEnrichment.errors
        classification = LyricsMatcher.classify(query, allCandidates, includeCandidates)
        if (!includeCandidates && classification.status == LyricsMatchStatus.MATCHED) {
            putCache(cacheKey, classification)
            return@withContext classification
        }

        val fallback = supervisorScope {
            listOf(
                async { metingSearch(query) },
                async { wwoyunSearch(query) },
                async { lrcApiSearch(query) },
            ).awaitAll()
        }
        fallback.forEach { outcome ->
            allCandidates += outcome.candidates
            errors += outcome.errors
            if (outcome.completed) completedProviders++
        }
        val fallbackEnrichment = if (allCandidates.any {
                it.source == "amll-ttml" && it.hasWordTiming && LyricsMatcher.score(query, it) >= 0.9
            }
        ) {
            ProviderOutcome(completed = true)
        } else {
            amllEnrich(query, fallback.flatMap(ProviderOutcome::candidates))
        }
        allCandidates += fallbackEnrichment.candidates
        errors += fallbackEnrichment.errors
        classification = LyricsMatcher.classify(query, allCandidates, includeCandidates)
        if (classification.status == LyricsMatchStatus.NOT_FOUND && completedProviders == 0 && errors.isNotEmpty()) {
            classification = LyricsMatchResult(LyricsMatchStatus.UPSTREAM_ERROR)
        }
        putCache(cacheKey, classification)
        classification
    }

    suspend fun quickMatch(track: Track): LyricsCandidate? = withContext(Dispatchers.IO) {
        val query = LyricsMatcher.prepare(track)
        if (query.title.isBlank()) return@withContext null
        val key = "quick|" + cacheKey(query, false, LyricsSearchSource.ALL)
        synchronized(cache) {
            cache[key]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let { return@withContext it.result.selected }
        }
        val found = java.util.Collections.synchronizedList(mutableListOf<LyricsCandidate>())
        val selected = kotlinx.coroutines.coroutineScope {
            val arrivals = kotlinx.coroutines.channels.Channel<ProviderOutcome>(kotlinx.coroutines.channels.Channel.UNLIMITED)
            val jobs = mutableListOf<kotlinx.coroutines.Job>()
            fun start(block: suspend () -> ProviderOutcome) {
                jobs += launch { arrivals.send(block()) }
            }
            start { neteaseSearch(query) }
            start { qqMusicSearch(query) }
            start { kugouSearch(query) }
            start { lrclibSearch(query) }
            start { appleLyricsSearch(query) }
            start { amllSearch(query) }
            var pending = jobs.size
            var enriched = false
            val startedAt = System.currentTimeMillis()
            var graceUntil = Long.MAX_VALUE
            var best: LyricsCandidate? = null
            while (pending > 0) {
                val deadline = minOf(startedAt + QUICK_TIMEOUT_MS, graceUntil)
                val wait = deadline - System.currentTimeMillis()
                if (wait <= 0) break
                val outcome = kotlinx.coroutines.withTimeoutOrNull(wait) { arrivals.receive() } ?: break
                pending--
                found += outcome.candidates
                if (!enriched && outcome.candidates.any { it.platform == "netease" }) {
                    enriched = true
                    pending++
                    start { amllEnrich(query, outcome.candidates) }
                }
                val classified = LyricsMatcher.classify(query, found.toList(), includeCandidates = false)
                val choice = classified.selected
                if (classified.status == LyricsMatchStatus.MATCHED && choice != null) {
                    best = choice
                    if (choice.hasWordTiming) break
                    if (graceUntil == Long.MAX_VALUE) graceUntil = System.currentTimeMillis() + QUICK_WORD_GRACE_MS
                }
            }
            jobs.forEach { it.cancel() }
            best ?: LyricsMatcher.classify(query, found.toList(), includeCandidates = true)
                .candidates.firstOrNull()?.takeIf { it.confidence >= QUICK_MINIMUM_CONFIDENCE }
        }
        if (selected != null) putCache(key, LyricsMatchResult(LyricsMatchStatus.MATCHED, selected.confidence, selected))
        selected
    }

    private suspend fun lrclibExact(query: LyricsQuery): ProviderOutcome {
        val errors = mutableListOf<Throwable>()
        var completed = false
        val params = linkedMapOf<String, Any?>(
            "track_name" to query.title,
            "artist_name" to query.artist,
            "album_name" to query.album,
            "duration" to query.durationSeconds?.roundToLong(),
        )
        for ((path, timeout) in listOf("/api/get-cached" to 5_000L, "/api/get" to 12_000L)) {
            try {
                val response = get(buildUrl(LRCLIB_BASE, path, params), timeout)
                if (response.code == 404) continue
                response.requireSuccess()
                completed = true
                val candidate = parseLrclib(jsonObject(response.text))
                if (candidate != null) return ProviderOutcome(true, listOf(candidate), errors)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                errors += error
            }
        }
        return ProviderOutcome(completed, errors = errors)
    }

    private suspend fun lrclibSearch(query: LyricsQuery): ProviderOutcome = provider {
        val response = get(
            buildUrl(
                LRCLIB_BASE,
                "/api/search",
                mapOf("track_name" to query.title, "artist_name" to query.artist, "album_name" to query.album),
            ),
            12_000L,
        ).requireSuccess()
        val rows = jsonArray(response.text)
        ProviderOutcome(true, rows.objects().mapNotNull(::parseLrclib))
    }

    private fun parseLrclib(row: JSONObject): LyricsCandidate? {
        val synced = cleanLineLyrics(row.string("syncedLyrics"))
        val plain = cleanLineLyrics(row.string("plainLyrics"))
        val instrumental = row.optBoolean("instrumental", false)
        val lyrics = synced.ifBlank { plain }
        if (!instrumental && lyrics.isBlank()) return null
        val rawId = row.opt("id")?.toString().orEmpty().ifBlank {
            listOf(row.string("trackName"), row.string("artistName"), row.opt("duration")).joinToString(":")
        }
        return LyricsCandidate(
            id = "lrclib:$rawId",
            title = firstText(row.opt("trackName"), row.opt("name"), row.opt("title")),
            artist = firstText(row.opt("artistName"), row.opt("artist")),
            album = firstText(row.opt("albumName"), row.opt("album")),
            durationSeconds = row.numberOrNull("duration"),
            instrumental = instrumental,
            lyrics = lyrics,
            format = lyricFormat(lyrics),
            source = "lrclib",
            database = "lrclib",
        )
    }

    private suspend fun neteaseSearch(query: LyricsQuery): ProviderOutcome = provider {
        val search = get(
            buildUrl(
                NETEASE_BASE,
                "/api/search/get/web",
                mapOf(
                    "s" to listOf(query.title, query.artist).filter(String::isNotBlank).joinToString(" "),
                    "type" to 1,
                    "offset" to 0,
                    "total" to "true",
                    "limit" to 6,
                ),
            ),
            10_000L,
            mapOf("Referer" to "https://music.163.com/"),
        ).requireSuccess()
        val rows = neteaseRows(jsonObject(search.text)).take(6)
        val (candidates, errors) = mapCandidates(rows) { row ->
            val metadata = neteaseMetadata(row)
            if (!metadata.platformId.matches(Regex("^\\d+$"))) return@mapCandidates null
            val bundle = fetchNeteaseLyrics(metadata.platformId)
            makeCandidate(
                id = "netease:${metadata.platformId}",
                metadata = metadata,
                bundle = bundle,
                source = "netease",
                platform = "netease",
                database = "netease",
            )
        }
        ProviderOutcome(true, candidates, errors)
    }

    private suspend fun fetchNeteaseLyrics(id: String): LyricsBundle {
        try {
            val response = get(
                buildUrl(
                    NETEASE_BASE,
                    "/api/song/lyric",
                    mapOf("os" to "pc", "id" to id, "lv" to -1, "kv" to -1, "tv" to -1, "yv" to -1, "rv" to -1),
                ),
                10_000L,
                mapOf("Referer" to "https://music.163.com/"),
            ).requireSuccess()
            val root = jsonObject(response.text)
            val word = root.optJSONObject("yrc")?.string("lyric").orEmpty()
            val direct = LyricsBundle(
                lyrics = firstText(
                    root.optJSONObject("lrc")?.opt("lyric"),
                    root.optJSONObject("klyric")?.opt("lyric"),
                    root.opt("lyric"),
                ),
                wordLyrics = word,
                wordFormat = if (word.isNotBlank()) "yrc" else "",
                translationLyrics = root.optJSONObject("tlyric")?.string("lyric").orEmpty(),
                romanLyrics = root.optJSONObject("romalrc")?.string("lyric").orEmpty(),
            ).cleaned()
            if (direct.hasLyrics) return direct
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
        }
        return LyricsBundle(lyrics = fetchMetingLyrics("netease", id)).cleaned()
    }

    private fun neteaseRows(root: JSONObject): List<JSONObject> {
        val result = root.optJSONObject("result") ?: root.optJSONObject("body")?.optJSONObject("result")
        return result?.optJSONArray("songs")?.objects().orEmpty()
    }

    private fun neteaseMetadata(row: JSONObject): RemoteMetadata {
        val albumValue = row.opt("album") ?: row.opt("al")
        val albumObject = albumValue as? JSONObject
        val duration = firstNumber(row.opt("duration"), row.opt("dt"))?.let { if (it > 10_000) it / 1_000 else it }
        val cover = firstText(albumObject?.opt("picUrl"), albumObject?.opt("blurPicUrl"), row.opt("picUrl"), row.opt("cover"))
            .replace(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
        return RemoteMetadata(
            platformId = firstText(row.opt("id"), row.opt("songId")),
            title = firstText(row.opt("name"), row.opt("title")),
            artist = firstText(row.opt("artists"), row.opt("ar"), row.opt("artist")),
            album = firstText(albumObject?.opt("name"), albumValue),
            durationSeconds = duration,
            coverUrl = safeUrl(cover),
        )
    }

    private suspend fun qqMusicSearch(query: LyricsQuery): ProviderOutcome = provider {
        val keyword = listOf(query.title, query.artist).filter(String::isNotBlank).joinToString(" ")
        val rows = suspendCatching { qqDesktopSearch(keyword) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: run {
            val response = get(
                buildUrl(
                    QQ_BASE,
                    "/soso/fcgi-bin/client_search_cp",
                    mapOf("p" to 1, "n" to 6, "w" to keyword, "format" to "json"),
                ),
                10_000L,
                QQ_HEADERS,
            ).requireSuccess()
            jsonObject(response.text).optJSONObject("data")
                ?.optJSONObject("song")?.optJSONArray("list")?.objects().orEmpty().take(6)
        }
        val (candidates, errors) = mapCandidates(rows) { row ->
            val metadata = qqMetadata(row)
            if (!metadata.platformId.matches(Regex("^[a-zA-Z0-9_-]+$"))) return@mapCandidates null
            makeCandidate(
                id = "qqmusic:${metadata.platformId}",
                metadata = metadata,
                bundle = fetchQqLyrics(metadata),
                source = "qqmusic",
                platform = "qqmusic",
                database = "qqmusic",
            )
        }
        ProviderOutcome(true, candidates, errors)
    }

    private suspend fun qqDesktopSearch(keyword: String): List<JSONObject> {
        val body = JSONObject().put(
            "req_1",
            JSONObject()
                .put("method", "DoSearchForQQMusicDesktop")
                .put("module", "music.search.SearchCgiService")
                .put("param", JSONObject().put("num_per_page", 8).put("page_num", 1).put("query", keyword).put("search_type", 0)),
        ).toString()
        val response = postJson(QQ_MUSICU, body, 10_000L, QQ_HEADERS).requireSuccess()
        val list = jsonObject(response.text).optJSONObject("req_1")?.optJSONObject("data")?.optJSONObject("body")
            ?.optJSONObject("song")?.optJSONArray("list")?.objects().orEmpty()
        return list.take(6).map { song ->
            val album = song.optJSONObject("album")
            JSONObject()
                .put("songmid", song.optString("mid"))
                .put("songid", song.optLong("id"))
                .put("songname", song.optString("title").ifBlank { song.optString("name") })
                .put("singer", song.optJSONArray("singer") ?: JSONArray())
                .put("albummid", album?.optString("mid").orEmpty())
                .put("albumname", album?.optString("title")?.ifBlank { album.optString("name") }.orEmpty())
                .put("interval", song.optInt("interval"))
        }
    }

    private suspend fun fetchQqDownloadLyrics(songId: Long): LyricsBundle {
        val response = postForm(
            "https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg",
            mapOf("version" to "15", "miniversion" to "82", "lrctype" to "4", "musicid" to songId.toString()),
            10_000L,
            QQ_HEADERS,
        ).requireSuccess()
        val text = response.text.replace("<!--", "").replace("-->", "")
        fun section(tag: String): String? = Regex("<$tag(?:\\s[^>]*)?>(?:<!\\[CDATA\\[)?([\\s\\S]*?)(?:]]>)?</$tag>").find(text)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> runCatching { decryptQrc(raw) }.getOrNull() ?: raw.takeIf { lyricFormat(it) == "lrc-line" } }
        val main = section("content") ?: return LyricsBundle()
        val bundle = parseQrc(main)
        val translation = section("contentts")?.let(::qqAuxiliary).orEmpty()
        val roman = section("contentroma")?.let(::qqAuxiliary).orEmpty()
        return bundle.copy(translationLyrics = translation, romanLyrics = roman).cleaned()
    }

    private fun qqMetadata(row: JSONObject): RemoteMetadata {
        val album = row.opt("album") as? JSONObject
        val albumMid = firstText(row.opt("albummid"), album?.opt("mid"), album?.opt("albumMid"))
        val cover = if (albumMid.matches(Regex("^[a-zA-Z0-9_-]{4,128}$"))) {
            "https://y.gtimg.cn/music/photo_new/T002R500x500M000${albumMid}.jpg?max_age=2592000"
        } else firstText(row.opt("pic"), row.opt("cover"), album?.opt("picUrl"))
        return RemoteMetadata(
            platformId = firstText(row.opt("songmid"), row.opt("mid"), row.opt("songid")),
            secondaryId = firstText(row.opt("songid"), row.opt("id")),
            title = firstText(row.opt("songname"), row.opt("name"), row.opt("title")),
            artist = firstText(row.opt("singer"), row.opt("artist")),
            album = firstText(row.opt("albumname"), album),
            durationSeconds = firstNumber(row.opt("interval")),
            coverUrl = safeUrl(cover),
        )
    }

    private suspend fun fetchQqLyrics(metadata: RemoteMetadata): LyricsBundle {
        if (metadata.secondaryId.toLongOrNull() != null ||
            metadata.platformId.toLongOrNull() != null ||
            metadata.platformId.matches(Regex("^[a-zA-Z0-9_-]{4,128}$"))
        ) {
            suspendCatching { fetchQrcLyrics(metadata) }.getOrNull()?.takeIf(LyricsBundle::hasLyrics)?.let { return it }
        }
        (metadata.secondaryId.toLongOrNull() ?: metadata.platformId.toLongOrNull())?.let { id ->
            suspendCatching { fetchQqDownloadLyrics(id) }.getOrNull()?.takeIf(LyricsBundle::hasLyrics)?.let { return it }
        }
        try {
            val response = get(
                buildUrl(
                    QQ_BASE,
                    "/lyric/fcgi-bin/fcg_query_lyric_new.fcg",
                    mapOf("songmid" to metadata.platformId, "format" to "json", "nobase64" to 1),
                ),
                10_000L,
                QQ_HEADERS,
            ).requireSuccess()
            val value = jsonObject(response.text).string("lyric")
            val lyric = decodePossibleBase64(value)
            if (lyric.isNotBlank()) return LyricsBundle(lyrics = lyric).cleaned()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
        }
        return LyricsBundle(lyrics = fetchMetingLyrics("tencent", metadata.platformId)).cleaned()
    }

    private suspend fun fetchQrcLyrics(metadata: RemoteMetadata): LyricsBundle {
        val songId = metadata.secondaryId.toLongOrNull() ?: metadata.platformId.toLongOrNull()
        val songMid = metadata.platformId.takeIf {
            songId == null && it.matches(Regex("^[a-zA-Z0-9_-]{4,128}$"))
        }
        if (songId == null && songMid == null) return LyricsBundle()
        val session = ensureQqSession()
        val params = JSONObject()
            .put("albumName", base64(metadata.album))
            .put("crypt", 1)
            .put("ct", 19)
            .put("cv", 2111)
            .put("interval", (metadata.durationSeconds ?: 0.0).roundToLong())
            .put("lrc_t", 0)
            .put("qrc", 1)
            .put("qrc_t", 0)
            .put("roma", 1)
            .put("roma_t", 0)
            .put("singerName", base64(metadata.artist))
            .put("songName", base64(metadata.title))
            .put("trans", 1)
            .put("trans_t", 0)
            .put("type", 0)
        if (songId != null) params.put("songID", songId) else params.put("songMID", songMid)
        val data = postMusicu(
            session.comm,
            "GetPlayLyricInfo",
            "music.musichallSong.PlayLyricInfo",
            params,
        )
        if (data.string("lyric").isBlank()) return LyricsBundle()
        val main = parseQrc(decryptQrc(data.string("lyric")))
        return main.copy(
            translationLyrics = data.string("trans").takeIf(String::isNotBlank)
                ?.let { runCatching { qqAuxiliary(decryptQrc(it)) }.getOrDefault("") }.orEmpty(),
            romanLyrics = data.string("roma").takeIf(String::isNotBlank)
                ?.let { runCatching { qqAuxiliary(decryptQrc(it)) }.getOrDefault("") }.orEmpty(),
        ).cleaned()
    }

    private suspend fun ensureQqSession(): QqSession = qqSessionMutex.withLock {
        qqSession?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let { return@withLock it }
        val base = JSONObject()
            .put("ct", 11)
            .put("cv", "1003006")
            .put("v", "1003006")
            .put("os_ver", "15")
            .put("phonetype", "24122RKC7C")
            .put("rom", "Redmi/miro/miro:15/AE3A.240806.005/OS2.0.105.0.VOMCNXM:user/release-keys")
            .put("tmeAppID", "qqmusiclight")
            .put("nettype", "NETWORK_WIFI")
            .put("udid", "0")
        val session = postMusicu(
            base,
            "GetSession",
            "music.getSession.session",
            JSONObject().put("caller", 0).put("uid", "0").put("vkey", 0),
        ).optJSONObject("session") ?: error("QQ 音乐会话不可用")
        val uid = session.string("uid")
        val sid = session.string("sid")
        require(uid.isNotBlank() && sid.isNotBlank()) { "QQ 音乐会话不可用" }
        QqSession(
            comm = JSONObject(base.toString())
                .put("uid", uid)
                .put("sid", sid)
                .put("userip", session.string("userip")),
            expiresAt = System.currentTimeMillis() + 30 * 60_000L,
        ).also { qqSession = it }
    }

    private suspend fun postMusicu(comm: JSONObject, method: String, module: String, params: JSONObject): JSONObject {
        val body = JSONObject()
            .put("comm", comm)
            .put(
                "request",
                JSONObject().put("method", method).put("module", module).put("param", params),
            )
            .toString()
        val response = postJson(
            QQ_MUSICU,
            body,
            10_000L,
            mapOf("User-Agent" to "okhttp/3.14.9", "Cookie" to "tmeLoginType=-1;"),
        ).requireSuccess()
        val root = jsonObject(response.text)
        val request = root.optJSONObject("request")
        require(root.optInt("code", -1) == 0 && request?.optInt("code", -1) == 0) { "QQ 音乐接口返回异常" }
        return request?.optJSONObject("data") ?: error("QQ 音乐接口缺少数据")
    }

    private suspend fun kugouSearch(query: LyricsQuery): ProviderOutcome = provider {
        val response = get(
            buildUrl(
                KUGOU_BASE,
                "/api/v3/search/song",
                mapOf(
                    "format" to "json",
                    "keyword" to listOf(query.title, query.artist).filter(String::isNotBlank).joinToString(" "),
                    "page" to 1,
                    "pagesize" to 8,
                    "showtype" to 1,
                ),
            ),
            10_000L,
        ).requireSuccess()
        val info = jsonObject(response.text).optJSONObject("data")?.optJSONArray("info")?.objects().orEmpty()
        val rows = buildList {
            val seen = mutableSetOf<String>()
            for (row in info) {
                val expanded = listOf(row) + row.optJSONArray("group")?.objects().orEmpty()
                for (item in expanded) {
                    val hash = item.string("hash").lowercase(Locale.ROOT)
                    if (hash.matches(Regex("^[a-f0-9]{32}$")) && seen.add(hash)) add(item)
                    if (size >= 8) break
                }
                if (size >= 8) break
            }
        }
        val (candidates, errors) = mapCandidates(rows) { row ->
            val metadata = kugouMetadata(row)
            makeCandidate(
                id = "kugou:${metadata.platformId}",
                metadata = metadata,
                bundle = fetchKugouLyrics(metadata, row),
                source = "kugou",
                platform = "kugou",
                database = "kugou",
            )
        }
        ProviderOutcome(true, candidates, errors)
    }

    private fun kugouMetadata(row: JSONObject): RemoteMetadata {
        var cover = firstText(
            row.optJSONObject("trans_param")?.opt("union_cover"),
            row.opt("album_cover"), row.opt("img"), row.opt("cover"),
        ).replace("{size}", "500", ignoreCase = true)
        cover = cover.replace(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
        val rawDuration = firstNumber(row.opt("duration"))
        return RemoteMetadata(
            platformId = row.string("hash").lowercase(Locale.ROOT),
            title = firstText(row.opt("songname"), row.opt("songname_original"), row.opt("title")),
            artist = firstText(row.opt("singername"), row.opt("artist")),
            album = firstText(row.opt("album_name"), row.opt("album")),
            durationSeconds = rawDuration?.let { if (it > 10_000) it / 1_000 else it },
            coverUrl = safeUrl(cover),
        )
    }

    private suspend fun fetchKugouLyrics(metadata: RemoteMetadata, row: JSONObject): LyricsBundle {
        try {
            val filename = firstText(
                row.opt("filename"),
                listOf(metadata.artist, metadata.title).filter(String::isNotBlank).joinToString(" - "),
                metadata.title,
            )
            val search = get(
                buildUrl(
                    KUGOU_LYRICS_BASE,
                    "/search",
                    mapOf(
                        "ver" to 1,
                        "man" to "yes",
                        "client" to "pc",
                        "keyword" to filename,
                        "duration" to metadata.durationSeconds?.times(1_000)?.roundToLong(),
                        "hash" to metadata.platformId,
                    ),
                ),
                10_000L,
            ).requireSuccess()
            val rows = jsonObject(search.text).optJSONArray("candidates")?.objects().orEmpty()
                .filter {
                    firstText(it.opt("download_id"), it.opt("id")).matches(Regex("^\\d+$")) &&
                        it.string("accesskey").matches(Regex("^[a-f0-9]{16,64}$", RegexOption.IGNORE_CASE))
                }
                .sortedBy {
                    metadata.durationSeconds?.let { duration ->
                        abs((firstNumber(it.opt("duration")) ?: duration * 1_000) - duration * 1_000)
                    } ?: 0.0
                }
                .take(2)
            for (candidate in rows) {
                val download = get(
                    buildUrl(
                        KUGOU_LYRICS_BASE,
                        "/download",
                        mapOf(
                            "ver" to 1,
                            "client" to "pc",
                            "id" to firstText(candidate.opt("download_id"), candidate.opt("id")),
                            "accesskey" to candidate.string("accesskey"),
                            "fmt" to "krc",
                            "charset" to "utf8",
                        ),
                    ),
                    10_000L,
                ).requireSuccess()
                val content = jsonObject(download.text).string("content")
                if (content.length > 2 * 1024 * 1024) continue
                val bundle = parseKrc(decryptKrc(Base64.decode(content, Base64.DEFAULT))).cleaned()
                if (bundle.hasLyrics) return bundle
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
        }
        return LyricsBundle(lyrics = fetchMetingLyrics("kugou", metadata.platformId)).cleaned()
    }

    private suspend fun metingSearch(query: LyricsQuery): ProviderOutcome {
        val results = supervisorScope {
            listOf("netease", "tencent", "kugou").map { server ->
                async { metingSearchServer(query, server) }
            }.awaitAll()
        }
        return ProviderOutcome(
            completed = results.any(ProviderOutcome::completed),
            candidates = results.flatMap(ProviderOutcome::candidates),
            errors = results.flatMap(ProviderOutcome::errors),
        )
    }

    private suspend fun metingSearchServer(query: LyricsQuery, server: String): ProviderOutcome = provider {
        val url = buildUrl(
            METING_BASE,
            "",
            buildMap {
                if (server != "netease") put("server", server)
                put("type", "search")
                put("id", listOf(query.title, query.artist).filter(String::isNotBlank).joinToString(" "))
            },
        )
        val response = get(url, 12_000L).requireSuccess()
        val rows = jsonArray(response.text).objects().take(4)
        val (candidates, errors) = mapCandidates(rows) { row ->
            val id = extractMetingId(firstText(row.opt("lrc"), row.opt("url")), server)
            if (id.isBlank()) return@mapCandidates null
            val platform = if (server == "tencent") "qqmusic" else server
            makeCandidate(
                id = "meting:$platform:$id",
                metadata = RemoteMetadata(
                    platformId = id,
                    title = firstText(row.opt("name"), row.opt("title")),
                    artist = firstText(row.opt("artist"), row.opt("artists")),
                    album = firstText(row.opt("album")),
                    durationSeconds = null,
                    coverUrl = safeUrl(firstText(row.opt("pic"), row.opt("cover"), row.opt("artwork"), row.opt("albumPic"))),
                ),
                bundle = LyricsBundle(lyrics = fetchMetingLyrics(server, id)),
                source = "meting",
                platform = platform,
                database = platform,
            )
        }
        ProviderOutcome(true, candidates, errors)
    }

    private suspend fun fetchMetingLyrics(server: String, id: String): String {
        if (!id.matches(Regex("^[a-zA-Z0-9_-]{1,128}$"))) return ""
        val url = buildUrl(
            METING_BASE,
            "",
            buildMap {
                if (server != "netease") put("server", server)
                put("type", "lrc")
                put("id", id)
            },
        )
        return cleanLineLyrics(get(url, 12_000L).requireSuccess().text)
    }

    private suspend fun wwoyunSearch(query: LyricsQuery): ProviderOutcome = provider {
        val search = get(
            buildUrl(
                WWOYUN_SEARCH,
                "",
                mapOf(
                    "keywords" to listOf(query.title, query.artist).filter(String::isNotBlank).joinToString(" "),
                    "limit" to 5,
                ),
            ),
            4_000L,
        ).requireSuccess()
        val rows = neteaseRows(jsonObject(search.text)).take(5)
        val (candidates, errors) = mapCandidates(rows) { row ->
            val metadata = neteaseMetadata(row)
            if (!metadata.platformId.matches(Regex("^\\d+$"))) return@mapCandidates null
            val lyricResponse = get(
                buildUrl(WWOYUN_BASE, "/lyric", mapOf("id" to metadata.platformId)),
                4_000L,
            ).requireSuccess()
            val root = jsonObject(lyricResponse.text)
            val lyrics = firstText(
                root.optJSONObject("lrc")?.opt("lyric"),
                root.optJSONObject("data")?.optJSONObject("lrc")?.opt("lyric"),
                root.opt("lyric"), root.opt("lyrics"),
            )
            makeCandidate(
                id = "wwoyun:netease:${metadata.platformId}",
                metadata = metadata,
                bundle = LyricsBundle(lyrics = lyrics),
                source = "wwoyun",
                platform = "netease",
                database = "netease",
            )
        }
        ProviderOutcome(true, candidates, errors)
    }

    private suspend fun lrcApiSearch(query: LyricsQuery): ProviderOutcome = provider {
        val response = get(
            buildUrl(
                LRC_API,
                "",
                mapOf("title" to query.title, "artist" to query.artist, "album" to query.album),
            ),
            15_000L,
        ).requireSuccess()
        val value = jsonValue(response.text)
        val rows = when (value) {
            is JSONArray -> value.objects()
            is JSONObject -> sequenceOf("data", "result", "results", "songs", "list")
                .mapNotNull { value.optJSONArray(it) }.firstOrNull()?.objects().orEmpty()
            else -> emptyList()
        }
        val candidates = rows.mapIndexedNotNull { index, row ->
            val synced = cleanLineLyrics(row.string("syncedLyrics"))
            val lyrics = synced.ifBlank {
                cleanLineLyrics(firstText(row.opt("lyrics"), row.opt("lrc")))
            }
            val instrumental = row.optBoolean("instrumental", false)
            if (!instrumental && lyrics.isBlank()) return@mapIndexedNotNull null
            val title = firstText(row.opt("title"), row.opt("name"), row.opt("song"), row.opt("trackName"))
            val artist = firstText(row.opt("artist"), row.opt("artists"), row.opt("singer"), row.opt("artistName"))
            val id = firstText(row.opt("id"), row.opt("songmid"), row.opt("mid")).ifBlank { "$title:$artist:$index" }
            LyricsCandidate(
                id = "lrcapi:$id",
                title = title,
                artist = artist,
                album = firstText(row.opt("album"), row.opt("albumName")),
                durationSeconds = firstNumber(row.opt("duration"), row.opt("interval"), row.opt("songDuration")),
                instrumental = instrumental,
                lyrics = lyrics,
                format = lyricFormat(lyrics),
                source = "lrcapi",
                database = "lrcapi",
                coverUrl = safeUrl(firstText(row.opt("coverUrl"), row.opt("cover"), row.opt("picUrl"), row.opt("pic"), row.opt("artwork"))),
            )
        }
        ProviderOutcome(true, candidates)
    }

    private suspend fun amllCatalogSearch(query: LyricsQuery): ProviderOutcome = supervisorScope {
        if (query.title.isBlank()) return@supervisorScope ProviderOutcome(completed = true)
        val responses = listOf(
            async { suspendCatching { searchBikonooAmllCatalog(query.title) } },
            async { suspendCatching { searchOfficialAmllCatalog(query.title) } },
        ).awaitAll()
        val hits = responses.flatMap { it.getOrNull().orEmpty() }
        val ranked = rankAmllSearchHits(query, hits, minimumScore = 0.0, limit = 40)
        val candidates = ranked.map { match ->
            LyricsCandidate(
                id = "amll:file:${match.hit.id}",
                title = match.title,
                artist = match.artist,
                album = match.album,
                durationSeconds = null,
                source = "amll-ttml",
                platform = "amll",
                platformId = match.hit.lookup,
                database = "amll-ttml",
                confidence = match.score,
                matchConfidence = match.score,
            )
        }
        ProviderOutcome(
            completed = responses.any { it.isSuccess },
            candidates = candidates,
            errors = responses.mapNotNull { it.exceptionOrNull() },
        )
    }

    private suspend fun searchBikonooAmllCatalog(title: String): List<AmllSearchHit> {
        val response = postJson(
            "$AMLL_COMMUNITY/api/search-lyrics",
            JSONObject().put("query", title).put("type", "all").toString(),
            8_000L,
        ).requireSuccess()
        return jsonArray(response.text).objects().mapNotNull { row ->
            val platform = row.stringList("platform").firstOrNull().orEmpty().lowercase(Locale.ROOT)
            val rawFilename = row.stringList("file").firstOrNull().orEmpty()
                .ifBlank { row.stringList("id").firstOrNull().orEmpty() }
            val filename = if (rawFilename.endsWith(".ttml", ignoreCase = true)) rawFilename else "$rawFilename.ttml"
            if (!isSafeAmllPath(platform, filename)) return@mapNotNull null
            AmllSearchHit(
                id = filename,
                titles = row.stringList("titles") + row.stringList("title"),
                artists = row.stringList("artists") + row.stringList("artist"),
                albums = row.stringList("albums") + row.stringList("album"),
                lookup = "mirror:$platform:$filename",
            )
        }
    }

    private suspend fun searchOfficialAmllCatalog(title: String): List<AmllSearchHit> {
        val response = get(
            buildUrl(
                AMLL_API_BASE,
                "/v1/lyrics/search",
                mapOf("musicName" to title, "pageSize" to 50),
            ),
            8_000L,
        ).requireSuccess()
        val rows = jsonObject(response.text).optJSONObject("data")?.optJSONArray("items")?.objects().orEmpty()
        return rows.mapNotNull { row ->
            val id = row.opt("id")?.toString()?.takeIf { value ->
                value.toLongOrNull()?.let { it in 0..9_007_199_254_740_991L } == true
            } ?: return@mapNotNull null
            val filename = row.string("filename")
            AmllSearchHit(
                id = filename.ifBlank { "api:$id" },
                titles = row.stringList("musicNames"),
                artists = row.stringList("artistNames"),
                albums = row.stringList("albumNames"),
                lookup = "api:$id",
            )
        }
    }

    private fun JSONObject.stringList(name: String): List<String> = when (val value = opt(name)) {
        is JSONArray -> value.strings()
        is String -> listOf(value.trim()).filter { it.isNotBlank() }
        else -> emptyList()
    }

    private fun isSafeAmllPath(platform: String, filename: String): Boolean =
        platform.matches(Regex("^[a-z0-9-]{2,40}$")) &&
            filename.matches(Regex("^[a-zA-Z0-9_-]{1,180}\\.ttml$"))

    suspend fun downloadAmllCandidate(candidate: LyricsCandidate): LyricsCandidate = withContext(Dispatchers.IO) {
        require(candidate.source == "amll-ttml") { "不是 AMLL 歌词候选" }
        val lookup = candidate.platformId
        val ttml = when {
            lookup.startsWith("mirror:") -> {
                val parts = lookup.removePrefix("mirror:").split(':', limit = 2)
                require(parts.size == 2 && isSafeAmllPath(parts[0], parts[1])) { "AMLL 歌词地址无效" }
                fetchAmllMirrorTtml(parts[0], parts[1])
            }
            lookup.startsWith("api:") -> {
                val id = lookup.removePrefix("api:")
                require(id.toLongOrNull()?.let { it in 0..9_007_199_254_740_991L } == true) {
                    "AMLL 歌词编号无效"
                }
                val data = jsonObject(get(
                    buildUrl(AMLL_API_BASE, "/v1/lyrics/get", mapOf("id" to id)),
                    10_000L,
                ).requireSuccess().text).optJSONObject("data") ?: error("AMLL 歌词响应无效")
                data.string("lyrics")
            }
            else -> error("AMLL 歌词来源无效")
        }
        val document = LyricsParser.parse(ttml)
        require(document.isSynchronized && document.lines.any { it.words.isNotEmpty() }) {
            "这份 AMLL 歌词没有可用的逐字时间"
        }
        val hydrated = makeCandidate(
            id = candidate.id,
            metadata = RemoteMetadata(
                platformId = lookup,
                title = candidate.title,
                artist = candidate.artist,
                album = candidate.album,
                durationSeconds = candidate.durationSeconds,
                coverUrl = candidate.coverUrl,
            ),
            bundle = LyricsBundle(
                lyrics = document.toLineLrc(),
                wordLyrics = ttml,
                wordFormat = "ttml",
            ),
            source = "amll-ttml",
            platform = "amll",
            database = "amll-ttml",
        ) ?: error("这份 AMLL 歌词无法使用")
        hydrated.copy(
            confidence = candidate.confidence,
            matchConfidence = candidate.matchConfidence,
            recommendationScore = candidate.recommendationScore,
        )
    }

    private suspend fun fetchAmllMirrorTtml(platform: String, filename: String): String {
        val folder = if (platform.endsWith("-lyrics")) platform else "$platform-lyrics"
        val urls = listOf(
            "$AMLL_BASE/$platform/${filename.removeSuffix(".ttml")}",
            "$AMLL_COMMUNITY/$folder/$filename",
            "$AMLL_DIMETA/api/db/$folder/$filename",
            "$AMLL_GITHUB_BASE/$folder/$filename",
        )
        var lastError: Throwable? = null
        for (url in urls) {
            try {
                val response = get(url, 7_000L)
                if (response.code !in 200..299) continue
                val ttml = response.text.trim()
                if (LyricsParser.parse(ttml).lines.any { it.words.isNotEmpty() }) return ttml
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                lastError = error
            }
        }
        try {
            val data = jsonObject(get(
                buildUrl(AMLL_API_BASE, "/v1/lyrics/get", mapOf("filename" to filename)),
                10_000L,
            ).requireSuccess().text).optJSONObject("data")
            val ttml = data?.string("lyrics").orEmpty()
            if (LyricsParser.parse(ttml).lines.any { it.words.isNotEmpty() }) return ttml
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            lastError = error
        }
        throw IllegalStateException("AMLL 镜像未能下载这份逐字歌词", lastError)
    }

    private suspend fun amllSearch(query: LyricsQuery): ProviderOutcome = provider {
        if (query.title.isBlank()) return@provider ProviderOutcome(completed = true)
        val response = get(
            buildUrl(
                AMLL_API_BASE,
                "/v1/lyrics/search",
                mapOf("musicName" to query.title, "artistName" to query.artist, "pageSize" to 20),
            ),
            10_000L,
        ).requireSuccess()
        val rows = jsonObject(response.text).optJSONObject("data")?.optJSONArray("items")?.objects().orEmpty()
        val ranked = rankAmllSearchHits(query, rows.mapNotNull { row ->
            val id = row.opt("id")?.toString()?.takeIf { value ->
                value.toLongOrNull()?.let { it in 0..9_007_199_254_740_991L } == true
            } ?: return@mapNotNull null
            AmllSearchHit(
                id = id,
                titles = row.optJSONArray("musicNames")?.strings().orEmpty(),
                artists = row.optJSONArray("artistNames")?.strings().orEmpty(),
                albums = row.optJSONArray("albumNames")?.strings().orEmpty(),
            )
        }, minimumScore = 0.0, limit = 8)
        val (candidates, errors) = mapCandidates(ranked) { hit ->
            val payload = get(
                buildUrl(AMLL_API_BASE, "/v1/lyrics/get", mapOf("id" to hit.hit.id)),
                10_000L,
            ).requireSuccess()
            val data = jsonObject(payload.text).optJSONObject("data") ?: return@mapCandidates null
            if (data.opt("id")?.toString() != hit.hit.id) return@mapCandidates null
            val ttml = data.string("lyrics")
            val document = LyricsParser.parse(ttml)
            if (!document.isSynchronized || document.lines.none { it.words.isNotEmpty() }) {
                return@mapCandidates null
            }
            makeCandidate(
                id = "amll:id:${hit.hit.id}",
                metadata = RemoteMetadata(
                    platformId = hit.hit.id,
                    title = hit.title,
                    artist = hit.artist,
                    album = hit.album,
                    durationSeconds = null,
                ),
                bundle = LyricsBundle(
                    lyrics = document.toLineLrc(),
                    wordLyrics = ttml,
                    wordFormat = "ttml",
                ),
                source = "amll-ttml",
                platform = "amll",
                database = "amll-ttml",
            )
        }
        ProviderOutcome(
            completed = ranked.isEmpty() || candidates.isNotEmpty() || errors.isEmpty(),
            candidates = candidates,
            errors = errors,
        )
    }

    private suspend fun amllEnrich(query: LyricsQuery, candidates: List<LyricsCandidate>): ProviderOutcome {
        val ids = candidates.asSequence()
            .filter { it.platform == "netease" && it.platformId.matches(Regex("^\\d+$")) }
            .map { it to LyricsMatcher.score(query, it) }
            .filter { it.second >= 0.6 }
            .sortedByDescending { it.second }
            .distinctBy { it.first.platformId }
            .take(6)
            .map { it.first }
            .toList()
        if (ids.isEmpty()) return ProviderOutcome(completed = true)
        val (enriched, errors) = mapCandidates(ids) { original ->
            val bundle = fetchAmllLyrics(original.platformId)
            makeCandidate(
                id = "amll:ncm:${original.platformId}",
                metadata = RemoteMetadata(
                    platformId = original.platformId,
                    title = original.title,
                    artist = original.artist,
                    album = original.album,
                    durationSeconds = original.durationSeconds,
                    coverUrl = original.coverUrl,
                ),
                bundle = bundle,
                source = "amll-ttml",
                platform = "netease",
                database = "amll-ttml",
            )
        }
        return ProviderOutcome(true, enriched, errors)
    }

    private suspend fun fetchAmllLyrics(id: String): LyricsBundle {
        var lineFallback = LyricsBundle()
        for (url in listOf("$AMLL_BASE/ncm/$id", "$AMLL_COMMUNITY/ncm-lyrics/${id}.ttml", "$AMLL_RAW/${id}.ttml")) {
            try {
                val response = get(url, 6_000L)
                if (response.code == 404) continue
                response.requireSuccess()
                val ttml = response.text.trim()
                val document = LyricsParser.parse(ttml)
                if (document.isSynchronized && document.lines.isNotEmpty()) {
                    val timed = document.lines.any { it.words.isNotEmpty() }
                    val bundle = LyricsBundle(
                        lyrics = document.toLineLrc(),
                        wordLyrics = if (timed) ttml else "",
                        wordFormat = if (timed) "ttml" else "",
                    ).cleaned()
                    if (timed) return bundle
                    if (!lineFallback.hasLyrics) lineFallback = bundle
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
            }
        }
        return lineFallback
    }

    private fun makeCandidate(
        id: String,
        metadata: RemoteMetadata,
        bundle: LyricsBundle,
        source: String,
        platform: String,
        database: String,
    ): LyricsCandidate? {
        val cleaned = bundle.cleaned()
        val timedWords = cleaned.wordLyrics.takeIf { raw ->
            LyricsParser.parse(raw).lines.any { line -> line.words.isNotEmpty() }
        }.orEmpty()
        if (!cleaned.instrumental && cleaned.lyrics.isBlank() && timedWords.isBlank()) return null
        return LyricsCandidate(
            id = id,
            title = metadata.title,
            artist = metadata.artist,
            album = metadata.album,
            durationSeconds = metadata.durationSeconds,
            instrumental = cleaned.instrumental,
            lyrics = cleaned.lyrics,
            format = lyricFormat(cleaned.lyrics),
            source = source,
            platform = platform,
            platformId = metadata.platformId,
            wordLyrics = timedWords,
            wordFormat = if (timedWords.isNotEmpty()) cleaned.wordFormat else "",
            translationLyrics = cleaned.translationLyrics,
            romanLyrics = cleaned.romanLyrics,
            database = database,
            coverUrl = metadata.coverUrl,
        )
    }

    private suspend fun appleLyricsSearch(query: LyricsQuery): ProviderOutcome = provider {
        val apple = appleMusic
        if (apple == null || !apple.settings.value.hasUserToken) return@provider ProviderOutcome(completed = true)
        val term = listOf(query.title, query.artist).filter(String::isNotBlank).joinToString(" ")
        val region = apple.account()?.storefront
        val found = (region?.let { apple.searchSongs(term, 6, only = it) }.orEmpty())
            .ifEmpty { apple.searchSongs(term, 6) }
        val probe = { song: AmSong ->
            LyricsCandidate(
                id = "applemusic:${song.id}",
                title = song.name,
                artist = song.artistName,
                album = song.albumName,
                durationSeconds = song.durationMs.takeIf { it > 0 }?.div(1000.0),
                source = "applemusic",
            )
        }
        val songs = found.sortedByDescending { LyricsMatcher.score(query, probe(it)) }.take(3)
        val (candidates, errors) = mapCandidates(songs) { song ->
            val ttml = apple.lyricsTtml(song) ?: return@mapCandidates null
            val parsed = LyricsParser.parse(ttml)
            val bundle = if (parsed.lines.any { it.words.isNotEmpty() }) {
                LyricsBundle(wordLyrics = ttml, wordFormat = "ttml")
            } else {
                LyricsBundle(lyrics = parsed.lines.joinToString("\n") { line -> "[${lrcTime(line.startMs)}]${line.text}" })
            }
            makeCandidate(
                id = "applemusic:${song.id}",
                metadata = RemoteMetadata(
                    platformId = song.id,
                    title = song.name,
                    artist = song.artistName,
                    album = song.albumName,
                    durationSeconds = song.durationMs.takeIf { it > 0 }?.div(1000.0),
                    coverUrl = AppleMusicClient.artwork(song.artworkTemplate, 600).orEmpty(),
                ),
                bundle = bundle,
                source = "applemusic",
                platform = "applemusic",
                database = "applemusic",
            )
        }
        ProviderOutcome(true, candidates, errors)
    }

    private fun lrcTime(ms: Long): String {
        val total = ms.coerceAtLeast(0L)
        return String.format(Locale.ROOT, "%02d:%02d.%02d", total / 60_000, (total / 1_000) % 60, (total % 1_000) / 10)
    }

    private suspend fun <T> mapCandidates(
        rows: List<T>,
        transform: suspend (T) -> LyricsCandidate?,
    ): Pair<List<LyricsCandidate>, List<Throwable>> = supervisorScope {
        val results = rows.map { row -> async { suspendCatching { transform(row) } } }.awaitAll()
        results.mapNotNull { it.getOrNull() } to results.mapNotNull { it.exceptionOrNull() }
    }

    private suspend fun provider(block: suspend () -> ProviderOutcome): ProviderOutcome = try {
        block()
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        ProviderOutcome(completed = false, errors = listOf(error))
    }

    private suspend fun <T> suspendCatching(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        Result.failure(error)
    }

    private suspend fun get(
        url: String,
        timeoutMs: Long,
        headers: Map<String, String> = emptyMap(),
    ): HttpPayload = execute(
        Request.Builder().url(url).get().apply {
            header("User-Agent", USER_AGENT)
            header("Accept", "application/json, text/plain, application/xml, text/xml, */*")
            headers.forEach { (name, value) -> header(name, value) }
        }.build(),
        timeoutMs,
    )

    private suspend fun postJson(
        url: String,
        json: String,
        timeoutMs: Long,
        headers: Map<String, String> = emptyMap(),
    ): HttpPayload = execute(
        Request.Builder().url(url)
            .post(json.toRequestBody(JSON_MEDIA_TYPE))
            .apply {
                header("User-Agent", USER_AGENT)
                header("Accept", "application/json")
                headers.forEach { (name, value) -> header(name, value) }
            }
            .build(),
        timeoutMs,
    )

    private suspend fun postForm(
        url: String,
        form: Map<String, String>,
        timeoutMs: Long,
        headers: Map<String, String> = emptyMap(),
    ): HttpPayload = execute(
        Request.Builder().url(url)
            .post(okhttp3.FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build())
            .apply {
                header("User-Agent", USER_AGENT)
                headers.forEach { (name, value) -> header(name, value) }
            }
            .build(),
        timeoutMs,
    )

    private suspend fun execute(request: Request, timeoutMs: Long): HttpPayload = requestGate.withPermit {
        withContext(Dispatchers.IO) {
            val call = client.newBuilder()
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()
                .newCall(request)
            call.execute().use body@{ response ->
                val body = response.body ?: return@body HttpPayload(response.code, "")
                val declared = body.contentLength()
                require(declared < 0 || declared <= MAX_HTTP_BYTES) { "歌词接口响应过大" }
                val output = ByteArrayOutputStream()
                body.byteStream().use { input ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= MAX_HTTP_BYTES) { "歌词接口响应过大" }
                        output.write(buffer, 0, count)
                    }
                }
                HttpPayload(response.code, output.toByteArray().toString(StandardCharsets.UTF_8))
            }
        }
    }

    private fun buildUrl(base: String, path: String, params: Map<String, Any?>): String {
        val absolute = if (path.isBlank()) base else "${base.trimEnd('/')}/${path.trimStart('/')}"
        val builder = absolute.toHttpUrlOrNull()?.newBuilder() ?: error("无效歌词接口地址")
        params.forEach { (name, value) ->
            if (value != null && value.toString().isNotBlank()) builder.addQueryParameter(name, value.toString())
        }
        return builder.build().toString()
    }

    private fun jsonValue(raw: String): Any {
        val text = raw.trim().removePrefix("\uFEFF")
        return runCatching { JSONTokener(text).nextValue() }.getOrElse {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start >= 0 && end > start) JSONTokener(text.substring(start, end + 1)).nextValue() else throw it
        }
    }

    private fun jsonObject(raw: String): JSONObject = jsonValue(raw) as? JSONObject ?: error("歌词接口响应格式错误")
    private fun jsonArray(raw: String): JSONArray = jsonValue(raw) as? JSONArray ?: error("歌词接口响应格式错误")

    private fun cleanLineLyrics(raw: String): String {
        val value = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').trim()
        if (value.isBlank() || value.toByteArray().size > MAX_LINE_LYRICS_BYTES) return ""
        if (Regex("^\\s*<\\?xml|<tt(?:\\s|>)|<p\\s+begin=", RegexOption.IGNORE_CASE).containsMatchIn(value)) return ""
        return value
    }

    private fun cleanWordLyrics(raw: String, format: String): Pair<String, String> {
        val value = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').trim()
        if (value.isBlank() || value.toByteArray().size > MAX_WORD_LYRICS_BYTES) return "" to ""
        return when {
            format == "ttml" || (format.isBlank() && Regex("^(?:\\s*<\\?xml[\\s\\S]*?)?<tt(?:\\s|>)", RegexOption.IGNORE_CASE).containsMatchIn(value)) -> {
                if (!value.contains(Regex("<p(?:\\s|>)", RegexOption.IGNORE_CASE)) ||
                    value.contains(Regex("<!DOCTYPE|<!ENTITY|<script(?:\\s|>)", RegexOption.IGNORE_CASE))
                ) "" to "" else value to "ttml"
            }
            format == "yrc" || (format.isBlank() && Regex("(?m)^\\s*\\[\\d+,\\d+]\\(\\d+,\\d+,\\d+\\)").containsMatchIn(value)) -> {
                if (Regex("(?m)^\\s*\\[\\d+,\\d+]\\(\\d+,\\d+(?:,\\d+)?\\)").containsMatchIn(value)) value to "yrc" else "" to ""
            }
            format == "enhanced-lrc" || Regex("<\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?>").containsMatchIn(value) -> {
                if (Regex("<\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?>").containsMatchIn(value)) value to "enhanced-lrc" else "" to ""
            }
            else -> "" to ""
        }
    }

    private fun lyricFormat(raw: String): String = when {
        Regex("\\[\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?]").containsMatchIn(raw) -> "lrc-line"
        raw.isNotBlank() -> "plain"
        else -> ""
    }

    private fun extractMetingId(raw: String, expectedServer: String): String {
        if (raw.isBlank()) return ""
        return runCatching {
            val uri = URI(if (raw.startsWith("//")) "https:$raw" else raw)
            val values = uri.rawQuery.orEmpty().split('&').mapNotNull { part ->
                val pair = part.split('=', limit = 2)
                if (pair.size == 2) java.net.URLDecoder.decode(pair[0], "UTF-8") to
                    java.net.URLDecoder.decode(pair[1], "UTF-8") else null
            }.toMap()
            val server = values["server"] ?: "netease"
            val id = values["id"].orEmpty()
            if (server == expectedServer && id.matches(Regex("^[a-zA-Z0-9_-]{1,128}$"))) id else ""
        }.getOrDefault("")
    }

    private fun safeUrl(value: String): String {
        if (value.isBlank() || value.length > 2_048) return ""
        return runCatching {
            val fixed = if (value.startsWith("//")) "https:$value" else value
            val uri = URI(fixed)
            if (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) fixed else ""
        }.getOrDefault("")
    }

    private fun firstText(vararg values: Any?): String {
        values.forEach { value ->
            when (value) {
                is String -> if (value.trim().isNotEmpty() && value != "null") return value.trim()
                is Number -> return value.toString()
                is JSONArray -> {
                    val joined = value.values().mapNotNull { item ->
                        when (item) {
                            is String -> item.trim().takeIf(String::isNotEmpty)
                            is JSONObject -> firstText(item.opt("name"), item.opt("artist"), item.opt("singername")).takeIf(String::isNotEmpty)
                            else -> null
                        }
                    }.joinToString(", ")
                    if (joined.isNotBlank()) return joined
                }
                is JSONObject -> {
                    val nested = firstText(value.opt("name"), value.opt("title"), value.opt("artist"), value.opt("singername"))
                    if (nested.isNotBlank()) return nested
                }
            }
        }
        return ""
    }

    private fun firstNumber(vararg values: Any?): Double? = values.firstNotNullOfOrNull { value ->
        when (value) {
            is Number -> value.toDouble().takeIf(Double::isFinite)
            is String -> value.toDoubleOrNull()?.takeIf(Double::isFinite)
            else -> null
        }
    }

    private fun JSONObject.string(name: String): String = opt(name)?.let { firstText(it) }.orEmpty()
    private fun JSONObject.numberOrNull(name: String): Double? = firstNumber(opt(name))
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
    private fun JSONArray.values(): List<Any?> = (0 until length()).map(::opt)
    private fun JSONArray.strings(): List<String> = values().mapNotNull { value ->
        (value as? String)?.trim()?.takeIf(String::isNotBlank)
    }

    private fun LyricsBundle.cleaned(): LyricsBundle {
        var line = cleanLineLyrics(lyrics)
        var (word, detectedFormat) = cleanWordLyrics(wordLyrics, wordFormat)
        if (word.isBlank() && line.isNotBlank()) {
            val enhanced = cleanWordLyrics(line, "enhanced-lrc")
            if (enhanced.first.isNotBlank()) {
                word = enhanced.first
                detectedFormat = enhanced.second
            }
        }
        return copy(
            lyrics = line,
            wordLyrics = word,
            wordFormat = detectedFormat,
            translationLyrics = cleanLineLyrics(translationLyrics),
            romanLyrics = cleanLineLyrics(romanLyrics),
        )
    }

    private fun decryptQrc(hex: String): String {
        val clean = hex.trim()
        require(clean.isNotEmpty() && clean.length % 16 == 0 && clean.matches(Regex("^[a-fA-F0-9]+$"))) { "QRC 数据无效" }
        require(clean.length / 2 <= MAX_ENCRYPTED_LYRICS_BYTES) { "QRC 数据过大" }
        return QrcDecrypter.decryptHex(clean)
    }

    private fun qqAuxiliary(decrypted: String): String =
        if (decrypted.contains("LyricContent", ignoreCase = true)) qrcToLineLrc(decrypted)
        else decrypted.takeIf { lyricFormat(it) == "lrc-line" }.orEmpty()

    private fun decryptKrc(encrypted: ByteArray): String {
        require(encrypted.size in 5..MAX_ENCRYPTED_LYRICS_BYTES) { "KRC 数据无效" }
        require(encrypted.copyOfRange(0, 4).toString(StandardCharsets.US_ASCII) == "krc1") { "KRC 数据无效" }
        val key = byteArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, -50, -46, 110, 105)
        val compressed = ByteArray(encrypted.size - 4) { index ->
            (encrypted[index + 4].toInt() xor key[index % key.size].toInt()).toByte()
        }
        return inflate(compressed)
    }

    private fun inflate(compressed: ByteArray): String {
        val output = ByteArrayOutputStream()
        InflaterInputStream(ByteArrayInputStream(compressed)).use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_WORD_LYRICS_BYTES) { "逐字歌词解压后过大" }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray().toString(StandardCharsets.UTF_8).removePrefix("\uFEFF")
    }

    private fun parseQrc(raw: String): LyricsBundle {
        val content = lyricContent(raw)
        val shift = -(Regex("(?m)^\\[offset:\\s*([+-]?\\d+)\\s*]").find(content)?.groupValues?.get(1)?.toLongOrNull() ?: 0L)
        val lines = mutableListOf<TimedRemoteLine>()
        content.lineSequence().forEach { value ->
            val match = Regex("^\\[(\\d+),(\\d+)](.*)$").matchEntire(value.trim()) ?: return@forEach
            val start = (match.groupValues[1].toLong() + shift).coerceAtLeast(0L)
            val duration = match.groupValues[2].toLong()
            val body = match.groupValues[3].trimEnd('\r')
            if (QRC_TIMESTAMP_ONLY.matches(body)) return@forEach
            val words = mutableListOf<TimedRemoteWord>()
            QRC_WORD.findAll(body).forEach { word ->
                val text = word.groupValues[1].replace("\r", "")
                if (text.isEmpty()) return@forEach
                if (text.isBlank()) {
                    words.lastOrNull()?.let { previous -> words[words.lastIndex] = previous.copy(text = previous.text + text) }
                    return@forEach
                }
                words += TimedRemoteWord(text, (word.groupValues[2].toLong() + shift).coerceAtLeast(0L), word.groupValues[3].toLong())
            }
            val text = if (words.isNotEmpty()) words.joinToString("") { it.text }.trim() else body
            if (text.isNotBlank()) lines += TimedRemoteLine(start, duration, text, words)
        }
        if (lines.isEmpty()) {
            val direct = content.takeIf { lyricFormat(it) == "lrc-line" }.orEmpty()
            return LyricsBundle(lyrics = direct)
        }
        return LyricsBundle(
            lyrics = lines.toLineLrc(),
            wordLyrics = if (lines.any { it.words.isNotEmpty() }) lines.toEnhancedLrc() else "",
            wordFormat = if (lines.any { it.words.isNotEmpty() }) "enhanced-lrc" else "",
        )
    }

    private fun qrcToLineLrc(raw: String): String {
        val content = lyricContent(raw)
        return Regex("(?m)^\\[(\\d+),(\\d+)](.*)$").findAll(content).mapNotNull { match ->
            val body = match.groupValues[3].trimEnd('\r')
            if (QRC_TIMESTAMP_ONLY.matches(body)) return@mapNotNull null
            val text = QRC_WORD.findAll(body).joinToString("") { it.groupValues[1] }.trim()
                .ifBlank { body }
            text.takeIf(String::isNotBlank)?.let { timestamp(match.groupValues[1].toLong()) + it }
        }.joinToString("\n")
    }

    private fun parseKrc(raw: String): LyricsBundle {
        val lines = mutableListOf<TimedRemoteLine>()
        raw.lineSequence().forEach { value ->
            val match = Regex("^\\[(\\d+),(\\d+)](.*)$").matchEntire(value.trim()) ?: return@forEach
            val start = match.groupValues[1].toLong()
            val duration = match.groupValues[2].toLong()
            val body = match.groupValues[3]
            val words = Regex("<(\\d+),(\\d+),\\d+>(.*?)(?=<\\d+,\\d+,\\d+>|$)")
                .findAll(body)
                .mapNotNull { word ->
                    val text = word.groupValues[3]
                    if (text.isBlank()) null else TimedRemoteWord(
                        text = text,
                        startMs = start + word.groupValues[1].toLong(),
                        durationMs = word.groupValues[2].toLong(),
                    )
                }.toList()
            val text = if (words.isNotEmpty()) words.joinToString("") { it.text } else body
            if (text.isNotBlank()) lines += TimedRemoteLine(start, duration, text, words)
        }
        val auxiliary = krcAuxiliary(raw, lines)
        return LyricsBundle(
            lyrics = lines.toLineLrc(),
            wordLyrics = if (lines.any { it.words.isNotEmpty() }) lines.toEnhancedLrc() else "",
            wordFormat = if (lines.any { it.words.isNotEmpty() }) "enhanced-lrc" else "",
            translationLyrics = auxiliary.first,
            romanLyrics = auxiliary.second,
        )
    }

    private fun krcAuxiliary(raw: String, lines: List<TimedRemoteLine>): Pair<String, String> {
        val encoded = Regex("(?m)^\\[language:([^]]+)]$").find(raw)?.groupValues?.get(1) ?: return "" to ""
        return runCatching {
            val content = JSONObject(Base64.decode(encoded, Base64.DEFAULT).toString(StandardCharsets.UTF_8))
                .optJSONArray("content")?.objects().orEmpty()
            fun render(type: Int, joiner: String): String {
                val lyricContent = content.firstOrNull { it.optInt("type", -1) == type }
                    ?.optJSONArray("lyricContent") ?: return ""
                return lines.mapIndexedNotNull { index, line ->
                    val row = lyricContent.optJSONArray(index) ?: return@mapIndexedNotNull null
                    row.values().mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }.joinToString(joiner)
                        .takeIf(String::isNotBlank)?.let { timestamp(line.startMs) + it }
                }.joinToString("\n")
            }
            render(1, "") to render(0, " ")
        }.getOrDefault("" to "")
    }

    private fun lyricContent(raw: String): String {
        val match = Regex(
            "<Lyric_1\\b[^>]*\\bLyricContent=([\"'])([\\s\\S]*?)\\1\\s*/?\\s*>",
            RegexOption.IGNORE_CASE,
        ).find(raw)
        return decodeEntities(match?.groupValues?.get(2) ?: raw).replace("\r\n", "\n").replace('\r', '\n')
    }

    private fun decodeEntities(raw: String): String = raw
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")

    private fun List<TimedRemoteLine>.toLineLrc(): String = joinToString("\n") { timestamp(it.startMs) + it.text }

    private fun List<TimedRemoteLine>.toEnhancedLrc(): String = joinToString("\n") { line ->
        timestamp(line.startMs) + if (line.words.isEmpty()) line.text else line.words.joinToString("") { word ->
            inlineTimestamp(word.startMs) + word.text + inlineTimestamp(word.startMs + word.durationMs)
        }
    }

    private fun LyricsDocument.toLineLrc(): String = lines.joinToString("\n") { timestamp(it.startMs) + it.text }

    private fun timestamp(value: Long): String {
        val safe = value.coerceAtLeast(0L)
        return "[%02d:%02d.%03d]".format(safe / 60_000, safe / 1_000 % 60, safe % 1_000)
    }

    private fun inlineTimestamp(value: Long): String = timestamp(value).replace('[', '<').replace(']', '>')

    private fun decodePossibleBase64(value: String): String {
        if (value.isBlank() || lyricFormat(value) == "lrc-line") return value
        return runCatching { Base64.decode(value, Base64.DEFAULT).toString(StandardCharsets.UTF_8) }
            .getOrDefault(value)
    }

    private fun base64(value: String): String = Base64.encodeToString(value.toByteArray(), Base64.NO_WRAP)

    private fun cacheKey(query: LyricsQuery, includeCandidates: Boolean, source: LyricsSearchSource): String = listOf(
        query.title.lowercase(Locale.ROOT), query.artist.lowercase(Locale.ROOT), query.album.lowercase(Locale.ROOT),
        query.durationSeconds?.let { kotlin.math.round(it * 10) / 10 }, query.filename.lowercase(Locale.ROOT),
        source.name,
        if (includeCandidates) "sources" else "match",
    ).joinToString("|")

    private fun putCache(key: String, result: LyricsMatchResult) {
        if (result.status == LyricsMatchStatus.UPSTREAM_ERROR) return
        val ttl = if (result.status == LyricsMatchStatus.NOT_FOUND) 6 * 60 * 60_000L else 30L * 24 * 60 * 60_000L
        synchronized(cache) { cache[key] = CacheEntry(result, System.currentTimeMillis() + ttl) }
    }

    private data class HttpPayload(val code: Int, val text: String) {
        fun requireSuccess(): HttpPayload {
            check(code in 200..299) { "歌词接口请求失败（HTTP $code）" }
            return this
        }
    }

    private data class RemoteMetadata(
        val platformId: String,
        val secondaryId: String = "",
        val title: String,
        val artist: String,
        val album: String,
        val durationSeconds: Double?,
        val coverUrl: String = "",
    )

    private data class LyricsBundle(
        val lyrics: String = "",
        val wordLyrics: String = "",
        val wordFormat: String = "",
        val translationLyrics: String = "",
        val romanLyrics: String = "",
        val instrumental: Boolean = false,
    ) {
        val hasLyrics: Boolean
            get() = instrumental || lyrics.isNotBlank() || wordLyrics.isNotBlank()
    }

    private data class TimedRemoteLine(
        val startMs: Long,
        val durationMs: Long,
        val text: String,
        val words: List<TimedRemoteWord>,
    )

    private data class TimedRemoteWord(val text: String, val startMs: Long, val durationMs: Long)
    private data class QqSession(val comm: JSONObject, val expiresAt: Long)
    private data class CacheEntry(val result: LyricsMatchResult, val expiresAt: Long)

    private companion object {
        val QRC_WORD = Regex("((?:(?!\\(\\d+,\\d+\\)).)*)\\((\\d+),(\\d+)\\)")
        val QRC_TIMESTAMP_ONLY = Regex("^\\(\\d+,\\d+\\)$")
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val USER_AGENT = "MiaoMusicAndroid/1.7.0-alpha.7 (+native lyrics matcher)"
        const val LRCLIB_BASE = "https://lrclib.net"
        const val NETEASE_BASE = "https://music.163.com"
        const val QQ_BASE = "https://c.y.qq.com"
        const val QQ_MUSICU = "https://u.y.qq.com/cgi-bin/musicu.fcg"
        const val KUGOU_BASE = "https://mobilecdn.kugou.com"
        const val KUGOU_LYRICS_BASE = "https://lyrics.kugou.com"
        const val METING_BASE = "https://api.qijieya.cn/meting/"
        const val WWOYUN_BASE = "https://zm.wwoyun.cn"
        const val WWOYUN_SEARCH = "https://zm.wwoyun.cn/cloudsearch"
        const val LRC_API = "https://api.lrc.cx/jsonapi"
        const val AMLL_API_BASE = "https://api.amll.dev"
        const val AMLL_BASE = "https://amll-ttml-db.stevexmh.net"
        const val AMLL_COMMUNITY = "https://amlldb.bikonoo.com"
        const val AMLL_DIMETA = "https://amll.mirror.dimeta.top"
        const val AMLL_GITHUB_BASE = "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/refs/heads/main"
        const val AMLL_RAW = "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/main/ncm-lyrics"
        const val MAX_HTTP_BYTES = 3 * 1024 * 1024
        const val MAX_LINE_LYRICS_BYTES = 256 * 1024
        const val MAX_WORD_LYRICS_BYTES = 512 * 1024
        const val MAX_ENCRYPTED_LYRICS_BYTES = 1024 * 1024
        const val QUICK_TIMEOUT_MS = 6_000L
        const val QUICK_WORD_GRACE_MS = 1_200L
        const val QUICK_MINIMUM_CONFIDENCE = 0.75
        val QQ_HEADERS = mapOf("Referer" to "https://y.qq.com/", "Origin" to "https://y.qq.com")
    }
}

internal data class AmllSearchHit(
    val id: String,
    val titles: List<String>,
    val artists: List<String>,
    val albums: List<String>,
    val lookup: String = "api:$id",
)

internal data class RankedAmllSearchHit(
    val hit: AmllSearchHit,
    val title: String,
    val artist: String,
    val album: String,
    val score: Double,
)

internal fun rankAmllSearchHits(
    query: LyricsQuery,
    hits: List<AmllSearchHit>,
    minimumScore: Double = 0.82,
    limit: Int = 3,
): List<RankedAmllSearchHit> =
    hits.asSequence().mapNotNull { hit ->
        val titles = hit.titles.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(8)
        val artists = hit.artists.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(8)
        if (titles.isEmpty()) return@mapNotNull null
        val artistVariants = if (artists.isEmpty()) listOf("") else (artists + artists.joinToString(", ")).distinct()
        val albums = hit.albums.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(4)
            .ifEmpty { listOf("") }
        titles.flatMap { title ->
            artistVariants.flatMap { artist ->
                albums.map { album ->
                    RankedAmllSearchHit(
                        hit = hit,
                        title = title,
                        artist = artist,
                        album = album,
                        score = LyricsMatcher.score(
                            query,
                            LyricsCandidate(
                                id = "amll:id:${hit.id}",
                                title = title,
                                artist = artist,
                                album = album,
                                durationSeconds = null,
                                source = "amll-ttml",
                            ),
                        ),
                    )
                }
            }
        }.maxByOrNull(RankedAmllSearchHit::score)
    }.filter { it.score >= minimumScore }
        .sortedByDescending(RankedAmllSearchHit::score)
        .distinctBy { it.hit.id }
        .take(limit)
        .toList()
