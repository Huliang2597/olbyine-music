package com.olbyine.music.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LxSourceInfo(
    val key: String,
    val name: String,
    val qualitys: List<String>,
)

data class LxScript(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val author: String,
    val code: String,
    val enabled: Boolean = true,
    val sources: List<LxSourceInfo> = emptyList(),
    val initialized: Boolean = false,
    val lastError: String = "",
) {
    val statusText: String
        get() = when {
            lastError.isNotBlank() -> "初始化失败：$lastError"
            initialized && sources.isNotEmpty() -> "已初始化 · " + sources.joinToString("、") { it.name }
            else -> "未初始化"
        }
}

class LxScriptStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("lx_sources", Context.MODE_PRIVATE)

    fun all(): List<LxScript> {
        val raw = preferences.getString(KEY, null)?.takeIf(String::isNotBlank) ?: return emptyList()
        return runCatching {
            val rows = JSONArray(raw)
            (0 until rows.length()).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                val id = row.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
                val code = row.optString("code")
                if (code.isBlank()) return@mapNotNull null
                LxScript(
                    id = id,
                    name = row.optString("name").ifBlank { "未命名源" },
                    description = row.optString("description"),
                    version = row.optString("version"),
                    author = row.optString("author"),
                    code = code,
                    enabled = row.optBoolean("enabled", true),
                    sources = parseSources(row.optJSONArray("sources")),
                    initialized = row.optBoolean("initialized", false),
                    lastError = row.optString("lastError"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(scripts: List<LxScript>) {
        val rows = JSONArray()
        scripts.forEach { script ->
            rows.put(
                JSONObject()
                    .put("id", script.id)
                    .put("name", script.name)
                    .put("description", script.description)
                    .put("version", script.version)
                    .put("author", script.author)
                    .put("code", script.code)
                    .put("enabled", script.enabled)
                    .put("sources", sourcesJson(script.sources))
                    .put("initialized", script.initialized)
                    .put("lastError", script.lastError),
            )
        }
        preferences.edit().putString(KEY, rows.toString()).apply()
    }

    fun add(script: LxScript) {
        save(all().filterNot { it.id == script.id } + script)
    }

    fun update(script: LxScript) {
        save(all().map { if (it.id == script.id) script else it })
    }

    fun remove(id: String) {
        save(all().filterNot { it.id == id })
    }

    /** 启用中的脚本；是否已初始化由调用方决定（未初始化的会先跑一次初始化）。 */
    fun enabled(): List<LxScript> = all().filter { it.enabled }

    fun markInitialized(id: String, sources: List<LxSourceInfo>) {
        val script = all().firstOrNull { it.id == id } ?: return
        update(script.copy(sources = sources, initialized = sources.isNotEmpty(), lastError = ""))
    }

    fun markFailed(id: String, message: String) {
        val script = all().firstOrNull { it.id == id } ?: return
        update(script.copy(initialized = false, lastError = message))
    }

    private fun parseSources(array: JSONArray?): List<LxSourceInfo> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val row = array.optJSONObject(index) ?: return@mapNotNull null
            val key = row.optString("key").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val qualitys = row.optJSONArray("qualitys")?.let { list ->
                (0 until list.length()).mapNotNull { list.optString(it).takeIf(String::isNotBlank) }
            }.orEmpty()
            LxSourceInfo(key = key, name = row.optString("name").ifBlank { key }, qualitys = qualitys)
        }
    }

    private fun sourcesJson(sources: List<LxSourceInfo>): JSONArray {
        val array = JSONArray()
        sources.forEach { source ->
            val qualities = JSONArray()
            source.qualitys.forEach { qualities.put(it) }
            array.put(
                JSONObject()
                    .put("key", source.key)
                    .put("name", source.name)
                    .put("qualitys", qualities),
            )
        }
        return array
    }

    companion object {
        private const val KEY = "scripts"

        fun parseHeader(code: String, field: String): String {
            val regex = Regex("@$field\\s+(.+)")
            return code.lineSequence()
                .take(30)
                .mapNotNull { regex.find(it)?.groupValues?.get(1)?.trim() }
                .firstOrNull()
                ?.takeIf { it.isNotBlank() }
                .orEmpty()
        }
    }
}
