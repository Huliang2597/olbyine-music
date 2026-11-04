package com.olbyine.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CueSheetTest {
    private val sample = """
        REM GENRE Game
        PERFORMER "小木曽雪菜(cv.米澤円)"
        TITLE "WHITE ALBUM2 - Original Soundtrack ～setsuna～"
        FILE "album.wav" WAVE
          TRACK 01 AUDIO
            TITLE "届かない恋 Live at Campus Fes"
            PERFORMER "小木曽雪菜(cv.米澤円)"
            INDEX 01 00:00:00
          TRACK 02 AUDIO
            TITLE "WHITE ALBUM Live at Campus Fes"
            INDEX 00 05:06:09
            INDEX 01 05:08:32
          TRACK 03 AUDIO
            TITLE "Feeling Heart"
            INDEX 01 09:48:49
    """.trimIndent()

    @Test
    fun parsesTracksAndOffsets() {
        val sheet = CueSheet.parse(sample)
        assertNotNull(sheet)
        sheet!!
        assertEquals("WHITE ALBUM2 - Original Soundtrack ～setsuna～", sheet.title)
        assertEquals("小木曽雪菜(cv.米澤円)", sheet.performer)
        assertEquals(3, sheet.tracks.size)
        assertEquals(0L, sheet.tracks[0].startMs)
        assertEquals(308426L, sheet.tracks[1].startMs)
        assertEquals(588653L, sheet.tracks[2].startMs)
        assertEquals("album.wav", sheet.tracks[0].fileName)
        assertEquals("届かない恋 Live at Campus Fes", sheet.tracks[0].title)
    }

    @Test
    fun fallsBackToIndexZeroWhenIndexOneMissing() {
        val text = """
            FILE "a.flac" WAVE
              TRACK 01 AUDIO
                TITLE "One"
                INDEX 00 00:00:00
              TRACK 02 AUDIO
                TITLE "Two"
                INDEX 01 00:02:00
        """.trimIndent()
        val sheet = CueSheet.parse(text)!!
        assertEquals(2, sheet.tracks.size)
        assertTrue(sheet.tracks[0].startMs < sheet.tracks[1].startMs)
        assertEquals(2000L, sheet.tracks[1].startMs)
    }

    @Test
    fun keepsTrackPerformerOverAlbumPerformer() {
        val text = """
            PERFORMER "Album Artist"
            TITLE "Album"
            FILE "b.wav" WAVE
              TRACK 01 AUDIO
                TITLE "Song"
                PERFORMER "Track Artist"
                INDEX 01 00:00:00
        """.trimIndent()
        val sheet = CueSheet.parse(text)!!
        assertEquals("Track Artist", sheet.tracks[0].performer)
    }

    @Test
    fun rejectsSheetsWithoutTracks() {
        assertEquals(null, CueSheet.parse("REM nothing here"))
    }
}
