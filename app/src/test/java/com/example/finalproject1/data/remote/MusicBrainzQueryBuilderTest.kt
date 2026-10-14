package com.example.finalproject1.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MusicBrainzQueryBuilderTest {
    @Test fun `explore constrains groups to Japan and genre`() {
        assertEquals("type:group AND country:JP AND tag:\"rock\"", MusicBrainzQueryBuilder.explore("rock"))
    }

    @Test fun `name search does not require country and escapes quotes`() {
        val query = MusicBrainzQueryBuilder.nameSearch("Band \"A\"")
        assertEquals("artist:\"Band \\\"A\\\"\" AND type:group", query)
        assertFalse(query.contains("country:JP"))
    }
}
