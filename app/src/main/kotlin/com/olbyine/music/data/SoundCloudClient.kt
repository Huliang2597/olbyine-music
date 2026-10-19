package com.olbyine.music.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class ScUser(
    val id: Long,
    val username: String,
    val avatarUrl: String?,
    val followers: Int = 0,
    val trackCount: Int = 0,
    val city: String = "",
    val description: String = "",
    val followings: Int = 0,
    val likes: Int = 0,
    val permalink: String = "",
)

data class ScTrackInfo(
    val track: ScTrack,
    val description: String,
    val tags: List<String>,
    val createdAt: String,
    val releaseDate: String,
    val reposts: Long,
    val label: String,
    val permalink: String,
)

data class ScTrack(
    val id: Long,
    val title: String,
    val user: ScUser,
    val artworkUrl: String?,
    val durationMs: Long,
    val genre: String,
    val playCount: Long,
    val likeCount: Long,
    val commentCount: Long,
    val preview: Boolean,
    val streamable: Boolean,
    val hlsOnly: Boolean,
) {
    fun toTrack(): Track = Track(
        id = trackId(id),
        uri = if (hlsOnly) "sc://t$id/playlist.m3u8" else "sc://t$id",
        title = title.ifBlank { "未知歌曲" },
        artist = user.username.ifBlank { "SoundCloud" },
        album = genre.ifBlank { "SoundCloud" },
        durationMs = durationMs,
        displayName = title,
        mimeType = if (hlsOnly) "application/x-mpegURL" else "audio/mpeg",
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
        artworkUrl = artworkUrl ?: user.avatarUrl,
    )

    companion object {
        fun trackId(id: Long) = "sc:$id"
    }
}

data class ScPlaylist(
    val id: Long,
    val title: String,
    val user: ScUser,
    val artworkUrl: String?,
    val trackCount: Int,
    val album: Boolean,
    val tracks: List<ScTrack> = emptyList(),
    val urn: String = "soundcloud:playlists:$id",
)

data class ScComment(val id: Long, val user: ScUser, val body: String, val atMs: Long, val createdAt: String)

data class ScSelection(val title: String, val playlists: List<ScPlaylist>)

data class ScPage<T>(val items: List<T>, val next: String?)

data class ScFeedItem(val track: ScTrack?, val playlist: ScPlaylist?, val by: ScUser, val repost: Boolean, val caption: String, val createdAt: String)

data class ScActivity(val type: String, val user: ScUser, val track: ScTrack?, val playlist: ScPlaylist?, val comment: String, val createdAt: String)

data class ScConversation(val id: String, val other: ScUser, val lastMessage: String, val lastAt: String, val read: Boolean)

data class ScMessage(val fromMe: Boolean, val text: String, val sentAt: String)

class SoundCloudClient(context: Context) {
    private val preferences = context.getSharedPreferences("soundcloud", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    @Volatile private var clientId: String? = preferences.getString(KEY_CLIENT_ID, null)
    private val _account = MutableStateFlow(readAccount())
    val account: StateFlow<ScUser?> = _account.asStateFlow()
    private val token: String? get() = preferences.getString(KEY_TOKEN, null)
    val signedIn: Boolean get() = token != null
    private val hlsBase = ConcurrentHashMap<Long, String>()

    private fun clientId(refresh: Boolean = false): String {
        if (!refresh) clientId?.let { return it }
        val page = fetchText("https://soundcloud.com/")
        val scripts = Regex("""src="(https://a-v2\.sndcdn\.com/assets/[^"]+\.js)"""").findAll(page).map { it.groupValues[1] }.toList()
        for (script in scripts.asReversed()) {
            val id = runCatching { Regex("""client_id\s*:\s*"([0-9A-Za-z]{32})"""").find(fetchText(script))?.groupValues?.get(1) }.getOrNull()
            if (id != null) {
                clientId = id
                preferences.edit().putString(KEY_CLIENT_ID, id).apply()
                return id
            }
        }
        error("无法连接 SoundCloud（取不到客户端标识）")
    }

    private fun fetchText(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "无法连接 SoundCloud（HTTP ${response.code}）" }
            return response.body?.string().orEmpty()
        }
    }

    private fun get(pathOrUrl: String, params: Map<String, String> = emptyMap(), auth: Boolean = true): String {
        for (attempt in 0..1) {
            val base = if (pathOrUrl.startsWith("http")) pathOrUrl.toHttpUrl() else "$API$pathOrUrl".toHttpUrl()
            val url = base.newBuilder().apply {
                params.forEach { (key, value) -> setQueryParameter(key, value) }
                setQueryParameter("client_id", clientId(refresh = attempt > 0))
                setQueryParameter("app_locale", "zh")
            }.build()
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Accept", "application/json")
                .header("Origin", "https://soundcloud.com").header("Referer", "https://soundcloud.com/")
                .apply { if (auth) token?.let { header("Authorization", "OAuth $it") } }
                .build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> return body
                    (response.code == 401 || response.code == 403) && attempt == 0 -> Unit
                    response.code == 401 -> error("SoundCloud 登录已失效，请重新登录")
                    response.code == 404 -> error("SoundCloud 上找不到这个内容")
                    else -> error("SoundCloud 请求失败（HTTP ${response.code}）")
                }
            }
        }
        error("SoundCloud 请求失败")
    }

    private fun send(method: String, path: String): Boolean {
        val url = "$API$path".toHttpUrl().newBuilder().setQueryParameter("client_id", clientId()).build()
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT)
            .header("Authorization", "OAuth ${token ?: error("请先登录 SoundCloud")}")
            .method(method, if (method == "DELETE") null else ByteArray(0).toRequestBody())
            .build()
        http.newCall(request).execute().use { return it.isSuccessful }
    }

    private fun sendJson(method: String, path: String, body: JSONObject?): String {
        val url = "$API$path".toHttpUrl().newBuilder().setQueryParameter("client_id", clientId()).setQueryParameter("app_locale", "zh").build()
        val payload = body?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Accept", "application/json")
            .header("Origin", "https://soundcloud.com").header("Referer", "https://soundcloud.com/")
            .header("Authorization", "OAuth ${token ?: error("请先登录 SoundCloud")}")
            .method(method, payload ?: if (method == "DELETE") null else ByteArray(0).toRequestBody())
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            when {
                response.isSuccessful -> return text
                response.code == 401 -> error("SoundCloud 登录已失效，请重新登录")
                response.code == 403 -> error("SoundCloud 拒绝了这个操作（可能对方关闭了私信或评论）")
                response.code == 429 -> error("操作太频繁，请稍后再试")
                else -> error("SoundCloud 请求失败（HTTP ${response.code}）")
            }
        }
    }
    private suspend fun json(path: String, params: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) { JSONObject(get(path, params)) }

    suspend fun search(query: String, kind: String, next: String? = null): ScPage<Any> = withContext(Dispatchers.IO) {
        val body = JSONObject(if (next != null) get(next) else get("/search/$kind", mapOf("q" to query, "limit" to "30", "linked_partitioning" to "1")))
        val rows = body.optJSONArray("collection").objects()
        val items: List<Any> = when (kind) {
            "users" -> rows.map(::user)
            "tracks" -> rows.mapNotNull(::track)
            else -> rows.map(::playlist)
        }
        ScPage(items, body.optString("next_href").takeIf { it.startsWith("http") })
    }

    suspend fun genre(tag: String, next: String? = null): ScPage<ScTrack> = withContext(Dispatchers.IO) {
        val body = JSONObject(
            if (next != null) get(next)
            else {
                val key = tag.lowercase()
                val filtered = get("/search/tracks", mapOf("q" to key, "filter.genre_or_tag" to key, "limit" to "30", "linked_partitioning" to "1"))
                if (JSONObject(filtered).optJSONArray("collection")?.length() ?: 0 > 0) filtered
                else get("/search/tracks", mapOf("q" to tag, "limit" to "30", "linked_partitioning" to "1"))
            },
        )
        ScPage(body.optJSONArray("collection").objects().mapNotNull(::track), body.optString("next_href").takeIf { it.startsWith("http") })
    }

    suspend fun discover(): List<ScSelection> = withContext(Dispatchers.IO) {
        JSONObject(get("/mixed-selections", mapOf("limit" to "12"))).optJSONArray("collection").objects().mapNotNull { row ->
            val items = row.optJSONObject("items")?.optJSONArray("collection").objects().map(::playlist).filter { it.title.isNotBlank() }
            val title = row.optString("title").ifBlank { row.optString("description") }
            if (items.isEmpty() || title.isBlank()) null else ScSelection(title, items)
        }
    }

    suspend fun playlist(target: ScPlaylist): ScPlaylist = withContext(Dispatchers.IO) {
        val body = JSONObject(
            if (target.urn.startsWith("soundcloud:system-playlists")) get("/system-playlists/${target.urn}")
            else get("/playlists/${target.id}"),
        )
        val rows = body.optJSONArray("tracks").objects()
        val full = rows.mapNotNull(::track).associateBy { it.id }.toMutableMap()
        val missing = rows.map { it.optLong("id") }.filter { it > 0 && it !in full }
        missing.chunked(50).forEach { chunk ->
            JSONArray(get("/tracks", mapOf("ids" to chunk.joinToString(",")))).objects().mapNotNull(::track).forEach { full[it.id] = it }
        }
        playlist(body).copy(tracks = rows.mapNotNull { full[it.optLong("id")] })
    }

    suspend fun user(id: Long): ScUser = withContext(Dispatchers.IO) { user(JSONObject(get("/users/$id"))) }

    suspend fun userTracks(id: Long, next: String? = null): ScPage<ScTrack> = withContext(Dispatchers.IO) {
        val body = JSONObject(if (next != null) get(next) else get("/users/$id/tracks", mapOf("limit" to "30", "linked_partitioning" to "1")))
        ScPage(body.optJSONArray("collection").objects().mapNotNull(::track), body.optString("next_href").takeIf { it.startsWith("http") })
    }

    suspend fun userPlaylists(id: Long): List<ScPlaylist> = withContext(Dispatchers.IO) {
        JSONObject(get("/users/$id/playlists", mapOf("limit" to "50"))).optJSONArray("collection").objects().map(::playlist)
    }

    suspend fun related(trackId: Long): List<ScTrack> = withContext(Dispatchers.IO) {
        JSONObject(get("/tracks/$trackId/related", mapOf("limit" to "30"))).optJSONArray("collection").objects().mapNotNull(::track)
    }

    suspend fun comments(trackId: Long, next: String? = null): ScPage<ScComment> = withContext(Dispatchers.IO) {
        val body = JSONObject(
            if (next != null) get(next)
            else get("/tracks/$trackId/comments", mapOf("threaded" to "0", "filter_replies" to "0", "limit" to "40", "linked_partitioning" to "1")),
        )
        val items = body.optJSONArray("collection").objects().map { row ->
            ScComment(row.optLong("id"), user(row.optJSONObject("user") ?: JSONObject()), row.optString("body"), row.optLong("timestamp"), row.optString("created_at"))
        }
        ScPage(items, body.optString("next_href").takeIf { it.startsWith("http") })
    }

    suspend fun signIn(rawToken: String): ScUser = withContext(Dispatchers.IO) {
        val value = rawToken.trim().removePrefix("OAuth ").substringAfter("oauth_token=").substringBefore(';').trim()
        require(value.length >= 10) { "令牌格式不对" }
        preferences.edit().putString(KEY_TOKEN, value).apply()
        val me = runCatching { user(JSONObject(get("/me"))) }.getOrElse {
            preferences.edit().remove(KEY_TOKEN).apply()
            throw IllegalStateException("令牌无效或已过期", it)
        }
        preferences.edit().putString(KEY_ACCOUNT, JSONObject().put("id", me.id).put("username", me.username).put("avatar", me.avatarUrl).toString()).apply()
        _account.value = me
        me
    }

    fun signOut() {
        preferences.edit().remove(KEY_TOKEN).remove(KEY_ACCOUNT).apply()
        _account.value = null
    }

    private fun readAccount(): ScUser? = preferences.getString(KEY_ACCOUNT, null)?.let { raw ->
        runCatching { JSONObject(raw).let { ScUser(it.getLong("id"), it.optString("username"), it.optString("avatar").takeIf { a -> a.isNotBlank() && a != "null" }) } }.getOrNull()
    }

    private fun me(): Long = _account.value?.id ?: error("请先登录 SoundCloud")

    suspend fun likedTracks(): List<ScTrack> = withContext(Dispatchers.IO) {
        JSONObject(get("/users/${me()}/track_likes", mapOf("limit" to "200"))).optJSONArray("collection").objects()
            .mapNotNull { it.optJSONObject("track")?.let(::track) }
    }

    suspend fun likedIds(): Set<Long> = withContext(Dispatchers.IO) {
        if (!signedIn) return@withContext emptySet()
        runCatching { JSONObject(get("/me/track_likes/ids", mapOf("limit" to "5000"))).optJSONArray("collection") }
            .getOrNull()?.let { ids -> (0 until ids.length()).mapTo(HashSet()) { ids.optLong(it) } } ?: likedTracks().mapTo(HashSet()) { it.id }
    }

    suspend fun setLiked(trackId: Long, liked: Boolean) = withContext(Dispatchers.IO) {
        require(send(if (liked) "PUT" else "DELETE", "/users/${me()}/track_likes/$trackId")) { "操作失败" }
    }

    suspend fun myPlaylists(): List<ScPlaylist> = withContext(Dispatchers.IO) {
        val id = me()
        val own = runCatching { JSONObject(get("/users/$id/playlists_without_albums", mapOf("limit" to "100"))).optJSONArray("collection").objects().map(::playlist) }.getOrDefault(emptyList())
        val liked = runCatching {
            JSONObject(get("/users/$id/playlist_likes", mapOf("limit" to "100"))).optJSONArray("collection").objects()
                .mapNotNull { it.optJSONObject("playlist")?.let(::playlist) }
        }.getOrDefault(emptyList())
        own + liked.filter { candidate -> own.none { it.id == candidate.id } }
    }

    suspend fun stream(): List<ScTrack> = withContext(Dispatchers.IO) {
        JSONObject(get("/stream", mapOf("limit" to "50"))).optJSONArray("collection").objects()
            .mapNotNull { it.optJSONObject("track")?.let(::track) }
            .distinctBy { it.id }
    }

    suspend fun history(): List<ScTrack> = withContext(Dispatchers.IO) {
        JSONObject(get("/me/play-history/tracks", mapOf("limit" to "50"))).optJSONArray("collection").objects()
            .mapNotNull { it.optJSONObject("track")?.let(::track) }
            .distinctBy { it.id }
    }

    suspend fun feed(next: String? = null): ScPage<ScFeedItem> = withContext(Dispatchers.IO) {
        val body = JSONObject(if (next != null) get(next) else get("/stream", mapOf("limit" to "30", "linked_partitioning" to "1")))
        val items = body.optJSONArray("collection").objects().mapNotNull { row ->
            val type = row.optString("type")
            val track = row.optJSONObject("track")?.let(::track)
            val playlist = row.optJSONObject("playlist")?.let(::playlist)
            if (track == null && playlist == null) return@mapNotNull null
            ScFeedItem(
                track = track,
                playlist = playlist,
                by = user(row.optJSONObject("user") ?: JSONObject()),
                repost = type.endsWith("repost"),
                caption = row.optString("caption").takeIf { it != "null" }.orEmpty(),
                createdAt = row.optString("created_at"),
            )
        }
        ScPage(items, body.optString("next_href").takeIf { it.startsWith("http") })
    }

    suspend fun activities(next: String? = null): ScPage<ScActivity> = withContext(Dispatchers.IO) {
        val body = JSONObject(if (next != null) get(next) else get("/activities", mapOf("limit" to "30", "linked_partitioning" to "1")))
        val items = body.optJSONArray("collection").objects().map { row ->
            ScActivity(
                type = row.optString("type"),
                user = user(row.optJSONObject("user") ?: JSONObject()),
                track = row.optJSONObject("track")?.let(::track) ?: row.optJSONObject("comment")?.optJSONObject("track")?.let(::track),
                playlist = row.optJSONObject("playlist")?.let(::playlist),
                comment = row.optJSONObject("comment")?.optString("body")?.takeIf { it != "null" }.orEmpty(),
                createdAt = row.optString("created_at"),
            )
        }
        ScPage(items, body.optString("next_href").takeIf { it.startsWith("http") })
    }

    suspend fun postComment(trackId: Long, text: String, atMs: Long) = withContext(Dispatchers.IO) {
        val comment = JSONObject().put("body", text).put("timestamp", atMs.coerceAtLeast(0))
        sendJson("POST", "/tracks/$trackId/comments", JSONObject().put("comment", comment))
        Unit
    }

    suspend fun repostedIds(): Set<Long> = withContext(Dispatchers.IO) {
        if (!signedIn) return@withContext emptySet()
        (runCatching { JSONObject(get("/e1/me/track_reposts/ids", mapOf("limit" to "5000"))).optJSONArray("collection") }.getOrNull()
            ?: runCatching { JSONObject(get("/me/track_reposts/ids", mapOf("limit" to "5000"))).optJSONArray("collection") }.getOrNull())
            ?.let { ids -> (0 until ids.length()).mapTo(HashSet()) { ids.optLong(it) } }.orEmpty()
    }

    suspend fun setReposted(trackId: Long, reposted: Boolean) = withContext(Dispatchers.IO) {
        runCatching { sendJson(if (reposted) "PUT" else "DELETE", "/e1/me/track_reposts/$trackId", null) }
            .getOrElse { sendJson(if (reposted) "PUT" else "DELETE", "/me/track_reposts/$trackId", null) }
        Unit
    }

    suspend fun followingIds(): Set<Long> = withContext(Dispatchers.IO) {
        if (!signedIn) return@withContext emptySet()
        (runCatching { JSONObject(get("/users/${me()}/followings/ids", mapOf("limit" to "5000"))).optJSONArray("collection") }.getOrNull()
            ?: runCatching { JSONObject(get("/me/followings/ids", mapOf("limit" to "5000"))).optJSONArray("collection") }.getOrNull())
            ?.let { ids -> (0 until ids.length()).mapTo(HashSet()) { ids.optLong(it) } }.orEmpty()
    }

    suspend fun setFollowing(userId: Long, follow: Boolean) = withContext(Dispatchers.IO) {
        runCatching { sendJson(if (follow) "POST" else "DELETE", "/me/followings/$userId", if (follow) JSONObject() else null) }
            .recoverCatching { sendJson(if (follow) "PUT" else "DELETE", "/users/${me()}/followings/$userId", null) }
            .getOrThrow()
        Unit
    }

    suspend fun ownPlaylists(): List<ScPlaylist> = withContext(Dispatchers.IO) {
        val id = me()
        JSONObject(get("/users/$id/playlists_without_albums", mapOf("limit" to "200"))).optJSONArray("collection").objects().map(::playlist)
    }

    suspend fun addToPlaylist(playlistId: Long, trackId: Long) = withContext(Dispatchers.IO) {
        val row = JSONObject(get("/playlists/$playlistId", mapOf("representation" to "id")))
        val ids = row.optJSONArray("tracks").objects().map { it.optLong("id") }.filter { it > 0 }.toMutableList()
        if (trackId in ids) error("这首曲目已经在歌单里了")
        ids += trackId
        val tracks = org.json.JSONArray().apply { ids.forEach { put(it) } }
        sendJson("PUT", "/playlists/$playlistId", JSONObject().put("playlist", JSONObject().put("tracks", tracks)))
        Unit
    }

    suspend fun createPlaylist(title: String, trackId: Long, private: Boolean = true) = withContext(Dispatchers.IO) {
        val tracks = org.json.JSONArray().put(trackId)
        sendJson("POST", "/playlists", JSONObject().put("playlist", JSONObject().put("title", title).put("sharing", if (private) "private" else "public").put("tracks", tracks)))
        Unit
    }

    suspend fun userLikes(id: Long, next: String? = null): ScPage<ScTrack> = withContext(Dispatchers.IO) {
        val body = JSONObject(if (next != null) get(next) else get("/users/$id/track_likes", mapOf("limit" to "30", "linked_partitioning" to "1")))
        ScPage(body.optJSONArray("collection").objects().mapNotNull { it.optJSONObject("track")?.let(::track) }, body.optString("next_href").takeIf { it.startsWith("http") })
    }

    suspend fun followers(id: Long, next: String? = null): ScPage<ScUser> = withContext(Dispatchers.IO) {
        val body = JSONObject(if (next != null) get(next) else get("/users/$id/followers", mapOf("limit" to "40", "linked_partitioning" to "1")))
        ScPage(body.optJSONArray("collection").objects().map(::user), body.optString("next_href").takeIf { it.startsWith("http") })
    }

    suspend fun followings(id: Long, next: String? = null): ScPage<ScUser> = withContext(Dispatchers.IO) {
        val body = JSONObject(if (next != null) get(next) else get("/users/$id/followings", mapOf("limit" to "40", "linked_partitioning" to "1")))
        ScPage(body.optJSONArray("collection").objects().map(::user), body.optString("next_href").takeIf { it.startsWith("http") })
    }

    suspend fun conversations(next: String? = null): ScPage<ScConversation> = withContext(Dispatchers.IO) {
        val me = me()
        val body = JSONObject(if (next != null) get(next) else firstOf("/users/soundcloud:users:$me/conversations", "/users/$me/conversations", mapOf("limit" to "30", "linked_partitioning" to "1")))
        val items = body.optJSONArray("collection").objects().mapNotNull { row ->
            val users = row.optJSONArray("users").objects().map(::user)
            val other = users.firstOrNull { it.id != me } ?: row.optJSONObject("user")?.let(::user) ?: return@mapNotNull null
            val last = row.optJSONObject("last_message")
            ScConversation(
                id = row.optString("id").ifBlank { conversationId(other.id) },
                other = other,
                lastMessage = last?.optString("content")?.takeIf { it != "null" }.orEmpty(),
                lastAt = last?.optString("sent_at").orEmpty().ifBlank { row.optString("last_message_at") },
                read = row.optBoolean("read", true),
            )
        }
        ScPage(items, body.optString("next_href").takeIf { it.startsWith("http") })
    }

    fun conversationId(otherId: Long): String {
        val me = me()
        return if (me < otherId) "$me:$otherId" else "$otherId:$me"
    }

    suspend fun messages(conversationId: String): List<ScMessage> = withContext(Dispatchers.IO) {
        val me = me()
        val other = otherIdOf(me, conversationId)
        val paths = listOf("/users/$me/conversations/$other/messages", "/users/soundcloud:users:$me/conversations/$other/messages") +
            legacyConversationPaths(me, conversationId)
        JSONObject(firstOfAll(paths, mapOf("limit" to "50", "linked_partitioning" to "1")))
            .optJSONArray("collection").objects().map { row ->
                val sender = row.optJSONObject("sender")?.optLong("id")?.takeIf { it > 0 }
                    ?: row.optString("sender_urn").substringAfterLast(':').toLongOrNull()
                    ?: row.optLong("sender_id")
                ScMessage(sender == me, row.optString("content").takeIf { it != "null" }.orEmpty(), row.optString("sent_at"))
            }
            .sortedBy { it.sentAt }
    }

    suspend fun sendMessage(conversationId: String, text: String) = withContext(Dispatchers.IO) {
        val me = me()
        val other = otherIdOf(me, conversationId)
        val body = JSONObject().put("contents", text)
        val paths = listOf("/users/$me/conversations/$other", "/users/$me/conversations/$other/messages") + legacyConversationPaths(me, conversationId)
        var failure: Throwable? = null
        for (path in paths) {
            val sent = runCatching { sendJson("POST", path, body) }
            if (sent.isSuccess) return@withContext Unit
            failure = sent.exceptionOrNull()
        }
        throw IllegalStateException("SoundCloud 私信发送失败（${failure?.message}）", failure)
    }

    private fun otherIdOf(me: Long, conversationId: String): String {
        val parts = conversationId.removePrefix("soundcloud:conversations:").split(':').map { it.substringAfterLast(':') }
        return parts.firstOrNull { it != me.toString() && it.isNotBlank() } ?: parts.last()
    }

    private fun legacyConversationPaths(me: Long, id: String): List<String> {
        val bare = id.removePrefix("soundcloud:conversations:")
        return listOf("/users/$me/conversations/$bare/messages", "/users/soundcloud:users:$me/conversations/$bare/messages")
    }
    private fun firstOfAll(paths: List<String>, params: Map<String, String>): String {
        var failure: Throwable? = null
        for (path in paths) {
            val result = runCatching { get(path, params) }
            if (result.isSuccess) return result.getOrThrow()
            failure = result.exceptionOrNull()
        }
        throw IllegalStateException("SoundCloud 私信接口不可用（${failure?.message}），可以改用网页版私信", failure)
    }

    private fun firstOf(primary: String, fallback: String, params: Map<String, String>): String =
        runCatching { get(primary, params) }.getOrElse { first ->
            runCatching { get(fallback, params) }.getOrElse { throw IllegalStateException("SoundCloud 私信接口不可用（${first.message}），可以改用网页版私信", it) }
        }

    suspend fun trackInfo(id: Long): Pair<ScTrackInfo, ScUser> = withContext(Dispatchers.IO) {
        val row = JSONObject(get("/tracks/$id"))
        val track = track(row) ?: error("SoundCloud 上找不到这首曲目")
        val tags = Regex("\"([^\"]+)\"|(\\S+)").findAll(row.optString("tag_list").takeIf { it != "null" }.orEmpty())
            .map { it.groupValues[1].ifBlank { it.groupValues[2] } }.filter(String::isNotBlank).toList()
        val uploader = runCatching { user(JSONObject(get("/users/${track.user.id}"))) }.getOrDefault(track.user)
        ScTrackInfo(
            track = track,
            description = row.optString("description").takeIf { it != "null" }.orEmpty().trim(),
            tags = (listOfNotNull(track.genre.takeIf(String::isNotBlank)) + tags).distinct(),
            createdAt = row.optString("created_at"),
            releaseDate = row.optString("release_date").takeIf { it != "null" }.orEmpty(),
            reposts = row.optLong("reposts_count"),
            label = row.optString("label_name").takeIf { it != "null" }.orEmpty(),
            permalink = row.optString("permalink_url").takeIf { it.startsWith("http") }.orEmpty(),
        ) to uploader
    }

    internal fun webToken(): String? = token

    fun streamUrl(trackId: Long, hls: Boolean): String {
        val track = JSONObject(get("/tracks/$trackId"))
        val authorization = track.optString("track_authorization")
        val transcodings = track.optJSONObject("media")?.optJSONArray("transcodings").objects()
        require(transcodings.isNotEmpty()) { "这首歌在 SoundCloud 上不能播放" }
        fun protocol(row: JSONObject) = row.optJSONObject("format")?.optString("protocol").orEmpty()
        fun mime(row: JSONObject) = row.optJSONObject("format")?.optString("mime_type").orEmpty()
        val candidates = if (!hls) {
            transcodings.filter { protocol(it) == "progressive" }
        } else {
            val usable = transcodings.filter { protocol(it) == "hls" && !mime(it).contains("encrypted") && !mime(it).contains("mpegurl") }
            usable.filter { mime(it) == "audio/mpeg" } + usable.filter { mime(it).startsWith("audio/mp4") } +
                usable.filter { mime(it) != "audio/mpeg" && !mime(it).startsWith("audio/mp4") }
        }
        require(candidates.isNotEmpty()) { if (hls) "这首歌在 SoundCloud 上不能播放" else "这首歌只有 HLS 流" }
        val params = mutableMapOf<String, String>()
        if (authorization.isNotBlank()) params["track_authorization"] = authorization
        val url = candidates.firstNotNullOfOrNull { candidate ->
            runCatching { JSONObject(get(candidate.optString("url"), params)).optString("url") }.getOrNull()?.takeIf { it.startsWith("http") }
        } ?: error("SoundCloud 没有返回播放地址")
        if (hls) hlsBase[trackId] = url
        return url
    }

    fun hlsSegment(trackId: Long, relative: String): String {
        val base = hlsBase[trackId] ?: streamUrl(trackId, hls = true)
        return java.net.URI(base).resolve(relative).toString()
    }

    private fun user(row: JSONObject) = ScUser(
        id = row.optLong("id"),
        username = row.optString("username"),
        avatarUrl = row.optString("avatar_url").artwork(),
        followers = row.optInt("followers_count"),
        trackCount = row.optInt("track_count"),
        city = row.optString("city").takeIf { it != "null" }.orEmpty(),
        description = row.optString("description").takeIf { it != "null" }.orEmpty(),
        followings = row.optInt("followings_count"),
        likes = row.optInt("likes_count").takeIf { it > 0 } ?: row.optInt("public_favorites_count"),
        permalink = row.optString("permalink_url").takeIf { it.startsWith("http") }.orEmpty(),
    )

    private fun track(row: JSONObject): ScTrack? {
        val id = row.optLong("id").takeIf { it > 0 } ?: return null
        if (!row.has("title")) return null
        val transcodings = row.optJSONObject("media")?.optJSONArray("transcodings").objects()
        val protocols = transcodings.map { it.optJSONObject("format")?.optString("protocol").orEmpty() }
        return ScTrack(
            id = id,
            title = row.optString("title"),
            user = user(row.optJSONObject("user") ?: JSONObject()),
            artworkUrl = row.optString("artwork_url").artwork(),
            durationMs = row.optLong("full_duration").takeIf { it > 0 } ?: row.optLong("duration"),
            genre = row.optString("genre").takeIf { it != "null" }.orEmpty(),
            playCount = row.optLong("playback_count"),
            likeCount = row.optLong("likes_count"),
            commentCount = row.optLong("comment_count"),
            preview = row.optString("policy") == "SNIP" || transcodings.isNotEmpty() && transcodings.all { it.optBoolean("snipped") },
            streamable = row.optBoolean("streamable", true) && row.optString("policy") != "BLOCK",
            hlsOnly = protocols.isNotEmpty() && "progressive" !in protocols,
        )
    }

    private fun playlist(row: JSONObject): ScPlaylist {
        val urn = row.optString("urn")
        return ScPlaylist(
            id = row.optLong("id"),
            title = row.optString("title").ifBlank { row.optString("short_title") },
            user = user(row.optJSONObject("user") ?: JSONObject()),
            artworkUrl = (row.optString("artwork_url").artwork()
                ?: row.optString("calculated_artwork_url").artwork()
                ?: row.optJSONArray("tracks").objects().firstNotNullOfOrNull { it.optString("artwork_url").artwork() }),
            trackCount = row.optInt("track_count").takeIf { it > 0 } ?: row.optJSONArray("tracks")?.length() ?: 0,
            album = row.optBoolean("is_album") || row.optString("set_type") == "album",
            urn = urn.ifBlank { "soundcloud:playlists:${row.optLong("id")}" },
        )
    }

    private fun String.artwork(): String? = takeIf { it.isNotBlank() && it != "null" }?.replace("-large.", "-t500x500.")

    private fun JSONArray?.objects(): List<JSONObject> = this?.let { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it) } }.orEmpty()

    companion object {
        private const val API = "https://api-v2.soundcloud.com"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_TOKEN = "oauth_token"
        private const val KEY_ACCOUNT = "account"

        val GENRES = listOf(
            "Electronic", "Hip-hop & Rap", "Pop", "R&B & Soul", "Lo-fi", "House", "Techno", "Drum & Bass",
            "Dance & EDM", "Trap", "Ambient", "Chill", "Piano", "Classical", "Jazz & Blues", "Rock",
            "Indie", "Soundtrack", "Dubstep", "Phonk", "Vocaloid", "Anime",
        )
    }
}
