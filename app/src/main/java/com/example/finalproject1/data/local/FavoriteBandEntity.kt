package com.example.finalproject1.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_bands")
data class FavoriteBandEntity(
    @PrimaryKey val artistId: String,
    val name: String,
    val country: String?,
    val genres: String,
    val representativeAlbumId: String?,
    val imageUrl: String?,
    val savedAt: Long,
)
