package com.example.finalproject1.data.remote

import com.example.finalproject1.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import java.net.SocketTimeoutException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class GeminiMusicAssistantServiceTest {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val band = ResolvedMusicEntity.ResolvedBand("known", "Known Band", genres = listOf("rock"))

    @Test fun `missing direct key is not configured`() = runTest {
        val service = GeminiMusicAssistantService("model", moshi, RecordingGeminiApi("{}"), "")
        assertEquals(false, service.isConfigured)
        var thrown: Exception? = null
        try { service.parseRequest("hello", ConversationContext()) } catch (error: Exception) { thrown = error }
        assertEquals(AiNotConfiguredException::class.java, thrown?.javaClass)
    }

    @Test fun `ranking accepts only known id and matching type`() = runTest {
        val api = RecordingGeminiApi("""{"recommendations":[{"entityId":"known","entityType":"BAND","reason":"Verified rock metadata."}]}""")
        val service = GeminiMusicAssistantService("current-model", moshi, api, "debug-test-key")
        val result = service.rankCandidates(ParsedMusicRequest(MusicIntent.RECOMMEND_BANDS), listOf(VerifiedCandidate(band)))
        assertEquals("known", result.single().entityId)
        assertEquals("current-model", api.model)
        assertEquals("application/json", api.request?.generationConfig?.responseMimeType)
    }

    @Test fun `unknown candidate id is rejected`() = runTest {
        val api = RecordingGeminiApi("""{"recommendations":[{"entityId":"invented","entityType":"BAND","reason":"No."}]}""")
        val service = GeminiMusicAssistantService("model", moshi, api, "debug-test-key")
        var thrown: Exception? = null
        try { service.rankCandidates(ParsedMusicRequest(MusicIntent.RECOMMEND_BANDS), listOf(VerifiedCandidate(band))) }
        catch (error: Exception) { thrown = error }
        assertEquals(InvalidAiResponseException::class.java, thrown?.javaClass)
    }

    @Test fun `candidate entity type mismatch is rejected`() = runTest {
        val api = RecordingGeminiApi("""{"recommendations":[{"entityId":"known","entityType":"SONG","reason":"Wrong type."}]}""")
        val service = GeminiMusicAssistantService("model", moshi, api, "key")
        var thrown: Exception? = null
        try { service.rankCandidates(ParsedMusicRequest(MusicIntent.RECOMMEND_BANDS), listOf(VerifiedCandidate(band))) }
        catch (error: Exception) { thrown = error }
        assertEquals(InvalidAiResponseException::class.java, thrown?.javaClass)
    }

    @Test fun `invalid JSON and unknown intent are rejected`() = runTest {
        val invalidJson = GeminiMusicAssistantService("model", moshi, RecordingGeminiApi("not json"), "key")
        var invalidThrown: Exception? = null
        try { invalidJson.parseRequest("hello", ConversationContext()) } catch (error: Exception) { invalidThrown = error }
        assertEquals(InvalidAiResponseException::class.java, invalidThrown?.javaClass)

        val unknown = """{"intent":"MAKE_UP_FACTS","entities":[],"preferences":[],"reference":{"type":"NONE"}}"""
        val unknownService = GeminiMusicAssistantService("model", moshi, RecordingGeminiApi(unknown), "key")
        var unknownThrown: Exception? = null
        try { unknownService.parseRequest("hello", ConversationContext()) } catch (error: Exception) { unknownThrown = error }
        assertEquals(InvalidAiResponseException::class.java, unknownThrown?.javaClass)
    }

    @Test fun `context prompt contains ids but not entity source text or history`() = runTest {
        val valid = """{"intent":"LOOKUP_ENTITY","entities":[{"type":"BAND","name":"BAND-MAID"}],"preferences":[],"reference":{"type":"NONE"}}"""
        val api = RecordingGeminiApi(valid)
        val service = GeminiMusicAssistantService("model", moshi, api, "key")
        service.parseRequest("Tell me about BAND-MAID", ConversationContext(lastResolvedEntity = band.copy(sourceText = "PRIVATE_UNNEEDED_TEXT")))
        val prompt = api.request!!.contents.single().parts.single().text
        assertEquals(true, prompt.contains("known"))
        assertEquals(false, prompt.contains("PRIVATE_UNNEEDED_TEXT"))
    }

    @Test fun `entity explanation requests a direct detailed grounded answer`() = runTest {
        val api = RecordingGeminiApi("""{"explanation":"A grounded explanation."}""")
        val service = GeminiMusicAssistantService("model", moshi, api, "key")

        assertEquals("A grounded explanation.", service.explainEntity(band, "Explain this band's style"))
        val prompt = api.request!!.contents.single().parts.single().text
        assertEquals(true, prompt.contains("Start with a direct answer"))
        assertEquals(true, prompt.contains("two to four"))
        assertEquals(true, prompt.contains("rock"))
        assertEquals(1024, api.request?.generationConfig?.maxOutputTokens)
    }

    @Test fun `authentication quota timeout and service failures are classified`() = runTest {
        suspend fun failureFor(error: Exception): Exception? {
            val service = GeminiMusicAssistantService("model", moshi, FailingGeminiApi(error), "key")
            return try { service.parseRequest("hello", ConversationContext()); null } catch (caught: Exception) { caught }
        }
        assertEquals(AiAuthenticationException::class.java, failureFor(httpError(401))?.javaClass)
        assertEquals(AiQuotaException::class.java, failureFor(httpError(429))?.javaClass)
        assertEquals(AiServiceUnavailableException::class.java, failureFor(httpError(503))?.javaClass)
        assertEquals(AiTimeoutException::class.java, failureFor(SocketTimeoutException())?.javaClass)
    }

    @Test fun `duplicate ids and more than three recommendations are filtered`() = runTest {
        val candidates = (1..4).map { VerifiedCandidate(ResolvedMusicEntity.ResolvedBand("id$it", "Band $it")) }
        val json = """{"recommendations":[
            {"entityId":"id1","entityType":"BAND","reason":"1"},
            {"entityId":"id1","entityType":"BAND","reason":"duplicate"},
            {"entityId":"id2","entityType":"BAND","reason":"2"},
            {"entityId":"id3","entityType":"BAND","reason":"3"},
            {"entityId":"id4","entityType":"BAND","reason":"4"}]}
        """.trimIndent()
        val result = GeminiMusicAssistantService("model", moshi, RecordingGeminiApi(json), "key")
            .rankCandidates(ParsedMusicRequest(MusicIntent.RECOMMEND_BANDS), candidates)
        assertEquals(listOf("id1", "id2", "id3"), result.map { it.entityId })
    }

    private class RecordingGeminiApi(private val json: String) : GeminiApi {
        var model: String? = null
        var request: GeminiGenerateRequest? = null
        override suspend fun generateContent(model: String, apiKey: String, request: GeminiGenerateRequest): GeminiGenerateResponse {
            this.model = model
            this.request = request
            return GeminiGenerateResponse(listOf(GeminiCandidate(GeminiResponseContent(listOf(GeminiResponsePart(json))))))
        }
    }

    private class FailingGeminiApi(private val error: Exception) : GeminiApi {
        override suspend fun generateContent(model: String, apiKey: String, request: GeminiGenerateRequest): GeminiGenerateResponse = throw error
    }

    private fun httpError(code: Int) = HttpException(Response.error<GeminiGenerateResponse>(code, "".toResponseBody()))
}
