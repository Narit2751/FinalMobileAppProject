package com.example.finalproject1.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class AudioDbArtistResponseDto(
    val artists: List<AudioDbArtistDto>? = null,
)

@JsonClass(generateAdapter = false)
data class AudioDbArtistDto(
    val idArtist: String,
    val strArtist: String,
    val strArtistAlternate: String? = null,
    val intFormedYear: String? = null,
    val strDisbanded: String? = null,
    val strStyle: String? = null,
    val strGenre: String? = null,
    val strMood: String? = null,
    val strBiography: String? = null,
    val strBiographyEN: String? = null,
    val strCountry: String? = null,
    val strCountryCode: String? = null,
    val strArtistThumb: String? = null,
    val strArtistLogo: String? = null,
    val strArtistFanart: String? = null,
    val strArtistWideThumb: String? = null,
    val strMusicBrainzID: String? = null,
)

@JsonClass(generateAdapter = false)
data class AudioDbAlbumResponseDto(
    val album: List<AudioDbAlbumDto>? = null,
)

@JsonClass(generateAdapter = false)
data class AudioDbAlbumDto(
    val idAlbum: String,
    val idArtist: String? = null,
    val strAlbum: String,
    val intYearReleased: String? = null,
    val strReleaseFormat: String? = null,
    val strAlbumThumb: String? = null,
)
