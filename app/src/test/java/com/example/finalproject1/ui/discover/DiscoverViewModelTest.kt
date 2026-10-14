package com.example.finalproject1.ui.discover

import com.example.finalproject1.data.model.Album
import com.example.finalproject1.data.model.Band
import com.example.finalproject1.data.repository.MusicRepository
import com.example.finalproject1.ui.common.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `emits loading then success`() = runTest(dispatcher) {
        val vm = DiscoverViewModel(FakeRepository(Result.success(listOf(Band("1", "Band")))), { true }, 0)
        assertTrue(vm.state.value is UiState.Loading)
        advanceUntilIdle()
        val content = vm.state.value as UiState.Content
        assertEquals("Band", content.data.single().name)
    }

    @Test fun `emits empty for no matches`() = runTest(dispatcher) {
        val vm = DiscoverViewModel(FakeRepository(Result.success(emptyList())), { true }, 0)
        advanceUntilIdle()
        assertTrue(vm.state.value is UiState.Empty)
    }

    @Test fun `emits friendly failure without raw exception`() = runTest(dispatcher) {
        val vm = DiscoverViewModel(FakeRepository(Result.failure(IllegalStateException("secret"))), { false }, 0)
        advanceUntilIdle()
        val error = vm.state.value as UiState.Error
        assertTrue(error.offline)
        assertTrue(!error.message.contains("secret"))
    }
}

private class FakeRepository(private val searchResult: Result<List<Band>>) : MusicRepository {
    private val favorites = MutableStateFlow<List<Band>>(emptyList())
    override fun observeFavorites(): Flow<List<Band>> = favorites
    override fun observeFavorite(artistId: String): Flow<Band?> = MutableStateFlow(null)
    override suspend fun searchBands(text: String, genre: String, forceRefresh: Boolean) = searchResult.getOrThrow()
    override suspend fun getBand(artistId: String, forceRefresh: Boolean) = Band(artistId, "Band")
    override suspend fun getAlbums(artistId: String, forceRefresh: Boolean): List<Album> = emptyList()
    override suspend fun setFavorite(band: Band, favorite: Boolean) = Unit
    override suspend fun restoreFavorite(band: Band) = Unit
}
