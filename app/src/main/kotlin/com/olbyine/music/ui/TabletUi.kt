package com.olbyine.music.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olbyine.music.AppDestination
import com.olbyine.music.data.HapticArea
import com.olbyine.music.data.Playlist

internal data class AppLayout(val tablet: Boolean, val sidebarWidth: Dp, val compactRail: Boolean, val splitPlayer: Boolean) {
    companion object {
        val Phone = AppLayout(tablet = false, sidebarWidth = 0.dp, compactRail = false, splitPlayer = false)

        fun of(mode: String, widthDp: Int, heightDp: Int): AppLayout {
            val tablet = when (mode) {
                "tablet" -> true
                "phone" -> false
                else -> widthDp >= 600
            }
            if (!tablet) return Phone
            val compact = widthDp < 600
            return AppLayout(
                tablet = true,
                sidebarWidth = when {
                    compact -> 76.dp
                    widthDp >= 1000 -> 272.dp
                    else -> 232.dp
                },
                compactRail = compact,
                splitPlayer = widthDp >= 600 && widthDp > heightDp,
            )
        }
    }
}

internal val LocalAppLayout = compositionLocalOf { AppLayout.Phone }

private data class SidebarEntry(val key: String, val label: String, val icon: ImageVector)

@Composable
internal fun TabletSidebar(
    layout: AppLayout,
    destination: AppDestination,
    section: LibrarySection,
    playlists: List<Playlist>,
    activePlaylistId: String?,
    favoriteCount: Int,
    source: BackdropSource,
    onLibrary: (LibrarySection) -> Unit,
    onPlaylist: (String) -> Unit,
    onDestination: (AppDestination) -> Unit,
    onWebDav: () -> Unit,
    modifier: Modifier = Modifier,
    streamingProvider: String = "netease",
    onStreaming: (String) -> Unit = { onDestination(AppDestination.ONLINE) },
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    val haptics = rememberHaptics()
    val material = LocalStyleSpec.current.style == com.olbyine.music.data.UiStyle.MATERIAL
    val style = GlassStyle(
        cornerRadius = 0.dp,
        blur = if (material) 0.dp else 30.dp,
        tint = if (material) colors.surfaceContainerLow else colors.surfaceContainer.copy(alpha = if (dark) .58f else .62f),
        saturation = 1.8f,
        fallback = colors.surfaceContainer,
    )
    val divider = colors.outlineVariant.copy(alpha = .5f)
    val library = listOf(
        SidebarEntry(LibrarySection.ALL.name, "全部歌曲", Icons.Rounded.MusicNote),
        SidebarEntry(LibrarySection.FAVORITES.name, if (favoriteCount > 0 && !layout.compactRail) "收藏 · $favoriteCount" else "收藏", Icons.Rounded.Favorite),
        SidebarEntry(LibrarySection.ALBUMS.name, "专辑", Icons.Rounded.Album),
        SidebarEntry(LibrarySection.FOLDERS.name, "文件夹", Icons.Rounded.FolderOpen),
        SidebarEntry(LibrarySection.PLAYLISTS.name, "歌单", Icons.AutoMirrored.Rounded.QueueMusic),
    )
    val inLibrary = destination == AppDestination.LIBRARY || destination == AppDestination.NOW_PLAYING
    fun selectedLibrary(key: String) = inLibrary && section.name == key && (key != LibrarySection.PLAYLISTS.name || activePlaylistId == null)
    val click: (() -> Unit) -> () -> Unit = { action -> { haptics.tick(HapticArea.GESTURES); action() } }

    Column(
        modifier
            .fillMaxHeight()
            .glass(source, style)
            .drawBehind { drawLine(divider, Offset(size.width - 1f, 0f), Offset(size.width - 1f, size.height), 1f) }
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        if (layout.compactRail) {
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                library.forEach { entry ->
                    RailItem(entry.icon, entry.label, selectedLibrary(entry.key), click { onLibrary(LibrarySection.valueOf(entry.key)) })
                }
                Spacer(Modifier.height(6.dp))
                RailItem(Icons.Rounded.Cloud, "网易云", destination == AppDestination.ONLINE && streamingProvider == "netease", click { onStreaming("netease") })
                RailItem(Icons.Rounded.Waves, "SoundCloud", destination == AppDestination.ONLINE && streamingProvider == "soundcloud", click { onStreaming("soundcloud") })
                RailItem(Icons.Rounded.Storage, "Navidrome", destination == AppDestination.ONLINE && streamingProvider == "navidrome", click { onStreaming("navidrome") })
                RailItem(Icons.Rounded.Dns, "WebDAV", false, click(onWebDav))
            }
            RailItem(Icons.Rounded.Settings, "设置", destination == AppDestination.SETTINGS, click { onDestination(AppDestination.SETTINGS) },
                Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp))
        } else {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                Text(
                    "Olbyine-X",
                    Modifier.padding(start = 10.dp, top = 18.dp, bottom = 10.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                SidebarLabel("资料库")
                library.forEach { entry ->
                    SidebarItem(entry.icon, entry.label, selectedLibrary(entry.key), click { onLibrary(LibrarySection.valueOf(entry.key)) })
                }
                SidebarLabel("在线")
                SidebarItem(Icons.Rounded.Cloud, "网易云音乐", destination == AppDestination.ONLINE && streamingProvider == "netease", click { onStreaming("netease") })
                SidebarItem(Icons.Rounded.Waves, "SoundCloud", destination == AppDestination.ONLINE && streamingProvider == "soundcloud", click { onStreaming("soundcloud") })
                SidebarItem(Icons.Rounded.Storage, "Navidrome", destination == AppDestination.ONLINE && streamingProvider == "navidrome", click { onStreaming("navidrome") })
                SidebarItem(Icons.Rounded.Dns, "WebDAV 与 NAS", false, click(onWebDav))
                if (playlists.isNotEmpty()) {
                    SidebarLabel("我的歌单")
                    playlists.forEach { playlist ->
                        SidebarItem(
                            Icons.AutoMirrored.Rounded.QueueMusic,
                            playlist.name,
                            inLibrary && section == LibrarySection.PLAYLISTS && activePlaylistId == playlist.id,
                            click { onPlaylist(playlist.id) },
                            trailing = "${playlist.trackIds.size}",
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                SidebarItem(Icons.Rounded.Settings, "设置", destination == AppDestination.SETTINGS, click { onDestination(AppDestination.SETTINGS) })
            }
        }
    }
}

@Composable
private fun SidebarLabel(text: String) {
    Text(
        text,
        Modifier.padding(start = 10.dp, top = 18.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SidebarItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit, trailing: String? = null) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(if (selected) colors.primary.copy(alpha = .16f) else Color.Transparent, label = "sidebar-bg")
    val ink by animateColorAsState(if (selected) colors.primary else colors.onSurface, label = "sidebar-ink")
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StyledIcon(icon, null, Modifier.size(21.dp), tint = if (selected) colors.primary else colors.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(if (selected) colors.primary.copy(alpha = .18f) else Color.Transparent, label = "rail-bg")
    Column(
        modifier
            .width(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(width = 48.dp, height = 30.dp).clip(RoundedCornerShape(15.dp)).background(background),
            contentAlignment = Alignment.Center,
        ) {
            StyledIcon(icon, label, Modifier.size(21.dp), tint = if (selected) colors.primary else colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
