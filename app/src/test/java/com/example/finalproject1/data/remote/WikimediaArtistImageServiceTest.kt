package com.example.finalproject1.data.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WikimediaArtistImageServiceTest {
    @Test
    fun `resolves and caches a Commons band photo by MusicBrainz artist id`() = runTest {
        val wikidata = FakeWikidataApi()
        val commons = FakeCommonsApi()
        val service = WikimediaArtistImageService(wikidata, commons)
        val artistId = "0da0f48c-3689-4c38-bf4a-c5b50d516689"

        val first = service.imagesForArtistIds(listOf(artistId))
        val second = service.imagesForArtistIds(listOf(artistId))

        assertEquals("https://upload.wikimedia.org/mucc-thumb.jpg", first.getValue(artistId).imageUrl)
        assertEquals("https://commons.wikimedia.org/wiki/File:MUCC.jpg", first.getValue(artistId).sourceUrl)
        assertEquals(first, second)
        assertEquals(1, wikidata.calls)
        assertEquals(1, commons.calls)
        check(wikidata.lastQuery.contains("wdt:P18"))
        check(!wikidata.lastQuery.contains("wdt:P154"))
    }

    @Test
    fun `logo mode requests a verified logo rather than a profile photo`() = runTest {
        val wikidata = FakeWikidataApi()
        val service = WikimediaArtistImageService(
            api = wikidata,
            commonsApi = FakeCommonsApi(),
            imageProperty = WikimediaImageProperty.LOGO,
        )

        service.imagesForArtistIds(listOf("0da0f48c-3689-4c38-bf4a-c5b50d516689"))

        check(wikidata.lastQuery.contains("wdt:P154"))
        check(!wikidata.lastQuery.contains("wdt:P18"))
    }
}

private class FakeWikidataApi : WikidataSparqlApi {
    var calls = 0
    var lastQuery = ""

    override suspend fun findArtistImages(query: String, format: String): WikidataSparqlResponseDto {
        calls++
        lastQuery = query
        return WikidataSparqlResponseDto(
            WikidataResultsDto(
                listOf(
                    WikidataImageBindingDto(
                        mbid = SparqlValueDto("0da0f48c-3689-4c38-bf4a-c5b50d516689"),
                        image = SparqlValueDto("http://commons.wikimedia.org/wiki/Special:FilePath/MUCC.jpg"),
                    )
                )
            )
        )
    }
}

private class FakeCommonsApi : WikimediaCommonsApi {
    var calls = 0

    override suspend fun resolveImages(
        action: String,
        property: String,
        imageInfoProperties: String,
        thumbnailWidth: Int,
        titles: String,
        format: String,
        formatVersion: Int,
    ): CommonsQueryResponseDto {
        calls++
        return CommonsQueryResponseDto(
            CommonsQueryDto(
                listOf(
                    CommonsPageDto(
                        title = "File:MUCC.jpg",
                        imageinfo = listOf(
                            CommonsImageInfoDto(
                                url = "https://upload.wikimedia.org/mucc.jpg",
                                thumburl = "https://upload.wikimedia.org/mucc-thumb.jpg",
                                descriptionurl = "https://commons.wikimedia.org/wiki/File:MUCC.jpg",
                            )
                        ),
                    )
                )
            )
        )
    }
}
