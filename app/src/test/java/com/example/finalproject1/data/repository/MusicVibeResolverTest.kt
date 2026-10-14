package com.example.finalproject1.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicVibeResolverTest {
    @Test fun `explicit genre takes priority over vibe hint`() {
        assertEquals("punk", MusicVibeResolver.genreFor(listOf("dark", "punk")))
    }

    @Test fun `vibes select a broad factual candidate tag`() {
        assertEquals("rock", MusicVibeResolver.genreFor(listOf("energetic")))
        assertEquals("jazz", MusicVibeResolver.genreFor(listOf("relaxing")))
        assertEquals("alternative", MusicVibeResolver.genreFor(listOf("emotional")))
        assertEquals("metal", MusicVibeResolver.genreFor(listOf("dark")))
        assertEquals("pop", MusicVibeResolver.genreFor(listOf("happy")))
        assertEquals("electronic", MusicVibeResolver.genreFor(listOf("focused")))
    }

    @Test fun `related song and album queries stay within verified Japanese artist ids`() {
        val ids = listOf("artist-one", "artist-two")
        assertEquals(
            "(arid:artist-one OR arid:artist-two)",
            MusicRecommendationQueries.forJapaneseArtists(ids),
        )
        assertEquals(
            "(arid:artist-one OR arid:artist-two) AND primarytype:album",
            MusicRecommendationQueries.forJapaneseArtists(ids, albumsOnly = true),
        )
    }
}
