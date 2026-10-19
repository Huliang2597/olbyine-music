package com.olbyine.music.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.olbyine.music.data.HapticArea
import com.olbyine.music.data.NavidromeClient
import com.olbyine.music.data.NdAlbum
import com.olbyine.music.data.NdArtist
import com.olbyine.music.data.NdPlaylist
import com.olbyine.music.data.NdSong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class NdTab(val label: String) { ALBUMS("专辑"), ARTISTS("艺人"), PLAYLISTS("歌单"), STARRED("收藏"), SEARCH("搜索") }

private sealed interface NdRoute {
    data class AlbumPage(val album: NdAlbum) : NdRoute
    data class ArtistPage(val artist: NdArtist) : NdRoute
    data class PlaylistPage(val playlist: NdPlaylist) : NdRoute
}

private class NdContext(
    val client: NavidromeClient,
    val currentTrackId: String?,
    val isPlaying: Boolean,
    val onPlay: (List<NdSong>, Int, Boolean) -> Unit,
    val open: (NdRoute) -> Unit,
)

private val LocalNd = staticCompositionLocalOf<NdContext?> { null }

@Composable
internal fun NavidromeScreen(
    client: NavidromeClient,
    bottomPadding: Dp,
    currentTrackId: String?,
    isPlaying: Boolean,
    onPlay: (List<NdSong>, Int, Boolean) -> Unit,
    revealTitle: String? = null,
    onRevealHandled: () -> Unit = {},
) {
    val server by client.server.collectAsState()
    var pendingSong by remember { mutableStateOf<String?>(null) }
    val routes = rememberPageStack<NdRoute>()
    LaunchedEffect(revealTitle) {
        val title = revealTitle ?: return@LaunchedEffect
        val found = runCatching { client.search(title) }.getOrNull()
        val song = found?.songs?.firstOrNull { it.title.equals(title, ignoreCase = true) } ?: found?.songs?.firstOrNull()
        val albumId = song?.albumId
        if (albumId != null) {
            val album = found?.albums?.firstOrNull { it.id == albumId }
                ?: runCatching { client.album(albumId).first }.getOrNull()
            if (album != null) {
                pendingSong = song.id
                routes.clear()
                routes.pushFromTouch(NdRoute.AlbumPage(album))
            }
        }
        onRevealHandled()
    }
    val context = NdContext(client, currentTrackId, isPlaying, onPlay) { routes.pushFromTouch(it) }
    CompositionLocalProvider(LocalNd provides context) {
        if (server == null) {
            Column(Modifier.fillMaxSize()) {
                NdHeader()
                NdSignIn(client)
            }
        } else {
            PageStack(routes, backEnabled = true, base = { NdHome(bottomPadding) }) { route ->
                val back: () -> Unit = { routes.pop() }
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                        .clickable(remember { MutableInteractionSource() }, indication = null) {},
                ) {
                    when (route) {
                        is NdRoute.AlbumPage -> NdAlbumPage(route.album, bottomPadding, back, pendingSong) { pendingSong = null }
                        is NdRoute.ArtistPage -> NdArtistPage(route.artist, bottomPadding, back)
                        is NdRoute.PlaylistPage -> NdPlaylistPage(route.playlist, bottomPadding, back)
                    }
                }
            }
        }
    }
}

@Composable
private fun NdHeader() {
    Row(Modifier.statusBarsPadding().padding(start = 20.dp, top = 18.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Navidrome", Modifier.weight(1f), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
        LocalStreamingSwitch.current?.invoke()
    }
}

@Composable
private fun NdSignIn(client: NavidromeClient) {
    var address by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    Column(
        Modifier.fillMaxWidth().widthIn(max = 560.dp).padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            "连接自建的 Navidrome（或任何兼容 Subsonic 的服务器），直接在线播放其中的音乐。密码不会被保存，只保存加盐后的登录令牌。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OlbyineTextField(address, { address = it }, "服务器地址，例如 https://music.example.com 或 192.168.1.5:4533")
        Spacer(Modifier.height(10.dp))
        OlbyineTextField(user, { user = it }, "用户名")
        Spacer(Modifier.height(10.dp))
        OlbyineTextField(password, { password = it }, "密码", password = true)
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        PillButton(
            if (busy) "正在连接…" else "连接",
            {
                busy = true
                error = null
                scope.launch {
                    try {
                        client.signIn(address, user, password)
                        haptics.confirm(HapticArea.MENUS)
                    } catch (failure: Throwable) {
                        if (failure is CancellationException) throw failure
                        error = failure.message ?: "连接失败"
                    }
                    busy = false
                }
            },
            icon = Icons.Rounded.Dns,
            enabled = !busy && address.isNotBlank() && user.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NdHome(bottomPadding: Dp) {
    val nd = LocalNd.current ?: return
    var tab by rememberSaveable { mutableStateOf(NdTab.ALBUMS) }
    val server by nd.client.server.collectAsState()
    Column(Modifier.fillMaxSize()) {
        NdHeader()
        Row(Modifier.padding(start = 20.dp, end = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(server?.user, server?.name?.ifBlank { null }).joinToString(" · "),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            GhostIconButton(Icons.AutoMirrored.Rounded.Logout, "断开服务器", { nd.client.signOut() }, size = 36.dp, iconSize = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PillTabs(NdTab.entries.map { it to it.label }, tab, { tab = it }, Modifier.padding(vertical = 6.dp))
        AnimatedContent(
            targetState = tab,
            transitionSpec = { tabSwitch(if (targetState.ordinal > initialState.ordinal) 1 else -1, false, false) },
            label = "nd-tab",
        ) { shown ->
            when (shown) {
                NdTab.ALBUMS -> NdAlbums(bottomPadding)
                NdTab.ARTISTS -> NdArtists(bottomPadding)
                NdTab.PLAYLISTS -> NdPlaylists(bottomPadding)
                NdTab.STARRED -> NdSongsList(bottomPadding, key = "starred") { nd.client.starred() }
                NdTab.SEARCH -> NdSearch(bottomPadding)
            }
        }
    }
}

private val AlbumSorts = listOf("newest" to "最新", "recent" to "最近播放", "frequent" to "常听", "random" to "随机", "alphabeticalByName" to "名称")

@Composable
private fun NdAlbums(bottomPadding: Dp) {
    val nd = LocalNd.current ?: return
    var sort by rememberSaveable { mutableStateOf("newest") }
    val (state, retry) = rememberLoad(sort) { nd.client.albums(sort, size = 120) }
    LazyVerticalGrid(
        GridCells.Adaptive(150.dp),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = bottomPadding + 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "sorts", span = { GridItemSpan(maxLineSpan) }) {
            PillTabs(AlbumSorts, sort, { sort = it }, contentPadding = PaddingValues(0.dp))
        }
        val albums = state.value
        if (albums == null) {
            item(key = "state", span = { GridItemSpan(maxLineSpan) }) { LoadingOrError(state.loading, state.error, retry) }
        } else {
            items(albums, key = { it.id }) { album -> NdAlbumCard(album, Modifier.animateItem()) }
        }
    }
}

@Composable
private fun NdAlbumCard(album: NdAlbum, modifier: Modifier = Modifier) {
    val nd = LocalNd.current ?: return
    Column(modifier.clip(RoundedCornerShape(16.dp)).clickable { nd.open(NdRoute.AlbumPage(album)) }.padding(4.dp)) {
        NdCover(nd.client.coverUrl(album.coverArt, 400), Modifier.fillMaxWidth().aspectRatio(1f), Icons.Rounded.Album)
        Spacer(Modifier.height(8.dp))
        Text(album.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(album.artist.ifBlank { null }, album.year.takeIf { it > 0 }?.toString()).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NdCover(url: String?, modifier: Modifier, fallback: androidx.compose.ui.graphics.vector.ImageVector, corner: Dp = 14.dp) {
    UrlImage(url, modifier.clip(RoundedCornerShape(corner)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            StyledIcon(fallback, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NdArtists(bottomPadding: Dp) {
    val nd = LocalNd.current ?: return
    val (state, retry) = rememberLoad(Unit) { nd.client.artists() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = bottomPadding + 24.dp)) {
        val list = state.value
        if (list == null) item(key = "state") { LoadingOrError(state.loading, state.error, retry) }
        else items(list, key = { it.id }) { artist -> NdArtistRow(artist, Modifier.animateItem()) }
    }
}

@Composable
private fun NdArtistRow(artist: NdArtist, modifier: Modifier = Modifier) {
    val nd = LocalNd.current ?: return
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { nd.open(NdRoute.ArtistPage(artist)) }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NdCover(nd.client.coverUrl(artist.coverArt, 200), Modifier.size(48.dp), Icons.Rounded.Person, corner = 24.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(artist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (artist.albumCount > 0) Text("${artist.albumCount} 张专辑", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NdPlaylists(bottomPadding: Dp) {
    val nd = LocalNd.current ?: return
    val (state, retry) = rememberLoad(Unit) { nd.client.playlists() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = bottomPadding + 24.dp)) {
        val list = state.value
        if (list == null) item(key = "state") { LoadingOrError(state.loading, state.error, retry) }
        else if (list.isEmpty()) item(key = "empty") { EmptyState(Icons.AutoMirrored.Rounded.QueueMusic, "服务器上还没有歌单") }
        else items(list, key = { it.id }) { playlist ->
            Row(
                Modifier.animateItem().fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { nd.open(NdRoute.PlaylistPage(playlist)) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NdCover(nd.client.coverUrl(playlist.coverArt, 200), Modifier.size(52.dp), Icons.AutoMirrored.Rounded.QueueMusic, corner = 12.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull("${playlist.songCount} 首", playlist.owner.ifBlank { null }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun NdSongsList(bottomPadding: Dp, key: Any, load: suspend () -> List<NdSong>) {
    val (state, retry) = rememberLoad(key) { load() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = bottomPadding + 24.dp)) {
        val songs = state.value
        if (songs == null) item(key = "state") { LoadingOrError(state.loading, state.error, retry) }
        else if (songs.isEmpty()) item(key = "empty") { EmptyState(Icons.AutoMirrored.Rounded.QueueMusic, "这里还没有歌曲") }
        else {
            item(key = "play") { NdPlayButtons(songs) }
            itemsIndexed(songs, key = { index, song -> "${song.id}:$index" }) { index, _ -> NdSongRow(songs, index, Modifier.animateItem()) }
        }
    }
}

@Composable
private fun NdPlayButtons(songs: List<NdSong>) {
    val nd = LocalNd.current ?: return
    Row(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("播放", { nd.onPlay(songs, 0, false) }, icon = Icons.Rounded.PlayArrow, enabled = songs.isNotEmpty())
        PillButton("随机", { nd.onPlay(songs, 0, true) }, icon = Icons.Rounded.Shuffle, style = PillStyle.TONAL, enabled = songs.isNotEmpty())
    }
}

@Composable
private fun NdSongRow(songs: List<NdSong>, index: Int, modifier: Modifier = Modifier, showCover: Boolean = true) {
    val nd = LocalNd.current ?: return
    val song = songs.getOrNull(index) ?: return
    val current = nd.currentTrackId == "nd:${song.id}"
    val haptics = rememberHaptics()
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (current) accent.copy(alpha = .09f) else Color.Transparent)
            .clickable { haptics.click(HapticArea.PLAYBACK); nd.onPlay(songs, index, false) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCover) {
            Box(Modifier.size(48.dp)) {
                NdCover(nd.client.coverUrl(song.coverArt, 160), Modifier.fillMaxSize(), Icons.Rounded.Album, corner = 10.dp)
                if (current) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = .35f)), contentAlignment = Alignment.Center) {
                        EqualizerBars(nd.isPlaying, Color.White)
                    }
                }
            }
        } else {
            Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                if (current) EqualizerBars(nd.isPlaying, accent)
                else Text(song.track.takeIf { it > 0 }?.toString() ?: "${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleSmall, color = if (current) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(song.artist.ifBlank { null }, song.album.ifBlank { null }.takeIf { showCover }, song.suffix.uppercase().ifBlank { null }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(formatDuration(song.durationSec * 1000), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NdSearch(bottomPadding: Dp) {
    val nd = LocalNd.current ?: return
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    LaunchedSearch(query) { submitted = it }
    val (state, retry) = rememberLoad(submitted) { if (submitted.isBlank()) null else nd.client.search(submitted) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = bottomPadding + 24.dp)) {
        item(key = "field") { OlbyineSearchField(query, { query = it }, "搜索歌曲、专辑、艺人", Modifier.padding(6.dp)) }
        val result = state.value
        if (submitted.isNotBlank() && result == null) item(key = "state") { LoadingOrError(state.loading, state.error, retry) }
        if (result != null) {
            if (result.artists.isNotEmpty()) {
                item(key = "artists-title") { NdSection("艺人") }
                items(result.artists, key = { "a:" + it.id }) { NdArtistRow(it) }
            }
            if (result.albums.isNotEmpty()) {
                item(key = "albums-title") { NdSection("专辑") }
                items(result.albums, key = { "al:" + it.id }) { album ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { nd.open(NdRoute.AlbumPage(album)) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NdCover(nd.client.coverUrl(album.coverArt, 200), Modifier.size(52.dp), Icons.Rounded.Album, corner = 12.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(album.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(album.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
            if (result.songs.isNotEmpty()) {
                item(key = "songs-title") { NdSection("歌曲") }
                itemsIndexed(result.songs, key = { index, song -> "s:${song.id}:$index" }) { index, _ -> NdSongRow(result.songs, index) }
            }
            if (result.artists.isEmpty() && result.albums.isEmpty() && result.songs.isEmpty()) {
                item(key = "none") { EmptyState(Icons.Rounded.Album, "没有找到「$submitted」") }
            }
        }
    }
}

@Composable
private fun LaunchedSearch(query: String, onSubmit: (String) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(query) {
        kotlinx.coroutines.delay(400)
        onSubmit(query.trim())
    }
}

@Composable
private fun NdSection(title: String) {
    Text(title, Modifier.padding(start = 10.dp, top = 16.dp, bottom = 6.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun NdPageTop(title: String, subtitle: String?, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        OlbyineIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回", onBack, size = 40.dp, iconSize = 20.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
    }
}

@Composable
private fun NdAlbumPage(
    album: NdAlbum,
    bottomPadding: Dp,
    onBack: () -> Unit,
    revealSongId: String? = null,
    onRevealed: () -> Unit = {},
) {
    val nd = LocalNd.current ?: return
    val (state, retry) = rememberLoad(album.id) { nd.client.album(album.id) }
    val songs = state.value?.second
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var handledReveal by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(revealSongId, songs) {
        val id = revealSongId ?: return@LaunchedEffect
        if (handledReveal == id) return@LaunchedEffect
        val index = songs?.indexOfFirst { it.id == id } ?: -1
        if (index < 0) return@LaunchedEffect
        handledReveal = id
        listState.scrollToItem(index + 1)
        onRevealed()
    }
    Column(Modifier.fillMaxSize()) {
        NdPageTop(album.name, album.artist, onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = bottomPadding + 24.dp),
        ) {
            item(key = "head") {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    NdCover(nd.client.coverUrl(album.coverArt, 600), Modifier.size(132.dp), Icons.Rounded.Album, corner = 18.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(album.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(album.artist, Modifier.clip(CircleShape).clickable {
                            album.artistId?.let { nd.open(NdRoute.ArtistPage(NdArtist(it, album.artist, 0, null))) }
                        }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                        Text(
                            listOfNotNull(album.year.takeIf { it > 0 }?.toString(), "${(songs?.size ?: album.songCount)} 首", formatTotalDuration(album.durationSec * 1000)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (songs == null) item(key = "state") { LoadingOrError(state.loading, state.error, retry) }
            else {
                item(key = "play") { NdPlayButtons(songs) }
                itemsIndexed(songs, key = { index, song -> "${song.id}:$index" }) { index, _ -> NdSongRow(songs, index, showCover = false) }
            }
        }
    }
}

@Composable
private fun NdArtistPage(artist: NdArtist, bottomPadding: Dp, onBack: () -> Unit) {
    val nd = LocalNd.current ?: return
    val (state, retry) = rememberLoad(artist.id) { nd.client.artist(artist.id) }
    Column(Modifier.fillMaxSize()) {
        NdPageTop(artist.name, state.value?.second?.size?.let { "$it 张专辑" }, onBack)
        val albums = state.value?.second
        LazyVerticalGrid(
            GridCells.Adaptive(150.dp),
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = bottomPadding + 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (albums == null) item(key = "state", span = { GridItemSpan(maxLineSpan) }) { LoadingOrError(state.loading, state.error, retry) }
            else items(albums, key = { it.id }) { NdAlbumCard(it) }
        }
    }
}

@Composable
private fun NdPlaylistPage(playlist: NdPlaylist, bottomPadding: Dp, onBack: () -> Unit) {
    val nd = LocalNd.current ?: return
    Column(Modifier.fillMaxSize()) {
        NdPageTop(playlist.name, "${playlist.songCount} 首", onBack)
        NdSongsList(bottomPadding, key = "pl:" + playlist.id) { nd.client.playlist(playlist.id) }
    }
}
