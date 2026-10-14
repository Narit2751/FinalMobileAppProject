package com.example.finalproject1.data.remote

object MusicBrainzQueryBuilder {
    fun explore(genre: String): String {
        val tag = escape(genre.ifBlank { "rock" })
        return "type:group AND country:JP AND tag:\"$tag\""
    }

    fun nameSearch(text: String): String =
        "artist:\"${escape(text.trim())}\" AND type:group"

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}
