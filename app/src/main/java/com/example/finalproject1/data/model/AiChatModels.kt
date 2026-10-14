package com.example.finalproject1.data.model

enum class MusicEntityType { BAND, MEMBER, ALBUM, SONG, GENRE, MOOD, UNKNOWN }

enum class MusicIntent {
    LOOKUP_ENTITY,
    RECOMMEND_BANDS,
    RECOMMEND_SONGS,
    RECOMMEND_ALBUMS,
    RECOMMEND_SIMILAR,
    LIST_MEMBERS,
    LIST_ALBUMS,
    LIST_SONGS,
    FIND_BAND_FOR_MEMBER,
    FIND_ALBUM_FOR_SONG,
    FIND_ARTIST_FOR_SONG,
    EXPLAIN_ENTITY,
    REFINE_PREVIOUS,
    UNKNOWN,
}

enum class ReferenceType {
    NONE,
    LAST_ENTITY,
    LAST_RECOMMENDATION,
    RESULT_INDEX,
    CURRENT_BAND,
    CURRENT_MEMBER,
    CURRENT_ALBUM,
    CURRENT_SONG,
}

data class EntityMention(val type: MusicEntityType, val name: String)
data class MusicReference(val type: ReferenceType = ReferenceType.NONE, val resultIndex: Int? = null)

data class ParsedMusicRequest(
    val intent: MusicIntent,
    val entities: List<EntityMention> = emptyList(),
    val preferences: List<String> = emptyList(),
    val reference: MusicReference = MusicReference(),
)

sealed interface ResolvedMusicEntity {
    val id: String
    val type: MusicEntityType
    val name: String
    val aliases: List<String>

    data class ResolvedBand(
        override val id: String,
        override val name: String,
        override val aliases: List<String> = emptyList(),
        val musicBrainzId: String? = id,
        val countryOrArea: String? = null,
        val genres: List<String> = emptyList(),
        val beginDate: String? = null,
        val ended: Boolean? = null,
        val imageUrl: String? = null,
        val members: List<ResolvedMember> = emptyList(),
        val sourceText: String? = null,
    ) : ResolvedMusicEntity { override val type = MusicEntityType.BAND }

    data class ResolvedMember(
        override val id: String,
        override val name: String,
        override val aliases: List<String> = emptyList(),
        val bands: List<MemberBandLink> = emptyList(),
        val imageUrl: String? = null,
    ) : ResolvedMusicEntity { override val type = MusicEntityType.MEMBER }

    data class ResolvedAlbum(
        override val id: String,
        override val name: String,
        override val aliases: List<String> = emptyList(),
        val artistName: String? = null,
        val artistId: String? = null,
        val primaryType: String? = null,
        val firstReleaseDate: String? = null,
        val imageUrl: String? = "https://coverartarchive.org/release-group/$id/front-500",
        val artistGenres: List<String> = emptyList(),
    ) : ResolvedMusicEntity { override val type = MusicEntityType.ALBUM }

    data class ResolvedSong(
        override val id: String,
        override val name: String,
        override val aliases: List<String> = emptyList(),
        val artistName: String? = null,
        val artistId: String? = null,
        val albumName: String? = null,
        val albumId: String? = null,
        val durationMillis: Long? = null,
        val artistGenres: List<String> = emptyList(),
    ) : ResolvedMusicEntity { override val type = MusicEntityType.SONG }
}

data class MemberBandLink(
    val bandId: String,
    val bandName: String,
    val roles: List<String> = emptyList(),
    val isCurrent: Boolean = true,
)

data class VerifiedCandidate(val entity: ResolvedMusicEntity)
data class RankedCandidate(val entityId: String, val entityType: MusicEntityType, val reason: String)

data class ConversationContext(
    val lastResolvedEntity: ResolvedMusicEntity? = null,
    val currentBand: ResolvedMusicEntity.ResolvedBand? = null,
    val currentMember: ResolvedMusicEntity.ResolvedMember? = null,
    val currentAlbum: ResolvedMusicEntity.ResolvedAlbum? = null,
    val currentSong: ResolvedMusicEntity.ResolvedSong? = null,
    val previousRecommendations: List<ResolvedMusicEntity> = emptyList(),
    val accumulatedPreferences: Set<String> = emptySet(),
    val lastIntent: MusicIntent? = null,
)

data class ClarificationOption(val label: String, val entity: ResolvedMusicEntity)

enum class ChatEntityAction { VIEW, MEMBERS, ALBUMS, SONGS, SIMILAR, ABOUT_BAND, RECOMMEND_ONE }
data class ChatAction(val label: String, val action: ChatEntityAction, val entity: ResolvedMusicEntity)

sealed interface ChatMessage {
    val id: String

    data class AssistantText(override val id: String, val text: String) : ChatMessage
    data class UserText(override val id: String, val text: String) : ChatMessage
    data class Loading(override val id: String) : ChatMessage
    data class Clarification(
        override val id: String,
        val question: String,
        val options: List<ClarificationOption>,
    ) : ChatMessage
    data class EntityResults(
        override val id: String,
        val introduction: String,
        val entities: List<ResolvedMusicEntity>,
        val actions: List<ChatAction> = emptyList(),
    ) : ChatMessage
    data class Recommendations(
        override val id: String,
        val introduction: String,
        val recommendations: List<VerifiedRecommendation>,
        val actions: List<ChatAction> = emptyList(),
    ) : ChatMessage
    data class Error(override val id: String, val message: String, val canRetry: Boolean) : ChatMessage
}

data class VerifiedRecommendation(val entity: ResolvedMusicEntity, val reason: String)
data class ChatRequest(val message: String, val selectedType: MusicEntityType?)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val selectedEntityType: MusicEntityType? = null,
    val selectedKeywords: Set<String> = emptySet(),
    val suggestedKeywords: List<String> = emptyList(),
    val conversationContext: ConversationContext = ConversationContext(),
    val isLoading: Boolean = false,
    val canSend: Boolean = false,
    val lastRetryableRequest: ChatRequest? = null,
)
