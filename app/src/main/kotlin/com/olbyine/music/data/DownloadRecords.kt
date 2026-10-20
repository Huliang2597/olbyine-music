package com.olbyine.music.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class DownloadRecord(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val fileName: String,
    val folder: String,
    val bytes: Long,
    val level: String,
    val audioUri: String,
    val lyricsUri: String?,
    val coverUri: String?,
    val at: Long,
    val tagged: Boolean = false,
    val coverEmbedded: Boolean = false,
)

class DownloadRecords(context: Context) {
    private val preferences = context.getSharedPreferences("download_records", Context.MODE_PRIVATE)

    fun all(): List<DownloadRecord> {
        val raw = preferences.getString(KEY, null)?.takeIf(String::isNotBlank) ?: return emptyList()
        return runCatching {
            val rows = JSONArray(raw)
            (0 until rows.length()).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                val id = row.optLong("songId", 0L)
                if (id <= 0L) return@mapNotNull null
                DownloadRecord(
                    songId = id,
                    title = row.optString("title"),
                    artist = row.optString("artist"),
                    album = row.optString("album"),
                    fileName = row.optString("fileName"),
                    folder = row.optString("folder"),
                    bytes = row.optLong("bytes"),
                    level = row.optString("level"),
                    audioUri = row.optString("audioUri"),
                    lyricsUri = row.optString("lyricsUri").takeIf(String::isNotBlank),
                    coverUri = row.optString("coverUri").takeIf(String::isNotBlank),
                    at = row.optLong("at"),
                    tagged = row.optBoolean("tagged"),
                    coverEmbedded = row.optBoolean("coverEmbedded"),
                )
            }.sortedByDescending { it.at }
        }.getOrDefault(emptyList())
    }

    fun save(records: List<DownloadRecord>) {
        val rows = JSONArray()
        records.forEach { record ->
            rows.put(
                JSONObject()
                    .put("songId", record.songId)
                    .put("title", record.title)
                    .put("artist", record.artist)
                    .put("album", record.album)
                    .put("fileName", record.fileName)
                    .put("folder", record.folder)
                    .put("bytes", record.bytes)
                    .put("level", record.level)
                    .put("audioUri", record.audioUri)
                    .put("lyricsUri", record.lyricsUri.orEmpty())
                    .put("coverUri", record.coverUri.orEmpty())
                    .put("at", record.at)
                    .put("tagged", record.tagged)
                    .put("coverEmbedded", record.coverEmbedded),
            )
        }
        preferences.edit().putString(KEY, rows.toString()).apply()
    }

    fun add(record: DownloadRecord) {
        save((all().filterNot { it.songId == record.songId }) + record)
    }

    fun remove(songId: Long) {
        save(all().filterNot { it.songId == songId })
    }

    fun clear() {
        preferences.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "records"
    }
}
