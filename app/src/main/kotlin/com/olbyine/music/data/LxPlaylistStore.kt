package com.olbyine.music.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class LxPlaylist(
    val id: String,
    val name: String,
    val songs: List<KuwoSong>,
    val createdAt: Long = 0L,
    val origin: String = "",
    /** 导出回洛雪时用的原始 name（默认列表是 list__name_default 这类语言键）。 */
    val rawName: String = "",
    val locationUpdateTime: Long? = null,
) {
    fun toCollection(): KuwoCollection = KuwoCollection(
        title = name,
        coverUrl = songs.firstOrNull()?.coverUrl,
        songs = songs,
        description = listOfNotNull(
            origin.takeIf { it.isNotBlank() },
            "${songs.size} 首",
        ).joinToString(" · "),
    )
}

/** 从洛雪音乐导出的歌单里解析出的单条曲目（尚未映射到可用音源）。 */
data class LxEntry(
    val source: String,
    val songmid: String,
    val name: String,
    val singer: String,
    val albumName: String,
    val interval: String,
    val cover: String?,
    val albumId: Long = 0L,
)

data class LxParsedPlaylist(
    val name: String,
    val entries: List<LxEntry>,
    val rawName: String = "",
    val locationUpdateTime: Long? = null,
)

class LxPlaylistStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("lx_playlists", Context.MODE_PRIVATE)

    fun all(): List<LxPlaylist> {
        val raw = preferences.getString(KEY, null)?.takeIf(String::isNotBlank) ?: return emptyList()
        return runCatching {
            val rows = JSONArray(raw)
            (0 until rows.length()).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                val id = row.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
                val songs = row.optJSONArray("songs")?.let { array ->
                    (0 until array.length()).mapNotNull { songIndex ->
                        val song = array.optJSONObject(songIndex) ?: return@mapNotNull null
                        val songId = song.optLong("id", 0L)
                        if (songId <= 0L) return@mapNotNull null
                        KuwoSong(
                            id = songId,
                            title = song.optString("title"),
                            artist = song.optString("artist"),
                            album = song.optString("album"),
                            durationMs = song.optLong("durationMs", 0L),
                            coverUrl = song.optString("coverUrl").takeIf(String::isNotBlank),
                            albumId = song.optLong("albumId", 0L),
                        )
                    }
                }.orEmpty()
                LxPlaylist(
                    id = id,
                    name = row.optString("name").ifBlank { "导入的歌单" },
                    songs = songs,
                    createdAt = row.optLong("createdAt", 0L),
                    origin = row.optString("origin"),
                    rawName = row.optString("rawName"),
                    locationUpdateTime = row.optLong("locationUpdateTime", 0L).takeIf { it > 0L },
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(playlists: List<LxPlaylist>) {
        val rows = JSONArray()
        playlists.forEach { playlist ->
            val songs = JSONArray()
            playlist.songs.forEach { song ->
                songs.put(
                    JSONObject()
                        .put("id", song.id)
                        .put("title", song.title)
                        .put("artist", song.artist)
                        .put("album", song.album)
                        .put("durationMs", song.durationMs)
                        .put("coverUrl", song.coverUrl.orEmpty())
                        .put("albumId", song.albumId),
                )
            }
            rows.put(
                JSONObject()
                    .put("id", playlist.id)
                    .put("name", playlist.name)
                    .put("songs", songs)
                    .put("createdAt", playlist.createdAt)
                    .put("origin", playlist.origin)
                    .put("rawName", playlist.rawName)
                    .put("locationUpdateTime", playlist.locationUpdateTime ?: 0L),
            )
        }
        preferences.edit().putString(KEY, rows.toString()).apply()
    }

    fun add(playlist: LxPlaylist) {
        save(all().filterNot { it.id == playlist.id } + playlist)
    }

    fun remove(id: String) {
        save(all().filterNot { it.id == id })
    }

    fun find(id: String): LxPlaylist? = all().firstOrNull { it.id == id }

    fun create(name: String): LxPlaylist {
        val playlist = LxPlaylist(
            id = newId(),
            name = name.ifBlank { "新建歌单" },
            songs = emptyList(),
            createdAt = System.currentTimeMillis(),
            origin = "本地歌单",
        )
        add(playlist)
        return playlist
    }

    fun rename(id: String, name: String) {
        val target = find(id) ?: return
        // 改名后导出应该用新名字，所以连同 rawName（导入时保留的语言键/原名）一起清掉
        add(target.copy(name = name.ifBlank { target.name }, rawName = ""))
    }

    /** 追加歌曲，自动去重（同 songId 只保留一份）。 */
    fun addSongs(id: String, songs: List<KuwoSong>): Int {
        val target = find(id) ?: return 0
        val existing = target.songs.map { it.id }.toMutableSet()
        val fresh = songs.filter { existing.add(it.id) }
        if (fresh.isEmpty()) return 0
        add(target.copy(songs = target.songs + fresh))
        return fresh.size
    }

    fun removeSong(id: String, songId: Long) {
        val target = find(id) ?: return
        add(target.copy(songs = target.songs.filterNot { it.id == songId }))
    }

    companion object {
        private const val KEY = "playlists"

        fun newId(): String = UUID.randomUUID().toString()
    }
}

/**
 * 洛雪音乐的配置文件编解码。
 *
 * `.lxmc` 就是 gzip 压缩后的 JSON（`saveLxConfigFile` = `gzip(JSON.stringify(data))`），
 * `.json` 则是同一份 JSON 的明文。读取时按 gzip 魔数判断，不依赖扩展名。
 */
object LxCodec {
    private val GZIP_MAGIC = byteArrayOf(0x1f, 0x8b.toByte())

    fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) error("文件内容为空")
        val text = if (bytes.size >= 2 && bytes[0] == GZIP_MAGIC[0] && bytes[1] == GZIP_MAGIC[1]) {
            val inflated = runCatching {
                GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
            }.getOrElse { error("不是有效的 lxmc 文件（gzip 解压失败）") }
            String(inflated, Charsets.UTF_8)
        } else {
            val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
            String(bytes, start, bytes.size - start, Charsets.UTF_8)
        }
        return text.trim()
    }

    fun encode(playlist: LxPlaylist): ByteArray = gzip(exportJson(playlist).toString())

    fun exportJson(playlist: LxPlaylist): JSONObject {
        val list = JSONArray()
        playlist.songs.forEach { song -> list.put(musicInfo(song)) }
        // 默认列表/收藏列表的 name 是语言键，导出时换成可读名字，避免在原版里显示 list__name_default
        val name = when {
            playlist.rawName.startsWith("list__name_") -> lxDisplayName(playlist.rawName)
            playlist.rawName.isNotBlank() -> playlist.rawName
            else -> playlist.name
        }
        return JSONObject()
            .put("type", "playListPart_v2")
            .put(
                "data",
                JSONObject()
                    .put("id", playlist.id)
                    .put("name", name)
                    .put("locationUpdateTime", playlist.locationUpdateTime ?: JSONObject.NULL)
                    .put("list", list),
            )
    }

    /** 单曲的洛雪 musicInfo 结构（v2），字段与原版导出保持一致。 */
    private fun musicInfo(song: KuwoSong): JSONObject {
        val qualities = JSONArray()
        val qualityMap = JSONObject()
        listOf("128k", "320k", "flac").forEach { type ->
            qualities.put(JSONObject().put("type", type).put("size", JSONObject.NULL))
            qualityMap.put(type, JSONObject().put("size", JSONObject.NULL))
        }
        val meta = JSONObject()
            .put("songId", song.id.toString())
            .put("albumName", song.album)
            .put("qualitys", qualities)
            .put("_qualitys", qualityMap)
        song.coverUrl?.let { meta.put("picUrl", it) }
        if (song.albumId > 0L) meta.put("albumId", song.albumId.toString())
        return JSONObject()
            .put("id", "kw_${song.id}")
            .put("name", song.title)
            .put("singer", song.artist)
            .put("source", "kw")
            .put("interval", formatInterval(song.durationMs))
            .put("meta", meta)
    }

    fun formatInterval(durationMs: Long): String {
        if (durationMs <= 0L) return "00:00"
        val total = durationMs / 1000
        return String.format(java.util.Locale.US, "%02d:%02d", total / 60, total % 60)
    }

    fun gzip(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }
}

/** 默认列表/收藏列表的 name 是语言键，显示成中文。 */
internal fun lxDisplayName(raw: String): String = when (raw) {
    "list__name_default" -> "试听列表"
    "list__name_love" -> "我的收藏"
    else -> raw
}

/**
 * 兼容洛雪音乐的导出格式：
 * 1. `{type:'playListPart_v2', data:{id, name, list:[musicInfo…]}}` ← 单歌单 .lxmc
 * 2. `{type:'playList_v2', data:[{name, list:[musicInfo…]}]}` ← 全部歌单
 * 3. `{type:'playList', data:[{name, list:[…]}]}` ← 旧版
 * 4. `[{name, list:[…]}]` / `{name, songs:[…]}` / 纯曲目数组
 */
object LxPlaylistParser {
    fun parse(text: String, fallbackName: String = "导入的歌单"): List<LxParsedPlaylist> {
        val trimmed = text.trim().removePrefix("\uFEFF")
        require(trimmed.isNotEmpty()) { "文件内容为空" }
        var root: Any = runCatching { org.json.JSONTokener(trimmed).nextValue() }.getOrNull()
            ?: error("不是有效的 JSON")
        // 修复 v1.14.0 里「被序列化两次」的导出数据
        if (root is String) {
            root = runCatching { org.json.JSONTokener(root as String).nextValue() }.getOrNull() ?: error("不是有效的 JSON")
        }

        val lists = ArrayList<JSONObject>()
        when (root) {
            is JSONObject -> {
                when (val data = root.opt("data")) {
                    is JSONArray -> for (index in 0 until data.length()) {
                        data.optJSONObject(index)?.let { lists += it }
                    }
                    is JSONObject -> lists += data
                    else -> if (root.has("list") || root.has("songs") || root.has("tracks")) lists += root
                }
                if (lists.isEmpty() && (root.has("list") || root.has("songs") || root.has("tracks"))) lists += root
            }
            is JSONArray -> {
                val first = root.optJSONObject(0)
                if (first != null && (first.has("list") || first.has("songs") || first.has("tracks"))) {
                    for (index in 0 until root.length()) {
                        root.optJSONObject(index)?.let { lists += it }
                    }
                } else {
                    lists += JSONObject().put("name", fallbackName).put("list", root)
                }
            }
        }
        require(lists.isNotEmpty()) { "文件里没有找到歌单" }

        return lists.mapNotNull { list ->
            val rawName = list.optString("name").ifBlank { list.optString("title") }
            val songs = list.optJSONArray("list")
                ?: list.optJSONArray("songs")
                ?: list.optJSONArray("tracks")
                ?: JSONArray()
            val entries = ArrayList<LxEntry>()
            for (index in 0 until songs.length()) {
                val row = songs.optJSONObject(index) ?: continue
                entries += toEntry(row)
            }
            if (entries.isEmpty()) return@mapNotNull null
            LxParsedPlaylist(
                name = lxDisplayName(rawName).ifBlank { fallbackName },
                entries = entries,
                rawName = rawName,
                locationUpdateTime = list.optLong("locationUpdateTime", 0L).takeIf { it > 0L },
            )
        }
    }

    private fun toEntry(row: JSONObject): LxEntry {
        val meta = row.optJSONObject("meta")
        val source = row.optString("source").ifBlank { meta?.optString("source").orEmpty() }.ifBlank { "kw" }
        val rawId = row.optString("id")
        val songmid = row.optString("songmid").ifBlank {
            row.optString("songId").ifBlank {
                meta?.optString("songId").orEmpty()
            }
        }.ifBlank { rawId.removePrefix("${source}_") }.ifBlank { rawId }
        val name = row.optString("name").ifBlank { row.optString("title") }.ifBlank { row.optString("songname") }
        val singer = row.optString("singer").ifBlank {
            row.optString("artist").ifBlank {
                row.optString("artists").ifBlank { meta?.optString("artist").orEmpty() }
            }
        }
        val album = row.optString("albumName").ifBlank { row.optString("album").ifBlank { row.optString("albumname") } }
            .ifBlank { meta?.optString("albumName").orEmpty() }
        val interval = row.optString("interval").ifBlank { row.optString("duration") }
        val cover = row.optString("img").ifBlank { row.optString("pic").ifBlank { row.optString("cover") } }
            .ifBlank { meta?.optString("picUrl").orEmpty() }
            .takeIf { it.startsWith("http") }
        return LxEntry(
            source = source,
            songmid = songmid,
            name = name,
            singer = singer,
            albumName = album,
            interval = interval,
            cover = cover,
            albumId = meta?.optString("albumId")?.toLongOrNull() ?: 0L,
        )
    }

    fun intervalToMs(interval: String): Long {
        val text = interval.trim()
        if (text.isBlank()) return 0L
        if (text.contains(':')) {
            val parts = text.split(':')
            val minutes = parts.getOrNull(0)?.toLongOrNull() ?: 0L
            val seconds = parts.getOrNull(1)?.toLongOrNull() ?: 0L
            return (minutes * 60 + seconds) * 1000L
        }
        val seconds = text.removeSuffix("s").toLongOrNull() ?: return 0L
        return if (seconds > 10_000L) seconds else seconds * 1000L
    }
}
