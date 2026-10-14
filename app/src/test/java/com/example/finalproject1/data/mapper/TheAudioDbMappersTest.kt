package com.example.finalproject1.data.mapper

import com.example.finalproject1.data.remote.AudioDbAlbumDto
import com.example.finalproject1.data.remote.AudioDbArtistDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TheAudioDbMappersTest {
    @Test
    fun `artist maps TheAudioDB identity metadata and separate artwork`() {
        val band = AudioDbArtistDto(
            idArtist = "122172",
            strArtist = "MUCC",
            intFormedYear = "1997",
            strStyle = "Metal/Rock",
            strCountry = "Japan",
            strCountryCode = "JP",
            strArtistLogo = "https://images/logo.png",
            strArtistThumb = "https://images/photo.jpg",
            strBiography = "Known source text.",
            strMusicBrainzID = "0da0f48c-3689-4c38-bf4a-c5b50d516689",
        ).toBand()

        assertEquals("122172", band.id)
        assertEquals(listOf("Metal", "Rock"), band.genres)
        assertEquals("https://images/logo.png", band.imageUrl)
        assertEquals("https://images/photo.jpg", band.profileImageUrl)
        assertEquals("Known source text.", band.summarySourceText)
        assertFalse(band.ended!!)
    }

    @Test
    fun `album maps TheAudioDB cover and source page`() {
        val album = AudioDbAlbumDto(
            idAlbum = "2173368",
            strAlbum = "Album",
            intYearReleased = "2004",
            strReleaseFormat = "Album",
            strAlbumThumb = "https://images/album.jpg",
        ).toAlbum()

        assertEquals("https://images/album.jpg", album.coverUrl)
        assertEquals("https://www.theaudiodb.com/album/2173368", album.sourceUrl)
    }
}
