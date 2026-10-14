package com.example.finalproject1.data.mapper

import com.example.finalproject1.data.local.FavoriteBandEntity
import com.example.finalproject1.data.model.Album
import com.example.finalproject1.data.model.Band
import com.example.finalproject1.data.model.BandMember
import com.example.finalproject1.data.remote.ArtistDto
import com.example.finalproject1.data.remote.ReleaseGroupDto

fun ArtistDto.toBand(isFavorite: Boolean = false): Band {
    val rankedTags = (genres.orEmpty().map { it.name to (it.count ?: 0) } +
        tags.orEmpty().map { it.name to (it.count ?: 0) })
        .groupBy({ it.first.lowercase() }, { it })
        .values
        .mapNotNull { group -> group.maxByOrNull { it.second } }
        .sortedByDescending { it.second }
        .map { it.first }
        .distinct()
    return Band(
        id = id,
        name = name,
        country = country,
        area = area?.name,
        beginDate = lifeSpan?.begin,
        ended = lifeSpan?.ended,
        genres = rankedTags,
        members = relations.orEmpty()
            .filter { relation ->
                relation.type == "member of band" &&
                    relation.direction == "backward" &&
                    relation.artist?.type == "Person"
            }
            .mapNotNull { relation ->
                relation.artist?.let { artist ->
                    BandMember(
                        artistId = artist.id,
                        name = artist.name,
                        roles = relation.attributes.orEmpty()
                            .filterNot { it in NON_ROLE_MEMBER_ATTRIBUTES },
                        isCurrent = relation.ended != true,
                    )
                }
            }
            .distinctBy { it.artistId }
            .sortedWith(compareByDescending<BandMember> { it.isCurrent }.thenBy { it.name }),
        isFavorite = isFavorite,
    )
}

fun ReleaseGroupDto.toAlbum() = Album(
    id = id,
    title = title,
    primaryType = primaryType,
    firstReleaseDate = firstReleaseDate,
    imageUrl = "https://coverartarchive.org/release-group/$id/front-500",
    sourceUrl = "https://musicbrainz.org/release-group/$id",
)

private val NON_ROLE_MEMBER_ATTRIBUTES = setOf(
    "additional",
    "eponymous",
    "original",
    "principal",
)

fun FavoriteBandEntity.toBand() = Band(
    id = artistId,
    name = name,
    country = country,
    genres = genres.split('|').map(String::trim).filter(String::isNotEmpty),
    representativeAlbumId = representativeAlbumId,
    imageUrl = imageUrl,
    isFavorite = true,
)

fun Band.toFavoriteEntity(savedAt: Long = System.currentTimeMillis()) = FavoriteBandEntity(
    artistId = id,
    name = name,
    country = country ?: area,
    genres = genres.joinToString("|"),
    representativeAlbumId = representativeAlbumId,
    imageUrl = imageUrl,
    savedAt = savedAt,
)
