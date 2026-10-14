package com.example.finalproject1.util

import android.graphics.Typeface
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.example.finalproject1.R

private val JAPANESE_CHARACTERS = Regex(
    "[\\u3040-\\u30ff\\u3400-\\u4dbf\\u4e00-\\u9fff\\uf900-\\ufaff]",
)

/** Returns true when the text contains hiragana, katakana, or Japanese-use kanji. */
fun CharSequence?.containsJapaneseCharacters(): Boolean =
    this != null && JAPANESE_CHARACTERS.containsMatchIn(this)

/**
 * Uses Rampart One for Japanese text and restores the supplied typeface for other text.
 * Supplying the original typeface is important for RecyclerView cells that are reused.
 */
fun TextView.setJapaneseAwareText(value: CharSequence?, defaultTypeface: Typeface) {
    text = value
    typeface = if (value.containsJapaneseCharacters()) {
        ResourcesCompat.getFont(context, R.font.rampart_one) ?: defaultTypeface
    } else {
        defaultTypeface
    }
}
