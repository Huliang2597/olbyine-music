package com.olbyine.music.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AppleMusicSettings(
    val storefront: String = "cn",
    val hasUserToken: Boolean = false,
    val accountStorefront: String? = null,
)

data class AppleAccount(val storefront: String, val language: String)

data class AmSong(
    val id: String,
    val name: String,
    val artistName: String,
    val albumName: String,
    val durationMs: Long,
    val artworkTemplate: String?,
    val releaseDate: String?,
    val genre: String?,
    val hasTimeSyncedLyrics: Boolean,
    val storefront: String,
)

data class AmAlbum(
    val id: String,
    val name: String,
    val artistName: String,
    val notes: String,
    val releaseDate: String?,
    val genre: String?,
    val artworkTemplate: String?,
    val motionSquare: String?,
    val motionTall: String?,
)

data class AmArtist(val id: String, val name: String, val artworkTemplate: String?, val bio: String)

class AppleMusicClient(context: Context) {
    private val preferences = context.getSharedPreferences("apple_music", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val tokenMutex = Mutex()
    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<AppleMusicSettings> = _settings.asStateFlow()

    fun setStorefront(value: String) {
        val code = value.trim().lowercase(Locale.ROOT).takeIf { it.matches(Regex("^[a-z]{2}$")) } ?: return
        preferences.edit().putString(KEY_STOREFRONT, code).apply()
        _settings.value = readSettings()
    }

    fun setUserToken(value: String?) {
        val token = value?.let(::extractUserToken)
        preferences.edit().apply {
            if (token == null) remove(KEY_USER_TOKEN) else putString(KEY_USER_TOKEN, token)
            remove(KEY_ACCOUNT_STOREFRONT)
            remove(KEY_ACCOUNT_LANGUAGE)
            remove(KEY_ACCOUNT_CHECKED)
        }.apply()
        _settings.value = readSettings()
    }

    private fun extractUserToken(raw: String): String? {
        val text = raw.trim().trim('"', '\'')
        val fromCookie = Regex("media-user-token\\s*[=:]\\s*\"?([A-Za-z0-9+/=_\\-.]{20,})", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)
        val token = (fromCookie ?: text).trim().trim('"', '\'', ';')
        return token.takeIf { it.length > 20 && !it.contains(' ') }
    }

    private fun readSettings() = AppleMusicSettings(
        storefront = preferences.getString(KEY_STOREFRONT, null) ?: "cn",
        hasUserToken = !preferences.getString(KEY_USER_TOKEN, null).isNullOrBlank(),
        accountStorefront = preferences.getString(KEY_ACCOUNT_STOREFRONT, null),
    )

    suspend fun account(forceRefresh: Boolean = false): AppleAccount? = withContext(Dispatchers.IO) {
        val userToken = preferences.getString(KEY_USER_TOKEN, null) ?: return@withContext null
        val cached = preferences.getString(KEY_ACCOUNT_STOREFRONT, null)
        val checkedAt = preferences.getLong(KEY_ACCOUNT_CHECKED, 0L)
        if (!forceRefresh && cached != null && System.currentTimeMillis() - checkedAt < 86_400_000L) {
            return@withContext AppleAccount(cached, preferences.getString(KEY_ACCOUNT_LANGUAGE, null) ?: "en-US")
        }
        val data = runCatching { get("$API/v1/me/storefront", userToken) }.getOrNull()
            ?.optJSONArray("data")?.optJSONObject(0)
        val storefront = data?.optString("id")?.lowercase(Locale.ROOT)?.takeIf { it.matches(Regex("^[a-z]{2}$")) }
            ?: return@withContext cached?.let { AppleAccount(it, preferences.getString(KEY_ACCOUNT_LANGUAGE, null) ?: "en-US") }
        val language = data.optJSONObject("attributes")?.optString("defaultLanguageTag")?.takeIf(String::isNotBlank) ?: "en-US"
        preferences.edit()
            .putString(KEY_ACCOUNT_STOREFRONT, storefront)
            .putString(KEY_ACCOUNT_LANGUAGE, language)
            .putLong(KEY_ACCOUNT_CHECKED, System.currentTimeMillis())
            .apply()
        _settings.value = readSettings()
        AppleAccount(storefront, language)
    }

    suspend fun verifyToken(): Result<AppleAccount> = runCatching {
        account(forceRefresh = true) ?: error("令牌无效或已过期（需要有效的 Apple Music 订阅）")
    }

    private val storefronts: List<String>
        get() = listOfNotNull(_settings.value.accountStorefront, _settings.value.storefront, "tw", "hk", "us").distinct()

    suspend fun searchSongs(term: String, limit: Int = 6, only: String? = null): List<AmSong> = withContext(Dispatchers.IO) {
        if (term.isBlank()) return@withContext emptyList()
        for (storefront in only?.let(::listOf) ?: storefronts) {
            val url = "$API/v1/catalog/$storefront/search".toHttpUrl().newBuilder()
                .addQueryParameter("term", term)
                .addQueryParameter("types", "songs")
                .addQueryParameter("limit", limit.toString())
                .build().toString()
            val songs = runCatching { get(url) }.getOrNull()
                ?.optJSONObject("results")?.optJSONObject("songs")?.optJSONArray("data")
                ?.objects()?.mapNotNull { song(it, storefront) }.orEmpty()
            if (songs.isNotEmpty()) return@withContext songs
        }
        emptyList()
    }

    suspend fun findSong(title: String, artist: String, album: String, durationMs: Long): AmSong? {
        val term = listOf(title, artist).filter(String::isNotBlank).joinToString(" ")
        val results = searchSongs(term, 8)
        val scored = results.map { it to matchScore(it, title, artist, album, durationMs) }
        return scored.filter { it.second >= 3.0 }.maxByOrNull { it.second }?.first
    }

    private fun matchScore(song: AmSong, title: String, artist: String, album: String, durationMs: Long): Double {
        val n = OnlineMetadataRepository::normalize
        var score = 0.0
        val t = n(title)
        val candidateTitle = n(song.name)
        when {
            candidateTitle == t -> score += 3.0
            t.isNotBlank() && (candidateTitle.contains(t) || t.contains(candidateTitle)) -> score += 1.8
        }
        val a = n(artist)
        val candidateArtist = n(song.artistName)
        if (a.isNotBlank() && (candidateArtist.contains(a) || a.contains(candidateArtist))) score += 2.0
        if (album.isNotBlank() && n(song.albumName) == n(album)) score += 1.0
        if (durationMs > 0 && song.durationMs > 0) {
            val delta = kotlin.math.abs(durationMs - song.durationMs)
            score += when {
                delta < 2_500 -> 1.0
                delta < 6_000 -> .4
                delta > 20_000 -> -1.5
                else -> 0.0
            }
        }
        return score
    }

    suspend fun songRelations(song: AmSong): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val data = runCatching { get("$API/v1/catalog/${song.storefront}/songs/${song.id}") }.getOrNull()
            ?.optJSONArray("data")?.optJSONObject(0)
        val relationships = data?.optJSONObject("relationships")
        val albumId = relationships?.optJSONObject("albums")?.optJSONArray("data")?.optJSONObject(0)?.optString("id")
        val artistId = relationships?.optJSONObject("artists")?.optJSONArray("data")?.optJSONObject(0)?.optString("id")
        albumId?.takeIf(String::isNotBlank) to artistId?.takeIf(String::isNotBlank)
    }

    suspend fun album(id: String, storefront: String = _settings.value.storefront): AmAlbum? = withContext(Dispatchers.IO) {
        val url = "$API/v1/catalog/$storefront/albums/$id?extend=editorialVideo"
        val data = runCatching { get(url) }.getOrNull()?.optJSONArray("data")?.optJSONObject(0) ?: return@withContext null
        val attributes = data.optJSONObject("attributes") ?: return@withContext null
        val video = attributes.optJSONObject("editorialVideo")
        fun videoUrl(key: String) = video?.optJSONObject(key)?.optString("video")?.takeIf { it.startsWith("https://") }
        AmAlbum(
            id = id,
            name = attributes.optString("name"),
            artistName = attributes.optString("artistName"),
            notes = stripHtml(
                attributes.optJSONObject("editorialNotes")?.let { it.optString("standard").ifBlank { it.optString("short") } }.orEmpty(),
            ),
            releaseDate = attributes.optString("releaseDate").takeIf(String::isNotBlank),
            genre = attributes.optJSONArray("genreNames")?.optString(0)?.takeIf { it.isNotBlank() && it != "音乐" && it != "Music" },
            artworkTemplate = attributes.optJSONObject("artwork")?.optString("url")?.takeIf(String::isNotBlank),
            motionSquare = videoUrl("motionDetailSquare") ?: videoUrl("motionSquareVideo1x1"),
            motionTall = videoUrl("motionDetailTall") ?: videoUrl("motionTallVideo3x4"),
        )
    }

    suspend fun artist(id: String, storefront: String = _settings.value.storefront): AmArtist? = withContext(Dispatchers.IO) {
        val url = "$API/v1/catalog/$storefront/artists/$id?extend=artistBio"
        val data = runCatching { get(url) }.getOrNull()?.optJSONArray("data")?.optJSONObject(0) ?: return@withContext null
        val attributes = data.optJSONObject("attributes") ?: return@withContext null
        AmArtist(
            id = id,
            name = attributes.optString("name"),
            artworkTemplate = attributes.optJSONObject("artwork")?.optString("url")?.takeIf(String::isNotBlank),
            bio = stripHtml(attributes.optString("artistBio")),
        )
    }

    suspend fun searchAlbums(term: String, limit: Int = 5): List<Pair<String, AmAlbum>> = withContext(Dispatchers.IO) {
        for (storefront in storefronts) {
            val url = "$API/v1/catalog/$storefront/search".toHttpUrl().newBuilder()
                .addQueryParameter("term", term)
                .addQueryParameter("types", "albums")
                .addQueryParameter("limit", limit.toString())
                .build().toString()
            val albums = runCatching { get(url) }.getOrNull()
                ?.optJSONObject("results")?.optJSONObject("albums")?.optJSONArray("data")
                ?.objects()?.mapNotNull { row ->
                    val attributes = row.optJSONObject("attributes") ?: return@mapNotNull null
                    storefront to AmAlbum(
                        id = row.optString("id"),
                        name = attributes.optString("name"),
                        artistName = attributes.optString("artistName"),
                        notes = "",
                        releaseDate = attributes.optString("releaseDate").takeIf(String::isNotBlank),
                        genre = null,
                        artworkTemplate = attributes.optJSONObject("artwork")?.optString("url"),
                        motionSquare = null,
                        motionTall = null,
                    )
                }.orEmpty()
            if (albums.isNotEmpty()) return@withContext albums
        }
        emptyList()
    }

    suspend fun lyricsTtml(song: AmSong): String? = withContext(Dispatchers.IO) {
        val userToken = preferences.getString(KEY_USER_TOKEN, null) ?: return@withContext null
        val account = account()
        val storefront = account?.storefront ?: song.storefront
        val languages = listOfNotNull("zh-Hans-CN", account?.language).distinct()
        for (language in languages) {
            val included = runCatching {
                get(
                    "$API/v1/catalog/$storefront/songs/${song.id}?include[songs]=syllable-lyrics" +
                        "&l=${java.net.URLEncoder.encode(language, "UTF-8")}&extend=ttmlLocalizations",
                    userToken,
                )
            }.getOrNull()
            val syllable = included?.optJSONArray("data")?.optJSONObject(0)?.optJSONObject("relationships")
                ?.optJSONObject("syllable-lyrics")?.optJSONArray("data")?.optJSONObject(0)?.optJSONObject("attributes")
            pickTtml(syllable)?.let { return@withContext it }
            for (kind in listOf("syllable-lyrics", "lyrics")) {
                val attributes = runCatching {
                    get(
                        "$API/v1/catalog/$storefront/songs/${song.id}/$kind" +
                            "?l=${java.net.URLEncoder.encode(language, "UTF-8")}&extend=ttmlLocalizations",
                        userToken,
                    )
                }.getOrNull()?.optJSONArray("data")?.optJSONObject(0)?.optJSONObject("attributes")
                pickTtml(attributes)?.let { return@withContext it }
            }
        }
        null
    }

    private fun pickTtml(attributes: JSONObject?): String? {
        attributes ?: return null
        return listOf(attributes.optString("ttmlLocalizations"), attributes.optString("ttml"))
            .firstOrNull { it.contains("<tt") && it.contains("begin=") }
    }

    private fun song(row: JSONObject, storefront: String): AmSong? {
        val attributes = row.optJSONObject("attributes") ?: return null
        return AmSong(
            id = row.optString("id").takeIf(String::isNotBlank) ?: return null,
            name = attributes.optString("name"),
            artistName = attributes.optString("artistName"),
            albumName = attributes.optString("albumName"),
            durationMs = attributes.optLong("durationInMillis"),
            artworkTemplate = attributes.optJSONObject("artwork")?.optString("url")?.takeIf(String::isNotBlank),
            releaseDate = attributes.optString("releaseDate").takeIf(String::isNotBlank),
            genre = attributes.optJSONArray("genreNames")?.optString(0),
            hasTimeSyncedLyrics = attributes.optBoolean("hasTimeSyncedLyrics"),
            storefront = storefront,
        )
    }

    private suspend fun get(url: String, userToken: String? = null): JSONObject {
        var token = developerToken()
        repeat(2) { attempt ->
            val request = Request.Builder().url(url)
                .header("Authorization", "Bearer $token")
                .header("Origin", "https://music.apple.com")
                .header("Referer", "https://music.apple.com/")
                .header("User-Agent", BROWSER_UA)
                .header("Accept", "application/json")
                .apply {
                    if (userToken != null) {
                        header("Media-User-Token", userToken)
                        header("Cookie", "media-user-token=$userToken")
                    }
                }
                .build()
            client.newCall(request).execute().use { response ->
                val staleToken = response.code == 401 ||
                    (response.code == 403 && (response.body?.contentLength() ?: 0L) <= 0L)
                if (staleToken && attempt == 0) {
                    token = developerToken(forceRefresh = true)
                    return@repeat
                }
                require(response.isSuccessful) { "Apple Music HTTP ${response.code}" }
                return JSONObject(response.body?.string().orEmpty())
            }
        }
        error("Apple Music 授权失败")
    }

    private suspend fun developerToken(forceRefresh: Boolean = false): String = tokenMutex.withLock {
        val cached = preferences.getString(KEY_DEV_TOKEN, null)
        val expiry = preferences.getLong(KEY_DEV_TOKEN_EXP, 0L)
        if (!forceRefresh && cached != null && System.currentTimeMillis() < expiry - 3_600_000L) return@withLock cached
        val token = withContext(Dispatchers.IO) { scrapeToken() }
        preferences.edit()
            .putString(KEY_DEV_TOKEN, token)
            .putLong(KEY_DEV_TOKEN_EXP, tokenExpiry(token) ?: (System.currentTimeMillis() + 86_400_000L))
            .apply()
        token
    }

    private fun scrapeToken(): String {
        val html = text("https://music.apple.com/us/browse")
        val scripts = Regex("/assets/index[~-][A-Za-z0-9_~-]+\\.js").findAll(html).map { it.value }.distinct()
            .sortedBy { if (it.contains("legacy")) 1 else 0 }.toList()
        require(scripts.isNotEmpty()) { "Apple Music 网页结构已变化" }
        for (script in scripts) {
            val js = text("https://music.apple.com$script")
            val tokens = Regex("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{20,}").findAll(js).map { it.value }.toList()
            val chosen = tokens.firstOrNull { tokenPayload(it)?.optString("iss") == "AMPWebPlay" } ?: tokens.firstOrNull()
            if (chosen != null) return chosen
        }
        error("未能获取 Apple Music 访问令牌")
    }

    private fun text(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", BROWSER_UA).build()
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "HTTP ${response.code}" }
            return response.body?.string().orEmpty()
        }
    }

    private fun tokenPayload(token: String): JSONObject? = runCatching {
        val part = token.split('.')[1]
        JSONObject(String(Base64.decode(part, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)))
    }.getOrNull()

    private fun tokenExpiry(token: String): Long? = tokenPayload(token)?.optLong("exp")?.takeIf { it > 0 }?.times(1000L)

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

    private fun stripHtml(value: String): String = value
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
        .trim()

    companion object {
        private const val API = "https://amp-api.music.apple.com"
        private const val KEY_DEV_TOKEN = "dev_token"
        private const val KEY_DEV_TOKEN_EXP = "dev_token_exp"
        private const val KEY_USER_TOKEN = "user_token"
        private const val KEY_STOREFRONT = "storefront"
        private const val KEY_ACCOUNT_STOREFRONT = "account_storefront"
        private const val KEY_ACCOUNT_LANGUAGE = "account_language"
        private const val KEY_ACCOUNT_CHECKED = "account_checked"
        private const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Safari/537.36"

        fun artwork(template: String?, size: Int, crop: String = "bb"): String? =
            template?.replace("{w}", size.toString())?.replace("{h}", size.toString())
                ?.replace(Regex("\\{c\\}"), crop)?.replace(Regex("\\{f\\}"), "jpg")
                ?.replace(Regex("/\\d+x\\d+bb\\.(png|jpg)$"), "/${size}x${size}$crop.jpg")
    }
}
