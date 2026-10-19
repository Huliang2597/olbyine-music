package com.olbyine.music.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.URI
import java.net.URLDecoder
import java.security.KeyStore
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class WebDavServer(
    val id: String,
    val name: String,
    val baseUrl: String,
    val username: String,
)

data class WebDavEntry(
    val url: String,
    val name: String,
    val directory: Boolean,
    val size: Long,
    val contentType: String?,
    val modified: Long,
)

class WebDavRepository(context: Context) {
    private val preferences = context.getSharedPreferences("webdav", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val _servers = MutableStateFlow(readServers())
    val servers: StateFlow<List<WebDavServer>> = _servers.asStateFlow()

    fun save(server: WebDavServer, password: String?): WebDavServer {
        val normalized = server.copy(
            id = server.id.ifBlank { UUID.randomUUID().toString() },
            name = server.name.trim().ifBlank { hostOf(server.baseUrl) },
            baseUrl = normalizeUrl(server.baseUrl),
            username = server.username.trim(),
        )
        val list = _servers.value.filterNot { it.id == normalized.id } + normalized
        writeServers(list)
        if (password != null) preferences.edit().putString(KEY_PASSWORD + normalized.id, encrypt(password)).apply()
        return normalized
    }

    fun remove(id: String) {
        writeServers(_servers.value.filterNot { it.id == id })
        preferences.edit().remove(KEY_PASSWORD + id).apply()
    }

    fun hasPassword(id: String): Boolean = preferences.contains(KEY_PASSWORD + id)

    fun headersFor(url: String): Map<String, String> {
        val server = _servers.value.firstOrNull { url.startsWith(it.baseUrl, ignoreCase = true) } ?: return emptyMap()
        val password = password(server.id) ?: return emptyMap()
        if (server.username.isBlank()) return emptyMap()
        return mapOf("Authorization" to Credentials.basic(server.username, password, Charsets.UTF_8))
    }

    private fun password(id: String): String? = preferences.getString(KEY_PASSWORD + id, null)?.let { runCatching { decrypt(it) }.getOrNull() }

    suspend fun test(server: WebDavServer, password: String?): Result<Int> = runCatching {
        list(normalizeUrl(server.baseUrl), server.username.trim(), password ?: password(server.id).orEmpty()).size
    }

    suspend fun list(server: WebDavServer, url: String = server.baseUrl): List<WebDavEntry> =
        list(url, server.username, password(server.id).orEmpty())

    private suspend fun list(url: String, username: String, password: String): List<WebDavEntry> = withContext(Dispatchers.IO) {
        val folder = if (url.endsWith("/")) url else "$url/"
        val request = Request.Builder()
            .url(folder)
            .method("PROPFIND", PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType()))
            .header("Depth", "1")
            .apply { if (username.isNotBlank()) header("Authorization", Credentials.basic(username, password, Charsets.UTF_8)) }
            .build()
        client.newCall(request).execute().use { response ->
            when (response.code) {
                401 -> error("用户名或密码不对")
                403 -> error("没有访问这个文件夹的权限")
                404 -> error("找不到这个地址（检查 WebDAV 路径）")
            }
            require(response.code == 207 || response.isSuccessful) { "服务器返回 HTTP ${response.code}" }
            val body = response.body?.string().orEmpty()
            parseMultistatus(body, response.request.url.toString())
                .filterNot { sameResource(it.url, folder) }
                .sortedWith(compareByDescending<WebDavEntry> { it.directory }.thenBy { it.name.lowercase(Locale.ROOT) })
        }
    }

    suspend fun collectAudio(server: WebDavServer, url: String, maxDepth: Int = 8, onFound: (Int) -> Unit = {}): List<WebDavAudio> {
        val result = mutableListOf<WebDavAudio>()
        val queue = ArrayDeque<Pair<String, Int>>().apply { add(url to 0) }
        while (queue.isNotEmpty() && result.size < MAX_FILES) {
            val (folder, depth) = queue.removeFirst()
            val entries = if (depth == 0) list(server, folder) else runCatching { list(server, folder) }.getOrNull() ?: continue
            val lyrics = entries.filter { !it.directory && it.name.substringAfterLast('.', "").lowercase(Locale.ROOT) in LYRIC_EXTENSIONS }
                .associateBy { it.name.substringBeforeLast('.').lowercase(Locale.ROOT) }
            val folderPath = "WebDAV · ${server.name}" + decode(folder.removePrefix(server.baseUrl).trimEnd('/')).let { if (it.isBlank()) "" else "/$it" }
            for (entry in entries) {
                if (entry.directory) {
                    if (depth < maxDepth && !entry.name.startsWith(".")) queue.add(entry.url to depth + 1)
                } else if (MusicLibraryRepository.isSupportedAudio(entry.name, entry.contentType)) {
                    result += WebDavAudio(
                        entry = entry,
                        folderId = "webdav:${server.id}:${folder.removePrefix(server.baseUrl)}",
                        folderPath = folderPath,
                        lyricUrl = lyrics[entry.name.substringBeforeLast('.').lowercase(Locale.ROOT)]?.url,
                    )
                }
            }
            onFound(result.size)
        }
        return result
    }

    private fun parseMultistatus(xml: String, requestUrl: String): List<WebDavEntry> {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(xml.reader())
        val entries = mutableListOf<WebDavEntry>()
        var href: String? = null
        var name: String? = null
        var directory = false
        var size = 0L
        var type: String? = null
        var modified = 0L
        var okStatus = true
        var inPropstat = false
        var text = StringBuilder()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    text = StringBuilder()
                    when (parser.name.lowercase(Locale.ROOT)) {
                        "response" -> {
                            href = null; name = null; directory = false; size = 0L; type = null; modified = 0L
                        }
                        "propstat" -> { inPropstat = true; okStatus = true }
                        "collection" -> if (okStatus) directory = true
                    }
                }
                XmlPullParser.TEXT -> text.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val value = text.toString().trim()
                    when (parser.name.lowercase(Locale.ROOT)) {
                        "href" -> if (href == null) href = value
                        "status" -> if (inPropstat) okStatus = value.contains(" 200")
                        "displayname" -> if (value.isNotBlank()) name = value
                        "getcontentlength" -> size = value.toLongOrNull() ?: size
                        "getcontenttype" -> if (value.isNotBlank()) type = value
                        "getlastmodified" -> modified = runCatching {
                            java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(value)?.time ?: 0L
                        }.getOrDefault(0L)
                        "propstat" -> inPropstat = false
                        "response" -> {
                            val link = href
                            if (link != null) {
                                val absolute = runCatching { URI(requestUrl).resolve(link.replace(" ", "%20")).toString() }.getOrDefault(link)
                                val shown = name ?: decode(absolute.trimEnd('/').substringAfterLast('/'))
                                entries += WebDavEntry(absolute, shown, directory || link.endsWith("/"), size, type, modified)
                            }
                        }
                    }
                    text = StringBuilder()
                }
            }
            parser.next()
        }
        return entries
    }

    private fun sameResource(a: String, b: String) = decode(a.trimEnd('/')).equals(decode(b.trimEnd('/')), ignoreCase = true) ||
        runCatching { URI(a).path.trimEnd('/') == URI(b).path.trimEnd('/') }.getOrDefault(false)

    private fun decode(value: String): String = runCatching { URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrDefault(value)

    private fun normalizeUrl(raw: String): String {
        var url = raw.trim()
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) url = "https://$url"
        return if (url.endsWith("/")) url else "$url/"
    }

    private fun hostOf(url: String) = runCatching { URI(normalizeUrl(url)).host }.getOrNull() ?: "WebDAV"

    private fun readServers(): List<WebDavServer> = runCatching {
        val array = JSONArray(preferences.getString(KEY_SERVERS, "[]"))
        (0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            WebDavServer(row.getString("id"), row.optString("name"), row.getString("url"), row.optString("user"))
        }
    }.getOrDefault(emptyList())

    private fun writeServers(list: List<WebDavServer>) {
        val array = JSONArray()
        list.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.baseUrl).put("user", it.username)) }
        preferences.edit().putString(KEY_SERVERS, array.toString()).apply()
        _servers.value = list
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val (iv, sealed) = stored.split(':', limit = 2).map { Base64.decode(it, Base64.NO_WRAP) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(sealed), Charsets.UTF_8)
    }

    companion object {
        private const val KEY_SERVERS = "servers"
        private const val KEY_PASSWORD = "password_"
        private const val KEY_ALIAS = "olbyine_webdav"
        private const val MAX_FILES = 20_000
        private val LYRIC_EXTENSIONS = setOf("lrc", "elrc", "yrc", "ttml")
        private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:displayname/><d:resourcetype/><d:getcontentlength/><d:getcontenttype/><d:getlastmodified/></d:prop></d:propfind>"""

        val PRESETS = listOf(
            "坚果云" to "https://dav.jianguoyun.com/dav/",
            "Alist" to "http://192.168.1.2:5244/dav/",
            "Nextcloud" to "https://example.com/remote.php/dav/files/用户名/",
            "群晖 NAS" to "http://192.168.1.2:5005/",
            "Infini-Cloud" to "https://jike.teracloud.jp/dav/",
        )
    }
}

data class WebDavAudio(
    val entry: WebDavEntry,
    val folderId: String,
    val folderPath: String,
    val lyricUrl: String?,
)
