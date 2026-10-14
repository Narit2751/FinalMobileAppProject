package com.example.finalproject1.data.repository

import com.example.finalproject1.data.local.FavoriteBandDao
import com.example.finalproject1.data.mapper.toAlbum
import com.example.finalproject1.data.mapper.toBand
import com.example.finalproject1.data.mapper.toFavoriteEntity
import com.example.finalproject1.data.model.Album
import com.example.finalproject1.data.model.Band
import com.example.finalproject1.data.remote.TheAudioDbApi
import com.example.finalproject1.data.remote.TheAudioDbRateLimiter
import com.example.finalproject1.data.remote.ArtistImageService
import com.example.finalproject1.data.remote.MusicBrainzApi
import com.example.finalproject1.data.remote.MusicBrainzRateLimiter
import com.example.finalproject1.data.remote.NoopArtistImageService
import com.squareup.moshi.JsonDataException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException

class TheAudioDbMusicRepository(
    private val api: TheAudioDbApi,
    private val apiKey: String,
    private val favoriteDao: FavoriteBandDao,
    private val rateLimiter: TheAudioDbRateLimiter,
    private val logoFallbackService: ArtistImageService = NoopArtistImageService,
    private val musicBrainzApi: MusicBrainzApi? = null,
    private val musicBrainzRateLimiter: MusicBrainzRateLimiter = MusicBrainzRateLimiter(),
    private val memberPhotoService: ArtistImageService = NoopArtistImageService,
) : MusicRepository {
    private val searchCache = ConcurrentHashMap<String, List<Band>>()
    private val bandCache = ConcurrentHashMap<String, Band>()
    private val detailCache = ConcurrentHashMap<String, Band>()
    private val albumCache = ConcurrentHashMap<String, List<Album>>()

    override fun observeFavorites(): Flow<List<Band>> =
        favoriteDao.observeAll().map { rows -> rows.map { it.toBand() } }

    override fun observeFavorite(artistId: String): Flow<Band?> =
        favoriteDao.observeById(artistId).map { it?.toBand() }

    override suspend fun searchBands(
        text: String,
        genre: String,
        forceRefresh: Boolean,
    ): List<Band> {
        val cacheKey = "${text.trim().lowercase()}|${genre.lowercase()}"
        if (!forceRefresh) searchCache[cacheKey]?.let { return applyFavoriteState(it) }
        val names = if (text.isBlank()) JapaneseBandSeeds.forGenre(genre) else listOf(text.trim())
        val bands = names.mapNotNull { name ->
            remoteCall {
                rateLimiter.run { api.searchArtist(apiKey, name) }.artists
                    .orEmpty()
                    .firstOrNull()
                    ?.takeIf(::isJapaneseArtist)
                    ?.toBand()
            }
        }.distinctBy { it.id }.let { enrichMissingLogos(it, forceRefresh) }
        searchCache[cacheKey] = bands
        bands.forEach { bandCache[it.id] = it.copy(isFavorite = false) }
        return applyFavoriteState(bands)
    }

    override suspend fun getBand(artistId: String, forceRefresh: Boolean): Band {
        val favorite = favoriteDao.getById(artistId)
        if (!forceRefresh) detailCache[artistId]?.let { return it.copy(isFavorite = favorite != null) }
        return try {
            remoteCall {
                val response = rateLimiter.run {
                    if (artistId.all(Char::isDigit)) api.getArtist(apiKey, artistId)
                    else api.getArtistByMusicBrainzId(apiKey, artistId)
                }
                response.artists.orEmpty().firstOrNull()
                    ?.toBand(isFavorite = favorite != null)
                    ?.let { band ->
                        if (artistId.all(Char::isDigit)) band else band.copy(id = artistId)
                    }
                    ?.let { band -> enrichWithMusicBrainzMembers(band, forceRefresh) }
                    ?.also { band ->
                        bandCache[artistId] = band.copy(isFavorite = false)
                        bandCache[band.id] = band.copy(isFavorite = false)
                        detailCache[artistId] = band.copy(isFavorite = false)
                        detailCache[band.id] = band.copy(isFavorite = false)
                    }
                    ?: throw DataUnavailableException()
            }
        } catch (error: DataUnavailableException) {
            favorite?.toBand() ?: throw error
        }
    }

    override suspend fun getAlbums(artistId: String, forceRefresh: Boolean): List<Album> {
        if (!forceRefresh) albumCache[artistId]?.let { return it }
        val knownBand = detailCache[artistId] ?: bandCache[artistId] ?: getBand(artistId, forceRefresh)
        val musicBrainzId = knownBand.musicBrainzId
            ?: artistId.takeIf(MUSICBRAINZ_ID_REGEX::matches)
        if (musicBrainzApi != null && musicBrainzId != null) {
            return remoteCall {
                musicBrainzRateLimiter.run {
                    musicBrainzApi.getReleaseGroups(musicBrainzId)
                }.releaseGroups
                    .map { it.toAlbum() }
                    .distinctBy { it.id }
                    .also { albumCache[artistId] = it }
            }
        }
        val sourceId = knownBand.sourceArtistId ?: return emptyList()
        return remoteCall {
            rateLimiter.run { api.getAlbums(apiKey, sourceId) }.album.orEmpty()
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

    private fun isJapaneseArtist(artist: com.example.finalproject1.data.remote.AudioDbArtistDto): Boolean =
        artist.strCountryCode.equals("JP", ignoreCase = true) ||
            artist.strCountry.orEmpty().contains("Japan", ignoreCase = true)

    private suspend fun enrichMissingLogos(bands: List<Band>, forceRefresh: Boolean): List<Band> {
        val missing = bands.filter { it.imageUrl == null && it.musicBrainzId != null }
        if (missing.isEmpty()) return bands
        val images = try {
            logoFallbackService.imagesForArtistIds(missing.mapNotNull { it.musicBrainzId }, forceRefresh)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyMap()
        }
        return bands.map { band ->
            val image = band.musicBrainzId?.let(images::get)
            if (band.imageUrl == null && image != null) {
                band.copy(imageUrl = image.imageUrl, imageSourceUrl = image.sourceUrl)
            } else band
        }
    }

    private suspend fun enrichWithMusicBrainzMembers(band: Band, forceRefresh: Boolean): Band {
        val mbid = band.musicBrainzId ?: return band
        val mbApi = musicBrainzApi ?: return band
        val members = try {
            musicBrainzRateLimiter.run { mbApi.getArtist(mbid) }.toBand().members
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return band
        }
        if (members.isEmpty()) return band
        val photos = try {
            memberPhotoService.imagesForArtistIds(members.map { it.artistId }, forceRefresh)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyMap()
        }
        return band.copy(
            members = members.map { member ->
                photos[member.artistId]?.let { photo ->
                    member.copy(imageUrl = photo.imageUrl, imageSourceUrl = photo.sourceUrl)
                } ?: member
            },
        )
    }

    private suspend fun <T> remoteCall(block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        when (error) {
            is DataUnavailableException -> throw error
            is IOException, is HttpException, is JsonDataException -> throw DataUnavailableException(error)
            else -> throw error
        }
    }

    private companion object {
        val MUSICBRAINZ_ID_REGEX = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )
    }
}

private object JapaneseBandSeeds {
    private val rock = listOf("ONE OK ROCK", "BAND-MAID", "SCANDAL", "MUCC", "DIR EN GREY", "L'Arc-en-Ciel")
    private val pop = listOf("Perfume", "SEKAI NO OWARI", "Mrs. GREEN APPLE", "Official HIGE DANdism", "Ikimonogakari", "Every Little Thing")
    private val metal = listOf("BABYMETAL", "DIR EN GREY", "MUCC", "X Japan", "Maximum the Hormone", "Loudness")
    private val punk = listOf("Shonen Knife", "The Blue Hearts", "Hi-Standard", "Guitar Wolf", "Otoboke Beaver", "The Stalin")
    private val jazz = listOf("CASIOPEA", "T-SQUARE", "SOIL & PIMP SESSIONS", "Mouse on the Keys", "JABBERLOOP", "Tokyo Ska Paradise Orchestra")
    private val electronic = listOf("Yellow Magic Orchestra", "Perfume", "Capsule", "Denki Groove", "Wednesday Campanella", "Boom Boom Satellites")

    fun forGenre(genre: String): List<String> = when (genre.lowercase()) {
        "pop" -> pop
        "metal" -> metal
        "punk" -> punk
        "jazz" -> jazz
        "electronic" -> electronic
        else -> rock
    }
}
