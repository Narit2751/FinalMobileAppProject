package com.example.finalproject1.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteBandDao {
    @Query("SELECT * FROM favorite_bands ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<FavoriteBandEntity>>

    @Query("SELECT * FROM favorite_bands WHERE artistId = :artistId LIMIT 1")
    fun observeById(artistId: String): Flow<FavoriteBandEntity?>

    @Query("SELECT * FROM favorite_bands WHERE artistId = :artistId LIMIT 1")
    suspend fun getById(artistId: String): FavoriteBandEntity?

    @Query("SELECT artistId FROM favorite_bands")
    suspend fun getAllIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteBandEntity)

    @Query("DELETE FROM favorite_bands WHERE artistId = :artistId")
    suspend fun deleteById(artistId: String)
}
