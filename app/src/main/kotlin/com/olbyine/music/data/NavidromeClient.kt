package com.olbyine.music.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

data class NdServer(val url: String, val user: String, val token: String, val salt: String, val name: String)

data class NdAlbum(
    val id: String,
    val name: String,
    val artist: String,
    val artistId: String?,
    val coverArt: String?,
    val songCount: Int,
    val year: Int,
    val durationSec: Long,
    val starred: Boolean,
)

data class NdArtist(val id: String, val name: String, val albumCount: Int, val coverArt: String?)

data class NdPlaylist(val id: String, val name: String, val songCount: Int, val coverArt: String?, val owner: String)

data class NdSong(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String?,
    val album: String,
    val albumId: String?,
    val coverArt: String?,
    val durationSec: Long,
    val track: Int,
    val year: Int,
    val suffix: String,
    val contentType: String,
    val size: Long,
    val bitRate: Int,
    val starred: Boolean,
)

data class NdSearch(val artists: List<NdArtist>, val albums: List<NdAlbum>, val songs: List<NdSong>)

class NavidromeClient(context: Context) {
    private val preferences = context.getSharedPreferences("navidrome", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val _server = MutableStateFlow(readServer())
    val server: StateFlow<NdServer?> = _server

    private fun readServer(): NdServer? {
        val raw = preferences.getString(KEY_SERVER, null) ?: return null
        return runCatching {
            val row = JSONObject(raw)
            NdServer(row.getString("url"), row.getString("user"), row.getString("token"), row.getString("salt"), row.optString("name"))
        }.getOrNull()
    }

    suspend fun signIn(address: String, user: String, password: String): NdServer = withContext(Dispatchers.IO) {
        val url = normalize(address)
        val salt = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val candidate = NdServer(url, user.trim(), md5(password + salt), salt, "")
        val ping = call(candidate, "ping", emptyMap())
        val name = ping.optString("type").ifBlank { "Subsonic" }.replaceFirstChar { it.uppercase() } +
            ping.optString("serverVersion").takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        val saved = candidate.copy(name = name)
        preferences.edit().putString(
            KEY_SERVER,
            JSONObject().put("url", saved.url).put("user", saved.user).put("token", saved.token).put("salt", saved.salt).put("name", saved.name).toString(),
        ).apply()
        _server.value = saved
        saved
    }

    fun signOut() {
        preferences.edit().remove(KEY_SERVER).apply()
        _server.value = null
    }

    private fun normalize(address: String): String {
        var url = address.trim().trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
        require(url.toHttpUrlOrNull() != null) { "服务器地址不正确" }
        return url
    }

    private fun base(server: NdServer, method: String, params: Map<String, String>): HttpUrl {
        val builder = ("${server.url}/rest/$method").toHttpUrlOrNull()?.newBuilder() ?: error("服务器地址不正确")
        builder.addQueryParameter("u", server.user)
            .addQueryParameter("t", server.token)
            .addQueryParameter("s", server.salt)
            .addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", CLIENT)
            .addQueryParameter("f", "json")
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    private fun call(server: NdServer, method: String, params: Map<String, String>): JSONObject {
        val request = Request.Builder().url(base(server, method, params)).header("Accept", "application/json").build()
        http.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "服务器没有响应（HTTP ${response.code}）" }
            val body = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            val root = body.optJSONObject("subsonic-response") ?: error("这不是 Navidrome / Subsonic 服务器")
            if (root.optString("status") != "ok") {
                val error = root.optJSONObject("error")
                val code = error?.optInt("code") ?: 0
                error(
                    when (code) {
                        40 -> "用户名或密码不正确"
                        41 -> "服务器不支持令牌登录"
                        70 -> "找不到这个内容"
                        else -> error?.optString("message")?.ifBlank { null } ?: "请求失败"
                    },
                )
            }
            return root
        }
    }

    private fun current(): NdServer = _server.value ?: error("请先连接 Navidrome 服务器")

    private suspend fun get(method: String, params: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) { call(current(), method, params) }

    suspend fun albums(type: String, offset: Int = 0, size: Int = 60): List<NdAlbum> =
        get("getAlbumList2", mapOf("type" to type, "size" to size.toString(), "offset" to offset.toString()))
            .optJSONObject("albumList2")?.optJSONArray("album").rows().map(::album)

    suspend fun album(id: String): Pair<NdAlbum, List<NdSong>> {
        val row = get("getAlbum", mapOf("id" to id)).optJSONObject("album") ?: error("找不到这张专辑")
        return album(row) to row.optJSONArray("song").rows().map(::song)
    }

    suspend fun artists(): List<NdArtist> =
        get("getArtists").optJSONObject("artists")?.optJSONArray("index").rows()
            .flatMap { index -> index.optJSONArray("artist").rows().map(::artist) }

    suspend fun artist(id: String): Pair<NdArtist, List<NdAlbum>> {
        val row = get("getArtist", mapOf("id" to id)).optJSONObject("artist") ?: error("找不到这位艺人")
        return artist(row) to row.optJSONArray("album").rows().map(::album)
    }

    suspend fun playlists(): List<NdPlaylist> =
        get("getPlaylists").optJSONObject("playlists")?.optJSONArray("playlist").rows().map { row ->
            NdPlaylist(row.optString("id"), row.optString("name"), row.optInt("songCount"), row.optString("coverArt").ifBlank { null }, row.optString("owner"))
        }

    suspend fun playlist(id: String): List<NdSong> =
        get("getPlaylist", mapOf("id" to id)).optJSONObject("playlist")?.optJSONArray("entry").rows().map(::song)

    suspend fun starred(): List<NdSong> =
        get("getStarred2").optJSONObject("starred2")?.optJSONArray("song").rows().map(::song)

    suspend fun randomSongs(size: Int = 60): List<NdSong> =
        get("getRandomSongs", mapOf("size" to size.toString())).optJSONObject("randomSongs")?.optJSONArray("song").rows().map(::song)

    suspend fun search(query: String): NdSearch {
        val result = get("search3", mapOf("query" to query, "artistCount" to "20", "albumCount" to "30", "songCount" to "60"))
            .optJSONObject("searchResult3") ?: return NdSearch(emptyList(), emptyList(), emptyList())
        return NdSearch(
            result.optJSONArray("artist").rows().map(::artist),
            result.optJSONArray("album").rows().map(::album),
            result.optJSONArray("song").rows().map(::song),
        )
    }

    suspend fun setStarred(songId: String, starred: Boolean) {
        get(if (starred) "star" else "unstar", mapOf("id" to songId))
    }

    suspend fun scrobble(songId: String) {
        runCatching { get("scrobble", mapOf("id" to songId, "submission" to "true", "time" to System.currentTimeMillis().toString())) }
    }

    suspend fun lyrics(song: NdSong): String? = runCatching {
        val open = get("getLyricsBySongId", mapOf("id" to song.id)).optJSONObject("lyricsList")?.optJSONArray("structuredLyrics").rows()
        val synced = open.firstOrNull { it.optBoolean("synced") } ?: open.firstOrNull()
        synced?.optJSONArray("line").rows().joinToString("\n") { line ->
            val start = line.optLong("start", -1)
            if (start >= 0) "[%02d:%02d.%02d]%s".format(start / 60_000, start / 1000 % 60, start % 1000 / 10, line.optString("value"))
            else line.optString("value")
        }.takeIf { it.isNotBlank() }
    }.getOrNull() ?: runCatching {
        get("getLyrics", mapOf("artist" to song.artist, "title" to song.title)).optJSONObject("lyrics")?.optString("value")?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun coverUrl(coverArt: String?, size: Int = 600): String? {
        val server = _server.value ?: return null
        coverArt ?: return null
        return base(server, "getCoverArt", mapOf("id" to coverArt, "size" to size.toString())).toString()
    }

    fun streamUrl(songId: String): String = base(current(), "stream", mapOf("id" to songId)).toString()

    fun toTrack(song: NdSong): Track = Track(
        id = "nd:${song.id}",
        uri = streamUrl(song.id),
        title = song.title.ifBlank { "未知歌曲" },
        artist = song.artist.ifBlank { "未知艺人" },
        album = song.album,
        durationMs = song.durationSec * 1000L,
        displayName = song.title,
        mimeType = song.contentType.ifBlank { "audio/mpeg" },
        sizeBytes = song.size,
        coverPath = null,
        lyrics = null,
        wordLyrics = null,
        translationLyrics = null,
        romanLyrics = null,
        lyricsSource = null,
        lyricsFormat = null,
        lyricsConfidence = null,
        lyricsInstrumental = false,
        lyricsLookupAt = 0L,
        lyricsLookupStatus = null,
        favorite = song.starred,
        addedAt = 0L,
        artworkUrl = coverUrl(song.coverArt),
    )

    private fun album(row: JSONObject) = NdAlbum(
        id = row.optString("id"),
        name = row.optString("name").ifBlank { row.optString("title") },
        artist = row.optString("artist"),
        artistId = row.optString("artistId").ifBlank { null },
        coverArt = row.optString("coverArt").ifBlank { null },
        songCount = row.optInt("songCount"),
        year = row.optInt("year"),
        durationSec = row.optLong("duration"),
        starred = row.has("starred"),
    )

    private fun artist(row: JSONObject) = NdArtist(
        id = row.optString("id"),
        name = row.optString("name"),
        albumCount = row.optInt("albumCount"),
        coverArt = row.optString("coverArt").ifBlank { null },
    )

    private fun song(row: JSONObject) = NdSong(
        id = row.optString("id"),
        title = row.optString("title"),
        artist = row.optString("artist"),
        artistId = row.optString("artistId").ifBlank { null },
        album = row.optString("album"),
        albumId = row.optString("albumId").ifBlank { null },
        coverArt = row.optString("coverArt").ifBlank { null },
        durationSec = row.optLong("duration"),
        track = row.optInt("track"),
        year = row.optInt("year"),
        suffix = row.optString("suffix"),
        contentType = row.optString("contentType"),
        size = row.optLong("size"),
        bitRate = row.optInt("bitRate"),
        starred = row.has("starred"),
    )

    private fun JSONArray?.rows(): List<JSONObject> {
        this ?: return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }

    private fun md5(text: String): String =
        MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    companion object {
        private const val KEY_SERVER = "server"
        private const val API_VERSION = "1.16.1"
        private const val CLIENT = "OlbyineMusic"
    }
}
