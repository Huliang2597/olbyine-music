@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.olbyine.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Stable
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olbyine.music.AlbumOrder
import com.olbyine.music.AppDestination
import com.olbyine.music.LibraryFilter
import com.olbyine.music.LibrarySortMode
import com.olbyine.music.LibraryViewMode
import com.olbyine.music.MainUiState
import com.olbyine.music.SortKeys
import com.olbyine.music.TrackSortKeys
import com.olbyine.music.data.Album
import com.olbyine.music.data.HapticArea
import com.olbyine.music.data.ImportProgress
import com.olbyine.music.data.Playlist
import com.olbyine.music.data.Track
import kotlinx.coroutines.launch

internal enum class LibrarySection(val label: String) {
    ALL("全部歌曲"),
    FAVORITES("收藏"),
    ALBUMS("专辑"),
    COMPOSERS("作曲人"),
    FOLDERS("文件夹"),
    PLAYLISTS("歌单"),
}

@Stable
internal class LibraryNav(section: LibrarySection, albumKey: String?, composerKey: String? = null) {
    var section by mutableStateOf(section)
    var albumKey by mutableStateOf(albumKey)
    var composerKey by mutableStateOf(composerKey)

    companion object {
        val Saver = androidx.compose.runtime.saveable.listSaver<LibraryNav, String?>(
            save = { listOf(it.section.name, it.albumKey, it.composerKey) },
            restore = { LibraryNav(LibrarySection.valueOf(it[0] ?: LibrarySection.ALL.name), it.getOrNull(1), it.getOrNull(2)) },
        )
    }
}

@Composable
internal fun rememberLibraryNav(favorites: Boolean): LibraryNav = rememberSaveable(saver = LibraryNav.Saver) {
    LibraryNav(if (favorites) LibrarySection.FAVORITES else LibrarySection.ALL, null)
}

private data class LibraryPage(
    val section: LibrarySection,
    val folderId: String?,
    val playlistId: String?,
    val albumKey: String?,
    val composerKey: String?,
) {
    val drilledIn: Boolean get() = folderId != null || playlistId != null || albumKey != null || composerKey != null
}

private fun pageTracks(page: LibraryPage, state: MainUiState, albums: List<Album>, query: String, playlistSort: LibrarySortMode?): List<Track> {
    val source = when (page.section) {
        LibrarySection.ALL, LibrarySection.FAVORITES -> state.visibleTracks
        LibrarySection.ALBUMS -> albums.firstOrNull { it.key == page.albumKey }?.tracks?.filter { it.matches(query) }.orEmpty()
        LibrarySection.COMPOSERS -> {
            val key = page.composerKey
            if (key == null) emptyList()
            else state.tracks.filter { track -> track.composerNames().any { it.equals(key, ignoreCase = true) } && track.matches(query) }
        }
        LibrarySection.FOLDERS -> state.tracks.filter { track ->
            val matchesFolder = if (page.folderId == "") track.folderId.isNullOrBlank() else track.folderId == page.folderId
            matchesFolder && track.matches(query)
        }
        LibrarySection.PLAYLISTS -> {
            val playlist = state.playlists.firstOrNull { it.id == page.playlistId }
            val tracksById = state.tracks.associateBy(Track::id)
            playlist?.trackIds.orEmpty().mapNotNull(tracksById::get).filter { it.matches(query) }
        }
    }
    return when (page.section) {
        LibrarySection.ALL, LibrarySection.FAVORITES, LibrarySection.ALBUMS, LibrarySection.COMPOSERS -> source
        LibrarySection.FOLDERS -> sortedForCollection(source, state.librarySortMode, state.trackKeys)
        LibrarySection.PLAYLISTS -> playlistSort?.let { sortedForCollection(source, it, state.trackKeys) } ?: source
    }
}

private fun sortedForCollection(source: List<Track>, mode: LibrarySortMode, keys: Map<String, TrackSortKeys>): List<Track> {
    fun title(track: Track) = keys[track.id]?.title ?: SortKeys.cachedOrPlain(track.title)
    fun artist(track: Track) = keys[track.id]?.artist ?: SortKeys.cachedOrPlain(track.artist)
    fun album(track: Track) = keys[track.id]?.album ?: SortKeys.cachedOrPlain(track.album)
    return when (mode) {
        LibrarySortMode.TITLE -> source.sortedWith(compareBy<Track> { title(it) }.thenBy { it.id })
        LibrarySortMode.ARTIST -> source.sortedWith(compareBy<Track> { artist(it) }.thenBy { title(it) })
        LibrarySortMode.ALBUM -> source.sortedWith(compareBy<Track> { album(it) }.then(AlbumOrder))
        LibrarySortMode.RECENT -> source.sortedWith(compareByDescending<Track> { it.addedAt }.thenBy { it.id })
        LibrarySortMode.PLAYS -> source.sortedWith(compareByDescending<Track> { it.playCount }.thenByDescending { it.lastPlayedAt }.thenBy { title(it) })
        LibrarySortMode.TRACK_NUMBER -> source.sortedWith(AlbumOrder)
    }
}

private class TopInsetShape(private val insetPx: Float) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline = androidx.compose.ui.graphics.Outline.Rectangle(
        androidx.compose.ui.geometry.Rect(0f, insetPx.coerceIn(0f, size.height), size.width, size.height),
    )
}

private class TrackCallbacks(
    val playlists: List<Playlist>,
    val activePlaylist: Playlist?,
    val onFavorite: (Track) -> Unit,
    val onDelete: (Track) -> Unit,
    val onEdit: (Track, String, String, String) -> Unit,
    val onImportLyrics: (String?) -> Unit,
    val onSearchLyrics: (Track) -> Unit,
    val onMatchMetadata: (Track) -> Unit,
    val onAddToPlaylist: (String, String) -> Unit,
    val onRemoveFromPlaylist: (String, String) -> Unit,
    val onReorderPlaylistTracks: (String, List<String>) -> Unit,
    val onPlayNext: (Track) -> Unit,
    val reorderable: Boolean = true,
)

private class Selection {
    var ids by mutableStateOf(emptySet<String>())
    val active: Boolean get() = ids.isNotEmpty()
    fun toggle(id: String) {
        ids = if (id in ids) ids - id else ids + id
    }
    fun clear() {
        ids = emptySet()
    }
}

// key() 放在独立的组合函数里：直接写在 LibraryScreen 中时，键值变化会让其后的 LaunchedEffect 重启，把刚开始的多选清空
@Composable
private fun LibraryBackHandlers(
    selecting: Boolean,
    searchOpen: Boolean,
    drilledIn: Boolean,
    underPlayer: Boolean,
    onClearSelection: () -> Unit,
    onCloseSearch: () -> Unit,
    onBack: () -> Unit,
) {
    androidx.compose.runtime.key(selecting, searchOpen, drilledIn, underPlayer) {
        BackHandler(enabled = selecting && !underPlayer, onBack = onClearSelection)
        BackHandler(enabled = !selecting && searchOpen && !underPlayer, onBack = onCloseSearch)
        BackHandler(enabled = !selecting && !searchOpen && drilledIn && !underPlayer, onBack = onBack)
    }
}

@Composable
internal fun LibraryScreen(
    state: MainUiState,
    albums: List<Album>,
    importProgress: ImportProgress?,
    bottomContentPadding: Dp,
    onSearch: (String) -> Unit,
    onFilter: (LibraryFilter) -> Unit,
    onPlayTracks: (List<Track>, Int, com.olbyine.music.PlayOrigin?) -> Unit,
    onPlayNext: (Track) -> Unit,
    onPlayInOrder: (List<Track>) -> Unit,
    onShufflePlay: (List<Track>) -> Unit,
    onFavorite: (Track) -> Unit,
    onDelete: (Track) -> Unit,
    onDeleteTracks: (Collection<String>) -> Unit,
    onAddTracksToPlaylist: (String, List<String>) -> Unit,
    onEdit: (Track, String, String, String) -> Unit,
    onImportAudio: () -> Unit,
    onImportFolder: () -> Unit,
    onImportLyrics: (String?) -> Unit,
    onSearchLyrics: (Track) -> Unit,
    onViewMode: (LibraryViewMode) -> Unit,
    onSortMode: (LibrarySortMode) -> Unit,
    onFolder: (String?) -> Unit,
    onPlaylist: (String?) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onAddToPlaylist: (String, String) -> Unit,
    onRemoveFromPlaylist: (String, String) -> Unit,
    onReorderPlaylistTracks: (String, List<String>) -> Unit,
    onReorderPlaylists: (List<String>) -> Unit,
    onMatchMetadata: (Track) -> Unit,
    onImportNeteasePlaylist: () -> Unit,
    onExternalLyrics: () -> Unit,
    albumRequest: String? = null,
    onAlbumRequestHandled: () -> Unit = {},
    composerRequest: String? = null,
    onComposerRequestHandled: () -> Unit = {},
    revealTrackId: com.olbyine.music.LibraryReveal? = null,
    onRevealHandled: () -> Unit = {},
    onPlayOrigin: (com.olbyine.music.PlayOrigin) -> Unit = {},
    nav: LibraryNav = rememberLibraryNav(state.libraryFilter == LibraryFilter.FAVORITES),
    onAlbumStyle: (String) -> Unit = {},
) {
    var section by nav::section
    var albumKey by nav::albumKey
    var composerKey by nav::composerKey
    LaunchedEffect(albumRequest) {
        val request = albumRequest ?: return@LaunchedEffect
        section = LibrarySection.ALBUMS
        onFolder(null)
        onPlaylist(null)
        onFilter(LibraryFilter.ALL)
        albumKey = request
        onAlbumRequestHandled()
    }
    LaunchedEffect(composerRequest) {
        val request = composerRequest?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        section = LibrarySection.COMPOSERS
        albumKey = null
        onFolder(null)
        onPlaylist(null)
        onFilter(LibraryFilter.ALL)
        composerKey = request
        onComposerRequestHandled()
    }
    LaunchedEffect(revealTrackId, state.libraryLoaded) {
        val request = revealTrackId ?: return@LaunchedEffect
        val target = state.tracks.firstOrNull { it.id == request.trackId }
        if (target == null) {
            if (state.libraryLoaded) onRevealHandled()
            return@LaunchedEffect
        }
        albumKey = null
        composerKey = null
        onFolder(null)
        onPlaylist(null)
        onFilter(LibraryFilter.ALL)
        when (request.kind) {
            "playlist" -> {
                section = LibrarySection.PLAYLISTS
                onPlaylist(request.id.takeIf { it.isNotBlank() })
            }
            "album" -> {
                section = LibrarySection.ALBUMS
                albumKey = request.id.takeIf { it.isNotBlank() }
            }
            else -> {
                section = LibrarySection.FOLDERS
                onFolder(target.folderId ?: "")
            }
        }
    }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var playlistSort by rememberSaveable { mutableStateOf<LibrarySortMode?>(null) }
    var creatingPlaylist by remember { mutableStateOf(false) }
    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var deletingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var addingSongs by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var addSelectionToPlaylist by remember { mutableStateOf(false) }
    val selection = remember { Selection() }
    val haptics = rememberHaptics()
    val focusManager = LocalFocusManager.current

    val selectedPlaylist = state.playlists.firstOrNull { it.id == state.activePlaylistId }
    val query = state.search.trim()
    val openAlbum = remember(albums, albumKey) { albums.firstOrNull { it.key == albumKey } }
    val composers = remember(state.tracks) { buildComposers(state.tracks) }
    val openComposer = remember(composers, composerKey) {
        val requested = composerKey
        val wanted = requested?.let { com.olbyine.music.data.ComposerTags.normalize(it) }
        composers.firstOrNull { it.name == requested }
            ?: composers.firstOrNull { wanted != null && wanted.isNotEmpty() && com.olbyine.music.data.ComposerTags.normalize(it.name) == wanted }
    }
    val currentPage = LibraryPage(
        section = section,
        folderId = state.libraryFolderId.takeIf { section == LibrarySection.FOLDERS },
        playlistId = state.activePlaylistId.takeIf { section == LibrarySection.PLAYLISTS },
        albumKey = albumKey.takeIf { section == LibrarySection.ALBUMS && openAlbum != null },
        composerKey = composerKey.takeIf { section == LibrarySection.COMPOSERS && openComposer != null },
    )
    val displayedTracks = remember(currentPage, state.tracks, state.visibleTracks, state.playlists, albums, query, state.librarySortMode, state.trackKeys, playlistSort) {
        pageTracks(currentPage, state, albums, query, playlistSort)
    }
    val albumGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    var coverFlowIndex by rememberSaveable { mutableIntStateOf(0) }
    val favoriteCount = remember(state.tracks) { state.tracks.count(Track::favorite) }

    val folderOpen = section == LibrarySection.FOLDERS && state.libraryFolderId != null
    val playlistOpen = section == LibrarySection.PLAYLISTS && selectedPlaylist != null
    val albumOpen = section == LibrarySection.ALBUMS && openAlbum != null
    val composerOpen = section == LibrarySection.COMPOSERS && openComposer != null
    val underPlayer = state.destination == AppDestination.NOW_PLAYING
    val currentOrigin: () -> com.olbyine.music.PlayOrigin = {
        when {
            playlistOpen && selectedPlaylist != null -> com.olbyine.music.PlayOrigin("playlist", selectedPlaylist.id, selectedPlaylist.name)
            albumOpen && openAlbum != null -> com.olbyine.music.PlayOrigin("album", openAlbum.key, openAlbum.title)
            composerOpen && openComposer != null -> com.olbyine.music.PlayOrigin("composer", openComposer.name, openComposer.name)
            else -> com.olbyine.music.PlayOrigin("folder", currentPage.folderId ?: "")
        }
    }
    val playFromList: (List<Track>, Int) -> Unit = { tracks, index ->
        onPlayTracks(tracks, index, currentOrigin())
    }
    val playAllFromList: (List<Track>) -> Unit = { tracks ->
        onPlayOrigin(currentOrigin())
        onPlayInOrder(tracks)
    }
    val shuffleList: (List<Track>) -> Unit = { tracks ->
        onPlayOrigin(currentOrigin())
        onShufflePlay(tracks)
    }
    LibraryBackHandlers(
        selecting = selection.active,
        searchOpen = searchOpen,
        drilledIn = folderOpen || playlistOpen || albumOpen || composerOpen,
        underPlayer = underPlayer,
        onClearSelection = { selection.clear() },
        onCloseSearch = {
            searchOpen = false
            onSearch("")
            focusManager.clearFocus()
        },
        onBack = {
            when {
                folderOpen -> onFolder(null)
                playlistOpen -> onPlaylist(null)
                composerOpen -> composerKey = null
                else -> albumKey = null
            }
        },
    )
    LaunchedEffect(section, state.libraryFolderId, state.activePlaylistId, albumKey, composerKey) { selection.clear() }

    val callbacks = TrackCallbacks(
        playlists = state.playlists,
        activePlaylist = selectedPlaylist,
        onFavorite = onFavorite,
        onDelete = onDelete,
        onEdit = onEdit,
        onImportLyrics = onImportLyrics,
        onSearchLyrics = onSearchLyrics,
        onMatchMetadata = onMatchMetadata,
        onAddToPlaylist = onAddToPlaylist,
        onRemoveFromPlaylist = onRemoveFromPlaylist,
        onReorderPlaylistTracks = onReorderPlaylistTracks,
        onPlayNext = onPlayNext,
        reorderable = playlistSort == null,
    )
    val showsTracks = section == LibrarySection.ALL || section == LibrarySection.FAVORITES || folderOpen || playlistOpen || albumOpen || composerOpen
    val selectSection: (LibrarySection) -> Unit = { item ->
        if (item != section) haptics.tick(HapticArea.MENUS)
        section = item
        albumKey = null
        composerKey = null
        onFolder(null)
        onPlaylist(null)
        onFilter(if (item == LibrarySection.FAVORITES) LibraryFilter.FAVORITES else LibraryFilter.ALL)
    }

    val density = LocalDensity.current
    val headerSource = rememberBackdropSource()
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    var headerContentPx by remember { mutableIntStateOf(0) }
    var collapsiblePx by remember { mutableIntStateOf(0) }
    val headerOffset = remember { mutableFloatStateOf(0f) }
    val scrolled = remember { mutableFloatStateOf(0f) }
    val collapse = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                headerOffset.floatValue = (headerOffset.floatValue + consumed.y).coerceIn(-collapsiblePx.toFloat(), 0f)
                scrolled.floatValue = (scrolled.floatValue - consumed.y).coerceAtLeast(0f)
                if (available.y > 0f) {
                    scrolled.floatValue = 0f
                    headerOffset.floatValue = 0f
                }
                return Offset.Zero
            }
        }
    }
    val overviewHeader = remember { floatArrayOf(0f, 0f) }
    var wasDrilled by remember { mutableStateOf(false) }
    LaunchedEffect(section, state.libraryFolderId, state.activePlaylistId, albumKey, composerKey, state.librarySortMode, state.libraryViewMode) {
        val drilled = currentPage.drilledIn
        when {
            drilled && !wasDrilled -> {
                overviewHeader[0] = headerOffset.floatValue
                overviewHeader[1] = scrolled.floatValue
                scrolled.floatValue = 0f
                headerOffset.floatValue = 0f
            }
            !drilled && wasDrilled -> {
                headerOffset.floatValue = overviewHeader[0]
                scrolled.floatValue = overviewHeader[1]
            }
            else -> {
                scrolled.floatValue = 0f
                headerOffset.floatValue = 0f
            }
        }
        wasDrilled = drilled
    }
    val topPadding = (with(density) { (statusBarPx + headerContentPx).toDp() } - BarEdge).coerceAtLeast(0.dp)
    val frosted = frostedBarStyle()

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(collapse)
                .backdropSource(headerSource),
        ) {
            @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
            androidx.compose.animation.SharedTransitionLayout(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = currentPage,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val sameSection = initialState.section == targetState.section
                    when {
                        sameSection && !initialState.drilledIn && targetState.drilledIn ->
                            (fadeIn(tween(220, delayMillis = 40, easing = OlbyineTokens.Motion.easeOut)) +
                                slideInVertically(tween(300, easing = OlbyineTokens.Motion.easeOut)) { it / 14 })
                                .togetherWith(fadeOut(tween(160)) + scaleOut(tween(260, easing = OlbyineTokens.Motion.easeOut), targetScale = .94f))
                        sameSection && initialState.drilledIn && !targetState.drilledIn ->
                            (fadeIn(tween(200, delayMillis = 30, easing = OlbyineTokens.Motion.easeOut)) +
                                scaleIn(tween(260, easing = OlbyineTokens.Motion.easeOut), initialScale = .94f))
                                .togetherWith(fadeOut(tween(140)) + slideOutVertically(tween(220, easing = OlbyineTokens.Motion.easeOut)) { it / 14 })
                        else -> fadeIn(tween(OlbyineTokens.Motion.standardMs, easing = OlbyineTokens.Motion.easeOut))
                            .togetherWith(fadeOut(tween(OlbyineTokens.Motion.quickMs)))
                    }
                },
                label = "library-section",
            ) { page ->
                val section = page.section
                val openAlbum = remember(albums, page.albumKey) { albums.firstOrNull { it.key == page.albumKey } }
                val albumOpen = section == LibrarySection.ALBUMS && openAlbum != null
                val openComposer = remember(composers, page.composerKey) { composers.firstOrNull { it.name == page.composerKey } }
                val composerOpen = section == LibrarySection.COMPOSERS && openComposer != null
                val libraryFolderId = page.folderId
                val folderOpen = section == LibrarySection.FOLDERS && libraryFolderId != null
                val selectedPlaylist = state.playlists.firstOrNull { it.id == page.playlistId }
                val playlistOpen = section == LibrarySection.PLAYLISTS && selectedPlaylist != null
                val displayedTracks = remember(page, state.tracks, state.visibleTracks, state.playlists, albums, query, state.librarySortMode, state.trackKeys, playlistSort) {
                    pageTracks(page, state, albums, query, playlistSort)
                }
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalSharedAlbum provides SharedAlbumScope(this@SharedTransitionLayout, this@AnimatedContent),
                ) {
                when {
                    !state.libraryLoaded && state.tracks.isEmpty() -> LibraryLoading(Modifier.fillMaxSize().padding(top = topPadding))
                    section == LibrarySection.ALBUMS && openAlbum == null -> {
                        val visibleAlbums = remember(albums, query) {
                            if (query.isBlank()) albums else albums.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
                        }
                        when (state.settings.albumStyle) {
                            "sphere" -> AlbumSphere(
                                albums = visibleAlbums,
                                currentTrackId = state.currentTrack?.id?.takeIf { state.playback.isPlaying },
                                onAlbum = { albumKey = it.key },
                                onPlay = { onPlayInOrder(it.tracks) },
                                topContentPadding = topPadding,
                                bottomContentPadding = bottomContentPadding,
                                onShuffle = { onShufflePlay(it.tracks) },
                            )
                            "coverflow" -> CoverFlow(
                                albums = visibleAlbums,
                                currentTrackId = state.currentTrack?.id?.takeIf { state.playback.isPlaying },
                                index = coverFlowIndex,
                                onIndex = { coverFlowIndex = it },
                                onAlbum = { albumKey = it.key },
                                onPlay = { onPlayInOrder(it.tracks) },
                                topContentPadding = topPadding,
                                bottomContentPadding = bottomContentPadding,
                            )
                            else -> AlbumGrid(
                                albums = albums,
                                query = query,
                                currentTrackId = state.currentTrack?.id?.takeIf { state.playback.isPlaying },
                                gridState = albumGridState,
                                onAlbum = { albumKey = it.key },
                                topContentPadding = topPadding,
                                bottomContentPadding = bottomContentPadding,
                            )
                        }
                    }
                    section == LibrarySection.COMPOSERS && openComposer == null -> ComposerGrid(
                        composers = composers,
                        query = query,
                        onComposer = { composerKey = it.name },
                        topContentPadding = topPadding,
                        bottomContentPadding = bottomContentPadding,
                    )
                    section == LibrarySection.FOLDERS && state.libraryFolderId == null -> FolderBrowser(
                        tracks = state.tracks,
                        query = query,
                        onFolder = onFolder,
                        onScan = onImportFolder,
                        topContentPadding = topPadding,
                        bottomContentPadding = bottomContentPadding,
                    )
                    section == LibrarySection.PLAYLISTS && selectedPlaylist == null -> PlaylistBrowser(
                        playlists = state.playlists,
                        tracks = state.tracks,
                        query = query,
                        onPlaylist = onPlaylist,
                        onReorder = onReorderPlaylists,
                        onCreate = { creatingPlaylist = true },
                        onEdit = { editingPlaylist = it },
                        onDelete = { deletingPlaylist = it },
                        topContentPadding = topPadding,
                        bottomContentPadding = bottomContentPadding,
                    )
                    else -> {
                        val header: @Composable () -> Unit = when {
                            albumOpen && openAlbum != null -> {
                                {
                                    CollectionHeader(
                                        title = openAlbum.title,
                                        subtitle = listOfNotNull(
                                            openAlbum.artist,
                                            openAlbum.year.takeIf { it > 0 }?.toString(),
                                            "${openAlbum.tracks.size} 首",
                                            formatTotalDuration(openAlbum.durationMs),
                                        ).joinToString(" · "),
                                        cover = { TrackArtwork(openAlbum.cover, Modifier.size(128.dp).sharedAlbumCover(openAlbum.key), 18.dp, decodeEdge = 512) },
                                        onBack = { albumKey = null },
                                        tracks = displayedTracks,
                                        onPlay = { playAllFromList(displayedTracks) },
                                        onShuffle = { shuffleList(displayedTracks) },
                                        sortMode = null,
                                        onSortMode = onSortMode,
                                    )
                                }
                            }
                            composerOpen && openComposer != null -> {
                                {
                                    CollectionHeader(
                                        title = openComposer.name,
                                        subtitle = openComposer.credit?.let { "${openComposer.tracks.size} 首 · 作曲 $it" } ?: "${openComposer.tracks.size} 首 · 作曲",
                                        cover = { CoverMosaic(openComposer.tracks.take(4), Modifier.size(112.dp), 18.dp, openComposer.name) },
                                        onBack = { composerKey = null },
                                        tracks = displayedTracks,
                                        onFanCard = { track -> playFromList(displayedTracks, displayedTracks.indexOf(track).coerceAtLeast(0)) },
                                        onPlay = { playAllFromList(displayedTracks) },
                                        onShuffle = { shuffleList(displayedTracks) },
                                        sortMode = state.librarySortMode,
                                        onSortMode = onSortMode,
                                    )
                                }
                            }
                            folderOpen -> {
                                {
                                    val folderTracks = state.tracks.filter {
                                        if (libraryFolderId == "") it.folderId.isNullOrBlank() else it.folderId == libraryFolderId
                                    }
                                    val path = folderTracks.firstOrNull()?.folderPath?.ifBlank { null } ?: "未分类文件"
                                    CollectionHeader(
                                        title = folderName(path),
                                        subtitle = "${folderTracks.size} 首 · $path",
                                        cover = { CoverMosaic(folderTracks.take(4), Modifier.size(112.dp), 18.dp, path) },
                                        onBack = { onFolder(null) },
                                        tracks = displayedTracks,
                                        onFanCard = { track -> playFromList(displayedTracks, displayedTracks.indexOf(track).coerceAtLeast(0)) },
                                        onPlay = { playAllFromList(displayedTracks) },
                                        onShuffle = { shuffleList(displayedTracks) },
                                        sortMode = state.librarySortMode,
                                        onSortMode = onSortMode,
                                    )
                                }
                            }
                            selectedPlaylist != null && playlistOpen -> {
                                {
                                    val tracksById = remember(state.tracks) { state.tracks.associateBy(Track::id) }
                                    val playlistTracks = selectedPlaylist.trackIds.mapNotNull(tracksById::get)
                                    CollectionHeader(
                                        title = selectedPlaylist.name,
                                        subtitle = "${playlistTracks.size} 首 · ${formatTotalDuration(playlistTracks.sumOf { it.durationMs })}",
                                        cover = { CoverMosaic(playlistTracks.take(4), Modifier.size(112.dp), 18.dp, selectedPlaylist.id) },
                                        onBack = { onPlaylist(null) },
                                        tracks = displayedTracks,
                                        onFanCard = { track -> playFromList(displayedTracks, displayedTracks.indexOf(track).coerceAtLeast(0)) },
                                        onPlay = { playAllFromList(displayedTracks) },
                                        onShuffle = { shuffleList(displayedTracks) },
                                        sortMode = playlistSort,
                                        onSortMode = { playlistSort = it },
                                        onCustomOrder = { playlistSort = null },
                                        extraAction = {
                                            OlbyineIconButton(Icons.Rounded.Add, "管理歌单歌曲", { addingSongs = true })
                                        },
                                    )
                                }
                            }
                            else -> {
                                {
                                    CollectionActions(
                                        count = displayedTracks.size,
                                        totalMs = displayedTracks.sumOf { it.durationMs },
                                        enabled = displayedTracks.isNotEmpty(),
                                        onPlay = { playAllFromList(displayedTracks) },
                                        onShuffle = { shuffleList(displayedTracks) },
                                    )
                                }
                            }
                        }
                        val empty: (@Composable () -> Unit)? = if (displayedTracks.isNotEmpty()) null else {
                            {
                                when {
                                    query.isNotEmpty() -> EmptyState(Icons.Rounded.Search, "没有找到「$query」", message = "换个关键词试试")
                                    section == LibrarySection.ALL -> EmptyState(
                                        Icons.Rounded.LibraryMusic,
                                        "音乐库还是空的",
                                        message = "导入本地音频文件，或扫描整个文件夹",
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            PillButton("选择文件", onImportAudio, icon = Icons.Rounded.AudioFile)
                                            PillButton("扫描文件夹", onImportFolder, icon = Icons.Rounded.FolderOpen, style = PillStyle.TONAL)
                                        }
                                    }
                                    section == LibrarySection.FAVORITES -> EmptyState(
                                        Icons.Rounded.FavoriteBorder,
                                        "还没有收藏",
                                        message = "在播放页点亮爱心，喜欢的歌会出现在这里",
                                    )
                                    playlistOpen -> EmptyState(
                                        Icons.AutoMirrored.Rounded.QueueMusic,
                                        "歌单还是空的",
                                    ) { PillButton("添加歌曲", { addingSongs = true }, icon = Icons.Rounded.Add) }
                                    else -> EmptyState(Icons.Rounded.FolderOpen, "这里还没有歌曲")
                                }
                            }
                        }
                        if (state.libraryViewMode == LibraryViewMode.TILES && !albumOpen && !composerOpen && displayedTracks.isNotEmpty() && !selection.active) {
                            val currentId = state.currentTrack?.id
                            val wallItems = remember(displayedTracks, currentId) {
                                displayedTracks.map { WallItem(it.id, it.title, it.artist, it, it.id == currentId) }
                            }
                            TileWall(
                                newItems = wallItems,
                                playback = state.playback,
                                onClick = { index -> playFromList(displayedTracks, index) },
                                topContentPadding = topPadding,
                                bottomContentPadding = bottomContentPadding,
                                header = header,
                                revealIndex = revealTrackId?.let { request -> displayedTracks.indexOfFirst { it.id == request.trackId } }?.takeIf { it >= 0 },
                                onRevealed = onRevealHandled,
                            )
                        } else TrackCollection(
                            tracks = displayedTracks,
                            keys = state.trackKeys,
                            viewMode = if (albumOpen || composerOpen || state.libraryViewMode == LibraryViewMode.TILES) LibraryViewMode.LIST else state.libraryViewMode,
                            sortMode = if (playlistOpen) playlistSort else state.librarySortMode,
                            indexEnabled = section == LibrarySection.ALL || section == LibrarySection.FAVORITES,
                            albumStyle = albumOpen,
                            currentTrackId = state.currentTrack?.id,
                            isPlaying = state.playback.isPlaying,
                            callbacks = callbacks,
                            selection = selection,
                            onPlayTracks = playFromList,
                            header = header,
                            empty = empty,
                            revealId = revealTrackId,
                            onRevealed = onRevealHandled,
                            topContentPadding = topPadding,
                            bottomContentPadding = bottomContentPadding,
                        )
                    }
                }
                }
            }
            }
        }

        val barAlpha by rememberUpdatedState(rememberBarAlpha(24.dp) { scrolled.floatValue })
        val headerDynamics = remember {
            GlassDynamics(
                visibleHeight = { statusBarPx + headerContentPx + headerOffset.floatValue },
                materialAlpha = { barAlpha() },
            )
        }
        val material = LocalStyleSpec.current.style == com.olbyine.music.data.UiStyle.MATERIAL
        Box(Modifier.fillMaxWidth().glass(headerSource, frosted, dynamics = headerDynamics)) {
        androidx.compose.runtime.CompositionLocalProvider(LocalGlassSource provides headerSource) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = with(density) { statusBarPx.toDp() })
                    .onSizeChanged { headerContentPx = it.height }
                    .graphicsLayer {
                        translationY = headerOffset.floatValue
                        clip = true
                        shape = TopInsetShape(-headerOffset.floatValue)
                        if (material && collapsiblePx > 0) {
                            alpha = (1f + headerOffset.floatValue / collapsiblePx * 1.4f).coerceIn(0f, 1f)
                        }
                    },
            ) {
                Column(Modifier.fillMaxWidth().onSizeChanged { collapsiblePx = it.height }) {
                    AnimatedContent(
                        targetState = when {
                            selection.active -> HeaderMode.SELECTING
                            searchOpen -> HeaderMode.SEARCHING
                            else -> HeaderMode.TITLE
                        },
                        transitionSpec = {
                            (fadeIn(tween(200)) + scaleIn(initialScale = .97f, animationSpec = tween(220, easing = OlbyineTokens.Motion.easeOut)))
                                .togetherWith(fadeOut(tween(120)))
                                .using(SizeTransform(clip = false))
                        },
                        label = "library-header",
                    ) { mode ->
                        when (mode) {
                            HeaderMode.SELECTING -> SelectionBar(
                                count = selection.ids.size,
                                total = displayedTracks.size,
                                onClose = { selection.clear() },
                                onSelectAll = {
                                    haptics.tick(HapticArea.SCRUB)
                                    selection.ids = if (selection.ids.size == displayedTracks.size) emptySet() else displayedTracks.mapTo(HashSet()) { it.id }
                                },
                                onAddToPlaylist = { addSelectionToPlaylist = true },
                                onDelete = { confirmDelete = true },
                            )
                            HeaderMode.SEARCHING -> SearchHeader(
                                value = state.search,
                                onValueChange = onSearch,
                                onClose = {
                                    searchOpen = false
                                    onSearch("")
                                    focusManager.clearFocus()
                                },
                            )
                            HeaderMode.TITLE -> TitleHeader(
                                section = section,
                                count = when {
                                    section == LibrarySection.ALBUMS && !albumOpen -> albums.size
                                    section == LibrarySection.COMPOSERS && !composerOpen -> composers.size
                                    section == LibrarySection.PLAYLISTS && !playlistOpen -> state.playlists.size
                                    else -> displayedTracks.size
                                },
                                favoriteCount = favoriteCount,
                                showsTracks = showsTracks,
                                viewMode = state.libraryViewMode,
                                sortMode = if (playlistOpen) playlistSort else state.librarySortMode,
                                canSelect = showsTracks && displayedTracks.isNotEmpty(),
                                onSection = selectSection,
                                onSortMode = { mode -> haptics.tick(HapticArea.MENUS); if (playlistOpen) playlistSort = mode else onSortMode(mode) },
                                onCustomOrder = if (playlistOpen) { { haptics.tick(HapticArea.MENUS); playlistSort = null } } else null,
                                onViewMode = onViewMode,
                                albumStyle = if (section == LibrarySection.ALBUMS && !albumOpen) state.settings.albumStyle else null,
                                onAlbumStyle = { style -> haptics.tick(HapticArea.MENUS); onAlbumStyle(style) },
                                onSearch = { searchOpen = true },
                                onStartSelection = { displayedTracks.firstOrNull()?.let { selection.ids = setOf(it.id) } },
                                onImportAudio = onImportAudio,
                                onImportFolder = onImportFolder,
                                onImportLyrics = { onImportLyrics(null) },
                                onCreatePlaylist = { creatingPlaylist = true },
                                onImportNetease = onImportNeteasePlaylist,
                                onExternalLyrics = onExternalLyrics,
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    importProgress != null || state.importing,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    ImportProgressBar(importProgress)
                }
                Spacer(Modifier.height(6.dp + BarEdge))
            }
        }
        }
    }

    if (creatingPlaylist) PlaylistNameDialog("新建歌单", "", { creatingPlaylist = false }) { name ->
        section = LibrarySection.PLAYLISTS
        onFilter(LibraryFilter.ALL)
        onCreatePlaylist(name); creatingPlaylist = false
    }
    editingPlaylist?.let { playlist ->
        PlaylistNameDialog("重命名歌单", playlist.name, { editingPlaylist = null }) { name ->
            onRenamePlaylist(playlist.id, name); editingPlaylist = null
        }
    }
    deletingPlaylist?.let { playlist ->
        OlbyineDialog(
            onDismissRequest = { deletingPlaylist = null },
            title = "删除歌单？",
            confirmButton = {
                GlassTextButton(destructive = true, onClick = { onDeletePlaylist(playlist.id); deletingPlaylist = null }) {
                    Text("删除")
                }
            },
            dismissButton = { GlassTextButton(onClick = { deletingPlaylist = null }) { Text("取消") } },
        ) { Text("删除「${playlist.name}」只会移除歌单，歌曲仍保留在音乐库。") }
    }
    if (addingSongs && selectedPlaylist != null) {
        PlaylistSongsDialog(selectedPlaylist, state.tracks,
            onDismiss = { addingSongs = false }, onAdd = onAddToPlaylist, onRemove = onRemoveFromPlaylist)
    }
    if (confirmDelete) {
        val count = selection.ids.size
        OlbyineDialog(
            onDismissRequest = { confirmDelete = false },
            title = "移除 $count 首歌曲？",
            confirmButton = {
                GlassTextButton(destructive = true, onClick = {
                    haptics.heavy(HapticArea.MENUS)
                    onDeleteTracks(selection.ids)
                    selection.clear()
                    confirmDelete = false
                }) { Text("移除") }
            },
            dismissButton = { GlassTextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        ) { Text("这些歌曲会从音乐库和歌单中移除，原音频文件不会被删除。") }
    }
    if (addSelectionToPlaylist) {
        OlbyineDialog(
            onDismissRequest = { addSelectionToPlaylist = false },
            title = "加入歌单",
            confirmButton = { GlassTextButton(onClick = { addSelectionToPlaylist = false }) { Text("取消") } },
        ) {
            if (state.playlists.isEmpty()) Text("还没有歌单。点击右上角的 + 新建一个。")
            else LazyColumn(Modifier.heightIn(max = 340.dp)) {
                items(state.playlists, key = Playlist::id) { playlist ->
                    Row(
                        itemMotion().fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                            onAddTracksToPlaylist(playlist.id, displayedTracks.filter { it.id in selection.ids }.map { it.id })
                            selection.clear()
                            addSelectionToPlaylist = false
                        }.padding(horizontal = 6.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StyledIcon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(playlist.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${playlist.trackIds.size} 首", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private enum class HeaderMode { TITLE, SEARCHING, SELECTING }

@Composable
private fun TitleHeader(
    section: LibrarySection,
    count: Int,
    favoriteCount: Int,
    showsTracks: Boolean,
    viewMode: LibraryViewMode,
    sortMode: LibrarySortMode?,
    canSelect: Boolean,
    onSection: (LibrarySection) -> Unit,
    onSortMode: (LibrarySortMode) -> Unit,
    onCustomOrder: (() -> Unit)?,
    onViewMode: (LibraryViewMode) -> Unit,
    albumStyle: String?,
    onAlbumStyle: (String) -> Unit,
    onSearch: () -> Unit,
    onStartSelection: () -> Unit,
    onImportAudio: () -> Unit,
    onImportFolder: () -> Unit,
    onImportLyrics: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onImportNetease: () -> Unit,
    onExternalLyrics: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val openMenu = { menu = true }
    Row(
        Modifier.fillMaxWidth().padding(start = OlbyineTokens.Spacing.page, end = 14.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("音乐库", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
            Row(
                Modifier.clip(CircleShape).clickable(onClick = openMenu).padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${section.label} · $count ${when (section) { LibrarySection.ALBUMS -> "张"; LibrarySection.COMPOSERS, LibrarySection.PLAYLISTS -> "个"; else -> "首" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                StyledIcon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OlbyineIconButton(Icons.Rounded.Search, "搜索", {
                haptics.light(HapticArea.MENUS)
                onSearch()
            })
            Box {
                OlbyineIconButton(Icons.Rounded.Tune, "分类与排序", openMenu)
                OlbyineDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenuLabel("分类")
                    LibrarySection.entries.forEach { item ->
                        GlassMenuItem(
                            if (item == LibrarySection.FAVORITES && favoriteCount > 0) "收藏 · $favoriteCount" else item.label,
                            { menu = false; onSection(item) },
                            icon = when (item) {
                                LibrarySection.ALL -> Icons.Rounded.MusicNote
                                LibrarySection.FAVORITES -> Icons.Rounded.Favorite
                                LibrarySection.ALBUMS -> Icons.Rounded.Album
                                LibrarySection.COMPOSERS -> Icons.Rounded.EditNote
                                LibrarySection.FOLDERS -> Icons.Rounded.FolderOpen
                                LibrarySection.PLAYLISTS -> Icons.AutoMirrored.Rounded.QueueMusic
                            },
                            selected = item == section,
                        )
                    }
                    if (albumStyle != null) {
                        MenuDivider()
                        MenuLabel("专辑样式")
                        listOf(
                            Triple("grid", "网格", Icons.Rounded.GridView),
                            Triple("coverflow", "Cover Flow（iPod）", Icons.Rounded.ViewCarousel),
                            Triple("sphere", "球形（可旋转）", Icons.Rounded.Public),
                        ).forEach { (key, label, icon) ->
                            GlassMenuItem(label, { menu = false; onAlbumStyle(key) }, icon = icon, selected = key == albumStyle)
                        }
                    }
                    if (showsTracks) {
                        MenuDivider()
                        MenuLabel("排序")
                        if (onCustomOrder != null) {
                            GlassMenuItem("自定义顺序", { menu = false; onCustomOrder() }, selected = sortMode == null)
                        }
                        LibrarySortMode.entries.forEach { mode ->
                            GlassMenuItem(mode.label, { menu = false; onSortMode(mode) }, selected = mode == sortMode)
                        }
                        MenuDivider()
                        MenuLabel("视图")
                        listOf(
                            Triple(LibraryViewMode.LIST, "列表", Icons.AutoMirrored.Rounded.ViewList),
                            Triple(LibraryViewMode.GRID, "网格", Icons.Rounded.GridView),
                            Triple(LibraryViewMode.TILES, "磁贴墙", Icons.Rounded.Dashboard),
                        ).forEach { (mode, label, icon) ->
                            GlassMenuItem(label, { menu = false; onViewMode(mode) }, icon = icon, selected = mode == viewMode)
                        }
                        MenuDivider()
                        GlassMenuItem("多选", { menu = false; onStartSelection() }, icon = Icons.Rounded.DoneAll, enabled = canSelect)
                    }
                }
            }
            AddMenu(onImportAudio, onImportFolder, onImportLyrics, onCreatePlaylist, onImportNetease, onExternalLyrics)
        }
    }
}

@Composable
private fun MenuLabel(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SearchHeader(value: String, onValueChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OlbyineSearchField(
            value = value,
            onValueChange = onValueChange,
            placeholder = "搜索歌曲、艺术家或专辑",
            modifier = Modifier.weight(1f).focusRequester(focus),
        )
        GlassTextButton(onClick = onClose) { Text("取消") }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    total: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GhostIconButton(Icons.Rounded.Close, "退出多选", onClose)
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text("已选择 $count 首", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                if (count == total) "取消全选" else "全选（$total）",
                Modifier.clip(CircleShape).clickable(onClick = onSelectAll).padding(vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        OlbyineIconButton(Icons.AutoMirrored.Rounded.PlaylistAdd, "加入歌单", onAddToPlaylist)
        Spacer(Modifier.width(8.dp))
        OlbyineIconButton(
            Icons.Rounded.DeleteOutline,
            "从音乐库移除",
            onDelete,
            container = MaterialTheme.colorScheme.error.copy(alpha = .14f),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ImportProgressBar(progress: ImportProgress?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = OlbyineTokens.Spacing.page, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(progress?.phase ?: "正在导入…", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (progress != null && progress.total > 0) {
                Text(
                    "${progress.done} / ${progress.total}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        if (progress != null && progress.total > 0) {
            OlbyineProgressBar(
                progress = { progress.done.toFloat() / progress.total },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = .14f),
            )
        } else {
            OlbyineProgressBar(
                null,
                Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = .14f),
            )
        }
    }
}

@Composable
private fun AddMenu(
    onImportAudio: () -> Unit,
    onImportFolder: () -> Unit,
    onImportLyrics: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onImportNetease: () -> Unit,
    onExternalLyrics: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    Box {
        OlbyineIconButton(
            Icons.Rounded.Add,
            "添加",
            { expanded = true },
            container = MaterialTheme.colorScheme.primary.takeIf { LocalStyleSpec.current.style == com.olbyine.music.data.UiStyle.MATERIAL },
            tint = if (LocalStyleSpec.current.style == com.olbyine.music.data.UiStyle.MATERIAL) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            iconSize = 22.dp,
        )
        OlbyineDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            GlassMenuItem("选择音频文件", { expanded = false; onImportAudio() }, icon = Icons.Rounded.AudioFile)
            GlassMenuItem("扫描文件夹", { expanded = false; onImportFolder() }, icon = Icons.Rounded.FolderOpen)
            LocalOpenWebDav.current?.let { openWebDav ->
                GlassMenuItem("WebDAV / NAS", { expanded = false; openWebDav() }, icon = Icons.Rounded.Dns)
            }
            GlassMenuItem("导入同名歌词", { expanded = false; onImportLyrics() }, icon = Icons.Rounded.Lyrics)
            MenuDivider()
            GlassMenuItem("新建歌单", { expanded = false; onCreatePlaylist() }, icon = Icons.AutoMirrored.Rounded.PlaylistAdd)
            GlassMenuItem("导入网易云 / Spotify 歌单", { expanded = false; onImportNetease() }, icon = Icons.Rounded.CloudDownload)
            MenuDivider()
            GlassMenuItem("监听其他播放器", { expanded = false; onExternalLyrics() }, icon = Icons.Rounded.Headphones)
        }
    }
}

internal data class ComposerGroup(val name: String, val tracks: List<Track>, val credit: String? = null)

internal fun Track.composerNames(): List<String> {
    val sources = listOfNotNull(
        composer?.trim()?.takeIf(String::isNotEmpty),
        composerCredit?.trim()?.takeIf(String::isNotEmpty),
    )
    return sources.flatMap { com.olbyine.music.data.ComposerTags.split(it) }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { com.olbyine.music.data.ComposerTags.normalize(it) }
}

internal fun buildComposers(tracks: List<Track>): List<ComposerGroup> {
    val spellings = LinkedHashMap<String, MutableMap<String, Int>>()
    val members = LinkedHashMap<String, MutableList<Track>>()
    tracks.forEach { track ->
        track.composerNames().forEach { name ->
            val key = com.olbyine.music.data.ComposerTags.normalize(name)
            if (key.isEmpty()) return@forEach
            val counts = spellings.getOrPut(key) { LinkedHashMap() }
            counts[name] = (counts[name] ?: 0) + 1
            val list = members.getOrPut(key) { mutableListOf() }
            if (list.none { it.id == track.id }) list.add(track)
        }
    }
    return members.map { (key, list) ->
        val display = spellings[key].orEmpty().maxByOrNull { it.value }?.key.orEmpty()
        val credit = list.asSequence()
            .mapNotNull { it.composerCredit?.trim()?.takeIf(String::isNotEmpty) }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?.takeIf { !it.equals(display, ignoreCase = true) }
        ComposerGroup(display, list.sortedWith(AlbumOrder), credit)
    }.filter { it.name.isNotBlank() && it.tracks.isNotEmpty() }
        .sortedBy { it.name.lowercase() }
}

@Composable
private fun ComposerGrid(
    composers: List<ComposerGroup>,
    query: String,
    onComposer: (ComposerGroup) -> Unit,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
) {
    val visible = remember(composers, query) {
        if (query.isBlank()) composers
        else composers.filter { it.name.contains(query, true) || it.tracks.any { track -> track.title.contains(query, true) } }
    }
    if (visible.isEmpty()) {
        EmptyState(
            Icons.Rounded.EditNote,
            if (query.isBlank()) "还没有作曲人信息" else "没有匹配的作曲人",
            modifier = Modifier.fillMaxSize().padding(top = topContentPadding, bottom = bottomContentPadding),
            message = if (query.isBlank()) "会先读歌曲文件里的作曲人标签，没有的再由网易云逐步补全" else null,
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = topContentPadding + 4.dp, bottom = bottomContentPadding + 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        gridItems(visible, key = { it.name }) { composer ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onComposer(composer) }.padding(6.dp),
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    CoverMosaic(composer.tracks.take(4), Modifier.fillMaxSize(), 16.dp, composer.name)
                }
                Text(
                    composer.name,
                    Modifier.padding(start = 2.dp, top = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${composer.tracks.size} 首",
                    Modifier.padding(start = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AlbumGrid(
    albums: List<Album>,
    query: String,
    currentTrackId: String?,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onAlbum: (Album) -> Unit,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
) {
    val visible = remember(albums, query) {
        if (query.isBlank()) albums else albums.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
    }
    if (visible.isEmpty()) {
        EmptyState(
            Icons.Rounded.Album,
            if (query.isBlank()) "还没有专辑" else "没有匹配的专辑",
            modifier = Modifier.fillMaxSize().padding(top = topContentPadding, bottom = bottomContentPadding),
            message = if (query.isBlank()) "导入歌曲后会按专辑自动归类" else null,
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        state = gridState,
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = topContentPadding + 4.dp, bottom = bottomContentPadding + 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        gridItems(visible, key = Album::key) { album ->
            val playing = currentTrackId != null && album.tracks.any { it.id == currentTrackId }
            Column(
                itemMotion(placement = false).fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onAlbum(album) }.padding(6.dp),
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    TrackArtwork(album.cover, Modifier.fillMaxSize().sharedAlbumCover(album.key), 16.dp, decodeEdge = 512)
                    if (playing) {
                        Box(
                            Modifier.align(Alignment.BottomStart).padding(8.dp).size(30.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) { EqualizerBars(true, MaterialTheme.colorScheme.onPrimary, Modifier.size(13.dp)) }
                    }
                }
                Text(album.title, Modifier.padding(start = 2.dp, top = 8.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(album.artist, album.year.takeIf { it > 0 }?.toString()).joinToString(" · "),
                    Modifier.padding(start = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TrackCollection(
    tracks: List<Track>,
    keys: Map<String, TrackSortKeys>,
    viewMode: LibraryViewMode,
    sortMode: LibrarySortMode?,
    indexEnabled: Boolean,
    albumStyle: Boolean,
    currentTrackId: String?,
    isPlaying: Boolean,
    callbacks: TrackCallbacks,
    selection: Selection,
    onPlayTracks: (List<Track>, Int) -> Unit,
    header: @Composable () -> Unit,
    empty: (@Composable () -> Unit)?,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    revealId: com.olbyine.music.LibraryReveal? = null,
    onRevealed: () -> Unit = {},
) {
    val bottom = bottomContentPadding + 20.dp
    val haptics = rememberHaptics()
    val onClick: (Int, Track) -> Unit = { index, track ->
        if (selection.active) {
            haptics.tick(HapticArea.SCRUB)
            selection.toggle(track.id)
        } else {
            onPlayTracks(tracks, index)
        }
    }
    val onLongClick: (Track) -> Unit = { track ->
        haptics.heavy(HapticArea.SCRUB)
        selection.toggle(track.id)
    }
    val revealOffset = with(androidx.compose.ui.platform.LocalDensity.current) { 96.dp.roundToPx() }
    if (viewMode == LibraryViewMode.GRID && empty == null) {
        val gridState = rememberLazyGridState()
        LaunchedEffect(sortMode, tracks.firstOrNull()?.id) {
            if (revealId == null) gridState.scrollToItem(0)
        }
        LaunchedEffect(revealId, tracks) {
            val id = revealId?.trackId ?: return@LaunchedEffect
            val index = tracks.indexOfFirst { it.id == id }
            if (index < 0) {
                if (tracks.isNotEmpty()) onRevealed()
                return@LaunchedEffect
            }
            gridState.scrollToItem(index + 1)
            gridState.scrollBy(-revealOffset.toFloat())
            onRevealed()
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = topContentPadding, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") { header() }
            gridItemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                TrackCard(
                    track = track,
                    current = track.id == currentTrackId,
                    isPlaying = isPlaying,
                    callbacks = callbacks,
                    selecting = selection.active,
                    selected = track.id in selection.ids,
                    onClick = { onClick(index, track) },
                    onLongClick = { onLongClick(track) },
                    modifier = itemMotion(),
                )
            }
        }
        return
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(callbacks.activePlaylist?.id, tracks.firstOrNull()?.id, sortMode) {
        if (revealId == null) listState.scrollToItem(0)
    }
    var handledReveal by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(revealId, tracks) {
        val id = revealId?.trackId ?: return@LaunchedEffect
        if (handledReveal == id) return@LaunchedEffect
        val index = tracks.indexOfFirst { it.id == id }
        if (index < 0) {
            if (tracks.isNotEmpty()) onRevealed()
            return@LaunchedEffect
        }
        handledReveal = id
        listState.scrollToItem(index + 1)
        listState.scrollBy(-revealOffset.toFloat())
        onRevealed()
    }
    val letters = remember(tracks, sortMode, keys) {
        tracks.mapIndexed { index, track ->
            val key = sortMode?.let { keys[track.id]?.forMode(it) } ?: SortKeys.cachedOrPlain(track.title)
            SortKeys.letter(key) to index
        }.distinctBy { it.first }.toMap()
    }
    var activeLetter by remember { mutableStateOf<String?>(null) }
    val lastLetter = remember { arrayOf("") }
    activeLetter?.let { lastLetter[0] = it }
    val alphabetical = sortMode == LibrarySortMode.TITLE || sortMode == LibrarySortMode.ARTIST || sortMode == LibrarySortMode.ALBUM
    val showIndex = indexEnabled && alphabetical && letters.size > 4 && empty == null && !selection.active
    val discs = albumStyle && tracks.map { if (it.discNumber > 0) it.discNumber else 1 }.distinct().size > 1
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = if (showIndex) 26.dp else 12.dp, top = topContentPadding, bottom = bottom),
        ) {
            item(key = "header") { header() }
            if (empty != null) {
                item(key = "empty") { Box(Modifier.fillMaxWidth().padding(top = 36.dp)) { empty() } }
            } else if (albumStyle) {
                albumItems(tracks, discs, currentTrackId, isPlaying, callbacks, selection, onClick, onLongClick)
            } else {
                trackItems(tracks, currentTrackId, isPlaying, callbacks, selection, onClick, onLongClick)
            }
        }
        if (showIndex) {
            val sortedLetters = remember(letters) { letters.keys.sortedWith(compareBy { if (it == "#") "~" else it }) }
            Box(Modifier.fillMaxSize().padding(top = topContentPadding + 8.dp, bottom = bottomContentPadding + 8.dp)) {
                AlphabetIndex(
                    letters = sortedLetters,
                    active = activeLetter,
                    onActive = { letter ->
                        activeLetter = letter
                        if (letter != null) scope.launch { listState.scrollToItem(letters.getValue(letter) + 1) }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp),
                )
            }
            AnimatedVisibility(
                visible = activeLetter != null,
                modifier = Modifier.align(Alignment.Center),
                enter = fadeIn(tween(90)) + scaleIn(initialScale = .94f, animationSpec = appleSpring(response = .2f)),
                exit = fadeOut(tween(OlbyineTokens.Motion.quickMs)) + scaleOut(tween(OlbyineTokens.Motion.quickMs, easing = OlbyineTokens.Motion.easeOut), targetScale = .94f),
            ) {
                Box(
                    Modifier.size(84.dp).clip(RoundedCornerShape(26.dp))
                        .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = .86f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(activeLetter ?: lastLetter[0], fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.inverseOnSurface)
                }
            }
        }
    }
}

private fun LazyListScope.trackItems(
    tracks: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    callbacks: TrackCallbacks,
    selection: Selection,
    onClick: (Int, Track) -> Unit,
    onLongClick: (Track) -> Unit,
) {
    itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
        TrackRow(
            track = track,
            current = track.id == currentTrackId,
            isPlaying = isPlaying,
            callbacks = callbacks,
            selecting = selection.active,
            selected = track.id in selection.ids,
            onClick = { onClick(index, track) },
            onLongClick = { onLongClick(track) },
            modifier = itemMotion(),
        )
    }
}

private fun LazyListScope.albumItems(
    tracks: List<Track>,
    discs: Boolean,
    currentTrackId: String?,
    isPlaying: Boolean,
    callbacks: TrackCallbacks,
    selection: Selection,
    onClick: (Int, Track) -> Unit,
    onLongClick: (Track) -> Unit,
) {
    var lastDisc = -1
    tracks.forEachIndexed { index, track ->
        val disc = if (track.discNumber > 0) track.discNumber else 1
        if (discs && disc != lastDisc) {
            lastDisc = disc
            item(key = "disc-$disc") {
                Overline("碟 $disc", itemMotion().padding(start = 12.dp, top = 14.dp, bottom = 4.dp))
            }
        }
        item(key = track.id) {
            AlbumTrackRow(
                track = track,
                number = if (track.trackNumber > 0) track.trackNumber else index + 1,
                current = track.id == currentTrackId,
                isPlaying = isPlaying,
                callbacks = callbacks,
                selecting = selection.active,
                selected = track.id in selection.ids,
                onClick = { onClick(index, track) },
                onLongClick = { onLongClick(track) },
                modifier = itemMotion(),
            )
        }
    }
}

@Composable
private fun AlbumTrackRow(
    track: Track,
    number: Int,
    current: Boolean,
    isPlaying: Boolean,
    callbacks: TrackCallbacks,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(rowFill(selected, current))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 6.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
            SwapContent(if (selecting) 0 else if (current) 1 else 2, contentAlignment = Alignment.Center) { slot ->
                when (slot) {
                    0 -> SelectionMark(selected)
                    1 -> EqualizerBars(isPlaying, MaterialTheme.colorScheme.primary, Modifier.size(15.dp))
                    else -> Text(
                        "$number",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                Modifier.then(if (current && !LocalReduceMotion.current) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1_800) else Modifier),
                maxLines = 1,
                overflow = if (current && !LocalReduceMotion.current) TextOverflow.Clip else TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                color = rowInk(current),
            )
            if (track.artist != track.albumOwner && track.artist.isNotBlank()) {
                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            formatDuration(track.durationMs),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .8f),
        )
        SelectingSwap(selecting, 0.dp) {
            HeartWithMenu(
                track = { track },
                liked = track.favorite,
                onToggle = { callbacks.onFavorite(track) },
                iconSize = 20.dp,
                minSize = 38.dp,
            )
        }
        SelectingSwap(selecting, 12.dp) { TrackActions(track, callbacks) }
    }
}

@Composable
private fun SelectionMark(selected: Boolean) {
    AnimatedContent(
        targetState = selected,
        transitionSpec = { (fadeIn(tween(120)) + scaleIn(initialScale = .86f, animationSpec = appleSpring(response = .22f))).togetherWith(fadeOut(tween(80))) },
        label = "select-mark",
    ) { on ->
        Icon(
            if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            if (on) "已选择" else "未选择",
            Modifier.size(24.dp).then(morphBlur(2.dp)),
            tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f),
        )
    }
}

@Composable
private fun SelectingSwap(selecting: Boolean, gap: Dp, actions: @Composable () -> Unit) {
    AnimatedContent(
        targetState = selecting,
        transitionSpec = {
            (fadeIn(tween(OlbyineTokens.Motion.standardMs, easing = OlbyineTokens.Motion.easeOut)) + scaleIn(initialScale = .85f, animationSpec = tween(OlbyineTokens.Motion.standardMs, easing = OlbyineTokens.Motion.easeOut)))
                .togetherWith(fadeOut(tween(OlbyineTokens.Motion.quickMs)))
                .using(androidx.compose.animation.SizeTransform(clip = false) { _, _ -> OlbyineMotion.resize })
        },
        contentAlignment = Alignment.CenterEnd,
        label = "row-actions",
    ) { on -> if (on) Spacer(Modifier.width(gap)) else actions() }
}

@Composable
private fun CollectionActions(
    count: Int,
    totalMs: Long,
    enabled: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        PillButton("播放", onPlay, icon = Icons.Rounded.PlayArrow, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        PillButton("随机", onShuffle, icon = Icons.Rounded.Shuffle, style = PillStyle.TONAL, enabled = enabled)
        Spacer(Modifier.weight(1f))
        if (count > 0) {
            Text(
                formatTotalDuration(totalMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CollectionHeader(
    title: String,
    subtitle: String,
    cover: @Composable () -> Unit,
    onBack: () -> Unit,
    tracks: List<Track>,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    sortMode: LibrarySortMode?,
    onSortMode: (LibrarySortMode) -> Unit,
    onCustomOrder: (() -> Unit)? = null,
    extraAction: (@Composable () -> Unit)? = null,
    onFanCard: ((Track) -> Unit)? = null,
) {
    val fan = remember(tracks) { tracks.distinctBy { it.coverPath ?: it.artworkUrl ?: it.id }.take(5) }
    val showFan = onFanCard != null && fan.size >= 3 && playerFx(com.olbyine.music.data.PlayerFx.BOUNCE_CARDS)
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OlbyineIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回", onBack, size = 38.dp, iconSize = 19.dp)
            Spacer(Modifier.weight(1f))
            extraAction?.invoke()
        }
        if (showFan) {
            BounceCards(fan, Modifier.padding(top = 4.dp), onCardClick = { index -> onFanCard?.invoke(fan[index]) })
        } else {
            Spacer(Modifier.height(14.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!showFan) {
                cover()
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillButton("播放", onPlay, icon = Icons.Rounded.PlayArrow, enabled = tracks.isNotEmpty())
            Spacer(Modifier.width(8.dp))
            PillButton("随机", onShuffle, icon = Icons.Rounded.Shuffle, style = PillStyle.TONAL, enabled = tracks.isNotEmpty())
            Spacer(Modifier.weight(1f))
            if (sortMode != null || onCustomOrder != null) SortButton(sortMode, onSortMode, onCustomOrder)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SortButton(sortMode: LibrarySortMode?, onSortMode: (LibrarySortMode) -> Unit, onCustomOrder: (() -> Unit)? = null) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.height(36.dp).clip(CircleShape).clickable(onClickLabel = "排序方式") { expanded = true }.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StyledIcon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(sortMode?.let(::sortLabel) ?: "自定义顺序", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OlbyineDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (onCustomOrder != null) {
                GlassMenuItem("自定义顺序", { expanded = false; onCustomOrder() }, selected = sortMode == null)
            }
            LibrarySortMode.entries.forEach { mode ->
                GlassMenuItem(sortLabel(mode), { expanded = false; onSortMode(mode) }, selected = mode == sortMode)
            }
        }
    }
}

@Composable
private fun AlphabetIndex(
    letters: List<String>,
    active: String?,
    onActive: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val currentOnActive by rememberUpdatedState(onActive)
    var heightPx by remember { mutableIntStateOf(1) }
    Column(
        modifier
            .width(26.dp)
            .fillMaxHeight()
            .onSizeChanged { heightPx = it.height }
            .pointerInput(letters) {
                awaitEachGesture {
                    var last: String? = null
                    fun pick(y: Float) {
                        val index = ((y / heightPx.coerceAtLeast(1)) * letters.size).toInt().coerceIn(0, letters.lastIndex)
                        val letter = letters[index]
                        if (letter != last) {
                            last = letter
                            haptics.tick(HapticArea.SCRUB)
                            currentOnActive(letter)
                        }
                    }
                    val down = awaitFirstDown()
                    down.consume()
                    pick(down.position.y)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        pick(change.position.y)
                    }
                    currentOnActive(null)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        letters.forEach { letter ->
            val isActive = letter == active
            Text(
                letter,
                Modifier.graphicsLayer {
                    val s = if (isActive) 1.35f else 1f
                    scaleX = s
                    scaleY = s
                },
                fontSize = 10.5.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .8f),
            )
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    current: Boolean,
    isPlaying: Boolean,
    callbacks: TrackCallbacks,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(rowFill(selected, current))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.animation.AnimatedVisibility(
            selecting,
            enter = fadeIn() + androidx.compose.animation.expandHorizontally(),
            exit = fadeOut() + androidx.compose.animation.shrinkHorizontally(),
        ) {
            Box(Modifier.padding(end = 10.dp)) { SelectionMark(selected) }
        }
        Box(Modifier.size(52.dp)) {
            TrackArtwork(track, Modifier.fillMaxSize(), OlbyineTokens.Corner.thumb)
            androidx.compose.animation.AnimatedVisibility(
                visible = current,
                enter = fadeIn(tween(OlbyineTokens.Motion.standardMs, easing = OlbyineTokens.Motion.easeOut)),
                exit = fadeOut(tween(OlbyineTokens.Motion.quickMs)),
            ) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(OlbyineTokens.Corner.thumb)).background(Color.Black.copy(alpha = .42f)),
                    contentAlignment = Alignment.Center,
                ) { EqualizerBars(isPlaying, Color.White, Modifier.size(18.dp)) }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                Modifier.then(if (current && !LocalReduceMotion.current) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1_800) else Modifier),
                maxLines = 1,
                overflow = if (current && !LocalReduceMotion.current) TextOverflow.Clip else TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                color = rowInk(current),
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.favorite) {
                    StyledIcon(Icons.Rounded.Favorite, "已收藏", Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    listOf(track.artist, track.album).filter(String::isNotBlank).joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            formatDuration(track.durationMs),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .8f),
        )
        SelectingSwap(selecting, 0.dp) {
            GhostIconButton(
                Icons.AutoMirrored.Rounded.PlaylistPlay,
                "下一首播放",
                { callbacks.onPlayNext(track) },
                size = 36.dp,
                iconSize = 18.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SelectingSwap(selecting, 14.dp) { TrackActions(track, callbacks) }
    }
}

@Composable
private fun TrackCard(
    track: Track,
    current: Boolean,
    isPlaying: Boolean,
    callbacks: TrackCallbacks,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(rowFill(selected, current = false))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            TrackArtwork(track, Modifier.fillMaxSize(), 16.dp, decodeEdge = 512)
            PopIn(selecting, Modifier.align(Alignment.TopStart)) {
                Box(Modifier.padding(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = .8f))) {
                    SelectionMark(selected)
                }
            }
            PopIn(current, Modifier.align(Alignment.BottomStart)) {
                Box(
                    Modifier.padding(8.dp).size(32.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { EqualizerBars(isPlaying, MaterialTheme.colorScheme.onPrimary, Modifier.size(14.dp)) }
            }
            if (track.favorite) {
                Icon(
                    Icons.Rounded.Favorite, "已收藏",
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(16.dp),
                    tint = Color.White,
                )
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(start = 2.dp)) {
                Text(
                    track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    color = rowInk(current),
                )
                Text(
                    track.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SelectingSwap(selecting, 0.dp) { Box(Modifier.offset(x = 8.dp, y = (-6).dp)) { TrackActions(track, callbacks) } }
        }
    }
}

@Composable
private fun TrackActions(track: Track, callbacks: TrackCallbacks) {
    var menu by remember { mutableStateOf(false) }
    var editDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var playlistDialog by remember { mutableStateOf(false) }
    val activePlaylist = callbacks.activePlaylist
    Box {
        GhostIconButton(Icons.Rounded.MoreVert, "更多操作", { menu = true }, size = 40.dp, iconSize = 20.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        OlbyineDropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
                ) {
            GlassMenuItem(if (track.favorite) "取消收藏" else "收藏", { menu = false; callbacks.onFavorite(track) },
                icon = if (track.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder)
            GlassMenuItem("添加到歌单", { menu = false; playlistDialog = true }, icon = Icons.AutoMirrored.Rounded.PlaylistAdd)
            MenuDivider()
            GlassMenuItem("编辑歌曲信息", { menu = false; editDialog = true }, icon = Icons.Rounded.Edit)
            GlassMenuItem(if (track.hasLyrics) "替换本地歌词" else "导入本地歌词", { menu = false; callbacks.onImportLyrics(track.id) }, icon = Icons.Rounded.Lyrics)
            GlassMenuItem("在线匹配歌词", { menu = false; callbacks.onSearchLyrics(track) }, icon = Icons.Rounded.CloudDownload)
            GlassMenuItem("匹配封面与信息", { menu = false; callbacks.onMatchMetadata(track) }, icon = Icons.Rounded.Image)
            if (activePlaylist != null) {
                val position = activePlaylist.trackIds.indexOf(track.id)
                MenuDivider()
                if (callbacks.reorderable) {
                    GlassMenuItem("在歌单中上移", {
                        menu = false
                        callbacks.onReorderPlaylistTracks(activePlaylist.id, moved(activePlaylist.trackIds, position, position - 1))
                    }, icon = Icons.Rounded.ArrowUpward, enabled = position > 0)
                    GlassMenuItem("在歌单中下移", {
                        menu = false
                        callbacks.onReorderPlaylistTracks(activePlaylist.id, moved(activePlaylist.trackIds, position, position + 1))
                    }, icon = Icons.Rounded.ArrowDownward, enabled = position >= 0 && position < activePlaylist.trackIds.lastIndex)
                }
                GlassMenuItem("移出当前歌单", { menu = false; callbacks.onRemoveFromPlaylist(activePlaylist.id, track.id) }, icon = Icons.Rounded.PlaylistRemove)
            }
            MenuDivider()
            GlassMenuItem("从音乐库移除", { menu = false; deleteDialog = true }, icon = Icons.Rounded.DeleteOutline, destructive = true)
        }
    }
    if (playlistDialog) {
        OlbyineDialog(
            onDismissRequest = { playlistDialog = false },
            title = "添加到歌单",
            confirmButton = { GlassTextButton(onClick = { playlistDialog = false }) { Text("完成") } },
        ) {
            if (callbacks.playlists.isEmpty()) Text("还没有歌单。点击音乐库右上角的 + 新建一个。")
            else LazyColumn(Modifier.heightIn(max = 340.dp)) {
                items(callbacks.playlists, key = Playlist::id) { playlist ->
                    val included = track.id in playlist.trackIds
                    Row(
                        itemMotion().fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                            if (included) callbacks.onRemoveFromPlaylist(playlist.id, track.id)
                            else callbacks.onAddToPlaylist(playlist.id, track.id)
                        }.padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(playlist.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface)
                        GlassCheck(included, onCheckedChange = { checked ->
                            if (checked) callbacks.onAddToPlaylist(playlist.id, track.id)
                            else callbacks.onRemoveFromPlaylist(playlist.id, track.id)
                        })
                    }
                }
            }
        }
    }
    if (editDialog) {
        TrackEditDialog(track, onDismiss = { editDialog = false }) { title, artist, album ->
            callbacks.onEdit(track, title, artist, album); editDialog = false
        }
    }
    if (deleteDialog) {
        OlbyineDialog(
            onDismissRequest = { deleteDialog = false },
            title = "移除歌曲？",
            confirmButton = {
                GlassTextButton(destructive = true, onClick = { deleteDialog = false; callbacks.onDelete(track) }) {
                    Text("移除")
                }
            },
            dismissButton = { GlassTextButton(onClick = { deleteDialog = false }) { Text("取消") } },
        ) { Text("将从音乐库移除「${track.title}」，原音频文件不会被删除。") }
    }
}

@Composable
private fun FolderBrowser(
    tracks: List<Track>,
    query: String,
    onFolder: (String?) -> Unit,
    onScan: () -> Unit,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
) {
    val folders = remember(tracks, query) {
        tracks.groupBy { it.folderId ?: "" }.map { (id, songs) ->
            Triple(id, songs.first().folderPath?.ifBlank { null } ?: "未分类文件", songs)
        }.filter { (_, path, _) -> query.isBlank() || path.contains(query, ignoreCase = true) }
            .sortedBy { SortKeys.cachedOrPlain(folderName(it.second)) }
    }
    if (folders.isEmpty()) {
        EmptyState(
            Icons.Rounded.FolderOpen,
            if (query.isBlank()) "还没有文件夹" else "没有匹配的文件夹",
            modifier = Modifier.fillMaxSize().padding(top = topContentPadding, bottom = bottomContentPadding),
            message = if (query.isBlank()) "扫描一个文件夹，歌曲会按目录自动归类" else null,
        ) { if (query.isBlank()) PillButton("扫描文件夹", onScan, icon = Icons.Rounded.FolderOpen) }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = topContentPadding + 4.dp, bottom = bottomContentPadding + 20.dp),
    ) {
        items(folders, key = { it.first }) { (id, path, songs) ->
            Row(
                itemMotion().fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onFolder(id) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverMosaic(songs.take(4), Modifier.size(56.dp), 14.dp, path)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(folderName(path), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${songs.size} 首 · $path",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StyledIcon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f))
            }
        }
    }
}

@Composable
private fun PlaylistBrowser(
    playlists: List<Playlist>,
    tracks: List<Track>,
    query: String,
    onPlaylist: (String?) -> Unit,
    onReorder: (List<String>) -> Unit,
    onCreate: () -> Unit,
    onEdit: (Playlist) -> Unit,
    onDelete: (Playlist) -> Unit,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
) {
    val visible = playlists.filter { it.name.contains(query, ignoreCase = true) }
    val tracksById = remember(tracks) { tracks.associateBy(Track::id) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = topContentPadding + 4.dp, bottom = bottomContentPadding + 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (query.isBlank()) item(key = "create") {
            Column(itemMotion().fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onCreate).padding(6.dp)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                        .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .14f)),
                        contentAlignment = Alignment.Center,
                    ) { StyledIcon(Icons.Rounded.Add, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary) }
                }
                Text("新建歌单", Modifier.padding(start = 2.dp, top = 8.dp), style = MaterialTheme.typography.titleSmall)
                Text("收藏一组喜欢的歌", Modifier.padding(start = 2.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        gridItems(visible, key = Playlist::id) { playlist ->
            var menu by remember { mutableStateOf(false) }
            val position = playlists.indexOfFirst { it.id == playlist.id }
            val covers = remember(playlist.trackIds, tracksById) { playlist.trackIds.asSequence().mapNotNull(tracksById::get).take(4).toList() }
            Column(itemMotion().fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onPlaylist(playlist.id) }.padding(6.dp)) {
                CoverMosaic(covers, Modifier.fillMaxWidth().aspectRatio(1f), 16.dp, playlist.id)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(start = 2.dp)) {
                        Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text("${playlist.trackIds.size} 首", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(Modifier.offset(x = 8.dp, y = (-6).dp)) {
                        GhostIconButton(Icons.Rounded.MoreVert, "歌单操作", { menu = true }, size = 40.dp, iconSize = 20.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        OlbyineDropdownMenu(
                            expanded = menu,
                            onDismissRequest = { menu = false },
                                                ) {
                            GlassMenuItem("重命名", { menu = false; onEdit(playlist) }, icon = Icons.Rounded.Edit)
                            GlassMenuItem("前移", {
                                menu = false; onReorder(moved(playlists.map(Playlist::id), position, position - 1))
                            }, icon = Icons.Rounded.ArrowUpward, enabled = position > 0)
                            GlassMenuItem("后移", {
                                menu = false; onReorder(moved(playlists.map(Playlist::id), position, position + 1))
                            }, icon = Icons.Rounded.ArrowDownward, enabled = position < playlists.lastIndex)
                            MenuDivider()
                            GlassMenuItem("删除", { menu = false; onDelete(playlist) }, icon = Icons.Rounded.DeleteOutline, destructive = true)
                        }
                    }
                }
            }
        }
        if (visible.isEmpty() && query.isNotBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "no-match") {
                EmptyState(Icons.Rounded.Search, "没有匹配的歌单", modifier = itemMotion().padding(top = 24.dp))
            }
        }
    }
}

@Composable
internal fun PlaylistNameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial) }
    OlbyineDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmButton = { GlassTextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("保存") } },
        dismissButton = { GlassTextButton(onClick = onDismiss) { Text("取消") } },
    ) { OlbyineTextField(name, { name = it }, "歌单名称") }
}

@Composable
private fun PlaylistSongsDialog(playlist: Playlist, tracks: List<Track>, onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit, onRemove: (String, String) -> Unit) {
    var search by remember { mutableStateOf("") }
    OlbyineDialog(
        onDismissRequest = onDismiss,
        title = "管理「${playlist.name}」",
        confirmButton = { GlassTextButton(onClick = onDismiss) { Text("完成") } },
    ) {
        Column {
            OlbyineSearchField(search, { search = it }, "搜索音乐库", Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.height(380.dp)) {
                items(tracks.filter { it.matches(search.trim()) }, key = Track::id) { track ->
                    val included = track.id in playlist.trackIds
                    Row(
                        itemMotion().fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                            if (included) onRemove(playlist.id, track.id) else onAdd(playlist.id, track.id)
                        }.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TrackArtwork(track, Modifier.size(40.dp), 8.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        GlassCheck(included, onCheckedChange = { checked ->
                            if (checked) onAdd(playlist.id, track.id) else onRemove(playlist.id, track.id)
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackEditDialog(track: Track, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    var album by remember(track.id) { mutableStateOf(track.album) }
    OlbyineDialog(
        onDismissRequest = onDismiss,
        title = "编辑歌曲信息",
        confirmButton = { GlassTextButton(onClick = { onSave(title, artist, album) }, enabled = title.isNotBlank()) { Text("保存") } },
        dismissButton = { GlassTextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackArtwork(track, Modifier.size(56.dp), 12.dp)
                Spacer(Modifier.width(12.dp))
                Text(track.displayName, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Start)
            }
            OlbyineTextField(title, { title = it }, "歌曲名")
            OlbyineTextField(artist, { artist = it }, "艺术家")
            OlbyineTextField(album, { album = it }, "专辑")
        }
    }
}

private fun Track.matches(query: String): Boolean = query.isBlank() ||
    title.contains(query, ignoreCase = true) || artist.contains(query, ignoreCase = true) ||
    album.contains(query, ignoreCase = true) || displayName.contains(query, ignoreCase = true)

private fun folderName(path: String): String =
    path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\').substringAfterLast(':').ifBlank { path }

private fun sortLabel(mode: LibrarySortMode): String = mode.label
private fun <T> moved(items: List<T>, from: Int, to: Int): List<T> {
    if (from !in items.indices || to !in items.indices) return items
    return items.toMutableList().apply { add(to, removeAt(from)) }
}
