package com.olbyine.music.data

import com.olbyine.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AppUpdate(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
)

class UpdateChecker {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Olbyine/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string().orEmpty().ifBlank { return@withContext null }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").ifBlank { return@withContext null }
            val version = tag.removePrefix("v").removePrefix("V").trim()
            if (version.isBlank() || !isNewer(version, BuildConfig.VERSION_NAME)) return@withContext null
            var apk: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val item = assets.optJSONObject(index) ?: continue
                    val name = item.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apk = item.optString("browser_download_url").takeIf(String::isNotBlank)
                        break
                    }
                }
            }
            AppUpdate(
                version = version,
                notes = json.optString("body").trim(),
                pageUrl = json.optString("html_url").ifBlank { "https://github.com/$REPO/releases" },
                apkUrl = apk,
            )
        }
    }

    companion object {
        const val REPO = "Huliang2597/olbyine-music"

        fun isNewer(candidate: String, current: String): Boolean {
            val left = parts(candidate)
            val right = parts(current)
            for (index in 0 until maxOf(left.size, right.size)) {
                val a = left.getOrElse(index) { 0 }
                val b = right.getOrElse(index) { 0 }
                if (a != b) return a > b
            }
            return false
        }

        private fun parts(version: String): List<Int> =
            version.split('.', '-', '+', '_', ' ')
                .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
                .dropLastWhile { it == 0 }
    }
}
