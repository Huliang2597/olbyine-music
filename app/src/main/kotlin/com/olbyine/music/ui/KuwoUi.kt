@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.olbyine.music.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.olbyine.music.KuwoUiState
import com.olbyine.music.OlbyineApplication
import com.olbyine.music.data.KuwoClient
import com.olbyine.music.data.KuwoPlaylist
import com.olbyine.music.data.KuwoSong
import com.olbyine.music.data.LxPlaylist
import kotlinx.coroutines.launch
import java.util.Locale

private enum class KuwoTab(val label: String) {
    MINE("我的歌单"),
    DISCOVER("推荐歌单"),
    CHARTS("排行榜"),
    SEARCH("搜索"),
}

private sealed interface KuwoRoute {
    data class Collection(val key: String) : KuwoRoute
}

@Composable
internal fun KuwoScreen(
    state: KuwoUiState,
    bottomPadding: Dp,
    currentTrackId: String?,
    isPlaying: Boolean,
    onSearch: (String) -> Unit,
    onSearchPlaylists: (String) -> Unit,
    onLoadHot: () -> Unit,
    onLoadMine: () -> Unit,
    onLoadExplore: () -> Unit,
    onRecommendSort: (String) -> Unit,
    onOpenBoard: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenMine: (String) -> Unit,
    onDeleteMine: (String) -> Unit,
    onRenameMine: (String, String) -> Unit,
    onCreateMine: (String) -> Unit,
    onImportBytes: (ByteArray, String) -> Unit,
    onImportText: (String, String) -> Unit,
    onExportMine: (String) -> ByteArray?,
    onAddToMine: (String, List<KuwoSong>) -> Unit,
    onCloseCollection: () -> Unit,
    onPlay: (List<KuwoSong>, Int, Boolean) -> Unit,
    backGestureEnabled: Boolean = true,
) {
    LaunchedEffect(Unit) {
        onLoadHot()
        onLoadMine()
        onLoadExplore()
    }

    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(KuwoTab.MINE) }
    var showImport by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<LxPlaylist?>(null) }
    var addTarget by remember { mutableStateOf<List<KuwoSong>?>(null) }
    var exportTarget by remember { mutableStateOf<LxPlaylist?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (bytes == null || bytes.isEmpty()) return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "导入的歌单"
        onImportBytes(bytes, name)
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val target = exportTarget
        exportTarget = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        val bytes = onExportMine(target.id) ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        }
    }

    val savedIds = remember(state.mine) { state.mine.flatMap { it.songs }.map { it.id }.toSet() }
    val pages = rememberPageStack<KuwoRoute>()
    // 退出动画期间 collection 已经被清空，必须留住最后一份内容，否则离场卡片是空的
    var shownCollection by remember { mutableStateOf(state.collection) }
    var shownCollectionId by remember { mutableStateOf("") }
    if (state.collection != null) {
        shownCollection = state.collection
        shownCollectionId = state.collectionId
    }
    val desired = if (state.collectionId.isNotEmpty()) listOf(KuwoRoute.Collection(state.collectionId)) else emptyList()
    LaunchedEffect(desired) {
        val current = pages.entries.filter { !it.leaving }.map { it.route }
        var keep = 0
        while (keep < current.size && keep < desired.size && current[keep] == desired[keep]) keep++
        repeat(current.size - keep) { pages.pop() }
        for (index in keep until desired.size) pages.pushFromTouch(desired[index])
    }

    PageStack(
        pages,
        backEnabled = backGestureEnabled,
        onBack = onCloseCollection,
        base = {
            KuwoTabs(
                state = state,
                savedIds = savedIds,
                tab = tab,
                onTab = { tab = it },
                bottomPadding = bottomPadding,
                currentTrackId = currentTrackId,
                onSearch = onSearch,
                onSearchPlaylists = onSearchPlaylists,
                onRecommendSort = onRecommendSort,
                onOpenBoard = onOpenBoard,
                onOpenPlaylist = onOpenPlaylist,
                onOpenMine = onOpenMine,
                onExportMine = { playlist ->
                    exportTarget = playlist
                    exporter.launch("lx_list_part_${sanitizeFileName(playlist.name)}.lxmc")
                },
                onDeleteMine = { pendingDelete = it },
                onRenameMine = onRenameMine,
                onCreateMine = { showCreate = true },
                onImportMine = { showImport = true },
                onPickFile = { filePicker.launch(arrayOf("*/*")) },
                onPlay = onPlay,
            )
        },
    ) { route ->
        when (route) {
            is KuwoRoute.Collection -> {
                val collection = if (state.collection != null) state.collection else shownCollection
                val key = if (state.collectionId.isNotEmpty()) state.collectionId else shownCollectionId
                if (collection != null) {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                            .clickable(
                                remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                            ) {},
                    ) {
                        KuwoCollectionPage(
                            title = collection.title,
                            coverUrl = collection.coverUrl,
                            description = collection.description,
                            songs = collection.songs,
                            bottomPadding = bottomPadding,
                            currentTrackId = currentTrackId,
                            isPlaying = isPlaying,
                            showRank = key.startsWith("kw:BOARD:"),
                            savedIds = savedIds,
                            onBack = onCloseCollection,
                            onPlay = onPlay,
                            onLongPressSong = { song -> addTarget = listOf(song) },
                        )
                    }
                }
            }
        }
    }

    if (showImport) {
        LxImportDialog(
            title = "导入歌单",
            hint = "支持洛雪音乐的 .lxmc（gzip）与 .json 歌单，格式包括 playListPart_v2 / playList_v2 / playList。" +
                "非酷我曲目会按歌名 + 歌手在酷我匹配。",
            onDismiss = { showImport = false },
            onText = { text, name ->
                showImport = false
                onImportText(text, name)
            },
            onBytes = { bytes, name ->
                showImport = false
                onImportBytes(bytes, name)
            },
        )
    }

    if (showCreate) {
        NameInputDialog(
            title = "新建歌单",
            label = "歌单名称",
            initial = "",
            confirmText = "创建",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                showCreate = false
                onCreateMine(name)
            },
        )
    }

    addTarget?.let { songs ->
        AddToPlaylistDialog(
            songs = songs,
            playlists = state.mine,
            onDismiss = { addTarget = null },
            onPick = { id ->
                addTarget = null
                onAddToMine(id, songs)
            },
            onCreate = { name ->
                addTarget = null
                onCreateMine(name)
            },
        )
    }

    pendingDelete?.let { target ->
        OlbyineDialog(
            onDismissRequest = { pendingDelete = null },
            title = "删除「${target.name}」？",
            confirmButton = {
                GlassTextButton(destructive = true, onClick = {
                    pendingDelete = null
                    onDeleteMine(target.id)
                }) { Text("删除") }
            },
            dismissButton = { GlassTextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        ) {
            Text("只会移除这份歌单记录，不会删除已下载的歌曲。")
        }
    }
}

@Composable
private fun KuwoTabs(
    state: KuwoUiState,
    savedIds: Set<Long>,
    tab: KuwoTab,
    onTab: (KuwoTab) -> Unit,
    bottomPadding: Dp,
    currentTrackId: String?,
    onSearch: (String) -> Unit,
    onSearchPlaylists: (String) -> Unit,
    onRecommendSort: (String) -> Unit,
    onOpenBoard: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenMine: (String) -> Unit,
    onExportMine: (LxPlaylist) -> Unit,
    onDeleteMine: (LxPlaylist) -> Unit,
    onRenameMine: (String, String) -> Unit,
    onCreateMine: () -> Unit,
    onImportMine: () -> Unit,
    onPickFile: () -> Unit,
    onPlay: (List<KuwoSong>, Int, Boolean) -> Unit,
) {
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.statusBarsPadding().padding(start = 20.dp, top = 18.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "洛雪音乐",
                Modifier.weight(1f),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            LocalStreamingSwitch.current?.invoke()
        }
        PillTabs(
            KuwoTab.values().map { it to it.label },
            tab,
            { haptics.tick(com.olbyine.music.data.HapticArea.MENUS); onTab(it) },
            Modifier.padding(bottom = 10.dp),
        )
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                fadeIn(tween(150, easing = OlbyineTokens.Motion.easeOut)) togetherWith
                    fadeOut(tween(90, easing = OlbyineTokens.Motion.easeOut))
            },
            label = "kuwo-tab",
        ) { current ->
            when (current) {
                KuwoTab.MINE -> LxMineTab(
                    state = state,
                    bottomPadding = bottomPadding,
                    onOpen = onOpenMine,
                    onExport = onExportMine,
                    onDelete = onDeleteMine,
                    onRename = onRenameMine,
                    onCreate = onCreateMine,
                    onImport = onImportMine,
                    onPickFile = onPickFile,
                )
                KuwoTab.DISCOVER -> KuwoDiscoverTab(
                    state = state,
                    bottomPadding = bottomPadding,
                    onRecommendSort = onRecommendSort,
                    onOpenPlaylist = onOpenPlaylist,
                )
                KuwoTab.CHARTS -> KuwoChartsTab(
                    state = state,
                    bottomPadding = bottomPadding,
                    onOpenBoard = onOpenBoard,
                )
                KuwoTab.SEARCH -> KuwoSearchTab(
                    state = state,
                    bottomPadding = bottomPadding,
                    currentTrackId = currentTrackId,
                    savedIds = savedIds,
                    onSearch = onSearch,
                    onSearchPlaylists = onSearchPlaylists,
                    onOpenPlaylist = onOpenPlaylist,
                    onPlay = onPlay,
                )
            }
        }
    }
}

@Composable
private fun LxMineTab(
    state: KuwoUiState,
    bottomPadding: Dp,
    onOpen: (String) -> Unit,
    onExport: (LxPlaylist) -> Unit,
    onDelete: (LxPlaylist) -> Unit,
    onRename: (String, String) -> Unit,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onPickFile: () -> Unit,
) {
    val haptics = rememberHaptics()
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renameFor by remember { mutableStateOf<LxPlaylist?>(null) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "actions") {
            Row(
                Modifier.fillMaxWidth().padding(start = 6.dp, top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (state.mine.isEmpty()) "还没有歌单" else "${state.mine.size} 个歌单 · 共 ${state.mine.sumOf { it.songs.size }} 首",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GhostIconButton(
                    Icons.AutoMirrored.Rounded.PlaylistAdd,
                    "导入 .lxmc",
                    onImport,
                    size = 36.dp,
                    iconSize = 19.dp,
                    tint = MaterialTheme.colorScheme.primary,
                )
                GhostIconButton(
                    Icons.Rounded.Add,
                    "从文件导入",
                    onPickFile,
                    size = 36.dp,
                    iconSize = 19.dp,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        // 与资料库-歌单页同一个「新建歌单」卡片
        item(key = "create") {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable {
                    haptics.light(com.olbyine.music.data.HapticArea.MENUS)
                    onCreate()
                }.padding(6.dp),
            ) {
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
                Text(
                    "收藏一组喜欢的歌",
                    Modifier.padding(start = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.importing?.let { progress ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "importing") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OlbyineSpinner(Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(progress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (state.mine.isEmpty() && state.importing == null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                Text(
                    "可以新建空歌单，或导入洛雪音乐的 .lxmc / .json 歌单文件；长按任意歌曲可以把它加进歌单。",
                    Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        gridItemsIndexed(state.mine, key = { index, it -> "mine:$index:${it.id}" }) { _, playlist ->
            MotionItem {
                Box {
                    LxPlaylistTile(
                        playlist = playlist,
                        onClick = { onOpen(playlist.id) },
                        onLongClick = {
                            haptics.light(com.olbyine.music.data.HapticArea.MENUS)
                            menuFor = playlist.id
                        },
                    )
                    OlbyineDropdownMenu(
                        expanded = menuFor == playlist.id,
                        onDismissRequest = { menuFor = null },
                    ) {
                        GlassMenuItem(
                            "导出 .lxmc",
                            { menuFor = null; onExport(playlist) },
                            icon = Icons.Rounded.FileDownload,
                        )
                        GlassMenuItem(
                            "重命名",
                            { menuFor = null; renameFor = playlist },
                            icon = Icons.Rounded.DriveFileRenameOutline,
                        )
                        GlassMenuItem(
                            "删除歌单",
                            { menuFor = null; onDelete(playlist) },
                            icon = Icons.Rounded.DeleteOutline,
                            destructive = true,
                        )
                    }
                }
            }
        }
    }
    renameFor?.let { target ->
        PlaylistNameDialog("重命名歌单", target.name, { renameFor = null }) { name ->
            renameFor = null
            onRename(target.id, name)
        }
    }
}

@Composable
private fun LxPlaylistTile(playlist: LxPlaylist, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp),
    ) {
        UrlImage(
            playlist.songs.firstOrNull()?.coverUrl,
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Text(
            playlist.name,
            Modifier.padding(start = 2.dp, top = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${playlist.songs.size} 首" + (if (playlist.origin.isNotBlank()) " · ${playlist.origin}" else ""),
            Modifier.padding(start = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun KuwoDiscoverTab(
    state: KuwoUiState,
    bottomPadding: Dp,
    onRecommendSort: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
) {
    val haptics = rememberHaptics()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 600.dp) 5 else 3
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = bottomPadding + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "sort") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf("hot" to "最热", "new" to "最新").forEach { (key, label) ->
                        val selected = state.recommendSort == key
                        Box(
                            Modifier.padding(end = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .14f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                )
                                .clickable {
                                    haptics.tab(com.olbyine.music.data.HapticArea.SWITCHES)
                                    onRecommendSort(key)
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (state.loadingRecommend && state.recommend.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "loading") {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { OlbyineSpinner() }
                }
            }
            gridItemsIndexed(state.recommend, key = { index, it -> "rc:$index:${it.id}" }) { _, playlist ->
                MotionItem {
                    RecommendCard(playlist) { onOpenPlaylist(playlist.id) }
                }
            }
        }
    }
}

@Composable
private fun KuwoChartsTab(
    state: KuwoUiState,
    bottomPadding: Dp,
    onOpenBoard: (String) -> Unit,
) {
    val boards = if (state.boards.isNotEmpty()) state.boards else KuwoClient.BOARDS
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 600.dp) 5 else 3
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = bottomPadding + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.loadingBoards && state.boards.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "loading") {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { OlbyineSpinner() }
                }
            }
            gridItemsIndexed(boards, key = { index, it -> "bd:$index:${it.id}" }) { _, board ->
                MotionItem {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onOpenBoard(board.id) },
                    ) {
                        UrlImage(
                            board.coverUrl,
                            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            board.name,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KuwoSearchTab(
    state: KuwoUiState,
    bottomPadding: Dp,
    currentTrackId: String?,
    savedIds: Set<Long>,
    onSearch: (String) -> Unit,
    onSearchPlaylists: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onPlay: (List<KuwoSong>, Int, Boolean) -> Unit,
) {
    var draft by remember { mutableStateOf(state.query) }
    var mode by remember { mutableStateOf(KuwoSearchMode.SONG) }
    val haptics = rememberHaptics()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding + 24.dp),
    ) {
        item(key = "search") {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Spacer(Modifier.height(4.dp))
                OlbyineSearchField(
                    draft,
                    {
                        draft = it
                        if (mode == KuwoSearchMode.SONG) onSearch(it) else onSearchPlaylists(it)
                    },
                    if (mode == KuwoSearchMode.SONG) "搜索歌曲、歌手" else "搜索歌单",
                    Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KuwoSearchMode.values().forEach { entry ->
                        val selected = mode == entry
                        Box(
                            Modifier.clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .14f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                )
                                .clickable {
                                    if (mode == entry) return@clickable
                                    haptics.tab(com.olbyine.music.data.HapticArea.SWITCHES)
                                    mode = entry
                                    if (draft.isNotBlank()) {
                                        if (entry == KuwoSearchMode.SONG) onSearch(draft) else onSearchPlaylists(draft)
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 7.dp),
                        ) {
                            Text(
                                entry.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (state.hot.isNotEmpty() && state.query.isBlank() && state.results.isEmpty()) {
            item(key = "hot") {
                Column(Modifier.padding(top = 14.dp)) {
                    Overline("热门搜索", Modifier.padding(start = 6.dp, bottom = 8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                        items(state.hot, key = { it }) { word ->
                            Box(
                                Modifier.clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable {
                                        draft = word
                                        if (mode == KuwoSearchMode.SONG) onSearch(word) else onSearchPlaylists(word)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(word, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        state.message?.let { message ->
            item(key = "message") {
                Text(
                    message,
                    Modifier.fillMaxWidth().padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (state.searching || state.searchingPlaylists) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { OlbyineSpinner() }
            }
        }
        if (mode == KuwoSearchMode.PLAYLIST) {
            items(state.playlists, key = { "pl-${it.id}" }) { playlist ->
                KuwoPlaylistRow(playlist) { onOpenPlaylist(playlist.id) }
            }
        } else {
            itemsIndexed(state.results, key = { _, song -> song.id }) { index, song ->
                KuwoSongRow(
                    song = song,
                    current = currentTrackId == KuwoSong.trackId(song.id),
                    savedIds = savedIds,
                    onClick = { onPlay(state.results, index, false) },
                )
            }
        }
    }
}

/** 播放页「加入歌单」：与网易云 / SoundCloud 同一个 GlassBottomSheet 面板。 */
@Composable
internal fun LxPlaylistPicker(
    playlists: List<LxPlaylist>,
    trackTitle: String,
    onAdd: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var naming by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val lxState = androidx.compose.foundation.lazy.rememberLazyListState()
    val lxAtTop by remember(lxState) {
        androidx.compose.runtime.derivedStateOf { lxState.firstVisibleItemIndex == 0 && lxState.firstVisibleItemScrollOffset <= 2 }
    }
    GlassBottomSheet(onDismissRequest = onDismiss, dismissBlocked = !lxAtTop) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
            Text(
                "收藏到歌单",
                Modifier.padding(horizontal = 22.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.size(10.dp))
            LazyColumn(Modifier.fillMaxWidth(), state = lxState) {
                item(key = "new") {
                    PickerRow(
                        leading = {
                            Box(
                                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)),
                                contentAlignment = Alignment.Center,
                            ) { StyledIcon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onSurface) }
                        },
                        title = "新建歌单",
                        subtitle = null,
                        onClick = { naming = true },
                    )
                }
                items(playlists, key = { it.id }) { playlist ->
                    PickerRow(
                        leading = {
                            UrlImage(
                                playlist.songs.firstOrNull()?.coverUrl,
                                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                            ) {
                                Box(
                                    Modifier.size(48.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    StyledIcon(
                                        Icons.AutoMirrored.Rounded.QueueMusic,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        title = playlist.name,
                        subtitle = "${playlist.songs.size} 首",
                        onClick = {
                            haptics.click(com.olbyine.music.data.HapticArea.MENUS)
                            onAdd(playlist.id)
                        },
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
        }
    }
    if (naming) {
        PlaylistNameDialog("新建洛雪歌单", "", { naming = false }) { name ->
            naming = false
            onCreate(name)
        }
    }
}

@Composable
private fun RecommendCard(
    playlist: KuwoPlaylist,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        UrlImage(
            playlist.coverUrl,
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Spacer(Modifier.height(7.dp))
        Text(
            playlist.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            listOfNotNull(
                playlist.creator.takeIf { it.isNotBlank() },
                playlist.playCount.takeIf { it > 0L }?.let { "播放 ${formatCount(it)}" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private enum class KuwoSearchMode(val label: String) {
    SONG("单曲"),
    PLAYLIST("歌单"),
}

internal fun sanitizeFileName(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").trim().ifBlank { "playlist" }.take(48)

@Composable
private fun LxPlaylistRow(
    playlist: LxPlaylist,
    onClick: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrlImage(
            playlist.songs.firstOrNull()?.coverUrl,
            Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${playlist.songs.size} 首 · ${playlist.origin}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        GhostIconButton(
            Icons.Rounded.FileDownload,
            "导出 .lxmc",
            onExport,
            size = 36.dp,
            iconSize = 17.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GhostIconButton(
            Icons.Rounded.DeleteOutline,
            "删除",
            onDelete,
            size = 36.dp,
            iconSize = 17.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddToPlaylistDialog(
    songs: List<KuwoSong>,
    playlists: List<LxPlaylist>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onCreate: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val label = if (songs.size == 1) "「${songs.first().title}」" else "${songs.size} 首歌曲"
    OlbyineDialog(
        onDismissRequest = onDismiss,
        title = if (creating) "新建歌单并添加" else "添加$label 到歌单",
        confirmButton = {
            if (creating) {
                GlassTextButton(onClick = { onCreate(name) }) { Text("创建并添加") }
            } else {
                GlassTextButton(onClick = { creating = true }) { Text("新建歌单") }
            }
        },
        dismissButton = {
            if (creating) {
                GlassTextButton(onClick = { creating = false }) { Text("返回") }
            } else {
                GlassTextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    ) {
        if (creating) {
            OlbyineTextField(
                value = name,
                onValueChange = { name = it },
                label = "歌单名称",
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (playlists.isEmpty()) {
            Text(
                "还没有歌单，点「新建歌单」建一个。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                playlists.forEach { playlist ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(playlist.id) }
                            .padding(horizontal = 6.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UrlImage(
                            playlist.songs.firstOrNull()?.coverUrl,
                            Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${playlist.songs.size} 首",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NameInputDialog(
    title: String,
    label: String,
    initial: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    OlbyineDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmButton = {
            GlassTextButton(onClick = { if (value.isNotBlank()) onConfirm(value.trim()) }) { Text(confirmText) }
        },
        dismissButton = { GlassTextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        OlbyineTextField(
            value = value,
            onValueChange = { value = it },
            label = label,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun KuwoPlaylistRow(playlist: KuwoPlaylist, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrlImage(
            playlist.coverUrl,
            Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    playlist.creator.takeIf { it.isNotBlank() },
                    "${playlist.songCount} 首",
                    playlist.playCount.takeIf { it > 0L }?.let { "播放 ${formatCount(it)}" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatCount(value: Long): String = when {
    value >= 100_000_000L -> String.format(Locale.US, "%.1f 亿", value / 100_000_000.0)
    value >= 10_000L -> String.format(Locale.US, "%.1f 万", value / 10_000.0)
    else -> value.toString()
}

@Composable
private fun KuwoCollectionPage(
    title: String,
    coverUrl: String? = null,
    description: String,
    songs: List<KuwoSong>,
    bottomPadding: Dp,
    currentTrackId: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlay: (List<KuwoSong>, Int, Boolean) -> Unit,
    showRank: Boolean = false,
    savedIds: Set<Long> = emptySet(),
    onLongPressSong: ((KuwoSong) -> Unit)? = null,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding + 24.dp),
    ) {
        item(key = "head") {
            Column(Modifier.statusBarsPadding().padding(horizontal = 4.dp)) {
                Spacer(Modifier.height(8.dp))
                OlbyineIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回", onBack, size = 38.dp, iconSize = 19.dp)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UrlImage(
                        coverUrl,
                        Modifier.size(108.dp).clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        Box(
                            Modifier.size(108.dp).clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            StyledIcon(
                                Icons.AutoMirrored.Rounded.QueueMusic,
                                null,
                                Modifier.size(34.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${songs.size} 首" + (if (description.isNotBlank()) " · $description" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillButton("播放", { onPlay(songs, 0, false) }, icon = Icons.Rounded.PlayArrow, enabled = songs.isNotEmpty())
                    PillButton("随机", { onPlay(songs, 0, true) }, icon = Icons.Rounded.Shuffle, style = PillStyle.TONAL, enabled = songs.isNotEmpty())
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            KuwoSongRow(
                song = song,
                current = currentTrackId == KuwoSong.trackId(song.id),
                rank = if (showRank) index + 1 else null,
                savedIds = savedIds,
                onClick = { onPlay(songs, index, false) },
                onLongClick = onLongPressSong?.let { handler -> { handler(song) } },
            )
        }
    }
}

@Composable
private fun KuwoSongRow(
    song: KuwoSong,
    current: Boolean,
    rank: Int? = null,
    savedIds: Set<Long> = emptySet(),
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val highlight = if (current) MaterialTheme.colorScheme.primary.copy(alpha = .08f) else androidx.compose.ui.graphics.Color.Transparent
    Row(
        Modifier.fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(highlight)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rank != null) {
            Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                Text(
                    rank.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Box(Modifier.size(46.dp)) {
                UrlImage(
                    song.coverUrl,
                    Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                PlayingCoverScrim(current, LocalIsPlaying.current(), 10.dp, barSize = 15.dp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(song.artist, song.album).filter(String::isNotBlank).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            formatKuwoDuration(song.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .8f),
        )
        val actions = LocalTrackActions.current
        if (actions != null) {
            val track = remember(song) { song.toTrack() }
            GhostIconButton(
                Icons.AutoMirrored.Rounded.PlaylistPlay,
                "下一首播放",
                { actions.playNext(track) },
                size = 36.dp,
                iconSize = 17.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HeartWithMenu(
                track = { track },
                liked = song.id in savedIds,
                onToggle = { actions.addToPlaylist(track) },
                iconSize = 20.dp,
                minSize = 40.dp,
            )
        }
    }
}

/** 通用「粘贴 JSON / 链接 / 选文件」导入弹窗。 */
@Composable
internal fun LxImportDialog(
    title: String,
    hint: String,
    onDismiss: () -> Unit,
    onText: (text: String, name: String) -> Unit,
    onBytes: ((bytes: ByteArray, name: String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext as OlbyineApplication
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        if (bytes == null || bytes.isEmpty()) {
            error = "读取文件失败"
            return@rememberLauncherForActivityResult
        }
        val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "导入的歌单"
        if (onBytes != null) onBytes(bytes, name) else onText(String(bytes, Charsets.UTF_8), name)
    }

    fun submit() {
        val target = url.trim()
        if (target.isEmpty()) {
            error = "请粘贴内容或链接"
            return
        }
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            val name = target.take(24).replace("\n", " ")
            onText(target, name.ifBlank { "导入的歌单" })
            return
        }
        busy = true
        error = null
        scope.launch {
            val result = runCatching { app.lxHttp.fetchBytes(target) }
            busy = false
            result.fold(
                onSuccess = { bytes ->
                    val name = target.substringAfterLast('/').substringBeforeLast('?').substringBeforeLast('.')
                    if (onBytes != null) {
                        onBytes(bytes, name.ifBlank { "导入的歌单" })
                    } else {
                        onText(String(bytes, Charsets.UTF_8), name.ifBlank { "导入的歌单" })
                    }
                },
                onFailure = { error = it.message ?: "下载失败" },
            )
        }
    }

    OlbyineDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmButton = {
            GlassTextButton(onClick = { if (!busy) submit() }) {
                Text(if (busy) "下载中…" else "导入")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassTextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("选文件") }
                GlassTextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    ) {
        Column {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            OlbyineTextField(
                value = url,
                onValueChange = { url = it },
                label = "JSON 内容或 http(s) 链接",
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun formatKuwoDuration(ms: Long): String {
    if (ms <= 0L) return "--:--"
    val total = ms / 1000
    return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}