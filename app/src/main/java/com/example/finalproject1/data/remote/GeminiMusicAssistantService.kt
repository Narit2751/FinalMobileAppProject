package com.example.finalproject1.data.remote

import com.example.finalproject1.data.model.ConversationContext
import com.example.finalproject1.data.model.EntityMention
import com.example.finalproject1.data.model.MusicEntityType
import com.example.finalproject1.data.model.MusicIntent
import com.example.finalproject1.data.model.MusicReference
import com.example.finalproject1.data.model.ParsedMusicRequest
import com.example.finalproject1.data.model.RankedCandidate
import com.example.finalproject1.data.model.ReferenceType
import com.example.finalproject1.data.model.ResolvedMusicEntity
import com.example.finalproject1.data.model.VerifiedCandidate
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.delay
import retrofit2.HttpException

@JsonClass(generateAdapter = true)
internal data class ParsedRequestWire(
    val intent: String,
    val entities: List<EntityWire> = emptyList(),
    val preferences: List<String> = emptyList(),
    val reference: ReferenceWire? = null,
)

@JsonClass(generateAdapter = true)
internal data class EntityWire(val type: String, val name: String)

@JsonClass(generateAdapter = true)
internal data class ReferenceWire(val type: String = "NONE", val resultIndex: Int? = null)

@JsonClass(generateAdapter = true)
internal data class RankedWireResponse(val recommendations: List<RankedWire> = emptyList())

@JsonClass(generateAdapter = true)
internal data class RankedWire(val entityId: String, val entityType: String, val reason: String)

@JsonClass(generateAdapter = true)
internal data class ExplanationWire(val explanation: String)

/**
 * One Gemini boundary for both secure production proxy mode and explicitly debug-only direct mode.
 * Facts are supplied by verified API models; Gemini may only interpret, rank, or explain them.
 */
class GeminiMusicAssistantService(
    private val model: String,
    private val moshi: Moshi,
    private val directApi: GeminiApi? = null,
    private val directDebugApiKey: String = "",
    private val proxyApi: AiProxyApi? = null,
) : AiMusicAssistantService {
    override val isConfigured: Boolean
        get() = proxyApi != null || (directApi != null && directDebugApiKey.isNotBlank())

    override suspend fun parseRequest(
        message: String,
        context: ConversationContext,
    ): ParsedMusicRequest {
        ensureConfigured()
        if (proxyApi != null) return validateParsed(call { proxyApi.parse(ProxyParseRequest(message.take(500), contextDto(context))) })

        val prompt = """
            You classify Japanese music discovery requests. Extract intent, entity names, preferences, and references.
            Do not answer the question and do not invent IDs. Return JSON only.
            Allowed intents: ${MusicIntent.entries.joinToString { it.name }}
            Allowed entity types: BAND, MEMBER, ALBUM, SONG, GENRE, MOOD, UNKNOWN.
            Allowed references: ${ReferenceType.entries.joinToString { it.name }}.
            Context: ${moshi.adapter(ProxyContextDto::class.java).toJson(contextDto(context))}
            Message: ${message.take(500)}
        """.trimIndent()
        val text = generate(prompt, parseSchema, maxOutputTokens = 512)
        val wire = parseJson(moshi.adapter(ParsedRequestWire::class.java), text)
        return validateParsed(ParsedMusicRequest(
            intent = MusicIntent.entries.firstOrNull { it.name == wire.intent } ?: throw InvalidAiResponseException(),
            entities = wire.entities.take(3).map {
                EntityMention(
                    type = MusicEntityType.entries.firstOrNull { type -> type.name == it.type }
                        ?: throw InvalidAiResponseException(),
                    name = it.name.trim().takeIf(String::isNotEmpty) ?: throw InvalidAiResponseException(),
                )
            },
            preferences = wire.preferences.map(String::trim).filter(String::isNotEmpty).distinct().take(12),
            reference = wire.reference?.let {
                MusicReference(
                    type = ReferenceType.entries.firstOrNull { type -> type.name == it.type }
                        ?: throw InvalidAiResponseException(),
                    resultIndex = it.resultIndex,
                )
            } ?: MusicReference(),
        ))
    }

    override suspend fun rankCandidates(
        request: ParsedMusicRequest,
        candidates: List<VerifiedCandidate>,
    ): List<RankedCandidate> {
        ensureConfigured()
        if (candidates.isEmpty()) return emptyList()
        val safeCandidates = candidates.take(10)
        val returned = if (proxyApi != null) {
            call { proxyApi.rank(ProxyRankRequest(request, safeCandidates.map { candidateDto(it.entity) })).recommendations }
        } else {
            val prompt = """
                You rank only the supplied verified candidates. Return only candidate IDs from the supplied list.
                Never create another artist, member, album, song, or ID. Reasons must use supplied metadata only.
                Give each recommendation a useful two-sentence reason: first state the strongest metadata-based match,
                then mention a relevant limitation or secondary match. Do not repeat the candidate name as filler.
                Treat energetic, relaxing, emotional, dark, happy, and focused as subjective vibe preferences.
                Judge vibe fit only from each candidate's supplied genres and tags. Do not state that a vibe is an
                objective fact about a band, and do not invent style details that are absent from the metadata.
                Request: ${moshi.adapter(ParsedMusicRequest::class.java).toJson(request)}
                Candidates: ${moshi.adapter<List<ProxyCandidateDto>>(
                    com.squareup.moshi.Types.newParameterizedType(List::class.java, ProxyCandidateDto::class.java)
                ).toJson(safeCandidates.map { candidateDto(it.entity) })}
            """.trimIndent()
            parseJson(
                moshi.adapter(RankedWireResponse::class.java),
                generate(prompt, rankingSchema, maxOutputTokens = 768),
            ).recommendations.map {
                RankedCandidate(
                    entityId = it.entityId,
                    entityType = MusicEntityType.entries.firstOrNull { type -> type.name == it.entityType }
                        ?: throw InvalidAiResponseException(),
                    reason = it.reason.trim().take(MAX_REASON_CHARS),
                )
            }
        }
        val map = safeCandidates.associateBy { it.entity.id }
        return returned.asSequence()
            .distinctBy { it.entityId }
            .filter { ranked -> map[ranked.entityId]?.entity?.type == ranked.entityType }
            .filter { it.reason.isNotBlank() }
            .take(3)
            .toList()
            .ifEmpty { throw InvalidAiResponseException() }
    }

    override suspend fun explainEntity(entity: ResolvedMusicEntity, question: String): String {
        ensureConfigured()
        if (proxyApi != null) {
            return call { proxyApi.explain(ProxyExplainRequest(candidateDto(entity), question.take(300))).explanation }
                .trim().take(MAX_EXPLANATION_CHARS).takeIf(String::isNotEmpty) ?: throw InvalidAiResponseException()
        }
        val prompt = """
            Answer only from the supplied verified metadata. If the metadata does not contain the requested fact,
            state that the information is unavailable. Do not add names, roles, dates, releases, or biography facts.
            Start with a direct answer to the question. Then give the most useful supporting details in two to four
            short paragraphs (roughly 90 to 180 words when enough metadata exists). Clearly separate verified facts
            from subjective interpretation. Avoid filler, repeated sentences, Markdown headings, and unsupported
            claims. For sparse metadata, be concise and say exactly which requested information is unavailable.
            Entity: ${moshi.adapter(ProxyCandidateDto::class.java).toJson(candidateDto(entity))}
            Question: ${question.take(300)}
        """.trimIndent()
        return parseJson(
            moshi.adapter(ExplanationWire::class.java),
            generate(prompt, explanationSchema, maxOutputTokens = 1024),
        )
            .explanation.trim().take(MAX_EXPLANATION_CHARS).takeIf(String::isNotEmpty)
            ?: throw InvalidAiResponseException()
    }

    private suspend fun generate(
        prompt: String,
        schema: GeminiJsonSchema,
        maxOutputTokens: Int,
    ): String = call {
        directApi!!.generateContent(
            model = model,
            apiKey = directDebugApiKey,
            request = GeminiGenerateRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt)))),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.1f,
                    maxOutputTokens = maxOutputTokens,
                    responseMimeType = "application/json",
                    responseJsonSchema = schema,
                    thinkingConfig = GeminiThinkingConfig("minimal"),
                ),
            ),
        ).text() ?: throw InvalidAiResponseException()
    }

    private fun contextDto(context: ConversationContext) = ProxyContextDto(
        lastEntityId = context.lastResolvedEntity?.id,
        lastEntityType = context.lastResolvedEntity?.type?.name,
        currentBandId = context.currentBand?.id,
        currentMemberId = context.currentMember?.id,
        currentAlbumId = context.currentAlbum?.id,
        currentSongId = context.currentSong?.id,
        previousResultIds = context.previousRecommendations.takeLast(3).map { it.id },
        preferences = context.accumulatedPreferences.take(12),
        lastIntent = context.lastIntent?.name,
    )

    private fun candidateDto(entity: ResolvedMusicEntity): ProxyCandidateDto {
        val metadata = when (entity) {
            is ResolvedMusicEntity.ResolvedBand -> mapOf(
                "countryOrArea" to entity.countryOrArea,
                "genres" to entity.genres.joinToString(),
                "beginDate" to entity.beginDate,
                "status" to entity.ended?.let { if (it) "ended" else "active" },
                "sourceText" to entity.sourceText?.take(4_000),
            )
            is ResolvedMusicEntity.ResolvedMember -> mapOf(
                "memberships" to entity.bands.joinToString { "${it.bandName}: ${it.roles.joinToString()} (${if (it.isCurrent) "current" else "former"})" },
            )
            is ResolvedMusicEntity.ResolvedAlbum -> mapOf(
                "artistName" to entity.artistName,
                "verifiedArtistGenres" to entity.artistGenres.joinToString(),
                "primaryType" to entity.primaryType,
                "firstReleaseDate" to entity.firstReleaseDate,
            )
            is ResolvedMusicEntity.ResolvedSong -> mapOf(
                "artistName" to entity.artistName,
                "verifiedArtistGenres" to entity.artistGenres.joinToString(),
                "albumName" to entity.albumName,
                "durationMillis" to entity.durationMillis?.toString(),
            )
        }
        return ProxyCandidateDto(entity.id, entity.type.name, entity.name, metadata)
    }

    private fun validateParsed(request: ParsedMusicRequest): ParsedMusicRequest {
        if (request.entities.any { it.type == MusicEntityType.UNKNOWN || it.name.isBlank() }) {
            throw InvalidAiResponseException()
        }
        return request.copy(
            entities = request.entities.take(3),
            preferences = request.preferences.map(String::trim).filter(String::isNotEmpty).distinct().take(12),
        )
    }

    private fun ensureConfigured() {
        if (!isConfigured) throw AiNotConfiguredException()
    }

    private suspend fun <T> call(block: suspend () -> T): T {
        repeat(2) { attempt ->
            try {
                return block()
            } catch (error: JsonDataException) {
                throw InvalidAiResponseException(error)
            } catch (error: HttpException) {
                when (error.code()) {
                    400, 401, 403 -> throw AiAuthenticationException()
                    429 -> if (attempt == 1) throw AiQuotaException()
                    in 500..599 -> if (attempt == 1) throw AiServiceUnavailableException()
                    else -> throw error
                }
            } catch (error: SocketTimeoutException) {
                throw AiTimeoutException()
            } catch (error: IOException) {
                if (attempt == 1) throw error
            }
            delay(750L * (attempt + 1))
        }
        error("Unreachable")
    }

    private fun <T> parseJson(adapter: com.squareup.moshi.JsonAdapter<T>, text: String): T = try {
        adapter.fromJson(text.take(MAX_RESPONSE_CHARS)) ?: throw InvalidAiResponseException()
    } catch (error: InvalidAiResponseException) {
        throw error
    } catch (error: Exception) {
        throw InvalidAiResponseException(error)
    }

    private companion object {
        const val MAX_RESPONSE_CHARS = 20_000
        const val MAX_REASON_CHARS = 420
        const val MAX_EXPLANATION_CHARS = 2_000
        val string = GeminiJsonSchema("string")
        val parseSchema = GeminiJsonSchema(
            "object",
            properties = mapOf(
                "intent" to string,
                "entities" to GeminiJsonSchema("array", items = GeminiJsonSchema("object", properties = mapOf("type" to string, "name" to string), required = listOf("type", "name"))),
                "preferences" to GeminiJsonSchema("array", items = string),
                "reference" to GeminiJsonSchema("object", properties = mapOf("type" to string, "resultIndex" to GeminiJsonSchema("integer")), required = listOf("type")),
            ),
            required = listOf("intent", "entities", "preferences", "reference"),
        )
        val rankingSchema = GeminiJsonSchema(
            "object",
            properties = mapOf("recommendations" to GeminiJsonSchema("array", maxItems = 3, items = GeminiJsonSchema("object", properties = mapOf("entityId" to string, "entityType" to string, "reason" to string), required = listOf("entityId", "entityType", "reason")))),
            required = listOf("recommendations"),
        )
        val explanationSchema = GeminiJsonSchema("object", properties = mapOf("explanation" to string), required = listOf("explanation"))
    }
}
