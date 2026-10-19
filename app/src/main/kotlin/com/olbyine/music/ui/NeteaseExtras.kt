@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.olbyine.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.olbyine.music.data.HapticArea
import com.olbyine.music.data.NeteaseClient
import com.olbyine.music.data.NeteasePlaylistSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun NeteasePlaylistPicker(
    client: NeteaseClient,
    onPick: (NeteasePlaylistSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    val (state, retry) = rememberLoad(client.signedIn) {
        if (!client.signedIn) error("登录网易云音乐后才能收藏到歌单")
        client.playlists().filter { it.own && !it.liked }
    }
    var naming by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    GlassBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
            Text(
                "收藏到歌单",
                Modifier.padding(horizontal = 22.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.size(10.dp))
            val lists = state.value
            if (lists == null) {
                LoadingOrError(state.loading, state.error, retry)
            } else {
                LazyColumn(Modifier.fillMaxWidth()) {
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
                    items(lists, key = { it.id }) { playlist ->
                        PickerRow(
                            leading = {
                                UrlImage(playlist.coverUrl?.let { "$it?param=120y120" }, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))) {
                                    Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)), contentAlignment = Alignment.Center) {
                                        StyledIcon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            },
                            title = playlist.name,
                            subtitle = "${playlist.trackCount} 首",
                            onClick = {
                                haptics.click(HapticArea.MENUS)
                                onPick(playlist)
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
        }
    }
    if (naming) {
        PlaylistNameDialog("新建网易云歌单", "", { naming = false }) { name ->
            naming = false
            scope.launch {
                try {
                    val id = client.createPlaylist(name)
                    onPick(NeteasePlaylistSummary(id, name, null, 0, "", liked = false, own = true))
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    retry()
                }
            }
        }
    }
}

@Composable
private fun PickerRow(leading: @Composable () -> Unit, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun ScPlaylistPicker(
    client: com.olbyine.music.data.SoundCloudClient,
    trackId: Long,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val (state, retry) = rememberLoad(trackId) {
        if (!client.signedIn) error("登录 SoundCloud 后才能加入歌单")
        client.ownPlaylists()
    }
    var naming by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val run: (suspend () -> Unit, String) -> Unit = { action, done ->
        busy = true
        scope.launch {
            try {
                action()
                haptics.confirm(HapticArea.MENUS)
                onDone(done)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                onDone(error.message ?: "操作失败")
            }
            busy = false
        }
    }
    GlassBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
            Text("加入 SoundCloud 歌单", Modifier.padding(horizontal = 22.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(10.dp))
            val lists = state.value
            if (lists == null) {
                LoadingOrError(state.loading, state.error, retry)
            } else {
                LazyColumn(Modifier.fillMaxWidth()) {
                    item(key = "new") {
                        PickerRow(
                            leading = {
                                Box(
                                    Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)),
                                    contentAlignment = Alignment.Center,
                                ) { StyledIcon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onSurface) }
                            },
                            title = "新建歌单",
                            subtitle = null,
                            onClick = { if (!busy) naming = true },
                        )
                    }
                    items(lists, key = { it.urn }) { playlist ->
                        PickerRow(
                            leading = {
                                UrlImage(playlist.artworkUrl, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))) {
                                    Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)), contentAlignment = Alignment.Center) {
                                        StyledIcon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            },
                            title = playlist.title,
                            subtitle = "${playlist.trackCount} 首",
                            onClick = {
                                if (!busy) {
                                    haptics.click(HapticArea.MENUS)
                                    run({ client.addToPlaylist(playlist.id, trackId) }, "已加入「${playlist.title}」")
                                }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
        }
    }
    if (naming) {
        PlaylistNameDialog("新建 SoundCloud 歌单", "", { naming = false }) { name ->
            naming = false
            run({ client.createPlaylist(name, trackId) }, "已新建歌单「$name」")
        }
    }
}