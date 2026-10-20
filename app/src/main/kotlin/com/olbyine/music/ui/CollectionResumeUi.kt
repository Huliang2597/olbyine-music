package com.olbyine.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.olbyine.music.CollectionResumePoint
import com.olbyine.music.data.NeteaseSong

internal val LocalCollectionResume = staticCompositionLocalOf<((String) -> CollectionResumePoint?)?> { null }

internal val LocalResumeCollection = staticCompositionLocalOf<((String, List<NeteaseSong>) -> Unit)?> { null }

internal val LocalPlayNeteaseCollection = staticCompositionLocalOf<((String, List<NeteaseSong>, Int, Boolean, Long) -> Unit)?> { null }

internal val LocalActiveCollection = staticCompositionLocalOf<String?> { null }

internal fun collectionKey(kind: String, id: Long): String = "ncm:$kind:$id"

@Composable
internal fun CollectionResumeRow(point: CollectionResumePoint, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StyledIcon(Icons.Rounded.History, null, Modifier.size(18.dp), tint = colors.primary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "继续上次播放",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface,
                maxLines = 1,
            )
            Text(
                listOfNotNull(
                    point.title.takeIf(String::isNotBlank),
                    point.positionMs.takeIf { it > 1_000L }?.let(::formatDuration),
                ).joinToString(" · ").ifBlank { "上次播放位置" },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        StyledIcon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp), tint = colors.primary)
    }
}
