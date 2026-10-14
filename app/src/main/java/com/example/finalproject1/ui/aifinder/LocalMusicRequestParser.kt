package com.example.finalproject1.ui.aifinder

import com.example.finalproject1.data.model.ConversationContext
import com.example.finalproject1.data.model.EntityMention
import com.example.finalproject1.data.model.MusicEntityType
import com.example.finalproject1.data.model.MusicIntent
import com.example.finalproject1.data.model.MusicReference
import com.example.finalproject1.data.model.ParsedMusicRequest
import com.example.finalproject1.data.model.ReferenceType

/** Fast, deterministic parsing for unambiguous chat requests. Ambiguous text still goes to Gemini. */
object LocalMusicRequestParser {
    private val preferenceAliases = linkedMapOf(
        "energetic" to listOf("energetic", "upbeat", "intense", "high energy"),
        "relaxing" to listOf("relaxing", "relaxed", "calm", "chill", "peaceful"),
        "emotional" to listOf("emotional", "heartfelt", "sad"),
        "dark" to listOf("dark", "moody", "gothic"),
        "happy" to listOf("happy", "cheerful", "fun"),
        "focused" to listOf("focused", "focus", "concentration"),
        "j-rock" to listOf("j-rock"), "j-pop" to listOf("j-pop"), "rock" to listOf("rock"),
        "metal" to listOf("metal"), "punk" to listOf("punk"), "jazz" to listOf("jazz"),
        "electronic" to listOf("electronic"), "visual kei" to listOf("visual kei"),
        "alternative" to listOf("alternative"), "female vocals" to listOf("female vocals"),
        "male vocals" to listOf("male vocals"), "guitar-heavy" to listOf("guitar-heavy"),
        "bass-heavy" to listOf("bass-heavy"), "fast tempo" to listOf("fast tempo"),
        "melodic" to listOf("melodic"), "experimental" to listOf("experimental"),
        "instrumental" to listOf("instrumental"), "classic" to listOf("classic"),
        "2000s" to listOf("2000s"), "modern" to listOf("modern"),
        "workout" to listOf("workout"), "study" to listOf("study", "studying"),
        "driving" to listOf("driving"), "beginner-friendly" to listOf("beginner-friendly"),
    )

    fun parse(message: String, context: ConversationContext): ParsedMusicRequest? {
        val clean = message.trim()
        val lower = clean.lowercase()
        if (clean.isEmpty()) return null

        val reference = reference(lower, context)
        val foundPreferences = preferenceAliases.mapNotNull { (canonical, aliases) ->
            canonical.takeIf {
                aliases.any { alias ->
                    Regex("(?<![\\p{L}\\p{N}])${Regex.escape(alias)}(?![\\p{L}\\p{N}])")
                        .containsMatchIn(lower)
                }
            }
        }.filterNot { it == "rock" && "j-rock" in lower }.distinct()

        if (reference.type == ReferenceType.CURRENT_SONG && lower.contains("album")) {
            return ParsedMusicRequest(MusicIntent.FIND_ALBUM_FOR_SONG, preferences = foundPreferences, reference = reference)
        }
        if (reference.type == ReferenceType.CURRENT_SONG && (lower.contains("band") || lower.contains("artist") || lower.contains("perform"))) {
            return ParsedMusicRequest(MusicIntent.FIND_ARTIST_FOR_SONG, preferences = foundPreferences, reference = reference)
        }
        if (reference.type == ReferenceType.CURRENT_MEMBER && lower.contains("band")) {
            return ParsedMusicRequest(MusicIntent.FIND_BAND_FOR_MEMBER, preferences = foundPreferences, reference = reference)
        }
        if (lower.contains("member") && (lower.contains("who") || lower.contains("show") || lower.contains("list"))) {
            return ParsedMusicRequest(MusicIntent.LIST_MEMBERS, explicitBand(clean), foundPreferences, reference)
        }
        if (lower.contains("album") && (lower.contains("show") || lower.contains("list") || lower.contains("from"))) {
            return ParsedMusicRequest(MusicIntent.LIST_ALBUMS, explicitBand(clean), foundPreferences, reference)
        }
        val recommendationCue = lower.contains("recommend") || lower.contains("suggest") ||
            lower.startsWith("find ") || lower.contains("find me") || lower.contains("find one") ||
            lower.contains("i want to find") || lower.contains("i want a ") || lower.contains("i want an ") ||
            lower.contains("looking for") || lower.contains("give me")
        if (recommendationCue &&
            (lower.contains("band") || lower.contains("song") || lower.contains("album") ||
                lower.contains("music") || foundPreferences.isNotEmpty())) {
            return ParsedMusicRequest(
                intent = when {
                    lower.contains("song") -> MusicIntent.RECOMMEND_SONGS
                    lower.contains("album") -> MusicIntent.RECOMMEND_ALBUMS
                    lower.contains("similar") || lower.contains("like this") -> MusicIntent.RECOMMEND_SIMILAR
                    else -> MusicIntent.RECOMMEND_BANDS
                },
                entities = emptyList(),
                preferences = foundPreferences,
                reference = reference,
            )
        }

        typedEntity(clean, "album", MusicEntityType.ALBUM)?.let { return it }
        typedEntity(clean, "song", MusicEntityType.SONG)?.let { return it }
        typedEntity(clean, "member", MusicEntityType.MEMBER)?.let { return it }
        typedEntity(clean, "band", MusicEntityType.BAND)?.let { return it }

        if (reference.type != ReferenceType.NONE &&
            (lower.contains("this") || lower.contains("that") || lower.contains("previous") || lower.contains("result"))) {
            return ParsedMusicRequest(MusicIntent.LOOKUP_ENTITY, reference = reference, preferences = foundPreferences)
        }

        // A short name-only input is treated as a band lookup, matching the app's main discovery purpose.
        val looksLikeName = clean.length <= 60 && clean.split(Regex("\\s+")).size <= 5 &&
            clean.none { it == '?' } && REQUEST_WORDS.none { lower.startsWith(it) }
        return if (looksLikeName) {
            ParsedMusicRequest(MusicIntent.LOOKUP_ENTITY, listOf(EntityMention(MusicEntityType.BAND, clean)))
        } else null
    }

    private fun typedEntity(
        original: String,
        marker: String,
        type: MusicEntityType,
    ): ParsedMusicRequest? {
        val match = Regex(
            "(?i)^(?:(?:tell me about|look up|find|who is|what is)\\s+(?:the\\s+)?)?" +
                Regex.escape(marker) + "(?:\\s+named|\\s*:)?\\s+(.+?)[?.!]*$",
        ).matchEntire(original.trim()) ?: return null
        val name = match.groupValues[1].trim().trim('"', '\'')
        if (name.isBlank() || name.equals("is this", true)) return null
        val intent = if (original.trim().startsWith("tell me about", ignoreCase = true) ||
            original.trim().startsWith("what is", ignoreCase = true) ||
            original.trim().startsWith("who is", ignoreCase = true)) {
            MusicIntent.EXPLAIN_ENTITY
        } else MusicIntent.LOOKUP_ENTITY
        return ParsedMusicRequest(intent, listOf(EntityMention(type, name)))
    }

    private fun explicitBand(message: String): List<EntityMention> {
        val match = Regex("(?i)(?:of|from|by)\\s+(.+?)[?.!]*$").find(message)?.groupValues?.getOrNull(1)?.trim()
        return match?.takeIf(String::isNotBlank)?.let { listOf(EntityMention(MusicEntityType.BAND, it)) }.orEmpty()
    }

    private fun reference(lower: String, context: ConversationContext): MusicReference = when {
        lower.contains("first result") || lower.contains("#1") -> MusicReference(ReferenceType.RESULT_INDEX, 1)
        lower.contains("second result") || lower.contains("#2") -> MusicReference(ReferenceType.RESULT_INDEX, 2)
        lower.contains("third result") || lower.contains("#3") -> MusicReference(ReferenceType.RESULT_INDEX, 3)
        lower.contains("previous result") -> MusicReference(ReferenceType.LAST_RECOMMENDATION)
        lower.contains("that band") || lower.contains("this band") -> MusicReference(ReferenceType.CURRENT_BAND)
        lower.contains("that member") || lower.contains("this member") -> MusicReference(ReferenceType.CURRENT_MEMBER)
        lower.contains("that album") || lower.contains("this album") -> MusicReference(ReferenceType.CURRENT_ALBUM)
        lower.contains("that song") || lower.contains("this song") -> MusicReference(ReferenceType.CURRENT_SONG)
        lower == "it" && context.lastResolvedEntity != null -> MusicReference(ReferenceType.LAST_ENTITY)
        else -> MusicReference()
    }

    private val REQUEST_WORDS = listOf("who ", "what ", "which ", "where ", "when ", "why ", "how ", "tell ", "show ", "list ", "recommend ", "find ")
}
