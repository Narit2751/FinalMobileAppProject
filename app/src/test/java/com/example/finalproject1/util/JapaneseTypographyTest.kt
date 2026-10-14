package com.example.finalproject1.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JapaneseTypographyTest {
    @Test
    fun `detects Japanese scripts and ignores Latin names`() {
        assertTrue("朽木の灯".containsJapaneseCharacters())
        assertTrue("ムック".containsJapaneseCharacters())
        assertTrue("小鳩ミク".containsJapaneseCharacters())
        assertFalse("MUCC".containsJapaneseCharacters())
        assertFalse("BAND-MAID".containsJapaneseCharacters())
    }
}
