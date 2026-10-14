package com.example.finalproject1.data.repository

import com.example.finalproject1.data.model.Album
import com.example.finalproject1.data.model.Band
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    fun observeFavorites(): Flow<List<Band>>
    fun observeFavorite(artistId: String): Flow<Band?>
    suspend fun searchBands(text: String, genre: String, forceRefresh: Boolean = false): List<Band>
    suspend fun getBand(artistId: String, forceRefresh: Boolean = false): Band
    suspend fun getAlbums(artistId: String, forceRefresh: Boolean = false): List<Album>
    suspend fun setFavorite(band: Band, favorite: Boolean)
    suspend fun restoreFavorite(band: Band)
}

class DataUnavailableException(cause: Throwable? = null) : Exception(cause)
