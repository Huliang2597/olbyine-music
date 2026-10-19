package com.olbyine.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParserTest {
    @Test
    fun parsesMultipleLrcTimestampsAndTranslation() {
        val document = LyricsParser.parse(
            """
            [00:01.00][00:03.00]第一行
            [00:01.00]First line
            [00:05.25]第二行
            """.trimIndent(),
        )

        assertEquals(listOf(1_000L, 3_000L, 5_250L), document.lines.map { it.startMs })
        assertEquals("First line", document.lines.first().translation)
        assertEquals(1, document.lineIndexAt(4_000L))
    }

    @Test
    fun parsesYrcWordTiming() {
        val document = LyricsParser.parse("[1000,2400](1000,600,0)你(1600,800,0)好")
        val line = document.lines.single()

        assertEquals("你好", line.text)
        assertEquals(2, line.words.size)
        assertEquals(2_400L, line.words.last().endMs)
    }

    @Test
    fun parsesEnhancedLrcAndTtml() {
        val enhanced = LyricsParser.parse("[00:01.00]<00:01.00>Hello <00:01.50>world")
        assertEquals(2, enhanced.lines.single().words.size)
        assertEquals(1_500L, enhanced.lines.single().words.first().endMs)

        val ttml = LyricsParser.parse(
            """<tt><body><p begin="00:00:02.000" end="00:00:04.000"><span begin="2s" end="3s">Hi </span><span begin="3s" end="4s">there</span></p></body></tt>""",
        )
        assertEquals(2_000L, ttml.lines.single().startMs)
        assertEquals("Hi there", ttml.lines.single().text)
        assertTrue(ttml.lines.single().words.isNotEmpty())
    }

    @Test
    fun parsesAppleWordTtmlWithHeadTranslationAndInlineRomanization() {
        val document = LyricsParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
                itunes:timing="Word">
              <head><metadata>
                <itunes:translations>
                  <itunes:translation xml:lang="zh-Hans">
                    <itunes:text for="L1">你好世界</itunes:text>
                  </itunes:translation>
                </itunes:translations>
              </metadata></head>
              <body><div>
                <p begin="1s" end="3s" itunes:key="L1">
                  <span begin="1s" end="2s">Hello </span><span begin="2s" dur="1s">world</span>
                  <span ttm:role="x-roman">Ni hao shi jie</span>
                </p>
              </div></body>
            </tt>
            """.trimIndent(),
        )

        val line = document.lines.single()
        assertEquals("Hello world", line.text)
        assertEquals("你好世界", line.translation)
        assertEquals("Ni hao shi jie", line.romanization)
        assertEquals(listOf(1_000L, 2_000L), line.words.map { it.startMs })
        assertEquals(listOf(2_000L, 3_000L), line.words.map { it.endMs })
    }

    @Test
    fun mergesSeparateWordTranslationAndRomanizationTimelines() {
        val document = LyricsParser.parseBundle(
            lineLyrics = "[00:01.00]你好",
            wordLyrics = "[00:01.00]<00:01.00>你<00:01.50>好",
            translationLyrics = "[00:01.00]Hello",
            romanLyrics = "[00:01.00]Ni hao",
        )

        assertEquals("你好", document.lines.single().text)
        assertEquals("Hello", document.lines.single().translation)
        assertEquals("Ni hao", document.lines.single().romanization)
        assertEquals(2, document.lines.single().words.size)
    }

    @Test
    fun keepsPlainLyricsVisibleWithoutInventingPlaybackTiming() {
        val document = LyricsParser.parse("第一行\n第二行")

        assertTrue(document.lines.isNotEmpty())
        assertTrue(!document.isSynchronized)
        assertEquals(-1, document.lineIndexAt(10_000L))
    }

    @Test
    fun fallsBackToTimedLineLyricsWhenWordPayloadIsPlain() {
        val document = LyricsParser.parseBundle(
            lineLyrics = "[00:01.00]有效歌词",
            wordLyrics = "不完整的逐字数据",
            translationLyrics = null,
            romanLyrics = null,
        )

        assertTrue(document.isSynchronized)
        assertEquals("有效歌词", document.lines.single().text)
    }

    @Test
    fun keepsLineTimelineWhenTtmlContainsNoTimedWords() {
        val document = LyricsParser.parseBundle(
            lineLyrics = "[00:01.00]正确版本",
            wordLyrics = "<tt><body><p begin=\"1s\" end=\"2s\">其他版本</p></body></tt>",
            translationLyrics = null,
            romanLyrics = null,
        )

        assertEquals("正确版本", document.lines.single().text)
        assertTrue(document.lines.single().words.isEmpty())
    }

    @Test
    fun derivesMissingTtmlWordEndsFromNextWordAndLine() {
        val document = LyricsParser.parse(
            """<tt xmlns:itunes="http://music.apple.com/lyric-ttml-internal" itunes:timing="Word"><body><p begin="1s" end="4s"><span begin="1s">你</span><span begin="2s">好</span><span begin="3s">啊</span></p></body></tt>""",
        )

        val words = document.lines.single().words
        assertEquals(listOf(1_000L, 2_000L, 3_000L), words.map { it.startMs })
        assertEquals(listOf(2_000L, 3_000L, 4_000L), words.map { it.endMs })
    }
}
