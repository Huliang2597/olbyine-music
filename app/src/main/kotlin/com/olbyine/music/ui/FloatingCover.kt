package com.olbyine.music.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.olbyine.music.data.Track
import kotlin.math.roundToInt

@Composable
internal fun FloatingCoverButton(
    track: Track,
    playing: Boolean,
    miniBounds: Rect?,
    dockArtBounds: Rect?,
    startBounds: Rect? = null,
    progress: () -> Float,
    source: BackdropSource,
    style: GlassStyle,
    onBounds: (Rect) -> Unit = {},
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    bottomInsetPx: Int = 0,
) {
    val density = LocalDensity.current
    val miniH = with(density) { MiniHeight.toPx() }
    val edge = with(density) { 10.dp.toPx() }
    val buttonPx = with(density) { 46.dp.toPx() }
    val cornerEndPx = with(density) { OlbyineTokens.Corner.thumb.toPx() }
    val cornerStartPx = with(density) { MiniCorner.toPx() }
    var fx by rememberSaveable { mutableFloatStateOf(0.86f) }
    var fy by rememberSaveable { mutableFloatStateOf(0.74f) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(modifier.fillMaxSize()) {
    val rootWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
    val rootHeight = constraints.maxHeight.toFloat().coerceAtLeast(1f)
    val origin = startBounds
    val startW = origin?.width?.takeIf { it > 1f } ?: miniBounds?.width?.takeIf { it > 1f } ?: 0f
    val startH = origin?.height?.takeIf { it > 1f } ?: miniBounds?.height?.takeIf { it > 1f } ?: miniH
    val startLeft = origin?.left ?: miniBounds?.left ?: 0f
    val startTop = origin?.top ?: miniBounds?.top ?: 0f
    val spanX = (rootWidth - buttonPx - edge * 2f).coerceAtLeast(0f)
    val spanY = (rootHeight - buttonPx - bottomInsetPx - edge * 2f).coerceAtLeast(0f)
    val targetLeft = edge + (if (spanX > 0f) fx.coerceIn(0f, 1f) * spanX else 0f) + dragX
    val targetTop = edge + (if (spanY > 0f) fy.coerceIn(0f, 1f) * spanY else 0f) + dragY

    val dynamics = remember(startW, startH, startLeft, startTop, buttonPx) {
        GlassDynamics(cornerRadius = { cornerStartPx + (cornerEndPx - cornerStartPx) * progress().coerceIn(0f, 1f) })
    }

    Box(
        Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val p = progress().coerceIn(0f, 1f)
                if (p <= 0.004f) return@layout layout(0, 0) {}
                val w = (startW + (buttonPx - startW) * p).roundToInt().coerceAtLeast(1)
                val h = (startH + (buttonPx - startH) * p).roundToInt().coerceAtLeast(1)
                val left = (startLeft + (targetLeft - startLeft) * p).roundToInt()
                val top = (startTop + (targetTop - startTop) * p).roundToInt()
                val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(left, top) }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .glass(source, style, dynamics = dynamics)
                .pointerInput(spanX, spanY) {
                    detectDragGestures(
                        onDragEnd = {
                            if (spanX > 0f) fx = ((fx * spanX + dragX).coerceIn(0f, spanX)) / spanX
                            if (spanY > 0f) fy = ((fy * spanY + dragY).coerceIn(0f, spanY)) / spanY
                            dragX = 0f
                            dragY = 0f
                        },
                    ) { change, delta ->
                        change.consume()
                        dragX += delta.x
                        dragY += delta.y
                    }
                }
                .onGloballyPositioned { if (progress() > 0.995f) onBounds(it.boundsInRoot()) }
                .pointerInput(Unit) { detectTapGestures { onOpen() } },
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = MiniArtInset),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.fillMaxHeight().padding(vertical = MiniArtInset)
                        .aspectRatio(1f, matchHeightConstraintsFirst = true)
                        .clip(RoundedCornerShape(OlbyineTokens.Corner.thumb)),
                ) {
                    TrackArtwork(track, Modifier.fillMaxSize(), corner = OlbyineTokens.Corner.thumb)
                }
                Spacer(Modifier.width(12.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .graphicsLayer { alpha = (1f - progress().coerceIn(0f, 1f) * 1.6f).coerceIn(0f, 1f) },
                ) {
                    Text(
                        track.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        track.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
}
