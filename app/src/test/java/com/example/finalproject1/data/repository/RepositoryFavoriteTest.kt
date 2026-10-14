package com.example.finalproject1.data.repository

import com.example.finalproject1.data.local.FavoriteBandDao
import com.example.finalproject1.data.local.FavoriteBandEntity
import com.example.finalproject1.data.model.Band
import com.example.finalproject1.data.remote.ArtistDto
import com.example.finalproject1.data.remote.ArtistSearchResponseDto
import com.example.finalproject1.data.remote.AreaDto
import com.example.finalproject1.data.remote.MusicBrainzApi
import com.example.finalproject1.data.remote.MusicBrainzRateLimiter
import com.example.finalproject1.data.remote.ReleaseGroupResponseDto
import com.example.finalproject1.data.remote.RecordingSearchResponseDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryFavoriteTest {
    @Test fun `saving and removing a favorite updates observable repository state`() = runTest {
        val dao = FakeFavoriteDao()
        val repository = DefaultMusicRepository(FakeApi(), dao, MusicBrainzRateLimiter(0))
        val band = Band(id = "id-1", name = "Band", genres = listOf("rock"))

        repository.setFavorite(band, true)
        assertEquals("Band", repository.observeFavorites().first().single().name)
        assertTrue(repository.observeFavorites().first().single().isFavorite)

        repository.setFavorite(band, false)
        assertTrue(repository.observeFavorites().first().isEmpty())
    }

    @Test fun `band details are fetched after a search result`() = runTest {
        val api = FakeApi()
        val repository = DefaultMusicRepository(api, FakeFavoriteDao(), MusicBrainzRateLimiter(0))

        repository.searchBands("Band", "rock")
        val detail = repository.getBand("id-1")

        assertEquals(1, api.detailCalls)
        assertEquals("Tokyo", detail.area)
    }
}

private class FakeFavoriteDao : FavoriteBandDao {
    private val rows = MutableStateFlow<List<FavoriteBandEntity>>(emptyList())
    override fun observeAll(): Flow<List<FavoriteBandEntity>> = rows
    override fun observeById(artistId: String): Flow<FavoriteBandEntity?> = MutableStateFlow(rows.value.firstOrNull { it.artistId == artistId })
    override suspend fun getById(artistId: String) = rows.value.firstOrNull { it.artistId == artistId }
    override suspend fun getAllIds() = rows.value.map { it.artistId }
    override suspend fun upsert(entity: FavoriteBandEntity) { rows.value = rows.value.filterNot { it.artistId == entity.artistId } + entity }
    override suspend fun deleteById(artistId: String) { rows.value = rows.value.filterNot { it.artistId == artistId } }
}

private class FakeApi : MusicBrainzApi {
    var detailCalls = 0
    override suspend fun searchArtists(query: String, format: String, limit: Int) = ArtistSearchResponseDto(listOf(ArtistDto("id-1", "Band")))
    override suspend fun getArtist(artistId: String, includes: String, format: String): ArtistDto {
        detailCalls++
        return ArtistDto(artistId, "Band", area = AreaDto("Tokyo"))
    }
    override suspend fun getReleaseGroups(artistId: String, type: String, format: String, limit: Int) = ReleaseGroupResponseDto()
    override suspend fun searchReleaseGroups(query: String, format: String, limit: Int) = ReleaseGroupResponseDto()
    override suspend fun searchRecordings(query: String, format: String, limit: Int) = RecordingSearchResponseDto()
}
