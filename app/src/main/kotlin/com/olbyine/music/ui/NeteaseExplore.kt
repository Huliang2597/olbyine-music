@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.olbyine.music.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olbyine.music.data.HapticArea
import com.olbyine.music.data.NeteaseArtist
import com.olbyine.music.data.NeteaseClient
import com.olbyine.music.data.NeteaseCollectionKind
import com.olbyine.music.data.NeteaseComment
import com.olbyine.music.data.NeteaseMessage
import com.olbyine.music.data.NeteasePlaylistSummary
import com.olbyine.music.data.NeteaseSong
import com.olbyine.music.data.NeteaseThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val LocalNeteaseComments = staticCompositionLocalOf<((Long, String) -> Unit)?> { null }

internal enum class NeteaseTab(val label: String) {
    MINE("我的"), DISCOVER("发现"), CHARTS("排行榜"), SEARCH("搜索"), MESSAGES("消息"),
}

private val NeteaseRed = Color(0xFFE5303C)

internal fun playCountLabel(count: Long): String = when {
    count >= 100_000_000 -> String.format(Locale.CHINA, "%.1f亿", count / 100_000_000.0)
    count >= 10_000 -> String.format(Locale.CHINA, "%.1f万", count / 10_000.0)
    else -> count.toString()
}

internal class Loaded<T>(val value: T? = null, val loading: Boolean = false, val error: String? = null)

@Composable
internal fun <T> rememberLoad(vararg keys: Any?, load: suspend () -> T): Pair<Loaded<T>, () -> Unit> {
    var state by remember(*keys) { mutableStateOf(Loaded<T>(loading = true)) }
    var attempt by remember(*keys) { mutableStateOf(0) }
    LaunchedEffect(*keys, attempt) {
        state = Loaded(state.value, loading = true)
        state = try {
            Loaded(load())
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Loaded(state.value, error = error.message ?: "加载失败，请检查网络")
        }
    }
    return state to { attempt++ }
}

@Composable
internal fun <T> rememberLoadStream(vararg keys: Any?, load: () -> kotlinx.coroutines.flow.Flow<T>): Pair<Loaded<T>, () -> Unit> {
    var state by remember(*keys) { mutableStateOf(Loaded<T>(loading = true)) }
    var attempt by remember(*keys) { mutableStateOf(0) }
    LaunchedEffect(*keys, attempt) {
        state = Loaded(state.value, loading = true)
        try {
            load().collect { value -> state = Loaded(value) }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            state = Loaded(state.value, error = error.message ?: "加载失败，请检查网络")
        }
    }
    return state to { attempt++ }
}

@Composable
internal fun LoadingOrError(loading: Boolean, error: String?, onRetry: () -> Unit, modifier: Modifier = Modifier, accent: Color = Color.Unspecified) {
    val reduce = LocalReduceMotion.current
    Box(modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
        androidx.compose.animation.AnimatedContent(
            targetState = when {
                loading -> LoadPhase.Loading
                error != null -> LoadPhase.Error(error)
                else -> LoadPhase.Idle
            },
            contentKey = { it::class },
            transitionSpec = {
                val enter = androidx.compose.animation.fadeIn(tween(220, easing = OlbyineTokens.Motion.easeOut)) +
                    if (reduce) androidx.compose.animation.EnterTransition.None
                    else androidx.compose.animation.slideInVertically(tween(260, easing = OlbyineTokens.Motion.easeOut)) { it / 6 }
                enter togetherWith androidx.compose.animation.fadeOut(tween(100))
            },
            contentAlignment = Alignment.Center,
            label = "load-phase",
        ) { phase ->
            when (phase) {
                LoadPhase.Loading -> DelayedSpinner(accent.takeOrElse { MaterialTheme.colorScheme.primary })
                is LoadPhase.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(phase.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    PillButton("重试", onRetry, style = PillStyle.TONAL)
                }
                LoadPhase.Idle -> Spacer(Modifier.size(1.dp))
            }
        }
    }
}

@Composable
private fun DelayedSpinner(color: Color) {
    val shown = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(180)
        shown.animateTo(1f, tween(220, easing = OlbyineTokens.Motion.easeOut))
    }
    OlbyineSpinner(
        Modifier.size(28.dp).graphicsLayer { alpha = shown.value },
        strokeWidth = 2.5.dp,
        color = color,
    )
}

private sealed interface LoadPhase {
    data object Loading : LoadPhase
    data class Error(val message: String) : LoadPhase
    data object Idle : LoadPhase
}

@Composable
internal fun NeteaseCoverCard(playlist: NeteasePlaylistSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            UrlImage(
                playlist.coverUrl?.let { "$it?param=400y400" },
                Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            if (playlist.playCount > 0) {
                Text(
                    "▶ " + playCountLabel(playlist.playCount),
                    Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = .38f)).padding(horizontal = 7.dp, vertical = 2.dp),
                    fontSize = 10.sp,
                    color = Color.White,
                )
            }
        }
        Text(playlist.name, Modifier.padding(start = 2.dp, top = 7.dp), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val sub = listOf(playlist.note, playlist.creator).firstOrNull { it.isNotBlank() }
        if (sub != null) {
            Text(sub, Modifier.padding(start = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun NeteaseDiscover(client: NeteaseClient, signedIn: Boolean, bottomPadding: Dp, onOpen: (NeteasePlaylistSummary) -> Unit) {
    val (recommended, retryRecommended) = rememberLoad(signedIn) { if (signedIn) client.recommendedPlaylists() else emptyList() }
    val (categories, _) = rememberLoad(Unit) { client.playlistCategories() }
    var category by rememberSaveable { mutableStateOf("全部") }
    var showAllCategories by remember { mutableStateOf(false) }
    val square = remember(category) { mutableStateListOf<NeteasePlaylistSummary>() }
    var squareLoading by remember(category) { mutableStateOf(false) }
    var squareError by remember(category) { mutableStateOf<String?>(null) }
    var squareEnd by remember(category) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val loadMore: () -> Unit = {
        if (!squareLoading && !squareEnd) {
            squareLoading = true
            squareError = null
            scope.launch {
                try {
                    val page = client.categoryPlaylists(category, square.size)
                    if (page.isEmpty()) squareEnd = true
                    square += page.filter { new -> square.none { it.id == new.id } }
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    squareError = error.message ?: "加载失败"
                }
                squareLoading = false
            }
        }
    }
    LaunchedEffect(category) { loadMore() }
    val hotCategories = remember(categories.value) {
        listOf("全部") + categories.value.orEmpty().flatMap { it.second }.take(14)
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (signedIn) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "daily") {
                DailyCard {
                    onOpen(NeteasePlaylistSummary(-1L, "每日推荐", null, 30, "根据你的口味生成", false, false, kind = NeteaseCollectionKind.DAILY))
                }
            }
            val picks = recommended.value.orEmpty()
            if (picks.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "rec-title") { Overline("为你推荐", Modifier.padding(start = 6.dp, top = 12.dp, bottom = 2.dp)) }
                item(span = { GridItemSpan(maxLineSpan) }, key = "rec") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        itemsIndexed(picks, key = { index, it -> "$index:${it.id}" }) { _, it -> NeteaseCoverCard(it, { onOpen(it) }, itemMotion().width(138.dp)) }
                    }
                }
            } else if (recommended.error != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "rec-error") { LoadingOrError(false, recommended.error, retryRecommended) }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "square-title") {
            Row(Modifier.fillMaxWidth().padding(start = 6.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Overline("歌单广场", Modifier.weight(1f))
                if (categories.value != null) {
                    Text(
                        "全部分类",
                        Modifier.clip(CircleShape).clickable { showAllCategories = true }.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "square-tabs") {
            PillTabs(
                items = (if (category in hotCategories) hotCategories else hotCategories + category).map { it to it },
                selected = category,
                onSelect = { category = it },
                contentPadding = PaddingValues(horizontal = 4.dp),
            )
        }
        itemsIndexed(square, key = { index, it -> "sq:$index:${it.id}" }) { index, playlist ->
            if (index >= square.size - 6) LaunchedEffect(square.size) { loadMore() }
            NeteaseCoverCard(playlist, { onOpen(playlist) }, itemMotion())
        }
        if (square.isEmpty() && squareLoading) {
            skeletonCards(6)
        } else if (squareLoading || squareError != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "square-state") { LoadingOrError(squareLoading, squareError, loadMore) }
        }
    }
    if (showAllCategories) {
        GlassBottomSheet(onDismissRequest = { showAllCategories = false }) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(horizontal = 20.dp)) {
                item { Text("歌单分类", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
                categories.value.orEmpty().forEach { (group, names) ->
                    item(key = group) {
                        Overline(group, Modifier.padding(top = 16.dp, bottom = 8.dp))
                        FlowChips(names, category) { category = it; showAllCategories = false }
                    }
                }
                item { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
            }
        }
    }
}

@Composable
private fun FlowChips(names: List<String>, selected: String, onSelect: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        names.forEach { name ->
            val on = name == selected
            Text(
                name,
                Modifier.clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .07f))
                    .clickable { onSelect(name) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun DailyCard(onClick: () -> Unit) {
    val day = remember { SimpleDateFormat("dd", Locale.CHINA).format(Date()) }
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(NeteaseRed, Color(0xFFB0213A))))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
            Text(day, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("每日推荐", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("根据你的口味，每天 6:00 更新", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .85f))
        }
    }
}

@Composable
internal fun NeteaseCharts(client: NeteaseClient, bottomPadding: Dp, onOpen: (NeteasePlaylistSummary) -> Unit) {
    val (charts, retry) = rememberLoad(Unit) { client.charts() }
    val list = charts.value.orEmpty()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (list.isEmpty()) {
            if (charts.loading) skeletonCards(8)
            else item(span = { GridItemSpan(maxLineSpan) }, key = "state") { LoadingOrError(charts.loading, charts.error, retry) }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }, key = "official") { Overline("官方榜", itemMotion().padding(start = 6.dp, top = 6.dp)) }
            itemsIndexed(list.take(4), key = { _, it -> "o${it.id}" }) { _, chart -> NeteaseCoverCard(chart, { onOpen(chart) }, itemMotion()) }
            item(span = { GridItemSpan(maxLineSpan) }, key = "more") { Overline("精选榜与全球榜", itemMotion().padding(start = 6.dp, top = 14.dp)) }
            itemsIndexed(list.drop(4), key = { _, it -> "m${it.id}" }) { _, chart -> NeteaseCoverCard(chart, { onOpen(chart) }, itemMotion()) }
        }
    }
}

private enum class SearchType(val label: String, val code: Int) { SONG("单曲", 1), PLAYLIST("歌单", 1000), ALBUM("专辑", 10), ARTIST("歌手", 100) }

@Composable
internal fun NeteaseSearch(
    client: NeteaseClient,
    bottomPadding: Dp,
    currentTrackId: String?,
    onOpen: (NeteasePlaylistSummary) -> Unit,
    onPlay: (List<NeteaseSong>, Int) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var keyword by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(SearchType.SONG) }
    val (hot, _) = rememberLoad(Unit) { client.hotSearches() }
    val songs = remember(keyword, type) { mutableStateListOf<NeteaseSong>() }
    val collections = remember(keyword, type) { mutableStateListOf<NeteasePlaylistSummary>() }
    val artists = remember(keyword, type) { mutableStateListOf<NeteaseArtist>() }
    var loading by remember(keyword, type) { mutableStateOf(false) }
    var error by remember(keyword, type) { mutableStateOf<String?>(null) }
    var total by remember(keyword, type) { mutableStateOf(-1) }
    val scope = rememberCoroutineScope()
    val count = songs.size + collections.size + artists.size
    val loadMore: () -> Unit = {
        if (keyword.isNotBlank() && !loading && (total < 0 || count < total)) {
            loading = true
            error = null
            scope.launch {
                try {
                    val page = client.search(keyword, type.code, count)
                    total = if (page.total > 0) page.total else count + page.songs.size + page.playlists.size + page.albums.size + page.artists.size
                    songs += page.songs
                    collections += page.playlists + page.albums
                    artists += page.artists
                    if (page.songs.isEmpty() && page.playlists.isEmpty() && page.albums.isEmpty() && page.artists.isEmpty()) total = count
                } catch (failure: Throwable) {
                    if (failure is CancellationException) throw failure
                    error = failure.message ?: "搜索失败"
                }
                loading = false
            }
        }
    }
    LaunchedEffect(keyword, type) { loadMore() }
    val submit: (String) -> Unit = { query -> input = query; keyword = query.trim() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                OlbyineSearchField(input, { input = it }, "搜索歌曲、歌单、专辑、歌手")
            }
            Spacer(Modifier.width(8.dp))
            PillButton("搜索", { submit(input) }, enabled = input.isNotBlank(), height = 42.dp)
        }
        if (keyword.isBlank()) {
            val words = hot.value.orEmpty()
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StyledIcon(Icons.Rounded.Whatshot, null, Modifier.size(18.dp), tint = NeteaseRed)
                    Spacer(Modifier.width(6.dp))
                    Text("热搜", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(10.dp))
                FlowChips(words, "", submit)
            }
            return@Column
        }
        PillTabs(SearchType.entries.map { it to it.label }, type, { type = it }, Modifier.padding(vertical = 10.dp))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = bottomPadding + 24.dp),
        ) {
            when (type) {
                SearchType.SONG -> itemsIndexed(songs, key = { index, it -> "s$index:${it.id}" }) { index, song ->
                    if (index >= songs.size - 6) LaunchedEffect(songs.size) { loadMore() }
                    MotionItem { NeteaseSongRow(song, currentTrackId == NeteaseSong.trackId(song.id)) { onPlay(songs.toList(), index) } }
                }
                SearchType.ARTIST -> itemsIndexed(artists, key = { index, it -> "a$index:${it.id}" }) { _, artist ->
                    Row(
                        itemMotion().fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                            onOpen(NeteasePlaylistSummary(artist.id, artist.name + " · 热门歌曲", artist.pictureUrl, 50, artist.name, false, false, kind = NeteaseCollectionKind.ARTIST))
                        }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UrlImage(artist.pictureUrl, Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(artist.name, style = MaterialTheme.typography.titleSmall)
                            Text("${artist.albumCount} 张专辑", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                else -> itemsIndexed(collections, key = { index, it -> "c$index:${it.id}" }) { index, collection ->
                    if (index >= collections.size - 6) LaunchedEffect(collections.size) { loadMore() }
                    Row(
                        itemMotion().fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onOpen(collection) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UrlImage(collection.coverUrl?.let { "$it?param=200y200" }, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(collection.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull("${collection.trackCount} 首", collection.creator.takeIf { it.isNotBlank() }, collection.note.takeIf { it.isNotBlank() },
                                    collection.playCount.takeIf { it > 0 }?.let { "播放 " + playCountLabel(it) }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (count == 0 && loading) {
                skeletonRows(8, avatar = type == SearchType.ARTIST)
            } else if (loading || error != null || (total == 0)) {
                item(key = "state") {
                    if (total == 0 && !loading && error == null) {
                        Text("没有找到「$keyword」", itemMotion().fillMaxWidth().padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else LoadingOrError(loading, error, loadMore, itemMotion())
                }
            }
        }
    }
}

@Composable
internal fun NeteaseSongRow(song: NeteaseSong, current: Boolean, onClick: () -> Unit) {
    val comments = LocalNeteaseComments.current
    val highlight by androidx.compose.animation.animateColorAsState(
        if (current) MaterialTheme.colorScheme.primary.copy(alpha = .08f) else Color.Transparent,
        tween(OlbyineTokens.Motion.standardMs, easing = OlbyineTokens.Motion.easeOut),
        label = "row-current",
    )
    val titleInk by androidx.compose.animation.animateColorAsState(
        if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        tween(OlbyineTokens.Motion.standardMs, easing = OlbyineTokens.Motion.easeOut),
        label = "row-title",
    )
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(highlight)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrlImage(song.coverUrl?.replace("600y600", "160y160"), Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleSmall, color = titleInk,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.unavailable) {
                    Badge("不可播放", filled = true)
                    Spacer(Modifier.width(5.dp))
                }
                val push = LocalNcmPush.current
                if (song.mvId > 0 && push != null) {
                    Box(Modifier.clip(RoundedCornerShape(5.dp)).clickable { push(NcmRoute.Mv(song.mvId)) }) {
                        Badge("MV", color = NeteaseRed)
                    }
                    Spacer(Modifier.width(5.dp))
                }
                Text(listOf(song.artist, song.album).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (song.unavailable) {
            LocalSearchSoundCloud.current?.let { search ->
                GhostIconButton(Icons.Rounded.TravelExplore, "在 SoundCloud 搜索音源", { search(song.title + " " + song.artists.firstOrNull().orEmpty()) },
                    size = 40.dp, iconSize = 19.dp, tint = MaterialTheme.colorScheme.primary)
            }
        }
        LocalPlayNextTrack.current?.let { next ->
            GhostIconButton(Icons.AutoMirrored.Rounded.PlaylistPlay, "下一首播放", { next(song.toTrack()) }, size = 40.dp, iconSize = 19.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (comments != null) {
            GhostIconButton(Icons.Rounded.ChatBubbleOutline, "评论", { comments(song.id, song.title) }, size = 40.dp, iconSize = 19.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val like = LocalNeteaseLike.current
        if (like != null) {
            val track = remember(song) { song.toTrack() }
            HeartWithMenu(
                track = { track },
                liked = like.isLiked(song.id),
                onToggle = { like.toggle(song.id) },
                iconSize = 20.dp,
                minSize = 40.dp,
            )
        }
    }
}

internal val LocalNeteaseProfile = staticCompositionLocalOf<((Long) -> Unit)?> { null }

/** 网易云曲目「喜欢」的状态与操作，由 OlbyineApp 统一提供。 */
internal class NeteaseLikeActions(
    val isLiked: (Long) -> Boolean,
    val toggle: (Long) -> Unit,
)

internal val LocalNeteaseLike = staticCompositionLocalOf<NeteaseLikeActions?> { null }

@Composable
internal fun NeteaseCommentsSheet(client: NeteaseClient, songId: Long, title: String, onDismiss: () -> Unit) {
    val hot = remember(songId) { mutableStateListOf<NeteaseComment>() }
    val latest = remember(songId) { mutableStateListOf<NeteaseComment>() }
    var total by remember(songId) { mutableStateOf(0) }
    var more by remember(songId) { mutableStateOf(true) }
    var loading by remember(songId) { mutableStateOf(false) }
    var error by remember(songId) { mutableStateOf<String?>(null) }
    var draft by remember(songId) { mutableStateOf("") }
    var replyTo by remember(songId) { mutableStateOf<NeteaseComment?>(null) }
    var thread by remember(songId) { mutableStateOf<NeteaseComment?>(null) }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val openProfile = LocalNeteaseProfile.current
    val onUser: (NeteaseComment) -> Unit = { comment -> if (comment.userId > 0 && openProfile != null) { onDismiss(); openProfile(comment.userId) } }
    val loadMore: () -> Unit = {
        if (!loading && more) {
            loading = true
            error = null
            scope.launch {
                try {
                    val page = client.comments(songId, offset = latest.size, before = latest.lastOrNull()?.time ?: 0)
                    if (hot.isEmpty()) hot += page.hot
                    latest += page.latest.filter { new -> latest.none { it.id == new.id } }
                    total = page.total
                    more = page.more && page.latest.isNotEmpty()
                } catch (failure: Throwable) {
                    if (failure is CancellationException) throw failure
                    error = failure.message ?: "评论加载失败"
                }
                loading = false
            }
        }
    }
    LaunchedEffect(songId) { loadMore() }
    val replies = remember(thread?.id) { mutableStateListOf<NeteaseComment>() }
    var replyCursor by remember(thread?.id) { mutableStateOf<Long?>(-1L) }
    var repliesLoading by remember(thread?.id) { mutableStateOf(false) }
    val loadReplies: () -> Unit = {
        val parent = thread
        val cursor = replyCursor
        if (parent != null && cursor != null && !repliesLoading) {
            repliesLoading = true
            scope.launch {
                runCatching { client.commentReplies(songId, parent.id, cursor) }
                    .onSuccess { (page, next) -> replies += page.filter { new -> replies.none { it.id == new.id } }; replyCursor = next }
                    .onFailure { error = it.message ?: "回复加载失败"; replyCursor = null }
                repliesLoading = false
            }
        }
    }
    LaunchedEffect(thread?.id) { if (thread != null) loadReplies() }
    GlassBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).imePadding()) {
            val parent = thread
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (parent != null) {
                    GhostIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回评论", { thread = null; replyTo = null }, size = 40.dp, iconSize = 20.dp)
                    Spacer(Modifier.width(4.dp))
                }
                Column(Modifier.padding(start = if (parent == null) 8.dp else 0.dp)) {
                    SwapContent(if (parent != null) "回复 · ${parent.replyCount}" else "评论" + if (total > 0) " · $total" else "") { heading ->
                        Text(heading, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                if (parent != null) {
                    item(key = "parent") { CommentRow(parent, onUser = onUser, onReply = { replyTo = it }, onReplies = null, modifier = itemMotion()) }
                    item(key = "parent-line") { Overline("全部回复", itemMotion().padding(start = 6.dp, top = 8.dp, bottom = 4.dp)) }
                    itemsIndexed(replies, key = { _, it -> "r${it.id}" }) { index, reply ->
                        if (index >= replies.size - 4) LaunchedEffect(replies.size) { loadReplies() }
                        CommentRow(reply, onUser = onUser, onReply = { replyTo = it }, onReplies = null, modifier = itemMotion())
                    }
                    if (repliesLoading && replies.isEmpty()) skeletonRows(4, avatar = true)
                    else if (repliesLoading) item(key = "replies-state") { LoadingOrError(true, null, {}, itemMotion()) }
                } else {
                    if (hot.isNotEmpty()) {
                        item(key = "hot") { Overline("精彩评论", itemMotion().padding(start = 6.dp, top = 8.dp, bottom = 4.dp)) }
                        itemsIndexed(hot, key = { index, it -> "h$index:${it.id}" }) { _, comment ->
                            CommentRow(comment, onUser = onUser, onReply = { c -> replyTo = c }, onReplies = { c -> thread = c }, modifier = itemMotion())
                        }
                    }
                    if (latest.isNotEmpty()) {
                        item(key = "latest") { Overline("最新评论", itemMotion().padding(start = 6.dp, top = 14.dp, bottom = 4.dp)) }
                        itemsIndexed(latest, key = { _, it -> "l${it.id}" }) { index, comment ->
                            if (index >= latest.size - 5) LaunchedEffect(latest.size) { loadMore() }
                            CommentRow(comment, onUser = onUser, onReply = { replyTo = it }, onReplies = { thread = it }, modifier = itemMotion())
                        }
                    }
                    if (loading && hot.isEmpty() && latest.isEmpty()) skeletonRows(6, avatar = true)
                    else if (loading || error != null) item(key = "state") { LoadingOrError(loading, error, loadMore, itemMotion()) }
                }
            }
            val target = replyTo
            val lastTarget = remember { arrayOfNulls<NeteaseComment>(1) }
            if (target != null) lastTarget[0] = target
            androidx.compose.animation.AnimatedVisibility(
                visible = target != null,
                enter = androidx.compose.animation.fadeIn(tween(160, easing = OlbyineTokens.Motion.easeOut)) +
                    androidx.compose.animation.expandVertically(tween(200, easing = OlbyineTokens.Motion.easeOut)),
                exit = androidx.compose.animation.fadeOut(tween(100)) + androidx.compose.animation.shrinkVertically(tween(160, easing = OlbyineTokens.Motion.easeOut)),
            ) {
                val shown = target ?: lastTarget[0]
                Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("回复 @${shown?.user.orEmpty()}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    Text("取消", Modifier.clip(CircleShape).clickable { replyTo = null }.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 6.dp).navigationBarsPadding().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    OlbyineTextField(draft, { draft = it }, when {
                        !client.signedIn -> "登录网易云后可以发表评论"
                        target != null -> "回复 ${target.user}…"
                        else -> "说点什么…"
                    })
                }
                Spacer(Modifier.width(8.dp))
                OlbyineIconButton(
                    Icons.AutoMirrored.Rounded.Send,
                    "发送",
                    {
                        val text = draft.trim()
                        if (text.isNotEmpty() && !sending) {
                            sending = true
                            scope.launch {
                                try {
                                    if (target != null) client.replyComment(songId, target.id, text) else client.postComment(songId, text)
                                    haptics.confirm(HapticArea.MENUS)
                                    val posted = System.currentTimeMillis()
                                    val me = client.account.value
                                    val mine = NeteaseComment(
                                        id = posted,
                                        user = me?.nickname?.takeIf(String::isNotBlank) ?: "我",
                                        userId = me?.userId ?: 0L,
                                        avatarUrl = me?.avatarUrl?.let { url -> if (url.contains("?")) url else "$url?param=100y100" },
                                        content = text,
                                        time = posted,
                                        likedCount = 0,
                                        replyTo = target?.let { "@${it.user}：${it.content}" },
                                    )
                                    if (thread != null) replies += mine else {
                                        latest.add(0, mine)
                                        total += 1
                                    }
                                    draft = ""
                                    replyTo = null
                                } catch (failure: Throwable) {
                                    if (failure is CancellationException) throw failure
                                    error = failure.message ?: "发送失败"
                                }
                                sending = false
                            }
                        }
                    },
                    size = 46.dp,
                    container = MaterialTheme.colorScheme.primary,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    enabled = client.signedIn && draft.isNotBlank() && !sending,
                )
            }
        }
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

@Composable
private fun CommentRow(
    comment: NeteaseComment,
    onUser: (NeteaseComment) -> Unit,
    onReply: (NeteaseComment) -> Unit,
    onReplies: ((NeteaseComment) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onReply(comment) }.padding(horizontal = 6.dp, vertical = 8.dp)) {
        UrlImage(
            comment.avatarUrl,
            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable { onUser(comment) },
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                comment.user,
                Modifier.clip(RoundedCornerShape(6.dp)).clickable { onUser(comment) },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(comment.content, style = MaterialTheme.typography.bodyMedium)
            if (comment.replyTo != null) {
                Text(
                    comment.replyTo,
                    Modifier.padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .05f)).padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dateFormat.format(Date(comment.time)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f))
                if (onReplies != null && comment.replyCount > 0) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "查看 ${comment.replyCount} 条回复 ›",
                        Modifier.clip(CircleShape).clickable { onReplies(comment) }.padding(horizontal = 6.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (comment.likedCount > 0) {
                    Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StyledIcon(Icons.Rounded.ThumbUp, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(3.dp))
                        Text(playCountLabel(comment.likedCount.toLong()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun NeteaseMessages(client: NeteaseClient, signedIn: Boolean, bottomPadding: Dp, onLogin: () -> Unit) {
    val openChat = LocalNeteaseChat.current
    if (!signedIn) {
        EmptyState(Icons.Rounded.Mail, "登录后查看私信", Modifier.fillMaxSize(), message = "私信需要登录网易云音乐") {
            PillButton("登录", onLogin)
        }
        return
    }

    val (threads, retry) = rememberLoad(Unit) { client.messageThreads() }
    var opened by remember { mutableStateOf(setOf<Long>()) }
    val list = threads.value.orEmpty().map { if (it.userId in opened) it.copy(unread = 0) else it }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = bottomPadding + 24.dp)) {
        if (list.isEmpty()) {
            if (threads.loading) skeletonRows(7, avatar = true)
            else item(key = "state") {
                if (threads.error != null) LoadingOrError(false, threads.error, retry, itemMotion())
                else Text("还没有私信", itemMotion().padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(list, key = { it.userId }) { item ->
            val openProfile = LocalNeteaseProfile.current
            val (presence, _) = rememberLoad(item.userId) { client.cachedUserStatus(item.userId) }
            Row(
                itemMotion().fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                    opened = opened + item.userId
                    client.markThreadRead(item.userId, item.time)
                    openChat?.invoke(item)
                }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    UrlImage(item.avatarUrl, Modifier.size(50.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { openProfile?.invoke(item.userId) })
                    if (presence.value != null) {
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background)
                                .padding(2.5.dp).clip(CircleShape).background(Color(0xFF34C759)),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.nickname,
                            Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (item.unread > 0) FontWeight.SemiBold else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        presence.value?.let { status ->
                            Text(
                                " · $status",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        item.lastMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.unread > 0) MaterialTheme.colorScheme.onSurface.copy(alpha = .82f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(dateFormat.format(Date(item.time)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.unread > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (item.unread > 99) "99+" else item.unread.toString(),
                            Modifier.clip(CircleShape).background(NeteaseRed.copy(alpha = .14f)).padding(horizontal = 7.dp, vertical = 1.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeteaseRed.copy(alpha = .9f),
                        )
                    }
                }
            }
        }
    }
}

internal val NeteaseLikedIcon = Icons.Rounded.Favorite
