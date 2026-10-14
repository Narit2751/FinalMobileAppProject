package com.example.finalproject1.ui.aifinder

import com.example.finalproject1.data.model.*
import com.example.finalproject1.data.remote.FakeAiMusicAssistantService
import com.example.finalproject1.data.repository.MusicEntityResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiFinderViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val band = ResolvedMusicEntity.ResolvedBand("verified-id", "BAND-MAID", genres = listOf("rock"))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `blank message cannot be sent and duplicate keyword is prevented`() {
        val vm = createVm()
        vm.updateInput("   ")
        assertFalse(vm.state.value.canSend)
        vm.toggleKeyword("Energetic")
        vm.toggleKeyword("Energetic")
        assertTrue(vm.state.value.selectedKeywords.isEmpty())
    }

    @Test fun `song category and vibe create an editable recommendation prompt`() {
        val vm = createVm()
        vm.selectRecommendationCategory(MusicEntityType.SONG)
        vm.setRecommendationVibe("Energetic", true)
        assertEquals("Recommend a Japanese song with energetic vibe.", vm.state.value.inputText)
        assertEquals(MusicEntityType.SONG, vm.state.value.selectedEntityType)
        assertTrue(vm.state.value.canSend)
    }

    @Test fun `multiple vibes can be combined and removed`() {
        val vm = createVm()
        vm.selectRecommendationCategory(MusicEntityType.BAND)
        vm.setRecommendationVibe("Energetic", true)
        vm.setRecommendationVibe("Dark", true)
        assertEquals("Recommend a Japanese band with energetic and dark vibes.", vm.state.value.inputText)
        vm.setRecommendationVibe("Energetic", false)
        assertEquals("Recommend a Japanese band with dark vibe.", vm.state.value.inputText)
    }

    @Test fun `entity mode resolves verified band and replaces loading`() = runTest(dispatcher) {
        val vm = createVm()
        vm.selectEntityType(MusicEntityType.BAND)
        vm.updateInput("BAND-MAID")
        vm.submit(); advanceUntilIdle()
        assertTrue(vm.state.value.messages.last() is ChatMessage.EntityResults)
        assertFalse(vm.state.value.messages.any { it is ChatMessage.Loading })
        assertEquals("verified-id", vm.state.value.conversationContext.currentBand?.id)
    }

    @Test fun `tell me about request includes grounded explanation and verified card`() = runTest(dispatcher) {
        val ai = FakeAiMusicAssistantService(explanation = "Grounded answer using verified metadata.")
        val vm = createVm(ai)
        vm.updateInput("Tell me about the band BAND-MAID")
        vm.submit(); advanceUntilIdle()

        assertTrue(vm.state.value.messages.any {
            it is ChatMessage.AssistantText && it.text == "Grounded answer using verified metadata."
        })
        assertTrue(vm.state.value.messages.last() is ChatMessage.EntityResults)
        assertFalse(vm.state.value.messages.any { it is ChatMessage.Loading })
    }

    @Test fun `recommendation displays only fake ranked verified candidate`() = runTest(dispatcher) {
        val ai = FakeAiMusicAssistantService(
            parsed = ParsedMusicRequest(MusicIntent.RECOMMEND_BANDS, preferences = listOf("rock")),
            ranked = listOf(RankedCandidate("verified-id", MusicEntityType.BAND, "Verified metadata match")),
        )
        val vm = createVm(ai)
        vm.updateInput("Recommend rock")
        vm.submit(); advanceUntilIdle()
        val result = vm.state.value.messages.last() as ChatMessage.Recommendations
        assertEquals("verified-id", result.recommendations.single().entity.id)
    }

    @Test fun `missing Gemini configuration is shown without crashing`() {
        val vm = createVm(FakeAiMusicAssistantService(isConfigured = false))
        assertTrue(vm.state.value.messages.any { it is ChatMessage.Error && it.message.contains("not configured") })
    }

    @Test fun `new chat clears context but keeps greeting`() = runTest(dispatcher) {
        val vm = createVm()
        vm.selectEntityType(MusicEntityType.BAND)
        vm.updateInput("BAND-MAID")
        vm.submit(); advanceUntilIdle()
        vm.resetConversation()
        assertNull(vm.state.value.conversationContext.lastResolvedEntity)
        assertEquals(1, vm.state.value.messages.size)
    }

    private fun createVm(ai: FakeAiMusicAssistantService = FakeAiMusicAssistantService()) =
        AiFinderViewModel(FakeResolver(band), ai) { true }
}

private class FakeResolver(private val band: ResolvedMusicEntity.ResolvedBand) : MusicEntityResolver {
    override suspend fun resolve(type: MusicEntityType, name: String, artistHint: String?) = listOf(band)
    override suspend fun candidates(request: ParsedMusicRequest) = listOf(VerifiedCandidate(band))
    override suspend fun related(entity: ResolvedMusicEntity, intent: MusicIntent) = emptyList<ResolvedMusicEntity>()
}
