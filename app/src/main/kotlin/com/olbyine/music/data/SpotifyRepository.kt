package com.olbyine.music.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

data class SpotifyPlaylistSummary(val id: String, val name: String, val owner: String, val trackCount: Int, val imageUrl: String?)

class SpotifyRepository(context: Context) {
    private val preferences = context.getSharedPreferences("spotify_auth", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val tokenMutex = Mutex()
    private val _connected = MutableStateFlow(preferences.getString(KEY_REFRESH, null) != null)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    val clientId: String get() = preferences.getString(KEY_CLIENT_ID, null).orEmpty()

    fun authorizeUrl(clientId: String): Uri {
        val id = clientId.trim()
        require(id.matches(Regex("^[0-9a-fA-F]{32}$"))) { "Client ID 应为 32 位十六进制字符" }
        val verifier = randomString(64)
        val state = randomString(16)
        preferences.edit()
            .putString(KEY_CLIENT_ID, id)
            .putString(KEY_VERIFIER, verifier)
            .putString(KEY_STATE, state)
            .apply()
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        return Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", id)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", SCOPES)
            .build()
    }

    suspend fun completeLogin(redirect: Uri) = withContext(Dispatchers.IO) {
        redirect.getQueryParameter("error")?.let { error(if (it == "access_denied") "已取消授权" else "Spotify 授权失败：$it") }
        val code = redirect.getQueryParameter("code") ?: error("授权回调缺少 code")
        require(redirect.getQueryParameter("state") == preferences.getString(KEY_STATE, null)) { "授权状态不匹配，请重试" }
        val verifier = preferences.getString(KEY_VERIFIER, null) ?: error("授权已过期，请重试")
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("client_id", clientId)
            .add("code_verifier", verifier)
            .build()
        storeTokens(postToken(body))
        preferences.edit().remove(KEY_VERIFIER).remove(KEY_STATE).apply()
        _connected.value = true
    }

    fun logout() {
        preferences.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_EXPIRES).apply()
        _connected.value = false
    }

    suspend fun myPlaylists(): List<SpotifyPlaylistSummary> = withContext(Dispatchers.IO) {
        val result = mutableListOf<SpotifyPlaylistSummary>()
        var next: String? = "$API/me/playlists?limit=50"
        while (next != null && result.size < 500) {
            val page = get(next)
            page.optJSONArray("items")?.let { items ->
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    result += SpotifyPlaylistSummary(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        owner = item.optJSONObject("owner")?.optString("display_name").orEmpty(),
                        trackCount = item.optJSONObject("tracks")?.optInt("total") ?: 0,
                        imageUrl = item.optJSONArray("images")?.optJSONObject(0)?.optString("url"),
                    )
                }
            }
            next = page.optString("next").takeIf { it.startsWith("https://") }
        }
        result
    }

    suspend fun playlist(input: String): RemotePlaylist = withContext(Dispatchers.IO) {
        val id = Regex("playlist[/:]([A-Za-z0-9]{22})").find(input)?.groupValues?.get(1)
            ?: input.trim().takeIf { it.matches(Regex("^[A-Za-z0-9]{22}$")) }
            ?: error("没有识别到 Spotify 歌单链接")
        val meta = get("$API/playlists/$id?fields=name,owner(display_name),images")
        val songs = mutableListOf<RemoteSong>()
        var next: String? = "$API/playlists/$id/tracks?limit=100&fields=next,items(track(id,name,duration_ms,artists(name),album(name,images)))"
        while (next != null && songs.size < 3000) {
            val page = get(next)
            page.optJSONArray("items")?.let { items ->
                for (i in 0 until items.length()) {
                    val track = items.optJSONObject(i)?.optJSONObject("track") ?: continue
                    val artists = track.optJSONArray("artists")
                    songs += RemoteSong(
                        id = track.optString("id").ifBlank { "local-$i-${songs.size}" },
                        title = track.optString("name"),
                        artists = (0 until (artists?.length() ?: 0)).mapNotNull { artists?.optJSONObject(it)?.optString("name") },
                        album = track.optJSONObject("album")?.optString("name").orEmpty(),
                        durationMs = track.optLong("duration_ms"),
                        coverUrl = track.optJSONObject("album")?.optJSONArray("images")?.optJSONObject(0)?.optString("url"),
                    )
                }
            }
            next = page.optString("next").takeIf { it.startsWith("https://") }
        }
        RemotePlaylist(
            id = id,
            name = meta.optString("name"),
            coverUrl = meta.optJSONArray("images")?.optJSONObject(0)?.optString("url"),
            creator = meta.optJSONObject("owner")?.optString("display_name").orEmpty(),
            songs = songs,
        )
    }

    private suspend fun get(url: String): JSONObject {
        val token = accessToken()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401) {
                logout()
                error("Spotify 登录已失效，请重新连接")
            }
            require(response.isSuccessful) {
                when (response.code) {
                    403 -> "Spotify 拒绝访问：请确认该账号已加入你的开发者应用的用户列表"
                    404 -> "歌单不存在或不可访问"
                    429 -> "请求过于频繁，请稍后再试"
                    else -> "Spotify 请求失败（HTTP ${response.code}）"
                }
            }
            return JSONObject(response.body?.string().orEmpty())
        }
    }

    private suspend fun accessToken(): String = tokenMutex.withLock {
        val access = preferences.getString(KEY_ACCESS, null)
        if (access != null && System.currentTimeMillis() < preferences.getLong(KEY_EXPIRES, 0L) - 60_000L) return@withLock access
        val refresh = preferences.getString(KEY_REFRESH, null) ?: error("请先连接 Spotify")
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .add("client_id", clientId)
            .build()
        storeTokens(postToken(body))
        preferences.getString(KEY_ACCESS, null) ?: error("Spotify 令牌无效")
    }

    private fun postToken(body: FormBody): JSONObject {
        val request = Request.Builder().url("https://accounts.spotify.com/api/token").post(body).build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            require(response.isSuccessful) {
                val reason = runCatching { JSONObject(text).optString("error_description") }.getOrNull().orEmpty()
                "Spotify 授权失败：${reason.ifBlank { "HTTP ${response.code}" }}"
            }
            return JSONObject(text)
        }
    }

    private fun storeTokens(json: JSONObject) {
        val editor = preferences.edit()
            .putString(KEY_ACCESS, json.getString("access_token"))
            .putLong(KEY_EXPIRES, System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L)
        json.optString("refresh_token").takeIf(String::isNotBlank)?.let { editor.putString(KEY_REFRESH, it) }
        editor.apply()
    }

    private fun randomString(length: Int): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random = SecureRandom()
        return String(CharArray(length) { alphabet[random.nextInt(alphabet.length)] })
    }

    companion object {
        const val REDIRECT_URI = "olbyine://spotify-callback"
        private const val API = "https://api.spotify.com/v1"
        private const val SCOPES = "playlist-read-private playlist-read-collaborative"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_VERIFIER = "verifier"
        private const val KEY_STATE = "state"
        private const val KEY_ACCESS = "access"
        private const val KEY_REFRESH = "refresh"
        private const val KEY_EXPIRES = "expires"
    }
}
