package com.example.finalproject1.data.model

data class Band(
    val id: String,
    val name: String,
    val sourceArtistId: String? = null,
    val musicBrainzId: String? = null,
    val country: String? = null,
    val area: String? = null,
    val beginDate: String? = null,
    val ended: Boolean? = null,
    val genres: List<String> = emptyList(),
    val representativeAlbumId: String? = null,
    val imageUrl: String? = null,
    val imageSourceUrl: String? = null,
    val profileImageUrl: String? = null,
    val profileImageSourceUrl: String? = null,
    val members: List<BandMember> = emptyList(),
    val summarySourceText: String? = null,
    val summarySourceUrl: String? = null,
    val isFavorite: Boolean = false,
)

data class BandMember(
    val artistId: String,
    val name: String,
    val roles: List<String> = emptyList(),
    val isCurrent: Boolean = true,
    val imageUrl: String? = null,
    val imageSourceUrl: String? = null,
)

data class Album(
    val id: String,
    val title: String,
    val primaryType: String? = null,
    val firstReleaseDate: String? = null,
    val imageUrl: String? = null,
    val sourceUrl: String? = null,
) {
    val coverUrl: String?
        get() = imageUrl
}
