package com.example.finalproject1.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class ArtistSearchResponseDto(
    val artists: List<ArtistDto> = emptyList(),
)

@JsonClass(generateAdapter = false)
data class ArtistDto(
    val id: String,
    val name: String,
    val country: String? = null,
    val area: AreaDto? = null,
    @param:Json(name = "life-span") val lifeSpan: LifeSpanDto? = null,
    val genres: List<TagDto>? = null,
    val tags: List<TagDto>? = null,
    val relations: List<ArtistRelationDto>? = null,
    val aliases: List<AliasDto>? = null,
)

@JsonClass(generateAdapter = false)
data class AliasDto(val name: String, val locale: String? = null)

@JsonClass(generateAdapter = false)
data class ArtistRelationDto(
    val type: String? = null,
    val direction: String? = null,
    val artist: RelatedArtistDto? = null,
    val attributes: List<String>? = null,
    val ended: Boolean? = null,
)

@JsonClass(generateAdapter = false)
data class RelatedArtistDto(
    val id: String,
    val name: String,
    val type: String? = null,
)

@JsonClass(generateAdapter = false)
data class AreaDto(val name: String? = null)

@JsonClass(generateAdapter = false)
data class LifeSpanDto(
    val begin: String? = null,
    val end: String? = null,
    val ended: Boolean? = null,
)

@JsonClass(generateAdapter = false)
data class TagDto(
    val name: String,
    val count: Int? = null,
)

@JsonClass(generateAdapter = false)
data class ReleaseGroupResponseDto(
    @param:Json(name = "release-groups") val releaseGroups: List<ReleaseGroupDto> = emptyList(),
)

@JsonClass(generateAdapter = false)
data class ReleaseGroupDto(
    val id: String,
    val title: String,
    @param:Json(name = "primary-type") val primaryType: String? = null,
    @param:Json(name = "first-release-date") val firstReleaseDate: String? = null,
    @param:Json(name = "artist-credit") val artistCredit: List<ArtistCreditDto>? = null,
)

@JsonClass(generateAdapter = false)
data class ArtistCreditDto(
    val name: String? = null,
    val artist: CreditArtistDto? = null,
)

@JsonClass(generateAdapter = false)
data class CreditArtistDto(val id: String, val name: String)

@JsonClass(generateAdapter = false)
data class RecordingSearchResponseDto(val recordings: List<RecordingDto> = emptyList())

@JsonClass(generateAdapter = false)
data class RecordingDto(
    val id: String,
    val title: String,
    val length: Long? = null,
    @param:Json(name = "artist-credit") val artistCredit: List<ArtistCreditDto>? = null,
    val releases: List<RecordingReleaseDto>? = null,
)

@JsonClass(generateAdapter = false)
data class RecordingReleaseDto(
    val id: String? = null,
    val title: String? = null,
    @param:Json(name = "release-group") val releaseGroup: RecordingReleaseGroupDto? = null,
)

@JsonClass(generateAdapter = false)
data class RecordingReleaseGroupDto(val id: String? = null, val title: String? = null)
