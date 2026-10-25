package com.olbyine.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileDownloadDone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.olbyine.music.MainViewModel
import com.olbyine.music.data.DownloadRecord
import com.olbyine.music.data.NeteaseClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun DownloadManagerPage(viewModel: MainViewModel) {
    val records by viewModel.downloadRecords.collectAsState()
    val active by viewModel.neteaseDownloads.collectAsState()
    val running = active.values.filter { !it.done && !it.failed }
    var target by remember { mutableStateOf<DownloadRecord?>(null) }
    var clearDialog by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val total = records.sumOf { it.bytes }
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (records.isEmpty()) "还没有下载过歌曲" else "已下载 ${records.size} 首 · ${formatDownloadSize(total)}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "文件保存在 ${com.olbyine.music.data.NeteaseDownloader.FOLDER}，系统音乐库可见",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (records.isNotEmpty()) {
                    GlassTextButton(destructive = true, onClick = { haptics.light(com.olbyine.music.data.HapticArea.MENUS); clearDialog = true }) {
                        Text("清空")
                    }
                }
            }
        running.forEach { state ->
            DownloadProgressRow(state)
        }
        if (running.isNotEmpty()) Spacer(Modifier.height(6.dp))
        if (records.isEmpty() && running.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                Text(
                    "在网易云歌单里长按爱心，或在播放页左下角菜单里选「下载」，即可保存到本地。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        records.forEach { record ->
            DownloadRecordRow(record) {
                haptics.tick(com.olbyine.music.data.HapticArea.MENUS)
                target = record
            }
        }
    }
    target?.let { record ->
        OlbyineDialog(
            onDismissRequest = { target = null },
            title = "「${record.title}」",
            confirmButton = {
                GlassTextButton(destructive = true, onClick = {
                    target = null
                    viewModel.deleteDownload(record, removeFile = true)
                }) { Text("删除文件") }
            },
            dismissButton = {
                GlassTextButton(onClick = {
                    target = null
                    viewModel.deleteDownload(record, removeFile = false)
                }) { Text("仅移除记录") }
            },
        ) {
            Column {
                Text(
                    listOf(record.artist, record.album).filter(String::isNotBlank).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${NeteaseClient.qualityBadge(record.level)} · ${formatDownloadSize(record.bytes)} · " +
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(record.at)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    record.fileName +
                        (if (record.tagged) " · 标签已内嵌" else " · 标签未内嵌") +
                        (if (record.coverEmbedded) "+封面" else "") +
                        (if (record.lyricsUri != null) " · 含 .lrc" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "「删除文件」会同时删除本地音频和歌词；「仅移除记录」保留文件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (clearDialog) {
        OlbyineDialog(
            onDismissRequest = { clearDialog = false },
            title = "清空下载记录？",
            confirmButton = {
                GlassTextButton(destructive = true, onClick = {
                    clearDialog = false
                    viewModel.clearDownloadRecords(removeFiles = true)
                }) { Text("连文件一起删") }
            },
            dismissButton = {
                GlassTextButton(onClick = {
                    clearDialog = false
                    viewModel.clearDownloadRecords(removeFiles = false)
                }) { Text("只清记录") }
            },
        ) {
            Text("共 ${records.size} 个文件，${formatDownloadSize(total)}。清空记录不会影响歌单和收藏。")
        }
    }
}

@Composable
private fun DownloadProgressRow(state: com.olbyine.music.NeteaseDownloadState) {
    Column(
        Modifier.fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = .07f))
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)),
                contentAlignment = Alignment.Center,
            ) {
                StyledIcon(
                    Icons.Rounded.FileDownloadDone,
                    null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(state.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append(NeteaseClient.qualityBadge(state.level))
                        append(" · ")
                        if (state.totalBytes > 0L) {
                            append("${(state.progress * 100).toInt()}% · ")
                            append(formatDownloadSize(state.bytes))
                            append(" / ")
                            append(formatDownloadSize(state.totalBytes))
                        } else {
                            append("下载中 · ")
                            append(formatDownloadSize(state.bytes))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.LinearProgressIndicator(
            progress = { if (state.progress > 0f) state.progress else 0.02f },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DownloadRecordRow(record: DownloadRecord, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)),
            contentAlignment = Alignment.Center,
        ) {
            StyledIcon(Icons.Rounded.FileDownloadDone, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(record.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(
                    record.artist,
                    NeteaseClient.qualityBadge(record.level),
                    formatDownloadSize(record.bytes),
                ).filter(String::isNotBlank).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StyledIcon(
            Icons.Rounded.DeleteOutline,
            null,
            Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f),
        )
    }
}

internal fun formatDownloadSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${bytes} B" else String.format(Locale.US, "%.1f %s", value, units[unit])
}
