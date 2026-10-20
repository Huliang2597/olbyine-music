package com.olbyine.music.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater

data class KuwoSong(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val coverUrl: String?,
    val albumId: Long = 0L,
) {
    fun toTrack(): Track = Track(
        id = trackId(id),
        uri = "kw://song/$id",
        title = title.ifBlank { "未知歌曲" },
        artist = artist.ifBlank { "未知艺术家" },
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
        fun trackId(songId: Long) = "kw:$songId"
    }
}

data class KuwoBoard(
    val id: String,
    val name: String,
    val coverUrl: String? = null,
    val info: String = "",
)

data class KuwoPlaylist(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val songCount: Int,
    val playCount: Long,
    val creator: String,
    val tags: String,
    val description: String = "",
)

data class KuwoCollection(
    val title: String,
    val coverUrl: String?,
    val songs: List<KuwoSong>,
    val description: String = "",
)

class KuwoClient(private val context: Context) {
    private val known = java.util.concurrent.ConcurrentHashMap<Long, KuwoSong>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun search(query: String, page: Int = 0, limit: Int = 40): List<KuwoSong> = withContext(Dispatchers.IO) {
        val url = "http://search.kuwo.cn/r.s?client=kt&all=${encode(query)}&pn=$page&rn=$limit" +
            "&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1&ft=music" +
            "&cluster=0&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1"
        val body = get(url)
        val rows = runCatching { JSONObject(body).optJSONArray("abslist") }.getOrNull() ?: return@withContext emptyList()
        rows.songs()
    }

    suspend fun hotSearch(): List<String> = withContext(Dispatchers.IO) {
        // 这个接口的 Content-Type 标的是 gbk，但字节实际是 UTF-8，必须按字节自己解，否则全是乱码
        val body = runCatching {
            getBytesAs(
                "http://hotword.kuwo.cn/hotword.s?prod=kwplayer_ar_9.2.2.1&corp=kuwo&pcmp4=1&kwname=popup_mobile" +
                    "&ver=kwplayer_ar_9.2.2.1&vipver=1&mode=manifests&uid=794762570&mobi=1&issubtitle=1" +
                    "&show_copyright_off=1&newver=1",
                Charsets.UTF_8,
            )
        }.getOrDefault("")
        if (!body.startsWith("TEXT=")) return@withContext emptyList()
        body.removePrefix("TEXT=").lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "TEXT" }
            .take(24)
            .toList()
    }

    suspend fun searchPlaylists(query: String, page: Int = 0, limit: Int = 30): List<KuwoPlaylist> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val url = "http://search.kuwo.cn/r.s?client=kt&all=${encode(query)}&pn=$page&rn=$limit" +
                "&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1&ft=playlist" +
                "&cluster=0&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1"
            val rows = runCatching { JSONObject(get(url)).optJSONArray("abslist") }.getOrNull()
                ?: return@withContext emptyList()
            (0 until rows.length()).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                val id = row.optString("playlistid").ifBlank { row.optString("DC_TARGETID") }.toLongOrNull()
                    ?: return@mapNotNull null
                KuwoPlaylist(
                    id = id,
                    name = row.optString("name").ifBlank { "未命名歌单" },
                    coverUrl = row.optString("pic").takeIf { it.startsWith("http") }
                        ?: row.optString("hts_pic").takeIf { it.startsWith("http") },
                    songCount = row.optString("songnum").toIntOrNull() ?: 0,
                    playCount = row.optString("playcnt").toLongOrNull() ?: 0L,
                    creator = row.optString("nickname"),
                    tags = row.optString("tags"),
                    description = row.optString("intro").replace("\\n", " ").take(120),
                )
            }
        }

    /** 把导入歌单里的一条曲目映射成可播放的酷我曲目：能直连就直连，否则按歌名+歌手搜索兜底。 */
    suspend fun resolveEntry(entry: LxEntry): KuwoSong? = withContext(Dispatchers.IO) {
        if (entry.source.equals("kw", ignoreCase = true)) {
            val direct = entry.songmid.toLongOrNull()
            if (direct != null && direct > 0L) {
                val song = KuwoSong(
                    id = direct,
                    title = entry.name.ifBlank { "未知歌曲" },
                    artist = entry.singer.ifBlank { "未知艺术家" },
                    album = entry.albumName.ifBlank { "未知专辑" },
                    durationMs = LxPlaylistParser.intervalToMs(entry.interval),
                    coverUrl = entry.cover,
                    albumId = entry.albumId,
                )
                known[direct] = song
                return@withContext song
            }
        }
        val query = listOf(entry.name, entry.singer).filter(String::isNotBlank).joinToString(" ")
        if (query.isBlank()) return@withContext null
        val hits = runCatching { search(query, limit = 5) }.getOrDefault(emptyList())
        val leadSinger = entry.singer.split("/", "、", "&").firstOrNull()?.trim().orEmpty()
        hits.firstOrNull { hit ->
            hit.title.equals(entry.name, ignoreCase = true) &&
                (leadSinger.isBlank() || hit.artist.contains(leadSinger, ignoreCase = true))
        } ?: hits.firstOrNull()
    }

    suspend fun boards(): List<KuwoBoard> = BOARDS

    /** 洛雪原版的排行榜列表：qukudata 的榜单树，带封面与更新时间。 */
    suspend fun leaderboards(): List<KuwoBoard> = withContext(Dispatchers.IO) {
        val body = runCatching {
            get("http://qukudata.kuwo.cn/q.k?op=query&cont=tree&node=2&pn=0&rn=1000&fmt=json&level=2")
        }.getOrNull() ?: return@withContext BOARDS
        val rows = runCatching { JSONObject(body).optJSONArray("child") }.getOrNull()
            ?: return@withContext BOARDS
        val list = (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            // source 为 1 才是榜单；部分节点（如「由你音乐榜」）的 sourceid 是外链，要跳过
            if (row.optString("source") != "1") return@mapNotNull null
            val id = row.optString("sourceid").takeIf { it.toLongOrNull() != null } ?: return@mapNotNull null
            KuwoBoard(
                id = id,
                name = row.optString("name").ifBlank { row.optString("disname") },
                coverUrl = row.optString("pic").takeIf { it.startsWith("http") },
                info = row.optString("info"),
            )
        }
        list.ifEmpty { BOARDS }
    }

    /** 洛雪原版的推荐歌单：getRcmPlayList，order 为 hot / new。 */
    suspend fun recommendPlaylists(sort: String = "hot", page: Int = 1, limit: Int = 36): List<KuwoPlaylist> =
        withContext(Dispatchers.IO) {
            val body = runCatching {
                get(
                    "http://wapi.kuwo.cn/api/pc/classify/playlist/getRcmPlayList?loginUid=0&loginSid=0" +
                        "&appUid=76039576&pn=$page&rn=$limit&order=${encode(sort)}",
                )
            }.getOrNull() ?: return@withContext emptyList()
            val data = runCatching { JSONObject(body).optJSONObject("data") }.getOrNull()
                ?: return@withContext emptyList()
            val rows = data.optJSONArray("data") ?: return@withContext emptyList()
            (0 until rows.length()).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                val id = row.optString("id").toLongOrNull() ?: return@mapNotNull null
                KuwoPlaylist(
                    id = id,
                    name = row.optString("name").ifBlank { "推荐歌单" },
                    coverUrl = row.optString("img").takeIf { it.startsWith("http") },
                    songCount = row.optString("total").toIntOrNull() ?: 0,
                    playCount = row.optString("listencnt").toLongOrNull() ?: 0L,
                    creator = row.optString("uname"),
                    tags = "",
                    description = row.optString("desc").take(120),
                )
            }
        }

    suspend fun board(bandId: String, page: Int = 0): KuwoCollection = withContext(Dispatchers.IO) {
        val body = get("http://kbangserver.kuwo.cn/ksong.s?from=pc&fmt=json&pn=$page&rn=100&type=bang&data=content&id=$bandId&show_copyright_off=0&pcmp4=1&isbang=1")
        val root = JSONObject(body)
        val songs = root.optJSONArray("musiclist").songs()
        KuwoCollection(
            title = root.firstText("name", "leader").ifBlank { BOARDS.firstOrNull { it.id == bandId }?.name.orEmpty() },
            // v9_pic2 是 120px 缩略图，换成 500px 免得详情页封面发虚
            coverUrl = root.firstText("v9_pic2", "pic").takeIf { it.startsWith("http") }?.replace("/120/", "/500/"),
            songs = songs,
            description = root.firstText("info"),
        )
    }

    suspend fun playlist(id: Long, page: Int = 0): KuwoCollection = withContext(Dispatchers.IO) {
        val body = get(
            "http://nplserver.kuwo.cn/pl.svc?op=getlistinfo&pid=$id&pn=$page&rn=200&encode=utf8" +
                "&keyset=pl2012&identity=kuwo&pcmp4=1&vipver=MUSIC_9.0.5.0_W1&newver=1",
        )
        val root = JSONObject(body)
        val songs = root.optJSONArray("musiclist").songs()
        KuwoCollection(
            title = root.firstText("title", "name").ifBlank { "歌单 $id" },
            coverUrl = root.firstText("pic").takeIf { it.startsWith("http") },
            songs = songs,
            description = listOfNotNull(
                root.firstText("uname").takeIf { it.isNotBlank() }?.let { "by $it" },
                root.firstText("tag").takeIf { it.isNotBlank() },
                root.firstText("info").takeIf { it.isNotBlank() }?.take(80),
            ).joinToString(" · "),
        )
    }

    suspend fun playUrl(id: Long, quality: String = "320k"): String = withContext(Dispatchers.IO) { playUrlBlocking(id, quality) }

    fun playUrlBlocking(id: Long, quality: String = "320k"): String {
        val br = builtinQuality(quality)
        val url = "http://antiserver.kuwo.cn/anti.s?type=convert_url&br=${encode(br)}&format=mp3&response=url&rid=MUSIC_$id"
        val body = get(url).trim()
        if (!body.startsWith("http")) {
            error(
                when {
                    body.contains("refuse", ignoreCase = true) -> "这首歌需要音源会员或版权受限"
                    body.isBlank() -> "音源没有返回播放地址"
                    else -> "音源无法播放这首歌（$body）"
                },
            )
        }
        return body
    }

    suspend fun lyrics(id: Long): StoredLyrics? = withContext(Dispatchers.IO) {
        val raw = getBytes("http://mlyric.kuwo.cn/mobi.s?f=web&type=lyric&lrcx=1&rid=$id&encode=utf8")
            ?: return@withContext null
        val text = decodeLyric(raw) ?: return@withContext null
        val word = convertToYrc(text)
        val lines = stripWordTags(text)
        if (lines.isBlank()) return@withContext null
        StoredLyrics(
            lyrics = lines,
            wordLyrics = word,
            translationLyrics = "",
            romanLyrics = "",
            source = "kuwo",
            format = if (word.isNotBlank()) "yrc" else "lrc-line",
            confidence = 1.0,
        )
    }

    private fun decodeLyric(raw: ByteArray): String? {
        val head = String(raw, 0, minOf(raw.size, 16), Charsets.ISO_8859_1).lowercase()
        if (!head.startsWith("tp=content")) return null
        val marker = byteArrayOf(13, 10, 13, 10)
        val index = indexOf(raw, marker)
        if (index < 0) return null
        val payload = raw.copyOfRange(index + 4, raw.size)
        val inflated = runCatching { inflate(payload) }.getOrNull() ?: return null
        val base64 = String(inflated, Charsets.ISO_8859_1)
        val decoded = runCatching { android.util.Base64.decode(base64, android.util.Base64.DEFAULT) }.getOrNull() ?: return null
        val key = "yeelion".toByteArray(Charsets.US_ASCII)
        val out = ByteArray(decoded.size)
        for (i in decoded.indices) out[i] = (decoded[i].toInt() xor key[i % key.size].toInt()).toByte()
        return String(out, Charsets.UTF_8)
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray): Int {
        outer@ for (i in 0..(data.size - pattern.size)) {
            for (j in pattern.indices) if (data[i + j] != pattern[j]) continue@outer
            return i
        }
        return -1
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val buffer = ByteArray(64 * 1024)
        val out = java.io.ByteArrayOutputStream()
        while (!inflater.finished()) {
            val read = inflater.inflate(buffer)
            if (read == 0) {
                if (inflater.needsInput() || inflater.needsDictionary()) break
            } else {
                out.write(buffer, 0, read)
            }
        }
        inflater.end()
        return out.toByteArray()
    }

    private fun stripWordTags(text: String): String = text.lineSequence()
        .filter { it.isNotBlank() }
        .map { WORD_TAG.replace(it, "").trim() }
        .filter { it.isNotBlank() && !it.startsWith("[ver:") && !it.startsWith("[kuwo:") }
        .joinToString("\n")

    private fun convertToYrc(text: String): String {
        // [kuwo:113] 决定逐字时间戳的换算系数：高位 /10 为 offset，个位为 offset2
        var factorA = 1
        var factorB = 1
        text.lineSequence().forEach { line ->
            KUWO_TAG.matchEntire(line.trim())?.let { match ->
                val value = match.groupValues[1].toIntOrNull() ?: 0
                val a = value / 10
                val b = value % 10
                if (a > 0 && b > 0) {
                    factorA = a
                    factorB = b
                }
            }
        }
        val out = StringBuilder()
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val stamp = LINE_STAMP.find(trimmed) ?: return@forEach
            val lineStart = stampMs(stamp.groupValues[1], stamp.groupValues[2], stamp.groupValues[3])
            val body = trimmed.substring(stamp.range.last + 1)
            val words = WORD_TAG.findAll(body).toList()
            if (words.isEmpty()) return@forEach
            val builder = StringBuilder()
            var end = lineStart
            words.forEachIndexed { index, word ->
                val offset = word.groupValues[1].toLongOrNull() ?: 0L
                val span = word.groupValues[2].toLongOrNull() ?: 0L
                val start = kotlin.math.abs((offset + span) / (factorA * 2L))
                val stop = kotlin.math.abs((offset - span) / (factorB * 2L)) + start
                val textStart = word.range.last + 1
                val textEnd = words.getOrNull(index + 1)?.range?.first ?: body.length
                val content = body.substring(textStart, textEnd)
                if (content.isEmpty()) return@forEachIndexed
                builder.append('(').append(lineStart + start).append(',')
                    .append((stop - start).coerceAtLeast(1L)).append(')').append(content)
                end = maxOf(end, lineStart + stop)
            }
            if (builder.isEmpty()) return@forEach
            out.append('[').append(lineStart).append(',').append((end - lineStart).coerceAtLeast(1L)).append(']')
                .append(builder).append('\n')
        }
        return out.toString().trim()
    }

    private fun stampMs(minute: String, second: String, fraction: String): Long {
        val m = minute.toLongOrNull() ?: 0L
        val s = second.toLongOrNull() ?: 0L
        val f = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.take(3).toLong()
        }
        return m * 60_000 + s * 1_000 + f
    }

    fun song(id: Long): KuwoSong? = known[id]

    /**
     * 酷我三个接口的字段名不一致：搜索返回大写（SONGNAME/DURATION），榜单和歌单返回小写（name/song_duration），
     * 所以这里按优先级逐个取，避免某一类列表整体解析成空。
     */
    private fun JSONObject.firstText(vararg keys: String): String =
        keys.firstNotNullOfOrNull { key ->
            optString(key).takeIf { it.isNotBlank() && it != "null" && it != "0" }
        }.orEmpty()

    private fun JSONObject.firstNumber(vararg keys: String): Long =
        keys.firstNotNullOfOrNull { key -> optString(key).toLongOrNull()?.takeIf { it > 0L } } ?: 0L

    private fun JSONArray?.songs(): List<KuwoSong> {
        val array = this ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val row = array.optJSONObject(index) ?: return@mapNotNull null
            val id = row.firstText("MUSICRID", "DC_TARGETID", "rid", "id").removePrefix("MUSIC_").toLongOrNull()
                ?: return@mapNotNull null
            if (id <= 0L) return@mapNotNull null
            val title = row.firstText("SONGNAME", "NAME", "name", "FSONGNAME", "displaysongname")
            val artist = row.firstText("ARTIST", "FARTIST", "artist", "AARTIST", "aartist").replace("&", "、")
            val album = row.firstText("ALBUM", "FALBUM", "album", "displayalbumname")
            // 榜单里的 duration 是假的（6/10/13），真实时长在 song_duration；歌单接口只有 duration
            val seconds = row.firstNumber("song_duration", "DURATION", "duration")
            val rawCover = row.firstText("web_albumpic_short", "albumpic", "musicPic", "img")
            val song = KuwoSong(
                id = id,
                title = title,
                artist = artist,
                album = album,
                durationMs = seconds * 1000L,
                coverUrl = if (rawCover.startsWith("http")) rawCover else coverOf(rawCover),
                albumId = row.firstNumber("ALBUMID", "albumid"),
            )
            known[song.id] = song
            song
        }
    }

    fun coverOf(short: String): String? {
        if (short.isBlank()) return null
        val tail = short.substringAfter('/', short)
        return "https://img1.kuwo.cn/star/albumcover/500/$tail"
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", "http://www.kuwo.cn/")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("音源请求失败（HTTP ${response.code}）")
            return response.body?.string().orEmpty()
        }
    }

    private fun getBytesAs(url: String, charset: java.nio.charset.Charset): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", "http://www.kuwo.cn/")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("音源请求失败（HTTP ${response.code}）")
            val bytes = response.body?.bytes() ?: return ""
            return String(bytes, charset)
        }
    }

    private fun getBytes(url: String): ByteArray? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", "http://www.kuwo.cn/")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.bytes()
            }
        }.getOrNull()
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    companion object {
        private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
        private val LINE_STAMP = Regex("^\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
        private val WORD_TAG = Regex("<(-?\\d+),(-?\\d+)(?:,-?\\d+)?>")
        private val KUWO_TAG = Regex("^\\[kuwo:(\\d+)]$")

        val BOARDS = listOf(
            KuwoBoard("93", "飙升榜"),
            KuwoBoard("17", "新歌榜"),
            KuwoBoard("16", "热歌榜"),
            KuwoBoard("158", "抖音热歌榜"),
            KuwoBoard("242", "电音榜"),
            KuwoBoard("187", "流行趋势榜"),
            KuwoBoard("186", "ACG神曲榜"),
            KuwoBoard("185", "最强翻唱榜"),
            KuwoBoard("278", "古风音乐榜"),
            KuwoBoard("264", "Vlog音乐榜"),
            KuwoBoard("284", "热评榜"),
            KuwoBoard("279", "冬日暖心榜"),
        )

        val QUALITIES = listOf(
            "128k" to "标准（128k）",
            "192k" to "较高（192k）",
            "320k" to "极高（320k）",
            "flac" to "无损（flac）",
            "flac24bit" to "Hi-Res（flac24bit）",
        )

        /** 内置直链只提供 mp3，无损必须走自定义源；这里把高音质映射回可用的 mp3 档位。 */
        fun builtinQuality(quality: String): String = when (quality) {
            "flac", "flac24bit" -> "320k"
            else -> quality
        }

        /** 自定义源声明音质后的降级链：请求档 → 更低档，避免脚本只支持部分档位时报错。 */
        fun resolveQuality(requested: String, available: List<String>): String? {
            val order = listOf("flac24bit", "flac", "320k", "192k", "128k")
            val start = order.indexOf(requested).let { if (it < 0) order.lastIndex else it }
            return order.drop(start).firstOrNull { available.contains(it) }
                ?: order.firstOrNull { available.contains(it) }
        }
    }
}
