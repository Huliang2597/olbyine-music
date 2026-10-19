package com.olbyine.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmllSearchRankingTest {
    private val query = LyricsQuery(
        title = "ME!",
        artist = "Taylor Swift",
        album = "Lover",
        durationSeconds = 185.0,
        filename = "ME!.mp3",
    )

    @Test
    fun picksMatchingTitleAndArtistFromMetadataArrays() {
        val ranked = rankAmllSearchHits(
            query,
            listOf(
                AmllSearchHit(
                    id = "269710089745311",
                    titles = listOf("ME! (feat. Brendon Urie)", "ME!"),
                    artists = listOf("Brendon Urie", "Taylor Swift"),
                    albums = listOf("Lover"),
                ),
            ),
        )

        assertEquals(1, ranked.size)
        assertEquals("ME!", ranked.single().title)
        assertEquals("Taylor Swift", ranked.single().artist)
        assertTrue(ranked.single().score >= 0.9)
    }

    @Test
    fun rejectsUnrelatedSearchHitsBeforeDownloadingLyrics() {
        val ranked = rankAmllSearchHits(
            query,
            listOf(
                AmllSearchHit(
                    id = "1",
                    titles = listOf("An unrelated song"),
                    artists = listOf("Someone else"),
                    albums = listOf("Lover"),
                ),
            ),
        )

        assertTrue(ranked.isEmpty())
    }

    @Test
    fun capsDownloadsAtThreeBestDistinctLyrics() {
        val hits = (1..6).map { index ->
            AmllSearchHit(
                id = index.toString(),
                titles = listOf("ME!"),
                artists = listOf("Taylor Swift"),
                albums = listOf("Lover"),
            )
        }

        assertEquals(3, rankAmllSearchHits(query, hits).size)
    }
}
