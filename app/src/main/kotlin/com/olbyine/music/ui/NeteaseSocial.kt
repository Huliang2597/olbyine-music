@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.olbyine.music.ui

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.luminance

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.olbyine.music.MainViewModel
import com.olbyine.music.data.NeteaseCard
import com.olbyine.music.data.NeteaseClient
import com.olbyine.music.data.NeteaseFriend
import com.olbyine.music.data.NeteaseMessage
import com.olbyine.music.data.NeteasePlaylistSummary
import com.olbyine.music.data.NeteaseSong
import com.olbyine.music.data.NeteaseTarget
import com.olbyine.music.data.NeteaseThread
import com.olbyine.music.data.NeteaseWikiContribution
import com.olbyine.music.data.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val NcmRed = Color(0xFFE5303C)
private val chatTime = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)

internal val LocalNeteaseLinks = staticCompositionLocalOf<((NeteaseTarget) -> Unit)?> { null }

internal val LocalNeteaseChat = staticCompositionLocalOf<((NeteaseThread) -> Unit)?> { null }

internal val LocalOpenArtist = staticCompositionLocalOf<((Long?, String) -> Unit)?> { null }

internal val LocalOpenArtistWith = staticCompositionLocalOf<((Long?, String, List<Pair<Long, String>>) -> Unit)?> { null }

@Composable
internal fun NeteaseChatScreen(client: NeteaseClient, thread: NeteaseThread, onBack: () -> Unit) {
    val messages = remember(thread.userId) { mutableStateListOf<NeteaseMessage>() }
    var loading by remember(thread.userId) { mutableStateOf(true) }
    var error by remember(thread.userId) { mutableStateOf<String?>(null) }
    var draft by rememberSaveable(thread.userId) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val links = LocalNeteaseLinks.current
    val openProfile = LocalNeteaseProfile.current
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    LaunchedEffect(thread.userId) {
        while (true) {
            try {
                val fresh = client.messageHistory(thread.userId)
                val grew = fresh.size != messages.size || fresh.lastOrNull()?.time != messages.lastOrNull()?.time
                if (grew) {
                    messages.clear()
                    messages += fresh
                    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
                }
                error = null
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                if (messages.isEmpty()) error = failure.message ?: "加载失败"
            }
            loading = false
            delay(8_000)
        }
    }
    LaunchedEffect(imeBottom > 0) {
        if (imeBottom > 0 && messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        val (status, _) = rememberLoad(thread.userId) { client.userStatus(thread.userId) }
        val material = LocalStyleSpec.current.style == com.olbyine.music.data.UiStyle.MATERIAL
        Row(
            Modifier.fillMaxWidth()
                .background(if (material) colors.surfaceContainer else colors.background.copy(alpha = .96f))
                .drawBehind {
                    if (!material) drawRect(colors.onSurface.copy(alpha = .08f), topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 1f),
                        size = androidx.compose.ui.geometry.Size(size.width, 1f))
                }
                .statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OlbyineIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回", onBack, size = 40.dp, iconSize = 20.dp)
            Spacer(Modifier.width(10.dp))
            Box {
                UrlImage(thread.avatarUrl, Modifier.size(38.dp).clip(CircleShape).background(colors.surfaceContainerHighest).clickable { openProfile?.invoke(thread.userId) })
                if (status.value != null) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(11.dp).clip(CircleShape).background(colors.background).padding(2.dp)
                            .clip(CircleShape).background(Color(0xFF34C759)),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(thread.nickname, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                androidx.compose.animation.AnimatedVisibility(status.value != null) {
                    Text(status.value.orEmpty(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            GhostIconButton(Icons.Rounded.Person, "主页", { openProfile?.invoke(thread.userId) }, size = 40.dp, iconSize = 20.dp)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val bubbleMax = maxWidth * .78f
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (messages.isEmpty()) item(key = "state") { LoadingOrError(loading, error, {}, itemMotion()) }
                itemsIndexed(messages, key = { index, it -> "$index:${it.time}" }) { index, message ->
                    Column(itemMotion()) {
                        val previous = messages.getOrNull(index - 1)
                        if (previous == null || message.time - previous.time > 10 * 60_000L) {
                            Text(
                                chatTime.format(Date(message.time)),
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant.copy(alpha = .7f),
                            )
                        }
                        ChatRow(message, thread, bubbleMax, onCard = { card -> card.target?.let { target -> links?.invoke(target) } })
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (material) colors.surfaceContainer else colors.background.copy(alpha = .96f))
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f).heightIn(min = 42.dp).clip(RoundedCornerShape(21.dp)).background(colors.onSurface.copy(alpha = .07f)).padding(horizontal = 16.dp, vertical = 11.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                maxLines = 5,
                decorationBox = { inner ->
                    Box {
                        if (draft.isEmpty()) Text("发送私信…", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant.copy(alpha = .7f))
                        inner()
                    }
                },
            )
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
                                client.sendMessage(thread.userId, text)
                                messages += NeteaseMessage(true, text, System.currentTimeMillis())
                                draft = ""
                                listState.animateScrollToItem(messages.lastIndex)
                            } catch (failure: Throwable) {
                                if (failure is CancellationException) throw failure
                                error = failure.message ?: "发送失败"
                            }
                            sending = false
                        }
                    }
                },
                size = 44.dp,
                container = colors.primary,
                tint = colors.onPrimary,
                enabled = draft.isNotBlank() && !sending,
            )
        }
    }
}

@Composable
private fun ChatRow(message: NeteaseMessage, thread: NeteaseThread, bubbleMax: Dp, onCard: (NeteaseCard) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
        if (!message.fromMe) {
            UrlImage(thread.avatarUrl, Modifier.size(34.dp).clip(CircleShape).background(colors.surfaceContainerHighest))
            Spacer(Modifier.width(8.dp))
        }
        var menu by remember { mutableStateOf(false) }
        val context = LocalContext.current
        val copy: (String, String) -> Unit = { label, value ->
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, value))
            menu = false
        }
        Column(
            Modifier.widthIn(max = bubbleMax).combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { message.card?.let(onCard) },
                onLongClick = { menu = true },
            ),
            horizontalAlignment = if (message.fromMe) Alignment.End else Alignment.Start,
        ) {
            OlbyineDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (message.text.isNotBlank()) GlassMenuItem("复制文字", { copy("私信", message.text) }, icon = Icons.Rounded.ContentCopy)
                message.card?.target?.let { target ->
                    if (target is NeteaseTarget.Web) GlassMenuItem("复制链接", { copy("链接", target.url) }, icon = Icons.Rounded.Link)
                }
                if (message.raw.isNotBlank()) GlassMenuItem("复制原始数据", { copy("消息数据", message.raw) }, icon = Icons.Rounded.ContentCopy)
            }
            if (message.text.isNotBlank()) {
                val (fill, ink) = chatBubbleColors(message.fromMe)
                val corner = when (LocalStyleSpec.current.style) {
                    com.olbyine.music.data.UiStyle.IOS -> 19.dp
                    com.olbyine.music.data.UiStyle.HYPEROS -> 20.dp
                    com.olbyine.music.data.UiStyle.MATERIAL -> 20.dp
                    else -> 16.dp
                }
                val tail = if (LocalStyleSpec.current.style == com.olbyine.music.data.UiStyle.MATERIAL) 4.dp else 6.dp
                Text(
                    message.text,
                    Modifier
                        .clip(RoundedCornerShape(topStart = corner, topEnd = corner, bottomStart = if (message.fromMe) corner else tail, bottomEnd = if (message.fromMe) tail else corner))
                        .background(fill)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    color = ink,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            message.card?.let { card ->
                if (message.text.isNotBlank()) Spacer(Modifier.height(6.dp))
                MessageCard(card, Modifier.widthIn(min = 220.dp), onClick = { onCard(card) })
            }
        }
    }
}

@Composable
private fun chatBubbleColors(fromMe: Boolean): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    return when (LocalStyleSpec.current.style) {
        com.olbyine.music.data.UiStyle.IOS ->
            if (fromMe) (if (dark) Color(0xFF3A3A3C) else Color(0xFF1C1C1E)) to Color.White
            else (if (dark) Color(0xFF262629) else Color(0xFFE9E9EB)) to colors.onSurface
        com.olbyine.music.data.UiStyle.MATERIAL ->
            if (fromMe) colors.primaryContainer to colors.onPrimaryContainer else colors.surfaceContainerHigh to colors.onSurface
        else ->
            if (fromMe) colors.primary to colors.onPrimary
            else (if (dark) Color.White.copy(alpha = .10f) else Color.White) to colors.onSurface
    }
}

@Composable
private fun MessageCard(card: NeteaseCard, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val wide = card.label == "通知" && card.imageUrl != null
    val (fill, _) = chatBubbleColors(false)
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(fill),
    ) {
        if (wide) {
            UrlImage(card.imageUrl, Modifier.fillMaxWidth().aspectRatio(2f).background(colors.surfaceContainerHighest))
        }
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!wide) {
                UrlImage(card.imageUrl?.let { if (it.contains("?")) it else "$it?param=160y160" }, Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).background(colors.surfaceContainerHighest)) {
                    StyledIcon(if (card.label == "一起听") Icons.Rounded.Groups else Icons.Rounded.Link, null, Modifier.padding(14.dp), tint = colors.onSurfaceVariant)
                }
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f, fill = false)) {
                Text(card.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (card.subtitle.isNotBlank()) {
                    Text(card.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(card.label, color = NcmRed, filled = true)
                    if (card.target != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when (card.target) {
                                is NeteaseTarget.Song -> "点击播放"
                                is NeteaseTarget.ListenTogether -> "点击加入"
                                is NeteaseTarget.Web -> "点击打开"
                                else -> "点击查看"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary,
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun NeteaseWebScreen(client: NeteaseClient, url: String, onBack: () -> Unit, backEnabled: Boolean = true) {
    val context = LocalContext.current
    val links = LocalNeteaseLinks.current
    var title by remember(url) { mutableStateOf("") }
    var progress by remember(url) { mutableStateOf(0) }
    var web by remember { mutableStateOf<WebView?>(null) }
    BackHandler(enabled = backEnabled) {
        val view = web
        if (view != null && view.canGoBack()) view.goBack() else onBack()
    }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background).clickable(remember { MutableInteractionSource() }, indication = null) {}) {
        Row(Modifier.fillMaxWidth().background(colors.surfaceContainer).statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OlbyineIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回", onBack, size = 40.dp, iconSize = 20.dp)
            Spacer(Modifier.width(10.dp))
            Text(title.ifBlank { "网易云音乐" }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            GhostIconButton(Icons.Rounded.Refresh, "刷新", { web?.reload() }, size = 40.dp, iconSize = 20.dp)
            GhostIconButton(Icons.AutoMirrored.Rounded.OpenInNew, "在浏览器打开", { openExternal(context, web?.url ?: url) }, size = 40.dp, iconSize = 20.dp)
        }
        if (progress in 1..99) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(colors.surfaceContainerHighest)) {
                Box(Modifier.fillMaxWidth(progress / 100f).height(2.dp).background(colors.primary))
            }
        }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    if (desktopPage(url)) {
                        settings.userAgentString = DESKTOP_UA
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                    }
                    val cookies = CookieManager.getInstance()
                    cookies.setAcceptCookie(true)
                    cookies.setAcceptThirdPartyCookies(this, true)
                    client.webCookie()?.let { value ->
                        cookies.setCookie("https://music.163.com", "MUSIC_U=$value; Domain=.music.163.com; Path=/")
                        cookies.setCookie("https://music.163.com", "os=android; Domain=.music.163.com; Path=/")
                    }
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                        override fun onReceivedTitle(view: WebView, value: String?) { title = value.orEmpty() }

                        override fun onJsPrompt(view: WebView, url: String?, message: String?, defaultValue: String?, result: android.webkit.JsPromptResult): Boolean {
                            val text = message.orEmpty().trim()
                            if (text.startsWith("{") && text.contains("\"method\"")) {
                                result.cancel()
                                return true
                            }
                            return false
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val target = request.url.toString()
                            val scheme = request.url.scheme?.lowercase()
                            if (scheme == "http" || scheme == "https") return false
                            val resolved = com.olbyine.music.data.NeteaseLinks.resolve(target)
                            if (resolved != null && resolved !is NeteaseTarget.Web) links?.invoke(resolved)
                            return true
                        }
                    }
                    loadUrl(url)
                    web = this
                }
            },
            onRelease = { it.destroy() },
        )
    }
}

private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

private fun desktopPage(url: String): Boolean {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    return uri.host.equals("music.163.com", ignoreCase = true) && (url.contains("/#/") || url.contains("/st/wiki") || url.contains("wiki"))
}

internal fun openExternal(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private enum class ArtistTab(val label: String) { SONGS("热门歌曲"), ALBUMS("专辑"), ABOUT("艺人介绍") }

@Composable
internal fun NeteaseArtistScreen(
    client: NeteaseClient,
    artistId: Long?,
    artistName: String,
    currentTrackId: String?,
    onBack: () -> Unit,
    onPlay: (List<NeteaseSong>, Int) -> Unit,
    onOpenAlbum: (NeteasePlaylistSummary) -> Unit,
    onAccount: (Long) -> Unit = {},
    relatedArtists: List<Pair<Long, String>> = emptyList(),
    onOpenArtist: (Long?, String) -> Unit = { _, _ -> },
) {
    val (resolved, retryId) = rememberLoad(artistId, artistName) { artistId ?: client.findArtist(artistName)?.id ?: error("网易云上没有找到「$artistName」") }
    val id = resolved.value
    val (home, retryHome) = rememberLoad(id) { id?.let { client.artistHome(it) } }
    val (songs, _) = rememberLoad(id) { id?.let { client.artistTopSongs(it) }.orEmpty() }
    val (albums, _) = rememberLoad(id) { id?.let { client.artistAlbums(it) }.orEmpty() }
    var tab by rememberSaveable(id) { mutableStateOf(ArtistTab.SONGS) }
    val roster = remember(relatedArtists, artistName) {
        (listOf<Pair<Long?, String>>(artistId to artistName) + relatedArtists.map { it.first as Long? to it.second })
            .filter { it.second.isNotBlank() }
            .distinctBy { it.second }
    }
    val rosterIndex = remember(roster, artistName) { roster.indexOfFirst { it.second == artistName }.coerceAtLeast(0) }
    val hasSiblings = roster.size > 1
    val colors = MaterialTheme.colorScheme
    val artist = home.value
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    Box(Modifier.fillMaxSize().background(colors.background).clickable(remember { MutableInteractionSource() }, indication = null) {}) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
            item(key = "head") {
                Box(Modifier.fillMaxWidth().clipToBounds()) {
                    Box(Modifier.fillMaxWidth().height(260.dp).parallaxHeader(listState)) {
                        UrlImage(artist?.coverUrl ?: artist?.avatarUrl, Modifier.fillMaxWidth().height(260.dp).background(colors.surfaceContainerHighest))
                        Box(Modifier.fillMaxWidth().height(260.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .35f), Color.Transparent, colors.background))))
                    }
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 176.dp)) {
                        UrlImage(artist?.avatarUrl, Modifier.size(92.dp).clip(CircleShape).border(2.dp, colors.background, CircleShape).background(colors.surfaceContainerHighest)) {
                            StyledIcon(Icons.Rounded.Person, null, Modifier.padding(24.dp), tint = colors.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(artist?.name ?: artistName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!artist?.alias.isNullOrBlank()) Text(artist?.alias.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        if (!artist?.identity.isNullOrBlank()) Text(artist?.identity.orEmpty(), style = MaterialTheme.typography.bodySmall, color = NcmRed)
                        if (artist != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                listOfNotNull(
                                    artist.songCount.takeIf { it > 0 }?.let { "单曲 $it" },
                                    artist.albumCount.takeIf { it > 0 }?.let { "专辑 $it" },
                                    artist.mvCount.takeIf { it > 0 }?.let { "MV $it" },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = resolved.error != null || home.error != null,
                            enter = androidx.compose.animation.fadeIn(tween(200)) + androidx.compose.animation.expandVertically(tween(220, easing = OlbyineTokens.Motion.easeOut)),
                            exit = androidx.compose.animation.fadeOut(tween(100)) + androidx.compose.animation.shrinkVertically(tween(180, easing = OlbyineTokens.Motion.easeOut)),
                        ) {
                            LoadingOrError(false, resolved.error ?: home.error, { retryId(); retryHome() })
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton("播放热门歌曲", { songs.value?.takeIf { it.isNotEmpty() }?.let { onPlay(it, 0) } }, icon = Icons.Rounded.PlayArrow, enabled = !songs.value.isNullOrEmpty(), height = 40.dp)
                            val account = artist?.accountUserId ?: 0L
                            if (account > 0) PillButton("个人主页", { onAccount(account) }, icon = Icons.Rounded.Person, style = PillStyle.TONAL, height = 40.dp)
                        }
                    }
                    Box(Modifier.statusBarsPadding().padding(10.dp)) {
                        OlbyineIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回", onBack, size = 40.dp, iconSize = 20.dp)
                    }
                }
            }
            item(key = "tabs") {
                PillTabs(ArtistTab.entries.map { it to it.label }, tab, { tab = it }, Modifier.padding(top = 14.dp, bottom = 6.dp))
            }
            when (tab) {
                ArtistTab.SONGS -> {
                    val list = songs.value.orEmpty()
                    if (list.isEmpty() && (songs.loading || resolved.loading)) skeletonRows(8)
                    else if (list.isEmpty()) item(key = "songs-empty") { LoadingOrError(false, songs.error, {}, itemMotion()) }
                    itemsIndexed(list, key = { index, it -> "s$index:${it.id}" }) { index, song ->
                        Box(itemMotion().padding(horizontal = 10.dp)) {
                            NeteaseSongRow(song, current = currentTrackId == NeteaseSong.trackId(song.id)) { onPlay(list, index) }
                        }
                    }
                }
                ArtistTab.ALBUMS -> {
                    val list = albums.value.orEmpty()
                    if (list.isEmpty() && (albums.loading || resolved.loading)) {
                        items(3, key = { "album-skeleton-$it" }) {
                            Row(itemMotion().fillMaxWidth().padding(horizontal = 10.dp)) { SkeletonCard(Modifier.weight(1f)); SkeletonCard(Modifier.weight(1f)) }
                        }
                    } else if (list.isEmpty()) item(key = "albums-empty") { LoadingOrError(false, albums.error, {}, itemMotion()) }
                    val rows = list.chunked(2)
                    itemsIndexed(rows, key = { index, _ -> "a$index" }) { _, row ->
                        Row(itemMotion().fillMaxWidth().padding(horizontal = 10.dp)) {
                            row.forEach { album -> NeteaseCoverCard(album, { onOpenAlbum(album) }, Modifier.weight(1f)) }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                ArtistTab.ABOUT -> item(key = "about") {
                    Column(itemMotion().padding(horizontal = 20.dp, vertical = 8.dp)) {
                        if (artist == null) {
                            LoadingOrError(home.loading, home.error, retryHome)
                        } else {
                            if (artist.briefDesc.isNotBlank()) {
                                Text("简介", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(6.dp))
                                Text(artist.briefDesc, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface.copy(alpha = .86f))
                                Spacer(Modifier.height(18.dp))
                            }
                            artist.sections.forEach { (heading, text) ->
                                if (heading.isNotBlank()) {
                                    Text(heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(6.dp))
                                }
                                Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface.copy(alpha = .86f))
                                Spacer(Modifier.height(18.dp))
                            }
                            if (artist.briefDesc.isBlank() && artist.sections.isEmpty()) {
                                Text("网易云上还没有这位艺人的介绍", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                            val siblings = roster.filter { it.second != artistName }
                            if (siblings.isNotEmpty()) {
                                Spacer(Modifier.height(18.dp))
                                Text("合作艺人", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                androidx.compose.foundation.layout.FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    siblings.forEach { artistEntry ->
                                        Box(
                                            Modifier.clip(RoundedCornerShape(50)).background(colors.onSurface.copy(alpha = .08f))
                                                .clickable { onOpenArtist(artistEntry.first, artistEntry.second) }
                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                        ) {
                                            Text(artistEntry.second, style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item(key = "bottom") { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SongWikiSection(
    client: NeteaseClient,
    track: Track,
    resolveId: suspend (Track) -> Long?,
    onContribute: (Long) -> Unit,
    onHistory: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val state by produceState<Triple<Long?, List<Pair<String, String>>, List<com.olbyine.music.data.NeteaseWikiSection>>?>(null, track.id) {
        value = runCatching {
            val id = resolveId(track)
            if (id == null) Triple(null, emptyList(), emptyList())
            else Triple(id, runCatching { client.songFacts(id) }.getOrDefault(emptyList()), runCatching { client.songWiki(id) }.getOrDefault(emptyList()))
        }.getOrElse { Triple(null, emptyList(), emptyList()) }
    }
    Column(modifier.fillMaxWidth()) {
        Overline("歌曲百科", color = ink.copy(alpha = .6f), modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = .08f))
                .border(0.7.dp, Color.White.copy(alpha = .1f), RoundedCornerShape(20.dp)).padding(16.dp),
        ) {
            SwapContent(state, Modifier.fillMaxWidth()) { current ->
            Column(Modifier.fillMaxWidth()) {
            when {
                current == null -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    OlbyineSpinner(Modifier.size(24.dp), strokeWidth = 2.dp, color = ink.copy(alpha = .7f))
                }
                current.first == null -> Text("网易云上没有找到这首歌，暂时没有百科", style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = .55f))
                else -> {
                    if (current.second.isEmpty() && current.third.isEmpty()) {
                        Text(
                            if (client.signedIn) "这首歌还没有百科内容，来做第一个共建者吧" else "这首歌暂无公开百科；登录网易云后可看到作词、作曲等资料",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ink.copy(alpha = .55f),
                        )
                    }
                    current.second.forEach { (label, value) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(label, Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = .55f))
                            Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = .9f))
                        }
                    }
                    current.third.filterNot { isContributors(it.title) }.forEach { section ->
                        Spacer(Modifier.height(12.dp))
                        if (section.title.isNotBlank()) {
                            Text(section.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = ink)
                            Spacer(Modifier.height(6.dp))
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            section.entries.forEach { entry ->
                                Row(
                                    Modifier.clip(RoundedCornerShape(12.dp)).background(ink.copy(alpha = .08f)).padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (entry.imageUrl != null) {
                                        UrlImage(entry.imageUrl, Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)))
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Column {
                                        if (entry.title.isNotBlank()) Text(entry.title, style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = .6f))
                                        if (entry.text.isNotBlank()) Text(entry.text, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = .92f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val id = state?.first
                WikiChip("参与百科共建", Icons.Rounded.Edit, enabled = id != null) { id?.let(onContribute) }
                WikiChip("共建记录", Icons.Rounded.History, enabled = true) { onHistory(id) }
            }
        }
    }
}

@Composable
private fun WikiChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    Row(
        Modifier.height(34.dp).clip(CircleShape).background(ink.copy(alpha = if (enabled) .12f else .05f)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(15.dp), tint = ink.copy(alpha = if (enabled) .9f else .4f))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = if (enabled) .9f else .4f))
    }
}

private val WikiKinds = listOf(3 to "歌曲", 1 to "歌手", 2 to "专辑", 5 to "逐字歌词", 6 to "歌词翻译", 4 to "MV", 101 to "补充专辑", 103 to "补充 MV")

private fun wikiStatus(status: Int): Pair<String, Color> = when (status) {
    0 -> "待审核" to Color(0xFF8E8E93)
    1 -> "审核中" to Color(0xFF0A84FF)
    4 -> "部分通过" to Color(0xFFFF9F0A)
    5 -> "已通过" to Color(0xFF30D158)
    -5 -> "未采纳" to Color(0xFFFF453A)
    else -> "状态 $status" to Color(0xFF8E8E93)
}

internal fun isContributors(title: String) = listOf("共建", "贡献", "编辑", "记录").any { title.contains(it) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WikiHistorySheet(client: NeteaseClient, songId: Long?, onDismiss: () -> Unit) {
    var songTab by rememberSaveable { mutableStateOf(songId != null) }
    if (songId != null && songTab) {
        val sections by produceState<List<com.olbyine.music.data.NeteaseWikiSection>?>(null, songId) {
            value = runCatching { client.songWiki(songId) }.getOrDefault(emptyList()).filter { isContributors(it.title) }
        }
        val colors = MaterialTheme.colorScheme
        GlassBottomSheet(onDismissRequest = onDismiss) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text("共建记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                PillTabs(listOf(true to "本曲共建", false to "我的共建"), songTab, { songTab = it }, contentPadding = PaddingValues(0.dp))
                Spacer(Modifier.height(10.dp))
                val list = sections
                when {
                    list == null -> Column(Modifier.fillMaxWidth()) { repeat(3) { SkeletonRow(avatar = true) } }
                    list.isEmpty() -> Text(
                        "网易云还没有公开这首歌的共建记录（或者还没有人参与共建）。可以点「参与百科共建」成为第一个共建者。",
                        Modifier.padding(vertical = 24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                        list.forEach { section ->
                            if (section.title.isNotBlank()) item(key = "title:${section.title}") { Overline(section.title, itemMotion().padding(top = 10.dp, bottom = 4.dp)) }
                            itemsIndexed(section.entries, key = { index, it -> "${section.title}:$index:${it.title}" }) { _, entry ->
                                Row(itemMotion().fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    UrlImage(entry.imageUrl, Modifier.size(38.dp).clip(CircleShape).background(colors.surfaceContainerHighest))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        if (entry.title.isNotBlank()) Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (entry.text.isNotBlank()) Text(entry.text, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(16.dp))
            }
        }
        return
    }
    MyWikiHistory(client, onDismiss, onSongTab = if (songId != null) ({ songTab = true }) else null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyWikiHistory(client: NeteaseClient, onDismiss: () -> Unit, onSongTab: (() -> Unit)?) {
    var kind by rememberSaveable { mutableStateOf(3) }
    val rows = remember(kind) { mutableStateListOf<NeteaseWikiContribution>() }
    var total by remember(kind) { mutableStateOf(0) }
    var loading by remember(kind) { mutableStateOf(true) }
    var error by remember(kind) { mutableStateOf<String?>(null) }
    var page by remember(kind) { mutableStateOf(0) }
    val devote by produceState<Pair<Int, Int>?>(null) { value = client.wikiDevote() }
    LaunchedEffect(kind, page) {
        loading = true
        try {
            val (list, count) = client.wikiContributions(kind, rows.size)
            rows += list
            total = count
            error = null
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            error = failure.message ?: "加载失败"
        }
        loading = false
    }
    val colors = MaterialTheme.colorScheme
    GlassBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("共建记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (onSongTab != null) {
                Spacer(Modifier.height(10.dp))
                PillTabs(listOf(true to "本曲共建", false to "我的共建"), false, { if (it) onSongTab() }, contentPadding = PaddingValues(0.dp))
                Spacer(Modifier.height(6.dp))
            }
            Text(
                devote?.let { (count, shells) -> "累计贡献 $count 次 · 获得云贝 $shells" } ?: if (client.signedIn) "你在网易云音乐百科提交过的修改和审核结果" else "登录网易云后查看",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
        }
        PillTabs(WikiKinds, kind, { kind = it }, contentPadding = PaddingValues(horizontal = 20.dp))
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 520.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)) {
            if (rows.isEmpty()) {
                if (loading) skeletonRows(5)
                else item(key = "state") {
                    if (error != null) LoadingOrError(false, error, { page++ }, itemMotion())
                    else Text("还没有这类贡献", itemMotion().fillMaxWidth().padding(28.dp), textAlign = TextAlign.Center, color = colors.onSurfaceVariant)
                }
            }
            itemsIndexed(rows, key = { index, it -> "$kind:$index:${it.time}" }) { _, row ->
                val (label, tint) = wikiStatus(row.status)
                Row(itemMotion().fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.name.ifBlank { "（未命名）" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(row.artists.takeIf(String::isNotBlank), row.time.takeIf { it > 0 }?.let { chatTime.format(Date(it)) }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Badge(label, color = tint, filled = true)
                }
            }
            if (rows.isNotEmpty() && rows.size < total) {
                item {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        if (loading) OlbyineSpinner(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else PillButton("加载更多", { page++ }, style = PillStyle.TONAL, height = 36.dp)
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
}

@Composable
private fun FriendPicker(client: NeteaseClient, selected: MutableList<Long>, modifier: Modifier = Modifier) {
    val (friends, retry) = rememberLoad(Unit) {
        val recent = runCatching { client.messageThreads() }.getOrDefault(emptyList()).map { NeteaseFriend(it.userId, it.nickname, it.avatarUrl) }
        val follows = runCatching { client.follows() }.getOrDefault(emptyList())
        (recent + follows).distinctBy { it.userId }.filter { it.userId > 0 }
    }
    val list = friends.value.orEmpty()
    val colors = MaterialTheme.colorScheme
    LazyColumn(modifier.fillMaxWidth().heightIn(max = 300.dp)) {
        if (list.isEmpty() && friends.loading) skeletonRows(4, avatar = true)
        else if (list.isEmpty()) item(key = "state") { LoadingOrError(false, friends.error ?: "还没有关注的人或私信", retry, itemMotion()) }
        itemsIndexed(list, key = { index, it -> "$index:${it.userId}" }) { _, friend ->
            val checked = friend.userId in selected
            Row(
                itemMotion().fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { if (checked) selected.remove(friend.userId) else selected.add(friend.userId) }.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UrlImage(friend.avatarUrl, Modifier.size(40.dp).clip(CircleShape).background(colors.surfaceContainerHighest))
                Spacer(Modifier.width(12.dp))
                Text(friend.nickname, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                AnimatedTick(checked, NcmRed)
            }
        }
    }
}

private enum class ShareMode { CARD, FRIENDS, FEED }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ShareSongSheet(
    track: Track,
    lyricLine: String?,
    client: NeteaseClient,
    resolveId: suspend (Track) -> Long?,
    onMessage: (String) -> Unit,
    onDismiss: () -> Unit,
    soundCloud: com.olbyine.music.data.SoundCloudClient? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scId = track.id.takeIf { it.startsWith("sc:") }?.removePrefix("sc:")?.toLongOrNull()
    val songId by produceState<Long?>(null, track.id) { value = if (scId != null) null else runCatching { resolveId(track) }.getOrNull() }
    val scLink by produceState<String?>(null, scId) {
        value = scId?.let { id -> runCatching { soundCloud?.trackInfo(id)?.first?.permalink }.getOrNull()?.takeIf { it.startsWith("http") } }
    }
    val link = if (scId != null) scLink else songId?.let(client::songLink)
    val card by produceState<android.graphics.Bitmap?>(null, track.id, link, lyricLine) {
        value = withContext(Dispatchers.IO) { runCatching { ShareCard.render(context, track, lyricLine, link) }.getOrNull() }
    }
    var mode by remember { mutableStateOf(ShareMode.CARD) }
    var picked by remember { mutableStateOf(false) }
    LaunchedEffect(songId) { if (songId != null && client.signedIn && !picked) mode = ShareMode.FRIENDS }
    val me = client.account.value?.userId
    val officialText = if (scId != null) scLink?.let { "${track.title} - ${track.artist}  $it" }
        else songId?.let { id -> "分享${track.artist}的单曲《${track.title}》: https://music.163.com/song?id=$id" + (me?.let { "&userid=$it" } ?: "") + " (来自@网易云音乐)" }
    val selected = remember { mutableStateListOf<Long>() }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    GlassBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Text("分享歌曲", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("${track.title} · ${track.artist}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            val modes = buildList {
                if (client.signedIn && songId != null) {
                    add(ShareMode.FRIENDS to "网易云好友")
                    add(ShareMode.FEED to "网易云动态")
                }
                add(ShareMode.CARD to "链接与图片")
            }
            if (modes.size > 1) PillTabs(modes, mode, { picked = true; mode = it }, contentPadding = PaddingValues(0.dp))
            Spacer(Modifier.height(12.dp))
            when (mode) {
                ShareMode.CARD -> {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val bitmap = card
                        if (bitmap == null) {
                            Box(Modifier.fillMaxWidth(.62f).aspectRatio(.75f).clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                                OlbyineSpinner(Modifier.size(26.dp), strokeWidth = 2.5.dp)
                            }
                        } else {
                            Image(bitmap.asImageBitmap(), "分享卡片", Modifier.fillMaxWidth(.62f).aspectRatio(bitmap.width / bitmap.height.toFloat()).clip(RoundedCornerShape(18.dp)))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("分享图片", {
                            card?.let { bitmap ->
                                scope.launch {
                                    val uri = withContext(Dispatchers.IO) { runCatching { ShareCard.save(context, bitmap, track.title) }.getOrNull() }
                                    if (uri == null) onMessage("图片保存失败") else runCatching {
                                        context.startActivity(
                                            Intent.createChooser(
                                                Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
                                                    .apply { link?.let { putExtra(Intent.EXTRA_TEXT, "${track.title} - ${track.artist} $it") } }
                                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                                "分享歌曲",
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }
                            }
                        }, icon = Icons.Rounded.Share, enabled = card != null, height = 40.dp)
                        PillButton("保存到相册", {
                            card?.let { bitmap ->
                                scope.launch {
                                    val uri = withContext(Dispatchers.IO) { runCatching { ShareCard.save(context, bitmap, track.title) }.getOrNull() }
                                    onMessage(if (uri != null) "已保存到 图片/Olbyine" else "保存失败")
                                }
                            }
                        }, icon = Icons.Rounded.Download, style = PillStyle.TONAL, enabled = card != null, height = 40.dp)
                        if (officialText != null) {
                            PillButton("分享链接", {
                                runCatching {
                                    context.startActivity(
                                        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, officialText), "分享歌曲")
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            }, icon = Icons.Rounded.Link, style = PillStyle.TONAL, height = 40.dp)
                        }
                        if (link != null) {
                            PillButton("复制链接", {
                                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("歌曲链接", officialText ?: "${track.title} - ${track.artist} $link"))
                                onMessage("已复制歌曲链接")
                            }, icon = Icons.Rounded.ContentCopy, style = PillStyle.TONAL, height = 40.dp)
                        }
                    }
                    if (songId == null && scId == null) {
                        Spacer(Modifier.height(8.dp))
                        Text("没有在网易云找到这首歌，卡片里不带收听二维码", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                }
                ShareMode.FRIENDS -> {
                    Text("以网易云官方歌曲卡片发送，对方在网易云里点开即可播放", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    FriendPicker(client, selected)
                    Spacer(Modifier.height(8.dp))
                    OlbyineTextField(note, { note = it }, "附言（可不填）")
                    Spacer(Modifier.height(10.dp))
                    PillButton(if (busy) "发送中…" else "发送给 ${selected.size} 位好友", {
                        val id = songId ?: return@PillButton
                        busy = true
                        scope.launch {
                            try {
                                client.shareSongToFriends(id, selected.toList(), note.trim())
                                onMessage("已分享给 ${selected.size} 位好友")
                                onDismiss()
                            } catch (failure: Throwable) {
                                if (failure is CancellationException) throw failure
                                onMessage(failure.message ?: "分享失败")
                            }
                            busy = false
                        }
                    }, icon = Icons.AutoMirrored.Rounded.Send, enabled = selected.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth())
                }
                ShareMode.FEED -> {
                    OlbyineTextField(note, { if (it.length <= 140) note = it }, "说点什么（最多 140 字）", minLines = 3)
                    Spacer(Modifier.height(10.dp))
                    PillButton(if (busy) "发布中…" else "分享到我的动态", {
                        val id = songId ?: return@PillButton
                        busy = true
                        scope.launch {
                            try {
                                client.shareSongToFeed(id, note.trim())
                                onMessage("已分享到网易云动态")
                                onDismiss()
                            } catch (failure: Throwable) {
                                if (failure is CancellationException) throw failure
                                onMessage(failure.message ?: "分享失败")
                            }
                            busy = false
                        }
                    }, icon = Icons.Rounded.Share, enabled = !busy, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListenTogetherSheet(
    state: MainViewModel.ListenTogetherUiState,
    client: NeteaseClient,
    currentSongId: Long?,
    onCreate: () -> Unit,
    onJoin: (String) -> Unit,
    onLeave: () -> Unit,
    onLogin: () -> Unit,
    onMessage: (String) -> Unit,
    onDismiss: () -> Unit,
    onDismissSummary: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var historyOpen by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    var inviting by remember { mutableStateOf(false) }
    var syncRaw by remember { mutableStateOf<String?>(null) }
    var clock by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            clock = System.currentTimeMillis()
        }
    }
    val selected = remember { mutableStateListOf<Long>() }
    val colors = MaterialTheme.colorScheme
    val me = client.account.value?.userId
    GlassBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StyledIcon(Icons.Rounded.Groups, null, tint = NcmRed)
                Spacer(Modifier.width(8.dp))
                Text("网易云一起听", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "和好友同步收听网易云上的歌。双方切歌、暂停、拖动进度都会同步给对方；本地歌曲不会同步。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            val room = state.room
            when {
                !client.signedIn -> PillButton("登录网易云", onLogin, icon = Icons.Rounded.Person)
                room == null -> {
                    PillButton(if (state.busy) "请稍候…" else "创建房间并邀请好友", onCreate, icon = Icons.Rounded.Groups, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    Overline("收到邀请？")
                    Spacer(Modifier.height(6.dp))
                    OlbyineTextField(link, { link = it }, "粘贴一起听邀请链接")
                    Spacer(Modifier.height(8.dp))
                    PillButton("加入房间", { onJoin(link) }, style = PillStyle.TONAL, enabled = link.isNotBlank() && !state.busy)
                    Text("私信里收到的一起听邀请卡片，点一下也能直接加入。", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
                else -> {
                    val others = room.users.filter { it.userId != me }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        val mine = room.users.firstOrNull { it.userId == me }
                        RoomAvatar(mine?.avatarUrl ?: client.account.value?.avatarUrl, if (room.host) "我（房主）" else "我")
                        Text(if (others.isEmpty()) "  ···  " else "  ♪  ", style = MaterialTheme.typography.titleLarge, color = NcmRed)
                        if (others.isEmpty()) {
                            RoomAvatar(null, "等待加入")
                        } else {
                            others.forEach { user -> RoomAvatar(user.avatarUrl, user.nickname) }
                        }
                    }
                    val since = state.startedAt.takeIf { it > 0L }
                    if (since != null) {
                        val minutes = ((clock - since) / 60_000L).coerceAtLeast(0L)
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
                            Text(
                                if (minutes <= 0L) "一起听了 不到 1 分钟" else "一起听了 ${togetherSpan(minutes)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    if (room.host) {
                        val invite = client.listenInviteLink(room, currentSongId)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton("邀请好友", { inviting = !inviting }, icon = Icons.AutoMirrored.Rounded.Send, height = 40.dp)
                            PillButton("复制邀请链接", {
                                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("一起听", invite))
                                onMessage("已复制邀请链接")
                            }, icon = Icons.Rounded.ContentCopy, style = PillStyle.TONAL, height = 40.dp)
                        }
                        if (inviting) {
                            Spacer(Modifier.height(10.dp))
                            FriendPicker(client, selected)
                            PillButton("发送邀请", {
                                scope.launch {
                                    val ids = selected.toList()
                                    val sent = runCatching {
                                        if (currentSongId != null) client.shareSongToFriends(currentSongId, ids, "来和我一起听这首歌吧 $invite")
                                        else ids.forEach { client.sendMessage(it, "来和我一起听歌吧 $invite") }
                                    }
                                    onMessage(if (sent.isSuccess) "已邀请 ${ids.size} 位好友" else sent.exceptionOrNull()?.message ?: "邀请发送失败")
                                    inviting = false
                                    selected.clear()
                                }
                            }, icon = Icons.AutoMirrored.Rounded.Send, enabled = selected.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        }
                        if (currentSongId == null) {
                            Text("当前播放的不是网易云歌曲，房间里暂时没有内容。", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        }
                    } else {
                        Text("已和对方同步，双方都能切歌和暂停。", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    others.firstOrNull()?.let { partner ->
                        Spacer(Modifier.height(16.dp))
                        Overline("和 ${partner.nickname} 聊天")
                        Spacer(Modifier.height(6.dp))
                        RoomChat(client, partner)
                    }
                    Text(
                        "同步诊断",
                        Modifier.padding(top = 12.dp).clip(CircleShape).clickable {
                            val im = com.olbyine.music.data.ListenTogetherIm
                            val chat = im.diagnostics() + "\n房间 chatRoomId：${room.chatRoomId ?: "无"}"
                            syncRaw = chat + "\n\n" + (client.lastListenSyncRaw ?: "还没有拉到房间状态")
                        }.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    PillButton(if (room.host) "结束房间" else "退出房间", onLeave, style = PillStyle.OUTLINED, modifier = Modifier.fillMaxWidth())
                }
            }
            syncRaw?.let { raw ->
                OlbyineDialog(
                    onDismissRequest = { syncRaw = null },
                    title = "一起听同步诊断",
                    confirmButton = { GlassTextButton(onClick = { syncRaw = null }) { Text("关闭") } },
                    dismissButton = {
                        GlassTextButton(onClick = { copyRoomText(context, raw) }) {
                            Text("复制")
                        }
                    },
                ) {
                    Text(raw, style = MaterialTheme.typography.labelSmall, modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()))
                }
            }
            state.message?.let { Text(it, Modifier.padding(top = 10.dp), color = colors.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }

    state.summary?.let { summary ->
        OlbyineDialog(
            onDismissRequest = onDismissSummary,
            title = if (summary.selfLeft) "你已退出一起听" else "对方已退出一起听",
            confirmButton = { GlassTextButton(onClick = onDismissSummary) { Text("完成") } },
            dismissButton = {
                GlassTextButton(onClick = { historyOpen = !historyOpen }) {
                    Text(if (historyOpen) "收起记录" else "查看聊天记录")
                }
            },
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryStat("本次一起听了", "${summary.songs}", "首", Modifier.weight(1f))
                    SummaryStat("本次陪伴彼此", togetherSpan(summary.minutes), "", Modifier.weight(1f))
                }
                if (historyOpen) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "聊天记录",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.onSurface.copy(alpha = .04f))
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (summary.messages.isEmpty()) {
                            Text("这次没有聊天记录", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        summary.messages.forEach { chat ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (chat.mine) colors.primary.copy(alpha = .10f) else colors.surfaceContainerHighest.copy(alpha = .6f))
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        chat.nick.ifBlank { if (chat.mine) "我" else "对方" },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onSurface,
                                    )
                                    Text(
                                        " · " + togetherClock(chat.time),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(chat.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(colors.onSurface.copy(alpha = .05f)).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (unit.isNotBlank()) {
                Text(unit, Modifier.padding(start = 2.dp, bottom = 3.dp), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}
@Composable
private fun RoomAvatar(url: String?, name: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
        UrlImage(url, Modifier.size(56.dp).clip(CircleShape).border(2.dp, NcmRed.copy(alpha = .6f), CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            StyledIcon(Icons.Rounded.Person, null, Modifier.padding(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RoomChat(client: NeteaseClient, partner: NeteaseFriend) {
    val realtime by com.olbyine.music.data.ListenTogetherIm.connected.collectAsState()
    if (realtime) RoomChatRealtime() else RoomChatDirect(client, partner)
}

private fun togetherSpan(minutes: Long): String {
    val safe = minutes.coerceAtLeast(0L)
    val hours = safe / 60L
    val rest = safe % 60L
    return when {
        hours > 0L && rest > 0L -> "${hours} 小时 ${rest} 分钟"
        hours > 0L -> "${hours} 小时"
        rest > 0L -> "${rest} 分钟"
        else -> "不到 1 分钟"
    }
}

private fun togetherClock(time: Long): String {
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = time }
    return "%02d:%02d".format(calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE))
}

private fun copyRoomText(context: android.content.Context, text: String) {
    runCatching {
        val manager = context.applicationContext.getSystemService(android.content.ClipboardManager::class.java)
        manager?.setPrimaryClip(android.content.ClipData.newPlainText("一起听消息", text))
    }
    android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
}

@Composable
private fun RoomChatRealtime() {
    val messages by com.olbyine.music.data.ListenTogetherIm.messages.collectAsState()
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var menuFor by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.onSurface.copy(alpha = .05f)).padding(10.dp)) {
        Text(
            "一起听房间消息",
            Modifier.padding(start = 4.dp, bottom = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (messages.isEmpty()) item { Text("打个招呼吧", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            itemsIndexed(messages, key = { index, it -> "$index:${it.time}" }) { index, message ->
                val itemKey = "$index:${message.time}"
                Box(Modifier.fillMaxWidth(), contentAlignment = if (message.mine) Alignment.CenterEnd else Alignment.CenterStart) {
                    Column(horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start) {
                        if (!message.mine && message.nick.isNotBlank()) {
                            Text(message.nick, Modifier.padding(start = 6.dp, bottom = 2.dp), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        }
                        Text(
                            message.text,
                            Modifier.widthIn(max = 260.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (message.mine) colors.primary else colors.onSurface.copy(alpha = .08f))
                                .combinedClickable(onClick = {}, onLongClick = { menuFor = itemKey })
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            color = if (message.mine) colors.onPrimary else colors.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OlbyineDropdownMenu(expanded = menuFor == itemKey, onDismissRequest = { menuFor = null }) {
                            GlassMenuItem("复制", { menuFor = null; copyRoomText(context, message.text) }, icon = Icons.Rounded.ContentCopy)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { OlbyineTextField(draft, { draft = it }, "说点什么…") }
            Spacer(Modifier.width(8.dp))
            OlbyineIconButton(Icons.AutoMirrored.Rounded.Send, "发送", {
                val text = draft.trim()
                if (text.isNotEmpty()) {
                    com.olbyine.music.data.ListenTogetherIm.send(text)
                    draft = ""
                }
            }, size = 44.dp, container = colors.primary, tint = colors.onPrimary, enabled = draft.isNotBlank())
        }
    }
}

@Composable
private fun RoomChatDirect(client: NeteaseClient, partner: NeteaseFriend) {

    val messages = remember(partner.userId) { mutableStateListOf<NeteaseMessage>() }
    var draft by remember(partner.userId) { mutableStateOf("") }
    var menuFor by remember(partner.userId) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    LaunchedEffect(partner.userId) {
        while (true) {
            runCatching { client.messageHistory(partner.userId) }.getOrNull()?.takeLast(30)?.let { fresh ->
                if (fresh.size != messages.size || fresh.lastOrNull()?.time != messages.lastOrNull()?.time) {
                    messages.clear()
                    messages += fresh
                    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
                }
            }
            delay(4_000)
        }
    }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.onSurface.copy(alpha = .05f)).padding(10.dp)) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (messages.isEmpty()) item { Text("打个招呼吧", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            itemsIndexed(messages, key = { index, it -> "$index:${it.time}" }) { index, message ->
                val itemKey = "$index:${message.time}"
                val shown = message.text.ifBlank { message.card?.let { "[${it.label}] ${it.title}" }.orEmpty() }
                Box(Modifier.fillMaxWidth(), contentAlignment = if (message.fromMe) Alignment.CenterEnd else Alignment.CenterStart) {
                    Text(
                        shown,
                        Modifier.widthIn(max = 260.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (message.fromMe) colors.primary else colors.onSurface.copy(alpha = .08f))
                            .combinedClickable(onClick = {}, onLongClick = { menuFor = itemKey })
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        color = if (message.fromMe) colors.onPrimary else colors.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OlbyineDropdownMenu(expanded = menuFor == itemKey, onDismissRequest = { menuFor = null }) {
                        GlassMenuItem("复制", { menuFor = null; copyRoomText(context, shown) }, icon = Icons.Rounded.ContentCopy)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { OlbyineTextField(draft, { draft = it }, "说点什么…") }
            Spacer(Modifier.width(8.dp))
            OlbyineIconButton(Icons.AutoMirrored.Rounded.Send, "发送", {
                val text = draft.trim()
                if (text.isNotEmpty() && !sending) {
                    sending = true
                    scope.launch {
                        runCatching { client.sendMessage(partner.userId, text) }.onSuccess {
                            messages += NeteaseMessage(true, text, System.currentTimeMillis())
                            draft = ""
                            listState.animateScrollToItem(messages.lastIndex)
                        }
                        sending = false
                    }
                }
            }, size = 44.dp, container = colors.primary, tint = colors.onPrimary, enabled = draft.isNotBlank() && !sending)
        }
    }
}

@Composable
internal fun ListenTogetherBar(room: com.olbyine.music.data.NeteaseListenRoom, me: Long?, onClick: () -> Unit) {
    val others = room.users.filter { it.userId != me }
    Row(
        Modifier.clip(CircleShape).background(Color.White.copy(alpha = .14f)).clickable(onClick = onClick).padding(start = 6.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            room.users.take(3).forEachIndexed { index, user ->
                UrlImage(user.avatarUrl, Modifier.padding(start = (index * 16).dp).size(24.dp).clip(CircleShape).border(1.5.dp, Color.White.copy(alpha = .8f), CircleShape).background(Color.White.copy(alpha = .2f)))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (others.isEmpty()) "一起听 · 等待好友加入" else "正在和 ${others.joinToString("、") { it.nickname }} 一起听",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}