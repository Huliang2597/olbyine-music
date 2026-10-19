package com.olbyine.music.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.olbyine.music.data.LyricLine
import com.olbyine.music.data.LyricsDocument
import com.olbyine.music.playback.AudioEnergy
import com.olbyine.music.playback.PlaybackState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal enum class MvStyle(val label: String) { BOUNCE("跃动"), TILT("倾斜"), EDITORIAL("排版") }

private class MvGlyph(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val space: Boolean,
    var x: Float = 0f,
    var y: Float = 0f,
    var row: Int = 0,
    val jitterAngle: Float,
    val jitterY: Float,
    val jitterScale: Float,
)

private class MvLayout(val glyphs: List<MvGlyph>, val fontPx: Float, val rows: Int, val blockHeight: Float)

private class MvShape(val kind: Int, val x: Float, val y: Float, val size: Float, val spin: Float, val driftX: Float, val driftY: Float, val phase: Float, val filled: Boolean, val accent: Boolean)

private fun seeded(seed: Int): Float {
    var x = seed * 1103515245 + 12345
    x = x xor (x ushr 13)
    x *= 0x5bd1e995
    x = x xor (x ushr 15)
    return (x and 0x7fffffff) / 2147483647f
}

private fun easeOut(t: Float): Float {
    val k = 1f - t.coerceIn(0f, 1f)
    return 1f - k * k * k
}

private fun graphemes(text: String): List<String> {
    val out = ArrayList<String>(text.length)
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val n = Character.charCount(cp)
        out += text.substring(i, i + n)
        i += n
    }
    return out
}

private fun isWide(s: String): Boolean {
    val c = s.codePointAt(0)
    return Character.UnicodeScript.of(c).let {
        it == Character.UnicodeScript.HAN || it == Character.UnicodeScript.HIRAGANA || it == Character.UnicodeScript.KATAKANA || it == Character.UnicodeScript.HANGUL
    }
}

private fun timedGlyphs(line: LyricLine, nextStart: Long, lineSeed: Int): List<MvGlyph> {
    val chars = graphemes(line.text)
    val times = LongArray(chars.size) { -1 }
    val ends = LongArray(chars.size) { -1 }
    if (line.words.isNotEmpty()) {
        var cursor = 0
        for (word in line.words) {
            val token = graphemes(word.text)
            val visible = token.count { it.isNotBlank() }.coerceAtLeast(1)
            var k = 0
            for (piece in token) {
                while (cursor < chars.size && chars[cursor].isBlank() && piece.isNotBlank()) cursor++
                if (cursor >= chars.size) break
                if (piece.isNotBlank()) {
                    val span = (word.endMs - word.startMs).coerceAtLeast(1)
                    times[cursor] = word.startMs + span * k / visible
                    ends[cursor] = word.startMs + span * (k + 1) / visible
                    k++
                }
                cursor++
            }
        }
    }
    val end = if (line.endMs > line.startMs) line.endMs else nextStart.takeIf { it > line.startMs } ?: (line.startMs + 3_000)
    val visible = chars.count { it.isNotBlank() }.coerceAtLeast(1)
    var seen = 0
    return chars.mapIndexed { index, text ->
        val space = text.isBlank()
        val start: Long
        val finish: Long
        if (times[index] >= 0) {
            start = times[index]
            finish = ends[index]
        } else {
            val span = (end - line.startMs).coerceAtLeast(1)
            start = line.startMs + span * seen / visible
            finish = line.startMs + span * (seen + 1) / visible
        }
        if (!space) seen++
        val seed = lineSeed * 131 + index * 17
        MvGlyph(
            text, start, finish, space,
            jitterAngle = (seeded(seed) - .5f) * 14f,
            jitterY = (seeded(seed + 3) - .5f) * .16f,
            jitterScale = .9f + seeded(seed + 7) * .22f,
        )
    }
}

private fun layoutGlyphs(
    glyphs: List<MvGlyph>,
    measurer: TextMeasurer,
    style: TextStyle,
    width: Float,
    fontPx: Float,
    alignCenter: Boolean,
): MvLayout {
    val advances = glyphs.map { if (it.space) fontPx * .32f else measurer.measure(it.text, style).size.width.toFloat() }
    val units = ArrayList<IntRange>()
    var i = 0
    while (i < glyphs.size) {
        if (!glyphs[i].space && !isWide(glyphs[i].text)) {
            var j = i
            while (j + 1 < glyphs.size && !glyphs[j + 1].space && !isWide(glyphs[j + 1].text)) j++
            units += i..j
            i = j + 1
        } else {
            units += i..i
            i++
        }
    }
    val rows = ArrayList<MutableList<IntRange>>()
    var row = ArrayList<IntRange>()
    var rowWidth = 0f
    for (unit in units) {
        val w = unit.sumOf { advances[it].toDouble() }.toFloat()
        if (rowWidth + w > width && row.isNotEmpty()) {
            rows += row
            row = ArrayList()
            rowWidth = 0f
            if (glyphs[unit.first].space) continue
        }
        row += unit
        rowWidth += w
    }
    if (row.isNotEmpty()) rows += row
    val lineHeight = fontPx * 1.32f
    rows.forEachIndexed { r, units ->
        val indices = units.flatMap { it.toList() }.let { list ->
            var s = 0
            var e = list.size
            while (s < e && glyphs[list[s]].space) s++
            while (e > s && glyphs[list[e - 1]].space) e--
            list.subList(s, e)
        }
        val total = indices.sumOf { advances[it].toDouble() }.toFloat()
        var x = if (alignCenter) (width - total) / 2f else 0f
        for (idx in indices) {
            glyphs[idx].x = x
            glyphs[idx].y = r * lineHeight
            glyphs[idx].row = r
            x += advances[idx]
        }
    }
    return MvLayout(glyphs, fontPx, rows.size, rows.size * lineHeight)
}

@Composable
internal fun MvLyricsView(
    document: LyricsDocument,
    playback: PlaybackState,
    offsetMs: Long,
    active: Boolean,
    showTranslation: Boolean,
    style: MvStyle,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
) {
    val clock = rememberLyricClock(playback, offsetMs, active)
    val measurer = rememberTextMeasurer(cacheSize = 512)
    val reduce = LocalReduceMotion.current
    val primary = MaterialTheme.colorScheme.primary
    val accent = remember(primary) {
        if (primary.luminance() < .45f) androidx.compose.ui.graphics.lerp(primary, Color.White, .45f) else primary
    }
    val frame = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active, reduce) {
        if (!active) return@LaunchedEffect
        val start = System.nanoTime()
        while (true) withFrameNanos { now -> frame.floatValue = (now - start) / 1e9f }
    }
    val shapes = remember {
        List(16) { k ->
            MvShape(
                kind = (seeded(k * 7 + 1) * 6).toInt().coerceAtMost(5),
                x = seeded(k * 11 + 2),
                y = seeded(k * 13 + 3),
                size = .04f + seeded(k * 17 + 4) * .13f,
                spin = (seeded(k * 19 + 5) - .5f) * 24f,
                driftX = (seeded(k * 23 + 6) - .5f) * .018f,
                driftY = (seeded(k * 29 + 7) - .5f) * .012f,
                phase = seeded(k * 31 + 8) * 6.28f,
                filled = seeded(k * 37 + 9) > .55f,
                accent = seeded(k * 41 + 10) > .5f,
            )
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }) {
        val landscape = maxWidth > maxHeight
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val margin = widthPx * (if (landscape) .09f else .08f)
        val usable = widthPx - margin * 2
        val fontPx = when (style) {
            MvStyle.EDITORIAL -> if (landscape) min(heightPx * .11f, usable / 16f) else min(usable / 8.5f, heightPx * .065f)
            else -> if (landscape) min(heightPx * .15f, usable / 13f) else min(usable / 7.2f, heightPx * .075f)
        }
        val family = if (style == MvStyle.EDITORIAL) FontFamily.Serif else FontFamily.Default
        val mainStyle = TextStyle(
            fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { fontPx.toSp() },
            fontWeight = if (style == MvStyle.EDITORIAL) FontWeight.Bold else FontWeight.Black,
            fontFamily = family,
            fontStyle = if (style == MvStyle.TILT) FontStyle.Italic else FontStyle.Normal,
            color = Color.White,
        )
        val glowStyle = mainStyle.copy(color = accent, shadow = Shadow(accent.copy(alpha = .9f), Offset.Zero, fontPx * .35f))
        val smallStyle = mainStyle.copy(fontSize = mainStyle.fontSize * .42f, fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Normal)
        val subStyle = TextStyle(fontSize = (if (landscape) 16 else 15).sp, color = accent.copy(alpha = .85f), fontWeight = FontWeight.Medium)
        val lines = document.lines
        val layouts = remember(document, widthPx, heightPx, style) { HashMap<Int, MvLayout>() }
        fun layoutOf(index: Int): MvLayout? {
            val line = lines.getOrNull(index)?.takeIf { it.text.isNotBlank() } ?: return null
            return layouts.getOrPut(index) {
                val next = lines.getOrNull(index + 1)?.startMs ?: (line.startMs + 4_000)
                layoutGlyphs(timedGlyphs(line, next, index + 1), measurer, mainStyle, usable, fontPx, alignCenter = style != MvStyle.EDITORIAL)
            }
        }
        val glyphCache = remember(widthPx, heightPx, style, accent) { HashMap<String, TextLayoutResult>() }
        fun measured(text: String, glow: Boolean): TextLayoutResult =
            glyphCache.getOrPut((if (glow) "g" else "n") + text) { measurer.measure(text, if (glow) glowStyle else mainStyle) }
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.value
            val seconds = frame.floatValue
            val beat = if (reduce) 0f else AudioEnergy.bands.getOrElse(0) { 0f }.coerceIn(0f, 1f)
            drawBackdrop(shapes, seconds, beat, accent, reduce)
            val index = document.lineIndexAt(t)
            val current = if (index >= 0) layoutOf(index) else null
            when (style) {
                MvStyle.EDITORIAL -> drawEditorial(document, index, t, ::layoutOf, ::measured, measurer, smallStyle, margin, accent)
                else -> {
                    val previous = (index - 1 downTo maxOf(0, index - 2)).firstOrNull { lines[it].text.isNotBlank() }
                    if (previous != null && index >= 0) {
                        val exitT = ((t - lines[index].startMs) / 420f).coerceIn(0f, 1f)
                        if (exitT < 1f) layoutOf(previous)?.let { drawLineBlock(it, t, style, margin, 1f - exitT, exitT, ::measured, accent, tilt = style == MvStyle.TILT) }
                    }
                    if (current != null) drawLineBlock(current, t, style, margin, 1f, 0f, ::measured, accent, tilt = style == MvStyle.TILT)
                }
            }
            val translation = lines.getOrNull(index)?.translation?.takeIf { showTranslation && it.isNotBlank() }
            if (translation != null) {
                val result = measurer.measure(translation, subStyle, constraints = androidx.compose.ui.unit.Constraints(maxWidth = (size.width * .86f).toInt()))
                drawText(result, topLeft = Offset((size.width - result.size.width) / 2f, size.height * (if (landscape) .86f else .8f)))
            }
        }
    }
}

private fun DrawScope.drawBackdrop(shapes: List<MvShape>, seconds: Float, beat: Float, accent: Color, reduce: Boolean) {
    drawRect(Brush.verticalGradient(listOf(Color(0xCC0B0E1A), Color(0xB30E1222), Color(0xD9090B14))))
    val unit = min(size.width, size.height)
    val time = if (reduce) 0f else seconds
    for (shape in shapes) {
        val cx = ((shape.x + shape.driftX * time) % 1f + 1f) % 1f * size.width
        val cy = ((shape.y + shape.driftY * time) % 1f + 1f) % 1f * size.height
        val s = shape.size * unit * (1f + .08f * beat + .04f * sin(time * .8f + shape.phase))
        val color = (if (shape.accent) accent else Color.White).copy(alpha = if (shape.accent) .16f else .07f)
        rotate(shape.spin * time + shape.phase * 57f, Offset(cx, cy)) {
            val stroke = Stroke(width = unit * .004f)
            when (shape.kind) {
                0 -> if (shape.filled) drawCircle(color, s / 2f, Offset(cx, cy)) else drawCircle(color, s / 2f, Offset(cx, cy), style = stroke)
                1 -> {
                    val path = Path().apply {
                        moveTo(cx, cy - s / 2f)
                        lineTo(cx + s * .45f, cy + s * .35f)
                        lineTo(cx - s * .45f, cy + s * .35f)
                        close()
                    }
                    if (shape.filled) drawPath(path, color) else drawPath(path, color, style = stroke)
                }
                2 -> {
                    val w = s * .18f
                    drawRect(color, Offset(cx - s / 2f, cy - w / 2f), Size(s, w))
                    drawRect(color, Offset(cx - w / 2f, cy - s / 2f), Size(w, s))
                }
                3 -> {
                    val path = Path()
                    for (k in 0 until 10) {
                        val r = if (k % 2 == 0) s / 2f else s / 4.6f
                        val a = (k * PI / 5 - PI / 2).toFloat()
                        val px = cx + cos(a) * r
                        val py = cy + sin(a) * r
                        if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()
                    drawPath(path, color, style = stroke)
                }
                4 -> {
                    val path = Path()
                    val steps = 24
                    for (k in 0..steps) {
                        val px = cx - s / 2f + s * k / steps
                        val py = cy + sin(k / steps.toFloat() * 4f * PI.toFloat() + time) * s * .12f
                        if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    drawPath(path, color, style = stroke)
                }
                else -> drawRect(color, Offset(cx - s / 3f, cy - s / 3f), Size(s / 1.5f, s / 1.5f), style = if (shape.filled) androidx.compose.ui.graphics.drawscope.Fill else stroke)
            }
        }
    }
}

private fun DrawScope.drawLineBlock(
    layout: MvLayout,
    t: Long,
    style: MvStyle,
    margin: Float,
    alpha: Float,
    exit: Float,
    measured: (String, Boolean) -> TextLayoutResult,
    accent: Color,
    tilt: Boolean,
) {
    if (alpha <= .01f) return
    val top = (size.height - layout.blockHeight) / 2f - size.height * .04f - exit * layout.fontPx * 1.4f
    val pivot = Offset(size.width / 2f, top + layout.blockHeight / 2f)
    rotate(if (tilt) -5f else 0f, pivot) {
        for (glyph in layout.glyphs) {
            if (glyph.space) continue
            val enter = ((t - (glyph.startMs - 220)) / 260f).coerceIn(0f, 1f)
            val appear = easeOut(enter)
            val sung = when {
                t >= glyph.endMs -> 1f
                t <= glyph.startMs -> 0f
                else -> (t - glyph.startMs).toFloat() / (glyph.endMs - glyph.startMs).coerceAtLeast(1)
            }
            val singing = t in glyph.startMs until glyph.endMs
            val baseAlpha = (.34f + .66f * sung) * alpha * (.25f + .75f * appear)
            val jitter = style == MvStyle.BOUNCE
            val angle = (if (jitter) glyph.jitterAngle else 0f) * appear + (1f - appear) * (if (tilt) -18f else 22f) * (if (glyph.jitterAngle > 0) 1f else -1f)
            val lift = if (singing) -layout.fontPx * .08f * sin(sung * PI.toFloat()) else 0f
            val dy = (if (jitter) glyph.jitterY * layout.fontPx else 0f) + (1f - appear) * layout.fontPx * (if (tilt) -.55f else .45f) + lift
            val scale = (if (jitter) glyph.jitterScale else 1f) * (1.28f - .28f * appear) * (if (singing) 1.08f else 1f)
            val result = measured(glyph.text, false)
            val x = margin + glyph.x
            val y = top + glyph.y + dy
            val cx = x + result.size.width / 2f
            val cy = y + result.size.height / 2f
            rotate(angle, Offset(cx, cy)) {
                scale(scale, Offset(cx, cy)) {
                    val accentRow = tilt && glyph.row % 2 == 1
                    if (sung > 0f && (singing || sung >= 1f)) {
                        val glow = measured(glyph.text, true)
                        val k = if (singing) 1f else .0f
                        if (k > 0f) drawText(glow, topLeft = Offset(x, y), alpha = alpha * appear)
                    }
                    val tint = when {
                        singing -> accent
                        accentRow -> accent.copy(alpha = 1f)
                        else -> Color.White
                    }
                    drawText(result, color = tint, topLeft = Offset(x, y), alpha = baseAlpha.coerceIn(0f, 1f))
                }
            }
        }
    }
}

private fun DrawScope.drawEditorial(
    document: LyricsDocument,
    index: Int,
    t: Long,
    layoutOf: (Int) -> MvLayout?,
    measured: (String, Boolean) -> TextLayoutResult,
    measurer: TextMeasurer,
    smallStyle: TextStyle,
    margin: Float,
    accent: Color,
) {
    val lines = document.lines
    val current = layoutOf(index)
    val top = size.height * .38f - (current?.blockHeight ?: 0f) / 2f
    var above = top - smallStyle.fontSize.toPx() * .8f
    var k = index - 1
    var shown = 0
    while (k >= 0 && shown < 3) {
        val text = lines[k].text
        if (text.isNotBlank()) {
            val result = measurer.measure(text, smallStyle, constraints = androidx.compose.ui.unit.Constraints(maxWidth = (size.width - margin * 2).toInt()))
            above -= result.size.height + smallStyle.fontSize.toPx() * .35f
            drawText(result, color = Color.White, topLeft = Offset(margin, above), alpha = .42f - shown * .11f)
            shown++
        }
        k--
    }
    if (current != null) {
        val active = current.glyphs.indexOfFirst { !it.space && t >= it.startMs && t < it.endMs }
        if (active >= 0) {
            val g = current.glyphs[active]
            val r = measured(g.text, false)
            val box = current.fontPx * .12f
            drawRect(accent.copy(alpha = .85f), Offset(margin + g.x - box, top + g.y), Size(r.size.width + box * 2, r.size.height * .92f))
        }
        for ((i, glyph) in current.glyphs.withIndex()) {
            if (glyph.space) continue
            val sung = t >= glyph.startMs
            val result = measured(glyph.text, false)
            val tint = when {
                i == active -> Color(0xFF111111)
                sung -> accent
                else -> Color.White
            }
            val enter = easeOut(((t - (glyph.startMs - 300)) / 300f).coerceIn(0f, 1f))
            drawText(result, color = tint, topLeft = Offset(margin + glyph.x, top + glyph.y + (1f - enter) * current.fontPx * .2f), alpha = if (sung) 1f else .55f + .25f * enter)
        }
    }
    var below = top + (current?.blockHeight ?: 0f) + smallStyle.fontSize.toPx() * .9f
    k = index + 1
    shown = 0
    while (k < lines.size && shown < 3 && below < size.height * .76f) {
        val text = lines[k].text
        if (text.isNotBlank()) {
            val result = measurer.measure(text, smallStyle, constraints = androidx.compose.ui.unit.Constraints(maxWidth = (size.width - margin * 2).toInt()))
            drawText(result, color = Color.White, topLeft = Offset(margin, below), alpha = .3f - shown * .07f)
            below += result.size.height + smallStyle.fontSize.toPx() * .35f
            shown++
        }
        k++
    }
}

@Suppress("unused")
private fun unusedGuard(a: Float, b: Float) = max(abs(a), b)
