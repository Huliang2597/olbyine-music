package com.olbyine.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.olbyine.music.data.HapticArea
import com.olbyine.music.data.ImportProgress
import com.olbyine.music.data.MusicLibraryRepository
import com.olbyine.music.data.UiStyle
import com.olbyine.music.data.WebDavEntry
import com.olbyine.music.data.WebDavRepository
import com.olbyine.music.data.WebDavServer
import kotlinx.coroutines.launch
import java.net.URI
import java.util.Locale

internal val LocalOpenWebDav = staticCompositionLocalOf<(() -> Unit)?> { null }

private data class DavLocation(val server: WebDavServer, val trail: List<Pair<String, String>>) {
    val url: String get() = trail.last().second
}

@Composable
internal fun WebDavScreen(
    visible: Boolean,
    repository: WebDavRepository,
    importing: Boolean,
    importProgress: ImportProgress?,
    backEnabled: Boolean,
    onDismiss: () -> Unit,
    onImport: (WebDavServer, String) -> Unit,
    onPlay: (WebDavServer, List<WebDavEntry>, Int) -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        progress.animateTo(if (visible) 1f else 0f, spring(dampingRatio = 1f, stiffness = if (visible) 420f else 700f))
    }
    if (!visible && progress.value <= 0.001f) return

    val haptics = rememberHaptics()
    val servers by repository.servers.collectAsState()
    var location by remember { mutableStateOf<DavLocation?>(null) }
    var editor by remember { mutableStateOf<WebDavServer?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    val listings = remember { mutableStateMapOf<String, Result<List<WebDavEntry>>>() }
    var reload by remember { mutableIntStateOf(0) }

    if (visible) BackHandler(enabled = backEnabled) {
        val current = location
        when {
            current == null -> onDismiss()
            current.trail.size > 1 -> location = current.copy(trail = current.trail.dropLast(1))
            else -> location = null
        }
    }

    val colors = MaterialTheme.colorScheme
    val source = rememberBackdropSource()
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides colors.onBackground) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = (1f - progress.value) * size.width * .18f
                alpha = progress.value
            }
            .background(colors.background),
    ) {
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val barHeight = 64.dp + if (location != null) 46.dp else 0.dp
        val topPadding = statusTop + barHeight + 8.dp

        Box(Modifier.fillMaxSize().backdropSource(source)) {
            AnimatedContent(
                targetState = location,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                contentKey = { it?.url ?: "servers" },
                label = "webdav-page",
            ) { here ->
                if (here == null) {
                    ServerList(
                        servers = servers,
                        hasPassword = repository::hasPassword,
                        contentPadding = PaddingValues(top = topPadding, bottom = navBottom + 28.dp),
                        onOpen = { server ->
                            haptics.click(HapticArea.MENUS)
                            location = DavLocation(server, listOf(server.name to server.baseUrl))
                        },
                        onEdit = { server -> editor = server; editorOpen = true },
                        onAdd = { editor = null; editorOpen = true },
                    )
                } else {
                    val result = listings[here.url]
                    LaunchedEffect(here.url, reload) {
                        if (listings[here.url] == null || reload > 0) {
                            listings[here.url] = runCatching { repository.list(here.server, here.url) }
                        }
                    }
                    FolderPage(
                        result = result,
                        contentPadding = PaddingValues(top = topPadding, bottom = navBottom + 104.dp),
                        onFolder = { entry ->
                            haptics.tick(HapticArea.MENUS)
                            location = here.copy(trail = here.trail + (entry.name to entry.url))
                        },
                        onPlay = { audio, index -> haptics.click(HapticArea.PLAYBACK); onPlay(here.server, audio, index) },
                        onRetry = { listings.remove(here.url); reload++ },
                    )
                }
            }
        }

        Column(Modifier.fillMaxWidth().glass(source, frostedBarStyle())) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(start = 6.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GhostIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回", {
                    val current = location
                    when {
                        current == null -> onDismiss()
                        current.trail.size > 1 -> location = current.copy(trail = current.trail.dropLast(1))
                        else -> location = null
                    }
                })
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        location?.trail?.last()?.first ?: "WebDAV",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        location?.let { hostLabel(it.server) } ?: "网盘与 NAS 里的音乐",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val here = location
                if (here == null) {
                    OlbyineIconButton(Icons.Rounded.Add, "添加服务器", { editor = null; editorOpen = true },
                        container = colors.primary, tint = colors.onPrimary, iconSize = 22.dp)
                } else {
                    GhostIconButton(Icons.Rounded.Refresh, "刷新", { listings.remove(here.url); reload++ })
                }
            }
            location?.let { here ->
                Breadcrumbs(here.trail) { index ->
                    haptics.tick(HapticArea.MENUS)
                    location = here.copy(trail = here.trail.take(index + 1))
                }
            }
            Spacer(Modifier.height(BarEdge))
        }

        val here = location
        if (here != null) {
            val entries = listings[here.url]?.getOrNull()
            val audio = entries.orEmpty().filter { !it.directory && MusicLibraryRepository.isSupportedAudio(it.name, it.contentType) }
            val folders = entries.orEmpty().count { it.directory }
            ActionBar(
                source = source,
                audioCount = audio.size,
                folderCount = folders,
                importing = importing,
                importProgress = importProgress,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                onPlayAll = { if (audio.isNotEmpty()) { haptics.click(HapticArea.PLAYBACK); onPlay(here.server, audio, 0) } },
                onImport = { haptics.confirm(HapticArea.MENUS); onImport(here.server, here.url) },
            )
        }
    }
    }

    if (editorOpen) {
        ServerEditor(
            initial = editor,
            repository = repository,
            onDismiss = { editorOpen = false },
            onSaved = { saved ->
                editorOpen = false
                haptics.confirm(HapticArea.MENUS)
                listings.keys.filter { it.startsWith(saved.baseUrl) }.forEach(listings::remove)
                if (location?.server?.id == saved.id) location = null
            },
            onDelete = { server ->
                editorOpen = false
                repository.remove(server.id)
                if (location?.server?.id == server.id) location = null
            },
        )
    }
}

private fun hostLabel(server: WebDavServer): String {
    val host = runCatching { URI(server.baseUrl).host }.getOrNull() ?: server.baseUrl
    return if (server.username.isBlank()) host else "${server.username} @ $host"
}

@Composable
private fun ServerList(
    servers: List<WebDavServer>,
    hasPassword: (String) -> Boolean,
    contentPadding: PaddingValues,
    onOpen: (WebDavServer) -> Unit,
    onEdit: (WebDavServer) -> Unit,
    onAdd: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "hero") { DavHero(Modifier.padding(horizontal = OlbyineTokens.Spacing.page)) }
        if (servers.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(horizontal = OlbyineTokens.Spacing.page, vertical = 8.dp)) {
                    PillButton("添加第一台服务器", onAdd, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add, height = 48.dp)
                    Spacer(Modifier.height(18.dp))
                    Overline("可用的服务", Modifier.padding(start = 4.dp, bottom = 8.dp))
                    WebDavRepository.PRESETS.forEach { (name, url) ->
                        Text(
                            "$name  ·  $url",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
            item(key = "label") { Overline("服务器", Modifier.padding(start = OlbyineTokens.Spacing.page + 4.dp, top = 8.dp)) }
            items(servers, key = { it.id }) { server ->
                ServerCard(
                    server = server,
                    locked = server.username.isNotBlank() && hasPassword(server.id),
                    onClick = { onOpen(server) },
                    onEdit = { onEdit(server) },
                    modifier = itemMotion().padding(horizontal = OlbyineTokens.Spacing.page),
                )
            }
        }
    }
}

@Composable
private fun DavHero(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LocalStyleSpec.current.cardCorner))
            .background(
                Brush.linearGradient(
                    listOf(colors.primary.copy(alpha = if (dark) .34f else .22f), colors.tertiary.copy(alpha = if (dark) .20f else .12f)),
                ),
            )
            .padding(20.dp),
    ) {
        Column {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                StyledIcon(Icons.Rounded.Dns, null, Modifier.size(24.dp), tint = colors.onPrimary)
            }
            Spacer(Modifier.height(14.dp))
            Text("把 NAS 和网盘里的音乐带进 Olbyine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "支持 Alist、群晖、Nextcloud、坚果云等 WebDAV 服务。歌曲直接在线播放、不占手机空间，也能整个文件夹导入音乐库，同名歌词一并带上。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ServerCard(server: WebDavServer, locked: Boolean, onClick: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val hue = (server.id.hashCode().toUInt().toLong() % 360L).toFloat()
    val avatar = Color.hsv(hue, .45f, if (colors.background.luminance() < .5f) .62f else .78f)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LocalStyleSpec.current.cardCorner))
            .background(colors.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(avatar), contentAlignment = Alignment.Center) {
            Text(
                server.name.firstOrNull()?.uppercase() ?: "W",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(server.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (locked) {
                    StyledIcon(Icons.Rounded.Lock, "已加密保存密码", Modifier.size(12.dp), tint = colors.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                }
                Text(hostLabel(server), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        GhostIconButton(Icons.Rounded.Edit, "编辑 ${server.name}", onEdit, size = 40.dp, iconSize = 19.dp, tint = colors.onSurfaceVariant)
        StyledIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(22.dp), tint = colors.onSurfaceVariant)
    }
}

@Composable
private fun Breadcrumbs(trail: List<Pair<String, String>>, onSelect: (Int) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(trail.size) { state.animateScrollToItem((trail.size - 1).coerceAtLeast(0)) }
    LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth().height(46.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(trail) { index, (name, _) ->
            val last = index == trail.lastIndex
            if (index > 0) {
                StyledIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f))
            }
            Row(
                Modifier
                    .height(32.dp)
                    .clip(CircleShape)
                    .background(if (last) MaterialTheme.colorScheme.onSurface.copy(alpha = .10f) else Color.Transparent)
                    .clickable(enabled = !last) { onSelect(index) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (index == 0) {
                    StyledIcon(Icons.Rounded.Home, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FolderPage(
    result: Result<List<WebDavEntry>>?,
    contentPadding: PaddingValues,
    onFolder: (WebDavEntry) -> Unit,
    onPlay: (List<WebDavEntry>, Int) -> Unit,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    when {
        result == null -> Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 10.dp)) {
            repeat(8) { SkeletonRow() }
        }
        result.isFailure -> Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Rounded.CloudOff,
                title = "连不上这个文件夹",
                message = result.exceptionOrNull()?.message ?: "请检查网络、地址和账号",
                action = { PillButton("重试", onRetry, icon = Icons.Rounded.Refresh, style = PillStyle.TONAL) },
            )
        }
        else -> {
            val entries = result.getOrThrow()
            val folders = entries.filter { it.directory }
            val audio = entries.filter { !it.directory && MusicLibraryRepository.isSupportedAudio(it.name, it.contentType) }
            val others = entries.size - folders.size - audio.size
            if (folders.isEmpty() && audio.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Rounded.CreateNewFolder,
                        title = "这里没有音乐",
                        message = if (others > 0) "有 $others 个其他文件，但没有可播放的音频" else "文件夹是空的",
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 10.dp,
                        end = 10.dp,
                        top = contentPadding.calculateTopPadding(),
                        bottom = contentPadding.calculateBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (folders.isNotEmpty()) {
                        item(key = "folders") { SectionLabel("文件夹 · ${folders.size}") }
                        items(folders, key = { "d:" + it.url }) { entry -> MotionItem { EntryRow(entry, folder = true) { onFolder(entry) } } }
                    }
                    if (audio.isNotEmpty()) {
                        item(key = "songs") { SectionLabel("歌曲 · ${audio.size}") }
                        itemsIndexed(audio, key = { _, it -> "f:" + it.url }) { index, entry -> MotionItem { EntryRow(entry, folder = false) { onPlay(audio, index) } } }
                    }
                    if (others > 0) {
                        item(key = "others") {
                            Text(
                                "另有 $others 个非音频文件未显示",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Overline(text, Modifier.padding(start = 14.dp, top = 14.dp, bottom = 6.dp))
}

@Composable
private fun EntryRow(entry: WebDavEntry, folder: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (folder) colors.primary.copy(alpha = .16f) else colors.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (folder) Icons.Rounded.Folder else Icons.Rounded.MusicNote,
                null,
                Modifier.size(22.dp),
                tint = if (folder) colors.primary else colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (folder) entry.name else entry.name.substringBeforeLast('.'),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = buildList {
                if (!folder) add(entry.name.substringAfterLast('.', "").uppercase(Locale.ROOT))
                if (!folder && entry.size > 0) add(formatBytes(entry.size))
                if (entry.modified > 0) add(java.text.SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(java.util.Date(entry.modified)))
            }.filter(String::isNotBlank)
            if (details.isNotEmpty()) {
                Text(details.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
            }
        }
        Icon(
            if (folder) Icons.AutoMirrored.Rounded.KeyboardArrowRight else Icons.Rounded.PlayArrow,
            null,
            Modifier.size(22.dp),
            tint = colors.onSurfaceVariant.copy(alpha = if (folder) .7f else .9f),
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.2f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%d KB".format(bytes / (1L shl 10))
    else -> "$bytes B"
}

@Composable
private fun ActionBar(
    source: BackdropSource,
    audioCount: Int,
    folderCount: Int,
    importing: Boolean,
    importProgress: ImportProgress?,
    modifier: Modifier,
    onPlayAll: () -> Unit,
    onImport: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    val ios = LocalStyleSpec.current.style == UiStyle.IOS
    val style = GlassStyle(
        cornerRadius = 30.dp,
        blur = if (LocalStyleSpec.current.style == UiStyle.MATERIAL) 0.dp else 28.dp,
        tint = if (LocalStyleSpec.current.style == UiStyle.MATERIAL) colors.surfaceContainerHigh
        else if (dark) Color(0xFF1C1C1E).copy(alpha = .58f) else Color.White.copy(alpha = .62f),
        saturation = 1.8f,
        lens = ios,
        refractionHeight = 16.dp,
        refractionAmount = 20.dp,
        highlight = Color.White.copy(alpha = if (dark) .22f else .7f),
        highlightWidth = 1.dp,
        shadow = 18.dp,
        fallback = colors.surfaceContainerHigh,
    )
    Row(
        modifier.fillMaxWidth().height(64.dp).glass(source, style).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (importing) {
            OlbyineSpinner(Modifier.padding(start = 12.dp).size(22.dp), strokeWidth = 2.5.dp, color = colors.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(importProgress?.phase ?: "正在导入…", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                importProgress?.takeIf { it.total > 0 }?.let {
                    Text(
                        if (it.done > 0) "${it.done} / ${it.total}" else "已找到 ${it.total} 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        } else {
            PillButton(
                if (audioCount > 0) "播放 $audioCount 首" else "无歌曲",
                onPlayAll,
                Modifier.weight(1f),
                icon = Icons.Rounded.PlayArrow,
                style = PillStyle.TONAL,
                enabled = audioCount > 0,
                height = 48.dp,
            )
            Spacer(Modifier.width(8.dp))
            PillButton(
                if (folderCount > 0) "全部导入" else "导入",
                onImport,
                Modifier.weight(1.25f),
                icon = Icons.Rounded.Add,
                height = 48.dp,
            )
        }
    }
}

@Composable
private fun ServerEditor(
    initial: WebDavServer?,
    repository: WebDavRepository,
    onDismiss: () -> Unit,
    onSaved: (WebDavServer) -> Unit,
    onDelete: (WebDavServer) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var url by remember { mutableStateOf(initial?.baseUrl.orEmpty()) }
    var user by remember { mutableStateOf(initial?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keepPassword = initial != null && password.isEmpty() && repository.hasPassword(initial.id)
    fun draft() = WebDavServer(initial?.id.orEmpty(), name, url, user)

    OlbyineDialog(
        onDismissRequest = onDismiss,
        title = if (initial == null) "添加 WebDAV 服务器" else "编辑服务器",
        confirmButton = {
            GlassTextButton(
                enabled = url.isNotBlank(),
                onClick = { onSaved(repository.save(draft(), if (keepPassword) null else password)) },
            ) { Text("保存") }
        },
        dismissButton = { GlassTextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WebDavRepository.PRESETS.forEach { (preset, template) ->
                    Box(
                        Modifier
                            .height(30.dp)
                            .clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable {
                                url = template
                                if (name.isBlank()) name = preset
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(preset, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            OlbyineTextField(url, { url = it; status = null }, "地址（http:// 或 https://）")
            OlbyineTextField(name, { name = it }, "名称（可选）")
            OlbyineTextField(user, { user = it; status = null }, "用户名")
            GlassTextField(
                password,
                { password = it; status = null },
                if (initial != null && repository.hasPassword(initial.id)) "密码（已加密保存，留空不修改）" else "密码 / 应用密码",
                password = true,
            )
            Text(
                "密码用 Android 密钥库加密后只存在本机，只会发给这台服务器。坚果云等请使用“第三方应用密码”。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillButton(
                    if (testing) "正在连接…" else "测试连接",
                    {
                        testing = true
                        status = null
                        scope.launch {
                            val result = repository.test(draft(), if (keepPassword) null else password)
                            testing = false
                            status = result.fold(
                                { count -> true to "连接成功，根目录有 $count 项" },
                                { error -> false to (error.message ?: "连接失败") },
                            )
                        }
                    },
                    style = PillStyle.TONAL,
                    enabled = url.isNotBlank() && !testing,
                    height = 38.dp,
                )
                Spacer(Modifier.width(10.dp))
                status?.let { (ok, text) ->
                    Icon(
                        if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                        null,
                        Modifier.size(18.dp),
                        tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (initial != null) {
                GlassTextButton(onClick = {
                    if (confirmDelete) onDelete(initial) else confirmDelete = true
                }) {
                    Text(
                        if (confirmDelete) "再点一次确认删除（已导入的歌曲保留）" else "删除这台服务器",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
