package com.example.finalproject1.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

data class WikipediaBandInfo(
    val extract: String,
    val sourceUrl: String,
)

interface BandInfoService {
    suspend fun informationForArtist(artistId: String): WikipediaBandInfo?
}

interface WikidataArticleApi {
    @GET("sparql")
    suspend fun findEnglishWikipediaArticle(
        @Query("query") query: String,
        @Query("format") format: String = "json",
    ): WikidataArticleResponseDto
}

interface WikipediaSummaryApi {
    @GET
    suspend fun getSummary(@Url url: String): WikipediaSummaryDto
}

@JsonClass(generateAdapter = false)
data class WikidataArticleResponseDto(
    val results: WikidataArticleResultsDto = WikidataArticleResultsDto(),
)

@JsonClass(generateAdapter = false)
data class WikidataArticleResultsDto(
    val bindings: List<WikidataArticleBindingDto> = emptyList(),
)

@JsonClass(generateAdapter = false)
data class WikidataArticleBindingDto(val article: SparqlValueDto)

@JsonClass(generateAdapter = false)
data class WikipediaSummaryDto(
    val extract: String? = null,
    @param:Json(name = "content_urls") val contentUrls: WikipediaContentUrlsDto? = null,
)

@JsonClass(generateAdapter = false)
data class WikipediaContentUrlsDto(val desktop: WikipediaPageUrlDto? = null)

@JsonClass(generateAdapter = false)
data class WikipediaPageUrlDto(val page: String? = null)

class WikipediaBandInfoService(
    private val wikidataApi: WikidataArticleApi,
    private val wikipediaApi: WikipediaSummaryApi,
) : BandInfoService {
    private val cache = ConcurrentHashMap<String, WikipediaBandInfo>()
    private val knownMissing = ConcurrentHashMap.newKeySet<String>()

    override suspend fun informationForArtist(artistId: String): WikipediaBandInfo? {
        cache[artistId]?.let { return it }
        if (artistId in knownMissing || !MBID_REGEX.matches(artistId)) return null
        val articleUrl = wikidataApi.findEnglishWikipediaArticle(buildQuery(artistId))
            .results.bindings.firstOrNull()?.article?.value
        if (articleUrl == null) {
            knownMissing += artistId
            return null
        }
        val summaryUrl = summaryEndpoint(articleUrl) ?: run {
            knownMissing += artistId
            return null
        }
        val response = wikipediaApi.getSummary(summaryUrl)
        val extract = response.extract?.trim()?.takeIf(String::isNotEmpty) ?: run {
            knownMissing += artistId
            return null
        }
        return WikipediaBandInfo(
            extract = extract,
            sourceUrl = response.contentUrls?.desktop?.page ?: articleUrl,
        ).also { cache[artistId] = it }
    }

    private fun buildQuery(artistId: String): String = """
        SELECT ?article WHERE {
          ?item wdt:P434 "$artistId".
          ?article schema:about ?item;
                   schema:isPartOf <https://en.wikipedia.org/>.
        }
        LIMIT 1
    """.trimIndent()

    private fun summaryEndpoint(articleUrl: String): String? = runCatching {
        val uri = URI(articleUrl)
        val title = uri.rawPath.substringAfter("/wiki/", "").takeIf(String::isNotBlank)
            ?: return@runCatching null
        "https://${uri.host}/api/rest_v1/page/summary/$title"
    }.getOrNull()

    private companion object {
        val MBID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }
}
