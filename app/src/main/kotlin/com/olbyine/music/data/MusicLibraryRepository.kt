package com.olbyine.music.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID

class MusicLibraryRepository(private val context: Context) {
    private val helper = MusicDatabase(context)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    private val _lyricsRevision = MutableStateFlow(0L)
    val lyricsRevision: StateFlow<Long> = _lyricsRevision.asStateFlow()
    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _importProgress.asStateFlow()
    private val _composerScanDone = MutableStateFlow(false)
    val composerScanDone: StateFlow<Boolean> = _composerScanDone.asStateFlow()

    init {
        scope.launch {
            mutex.withLock {
                migrateRecoveredAudioIfNeeded()
                refreshLocked()
                _loaded.value = true
            }
            runCatching { rescanTags(onlyMissing = true) }
            runCatching { repairMisreadTags() }
            runCatching { backfillComposers() }
            _composerScanDone.value = true
        }
    }

    suspend fun importAudio(uris: List<Uri>, options: ImportOptions = ImportOptions()): ImportReport = withContext(Dispatchers.IO) {
        val candidates = uris.distinct().map { uri ->
            ImportCandidate(uri, displayName(uri), querySize(uri), context.contentResolver.getType(uri), null, null, null, persist = true)
        }
        runImport(candidates, options)
    }

    var remoteHeaders: (String) -> Map<String, String> = { emptyMap() }

    suspend fun importWebDav(
        scan: suspend (onFound: (Int) -> Unit) -> List<WebDavAudio>,
        options: ImportOptions = ImportOptions(),
    ): ImportReport = withContext(Dispatchers.IO) {
        _importProgress.value = ImportProgress(0, 0, "正在扫描 WebDAV 文件夹…")
        val items = try {
            scan { found -> _importProgress.value = ImportProgress(0, found, "正在扫描 WebDAV 文件夹…") }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            _importProgress.value = null
            return@withContext ImportReport(0, 0, listOf(error.message ?: "无法访问 WebDAV 服务器"))
        }
        if (items.isEmpty()) {
            _importProgress.value = null
            return@withContext ImportReport(0, 0, listOf("这个文件夹里没有可导入的音乐"))
        }
        val candidates = items.map { item ->
            ImportCandidate(
                uri = Uri.parse(item.entry.url),
                name = item.entry.name,
                size = item.entry.size,
                mime = item.entry.contentType,
                folderId = item.folderId,
                folderPath = item.folderPath,
                lyricUri = item.lyricUrl?.let(Uri::parse),
                persist = false,
            )
        }
        runImport(candidates, options)
    }

    suspend fun importFolder(treeUri: Uri, options: ImportOptions = ImportOptions()): ImportReport = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        _importProgress.value = ImportProgress(0, 0, "正在扫描文件夹…")
        val errors = mutableListOf<String>()
        val candidates = runCatching { listTree(treeUri, errors) }.getOrElse { error ->
            _importProgress.value = null
            return@withContext ImportReport(0, 0, listOf(error.message ?: "无法访问所选文件夹"))
        }
        runImport(candidates, options, errors)
    }

    fun grantedFolders(): List<ScanFolder> = context.contentResolver.persistedUriPermissions
        .filter { it.isReadPermission && runCatching { DocumentsContract.isTreeUri(it.uri) }.getOrDefault(false) }
        .sortedByDescending { it.persistedTime }
        .map { ScanFolder(it.uri, folderLabel(it.uri)) }
        .distinctBy { it.uri.toString() }

    fun grantFolder(uri: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private fun folderLabel(uri: Uri): String {
        val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        val (volume, path) = id?.split(':', limit = 2)?.let { it.getOrNull(0) to it.getOrNull(1) } ?: (null to null)
        if (volume != null && path != null) return (if (volume == "primary") "内部存储" else volume) + if (path.isBlank()) "" else "/$path"
        return DocumentFile.fromTreeUri(context, uri)?.name?.takeIf(String::isNotBlank) ?: uri.lastPathSegment.orEmpty()
    }

    suspend fun subfolders(treeUri: Uri, documentId: String?): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val parent = documentId ?: DocumentsContract.getTreeDocumentId(treeUri)
        val result = mutableListOf<Pair<String, String>>()
        runCatching {
            context.contentResolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parent),
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
                null, null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1).orEmpty()
                    if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR && !name.startsWith(".")) {
                        result += (cursor.getString(0) ?: continue) to name
                    }
                }
            }
        }
        result.sortedBy { it.second.lowercase(Locale.ROOT) }
    }

    suspend fun importFolders(targets: List<ScanTarget>, options: ImportOptions = ImportOptions()): ImportReport = withContext(Dispatchers.IO) {
        targets.forEach { grantFolder(it.treeUri) }
        _importProgress.value = ImportProgress(0, 0, "正在扫描 ${targets.size} 个文件夹…")
        val errors = mutableListOf<String>()
        val candidates = targets.flatMap { target ->
            runCatching { listTree(target.treeUri, errors, target.documentId, target.name) }.getOrElse { error ->
                errors += (target.name ?: "文件夹") + "：" + (error.message ?: "无法访问")
                emptyList()
            }
        }
        if (candidates.isEmpty() && errors.isNotEmpty()) {
            _importProgress.value = null
            return@withContext ImportReport(0, 0, errors)
        }
        runImport(candidates, options, errors)
    }

    private fun listTree(treeUri: Uri, errors: MutableList<String>, startId: String? = null, startName: String? = null): List<ImportCandidate> {
        val resolver = context.contentResolver
        val rootId = startId ?: DocumentsContract.getTreeDocumentId(treeUri)
        val rootName = startName?.takeIf(String::isNotBlank) ?: DocumentFile.fromTreeUri(context, treeUri)?.name?.takeIf(String::isNotBlank) ?: "文件夹"
        val queue = ArrayDeque<Pair<String, String>>().apply { add(rootId to rootName) }
        val result = mutableListOf<ImportCandidate>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        while (queue.isNotEmpty()) {
            val (documentId, path) = queue.removeFirst()
            val children = mutableListOf<TreeChild>()
            val listed = runCatching {
                resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId), projection, null, null, null)
                    ?.use { cursor ->
                        while (cursor.moveToNext()) {
                            children += TreeChild(
                                id = cursor.getString(0) ?: continue,
                                name = cursor.getString(1).orEmpty(),
                                mime = cursor.getString(2).orEmpty(),
                                size = if (cursor.isNull(3)) 0L else cursor.getLong(3),
                            )
                        }
                    }
            }
            if (listed.isFailure) {
                errors += "$path：${listed.exceptionOrNull()?.message ?: "无法读取文件夹"}"
                continue
            }
            if (children.any { it.name.equals(".nomedia", ignoreCase = true) }) continue
            val folderId = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId).toString()
            val lyrics = children.asSequence()
                .filter { it.mime != DocumentsContract.Document.MIME_TYPE_DIR && isSupportedLyrics(it.name) }
                .sortedBy { lyricPriority(it.name) }
                .groupBy { baseName(it.name).lowercase(Locale.ROOT) }
            for (child in children) {
                if (child.mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (!child.name.startsWith(".")) queue.add(child.id to "$path/${child.name}")
                } else if (isSupportedAudio(child.name, child.mime)) {
                    result += ImportCandidate(
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, child.id),
                        name = child.name,
                        size = child.size,
                        mime = child.mime.takeIf(String::isNotBlank),
                        folderId = folderId,
                        folderPath = path,
                        lyricUri = lyrics[baseName(child.name).lowercase(Locale.ROOT)]?.firstOrNull()
                            ?.let { DocumentsContract.buildDocumentUriUsingTree(treeUri, it.id) },
                        persist = false,
                    )
                }
            }
            _importProgress.value = ImportProgress(0, result.size, "正在扫描文件夹…")
        }
        return result
    }

    private suspend fun runImport(
        candidates: List<ImportCandidate>,
        options: ImportOptions,
        errors: MutableList<String> = mutableListOf(),
    ): ImportReport {
        var skipped = 0
        var filtered = 0
        val importedIds = mutableListOf<String>()
        try {
            val existing = mutex.withLock { queryAll() }
            val known = DuplicateIndex(existing)
            val fresh = mutableListOf<ImportCandidate>()
            for (candidate in candidates.distinctBy { it.uri.toString() }) {
                when {
                    known.hasUri(candidate.uri.toString()) -> {
                        candidate.folderId?.let { folderId ->
                            mutex.withLock {
                                helper.writableDatabase.update(
                                    TABLE_TRACKS,
                                    ContentValues().apply {
                                        put(COL_FOLDER_ID, folderId)
                                        put(COL_FOLDER_PATH, candidate.folderPath)
                                    },
                                    "$COL_URI = ? AND $COL_FOLDER_ID IS NULL",
                                    arrayOf(candidate.uri.toString()),
                                )
                            }
                        }
                        skipped++
                    }
                    options.skipDuplicates && known.hasFile(candidate.name, candidate.size) -> skipped++
                    else -> {
                        fresh += candidate
                        known.addFile(candidate.name, candidate.size)
                    }
                }
            }
            val total = fresh.size
            _importProgress.value = ImportProgress(0, total, "正在读取歌曲信息…")
            val done = java.util.concurrent.atomic.AtomicInteger(0)
            val gate = Semaphore(IMPORT_PARALLELISM)
            val now = System.currentTimeMillis()
            var order = 0
            fresh.chunked(IMPORT_BATCH).forEach { batch ->
                val read = coroutineScope {
                    batch.map { candidate ->
                        async(Dispatchers.IO) {
                            gate.withPermit {
                                if (candidate.persist) persistReadPermission(candidate.uri)
                                val outcome = runCatching { readMetadata(candidate.uri, candidate.name) }
                                val finished = done.incrementAndGet()
                                if (finished % 8 == 0 || finished == total) {
                                    _importProgress.value = ImportProgress(finished, total, "正在读取歌曲信息…")
                                }
                                candidate to outcome
                            }
                        }
                    }.awaitAll()
                }
                mutex.withLock {
                    val db = helper.writableDatabase
                    db.beginTransaction()
                    try {
                        read.forEach { (candidate, outcome) ->
                            val index = order++
                            val metadata = outcome.getOrElse { error ->
                                errors += "${candidate.name}：${error.message ?: "无法读取"}"
                                return@forEach
                            }
                            if (metadata.hasVideo) {
                                filtered++
                                return@forEach
                            }
                            if (options.minDurationMs > 0 && metadata.durationMs in 1 until options.minDurationMs) {
                                filtered++
                                return@forEach
                            }
                            if (options.skipDuplicates && known.hasSong(metadata.title, metadata.artist, metadata.durationMs)) {
                                skipped++
                                return@forEach
                            }
                            val id = sha256(candidate.uri.toString())
                            val inserted = runCatching {
                                insertTrack(db, id, candidate, metadata, addedAt = now + index)
                            }.onFailure { error ->
                                errors += "${candidate.name}：${error.message ?: "写入失败"}"
                            }.isSuccess
                            if (inserted) {
                                known.addSong(metadata.title, metadata.artist, metadata.durationMs)
                                importedIds += id
                                val sidecar = candidate.lyricUri
                                if (sidecar != null) {
                                    runCatching { importSidecarIfMissing(candidate.uri, sidecar) }
                                        .onFailure { error -> errors += "${displayName(sidecar)}：${error.message ?: "歌词读取失败"}" }
                                } else {
                                    metadata.embeddedLyrics?.let { text -> runCatching { storeEmbeddedLyrics(id, text) } }
                                }
                            }
                        }
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
            }
            _importProgress.value = ImportProgress(total, total, "正在写入音乐库…")
            mutex.withLock { refreshLocked() }        } finally {
            _importProgress.value = null
        }
        return ImportReport(importedIds.size, skipped, errors, filtered, importedIds)
    }

    private fun insertTrack(db: SQLiteDatabase, id: String, candidate: ImportCandidate, metadata: Metadata, addedAt: Long) {
        val coverPath = metadata.picture?.takeIf { it.size <= MAX_COVER_BYTES }?.let { bytes ->
            val directory = File(context.filesDir, "covers").apply { mkdirs() }
            File(directory, "$id.cover").also { it.writeBytes(bytes) }.absolutePath
        }
        val values = ContentValues().apply {
            put(COL_ID, id)
            put(COL_URI, candidate.uri.toString())
            put(COL_TITLE, metadata.title)
            put(COL_ARTIST, metadata.artist)
            put(COL_ALBUM, metadata.album)
            put(COL_DURATION, metadata.durationMs)
            put(COL_DISPLAY_NAME, candidate.name)
            put(COL_MIME, candidate.mime)
            put(COL_SIZE, candidate.size.takeIf { it > 0 } ?: querySize(candidate.uri))
            put(COL_COVER, coverPath)
            put(COL_LYRICS_INSTRUMENTAL, 0)
            put(COL_LYRICS_LOOKUP_AT, 0L)
            put(COL_FAVORITE, 0)
            put(COL_ADDED_AT, addedAt)
            put(COL_FOLDER_ID, candidate.folderId)
            put(COL_FOLDER_PATH, candidate.folderPath)
            putTags(metadata)
        }
        db.insertOrThrow(TABLE_TRACKS, null, values)
    }

    private fun ContentValues.putTags(metadata: Metadata) {
        put(COL_TRACK_NUMBER, metadata.trackNumber)
        put(COL_DISC_NUMBER, metadata.discNumber)
        put(COL_YEAR, metadata.year)
        put(COL_ALBUM_ARTIST, metadata.albumArtist)
        put(COL_COMPOSER, metadata.composer ?: "?")
        put(COL_SAMPLE_RATE, metadata.sampleRate)
        put(COL_BITS_PER_SAMPLE, metadata.bitsPerSample)
        put(COL_BITRATE, metadata.bitrate)
        put(COL_TAGS_SCANNED, 1)
    }

    suspend fun rescanTags(onlyMissing: Boolean = true): Int = withContext(Dispatchers.IO) {
        val targets = mutex.withLock {
            helper.readableDatabase.rawQuery(
                "SELECT $COL_ID, $COL_URI, $COL_DISPLAY_NAME FROM $TABLE_TRACKS" +
                    if (onlyMissing) " WHERE $COL_TAGS_SCANNED = 0" else "",
                null,
            ).use { cursor -> buildList { while (cursor.moveToNext()) add(Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2))) } }
        }.filterNot { it.second.startsWith("ncm://") }
        if (targets.isEmpty()) return@withContext 0
        val gate = Semaphore(IMPORT_PARALLELISM)
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        _importProgress.value = ImportProgress(0, targets.size, "正在扫描曲目信息…")
        try {
            targets.chunked(48).forEach { chunk ->
                val read = coroutineScope {
                    chunk.map { (id, uri, name) ->
                        async {
                            gate.withPermit {
                                val metadata = runCatching { readMetadata(Uri.parse(uri), name) }.getOrNull()
                                _importProgress.value = ImportProgress(done.incrementAndGet(), targets.size, "正在扫描曲目信息…")
                                id to metadata
                            }
                        }
                    }.awaitAll()
                }
                mutex.withLock {
                    val db = helper.writableDatabase
                    db.beginTransaction()
                    try {
                        read.forEach { (id, metadata) ->
                            val values = ContentValues()
                            if (metadata != null) values.putTags(metadata) else values.put(COL_TAGS_SCANNED, 1)
                            db.update(TABLE_TRACKS, values, "$COL_ID = ?", arrayOf(id))
                        }
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
            }
            mutex.withLock { refreshLocked() }
        } finally {
            _importProgress.value = null
        }
        targets.size
    }

    suspend fun recordPlay(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            helper.writableDatabase.execSQL(
                "UPDATE $TABLE_TRACKS SET $COL_PLAY_COUNT = $COL_PLAY_COUNT + 1, $COL_LAST_PLAYED = ? WHERE $COL_ID = ?",
                arrayOf<Any>(System.currentTimeMillis(), id),
            )
            refreshLocked()
        }
    }

    suspend fun deleteMany(ids: Collection<String>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        mutex.withLock {
            val tracks = queryAll().filter { it.id in ids }
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                ids.forEach { db.delete(TABLE_TRACKS, "$COL_ID = ?", arrayOf(it)) }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            val remaining = queryAll().mapNotNullTo(HashSet()) { it.coverPath }
            tracks.mapNotNull { it.coverPath }.filter { it !in remaining }.forEach { File(it).delete() }
            refreshLocked()
        }
    }

    private class DuplicateIndex(tracks: List<Track>) {
        private val uris = tracks.mapTo(HashSet()) { it.uri }
        private val files = tracks.filter { it.sizeBytes > 0 }.mapTo(HashSet()) { fileKey(it.displayName, it.sizeBytes) }
        private val songs = HashMap<String, MutableList<Long>>().apply {
            tracks.forEach { track -> songKey(track.title, track.artist)?.let { getOrPut(it) { mutableListOf() } += track.durationMs } }
        }

        fun hasUri(uri: String) = uri in uris
        fun hasFile(name: String, size: Long) = size > 0 && fileKey(name, size) in files
        fun addFile(name: String, size: Long) { if (size > 0) files += fileKey(name, size) }

        fun hasSong(title: String, artist: String, durationMs: Long): Boolean {
            val key = songKey(title, artist) ?: return false
            return songs[key]?.any { durationMs <= 0 || it <= 0 || kotlin.math.abs(it - durationMs) <= 2_000 } == true
        }

        fun addSong(title: String, artist: String, durationMs: Long) {
            songKey(title, artist)?.let { songs.getOrPut(it) { mutableListOf() } += durationMs }
        }

        private fun fileKey(name: String, size: Long) = "${name.lowercase(Locale.ROOT)}|$size"

        private fun songKey(title: String, artist: String): String? {
            if (title.isBlank() || title == "未知歌曲" || artist.isBlank() || artist == "未知艺术家") return null
            return OnlineMetadataRepository.normalize(title) + "|" + OnlineMetadataRepository.normalize(artist)
        }
    }

    private data class TreeChild(val id: String, val name: String, val mime: String, val size: Long)

    data class ScanFolder(val uri: Uri, val name: String)

    data class ScanTarget(val treeUri: Uri, val documentId: String? = null, val name: String? = null)

    private data class ImportCandidate(
        val uri: Uri,
        val name: String,
        val size: Long,
        val mime: String?,
        val folderId: String?,
        val folderPath: String?,
        val lyricUri: Uri?,
        val persist: Boolean,
    )

    suspend fun importLyrics(uris: List<Uri>, preferredTrackId: String? = null): ImportReport =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                var imported = 0
                var skipped = 0
                val errors = mutableListOf<String>()
                val available = queryAll()
                for (uri in uris.distinct()) {
                    val result = runCatching {
                        persistReadPermission(uri)
                        val raw = readTextLimited(uri)
                        require(LyricsParser.parse(raw).lines.isNotEmpty()) { "未解析到有效歌词内容" }
                        val target = preferredTrackId?.let { id -> available.firstOrNull { it.id == id } }
                            ?: matchLyricsToTrack(displayName(uri), available)
                            ?: error("找不到同名歌曲")
                        writeLyricsLocked(
                            target.id,
                            StoredLyrics(
                                lyrics = raw,
                                source = "local",
                                format = detectLyricsFormat(raw),
                                confidence = 1.0,
                            ),
                        )
                        imported++
                    }
                    result.onFailure { error ->
                        errors += "${displayName(uri)}：${error.message ?: "导入失败"}"
                    }
                }
                refreshLocked()
                if (imported == 0 && errors.isEmpty()) skipped = uris.size
                ImportReport(imported, skipped, errors)
            }
        }

    suspend fun storeLyrics(id: String, lyrics: StoredLyrics) = withContext(Dispatchers.IO) {
        mutex.withLock {
            writeLyricsLocked(id, lyrics)
            refreshLocked()
        }
    }

    suspend fun markLyricsLookup(id: String, status: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            helper.writableDatabase.update(
                TABLE_TRACKS,
                ContentValues().apply {
                    put(COL_LYRICS_LOOKUP_AT, System.currentTimeMillis())
                    put(COL_LYRICS_LOOKUP_STATUS, status)
                },
                "$COL_ID = ?",
                arrayOf(id),
            )
            refreshLocked()
        }
    }

    suspend fun setFavorite(id: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock {
            helper.writableDatabase.update(
                TABLE_TRACKS,
                ContentValues().apply { put(COL_FAVORITE, if (favorite) 1 else 0) },
                "$COL_ID = ?",
                arrayOf(id),
            )
            refreshLocked()
        }
    }

    suspend fun backfillComposers(): Int = withContext(Dispatchers.IO) {
        val rows = mutex.withLock {
            helper.readableDatabase.rawQuery(
                "SELECT $COL_ID, $COL_URI, $COL_DISPLAY_NAME FROM $TABLE_TRACKS WHERE $COL_COMPOSER IS NULL",
                null,
            ).use { cursor -> buildList { while (cursor.moveToNext()) add(Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2).orEmpty())) } }
        }.filterNot { row -> row.second.startsWith("ncm://") || row.second.startsWith("sc://") || row.second.startsWith("kw://") || row.second.startsWith("nd:") }
        if (rows.isEmpty()) return@withContext 0
        var done = 0
        rows.chunked(24).forEach { chunk ->
            val updates = chunk.map { (id, uri, name) ->
                val composer = runCatching {
                    val target = Uri.parse(uri)
                    embeddedComposer(target) ?: run {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(context, target)
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)?.trim()?.ifEmpty { null }
                        } finally {
                            retriever.release()
                        }
                    }
                }.getOrNull()
                id to composer
            }
            mutex.withLock {
                val db = helper.writableDatabase
                db.beginTransaction()
                try {
                    updates.forEach { (id, composer) ->
                        db.update(TABLE_TRACKS, ContentValues().apply { put(COL_COMPOSER, composer ?: "?") }, "$COL_ID = ?", arrayOf(id))
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
            done += updates.size
        }
        mutex.withLock { refreshLocked() }
        done
    }

    suspend fun setComposers(values: Map<String, Pair<String, String?>>) = withContext(Dispatchers.IO) {
        if (values.isEmpty()) return@withContext
        mutex.withLock {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                values.forEach { (id, value) ->
                    db.update(TABLE_TRACKS, ContentValues().apply {
                        put(COL_COMPOSER, value.first)
                        put(COL_COMPOSER_CREDIT, value.second)
                    }, "$COL_ID = ?", arrayOf(id))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            refreshLocked()
        }
    }

    suspend fun updateMetadata(id: String, title: String, artist: String, album: String) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                helper.writableDatabase.update(
                    TABLE_TRACKS,
                    ContentValues().apply {
                        put(COL_TITLE, title.trim().ifEmpty { "未知歌曲" })
                        put(COL_ARTIST, artist.trim().ifEmpty { "未知艺术家" })
                        put(COL_ALBUM, album.trim().ifEmpty { "未知专辑" })
                        put(COL_LYRICS_LOOKUP_AT, 0L)
                        putNull(COL_LYRICS_LOOKUP_STATUS)
                    },
                    "$COL_ID = ?",
                    arrayOf(id),
                )
                refreshLocked()
            }
        }

    suspend fun setCover(id: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        require(bytes.size in 1..MAX_COVER_BYTES) { "封面图片无效" }
        mutex.withLock {
            val previous = queryAll().firstOrNull { it.id == id }?.coverPath
            val directory = File(context.filesDir, "covers").apply { mkdirs() }
            val file = File(directory, "$id-${System.currentTimeMillis()}.cover").also { it.writeBytes(bytes) }
            helper.writableDatabase.update(
                TABLE_TRACKS,
                ContentValues().apply { put(COL_COVER, file.absolutePath) },
                "$COL_ID = ?",
                arrayOf(id),
            )
            previous?.takeIf { it != file.absolutePath }?.let(::deleteUnusedCover)
            refreshLocked()
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val track = queryAll().firstOrNull { it.id == id }
            helper.writableDatabase.delete(TABLE_TRACKS, "$COL_ID = ?", arrayOf(id))
            track?.coverPath?.let(::deleteUnusedCover)
            val managedAudioDir = File(context.filesDir, LEGACY_AUDIO_DIR).canonicalFile
            track?.uri?.let(Uri::parse)?.takeIf { it.scheme == "file" }?.path?.let(::File)?.let { audio ->
                if (runCatching { audio.canonicalFile.parentFile == managedAudioDir }.getOrDefault(false)) {
                    audio.delete()
                }
            }
            refreshLocked()
        }
    }

    suspend fun createPlaylist(name: String): Playlist = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cleanName = playlistName(name)
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val db = helper.writableDatabase
            val nextOrder = db.rawQuery(
                "SELECT COALESCE(MAX($COL_SORT_ORDER), -1) + 1 FROM $TABLE_PLAYLISTS",
                null,
            ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
            db.insertOrThrow(TABLE_PLAYLISTS, null, ContentValues().apply {
                put(COL_ID, id)
                put(COL_NAME, cleanName)
                put(COL_SORT_ORDER, nextOrder)
                put(COL_CREATED_AT, now)
                put(COL_UPDATED_AT, now)
            })
            refreshLocked()
            checkNotNull(_playlists.value.firstOrNull { it.id == id })
        }
    }

    suspend fun createPlaylistWithTracks(name: String, trackIds: List<String>): Playlist = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cleanName = playlistName(name.take(80))
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                val nextOrder = db.rawQuery(
                    "SELECT COALESCE(MAX($COL_SORT_ORDER), -1) + 1 FROM $TABLE_PLAYLISTS",
                    null,
                ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
                db.insertOrThrow(TABLE_PLAYLISTS, null, ContentValues().apply {
                    put(COL_ID, id)
                    put(COL_NAME, cleanName)
                    put(COL_SORT_ORDER, nextOrder)
                    put(COL_CREATED_AT, now)
                    put(COL_UPDATED_AT, now)
                })
                trackIds.distinct().filter { trackExists(db, it) }.forEachIndexed { index, trackId ->
                    db.insertWithOnConflict(
                        TABLE_PLAYLIST_TRACKS,
                        null,
                        ContentValues().apply {
                            put(COL_PLAYLIST_ID, id)
                            put(COL_TRACK_ID, trackId)
                            put(COL_SORT_ORDER, index)
                        },
                        SQLiteDatabase.CONFLICT_IGNORE,
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            refreshLocked()
            checkNotNull(_playlists.value.firstOrNull { it.id == id })
        }
    }

    suspend fun renamePlaylist(id: String, name: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            requirePlaylistUpdate(id, ContentValues().apply {
                put(COL_NAME, playlistName(name))
                put(COL_UPDATED_AT, System.currentTimeMillis())
            })
            refreshLocked()
        }
    }

    suspend fun deletePlaylist(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(helper.writableDatabase.delete(TABLE_PLAYLISTS, "$COL_ID = ?", arrayOf(id)) == 1) {
                "歌单不存在"
            }
            refreshLocked()
        }
    }

    suspend fun addToPlaylist(id: String, trackId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val db = helper.writableDatabase
            requirePlaylistExists(db, id)
            require(trackExists(db, trackId)) { "歌曲不存在" }
            val nextOrder = db.rawQuery(
                "SELECT COALESCE(MAX($COL_SORT_ORDER), -1) + 1 FROM $TABLE_PLAYLIST_TRACKS WHERE $COL_PLAYLIST_ID = ?",
                arrayOf(id),
            ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
            val inserted = db.insertWithOnConflict(
                TABLE_PLAYLIST_TRACKS,
                null,
                ContentValues().apply {
                    put(COL_PLAYLIST_ID, id)
                    put(COL_TRACK_ID, trackId)
                    put(COL_SORT_ORDER, nextOrder)
                },
                SQLiteDatabase.CONFLICT_IGNORE,
            )
            if (inserted != -1L) touchPlaylist(db, id)
            refreshLocked()
        }
    }

    suspend fun removeFromPlaylist(id: String, trackId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val db = helper.writableDatabase
            requirePlaylistExists(db, id)
            if (db.delete(
                    TABLE_PLAYLIST_TRACKS,
                    "$COL_PLAYLIST_ID = ? AND $COL_TRACK_ID = ?",
                    arrayOf(id, trackId),
                ) > 0
            ) touchPlaylist(db, id)
            refreshLocked()
        }
    }

    suspend fun reorderPlaylistTracks(id: String, orderedTrackIds: List<String>) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val db = helper.writableDatabase
                requirePlaylistExists(db, id)
                val existing = playlistTrackIds(db, id)
                require(orderedTrackIds.size == existing.size &&
                    orderedTrackIds.toSet() == existing.toSet()) { "歌单歌曲顺序与当前内容不一致" }
                db.beginTransaction()
                try {
                    orderedTrackIds.forEachIndexed { index, trackId ->
                        db.update(
                            TABLE_PLAYLIST_TRACKS,
                            ContentValues().apply { put(COL_SORT_ORDER, index) },
                            "$COL_PLAYLIST_ID = ? AND $COL_TRACK_ID = ?",
                            arrayOf(id, trackId),
                        )
                    }
                    touchPlaylist(db, id)
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                refreshLocked()
            }
        }

    suspend fun reorderPlaylists(orderedIds: List<String>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val db = helper.writableDatabase
            val existing = queryPlaylists().map(Playlist::id)
            require(orderedIds.size == existing.size && orderedIds.toSet() == existing.toSet()) {
                "歌单顺序与当前内容不一致"
            }
            db.beginTransaction()
            try {
                orderedIds.forEachIndexed { index, id ->
                    db.update(
                        TABLE_PLAYLISTS,
                        ContentValues().apply { put(COL_SORT_ORDER, index) },
                        "$COL_ID = ?",
                        arrayOf(id),
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            refreshLocked()
        }
    }

    private fun importOne(
        uri: Uri,
        persistPermission: Boolean,
        folderId: String? = null,
        folderPath: String? = null,
    ): Boolean {
        val uriText = uri.toString()
        if (helper.readableDatabase.rawQuery(
                "SELECT 1 FROM $TABLE_TRACKS WHERE $COL_URI = ? LIMIT 1",
                arrayOf(uriText),
            ).use { it.moveToFirst() }
        ) {
            if (folderId != null) helper.writableDatabase.update(
                TABLE_TRACKS,
                ContentValues().apply {
                    put(COL_FOLDER_ID, folderId)
                    put(COL_FOLDER_PATH, folderPath)
                },
                "$COL_URI = ? AND $COL_FOLDER_ID IS NULL",
                arrayOf(uriText),
            )
            return false
        }

        if (persistPermission) persistReadPermission(uri)
        val name = displayName(uri)
        val mime = context.contentResolver.getType(uri)
        require(uri.scheme == "file" || isSupportedAudio(name, mime)) { "不支持的音频格式" }
        val metadata = runCatching { readMetadata(uri, name) }.getOrElse {
            Metadata(
                title = baseName(name).ifBlank { "未知歌曲" },
                artist = "未知艺术家",
                album = "未知专辑",
                durationMs = 0L,
                picture = null,
            )
        }
        val id = sha256(uriText)
        val coverPath = metadata.picture?.takeIf { it.size <= MAX_COVER_BYTES }?.let { bytes ->
            val directory = File(context.filesDir, "covers").apply { mkdirs() }
            File(directory, "$id.cover").also { it.writeBytes(bytes) }.absolutePath
        }
        val values = ContentValues().apply {
            put(COL_ID, id)
            put(COL_URI, uriText)
            put(COL_TITLE, metadata.title)
            put(COL_ARTIST, metadata.artist)
            put(COL_ALBUM, metadata.album)
            put(COL_DURATION, metadata.durationMs)
            put(COL_DISPLAY_NAME, name)
            put(COL_MIME, mime)
            put(COL_SIZE, querySize(uri))
            put(COL_COVER, coverPath)
            putNull(COL_LYRICS)
            putNull(COL_WORD_LYRICS)
            putNull(COL_TRANSLATION_LYRICS)
            putNull(COL_ROMAN_LYRICS)
            putNull(COL_LYRICS_SOURCE)
            putNull(COL_LYRICS_FORMAT)
            putNull(COL_LYRICS_CONFIDENCE)
            put(COL_LYRICS_INSTRUMENTAL, 0)
            put(COL_LYRICS_LOOKUP_AT, 0L)
            putNull(COL_LYRICS_LOOKUP_STATUS)
            put(COL_FAVORITE, 0)
            put(COL_ADDED_AT, System.currentTimeMillis())
            put(COL_FOLDER_ID, folderId)
            put(COL_FOLDER_PATH, folderPath)
            putTags(metadata)
        }
        helper.writableDatabase.insertOrThrow(TABLE_TRACKS, null, values)
        return true
    }

    private fun persistReadPermission(uri: Uri) {
        if (uri.scheme != "content") return
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun readMetadata(uri: Uri, fallbackName: String): Metadata {
        val retriever = MediaMetadataRetriever()
        try {
            try {
                if (uri.scheme == "http" || uri.scheme == "https") retriever.setDataSource(uri.toString(), remoteHeaders(uri.toString()))
                else retriever.setDataSource(context, uri)
            } catch (error: Exception) {
                if (fallbackName.substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("aif", "aiff", "aifc")) {
                    return readAiffMetadata(uri, fallbackName) ?: throw error
                }
                throw error
            }
            fun tag(key: Int) = retriever.extractMetadata(key)?.trim()?.takeIf(String::isNotEmpty)
            val wav = readWavTags(uri, fallbackName)
            fun own(retrieved: String?, fromFile: String?): String? = when {
                retrieved == null -> fromFile
                fromFile != null && TagCharset.looksMojibake(retrieved) && !TagCharset.looksMojibake(fromFile) -> fromFile
                else -> retrieved
            }
            val (rawTitle, rawArtist, rawAlbum, rawAlbumArtist) = TagCharset.repair(listOf(
                own(tag(MediaMetadataRetriever.METADATA_KEY_TITLE), wav?.title),
                own(tag(MediaMetadataRetriever.METADATA_KEY_ARTIST), wav?.artist),
                own(tag(MediaMetadataRetriever.METADATA_KEY_ALBUM), wav?.album),
                own(tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST), wav?.albumArtist),
            ))
            val fromName = parseFileName(fallbackName)
            val title = rawTitle
                ?: fromName.title.ifBlank { fallbackName.substringBeforeLast('.').ifBlank { "未知歌曲" } }
            val artist = rawArtist ?: fromName.artist ?: "未知艺术家"
            val album = rawAlbum ?: "未知专辑"
            val duration = tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            val track = leadingNumber(tag(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)) ?: wav?.track?.takeIf { it > 0 } ?: fromName.track ?: 0
            val disc = leadingNumber(tag(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)) ?: fromName.disc ?: 0
            return Metadata(
                title = title,
                artist = artist,
                album = album,
                durationMs = duration,
                picture = retriever.embeddedPicture,
                trackNumber = track.coerceIn(0, 9_999),
                discNumber = disc.coerceIn(0, 999),
                year = tag(MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull()
                    ?: tag(MediaMetadataRetriever.METADATA_KEY_DATE)?.take(4)?.toIntOrNull() ?: wav?.year ?: 0,
                albumArtist = rawAlbumArtist,
                composer = embeddedComposer(uri) ?: tag(MediaMetadataRetriever.METADATA_KEY_COMPOSER),
                sampleRate = tag(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0,
                bitsPerSample = tag(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull() ?: 0,
                bitrate = tag(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0,
                hasVideo = tag(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes",
                embeddedLyrics = runCatching { readEmbeddedLyrics(uri, fallbackName) }.getOrNull(),
            )
        } finally {
            retriever.release()
        }
    }

    private fun embeddedComposer(uri: Uri): String? {
        if (uri.scheme != "content" && uri.scheme != "file") return null
        val values = ComposerTags.read { runCatching { context.contentResolver.openInputStream(uri) }.getOrNull() }
        return values.joinToString(" / ").trim().takeIf(String::isNotEmpty)
    }

    private fun readWavTags(uri: Uri, fallbackName: String): WavTags? {
        if (uri.scheme != "content" && uri.scheme != "file") return null
        if (fallbackName.substringAfterLast('.', "").lowercase(Locale.ROOT) !in setOf("wav", "wave")) return null
        return runCatching { context.contentResolver.openInputStream(uri)?.use { WavTags.read(it) } }.getOrNull()
    }

    suspend fun repairMisreadTags(): Int = withContext(Dispatchers.IO) {
        data class Row(val id: String, val uri: String, val name: String, val values: List<String?>)
        val rows = mutex.withLock {
            helper.readableDatabase.rawQuery(
                "SELECT $COL_ID, $COL_URI, $COL_DISPLAY_NAME, $COL_TITLE, $COL_ARTIST, $COL_ALBUM, $COL_ALBUM_ARTIST FROM $TABLE_TRACKS",
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val values = (3..6).map { if (cursor.isNull(it)) null else cursor.getString(it) }
                        if (values.any(TagCharset::looksMojibake)) {
                            add(Row(cursor.getString(0), cursor.getString(1), cursor.getString(2).orEmpty(), values))
                        }
                    }
                }
            }
        }.filterNot { it.uri.startsWith("ncm://") || it.uri.startsWith("sc://") }
        if (rows.isEmpty()) return@withContext 0
        val fixed = rows.mapNotNull { row ->
            val read = runCatching { readMetadata(Uri.parse(row.uri), row.name) }.getOrNull()
            val fresh = if (read != null) listOf(read.title, read.artist, read.album, read.albumArtist) else TagCharset.repair(row.values)
            val next = row.values.mapIndexed { i, old -> if (TagCharset.looksMojibake(old)) fresh[i]?.takeUnless(TagCharset::looksMojibake) ?: old else old }
            if (next == row.values) null else row.id to next
        }
        if (fixed.isEmpty()) return@withContext 0
        mutex.withLock {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                fixed.forEach { (id, values) ->
                    db.update(TABLE_TRACKS, ContentValues().apply {
                        values[0]?.let { put(COL_TITLE, it) }
                        values[1]?.let { put(COL_ARTIST, it) }
                        values[2]?.let { put(COL_ALBUM, it) }
                        put(COL_ALBUM_ARTIST, values[3])
                    }, "$COL_ID = ?", arrayOf(id))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            refreshLocked()
        }
        fixed.size
    }

    private fun readAiffMetadata(uri: Uri, fallbackName: String): Metadata? {
        if (uri.scheme != "content" && uri.scheme != "file") return null
        val tags = context.contentResolver.openInputStream(uri)?.use { com.olbyine.music.playback.AiffTags.read(it.buffered()) } ?: return null
        val fromName = parseFileName(fallbackName)
        return Metadata(
            title = tags.title?.takeIf(String::isNotBlank) ?: fromName.title.ifBlank { fallbackName.substringBeforeLast('.') },
            artist = tags.artist?.takeIf(String::isNotBlank) ?: fromName.artist ?: "未知艺术家",
            album = tags.album?.takeIf(String::isNotBlank) ?: "未知专辑",
            durationMs = tags.format?.durationMs ?: 0L,
            picture = tags.picture,
            trackNumber = tags.track.takeIf { it > 0 } ?: fromName.track ?: 0,
            discNumber = fromName.disc ?: 0,
            year = tags.year,
            albumArtist = null,
            sampleRate = tags.format?.sampleRate ?: 0,
            bitsPerSample = tags.format?.bits ?: 0,
            bitrate = tags.format?.let { it.sampleRate * it.bytesPerFrame * 8 } ?: 0,
            hasVideo = false,
            embeddedLyrics = runCatching { readEmbeddedLyrics(uri, fallbackName) }.getOrNull(),
        )
    }
    private fun leadingNumber(value: String?): Int? = value?.let { Regex("^\\s*(\\d{1,4})").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        ?.takeIf { it > 0 }

    private data class NameParts(val title: String, val artist: String?, val track: Int?, val disc: Int?)

    private fun parseFileName(name: String): NameParts {
        var base = name.substringBeforeLast('.').trim()
        var track: Int? = null
        var disc: Int? = null
        Regex("^[\\[(]?(?:(\\d{1,2})[-.](\\d{1,3})|(\\d{1,3}))[\\])]?(?:\\s*[-._]\\s*|\\s+)(?=\\S)").find(base)?.let { match ->
            if (match.groupValues[2].isNotEmpty()) {
                disc = match.groupValues[1].toIntOrNull()
                track = match.groupValues[2].toIntOrNull()
            } else {
                track = match.groupValues[3].toIntOrNull()
            }
            base = base.substring(match.range.last + 1).trim()
        }
        val parts = base.split(Regex("\\s+[-–—]\\s+"), limit = 2)
        return if (parts.size == 2) NameParts(parts[1].trim(), parts[0].trim().takeIf(String::isNotBlank), track, disc)
        else NameParts(base, null, track, disc)
    }

    private fun storeEmbeddedLyrics(id: String, raw: String) {
        if (raw.isBlank()) return
        writeLyricsLocked(id, StoredLyrics(
            lyrics = raw,
            source = "embedded",
            format = detectLyricsFormat(raw),
            confidence = 1.0,
        ))
    }

    private fun readEmbeddedLyrics(uri: Uri, name: String): String? {
        if (uri.scheme != "content" && uri.scheme != "file") return null
        val resolver = context.contentResolver
        return EmbeddedLyrics.read(
            name,
            open = { resolver.openInputStream(uri) },
            channel = {
                resolver.openFileDescriptor(uri, "r")?.let { descriptor ->
                    val stream = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                    stream.channel
                }
            },
        )
    }
    private fun importSidecarIfMissing(audioUri: Uri, lyricUri: Uri) {
        val id = sha256(audioUri.toString())
        val missing = helper.readableDatabase.rawQuery(
            "SELECT $COL_LYRICS, $COL_WORD_LYRICS, $COL_LYRICS_INSTRUMENTAL FROM $TABLE_TRACKS WHERE $COL_ID = ?",
            arrayOf(id),
        ).use { cursor ->
            cursor.moveToFirst() && cursor.getString(0).isNullOrBlank() &&
                cursor.getString(1).isNullOrBlank() && cursor.getInt(2) == 0
        }
        if (!missing) return
        val raw = readTextLimited(lyricUri)
        require(LyricsParser.parse(raw).lines.isNotEmpty()) { "未解析到有效歌词内容" }
        writeLyricsLocked(id, StoredLyrics(
            lyrics = raw,
            source = "local",
            format = detectLyricsFormat(raw),
            confidence = 1.0,
        ))
    }

    private fun isSupportedLyrics(name: String?): Boolean =
        name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) in LYRIC_EXTENSIONS

    private fun lyricPriority(name: String?): Int = when (
        name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
    ) {
        "ttml" -> 0
        "yrc", "elrc" -> 1
        else -> 2
    }

    private fun baseName(name: String?): String = name.orEmpty().substringBeforeLast('.')

    private fun displayName(uri: Uri): String {
        if (uri.scheme == "file") return uri.lastPathSegment ?: "音频文件"
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf(String::isNotBlank) ?: uri.lastPathSegment ?: "音频文件"
    }

    private fun querySize(uri: Uri): Long {
        if (uri.scheme == "file") return uri.path?.let(::File)?.length() ?: 0L
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
            }
        }.getOrNull() ?: 0L
    }

    private fun readTextLimited(uri: Uri): String {
        val stream = if (uri.scheme == "http" || uri.scheme == "https") {
            (java.net.URL(uri.toString()).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 20_000
                remoteHeaders(uri.toString()).forEach { (name, value) -> setRequestProperty(name, value) }
            }.inputStream
        } else context.contentResolver.openInputStream(uri) ?: error("无法打开文件")
        val output = ByteArrayOutputStream()
        stream.use { input ->
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_LYRIC_BYTES) { "歌词文件过大" }
                output.write(buffer, 0, count)
            }
        }
        val bytes = output.toByteArray()
        return when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16BE)
            else -> decodeText(bytes)
        }
    }

    private fun decodeText(bytes: ByteArray): String {
        val utf8 = runCatching {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull()
        return (utf8 ?: bytes.toString(Charset.forName("GB18030"))).removePrefix("\uFEFF")
    }

    private fun writeLyricsLocked(id: String, lyrics: StoredLyrics) {
        val values = ContentValues().apply {
            put(COL_LYRICS, lyrics.lyrics.takeIf(String::isNotBlank))
            put(COL_WORD_LYRICS, lyrics.wordLyrics.takeIf(String::isNotBlank))
            put(COL_TRANSLATION_LYRICS, lyrics.translationLyrics.takeIf(String::isNotBlank))
            put(COL_ROMAN_LYRICS, lyrics.romanLyrics.takeIf(String::isNotBlank))
            put(COL_LYRICS_SOURCE, lyrics.source)
            put(COL_LYRICS_FORMAT, lyrics.format)
            lyrics.confidence?.let { put(COL_LYRICS_CONFIDENCE, it) }
                ?: putNull(COL_LYRICS_CONFIDENCE)
            put(COL_LYRICS_INSTRUMENTAL, if (lyrics.instrumental) 1 else 0)
            put(COL_LYRICS_LOOKUP_AT, System.currentTimeMillis())
            put(COL_LYRICS_LOOKUP_STATUS, "matched")
        }
        check(helper.writableDatabase.update(TABLE_TRACKS, values, "$COL_ID = ?", arrayOf(id)) == 1) {
            "歌曲不存在"
        }
    }

    private fun detectLyricsFormat(raw: String): String = when {
        raw.contains("<tt", ignoreCase = true) -> "ttml"
        Regex("(?m)^\\s*\\[\\d+,\\d+]\\(\\d+,\\d+").containsMatchIn(raw) -> "yrc"
        Regex("<\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?>").containsMatchIn(raw) -> "enhanced-lrc"
        Regex("\\[\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?]").containsMatchIn(raw) -> "lrc-line"
        else -> "plain"
    }

    private fun matchLyricsToTrack(fileName: String, tracks: List<Track>): Track? {
        val key = normalizedName(fileName.substringBeforeLast('.'))
        return tracks.firstOrNull { normalizedName(it.displayName.substringBeforeLast('.')) == key }
            ?: tracks.firstOrNull { normalizedName(it.title) == key }
    }

    private fun normalizedName(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("[\\s_\\-–—.·]+"), "")

    private fun deleteUnusedCover(path: String) {
        val inUse = queryAll().any { it.coverPath == path }
        if (!inUse) File(path).delete()
    }

    private fun playlistName(name: String): String = name.trim().also {
        require(it.isNotEmpty() && it.length <= 80) { "歌单名称须为 1 到 80 个字符" }
    }

    private fun requirePlaylistExists(db: SQLiteDatabase, id: String) {
        check(db.rawQuery(
            "SELECT 1 FROM $TABLE_PLAYLISTS WHERE $COL_ID = ? LIMIT 1",
            arrayOf(id),
        ).use { it.moveToFirst() }) { "歌单不存在" }
    }

    private fun trackExists(db: SQLiteDatabase, id: String): Boolean = db.rawQuery(
        "SELECT 1 FROM $TABLE_TRACKS WHERE $COL_ID = ? LIMIT 1",
        arrayOf(id),
    ).use { it.moveToFirst() }

    private fun requirePlaylistUpdate(id: String, values: ContentValues) {
        check(helper.writableDatabase.update(
            TABLE_PLAYLISTS,
            values,
            "$COL_ID = ?",
            arrayOf(id),
        ) == 1) { "歌单不存在" }
    }

    private fun touchPlaylist(db: SQLiteDatabase, id: String) {
        db.update(
            TABLE_PLAYLISTS,
            ContentValues().apply { put(COL_UPDATED_AT, System.currentTimeMillis()) },
            "$COL_ID = ?",
            arrayOf(id),
        )
    }

    private fun playlistTrackIds(db: SQLiteDatabase, id: String): List<String> = db.query(
        TABLE_PLAYLIST_TRACKS,
        arrayOf(COL_TRACK_ID),
        "$COL_PLAYLIST_ID = ?",
        arrayOf(id),
        null,
        null,
        "$COL_SORT_ORDER ASC, $COL_TRACK_ID ASC",
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    private fun queryPlaylists(): List<Playlist> {
        val db = helper.readableDatabase
        return db.query(
            TABLE_PLAYLISTS,
            null,
            null,
            null,
            null,
            null,
            "$COL_SORT_ORDER ASC, $COL_CREATED_AT ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID))
                    add(Playlist(
                        id = id,
                        name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)),
                        trackIds = playlistTrackIds(db, id),
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT)),
                        updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_UPDATED_AT)),
                    ))
                }
            }
        }
    }

    private fun refreshLocked() {
        _tracks.value = queryAll()
        _playlists.value = queryPlaylists()
        _lyricsRevision.value = _lyricsRevision.value + 1
    }

    suspend fun lyricsText(id: String): StoredLyricsText? = withContext(Dispatchers.IO) {
        helper.readableDatabase.query(
            TABLE_TRACKS,
            arrayOf(COL_LYRICS, COL_WORD_LYRICS, COL_TRANSLATION_LYRICS, COL_ROMAN_LYRICS),
            "$COL_ID = ?",
            arrayOf(id),
            null,
            null,
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) null
            else StoredLyricsText(
                lines = cursor.getString(0),
                words = cursor.getString(1),
                translation = cursor.getString(2),
                romanization = cursor.getString(3),
            )
        }
    }

    private fun queryAll(): List<Track> = helper.readableDatabase.rawQuery(
        "SELECT $COL_ID, $COL_URI, $COL_TITLE, $COL_ARTIST, $COL_ALBUM, $COL_DURATION, $COL_DISPLAY_NAME, " +
            "$COL_MIME, $COL_SIZE, $COL_COVER, $COL_LYRICS_SOURCE, $COL_LYRICS_FORMAT, $COL_LYRICS_CONFIDENCE, " +
            "$COL_LYRICS_INSTRUMENTAL, $COL_LYRICS_LOOKUP_AT, $COL_LYRICS_LOOKUP_STATUS, $COL_FAVORITE, " +
            "$COL_ADDED_AT, $COL_FOLDER_ID, $COL_FOLDER_PATH, $COL_TRACK_NUMBER, $COL_DISC_NUMBER, $COL_YEAR, " +
            "$COL_ALBUM_ARTIST, $COL_COMPOSER, $COL_COMPOSER_CREDIT, $COL_PLAY_COUNT, $COL_LAST_PLAYED, $COL_SAMPLE_RATE, $COL_BITS_PER_SAMPLE, $COL_BITRATE, " +
            "(COALESCE(length($COL_LYRICS), 0) > 0 OR COALESCE(length($COL_WORD_LYRICS), 0) > 0) AS $COL_HAS_LYRICS " +
            "FROM $TABLE_TRACKS ORDER BY $COL_ADDED_AT DESC",
        null,
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toTrack()) } }

    private fun Cursor.optionalString(column: String): String? =
        getColumnIndex(column).takeIf { it >= 0 }?.let(::getString)

    private fun Cursor.toTrack() = Track(
        id = getString(getColumnIndexOrThrow(COL_ID)),
        uri = getString(getColumnIndexOrThrow(COL_URI)),
        title = getString(getColumnIndexOrThrow(COL_TITLE)),
        artist = getString(getColumnIndexOrThrow(COL_ARTIST)),
        album = getString(getColumnIndexOrThrow(COL_ALBUM)),
        durationMs = getLong(getColumnIndexOrThrow(COL_DURATION)),
        displayName = getString(getColumnIndexOrThrow(COL_DISPLAY_NAME)),
        mimeType = getString(getColumnIndexOrThrow(COL_MIME)),
        sizeBytes = getLong(getColumnIndexOrThrow(COL_SIZE)),
        coverPath = getString(getColumnIndexOrThrow(COL_COVER)),
        lyrics = optionalString(COL_LYRICS),
        wordLyrics = optionalString(COL_WORD_LYRICS),
        translationLyrics = optionalString(COL_TRANSLATION_LYRICS),
        romanLyrics = optionalString(COL_ROMAN_LYRICS),
        storedLyrics = getColumnIndex(COL_HAS_LYRICS).takeIf { it >= 0 }?.let { getInt(it) != 0 } ?: false,
        lyricsSource = getString(getColumnIndexOrThrow(COL_LYRICS_SOURCE)),
        lyricsFormat = getString(getColumnIndexOrThrow(COL_LYRICS_FORMAT)),
        lyricsConfidence = getColumnIndexOrThrow(COL_LYRICS_CONFIDENCE).let { index ->
            if (isNull(index)) null else getDouble(index)
        },
        lyricsInstrumental = getInt(getColumnIndexOrThrow(COL_LYRICS_INSTRUMENTAL)) != 0,
        lyricsLookupAt = getLong(getColumnIndexOrThrow(COL_LYRICS_LOOKUP_AT)),
        lyricsLookupStatus = getString(getColumnIndexOrThrow(COL_LYRICS_LOOKUP_STATUS)),
        favorite = getInt(getColumnIndexOrThrow(COL_FAVORITE)) != 0,
        addedAt = getLong(getColumnIndexOrThrow(COL_ADDED_AT)),
        folderId = getString(getColumnIndexOrThrow(COL_FOLDER_ID)),
        folderPath = getString(getColumnIndexOrThrow(COL_FOLDER_PATH)),
        trackNumber = getInt(getColumnIndexOrThrow(COL_TRACK_NUMBER)),
        discNumber = getInt(getColumnIndexOrThrow(COL_DISC_NUMBER)),
        year = getInt(getColumnIndexOrThrow(COL_YEAR)),
        albumArtist = getString(getColumnIndexOrThrow(COL_ALBUM_ARTIST)),
        composer = optionalString(COL_COMPOSER)?.takeIf { it.isNotBlank() && it != "?" },
        composerCredit = optionalString(COL_COMPOSER_CREDIT),
        playCount = getInt(getColumnIndexOrThrow(COL_PLAY_COUNT)),
        lastPlayedAt = getLong(getColumnIndexOrThrow(COL_LAST_PLAYED)),
        sampleRate = getInt(getColumnIndexOrThrow(COL_SAMPLE_RATE)),
        bitsPerSample = getInt(getColumnIndexOrThrow(COL_BITS_PER_SAMPLE)),
        bitrate = getInt(getColumnIndexOrThrow(COL_BITRATE)),
    )

    private fun migrateRecoveredAudioIfNeeded() {
        val count = helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_TRACKS", null)
            .use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }
        if (count != 0L) return
        val legacy = File(context.filesDir, LEGACY_AUDIO_DIR)
        legacy.listFiles()?.filter(File::isFile)?.forEach { file ->
            runCatching { importOne(Uri.fromFile(file), persistPermission = false) }
        }
    }

    private data class Metadata(
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val picture: ByteArray?,
        val trackNumber: Int = 0,
        val discNumber: Int = 0,
        val year: Int = 0,
        val albumArtist: String? = null,
        val composer: String? = null,
        val sampleRate: Int = 0,
        val bitsPerSample: Int = 0,
        val bitrate: Int = 0,
        val hasVideo: Boolean = false,
    val embeddedLyrics: String? = null,
    )

    private class MusicDatabase(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onConfigure(db: SQLiteDatabase) {
            super.onConfigure(db)
            db.setForeignKeyConstraintsEnabled(true)
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE_TRACKS (
                    $COL_ID TEXT PRIMARY KEY NOT NULL,
                    $COL_URI TEXT UNIQUE NOT NULL,
                    $COL_TITLE TEXT NOT NULL,
                    $COL_ARTIST TEXT NOT NULL,
                    $COL_ALBUM TEXT NOT NULL,
                    $COL_DURATION INTEGER NOT NULL,
                    $COL_DISPLAY_NAME TEXT NOT NULL,
                    $COL_MIME TEXT,
                    $COL_SIZE INTEGER NOT NULL,
                    $COL_COVER TEXT,
                    $COL_LYRICS TEXT,
                    $COL_WORD_LYRICS TEXT,
                    $COL_TRANSLATION_LYRICS TEXT,
                    $COL_ROMAN_LYRICS TEXT,
                    $COL_LYRICS_SOURCE TEXT,
                    $COL_LYRICS_FORMAT TEXT,
                    $COL_LYRICS_CONFIDENCE REAL,
                    $COL_LYRICS_INSTRUMENTAL INTEGER NOT NULL DEFAULT 0,
                    $COL_LYRICS_LOOKUP_AT INTEGER NOT NULL DEFAULT 0,
                    $COL_LYRICS_LOOKUP_STATUS TEXT,
                    $COL_FAVORITE INTEGER NOT NULL DEFAULT 0,
                    $COL_ADDED_AT INTEGER NOT NULL,
                    $COL_FOLDER_ID TEXT,
                    $COL_FOLDER_PATH TEXT
                )
                """.trimIndent(),
            )
            addTagColumns(db)
            db.execSQL("CREATE INDEX tracks_artist ON $TABLE_TRACKS($COL_ARTIST)")
            db.execSQL("CREATE INDEX tracks_album ON $TABLE_TRACKS($COL_ALBUM)")
            db.execSQL("CREATE INDEX tracks_folder ON $TABLE_TRACKS($COL_FOLDER_ID)")
            createPlaylistTables(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE $TABLE_TRACKS ADD COLUMN $COL_WORD_LYRICS TEXT")
                db.execSQL("ALTER TABLE $TABLE_TRACKS ADD COLUMN $COL_TRANSLATION_LYRICS TEXT")
                db.execSQL("ALTER TABLE $TABLE_TRACKS ADD COLUMN $COL_ROMAN_LYRICS TEXT")
                db.execSQL("ALTER TABLE $TABLE_TRACKS ADD COLUMN $COL_LYRICS_SOURCE TEXT")
                db.execSQL("ALTER TABLE $TABLE_TRACKS ADD COLUMN $COL_LYRICS_FORMAT TEXT")
                db.execSQL("ALTER TABLE $TABLE_TRACKS ADD COLUMN $COL_LYRICS_CONFIDENCE REAL")
                db.execSQL("ALTER TABLE $TABLE_TRACKS ADD COLUMN $COL_LYRICS_INSTRUMENTAL INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE $TABLE_TRACKS ADD COLUMN $COL_LYRICS_LOOKUP_AT INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE $TABLE_TRACKS ADD COLUMN $COL_LYRICS_LOOKUP_STATUS TEXT")
                db.execSQL("UPDATE $TABLE_TRACKS SET $COL_LYRICS_SOURCE = 'existing', $COL_LYRICS_FORMAT = 'unknown', $COL_LYRICS_LOOKUP_STATUS = 'matched' WHERE $COL_LYRICS IS NOT NULL")
            }
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE $TABLE_TRACKS ADD COLUMN $COL_FOLDER_ID TEXT")
                db.execSQL("ALTER TABLE $TABLE_TRACKS ADD COLUMN $COL_FOLDER_PATH TEXT")
                db.execSQL("CREATE INDEX tracks_folder ON $TABLE_TRACKS($COL_FOLDER_ID)")
                createPlaylistTables(db)
            }
            if (oldVersion < 4) addTagColumns(db)
            if (oldVersion == 4) db.execSQL("ALTER TABLE $TABLE_TRACKS ADD COLUMN $COL_COMPOSER TEXT")
            if (oldVersion in 4..7) db.execSQL("ALTER TABLE $TABLE_TRACKS ADD COLUMN $COL_COMPOSER_CREDIT TEXT")
            if (oldVersion in 5..7) db.execSQL("UPDATE $TABLE_TRACKS SET $COL_COMPOSER = NULL")
        }

        private fun addTagColumns(db: SQLiteDatabase) {
            db.execSQL("ALTER TABLE $TABLE_TRACKS ADD COLUMN $COL_TRACK_NUMBER INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_TRACKS ADD COLUMN $COL_DISC_NUMBER INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_TRACKS ADD COLUMN $COL_YEAR INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_TRACKS ADD COLUMN $COL_ALBUM_ARTIST TEXT")
            db.execSQL("ALTER TABLE $TABLE_TRACKS ADD COLUMN $COL_PLAY_COUNT INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_TRACKS ADD COLUMN $COL_LAST_PLAYED INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_TRACKS ADD COLUMN $COL_SAMPLE_RATE INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_TRACKS ADD COLUMN $COL_BITS_PER_SAMPLE INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_TRACKS ADD COLUMN $COL_BITRATE INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_TRACKS ADD COLUMN $COL_TAGS_SCANNED INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_TRACKS ADD COLUMN $COL_COMPOSER TEXT")
            db.execSQL("ALTER TABLE $TABLE_TRACKS ADD COLUMN $COL_COMPOSER_CREDIT TEXT")
        }

        private fun createPlaylistTables(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE_PLAYLISTS (
                    $COL_ID TEXT PRIMARY KEY NOT NULL,
                    $COL_NAME TEXT NOT NULL,
                    $COL_SORT_ORDER INTEGER NOT NULL,
                    $COL_CREATED_AT INTEGER NOT NULL,
                    $COL_UPDATED_AT INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE $TABLE_PLAYLIST_TRACKS (
                    $COL_PLAYLIST_ID TEXT NOT NULL REFERENCES $TABLE_PLAYLISTS($COL_ID) ON DELETE CASCADE,
                    $COL_TRACK_ID TEXT NOT NULL REFERENCES $TABLE_TRACKS($COL_ID) ON DELETE CASCADE,
                    $COL_SORT_ORDER INTEGER NOT NULL,
                    PRIMARY KEY ($COL_PLAYLIST_ID, $COL_TRACK_ID)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX playlist_tracks_order ON $TABLE_PLAYLIST_TRACKS($COL_PLAYLIST_ID, $COL_SORT_ORDER)",
            )
        }
    }

    internal companion object {
        const val DATABASE_NAME = "olbyine_native.db"
        const val DATABASE_VERSION = 8
        const val TABLE_TRACKS = "tracks"
        const val TABLE_PLAYLISTS = "playlists"
        const val TABLE_PLAYLIST_TRACKS = "playlist_tracks"
        const val COL_ID = "id"
        const val COL_URI = "uri"
        const val COL_TITLE = "title"
        const val COL_ARTIST = "artist"
        const val COL_ALBUM = "album"
        const val COL_DURATION = "duration_ms"
        const val COL_DISPLAY_NAME = "display_name"
        const val COL_MIME = "mime_type"
        const val COL_SIZE = "size_bytes"
        const val COL_COVER = "cover_path"
        const val COL_LYRICS = "lyrics"
        const val COL_WORD_LYRICS = "word_lyrics"
        const val COL_TRANSLATION_LYRICS = "translation_lyrics"
        const val COL_ROMAN_LYRICS = "roman_lyrics"
        const val COL_LYRICS_SOURCE = "lyrics_source"
        const val COL_LYRICS_FORMAT = "lyrics_format"
        const val COL_LYRICS_CONFIDENCE = "lyrics_confidence"
        const val COL_LYRICS_INSTRUMENTAL = "lyrics_instrumental"
        const val COL_LYRICS_LOOKUP_AT = "lyrics_lookup_at"
        const val COL_LYRICS_LOOKUP_STATUS = "lyrics_lookup_status"
        const val COL_FAVORITE = "favorite"
        const val COL_ADDED_AT = "added_at"
        const val COL_FOLDER_ID = "folder_id"
        const val COL_FOLDER_PATH = "folder_path"
        const val COL_HAS_LYRICS = "has_lyrics_text"
        const val COL_TRACK_NUMBER = "track_number"
        const val COL_DISC_NUMBER = "disc_number"
        const val COL_YEAR = "year"
        const val COL_ALBUM_ARTIST = "album_artist"
        const val COL_COMPOSER = "composer"
        const val COL_COMPOSER_CREDIT = "composer_credit"
        const val COL_PLAY_COUNT = "play_count"
        const val COL_LAST_PLAYED = "last_played_at"
        const val COL_SAMPLE_RATE = "sample_rate"
        const val COL_BITS_PER_SAMPLE = "bits_per_sample"
        const val COL_BITRATE = "bitrate"
        const val COL_TAGS_SCANNED = "tags_scanned"
        const val IMPORT_PARALLELISM = 4
        const val IMPORT_BATCH = 64
        const val COL_PLAYLIST_ID = "playlist_id"
        const val COL_TRACK_ID = "track_id"
        const val COL_NAME = "name"
        const val COL_SORT_ORDER = "sort_order"
        const val COL_CREATED_AT = "created_at"
        const val COL_UPDATED_AT = "updated_at"
        const val LEGACY_AUDIO_DIR = "olbyine-music"
        const val MAX_COVER_BYTES = 8 * 1024 * 1024
        const val MAX_LYRIC_BYTES = 4 * 1024 * 1024

        val LYRIC_EXTENSIONS = setOf("lrc", "elrc", "yrc", "ttml")

        val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "mp4", "aac", "flac", "ogg", "oga", "opus", "wav", "wave", "aif", "aiff",
            "amr", "3gp", "ac3", "ec3", "mka", "webm", "alac", "ape", "wv", "dsf", "dff",
        )

        private val VIDEO_CONTAINERS = setOf("mp4", "3gp", "webm", "mkv", "mov", "avi", "flv", "ts", "m4v")

        fun isSupportedAudio(name: String?, mime: String?): Boolean {
            val type = mime.orEmpty().lowercase(Locale.ROOT)
            if (type.startsWith("video/")) return false
            if (type.startsWith("audio/")) return true
            val extension = name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
            return extension in AUDIO_EXTENSIONS && (extension !in VIDEO_CONTAINERS || type.isEmpty() || type == "application/octet-stream")
        }

        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
