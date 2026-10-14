package com.example.finalproject1.data.remote

import com.example.finalproject1.data.model.*

/** Test-only fake. Production constructs GeminiMusicAssistantService. */
class FakeAiMusicAssistantService(
    override val isConfigured: Boolean = true,
    var parsed: ParsedMusicRequest = ParsedMusicRequest(MusicIntent.RECOMMEND_BANDS),
    var ranked: List<RankedCandidate> = emptyList(),
    var explanation: String = "Test explanation",
    var failure: Exception? = null,
) : AiMusicAssistantService {
    override suspend fun parseRequest(message: String, context: ConversationContext): ParsedMusicRequest {
        failure?.let { throw it }
        return parsed
    }
    override suspend fun rankCandidates(request: ParsedMusicRequest, candidates: List<VerifiedCandidate>): List<RankedCandidate> {
        failure?.let { throw it }
        return ranked
    }
    override suspend fun explainEntity(entity: ResolvedMusicEntity, question: String): String = explanation
}
