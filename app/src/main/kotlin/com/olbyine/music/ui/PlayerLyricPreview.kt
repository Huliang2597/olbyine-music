package com.olbyine.music.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.olbyine.music.data.LyricLine
import com.olbyine.music.data.LyricsDocument
import com.olbyine.music.playback.PlaybackState
import kotlin.math.exp
import kotlin.math.floor

@Composable
internal fun PlayerLyricPreview(
    document: LyricsDocument,
    trackId: String?,
    playback: PlaybackState,
    offsetMs: Long,
    active: Boolean,
    onOpen: () -> Unit,
    showTranslation: Boolean = true,
) {
    val lines = if (document.trackId == null || document.trackId == trackId) document.lines else emptyList()
    Box(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        if (lines.isEmpty()) {
            Text("点按查看歌词", color = MaterialTheme.colorScheme.onBackground.copy(alpha = .5f), fontSize = 14.sp)
            return@Box
        }
        val clock = rememberLyricClock(playback, offsetMs, active)
        val index by remember(document) {
            derivedStateOf { if (document.isSynchronized) document.lineIndexAt(clock.value) else 0 }
        }
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                val forward = targetState >= initialState
                (slideInVertically(spring(dampingRatio = 0.9f, stiffness = 300f, visibilityThreshold = IntOffset(1, 1))) { if (forward) it / 2 else -it / 2 } +
                    fadeIn(tween(260)))
                    .togetherWith(
                        slideOutVertically(spring(dampingRatio = 0.9f, stiffness = 300f, visibilityThreshold = IntOffset(1, 1))) { if (forward) -it / 2 else it / 2 } +
                            fadeOut(tween(200)),
                    )
            },
            contentAlignment = Alignment.Center,
            label = "preview-line",
        ) { shown ->
            val line = lines.getOrNull(shown.coerceAtLeast(0))
            val next = lines.getOrNull(shown + 1)
            Column(
                Modifier.fillMaxSize().padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (line != null) {
                    SweepLine(
                        line = line,
                        clock = clock,
                        started = shown >= 0 || !document.isSynchronized,
                        synced = document.isSynchronized,
                    )
                    val secondary = line.translation?.takeIf { showTranslation && it.isNotBlank() }
                    if (secondary != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            secondary,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .66f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    } else if (next != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            next.text.ifBlank { "♪" },
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .38f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

private class Follow {
    var position = 0f
    var lastNanos = 0L
}

@Composable
private fun SweepLine(line: LyricLine, clock: State<Long>, started: Boolean, synced: Boolean) {
    LyricSweepLine(
        line = line,
        clock = clock,
        started = started,
        synced = synced,
        style = remember { TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold) },
        ink = MaterialTheme.colorScheme.onBackground,
        centered = true,
    )
}

@Composable
internal fun LyricSweepLine(
    line: LyricLine,
    clock: State<Long>,
    started: Boolean,
    synced: Boolean,
    style: TextStyle,
    ink: Color,
    centered: Boolean,
    unsungAlpha: Float = .46f,
    modifier: Modifier = Modifier,
) {
    val text = line.text.ifBlank { "♪" }
    val unsung = ink.copy(alpha = ink.alpha * unsungAlpha)
    val clear = ink.copy(alpha = 0f)
    val measurer = rememberTextMeasurer(cacheSize = 4)
    val layout = remember(text, measurer, style) {
        measurer.measure(text, style, overflow = TextOverflow.Clip, softWrap = false, maxLines = 1)
    }
    val ranges = remember(line) { wordRanges(line, text) }
    val follow = remember(text) { Follow() }
    val height = with(LocalDensity.current) { layout.size.height.toDp() }
    Spacer(
        modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                val time = clock.value
                val textWidth = layout.size.width.toFloat()
                val overflow = (textWidth - size.width).coerceAtLeast(0f)
                val sung = when {
                    !started -> 0f
                    ranges.isEmpty() -> if (!synced || time >= line.startMs) text.length.toFloat() else 0f
                    else -> sungCharacters(ranges, time, text.length)
                }
                val sungX = if (sung <= 0f) 0f else horizontalPosition(layout, sung, text.length)

                val target = if (overflow <= 0f) 0f else {
                    val focus = if (ranges.isNotEmpty()) sungX else {
                        val span = (line.endMs - line.startMs).coerceAtLeast(1L)
                        textWidth * ((time - line.startMs).toFloat() / span).coerceIn(0f, 1f)
                    }
                    (focus - size.width * .58f).coerceIn(0f, overflow)
                }
                val now = System.nanoTime()
                val elapsedMs = (now - follow.lastNanos) / 1_000_000f
                follow.position = if (follow.lastNanos == 0L || elapsedMs > 250f) {
                    target
                } else {
                    follow.position + (target - follow.position) * (1f - exp(-elapsedMs / 160f))
                }
                follow.lastNanos = now
                val scroll = follow.position.coerceIn(0f, overflow)
                val originX = when {
                    overflow > 0f -> -scroll
                    centered -> (size.width - textWidth) / 2f
                    else -> 0f
                }

                translate(originX, (size.height - layout.size.height) / 2f) {
                    drawText(layout, color = unsung)
                    when {
                        sung >= text.length -> drawText(layout, color = ink, alpha = ink.alpha)
                        sung > 0f -> {
                            val fade = 9.dp.toPx()
                            drawText(
                                layout,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(ink, clear),
                                    startX = sungX - fade,
                                    endX = sungX + fade,
                                ),
                                alpha = 1f,
                            )
                        }
                    }
                }
                if (overflow > 0f) {
                    val edge = 20.dp.toPx().coerceAtMost(size.width / 4f)
                    val leftFade = (scroll / edge).coerceIn(0f, 1f)
                    val rightFade = ((overflow - scroll) / edge).coerceIn(0f, 1f)
                    val stop = edge / size.width
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 1f - leftFade),
                            stop to Color.Black,
                            1f - stop to Color.Black,
                            1f to Color.Black.copy(alpha = 1f - rightFade),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
    )
}

private data class WordRange(val start: Int, val end: Int, val startMs: Long, val endMs: Long)

private fun wordRanges(line: LyricLine, text: String): List<WordRange> {
    if (line.words.isEmpty()) return emptyList()
    var cursor = 0
    return line.words.map { word ->
        val token = word.text.trim()
        var start = if (token.isEmpty()) cursor else text.indexOf(token, cursor)
        if (start < 0) start = cursor
        val end = (start + token.length).coerceIn(start, text.length)
        cursor = end
        WordRange(start, end, word.startMs, word.endMs.coerceAtLeast(word.startMs + 1))
    }
}

private fun sungCharacters(ranges: List<WordRange>, time: Long, length: Int): Float {
    if (time < ranges.first().startMs) return 0f
    for (range in ranges) {
        if (time < range.startMs) return range.start.toFloat()
        if (time < range.endMs) {
            val fraction = (time - range.startMs).toFloat() / (range.endMs - range.startMs)
            return range.start + (range.end - range.start) * fraction
        }
    }
    return length.toFloat()
}

private fun horizontalPosition(layout: TextLayoutResult, characters: Float, length: Int): Float {
    val index = floor(characters).toInt().coerceIn(0, length)
    val fraction = characters - index
    val x0 = layout.getHorizontalPosition(index, usePrimaryDirection = true)
    val x1 = if (index < length) {
        layout.getHorizontalPosition(index + 1, usePrimaryDirection = true)
    } else {
        layout.size.width.toFloat()
    }
    return x0 + (x1 - x0) * fraction
}

@Composable
internal fun rememberLyricClock(playback: PlaybackState, offsetMs: Long, active: Boolean): State<Long> {
    val clock = remember { mutableLongStateOf(playback.estimatedPosition() + offsetMs) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(
        playback.currentMediaId,
        playback.sampledAtElapsedMs,
        playback.isPlaying,
        playback.pendingSeekMs,
        playback.playbackSpeed,
        offsetMs,
        active,
    ) {
        clock.longValue = playback.estimatedPosition() + offsetMs
        if (!active || !playback.isPlaying || playback.pendingSeekMs != null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                withFrameMillis { }
                clock.longValue = playback.estimatedPosition() + offsetMs
            }
        }
    }
    return clock
}
