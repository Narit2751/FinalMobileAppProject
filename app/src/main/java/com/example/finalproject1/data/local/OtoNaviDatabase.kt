package com.example.finalproject1.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [FavoriteBandEntity::class], version = 3, exportSchema = true)
abstract class OtoNaviDatabase : RoomDatabase() {
    abstract fun favoriteBandDao(): FavoriteBandDao

    companion object {
        fun create(context: Context): OtoNaviDatabase =
            Room.databaseBuilder(context, OtoNaviDatabase::class.java, "otonavi.db")
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration(dropAllTables = false)
                .build()

        /** Clears legacy cached artwork URLs while preserving favorite records. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE favorite_bands SET imageUrl = NULL")
            }
        }

        /** Removes the retired personal-note column while keeping saved bands. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE favorite_bands_new (
                        artistId TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        country TEXT,
                        genres TEXT NOT NULL,
                        representativeAlbumId TEXT,
                        imageUrl TEXT,
                        savedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO favorite_bands_new
                        (artistId, name, country, genres, representativeAlbumId, imageUrl, savedAt)
                    SELECT artistId, name, country, genres, representativeAlbumId, imageUrl, savedAt
                    FROM favorite_bands
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE favorite_bands")
                db.execSQL("ALTER TABLE favorite_bands_new RENAME TO favorite_bands")
            }
        }
    }
}
