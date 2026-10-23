package com.olbyine.music.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.olbyine.music.data.Track
import kotlinx.coroutines.launch
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
    val artInset = with(density) { MiniArtInset.toPx() }
    val edge = with(density) { 10.dp.toPx() }
    val buttonPx = with(density) { 46.dp.toPx() }
    val cornerEndPx = with(density) { OlbyineTokens.Corner.thumb.toPx() }
    val cornerStartPx = with(density) { MiniCorner.toPx() }
    var fx by rememberSaveable { mutableFloatStateOf(0.86f) }
    var fy by rememberSaveable { mutableFloatStateOf(0.74f) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val p = progress().coerceIn(0f, 1f)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val rootW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val rootH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val spanX = (rootW - buttonPx - edge * 2f).coerceAtLeast(0f)
        val spanY = (rootH - buttonPx - bottomInsetPx - edge * 2f).coerceAtLeast(0f)
        val origin = startBounds
        val startW = origin?.width?.takeIf { it > 1f } ?: miniBounds?.width?.takeIf { it > 1f } ?: (rootW - edge * 2f)
        val startH = origin?.height?.takeIf { it > 1f } ?: miniBounds?.height?.takeIf { it > 1f } ?: miniH
        val startLeft = origin?.left ?: miniBounds?.left ?: (rootW - buttonPx - edge)
        val startTop = origin?.top ?: miniBounds?.top ?: (rootH - bottomInsetPx - startH)
        val targetLeft = edge + (if (spanX > 0f) fx.coerceIn(0f, 1f) * spanX else 0f) + dragX
        val targetTop = edge + (if (spanY > 0f) fy.coerceIn(0f, 1f) * spanY else 0f) + dragY
        if (p > 0.004f) {
            val w = startW + (buttonPx - startW) * p
            val h = startH + (buttonPx - startH) * p
            val l = startLeft + (targetLeft - startLeft) * p
            val ty = startTop + (targetTop - startTop) * p
            val cornerPx = cornerStartPx + (cornerEndPx - cornerStartPx) * p
            val textAlpha = (1f - p * 1.6f).coerceIn(0f, 1f)
            val artSide = (h - artInset * 2f).coerceIn(buttonPx * 0.42f, h)
            Box(
                Modifier
                    .offset { IntOffset(l.roundToInt(), ty.roundToInt()) }
                    .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                    .glass(
                        source,
                        style,
                        dynamics = GlassDynamics(cornerRadius = { cornerPx }),
                    )
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
                    .onGloballyPositioned { onBounds(it.boundsInRoot()) }
                    .pointerInput(Unit) { detectTapGestures { onOpen() } },
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = with(density) { artInset.toDp() }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(with(density) { artSide.toDp() })
                            .clip(RoundedCornerShape(OlbyineTokens.Corner.thumb)),
                    ) {
                        TrackArtwork(track, Modifier.fillMaxSize(), corner = OlbyineTokens.Corner.thumb)
                    }
                    if (textAlpha > 0.02f) {
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha),
                            )
                            Text(
                                track.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha),
                            )
                        }
                    }
                }
            }
        }
    }
}
