package com.example.finalproject1.data.remote

import com.example.finalproject1.data.model.ParsedMusicRequest
import com.example.finalproject1.data.model.RankedCandidate
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.POST

interface AiProxyApi {
    @POST("ai/parse")
    suspend fun parse(@Body request: ProxyParseRequest): ParsedMusicRequest

    @POST("ai/rank")
    suspend fun rank(@Body request: ProxyRankRequest): ProxyRankResponse

    @POST("ai/explain")
    suspend fun explain(@Body request: ProxyExplainRequest): ProxyExplainResponse
}

@JsonClass(generateAdapter = true)
data class ProxyParseRequest(val message: String, val context: ProxyContextDto)

@JsonClass(generateAdapter = true)
data class ProxyContextDto(
    val lastEntityId: String? = null,
    val lastEntityType: String? = null,
    val currentBandId: String? = null,
    val currentMemberId: String? = null,
    val currentAlbumId: String? = null,
    val currentSongId: String? = null,
    val previousResultIds: List<String> = emptyList(),
    val preferences: List<String> = emptyList(),
    val lastIntent: String? = null,
)

@JsonClass(generateAdapter = true)
data class ProxyRankRequest(
    val request: ParsedMusicRequest,
    val candidates: List<ProxyCandidateDto>,
)

@JsonClass(generateAdapter = true)
data class ProxyCandidateDto(
    val id: String,
    val type: String,
    val name: String,
    val metadata: Map<String, String?>,
)

@JsonClass(generateAdapter = true)
data class ProxyRankResponse(val recommendations: List<RankedCandidate> = emptyList())

@JsonClass(generateAdapter = true)
data class ProxyExplainRequest(val entity: ProxyCandidateDto, val question: String)

@JsonClass(generateAdapter = true)
data class ProxyExplainResponse(val explanation: String)
