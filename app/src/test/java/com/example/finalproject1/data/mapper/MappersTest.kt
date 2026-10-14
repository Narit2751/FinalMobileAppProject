package com.example.finalproject1.data.mapper

import com.example.finalproject1.data.remote.AreaDto
import com.example.finalproject1.data.remote.ArtistRelationDto
import com.example.finalproject1.data.remote.ArtistDto
import com.example.finalproject1.data.remote.LifeSpanDto
import com.example.finalproject1.data.remote.RelatedArtistDto
import com.example.finalproject1.data.remote.ReleaseGroupDto
import com.example.finalproject1.data.remote.TagDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MappersTest {
    @Test fun `artist DTO maps metadata and ranked unique genres`() {
        val dto = ArtistDto(
            id = "artist-1", name = "Test Band", country = "JP", area = AreaDto("Tokyo"),
            lifeSpan = LifeSpanDto(begin = "2001", ended = false),
            genres = listOf(TagDto("Rock", 8)), tags = listOf(TagDto("rock", 4), TagDto("Shoegaze", 7)),
        )

        val band = dto.toBand()

        assertEquals("artist-1", band.id)
        assertEquals("Tokyo", band.area)
        assertEquals("2001", band.beginDate)
        assertFalse(band.ended!!)
        assertEquals(listOf("Rock", "Shoegaze"), band.genres)
    }

    @Test fun `artist relationships map member roles and current status`() {
        val dto = ArtistDto(
            id = "band-1",
            name = "Test Band",
            relations = listOf(
                ArtistRelationDto(
                    type = "member of band",
                    direction = "backward",
                    artist = RelatedArtistDto("person-1", "Aya", "Person"),
                    attributes = listOf("guitar", "principal"),
                    ended = false,
                ),
                ArtistRelationDto(
                    type = "member of band",
                    direction = "backward",
                    artist = RelatedArtistDto("person-2", "Ren", "Person"),
                    attributes = listOf("drums"),
                    ended = true,
                ),
            ),
        )

        val members = dto.toBand().members

        assertEquals(listOf("Aya", "Ren"), members.map { it.name })
        assertEquals(listOf("guitar"), members.first().roles)
        assertEquals(true, members.first().isCurrent)
        assertEquals(false, members.last().isCurrent)
    }

    @Test fun `release group maps MusicBrainz source and Cover Art Archive image`() {
        val album = ReleaseGroupDto(
            id = "release-group-id",
            title = "Album",
            primaryType = "Album",
            firstReleaseDate = "2020-01-01",
        ).toAlbum()

        assertEquals(
            "https://coverartarchive.org/release-group/release-group-id/front-500",
            album.imageUrl,
        )
        assertEquals(
            "https://musicbrainz.org/release-group/release-group-id",
            album.sourceUrl,
        )
    }
}
