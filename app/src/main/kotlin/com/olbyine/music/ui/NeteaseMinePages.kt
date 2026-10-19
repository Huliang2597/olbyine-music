@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.olbyine.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.olbyine.music.data.HapticArea
import com.olbyine.music.data.NeteaseClient
import com.olbyine.music.data.NeteasePlaylistSummary
import com.olbyine.music.data.NeteaseSong
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.grid.items as gridItems

@Composable
private fun ExtrasHeader(title: String, subtitle: String?, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp)) {
        Spacer(Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OlbyineIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回", onBack, size = 40.dp, iconSize = 20.dp)
            Spacer(Modifier.weight(1f))
            trailing()
        }
        Spacer(Modifier.size(10.dp))
        Text(title, Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(12.dp))
    }
}

@Composable
internal fun NeteaseRankingScreen(
    client: NeteaseClient,
    userId: Long,
    currentTrackId: String?,
    onBack: () -> Unit,
    onPlay: (List<NeteaseSong>, Int) -> Unit,
) {
    var allTime by rememberSaveable { mutableStateOf(false) }
    val (state, retry) = rememberLoad(userId, allTime) { client.playRanking(allTime, userId) }
    val rows = state.value.orEmpty()
    val songs = remember(rows) { rows.map { it.first } }
    val top = rows.firstOrNull()?.second?.coerceAtLeast(1) ?: 1
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "head") {
            Column {
                ExtrasHeader("听歌排行", "按播放次数排列", onBack) {
                    if (songs.isNotEmpty()) PillButton("播放", { onPlay(songs, 0) }, icon = Icons.Rounded.PlayArrow, height = 36.dp)
                }
                PillTabs(listOf(false to "最近一周", true to "所有时间"), allTime, { allTime = it }, Modifier.padding(bottom = 8.dp))
            }
        }
        if (rows.isEmpty()) {
            if (state.loading) skeletonRows(10)
            else item(key = "state") { LoadingOrError(false, state.error ?: "还没有听歌记录", retry, itemMotion()) }
        }
        itemsIndexed(rows, key = { index, it -> "$index:${it.first.id}" }) { index, (song, count) ->
            Row(itemMotion().fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (index < 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(Modifier.padding(top = 3.dp).size(width = 22.dp, height = 3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .1f))) {
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth((count.toFloat() / top).coerceIn(.08f, 1f)).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = .7f)),
                        )
                    }
                }
                Box(Modifier.weight(1f)) {
                    NeteaseSongRow(song, current = currentTrackId == NeteaseSong.trackId(song.id)) { onPlay(songs, index) }
                }
                if (count > 0) {
                    Text("$count 次", Modifier.padding(start = 4.dp, end = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item(key = "bottom") { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
internal fun NeteaseSavedAlbumsScreen(client: NeteaseClient, onBack: () -> Unit, onOpen: (NeteasePlaylistSummary) -> Unit) {
    val (state, retry) = rememberLoad(Unit) {
        val all = ArrayList<NeteasePlaylistSummary>()
        var offset = 0
        while (true) {
            val (rows, more) = client.savedAlbums(offset)
            all += rows
            offset += rows.size
            if (!more || rows.isEmpty() || all.size >= 1000) break
        }
        all
    }
    val removed = remember { mutableStateListOf<Long>() }
    val albums = state.value.orEmpty().filter { it.id !in removed }
    val scope = rememberCoroutineScope()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "head") { ExtrasHeader("收藏的专辑", state.value?.let { "${albums.size} 张 · 长按可取消收藏" }, onBack) }
        if (albums.isEmpty()) {
            if (state.loading) skeletonCards(6)
            else item(span = { GridItemSpan(maxLineSpan) }, key = "state") { LoadingOrError(false, state.error ?: "还没有收藏专辑", retry) }
        }
        gridItems(albums, key = { it.id }) { album ->
            var menu by remember { mutableStateOf(false) }
            Column(
                itemMotion().fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .combinedClickable(onClick = { onOpen(album) }, onLongClick = { menu = true })
                    .padding(6.dp),
            ) {
                Box {
                    UrlImage(
                        album.coverUrl?.let { "$it?param=400y400" },
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                    OlbyineDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        GlassMenuItem("取消收藏", {
                            menu = false
                            removed += album.id
                            scope.launch { if (runCatching { client.setAlbumSaved(album.id, false) }.isFailure) removed -= album.id }
                        }, icon = Icons.Rounded.DeleteOutline)
                    }
                }
                Text(album.name, Modifier.padding(start = 2.dp, top = 8.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(album.creator, "${album.trackCount} 首").filter(String::isNotBlank).joinToString(" · "),
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
private fun PodcastRow(podcast: NeteaseClient.Podcast, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrlImage(podcast.coverUrl, Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(podcast.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(podcast.host.takeIf(String::isNotBlank), podcast.programCount.takeIf { it > 0 }?.let { "$it 期" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (podcast.note.isNotBlank()) {
                Text(podcast.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun NeteasePodcastsScreen(client: NeteaseClient, onBack: () -> Unit, onOpen: (NeteaseClient.Podcast) -> Unit) {
    val (mine, _) = rememberLoad(client.signedIn) { client.subscribedPodcasts() }
    val (hot, retry) = rememberLoad(Unit) { client.hotPodcasts() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 32.dp)) {
        item(key = "head") { ExtrasHeader("播客", "订阅的电台和正在流行的节目", onBack) }
        val subscribed = mine.value.orEmpty()
        if (subscribed.isNotEmpty()) {
            item(key = "mine-title") { Overline("我订阅的", itemMotion().padding(start = 16.dp, top = 4.dp, bottom = 4.dp)) }
            items(subscribed, key = { "mine:${it.id}" }) { podcast -> PodcastRow(podcast, { onOpen(podcast) }, itemMotion()) }
        }
        item(key = "hot-title") { Overline("热门播客", itemMotion().padding(start = 16.dp, top = 14.dp, bottom = 4.dp)) }
        val popular = hot.value.orEmpty()
        if (popular.isEmpty()) {
            if (hot.loading) skeletonRows(8)
            else item(key = "state") { LoadingOrError(false, hot.error, retry, itemMotion()) }
        }
        items(popular, key = { "hot:${it.id}" }) { podcast -> PodcastRow(podcast, { onOpen(podcast) }, itemMotion()) }
        item(key = "bottom") { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
internal fun NeteasePodcastScreen(
    client: NeteaseClient,
    podcast: NeteaseClient.Podcast,
    currentTrackId: String?,
    onBack: () -> Unit,
    onPlay: (List<NeteaseSong>, Int) -> Unit,
) {
    val (state, retry) = rememberLoad(podcast.id) { client.podcastEpisodes(podcast.id) }
    val episodes = state.value.orEmpty()
    val playable = remember(episodes) { episodes.mapNotNull { episode -> episode.song?.copy(title = episode.name) } }
    var subscribed by remember(podcast.id) { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    val dates = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = 32.dp)) {
        item(key = "head") {
            Column {
                ExtrasHeader(
                    podcast.name,
                    listOfNotNull(podcast.host.takeIf(String::isNotBlank), podcast.programCount.takeIf { it > 0 }?.let { "$it 期" }).joinToString(" · "),
                    onBack,
                ) {
                    if (client.signedIn) {
                        val on = subscribed == true
                        PillButton(if (on) "已订阅" else "订阅", {
                            val next = !on
                            subscribed = next
                            scope.launch { if (runCatching { client.setPodcastSubscribed(podcast.id, next) }.isFailure) subscribed = !next }
                        }, style = if (on) PillStyle.TONAL else PillStyle.FILLED, height = 36.dp)
                    }
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    PillButton("从最新一期播放", { if (playable.isNotEmpty()) onPlay(playable, 0) }, icon = Icons.Rounded.PlayArrow, enabled = playable.isNotEmpty(), height = 40.dp)
                }
                Spacer(Modifier.size(8.dp))
            }
        }
        if (episodes.isEmpty()) {
            if (state.loading) skeletonRows(8)
            else item(key = "state") { LoadingOrError(false, state.error ?: "这个播客还没有节目", retry, itemMotion()) }
        }
        itemsIndexed(episodes, key = { _, it -> it.id }) { _, episode ->
            val song = episode.song
            val current = song != null && currentTrackId == NeteaseSong.trackId(song.id)
            Row(
                itemMotion().fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (current) MaterialTheme.colorScheme.primary.copy(alpha = .08f) else Color.Transparent)
                    .clickable(enabled = song != null) {
                        val at = playable.indexOfFirst { it.id == song?.id }
                        if (at >= 0) onPlay(playable, at)
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UrlImage(episode.coverUrl ?: podcast.coverUrl, Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        episode.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            episode.time.takeIf { it > 0 }?.let { dates.format(java.util.Date(it)) },
                            episode.durationMs.takeIf { it > 0 }?.let { formatDuration(it) },
                            episode.listeners.takeIf { it > 0 }?.let { playCountLabel(it) + " 人收听" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        item(key = "bottom") { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun NeteaseMvScreen(client: NeteaseClient, mvId: Long, active: Boolean, onBack: () -> Unit, onStart: () -> Unit) {
    val context = LocalContext.current
    var resolution by rememberSaveable(mvId) { mutableIntStateOf(1080) }
    val (detail, _) = rememberLoad(mvId) { client.mvDetail(mvId) }
    val (address, retry) = rememberLoad(mvId, resolution) { client.mvUrl(mvId, resolution) }
    val player = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
        }
    }
    var playing by remember { mutableStateOf(false) }
    var aspect by remember { mutableFloatStateOf(16f / 9f) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    var failure by remember(address.value) { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == androidx.media3.common.Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                failure = "视频加载失败（${error.errorCodeName}）"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(address.value) {
        val url = address.value ?: return@LaunchedEffect
        val resume = player.currentPosition
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(url), resume.coerceAtLeast(0L))
        player.prepare()
        player.play()
        onStart()
    }
    LaunchedEffect(active) { if (!active) player.pause() }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            delay(200)
        }
    }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OlbyineIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回", onBack, size = 40.dp, iconSize = 20.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(detail.value?.first ?: "MV", style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                detail.value?.second?.takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .7f), maxLines = 1)
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(aspect.coerceIn(.4f, 3f))
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        if (player.isPlaying) player.pause() else player.play()
                    },
            ) {
                AndroidView(
                    factory = { ctx -> android.view.TextureView(ctx).also { player.setVideoTextureView(it) } },
                    modifier = Modifier.fillMaxSize(),
                )
                if (address.value == null || failure != null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CompositionLocalProvider(LocalContentColor provides Color.White) {
                            LoadingOrError(address.loading && failure == null, failure ?: address.error, onRetry = {
                                failure = null
                                retry()
                            })
                        }
                    }
                } else if (buffering && player.playWhenReady) {
                    Box(Modifier.align(Alignment.Center)) { OlbyineSpinner(color = Color.White) }
                } else if (!playing) {
                    Box(
                        Modifier.align(Alignment.Center).size(64.dp).clip(CircleShape).background(Color.Black.copy(alpha = .45f)),
                        contentAlignment = Alignment.Center,
                    ) { StyledIcon(Icons.Rounded.PlayArrow, "播放", Modifier.size(36.dp), tint = Color.White) }
                }
            }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            OlbyineSlider(
                value = scrubbing ?: if (duration > 0) position.toFloat() / duration else 0f,
                onValueChange = { scrubbing = it },
                onValueChangeFinished = {
                    scrubbing?.let { player.seekTo((it * duration).toLong()) }
                    scrubbing = null
                },
                modifier = Modifier.fillMaxWidth(),
                quiet = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatDuration(position)} / ${formatDuration(duration)}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = Color.White.copy(alpha = .75f),
                )
                listOf(1080, 720, 480).forEach { r ->
                    val selected = r == resolution
                    Text(
                        "${r}P",
                        Modifier.padding(start = 6.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = if (selected) .22f else .08f))
                            .clickable { if (!selected) resolution = r }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = if (selected) 1f else .7f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun NeteasePlaylistManager(client: NeteaseClient, onBack: () -> Unit, onOpen: (NeteasePlaylistSummary) -> Unit) {
    val (state, retry) = rememberLoad(Unit) { client.playlists() }
    val own = remember { mutableStateListOf<NeteasePlaylistSummary>() }
    val saved = remember { mutableStateListOf<NeteasePlaylistSummary>() }
    LaunchedEffect(state.value) {
        val lists = state.value ?: return@LaunchedEffect
        own.clear(); own += lists.filter { it.own && !it.liked }
        saved.clear(); saved += lists.filter { !it.own && !it.liked }
    }
    var reordered by remember { mutableStateOf(false) }
    var tagging by remember { mutableStateOf<NeteasePlaylistSummary?>(null) }
    var deleting by remember { mutableStateOf<NeteasePlaylistSummary?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = 32.dp)) {
        item(key = "head") {
            ExtrasHeader("歌单管理", notice ?: "调整顺序、设置分类、删除或取消收藏", onBack) {
                if (reordered) {
                    PillButton("保存顺序", {
                        reordered = false
                        scope.launch {
                            notice = if (runCatching { client.reorderPlaylists(own.map { it.id }) }.isSuccess) "顺序已保存" else "保存顺序失败"
                        }
                    }, height = 36.dp)
                }
            }
        }
        if (state.value == null) {
            if (state.loading) skeletonRows(8)
            else item(key = "state") { LoadingOrError(false, state.error, retry, itemMotion()) }
        }
        if (own.isNotEmpty()) item(key = "own-title") { Overline("创建的歌单", itemMotion().padding(start = 16.dp, top = 4.dp, bottom = 4.dp)) }
        itemsIndexed(own, key = { _, it -> "own:${it.id}" }) { index, playlist ->
            ManagedRow(playlist, onClick = { onOpen(playlist) }) {
                GhostIconButton(Icons.Rounded.KeyboardArrowUp, "上移", {
                    if (index > 0) {
                        haptics.tick(HapticArea.MENUS)
                        own.add(index - 1, own.removeAt(index))
                        reordered = true
                    }
                }, size = 36.dp, iconSize = 20.dp, enabled = index > 0)
                GhostIconButton(Icons.Rounded.KeyboardArrowDown, "下移", {
                    if (index < own.lastIndex) {
                        haptics.tick(HapticArea.MENUS)
                        own.add(index + 1, own.removeAt(index))
                        reordered = true
                    }
                }, size = 36.dp, iconSize = 20.dp, enabled = index < own.lastIndex)
                GhostIconButton(Icons.Rounded.Sell, "分类", { tagging = playlist }, size = 36.dp, iconSize = 19.dp)
                GhostIconButton(Icons.Rounded.DeleteOutline, "删除", { deleting = playlist }, size = 36.dp, iconSize = 19.dp)
            }
        }
        if (saved.isNotEmpty()) item(key = "saved-title") { Overline("收藏的歌单", itemMotion().padding(start = 16.dp, top = 14.dp, bottom = 4.dp)) }
        itemsIndexed(saved, key = { _, it -> "saved:${it.id}" }) { _, playlist ->
            ManagedRow(playlist, onClick = { onOpen(playlist) }) {
                GhostIconButton(Icons.Rounded.BookmarkRemove, "取消收藏", {
                    saved.remove(playlist)
                    scope.launch {
                        if (runCatching { client.setPlaylistSaved(playlist.id, false) }.isFailure) {
                            saved += playlist
                            notice = "取消收藏失败"
                        }
                    }
                }, size = 36.dp, iconSize = 19.dp)
            }
        }
        item(key = "bottom") { Spacer(Modifier.navigationBarsPadding()) }
    }
    tagging?.let { playlist ->
        PlaylistTagsDialog(client, playlist, onDismiss = { tagging = null }) { tags ->
            tagging = null
            scope.launch {
                notice = if (runCatching { client.updatePlaylistTags(playlist.id, tags) }.isSuccess) "「${playlist.name}」的分类已更新" else "修改分类失败"
            }
        }
    }
    deleting?.let { playlist ->
        OlbyineDialog(
            onDismissRequest = { deleting = null },
            title = "删除歌单？",
            confirmButton = {
                GlassTextButton(onClick = {
                    deleting = null
                    own.remove(playlist)
                    scope.launch {
                        notice = if (runCatching { client.deletePlaylist(playlist.id) }.isFailure) {
                            own += playlist
                            "删除失败"
                        } else "已删除「${playlist.name}」"
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { GlassTextButton(onClick = { deleting = null }) { Text("取消") } },
        ) { Text("「${playlist.name}」会从你的网易云账号里删除，无法恢复。") }
    }
}

@Composable
private fun ManagedRow(playlist: NeteasePlaylistSummary, onClick: () -> Unit, actions: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrlImage(playlist.coverUrl?.let { "$it?param=120y120" }, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull("${playlist.trackCount} 首", playlist.creator.takeIf { !playlist.own && it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        actions()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaylistTagsDialog(client: NeteaseClient, playlist: NeteasePlaylistSummary, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    val (groups, retry) = rememberLoad(Unit) { client.playlistCategories() }
    val chosen = remember { mutableStateListOf<String>() }
    OlbyineDialog(
        onDismissRequest = onDismiss,
        title = "「${playlist.name}」的分类",
        confirmButton = { GlassTextButton(onClick = { onSave(chosen.toList()) }) { Text("保存") } },
        dismissButton = { GlassTextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text("最多选择 3 个（已选 ${chosen.size}）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val list = groups.value
            if (list == null) LoadingOrError(groups.loading, groups.error, retry)
            list.orEmpty().forEach { (group, names) ->
                Text(group, Modifier.padding(top = 12.dp, bottom = 6.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    names.forEach { name ->
                        val on = name in chosen
                        Text(
                            name,
                            Modifier.clip(CircleShape)
                                .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .07f))
                                .clickable { if (on) chosen.remove(name) else if (chosen.size < 3) chosen += name }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun NeteaseMineShortcuts(userId: Long, modifier: Modifier = Modifier) {
    val push = LocalNcmPush.current ?: return
    val haptics = rememberHaptics()
    Row(modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple("听歌排行", Icons.Rounded.Leaderboard, NcmRoute.Ranking(userId)),
            Triple("收藏专辑", Icons.Rounded.Album, NcmRoute.SavedAlbums),
            Triple("播客", Icons.Rounded.Podcasts, NcmRoute.Podcasts),
            Triple("歌单管理", Icons.Rounded.Tune, NcmRoute.ManagePlaylists),
        ).forEach { (label, icon, route) ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .05f))
                    .pressable(label = label) {
                        haptics.light(HapticArea.MENUS)
                        push(route)
                    }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(5.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}
