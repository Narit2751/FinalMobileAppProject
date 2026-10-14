package com.example.finalproject1.ui.aifinder

import com.example.finalproject1.data.model.ConversationContext
import com.example.finalproject1.data.model.MusicEntityType
import com.example.finalproject1.data.model.MusicIntent
import com.example.finalproject1.data.model.ReferenceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalMusicRequestParserTest {
    private val context = ConversationContext()

    @Test fun `common recommendation avoids AI parsing`() {
        val result = LocalMusicRequestParser.parse(
            "Recommend an energetic Japanese rock band with female vocals",
            context,
        )

        assertEquals(MusicIntent.RECOMMEND_BANDS, result?.intent)
        assertEquals(listOf("energetic", "rock", "female vocals"), result?.preferences)
    }

    @Test fun `natural vibe synonyms become recommendation keywords`() {
        val result = LocalMusicRequestParser.parse("Recommend a chill Japanese band for studying", context)

        assertEquals(MusicIntent.RECOMMEND_BANDS, result?.intent)
        assertEquals(listOf("relaxing", "study"), result?.preferences)
    }

    @Test fun `find me a vibe song is a recommendation not a title lookup`() {
        val result = LocalMusicRequestParser.parse(
            "I want to find Energetic vibe song can you find me one?",
            context,
        )

        assertEquals(MusicIntent.RECOMMEND_SONGS, result?.intent)
        assertEquals(listOf("energetic"), result?.preferences)
        assertEquals(emptyList<com.example.finalproject1.data.model.EntityMention>(), result?.entities)
    }

    @Test fun `natural recommendation chooses requested band or album result type`() {
        assertEquals(
            MusicIntent.RECOMMEND_BANDS,
            LocalMusicRequestParser.parse("Find me a happy Japanese band", context)?.intent,
        )
        assertEquals(
            MusicIntent.RECOMMEND_ALBUMS,
            LocalMusicRequestParser.parse("Suggest a dark Japanese album", context)?.intent,
        )
    }

    @Test fun `short artist name is a band lookup without corrupting BAND prefix`() {
        val result = LocalMusicRequestParser.parse("BAND-MAID", context)

        assertEquals(MusicIntent.LOOKUP_ENTITY, result?.intent)
        assertEquals(MusicEntityType.BAND, result?.entities?.single()?.type)
        assertEquals("BAND-MAID", result?.entities?.single()?.name)
    }

    @Test fun `explicit album is recognized locally`() {
        val result = LocalMusicRequestParser.parse("Tell me about the album Conqueror", context)

        assertEquals(MusicEntityType.ALBUM, result?.entities?.single()?.type)
        assertEquals("Conqueror", result?.entities?.single()?.name)
    }

    @Test fun `numbered result is resolved without Gemini`() {
        val result = LocalMusicRequestParser.parse("Show albums from the second result", context)

        assertEquals(MusicIntent.LIST_ALBUMS, result?.intent)
        assertEquals(ReferenceType.RESULT_INDEX, result?.reference?.type)
        assertEquals(2, result?.reference?.resultIndex)
    }

    @Test fun `ambiguous conversational question falls through to Gemini`() {
        assertNull(LocalMusicRequestParser.parse("Could you surprise me with something unusual?", context))
    }
}
