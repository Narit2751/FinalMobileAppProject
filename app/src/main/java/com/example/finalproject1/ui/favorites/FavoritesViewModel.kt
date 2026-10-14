package com.example.finalproject1.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalproject1.data.model.Band
import com.example.finalproject1.data.repository.MusicRepository
import com.example.finalproject1.ui.common.UiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FavoritesViewModel(
    private val repository: MusicRepository,
) : ViewModel() {
    val state: StateFlow<UiState<List<Band>>> = repository.observeFavorites()
        .map { if (it.isEmpty()) UiState.Empty else UiState.Content(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun remove(band: Band) {
        viewModelScope.launch { repository.setFavorite(band, false) }
    }

    fun restore(band: Band) {
        viewModelScope.launch { repository.restoreFavorite(band) }
    }

}
