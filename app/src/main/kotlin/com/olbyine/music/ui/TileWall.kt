package com.olbyine.music.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.olbyine.music.data.LyricLine
import com.olbyine.music.data.LyricsDocument
import com.olbyine.music.data.Track
import com.olbyine.music.playback.PlaybackState

internal class LyricFeed(val document: LyricsDocument, val trackId: String?, val clock: State<Long>, val showTranslation: Boolean = true)

internal val LocalLyricFeed = staticCompositionLocalOf<LyricFeed?> { null }

internal val LocalPlayerLaunch = staticCompositionLocalOf<((androidx.compose.ui.geometry.Rect) -> Unit)?> { null }

internal data class WallItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val cover: Track,
    val playing: Boolean,
)

private data class WallTile(val index: Int, val main: Int, val cross: Int, val mainSpan: Int, val crossSpan: Int)

private fun packWall(items: List<WallItem>, crossCount: Int): List<WallTile> {
    val rows = ArrayList<BooleanArray>()
    fun free(main: Int, cross: Int, mainSpan: Int, crossSpan: Int): Boolean {
        for (m in main until main + mainSpan) {
            val row = rows.getOrNull(m) ?: continue
            for (c in cross until cross + crossSpan) if (row[c]) return false
        }
        return true
    }
    var firstOpen = 0
    val big = minOf(3, crossCount)
    return items.mapIndexed { index, item ->
        val roll = (item.key.hashCode() ushr 3) % 100
        val (mainSpan, crossSpan) = when {
            item.playing -> big to big
            roll < 18 && crossCount >= 3 -> 2 to 2
            roll < 26 -> 2 to 1
            else -> 1 to 1
        }
        var placed: WallTile? = null
        var main = firstOpen
        while (placed == null) {
            for (cross in 0..(crossCount - crossSpan)) {
                if (free(main, cross, mainSpan, crossSpan)) {
                    placed = WallTile(index, main, cross, mainSpan, crossSpan)
                    break
                }
            }
            if (placed == null) main++
        }
        val tile = placed!!
        while (rows.size < tile.main + tile.mainSpan) rows += BooleanArray(crossCount)
        for (m in tile.main until tile.main + tile.mainSpan) for (c in tile.cross until tile.cross + tile.crossSpan) rows[m][c] = true
        while (firstOpen < rows.size && rows[firstOpen].all { it }) firstOpen++
        tile
    }
}

@Composable
internal fun TileWall(
    newItems: List<WallItem>,
    playback: PlaybackState,
    onClick: (Int) -> Unit,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    header: (@Composable () -> Unit)? = null,
) {
    val density = LocalDensity.current
    val scroll = androidx.compose.foundation.rememberScrollState()
    val feed = LocalLyricFeed.current
    var items by remember { mutableStateOf(newItems) }
    LaunchedEffect(newItems) {
        val before = items.indexOfFirst { it.playing }
        val after = newItems.indexOfFirst { it.playing }
        if (before != after && items.size == newItems.size && after >= 0) kotlinx.coroutines.delay(900)
        items = newItems
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val horizontal = maxWidth > maxHeight
        val crossLength = if (horizontal) maxHeight - topContentPadding - bottomContentPadding - 8.dp else maxWidth - 8.dp
        val crossCount = if (horizontal) (crossLength / 112.dp).toInt().coerceIn(2, 7) else (crossLength / 104.dp).toInt().coerceIn(3, 8)
        val cell = crossLength / crossCount
        val cellPx = with(density) { cell.toPx() }
        val tiles = remember(items, crossCount) { packWall(items, crossCount) }
        val mainCells = tiles.maxOfOrNull { it.main + it.mainSpan } ?: 0
        var headerPx by remember { mutableIntStateOf(0) }
        val leadPx = with(density) { (if (horizontal) 4.dp else topContentPadding + 4.dp).toPx() } + if (horizontal) 0 else headerPx
        val viewportPx = with(density) { (if (horizontal) maxWidth else maxHeight).toPx() }
        val window by remember(cellPx, viewportPx, leadPx) {
            derivedStateOf {
                val first = ((scroll.value - leadPx) / cellPx).toInt() - 3
                val last = ((scroll.value - leadPx + viewportPx) / cellPx).toInt() + 3
                first..last
            }
        }
        val wall: @Composable () -> Unit = {
            Box(
                if (horizontal) Modifier.padding(4.dp).size(width = cell * mainCells, height = crossLength)
                else Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp).size(width = crossLength, height = cell * mainCells),
            ) {
                val visible = window
                tiles.forEach { tile ->
                    if (tile.main + tile.mainSpan < visible.first || tile.main > visible.last) return@forEach
                    val item = items[tile.index]
                    key(item.key) {
                        WallTileView(
                            item = item,
                            number = tile.index + 1,
                            x = if (horizontal) cell * tile.main else cell * tile.cross,
                            y = if (horizontal) cell * tile.cross else cell * tile.main,
                            width = if (horizontal) cell * tile.mainSpan else cell * tile.crossSpan,
                            height = if (horizontal) cell * tile.crossSpan else cell * tile.mainSpan,
                            span = minOf(tile.mainSpan, tile.crossSpan),
                            playback = playback,
                            feed = feed?.takeIf { item.playing && it.trackId == item.key },
                            onClick = { onClick(tile.index) },
                        )
                    }
                }
            }
        }
        if (horizontal) {
            Box(Modifier.fillMaxSize().padding(top = topContentPadding, bottom = bottomContentPadding).horizontalScroll(scroll)) { wall() }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(top = topContentPadding, bottom = bottomContentPadding + 24.dp)) {
                if (header != null) Box(Modifier.fillMaxWidth().onSizeChanged { headerPx = it.height }) { header() }
                wall()
            }
        }
    }
}

private class TileBoundsShape(private val width: Float, private val height: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(Rect(0f, 0f, width.coerceAtLeast(0f), height.coerceAtLeast(0f)))
}

@Composable
private fun WallTileView(
    item: WallItem,
    number: Int,
    x: Dp,
    y: Dp,
    width: Dp,
    height: Dp,
    span: Int,
    playback: PlaybackState,
    feed: LyricFeed?,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val target = with(density) { Rect(x.toPx(), y.toPx(), (x + width).toPx(), (y + height).toPx()) }
    val rectState = animateRectAsState(target, spring(dampingRatio = .82f, stiffness = 300f), label = "tile-bounds")
    val rect by rectState
    val currentTarget by rememberUpdatedState(target)
    val landed by remember {
        derivedStateOf {
            val r = rectState.value
            abs(r.width - currentTarget.width) <= .5f && abs(r.height - currentTarget.height) <= .5f
        }
    }
    val lift by animateFloatAsState(
        if (item.playing && landed) 1f else 0f,
        tween(OlbyineTokens.Motion.standardMs, easing = OlbyineTokens.Motion.easeOut),
        label = "tile-shadow",
    )
    val shape = RoundedCornerShape(5.dp)
    val big = span >= 2
    val launch = LocalPlayerLaunch.current
    var bounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    Box(
        Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(width, height)
            .graphicsLayer {
                val w = size.width.coerceAtLeast(1f)
                val h = size.height.coerceAtLeast(1f)
                val gliding = abs(rect.width - w) > .5f || abs(rect.height - h) > .5f
                val s = if (gliding) maxOf(rect.width / w, rect.height / h).coerceIn(.05f, 20f) else 1f
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = s
                scaleY = s
                clip = gliding
                if (gliding) this.shape = TileBoundsShape(rect.width / s, rect.height / s)
            }
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .zIndex(if (item.playing) 1f else 0f)
            .padding(1.5.dp)
            .graphicsLayer {
                shadowElevation = 10.dp.toPx() * lift
                this.shape = shape
                clip = false
            }
            .clip(shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                bounds?.let { launch?.invoke(it) }
                onClick()
            },
    ) {
        TrackArtwork(item.cover, Modifier.fillMaxSize(), 0.dp, decodeEdge = if (big) 512 else 256)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = if (feed != null) .45f else .16f),
                    .38f to Color.Black.copy(alpha = if (feed != null) .40f else 0f),
                    1f to Color.Black.copy(alpha = .78f),
                ),
            ),
        )
        Text(
            number.toString().padStart(2, '0'),
            Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 5.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = .72f),
        )
        if (feed != null) {
            TileLyrics(
                feed,
                Modifier.fillMaxWidth().fillMaxHeight(.62f).padding(start = 10.dp, end = 10.dp, top = 20.dp),
                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = if (span >= 3) 18.sp else 14.sp),
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 8.dp, bottom = if (item.playing) 11.dp else 7.dp)) {
            Text(
                item.title,
                fontSize = if (span >= 3) 20.sp else if (big) 15.sp else 12.sp,
                lineHeight = if (span >= 3) 24.sp else if (big) 18.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(item.subtitle, fontSize = if (big) 11.sp else 9.sp, color = Color.White.copy(alpha = .7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.playing) {
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = .45f)), contentAlignment = Alignment.Center) {
                EqualizerBars(playback.isPlaying, Color.White, Modifier.size(11.dp))
            }
            TileProgress(playback, Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp))
            Box(Modifier.fillMaxSize().border(1.5.dp, Color.White.copy(alpha = .75f), shape))
        }
    }
}

@Composable
private fun TileProgress(playback: PlaybackState, modifier: Modifier) {
    val fraction = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playback) {
        while (true) {
            val duration = playback.durationMs
            fraction.floatValue = if (duration > 0) (playback.estimatedPosition().toFloat() / duration).coerceIn(0f, 1f) else 0f
            if (!playback.isPlaying) break
            kotlinx.coroutines.delay(500)
        }
    }
    Box(modifier.height(2.dp).clip(CircleShape).background(Color.White.copy(alpha = .25f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.floatValue).background(Color.White))
    }
}

@Composable
internal fun TileLyrics(feed: LyricFeed, modifier: Modifier, style: TextStyle) {
    val document = feed.document
    if (!document.isSynchronized || document.lines.isEmpty()) return
    val index by remember(document) { derivedStateOf { document.lineIndexAt(feed.clock.value) } }
    val listState = rememberLazyListState()
    var viewport by remember { mutableIntStateOf(0) }
    LaunchedEffect(index, viewport) {
        if (index >= 0) listState.animateScrollToItem(index, scrollOffset = -(viewport * .28f).toInt())
    }
    LazyColumn(
        modifier
            .onSizeChanged { viewport = it.height }
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        .16f to Color.Black,
                        .82f to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                )
            },
        state = listState,
        userScrollEnabled = false,
    ) {
        itemsIndexed(document.lines, key = { i, _ -> i }) { i, line ->
            val distance = if (index < 0) 2 else kotlin.math.abs(i - index)
            val current = i == index
            val alpha by animateFloatAsState(
                when {
                    current -> 1f
                    distance == 1 -> .52f
                    distance == 2 -> .34f
                    else -> .22f
                },
                tween(320),
                label = "tile-line",
            )
            val scale by animateFloatAsState(if (current) 1f else .94f, spring(dampingRatio = .8f, stiffness = 260f), label = "tile-scale")
            val blur = if (current || android.os.Build.VERSION.SDK_INT < 31) 0.dp else (distance.coerceAtMost(3) * .7f).dp
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .graphicsLayer {
                        this.alpha = alpha
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f)
                    }
                    .then(if (blur > 0.dp) Modifier.blur(blur) else Modifier),
            ) {
                if (current) {
                    KaraokeLine(line, feed.clock, style, Modifier)
                } else {
                    Text(line.text, style = style, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                val translation = line.translation?.takeIf { feed.showTranslation && it.isNotBlank() }
                if (translation != null && (current || distance <= 1)) {
                    Text(
                        translation,
                        Modifier.padding(top = 2.dp),
                        style = style.copy(fontSize = style.fontSize * .72f, fontWeight = FontWeight.Medium),
                        color = Color.White.copy(alpha = if (current) .78f else .6f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun KaraokeLine(line: LyricLine, clock: State<Long>, style: TextStyle, modifier: Modifier) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val spans = remember(line) { wordSpans(line) }
    val density = LocalDensity.current
    val feather = with(density) { 14.dp.toPx() }
    Text(
        line.text,
        modifier.drawWithContent {
            drawContent()
            val result = layout ?: return@drawWithContent
            val now = clock.value
            val sung = sungChars(line, spans, now)
            val glow = heldGlow(line, now)
            for (row in 0 until result.lineCount) {
                val start = result.getLineStart(row)
                val end = result.getLineEnd(row)
                if (sung <= start) break
                val rowLeft = result.getLineLeft(row)
                val rowRight = result.getLineRight(row)
                val edge = if (sung >= end) {
                    rowRight + feather
                } else {
                    val char = sung.toInt().coerceIn(start, end - 1)
                    val x0 = result.getHorizontalPosition(char, true)
                    val x1 = result.getHorizontalPosition((char + 1).coerceAtMost(end), true)
                    x0 + (x1 - x0) * (sung - char)
                }
                clipRect(left = rowLeft - feather, top = result.getLineTop(row), right = rowRight + feather, bottom = result.getLineBottom(row)) {
                    drawText(
                        result,
                        brush = Brush.horizontalGradient(
                            0f to Color.White,
                            1f to Color.White.copy(alpha = 0f),
                            startX = edge - feather,
                            endX = edge + feather * .35f,
                        ),
                        shadow = if (glow > 0f) androidx.compose.ui.graphics.Shadow(Color.White.copy(alpha = .55f * glow), blurRadius = 18f * glow) else null,
                    )
                }
            }
        },
        style = style,
        color = Color.White.copy(alpha = .38f),
        onTextLayout = { layout = it },
    )
}

private fun heldGlow(line: LyricLine, timeMs: Long): Float {
    val word = line.words.firstOrNull { timeMs in it.startMs until it.endMs } ?: return 0f
    val length = word.endMs - word.startMs
    if (length < 1_000) return 0f
    val t = (timeMs - word.startMs).toFloat() / length
    return (kotlin.math.sin(t * Math.PI).toFloat()).coerceIn(0f, 1f)
}
private fun wordSpans(line: LyricLine): List<IntRange> {
    var cursor = 0
    return line.words.map { word ->
        val text = word.text
        val found = line.text.indexOf(text.trim().ifEmpty { text }, cursor).takeIf { it >= 0 } ?: cursor
        val end = (found + text.trimEnd().length).coerceAtMost(line.text.length)
        cursor = end
        found until end
    }
}

private fun sungChars(line: LyricLine, spans: List<IntRange>, timeMs: Long): Float {
    if (line.words.isEmpty()) return if (timeMs >= line.startMs) line.text.length.toFloat() else 0f
    var sung = 0f
    line.words.forEachIndexed { i, word ->
        val span = spans[i]
        when {
            timeMs >= word.endMs -> sung = (span.last + 1).toFloat()
            timeMs >= word.startMs -> {
                val fraction = (timeMs - word.startMs).toFloat() / (word.endMs - word.startMs).coerceAtLeast(1)
                return span.first + (span.last + 1 - span.first) * fraction.coerceIn(0f, 1f)
            }
            else -> return sung
        }
    }
    return line.text.length.toFloat()
}
