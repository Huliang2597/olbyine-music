@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.olbyine.music.ui

import android.annotation.SuppressLint
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
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalConfiguration
import com.olbyine.music.data.HapticArea
import com.olbyine.music.data.ScComment
import com.olbyine.music.data.ScPlaylist
import com.olbyine.music.data.ScSelection
import com.olbyine.music.data.ScTrack
import com.olbyine.music.data.ScUser
import com.olbyine.music.data.SoundCloudClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val ScOrange = Color(0xFFFF5500)

internal val LocalScComments = staticCompositionLocalOf<((Long, String) -> Unit)?> { null }

internal val LocalStreamingSwitch = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

internal val LocalSearchSoundCloud = staticCompositionLocalOf<((String) -> Unit)?> { null }

private sealed interface ScRoute {
    data class Playlist(val playlist: ScPlaylist) : ScRoute
    data class User(val user: ScUser) : ScRoute
    data class Genre(val tag: String) : ScRoute
    data class Related(val track: ScTrack) : ScRoute
    data class Conversation(val id: String, val user: ScUser) : ScRoute
    data class People(val user: ScUser, val followers: Boolean) : ScRoute
    data class Web(val url: String, val title: String) : ScRoute
}

private enum class ScTab(val label: String) { DISCOVER("发现"), FEED("动态"), SEARCH("搜索"), INBOX("消息"), MINE("我的") }

private class ScContext(
    val client: SoundCloudClient,
    val currentTrackId: String?,
    val isPlaying: Boolean,
    val liked: Set<Long>,
    val onLike: ((ScTrack) -> Unit)?,
    val onPlay: (List<ScTrack>, Int, Boolean) -> Unit,
    val open: (ScRoute) -> Unit,
    val reposted: Set<Long> = emptySet(),
    val onRepost: ((ScTrack) -> Unit)? = null,
    val following: Set<Long> = emptySet(),
    val onFollow: ((ScUser) -> Unit)? = null,
    val setOrigin: (com.olbyine.music.PlayOrigin?) -> Unit = {},
)

private val LocalSc = staticCompositionLocalOf<ScContext?> { null }

@Composable
internal fun SoundCloudScreen(
    client: SoundCloudClient,
    bottomPadding: Dp,
    currentTrackId: String?,
    isPlaying: Boolean,
    searchRequest: String?,
    onSearchHandled: () -> Unit,
    onPlay: (List<ScTrack>, Int, Boolean, com.olbyine.music.PlayOrigin?) -> Unit,
    openUser: ScUser? = null,
    onOpenUserHandled: () -> Unit = {},
    revealUser: ScUser? = null,
    revealPlaylist: com.olbyine.music.data.ScPlaylist? = null,
    revealTrackId: Long? = null,
    onRevealHandled: () -> Unit = {},
) {
    val account by client.account.collectAsState()
    var tab by rememberSaveable { mutableStateOf(ScTab.DISCOVER) }
    val routes = rememberPageStack<ScRoute>()
    var searchSeed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(searchRequest) {
        val query = searchRequest ?: return@LaunchedEffect
        routes.clear()
        tab = ScTab.SEARCH
        searchSeed = query
        onSearchHandled()
    }
    LaunchedEffect(openUser) {
        val user = openUser ?: return@LaunchedEffect
        if ((routes.top as? ScRoute.User)?.user?.id != user.id) routes.push(ScRoute.User(user))
        onOpenUserHandled()
    }
    var pendingRevealTrack by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(revealUser, revealPlaylist, revealTrackId) {
        tab = ScTab.DISCOVER
        val playlist = revealPlaylist
        val user = revealUser
        if (playlist != null) {
            routes.clear()
            pendingRevealTrack = revealTrackId
            routes.push(ScRoute.Playlist(playlist))
            return@LaunchedEffect
        }
        if (user == null) return@LaunchedEffect
        routes.clear()
        pendingRevealTrack = revealTrackId
        routes.push(ScRoute.User(user))
    }
    var liked by remember(account) { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(account) { liked = if (account != null) runCatching { client.likedIds() }.getOrDefault(emptySet()) else emptySet() }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val failed: (Throwable) -> Unit = { error -> android.widget.Toast.makeText(appContext, error.message ?: "操作失败", android.widget.Toast.LENGTH_SHORT).show() }
    val onLike: ((ScTrack) -> Unit)? = if (account == null) null else { track ->
        val was = track.id in liked
        haptics.click(HapticArea.PLAYBACK)
        liked = if (was) liked - track.id else liked + track.id
        scope.launch {
            runCatching { client.setLiked(track.id, !was) }.onFailure { liked = if (was) liked + track.id else liked - track.id; failed(it) }
        }
    }
    var reposted by remember(account) { mutableStateOf<Set<Long>>(emptySet()) }
    var following by remember(account) { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(account) {
        reposted = if (account != null) runCatching { client.repostedIds() }.getOrDefault(emptySet()) else emptySet()
        following = if (account != null) runCatching { client.followingIds() }.getOrDefault(emptySet()) else emptySet()
    }
    val onRepost: ((ScTrack) -> Unit)? = if (account == null) null else { track ->
        val was = track.id in reposted
        haptics.click(HapticArea.MENUS)
        reposted = if (was) reposted - track.id else reposted + track.id
        scope.launch {
            runCatching { client.setReposted(track.id, !was) }.onFailure { reposted = if (was) reposted + track.id else reposted - track.id; failed(it) }
        }
    }
    val onFollow: ((ScUser) -> Unit)? = if (account == null) null else { user ->
        val was = user.id in following
        haptics.click(HapticArea.MENUS)
        following = if (was) following - user.id else following + user.id
        scope.launch {
            runCatching { client.setFollowing(user.id, !was) }.onFailure { following = if (was) following + user.id else following - user.id; failed(it) }
        }
    }
    var playOrigin by remember { mutableStateOf<com.olbyine.music.PlayOrigin?>(null) }
    val context = ScContext(
        client, currentTrackId, isPlaying, liked, onLike,
        { tracks, index, shuffle -> onPlay(tracks, index, shuffle, playOrigin) },
        { routes.pushFromTouch(it) }, reposted, onRepost, following, onFollow,
        setOrigin = { playOrigin = it },
    )
    CompositionLocalProvider(LocalSc provides context) {
        PageStack(routes, backEnabled = true, base = { ScHome(account, tab, { tab = it }, searchSeed, bottomPadding) }) { route ->
            val back: () -> Unit = { routes.pop() }
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).clickable(remember { MutableInteractionSource() }, indication = null) {}) {            when (route) {
                is ScRoute.Web -> ScWebPage(route.url, route.title, client, back)
                is ScRoute.Playlist -> ScPlaylistPage(route.playlist, bottomPadding, back, pendingRevealTrack) {
                    pendingRevealTrack = null
                    onRevealHandled()
                }
                is ScRoute.User -> ScUserPage(route.user, bottomPadding, back, pendingRevealTrack) {
                    pendingRevealTrack = null
                    onRevealHandled()
                }
                is ScRoute.Genre -> ScTrackListPage("#${route.tag}", "曲风 · 热门", bottomPadding, back) { next -> client.genre(route.tag, next) }
                is ScRoute.Conversation -> ScConversationPage(route.id, route.user, bottomPadding, back)
                is ScRoute.People -> ScPeoplePage(route.user, route.followers, bottomPadding, back)
                is ScRoute.Related -> ScTrackListPage("相似曲目", "与「${route.track.title}」相似", bottomPadding, back) { next ->
                    if (next == null) com.olbyine.music.data.ScPage(client.related(route.track.id), null) else com.olbyine.music.data.ScPage(emptyList(), null)
                }
            }
            }
        }
    }
}

@Composable
private fun ScHome(account: ScUser?, tab: ScTab, onTab: (ScTab) -> Unit, searchSeed: String?, bottomPadding: Dp) {
    val sc = LocalSc.current ?: return
    val haptics = rememberHaptics()
    var loginOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().padding(start = 20.dp, top = 18.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("SoundCloud", Modifier.weight(1f), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
            LocalStreamingSwitch.current?.invoke()
        }
        PillTabs(ScTab.entries.map { it to it.label }, tab, { haptics.tick(HapticArea.MENUS); onTab(it) }, Modifier.padding(bottom = 10.dp))
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.weight(1f),
            transitionSpec = { fadeIn(tween(150, easing = OlbyineTokens.Motion.easeOut)) togetherWith fadeOut(tween(90, easing = OlbyineTokens.Motion.easeOut)) },
            label = "sc-tab",
        ) { tab ->
            when (tab) {
                ScTab.DISCOVER -> ScDiscover(bottomPadding)
                ScTab.SEARCH -> ScSearch(searchSeed, bottomPadding)
                ScTab.FEED -> if (account == null) ScLoginCard { loginOpen = true } else ScFeed(bottomPadding)
                ScTab.INBOX -> if (account == null) ScLoginCard { loginOpen = true } else ScInbox(bottomPadding)
                ScTab.MINE -> if (account == null) ScLoginCard { loginOpen = true } else ScMine(account, bottomPadding) { sc.client.signOut() }
            }
        }
    }
    if (loginOpen) ScLoginSheet(sc.client) { loginOpen = false }
}

@Composable
private fun ScDiscover(bottomPadding: Dp) {
    val sc = LocalSc.current ?: return
    var selections by remember { mutableStateOf<List<ScSelection>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(attempt) {
        error = null
        try {
            selections = sc.client.discover()
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            error = failure.message ?: "加载失败，请检查网络"
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding + 24.dp)) {
        item(key = "genres") {
            Column(Modifier.padding(horizontal = 18.dp)) {
                Overline("曲风", Modifier.padding(bottom = 6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SoundCloudClient.GENRES.forEach { genre ->
                        Text(
                            genre,
                            Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .07f))
                                .clickable { sc.open(ScRoute.Genre(genre)) }.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        val rows = selections
        if (rows == null && error == null) {
            items(2, key = { "shelf-skeleton-$it" }) {
                Column(itemMotion().padding(top = 20.dp)) {
                    Box(Modifier.padding(start = 18.dp, bottom = 10.dp).width(120.dp).height(14.dp).skeleton(RoundedCornerShape(7.dp)))
                    Row(Modifier.padding(horizontal = 12.dp)) { repeat(3) { SkeletonCard(Modifier.width(150.dp)) } }
                }
            }
        } else if (rows == null) {
            item(key = "state") { ScState(false, error) { attempt++ } }
        } else {
            rows.forEachIndexed { row, selection ->
                item(key = "t:$row") { Overline(selection.title, itemMotion().padding(start = 18.dp, top = 20.dp, bottom = 6.dp)) }
                item(key = "r:$row") {
                    LazyRow(itemMotion(), contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        itemsIndexed(selection.playlists.distinctBy { it.urn }, key = { index, it -> "$index:${it.urn}" }) { _, playlist -> ScCoverCard(playlist, itemMotion().width(150.dp)) { sc.open(ScRoute.Playlist(playlist)) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScCoverCard(playlist: ScPlaylist, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(6.dp)) {
        UrlImage(playlist.artworkUrl, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        Text(playlist.title, Modifier.padding(start = 2.dp, top = 7.dp), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(playlist.user.username.takeIf { it.isNotBlank() }, if (playlist.album) "专辑" else null).joinToString(" · ").ifBlank { "SoundCloud" },
            Modifier.padding(start = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private enum class ScSearchKind(val label: String, val path: String) {
    TRACKS("单曲", "tracks"), PLAYLISTS("歌单", "playlists_without_albums"), ALBUMS("专辑", "albums"), USERS("用户", "users"),
}

@Composable
private fun ScSearch(seed: String?, bottomPadding: Dp) {
    val sc = LocalSc.current ?: return
    var input by rememberSaveable(seed) { mutableStateOf(seed.orEmpty()) }
    var query by rememberSaveable(seed) { mutableStateOf(seed.orEmpty()) }
    var kind by rememberSaveable { mutableStateOf(ScSearchKind.TRACKS) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { OlbyineSearchField(input, { input = it }, "搜索曲目、歌单、艺人") }
            Spacer(Modifier.width(8.dp))
            PillButton("搜索", { query = input.trim() }, enabled = input.isNotBlank())
        }
        if (query.isBlank()) {
            EmptyState(Icons.Rounded.Waves, "在 SoundCloud 上搜索", Modifier.fillMaxSize(), message = "独立音乐人、混音、Remix 和播客")
            return@Column
        }
        PillTabs(ScSearchKind.entries.map { it to it.label }, kind, { kind = it }, Modifier.padding(vertical = 10.dp))
        Paged(key = query + kind.name, bottomPadding = bottomPadding, load = { next -> sc.client.search(query, kind.path, next) }) { items, index ->
            when (val item = items[index]) {
                is ScTrack -> ScTrackRow(items.filterIsInstance<ScTrack>(), items.filterIsInstance<ScTrack>().indexOf(item))
                is ScUser -> ScUserRow(item) { sc.open(ScRoute.User(item)) }
                is ScPlaylist -> ScPlaylistRow(item) { sc.open(ScRoute.Playlist(item)) }
            }
        }
    }
}

@Composable
private fun <T> Paged(
    key: String,
    bottomPadding: Dp,
    header: (@Composable () -> Unit)? = null,
    load: suspend (next: String?) -> com.olbyine.music.data.ScPage<T>,
    revealMatch: ((T) -> Boolean)? = null,
    onRevealed: () -> Unit = {},
    row: @Composable (List<T>, Int) -> Unit,
) {
    val items = remember(key) { mutableStateListOf<T>() }
    var next by remember(key) { mutableStateOf<String?>(null) }
    var done by remember(key) { mutableStateOf(false) }
    var loading by remember(key) { mutableStateOf(false) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val more: () -> Unit = {
        if (!loading && !done) {
            loading = true
            error = null
            scope.launch {
                try {
                    val page = load(next)
                    items += page.items
                    next = page.next
                    done = page.next == null || page.items.isEmpty()
                } catch (failure: Throwable) {
                    if (failure is CancellationException) throw failure
                    error = failure.message ?: "加载失败，请检查网络"
                }
                loading = false
            }
        }
    }
    LaunchedEffect(key) { more() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val revealOffset = with(androidx.compose.ui.platform.LocalDensity.current) { 96.dp.roundToPx() }
    var handledReveal by remember { mutableStateOf(false) }
    LaunchedEffect(items.size, revealMatch) {
        val match = revealMatch ?: return@LaunchedEffect
        if (handledReveal) return@LaunchedEffect
        val index = items.indexOfFirst(match)
        if (index < 0) return@LaunchedEffect
        handledReveal = true
        listState.scrollToItem(index + if (header != null) 1 else 0)
        listState.scrollBy(-revealOffset.toFloat())
        onRevealed()
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = bottomPadding + 24.dp),
    ) {
        if (header != null) item(key = "header") { header() }
        items(items.size, key = { "$key:$it" }) { index ->
            if (index >= items.size - 6) LaunchedEffect(items.size) { more() }
            MotionItem { row(items, index) }
        }
        if (loading && items.isEmpty()) skeletonRows(8)
        else if (loading || error != null) item(key = "state") { Box(itemMotion()) { ScState(loading, error, more) } }
        else if (done && items.isEmpty()) item(key = "empty") {
            Text("这里还没有内容", itemMotion().fillMaxWidth().padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScState(loading: Boolean, error: String?, onRetry: () -> Unit) {
    LoadingOrError(loading, error, onRetry, accent = ScOrange)
}

@Composable
private fun ScTrackRow(tracks: List<ScTrack>, index: Int) {
    val sc = LocalSc.current ?: return
    val track = tracks.getOrNull(index) ?: return
    val current = sc.currentTrackId == ScTrack.trackId(track.id)
    val comments = LocalScComments.current
    val haptics = rememberHaptics()
    var menu by remember { mutableStateOf(false) }
    val highlight by androidx.compose.animation.animateColorAsState(
        if (current) ScOrange.copy(alpha = .09f) else Color.Transparent, tween(OlbyineTokens.Motion.standardMs), label = "sc-row-current",
    )
    val titleInk by androidx.compose.animation.animateColorAsState(
        if (current) ScOrange else MaterialTheme.colorScheme.onSurface, tween(OlbyineTokens.Motion.standardMs), label = "sc-row-title",
    )
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(highlight)
            .clickable(enabled = track.streamable) { haptics.click(HapticArea.PLAYBACK); sc.onPlay(tracks, index, false) }
            .padding(start = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp)) {
            UrlImage(track.artworkUrl ?: track.user.avatarUrl, Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
            androidx.compose.animation.AnimatedVisibility(
                visible = current,
                enter = androidx.compose.animation.fadeIn(tween(OlbyineTokens.Motion.standardMs, easing = OlbyineTokens.Motion.easeOut)),
                exit = androidx.compose.animation.fadeOut(tween(OlbyineTokens.Motion.quickMs)),
            ) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = .42f)), contentAlignment = Alignment.Center) {
                    EqualizerBars(sc.isPlaying, Color.White, Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleSmall, color = titleInk,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.preview) {
                    Badge("试听", color = ScOrange, filled = true)
                    Spacer(Modifier.width(5.dp))
                } else if (!track.streamable) {
                    Badge("不可播放", filled = true)
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    listOfNotNull(track.user.username, track.playCount.takeIf { it > 0 }?.let { "▶ " + playCountLabel(it) }, formatDuration(track.durationMs)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        sc.onLike?.let { like ->
            val liked = track.id in sc.liked
            val likedAtLoad = remember(track.id) { liked }
            HeartWithMenu(
                track = { track.toTrack() },
                liked = liked,
                onToggle = { like(track) },
                count = track.likeCount + when {
                    liked && !likedAtLoad -> 1
                    !liked && likedAtLoad -> -1
                    else -> 0
                },
                likedColor = ScOrange,
                idleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                pill = MaterialTheme.colorScheme.onSurface.copy(alpha = .06f),
                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                iconSize = 16.dp,
            )
        }
        Box {
            GhostIconButton(Icons.Rounded.MoreVert, "更多", { menu = true }, size = 40.dp, iconSize = 19.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            OlbyineDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                GlassMenuItem("相似曲目", { menu = false; sc.open(ScRoute.Related(track)) }, icon = Icons.Rounded.Waves)
                GlassMenuItem("查看 ${track.user.username}", { menu = false; sc.open(ScRoute.User(track.user)) }, icon = Icons.Rounded.Person)
                sc.onRepost?.let { repost ->
                    val done = track.id in sc.reposted
                    GlassMenuItem(if (done) "取消转发" else "转发", { menu = false; repost(track) }, icon = Icons.Rounded.Repeat)
                }
                if (comments != null) {
                    GlassMenuItem("评论 · ${track.commentCount}", { menu = false; comments(track.id, track.title) }, icon = Icons.Rounded.ChatBubbleOutline)
                }
            }
        }
    }
}

@Composable
private fun ScUserRow(user: ScUser, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        UrlImage(user.avatarUrl, Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(user.username, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull("${playCountLabel(user.followers.toLong())} 粉丝", "${user.trackCount} 首曲目", user.city.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ScPlaylistRow(playlist: ScPlaylist, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        UrlImage(playlist.artworkUrl, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(playlist.user.username, "${playlist.trackCount} 首", if (playlist.album) "专辑" else "歌单").filter(String::isNotBlank).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String, onBack: () -> Unit, art: @Composable () -> Unit, actions: @Composable () -> Unit = {}) {
    Column(Modifier.statusBarsPadding().padding(horizontal = 8.dp)) {
        Spacer(Modifier.height(8.dp))
        OlbyineIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回", onBack, size = 38.dp, iconSize = 19.dp)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            art()
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(14.dp))
        actions()
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PlayButtons(tracks: List<ScTrack>) {
    val sc = LocalSc.current ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("播放", { sc.onPlay(tracks, 0, false) }, icon = Icons.Rounded.PlayArrow, enabled = tracks.isNotEmpty())
        PillButton("随机", { sc.onPlay(tracks, 0, true) }, icon = Icons.Rounded.Shuffle, style = PillStyle.TONAL, enabled = tracks.isNotEmpty())
    }
}

@Composable
private fun ScPlaylistPage(
    target: ScPlaylist,
    bottomPadding: Dp,
    onBack: () -> Unit,
    revealTrackId: Long? = null,
    onRevealed: () -> Unit = {},
) {
    val sc = LocalSc.current ?: return
    var loaded by remember(target.urn) { mutableStateOf<ScPlaylist?>(null) }
    var error by remember(target.urn) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(target.urn, attempt) {
        error = null
        try {
            loaded = sc.client.playlist(target)
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            error = failure.message ?: "加载失败"
        }
    }
    val playlist = loaded ?: target
    val tracks = loaded?.tracks.orEmpty()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val revealOffset = with(androidx.compose.ui.platform.LocalDensity.current) { 96.dp.roundToPx() }
    var handledReveal by remember { mutableStateOf(false) }
    LaunchedEffect(revealTrackId, tracks) {
        val id = revealTrackId ?: return@LaunchedEffect
        if (handledReveal || tracks.isEmpty()) return@LaunchedEffect
        val index = tracks.indexOfFirst { it.id == id }
        if (index < 0) {
            onRevealed()
            return@LaunchedEffect
        }
        handledReveal = true
        listState.scrollToItem(index + 1)
        listState.scrollBy(-revealOffset.toFloat())
        onRevealed()
    }
    LaunchedEffect(playlist.urn) {
        sc.setOrigin(
            com.olbyine.music.PlayOrigin("sc:playlist", playlist.urn, playlist.title, cover = playlist.artworkUrl, scPlaylist = playlist),
        )
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = bottomPadding + 24.dp),
    ) {
        item(key = "header") {
            PageHeader(
                playlist.title,
                listOf(playlist.user.username, "${tracks.size.takeIf { it > 0 } ?: playlist.trackCount} 首", if (playlist.album) "专辑" else "歌单").filter(String::isNotBlank).joinToString(" · "),
                onBack,
                art = { UrlImage(playlist.artworkUrl, Modifier.size(116.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) },
            ) { PlayButtons(tracks) }
        }
        if (loaded == null && error == null) skeletonRows(10)
        else if (loaded == null) item(key = "state") { Box(itemMotion()) { ScState(false, error) { attempt++ } } }
        items(tracks.size, key = { "${tracks[it].id}:$it" }) { index -> MotionItem { ScTrackRow(tracks, index) } }
    }
}

@Composable
private fun ScUserPage(
    target: ScUser,
    bottomPadding: Dp,
    onBack: () -> Unit,
    revealTrackId: Long? = null,
    onRevealed: () -> Unit = {},
) {
    val sc = LocalSc.current ?: return
    var user by remember(target.id) { mutableStateOf(target) }
    var shelf by rememberSaveable(target.id) { mutableStateOf(0) }
    val showPlaylists = shelf == 2
    var playlists by remember(target.id) { mutableStateOf<List<ScPlaylist>?>(null) }
    LaunchedEffect(target.id) { runCatching { sc.client.user(target.id) }.onSuccess { user = it } }
    LaunchedEffect(target.id) { sc.setOrigin(com.olbyine.music.PlayOrigin("sc:user", target.id.toString(), target.username)) }
    LaunchedEffect(target.id, showPlaylists) { if (showPlaylists && playlists == null) playlists = runCatching { sc.client.userPlaylists(target.id) }.getOrDefault(emptyList()) }
    val header: @Composable () -> Unit = {
        PageHeader(
            user.username,
            listOfNotNull("${playCountLabel(user.followers.toLong())} 粉丝", "${user.trackCount} 首曲目", user.city.takeIf { it.isNotBlank() }).joinToString(" · "),
            onBack,
            art = { UrlImage(user.avatarUrl, Modifier.size(104.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                sc.onFollow?.let { follow ->
                    val followed = user.id in sc.following
                    PillButton(if (followed) "已关注" else "关注", { follow(user) }, icon = if (followed) Icons.Rounded.Check else Icons.Rounded.PersonAdd,
                        style = if (followed) PillStyle.TONAL else PillStyle.FILLED, height = 38.dp)
                    PillButton("私信", { sc.open(ScRoute.Conversation(sc.client.conversationId(user.id), user)) }, icon = Icons.Rounded.Mail, style = PillStyle.TONAL, height = 38.dp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ScCount("粉丝", user.followers) { sc.open(ScRoute.People(user, followers = true)) }
                ScCount("关注", user.followings) { sc.open(ScRoute.People(user, followers = false)) }
                ScCount("曲目", user.trackCount, null)
                if (user.likes > 0) ScCount("喜欢", user.likes, null)
            }
            if (user.description.isNotBlank()) {
                Text(user.description, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            PillTabs(listOf(0 to "曲目", 1 to "喜欢", 2 to "歌单"), shelf, { shelf = it }, contentPadding = PaddingValues(0.dp))
        }
    }
    if (!showPlaylists) {
        Paged(
            key = "user:${target.id}:$shelf",
            bottomPadding = bottomPadding,
            header = header,
            load = { next -> if (shelf == 1) sc.client.userLikes(target.id, next) else sc.client.userTracks(target.id, next) },
            revealMatch = revealTrackId?.let { id -> { track: ScTrack -> track.id == id } },
            onRevealed = onRevealed,
        ) { items, index ->
            ScTrackRow(items, index)
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = bottomPadding + 24.dp)) {
            item(key = "header") { header() }
            val list = playlists
            if (list == null) skeletonRows(6)
            else itemsIndexed(list, key = { index, it -> "$index:${it.urn}" }) { _, playlist -> MotionItem { ScPlaylistRow(playlist) { sc.open(ScRoute.Playlist(playlist)) } } }
        }
    }
}

@Composable
private fun ScTrackListPage(
    title: String,
    subtitle: String,
    bottomPadding: Dp,
    onBack: () -> Unit,
    load: suspend (String?) -> com.olbyine.music.data.ScPage<ScTrack>,
) {
    Paged(
        key = title + subtitle,
        bottomPadding = bottomPadding,
        header = {
            PageHeader(title, subtitle, onBack, art = {
                Box(
                    Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(ScOrange, Color(0xFFFF2D55)))),
                    contentAlignment = Alignment.Center,
                ) { StyledIcon(Icons.Rounded.Waves, null, Modifier.size(44.dp), tint = Color.White) }
            })
        },
        load = load,
    ) { items, index -> ScTrackRow(items, index) }
}

private enum class ScShelf(val label: String) { LIKES("喜欢"), PLAYLISTS("歌单"), STREAM("动态"), HISTORY("最近播放") }

@Composable
private fun ScMine(account: ScUser, bottomPadding: Dp, onSignOut: () -> Unit) {
    val sc = LocalSc.current ?: return
    var shelf by rememberSaveable { mutableStateOf(ScShelf.LIKES) }
    var tracks by remember(shelf) { mutableStateOf<List<ScTrack>?>(null) }
    var playlists by remember(shelf) { mutableStateOf<List<ScPlaylist>?>(null) }
    var error by remember(shelf) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(shelf, attempt) {
        error = null
        try {
            when (shelf) {
                ScShelf.LIKES -> tracks = sc.client.likedTracks()
                ScShelf.PLAYLISTS -> playlists = sc.client.myPlaylists()
                ScShelf.STREAM -> tracks = sc.client.stream()
                ScShelf.HISTORY -> tracks = sc.client.history()
            }
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            error = failure.message ?: "加载失败"
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = bottomPadding + 24.dp)) {
        item(key = "account") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp).clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .78f))
                    .clickable { sc.open(ScRoute.User(account)) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UrlImage(account.avatarUrl, Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(account.username, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("查看我的主页 ›", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                GhostIconButton(Icons.AutoMirrored.Rounded.Logout, "退出登录", onSignOut, size = 40.dp, iconSize = 20.dp)
            }
        }
        item(key = "shelves") { PillTabs(ScShelf.entries.map { it to it.label }, shelf, { shelf = it }, Modifier.padding(vertical = 12.dp), PaddingValues(horizontal = 4.dp)) }
        if (shelf == ScShelf.PLAYLISTS) {
            val list = playlists
            if (list == null && error == null) skeletonRows(6)
            else if (list == null) item(key = "state") { Box(itemMotion()) { ScState(false, error) { attempt++ } } }
            else itemsIndexed(list, key = { index, it -> "${shelf.name}:$index:${it.urn}" }) { _, playlist -> MotionItem { ScPlaylistRow(playlist) { sc.open(ScRoute.Playlist(playlist)) } } }
        } else {
            val list = tracks
            if (list == null && error == null) skeletonRows(8)
            else if (list == null) item(key = "state") { Box(itemMotion()) { ScState(false, error) { attempt++ } } }
            else {
                if (list.isNotEmpty()) item(key = "play") { Box(itemMotion().padding(horizontal = 6.dp, vertical = 4.dp)) { PlayButtons(list) } }
                items(list.size, key = { "${shelf.name}:${list[it].id}:$it" }) { index -> MotionItem { ScTrackRow(list, index) } }
            }
        }
    }
}

@Composable
private fun ScLoginCard(onLogin: () -> Unit) {
    Box(Modifier.fillMaxSize().wrapContentHeight(Alignment.Top, unbounded = false), contentAlignment = Alignment.TopCenter) { Column(
        Modifier.widthIn(max = 640.dp).fillMaxWidth().wrapContentHeight().padding(16.dp).clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(ScOrange, Color(0xFFFF2D55))))
            .padding(22.dp),
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = .2f)), contentAlignment = Alignment.Center) {
            StyledIcon(Icons.Rounded.Waves, null, Modifier.size(30.dp), tint = Color.White)
        }
        Spacer(Modifier.height(16.dp))
        Text("登录 SoundCloud", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "不登录也能发现、搜索和收听。登录后可以看到你喜欢的曲目、歌单、关注的动态和最近播放，红心会同步回 SoundCloud。Go+ 付费曲目按你的账号权限播放（没有权限的只有 30 秒试听）。登录令牌只保存在本机，只发送给 SoundCloud。",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = .88f),
        )
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.height(44.dp).clip(CircleShape).background(Color.White).clickable(onClick = onLogin).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("登录", style = MaterialTheme.typography.labelLarge, color = ScOrange, fontWeight = FontWeight.SemiBold)
        }
    } }
}

@Composable
private fun ScLoginSheet(client: SoundCloudClient, onDismiss: () -> Unit) {
    var web by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val submit: (String) -> Unit = { raw ->
        busy = true
        error = null
        scope.launch {
            try {
                client.signIn(raw)
                haptics.confirm(HapticArea.MENUS)
                onDismiss()
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                error = failure.message ?: "登录失败"
            }
            busy = false
        }
    }
    GlassBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).imePadding()) {
            Text("登录 SoundCloud", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("在 SoundCloud 自己的登录页登录（邮箱或 Apple 账号），完成后自动回到这里。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            PillButton(if (busy) "正在登录…" else "打开 SoundCloud 登录页", { web = true }, enabled = !busy)
            Spacer(Modifier.height(20.dp))
            Overline("或者粘贴令牌")
            Spacer(Modifier.height(6.dp))
            Text(
                "Google 账号无法在应用内网页登录：可以在电脑浏览器登录 soundcloud.com 后，从 Cookie 里复制 oauth_token 的值粘贴到这里。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OlbyineTextField(token, { token = it }, "oauth_token", password = true)
            Spacer(Modifier.height(10.dp))
            PillButton("登录", { submit(token) }, style = PillStyle.TONAL, enabled = token.length >= 10 && !busy)
            error?.let { Text(it, Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.navigationBarsPadding().height(18.dp))
        }
    }
    if (web) ScWebLogin(onToken = { web = false; submit(it) }, onDismiss = { web = false })
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ScWebLogin(onToken: (String) -> Unit, onDismiss: () -> Unit) {
    var delivered by remember { mutableStateOf(false) }
    val deliver: () -> Unit = {
        val cookie = CookieManager.getInstance().getCookie("https://soundcloud.com").orEmpty()
        val token = Regex("oauth_token=([^;\\s]+)").find(cookie)?.groupValues?.get(1)
        if (!delivered && token != null) {
            delivered = true
            onToken(token)
        }
    }
    LaunchedEffect(Unit) {
        while (!delivered) {
            delay(1_200)
            deliver()
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                GhostIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "关闭", onDismiss)
                Text("SoundCloud 登录", style = MaterialTheme.typography.titleMedium)
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = SoundCloudClient.USER_AGENT
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        val cookies = CookieManager.getInstance()
                        cookies.setAcceptCookie(true)
                        cookies.setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) = deliver()
                        }
                        loadUrl("https://soundcloud.com/signin")
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}

@Composable
internal fun ScCommentsSheet(client: SoundCloudClient, trackId: Long, title: String, onDismiss: () -> Unit, positionMs: () -> Long = { 0L }) {
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var round by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val sheetHeight = (LocalConfiguration.current.screenHeightDp * .86f).dp
    GlassBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().height(sheetHeight).imePadding()) {
            Column(Modifier.padding(horizontal = 22.dp)) {
                Text("评论", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.weight(1f)) {
                Paged(key = "comments:$trackId:$round", bottomPadding = 0.dp, load = { next -> client.comments(trackId, next) }) { items, index ->
                    ScCommentRow(items[index])
                }
            }
            if (client.signedIn) {
                val at = remember(round) { positionMs() }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { OlbyineTextField(draft, { draft = it }, "在 ${formatDuration(at)} 处评论") }
                    Spacer(Modifier.width(8.dp))
                    OlbyineIconButton(
                        Icons.AutoMirrored.Rounded.Send,
                        "发送",
                        {
                            val text = draft.trim()
                            if (text.isNotEmpty() && !sending) {
                                sending = true
                                error = null
                                scope.launch {
                                    try {
                                        client.postComment(trackId, text, at)
                                        draft = ""
                                        round++
                                    } catch (failure: Throwable) {
                                        if (failure is CancellationException) throw failure
                                        error = failure.message ?: "评论失败"
                                    }
                                    sending = false
                                }
                            }
                        },
                        size = 44.dp,
                        container = ScOrange,
                        tint = Color.White,
                        enabled = draft.isNotBlank() && !sending,
                    )
                }
                error?.let { Text(it, Modifier.padding(horizontal = 22.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
    }
}

private val scTime = java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINA)

private fun scDate(raw: String): String = runCatching {
    val instant = runCatching { java.time.Instant.parse(raw) }.getOrNull()
        ?: java.time.ZonedDateTime.parse(raw, java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss Z")).toInstant()
    scTime.format(java.util.Date(instant.toEpochMilli()))
}.getOrDefault("")

@Composable
private fun ScFeed(bottomPadding: Dp) {
    val sc = LocalSc.current ?: return
    Paged(key = "feed", bottomPadding = bottomPadding, load = { next -> sc.client.feed(next) }) { items, index ->
        val item = items[index]
        Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Row(Modifier.padding(start = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                UrlImage(item.by.avatarUrl, Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable { sc.open(ScRoute.User(item.by)) })
                Spacer(Modifier.width(6.dp))
                if (item.repost) {
                    StyledIcon(Icons.Rounded.Repeat, null, Modifier.size(13.dp), tint = ScOrange)
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    item.by.username + (if (item.repost) " 转发了" else " 发布了") + " · " + scDate(item.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.caption.isNotBlank()) {
                Text(item.caption, Modifier.padding(start = 34.dp, end = 8.dp), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            val track = item.track
            val playlist = item.playlist
            if (track != null) {
                val tracks = items.mapNotNull { it.track }
                ScTrackRow(tracks, tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
            } else if (playlist != null) {
                ScPlaylistRow(playlist) { sc.open(ScRoute.Playlist(playlist)) }
            }
        }
    }
}

private enum class ScInboxTab(val label: String) { NOTIFICATIONS("通知"), MESSAGES("私信") }

@Composable
private fun ScInbox(bottomPadding: Dp) {
    val sc = LocalSc.current ?: return
    var tab by rememberSaveable { mutableStateOf(ScInboxTab.NOTIFICATIONS) }
    Column(Modifier.fillMaxSize()) {
        PillTabs(ScInboxTab.entries.map { it to it.label }, tab, { tab = it }, Modifier.padding(bottom = 6.dp))
        Box(Modifier.weight(1f)) {
            when (tab) {
                ScInboxTab.NOTIFICATIONS -> Paged(key = "activities", bottomPadding = bottomPadding, load = { next -> sc.client.activities(next) }) { items, index ->
                    val item = items[index]
                    val verb = when {
                        item.type.contains("like") -> "喜欢了你的作品"
                        item.type.contains("repost") -> "转发了你的作品"
                        item.type.contains("comment") -> "评论了"
                        item.type.contains("affiliation") || item.type.contains("follow") -> "关注了你"
                        else -> "与你互动"
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                            when {
                                item.track != null -> sc.onPlay(listOf(item.track), 0, false)
                                item.playlist != null -> sc.open(ScRoute.Playlist(item.playlist))
                                else -> sc.open(ScRoute.User(item.user))
                            }
                        }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UrlImage(item.user.avatarUrl, Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable { sc.open(ScRoute.User(item.user)) })
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${item.user.username} $verb", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val what = item.track?.title ?: item.playlist?.title
                            if (item.comment.isNotBlank()) Text("「${item.comment}」", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(what, scDate(item.createdAt).takeIf(String::isNotBlank)).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (item.user.id > 0 && (item.type.contains("affiliation") || item.type.contains("follow"))) {
                            sc.onFollow?.let { follow ->
                                val followed = item.user.id in sc.following
                                PillButton(if (followed) "已关注" else "回关", { follow(item.user) }, style = if (followed) PillStyle.TONAL else PillStyle.FILLED, height = 32.dp)
                            }
                        }
                    }
                }
                ScInboxTab.MESSAGES -> Paged(
                    key = "conversations",
                    bottomPadding = bottomPadding,
                    header = {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("私信", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            PillButton("网页版私信", { sc.open(ScRoute.Web("https://soundcloud.com/messages", "SoundCloud 私信")) }, icon = Icons.AutoMirrored.Rounded.OpenInNew, style = PillStyle.TONAL, height = 34.dp)
                        }
                    },
                    load = { next -> sc.client.conversations(next) },
                ) { items, index ->
                    val item = items[index]
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { sc.open(ScRoute.Conversation(item.id, item.other)) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            UrlImage(item.other.avatarUrl, Modifier.size(50.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                            if (!item.read) Box(Modifier.align(Alignment.TopEnd).size(10.dp).clip(CircleShape).background(ScOrange))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.other.username, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(item.lastMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(scDate(item.lastAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScConversationPage(id: String, user: ScUser, bottomPadding: Dp, onBack: () -> Unit) {
    val sc = LocalSc.current ?: return
    val messages = remember(id) { mutableStateListOf<com.olbyine.music.data.ScMessage>() }
    var loading by remember(id) { mutableStateOf(true) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var draft by rememberSaveable(id) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    LaunchedEffect(id) {
        while (true) {
            try {
                val fresh = sc.client.messages(id)
                if (fresh.size != messages.size) {
                    messages.clear()
                    messages += fresh
                    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
                }
                error = null
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                if (messages.isEmpty() && failure.message?.contains("找不到") != true) error = failure.message ?: "加载失败"
            }
            loading = false
            delay(10_000)
        }
    }
    Column(Modifier.fillMaxSize().padding(bottom = bottomPadding).imePadding()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OlbyineIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回", onBack, size = 40.dp, iconSize = 20.dp)
            Spacer(Modifier.width(10.dp))
            UrlImage(user.avatarUrl, Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable { sc.open(ScRoute.User(user)) })
            Spacer(Modifier.width(10.dp))
            Text(user.username, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            GhostIconButton(Icons.AutoMirrored.Rounded.OpenInNew, "网页版私信", { sc.open(ScRoute.Web("https://soundcloud.com/messages", "SoundCloud 私信")) }, size = 40.dp, iconSize = 20.dp)
        }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val bubbleMax = maxWidth * .78f
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (messages.isEmpty()) item(key = "state") {
                    Box(itemMotion()) {
                        if (loading || error != null) ScState(loading, error) {} else Text("还没有消息，打个招呼吧", Modifier.fillMaxWidth().padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(messages.size, key = { "$id:$it" }) { index ->
                    val message = messages[index]
                    Box(itemMotion().fillMaxWidth(), contentAlignment = if (message.fromMe) Alignment.CenterEnd else Alignment.CenterStart) {
                        Text(
                            message.text,
                            Modifier.widthIn(max = bubbleMax).clip(RoundedCornerShape(18.dp))
                                .background(if (message.fromMe) ScOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            color = if (message.fromMe) Color.White else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
        error?.takeIf { messages.isNotEmpty() }?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { OlbyineTextField(draft, { draft = it }, "发送私信") }
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
                                sc.client.sendMessage(id, text)
                                messages += com.olbyine.music.data.ScMessage(true, text, "")
                                draft = ""
                                listState.animateScrollToItem(messages.lastIndex)
                                error = null
                            } catch (failure: Throwable) {
                                if (failure is CancellationException) throw failure
                                error = failure.message ?: "发送失败"
                            }
                            sending = false
                        }
                    }
                },
                size = 46.dp,
                container = ScOrange,
                tint = Color.White,
                enabled = draft.isNotBlank() && !sending,
            )
        }
    }
}

@Composable
private fun ScPeoplePage(user: ScUser, followers: Boolean, bottomPadding: Dp, onBack: () -> Unit) {
    val sc = LocalSc.current ?: return
    Paged(
        key = "people:${user.id}:$followers",
        bottomPadding = bottomPadding,
        header = {
            PageHeader(if (followers) "粉丝" else "关注", user.username, onBack, art = {
                UrlImage(user.avatarUrl, Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
            })
        },
        load = { next -> if (followers) sc.client.followers(user.id, next) else sc.client.followings(user.id, next) },
    ) { items, index ->
        val person = items[index]
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ScUserRow(person) { sc.open(ScRoute.User(person)) } }
            sc.onFollow?.let { follow ->
                val followed = person.id in sc.following
                PillButton(if (followed) "已关注" else "关注", { follow(person) }, style = if (followed) PillStyle.TONAL else PillStyle.FILLED, height = 32.dp)
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun ScCommentRow(comment: ScComment) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        UrlImage(comment.user.avatarUrl, Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.user.username, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Text(
                    "@ " + formatDuration(comment.atMs),
                    Modifier.clip(CircleShape).background(ScOrange.copy(alpha = .14f)).padding(horizontal = 7.dp, vertical = 1.dp),
                    fontSize = 11.sp,
                    color = ScOrange,
                )
            }
            Text(comment.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ScCount(label: String, count: Int, onClick: (() -> Unit)?) {
    Row(
        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .07f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(playCountLabel(count.toLong()), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
        Text(label + if (onClick != null) " ›" else "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ScWebPage(url: String, title: String, client: SoundCloudClient, onBack: () -> Unit) {
    var web by remember { mutableStateOf<WebView?>(null) }
    BackHandler {
        val view = web
        if (view != null && view.canGoBack()) view.goBack() else onBack()
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OlbyineIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回", onBack, size = 40.dp, iconSize = 20.dp)
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = SoundCloudClient.USER_AGENT
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    val cookies = CookieManager.getInstance()
                    cookies.setAcceptCookie(true)
                    cookies.setAcceptThirdPartyCookies(this, true)
                    client.webToken()?.let { cookies.setCookie("https://soundcloud.com", "oauth_token=$it; Domain=.soundcloud.com; Path=/; Secure") }
                    webViewClient = WebViewClient()
                    loadUrl(url)
                    web = this
                }
            },
            onRelease = { it.destroy() },
        )
    }
}