package com.olbyine.music.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 拉取远程文本（自定义源脚本、歌单 JSON）。 */
class LxHttp(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun fetch(url: String): String = String(fetchBytes(url), Charsets.UTF_8)

    suspend fun fetchBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val normalized = normalize(url)
        val request = Request.Builder()
            .url(normalized)
            .header("User-Agent", UA)
            .header("Accept", "*/*")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("请求失败（HTTP ${response.code}）")
            val length = response.body?.contentLength() ?: -1L
            if (length > MAX_BYTES) error("内容过大（${length / 1024 / 1024} MB）")
            val bytes = response.body?.bytes() ?: error("响应为空")
            if (bytes.size > MAX_BYTES) error("内容过大（${bytes.size / 1024 / 1024} MB）")
            bytes
        }
    }

    private fun normalize(url: String): String {
        val trimmed = url.trim()
        require(trimmed.isNotEmpty()) { "请输入链接" }
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        require(withScheme.startsWith("http://") || withScheme.startsWith("https://")) { "只支持 http/https 链接" }
        return withScheme
    }

    companion object {
        private const val MAX_BYTES = 9L * 1024 * 1024
        private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
    }
}
