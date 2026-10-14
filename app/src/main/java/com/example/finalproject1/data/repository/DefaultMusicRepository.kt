package com.example.finalproject1.data.repository

import android.util.Log
import com.example.finalproject1.BuildConfig
import com.example.finalproject1.data.local.FavoriteBandDao
import com.example.finalproject1.data.mapper.toAlbum
import com.example.finalproject1.data.mapper.toBand
import com.example.finalproject1.data.mapper.toFavoriteEntity
import com.example.finalproject1.data.model.Album
import com.example.finalproject1.data.model.Band
import com.example.finalproject1.data.remote.MusicBrainzApi
import com.example.finalproject1.data.remote.MusicBrainzQueryBuilder
import com.example.finalproject1.data.remote.MusicBrainzRateLimiter
import com.example.finalproject1.data.remote.ArtistImageService
import com.example.finalproject1.data.remote.NoopArtistImageService
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException

class DefaultMusicRepository(
    private val api: MusicBrainzApi,
    private val favoriteDao: FavoriteBandDao,
    private val rateLimiter: MusicBrainzRateLimiter,
    private val artistLogoService: ArtistImageService = NoopArtistImageService,
    private val artistPhotoService: ArtistImageService = NoopArtistImageService,
) : MusicRepository {
    private val searchCache = ConcurrentHashMap<String, List<Band>>()
    private val bandCache = ConcurrentHashMap<String, Band>()
    private val albumCache = ConcurrentHashMap<String, List<Album>>()

    override fun observeFavorites(): Flow<List<Band>> =
        favoriteDao.observeAll().map { rows ->
            enrichWithLogos(rows.map { it.toBand() }, forceRefresh = false)
        }

    override fun observeFavorite(artistId: String): Flow<Band?> =
        favoriteDao.observeById(artistId).map { it?.toBand() }

    override suspend fun searchBands(text: String, genre: String, forceRefresh: Boolean): List<Band> {
        val query = if (text.isBlank()) MusicBrainzQueryBuilder.explore(genre)
        else MusicBrainzQueryBuilder.nameSearch(text)
        if (!forceRefresh) searchCache[query]?.let { return applyFavoriteState(it) }
        return remoteCall {
            rateLimiter.run { api.searchArtists(query) }.artists
                .map { it.toBand() }
                .let { enrichWithLogos(it, forceRefresh) }
                .also { bands ->
                    searchCache[query] = bands
                }
                .let { applyFavoriteState(it) }
        }
    }

    override suspend fun getBand(artistId: String, forceRefresh: Boolean): Band {
        val favorite = favoriteDao.getById(artistId)
        if (!forceRefresh) bandCache[artistId]?.let { cached ->
            return cached.copy(isFavorite = favorite != null)
        }
        return try {
            remoteCall {
                rateLimiter.run { api.getArtist(artistId) }.toBand(
                    isFavorite = favorite != null,
                ).let { enrichWithLogos(listOf(it), forceRefresh).single() }
                    .let { enrichDetailPhotos(it, forceRefresh) }
                    .also { resolved ->
                        bandCache[artistId] = resolved.copy(isFavorite = false)
                        if (favorite != null && favorite.imageUrl != resolved.imageUrl) {
                            favoriteDao.upsert(favorite.copy(imageUrl = resolved.imageUrl))
                        }
                    }
            }
        } catch (error: DataUnavailableException) {
            favorite?.toBand() ?: throw error
        }
    }

    override suspend fun getAlbums(artistId: String, forceRefresh: Boolean): List<Album> {
        if (!forceRefresh) albumCache[artistId]?.let { return it }
        return remoteCall {
            rateLimiter.run { api.getReleaseGroups(artistId) }.releaseGroups
                .map { it.toAlbum() }
                .distinctBy { it.id }
                .also { albumCache[artistId] = it }
        }
    }

    override suspend fun setFavorite(band: Band, favorite: Boolean) {
        if (favorite) favoriteDao.upsert(band.copy(isFavorite = true).toFavoriteEntity())
        else favoriteDao.deleteById(band.id)
    }

    override suspend fun restoreFavorite(band: Band) {
        favoriteDao.upsert(band.copy(isFavorite = true).toFavoriteEntity())
    }

    private suspend fun applyFavoriteState(bands: List<Band>): List<Band> {
        val ids = favoriteDao.getAllIds().toHashSet()
        return bands.map { it.copy(isFavorite = it.id in ids) }
    }

    private suspend fun enrichWithLogos(bands: List<Band>, forceRefresh: Boolean): List<Band> {
        if (bands.isEmpty()) return bands
        val images = try {
            artistLogoService.imagesForArtistIds(bands.map { it.id }, forceRefresh)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (BuildConfig.DEBUG) Log.w("OtoNaviImages", "Artist image lookup failed", error)
            emptyMap()
        }
        return bands.map { band ->
            images[band.id]?.let { image ->
                band.copy(imageUrl = image.imageUrl, imageSourceUrl = image.sourceUrl)
            } ?: band
        }
    }

    private suspend fun enrichDetailPhotos(band: Band, forceRefresh: Boolean): Band {
        val ids = listOf(band.id) + band.members.map { it.artistId }
        val photos = try {
            artistPhotoService.imagesForArtistIds(ids, forceRefresh)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (BuildConfig.DEBUG) Log.w("OtoNaviImages", "Band/member photo lookup failed", error)
            emptyMap()
        }
        val groupPhoto = photos[band.id]
        return band.copy(
            profileImageUrl = groupPhoto?.imageUrl,
            profileImageSourceUrl = groupPhoto?.sourceUrl,
            members = band.members.map { member ->
                photos[member.artistId]?.let { photo ->
                    member.copy(imageUrl = photo.imageUrl, imageSourceUrl = photo.sourceUrl)
                } ?: member
            },
        )
    }

    private suspend fun <T> remoteCall(block: suspend () -> T): T = try {
        block()
    } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        when (error) {
            is IOException, is HttpException, is com.squareup.moshi.JsonDataException ->
                throw DataUnavailableException(error)
            else -> throw error
        }
    }
}
