package com.example.finalproject1.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiGenerateRequest,
    ): GeminiGenerateResponse
}

@JsonClass(generateAdapter = true)
data class GeminiGenerateRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig,
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val role: String = "user",
    val parts: List<GeminiPart>,
)

@JsonClass(generateAdapter = true)
data class GeminiPart(val text: String)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val temperature: Float = 0.2f,
    val maxOutputTokens: Int = 2048,
    val responseMimeType: String = "text/plain",
    val responseJsonSchema: GeminiJsonSchema? = null,
    val thinkingConfig: GeminiThinkingConfig? = null,
)

@JsonClass(generateAdapter = true)
data class GeminiThinkingConfig(val thinkingLevel: String = "low")

@JsonClass(generateAdapter = true)
data class GeminiJsonSchema(
    val type: String,
    val properties: Map<String, GeminiJsonSchema>? = null,
    val items: GeminiJsonSchema? = null,
    val required: List<String>? = null,
    val maxItems: Int? = null,
)

@JsonClass(generateAdapter = true)
data class GeminiGenerateResponse(val candidates: List<GeminiCandidate> = emptyList()) {
    fun text(): String? = candidates.firstOrNull()?.content?.parts
        ?.joinToString(separator = "") { it.text }
        ?.trim()
        ?.takeIf(String::isNotEmpty)
}

@JsonClass(generateAdapter = true)
data class GeminiCandidate(val content: GeminiResponseContent?)

@JsonClass(generateAdapter = true)
data class GeminiResponseContent(val parts: List<GeminiResponsePart> = emptyList())

@JsonClass(generateAdapter = true)
data class GeminiResponsePart(val text: String = "")
