package com.example.finalproject1.ui.aifinder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalproject1.data.model.*
import com.example.finalproject1.data.remote.*
import com.example.finalproject1.data.repository.MusicEntityResolver
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AiFinderViewModel(
    private val resolver: MusicEntityResolver,
    private val aiService: AiMusicAssistantService,
    private val isOnline: () -> Boolean,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState(
        messages = buildList {
            add(ChatMessage.AssistantText(id(), GREETING))
            if (!aiService.isConfigured) add(ChatMessage.Error(id(), MISSING_CONFIGURATION, true))
        },
        suggestedKeywords = SUGGESTION_PAGES.first(),
    ))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    private var requestJob: Job? = null
    private var suggestionPage = 0

    fun updateInput(text: String) = update { it.copy(inputText = text, canSend = text.isNotBlank() && !it.isLoading) }

    fun selectRecommendationCategory(type: MusicEntityType?) = update { state ->
        val input = recommendationPrompt(type, state.selectedKeywords)
        state.copy(
            selectedEntityType = type,
            inputText = input,
            canSend = input.isNotBlank() && !state.isLoading,
        )
    }

    fun setRecommendationVibe(vibe: String, selected: Boolean) = update { state ->
        val vibes = state.selectedKeywords.toMutableSet().apply {
            if (selected) add(vibe) else remove(vibe)
        }
        val input = recommendationPrompt(state.selectedEntityType, vibes)
        state.copy(
            selectedKeywords = vibes,
            inputText = input,
            canSend = input.isNotBlank() && !state.isLoading,
        )
    }

    fun selectEntityType(type: MusicEntityType?) = update {
        it.copy(selectedEntityType = type, canSend = it.inputText.isNotBlank())
    }

    fun toggleKeyword(keyword: String) = update { state ->
        val selected = state.selectedKeywords.toMutableSet()
        val input = if (selected.remove(keyword)) {
            state.inputText.replace(Regex("(?i)(,?\\s*)${Regex.escape(keyword)}"), "").trim().trim(',')
        } else {
            selected += keyword
            listOf(state.inputText.trim().trimEnd('.', ','), keyword.lowercase()).filter(String::isNotBlank).joinToString(", ")
        }
        state.copy(selectedKeywords = selected, inputText = input, canSend = input.isNotBlank())
    }

    fun moreSuggestions() {
        suggestionPage = (suggestionPage + 1) % SUGGESTION_PAGES.size
        update { it.copy(suggestedKeywords = SUGGESTION_PAGES[suggestionPage]) }
    }

    fun submit() {
        val text = _state.value.inputText.trim()
        if (text.isBlank() || _state.value.isLoading) return
        execute(ChatRequest(text, null))
    }

    fun retry() = _state.value.lastRetryableRequest?.let(::execute)

    fun checkConfiguration() {
        val message: ChatMessage = if (aiService.isConfigured) {
            ChatMessage.AssistantText(id(), "Gemini is configured. Ask me about Japanese music.")
        } else ChatMessage.Error(id(), MISSING_CONFIGURATION, true)
        update { it.copy(messages = it.messages + message) }
    }

    fun resetConversation() {
        requestJob?.cancel()
        _state.value = ChatUiState(messages = listOf(ChatMessage.AssistantText(id(), GREETING)), suggestedKeywords = SUGGESTION_PAGES.first())
    }

    fun chooseClarification(entity: ResolvedMusicEntity) {
        showEntities(listOf(entity), _state.value.conversationContext.lastIntent ?: MusicIntent.LOOKUP_ENTITY)
    }

    fun runAction(action: ChatAction) {
        if (action.action == ChatEntityAction.VIEW) return
        val text = when (action.action) {
            ChatEntityAction.MEMBERS -> "Who are the members of ${action.entity.name}?"
            ChatEntityAction.ALBUMS -> "Show albums from ${action.entity.name}"
            ChatEntityAction.SONGS, ChatEntityAction.RECOMMEND_ONE -> "Recommend one song from ${action.entity.name}"
            ChatEntityAction.SIMILAR -> "Recommend music similar to ${action.entity.name}"
            ChatEntityAction.ABOUT_BAND -> "Tell me about the band connected to ${action.entity.name}"
            ChatEntityAction.VIEW -> return
        }
        val loadingId = id()
        update { state -> state.copy(
            messages = state.messages + ChatMessage.UserText(id(), text) + ChatMessage.Loading(loadingId),
            isLoading = true,
            canSend = false,
        ) }
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            try {
                if (!isOnline()) throw OfflineException()
                when (action.action) {
                    ChatEntityAction.MEMBERS -> showRelated(action.entity, MusicIntent.LIST_MEMBERS, loadingId)
                    ChatEntityAction.ALBUMS -> showRelated(action.entity, MusicIntent.LIST_ALBUMS, loadingId)
                    ChatEntityAction.SONGS, ChatEntityAction.RECOMMEND_ONE -> showRelated(action.entity, MusicIntent.RECOMMEND_SONGS, loadingId)
                    ChatEntityAction.SIMILAR -> recommend(
                        ParsedMusicRequest(MusicIntent.RECOMMEND_SIMILAR, listOf(EntityMention(action.entity.type, action.entity.name)), _state.value.conversationContext.accumulatedPreferences.toList()),
                        loadingId,
                    )
                    ChatEntityAction.ABOUT_BAND -> {
                        val band = relatedBand(action.entity)
                        if (band == null) replaceLoading(loadingId, ChatMessage.AssistantText(id(), "Verified band information is unavailable."))
                        else showEntities(listOf(band), MusicIntent.LOOKUP_ENTITY, loadingId)
                    }
                    ChatEntityAction.VIEW -> Unit
                }
            } catch (error: Exception) {
                replaceLoading(loadingId, ChatMessage.Error(id(), friendlyError(error), true))
            } finally {
                update { it.copy(isLoading = false, canSend = it.inputText.isNotBlank()) }
            }
        }
    }

    private fun execute(chatRequest: ChatRequest) {
        requestJob?.cancel()
        val loadingId = id()
        val current = _state.value
        _state.value = current.copy(
            messages = current.messages + ChatMessage.UserText(id(), chatRequest.message) + ChatMessage.Loading(loadingId),
            inputText = "", selectedEntityType = null, selectedKeywords = emptySet(), isLoading = true, canSend = false,
            lastRetryableRequest = chatRequest,
        )
        requestJob = viewModelScope.launch {
            try {
                if (!isOnline()) throw OfflineException()
                val parsed = parse(chatRequest)
                val referenced = resolveReference(parsed.reference)
                when {
                    parsed.intent in RECOMMENDATION_INTENTS && parsed.entities.isEmpty() && referenced == null -> recommend(parsed, loadingId)
                    referenced != null && parsed.entities.isEmpty() && parsed.intent in RELATED_INTENTS -> showRelated(referenced, parsed.intent, loadingId)
                    referenced != null && parsed.entities.isEmpty() -> showEntityAnswer(referenced, parsed.intent, chatRequest.message, loadingId)
                    parsed.entities.isNotEmpty() -> resolveMention(parsed, chatRequest.message, loadingId)
                    else -> replaceLoading(loadingId, ChatMessage.AssistantText(id(), OUT_OF_SCOPE))
                }
            } catch (error: Exception) {
                replaceLoading(loadingId, ChatMessage.Error(id(), friendlyError(error), true))
            } finally {
                update { it.copy(isLoading = false, canSend = it.inputText.isNotBlank()) }
            }
        }
    }

    private suspend fun parse(request: ChatRequest): ParsedMusicRequest {
        val type = request.selectedType
        if (type != null) {
            val prefix = ENTITY_PREFIXES[type].orEmpty()
            val name = request.message.removePrefix(prefix).trim().ifBlank { request.message.trim() }
            return ParsedMusicRequest(MusicIntent.LOOKUP_ENTITY, listOf(EntityMention(type, name)))
        }
        val context = _state.value.conversationContext
        return LocalMusicRequestParser.parse(request.message, context)
            ?: aiService.parseRequest(request.message, context)
    }

    private suspend fun resolveMention(parsed: ParsedMusicRequest, question: String, loadingId: String) {
        val mention = parsed.entities.first()
        val artistHint = parsed.entities.firstOrNull { it.type == MusicEntityType.BAND }?.name
            ?: _state.value.conversationContext.currentBand?.name
        val results = resolver.resolve(mention.type, mention.name, artistHint)
        if (results.isEmpty()) {
            replaceLoading(loadingId, ChatMessage.Error(id(), "I couldn’t verify that name. Check the spelling or add the band name.", true))
            return
        }
        val exact = results.filter { entity -> entity.name.equals(mention.name, true) || entity.aliases.any { it.equals(mention.name, true) } }
        val choices = exact.ifEmpty { results }
        if (choices.size > 1) {
            replaceLoading(loadingId, ChatMessage.Clarification(id(), "I found several verified matches. Which one did you mean?", choices.take(5).map {
                ClarificationOption("${it.type.name.lowercase().replaceFirstChar(Char::uppercase)} — ${it.name}", it)
            }))
        } else {
            val base = choices.single()
            val related = if (parsed.intent in RELATED_INTENTS) resolver.related(base, parsed.intent) else emptyList()
            if (parsed.intent in RELATED_INTENTS) {
                if (related.isEmpty()) {
                    val unavailable = if (parsed.intent == MusicIntent.LIST_MEMBERS) "I found the artist, but verified band-membership information is unavailable."
                    else "I found the artist, but no verified ${parsed.intent.name.substringAfter("LIST_").lowercase()} were returned."
                    replaceLoading(loadingId, ChatMessage.AssistantText(id(), unavailable))
                } else showEntities(related, parsed.intent, loadingId)
            } else showEntityAnswer(base, parsed.intent, question, loadingId)
        }
    }

    private suspend fun recommend(parsed: ParsedMusicRequest, loadingId: String) {
        val candidates = resolver.candidates(parsed)
        if (candidates.isEmpty()) {
            replaceLoading(loadingId, ChatMessage.Error(id(), "I couldn’t find verified candidates for that request.", true))
            return
        }
        val ranked = aiService.rankCandidates(parsed, candidates)
        val map = candidates.associateBy { it.entity.id }
        val verified = ranked.mapNotNull { result -> map[result.entityId]?.entity?.let { VerifiedRecommendation(it, result.reason) } }
        if (verified.isEmpty()) throw InvalidAiResponseException()
        val entities = verified.map { it.entity }
        replaceLoading(loadingId, ChatMessage.Recommendations(id(), "Verified recommendations", verified, actionsFor(entities)))
        updateContext(entities, parsed.intent, parsed.preferences)
    }

    private suspend fun showRelated(entity: ResolvedMusicEntity, intent: MusicIntent, loadingId: String) {
        val related = resolver.related(entity, intent)
        if (related.isEmpty()) {
            replaceLoading(loadingId, ChatMessage.AssistantText(id(), unavailableRelatedMessage(intent)))
        } else showEntities(related, intent, loadingId)
    }

    private suspend fun showEntityAnswer(
        entity: ResolvedMusicEntity,
        intent: MusicIntent,
        question: String,
        loadingId: String,
    ) {
        val answer = if (shouldUseAiExplanation(intent, question) && aiService.isConfigured) {
            try {
                aiService.explainEntity(entity, question)
            } catch (_: Exception) {
                verifiedOverview(entity)
            }
        } else verifiedOverview(entity)
        val card = ChatMessage.EntityResults(id(), entityIntroduction(entity), listOf(entity), actionsFor(listOf(entity)))
        replaceLoading(loadingId, listOf(ChatMessage.AssistantText(id(), answer), card))
        updateContext(listOf(entity), intent, emptyList())
    }

    private fun relatedBand(entity: ResolvedMusicEntity): ResolvedMusicEntity.ResolvedBand? = when (entity) {
        is ResolvedMusicEntity.ResolvedBand -> entity
        is ResolvedMusicEntity.ResolvedMember -> entity.bands.firstOrNull()?.let { ResolvedMusicEntity.ResolvedBand(it.bandId, it.bandName) }
        is ResolvedMusicEntity.ResolvedAlbum -> entity.artistId?.let { ResolvedMusicEntity.ResolvedBand(it, entity.artistName ?: "Artist") }
        is ResolvedMusicEntity.ResolvedSong -> entity.artistId?.let { ResolvedMusicEntity.ResolvedBand(it, entity.artistName ?: "Artist") }
    }

    private fun showEntities(entities: List<ResolvedMusicEntity>, intent: MusicIntent, loadingId: String? = null) {
        val message = ChatMessage.EntityResults(id(), resultIntroduction(intent, entities.size), entities, actionsFor(entities))
        if (loadingId == null) update { it.copy(messages = it.messages + message) } else replaceLoading(loadingId, message)
        updateContext(entities, intent, emptyList())
    }

    private fun updateContext(entities: List<ResolvedMusicEntity>, intent: MusicIntent, preferences: List<String>) {
        val last = entities.lastOrNull() ?: return
        update { state ->
            val old = state.conversationContext
            state.copy(
                conversationContext = old.copy(
                    lastResolvedEntity = last,
                    currentBand = entities.filterIsInstance<ResolvedMusicEntity.ResolvedBand>().lastOrNull() ?: old.currentBand,
                    currentMember = entities.filterIsInstance<ResolvedMusicEntity.ResolvedMember>().lastOrNull() ?: old.currentMember,
                    currentAlbum = entities.filterIsInstance<ResolvedMusicEntity.ResolvedAlbum>().lastOrNull() ?: old.currentAlbum,
                    currentSong = entities.filterIsInstance<ResolvedMusicEntity.ResolvedSong>().lastOrNull() ?: old.currentSong,
                    previousRecommendations = entities.take(3),
                    accumulatedPreferences = old.accumulatedPreferences + preferences,
                    lastIntent = intent,
                ),
                suggestedKeywords = followUps(last),
            )
        }
    }

    private fun resolveReference(reference: MusicReference): ResolvedMusicEntity? {
        val context = _state.value.conversationContext
        return when (reference.type) {
            ReferenceType.LAST_ENTITY -> context.lastResolvedEntity
            ReferenceType.LAST_RECOMMENDATION -> context.previousRecommendations.lastOrNull()
            ReferenceType.RESULT_INDEX -> reference.resultIndex?.minus(1)?.let(context.previousRecommendations::getOrNull)
            ReferenceType.CURRENT_BAND -> context.currentBand
            ReferenceType.CURRENT_MEMBER -> context.currentMember
            ReferenceType.CURRENT_ALBUM -> context.currentAlbum
            ReferenceType.CURRENT_SONG -> context.currentSong
            ReferenceType.NONE -> null
        }
    }

    private fun actionsFor(entities: List<ResolvedMusicEntity>) = entities.flatMap { entity ->
        when (entity.type) {
            MusicEntityType.BAND -> listOf(ChatAction("View band", ChatEntityAction.VIEW, entity), ChatAction("Members", ChatEntityAction.MEMBERS, entity), ChatAction("Albums", ChatEntityAction.ALBUMS, entity), ChatAction("Recommend song", ChatEntityAction.RECOMMEND_ONE, entity))
            MusicEntityType.MEMBER -> listOf(ChatAction("Their band", ChatEntityAction.ABOUT_BAND, entity), ChatAction("Recommend songs", ChatEntityAction.SONGS, entity))
            MusicEntityType.ALBUM -> listOf(ChatAction("View album", ChatEntityAction.VIEW, entity), ChatAction("About band", ChatEntityAction.ABOUT_BAND, entity), ChatAction("Recommend song", ChatEntityAction.RECOMMEND_ONE, entity))
            MusicEntityType.SONG -> listOf(ChatAction("About band", ChatEntityAction.ABOUT_BAND, entity), ChatAction("Similar songs", ChatEntityAction.SIMILAR, entity))
            else -> emptyList()
        }
    }.take(6)

    private fun followUps(entity: ResolvedMusicEntity) = when (entity.type) {
        MusicEntityType.BAND -> listOf("Members", "Albums", "Top songs", "Similar bands", "Recommend one song", "More like this")
        MusicEntityType.MEMBER -> listOf("Their band", "Other projects", "Role in the band", "Recommend songs", "About the band", "Current member")
        MusicEntityType.ALBUM -> listOf("Track list", "About the band", "Similar albums", "Recommend one song", "More albums", "Release year")
        MusicEntityType.SONG -> listOf("Which album?", "About the band", "Similar songs", "More from this band", "Save band", "Relaxing")
        else -> SUGGESTION_PAGES.first()
    }

    private fun replaceLoading(loadingId: String, message: ChatMessage) = update { state ->
        state.copy(messages = state.messages.map { if (it.id == loadingId) message else it })
    }

    private fun replaceLoading(loadingId: String, messages: List<ChatMessage>) = update { state ->
        state.copy(messages = state.messages.flatMap { if (it.id == loadingId) messages else listOf(it) })
    }

    private fun shouldUseAiExplanation(intent: MusicIntent, question: String): Boolean {
        if (intent == MusicIntent.EXPLAIN_ENTITY) return true
        val lower = question.lowercase()
        return listOf("tell me", "explain", "about", "what is", "who is", "style", "origin", "why", "how")
            .any(lower::contains)
    }

    private fun verifiedOverview(entity: ResolvedMusicEntity): String = when (entity) {
        is ResolvedMusicEntity.ResolvedBand -> buildList {
            add("${entity.name} is a verified music group${entity.countryOrArea?.let { " associated with $it" }.orEmpty()}.")
            if (entity.genres.isNotEmpty()) add("MusicBrainz lists these genres or tags: ${entity.genres.take(5).joinToString()}.")
            entity.beginDate?.let { add("Its recorded begin date is $it.") }
            entity.ended?.let { add(if (it) "MusicBrainz marks the group as ended." else "MusicBrainz does not mark the group as ended.") }
            if (size == 1) add("More verified background information is unavailable in the current metadata.")
        }.joinToString(" ")
        is ResolvedMusicEntity.ResolvedMember -> buildList {
            add("${entity.name} is a verified person entry in MusicBrainz.")
            if (entity.bands.isEmpty()) add("Verified band-membership details are unavailable.")
            else add(entity.bands.joinToString(prefix = "Verified band connections: ", postfix = ".") { link ->
                val role = link.roles.takeIf(List<String>::isNotEmpty)?.joinToString()?.let { ", role: $it" }.orEmpty()
                "${link.bandName}$role (${if (link.isCurrent) "current" else "former"})"
            })
        }.joinToString(" ")
        is ResolvedMusicEntity.ResolvedAlbum -> buildList {
            add("${entity.name} is a verified ${entity.primaryType?.lowercase() ?: "release group"}${entity.artistName?.let { " by $it" }.orEmpty()}.")
            entity.firstReleaseDate?.let { add("Its first-release date is $it.") }
            if (size == 1) add("Additional verified release details are unavailable in the current metadata.")
        }.joinToString(" ")
        is ResolvedMusicEntity.ResolvedSong -> buildList {
            add("${entity.name} is a verified recording${entity.artistName?.let { " credited to $it" }.orEmpty()}.")
            entity.albumName?.let { add("The available release data connects it to $it.") }
            entity.durationMillis?.let { duration -> add("The recorded duration is ${duration / 60_000}:${((duration / 1_000) % 60).toString().padStart(2, '0')}.") }
            if (size == 1) add("Additional verified recording details are unavailable.")
        }.joinToString(" ")
    }

    private fun entityIntroduction(entity: ResolvedMusicEntity) = "Verified ${entity.type.name.lowercase()} record"

    private fun resultIntroduction(intent: MusicIntent, count: Int): String = when (intent) {
        MusicIntent.LIST_MEMBERS -> "$count verified band member${if (count == 1) "" else "s"}"
        MusicIntent.LIST_ALBUMS -> "$count verified album${if (count == 1) "" else "s"}"
        MusicIntent.LIST_SONGS, MusicIntent.RECOMMEND_SONGS -> "$count verified song${if (count == 1) "" else "s"}"
        MusicIntent.FIND_ALBUM_FOR_SONG -> "Verified album connected to this recording"
        MusicIntent.FIND_ARTIST_FOR_SONG -> "Verified artist credited for this recording"
        MusicIntent.FIND_BAND_FOR_MEMBER -> "Verified band connection"
        else -> "Verified music data"
    }

    private fun unavailableRelatedMessage(intent: MusicIntent): String = when (intent) {
        MusicIntent.LIST_MEMBERS -> "I found the artist, but verified band-membership information is unavailable."
        MusicIntent.FIND_ALBUM_FOR_SONG -> "I found the song, but its verified album information is unavailable."
        MusicIntent.FIND_ARTIST_FOR_SONG -> "I found the song, but its verified artist credit is unavailable."
        MusicIntent.FIND_BAND_FOR_MEMBER -> "I found the member, but no verified band relationship is available."
        MusicIntent.LIST_ALBUMS -> "I found the artist, but no verified albums were returned."
        MusicIntent.LIST_SONGS, MusicIntent.RECOMMEND_SONGS -> "I found the artist, but no verified songs were returned."
        else -> "No verified results were returned for that follow-up."
    }

    private fun recommendationPrompt(type: MusicEntityType?, vibes: Set<String>): String {
        if (type == null && vibes.isEmpty()) return ""
        val target = when (type) {
            MusicEntityType.BAND -> "a Japanese band"
            MusicEntityType.SONG -> "a Japanese song"
            else -> "Japanese music"
        }
        if (vibes.isEmpty()) return "Recommend $target."
        val vibeText = vibes.joinToString(" and ") { it.lowercase() }
        val suffix = if (vibes.size == 1) "vibe" else "vibes"
        return "Recommend $target with $vibeText $suffix."
    }

    private fun friendlyError(error: Exception) = when (error) {
        is AiNotConfiguredException -> MISSING_CONFIGURATION
        is AiAuthenticationException -> "The Gemini configuration was rejected. Check the API credentials."
        is AiQuotaException -> "The AI service is temporarily unavailable because its request limit was reached."
        is AiServiceUnavailableException -> "Gemini is temporarily unavailable. Please try again in a moment."
        is AiTimeoutException -> "Gemini did not respond within 25 seconds. Please try again."
        is InvalidAiResponseException -> "I couldn’t understand the AI response safely. Please try again."
        is OfflineException -> "Connect to the internet to use AI Chat and verify music data."
        else -> "Music search is unavailable right now. Please retry."
    }

    private inline fun update(transform: (ChatUiState) -> ChatUiState) { _state.value = transform(_state.value) }
    private class OfflineException : Exception()

    companion object {
        const val GREETING = "Hi! I can help you discover Japanese music. Enter a band, member, album, song, genre, mood, or listening activity."
        const val MISSING_CONFIGURATION = "Gemini is not configured. Add the local debug configuration or configure the AI proxy to enable chat recommendations."
        const val OUT_OF_SCOPE = "I can help you discover Japanese bands, members, albums, and songs. Try entering a name, genre, mood, or listening activity."
        val RECOMMENDATION_INTENTS = setOf(MusicIntent.RECOMMEND_BANDS, MusicIntent.RECOMMEND_SONGS, MusicIntent.RECOMMEND_ALBUMS, MusicIntent.RECOMMEND_SIMILAR, MusicIntent.REFINE_PREVIOUS)
        val RELATED_INTENTS = setOf(
            MusicIntent.LIST_MEMBERS,
            MusicIntent.LIST_ALBUMS,
            MusicIntent.LIST_SONGS,
            MusicIntent.RECOMMEND_SONGS,
            MusicIntent.FIND_BAND_FOR_MEMBER,
            MusicIntent.FIND_ALBUM_FOR_SONG,
            MusicIntent.FIND_ARTIST_FOR_SONG,
        )
        val ENTITY_PREFIXES = mapOf(MusicEntityType.BAND to "Tell me about the band ", MusicEntityType.MEMBER to "Which band is this member from? ", MusicEntityType.ALBUM to "Tell me about the album ", MusicEntityType.SONG to "Recommend music like the song ")
        val SUGGESTION_PAGES = listOf(
            listOf("Energetic", "J-Rock", "Metal", "Female vocals", "Relaxing", "Workout"),
            listOf("Emotional", "J-Pop", "Punk", "Guitar-heavy", "Fast tempo", "Study"),
            listOf("Dark", "Jazz", "Electronic", "Melodic", "Experimental", "Modern"),
            listOf("Happy", "Visual kei", "Alternative", "Instrumental", "Classic", "Beginner-friendly"),
        )
        private fun id() = UUID.randomUUID().toString()
    }
}
