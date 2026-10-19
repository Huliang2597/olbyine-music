package com.olbyine.music

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.olbyine.music.data.AlbumInfo
import com.olbyine.music.data.AppleMusicSettings
import com.olbyine.music.data.MotionArtwork
import com.olbyine.music.data.MotionArtworkRepository
import com.olbyine.music.data.UiStyle
import com.olbyine.music.data.ArtistInfo
import com.olbyine.music.data.ArtworkPaletteRepository
import com.olbyine.music.data.MetadataCandidate
import com.olbyine.music.data.MetadataSource
import com.olbyine.music.data.OnlineMetadataRepository
import com.olbyine.music.data.RemotePlaylist
import com.olbyine.music.data.RemoteSong
import com.olbyine.music.data.SpotifyPlaylistSummary
import com.olbyine.music.data.LyricsDocument
import com.olbyine.music.data.LyricsCandidate
import com.olbyine.music.data.LyricsMatchStatus
import com.olbyine.music.data.LyricsMatcher
import com.olbyine.music.data.LyricsSearchSource
import com.olbyine.music.data.LyricsParser
import com.olbyine.music.data.Playlist
import com.olbyine.music.data.ThemeMode
import com.olbyine.music.data.Track
import com.olbyine.music.data.UserSettings
import com.olbyine.music.playback.ExternalMediaMonitor
import com.olbyine.music.playback.ExternalNowPlaying
import com.olbyine.music.playback.PlaybackState
import com.olbyine.music.playback.UsbBitPerfectState
import com.olbyine.music.playback.UsbBitPerfectStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.DataInputStream
import java.io.FileInputStream
import java.util.UUID
import kotlin.math.abs

enum class AppDestination { NOW_PLAYING, LIBRARY, ONLINE, SETTINGS }

enum class OutputMode(val label: String, val description: String) {
    STANDARD("标准", "16 位，经系统混音器输出，兼容性最好"),
    HI_RES("高解析", "32 位浮点输出，24 位音源不会被截成 16 位"),
    USB_DRIVER("USB 独占", "Olbyine 自带驱动直连 USB DAC，绕过系统音频"),
}

fun com.olbyine.music.data.UserSettings.outputMode(): OutputMode = when {
    usbDirectDriver -> OutputMode.USB_DRIVER
    floatOutput -> OutputMode.HI_RES
    else -> OutputMode.STANDARD
}
enum class LibraryFilter { ALL, FAVORITES }
enum class LibraryViewMode { LIST, GRID, TILES }
enum class LibrarySortMode(val label: String) {
    TITLE("歌曲名"),
    ARTIST("艺术家"),
    ALBUM("专辑"),
    RECENT("最近导入"),
    PLAYS("播放次数"),
    TRACK_NUMBER("曲目编号"),
}

val AlbumOrder: Comparator<Track> = compareBy<Track> { if (it.discNumber > 0) it.discNumber else 1 }
    .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
    .thenBy { it.displayName.lowercase() }

data class OnlineLyricsUiState(
    val trackId: String? = null,
    val visible: Boolean = false,
    val selectedSource: LyricsSearchSource = LyricsSearchSource.ALL,
    val queryTitle: String = "",
    val queryArtist: String = "",
    val searching: Boolean = false,
    val downloading: Boolean = false,
    val candidates: List<LyricsCandidate> = emptyList(),
    val message: String? = null,
)

enum class PlaylistSource(val label: String) { NETEASE("网易云"), SPOTIFY("Spotify") }

data class PlaylistImportUiState(
    val visible: Boolean = false,
    val source: PlaylistSource = PlaylistSource.NETEASE,
    val spotifyConnected: Boolean = false,
    val spotifyClientId: String = "",
    val spotifyPlaylists: List<SpotifyPlaylistSummary> = emptyList(),
    val loading: Boolean = false,
    val playlist: RemotePlaylist? = null,
    val matches: Map<String, String> = emptyMap(),
    val message: String? = null,
)

data class ExternalLyricsUiState(
    val visible: Boolean = false,
    val access: Boolean = false,
    val nowPlaying: ExternalNowPlaying? = null,
    val document: LyricsDocument = LyricsDocument.Empty,
    val searching: Boolean = false,
    val message: String? = null,
    val fallbackArtworkPath: String? = null,
    val fallbackKey: String? = null,
) {
    val artworkPath: String?
        get() = nowPlaying?.artworkPath ?: fallbackArtworkPath?.takeIf { fallbackKey == nowPlaying?.key }
}

data class NeteaseLoginUiState(
    val qrContent: String? = null,
    val status: com.olbyine.music.data.QrLoginStatus? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

data class NeteaseUiState(
    val account: com.olbyine.music.data.NeteaseAccount? = null,
    val playlists: List<com.olbyine.music.data.NeteasePlaylistSummary> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null,
    val openPlaylist: com.olbyine.music.data.NeteasePlaylistSummary? = null,
    val songs: List<com.olbyine.music.data.NeteaseSong> = emptyList(),
    val songsLoading: Boolean = false,
    val songsPartial: Boolean = false,
    val liked: Set<Long> = emptySet(),
    val localMatches: Map<Long, String> = emptyMap(),
    val login: NeteaseLoginUiState? = null,
)

data class MetadataMatchUiState(
    val visible: Boolean = false,
    val trackId: String? = null,
    val queryTitle: String = "",
    val queryArtist: String = "",
    val searching: Boolean = false,
    val applying: Boolean = false,
    val candidates: List<MetadataCandidate> = emptyList(),
    val message: String? = null,
)

data class MainUiState(
    val tracks: List<Track> = emptyList(),
    val visibleTracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val currentTrack: Track? = null,
    val queue: List<Track> = emptyList(),
    val playback: PlaybackState = PlaybackState(),
    val usbBitPerfectState: UsbBitPerfectState = UsbBitPerfectState(UsbBitPerfectStatus.NO_DEVICE),
    val settings: UserSettings = UserSettings(),
    val artworkColors: List<Int> = ArtworkPaletteRepository.DefaultColors,
    val destination: AppDestination = AppDestination.LIBRARY,
    val libraryFilter: LibraryFilter = LibraryFilter.ALL,
    val libraryViewMode: LibraryViewMode = LibraryViewMode.LIST,
    val librarySortMode: LibrarySortMode = LibrarySortMode.TITLE,
    val libraryFolderId: String? = null,
    val activePlaylistId: String? = null,
    val search: String = "",
    val lyricsVisible: Boolean = false,
    val importing: Boolean = false,
    val onlineLyrics: OnlineLyricsUiState = OnlineLyricsUiState(),
    val message: String? = null,
    val trackKeys: Map<String, TrackSortKeys> = emptyMap(),
    val libraryLoaded: Boolean = false,
)

data class PlayOrigin(
    val kind: String,
    val id: String,
    val title: String = "",
    val scPlaylist: com.olbyine.music.data.ScPlaylist? = null,
    val ndAlbum: com.olbyine.music.data.NdAlbum? = null,
    val ndPlaylist: com.olbyine.music.data.NdPlaylist? = null,
)

private fun PlayOrigin.scStubPlaylist(): com.olbyine.music.data.ScPlaylist? {
    if (kind != "sc:playlist") return null
    val playlistId = id.substringAfterLast(':').toLongOrNull() ?: return null
    return com.olbyine.music.data.ScPlaylist(
        id = playlistId,
        title = title.ifBlank { "歌单" },
        user = com.olbyine.music.data.ScUser(0, "", null),
        artworkUrl = null,
        trackCount = 0,
        album = false,
    )
}

private fun PlayOrigin.ndStubAlbum(): com.olbyine.music.data.NdAlbum? {
    if (kind != "nd:album" || id.isBlank()) return null
    return com.olbyine.music.data.NdAlbum(
        id = id,
        name = title.ifBlank { "专辑" },
        artist = "",
        artistId = null,
        coverArt = null,
        songCount = 0,
        year = 0,
        durationSec = 0L,
        starred = false,
    )
}

private fun PlayOrigin.ndStubPlaylist(): com.olbyine.music.data.NdPlaylist? {
    if (kind != "nd:playlist" || id.isBlank()) return null
    return com.olbyine.music.data.NdPlaylist(id = id, name = title.ifBlank { "歌单" }, songCount = 0, coverArt = null, owner = "")
}

data class LibraryReveal(
    val trackId: String,
    val kind: String,
    val id: String,
)

data class OnlineReveal(
    val provider: String,
    val trackId: String,
    val title: String = "",
    val artist: String = "",
    val songId: Long = 0,
    val scTrackId: Long = 0,
    val scPlaylist: com.olbyine.music.data.ScPlaylist? = null,
    val ndAlbum: com.olbyine.music.data.NdAlbum? = null,
    val ndPlaylist: com.olbyine.music.data.NdPlaylist? = null,
)

data class CollectionResumePoint(
    val songId: Long,
    val title: String,
    val positionMs: Long,
    val at: Long,
)

data class TrackSortKeys(val title: String, val artist: String, val album: String) {
    fun forMode(mode: LibrarySortMode): String = when (mode) {
        LibrarySortMode.ARTIST -> artist
        LibrarySortMode.ALBUM -> album
        else -> title
    }
}

object SortKeys {
    private val memo = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val transliterator by lazy { android.icu.text.Transliterator.getInstance("Any-Latin; Latin-ASCII") }

    fun of(value: String): String = memo[value] ?: synchronized(this) {
        memo.getOrPut(value) { transliterator.transliterate(value.trim()).lowercase(java.util.Locale.ROOT) }
    }

    fun cachedOrPlain(value: String): String = memo[value] ?: value.trim().lowercase(java.util.Locale.ROOT)

    fun letter(key: String): String = key.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' }?.toString() ?: "#"
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OlbyineApplication
    private val library = app.musicLibrary
    private val playback = app.playback
    private val settingsRepository = app.settings
    private val onlineLyricsRepository = app.onlineLyrics
    private val artworkPaletteRepository = ArtworkPaletteRepository()
    private val savedPlayback = application.getSharedPreferences("native_playback", Application.MODE_PRIVATE)
    private val libraryPreferences = application.getSharedPreferences("native_library_view", Application.MODE_PRIVATE)

    private val _trackReveal = MutableStateFlow<LibraryReveal?>(null)
    val trackReveal: StateFlow<LibraryReveal?> = _trackReveal
    private val _playOrigin = MutableStateFlow<PlayOrigin?>(null)
    val playOrigin: StateFlow<PlayOrigin?> = _playOrigin
    private val _onlineReveal = MutableStateFlow<OnlineReveal?>(null)
    val onlineReveal: StateFlow<OnlineReveal?> = _onlineReveal
    private val _collectionResume = MutableStateFlow<Map<String, CollectionResumePoint>>(emptyMap())
    val collectionResume: StateFlow<Map<String, CollectionResumePoint>> = _collectionResume
    private val _activeCollection = MutableStateFlow<String?>(null)
    val activeCollection: StateFlow<String?> = _activeCollection
    private var activeQueueIds: Set<String> = emptySet()
    private var activeSongByTrack: Map<String, Long> = emptyMap()
    private var activeCollectionAt = 0L
    private var resumeWriteAt = 0L

    private val destination = MutableStateFlow(AppDestination.LIBRARY)
    private var returnDestination = AppDestination.LIBRARY
    private val filter = MutableStateFlow(LibraryFilter.ALL)
    private val search = MutableStateFlow("")
    private val viewMode = MutableStateFlow(enumPreference(KEY_VIEW_MODE, LibraryViewMode.LIST))
    private val sortMode = MutableStateFlow(enumPreference(KEY_SORT_MODE, LibrarySortMode.TITLE))
    private val folderId = MutableStateFlow<String?>(null)
    private val playlistId = MutableStateFlow<String?>(null)
    private val lyricsVisible = MutableStateFlow(false)
    private val importing = MutableStateFlow(false)
    private val onlineLyrics = MutableStateFlow(OnlineLyricsUiState())
    private val artworkColors = MutableStateFlow(ArtworkPaletteRepository.DefaultColors)
    private val message = MutableStateFlow<String?>(null)
    private val _externalLyrics = MutableStateFlow(ExternalLyricsUiState())
    val externalLyrics: StateFlow<ExternalLyricsUiState> = _externalLyrics
    private val automaticAttempts = mutableSetOf<String>()
    private val automaticLyricsQueue = Channel<String>(Channel.BUFFERED)
    private var manualLyricsJob: Job? = null
    private var manualDownloadJob: Job? = null
    private var manualLyricsRequest = 0L
    private var fontImportJob: Job? = null
    private val onlineTracks = MutableStateFlow<Map<String, Track>>(emptyMap())

    private val indexedTracks = combine(library.tracks, library.loaded, onlineTracks) { tracks, loaded, online ->
        IndexedTracks(
            tracks = tracks,
            byId = online + tracks.associateBy(Track::id),
            keys = tracks.associate { track ->
                track.id to TrackSortKeys(SortKeys.of(track.title), SortKeys.of(track.artist), SortKeys.of(track.album))
            },
            loaded = loaded,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), IndexedTracks(emptyList(), emptyMap(), emptyMap(), false))

    private val visibleTracks = combine(
        library.tracks,
        library.playlists,
        combine(filter, search) { selectedFilter, query -> selectedFilter to query.trim() },
        combine(sortMode, folderId, playlistId) { sort, folder, playlist ->
            Triple(sort, folder, playlist)
        },
    ) { tracks, playlists, query, selection ->
        val (selectedFilter, normalizedQuery) = query
        val (sort, folder, playlist) = selection
        val chosen = playlists.firstOrNull { it.id == playlist }
        val positions = chosen?.trackIds?.withIndex()?.associate { it.value to it.index }.orEmpty()
        tracks.filter { track ->
            (selectedFilter != LibraryFilter.FAVORITES || track.favorite) &&
                (folder == null || track.folderId == folder) &&
                (chosen == null || track.id in positions) &&
                (normalizedQuery.isEmpty() || listOf(track.title, track.artist, track.album)
                    .any { it.contains(normalizedQuery, ignoreCase = true) })
        }.let { filtered ->
            if (chosen != null) filtered.sortedBy { positions[it.id] ?: Int.MAX_VALUE }
            else sortTracks(filtered, sort)
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val albums: StateFlow<List<com.olbyine.music.data.Album>> = library.tracks
        .map { tracks -> buildAlbums(tracks) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val importProgress: StateFlow<com.olbyine.music.data.ImportProgress?> = library.importProgress

    val uiState: StateFlow<MainUiState> = combine(
        combine(indexedTracks, library.playlists, playback.state, playback.usbBitPerfectState, settingsRepository.settings) { tracks, playlists, player, usb, settings ->
            LibraryCore(tracks.tracks, tracks.byId, tracks.keys, tracks.loaded, playlists, player, usb, settings)
        },
        combine(destination, filter, search) { destination, filter, search ->
            Triple(destination, filter, search)
        },
        combine(viewMode, sortMode, folderId, playlistId) { view, sort, folder, playlist ->
            LibrarySelection(view, sort, folder, playlist)
        },
        combine(lyricsVisible, importing, onlineLyrics, message, artworkColors) { lyrics, importing, online, message, colors ->
            TransientState(lyrics, importing, online, message, colors)
        },
        visibleTracks,
    ) { core, navigation, selection, transient, visible ->
        val tracks = core.tracks
        val player = core.player
        val settings = core.settings
        val (currentDestination, currentFilter, query) = navigation
        val tracksById = core.tracksById
        MainUiState(
            tracks = tracks,
            visibleTracks = visible,
            playlists = core.playlists,
            currentTrack = player.currentMediaId?.let { id -> tracksById[id] ?: playerTrack(id, player) },
            queue = player.queueIds.mapNotNull(tracksById::get),
            playback = player,
            usbBitPerfectState = core.usb,
            settings = settings,
            artworkColors = transient.artworkColors,
            destination = currentDestination,
            libraryFilter = currentFilter,
            libraryViewMode = selection.view,
            librarySortMode = selection.sort,
            libraryFolderId = selection.folder,
            activePlaylistId = selection.playlist,
            search = query,
            lyricsVisible = transient.lyricsVisible,
            importing = transient.importing,
            onlineLyrics = transient.onlineLyrics,
            message = transient.message,
            trackKeys = core.keys,
            libraryLoaded = core.loaded,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), MainUiState())

    val parsedLyrics: StateFlow<LyricsDocument> = app.lyricsHub.document

    init {
        restorePlaybackQueue()
        persistPlaybackPosition()
        processAutomaticLyricsQueue()
        observeArtworkColors()
        viewModelScope.launch { playback.state.map { it.currentMediaId }.distinctUntilChanged().collect { app.lyricsHub.setCurrent(it) } }
        viewModelScope.launch {
            playback.state.collect { state ->
                val key = _activeCollection.value ?: return@collect
                val ids = activeQueueIds
                if (ids.isNotEmpty() && state.queueIds.isNotEmpty() && ids.none { it in state.queueIds } &&
                    System.currentTimeMillis() - activeCollectionAt > 2_500L
                ) {
                    _activeCollection.value = null
                    activeQueueIds = emptySet()
                    activeSongByTrack = emptyMap()
                    return@collect
                }
                val mediaId = state.currentMediaId ?: return@collect
                val songId = activeSongByTrack[mediaId] ?: return@collect
                val previous = _collectionResume.value[key]
                val changed = previous?.songId != songId
                val now = System.currentTimeMillis()
                if (!changed && now - resumeWriteAt < 3_000L) return@collect
                resumeWriteAt = now
                _collectionResume.value = _collectionResume.value + (key to CollectionResumePoint(
                    songId = songId,
                    title = state.currentTitle ?: previous?.title.orEmpty(),
                    positionMs = state.positionMs.coerceAtLeast(0L),
                    at = now,
                ))
            }
        }
    }

    fun navigateTo(value: AppDestination) {
        if (value == AppDestination.NOW_PLAYING && destination.value != AppDestination.NOW_PLAYING) {
            returnDestination = destination.value
        } else if (value != AppDestination.NOW_PLAYING) {
            returnDestination = value
        }
        destination.value = value
        if (value != AppDestination.NOW_PLAYING) lyricsVisible.value = false
    }

    fun collapseNowPlaying() {
        val target = returnDestination.takeIf { it != AppDestination.NOW_PLAYING } ?: AppDestination.LIBRARY
        destination.value = target
        lyricsVisible.value = false
    }

    fun setSearch(value: String) {
        search.value = value
    }

    fun setLibraryFilter(value: LibraryFilter) {
        filter.value = value
    }

    fun setLibraryViewMode(value: LibraryViewMode) {
        viewMode.value = value
        libraryPreferences.edit().putString(KEY_VIEW_MODE, value.name).apply()
    }

    fun setLibrarySortMode(value: LibrarySortMode) {
        sortMode.value = value
        libraryPreferences.edit().putString(KEY_SORT_MODE, value.name).apply()
    }

    fun setPlayOrigin(origin: PlayOrigin?) {
        _playOrigin.value = origin
        persistPlayOrigin(origin)
    }

    private fun persistPlayOrigin(origin: PlayOrigin?) {
        val editor = savedPlayback.edit()
        if (origin == null) {
            editor.remove(KEY_ORIGIN_KIND).remove(KEY_ORIGIN_ID).remove(KEY_ORIGIN_TITLE).apply()
            return
        }
        editor.putString(KEY_ORIGIN_KIND, origin.kind)
            .putString(KEY_ORIGIN_ID, origin.id)
            .putString(KEY_ORIGIN_TITLE, origin.title)
            .apply()
    }

    private fun readPersistedOrigin(): PlayOrigin? {
        val kind = savedPlayback.getString(KEY_ORIGIN_KIND, null)?.takeIf(String::isNotBlank) ?: return null
        val id = savedPlayback.getString(KEY_ORIGIN_ID, null).orEmpty()
        return PlayOrigin(kind = kind, id = id, title = savedPlayback.getString(KEY_ORIGIN_TITLE, null).orEmpty())
    }

    fun revealInLibrary(track: Track) {
        val origin = _playOrigin.value
        if (!track.isOnline) {
            val kind = origin?.kind?.takeIf { it == "folder" || it == "playlist" || it == "album" }
            val id = origin?.id.orEmpty()
            _trackReveal.value = LibraryReveal(track.id, kind ?: "folder", if (kind == null) "" else id)
            navigateTo(AppDestination.LIBRARY)
            return
        }
        when {
            track.id.startsWith("ncm:") -> {
                val songId = track.id.removePrefix("ncm:").toLongOrNull() ?: return
                _onlineReveal.value = OnlineReveal(provider = "netease", trackId = track.id, songId = songId, title = track.title)
                val originKind = origin?.kind
                val originId = origin?.id?.toLongOrNull()
                if ((originKind == "ncm:PLAYLIST" || originKind == "ncm:ALBUM" || originKind == "ncm:CHART" || originKind == "ncm:DAILY") && originId != null) {
                    openNeteasePlaylist(
                        com.olbyine.music.data.NeteasePlaylistSummary(
                            originId,
                            origin?.title.orEmpty(),
                            null,
                            0,
                            "",
                            liked = false,
                            own = false,
                            kind = runCatching { com.olbyine.music.data.NeteaseCollectionKind.valueOf(originKind.removePrefix("ncm:")) }
                                .getOrDefault(com.olbyine.music.data.NeteaseCollectionKind.PLAYLIST),
                        ),
                    )
                    return
                }
                viewModelScope.launch {
                    val albumId = runCatching { netease.song(songId)?.albumId?.takeIf { it > 0 } }.getOrNull()
                    if (albumId == null) {
                        _onlineReveal.value = null
                        message.value = "这首歌没有可打开的专辑页"
                    }
                    if (albumId != null) {
                        openNeteasePlaylist(
                            com.olbyine.music.data.NeteasePlaylistSummary(
                                albumId,
                                "专辑",
                                null,
                                0,
                                "",
                                liked = false,
                                own = false,
                                kind = com.olbyine.music.data.NeteaseCollectionKind.ALBUM,
                            ),
                        )
                    }
                }
            }
            track.id.startsWith("sc:") -> {
                val scId = track.id.removePrefix("sc:").toLongOrNull() ?: return
                _onlineReveal.value = OnlineReveal(
                    provider = "soundcloud",
                    trackId = track.id,
                    scTrackId = scId,
                    title = track.title,
                    scPlaylist = origin?.scPlaylist ?: origin?.scStubPlaylist(),
                )
            }
            else -> _onlineReveal.value = OnlineReveal(
                provider = "navidrome",
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                ndAlbum = origin?.ndAlbum ?: origin?.ndStubAlbum(),
                ndPlaylist = origin?.ndPlaylist ?: origin?.ndStubPlaylist(),
            )
        }
    }

    fun clearOnlineReveal() {
        _onlineReveal.value = null
    }

    fun clearTrackReveal() {
        _trackReveal.value = null
    }

    fun setLibraryFolderId(value: String?) {
        folderId.value = value
        if (value != null) playlistId.value = null
    }

    fun setActivePlaylistId(value: String?) {
        playlistId.value = value
        if (value != null) folderId.value = null
    }

    fun createPlaylist(name: String) = libraryAction {
        playlistId.value = library.createPlaylist(name).id
        folderId.value = null
    }

    fun renamePlaylist(id: String, name: String) = libraryAction { library.renamePlaylist(id, name) }

    fun deletePlaylist(id: String) = libraryAction {
        library.deletePlaylist(id)
        if (playlistId.value == id) playlistId.value = null
    }

    fun addToPlaylist(id: String, trackId: String) = libraryAction {
        library.addToPlaylist(id, trackId)
    }

    fun removeFromPlaylist(id: String, trackId: String) = libraryAction {
        library.removeFromPlaylist(id, trackId)
    }

    fun reorderPlaylistTracks(id: String, orderedTrackIds: List<String>) = libraryAction {
        library.reorderPlaylistTracks(id, orderedTrackIds)
    }

    fun reorderPlaylists(orderedIds: List<String>) = libraryAction {
        library.reorderPlaylists(orderedIds)
    }

    private fun libraryAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            try { action() }
            catch (error: Throwable) {
                if (error is CancellationException) throw error
                message.value = "歌单操作失败：${error.message ?: "请重试"}"
            }
        }
    }

    private inline fun <reified T : Enum<T>> enumPreference(key: String, default: T): T =
        runCatching { enumValueOf<T>(libraryPreferences.getString(key, null) ?: default.name) }
            .getOrDefault(default)

    fun setLyricPreviewEnabled(value: Boolean) = settingsRepository.setLyricPreviewEnabled(value)

    fun setLyricFontWeight(value: Int) = settingsRepository.setLyricFontWeight(value)

    fun setLiquidGlassEnabled(value: Boolean) = settingsRepository.setLiquidGlassEnabled(value)

    fun setDynamicAccentEnabled(value: Boolean) = settingsRepository.setDynamicAccentEnabled(value)

    fun setUsbBitPerfectEnabled(value: Boolean) = settingsRepository.setUsbBitPerfectEnabled(value)

    fun setPlayerGlassButtons(value: Boolean) = settingsRepository.setPlayerGlassButtons(value)

    fun setGlassPopups(value: Boolean) = settingsRepository.setGlassPopups(value)

    fun setGlassTuning(highlight: Float, blur: Float, opacity: Float, refraction: Float, gyro: Boolean) =
        settingsRepository.setGlassTuning(highlight, blur, opacity, refraction, gyro)

    fun setCustomBackgroundBlur(value: Float) = settingsRepository.setCustomBackgroundBlur(value)

    fun setCustomBackgroundDim(value: Float) = settingsRepository.setCustomBackgroundDim(value)

    fun setCustomBackgroundOnPlayer(value: Boolean) = settingsRepository.setCustomBackgroundOnPlayer(value)

    fun importCustomBackground(uri: Uri) {
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { copyBackground(uri) }
                val previous = settingsRepository.settings.value.customBackgroundPath
                settingsRepository.setCustomBackground(file.absolutePath)
                if (previous != null && previous != file.absolutePath) {
                    withContext(Dispatchers.IO) { privateBackground(previous)?.delete() }
                }
                message.value = "已设置自定义背景"
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                message.value = "背景导入失败：图片无效或无法读取"
            }
        }
    }

    fun clearCustomBackground() {
        val previous = settingsRepository.settings.value.customBackgroundPath ?: return
        settingsRepository.setCustomBackground(null)
        viewModelScope.launch(Dispatchers.IO) { privateBackground(previous)?.delete() }
    }

    private fun privateBackground(path: String): File? {
        val directory = File(getApplication<Application>().filesDir, "backgrounds").canonicalFile
        return File(path).canonicalFile.takeIf { it.parentFile == directory }
    }

    private fun copyBackground(uri: Uri): File {
        val resolver = getApplication<Application>().contentResolver
        val source = android.graphics.ImageDecoder.createSource(resolver, uri)
        val bitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
            val scale = (2160f / longest).coerceAtMost(1f)
            decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val directory = File(getApplication<Application>().filesDir, "backgrounds").apply { mkdirs() }
        val file = File(directory, "bg-${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return file
    }

    fun setPlayMode(shuffle: Boolean, repeatMode: Int) = playback.setPlayMode(shuffle, repeatMode)

    fun setMotionCover(value: Boolean) = settingsRepository.setMotionCover(value)
    fun setMotionBackground(value: Boolean) = settingsRepository.setMotionBackground(value)
    fun setBeatPulse(value: Boolean) = settingsRepository.setBeatPulse(value)

    fun setLyricBlur(value: Boolean) = settingsRepository.setLyricBlur(value)
    fun setLyricTranslation(value: Boolean) = settingsRepository.setLyricTranslation(value)
    fun setLyricRomanization(value: Boolean) = settingsRepository.setLyricRomanization(value)
    fun setLyricSubScale(value: Float) = settingsRepository.setLyricSubScale(value)
    fun setFloatingLyrics(value: Boolean) = settingsRepository.setFloatingLyrics(value)
    fun setFloatingLyricsMode(value: String) = settingsRepository.setFloatingLyricsMode(value)
    fun setFloatingLyricsLocked(value: Boolean) = settingsRepository.setFloatingLyricsLocked(value)
    fun setFloatingLyricsColor(value: Int) = settingsRepository.setFloatingLyricsColor(value)
    fun setLyricDoubleTapSeek(value: Boolean) = settingsRepository.setLyricDoubleTapSeek(value)
    fun setLyricMvStyle(value: String) = settingsRepository.setLyricMvStyle(value)
    fun setShowSourceSwitch(value: Boolean) = settingsRepository.setShowSourceSwitch(value)
    fun setBackgroundStyle(value: String) = settingsRepository.setBackgroundStyle(value)
    fun setLayoutMode(value: String) = settingsRepository.setLayoutMode(value)
    fun setAlbumStyle(value: String) = settingsRepository.setAlbumStyle(value)
    fun setUiStyle(value: UiStyle) = settingsRepository.setUiStyle(value)

    val usbDriverState: StateFlow<com.olbyine.music.playback.usb.UsbDriverState> =
        com.olbyine.music.playback.usb.UsbAudioDriver.state

    fun setUsbDirectDriver(value: Boolean) = settingsRepository.setUsbDirectDriver(value)
    fun setImportMinDuration(seconds: Int) = settingsRepository.setImportMinDuration(seconds)
    fun setImportSkipDuplicates(value: Boolean) = settingsRepository.setImportSkipDuplicates(value)
    fun setAutoCompleteMetadata(value: Boolean) = settingsRepository.setAutoCompleteMetadata(value)
    fun setHapticArea(area: Int, enabled: Boolean) = settingsRepository.setHapticArea(area, enabled)
    fun setArtworkBackdrop(value: Boolean) = settingsRepository.setArtworkBackdrop(value)
    fun setPlayerFx(bit: Int, enabled: Boolean) = settingsRepository.setPlayerFx(bit, enabled)
    fun setNotificationLyrics(value: Boolean) = settingsRepository.setNotificationLyrics(value)
    fun setBluetoothLyrics(value: Boolean) = settingsRepository.setBluetoothLyrics(value)
    fun setTransitionMode(value: com.olbyine.music.data.TransitionMode) = settingsRepository.setTransitionMode(value)
    fun setCrossfadeSeconds(value: Int) = settingsRepository.setCrossfadeSeconds(value)
    fun setFloatOutput(value: Boolean) = settingsRepository.setFloatOutput(value)
    fun setEqEnabled(value: Boolean) = settingsRepository.setEqEnabled(value)
    fun setEqPreset(value: String) = settingsRepository.setEqPreset(value)
    fun setEqBand(index: Int, gain: Float) = settingsRepository.setEqBand(index, gain)
    fun setEqPreamp(value: Float) = settingsRepository.setEqPreamp(value)
    fun setBalance(value: Float) = settingsRepository.setBalance(value)
    fun setMonoAudio(value: Boolean) = settingsRepository.setMonoAudio(value)
    fun setUsbBitDepth(value: Int) = settingsRepository.setUsbBitDepth(value)
    fun setUsbDither(value: Boolean) = settingsRepository.setUsbDither(value)
    fun setUsbBufferMs(value: Int) = settingsRepository.setUsbBufferMs(value)
    fun setUsbVolumeMode(value: com.olbyine.music.data.UsbVolumeMode) = settingsRepository.setUsbVolumeMode(value)
    fun setUsbMaxVolume(value: Int) = settingsRepository.setUsbMaxVolume(value)
    fun setNeteaseOnline(value: Boolean) = settingsRepository.setNeteaseOnline(value)
    fun setNeteaseQuality(value: String) = settingsRepository.setNeteaseQuality(value)

    fun switchNeteaseQuality(value: String) {
        settingsRepository.setNeteaseQuality(value)
        if (uiState.value.currentTrack?.id?.startsWith("ncm:") == true) playback.reloadCurrent(value)
        message.value = "已切换为${com.olbyine.music.data.NeteaseClient.QUALITIES.firstOrNull { it.first == value }?.second ?: value}"
    }

    fun setOutputMode(mode: OutputMode) {
        settingsRepository.setUsbDirectDriver(mode == OutputMode.USB_DRIVER)
        settingsRepository.setFloatOutput(mode == OutputMode.HI_RES)
    }

    fun setUsbVolume(value: Int) = com.olbyine.music.playback.usb.UsbAudioDriver.setVolume(value)

    fun retryUsbDriver() = com.olbyine.music.playback.usb.UsbAudioDriver.attach()

    val appleMusicSettings: StateFlow<AppleMusicSettings> = app.appleMusic.settings

    fun setAppleStorefront(value: String) = app.appleMusic.setStorefront(value)

    suspend fun verifyAppleToken(): String = app.appleMusic.verifyToken().fold(
        onSuccess = { "令牌有效 · 账号地区 ${it.storefront.uppercase()} · 语言 ${it.language}" },
        onFailure = { it.message ?: "验证失败" },
    )

    fun setAppleUserToken(value: String?) {
        app.appleMusic.setUserToken(value)
        message.value = if (value.isNullOrBlank()) "已移除 Apple Music 令牌" else "已保存 Apple Music 令牌"
    }

    private val motionRepository by lazy { MotionArtworkRepository(getApplication(), app.appleMusic) }

    private data class MotionQuery(val title: String, val artist: String, val album: String, val durationMs: Long)

    @OptIn(ExperimentalCoroutinesApi::class)
    val motionArtwork: StateFlow<MotionArtwork?> = combine(
        library.tracks,
        playback.state.map { it.currentMediaId }.distinctUntilChanged(),
        _externalLyrics,
        settingsRepository.settings,
    ) { tracks, currentId, external, settings ->
        if (!settings.motionCover && !settings.motionBackground) return@combine null
        if (external.visible) {
            external.nowPlaying?.let { MotionQuery(it.title, it.artist, it.album, it.durationMs) }
        } else {
            tracks.firstOrNull { it.id == currentId }?.let {
                MotionQuery(it.title, it.artist.takeUnless { a -> a == "未知艺术家" }.orEmpty(), it.album.takeUnless { a -> a == "未知专辑" }.orEmpty(), it.durationMs)
            }
        }
    }
        .distinctUntilChanged()
        .transformLatest { query ->
            emit(null)
            if (query != null) emit(runCatching { motionRepository.forTrack(query.title, query.artist, query.album, query.durationMs) }.getOrNull())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    private val _playlistImport = MutableStateFlow(PlaylistImportUiState())
    val playlistImport: StateFlow<PlaylistImportUiState> = _playlistImport
    private var playlistImportJob: Job? = null

    private val spotify get() = app.spotify

    fun openPlaylistImport(source: PlaylistSource = PlaylistSource.NETEASE) {
        _playlistImport.value = PlaylistImportUiState(
            visible = true,
            source = source,
            spotifyConnected = spotify.connected.value,
            spotifyClientId = spotify.clientId,
        )
        if (source == PlaylistSource.SPOTIFY && spotify.connected.value) loadSpotifyPlaylists()
    }

    fun selectPlaylistSource(source: PlaylistSource) {
        playlistImportJob?.cancel()
        _playlistImport.value = _playlistImport.value.copy(source = source, playlist = null, matches = emptyMap(), message = null, loading = false)
        if (source == PlaylistSource.SPOTIFY && spotify.connected.value && _playlistImport.value.spotifyPlaylists.isEmpty()) loadSpotifyPlaylists()
    }

    fun spotifyLoginUrl(clientId: String): Uri? = try {
        spotify.authorizeUrl(clientId).also { _playlistImport.value = _playlistImport.value.copy(spotifyClientId = clientId.trim(), message = null) }
    } catch (error: IllegalArgumentException) {
        _playlistImport.value = _playlistImport.value.copy(message = error.message)
        null
    }

    fun completeSpotifyLogin(redirect: Uri) {
        viewModelScope.launch {
            try {
                spotify.completeLogin(redirect)
                message.value = "已连接 Spotify"
                openPlaylistImport(PlaylistSource.SPOTIFY)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                message.value = error.message ?: "Spotify 连接失败"
            }
        }
    }

    fun logoutSpotify() {
        spotify.logout()
        _playlistImport.value = _playlistImport.value.copy(spotifyConnected = false, spotifyPlaylists = emptyList(), playlist = null, matches = emptyMap())
    }

    private fun loadSpotifyPlaylists() {
        playlistImportJob?.cancel()
        _playlistImport.value = _playlistImport.value.copy(loading = true, message = null)
        playlistImportJob = viewModelScope.launch {
            try {
                val lists = spotify.myPlaylists()
                _playlistImport.value = _playlistImport.value.copy(loading = false, spotifyPlaylists = lists, spotifyConnected = true)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _playlistImport.value = _playlistImport.value.copy(
                    loading = false,
                    spotifyConnected = spotify.connected.value,
                    message = error.message ?: "读取 Spotify 歌单失败",
                )
            }
        }
    }

    fun fetchNeteasePlaylist(input: String) {
        playlistImportJob?.cancel()
        _playlistImport.value = _playlistImport.value.copy(loading = true, message = null, playlist = null, matches = emptyMap())
        playlistImportJob = viewModelScope.launch {
            try {
                val playlist = when (_playlistImport.value.source) {
                    PlaylistSource.NETEASE -> metadataRepository.neteasePlaylist(input)
                    PlaylistSource.SPOTIFY -> spotify.playlist(input)
                }
                val matches = withContext(Dispatchers.Default) { matchSongs(playlist.songs, uiState.value.tracks) }
                _playlistImport.value = _playlistImport.value.copy(loading = false, playlist = playlist, matches = matches)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _playlistImport.value = _playlistImport.value.copy(
                    loading = false,
                    message = error.message?.takeIf { it.isNotBlank() && !it.startsWith("HTTP") } ?: "获取歌单失败，请检查链接与网络",
                )
            }
        }
    }

    fun confirmPlaylistImport() {
        val current = _playlistImport.value
        val playlist = current.playlist ?: return
        val ids = playlist.songs.mapNotNull { current.matches[it.id] }
        if (ids.isEmpty()) {
            _playlistImport.value = current.copy(message = "本地曲库里没有这个歌单的歌曲")
            return
        }
        viewModelScope.launch {
            try {
                library.createPlaylistWithTracks("${current.source.label} · ${playlist.name}".take(80), ids)
                _playlistImport.value = PlaylistImportUiState()
                message.value = "已导入歌单：${ids.distinct().size}/${playlist.songs.size} 首在本地找到"
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _playlistImport.value = current.copy(message = "创建歌单失败：${error.message ?: "未知错误"}")
            }
        }
    }

    fun dismissPlaylistImport() {
        playlistImportJob?.cancel()
        _playlistImport.value = PlaylistImportUiState()
    }

    private fun matchSongs(songs: List<RemoteSong>, tracks: List<Track>): Map<String, String> {
        fun key(value: String) = OnlineMetadataRepository.normalize(value)
        fun bare(value: String) = key(value.replace(Regex("[(（\\[【].*?[)）\\]】]"), ""))
        val byTitle = tracks.groupBy { bare(it.title) }
        val result = HashMap<String, String>()
        for (song in songs) {
            val candidates = byTitle[bare(song.title)].orEmpty()
            if (candidates.isEmpty()) continue
            val artists = song.artists.map(::key).filter(String::isNotBlank)
            val best = candidates.maxByOrNull { track ->
                val local = key(track.artist)
                var score = 0
                if (key(track.title) == key(song.title)) score += 2
                if (artists.any { it.isNotBlank() && (local.contains(it) || it.contains(local)) }) score += 4
                if (song.durationMs > 0 && abs(track.durationMs - song.durationMs) < 4_000) score += 1
                score
            } ?: continue
            val local = key(best.artist)
            val artistOk = artists.isEmpty() || local.isBlank() || best.artist == "未知艺术家" ||
                artists.any { local.contains(it) || it.contains(local) }
            if (artistOk) result[song.id] = best.id
        }
        return result
    }

    private val externalMonitor by lazy { ExternalMediaMonitor(getApplication()) }
    private var externalJob: Job? = null
    private var externalLyricsJob: Job? = null

    fun openExternalLyrics() {
        val access = externalMonitor.hasAccess()
        _externalLyrics.value = _externalLyrics.value.copy(visible = true, access = access)
        if (!access || externalJob?.isActive == true) return
        if (!externalMonitor.start()) {
            _externalLyrics.value = _externalLyrics.value.copy(access = false)
            return
        }
        externalJob = viewModelScope.launch {
            var lastKey: String? = null
            externalMonitor.state.collect { playing ->
                _externalLyrics.value = _externalLyrics.value.copy(nowPlaying = playing)
                val key = playing?.key
                if (key != lastKey) {
                    lastKey = key
                    loadExternalLyrics(playing)
                    findExternalCover(playing)
                }
            }
        }
    }

    fun refreshExternalAccess() {
        if (!_externalLyrics.value.visible) return
        if (externalMonitor.hasAccess() && externalJob?.isActive != true) openExternalLyrics()
        else _externalLyrics.value = _externalLyrics.value.copy(access = externalMonitor.hasAccess())
    }

    private var externalCoverJob: Job? = null

    private fun findExternalCover(playing: ExternalNowPlaying?) {
        externalCoverJob?.cancel()
        if (playing == null || playing.title.isBlank()) return
        externalCoverJob = viewModelScope.launch {
            delay(1_500)
            val current = _externalLyrics.value
            if (current.nowPlaying?.key != playing.key || current.artworkPath != null) return@launch
            val path = runCatching {
                withContext(Dispatchers.IO) {
                    val candidates = metadataRepository.search(playing.title, playing.artist, playing.album)
                    val best = candidates.firstOrNull { candidate ->
                        candidate.coverUrl != null &&
                            OnlineMetadataRepository.normalize(candidate.title) == OnlineMetadataRepository.normalize(playing.title)
                    } ?: candidates.firstOrNull { it.coverUrl != null } ?: return@withContext null
                    val bytes = metadataRepository.downloadCover(best)
                    val directory = File(getApplication<Application>().cacheDir, "external-covers").apply { mkdirs() }
                    directory.listFiles()?.sortedBy { it.lastModified() }?.dropLast(20)?.forEach { it.delete() }
                    File(directory, "cover-${OnlineMetadataRepository.hash(playing.key)}.jpg").apply { writeBytes(bytes) }.absolutePath
                }
            }.getOrNull() ?: return@launch
            if (_externalLyrics.value.nowPlaying?.key == playing.key) {
                _externalLyrics.value = _externalLyrics.value.copy(fallbackArtworkPath = path, fallbackKey = playing.key)
            }
        }
    }

    fun closeExternalLyrics() {
        externalJob?.cancel()
        externalLyricsJob?.cancel()
        externalCoverJob?.cancel()
        externalMonitor.stop()
        _externalLyrics.value = ExternalLyricsUiState()
    }

    override fun onCleared() {
        if (externalJob != null) externalMonitor.stop()
        super.onCleared()
    }

    fun externalTogglePlay() = externalMonitor.togglePlay()
    fun externalNext() { externalMonitor.next() }
    fun externalPrevious() { externalMonitor.previous() }
    fun externalSeek(positionMs: Long) { externalMonitor.seekTo(positionMs) }

    private fun loadExternalLyrics(playing: ExternalNowPlaying?) {
        externalLyricsJob?.cancel()
        if (playing == null || playing.title.isBlank()) {
            _externalLyrics.value = _externalLyrics.value.copy(document = LyricsDocument.Empty, searching = false, message = null)
            return
        }
        _externalLyrics.value = _externalLyrics.value.copy(document = LyricsDocument.Empty, searching = true, message = null)
        externalLyricsJob = viewModelScope.launch {
            try {
                val local = uiState.value.tracks.firstOrNull {
                    it.hasLyrics && it.title.equals(playing.title, ignoreCase = true) &&
                        (playing.artist.isBlank() || it.artist.contains(playing.artist.substringBefore('/').trim(), ignoreCase = true))
                }
                val document = if (local != null) {
                    val text = library.lyricsText(local.id)
                    text?.let {
                        withContext(Dispatchers.Default) {
                            LyricsParser.parseBundle(it.lines, it.words, it.translation, it.romanization)
                                .copy(rawTtml = listOfNotNull(it.words, it.lines).firstOrNull { raw -> TTML_ROOT.containsMatchIn(raw) })
                        }
                    }
                } else null
                val resolved = document ?: run {
                    val probe = externalProbeTrack(playing)
                    val candidate = onlineLyricsRepository.quickMatch(probe) ?: return@run null
                    val full = if (candidate.source == "amll-ttml" && candidate.wordLyrics.isBlank()) {
                        onlineLyricsRepository.downloadAmllCandidate(candidate)
                    } else candidate
                    withContext(Dispatchers.Default) {
                        LyricsParser.parseBundle(full.lyrics, full.wordLyrics, full.translationLyrics, full.romanLyrics)
                            .copy(rawTtml = listOf(full.wordLyrics, full.lyrics).firstOrNull { raw -> TTML_ROOT.containsMatchIn(raw) })
                    }
                }
                _externalLyrics.value = _externalLyrics.value.copy(
                    document = resolved?.copy(trackId = playing.key) ?: LyricsDocument.Empty,
                    searching = false,
                    message = if (resolved == null || resolved.lines.isEmpty()) "没有找到这首歌的歌词" else null,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _externalLyrics.value = _externalLyrics.value.copy(searching = false, message = "歌词获取失败，请检查网络")
            }
        }
    }

    private fun externalProbeTrack(playing: ExternalNowPlaying) = Track(
        id = "external:${playing.key.hashCode()}",
        uri = "",
        title = playing.title,
        artist = playing.artist,
        album = playing.album,
        durationMs = playing.durationMs,
        displayName = playing.title,
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
    )

    private val metadataRepository = app.onlineMetadata
    private val _metadataMatch = MutableStateFlow(MetadataMatchUiState())
    val metadataMatch: StateFlow<MetadataMatchUiState> = _metadataMatch
    val metadataRevision: StateFlow<Int> = metadataRepository.revision
    private var metadataJob: Job? = null

    private var metadataTarget: Track? = null

    fun matchMetadata(track: Track?, title: String? = null, artist: String? = null) {
        track ?: return
        metadataTarget = track
        metadataJob?.cancel()
        val queryTitle = title ?: track.title
        val queryArtist = artist ?: track.artist.takeUnless { it == "未知艺术家" }.orEmpty()
        _metadataMatch.value = MetadataMatchUiState(
            visible = true,
            trackId = track.id,
            queryTitle = queryTitle,
            queryArtist = queryArtist,
            searching = true,
        )
        metadataJob = viewModelScope.launch {
            val results = runCatching { metadataRepository.search(queryTitle, queryArtist, track.album) }
            _metadataMatch.value = _metadataMatch.value.copy(
                searching = false,
                candidates = results.getOrDefault(emptyList()),
                message = when {
                    results.isFailure -> "网络不可用，请稍后重试"
                    results.getOrThrow().isEmpty() -> "三个平台都没有找到匹配结果，试试修改关键词"
                    else -> null
                },
            )
        }
    }

    fun applyMetadata(candidate: MetadataCandidate, cover: Boolean, text: Boolean, details: Boolean) {
        val state = _metadataMatch.value
        val libraryTrack = uiState.value.tracks.firstOrNull { it.id == state.trackId }
        val track = libraryTrack ?: metadataTarget?.takeIf { it.id == state.trackId } ?: return
        @Suppress("NAME_SHADOWING") val text = text && libraryTrack != null
        @Suppress("NAME_SHADOWING") val cover = cover && libraryTrack != null
        metadataJob?.cancel()
        _metadataMatch.value = state.copy(applying = true, message = null)
        metadataJob = viewModelScope.launch {
            val errors = mutableListOf<String>()
            if (text) {
                runCatching { library.updateMetadata(track.id, candidate.title, candidate.artist, candidate.album) }
                    .onFailure { errors += "信息" }
            }
            if (cover) {
                try {
                    library.setCover(track.id, metadataRepository.downloadCover(candidate))
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    errors += "封面"
                }
            }
            if (details) {
                try {
                    metadataRepository.applyDetails(
                        candidate,
                        localArtist = if (text) candidate.artist else track.artist,
                        localAlbum = if (text) candidate.album else track.album,
                    )
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    errors += "歌手/专辑资料"
                }
            }
            _metadataMatch.value = MetadataMatchUiState()
            message.value = if (errors.isEmpty()) "已从${candidate.source.label}更新" else "部分内容更新失败：${errors.joinToString("、")}"
        }
    }

    fun dismissMetadataMatch() {
        metadataJob?.cancel()
        _metadataMatch.value = MetadataMatchUiState()
    }

    suspend fun artistInfo(name: String): ArtistInfo? = withContext(Dispatchers.IO) { metadataRepository.artist(name) }

    suspend fun albumInfo(title: String, artist: String): AlbumInfo? =
        withContext(Dispatchers.IO) { metadataRepository.album(title, artist) }

    fun saveArtistInfo(name: String, bio: String, image: Uri?) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val current = metadataRepository.artist(name)
                    val path = image?.let { metadataRepository.saveImage("artist", name, readImage(it)) } ?: current?.imagePath
                    metadataRepository.saveArtist(ArtistInfo(name, path, bio.trim(), MetadataSource.LOCAL, edited = true))
                }
                message.value = "已保存歌手资料"
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                message.value = "保存失败：图片无效或无法读取"
            }
        }
    }

    fun saveAlbumInfo(title: String, artist: String, description: String, releaseDate: String, image: Uri?) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val current = metadataRepository.album(title, artist)
                    val path = image?.let { metadataRepository.saveImage("album", "$artist\u0000$title", readImage(it)) } ?: current?.coverPath
                    metadataRepository.saveAlbum(
                        AlbumInfo(
                            title = title,
                            artist = artist,
                            coverPath = path,
                            description = description.trim(),
                            releaseDate = releaseDate.trim(),
                            genre = current?.genre.orEmpty(),
                            source = MetadataSource.LOCAL,
                            edited = true,
                        ),
                    )
                }
                message.value = "已保存专辑资料"
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                message.value = "保存失败：图片无效或无法读取"
            }
        }
    }

    private fun readImage(uri: Uri): ByteArray {
        val resolver = getApplication<Application>().contentResolver
        val source = android.graphics.ImageDecoder.createSource(resolver, uri)
        val bitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
            val scale = (1400f / longest).coerceAtMost(1f)
            decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return java.io.ByteArrayOutputStream().use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    fun retryPlayback() = playback.retryCurrent()

    fun importCustomFont(uri: Uri) {
        if (fontImportJob?.isActive == true) return
        fontImportJob = viewModelScope.launch {
            try {
                val font = withContext(Dispatchers.IO) { copyFont(uri) }
                val previous = settingsRepository.settings.value.lyricFontPath
                settingsRepository.setLyricFontPath(font.absolutePath)
                if (previous != null && previous != font.absolutePath) {
                    withContext(Dispatchers.IO) { privateFont(previous)?.delete() }
                }
                message.value = "已导入字体"
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                message.value = when {
                    error.message?.contains("256 MB", ignoreCase = true) == true -> "字体文件超过 256 MB"
                    error.message?.contains("Android 版本", ignoreCase = true) == true -> "当前系统不支持该字体格式"
                    else -> "字体导入失败：字体文件无效或无法读取"
                }
            }
        }
    }

    fun clearCustomFont() {
        val previous = settingsRepository.settings.value.lyricFontPath ?: return
        settingsRepository.setLyricFontPath(null)
        viewModelScope.launch(Dispatchers.IO) { privateFont(previous)?.delete() }
        message.value = "已恢复默认字体"
    }

    private fun privateFont(path: String): File? {
        val directory = File(getApplication<Application>().filesDir, "fonts").canonicalFile
        return File(path).canonicalFile.takeIf { it.parentFile == directory }
    }

    private fun copyFont(uri: Uri): File {
        val context = getApplication<Application>()
        val directory = File(context.filesDir, "fonts").apply { mkdirs() }
        val temporary = File.createTempFile("import-", ".tmp", directory)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("无法打开字体文件")
            input.use { source -> temporary.outputStream().use { target ->
                val buffer = ByteArray(8 * 1024)
                var count = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    count += read
                    require(count <= MAX_FONT_BYTES) { "字体文件超过 256 MB" }
                    target.write(buffer, 0, read)
                }
                require(count >= 4) { "字体文件为空" }
            } }
            val header = ByteArray(4)
            DataInputStream(FileInputStream(temporary)).use { it.readFully(header) }
            val type = when {
                header.contentEquals(byteArrayOf(0, 1, 0, 0)) -> "ttf"
                header.decodeToString() == "OTTO" -> "otf"
                header.decodeToString() == "wOFF" -> "woff"
                header.decodeToString() == "wOF2" -> "woff2"
                header.decodeToString() == "ttcf" -> "ttc"
                else -> error("字体文件无效")
            }
            if (type == "ttc" && android.os.Build.VERSION.SDK_INT < 26) {
                error("当前 Android 版本无法读取 TTC 字体")
            }
            if (type in setOf("ttf", "otf", "ttc")) {
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    android.graphics.Typeface.Builder(temporary)
                        .setTtcIndex(0)
                        .build()
                } else {
                    android.graphics.Typeface.createFromFile(temporary)
                }
            }
            val destination = File(directory, "custom-${UUID.randomUUID()}.$type")
            check(temporary.renameTo(destination)) { "无法保存字体文件" }
            return destination
        } finally {
            temporary.delete()
        }
    }

    fun showLyrics(show: Boolean) {
        lyricsVisible.value = show
        if (show && destination.value != AppDestination.NOW_PLAYING) navigateTo(AppDestination.NOW_PLAYING)
    }

    fun playTrack(track: Track) {
        val queue = uiState.value.visibleTracks.ifEmpty { uiState.value.tracks }
        val index = queue.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0
        playback.playQueue(queue, index)
        navigateTo(AppDestination.NOW_PLAYING)
    }

    fun playTracks(tracks: List<Track>, index: Int, origin: PlayOrigin? = null) {
        if (tracks.isEmpty() || index !in tracks.indices) return
        val currentKind = _playOrigin.value?.kind
        if (origin != null || currentKind == "folder" || currentKind == "playlist" || currentKind == "album") {
            setPlayOrigin(origin)
        }
        playback.playQueue(tracks, index)
        navigateTo(AppDestination.NOW_PLAYING)
    }

    fun togglePlayPause() = playback.togglePlayPause()
    fun skipNext() = playback.skipNext()
    fun skipPrevious() = playback.skipPrevious()
    fun playQueueIndex(index: Int) = playback.playQueueIndex(index)
    fun removeQueueIndex(index: Int) = playback.removeQueueIndex(index)
    fun moveQueueItem(from: Int, to: Int) = playback.moveQueueItem(from, to)

    fun playNext(tracks: List<Track>) {
        onlineTracks.value = onlineTracks.value + tracks.filter(Track::isOnline).associateBy(Track::id)
        playback.insertNext(tracks)
        showMessage(if (tracks.size == 1) "将在下一首播放" else "${tracks.size} 首将在接下来播放")
    }

    fun enqueue(tracks: List<Track>) {
        onlineTracks.value = onlineTracks.value + tracks.filter(Track::isOnline).associateBy(Track::id)
        playback.enqueue(tracks)
        showMessage(if (tracks.size == 1) "已加入播放列表" else "已加入 ${tracks.size} 首")
    }
    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)
    fun cycleRepeatMode() = playback.cycleRepeatMode()
    fun toggleShuffle() = playback.toggleShuffle()
    fun setPlaybackSpeed(speed: Float) = playback.setPlaybackSpeed(speed)

    private fun importOptions(): com.olbyine.music.data.ImportOptions {
        val settings = settingsRepository.settings.value
        return com.olbyine.music.data.ImportOptions(
            minDurationMs = settings.importMinDurationSec * 1_000L,
            skipDuplicates = settings.importSkipDuplicates,
        )
    }

    fun importAudio(uris: List<Uri>) {
        if (uris.isEmpty() || importing.value) return
        viewModelScope.launch {
            importing.value = true
            library.loaded.first { it }
            val existingTrackIds = library.tracks.value.mapTo(mutableSetOf()) { it.id }
            val report = library.importAudio(uris, importOptions())
            importing.value = false
            message.value = report.message + report.errors.firstOrNull()?.let { "：$it" }.orEmpty()
            viewModelScope.launch { enqueueAutomaticLyrics(existingTrackIds) }
            completeMetadata(report.importedIds)
        }
    }

    fun importFolder(uri: Uri?) {
        if (uri == null || importing.value) return
        viewModelScope.launch {
            importing.value = true
            library.loaded.first { it }
            val existingTrackIds = library.tracks.value.mapTo(mutableSetOf()) { it.id }
            val report = library.importFolder(uri, importOptions())
            importing.value = false
            message.value = report.message + report.errors.firstOrNull()?.let { "：$it" }.orEmpty()
            viewModelScope.launch { enqueueAutomaticLyrics(existingTrackIds) }
            completeMetadata(report.importedIds)
        }
    }

    private val _scanFolders = MutableStateFlow<List<com.olbyine.music.data.MusicLibraryRepository.ScanFolder>>(emptyList())

    val scanFolders: StateFlow<List<com.olbyine.music.data.MusicLibraryRepository.ScanFolder>> = _scanFolders

    fun refreshScanFolders() {
        viewModelScope.launch(Dispatchers.IO) { _scanFolders.value = library.grantedFolders() }
    }

    fun addScanFolder(uri: Uri?) {
        uri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            library.grantFolder(uri)
            _scanFolders.value = library.grantedFolders()
        }
    }

    suspend fun subfolders(treeUri: Uri, documentId: String?) = library.subfolders(treeUri, documentId)

    fun importFolders(targets: List<com.olbyine.music.data.MusicLibraryRepository.ScanTarget>) {
        if (targets.isEmpty() || importing.value) return
        viewModelScope.launch {
            importing.value = true
            library.loaded.first { it }
            val existingTrackIds = library.tracks.value.mapTo(mutableSetOf()) { it.id }
            val report = library.importFolders(targets, importOptions())
            importing.value = false
            message.value = report.message.ifBlank { "没有导入新歌曲" } + report.errors.firstOrNull()?.let { "：$it" }.orEmpty()
            viewModelScope.launch { enqueueAutomaticLyrics(existingTrackIds) }
            completeMetadata(report.importedIds)
        }
    }

    val webDav: com.olbyine.music.data.WebDavRepository get() = app.webDav

    fun importWebDav(server: com.olbyine.music.data.WebDavServer, folderUrl: String) {
        if (importing.value) return
        viewModelScope.launch {
            importing.value = true
            library.loaded.first { it }
            val existingTrackIds = library.tracks.value.mapTo(mutableSetOf()) { it.id }
            val report = library.importWebDav({ onFound -> app.webDav.collectAudio(server, folderUrl, onFound = onFound) }, importOptions())
            importing.value = false
            message.value = report.message.ifBlank { "没有导入新歌曲" } + report.errors.firstOrNull()?.let { "：$it" }.orEmpty()
            viewModelScope.launch { enqueueAutomaticLyrics(existingTrackIds) }
            completeMetadata(report.importedIds)
        }
    }

    fun playWebDav(server: com.olbyine.music.data.WebDavServer, entries: List<com.olbyine.music.data.WebDavEntry>, index: Int) {
        val known = library.tracks.value.associateBy(Track::uri)
        val tracks = entries.map { entry ->
            known[entry.url] ?: run {
                val base = entry.name.substringBeforeLast('.')
                val parts = base.split(Regex("\\s+[-–—]\\s+"), limit = 2)
                Track(
                    id = "dav:" + entry.url.hashCode().toUInt().toString(16) + entry.size.toString(16),
                    uri = entry.url,
                    title = if (parts.size == 2) parts[1].trim() else base,
                    artist = if (parts.size == 2) parts[0].trim() else server.name,
                    album = "WebDAV",
                    durationMs = 0L,
                    displayName = entry.name,
                    mimeType = entry.contentType,
                    sizeBytes = entry.size,
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
                )
            }
        }
        onlineTracks.value = onlineTracks.value + tracks.filter { it.id.startsWith("dav:") }.associateBy(Track::id)
        playTracks(tracks, index)
    }

    fun rescanLibraryTags() {
        if (importing.value) return
        viewModelScope.launch {
            importing.value = true
            val count = runCatching { library.rescanTags(onlyMissing = false) }.getOrDefault(0)
            runCatching { library.repairMisreadTags() }
            importing.value = false
            message.value = "已重新扫描 $count 首歌曲的曲目信息"
        }
    }

    private var completionJob: Job? = null
    private val completionQueue = java.util.concurrent.ConcurrentLinkedDeque<String>()

    private fun completeMetadata(ids: List<String>, force: Boolean = false) {
        if (ids.isEmpty() || (!force && !settingsRepository.settings.value.autoCompleteMetadata)) return
        completionQueue.addAll(ids)
        if (completionJob?.isActive == true) return
        completionJob = viewModelScope.launch(Dispatchers.IO) {
            val seenArtists = HashSet<String>()
            while (true) {
                val id = completionQueue.pollFirst() ?: break
                val track = library.tracks.value.firstOrNull { it.id == id } ?: continue
                runCatching { completeOne(track, seenArtists) }
                delay(600)
            }
        }
    }

    private suspend fun completeOne(track: Track, seenArtists: MutableSet<String>) {
        val unknownArtist = track.artist.isBlank() || track.artist == "未知艺术家"
        val unknownAlbum = track.album.isBlank() || track.album == "未知专辑"
        val artistKey = track.artist.split(" / ", "/", "、", ";", "&").first().trim()
        val needsArtistInfo = !unknownArtist && artistKey !in seenArtists && metadataRepository.artist(artistKey) == null
        if (track.coverPath != null && !unknownArtist && !unknownAlbum && !needsArtistInfo) return
        val candidates = metadataRepository.search(track.title, track.artist.takeUnless { unknownArtist }.orEmpty(), track.album.takeUnless { unknownAlbum }.orEmpty())
        fun agrees(candidate: MetadataCandidate) = candidateAgrees(track, candidate)
        val best = listOf(MetadataSource.NETEASE, MetadataSource.APPLE, MetadataSource.QQ)
            .firstNotNullOfOrNull { source -> candidates.firstOrNull { it.source == source && agrees(it) } } ?: return
        if (track.coverPath == null && best.coverUrl != null) {
            runCatching { library.setCover(track.id, metadataRepository.downloadCover(best)) }
        }
        if (unknownArtist || unknownAlbum) {
            library.updateMetadata(
                track.id,
                track.title,
                if (unknownArtist) best.artist.ifBlank { track.artist } else track.artist,
                if (unknownAlbum) best.album.ifBlank { track.album } else track.album,
            )
        }
        if (needsArtistInfo || unknownArtist) {
            seenArtists += artistKey
            runCatching {
                metadataRepository.applyDetails(
                    best,
                    localArtist = if (unknownArtist) best.artist else track.artist,
                    localAlbum = if (unknownAlbum) best.album else track.album,
                )
            }
        }
    }

    private fun candidateAgrees(track: Track, candidate: MetadataCandidate): Boolean {
        val n = OnlineMetadataRepository::normalize
        val unknownArtist = track.artist.isBlank() || track.artist == "未知艺术家"
        if (n(candidate.title) != n(track.title) &&
            n(candidate.title.replace(Regex("[(（\\[【].*?[)）\\]】]"), "")) != n(track.title.replace(Regex("[(（\\[【].*?[)）\\]】]"), ""))
        ) return false
        if (!unknownArtist) {
            val local = n(track.artist)
            val remote = n(candidate.artist)
            if (local.isNotBlank() && remote.isNotBlank() && !local.contains(remote) && !remote.contains(local) &&
                candidate.artist.split(" / ", "/", "、", ",").none { local.contains(n(it)) && n(it).length >= 2 }
            ) return false
        }
        val duration = candidate.durationMs
        return duration == null || track.durationMs <= 0 || abs(duration - track.durationMs) <= 8_000
    }

    private val autoDetailsTried = HashSet<String>()

    fun autoDetails(track: Track) {
        if (!autoDetailsTried.add(track.id)) return
        viewModelScope.launch(Dispatchers.IO) {
            val songId = track.id.removePrefix("ncm:").toLongOrNull()?.takeIf { track.id.startsWith("ncm:") }
            if (songId != null) {
                runCatching {
                    val song = netease.song(songId) ?: return@runCatching
                    val candidate = MetadataCandidate(
                        source = MetadataSource.NETEASE,
                        title = song.title,
                        artist = song.artist,
                        album = song.album,
                        coverUrl = song.coverUrl,
                        artistId = song.artistIds.firstOrNull()?.toString(),
                        albumId = song.albumId.takeIf { it > 0 }?.toString(),
                        durationMs = song.durationMs,
                    )
                    metadataRepository.applyDetails(candidate, localArtist = track.artist, localAlbum = track.album)
                }
                return@launch
            }
            runCatching {
                val unknownArtist = track.artist.isBlank() || track.artist == "未知艺术家"
                val unknownAlbum = track.album.isBlank() || track.album == "未知专辑"
                val artistKey = track.artist.split(" / ", "/", "、", ";", "&").first().trim()
                val needArtist = !unknownArtist && metadataRepository.artist(artistKey) == null
                val needAlbum = !unknownAlbum && metadataRepository.album(track.album, track.artist).let { it == null || (it.coverPath == null && it.description.isBlank()) }
                val needCover = !track.isOnline && track.uri.isNotBlank() && track.coverPath == null
                if (!needArtist && !needAlbum && !needCover) return@runCatching
                val candidates = metadataRepository.search(track.title, track.artist.takeUnless { unknownArtist }.orEmpty(), track.album.takeUnless { unknownAlbum }.orEmpty())
                val best = listOf(MetadataSource.NETEASE, MetadataSource.APPLE, MetadataSource.QQ)
                    .firstNotNullOfOrNull { source -> candidates.firstOrNull { it.source == source && candidateAgrees(track, it) } } ?: return@runCatching
                if (needCover && best.coverUrl != null) runCatching { library.setCover(track.id, metadataRepository.downloadCover(best)) }
                if (needArtist || needAlbum) metadataRepository.applyDetails(best, localArtist = track.artist, localAlbum = track.album)
            }
        }
    }
    fun completeLibraryMetadata() {
        val ids = library.tracks.value.filter {
            it.coverPath == null || it.artist == "未知艺术家" || it.album == "未知专辑"
        }.map { it.id }
        if (ids.isEmpty()) {
            message.value = "所有歌曲都有封面和信息"
            return
        }
        message.value = "正在为 ${ids.size} 首歌曲补全封面与信息…"
        completeMetadata(ids, force = true)
    }

    private val _albumRequest = MutableStateFlow<String?>(null)
    val albumRequest: StateFlow<String?> = _albumRequest

    fun openAlbumOf(track: Track) {
        _albumRequest.value = OnlineMetadataRepository.normalize(track.album) + "\u0000" + OnlineMetadataRepository.normalize(track.albumOwner)
        collapseNowPlaying()
        navigateTo(AppDestination.LIBRARY)
    }

    fun consumeAlbumRequest() {
        _albumRequest.value = null
    }

    fun deleteTracks(ids: Collection<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { playback.removeTrack(it) }
            library.deleteMany(ids)
            message.value = "已从音乐库移除 ${ids.size} 首歌曲"
        }
    }

    fun addTracksToPlaylist(playlistId: String, ids: List<String>) = libraryAction {
        ids.forEach { library.addToPlaylist(playlistId, it) }
        message.value = "已加入歌单（${ids.size} 首）"
    }

    fun importLyrics(uris: List<Uri>, trackId: String? = null) {
        if (uris.isEmpty() || importing.value) return
        viewModelScope.launch {
            importing.value = true
            val report = library.importLyrics(uris, trackId)
            importing.value = false
            message.value = report.message + report.errors.firstOrNull()?.let { "：$it" }.orEmpty()
        }
    }

    fun searchOnlineLyrics(track: Track?) = searchOnlineLyrics(track, LyricsSearchSource.ALL)

    fun searchOnlineLyrics(track: Track?, source: LyricsSearchSource) {
        if (track == null) return
        val query = LyricsMatcher.prepare(track)
        startManualLyricsSearch(track, source, query.title, query.artist)
    }

    fun updateOnlineLyricsQuery(title: String, artist: String) {
        val current = onlineLyrics.value
        if (!current.visible) return
        cancelManualLyricsSearch()
        onlineLyrics.value = current.copy(
            queryTitle = title,
            queryArtist = artist,
            searching = false,
            downloading = false,
            candidates = emptyList(),
            message = null,
        )
    }

    private var lyricsSearchTrack: Track? = null

    private fun lyricsTrack(id: String?): Track? =
        library.tracks.value.firstOrNull { it.id == id } ?: lyricsSearchTrack?.takeIf { it.id == id }

    private fun startManualLyricsSearch(track: Track, source: LyricsSearchSource, title: String, artist: String) {
        cancelManualLyricsSearch()
        lyricsSearchTrack = track
        val request = manualLyricsRequest
        onlineLyrics.value = OnlineLyricsUiState(
            trackId = track.id,
            visible = true,
            selectedSource = source,
            queryTitle = title,
            queryArtist = artist,
            searching = title.trim().isNotBlank(),
            message = if (title.trim().isBlank()) "请输入歌曲名后搜索" else null,
        )
        if (title.trim().isBlank()) return
        manualLyricsJob = viewModelScope.launch {
            try {
                val result = onlineLyricsRepository.match(
                    track = track,
                    includeCandidates = true,
                    forceSearch = true,
                    source = source,
                    searchTitle = title,
                    searchArtist = artist,
                )
                if (!isCurrentManualLyricsRequest(request, track.id, source)) return@launch
                if (source == LyricsSearchSource.ALL && library.tracks.value.any { it.id == track.id }) {
                    library.markLyricsLookup(track.id, result.status.storageValue)
                    if (!isCurrentManualLyricsRequest(request, track.id, source)) return@launch
                }
                val choices = (listOfNotNull(result.selected) + result.candidates)
                    .distinctBy(LyricsCandidate::id)
                val statusMessage = when {
                    choices.isNotEmpty() -> null
                    result.status == LyricsMatchStatus.UPSTREAM_ERROR && source == LyricsSearchSource.AMLL ->
                        "AMLL 歌词库暂时不可用，请稍后重试"
                    result.status == LyricsMatchStatus.UPSTREAM_ERROR && source == LyricsSearchSource.UNILYRIC ->
                        "UniLyric 歌词源暂时不可用，请检查网络后重试"
                    result.status == LyricsMatchStatus.UPSTREAM_ERROR ->
                        "歌词服务暂时不可用，请检查网络后重试"
                    source == LyricsSearchSource.AMLL -> "AMLL 歌词库没有找到结果，可修改歌曲名重试"
                    source == LyricsSearchSource.UNILYRIC -> "UniLyric 支持的歌词源没有找到结果，可修改歌曲名重试"
                    source == LyricsSearchSource.APPLE_MUSIC && !app.appleMusic.settings.value.hasUserToken ->
                        "需要先在 设置 → Apple Music 里填写 media-user-token（需要有效订阅）"
                    source == LyricsSearchSource.APPLE_MUSIC -> "Apple Music 没有这首歌的歌词"
                    source == LyricsSearchSource.QQ_MUSIC -> "QQ 音乐没有找到这首歌的歌词，可修改歌曲名重试"
                    source == LyricsSearchSource.NETEASE -> "网易云没有找到这首歌的歌词，可修改歌曲名重试"
                    source == LyricsSearchSource.KUGOU -> "酷狗没有找到这首歌的歌词，可修改歌曲名重试"
                    else -> "没有找到匹配度足够的歌词"
                }
                onlineLyrics.value = onlineLyrics.value.copy(
                    searching = false,
                    candidates = choices,
                    message = statusMessage,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (!isCurrentManualLyricsRequest(request, track.id, source)) return@launch
                onlineLyrics.value = onlineLyrics.value.copy(
                    searching = false,
                    candidates = emptyList(),
                    message = when (source) {
                        LyricsSearchSource.AMLL -> "AMLL 歌词库搜索失败：${error.message ?: "网络错误"}"
                        LyricsSearchSource.UNILYRIC -> "UniLyric 搜索失败：${error.message ?: "网络错误"}"
                        LyricsSearchSource.ALL -> "在线歌词搜索失败：${error.message ?: "网络错误"}"
                        LyricsSearchSource.APPLE_MUSIC -> "Apple Music 歌词获取失败：${error.message ?: "网络错误"}"
                        LyricsSearchSource.QQ_MUSIC -> "QQ 音乐搜索失败：${error.message ?: "网络错误"}"
                        LyricsSearchSource.NETEASE -> "网易云搜索失败：${error.message ?: "网络错误"}"
                        LyricsSearchSource.KUGOU -> "酷狗搜索失败：${error.message ?: "网络错误"}"
                    },
                )
            }
        }
    }

    fun selectOnlineLyricsSource(source: LyricsSearchSource) {
        val current = onlineLyrics.value
        if (!current.visible || current.selectedSource == source) return
        val track = lyricsTrack(current.trackId)
        if (track != null) startManualLyricsSearch(track, source, current.queryTitle, current.queryArtist)
        else {
            cancelManualLyricsSearch()
            onlineLyrics.value = current.copy(
                selectedSource = source,
                searching = false,
                candidates = emptyList(),
                message = "歌曲已从音乐库移除",
            )
        }
    }

    fun retryOnlineLyrics() {
        val current = onlineLyrics.value
        val track = lyricsTrack(current.trackId)
        if (track != null) startManualLyricsSearch(track, current.selectedSource, current.queryTitle, current.queryArtist)
        else if (current.visible) onlineLyrics.value = current.copy(
            searching = false,
            candidates = emptyList(),
            message = "歌曲已从音乐库移除",
        )
    }

    fun applyOnlineLyrics(candidate: LyricsCandidate) {
        val current = onlineLyrics.value
        val trackId = current.trackId ?: return
        if (!current.visible || current.searching || current.downloading ||
            current.candidates.none { it.id == candidate.id }
        ) return
        cancelManualLyricsSearch()
        val request = manualLyricsRequest
        onlineLyrics.value = current.copy(downloading = true, message = "正在下载歌词…")
        manualDownloadJob = viewModelScope.launch {
            try {
                val resolved = if (candidate.source == "amll-ttml" && candidate.wordLyrics.isBlank()) {
                    onlineLyricsRepository.downloadAmllCandidate(candidate)
                } else candidate
                if (!isCurrentManualLyricsRequest(request, trackId, current.selectedSource)) return@launch
                if (library.tracks.value.any { it.id == trackId }) {
                    library.storeLyrics(trackId, resolved.toStoredLyrics())
                } else if (_externalLyrics.value.nowPlaying?.key == trackId) {
                    val document = withContext(Dispatchers.Default) {
                        LyricsParser.parseBundle(resolved.lyrics, resolved.wordLyrics, resolved.translationLyrics, resolved.romanLyrics)
                            .copy(rawTtml = listOf(resolved.wordLyrics, resolved.lyrics).firstOrNull { TTML_ROOT.containsMatchIn(it) }, trackId = trackId)
                    }
                    _externalLyrics.value = _externalLyrics.value.copy(document = document, searching = false, message = null)
                } else {
                    app.lyricsHub.setOverride(trackId, resolved.toStoredLyrics())
                }
                if (!isCurrentManualLyricsRequest(request, trackId, current.selectedSource)) return@launch
                onlineLyrics.value = OnlineLyricsUiState()
                message.value = "已使用${candidate.source.displaySource()}歌词"
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (!isCurrentManualLyricsRequest(request, trackId, current.selectedSource)) return@launch
                onlineLyrics.value = onlineLyrics.value.copy(
                    downloading = false,
                    message = "歌词下载或保存失败：${error.message ?: "网络错误"}，可重试或选择其他结果",
                )
            }
        }
    }

    fun dismissOnlineLyricsCandidates() {
        cancelManualLyricsSearch()
        onlineLyrics.value = OnlineLyricsUiState()
    }

    private fun cancelManualLyricsSearch() {
        manualLyricsRequest++
        manualLyricsJob?.cancel()
        manualLyricsJob = null
        manualDownloadJob?.cancel()
        manualDownloadJob = null
    }

    private fun isCurrentManualLyricsRequest(request: Long, trackId: String, source: LyricsSearchSource): Boolean {
        val current = onlineLyrics.value
        return request == manualLyricsRequest && current.visible && current.trackId == trackId &&
            current.selectedSource == source
    }

    val navidrome: com.olbyine.music.data.NavidromeClient get() = app.navidrome

    fun playNavidrome(
        songs: List<com.olbyine.music.data.NdSong>,
        index: Int,
        shuffle: Boolean,
        origin: PlayOrigin? = null,
    ) {
        if (songs.isEmpty()) return
        setPlayOrigin(origin)
        val tracks = runCatching { songs.map(app.navidrome::toTrack) }.getOrElse {
            showMessage(it.message ?: "无法播放")
            return
        }
        onlineTracks.value = onlineTracks.value + tracks.associateBy(Track::id)
        if (shuffle) playback.setPlayMode(true, playback.state.value.repeatMode)
        playback.playQueue(tracks, if (shuffle) tracks.indices.random() else index)
    }

    fun toggleFavorite(track: Track) {
        if (track.id.startsWith("nd:")) {
            val flipped = track.copy(favorite = !track.favorite)
            onlineTracks.value = onlineTracks.value + (track.id to flipped)
            viewModelScope.launch {
                runCatching { app.navidrome.setStarred(track.id.removePrefix("nd:"), flipped.favorite) }.onFailure {
                    onlineTracks.value = onlineTracks.value + (track.id to track)
                    showMessage(it.message ?: "收藏失败")
                }
            }
            return
        }
        if (track.isOnline) {
            track.id.removePrefix("ncm:").toLongOrNull()?.let { id -> toggleNeteaseLike(id) }
            return
        }
        viewModelScope.launch { library.setFavorite(track.id, !track.favorite) }
    }

    private val netease get() = app.netease
    private val _netease = MutableStateFlow(NeteaseUiState(account = app.netease.account.value))
    val neteaseState: StateFlow<NeteaseUiState> = _netease
    private var neteaseJob: Job? = null
    private var loginJob: Job? = null

    init {
        viewModelScope.launch {
            netease.account.collect { account -> _netease.value = _netease.value.copy(account = account) }
        }
        if (netease.signedIn) viewModelScope.launch {
            runCatching { netease.refreshAccount() }
            loadNeteasePlaylists()
        }
    }

    fun loadNeteasePlaylists() {
        if (!netease.signedIn) return
        neteaseJob?.cancel()
        _netease.value = _netease.value.copy(loading = true, message = null)
        neteaseJob = viewModelScope.launch {
            try {
                val lists = netease.playlists()
                val liked = runCatching { netease.likedIds() }.getOrDefault(_netease.value.liked)
                _netease.value = _netease.value.copy(loading = false, playlists = lists, liked = liked)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _netease.value = _netease.value.copy(loading = false, message = error.message ?: "读取歌单失败，请检查网络")
            }
        }
    }

    val soundCloud: com.olbyine.music.data.SoundCloudClient get() = app.soundCloud

    fun playSoundCloud(
        tracks: List<com.olbyine.music.data.ScTrack>,
        index: Int,
        shuffle: Boolean = false,
        origin: PlayOrigin? = null,
    ) {
        setPlayOrigin(origin)
        val chosen = tracks.getOrNull(index)
        val playable = tracks.filter { it.streamable }
        if (playable.isEmpty()) {
            message.value = "这些曲目在 SoundCloud 上不能播放"
            return
        }
        val ordered = if (shuffle) playable.shuffled() else playable
        val queue = ordered.map { it.toTrack() }
        onlineTracks.value = onlineTracks.value + queue.associateBy(Track::id)
        val start = if (shuffle) 0 else chosen?.let { pick -> ordered.indexOfFirst { it.id == pick.id } }?.coerceAtLeast(0) ?: 0
        playTracks(queue, start)
    }
    val neteaseClient: com.olbyine.music.data.NeteaseClient get() = app.netease

    fun openNeteasePlaylist(playlist: com.olbyine.music.data.NeteasePlaylistSummary) {
        val cached = playlist.kind == com.olbyine.music.data.NeteaseCollectionKind.PLAYLIST ||
            playlist.kind == com.olbyine.music.data.NeteaseCollectionKind.CHART
        openNeteaseCollection(playlist, cached)
    }

    fun openNeteaseCollection(
        playlist: com.olbyine.music.data.NeteasePlaylistSummary,
        cached: Boolean = false,
    ) {
        _netease.value = _netease.value.copy(openPlaylist = playlist, songs = emptyList(), songsLoading = true, songsPartial = false, message = null, localMatches = emptyMap())
        neteaseJob?.cancel()
        neteaseJob = viewModelScope.launch {
            suspend fun show(songs: List<com.olbyine.music.data.NeteaseSong>, loading: Boolean, partial: Boolean = false) {
                if (_netease.value.openPlaylist?.id != playlist.id) return
                _netease.value = _netease.value.copy(songs = songs, songsLoading = loading, songsPartial = partial)
                val matches = withContext(Dispatchers.Default) {
                    matchSongs(songs.map { RemoteSong(it.id.toString(), it.title, it.artists, it.album, it.durationMs, it.coverUrl) }, library.tracks.value)
                        .mapKeys { it.key.toLong() }
                }
                if (_netease.value.openPlaylist?.id == playlist.id) _netease.value = _netease.value.copy(localMatches = matches)
            }
            try {
                val saved = if (cached) netease.cachedSongs(playlist.id) else null
                if (!saved.isNullOrEmpty()) show(saved, loading = false)
                var best = saved.orEmpty()
                netease.collectionStream(playlist).collect { batch ->
                    if (!batch.complete && batch.songs.size < best.size) return@collect
                    best = batch.songs
                    show(batch.songs, loading = false, partial = batch.songs.size < playlist.trackCount)
                }
                if (_netease.value.openPlaylist?.id == playlist.id) _netease.value = _netease.value.copy(songsPartial = false)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _netease.value = _netease.value.copy(songsLoading = false, message = error.message ?: "读取歌单失败")
            }
        }
    }

    fun pausePlayback() = playback.pause()

    fun addNeteaseSongToPlaylist(playlist: com.olbyine.music.data.NeteasePlaylistSummary, songId: Long) {
        viewModelScope.launch {
            try {
                val added = netease.addToPlaylist(playlist.id, listOf(songId))
                message.value = if (added) "已收藏到「${playlist.name}」" else "这首歌已经在「${playlist.name}」里了"
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                message.value = error.message ?: "收藏失败"
            }
        }
    }

    private val likeCounts = java.util.concurrent.ConcurrentHashMap<String, Long>()

    suspend fun streamLikeCount(track: Track): Long? {
        likeCounts[track.id]?.let { return it }
        val count = when {
            track.id.startsWith("ncm:") -> track.id.removePrefix("ncm:").toLongOrNull()?.let { netease.redCount(it) }
            track.id.startsWith("sc:") -> track.id.removePrefix("sc:").toLongOrNull()?.let { id ->
                runCatching { app.soundCloud.trackInfo(id).first.track.likeCount }.getOrNull()
            }
            else -> null
        } ?: return null
        likeCounts[track.id] = count
        return count
    }

    suspend fun neteaseAlbumIdOf(track: Track): Long? {
        val songId = track.id.removePrefix("ncm:").toLongOrNull()?.takeIf { track.id.startsWith("ncm:") } ?: return null
        return runCatching { netease.song(songId)?.albumId?.takeIf { it > 0 } }.getOrNull()
    }

    suspend fun neteaseMvOf(track: Track): Long {
        val songId = track.id.removePrefix("ncm:").toLongOrNull()?.takeIf { track.id.startsWith("ncm:") } ?: return 0
        return runCatching { netease.song(songId)?.mvId ?: 0 }.getOrDefault(0)
    }

    suspend fun neteaseArtistIdsOf(track: Track): List<Long> {
        val songId = track.id.removePrefix("ncm:").toLongOrNull()?.takeIf { track.id.startsWith("ncm:") } ?: return emptyList()
        return runCatching { netease.song(songId)?.artistIds.orEmpty() }.getOrDefault(emptyList())
    }

    fun closeNeteasePlaylist() {
        neteaseJob?.cancel()
        _netease.value = _netease.value.copy(openPlaylist = null, songs = emptyList(), songsLoading = false)
    }

    fun playNeteaseSongs(
        songs: List<com.olbyine.music.data.NeteaseSong>,
        index: Int,
        shuffle: Boolean = false,
        source: String? = null,
        startMs: Long = 0L,
    ) {
        val state = _netease.value
        val online = settingsRepository.settings.value.neteaseOnline
        val local = library.tracks.value.associateBy(Track::id)
        val chosen = songs.getOrNull(index)
        val queue = songs.mapNotNull { song ->
            state.localMatches[song.id]?.let(local::get)
                ?: song.takeIf { online && !it.unavailable }?.toTrack()
        }
        if (queue.isEmpty()) {
            message.value = if (online) "这些歌曲都无法播放" else "在线播放已关闭，本地曲库里也没有这些歌曲"
            return
        }
        onlineTracks.value = onlineTracks.value + queue.filter(Track::isOnline).associateBy(Track::id)
        val start = chosen?.let { song ->
            queue.indexOfFirst { it.id == (state.localMatches[song.id] ?: com.olbyine.music.data.NeteaseSong.trackId(song.id)) }
        }?.takeIf { it >= 0 } ?: 0
        if (shuffle && !uiState.value.playback.shuffleEnabled) playback.toggleShuffle()
        _activeCollection.value = source
        setPlayOrigin(
            source?.let { key ->
                val parts = key.split(":")
                if (parts.size >= 3) {
                    PlayOrigin(kind = parts[0] + ":" + parts[1], id = parts[2], title = _netease.value.openPlaylist?.name.orEmpty())
                } else null
            },
        )
        activeQueueIds = if (source == null) emptySet() else queue.map(Track::id).toSet()
        activeSongByTrack = if (source == null) emptyMap() else songs.mapNotNull { song ->
            (state.localMatches[song.id] ?: com.olbyine.music.data.NeteaseSong.trackId(song.id)).let { it to song.id }
        }.toMap()
        activeCollectionAt = System.currentTimeMillis()
        playback.playQueue(queue, if (shuffle) queue.indices.random() else start)
        if (startMs > 1_000L) viewModelScope.launch {
            delay(700)
            playback.seekTo(startMs)
        }
        navigateTo(AppDestination.NOW_PLAYING)
    }

    fun resumeNeteaseCollection(key: String, songs: List<com.olbyine.music.data.NeteaseSong>) {
        val point = _collectionResume.value[key] ?: return
        if (songs.isEmpty()) return
        val index = songs.indexOfFirst { it.id == point.songId }
        if (index < 0) playNeteaseSongs(songs, 0, source = key)
        else playNeteaseSongs(songs, index, source = key, startMs = point.positionMs)
    }

    fun toggleNeteaseLike(id: Long) {
        val liked = id in _netease.value.liked
        _netease.value = _netease.value.copy(liked = if (liked) _netease.value.liked - id else _netease.value.liked + id)
        viewModelScope.launch {
            try {
                netease.setLiked(id, !liked)
                message.value = if (liked) "已从「我喜欢的音乐」移除" else "已添加到「我喜欢的音乐」"
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _netease.value = _netease.value.copy(liked = if (liked) _netease.value.liked + id else _netease.value.liked - id)
                message.value = error.message ?: "操作失败"
            }
        }
    }

    fun playNeteaseSong(id: Long, startMs: Long = 0L) {
        viewModelScope.launch {
            try {
                val songs = netease.songDetails(listOf(id))
                if (songs.isEmpty()) error("找不到这首歌")
                playNeteaseSongs(songs, 0)
                if (startMs > 1_000) {
                    delay(600)
                    playback.seekTo(startMs)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                message.value = error.message ?: "无法播放"
            }
        }
    }

    fun openNeteaseTarget(target: com.olbyine.music.data.NeteaseTarget) {
        when (target) {
            is com.olbyine.music.data.NeteaseTarget.Song -> playNeteaseSong(target.id)
            is com.olbyine.music.data.NeteaseTarget.Playlist -> openNeteasePlaylist(
                com.olbyine.music.data.NeteasePlaylistSummary(target.id, "歌单", null, 0, "", liked = false, own = false),
            )
            is com.olbyine.music.data.NeteaseTarget.Album -> openNeteasePlaylist(
                com.olbyine.music.data.NeteasePlaylistSummary(target.id, "专辑", null, 0, "", liked = false, own = false, kind = com.olbyine.music.data.NeteaseCollectionKind.ALBUM),
            )
            is com.olbyine.music.data.NeteaseTarget.ListenTogether -> joinListenTogether(target.roomId, target.inviterId)
            else -> Unit
        }
    }

    suspend fun neteaseSongIdOf(track: Track): Long? {
        if (track.id.startsWith("ncm:")) return track.id.removePrefix("ncm:").toLongOrNull()
        neteaseIdCache[track.id]?.let { return it.takeIf { id -> id > 0 } }
        val found = runCatching {
            val n = OnlineMetadataRepository::normalize
            fun bare(value: String) = n(value.replace(Regex("[(（\\[【].*?[)）\\]】]"), ""))
            val unknownArtist = track.artist.isBlank() || track.artist == "未知艺术家"
            val firstArtist = track.artist.split(" / ", "/", "、", ";", "&", ",").first().trim()
            val queries = listOfNotNull(if (unknownArtist) null else "${track.title} $firstArtist", track.title, track.album.takeIf { it.isNotBlank() && it != "未知专辑" }?.let { "${track.title} $it" })
            for (query in queries) {
                val songs = runCatching { netease.search(query, 1).songs }.getOrDefault(emptyList())
                val match = songs.firstOrNull { song ->
                    if (bare(song.title) != bare(track.title) && n(song.title) != n(track.title)) return@firstOrNull false
                    val artistOk = unknownArtist || song.artists.any { a -> n(a).isNotBlank() && (n(track.artist).contains(n(a)) || n(a).contains(n(firstArtist))) }
                    val durationOk = track.durationMs > 0 && song.durationMs > 0 && kotlin.math.abs(song.durationMs - track.durationMs) <= 3_000
                    val albumOk = track.album.isNotBlank() && n(song.album) == n(track.album)
                    artistOk || durationOk || albumOk
                }
                if (match != null) return@runCatching match.id
            }
            null
        }.getOrNull()
        neteaseIdCache[track.id] = found ?: 0L
        return found
    }

    private val neteaseIdCache = java.util.concurrent.ConcurrentHashMap<String, Long>()

    data class ListenTogetherUiState(
        val room: com.olbyine.music.data.NeteaseListenRoom? = null,
        val busy: Boolean = false,
        val message: String? = null,
    )

    private val _listenTogether = MutableStateFlow(ListenTogetherUiState())
    val listenTogether: StateFlow<ListenTogetherUiState> = _listenTogether
    private var listenJob: Job? = null

    fun refreshListenTogether() {
        if (!netease.signedIn || listenJob?.isActive == true) return
        viewModelScope.launch {
            val room = runCatching { netease.listenStatus() }.getOrNull()
            if (room != null && room.roomId.isNotBlank()) startListenLoop(room)
        }
    }

    fun createListenTogether() {
        if (_listenTogether.value.busy) return
        _listenTogether.value = ListenTogetherUiState(busy = true)
        viewModelScope.launch {
            try {
                startListenLoop(netease.listenCreate())
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _listenTogether.value = ListenTogetherUiState(message = error.message ?: "无法创建房间")
            }
        }
    }

    fun joinListenTogether(link: String) {
        val target = com.olbyine.music.data.NeteaseLinks.resolve(link.trim().let { if (it.startsWith("http")) it else "https://$it" })
        if (target !is com.olbyine.music.data.NeteaseTarget.ListenTogether) {
            _listenTogether.value = _listenTogether.value.copy(message = "没有识别到一起听邀请链接")
            return
        }
        joinListenTogether(target.roomId, target.inviterId)
    }

    fun joinListenTogether(roomId: String, inviterId: Long) {
        if (_listenTogether.value.busy) return
        _listenTogether.value = ListenTogetherUiState(busy = true)
        viewModelScope.launch {
            try {
                startListenLoop(netease.listenAccept(roomId, inviterId))
                message.value = "已加入一起听"
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _listenTogether.value = ListenTogetherUiState(message = error.message ?: "无法加入房间")
            }
        }
    }

    fun leaveListenTogether() {
        val room = _listenTogether.value.room
        listenJob?.cancel()
        listenJob = null
        _listenTogether.value = ListenTogetherUiState()
        if (room != null) viewModelScope.launch { netease.listenEnd(room.roomId) }
    }

    private fun startListenLoop(room: com.olbyine.music.data.NeteaseListenRoom) {
        listenJob?.cancel()
        _listenTogether.value = ListenTogetherUiState(room = room)
        listenJob = viewModelScope.launch {
            var seq = 1
            var version = 1
            var lastSong = 0L
            var lastPlaying: Boolean? = null
            var lastPosition = 0L
            var lastQueue: List<Long> = emptyList()
            var lastBeat = 0L
            var tick = 0
            while (true) {
                val current = _listenTogether.value.room ?: break
                val state = playback.state.value
                val songId = state.currentMediaId?.takeIf { it.startsWith("ncm:") }?.removePrefix("ncm:")?.toLongOrNull() ?: 0L
                val position = state.estimatedPosition()
                if (current.host) {
                    val queue = uiState.value.queue.mapNotNull { it.id.takeIf { id -> id.startsWith("ncm:") }?.removePrefix("ncm:")?.toLongOrNull() }
                    runCatching {
                        if (queue.isNotEmpty() && queue != lastQueue) {
                            netease.listenSyncList(current.roomId, queue, version++)
                            lastQueue = queue
                        }
                        if (songId != 0L && songId != lastSong) {
                            netease.listenCommand(current.roomId, "GOTO", position, state.isPlaying, lastSong, songId, seq++)
                        } else if (songId != 0L && lastPlaying != null && lastPlaying != state.isPlaying) {
                            netease.listenCommand(current.roomId, if (state.isPlaying) "PLAY" else "PAUSE", position, state.isPlaying, songId, songId, seq++)
                        } else if (songId != 0L && state.isPlaying && kotlin.math.abs(position - (lastPosition + 1_000L)) > 4_000L) {
                            netease.listenCommand(current.roomId, "SEEK", position, true, songId, songId, seq++)
                        }
                    }
                    if (songId != 0L) {
                        lastSong = songId
                        lastPlaying = state.isPlaying
                    }
                    lastPosition = position
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (songId != 0L && now - lastBeat > 30_000L) {
                        netease.listenHeartbeat(current.roomId, songId, state.isPlaying, position)
                        lastBeat = now
                    }
                } else if (tick % 5 == 0) {
                    runCatching { netease.listenPlaylist(current.roomId) }.getOrNull()?.let { (ids, playing, progress) ->
                        val target = playing ?: ids.firstOrNull()
                        if (target != null && target != songId && target != lastSong) {
                            lastSong = target
                            playNeteaseSong(target, progress ?: 0L)
                        } else if (target != null && target == songId && progress != null && kotlin.math.abs(progress - position) > 6_000L) {
                            playback.seekTo(progress)
                        }
                    }
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (songId != 0L && now - lastBeat > 30_000L) {
                        netease.listenHeartbeat(current.roomId, songId, state.isPlaying, position)
                        lastBeat = now
                    }
                }
                if (tick % 4 == 0) {
                    val fresh = runCatching { netease.listenStatus() }.getOrNull()?.takeIf { it.roomId == current.roomId }
                        ?: runCatching { netease.listenCheck(current.roomId) }.getOrNull()
                    if (fresh != null && fresh.users.isNotEmpty() && fresh.users != current.users) {
                        val joined = fresh.users.filter { user -> current.users.none { it.userId == user.userId } && user.userId != netease.account.value?.userId }
                        _listenTogether.value = _listenTogether.value.copy(room = current.copy(users = fresh.users))
                        joined.firstOrNull()?.let { message.value = "${it.nickname} 加入了一起听" }
                    }
                }
                tick++
                delay(1_000)
            }
        }
    }
    fun saveNeteasePlaylistLocally() {
        val state = _netease.value
        val playlist = state.openPlaylist ?: return
        val ids = state.songs.mapNotNull { state.localMatches[it.id] }.distinct()
        if (ids.isEmpty()) {
            message.value = "本地曲库里没有这个歌单的歌曲"
            return
        }
        viewModelScope.launch {
            runCatching { library.createPlaylistWithTracks("网易云 · ${playlist.name}".take(80), ids) }
                .onSuccess { message.value = "已创建本地歌单（${ids.size}/${state.songs.size} 首）" }
                .onFailure { message.value = "创建歌单失败：${it.message ?: "未知错误"}" }
        }
    }

    fun openNeteaseLogin() {
        _netease.value = _netease.value.copy(login = NeteaseLoginUiState(busy = true))
        startQrLogin()
    }

    fun refreshNeteaseQr() = startQrLogin()

    private fun startQrLogin() {
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            try {
                val key = netease.qrKey()
                updateLogin { NeteaseLoginUiState(qrContent = netease.qrContent(key), status = com.olbyine.music.data.QrLoginStatus(com.olbyine.music.data.QrLoginState.WAITING)) }
                while (true) {
                    delay(2_000)
                    val status = netease.qrStatus(key)
                    updateLogin { it.copy(status = status) }
                    when (status.state) {
                        com.olbyine.music.data.QrLoginState.CONFIRMED -> {
                            finishLogin()
                            return@launch
                        }
                        com.olbyine.music.data.QrLoginState.EXPIRED, com.olbyine.music.data.QrLoginState.ERROR -> return@launch
                        else -> Unit
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                updateLogin { it.copy(busy = false, error = error.message ?: "网络错误") }
            }
        }
    }

    private fun updateLogin(transform: (NeteaseLoginUiState) -> NeteaseLoginUiState) {
        val current = _netease.value.login ?: return
        _netease.value = _netease.value.copy(login = transform(current))
    }

    fun signInNeteaseWithCookie(raw: String) {
        loginJob?.cancel()
        updateLogin { it.copy(busy = true, error = null) }
        loginJob = viewModelScope.launch {
            try {
                netease.signInWithCookie(raw)
                finishLogin()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                updateLogin { it.copy(busy = false, error = error.message ?: "登录失败") }
            }
        }
    }

    private fun finishLogin() {
        _netease.value = _netease.value.copy(login = null)
        message.value = "已登录网易云音乐" + (netease.account.value?.nickname?.let { "：$it" } ?: "")
        loadNeteasePlaylists()
    }

    fun closeNeteaseLogin() {
        loginJob?.cancel()
        _netease.value = _netease.value.copy(login = null)
    }

    fun signOutNetease() {
        netease.signOut()
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        _netease.value = NeteaseUiState()
        message.value = "已退出网易云音乐"
    }

    fun updateMetadata(track: Track, title: String, artist: String, album: String) {
        viewModelScope.launch { library.updateMetadata(track.id, title, artist, album) }
    }

    fun deleteTrack(track: Track) {
        viewModelScope.launch {
            playback.removeTrack(track.id)
            library.delete(track.id)
            message.value = "已从音乐库移除「${track.title}」"
        }
    }

    fun setThemeMode(mode: ThemeMode) = settingsRepository.setThemeMode(mode)
    fun setLyricFontSize(value: Float) = settingsRepository.setLyricFontSize(value)
    fun setLyricOpacity(value: Float) = settingsRepository.setLyricInactiveOpacity(value)
    fun setLyricOffset(value: Long) = settingsRepository.setLyricOffsetMs(value)

    fun setLyricAlignPosition(value: Float) = settingsRepository.setLyricAlignPosition(value)
    fun setHapticsEnabled(value: Boolean) = settingsRepository.setHapticsEnabled(value)
    fun setStatusBarLyricsEnabled(value: Boolean) = settingsRepository.setStatusBarLyricsEnabled(value)
    fun setAutomaticLyricsEnabled(value: Boolean) = settingsRepository.setAutomaticLyricsEnabled(value)

    fun showMessage(text: String) {
        message.value = text
    }

    fun consumeMessage() {
        message.value = null
    }

    private fun restorePlaybackQueue() {
        _playOrigin.value = readPersistedOrigin()
        viewModelScope.launch {
            playback.state.filter { it.connected }.first()
            if (playback.state.value.mediaItemCount != 0) return@launch
            val savedId = savedPlayback.getString(KEY_MEDIA_ID, null) ?: return@launch
            val stored = decodeQueue(savedPlayback.getString(KEY_QUEUE_JSON, null))
            val byId = library.tracks.value.associateBy(Track::id)
            val restored = stored.map { byId[it.id] ?: it }
            if (restored.isEmpty()) return@launch
            onlineTracks.value = onlineTracks.value + restored.filter(Track::isOnline).associateBy(Track::id)
            val index = restored.indexOfFirst { it.id == savedId }
            if (index < 0) return@launch
            playback.restoreQueue(restored, index, savedPlayback.getLong(KEY_POSITION, 0L))
        }
    }

    private fun persistPlaybackPosition() {
        viewModelScope.launch {
            var lastQueueSignature = ""
            playback.state.collect { state ->
                val id = state.currentMediaId ?: return@collect
                val editor = savedPlayback.edit().putString(KEY_MEDIA_ID, id)
                val signature = state.queueIds.joinToString(",")
                if (signature != lastQueueSignature) {
                    val known = onlineTracks.value + library.tracks.value.associateBy(Track::id)
                    val queue = state.queueIds.mapNotNull(known::get)
                    if (queue.isNotEmpty()) {
                        editor.putString(KEY_QUEUE_JSON, encodeQueue(queue))
                        lastQueueSignature = signature
                    }
                }
                if (state.positionMs > 0L) editor.putLong(KEY_POSITION, state.positionMs)
                editor.apply()
            }
        }
        viewModelScope.launch {
            playback.positionUpdates(2_000L).collect { position ->
                savedPlayback.edit().putLong(KEY_POSITION, position).apply()
            }
        }
    }

    private fun encodeQueue(tracks: List<Track>): String {
        val rows = org.json.JSONArray()
        tracks.forEach { track ->
            rows.put(
                org.json.JSONObject()
                    .put("id", track.id)
                    .put("uri", track.uri)
                    .put("title", track.title)
                    .put("artist", track.artist)
                    .put("album", track.album)
                    .put("durationMs", track.durationMs)
                    .put("displayName", track.displayName)
                    .put("mimeType", track.mimeType)
                    .put("sizeBytes", track.sizeBytes)
                    .put("coverPath", track.coverPath)
                    .put("favorite", track.favorite)
                    .put("addedAt", track.addedAt)
                    .put("folderId", track.folderId)
                    .put("folderPath", track.folderPath)
                    .put("trackNumber", track.trackNumber)
                    .put("discNumber", track.discNumber)
                    .put("year", track.year)
                    .put("albumArtist", track.albumArtist)
                    .put("sampleRate", track.sampleRate)
                    .put("bitsPerSample", track.bitsPerSample)
                    .put("bitrate", track.bitrate)
                    .put("artworkUrl", track.artworkUrl),
            )
        }
        return rows.toString()
    }

    private fun decodeQueue(raw: String?): List<Track> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val rows = org.json.JSONArray(raw)
            (0 until rows.length()).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                val id = row.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
                val uri = row.optString("uri").takeIf(String::isNotBlank) ?: return@mapNotNull null
                Track(
                    id = id,
                    uri = uri,
                    title = row.optString("title"),
                    artist = row.optString("artist"),
                    album = row.optString("album"),
                    durationMs = row.optLong("durationMs"),
                    displayName = row.optString("displayName"),
                    mimeType = row.optString("mimeType").takeIf(String::isNotBlank),
                    sizeBytes = row.optLong("sizeBytes"),
                    coverPath = row.optString("coverPath").takeIf(String::isNotBlank),
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
                    favorite = row.optBoolean("favorite"),
                    addedAt = row.optLong("addedAt"),
                    folderId = row.optString("folderId").takeIf(String::isNotBlank),
                    folderPath = row.optString("folderPath").takeIf(String::isNotBlank),
                    trackNumber = row.optInt("trackNumber"),
                    discNumber = row.optInt("discNumber"),
                    year = row.optInt("year"),
                    albumArtist = row.optString("albumArtist").takeIf(String::isNotBlank),
                    sampleRate = row.optInt("sampleRate"),
                    bitsPerSample = row.optInt("bitsPerSample"),
                    bitrate = row.optInt("bitrate"),
                    artworkUrl = row.optString("artworkUrl").takeIf(String::isNotBlank),
                )
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun enqueueAutomaticLyrics(existingTrackIds: Set<String>) {
        if (!settingsRepository.settings.value.automaticLyricsEnabled) return
        val newTracks = library.tracks.value.filter { it.id !in existingTrackIds && needsAutomaticLyrics(it) }
        newTracks.forEach { track ->
            if (automaticAttempts.add(track.id)) automaticLyricsQueue.send(track.id)
        }
    }

    private fun processAutomaticLyricsQueue() {
        viewModelScope.launch {
            for (trackId in automaticLyricsQueue) {
                settingsRepository.settings.first { it.automaticLyricsEnabled }
                delay(AUTOMATIC_LYRICS_START_DELAY_MS)
                if (!settingsRepository.settings.value.automaticLyricsEnabled) {
                    automaticAttempts.remove(trackId)
                    continue
                }
                val track = library.tracks.value.firstOrNull { it.id == trackId } ?: continue
                if (needsAutomaticLyrics(track, ignoreQueued = true)) automaticMatch(track)
                delay(AUTOMATIC_LYRICS_INTERVAL_MS)
            }
        }
    }

    private fun observeArtworkColors() {
        viewModelScope.launch {
            combine(library.tracks, playback.state, _externalLyrics) { tracks, player, external ->
                val listening = external.visible && external.nowPlaying != null
                if (listening) {
                    external.nowPlaying?.let { ArtworkRequest(it.key, external.artworkPath) }
                } else {
                    tracks.firstOrNull { it.id == player.currentMediaId }
                        ?.let { ArtworkRequest(it.id, it.coverPath) }
                }
            }.distinctUntilChanged().collectLatest { artwork ->
                artworkColors.value = artworkPaletteRepository.colorsFor(
                    seed = artwork?.trackId,
                    coverPath = artwork?.coverPath,
                )
            }
        }
    }

    private fun needsAutomaticLyrics(track: Track, ignoreQueued: Boolean = false): Boolean {
        if (track.hasLyrics) return false
        if (!ignoreQueued && track.id in automaticAttempts) return false
        if (track.lyricsLookupAt == 0L) return true
        val retryAfter = when (track.lyricsLookupStatus) {
            LyricsMatchStatus.NOT_FOUND.storageValue -> NOT_FOUND_RETRY_MS
            LyricsMatchStatus.UPSTREAM_ERROR.storageValue -> UPSTREAM_RETRY_MS
            else -> Long.MAX_VALUE
        }
        return System.currentTimeMillis() - track.lyricsLookupAt >= retryAfter
    }

    private suspend fun automaticMatch(track: Track) {
        try {
            val result = onlineLyricsRepository.match(track, includeCandidates = false)
            val selected = result.selected
            if (result.status == LyricsMatchStatus.MATCHED && selected != null) {
                library.storeLyrics(track.id, selected.toStoredLyrics())
            } else {
                library.markLyricsLookup(track.id, result.status.storageValue)
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            library.markLyricsLookup(track.id, LyricsMatchStatus.UPSTREAM_ERROR.storageValue)
        }
    }

    private fun playerTrack(id: String, player: PlaybackState): Track? {
        val title = player.currentTitle?.takeIf(String::isNotBlank) ?: return null
        val artwork = player.currentArtwork
        return Track(
            id = id,
            uri = player.currentUri.orEmpty(),
            title = title,
            artist = player.currentArtist?.takeIf(String::isNotBlank) ?: "未知艺术家",
            album = player.currentAlbum?.takeIf(String::isNotBlank) ?: "未知专辑",
            durationMs = player.durationMs,
            displayName = title,
            mimeType = null,
            sizeBytes = 0L,
            coverPath = artwork?.takeIf { it.startsWith("file://") }?.let { Uri.parse(it).path },
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
            artworkUrl = artwork?.takeIf { it.startsWith("http") },
        )
    }

    private fun sortTracks(tracks: List<Track>, sort: LibrarySortMode): List<Track> = when (sort) {
        LibrarySortMode.TITLE -> tracks.sortedWith(compareBy<Track> { SortKeys.of(it.title) }.thenBy { it.id })
        LibrarySortMode.ARTIST -> tracks.sortedWith(compareBy<Track> { SortKeys.of(it.artist) }.thenBy { SortKeys.of(it.title) })
        LibrarySortMode.ALBUM -> tracks.sortedWith(compareBy<Track> { SortKeys.of(it.album) }.then(AlbumOrder))
        LibrarySortMode.RECENT -> tracks.sortedWith(compareByDescending<Track> { it.addedAt }.thenBy { it.id })
        LibrarySortMode.PLAYS -> tracks.sortedWith(
            compareByDescending<Track> { it.playCount }.thenByDescending { it.lastPlayedAt }.thenBy { SortKeys.of(it.title) },
        )
        LibrarySortMode.TRACK_NUMBER -> tracks.sortedWith(
            compareBy<Track> { SortKeys.of(it.albumOwner) }.thenBy { SortKeys.of(it.album) }.then(AlbumOrder),
        )
    }

    private fun buildAlbums(tracks: List<Track>): List<com.olbyine.music.data.Album> =
        tracks.filterNot { it.isOnline }
            .groupBy { OnlineMetadataRepository.normalize(it.album) + "\u0000" + OnlineMetadataRepository.normalize(it.albumOwner) }
            .map { (key, songs) ->
                val ordered = songs.sortedWith(AlbumOrder)
                val head = ordered.first()
                com.olbyine.music.data.Album(
                    key = key,
                    title = head.album,
                    artist = head.albumOwner,
                    year = ordered.firstNotNullOfOrNull { it.year.takeIf { year -> year > 0 } } ?: 0,
                    tracks = ordered,
                )
            }
            .sortedWith(compareBy<com.olbyine.music.data.Album> { it.title == "未知专辑" }.thenBy { SortKeys.of(it.title) })

    private data class TransientState(
        val lyricsVisible: Boolean,
        val importing: Boolean,
        val onlineLyrics: OnlineLyricsUiState,
        val message: String?,
        val artworkColors: List<Int>,
    )

    private data class LibraryCore(
        val tracks: List<Track>,
        val tracksById: Map<String, Track>,
        val keys: Map<String, TrackSortKeys>,
        val loaded: Boolean,
        val playlists: List<Playlist>,
        val player: PlaybackState,
        val usb: UsbBitPerfectState,
        val settings: UserSettings,
    )

    private data class IndexedTracks(
        val tracks: List<Track>,
        val byId: Map<String, Track>,
        val keys: Map<String, TrackSortKeys>,
        val loaded: Boolean,
    )

    private data class LibrarySelection(
        val view: LibraryViewMode,
        val sort: LibrarySortMode,
        val folder: String?,
        val playlist: String?,
    )

    private data class ArtworkRequest(
        val trackId: String,
        val coverPath: String?,
    )

    private fun String.displaySource(): String = when (this) {
        "lrclib" -> "LRCLIB"
        "netease" -> "网易云"
        "qqmusic" -> "QQ 音乐"
        "kugou" -> "酷狗"
        "amll-ttml" -> "AMLL"
        "applemusic" -> "Apple Music"
        "meting" -> "Meting"
        "wwoyun" -> "Wwoyun"
        "lrcapi" -> "LrcApi"
        else -> this
    }

    private companion object {
        const val KEY_MEDIA_ID = "media_id"
        const val KEY_POSITION = "position"
        const val KEY_QUEUE = "queue"
    const val KEY_QUEUE_JSON = "queue_json"
    const val KEY_ORIGIN_KIND = "origin_kind"
    const val KEY_ORIGIN_ID = "origin_id"
    const val KEY_ORIGIN_TITLE = "origin_title"
        const val KEY_VIEW_MODE = "view_mode"
        const val KEY_SORT_MODE = "sort_mode"
        const val NOT_FOUND_RETRY_MS = 6 * 60 * 60_000L
        const val UPSTREAM_RETRY_MS = 30 * 60_000L
        const val AUTOMATIC_LYRICS_START_DELAY_MS = 2_000L
        const val AUTOMATIC_LYRICS_INTERVAL_MS = 1_500L
        const val MAX_FONT_BYTES = 256L * 1024 * 1024
        val TTML_ROOT = Regex("<(?:\\w+:)?tt(?:\\s|>)", RegexOption.IGNORE_CASE)
    }
}
