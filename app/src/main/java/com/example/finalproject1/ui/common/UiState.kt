package com.example.finalproject1.ui.common

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Content<T>(val data: T) : UiState<T>
    data object Empty : UiState<Nothing>
    data class Error(val message: String, val offline: Boolean = false) : UiState<Nothing>
}

fun friendlyNetworkError(isOnline: Boolean): UiState.Error = if (isOnline) {
    UiState.Error("TheAudioDB could not be reached. Please try again.")
} else {
    UiState.Error("You appear to be offline. Check your connection and try again.", offline = true)
}
