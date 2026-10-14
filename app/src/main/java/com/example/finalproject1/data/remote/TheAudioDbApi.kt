package com.example.finalproject1.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TheAudioDbApi {
    @GET("api/v1/json/{apiKey}/search.php")
    suspend fun searchArtist(
        @Path("apiKey") apiKey: String,
        @Query("s") artistName: String,
    ): AudioDbArtistResponseDto

    @GET("api/v1/json/{apiKey}/artist.php")
    suspend fun getArtist(
        @Path("apiKey") apiKey: String,
        @Query("i") artistId: String,
    ): AudioDbArtistResponseDto

    @GET("api/v1/json/{apiKey}/artist-mb.php")
    suspend fun getArtistByMusicBrainzId(
        @Path("apiKey") apiKey: String,
        @Query("i") musicBrainzId: String,
    ): AudioDbArtistResponseDto

    @GET("api/v1/json/{apiKey}/album.php")
    suspend fun getAlbums(
        @Path("apiKey") apiKey: String,
        @Query("i") artistId: String,
    ): AudioDbAlbumResponseDto
}
