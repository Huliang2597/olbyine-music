package com.olbyine.music.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class NeteaseAccount(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String?,
    val vipType: Int,
)

data class NeteasePlaylistSummary(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val trackCount: Int,
    val creator: String,
    val liked: Boolean,
    val own: Boolean,
    val playCount: Long = 0,
    val kind: NeteaseCollectionKind = NeteaseCollectionKind.PLAYLIST,
    val note: String = "",
)

enum class NeteaseCollectionKind { PLAYLIST, ALBUM, ARTIST, CHART, DAILY }

data class NeteaseComment(
    val id: Long,
    val user: String,
    val userId: Long = 0,
    val replyCount: Int = 0,
    val avatarUrl: String?,
    val content: String,
    val time: Long,
    val likedCount: Int,
    val replyTo: String?,
    val liked: Boolean = false,
)

data class NeteaseComments(val hot: List<NeteaseComment>, val latest: List<NeteaseComment>, val total: Int, val more: Boolean)

data class NeteaseProfile(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String?,
    val backgroundUrl: String?,
    val signature: String,
    val followers: Int,
    val follows: Int,
    val level: Int,
    val listened: Int,
    val identity: String,
    val followed: Boolean = false,
    val gender: Int = 0,
    val birthday: Long = 0,
    val days: Int = 0,
    val artistId: Long = 0,
    val links: List<Pair<String, String>> = emptyList(),
    val eventCount: Int = 0,
)

data class NeteaseThread(val userId: Long, val nickname: String, val avatarUrl: String?, val lastMessage: String, val time: Long, val unread: Int)

data class NeteaseMessage(val fromMe: Boolean, val text: String, val time: Long, val card: NeteaseCard? = null, val raw: String = "")

sealed interface NeteaseTarget {
    data class Song(val id: Long) : NeteaseTarget
    data class Playlist(val id: Long) : NeteaseTarget
    data class Album(val id: Long) : NeteaseTarget
    data class Artist(val id: Long, val name: String = "") : NeteaseTarget
    data class User(val id: Long) : NeteaseTarget
    data class Mv(val id: Long) : NeteaseTarget
    data class ListenTogether(val roomId: String, val inviterId: Long) : NeteaseTarget
    data class Web(val url: String) : NeteaseTarget
}

data class NeteaseCard(val title: String, val subtitle: String, val imageUrl: String?, val target: NeteaseTarget?, val label: String)

data class NeteaseFriend(val userId: Long, val nickname: String, val avatarUrl: String?, val signature: String = "", val followed: Boolean = false)

data class NeteaseCollection(
    val summary: NeteasePlaylistSummary,
    val description: String,
    val songs: List<NeteaseSong>,
    val ownerId: Long = 0,
    val artistId: Long = 0,
    val tags: List<String> = emptyList(),
    val complete: Boolean = true,
    val saved: Boolean? = null,
)

data class NeteaseArtistHome(
    val id: Long,
    val name: String,
    val avatarUrl: String?,
    val coverUrl: String?,
    val alias: String,
    val identity: String,
    val briefDesc: String,
    val sections: List<Pair<String, String>>,
    val songCount: Int,
    val albumCount: Int,
    val mvCount: Int,
    val accountUserId: Long = 0,
)

data class NeteaseWikiSection(val title: String, val entries: List<NeteaseWikiEntry>)

data class NeteaseWikiEntry(val title: String, val text: String, val imageUrl: String?)

data class NeteaseWikiContribution(val name: String, val artists: String, val time: Long, val status: Int, val type: Int)

data class NeteaseListenRoom(
    val roomId: String,
    val inviterId: Long,
    val users: List<NeteaseFriend>,
    val host: Boolean,
    val chatRoomId: String? = null,
    val startedAt: Long = 0L,
)

data class NeteaseArtist(val id: Long, val name: String, val pictureUrl: String?, val albumCount: Int)

data class NeteaseSearchResult(
    val songs: List<NeteaseSong> = emptyList(),
    val playlists: List<NeteasePlaylistSummary> = emptyList(),
    val albums: List<NeteasePlaylistSummary> = emptyList(),
    val artists: List<NeteaseArtist> = emptyList(),
    val total: Int = 0,
)

data class NeteaseSong(
    val id: Long,
    val title: String,
    val artists: List<String>,
    val album: String,
    val durationMs: Long,
    val coverUrl: String?,
    val unavailable: Boolean,
    val albumId: Long = 0,
    val artistIds: List<Long> = emptyList(),
    val mvId: Long = 0,
) {
    val artist: String get() = artists.joinToString(" / ").ifBlank { "未知艺术家" }

    fun toTrack(): Track = Track(
        id = trackId(id),
        uri = "ncm://song/$id",
        title = title.ifBlank { "未知歌曲" },
        artist = artist,
        album = album.ifBlank { "未知专辑" },
        durationMs = durationMs,
        displayName = title,
        mimeType = null,
        sizeBytes = 0L,
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
        favorite = false,
        addedAt = 0L,
        artworkUrl = coverUrl,
    )

    companion object {
        fun trackId(songId: Long) = "ncm:$songId"
    }
}

enum class QrLoginState { WAITING, SCANNED, CONFIRMED, EXPIRED, ERROR }

data class QrLoginStatus(val state: QrLoginState, val nickname: String? = null, val message: String? = null)

class NeteaseClient(context: Context) {
    private val preferences = context.getSharedPreferences("netease_account", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val deviceId: String = preferences.getString(KEY_DEVICE, null)
        ?: java.util.UUID.randomUUID().toString().replace("-", "").take(32).uppercase(Locale.ROOT)
            .also { preferences.edit().putString(KEY_DEVICE, it).apply() }
    private val _account = MutableStateFlow(readAccount())
    val account: StateFlow<NeteaseAccount?> = _account.asStateFlow()

    val signedIn: Boolean get() = cookie != null

    private val cookie: String? get() = preferences.getString(KEY_MUSIC_U, null)

    suspend fun qrKey(): String = withContext(Dispatchers.IO) {
        val result = eapi("/api/login/qrcode/unikey", JSONObject().put("type", "1"))
        result.optString("unikey").takeIf(String::isNotBlank) ?: error("无法获取登录二维码")
    }

    fun qrContent(key: String) = "https://music.163.com/login?codekey=$key"

    suspend fun qrStatus(key: String): QrLoginStatus = withContext(Dispatchers.IO) {
        val request = eapiRequest("/api/login/qrcode/client/login", JSONObject().put("key", key).put("type", "1"))
        client.newCall(request).execute().use { response ->
            val body = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            when (body.optInt("code")) {
                801 -> QrLoginStatus(QrLoginState.WAITING)
                802 -> QrLoginStatus(QrLoginState.SCANNED, body.optString("nickname").takeIf(String::isNotBlank))
                800 -> QrLoginStatus(QrLoginState.EXPIRED, message = "二维码已过期")
                803 -> {
                    val musicU = response.headers("Set-Cookie").firstNotNullOfOrNull { header ->
                        Regex("MUSIC_U=([^;]+)").find(header)?.groupValues?.get(1)
                    } ?: Regex("MUSIC_U=([^;]+)").find(body.optString("cookie"))?.groupValues?.get(1)
                    if (musicU.isNullOrBlank()) {
                        QrLoginStatus(QrLoginState.ERROR, message = "登录成功但没有拿到账号凭据，请改用网页登录")
                    } else {
                        saveCookie(musicU)
                        refreshAccount()
                        QrLoginStatus(QrLoginState.CONFIRMED)
                    }
                }
                else -> QrLoginStatus(QrLoginState.ERROR, message = body.optString("message").ifBlank { "登录状态未知" })
            }
        }
    }

    suspend fun signInWithCookie(raw: String): NeteaseAccount = withContext(Dispatchers.IO) {
        val value = Regex("MUSIC_U=([^;\\s]+)").find(raw)?.groupValues?.get(1) ?: raw.trim().trim('"', ';')
        require(value.length > 40 && !value.contains(' ')) { "没有识别到 MUSIC_U" }
        saveCookie(value)
        refreshAccount() ?: run {
            signOut()
            error("这个凭据无效或已过期")
        }
    }

    fun signOut() {
        preferences.edit().remove(KEY_MUSIC_U).remove(KEY_ACCOUNT).apply()
        _account.value = null
    }

    private fun saveCookie(musicU: String) {
        preferences.edit().putString(KEY_MUSIC_U, musicU).apply()
    }

    suspend fun refreshAccount(): NeteaseAccount? = withContext(Dispatchers.IO) {
        if (cookie == null) return@withContext null
        val result = runCatching { eapi("/api/nuser/account/get", JSONObject()) }.getOrNull() ?: return@withContext _account.value
        val profile = result.optJSONObject("profile")
        if (profile == null) {
            if (result.optInt("code") == 200 || result.optInt("code") == 301) signOut()
            return@withContext null
        }
        val account = NeteaseAccount(
            userId = profile.optLong("userId"),
            nickname = profile.optString("nickname"),
            avatarUrl = profile.optString("avatarUrl").https(),
            vipType = profile.optInt("vipType"),
        )
        preferences.edit().putString(
            KEY_ACCOUNT,
            JSONObject().put("id", account.userId).put("name", account.nickname).put("avatar", account.avatarUrl).put("vip", account.vipType).toString(),
        ).apply()
        _account.value = account
        account
    }

    private fun readAccount(): NeteaseAccount? = preferences.getString(KEY_ACCOUNT, null)?.let { raw ->
        runCatching {
            val row = JSONObject(raw)
            NeteaseAccount(row.getLong("id"), row.optString("name"), row.optString("avatar").takeIf(String::isNotBlank), row.optInt("vip"))
        }.getOrNull()
    }

    suspend fun playlists(): List<NeteasePlaylistSummary> = withContext(Dispatchers.IO) {
        val account = _account.value ?: refreshAccount() ?: error("请先登录网易云音乐")
        val result = eapi(
            "/api/user/playlist",
            JSONObject().put("uid", account.userId.toString()).put("limit", "1000").put("offset", "0").put("includeVideo", "true"),
        )
        result.optJSONArray("playlist").objects().map { row ->
            val creator = row.optJSONObject("creator")
            NeteasePlaylistSummary(
                id = row.optLong("id"),
                name = row.optString("name"),
                coverUrl = row.optString("coverImgUrl").https(),
                trackCount = row.optInt("trackCount"),
                creator = creator?.optString("nickname").orEmpty(),
                liked = row.optInt("specialType") == 5,
                own = creator?.optLong("userId") == account.userId,
            )
        }
    }

    suspend fun playlistSongs(id: Long): List<NeteaseSong> = withContext(Dispatchers.IO) {
        val detail = eapi("/api/v6/playlist/detail", JSONObject().put("id", id.toString()).put("n", "100000").put("s", "8"))
        val playlist = detail.optJSONObject("playlist") ?: error(detail.optString("message").ifBlank { "歌单不可用" })
        val ids = playlist.optJSONArray("trackIds").objects().map { it.optLong("id") }.filter { it > 0 }
        val privileges = HashMap<Long, JSONObject>()
        detail.optJSONArray("privileges").objects().forEach { privileges[it.optLong("id")] = it }
        val songs = java.util.concurrent.ConcurrentHashMap<Long, NeteaseSong>()
        playlist.optJSONArray("tracks").objects().forEach { row -> song(row, privileges[row.optLong("id")])?.let { songs[it.id] = it } }
        val missing = ids.filter { !songs.containsKey(it) }.chunked(500)
        kotlinx.coroutines.coroutineScope {
            val gate = kotlinx.coroutines.sync.Semaphore(4)
            missing.map { chunk ->
                async { gate.withPermit { songDetails(chunk).forEach { songs[it.id] = it } } }
            }.forEach { it.await() }
        }
        ids.mapNotNull(songs::get).also { runCatching { writeCache(id, it) } }
    }

    private val listCache = java.io.File(context.cacheDir, "ncm-lists").apply { mkdirs() }

    suspend fun cachedSongs(playlistId: Long): List<NeteaseSong>? = withContext(Dispatchers.IO) {
        val file = java.io.File(listCache, "$playlistId.json")
        if (!file.isFile) return@withContext null
        runCatching {
            JSONArray(file.readText()).objects().map { row ->
                NeteaseSong(
                    id = row.optLong("i"),
                    title = row.optString("t"),
                    artists = row.optJSONArray("a").let { a -> (0 until (a?.length() ?: 0)).map { a!!.optString(it) } },
                    album = row.optString("al"),
                    durationMs = row.optLong("d"),
                    coverUrl = row.optString("c").takeIf(String::isNotBlank),
                    unavailable = row.optBoolean("u"),
                    albumId = row.optLong("ai"),
                    artistIds = row.optJSONArray("ar").let { a -> (0 until (a?.length() ?: 0)).map { a!!.optLong(it) } },
                    mvId = row.optLong("mv"),
                )
            }
        }.getOrNull()
    }

    private fun writeCache(playlistId: Long, songs: List<NeteaseSong>) {
        val rows = JSONArray()
        songs.forEach { s ->
            rows.put(
                JSONObject().put("i", s.id).put("t", s.title).put("a", JSONArray(s.artists)).put("al", s.album)
                    .put("d", s.durationMs).put("c", s.coverUrl.orEmpty()).put("u", s.unavailable)
                    .put("ai", s.albumId).put("ar", JSONArray(s.artistIds)).put("mv", s.mvId),
            )
        }
        val file = java.io.File(listCache, "$playlistId.json")
        val temp = java.io.File(listCache, "$playlistId.json.tmp")
        temp.writeText(rows.toString())
        temp.renameTo(file)
    }

    suspend fun songDetails(ids: List<Long>): List<NeteaseSong> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val c = JSONArray().apply { ids.forEach { put(JSONObject().put("id", it)) } }
        val result = eapi("/api/v3/song/detail", JSONObject().put("c", c.toString()))
        val privileges = HashMap<Long, JSONObject>()
        result.optJSONArray("privileges").objects().forEach { privileges[it.optLong("id")] = it }
        result.optJSONArray("songs").objects().mapNotNull { song(it, privileges[it.optLong("id")]) }
    }

    suspend fun likedIds(): Set<Long> = withContext(Dispatchers.IO) {
        val account = _account.value ?: return@withContext emptySet()
        val result = eapi("/api/song/like/get", JSONObject().put("uid", account.userId.toString()))
        val ids = result.optJSONArray("ids") ?: return@withContext emptySet()
        (0 until ids.length()).mapTo(HashSet()) { ids.optLong(it) }
    }

    suspend fun setLiked(id: Long, liked: Boolean) = withContext(Dispatchers.IO) {
        val result = eapi("/api/radio/like", JSONObject().put("alg", "itembased").put("trackId", id.toString()).put("like", liked.toString()).put("time", "3"))
        require(result.optInt("code") == 200) { result.optString("message").ifBlank { "操作失败" } }
    }

    suspend fun userStatus(uid: Long): String? = withContext(Dispatchers.IO) {
        runCatching {
            val result = api("/api/social/user/status", JSONObject().put("visitorId", uid.toString()))
            fun find(node: Any?, depth: Int): String? {
                if (depth > 4) return null
                when (node) {
                    is JSONObject -> {
                        for (key in listOf("content", "statusText", "text", "title", "desc")) {
                            val value = node.opt(key)
                            if (value is String && value.isNotBlank() && value != "null") return value
                        }
                        node.keys().forEach { key -> find(node.opt(key), depth + 1)?.let { return it } }
                    }
                    is JSONArray -> for (i in 0 until node.length()) find(node.opt(i), depth + 1)?.let { return it }
                }
                return null
            }
            find(result.opt("data"), 0)
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() && it.length <= 40 }
    }

    suspend fun song(id: Long): NeteaseSong? = songDetails(listOf(id)).firstOrNull()

    suspend fun addToPlaylist(playlistId: Long, songIds: List<Long>): Boolean = withContext(Dispatchers.IO) {
        val result = eapi(
            "/api/playlist/manipulate/tracks",
            JSONObject().put("op", "add").put("pid", playlistId.toString())
                .put("trackIds", JSONArray(songIds).toString()).put("imme", "true"),
        )
        when (val code = result.optInt("code")) {
            200 -> true
            502 -> false
            else -> error(result.optString("message").ifBlank { "收藏失败（$code）" })
        }
    }

    suspend fun removeFromPlaylist(playlistId: Long, songIds: List<Long>) = withContext(Dispatchers.IO) {
        val result = eapi(
            "/api/playlist/manipulate/tracks",
            JSONObject().put("op", "del").put("pid", playlistId.toString())
                .put("trackIds", JSONArray(songIds).toString()).put("imme", "true"),
        )
        require(result.optInt("code") == 200) { result.optString("message").ifBlank { "移除失败" } }
    }

    suspend fun redCount(songId: Long): Long? = withContext(Dispatchers.IO) {
        runCatching {
            api("/api/song/red/count", JSONObject().put("songId", songId.toString()))
                .optJSONObject("data")?.optLong("count", -1)?.takeIf { it >= 0 }
        }.getOrNull()
    }

    suspend fun savedAlbums(offset: Int = 0): Pair<List<NeteasePlaylistSummary>, Boolean> = withContext(Dispatchers.IO) {
        val result = eapi("/api/album/sublist", JSONObject().put("limit", "50").put("offset", offset.toString()).put("total", "true"))
        val rows = result.optJSONArray("data").objects().map { row ->
            NeteasePlaylistSummary(
                id = row.optLong("id"),
                name = row.optString("name"),
                coverUrl = row.optString("picUrl").https(),
                trackCount = row.optInt("size"),
                creator = row.optJSONArray("artists").objects().joinToString(" / ") { it.optString("name") },
                liked = false,
                own = false,
                kind = NeteaseCollectionKind.ALBUM,
            )
        }
        rows to result.optBoolean("hasMore")
    }

    suspend fun albumSaved(albumId: Long): Boolean? = withContext(Dispatchers.IO) {
        if (cookie == null) return@withContext null
        runCatching {
            val result = api("/api/album/detail/dynamic", JSONObject().put("id", albumId.toString()))
            if (result.optInt("code") == 200 && result.has("isSub")) result.optBoolean("isSub") else null
        }.getOrNull() ?: runCatching {
            var offset = 0
            repeat(10) {
                val (rows, more) = savedAlbums(offset)
                if (rows.any { it.id == albumId }) return@runCatching true
                if (!more || rows.isEmpty()) return@runCatching false
                offset += rows.size
            }
            null
        }.getOrNull()
    }

    suspend fun setAlbumSaved(albumId: Long, saved: Boolean) = withContext(Dispatchers.IO) {
        val result = eapi(if (saved) "/api/album/sub" else "/api/album/unsub", JSONObject().put("id", albumId.toString()))
        require(result.optInt("code") == 200) { result.optString("message").ifBlank { "操作失败" } }
    }

    suspend fun setPlaylistSaved(playlistId: Long, saved: Boolean) = withContext(Dispatchers.IO) {
        val result = eapi(if (saved) "/api/playlist/subscribe" else "/api/playlist/unsubscribe", JSONObject().put("id", playlistId.toString()))
        require(result.optInt("code") == 200) { result.optString("message").ifBlank { "操作失败" } }
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        val result = eapi("/api/playlist/remove", JSONObject().put("ids", "[$playlistId]"))
        require(result.optInt("code") == 200) { result.optString("message").ifBlank { "删除失败" } }
    }

    suspend fun updatePlaylistTags(playlistId: Long, tags: List<String>) = withContext(Dispatchers.IO) {
        val result = eapi("/api/playlist/tags/update", JSONObject().put("id", playlistId.toString()).put("tags", tags.take(3).joinToString(";")))
        require(result.optInt("code") == 200) { result.optString("message").ifBlank { "修改分类失败" } }
    }

    suspend fun reorderPlaylists(ids: List<Long>) = withContext(Dispatchers.IO) {
        val result = eapi("/api/playlist/order/update", JSONObject().put("ids", JSONArray(ids).toString()))
        require(result.optInt("code") == 200) { result.optString("message").ifBlank { "保存排序失败" } }
    }

    suspend fun createPlaylist(name: String, private: Boolean = false): Long = withContext(Dispatchers.IO) {
        val result = eapi("/api/playlist/create", JSONObject().put("name", name).put("privacy", if (private) "10" else "0").put("type", "NORMAL"))
        result.optJSONObject("playlist")?.optLong("id")?.takeIf { it > 0 } ?: error(result.optString("message").ifBlank { "创建失败" })
    }

    suspend fun playRanking(allTime: Boolean, userId: Long? = null): List<Pair<NeteaseSong, Int>> = withContext(Dispatchers.IO) {
        val uid = userId ?: _account.value?.userId ?: error("请先登录网易云音乐")
        val result = eapi("/api/v1/play/record", JSONObject().put("uid", uid.toString()).put("type", if (allTime) "0" else "1"))
        if (result.optInt("code") != 200) error(result.optString("message").ifBlank { "听歌排行不可见" })
        result.optJSONArray(if (allTime) "allData" else "weekData").objects().mapNotNull { row ->
            val song = row.optJSONObject("song")?.let { song(it, it.optJSONObject("privilege")) } ?: return@mapNotNull null
            song to row.optInt("playCount")
        }
    }

    data class Podcast(val id: Long, val name: String, val coverUrl: String?, val host: String, val programCount: Int, val note: String = "")

    data class PodcastEpisode(val id: Long, val name: String, val coverUrl: String?, val durationMs: Long, val time: Long, val listeners: Long, val song: NeteaseSong?)

    private fun podcast(row: JSONObject) = Podcast(
        id = row.optLong("id"),
        name = row.optString("name"),
        coverUrl = (row.optString("picUrl").https() ?: row.optString("intervenePicUrl").https())?.let { "$it?param=300y300" },
        host = row.optJSONObject("dj")?.optString("nickname").orEmpty(),
        programCount = row.optInt("programCount"),
        note = row.optString("rcmdtext").takeIf { it.isNotBlank() && it != "null" } ?: row.optString("desc").takeIf { it != "null" }.orEmpty(),
    )

    suspend fun subscribedPodcasts(): List<Podcast> = withContext(Dispatchers.IO) {
        if (cookie == null) return@withContext emptyList()
        eapi("/api/djradio/get/subed", JSONObject().put("limit", "100").put("offset", "0").put("total", "true"))
            .optJSONArray("djRadios").objects().map(::podcast)
    }

    suspend fun hotPodcasts(offset: Int = 0): List<Podcast> = withContext(Dispatchers.IO) {
        api("/api/djradio/hot/v1", JSONObject().put("limit", "30").put("offset", offset.toString()))
            .optJSONArray("djRadios").objects().map(::podcast)
    }

    suspend fun podcastEpisodes(radioId: Long, offset: Int = 0): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        api("/api/dj/program/byradio", JSONObject().put("radioId", radioId.toString()).put("limit", "50").put("offset", offset.toString()).put("asc", "false"))
            .optJSONArray("programs").objects().map { row ->
                PodcastEpisode(
                    id = row.optLong("id"),
                    name = row.optString("name"),
                    coverUrl = row.optString("coverUrl").https()?.let { "$it?param=300y300" },
                    durationMs = row.optLong("duration"),
                    time = row.optLong("createTime"),
                    listeners = row.optLong("listenerCount"),
                    song = row.optJSONObject("mainSong")?.let { main ->
                        song(main, null)?.copy(coverUrl = row.optString("coverUrl").https()?.let { "$it?param=600y600" })
                    },
                )
            }
    }

    suspend fun setPodcastSubscribed(radioId: Long, subscribed: Boolean) = withContext(Dispatchers.IO) {
        val result = eapi(if (subscribed) "/api/djradio/sub" else "/api/djradio/unsub", JSONObject().put("id", radioId.toString()))
        require(result.optInt("code") == 200) { result.optString("message").ifBlank { "操作失败" } }
    }

    suspend fun mvUrl(mvId: Long, resolution: Int = 1080): String = withContext(Dispatchers.IO) {
        val data = api("/api/song/enhance/play/mv/url", JSONObject().put("id", mvId.toString()).put("r", resolution.toString())).optJSONObject("data")
        data?.optString("url")?.takeIf { it.startsWith("http") }?.replaceFirst("http://", "https://")
            ?: error(data?.optString("msg")?.takeIf(String::isNotBlank) ?: "这个 MV 暂时无法播放")
    }

    suspend fun mvDetail(mvId: Long): Triple<String, String, String?> = withContext(Dispatchers.IO) {
        val data = api("/api/v1/mv/detail", JSONObject().put("id", mvId.toString())).optJSONObject("data") ?: error("MV 不存在")
        Triple(data.optString("name"), data.optString("artistName"), data.optString("cover").https())
    }

    suspend fun search(keyword: String, type: Int, offset: Int = 0): NeteaseSearchResult = withContext(Dispatchers.IO) {
        val result = api(
            "/api/cloudsearch/pc",
            JSONObject().put("s", keyword).put("type", type.toString()).put("limit", "30").put("offset", offset.toString()).put("total", "true"),
        ).optJSONObject("result") ?: return@withContext NeteaseSearchResult()
        when (type) {
            10 -> NeteaseSearchResult(albums = result.optJSONArray("albums").objects().map(::albumSummary), total = result.optInt("albumCount"))
            100 -> NeteaseSearchResult(
                artists = result.optJSONArray("artists").objects().map { row ->
                    NeteaseArtist(row.optLong("id"), row.optString("name"), (row.optString("picUrl").https() ?: row.optString("img1v1Url").https())?.let { "$it?param=300y300" }, row.optInt("albumSize"))
                },
                total = result.optInt("artistCount"),
            )
            1000 -> NeteaseSearchResult(playlists = result.optJSONArray("playlists").objects().map(::playlistSummary), total = result.optInt("playlistCount"))
            else -> NeteaseSearchResult(
                songs = result.optJSONArray("songs").objects().mapNotNull { song(it, it.optJSONObject("privilege")) },
                total = result.optInt("songCount"),
            )
        }
    }

    suspend fun hotSearches(): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            api("/api/search/hot", JSONObject().put("type", "1111")).optJSONObject("result")
                ?.optJSONArray("hots").objects().map { it.optString("first") }.filter(String::isNotBlank)
        }.getOrNull().orEmpty()
    }

    suspend fun playlistCategories(): List<Pair<String, List<String>>> = withContext(Dispatchers.IO) {
        val result = api("/api/playlist/catalogue", JSONObject())
        val groups = result.optJSONObject("categories")
        val sub = result.optJSONArray("sub").objects()
        (0 until 10).mapNotNull { index ->
            val name = groups?.optString(index.toString())?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            name to sub.filter { it.optInt("category") == index }.map { it.optString("name") }
        }
    }

    suspend fun categoryPlaylists(category: String, offset: Int = 0): List<NeteasePlaylistSummary> = withContext(Dispatchers.IO) {
        api(
            "/api/playlist/list",
            JSONObject().put("cat", category).put("order", "hot").put("limit", "36").put("offset", offset.toString()).put("total", "true"),
        ).optJSONArray("playlists").objects().map(::playlistSummary)
    }

    suspend fun charts(): List<NeteasePlaylistSummary> = withContext(Dispatchers.IO) {
        api("/api/toplist", JSONObject()).optJSONArray("list").objects().map { row ->
            NeteasePlaylistSummary(
                id = row.optLong("id"),
                name = row.optString("name"),
                coverUrl = row.optString("coverImgUrl").https(),
                trackCount = row.optInt("trackCount"),
                creator = "",
                liked = false,
                own = false,
                playCount = row.optLong("playCount"),
                kind = NeteaseCollectionKind.CHART,
                note = row.optString("updateFrequency").takeIf { it.isNotBlank() && it != "null" }.orEmpty(),
            )
        }
    }

    suspend fun recommendedPlaylists(): List<NeteasePlaylistSummary> = withContext(Dispatchers.IO) {
        if (cookie == null) return@withContext emptyList()
        eapi("/api/v1/discovery/recommend/resource", JSONObject()).optJSONArray("recommend").objects().map { row ->
            NeteasePlaylistSummary(
                id = row.optLong("id"),
                name = row.optString("name"),
                coverUrl = row.optString("picUrl").https(),
                trackCount = row.optInt("trackCount"),
                creator = row.optJSONObject("creator")?.optString("nickname").orEmpty(),
                liked = false,
                own = false,
                playCount = row.optLong("playcount"),
                note = row.optString("copywriter").takeIf { it != "null" }.orEmpty(),
            )
        }
    }

    suspend fun dailySongs(): List<NeteaseSong> = withContext(Dispatchers.IO) {
        val data = eapi("/api/v3/discovery/recommend/songs", JSONObject()).optJSONObject("data") ?: error("请先登录网易云音乐")
        val privileges = HashMap<Long, JSONObject>()
        data.optJSONArray("privileges").objects().forEach { privileges[it.optLong("id")] = it }
        data.optJSONArray("dailySongs").objects().mapNotNull { row -> song(row, row.optJSONObject("privilege") ?: privileges[row.optLong("id")]) }
    }

    suspend fun albumSongs(id: Long): List<NeteaseSong> = withContext(Dispatchers.IO) {
        val result = api("/api/v1/album/$id", JSONObject())
        result.optJSONArray("songs").objects().mapNotNull { song(it, it.optJSONObject("privilege")) }
    }

    suspend fun artistTopSongs(id: Long): List<NeteaseSong> = withContext(Dispatchers.IO) {
        api("/api/artist/top/song", JSONObject().put("id", id.toString())).optJSONArray("songs").objects()
            .mapNotNull { song(it, it.optJSONObject("privilege")) }
    }

    private fun playlistSummary(row: JSONObject) = NeteasePlaylistSummary(
        id = row.optLong("id"),
        name = row.optString("name"),
        coverUrl = row.optString("coverImgUrl").https(),
        trackCount = row.optInt("trackCount"),
        creator = row.optJSONObject("creator")?.optString("nickname").orEmpty(),
        liked = false,
        own = false,
        playCount = row.optLong("playCount"),
    )

    private fun albumSummary(row: JSONObject) = NeteasePlaylistSummary(
        id = row.optLong("id"),
        name = row.optString("name"),
        coverUrl = row.optString("picUrl").https(),
        trackCount = row.optInt("size"),
        creator = row.optJSONObject("artist")?.optString("name").orEmpty(),
        liked = false,
        own = false,
        kind = NeteaseCollectionKind.ALBUM,
        note = row.optLong("publishTime").takeIf { it > 0 }?.let {
            java.text.SimpleDateFormat("yyyy", Locale.CHINA).format(java.util.Date(it))
        }.orEmpty(),
    )

    suspend fun comments(songId: Long, offset: Int = 0, before: Long = 0): NeteaseComments = withContext(Dispatchers.IO) {
        val params = JSONObject().put("rid", "R_SO_4_$songId").put("limit", "30").put("offset", offset.toString())
        if (before > 0) params.put("beforeTime", before.toString())
        val result = api("/api/v1/resource/comments/R_SO_4_$songId", params)
        NeteaseComments(
            hot = result.optJSONArray("hotComments").objects().map(::comment),
            latest = result.optJSONArray("comments").objects().map(::comment),
            total = result.optInt("total"),
            more = result.optBoolean("more"),
        )
    }

    suspend fun postComment(songId: Long, content: String) = withContext(Dispatchers.IO) {
        require(cookie != null) { "请先登录网易云音乐" }
        val result = eapi("/api/resource/comments/add", JSONObject().put("threadId", "R_SO_4_$songId").put("content", content))
        require(result.optInt("code") == 200) { result.optString("message").ifBlank { "评论发送失败" } }
    }

    suspend fun likeComment(songId: Long, commentId: Long, liked: Boolean) = withContext(Dispatchers.IO) {
        require(cookie != null) { "请先登录网易云音乐" }
        val result = eapi(
            "/api/v1/comment/like",
            JSONObject()
                .put("threadId", "R_SO_4_$songId")
                .put("commentId", commentId.toString())
                .put("type", "0")
                .put("t", if (liked) "1" else "0"),
        )
        require(result.optInt("code") == 200) { result.optString("message").ifBlank { "操作失败" } }
    }

    suspend fun commentReplies(songId: Long, parentId: Long, cursor: Long = -1): Pair<List<NeteaseComment>, Long?> = withContext(Dispatchers.IO) {
        val data = api(
            "/api/resource/comment/floor/get",
            JSONObject().put("parentCommentId", parentId.toString()).put("threadId", "R_SO_4_$songId").put("time", cursor.toString()).put("limit", "20"),
        ).optJSONObject("data") ?: return@withContext emptyList<NeteaseComment>() to null
        val next = data.optLong("time").takeIf { data.optBoolean("hasMore") && it > 0 }
        data.optJSONArray("comments").objects().map(::comment) to next
    }

    suspend fun replyComment(songId: Long, commentId: Long, content: String) = withContext(Dispatchers.IO) {
        require(cookie != null) { "请先登录网易云音乐" }
        val result = eapi(
            "/api/resource/comments/reply",
            JSONObject().put("threadId", "R_SO_4_$songId").put("commentId", commentId.toString()).put("content", content),
        )
        require(result.optInt("code") == 200) { result.optString("message").ifBlank { "回复发送失败" } }
    }

    suspend fun userProfile(userId: Long): NeteaseProfile = withContext(Dispatchers.IO) {
        val result = api("/api/v1/user/detail/$userId", JSONObject())
        val profile = result.optJSONObject("profile") ?: error(result.optString("message").ifBlank { "找不到这个用户" })
        NeteaseProfile(
            userId = profile.optLong("userId"),
            nickname = profile.optString("nickname"),
            avatarUrl = profile.optString("avatarUrl").https()?.let { "$it?param=300y300" },
            backgroundUrl = profile.optString("backgroundUrl").https()?.let { "$it?param=1080y600" },
            signature = profile.optString("signature").takeIf { it != "null" }.orEmpty(),
            followers = profile.optInt("followeds"),
            follows = profile.optInt("follows"),
            level = result.optInt("level"),
            listened = result.optInt("listenSongs"),
            identity = result.optJSONObject("identify")?.optString("imageDesc")?.takeIf { it != "null" }.orEmpty(),
            followed = profile.optBoolean("followed"),
            gender = profile.optInt("gender"),
            birthday = profile.optLong("birthday").takeIf { it > 0 } ?: 0L,
            days = result.optInt("createDays").takeIf { it > 0 }
                ?: profile.optLong("createTime").takeIf { it > 0 }?.let { ((System.currentTimeMillis() - it) / 86_400_000L).toInt() } ?: 0,
            artistId = profile.optLong("artistId"),
            links = result.optJSONArray("bindings").objects().mapNotNull { binding ->
                val url = binding.optString("url").takeIf { it.startsWith("http") } ?: return@mapNotNull null
                if (binding.optBoolean("expired")) return@mapNotNull null
                linkLabel(url, binding.optInt("type")) to url
            }.distinctBy { it.second },
            eventCount = profile.optInt("eventCount"),
        )
    }

    private fun linkLabel(url: String, type: Int): String {
        val host = runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")
        return when {
            host.contains("weibo") -> "微博"
            host.contains("douyin") -> "抖音"
            host.contains("bilibili") || host.contains("b23.tv") -> "哔哩哔哩"
            host.contains("xiaohongshu") -> "小红书"
            host.contains("douban") -> "豆瓣"
            host.contains("qq.com") -> "QQ"
            host.contains("instagram") -> "Instagram"
            host.contains("twitter") || host.contains("x.com") -> "X"
            host.contains("youtube") -> "YouTube"
            type == 2 -> "微博"
            else -> host.removePrefix("www.").ifBlank { "链接" }
        }
    }

    suspend fun userFollows(uid: Long, offset: Int = 0): Pair<List<NeteaseFriend>, Boolean> = withContext(Dispatchers.IO) {
        val result = api("/api/user/getfollows/$uid", JSONObject().put("offset", offset.toString()).put("limit", "30").put("order", "true"))
        result.optJSONArray("follow").objects().map(::friend) to result.optBoolean("more")
    }

    suspend fun userFollowers(uid: Long, offset: Int = 0): Pair<List<NeteaseFriend>, Boolean> = withContext(Dispatchers.IO) {
        val result = api(
            "/api/user/getfolloweds/$uid",
            JSONObject().put("userId", uid.toString()).put("time", "0").put("limit", "30").put("offset", offset.toString()).put("getcounts", "true"),
        )
        result.optJSONArray("followeds").objects().map(::friend) to result.optBoolean("more")
    }

    suspend fun setFollowUser(uid: Long, follow: Boolean) = withContext(Dispatchers.IO) {
        require(cookie != null) { "请先登录网易云音乐" }
        val path = "/api/user/${if (follow) "follow" else "delfollow"}/$uid"
        fun JSONObject.ok() = optInt("code") == 200 || optInt("code") == 201
        // 关注接口以网页端 weapi 为准，App 端 eapi 兜底；api() 会把 eapi 的成功响应当成失败再发一次网页请求
        val web = runCatching { weapiBlocking(path, JSONObject()) }
        if (web.getOrNull()?.ok() == true) return@withContext
        val app = runCatching { eapiBlocking(path, JSONObject().put("followId", uid.toString())) }
        if (app.getOrNull()?.ok() == true) return@withContext
        val message = listOfNotNull(web.getOrNull(), app.getOrNull())
            .firstNotNullOfOrNull { result -> result.optString("message").takeIf { it.isNotBlank() && it != "null" } ?: result.optString("msg").takeIf { it.isNotBlank() && it != "null" } }
            ?: web.exceptionOrNull()?.message
        error(message ?: if (follow) "关注失败" else "取消关注失败")
    }

    private fun friend(row: JSONObject) = NeteaseFriend(
        userId = row.optLong("userId"),
        nickname = row.optString("nickname"),
        avatarUrl = row.optString("avatarUrl").https()?.let { "$it?param=100y100" },
        signature = row.optString("signature").takeIf { it != "null" }.orEmpty(),
        followed = row.optBoolean("followed"),
    )

    suspend fun collection(target: NeteasePlaylistSummary): NeteaseCollection = withContext(Dispatchers.IO) {
        when (target.kind) {
            NeteaseCollectionKind.ALBUM -> {
                val result = api("/api/v1/album/${target.id}", JSONObject())
                val album = result.optJSONObject("album") ?: error(result.optString("message").ifBlank { "专辑不可用" })
                val artist = album.optJSONObject("artist")
                NeteaseCollection(
                    summary = albumSummary(album).copy(trackCount = album.optInt("size")),
                    description = album.optString("description").takeIf { it != "null" }.orEmpty().trim(),
                    songs = result.optJSONArray("songs").objects().mapNotNull { song(it, it.optJSONObject("privilege")) },
                    artistId = artist?.optLong("id") ?: 0L,
                    tags = listOfNotNull(album.optString("company").takeIf { it.isNotBlank() && it != "null" }, album.optString("subType").takeIf { it.isNotBlank() && it != "null" }),
                )
            }
            NeteaseCollectionKind.ARTIST -> NeteaseCollection(target, "", artistTopSongs(target.id), artistId = target.id)
            NeteaseCollectionKind.DAILY -> NeteaseCollection(target, "根据你的音乐口味生成，每天 6:00 更新", dailySongs())
            else -> {
                val detail = eapi("/api/v6/playlist/detail", JSONObject().put("id", target.id.toString()).put("n", "100000").put("s", "8"))
                val playlist = detail.optJSONObject("playlist") ?: error(detail.optString("message").ifBlank { "歌单不可用" })
                val creator = playlist.optJSONObject("creator")
                NeteaseCollection(
                    summary = playlistSummary(playlist).copy(kind = target.kind, liked = playlist.optInt("specialType") == 5),
                    description = playlist.optString("description").takeIf { it != "null" }.orEmpty().trim(),
                    songs = playlistSongs(target.id),
                    ownerId = creator?.optLong("userId") ?: 0L,
                    tags = playlist.optJSONArray("tags")?.let { tags -> (0 until tags.length()).map { tags.optString(it) } }.orEmpty(),
                    saved = playlist.optBoolean("subscribed"),
                )
            }
        }
    }

    fun collectionStream(target: NeteasePlaylistSummary): Flow<NeteaseCollection> = flow {
        when (target.kind) {
            NeteaseCollectionKind.PLAYLIST, NeteaseCollectionKind.CHART -> {
                val detail = eapi("/api/v6/playlist/detail", JSONObject().put("id", target.id.toString()).put("n", "0").put("s", "8"))
                val playlist = detail.optJSONObject("playlist") ?: error(detail.optString("message").ifBlank { "歌单不可用" })
                val creator = playlist.optJSONObject("creator")
                val summary = playlistSummary(playlist).copy(kind = target.kind, liked = playlist.optInt("specialType") == 5)
                val description = playlist.optString("description").takeIf { it != "null" }.orEmpty().trim()
                val tags = playlist.optJSONArray("tags")?.let { rows -> (0 until rows.length()).map { rows.optString(it) } }.orEmpty()
                val ownerId = creator?.optLong("userId") ?: 0L
                val saved = playlist.optBoolean("subscribed")
                val ids = playlist.optJSONArray("trackIds").objects().map { it.optLong("id") }.filter { it > 0 }
                val privileges = HashMap<Long, JSONObject>()
                detail.optJSONArray("privileges").objects().forEach { privileges[it.optLong("id")] = it }
                val songs = java.util.concurrent.ConcurrentHashMap<Long, NeteaseSong>()
                playlist.optJSONArray("tracks").objects().forEach { row -> song(row, privileges[row.optLong("id")])?.let { songs[it.id] = it } }
                val firstIds = ids.take(FIRST_BATCH)
                val missingFirst = firstIds.filter { !songs.containsKey(it) }
                if (missingFirst.isNotEmpty()) songDetails(missingFirst).forEach { songs[it.id] = it }
                fun snapshot() = ids.mapNotNull(songs::get)
                emit(NeteaseCollection(summary, description, snapshot(), ownerId = ownerId, tags = tags, complete = false, saved = saved))
                val restIds = ids.drop(FIRST_BATCH).filter { !songs.containsKey(it) }
                val chunks = restIds.chunked(REST_CHUNK)
                chunks.forEachIndexed { index, chunk ->
                    songDetails(chunk).forEach { songs[it.id] = it }
                    emit(NeteaseCollection(summary, description, snapshot(), ownerId = ownerId, tags = tags, complete = index == chunks.lastIndex, saved = saved))
                }
                if (chunks.isEmpty()) emit(NeteaseCollection(summary, description, snapshot(), ownerId = ownerId, tags = tags, saved = saved))
                runCatching { writeCache(target.id, snapshot()) }
            }
            else -> emit(collection(target))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun userPlaylists(userId: Long): List<NeteasePlaylistSummary> = withContext(Dispatchers.IO) {
        api("/api/user/playlist", JSONObject().put("uid", userId.toString()).put("limit", "100").put("offset", "0").put("includeVideo", "true"))
            .optJSONArray("playlist").objects().map { row ->
                playlistSummary(row).copy(
                    liked = row.optInt("specialType") == 5,
                    own = row.optJSONObject("creator")?.optLong("userId") == userId,
                )
            }
    }
    private fun comment(row: JSONObject): NeteaseComment {
        val user = row.optJSONObject("user")
        val replied = row.optJSONArray("beReplied").objects().firstOrNull()
        return NeteaseComment(
            id = row.optLong("commentId"),
            user = user?.optString("nickname").orEmpty(),
            userId = user?.optLong("userId") ?: 0L,
            replyCount = row.optJSONObject("showFloorComment")?.optInt("replyCount") ?: 0,
            avatarUrl = user?.optString("avatarUrl")?.https()?.let { "$it?param=100y100" },
            content = row.optString("content"),
            time = row.optLong("time"),
            likedCount = row.optInt("likedCount"),
            liked = row.optBoolean("liked"),
            replyTo = replied?.let { "@${it.optJSONObject("user")?.optString("nickname").orEmpty()}：${it.optString("content")}" },
        )
    }

    suspend fun messageThreads(): List<NeteaseThread> = withContext(Dispatchers.IO) {
        require(cookie != null) { "请先登录网易云音乐" }
        eapi("/api/msg/private/users", JSONObject().put("limit", "50").put("offset", "0")).optJSONArray("msgs").objects().map { row ->
            val user = row.optJSONObject("fromUser")
            NeteaseThread(
                userId = user?.optLong("userId") ?: 0L,
                nickname = user?.optString("nickname").orEmpty(),
                avatarUrl = user?.optString("avatarUrl")?.https()?.let { "$it?param=100y100" },
                lastMessage = messageText(row.optString("lastMsg")),
                time = row.optLong("lastMsgTime"),
                unread = row.optInt("newMsgCount").takeIf { row.optLong("lastMsgTime") > readUntil(user?.optLong("userId") ?: 0L) } ?: 0,
            )
        }
    }

    suspend fun scrobble(songId: Long, sourceId: Long, seconds: Int) = withContext(Dispatchers.IO) {
        if (cookie == null || songId <= 0) return@withContext
        val start = JSONObject().put("action", "startplay").put(
            "json",
            JSONObject().put("id", songId.toString()).put("type", "song").put("mainsite", "1").put("mainsiteWeb", "1").put("content", "id=$sourceId"),
        )
        val play = JSONObject().put("action", "play").put(
            "json",
            JSONObject().put("download", 0).put("end", "playend").put("id", songId.toString()).put("sourceId", sourceId.toString())
                .put("time", seconds).put("type", "song").put("wifi", 0).put("source", "list").put("mainsite", "1").put("mainsiteWeb", "1")
                .put("content", "id=$sourceId"),
        )
        for (log in listOf(start, play)) {
            runCatching {
                eapiBlocking("/api/feedback/weblog", JSONObject().put("logs", JSONArray().put(log).toString()), "https://clientlog.music.163.com")
            }
        }
    }

    private fun readUntil(userId: Long): Long = runCatching { preferences.getLong("read_$userId", 0L) }.getOrDefault(0L)

    fun markThreadRead(userId: Long, time: Long = System.currentTimeMillis()) {
        if (userId > 0) preferences.edit().putLong("read_$userId", maxOf(time, readUntil(userId))).apply()
    }

    private val statusCache = java.util.concurrent.ConcurrentHashMap<Long, Pair<Long, String?>>()

    suspend fun cachedUserStatus(uid: Long): String? {
        statusCache[uid]?.takeIf { System.currentTimeMillis() - it.first < 300_000 }?.let { return it.second }
        val value = userStatus(uid)
        statusCache[uid] = System.currentTimeMillis() to value
        return value
    }

    suspend fun messageHistory(userId: Long): List<NeteaseMessage> = withContext(Dispatchers.IO) {
        val me = _account.value?.userId ?: refreshAccount()?.userId ?: error("请先登录网易云音乐")
        markThreadRead(userId)
        eapi("/api/msg/private/history", JSONObject().put("userId", userId.toString()).put("limit", "50").put("time", "-1"))
            .optJSONArray("msgs").objects()
            .map { row ->
                val raw = row.optString("msg")
                val card = messageCard(raw)
                val text = if (card != null) runCatching { JSONObject(raw).optString("msg").takeIf { it != "null" }.orEmpty() }.getOrDefault("") else messageText(raw)
                NeteaseMessage(fromMe = row.optJSONObject("fromUser")?.optLong("userId") == me, text = text, time = row.optLong("time"), card = card, raw = raw)
            }
            .sortedBy { it.time }
    }

    suspend fun sendMessage(userId: Long, text: String) = withContext(Dispatchers.IO) {
        require(cookie != null) { "请先登录网易云音乐" }
        val result = eapi("/api/msg/private/send", JSONObject().put("type", "text").put("msg", text).put("userIds", "[$userId]"))
        require(result.optInt("code") == 200) { result.optString("message").ifBlank { "发送失败" } }
    }

    suspend fun shareSongToFriends(songId: Long, userIds: List<Long>, note: String) = withContext(Dispatchers.IO) {
        require(cookie != null) { "请先登录网易云音乐" }
        require(userIds.isNotEmpty()) { "请选择好友" }
        val result = eapi(
            "/api/msg/private/send",
            JSONObject().put("id", songId.toString()).put("msg", note).put("type", "song").put("userIds", userIds.joinToString(",", "[", "]")),
        )
        require(result.optInt("code") == 200) { result.optString("message").ifBlank { "分享失败" } }
    }

    suspend fun shareSongToFeed(songId: Long, text: String) = withContext(Dispatchers.IO) {
        require(cookie != null) { "请先登录网易云音乐" }
        val result = eapi("/api/share/friends/resource", JSONObject().put("type", "song").put("msg", text.take(140)).put("id", songId.toString()))
        require(result.optInt("code") == 200) { result.optString("message").ifBlank { "分享失败" } }
    }

    suspend fun follows(offset: Int = 0): List<NeteaseFriend> = withContext(Dispatchers.IO) {
        val me = _account.value?.userId ?: refreshAccount()?.userId ?: error("请先登录网易云音乐")
        eapi("/api/user/getfollows/$me", JSONObject().put("offset", offset.toString()).put("limit", "100").put("order", "true"))
            .optJSONArray("follow").objects().map { row ->
                NeteaseFriend(row.optLong("userId"), row.optString("nickname"), row.optString("avatarUrl").https()?.let { "$it?param=100y100" }, row.optString("signature").takeIf { it != "null" }.orEmpty())
            }
    }

    fun songLink(songId: Long) = "https://music.163.com/song?id=$songId"

    fun target(url: String): NeteaseTarget? = NeteaseLinks.resolve(url)

    private fun messageCard(raw: String): NeteaseCard? = runCatching { NeteaseLinks.card(JSONObject(raw)) }.getOrNull()

    private fun messageText(raw: String): String = runCatching {
        val body = JSONObject(raw)
        body.optString("msg").takeIf { it.isNotBlank() }
            ?: body.optJSONObject("song")?.let { "[分享歌曲] " + it.optString("name") }
            ?: body.optJSONObject("playlist")?.let { "[分享歌单] " + it.optString("name") }
            ?: body.optJSONObject("album")?.let { "[分享专辑] " + it.optString("name") }
            ?: body.optString("title").takeIf { it.isNotBlank() }
            ?: "[消息]"
    }.getOrDefault(raw)

    suspend fun findArtist(name: String): NeteaseArtist? = withContext(Dispatchers.IO) {
        val clean = name.trim()
        if (clean.isBlank()) return@withContext null
        val artists = search(clean, 100).artists
        artists.firstOrNull { it.name.equals(clean, ignoreCase = true) } ?: artists.firstOrNull()
    }

    suspend fun artistHome(id: Long): NeteaseArtistHome = withContext(Dispatchers.IO) {
        val head = runCatching { api("/api/artist/head/info/get", JSONObject().put("id", id.toString())).optJSONObject("data") }.getOrNull()
        val legacy = if (head?.optJSONObject("artist") == null) api("/api/v1/artist/$id", JSONObject()).optJSONObject("artist") else null
        val artist = head?.optJSONObject("artist") ?: legacy ?: error("找不到这位歌手")
        val intro = runCatching { api("/api/artist/introduction", JSONObject().put("id", id.toString())) }.getOrNull()
        NeteaseArtistHome(
            id = id,
            name = artist.optString("name"),
            avatarUrl = (artist.optString("avatar").https() ?: artist.optString("picUrl").https() ?: artist.optString("img1v1Url").https())?.let { "$it?param=400y400" },
            coverUrl = (artist.optString("cover").https() ?: artist.optString("picUrl").https())?.let { "$it?param=1080y720" },
            alias = (artist.optJSONArray("alias") ?: artist.optJSONArray("transNames")).strings().joinToString(" / "),
            identity = head?.optJSONObject("identify")?.optString("imageDesc")?.takeIf { it != "null" }
                ?: artist.optJSONArray("identities").strings().joinToString("、"),
            briefDesc = (intro?.optString("briefDesc")?.takeIf { it.isNotBlank() && it != "null" } ?: artist.optString("briefDesc")).takeIf { it != "null" }.orEmpty().trim(),
            sections = intro?.optJSONArray("introduction").objects().mapNotNull { row ->
                val text = row.optString("txt").trim()
                if (text.isBlank() || text == "null") null else row.optString("ti").trim() to text
            },
            songCount = artist.optInt("musicSize"),
            albumCount = artist.optInt("albumSize"),
            mvCount = artist.optInt("mvSize"),
            accountUserId = head?.optJSONObject("user")?.optLong("userId") ?: artist.optLong("accountId"),
        ).also { if (it.name.isBlank()) error("找不到这位歌手") }
    }

    suspend fun artistAlbums(id: Long, offset: Int = 0): List<NeteasePlaylistSummary> = withContext(Dispatchers.IO) {
        api("/api/artist/albums/$id", JSONObject().put("limit", "60").put("offset", offset.toString()).put("total", "true"))
            .optJSONArray("hotAlbums").objects().map(::albumSummary)
    }

    suspend fun songWiki(songId: Long): List<NeteaseWikiSection> = withContext(Dispatchers.IO) {
        val summary = runCatching {
            api("/api/song/play/about/block/page", JSONObject().put("songId", songId.toString())).optJSONObject("data")
        }.getOrNull()
        val full = runCatching {
            val ext = JSONObject().put("states", JSONObject().put("playingResource", JSONObject().put("current", songId.toString()).put("scene", "songWiki")))
            eapi("/api/link/page/parent/relation/construct/info", JSONObject().put("extJson", ext.toString()).put("positionCode", "songWikiMainPosition")).optJSONObject("data")
        }.getOrNull()
        val sections = ArrayList<NeteaseWikiSection>()
        listOfNotNull(summary, full).forEach { root -> collectBlocks(root, 0) { block -> wikiSection(block)?.let(sections::add) } }
        sections.groupBy { it.title.ifBlank { it.entries.firstOrNull()?.title.orEmpty() } }
            .map { (_, same) -> same.maxBy { it.entries.size } }
    }

    private fun collectBlocks(node: Any?, depth: Int, found: (JSONObject) -> Unit) {
        if (depth > 8) return
        when (node) {
            is JSONObject -> {
                if (node.has("creatives")) {
                    found(node)
                    return
                }
                node.keys().forEach { key -> collectBlocks(node.opt(key), depth + 1, found) }
            }
            is JSONArray -> for (i in 0 until node.length()) collectBlocks(node.opt(i), depth + 1, found)
        }
    }

    private fun wikiSection(block: JSONObject): NeteaseWikiSection? {
        val ui = block.optJSONObject("uiElement")
        val title = ui?.optJSONObject("mainTitle")?.optString("title")?.takeIf { it.isNotBlank() && it != "null" }
            ?: block.optString("blockTitle").takeIf { it.isNotBlank() && it != "null" } ?: ""
        val entries = block.optJSONArray("creatives").objects().mapNotNull { creative ->
            val cui = creative.optJSONObject("uiElement")
            val name = cui?.optJSONObject("mainTitle")?.optString("title")?.takeIf { it.isNotBlank() && it != "null" }.orEmpty()
            val texts = LinkedHashSet<String>()
            cui?.optJSONArray("textLinks").objects().forEach { texts += it.optString("text") }
            cui?.optJSONArray("descriptions").objects().forEach { texts += it.optString("description") }
            cui?.optJSONObject("subTitle")?.optString("title")?.let { texts += it }
            creative.optJSONArray("resources").objects().forEach { resource ->
                val rui = resource.optJSONObject("uiElement")
                rui?.optJSONObject("mainTitle")?.optString("title")?.let { texts += it }
                rui?.optJSONObject("subTitle")?.optString("title")?.let { texts += it }
                rui?.optJSONArray("descriptions").objects().forEach { texts += it.optString("description") }
            }
            val text = texts.map(String::trim).filter { it.isNotBlank() && it != "null" && it != name }.joinToString("、")
            val image = cui?.optJSONArray("images").objects().firstOrNull()?.optString("imageUrl")?.https()
                ?: cui?.optJSONObject("image")?.optString("imageUrl")?.https()
                ?: creative.optJSONArray("resources").objects().firstNotNullOfOrNull { r ->
                    r.optJSONObject("uiElement")?.optJSONArray("images").objects().firstOrNull()?.optString("imageUrl")?.https()
                }
            if (name.isBlank() && text.isBlank()) null else NeteaseWikiEntry(name, text, image)
        }
        return if (entries.isEmpty()) null else NeteaseWikiSection(title, entries)
    }
    suspend fun songFacts(songId: Long): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        if (cookie == null) return@withContext emptyList()
        val data = runCatching { eapi("/api/rep/ugc/song/get", JSONObject().put("songId", songId.toString())).optJSONObject("data") }.getOrNull()
            ?: return@withContext emptyList()
        fun names(key: String) = data.optJSONArray(key).objects().map { it.optString("artistName").ifBlank { it.optString("name") } }
            .filter { it.isNotBlank() && it != "null" }.joinToString(" / ")
        listOfNotNull(
            data.optJSONObject("albumRepVo")?.optString("albumName")?.takeIf { it.isNotBlank() && it != "null" }?.let { "专辑" to it },
            names("artistRepVos").takeIf(String::isNotBlank)?.let { "歌手" to it },
            names("lyricArtists").takeIf(String::isNotBlank)?.let { "作词" to it },
            names("composeArtists").takeIf(String::isNotBlank)?.let { "作曲" to it },
            names("arrangeArtists").takeIf(String::isNotBlank)?.let { "编曲" to it },
            data.optLong("publishTime").takeIf { it > 0 }?.let { "发行时间" to java.text.SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(java.util.Date(it)) },
            data.optString("language").takeIf { it.isNotBlank() && it != "null" }?.let { "语种" to it },
            data.optString("company").takeIf { it.isNotBlank() && it != "null" }?.let { "发行公司" to it },
            when (data.optInt("originalCover")) { 1 -> "版本" to "原唱"; 2 -> "版本" to "翻唱"; else -> null },
            data.optString("transName").takeIf { it.isNotBlank() && it != "null" }?.let { "译名" to it },
        )
    }

    suspend fun wikiDevote(): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        if (cookie == null) return@withContext null
        val data = runCatching { api("/api/rep/ugc/user/devote", JSONObject()).optJSONObject("data") }.getOrNull() ?: return@withContext null
        data.optInt("ugcCount") to data.optInt("cloudShell")
    }

    suspend fun wikiContributions(type: Int, offset: Int = 0): Pair<List<NeteaseWikiContribution>, Int> = withContext(Dispatchers.IO) {
        require(cookie != null) { "请先登录网易云音乐" }
        val data = api(
            "/api/rep/ugc/detail",
            JSONObject().put("auditStatus", "").put("limit", "20").put("offset", offset.toString()).put("order", "desc").put("sortBy", "createTime").put("type", type.toString()),
        ).optJSONObject("data") ?: return@withContext emptyList<NeteaseWikiContribution>() to 0
        val rows = data.optJSONArray("wikiDetailResultVoList") ?: data.optJSONArray("records") ?: data.optJSONArray("list")
        rows.objects().map { row ->
            val artists = (row.optJSONArray("artists") ?: row.optJSONArray("artistSimpleVos")).objects()
                .map { it.optString("artistName").ifBlank { it.optString("name") } }.filter { it.isNotBlank() && it != "null" }
            NeteaseWikiContribution(
                name = row.optString("name").ifBlank { row.optString("bizName") },
                artists = artists.joinToString(" / "),
                time = row.optLong("createTime"),
                status = row.optInt("status", row.optInt("auditStatus")),
                type = type,
            )
        } to data.optInt("total")
    }

    fun wikiEditorUrl(songId: Long) = "https://music.163.com/#/wiki/song?songId=$songId"

    internal fun webCookie(): String? = cookie

    @Volatile
    var lastImRequestId: String = ""

    suspend fun listenStatus(): NeteaseListenRoom? = withContext(Dispatchers.IO) {
        if (cookie == null) return@withContext null
        val data = eapi("/api/listen/together/status/get", JSONObject()).optJSONObject("data") ?: return@withContext null
        val room = data.optJSONObject("roomInfo") ?: return@withContext null
        if (!data.optBoolean("inRoom", true) && room.optString("roomId").isBlank()) return@withContext null
        listenRoom(room)
    }

    suspend fun listenCreate(): NeteaseListenRoom = withContext(Dispatchers.IO) {
        require(cookie != null) { "请先登录网易云音乐" }
        val result = eapi("/api/listen/together/room/create", JSONObject().put("refer", "songplay_more"))
        val room = result.optJSONObject("data")?.optJSONObject("roomInfo") ?: error(result.optString("message").ifBlank { "无法创建房间" })
        listenRoom(room).copy(host = true, inviterId = _account.value?.userId ?: 0L)
    }

    suspend fun listenCheck(roomId: String): NeteaseListenRoom? = withContext(Dispatchers.IO) {
        val room = eapi("/api/listen/together/room/check", JSONObject().put("roomId", roomId)).optJSONObject("data")?.optJSONObject("roomInfo")
        room?.let(::listenRoom)
    }

    suspend fun listenAccept(roomId: String, inviterId: Long): NeteaseListenRoom = withContext(Dispatchers.IO) {
        require(cookie != null) { "请先登录网易云音乐" }
        val result = eapi("/api/listen/together/play/invitation/accept", JSONObject().put("refer", "inbox_invite").put("roomId", roomId).put("inviterId", inviterId.toString()))
        require(result.optInt("code") == 200) { result.optString("message").ifBlank { "无法加入房间（可能已结束）" } }
        (result.optJSONObject("data")?.optJSONObject("roomInfo")?.let(::listenRoom) ?: NeteaseListenRoom(roomId, inviterId, emptyList(), false, result.optJSONObject("data")?.optString("chatRoomId"))).copy(host = false)
    }

    suspend fun listenEnd(roomId: String) = withContext(Dispatchers.IO) {
        runCatching { eapi("/api/listen/together/end/v2", JSONObject().put("roomId", roomId)) }
        Unit
    }

    suspend fun listenCommand(
        roomId: String,
        type: String,
        progressMs: Long,
        playing: Boolean,
        formerSongId: Long,
        targetSongId: Long,
        seq: Int,
    ) = withContext(Dispatchers.IO) {
        val info = JSONObject().put("commandType", type).put("progress", progressMs).put("playStatus", if (playing) "PLAY" else "PAUSE")
            .put("formerSongId", formerSongId.toString()).put("targetSongId", targetSongId.toString()).put("clientSeq", seq)
            eapi("/api/listen/together/play/command/report", JSONObject().put("roomId", roomId).put("commandInfo", info.toString()))
            Unit
        }

    suspend fun listenSyncList(
        roomId: String,
        songIds: List<Long>,
        version: Int,
        anchorSongId: Long = 0L,
        anchorPosition: Long = -1L,
        userId: Long = 0L,
    ) = withContext(Dispatchers.IO) {
        val me = _account.value?.userId ?: 0L
        val ids = JSONArray().apply { songIds.forEach { put(it.toString()) } }
        val param = JSONObject().put("commandType", "REPLACE")
            .put("version", JSONArray().put(JSONObject().put("userId", if (userId > 0L) userId else me).put("version", version)))
            .put("playMode", "ORDER_LOOP")
            .put("anchorSongId", anchorSongId.takeIf { it > 0L }?.toString().orEmpty())
            .put("anchorPosition", anchorPosition.takeIf { it >= 0L } ?: -1L)
            .put("randomList", JSONArray())
            .put("displayList", ids)
        eapi("/api/listen/together/sync/list/command/report", JSONObject().put("roomId", roomId).put("playlistParam", param.toString()))
        Unit
    }

    suspend fun listenHeartbeat(roomId: String, songId: Long, playing: Boolean, progressMs: Long) = withContext(Dispatchers.IO) {
        runCatching {
            eapi(
                "/api/listen/together/heartbeat",
                JSONObject().put("roomId", roomId).put("songId", songId.toString()).put("playStatus", if (playing) "PLAY" else "PAUSE").put("progress", progressMs.toString()),
            )
        }
        Unit
    }

    data class NeteaseListenSync(
        val ids: List<Long>,
        val songId: Long? = null,
        val progress: Long? = null,
        val playing: Boolean? = null,
        val seq: Long = 0L,
        val fromMe: Boolean = false,
        val commandType: String? = null,
        val listed: Boolean = true,
        val roomSongId: Long? = null,
    )

    @Volatile
    internal var lastListenSeq: Long = 0L

    fun appliedListenSeq(): Long = lastListenSeq

    fun markListenSeq(seq: Long) {
        if (seq > lastListenSeq) lastListenSeq = seq
    }

    @Volatile
    var lastListenSyncRaw: String? = null
        private set

    data class ListenImAccount(val accId: String, val token: String, val addresses: List<String>)

    suspend fun listenImAccount(): ListenImAccount? = withContext(Dispatchers.IO) {
        val musicU = cookie ?: return@withContext null
        val now = System.currentTimeMillis()
        val requestId = "${now}_${(now % 1000).toString().padStart(4, '0')}"
        lastImRequestId = requestId
        val cookieHeader = "MUSIC_U=$musicU; osver=15.5; os=osx; appver=3.1.10.5100; channel=netease; versioncode=140; " +
            "buildver=${now / 1000}; resolution=1920x1080; requestId=$requestId"
        val request = Request.Builder()
            .url("https://interface3.music.163.com/api/middle/im/token/get?bizName=music_listenTogether")
            .header("Cookie", cookieHeader)
            .header("Accept", "application/json")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                val body = runCatching { JSONObject(response.body?.string().orEmpty().ifBlank { "{}" }) }.getOrNull() ?: return@runCatching null
                val data = body.optJSONObject("data") ?: return@runCatching null
                val accId = data.optString("accId").takeIf { it.isNotBlank() && it != "null" } ?: return@runCatching null
                val token = data.optString("token").takeIf { it.isNotBlank() && it != "null" } ?: return@runCatching null
                val addresses = data.optJSONArray("addr")?.let { array ->
                    (0 until array.length()).mapNotNull { array.optString(it).takeIf { value -> value.isNotBlank() } }
                }.orEmpty()
                ListenImAccount(accId, token, addresses)
            }
        }.getOrNull()
    }

    suspend fun listenSync(roomId: String): NeteaseListenSync? = withContext(Dispatchers.IO) {
        val response = eapi("/api/listen/together/sync/playlist/get", JSONObject().put("roomId", roomId))
        lastListenSyncRaw = response.toString()
        val data = response.optJSONObject("data") ?: return@withContext null
        val playlist = data.optJSONObject("playlist") ?: data
        val play = listOfNotNull(
            data.optJSONObject("playCommand"),
            data.optJSONObject("playInfo"),
            data.optJSONObject("currentPlay"),
            data.optJSONObject("commandInfo"),
            playlist.optJSONObject("playInfo"),
            playlist.optJSONObject("currentPlay"),
            data.optJSONObject("roomInfo")?.optJSONObject("playInfo"),
            data.optJSONObject("roomInfo")?.optJSONObject("playCommand"),
        ).firstOrNull { it.listenLong("songId", "targetSongId", "currentSongId") != null } ?: findPlayState(data)
        val status = play?.listenString("playStatus", "playState", "status", "playOrPause")
        val playing = when {
            play?.has("playing") == true -> play.optBoolean("playing")
            status == null -> null
            status.equals("PLAY", true) || status.equals("PLAYING", true) -> true
            status.equals("PAUSE", true) || status.equals("PAUSED", true) -> false
            else -> null
        }
        val seq = play?.listenLong("serverSeq") ?: 0L
        val fromMe = play?.listenLong("userId")?.let { it == (_account.value?.userId ?: 0L) } ?: false
        if (seq > 0L && seq > lastListenSeq) lastListenSeq = seq
        val anchorSong = playlist.listenLong("anchorSongId", "anchor_song_id") ?: data.listenLong("anchorSongId")
        val anchorPosition = (playlist.listenLong("anchorPosition", "anchor_position") ?: data.listenLong("anchorPosition"))?.takeIf { it >= 0 }
        NeteaseListenSync(
            ids = playlist.listenIds(),
            songId = play?.listenLong("songId", "targetSongId", "currentSongId") ?: anchorSong,
            progress = play?.listenLong("progress", "position", "playPosition", "currentPosition")?.takeIf { it >= 0 } ?: anchorPosition,
            playing = playing,
            seq = seq,
            fromMe = fromMe,
            commandType = play?.listenString("commandType")?.uppercase(),
            listed = true,
            roomSongId = anchorSong?.takeIf { it > 0L } ?: play?.listenLong("formerSongId"),
        )
    }

    fun mergeListenCommand(command: com.olbyine.music.data.ListenTogetherIm.Command): NeteaseListenSync {
        if ((command.seq ?: 0L) > lastListenSeq) lastListenSeq = command.seq ?: 0L
        val me = _account.value?.userId ?: 0L
        return NeteaseListenSync(
            ids = emptyList(),
            songId = command.songId,
            progress = command.progress,
            playing = command.playing,
            seq = command.seq ?: 0L,
            fromMe = command.from != null && me != 0L && command.from.toLongOrNull() == me,
            commandType = command.type,
            listed = false,
        )
    }

    private fun findPlayState(root: JSONObject): JSONObject? {
        var found: JSONObject? = null
        fun visit(node: JSONObject?, depth: Int) {
            if (node == null || found != null || depth > 5) return
            val song = node.listenLong("songId", "targetSongId", "currentSongId")
            val status = node.listenString("playStatus", "playState", "playOrPause")
            val progress = node.listenLong("progress", "playPosition", "currentPosition")
            if (song != null && (status != null || progress != null)) {
                found = node
                return
            }
            val keys = node.keys()
            while (keys.hasNext()) {
                when (val value = node.opt(keys.next())) {
                    is JSONObject -> visit(value, depth + 1)
                    is JSONArray -> for (index in 0 until value.length()) visit(value.optJSONObject(index), depth + 1)
                    else -> Unit
                }
                if (found != null) return
            }
        }
        visit(root, 0)
        return found
    }

    private fun JSONObject.listenLong(vararg keys: String): Long? =
        keys.firstNotNullOfOrNull { key -> optString(key).trim().takeIf { it.isNotBlank() && it != "null" }?.toLongOrNull() }

    private fun JSONObject.listenString(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> optString(key).trim().takeIf { it.isNotBlank() && it != "null" } }

    private fun JSONObject.listenIds(): List<Long> {
        val array = listOf(
            optJSONObject("displayList")?.optJSONArray("result"),
            optJSONArray("displayList"),
            optJSONObject("displayList")?.optJSONArray("ids"),
            optJSONObject("randomList")?.optJSONArray("result"),
            optJSONArray("result"),
        ).firstOrNull { it != null && it.length() > 0 } ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            when (val row = array.opt(index)) {
                is String -> row.toLongOrNull()
                is Number -> row.toLong()
                is JSONObject -> row.listenLong("id", "songId")
                else -> null
            }
        }
    }

    fun listenInviteLink(room: NeteaseListenRoom, songId: Long?) =
        "https://st.music.163.com/listen-together/share/?songId=${songId ?: 0}&roomId=${room.roomId}&inviterId=${room.inviterId}"

    private fun listenRoom(room: JSONObject): NeteaseListenRoom = NeteaseListenRoom(
        roomId = room.optString("roomId"),
        chatRoomId = room.optString("chatRoomId").takeIf { it.isNotBlank() && it != "null" },
        startedAt = listOf("createTime", "startTime", "createAt", "ctime")
            .firstNotNullOfOrNull { key -> room.optLong(key).takeIf { it > 0L } }
            ?.let { if (it < 10_000_000_000L) it * 1000L else it } ?: 0L,
        inviterId = room.optLong("creatorId", room.optLong("inviterId")),
        users = room.optJSONArray("roomUsers").objects().map { user ->
            NeteaseFriend(user.optLong("userId"), user.optString("nickname"), user.optString("avatarUrl").https()?.let { "$it?param=100y100" })
        },
        host = room.optLong("creatorId") != 0L && room.optLong("creatorId") == _account.value?.userId,
    )

    private fun JSONArray?.strings(): List<String> = this?.let { array -> (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() && s != "null" } } }.orEmpty()

    fun playUrl(id: Long, level: String): String {
        val result = eapiBlocking(
            "/api/song/enhance/player/url/v1",
            JSONObject().put("ids", "[$id]").put("level", level).put("encodeType", "flac"),
        )
        val row = result.optJSONArray("data")?.optJSONObject(0) ?: error("网易云没有返回播放地址")
        val url = row.optString("url").takeIf { it.isNotBlank() && it != "null" }
        if (url == null) {
            error(
                when {
                    row.optInt("fee") == 1 || row.optInt("fee") == 4 -> "这首歌需要网易云会员或单独购买"
                    cookie == null -> "需要登录网易云才能播放"
                    else -> "网易云暂时无法播放这首歌（版权限制）"
                },
            )
        }
        if (row.optJSONObject("freeTrialInfo") != null) error("这首歌只能试听（需要会员）")
        com.olbyine.music.playback.NowPlayingFormat.served(
            id,
            com.olbyine.music.playback.StreamQuality(
                level = row.optString("level").ifBlank { level },
                bitrate = row.optInt("br"),
                type = row.optString("type").takeIf { it.isNotBlank() && it != "null" }.orEmpty(),
                sizeBytes = row.optLong("size"),
            ),
        )
        return url.https() ?: url
    }

    suspend fun downloadInfo(id: Long, level: String): NeteaseDownloadInfo = withContext(Dispatchers.IO) {
        val params = JSONObject()
            .put("ids", "[$id]")
            .put("level", level)
            .put("encodeType", "flac")
            .put("immerseType", "c51")
            .put("trialMode", "-1")
        var result = runCatching { api("/api/song/enhance/player/url/v1", JSONObject(params.toString())) }
            .getOrElse { error("获取下载地址失败：${it.message ?: "网络不可用"}") }
        var row = firstDataRow(result)
        if (row == null) {
            val legacy = runCatching {
                api(
                    "/api/song/enhance/player/url",
                    JSONObject().put("ids", "[$id]").put("br", legacyBitrate(level).toString()),
                )
            }.getOrNull()
            if (legacy != null) {
                result = legacy
                row = firstDataRow(legacy)
            }
        }
        val data = row ?: error(
            when (result.optInt("code", 200)) {
                200 -> "网易云没有返回下载地址"
                -460 -> "网易云拒绝了请求（风控），请稍后再试或重新登录"
                else -> result.optString("message").ifBlank { "网易云没有返回下载地址（code ${result.optInt("code")}）" }
            },
        )
        val url = data.optString("url").takeIf { it.isNotBlank() && it != "null" }
        if (url == null) {
            error(
                when {
                    data.optInt("fee") == 1 || data.optInt("fee") == 4 -> "这首歌需要网易云会员或单独购买"
                    cookie == null -> "需要登录网易云才能下载"
                    else -> "网易云暂时无法下载这首歌（版权限制）"
                },
            )
        }
        if (data.optJSONObject("freeTrialInfo") != null) error("这首歌只能试听（需要会员），无法完整下载")
        NeteaseDownloadInfo(
            url = url.https() ?: url,
            type = data.optString("type").takeIf { it.isNotBlank() && it != "null" }.orEmpty(),
            sizeBytes = data.optLong("size"),
            bitrate = data.optInt("br"),
            level = data.optString("level").ifBlank { level },
            coverUrl = (runCatching { song(id) }.getOrNull()?.coverUrl ?: "").let { if (it.startsWith("http")) "$it?param=500y500" else it },
        )
    }

    private fun legacyBitrate(level: String): Int = when (level) {
        "standard" -> 128_000
        "higher" -> 192_000
        "exhigh" -> 320_000
        "lossless" -> 999_000
        "hires", "jymaster" -> 1_900_000
        else -> 320_000
    }

    private fun firstDataRow(result: JSONObject): JSONObject? {
        val data = result.opt("data")
        return when (data) {
            is JSONObject -> data
            is JSONArray -> (0 until data.length()).mapNotNull { data.optJSONObject(it) }.firstOrNull()
            is String -> runCatching {
                val text = data.trim()
                if (text.startsWith("[")) JSONArray(text).optJSONObject(0) else JSONObject(text)
            }.getOrNull()
            else -> result.optJSONArray("songs")?.optJSONObject(0)
        }
    }

    suspend fun lyrics(id: Long): StoredLyrics? = withContext(Dispatchers.IO) {
        val result = eapi(
            "/api/song/lyric/v1",
            JSONObject().put("id", id.toString()).put("cp", "false").put("lv", "0").put("kv", "0").put("tv", "0")
                .put("rv", "0").put("yv", "0").put("ytv", "0").put("yrv", "0"),
        )
        val lines = result.optJSONObject("lrc")?.optString("lyric").orEmpty()
        val words = result.optJSONObject("yrc")?.optString("lyric").orEmpty()
        if (lines.isBlank() && words.isBlank()) {
            return@withContext if (result.optBoolean("pureMusic") || result.optBoolean("nolyric")) {
                StoredLyrics(source = "netease", format = "plain", instrumental = true)
            } else null
        }
        StoredLyrics(
            lyrics = lines,
            wordLyrics = words,
            translationLyrics = result.optJSONObject("tlyric")?.optString("lyric").orEmpty(),
            romanLyrics = result.optJSONObject("romalrc")?.optString("lyric").orEmpty(),
            source = "netease",
            format = if (words.isNotBlank()) "yrc" else "lrc-line",
            confidence = 1.0,
        )
    }

    private fun song(row: JSONObject, privilege: JSONObject?): NeteaseSong? {
        val id = row.optLong("id").takeIf { it > 0 } ?: return null
        val album = row.optJSONObject("al") ?: row.optJSONObject("album")
        val artistRows = (row.optJSONArray("ar") ?: row.optJSONArray("artists")).objects()
        val artists = artistRows.map { it.optString("name") }.filter(String::isNotBlank)
        val unavailable = (privilege?.optInt("st", 0) ?: 0) < 0 || privilege?.optInt("pl", 1) == 0
        return NeteaseSong(
            id = id,
            title = row.optString("name"),
            artists = artists,
            album = album?.optString("name").orEmpty(),
            durationMs = row.optLong("dt").takeIf { it > 0 } ?: row.optLong("duration"),
            coverUrl = (album?.optString("picUrl")?.https() ?: album?.let(::pictureFromId))?.let { "$it?param=600y600" },
            unavailable = unavailable,
            albumId = album?.optLong("id")?.takeIf { it > 0 } ?: 0,
            artistIds = artistRows.map { it.optLong("id") }.filter { it > 0 },
            mvId = row.optLong("mv").takeIf { it > 0 } ?: row.optLong("mvid").takeIf { it > 0 } ?: 0,
        )
    }

    private suspend fun eapi(path: String, params: JSONObject): JSONObject = withContext(Dispatchers.IO) { eapiBlocking(path, params) }

    private suspend fun api(path: String, params: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val viaApp = runCatching { eapiBlocking(path, JSONObject(params.toString())) }.getOrNull()
        if (viaApp != null && viaApp.optInt("code", 200) == 200 && viaApp.length() > 1) return@withContext viaApp
        val form = FormBody.Builder()
        params.keys().forEach { key -> form.add(key, params.optString(key)) }
        val request = Request.Builder()
            .url("https://music.163.com$path")
            .post(form.build())
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36")
            .header("Referer", "https://music.163.com/")
            .header("Cookie", "os=pc" + (cookie?.let { "; MUSIC_U=$it" } ?: ""))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull()
            json ?: viaApp ?: error("网易云请求失败（HTTP ${response.code}）")
        }
    }

    private fun weapiBlocking(path: String, params: JSONObject): JSONObject {
        val text = params.put("csrf_token", "").toString()
        val secret = String(CharArray(16) { WEAPI_CHARS.random() })
        val form = FormBody.Builder()
            .add("params", weapiAes(weapiAes(text, WEAPI_PRESET_KEY), secret))
            .add("encSecKey", weapiRsa(secret.reversed()))
            .build()
        val request = Request.Builder()
            .url("https://music.163.com/weapi" + path.removePrefix("/api"))
            .post(form)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36")
            .header("Referer", "https://music.163.com/")
            .header("Cookie", "os=pc; appver=2.10.13" + (cookie?.let { "; MUSIC_U=$it" } ?: ""))
            .build()
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "网易云请求失败（HTTP ${response.code}）" }
            return JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
        }
    }

    private fun weapiAes(text: String, key: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.US_ASCII), "AES"), IvParameterSpec(WEAPI_IV))
        return android.util.Base64.encodeToString(cipher.doFinal(text.toByteArray(Charsets.UTF_8)), android.util.Base64.NO_WRAP)
    }

    private fun weapiRsa(text: String): String {
        val value = BigInteger(1, text.toByteArray(Charsets.US_ASCII))
        return value.modPow(BigInteger(WEAPI_PUBLIC_EXPONENT, 16), BigInteger(WEAPI_MODULUS, 16)).toString(16).padStart(256, '0')
    }

    private fun eapiBlocking(path: String, params: JSONObject, host: String = "https://interface3.music.163.com"): JSONObject {
        client.newCall(eapiRequest(path, params, host)).execute().use { response ->
            require(response.isSuccessful) { "网易云请求失败（HTTP ${response.code}）" }
            return JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
        }
    }

    private fun eapiRequest(path: String, params: JSONObject, host: String = "https://interface3.music.163.com"): Request {
        val now = System.currentTimeMillis()
        val header = JSONObject()
            .put("os", "android")
            .put("appver", APP_VERSION)
            .put("osver", "14")
            .put("deviceId", deviceId)
            .put("versioncode", "8200")
            .put("buildver", (now / 1000).toString())
            .put("resolution", "1080x2400")
            .put("requestId", "${now}_${(now % 1000).toString().padStart(4, '0')}")
            .put("channel", "xiaomi")
            .put("__csrf", "")
            .apply { cookie?.let { put("MUSIC_U", it) } }
        params.put("header", header.toString())
        val text = params.toString()
        val digest = md5Hex("nobody${path}use${text}md5forencrypt")
        val payload = "$path-36cd479b6b5-$text-36cd479b6b5-$digest"
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(EAPI_KEY, "AES"))
        val encrypted = cipher.doFinal(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }
        val cookieHeader = buildString {
            append("os=android; appver=$APP_VERSION; deviceId=$deviceId; channel=xiaomi")
            cookie?.let { append("; MUSIC_U=").append(it) }
        }
        return Request.Builder()
            .url("$host/e" + path.removePrefix("/"))
            .post(FormBody.Builder().add("params", encrypted).build())
            .header("User-Agent", "NeteaseMusic/$APP_VERSION.240716 (Linux; Android 14)")
            .header("Referer", "https://music.163.com/")
            .header("Cookie", cookieHeader)
            .build()
    }

    private fun pictureFromId(album: JSONObject): String? {
        val id = album.optString("pic_str").takeIf { it.isNotBlank() && it != "null" }
            ?: album.optLong("pic").takeIf { it > 0 }?.toString() ?: return null
        val key = "3go8&\$8*3*3h0k(2)2".toByteArray()
        val bytes = id.toByteArray().mapIndexed { index, b -> (b.toInt() xor key[index % key.size].toInt()).toByte() }.toByteArray()
        val digest = MessageDigest.getInstance("MD5").digest(bytes)
        val encoded = android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP).replace('/', '_').replace('+', '-')
        return "https://p1.music.126.net/$encoded/$id.jpg"
    }

    private fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun JSONArray?.objects(): List<JSONObject> = this?.let { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it) } }.orEmpty()

    private fun String.https(): String? = takeIf { it.isNotBlank() && it != "null" }?.replace(Regex("^http://", RegexOption.IGNORE_CASE), "https://")

    companion object {
        private const val FIRST_BATCH = 60
        private const val REST_CHUNK = 200
        private const val KEY_MUSIC_U = "music_u"
        private const val KEY_ACCOUNT = "account"
        private const val KEY_DEVICE = "device_id"
        private const val APP_VERSION = "8.20.20"
        private val EAPI_KEY = "e82ckenh8dichen8".toByteArray(Charsets.US_ASCII)
        private const val WEAPI_PRESET_KEY = "0CoJUm6Qyw8W8jud"
        private val WEAPI_IV = "0102030405060708".toByteArray(Charsets.US_ASCII)
        private const val WEAPI_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        private const val WEAPI_PUBLIC_EXPONENT = "010001"
        private const val WEAPI_MODULUS =
            "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7"

        val QUALITIES = listOf(
            "standard" to "标准（128k）",
            "higher" to "较高（192k）",
            "exhigh" to "极高（320k）",
            "lossless" to "无损（FLAC）",
            "hires" to "高解析度无损",
            "jyeffect" to "高清环绕声",
            "sky" to "沉浸环绕声",
            "jymaster" to "超清母带",
        )

        fun qualityBadge(level: String): String = when (level) {
            "standard" -> "标准"
            "higher" -> "较高"
            "exhigh" -> "极高"
            "lossless" -> "无损"
            "hires" -> "Hi-Res"
            "jyeffect" -> "环绕声"
            "sky" -> "沉浸声"
            "jymaster" -> "母带"
            else -> level.uppercase()
        }
    }
}
