package com.example.finalproject1.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MusicBrainzApi {
    @GET("artist")
    suspend fun searchArtists(
        @Query("query") query: String,
        @Query("fmt") format: String = "json",
        @Query("limit") limit: Int = 25,
    ): ArtistSearchResponseDto

    @GET("artist/{id}")
    suspend fun getArtist(
        @Path("id") artistId: String,
        @Query("inc") includes: String = "genres+tags+aliases+url-rels+artist-rels",
        @Query("fmt") format: String = "json",
    ): ArtistDto

    @GET("release-group")
    suspend fun getReleaseGroups(
        @Query("artist") artistId: String,
        @Query("type") type: String = "album",
        @Query("fmt") format: String = "json",
        @Query("limit") limit: Int = 50,
    ): ReleaseGroupResponseDto

    @GET("release-group")
    suspend fun searchReleaseGroups(
        @Query("query") query: String,
        @Query("fmt") format: String = "json",
        @Query("limit") limit: Int = 10,
    ): ReleaseGroupResponseDto

    @GET("recording")
    suspend fun searchRecordings(
        @Query("query") query: String,
        @Query("fmt") format: String = "json",
        @Query("limit") limit: Int = 10,
    ): RecordingSearchResponseDto
}
