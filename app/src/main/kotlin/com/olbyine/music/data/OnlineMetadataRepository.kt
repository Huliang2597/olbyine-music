package com.olbyine.music.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class MetadataSource(val label: String) {
    NETEASE("网易云音乐"),
    QQ("QQ 音乐"),
    APPLE("Apple Music"),
    LOCAL("本地编辑"),
}

data class MetadataCandidate(
    val source: MetadataSource,
    val title: String,
    val artist: String,
    val album: String,
    val coverUrl: String?,
    val artistImageUrl: String? = null,
    val releaseDate: String? = null,
    val genre: String? = null,
    val artistId: String? = null,
    val albumId: String? = null,
    val durationMs: Long? = null,
    val appleSong: AmSong? = null,
)

data class RemoteSong(
    val id: String,
    val title: String,
    val artists: List<String>,
    val album: String,
    val durationMs: Long,
    val coverUrl: String?,
)

data class RemotePlaylist(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val creator: String,
    val songs: List<RemoteSong>,
)

data class ArtistInfo(
    val name: String,
    val imagePath: String? = null,
    val bio: String = "",
    val source: MetadataSource? = null,
    val edited: Boolean = false,
)

data class AlbumInfo(
    val title: String,
    val artist: String,
    val coverPath: String? = null,
    val description: String = "",
    val releaseDate: String = "",
    val genre: String = "",
    val source: MetadataSource? = null,
    val edited: Boolean = false,
)

class OnlineMetadataRepository(private val context: Context, private val apple: AppleMusicClient) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val root = File(context.filesDir, "meta").apply { mkdirs() }
    private val imageDir = File(root, "img").apply { mkdirs() }
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    suspend fun search(title: String, artist: String, album: String): List<MetadataCandidate> = coroutineScope {
        val query = listOf(title, artist).filter(String::isNotBlank).joinToString(" ")
        if (query.isBlank()) return@coroutineScope emptyList()
        val jobs = listOf(
            async(Dispatchers.IO) { safe { neteaseSearch(query) } },
            async(Dispatchers.IO) { safe { qqSearch(query) } },
            async(Dispatchers.IO) { safe { appleSearch(query) } },
        )
        val all = jobs.flatMap { it.await() }
        all.sortedByDescending { score(it, title, artist, album) }
    }

    private fun score(candidate: MetadataCandidate, title: String, artist: String, album: String): Double {
        var score = 0.0
        if (normalize(candidate.title) == normalize(title)) score += 3.0
        else if (normalize(candidate.title).contains(normalize(title)) && title.isNotBlank()) score += 1.5
        val artistKey = normalize(artist)
        if (artistKey.isNotBlank() && normalize(candidate.artist).let { it.contains(artistKey) || artistKey.contains(it) }) score += 2.0
        if (album.isNotBlank() && normalize(candidate.album) == normalize(album)) score += 1.0
        if (candidate.coverUrl != null) score += .2
        if (candidate.source == MetadataSource.NETEASE) score += .15
        return score
    }

    private fun neteaseSearch(query: String): List<MetadataCandidate> {
        val url = "$NETEASE/api/search/get/web".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .addQueryParameter("type", "1")
            .addQueryParameter("limit", "6")
            .build().toString()
        val songs = json(getText(url, NETEASE_HEADERS)).optJSONObject("result")?.optJSONArray("songs") ?: return emptyList()
        return songs.objects().map { song ->
            val album = song.optJSONObject("album") ?: song.optJSONObject("al")
            val artists = (song.optJSONArray("artists") ?: song.optJSONArray("ar"))?.objects().orEmpty()
            MetadataCandidate(
                source = MetadataSource.NETEASE,
                title = song.optString("name"),
                artist = artists.joinToString(" / ") { it.optString("name") },
                album = album?.optString("name").orEmpty(),
                coverUrl = album?.optString("picUrl")?.takeIf(String::isNotBlank)?.https(),
                artistId = artists.firstOrNull()?.optLong("id")?.takeIf { it > 0 }?.toString(),
                albumId = album?.optLong("id")?.takeIf { it > 0 }?.toString(),
                durationMs = song.optLong("duration").takeIf { it > 0 } ?: song.optLong("dt").takeIf { it > 0 },
                releaseDate = album?.optLong("publishTime")?.takeIf { it > 0 }?.let(::formatDate),
            )
        }
    }

    private fun qqSearch(query: String): List<MetadataCandidate> {
        val url = "$QQ/soso/fcgi-bin/client_search_cp".toHttpUrl().newBuilder()
            .addQueryParameter("p", "1")
            .addQueryParameter("n", "6")
            .addQueryParameter("w", query)
            .addQueryParameter("format", "json")
            .build().toString()
        val list = json(getText(url, QQ_HEADERS)).optJSONObject("data")?.optJSONObject("song")?.optJSONArray("list")
            ?: return emptyList()
        return list.objects().map { song ->
            val albumMid = song.optString("albummid").ifBlank { song.optJSONObject("album")?.optString("mid").orEmpty() }
            val singers = song.optJSONArray("singer")?.objects().orEmpty()
            val singerMid = singers.firstOrNull()?.optString("mid").orEmpty()
            MetadataCandidate(
                source = MetadataSource.QQ,
                title = song.optString("songname").ifBlank { song.optString("name") },
                artist = singers.joinToString(" / ") { it.optString("name") },
                album = song.optString("albumname").ifBlank { song.optJSONObject("album")?.optString("name").orEmpty() },
                coverUrl = albumMid.takeIf(::isMid)?.let { "https://y.gtimg.cn/music/photo_new/T002R800x800M000$it.jpg" },
                artistImageUrl = singerMid.takeIf(::isMid)?.let { "https://y.gtimg.cn/music/photo_new/T001R800x800M000$it.jpg" },
                artistId = singerMid.takeIf(::isMid),
                albumId = albumMid.takeIf(::isMid),
                durationMs = song.optLong("interval").takeIf { it > 0 }?.times(1000),
            )
        }
    }

    private suspend fun appleSearch(query: String): List<MetadataCandidate> {
        val songs = runCatching { apple.searchSongs(query, 6) }.getOrDefault(emptyList())
        if (songs.isNotEmpty()) {
            return songs.map { song ->
                MetadataCandidate(
                    source = MetadataSource.APPLE,
                    title = song.name,
                    artist = song.artistName,
                    album = song.albumName,
                    coverUrl = AppleMusicClient.artwork(song.artworkTemplate, 1200),
                    releaseDate = song.releaseDate?.take(10),
                    genre = song.genre,
                    durationMs = song.durationMs.takeIf { it > 0 },
                    appleSong = song,
                )
            }
        }
        return itunesSearch(query)
    }

    private fun itunesSearch(query: String): List<MetadataCandidate> {
        for (country in APPLE_STOREFRONTS) {
            val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
                .addQueryParameter("term", query)
                .addQueryParameter("entity", "song")
                .addQueryParameter("limit", "5")
                .addQueryParameter("country", country)
                .build().toString()
            val results = runCatching { json(getText(url, emptyMap())).optJSONArray("results") }.getOrNull()
            val rows = results?.objects().orEmpty()
            if (rows.isEmpty()) continue
            return rows.map { row ->
                MetadataCandidate(
                    source = MetadataSource.APPLE,
                    title = row.optString("trackName"),
                    artist = row.optString("artistName"),
                    album = row.optString("collectionName"),
                    coverUrl = row.optString("artworkUrl100").takeIf(String::isNotBlank)
                        ?.replace(Regex("/\\d+x\\d+bb\\.(jpg|png)$"), "/1200x1200bb.jpg"),
                    releaseDate = row.optString("releaseDate").take(10).takeIf(String::isNotBlank),
                    genre = row.optString("primaryGenreName").takeIf(String::isNotBlank),
                    artistId = row.optString("artistViewUrl").takeIf(String::isNotBlank),
                    albumId = row.optLong("collectionId").takeIf { it > 0 }?.toString(),
                    durationMs = row.optLong("trackTimeMillis").takeIf { it > 0 },
                )
            }
        }
        return emptyList()
    }

    suspend fun downloadCover(candidate: MetadataCandidate): ByteArray = withContext(Dispatchers.IO) {
        val url = candidate.coverUrl ?: when (candidate.source) {
            MetadataSource.NETEASE -> candidate.albumId?.let(::neteaseAlbum)?.optString("picUrl")?.https()
            else -> null
        } ?: error("该来源没有封面")
        getBytes(sized(url, candidate.source))
    }

    private fun neteaseArtistImageAndBio(artistId: String): Pair<String?, String> {
        val artist = safeObj { json(getText("$NETEASE/api/v1/artist/$artistId", NETEASE_HEADERS)).optJSONObject("artist") }
        val imageUrl = artist?.optString("picUrl")?.takeIf(String::isNotBlank)?.https()
        var bio = artist?.optString("briefDesc").orEmpty().takeIf { it != "null" }.orEmpty().trim()
        if (bio.isBlank()) {
            val intro = safeObj { json(getText("$NETEASE/api/artist/introduction?id=$artistId", NETEASE_HEADERS)) }
            bio = intro?.optString("briefDesc").orEmpty().takeIf { it != "null" }.orEmpty().trim()
            if (bio.isBlank()) {
                bio = intro?.optJSONArray("introduction")?.let { rows ->
                    (0 until rows.length()).mapNotNull { index ->
                        val row = rows.optJSONObject(index) ?: return@mapNotNull null
                        val title = row.optString("ti").trim()
                        val body = row.optString("txt").trim()
                        when {
                            body.isBlank() -> null
                            title.isBlank() -> body
                            else -> title + "\n" + body
                        }
                    }.joinToString("\n\n").trim()
                }.orEmpty()
            }
        }
        return imageUrl to bio
    }

    suspend fun ensureArtist(name: String): ArtistInfo? = withContext(Dispatchers.IO) {
        val key = name.trim()
        if (key.isBlank()) return@withContext null
        val existing = artist(key)
        if (existing != null && existing.bio.isNotBlank()) return@withContext existing
        var candidates = safe { search(key, "", "") }.orEmpty()
        if (candidates.none { it.artist.contains(key, ignoreCase = true) }) {
            candidates = safe { search(key, key, "") }.orEmpty()
        }
        val nameMatches = candidates.filter { it.artist.contains(key, ignoreCase = true) }
        val best = nameMatches.firstOrNull { it.source == MetadataSource.NETEASE }
            ?: nameMatches.firstOrNull()
            ?: candidates.firstOrNull { it.source == MetadataSource.NETEASE }
            ?: candidates.firstOrNull()
        if (best != null) {
            val (imageUrl, bio) = when (best.source) {
                MetadataSource.NETEASE -> best.artistId?.let { id -> neteaseArtistImageAndBio(id) } ?: (null to "")
                MetadataSource.QQ -> best.artistImageUrl to ""
                MetadataSource.APPLE -> appleArtistDetails(best)
                MetadataSource.LOCAL -> null to ""
            }
            var resolvedBio = bio
            if (resolvedBio.isBlank() && best.source != MetadataSource.NETEASE) {
                val neteaseId = candidates.firstOrNull { it.source == MetadataSource.NETEASE && it.artistId != null }?.artistId
                    ?: safe { search(key, key, "") }.orEmpty()
                        .firstOrNull { it.source == MetadataSource.NETEASE && it.artistId != null }?.artistId
                if (neteaseId != null) resolvedBio = neteaseArtistImageAndBio(neteaseId).second
            }
            val imagePath = imageUrl?.let { url -> safeObj { saveImage("artist", key, getBytes(sized(url, best.source))) } }
            if (imagePath != null || resolvedBio.isNotBlank()) {
                saveArtist(
                    ArtistInfo(
                        name = key,
                        imagePath = imagePath ?: existing?.imagePath,
                        bio = resolvedBio.ifBlank { existing?.bio.orEmpty() },
                        source = best.source,
                    ),
                )
            }
        }
        artist(key)
    }

    suspend fun applyDetails(candidate: MetadataCandidate, localArtist: String, localAlbum: String) = withContext(Dispatchers.IO) {
        val artistKey = localArtist.ifBlank { candidate.artist }
        val albumKey = localAlbum.ifBlank { candidate.album }
        val existingArtist = artist(artistKey)
        if (existingArtist?.edited != true && existingArtist?.bio.isNullOrBlank()) {
            val (imageUrl, bio) = when (candidate.source) {
                MetadataSource.NETEASE -> candidate.artistId?.let { id -> neteaseArtistImageAndBio(id) } ?: (null to "")
                MetadataSource.QQ -> candidate.artistImageUrl to ""
                MetadataSource.APPLE -> appleArtistDetails(candidate)
                MetadataSource.LOCAL -> null to ""
            }
            val imagePath = imageUrl?.let { url -> safeObj { saveImage("artist", artistKey, getBytes(sized(url, candidate.source))) } }
            if (imagePath != null || bio.isNotBlank()) {
                saveArtist(
                    ArtistInfo(
                        name = artistKey,
                        imagePath = imagePath ?: existingArtist?.imagePath,
                        bio = bio.ifBlank { existingArtist?.bio.orEmpty() },
                        source = candidate.source,
                    ),
                )
            }
        }
        val existingAlbum = album(albumKey, artistKey)
        if (existingAlbum?.edited != true) {
            val description = when (candidate.source) {
                MetadataSource.NETEASE -> candidate.albumId?.let { id -> safeObj { neteaseAlbum(id) }?.optString("description") }.orEmpty()
                MetadataSource.QQ -> candidate.albumId?.let { mid -> safeObj { qqAlbumDescription(mid) } }.orEmpty()
                MetadataSource.APPLE -> appleAlbum(candidate)?.notes.orEmpty()
                else -> ""
            }
            saveAlbum(
                AlbumInfo(
                    title = albumKey,
                    artist = artistKey,
                    coverPath = existingAlbum?.coverPath,
                    description = description.trim().ifBlank { existingAlbum?.description.orEmpty() },
                    releaseDate = candidate.releaseDate ?: existingAlbum?.releaseDate.orEmpty(),
                    genre = candidate.genre ?: existingAlbum?.genre.orEmpty(),
                    source = candidate.source,
                ),
            )
        }
    }

    private val appleRelations = HashMap<String, Pair<String?, String?>>()

    private suspend fun appleRelationsOf(candidate: MetadataCandidate): Pair<String?, String?> {
        val song = candidate.appleSong ?: return null to null
        return appleRelations[song.id] ?: safeObj { apple.songRelations(song) }.orNullPair().also { appleRelations[song.id] = it }
    }

    private fun Pair<String?, String?>?.orNullPair(): Pair<String?, String?> = this ?: (null to null)

    private suspend fun appleArtistDetails(candidate: MetadataCandidate): Pair<String?, String> {
        val song = candidate.appleSong
        if (song == null) {
            return candidate.artistId?.let { page -> safeObj { appleArtistImage(page) } } to ""
        }
        val artistId = appleRelationsOf(candidate).second ?: return null to ""
        val artist = safeObj { apple.artist(artistId, song.storefront) } ?: return null to ""
        return AppleMusicClient.artwork(artist.artworkTemplate, 800, "cc") to artist.bio
    }

    private suspend fun appleAlbum(candidate: MetadataCandidate): AmAlbum? {
        val song = candidate.appleSong ?: return null
        val albumId = appleRelationsOf(candidate).first ?: return null
        return safeObj { apple.album(albumId, song.storefront) }
    }

    private fun neteaseAlbum(id: String): JSONObject? =
        json(getText("$NETEASE/api/v1/album/$id", NETEASE_HEADERS)).optJSONObject("album")

    private fun qqAlbumDescription(mid: String): String {
        val url = "$QQ/v8/fcg-bin/fcg_v8_album_info_cp.fcg".toHttpUrl().newBuilder()
            .addQueryParameter("albummid", mid)
            .addQueryParameter("format", "json")
            .build().toString()
        return json(getText(url, QQ_HEADERS)).optJSONObject("data")?.optString("desc").orEmpty()
    }

    private fun appleArtistImage(pageUrl: String): String? {
        val html = getText(pageUrl, mapOf("User-Agent" to BROWSER_UA))
        val image = Regex("property=\"og:image\"\\s+content=\"([^\"]+)\"").find(html)?.groupValues?.get(1)
            ?: Regex("og:image\" content=\"([^\"]+)\"").find(html)?.groupValues?.get(1)
            ?: return null
        return image.replace(Regex("/\\d+x\\d+[a-z]*\\.(png|jpg)$"), "/800x800cc.jpg")
    }

    private fun sized(url: String, source: MetadataSource): String = when (source) {
        MetadataSource.NETEASE -> if (url.contains("?")) url else "$url?param=1000y1000"
        else -> url
    }

    suspend fun neteasePlaylist(input: String): RemotePlaylist = withContext(Dispatchers.IO) {
        val id = playlistId(input) ?: resolveShortLink(input)?.let(::playlistId)
            ?: error("没有识别到歌单链接或 ID")
        val url = "$NETEASE/api/v6/playlist/detail".toHttpUrl().newBuilder()
            .addQueryParameter("id", id)
            .addQueryParameter("n", "1000")
            .build().toString()
        val root = json(getText(url, NETEASE_HEADERS))
        require(root.optInt("code") == 200) { "歌单不可用（可能是私密歌单）" }
        val playlist = root.optJSONObject("playlist") ?: error("歌单不可用")
        val songs = playlist.optJSONArray("tracks")?.objects().orEmpty().map(::neteaseSong).toMutableList()
        val allIds = playlist.optJSONArray("trackIds")?.objects().orEmpty().map { it.optLong("id") }
        val missing = allIds.drop(songs.size).take(MAX_PLAYLIST_SONGS - songs.size)
        missing.chunked(200).forEach { chunk ->
            val param = JSONArray().apply { chunk.forEach { put(JSONObject().put("id", it)) } }.toString()
            val detail = "$NETEASE/api/v3/song/detail".toHttpUrl().newBuilder()
                .addQueryParameter("c", param)
                .build().toString()
            json(getText(detail, NETEASE_HEADERS)).optJSONArray("songs")?.objects().orEmpty().mapTo(songs, ::neteaseSong)
        }
        RemotePlaylist(
            id = id,
            name = playlist.optString("name"),
            coverUrl = playlist.optString("coverImgUrl").takeIf(String::isNotBlank)?.https(),
            creator = playlist.optJSONObject("creator")?.optString("nickname").orEmpty(),
            songs = songs,
        )
    }

    private fun neteaseSong(row: JSONObject): RemoteSong {
        val album = row.optJSONObject("al") ?: row.optJSONObject("album")
        val artists = (row.optJSONArray("ar") ?: row.optJSONArray("artists"))?.objects().orEmpty()
        return RemoteSong(
            id = row.optLong("id").toString(),
            title = row.optString("name"),
            artists = artists.map { it.optString("name") }.filter(String::isNotBlank),
            album = album?.optString("name").orEmpty(),
            durationMs = row.optLong("dt").takeIf { it > 0 } ?: row.optLong("duration"),
            coverUrl = album?.optString("picUrl")?.takeIf(String::isNotBlank)?.https(),
        )
    }

    private fun playlistId(input: String): String? {
        val text = input.trim()
        if (text.matches(Regex("^\\d{3,20}$"))) return text
        return Regex("playlist[^\\d]{0,12}?(?:id=)?(\\d{3,20})").find(text)?.groupValues?.get(1)
            ?: Regex("[?&]id=(\\d{3,20})").find(text)?.groupValues?.get(1)
    }

    private fun resolveShortLink(input: String): String? {
        val link = Regex("https?://(?:163cn\\.tv|y\\.music\\.163\\.com)/\\S+").find(input)?.value ?: return null
        val noRedirect = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
        val request = Request.Builder().url(link).header("User-Agent", BROWSER_UA).build()
        return noRedirect.newCall(request).execute().use { response -> response.header("Location") ?: response.request.url.toString() }
    }

    fun artist(name: String): ArtistInfo? = readJson(fileFor("artist", name))?.let { row ->
        ArtistInfo(
            name = row.optString("name", name),
            imagePath = row.optString("image").takeIf { it.isNotBlank() && File(it).exists() },
            bio = row.optString("bio"),
            source = row.optString("source").toSource(),
            edited = row.optBoolean("edited"),
        )
    }

    fun album(title: String, artist: String): AlbumInfo? = readJson(fileFor("album", "$artist\u0000$title"))?.let { row ->
        AlbumInfo(
            title = row.optString("title", title),
            artist = row.optString("artist", artist),
            coverPath = row.optString("cover").takeIf { it.isNotBlank() && File(it).exists() },
            description = row.optString("description"),
            releaseDate = row.optString("releaseDate"),
            genre = row.optString("genre"),
            source = row.optString("source").toSource(),
            edited = row.optBoolean("edited"),
        )
    }

    fun saveArtist(info: ArtistInfo) {
        writeJson(
            fileFor("artist", info.name),
            JSONObject()
                .put("name", info.name)
                .put("image", info.imagePath.orEmpty())
                .put("bio", info.bio)
                .put("source", info.source?.name.orEmpty())
                .put("edited", info.edited),
        )
    }

    fun saveAlbum(info: AlbumInfo) {
        writeJson(
            fileFor("album", "${info.artist}\u0000${info.title}"),
            JSONObject()
                .put("title", info.title)
                .put("artist", info.artist)
                .put("cover", info.coverPath.orEmpty())
                .put("description", info.description)
                .put("releaseDate", info.releaseDate)
                .put("genre", info.genre)
                .put("source", info.source?.name.orEmpty())
                .put("edited", info.edited),
        )
    }

    fun saveImage(kind: String, key: String, bytes: ByteArray): String {
        require(bytes.size in 1..MAX_IMAGE_BYTES) { "图片无效或过大" }
        imageDir.listFiles { file -> file.name.startsWith("$kind-${hash(key)}-") }?.forEach(File::delete)
        val file = File(imageDir, "$kind-${hash(key)}-${System.currentTimeMillis()}.jpg")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    private fun fileFor(kind: String, key: String) = File(root, "$kind-${hash(key)}.json")

    private fun readJson(file: File): JSONObject? =
        if (file.exists()) runCatching { JSONObject(file.readText()) }.getOrNull() else null

    private fun writeJson(file: File, value: JSONObject) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(value.toString())
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
        _revision.value++
    }

    private fun getText(url: String, headers: Map<String, String>): String =
        execute(url, headers) { it.body?.string().orEmpty() }

    private fun getBytes(url: String): ByteArray = execute(url, mapOf("User-Agent" to BROWSER_UA)) { response ->
        val body = response.body ?: error("空响应")
        require(body.contentLength() <= MAX_IMAGE_BYTES) { "图片过大" }
        body.bytes().also { require(it.size in 1..MAX_IMAGE_BYTES) { "图片无效或过大" } }
    }

    private fun <T> execute(url: String, headers: Map<String, String>, read: (okhttp3.Response) -> T): T {
        val request = Request.Builder().url(url).apply {
            header("User-Agent", headers["User-Agent"] ?: BROWSER_UA)
            headers.forEach { (name, value) -> if (name != "User-Agent") header(name, value) }
        }.build()
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "HTTP ${response.code}" }
            return read(response)
        }
    }

    private suspend fun <T> safe(block: suspend () -> List<T>): List<T> = try {
        block()
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        emptyList()
    }

    private inline fun <T> safeObj(block: () -> T?): T? = try {
        block()
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        null
    }

    private fun json(text: String): JSONObject = JSONObject(text.trim().removePrefix("callback(").removeSuffix(")"))

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

    private fun String.https() = replace(Regex("^http://", RegexOption.IGNORE_CASE), "https://")

    private fun String.toSource(): MetadataSource? = runCatching { MetadataSource.valueOf(this) }.getOrNull()

    private fun isMid(value: String) = value.matches(Regex("^[a-zA-Z0-9]{4,64}$"))

    private fun formatDate(millis: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(java.util.Date(millis))

    companion object {
        private const val NETEASE = "https://music.163.com"
        private const val QQ = "https://c.y.qq.com"
        private const val MAX_IMAGE_BYTES = 12 * 1024 * 1024
        private const val MAX_PLAYLIST_SONGS = 3000
        private const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
        private val NETEASE_HEADERS = mapOf("Referer" to "https://music.163.com/")
        private val QQ_HEADERS = mapOf("Referer" to "https://y.qq.com/")
        private val APPLE_STOREFRONTS = listOf("cn", "tw", "hk", "us")

        fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(Regex("[\\s_\\-–—.·/&,，、()（）\\[\\]【】]+"), "")

        fun hash(value: String): String = java.security.MessageDigest.getInstance("SHA-256")
            .digest(normalize(value).toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)
    }
}
