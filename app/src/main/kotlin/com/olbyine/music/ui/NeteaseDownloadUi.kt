package com.olbyine.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.olbyine.music.data.NeteaseClient
import com.olbyine.music.data.NeteaseSong

@Composable
internal fun NeteaseDownloadDialog(
    song: NeteaseSong,
    defaultLevel: String,
    saveLyrics: Boolean,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val haptics = rememberHaptics()
    OlbyineDialog(
        onDismissRequest = onDismiss,
        title = "下载「${song.title}」",
        confirmButton = { GlassTextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text(
                listOf(song.artist, song.album).filter(String::isNotBlank).joinToString(" · ") +
                    "　·　保存到 " + com.olbyine.music.data.NeteaseDownloader.FOLDER +
                    if (saveLyrics) "（含 .lrc 歌词）" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            NeteaseClient.QUALITIES.forEach { (level, label) ->
                val selected = level == defaultLevel
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .10f) else androidx.compose.ui.graphics.Color.Transparent,
                        )
                        .clickable {
                            haptics.tick(com.olbyine.music.data.HapticArea.MENUS)
                            onPick(level)
                        }
                        .padding(horizontal = 8.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StyledIcon(
                        Icons.Rounded.FileDownload,
                        null,
                        Modifier.size(18.dp),
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        label,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (selected) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            StyledIcon(Icons.Rounded.Check, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "高于账号权益的音质会自动降级到可用的最高音质。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
