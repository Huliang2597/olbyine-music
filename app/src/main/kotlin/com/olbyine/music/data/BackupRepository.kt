package com.olbyine.music.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class BackupSummary(
    val createdAt: Long,
    val entries: Int,
    val keys: List<String>,
)

class BackupRepository(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun exportJson(): String = buildJson()

    suspend fun upload(server: WebDavServer, password: String, fileName: String = FILE_NAME): String = withContext(Dispatchers.IO) {
        val folder = if (server.baseUrl.endsWith("/")) server.baseUrl else "${server.baseUrl}/"
        val url = folder + fileName
        val body = buildJson().toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url(url)
            .put(body)
            .apply { if (server.username.isNotBlank()) header("Authorization", Credentials.basic(server.username, password, Charsets.UTF_8)) }
            .build()
        client.newCall(request).execute().use { response ->
            when (response.code) {
                401 -> error("用户名或密码不对")
                403 -> error("没有写入这个文件夹的权限")
                404 -> error("找不到这个地址（检查 WebDAV 路径）")
            }
            require(response.isSuccessful || response.code == 201 || response.code == 204) {
                "服务器返回 HTTP ${response.code}"
            }
            url
        }
    }

    suspend fun download(server: WebDavServer, password: String, fileName: String = FILE_NAME): String = withContext(Dispatchers.IO) {
        val folder = if (server.baseUrl.endsWith("/")) server.baseUrl else "${server.baseUrl}/"
        val request = Request.Builder()
            .url(folder + fileName)
            .get()
            .apply { if (server.username.isNotBlank()) header("Authorization", Credentials.basic(server.username, password, Charsets.UTF_8)) }
            .build()
        client.newCall(request).execute().use { response ->
            when (response.code) {
                401 -> error("用户名或密码不对")
                403 -> error("没有读取这个文件的权限")
                404 -> error("云端没有找到 $fileName")
            }
            require(response.isSuccessful) { "服务器返回 HTTP ${response.code}" }
            response.body?.string().orEmpty().ifBlank { error("云端文件是空的") }
        }
    }

    fun summarize(json: String): BackupSummary? = runCatching {
        val root = JSONObject(json)
        val stores = root.optJSONObject("stores") ?: return null
        BackupSummary(
            createdAt = root.optLong("createdAt"),
            entries = root.optInt("entries"),
            keys = stores.keys().asSequence().toList(),
        )
    }.getOrNull()

    fun restore(json: String): BackupSummary = runCatching {
        val root = JSONObject(json)
        val version = root.optInt("version", 0)
        require(version in 1..VERSION) { "备份文件版本不兼容（v$version）" }
        val stores = root.optJSONObject("stores") ?: error("备份文件里没有数据")
        var entries = 0
        val keys = stores.keys().asSequence().toList()
        keys.forEach { name ->
            val values = stores.optJSONObject(name) ?: return@forEach
            val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val editor = preferences.edit()
            values.keys().asSequence().forEach { key ->
                writeValue(editor, key, values.opt(key))
                entries++
            }
            editor.apply()
        }
        BackupSummary(root.optLong("createdAt"), entries, keys)
    }.getOrElse { error(it.message ?: "备份文件格式不对") }

    private fun writeValue(editor: android.content.SharedPreferences.Editor, key: String, raw: Any?) {
        if (raw is JSONObject) {
            val value = raw.opt("v")
            when (raw.optString("t")) {
                "b" -> editor.putBoolean(key, value as? Boolean ?: false)
                "i" -> editor.putInt(key, (value as? Number)?.toInt() ?: 0)
                "l" -> editor.putLong(key, (value as? Number)?.toLong() ?: 0L)
                "f" -> editor.putFloat(key, (value as? Number)?.toFloat() ?: 0f)
                "s" -> editor.putString(key, value?.toString() ?: "")
                "n" -> editor.remove(key)
                else -> editor.putString(key, value?.toString() ?: "")
            }
            return
        }
        // 兼容早期没有类型标记的备份
        when (raw) {
            is Boolean -> editor.putBoolean(key, raw)
            is Int -> editor.putInt(key, raw)
            is Long -> editor.putLong(key, raw)
            is Double -> editor.putFloat(key, raw.toFloat())
            is String -> editor.putString(key, raw)
            null -> editor.remove(key)
            else -> editor.putString(key, raw.toString())
        }
    }

    private fun tag(type: String, value: Any): JSONObject = JSONObject().put("t", type).put("v", value)

    private fun buildJson(): String {
        val stores = JSONObject()
        var total = 0
        STORES.forEach { name ->
            val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val values = JSONObject()
            preferences.all.forEach { (key, value) ->
                when (value) {
                    is Boolean -> values.put(key, tag("b", value))
                    is Int -> values.put(key, tag("i", value))
                    is Long -> values.put(key, tag("l", value))
                    is Float -> values.put(key, tag("f", value.toDouble()))
                    is String -> values.put(key, tag("s", value))
                    is Set<*> -> {
                        val array = JSONArray()
                        value.filterNotNull().forEach { array.put(it.toString()) }
                        values.put(key, tag("s", value.filterNotNull().joinToString(SEPARATOR) { it.toString() }))
                    }
                    else -> return@forEach
                }
                total++
            }
            if (values.length() > 0) stores.put(name, values)
        }
        return JSONObject()
            .put("version", VERSION)
            .put("app", "Olbyine")
            .put("createdAt", System.currentTimeMillis())
            .put("entries", total)
            .put("stores", stores)
            .toString(2)
    }

    companion object {
        const val FILE_NAME = "olbyine-backup.json"
        private const val VERSION = 2
        private const val SEPARATOR = "\u0001"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val STORES = listOf(
            "native_settings",
            "native_playback",
            "native_library_view",
            "netease_account",
            "soundcloud",
            "spotify_auth",
            "navidrome",
            "apple_music",
            "webdav",
            "download_records",
            "motion_artwork",
        )
    }
}
