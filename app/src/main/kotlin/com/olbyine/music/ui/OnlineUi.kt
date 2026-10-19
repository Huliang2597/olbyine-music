package com.olbyine.music.ui

import android.annotation.SuppressLint
import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.olbyine.music.NeteaseLoginUiState
import com.olbyine.music.NeteaseUiState
import com.olbyine.music.data.HapticArea
import com.olbyine.music.data.NeteasePlaylistSummary
import com.olbyine.music.data.NeteaseSong
import com.olbyine.music.data.QrLoginState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val NeteaseRed = Color(0xFFE5303C)

@Composable
internal fun NeteaseScreen(
    state: NeteaseUiState,
    onlinePlayback: Boolean,
    bottomContentPadding: Dp,
    currentTrackId: String?,
    isPlaying: Boolean,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onOpenPlaylist: (NeteasePlaylistSummary) -> Unit,
    onClosePlaylist: () -> Unit,
    onPlay: (List<NeteaseSong>, Int, Boolean) -> Unit,
    onLike: (Long) -> Unit,
    onSaveLocal: () -> Unit,
    onOnlinePlayback: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    client: com.olbyine.music.data.NeteaseClient? = null,
) {
    val open = state.openPlaylist
    val pages = rememberPageStack<NeteasePlaylistSummary>()
    LaunchedEffect(open) {
        if (open != null && pages.top?.id != open.id) pages.pushFromTouch(open)
        if (open == null && !pages.isEmpty) pages.pop()
    }
    var shown by remember { mutableStateOf(state) }
    if (state.openPlaylist != null) shown = state
    PageStack(
        pages,
        backEnabled = open != null,
        onBack = onClosePlaylist,
        base = { NeteaseHome(state, onlinePlayback, bottomContentPadding, currentTrackId, client, onLogin, onRefresh, onOpenPlaylist, onPlay, onOnlinePlayback, onSignOut) },
    ) { playlist ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}) {
            NeteasePlaylistPage(
                playlist = playlist,
                state = if (shown.openPlaylist?.id == playlist.id) shown else state,
                onlinePlayback = onlinePlayback,
                bottomContentPadding = bottomContentPadding,
                currentTrackId = currentTrackId,
                isPlaying = isPlaying,
                onBack = onClosePlaylist,
                onPlay = onPlay,
                onLike = onLike,
                onSaveLocal = onSaveLocal,
            )
        }
    }
}
@Composable
private fun NeteaseHome(
    state: NeteaseUiState,
    onlinePlayback: Boolean,
    bottomContentPadding: Dp,
    currentTrackId: String?,
    client: com.olbyine.music.data.NeteaseClient?,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onOpenPlaylist: (NeteasePlaylistSummary) -> Unit,
    onPlay: (List<NeteaseSong>, Int, Boolean) -> Unit,
    onOnlinePlayback: (Boolean) -> Unit,
    onSignOut: () -> Unit,
) {
    var tab by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(NeteaseTab.MINE) }
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().padding(start = 20.dp, top = 18.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("网易云音乐", Modifier.weight(1f), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
            LocalStreamingSwitch.current?.invoke()
        }
        PillTabs(
            NeteaseTab.entries.map { it to it.label },
            tab,
            { haptics.tick(HapticArea.MENUS); tab = it },
            Modifier.padding(bottom = 10.dp),
        )
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.weight(1f),
            transitionSpec = { fadeIn(tween(150, easing = OlbyineTokens.Motion.easeOut)) togetherWith fadeOut(tween(90, easing = OlbyineTokens.Motion.easeOut)) },
            label = "netease-tab",
        ) { tab ->
            when {
                tab == NeteaseTab.MINE || client == null ->
                    NeteaseMine(state, onlinePlayback, bottomContentPadding, onLogin, onRefresh, onOpenPlaylist, onOnlinePlayback, onSignOut)
                tab == NeteaseTab.DISCOVER -> NeteaseDiscover(client, state.account != null, bottomContentPadding, onOpenPlaylist)
                tab == NeteaseTab.CHARTS -> NeteaseCharts(client, bottomContentPadding, onOpenPlaylist)
                tab == NeteaseTab.SEARCH -> NeteaseSearch(client, bottomContentPadding, currentTrackId, onOpenPlaylist) { songs, index -> onPlay(songs, index, false) }
                else -> NeteaseMessages(client, state.account != null, bottomContentPadding, onLogin)
            }
        }
    }
}

@Composable
private fun NeteaseMine(
    state: NeteaseUiState,
    onlinePlayback: Boolean,
    bottomContentPadding: Dp,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onOpenPlaylist: (NeteasePlaylistSummary) -> Unit,
    onOnlinePlayback: (Boolean) -> Unit,
    onSignOut: () -> Unit,
) {
    val account = state.account
    val liked = state.playlists.firstOrNull { it.liked }
    val own = state.playlists.filter { it.own && !it.liked }
    val saved = state.playlists.filter { !it.own && !it.liked }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = bottomContentPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (account == null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "login") { LoginCard(onLogin) }
            return@LazyVerticalGrid
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "account") {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .78f))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val openProfile = LocalNeteaseProfile.current
                    UrlImage(account.avatarUrl, Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { openProfile?.invoke(account.userId) })
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(account.nickname, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (account.vipType > 0) {
                                Spacer(Modifier.width(6.dp))
                                Badge("VIP", color = NeteaseRed, filled = true)
                            }
                        }
                        Text(
                            "${state.playlists.size} 个歌单",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    GhostIconButton(Icons.Rounded.Refresh, "刷新", onRefresh, size = 40.dp, iconSize = 20.dp)
                    GhostIconButton(Icons.AutoMirrored.Rounded.Logout, "退出登录", onSignOut, size = 40.dp, iconSize = 20.dp)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .05f))
                        .clickable { onOnlinePlayback(!onlinePlayback) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StyledIcon(Icons.Rounded.Cloud, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("在线播放", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (onlinePlayback) "曲库里没有的歌从网易云在线播放" else "只播放本地曲库里已有的歌曲",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OlbyineSwitch(onlinePlayback, onOnlinePlayback)
                }
                NeteaseMineShortcuts(account.userId)
            }
        }
        if (state.loading && state.playlists.isEmpty()) skeletonCards(6)
        state.message?.let { message ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "message") {
                Text(message, itemMotion().padding(8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (liked != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "liked") { MotionItem { LikedCard(liked) { onOpenPlaylist(liked) } } }
        }
        if (own.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "own-title") { Overline("创建的歌单", itemMotion().padding(start = 6.dp, top = 14.dp, bottom = 2.dp)) }
            itemsIndexed(own, key = { index, it -> "own:$index:${it.id}" }) { _, playlist -> MotionItem { PlaylistTile(playlist) { onOpenPlaylist(playlist) } } }
        }
        if (saved.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "saved-title") { Overline("收藏的歌单", itemMotion().padding(start = 6.dp, top = 14.dp, bottom = 2.dp)) }
            itemsIndexed(saved, key = { index, it -> "saved:$index:${it.id}" }) { _, playlist -> MotionItem { PlaylistTile(playlist) { onOpenPlaylist(playlist) } } }
        }
    }
}

@Composable
private fun LoginCard(onLogin: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(NeteaseRed.copy(alpha = .9f), Color(0xFFB0213A))))
            .padding(22.dp),
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
            StyledIcon(Icons.Rounded.Cloud, null, Modifier.size(30.dp), tint = Color.White)
        }
        Spacer(Modifier.height(16.dp))
        Text("登录网易云音乐", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "登录后可以收听你创建和收藏的歌单、「我喜欢的音乐」，红心会同步回网易云。能播放什么由网易云按你的账号决定（会员歌曲需要会员）。凭据只保存在本机，只发送给网易云。",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = .86f),
        )
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.height(44.dp).clip(CircleShape).background(Color.White).clickable(onClick = onLogin).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("登录", style = MaterialTheme.typography.labelLarge, color = NeteaseRed, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LikedCard(playlist: NeteasePlaylistSummary, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFFF4D6D), Color(0xFFB72A6F))))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
            StyledIcon(Icons.Rounded.Favorite, null, Modifier.size(32.dp), tint = Color.White)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("我喜欢的音乐", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("${playlist.trackCount} 首", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .85f))
        }
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            StyledIcon(Icons.Rounded.PlayArrow, "打开", tint = Color(0xFFD7335F))
        }
    }
}

@Composable
private fun PlaylistTile(playlist: NeteasePlaylistSummary, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(6.dp)) {
        UrlImage(
            playlist.coverUrl?.let { "$it?param=400y400" },
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Text(playlist.name, Modifier.padding(start = 2.dp, top = 8.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOf("${playlist.trackCount} 首", playlist.creator.takeIf { !playlist.own }).filterNotNull().joinToString(" · "),
            Modifier.padding(start = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NeteasePlaylistPage(
    playlist: NeteasePlaylistSummary,
    state: NeteaseUiState,
    onlinePlayback: Boolean,
    bottomContentPadding: Dp,
    currentTrackId: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlay: (List<NeteaseSong>, Int, Boolean) -> Unit,
    onLike: (Long) -> Unit,
    onSaveLocal: () -> Unit,
) {
    val allSongs = state.songs
    var query by androidx.compose.runtime.saveable.rememberSaveable(playlist.id) { mutableStateOf("") }
    val songs = remember(allSongs, query) { filterSongs(allSongs, query) }
    val localCount = remember(allSongs, state.localMatches) { allSongs.count { it.id in state.localMatches } }
    val haptics = rememberHaptics()
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomContentPadding + 24.dp),
    ) {
        item(key = "header") {
            Column(Modifier.statusBarsPadding().padding(horizontal = 8.dp)) {
                Spacer(Modifier.height(8.dp))
                OlbyineIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回", onBack, size = 38.dp, iconSize = 19.dp)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (playlist.liked) {
                        Box(
                            Modifier.size(112.dp).clip(RoundedCornerShape(20.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFFFF4D6D), Color(0xFFB72A6F)))),
                            contentAlignment = Alignment.Center,
                        ) { StyledIcon(Icons.Rounded.Favorite, null, Modifier.size(48.dp), tint = Color.White) }
                    } else {
                        UrlImage(playlist.coverUrl?.let { "$it?param=500y500" }, Modifier.size(112.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (playlist.liked) "我喜欢的音乐" else playlist.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            (if (allSongs.isNotEmpty()) "${allSongs.size} 首" else "${playlist.trackCount} 首") + " · 本地已有 $localCount 首" +
                                (playlist.creator.takeIf { it.isNotBlank() && !playlist.own }?.let { " · $it" } ?: "") +
                                (playlist.note.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("播放", { haptics.click(HapticArea.PLAYBACK); onPlay(songs, 0, false) }, icon = Icons.Rounded.PlayArrow, enabled = songs.isNotEmpty())
                    PillButton("随机", { haptics.click(HapticArea.PLAYBACK); onPlay(songs, 0, true) }, icon = Icons.Rounded.Shuffle, style = PillStyle.TONAL, enabled = songs.isNotEmpty())
                    Spacer(Modifier.weight(1f))
                    GhostIconButton(Icons.Rounded.LibraryAdd, "存为本地歌单", onSaveLocal, enabled = localCount > 0)
                }
                if (!onlinePlayback) {
                    Text(
                        "在线播放已关闭：只会播放本地曲库里已有的歌曲。",
                        Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (allSongs.size > 8) {
                    Spacer(Modifier.height(12.dp))
                    OlbyineSearchField(query, { query = it }, if (playlist.liked) "在我喜欢的音乐中搜索" else "在歌单中搜索", Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        if (state.songsLoading && songs.isEmpty()) {
            skeletonRows(10)
        } else if (state.songsLoading) {
            item(key = "loading") { Box(itemMotion().fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { OlbyineSpinner() } }
        }
        state.message?.let { message ->
            item(key = "message") { Text(message, itemMotion().padding(12.dp), color = MaterialTheme.colorScheme.error) }
        }
        items(songs.size, key = { index -> "$index:${songs[index].id}" }) { index ->
            val song = songs[index]
            val local = song.id in state.localMatches
            val playable = local || (onlinePlayback && !song.unavailable)
            val current = currentTrackId != null && (currentTrackId == NeteaseSong.trackId(song.id) || currentTrackId == state.localMatches[song.id])
            val highlight by androidx.compose.animation.animateColorAsState(
                if (current) MaterialTheme.colorScheme.primary.copy(alpha = .08f) else Color.Transparent, tween(OlbyineTokens.Motion.standardMs), label = "row-current",
            )
            val dim by androidx.compose.animation.core.animateFloatAsState(if (playable) 1f else .42f, tween(OlbyineTokens.Motion.standardMs), label = "row-playable")
            Row(
                itemMotion().fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(highlight)
                    .clickable(enabled = playable) { haptics.click(HapticArea.PLAYBACK); onPlay(songs, index, false) }
                    .padding(start = 6.dp, top = 6.dp, bottom = 6.dp)
                    .graphicsLayer { alpha = dim },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp)) {
                    UrlImage(song.coverUrl?.replace("600y600", "160y160"), Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                    androidx.compose.animation.AnimatedVisibility(
                        visible = current,
                        enter = fadeIn(tween(OlbyineTokens.Motion.standardMs, easing = OlbyineTokens.Motion.easeOut)),
                        exit = fadeOut(tween(OlbyineTokens.Motion.quickMs)),
                    ) {
                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = .42f)), contentAlignment = Alignment.Center) {
                            EqualizerBars(isPlaying, Color.White, Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = androidx.compose.animation.animateColorAsState(
                            if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, tween(OlbyineTokens.Motion.standardMs), label = "row-title",
                        ).value,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (local) {
                            Badge("本地", color = MaterialTheme.colorScheme.primary, filled = true)
                            Spacer(Modifier.width(5.dp))
                        } else if (song.unavailable) {
                            Badge("不可播放", filled = true)
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            listOf(song.artist, song.album).filter(String::isNotBlank).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (song.unavailable && !local) {
                    LocalSearchSoundCloud.current?.let { search ->
                        GhostIconButton(Icons.Rounded.TravelExplore, "在 SoundCloud 搜索音源", { search(song.title + " " + song.artists.firstOrNull().orEmpty()) },
                            size = 40.dp, iconSize = 19.dp, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                LocalNeteaseComments.current?.let { openComments ->
                    GhostIconButton(Icons.Rounded.ChatBubbleOutline, "评论", { openComments(song.id, song.title) }, size = 40.dp, iconSize = 19.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val liked = song.id in state.liked
                HeartWithMenu(
                    track = { song.toTrack() },
                    liked = liked,
                    onToggle = { onLike(song.id) },
                    likedColor = NeteaseRed,
                    idleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconSize = 20.dp,
                    minSize = 40.dp,
                )
            }
        }
    }
}

private enum class LoginMethod(val label: String) { QR("扫码"), WEB("手机号"), COOKIE("粘贴凭据") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NeteaseLoginSheet(
    state: NeteaseLoginUiState,
    onRefreshQr: () -> Unit,
    onCookie: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var method by rememberSaveable { mutableStateOf(LoginMethod.QR) }
    var webOpen by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .94f),
    ) {
        WindowBlurBehind(radius = 24.dp)
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
            Text("登录网易云音乐", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            PillTabs(
                items = LoginMethod.entries.map { it to it.label },
                selected = method,
                onSelect = { method = it },
                contentPadding = PaddingValues(0.dp),
            )
            Spacer(Modifier.height(16.dp))
            SwapContent(method, Modifier.fillMaxWidth()) { shownMethod ->
            when (shownMethod) {
                LoginMethod.QR -> QrLogin(state, onRefreshQr)
                LoginMethod.WEB -> Column {
                    Text(
                        "在网易云自己的登录页里用手机号 + 验证码（或密码）登录，登录完成后会自动回到这里。Olbyine 不会看到你的密码。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    PillButton("打开网易云登录页", { webOpen = true }, icon = Icons.Rounded.Smartphone)
                }
                LoginMethod.COOKIE -> CookieLogin(onCookie)
            }
            }
            state.error?.let {
                Text(it, Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (state.busy && method != LoginMethod.QR) {
                Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) { OlbyineSpinner(Modifier.size(24.dp)) }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(20.dp))
    }
    if (webOpen) {
        NeteaseWebLogin(
            onCookie = { cookie ->
                webOpen = false
                onCookie(cookie)
            },
            onDismiss = { webOpen = false },
        )
    }
}

@Composable
private fun QrLogin(state: NeteaseLoginUiState, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val content = state.qrContent
    var saved by remember(content) { mutableStateOf(false) }
    val bitmap by androidx.compose.runtime.produceState<Bitmap?>(null, content) {
        value = content?.let { withContext(Dispatchers.Default) { runCatching { qrBitmap(it, 720) }.getOrNull() } }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(220.dp).clip(RoundedCornerShape(24.dp)).background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            SwapContent(bitmap, Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { image ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (image != null) Image(image.asImageBitmap(), "登录二维码", Modifier.fillMaxSize())
                    else OlbyineSpinner(color = NeteaseRed)
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = state.status?.state == QrLoginState.EXPIRED,
                enter = fadeIn(tween(OlbyineTokens.Motion.standardMs)),
                exit = fadeOut(tween(OlbyineTokens.Motion.quickMs)),
            ) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .92f)).clickable(onClick = onRefresh),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        StyledIcon(Icons.Rounded.Refresh, null, tint = NeteaseRed)
                        Text("二维码已过期，点按刷新", color = Color(0xFF333333), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = state.status?.state == QrLoginState.CONFIRMED,
                enter = fadeIn(tween(OlbyineTokens.Motion.standardMs)),
                exit = fadeOut(tween(OlbyineTokens.Motion.quickMs)),
            ) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .94f)), contentAlignment = Alignment.Center) {
                    AnimatedTick(true, NeteaseRed, size = 72.dp, appear = true)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SwapContent(
            when (state.status?.state) {
                QrLoginState.SCANNED -> "已扫码" + (state.status.nickname?.let { "（$it）" } ?: "") + "，请在网易云 App 里确认登录"
                QrLoginState.CONFIRMED -> "登录成功"
                QrLoginState.EXPIRED -> "二维码已过期"
                QrLoginState.ERROR -> state.status.message ?: "登录失败"
                else -> "打开网易云音乐 App，用「扫一扫」扫描二维码"
            },
            contentAlignment = Alignment.Center,
        ) { line ->
            Text(line, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "只有这一台手机？点下面保存二维码，再到网易云 App「扫一扫」里从相册选择这张图片。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(
                if (saved) "已保存到相册" else "保存二维码",
                {
                    val image = bitmap ?: return@PillButton
                    scope.launch {
                        saved = withContext(Dispatchers.IO) { saveToGallery(context, image) }
                    }
                },
                icon = Icons.Rounded.Download,
                style = PillStyle.TONAL,
                enabled = bitmap != null,
            )
            PillButton("刷新", onRefresh, icon = Icons.Rounded.QrCode2, style = PillStyle.OUTLINED)
        }
    }
}

@Composable
private fun CookieLogin(onCookie: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf("") }
    Column {
        Text(
            "已经在电脑浏览器登录过网易云？在 music.163.com 的开发者工具里复制 Cookie 中的 MUSIC_U（或整段 Cookie）粘贴到这里。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OlbyineTextField(value, { value = it }, "MUSIC_U 或 Cookie")
        Spacer(Modifier.height(12.dp))
        PillButton("登录", { onCookie(value) }, icon = Icons.Rounded.VpnKey, enabled = value.length > 40)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun NeteaseWebLogin(onCookie: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                GhostIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "关闭", onDismiss)
                Text("网易云音乐登录", style = MaterialTheme.typography.titleMedium)
            }
            var delivered by remember { mutableStateOf(false) }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        val cookies = CookieManager.getInstance()
                        cookies.setAcceptCookie(true)
                        cookies.setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                val cookie = cookies.getCookie("https://music.163.com").orEmpty()
                                if (!delivered && cookie.contains("MUSIC_U=")) {
                                    delivered = true
                                    onCookie(cookie)
                                }
                            }
                        }
                        loadUrl("https://music.163.com/m/login")
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}

private fun qrBitmap(content: String, size: Int): Bitmap {
    val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(
        content,
        com.google.zxing.BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(com.google.zxing.EncodeHintType.MARGIN to 1),
    )
    val pixels = IntArray(size * size) { index ->
        if (matrix.get(index % size, index / size)) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

private fun saveToGallery(context: android.content.Context, bitmap: Bitmap): Boolean = runCatching {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "olbyine-netease-login-${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Olbyine")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } != null
}.getOrDefault(false)
