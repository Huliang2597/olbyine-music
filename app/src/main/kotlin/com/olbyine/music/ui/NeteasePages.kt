package com.olbyine.music.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.olbyine.music.data.NeteaseClient
import com.olbyine.music.data.NeteaseCollectionKind
import com.olbyine.music.data.NeteaseFriend
import com.olbyine.music.data.NeteasePlaylistSummary
import com.olbyine.music.data.NeteaseSong
import com.olbyine.music.data.NeteaseThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val PageRed = Color(0xFFE5303C)

internal sealed interface NcmRoute {
    data class Chat(val thread: NeteaseThread) : NcmRoute
    data class Artist(
        val id: Long?,
        val name: String,
        val coArtists: List<Pair<Long, String>> = emptyList(),
    ) : NcmRoute
    data class Web(val url: String) : NcmRoute
    data class Profile(val userId: Long) : NcmRoute
    data class Collection(val summary: NeteasePlaylistSummary) : NcmRoute
    data class People(val userId: Long, val name: String, val followers: Boolean) : NcmRoute
    data class Ranking(val userId: Long) : NcmRoute
    data object SavedAlbums : NcmRoute
    data object Podcasts : NcmRoute
    data class Podcast(val podcast: NeteaseClient.Podcast) : NcmRoute
    data class Mv(val id: Long) : NcmRoute
    data object ManagePlaylists : NcmRoute
}

internal val LocalNcmPush = androidx.compose.runtime.staticCompositionLocalOf<((NcmRoute) -> Unit)?> { null }

@Composable
internal fun NeteaseOverlayHost(
    stack: PageStackState<NcmRoute>,
    client: NeteaseClient,
    currentTrackId: String?,
    backEnabled: Boolean,
    onPush: (NcmRoute) -> Unit,
    onPlay: (List<NeteaseSong>, Int, Boolean) -> Unit,
    onPauseMusic: () -> Unit = {},
) {
    val onPop: () -> Unit = { stack.pop() }
    PageStack(stack, backEnabled = backEnabled && stack.top !is NcmRoute.Web) { route ->
        val top = route == stack.top
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
        ) { androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            when (route) {
                is NcmRoute.Chat -> NeteaseChatScreen(client, route.thread, onBack = onPop)
                is NcmRoute.Web -> NeteaseWebScreen(client, route.url, onBack = onPop, backEnabled = backEnabled && top)
                is NcmRoute.Artist -> NeteaseArtistScreen(
                    client = client,
                    artistId = route.id,
                    artistName = route.name,
                    currentTrackId = currentTrackId,
                    onBack = onPop,
                    onPlay = { songs, index -> onPlay(songs, index, false) },
                    onOpenAlbum = { onPush(NcmRoute.Collection(it)) },
                    onAccount = { onPush(NcmRoute.Profile(it)) },
                    relatedArtists = route.coArtists,
                    onOpenArtist = { id, name -> onPush(NcmRoute.Artist(id, name)) },
                )
                is NcmRoute.Profile -> NeteaseProfileScreen(client, route.userId, onBack = onPop, onPush = onPush)
                is NcmRoute.Collection -> NeteaseCollectionScreen(client, route.summary, currentTrackId, onBack = onPop, onPush = onPush, onPlay = onPlay)
                is NcmRoute.People -> NeteasePeopleScreen(client, route.userId, route.name, route.followers, onBack = onPop, onPush = onPush)
                is NcmRoute.Ranking -> NeteaseRankingScreen(client, route.userId, currentTrackId, onBack = onPop, onPlay = { songs, index -> onPlay(songs, index, false) })
                NcmRoute.SavedAlbums -> NeteaseSavedAlbumsScreen(client, onBack = onPop, onOpen = { onPush(NcmRoute.Collection(it)) })
                NcmRoute.Podcasts -> NeteasePodcastsScreen(client, onBack = onPop, onOpen = { onPush(NcmRoute.Podcast(it)) })
                is NcmRoute.Podcast -> NeteasePodcastScreen(client, route.podcast, currentTrackId, onBack = onPop, onPlay = { songs, index -> onPlay(songs, index, false) })
                is NcmRoute.Mv -> NeteaseMvScreen(client, route.id, active = top, onBack = onPop, onStart = onPauseMusic)
                NcmRoute.ManagePlaylists -> NeteasePlaylistManager(client, onBack = onPop, onOpen = { onPush(NcmRoute.Collection(it)) })
            }
        } }
    }
}

private enum class ProfileTab(val label: String) { PLAYLISTS("歌单"), ABOUT("简介") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NeteaseProfileScreen(client: NeteaseClient, userId: Long, onBack: () -> Unit, onPush: (NcmRoute) -> Unit) {
    val (profile, retry) = rememberLoad(userId) { client.userProfile(userId) }
    val (playlists, _) = rememberLoad(userId) { client.userPlaylists(userId) }
    var tab by rememberSaveable(userId) { mutableStateOf(ProfileTab.PLAYLISTS) }
    val user = profile.value
    var followed by remember(user) { mutableStateOf(user?.followed == true) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val me = client.account.value?.userId
    val own = playlists.value.orEmpty().filter { it.own }
    val saved = playlists.value.orEmpty().filter { !it.own }
    val colors = MaterialTheme.colorScheme
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        modifier = Modifier.fillMaxSize(),
        state = gridState,
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "head") {
            Box(Modifier.fillMaxWidth().clipToBounds()) {
                Box(Modifier.fillMaxWidth().height(230.dp).parallaxHeader(gridState)) {
                    UrlImage(user?.backgroundUrl, Modifier.fillMaxWidth().height(230.dp).background(colors.surfaceContainerHighest))
                    Box(Modifier.fillMaxWidth().height(230.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .35f), Color.Transparent, colors.background))))
                }
                Box(Modifier.statusBarsPadding().padding(10.dp)) {
                    OlbyineIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回", onBack, size = 40.dp, iconSize = 20.dp)
                }
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 156.dp)) {
                    UrlImage(user?.avatarUrl, Modifier.size(88.dp).clip(CircleShape).border(2.dp, colors.background, CircleShape).background(colors.surfaceContainerHighest))
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(user?.nickname.orEmpty(), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if ((user?.level ?: 0) > 0) {
                            Spacer(Modifier.width(8.dp))
                            Badge("Lv.${user?.level}", color = PageRed, filled = true)
                        }
                    }
                    if (!user?.identity.isNullOrBlank()) Text(user?.identity.orEmpty(), style = MaterialTheme.typography.bodySmall, color = PageRed)
                    if (user == null) {
                        LoadingOrError(profile.loading, profile.error, retry)
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CountChip("关注", user.follows) { onPush(NcmRoute.People(user.userId, user.nickname, followers = false)) }
                            CountChip("粉丝", user.followers) { onPush(NcmRoute.People(user.userId, user.nickname, followers = true)) }
                            CountChip("听歌", user.listened, null)
                        }
                        if (user.signature.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(user.signature, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(12.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (client.signedIn && me != user.userId) {
                                PillButton(
                                    if (followed) "已关注" else "关注",
                                    {
                                        if (!busy) {
                                            busy = true
                                            val target = !followed
                                            followed = target
                                            scope.launch {
                                                try {
                                                    client.setFollowUser(user.userId, target)
                                                } catch (failure: Throwable) {
                                                    if (failure is CancellationException) throw failure
                                                    followed = !target
                                                }
                                                busy = false
                                            }
                                        }
                                    },
                                    icon = if (followed) Icons.Rounded.Check else Icons.Rounded.PersonAdd,
                                    style = if (followed) PillStyle.TONAL else PillStyle.FILLED,
                                    height = 38.dp,
                                )
                                PillButton("私信", { onPush(NcmRoute.Chat(NeteaseThread(user.userId, user.nickname, user.avatarUrl, "", 0, 0))) }, icon = Icons.Rounded.Mail, style = PillStyle.TONAL, height = 38.dp)
                            }
                            if (user.artistId > 0) {
                                PillButton("歌手主页", { onPush(NcmRoute.Artist(user.artistId, user.nickname)) }, icon = Icons.Rounded.Mic, style = PillStyle.TONAL, height = 38.dp)
                            }
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "tabs") {
            PillTabs(ProfileTab.entries.map { it to it.label }, tab, { tab = it }, Modifier.padding(top = 14.dp, bottom = 4.dp))
        }
        if (tab == ProfileTab.PLAYLISTS) {
            if (own.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "own") { Overline("创建的歌单", itemMotion().padding(start = 18.dp, top = 10.dp)) }
                gridItemsIndexed(own, key = { index, it -> "o$index:${it.id}" }) { _, playlist ->
                    NeteaseCoverCard(playlist, { onPush(NcmRoute.Collection(playlist)) }, itemMotion().padding(horizontal = 6.dp))
                }
            }
            if (saved.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "saved") { Overline("收藏的歌单", itemMotion().padding(start = 18.dp, top = 14.dp)) }
                gridItemsIndexed(saved, key = { index, it -> "s$index:${it.id}" }) { _, playlist ->
                    NeteaseCoverCard(playlist, { onPush(NcmRoute.Collection(playlist)) }, itemMotion().padding(horizontal = 6.dp))
                }
            }
            if (playlists.value == null && playlists.loading) skeletonCards(6)
            if (playlists.value != null && own.isEmpty() && saved.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "none") {
                    Text("TA 的歌单不公开，或者还没有歌单", itemMotion().padding(20.dp), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else if (user != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "about") {
                Column(itemMotion().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    AboutRow("昵称", user.nickname)
                    if (user.level > 0) AboutRow("等级", "Lv.${user.level}")
                    when (user.gender) {
                        1 -> AboutRow("性别", "男")
                        2 -> AboutRow("性别", "女")
                    }
                    if (user.birthday > 0) {
                        val year = java.util.Calendar.getInstance().apply { timeInMillis = user.birthday }.get(java.util.Calendar.YEAR)
                        AboutRow("年龄", "${(year % 100) / 5 * 5}后".padStart(3, '0'))
                    }
                    if (user.days > 0) AboutRow("村龄", if (user.days >= 365) "${user.days / 365} 年" else "${user.days} 天")
                    if (user.identity.isNotBlank()) AboutRow("认证", user.identity)
                    if (user.eventCount > 0) AboutRow("动态", "${user.eventCount} 条")
                    if (user.signature.isNotBlank()) AboutRow("签名", user.signature)
                    if (user.links.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Overline("社交网络")
                        Spacer(Modifier.height(6.dp))
                        user.links.forEach { (label, url) ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { openExternal(context, url) }.padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label, Modifier.width(76.dp), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                                Text(url, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                StyledIcon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                            }
                        }
                    }
                    if (user.artistId > 0) {
                        Spacer(Modifier.height(14.dp))
                        PillButton("查看歌手主页", { onPush(NcmRoute.Artist(user.artistId, user.nickname)) }, icon = Icons.Rounded.Mic, style = PillStyle.TONAL)
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "bottom") { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun CountChip(label: String, count: Int, onClick: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.clip(CircleShape).background(colors.onSurface.copy(alpha = .07f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(playCountLabel(count.toLong()), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        if (onClick != null) Text(" ›", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(label, Modifier.width(76.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun NeteasePeopleScreen(client: NeteaseClient, userId: Long, name: String, followers: Boolean, onBack: () -> Unit, onPush: (NcmRoute) -> Unit) {
    val people = remember(userId, followers) { mutableStateListOf<NeteaseFriend>() }
    var more by remember(userId, followers) { mutableStateOf(true) }
    var loading by remember(userId, followers) { mutableStateOf(false) }
    var error by remember(userId, followers) { mutableStateOf<String?>(null) }
    val following = remember(userId, followers) { mutableStateListOf<Long>() }
    val scope = rememberCoroutineScope()
    val me = client.account.value?.userId
    val load: () -> Unit = {
        if (!loading && more) {
            loading = true
            error = null
            scope.launch {
                try {
                    val (page, hasMore) = if (followers) client.userFollowers(userId, people.size) else client.userFollows(userId, people.size)
                    val fresh = page.filter { new -> people.none { it.userId == new.userId } }
                    people += fresh
                    following += fresh.filter { it.followed }.map { it.userId }
                    more = hasMore && fresh.isNotEmpty()
                } catch (failure: Throwable) {
                    if (failure is CancellationException) throw failure
                    error = failure.message ?: "加载失败"
                }
                loading = false
            }
        }
    }
    LaunchedEffect(userId, followers) { load() }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OlbyineIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回", onBack, size = 40.dp, iconSize = 20.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(if (followers) "粉丝" else "关注", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(name, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 32.dp)) {
            itemsIndexed(people, key = { index, it -> "$index:${it.userId}" }) { index, person ->
                if (index >= people.size - 6) LaunchedEffect(people.size) { load() }
                Row(
                    itemMotion().fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPush(NcmRoute.Profile(person.userId)) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UrlImage(person.avatarUrl, Modifier.size(48.dp).clip(CircleShape).background(colors.surfaceContainerHighest))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(person.nickname, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (person.signature.isNotBlank()) Text(person.signature, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (client.signedIn && person.userId != me) {
                        val on = person.userId in following
                        PillButton(if (on) "已关注" else "关注", {
                            if (on) following.remove(person.userId) else following.add(person.userId)
                            scope.launch {
                                runCatching { client.setFollowUser(person.userId, !on) }.onFailure {
                                    if (on) following.add(person.userId) else following.remove(person.userId)
                                }
                            }
                        }, style = if (on) PillStyle.TONAL else PillStyle.FILLED, height = 32.dp)
                    }
                }
            }
            if (loading && people.isEmpty()) skeletonRows(8, avatar = true)
            else if (loading || error != null || people.isEmpty()) item(key = "state") {
                if (!loading && error == null) Text("还没有人", itemMotion().padding(24.dp), color = colors.onSurfaceVariant)
                else LoadingOrError(loading, error, load, itemMotion())
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NeteaseCollectionScreen(
    client: NeteaseClient,
    target: NeteasePlaylistSummary,
    currentTrackId: String?,
    onBack: () -> Unit,
    onPush: (NcmRoute) -> Unit,
    onPlay: (List<NeteaseSong>, Int, Boolean) -> Unit,
) {
    val (loaded, retry) = rememberLoadStream(target.id, target.kind) { client.collectionStream(target) }
    val data = loaded.value
    val summary = data?.summary ?: target
    val allSongs = data?.songs.orEmpty()
    var query by rememberSaveable(target.id) { mutableStateOf("") }
    val songs = remember(allSongs, query) { filterSongs(allSongs, query) }
    var expanded by remember(target.id) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    var saved by remember(target.id) { mutableStateOf<Boolean?>(null) }
    val resumeKey = collectionKey(summary.kind.name, summary.id)
    val scopedPlay = LocalPlayNeteaseCollection.current
    val play: (Int, Boolean) -> Unit = { index, shuffle ->
        if (scopedPlay != null) scopedPlay(resumeKey, songs, index, shuffle, 0L) else onPlay(songs, index, shuffle)
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "head") {
            Box(Modifier.fillMaxWidth().clipToBounds()) {
                Box(Modifier.fillMaxWidth().height(300.dp).parallaxHeader(listState)) {
                    UrlImage(
                        summary.coverUrl?.let { "$it?param=200y200" },
                        Modifier.fillMaxWidth().height(300.dp).blur(40.dp).background(colors.surfaceContainerHighest),
                    )
                    Box(Modifier.fillMaxWidth().height(300.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .30f), colors.background.copy(alpha = .55f), colors.background))))
                }
                Column(Modifier.statusBarsPadding().padding(horizontal = 10.dp)) {
                    Spacer(Modifier.height(10.dp))
                    OlbyineIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回", onBack, size = 40.dp, iconSize = 20.dp)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        UrlImage(
                            summary.coverUrl?.let { "$it?param=500y500" },
                            Modifier.size(136.dp).shadow(16.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainerHighest),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Badge(
                                when (summary.kind) {
                                    NeteaseCollectionKind.ALBUM -> "专辑"
                                    NeteaseCollectionKind.ARTIST -> "热门歌曲"
                                    NeteaseCollectionKind.CHART -> "榜单"
                                    NeteaseCollectionKind.DAILY -> "每日推荐"
                                    NeteaseCollectionKind.PLAYLIST -> "歌单"
                                },
                                color = PageRed,
                                filled = true,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(summary.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (summary.creator.isNotBlank()) {
                                val byline: (() -> Unit)? = when {
                                    data?.ownerId?.let { it > 0 } == true -> { { onPush(NcmRoute.Profile(data.ownerId)) } }
                                    data?.artistId?.let { it > 0 } == true -> { { onPush(NcmRoute.Artist(data.artistId, summary.creator)) } }
                                    else -> null
                                }
                                Text(
                                    summary.creator + if (byline != null) " ›" else "",
                                    Modifier.padding(top = 4.dp).clip(CircleShape).then(if (byline != null) Modifier.clickable(onClick = byline) else Modifier),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurface.copy(alpha = .8f),
                                    maxLines = 1,
                                )
                            }
                            Text(
                                listOfNotNull(
                                    (songs.size.takeIf { it > 0 } ?: summary.trackCount.takeIf { it > 0 })?.let { "$it 首" },
                                    summary.playCount.takeIf { it > 0 }?.let { "播放 " + playCountLabel(it) },
                                    summary.note.takeIf(String::isNotBlank),
                                ).joinToString(" · "),
                                Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                    val tags = data?.tags.orEmpty()
                    if (tags.isNotEmpty()) {
                        FlowRow(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            tags.forEach { Badge(it) }
                        }
                    }
                    val description = data?.description.orEmpty()
                    if (description.isNotBlank()) {
                        Text(
                            description,
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp).expandable().clip(RoundedCornerShape(10.dp)).clickable { expanded = !expanded },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PillButton("播放全部", { if (songs.isNotEmpty()) play(0, false) }, icon = Icons.Rounded.PlayArrow, enabled = songs.isNotEmpty(), height = 40.dp)
                        PillButton("随机播放", { if (songs.isNotEmpty()) play(0, true) }, icon = Icons.Rounded.Shuffle, style = PillStyle.TONAL, enabled = songs.isNotEmpty(), height = 40.dp)
                        Spacer(Modifier.weight(1f))
                        if (client.signedIn && (summary.kind == NeteaseCollectionKind.ALBUM || (summary.kind == NeteaseCollectionKind.PLAYLIST && !summary.own))) {
                            val on = saved == true
                            OlbyineIconButton(
                                if (on) Icons.Rounded.Check else Icons.Rounded.LibraryAdd,
                                if (on) "取消收藏" else "收藏",
                                {
                                    val next = !on
                                    saved = next
                                    scope.launch {
                                        val ok = runCatching {
                                            if (summary.kind == NeteaseCollectionKind.ALBUM) client.setAlbumSaved(summary.id, next) else client.setPlaylistSaved(summary.id, next)
                                        }.isSuccess
                                        if (!ok) saved = !next
                                    }
                                },
                                size = 40.dp,
                                iconSize = 20.dp,
                            )
                        }
                    }
                    if (allSongs.size > 8) {
                        OlbyineSearchField(query, { query = it }, if (summary.kind == NeteaseCollectionKind.ALBUM) "在专辑中搜索" else "在歌单中搜索",
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(bottom = 6.dp))
                    }
                    val resumePoint = LocalCollectionResume.current?.invoke(resumeKey)
                    val resumeCollection = LocalResumeCollection.current
                    if (resumePoint != null && resumeCollection != null && LocalActiveCollection.current != resumeKey) {
                        CollectionResumeRow(resumePoint, Modifier.padding(top = 8.dp)) { resumeCollection(resumeKey, allSongs) }
                    }
                }
            }
        }
        if (data == null && loaded.loading) skeletonRows(10)
        else if (data == null) item(key = "state") { LoadingOrError(false, loaded.error, retry, itemMotion()) }
        else if (songs.isEmpty() && query.isNotBlank()) item(key = "none") {
            Text("没有找到「」", itemMotion().fillMaxWidth().padding(24.dp), color = colors.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        itemsIndexed(songs, key = { index, it -> "$index:${it.id}" }) { index, song ->
            Box(itemMotion().padding(horizontal = 10.dp)) {
                NeteaseSongRow(song, current = currentTrackId == NeteaseSong.trackId(song.id)) { play(index, false) }
            }
        }
        if (data != null && !data.complete) item(key = "more") {
            Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) { OlbyineSpinner() }
        }
        item(key = "bottom") { Spacer(Modifier.navigationBarsPadding()) }
    }
}

internal fun filterSongs(songs: List<NeteaseSong>, query: String): List<NeteaseSong> {
    val q = query.trim()
    if (q.isEmpty()) return songs
    return songs.filter { song ->
        song.title.contains(q, ignoreCase = true) || song.album.contains(q, ignoreCase = true) ||
            song.artists.any { it.contains(q, ignoreCase = true) }
    }
}
