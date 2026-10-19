package com.olbyine.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineLyricsMatcherTest {
    @Test
    fun manualSearchKeepsLowConfidenceResultsSelectable() {
        val query = LyricsQuery(
            title = "Completely Different Local Title",
            artist = "Local Artist",
            album = "",
            durationSeconds = 210.0,
            filename = "track.mp3",
        )
        val remote = LyricsCandidate(
            id = "provider:1",
            title = "Remote Result",
            artist = "Someone Else",
            album = "",
            durationSeconds = 360.0,
            lyrics = "[00:01.00]Still a real selectable result",
            source = "lrclib",
        )

        val manual = LyricsMatcher.classify(query, listOf(remote), includeCandidates = true)
        val automatic = LyricsMatcher.classify(query, listOf(remote), includeCandidates = false)

        assertEquals(LyricsMatchStatus.REVIEW, manual.status)
        assertEquals("provider:1", manual.candidates.single().id)
        assertTrue(manual.candidates.single().confidence < 0.65)
        assertEquals(LyricsMatchStatus.NOT_FOUND, automatic.status)
    }
}
