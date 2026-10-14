package com.example.finalproject1.data.mapper

import com.example.finalproject1.data.model.Album
import com.example.finalproject1.data.model.Band
import com.example.finalproject1.data.remote.AudioDbAlbumDto
import com.example.finalproject1.data.remote.AudioDbArtistDto

fun AudioDbArtistDto.toBand(isFavorite: Boolean = false): Band {
    val genres = listOfNotNull(strGenre, strStyle)
        .flatMap { it.split(Regex("[/,;]")) }
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy(String::lowercase)
    val biography = strBiographyEN?.takeIf(String::isNotBlank)
        ?: strBiography?.takeIf(String::isNotBlank)
    return Band(
        id = idArtist,
        name = strArtist,
        sourceArtistId = idArtist,
        musicBrainzId = strMusicBrainzID?.takeIf(String::isNotBlank),
        country = strCountryCode?.takeIf(String::isNotBlank),
        area = strCountry?.takeIf(String::isNotBlank),
        beginDate = intFormedYear?.takeIf(String::isNotBlank),
        ended = !strDisbanded.isNullOrBlank(),
        genres = genres,
        imageUrl = strArtistLogo?.takeIf(String::isNotBlank),
        imageSourceUrl = "https://www.theaudiodb.com/artist/$idArtist",
        profileImageUrl = listOf(strArtistThumb, strArtistWideThumb, strArtistFanart)
            .firstOrNull { !it.isNullOrBlank() },
        profileImageSourceUrl = "https://www.theaudiodb.com/artist/$idArtist",
        summarySourceText = biography,
        summarySourceUrl = "https://www.theaudiodb.com/artist/$idArtist",
        isFavorite = isFavorite,
    )
}

fun AudioDbAlbumDto.toAlbum() = Album(
    id = idAlbum,
    title = strAlbum,
    primaryType = strReleaseFormat?.takeIf(String::isNotBlank),
    firstReleaseDate = intYearReleased?.takeIf(String::isNotBlank),
    imageUrl = strAlbumThumb?.takeIf(String::isNotBlank),
    sourceUrl = "https://www.theaudiodb.com/album/$idAlbum",
)
