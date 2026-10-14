package com.example.finalproject1.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalproject1.data.model.Band
import com.example.finalproject1.data.repository.MusicRepository
import com.example.finalproject1.ui.common.UiState
import com.example.finalproject1.ui.common.friendlyNetworkError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class DiscoverCriteria(val query: String = "", val genre: String = "rock")

@OptIn(FlowPreview::class)
class DiscoverViewModel(
    private val repository: MusicRepository,
    private val isOnline: () -> Boolean,
    debounceMillis: Long = 450L,
) : ViewModel() {
    private val criteria = MutableStateFlow(DiscoverCriteria())
    private val _state = MutableStateFlow<UiState<List<Band>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Band>>> = _state.asStateFlow()
    val currentCriteria: StateFlow<DiscoverCriteria> = criteria.asStateFlow()

    init {
        viewModelScope.launch {
            criteria.debounce(debounceMillis).distinctUntilChanged().collectLatest {
                load(it, forceRefresh = false)
            }
        }
        viewModelScope.launch {
            repository.observeFavorites().collectLatest { favorites ->
                val ids = favorites.mapTo(hashSetOf()) { it.id }
                val content = _state.value as? UiState.Content ?: return@collectLatest
                _state.value = UiState.Content(content.data.map { it.copy(isFavorite = it.id in ids) })
            }
        }
    }

    fun onQueryChanged(value: String) {
        criteria.value = criteria.value.copy(query = value)
    }

    fun onGenreChanged(value: String) {
        criteria.value = criteria.value.copy(genre = value)
    }

    fun refresh() {
        viewModelScope.launch { load(criteria.value, forceRefresh = true) }
    }

    fun toggleFavorite(band: Band) {
        viewModelScope.launch { repository.setFavorite(band, !band.isFavorite) }
    }

    private suspend fun load(value: DiscoverCriteria, forceRefresh: Boolean) {
        _state.value = UiState.Loading
        _state.value = try {
            val bands = repository.searchBands(value.query, value.genre, forceRefresh)
            if (bands.isEmpty()) UiState.Empty else UiState.Content(bands)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            friendlyNetworkError(isOnline())
        }
    }
}
