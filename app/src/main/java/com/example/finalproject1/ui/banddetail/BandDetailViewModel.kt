package com.example.finalproject1.ui.banddetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalproject1.data.model.Album
import com.example.finalproject1.data.model.Band
import com.example.finalproject1.data.remote.AiNotConfiguredException
import com.example.finalproject1.data.remote.AiMusicAssistantService
import com.example.finalproject1.data.remote.BandInfoService
import com.example.finalproject1.data.model.ResolvedMusicEntity
import com.example.finalproject1.data.repository.MusicRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BandDetailUiState(
    val loading: Boolean = true,
    val band: Band? = null,
    val albums: List<Album> = emptyList(),
    val albumLoading: Boolean = false,
    val errorMessage: String? = null,
    val albumError: String? = null,
    val aiExplanation: String? = null,
    val summarySourceUrl: String? = null,
    val aiLoading: Boolean = false,
)

class BandDetailViewModel(
    private val artistId: String,
    private val repository: MusicRepository,
    private val aiService: AiMusicAssistantService,
    private val bandInfoService: BandInfoService,
    private val isOnline: () -> Boolean,
) : ViewModel() {
    private val _state = MutableStateFlow(BandDetailUiState())
    val state: StateFlow<BandDetailUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            repository.observeFavorite(artistId).collect { favorite ->
                val current = _state.value.band ?: return@collect
                _state.value = _state.value.copy(
                    band = current.copy(isFavorite = favorite != null)
                )
            }
        }
    }

    fun retry() = load(forceRefresh = true)

    fun toggleFavorite() {
        val band = _state.value.band ?: return
        viewModelScope.launch { repository.setFavorite(band, !band.isFavorite) }
    }

    fun summarizeBand() = summarize(origins = false)

    fun summarizeOrigins() = summarize(origins = true)

    private fun summarize(origins: Boolean) {
        val band = _state.value.band ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                aiLoading = true,
                aiExplanation = null,
                summarySourceUrl = null,
            )
            _state.value = try {
                val embeddedText = band.summarySourceText
                val externalSource = if (embeddedText.isNullOrBlank()) {
                    band.musicBrainzId?.let { bandInfoService.informationForArtist(it) }
                } else null
                val sourceText = embeddedText ?: externalSource?.extract
                    ?: return@launch run {
                        _state.value = _state.value.copy(
                            aiLoading = false,
                            aiExplanation = "TheAudioDB does not have enough source information to summarize this band.",
                        )
                    }
                val sourceUrl = band.summarySourceUrl ?: externalSource?.sourceUrl
                val verifiedEntity = ResolvedMusicEntity.ResolvedBand(
                    id = band.id,
                    name = band.name,
                    musicBrainzId = band.musicBrainzId,
                    countryOrArea = band.area ?: band.country,
                    genres = band.genres,
                    beginDate = band.beginDate,
                    ended = band.ended,
                    imageUrl = band.profileImageUrl,
                    sourceText = sourceText,
                )
                val explanation = aiService.explainEntity(
                    verifiedEntity,
                    if (origins) "Summarize this band's origins and early history." else "Summarize this band's style and history.",
                )
                _state.value.copy(
                    aiLoading = false,
                    aiExplanation = explanation,
                    summarySourceUrl = sourceUrl,
                )
            } catch (_: AiNotConfiguredException) {
                _state.value.copy(
                    aiLoading = false,
                    aiExplanation = "Gemini is not configured on this computer. Add GEMINI_API_KEY to root local.properties and rebuild debug, or configure AI_PROXY_BASE_URL for release.",
                )
            } catch (_: Exception) {
                _state.value.copy(
                    aiLoading = false,
                    aiExplanation = "The AI explanation is unavailable right now.",
                )
            }
        }
    }

    private fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.value = BandDetailUiState(loading = true)
            val bandRequest = async { repository.getBand(artistId, forceRefresh) }
            val band = try {
                bandRequest.await()
            } catch (_: Exception) {
                _state.value = BandDetailUiState(
                    loading = false,
                    errorMessage = if (isOnline()) "Band details could not be loaded."
                    else "This band is not saved offline. Connect to the internet and retry.",
                )
                return@launch
            }
            _state.value = BandDetailUiState(loading = false, band = band, albumLoading = true)
            try {
                val albums = repository.getAlbums(artistId, forceRefresh)
                _state.value = _state.value.copy(albums = albums, albumLoading = false)
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    albumLoading = false,
                    albumError = if (isOnline()) "Albums could not be loaded."
                    else "Albums are unavailable offline.",
                )
            }
        }
    }
}
