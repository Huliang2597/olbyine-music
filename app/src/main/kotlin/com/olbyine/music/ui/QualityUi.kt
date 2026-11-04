@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.olbyine.music.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.olbyine.music.data.HapticArea
import com.olbyine.music.data.NeteaseClient
import com.olbyine.music.data.Track
import com.olbyine.music.playback.AudioChainRuntime
import com.olbyine.music.playback.AudioEffects
import com.olbyine.music.playback.NowPlayingFormat
import com.olbyine.music.playback.usb.UsbAudioDriver
import com.olbyine.music.playback.usb.UsbDriverState
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
    val picker = if (ncmId != null) quality else null
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .pressable(label = "音质") {
                haptics.light(HapticArea.MENUS)
                open = true
            },
    ) {
        Badge(label, shine)
        if (picker != null) {
            OlbyineDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                NeteaseQualityMenu(
                    current = picker.first,
                    served = streams[ncmId]?.let { NeteaseClient.qualityBadge(it.level) + (it.bitrate.takeIf { br -> br > 0 }?.let { br -> " · ${br / 1000} kbps" } ?: "") },
                    onPick = { level ->
                        open = false
                        if (level != picker.first) picker.second(level)
                    },
                )
            }
        }
    }
    if (open && picker == null) AudioInfoDialog(track) { open = false }
}
@Composable
private fun AudioInfoDialog(track: Track, onDismiss: () -> Unit) {
    val format by NowPlayingFormat.format.collectAsState()
    val live = format.takeIf { it.mediaId == track.id }
    val driver by UsbAudioDriver.state.collectAsState()
    val chain by AudioChainRuntime.state.collectAsState()
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
        track.sizeBytes.takeIf { it > 0 }?.let { "文件大小" to String.format(Locale.US, "%.1f MB", it / 1_048_576.0) },
        "输出" to when (driver.status) {
            UsbDriverStatus.STREAMING -> if (driver.bitPerfect) "USB 位完美" else "USB 独占"
            else -> "系统混音"
        },
    )
    OlbyineDialog(
        onDismissRequest = onDismiss,
        title = "音频信息",
        confirmButton = { GlassTextButton(onClick = onDismiss) { Text("关闭") } },
    ) {
        val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.62f).dp
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
            Text(
                track.title,
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            rows.forEachIndexed { index, (label, value) ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f)))
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start)
                    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("音频链路", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            AudioChain(
                listOf(
                    "来源" to chainSource(track, live, sampleRate, bits, bitrate, channels),
                    "解码" to "ExoPlayer · ${chain.encoding.ifBlank { "PCM" }}",
                    "效果" to effectsLabel(),
                    "路径" to if (driver.status == UsbDriverStatus.STREAMING) {
                        "USB 独占直通 · 跳过系统混音"
                    } else {
                        "系统混音 · AudioTrack"
                    },
                    "输出" to outputLabel(driver, chain),
                    "协商" to when {
                        chain.sampleRate <= 0 -> "—"
                        sampleRate <= 0 -> hz(chain.sampleRate)
                        chain.sampleRate == sampleRate -> "${hz(chain.sampleRate)} · 未重采样"
                        else -> "${hz(sampleRate)} → ${hz(chain.sampleRate)} · 系统重采样"
                    },
                )
            )
        }
    }
}

private fun effectsLabel(): String {
    val effects = AudioEffects.current
    val active = buildList {
        if (effects.eqEnabled) add(if (effects.eqActive) "均衡器 生效" else "均衡器 平直")
        if (effects.balance != 0f) add(String.format(Locale.US, "平衡 %+.1f", effects.balance))
        if (effects.mono) add("单声道")
    }
    return if (active.isEmpty()) "原声（未处理）" else active.joinToString(" · ")
}

private fun outputLabel(driver: UsbDriverState, chain: com.olbyine.music.playback.AudioChainState): String {
    val parts = mutableListOf(driver.deviceName ?: chain.deviceName ?: "默认输出")
    if (driver.status == UsbDriverStatus.STREAMING) {
        if (driver.bitPerfect) parts += "位完美"
        driver.format?.takeIf { it.isNotBlank() }?.let { parts += it }
        if (driver.hardwareVolume) parts += "硬件音量 ${driver.volume}/100"
        else parts += "软件音量 ${driver.volume}%"
    } else {
        chain.encoding.takeIf { it.isNotBlank() }?.let { parts += it }
    }
    return parts.joinToString(" · ")
}

private fun hz(value: Int): String =
    String.format(Locale.US, "%.1f kHz", value / 1000.0).replace(".0 kHz", " kHz")

private fun chainSource(track: Track, live: com.olbyine.music.playback.AudioFormatInfo?, rate: Int, bits: Int, bitrate: Int, channels: Int): String {
    val parts = listOfNotNull(
        track.formatLabel() ?: live?.mime?.substringAfter('/')?.uppercase(),
        bits.takeIf { it > 0 }?.let { "$it bit" },
        rate.takeIf { it > 0 }?.let(::hz),
        when (channels) {
            0 -> null
            1 -> "单声道"
            2 -> "立体声"
            else -> "${channels} 声道"
        },
        bitrate.takeIf { it > 0 }?.let { "${it / 1000} kbps" },
    )
    return parts.joinToString(" · ").ifBlank { "未知" }
}

@Composable
private fun AudioChain(stages: List<Pair<String, String>>) {
    Column(Modifier.fillMaxWidth()) {
        stages.forEachIndexed { index, (title, detail) ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Column(Modifier.width(16.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    if (index != stages.lastIndex) {
                        Box(Modifier.padding(top = 3.dp).width(1.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
                    }
                }
                Column(Modifier.weight(1f).padding(start = 10.dp, bottom = if (index == stages.lastIndex) 0.dp else 14.dp)) {
                    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(detail, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
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