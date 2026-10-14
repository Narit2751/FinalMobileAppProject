package com.example.finalproject1.data.remote

import com.example.finalproject1.data.model.ConversationContext
import com.example.finalproject1.data.model.ParsedMusicRequest
import com.example.finalproject1.data.model.RankedCandidate
import com.example.finalproject1.data.model.ResolvedMusicEntity
import com.example.finalproject1.data.model.VerifiedCandidate

interface AiMusicAssistantService {
    val isConfigured: Boolean

    suspend fun parseRequest(message: String, context: ConversationContext): ParsedMusicRequest

    suspend fun rankCandidates(
        request: ParsedMusicRequest,
        candidates: List<VerifiedCandidate>,
    ): List<RankedCandidate>

    suspend fun explainEntity(entity: ResolvedMusicEntity, question: String): String
}

class AiNotConfiguredException : Exception()
class InvalidAiResponseException(cause: Throwable? = null) : Exception(cause)
class AiAuthenticationException : Exception()
class AiQuotaException : Exception()
class AiTimeoutException : Exception()
class AiServiceUnavailableException : Exception()
