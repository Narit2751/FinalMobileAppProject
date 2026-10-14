package com.example.finalproject1.data.repository

import com.example.finalproject1.data.model.MusicEntityType
import com.example.finalproject1.data.model.ParsedMusicRequest
import com.example.finalproject1.data.model.ResolvedMusicEntity
import com.example.finalproject1.data.model.ResolvedMusicEntity.ResolvedAlbum
import com.example.finalproject1.data.model.ResolvedMusicEntity.ResolvedBand
import com.example.finalproject1.data.model.ResolvedMusicEntity.ResolvedMember
import com.example.finalproject1.data.model.ResolvedMusicEntity.ResolvedSong
import com.example.finalproject1.data.model.MemberBandLink
import com.example.finalproject1.data.model.VerifiedCandidate
import com.example.finalproject1.data.remote.MusicBrainzApi
import com.example.finalproject1.data.remote.MusicBrainzQueryBuilder
import com.example.finalproject1.data.remote.MusicBrainzRateLimiter
import java.util.concurrent.ConcurrentHashMap

interface MusicEntityResolver {
    suspend fun resolve(type: MusicEntityType, name: String, artistHint: String? = null): List<ResolvedMusicEntity>
    suspend fun candidates(request: ParsedMusicRequest): List<VerifiedCandidate>
    suspend fun related(entity: ResolvedMusicEntity, intent: com.example.finalproject1.data.model.MusicIntent): List<ResolvedMusicEntity>
}

class MusicBrainzEntityResolver(
    private val api: MusicBrainzApi,
    private val rateLimiter: MusicBrainzRateLimiter,
    private val repository: MusicRepository,
) : MusicEntityResolver {
    private val cache = ConcurrentHashMap<String, List<ResolvedMusicEntity>>()

    override suspend fun resolve(
        type: MusicEntityType,
        name: String,
        artistHint: String?,
    ): List<ResolvedMusicEntity> {
        val cleanName = name.trim().take(120)
        if (cleanName.isEmpty()) return emptyList()
        val key = "${type.name}:${cleanName.lowercase()}:${artistHint.orEmpty().lowercase()}"
        return cache[key] ?: when (type) {
            MusicEntityType.BAND -> searchBands(cleanName)
            MusicEntityType.MEMBER -> searchMembers(cleanName)
            MusicEntityType.ALBUM -> searchAlbums(cleanName)
            MusicEntityType.SONG -> searchSongs(cleanName, artistHint)
            else -> emptyList()
        }.also { if (it.isNotEmpty()) cache[key] = it }
    }

    override suspend fun candidates(request: ParsedMusicRequest): List<VerifiedCandidate> {
        val genre = MusicVibeResolver.genreFor(request.preferences)
        val bands = rateLimiter.run { api.searchArtists(MusicBrainzQueryBuilder.explore(genre), limit = 15) }
            .artists
            .map { artist ->
                ResolvedBand(
                    id = artist.id,
                    name = artist.name,
                    aliases = artist.aliases.orEmpty().map { it.name }.distinct(),
                    musicBrainzId = artist.id,
                    countryOrArea = artist.area?.name ?: artist.country,
                    genres = (artist.genres.orEmpty() + artist.tags.orEmpty())
                        .sortedByDescending { it.count ?: 0 }.map { it.name }.distinct().take(5),
                    beginDate = artist.lifeSpan?.begin,
                    ended = artist.lifeSpan?.ended,
                )
            }
        if (bands.isEmpty()) return emptyList()
        return when (request.recommendationEntityType()) {
            MusicEntityType.SONG -> songCandidates(bands)
            MusicEntityType.ALBUM -> albumCandidates(bands)
            else -> bands.map(::VerifiedCandidate)
        }
    }

    private suspend fun songCandidates(bands: List<ResolvedBand>): List<VerifiedCandidate> {
        val bandMap = bands.take(8).associateBy { it.id }
        val bandIds = bandMap.keys
        val recordings = rateLimiter.run {
            api.searchRecordings(MusicRecommendationQueries.forJapaneseArtists(bandIds), limit = 15)
        }.recordings
        return recordings.mapNotNull { song ->
            val credit = song.artistCredit.orEmpty().firstOrNull()
            val artist = credit?.artist ?: return@mapNotNull null
            val artistId = artist.id
            val band = bandMap[artistId] ?: return@mapNotNull null
            val releaseGroup = song.releases.orEmpty().firstOrNull()?.releaseGroup
            VerifiedCandidate(
                ResolvedSong(
                    id = song.id,
                    name = song.title,
                    artistName = credit.name ?: artist.name,
                    artistId = artistId,
                    albumName = releaseGroup?.title,
                    albumId = releaseGroup?.id,
                    durationMillis = song.length,
                    artistGenres = band.genres,
                )
            )
        }.distinctBy { it.entity.id }
    }

    private suspend fun albumCandidates(bands: List<ResolvedBand>): List<VerifiedCandidate> {
        val bandMap = bands.take(8).associateBy { it.id }
        val bandIds = bandMap.keys
        val albums = rateLimiter.run {
            api.searchReleaseGroups(MusicRecommendationQueries.forJapaneseArtists(bandIds, albumsOnly = true), limit = 15)
        }.releaseGroups
        return albums.mapNotNull { album ->
            val credit = album.artistCredit.orEmpty().firstOrNull()
            val artist = credit?.artist ?: return@mapNotNull null
            val artistId = artist.id
            val band = bandMap[artistId] ?: return@mapNotNull null
            VerifiedCandidate(
                ResolvedAlbum(
                    id = album.id,
                    name = album.title,
                    artistName = credit.name ?: artist.name,
                    artistId = artistId,
                    primaryType = album.primaryType,
                    firstReleaseDate = album.firstReleaseDate,
                    artistGenres = band.genres,
                )
            )
        }.distinctBy { it.entity.id }
    }

    override suspend fun related(
        entity: ResolvedMusicEntity,
        intent: com.example.finalproject1.data.model.MusicIntent,
    ): List<ResolvedMusicEntity> {
        when (intent) {
            com.example.finalproject1.data.model.MusicIntent.FIND_ALBUM_FOR_SONG -> {
                val song = entity as? ResolvedSong ?: return emptyList()
                val albumId = song.albumId ?: return emptyList()
                val albumName = song.albumName ?: return emptyList()
                return listOf(ResolvedAlbum(albumId, albumName, artistName = song.artistName, artistId = song.artistId))
            }
            com.example.finalproject1.data.model.MusicIntent.FIND_ARTIST_FOR_SONG -> {
                val song = entity as? ResolvedSong ?: return emptyList()
                val artistId = song.artistId ?: return emptyList()
                val artistName = song.artistName ?: return emptyList()
                return listOf(ResolvedBand(artistId, artistName))
            }
            com.example.finalproject1.data.model.MusicIntent.FIND_BAND_FOR_MEMBER -> {
                val member = entity as? ResolvedMember ?: return emptyList()
                return member.bands.map { ResolvedBand(it.bandId, it.bandName) }.distinctBy { it.id }
            }
            else -> Unit
        }
        val band = entity as? ResolvedBand ?: return emptyList()
        val artistId = band.musicBrainzId ?: band.id
        return when (intent) {
            com.example.finalproject1.data.model.MusicIntent.LIST_MEMBERS -> {
                val artist = rateLimiter.run { api.getArtist(artistId) }
                artist.relations.orEmpty().mapNotNull { relation ->
                    val person = relation.artist ?: return@mapNotNull null
                    if (!relation.type.orEmpty().contains("member", true) || !person.type.orEmpty().equals("Person", true)) return@mapNotNull null
                    ResolvedMember(
                        id = person.id,
                        name = person.name,
                        bands = listOf(MemberBandLink(band.id, band.name, relation.attributes.orEmpty(), relation.ended != true)),
                    )
                }.distinctBy { it.id }
            }
            com.example.finalproject1.data.model.MusicIntent.LIST_ALBUMS -> {
                rateLimiter.run { api.getReleaseGroups(artistId) }.releaseGroups.map { album ->
                    ResolvedAlbum(
                        id = album.id,
                        name = album.title,
                        artistName = band.name,
                        artistId = band.id,
                        primaryType = album.primaryType,
                        firstReleaseDate = album.firstReleaseDate,
                    )
                }
            }
            com.example.finalproject1.data.model.MusicIntent.LIST_SONGS,
            com.example.finalproject1.data.model.MusicIntent.RECOMMEND_SONGS -> {
                rateLimiter.run { api.searchRecordings("arid:$artistId", limit = 10) }.recordings.map { song ->
                    val releaseGroup = song.releases.orEmpty().firstOrNull()?.releaseGroup
                    ResolvedSong(song.id, song.title, artistName = band.name, artistId = band.id,
                        albumName = releaseGroup?.title, albumId = releaseGroup?.id, durationMillis = song.length)
                }.distinctBy { it.id }
            }
            else -> emptyList()
        }
    }

    private suspend fun searchBands(name: String): List<ResolvedMusicEntity> {
        val result = rateLimiter.run { api.searchArtists(MusicBrainzQueryBuilder.nameSearch(name)) }
        return result.artists.take(5).map { artist ->
            ResolvedBand(
                id = artist.id,
                name = artist.name,
                aliases = artist.aliases.orEmpty().map { it.name }.distinct(),
                countryOrArea = artist.area?.name ?: artist.country,
                genres = (artist.genres.orEmpty() + artist.tags.orEmpty())
                    .sortedByDescending { it.count ?: 0 }.map { it.name }.distinct().take(5),
                beginDate = artist.lifeSpan?.begin,
                ended = artist.lifeSpan?.ended,
            )
        }
    }

    private suspend fun searchMembers(name: String): List<ResolvedMusicEntity> {
        val query = "artist:\"${escape(name)}\" AND type:person"
        val matches = rateLimiter.run { api.searchArtists(query, limit = 5) }.artists
        return matches.map { hit ->
            val artist = rateLimiter.run { api.getArtist(hit.id) }
            val bands = artist.relations.orEmpty().mapNotNull { relation ->
                val related = relation.artist ?: return@mapNotNull null
                if (!relation.type.orEmpty().contains("member", ignoreCase = true) ||
                    !related.type.orEmpty().equals("Group", ignoreCase = true)) return@mapNotNull null
                MemberBandLink(
                    bandId = related.id,
                    bandName = related.name,
                    roles = relation.attributes.orEmpty(),
                    isCurrent = relation.ended != true,
                )
            }
            ResolvedMember(
                id = artist.id,
                name = artist.name,
                aliases = artist.aliases.orEmpty().map { it.name }.distinct(),
                bands = bands,
            )
        }
    }

    private suspend fun searchAlbums(name: String): List<ResolvedMusicEntity> {
        val result = rateLimiter.run {
            api.searchReleaseGroups("releasegroup:\"${escape(name)}\"", limit = 8)
        }
        return result.releaseGroups.map { album ->
            val credit = album.artistCredit.orEmpty().firstOrNull()
            ResolvedAlbum(
                id = album.id,
                name = album.title,
                artistName = credit?.name ?: credit?.artist?.name,
                artistId = credit?.artist?.id,
                primaryType = album.primaryType,
                firstReleaseDate = album.firstReleaseDate,
            )
        }
    }

    private suspend fun searchSongs(name: String, artistHint: String?): List<ResolvedMusicEntity> {
        val artist = artistHint?.takeIf(String::isNotBlank)?.let { " AND artist:\"${escape(it)}\"" }.orEmpty()
        val result = rateLimiter.run {
            api.searchRecordings("recording:\"${escape(name)}\"$artist", limit = 8)
        }
        return result.recordings.map { song ->
            val credit = song.artistCredit.orEmpty().firstOrNull()
            val releaseGroup = song.releases.orEmpty().firstOrNull()?.releaseGroup
            ResolvedSong(
                id = song.id,
                name = song.title,
                artistName = credit?.name ?: credit?.artist?.name,
                artistId = credit?.artist?.id,
                albumName = releaseGroup?.title,
                albumId = releaseGroup?.id,
                durationMillis = song.length,
            )
        }
    }

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

}

private fun ParsedMusicRequest.recommendationEntityType(): MusicEntityType = when (intent) {
    com.example.finalproject1.data.model.MusicIntent.RECOMMEND_SONGS -> MusicEntityType.SONG
    com.example.finalproject1.data.model.MusicIntent.RECOMMEND_ALBUMS -> MusicEntityType.ALBUM
    com.example.finalproject1.data.model.MusicIntent.RECOMMEND_SIMILAR ->
        entities.firstOrNull()?.type?.takeIf { it in setOf(MusicEntityType.SONG, MusicEntityType.ALBUM, MusicEntityType.BAND) }
            ?: MusicEntityType.BAND
    else -> MusicEntityType.BAND
}

internal object MusicRecommendationQueries {
    fun forJapaneseArtists(artistIds: Collection<String>, albumsOnly: Boolean = false): String {
        require(artistIds.isNotEmpty())
        val artists = artistIds.joinToString(separator = " OR ", prefix = "(", postfix = ")") { "arid:$it" }
        return if (albumsOnly) "$artists AND primarytype:album" else artists
    }
}

/** Converts subjective recommendation words into a broad MusicBrainz tag query, not a factual label. */
internal object MusicVibeResolver {
    private val genres = setOf("rock", "pop", "metal", "punk", "jazz", "electronic", "alternative")
    private val vibeGenreHints = mapOf(
        "energetic" to "rock",
        "relaxing" to "jazz",
        "emotional" to "alternative",
        "dark" to "metal",
        "happy" to "pop",
        "focused" to "electronic",
    )

    fun genreFor(preferences: List<String>): String {
        val normalized = preferences.map { it.trim().lowercase() }
        return normalized.firstOrNull(genres::contains)
            ?: normalized.firstNotNullOfOrNull(vibeGenreHints::get)
            ?: "rock"
    }
}
