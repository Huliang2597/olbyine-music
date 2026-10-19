@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.olbyine.music.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.olbyine.music.data.HapticArea
import com.olbyine.music.data.NeteaseClient
import com.olbyine.music.data.Track
import com.olbyine.music.playback.NowPlayingFormat
import com.olbyine.music.playback.usb.UsbAudioDriver
import com.olbyine.music.playback.usb.UsbDriverStatus
import java.util.Locale

internal val LocalNeteaseQuality = staticCompositionLocalOf<Pair<String, (String) -> Unit>?> { null }

@Composable
internal fun QualityBadge(track: Track, shine: Modifier) {
    var open by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val ncmId = track.id.removePrefix("ncm:").toLongOrNull()?.takeIf { track.id.startsWith("ncm:") }
    val quality = LocalNeteaseQuality.current
    val streams by NowPlayingFormat.streams.collectAsState()
    val label = when {
        ncmId != null -> NeteaseClient.qualityBadge(streams[ncmId]?.level ?: quality?.first ?: "exhigh")
        track.id.startsWith("sc:") -> null
        else -> track.formatLabel()
    } ?: return
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .pressable(label = "音质") {
                haptics.light(HapticArea.MENUS)
                open = true
            },
    ) {
        Badge(label, shine)
        OlbyineDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (ncmId != null && quality != null) {
                NeteaseQualityMenu(
                    current = quality.first,
                    served = streams[ncmId]?.let { NeteaseClient.qualityBadge(it.level) + (it.bitrate.takeIf { br -> br > 0 }?.let { br -> " · ${br / 1000} kbps" } ?: "") },
                    onPick = { level ->
                        open = false
                        if (level != quality.first) quality.second(level)
                    },
                )
            } else {
                AudioDetailsCard(track)
            }
        }
    }
}
@Composable
private fun AudioDetailsCard(track: Track) {
    val format by NowPlayingFormat.format.collectAsState()
    val live = format.takeIf { it.mediaId == track.id }
    val driver by UsbAudioDriver.state.collectAsState()
    val sampleRate = live?.sampleRate?.takeIf { it > 0 } ?: track.sampleRate
    val bits = track.bitsPerSample.takeIf { it > 0 } ?: live?.bitsPerSample ?: 0
    val bitrate = track.bitrate.takeIf { it > 0 } ?: live?.bitrate ?: 0
    val channels = live?.channels ?: 0
    val rows = listOfNotNull(
        "格式" to (track.formatLabel() ?: live?.mime?.substringAfter('/')?.uppercase() ?: "未知"),
        "采样率" to (sampleRate.takeIf { it > 0 }?.let { String.format(Locale.US, "%.1f kHz", it / 1000.0).replace(".0 kHz", " kHz") } ?: "未知"),
        "位深" to (bits.takeIf { it > 0 }?.let { "$it bit" } ?: "—"),
        "比特率" to (bitrate.takeIf { it > 0 }?.let { "${it / 1000} kbps" } ?: "—"),
        "声道" to when (channels) {
            0 -> "—"
            1 -> "单声道"
            2 -> "立体声"
            else -> "$channels 声道"
        },
        track.sizeBytes.takeIf { it > 0 }?.let { "大小" to String.format(Locale.US, "%.1f MB", it / 1_048_576.0) },
        "输出" to when (driver.status) {
            UsbDriverStatus.STREAMING -> if (driver.bitPerfect) "USB 位完美" else "USB 独占"
            else -> "系统混音"
        },
    )
    Column(Modifier.width(244.dp).padding(horizontal = 14.dp, vertical = 6.dp)) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f)))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun NeteaseQualityMenu(current: String, served: String?, onPick: (String) -> Unit) {
    Text(
        served?.let { "正在播放 $it" } ?: "账号不支持的音质会自动降级",
        Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    NeteaseClient.QUALITIES.forEach { (level, label) ->
        GlassMenuItem(label, onClick = { onPick(level) }, selected = level == current)
    }
}