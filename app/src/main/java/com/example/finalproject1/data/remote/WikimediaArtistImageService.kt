package com.example.finalproject1.data.remote

import com.squareup.moshi.JsonClass
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import retrofit2.http.GET
import retrofit2.http.Query

data class ArtistImage(
    val imageUrl: String,
    val sourceUrl: String,
)

interface ArtistImageService {
    suspend fun imagesForArtistIds(
        artistIds: List<String>,
        forceRefresh: Boolean = false,
    ): Map<String, ArtistImage>
}

object NoopArtistImageService : ArtistImageService {
    override suspend fun imagesForArtistIds(
        artistIds: List<String>,
        forceRefresh: Boolean,
    ): Map<String, ArtistImage> = emptyMap()
}

interface WikidataSparqlApi {
    @GET("sparql")
    suspend fun findArtistImages(
        @Query("query") query: String,
        @Query("format") format: String = "json",
    ): WikidataSparqlResponseDto
}

interface WikimediaCommonsApi {
    @GET("api.php")
    suspend fun resolveImages(
        @Query("action") action: String = "query",
        @Query("prop") property: String = "imageinfo",
        @Query("iiprop") imageInfoProperties: String = "url",
        @Query("iiurlwidth") thumbnailWidth: Int = 900,
        @Query("titles") titles: String,
        @Query("format") format: String = "json",
        @Query("formatversion") formatVersion: Int = 2,
    ): CommonsQueryResponseDto
}

@JsonClass(generateAdapter = false)
data class WikidataSparqlResponseDto(
    val results: WikidataResultsDto = WikidataResultsDto(),
)

@JsonClass(generateAdapter = false)
data class WikidataResultsDto(
    val bindings: List<WikidataImageBindingDto> = emptyList(),
)

@JsonClass(generateAdapter = false)
data class WikidataImageBindingDto(
    val mbid: SparqlValueDto,
    val image: SparqlValueDto,
)

@JsonClass(generateAdapter = false)
data class SparqlValueDto(val value: String)

@JsonClass(generateAdapter = false)
data class CommonsQueryResponseDto(
    val query: CommonsQueryDto? = null,
)

@JsonClass(generateAdapter = false)
data class CommonsQueryDto(
    val pages: List<CommonsPageDto> = emptyList(),
)

@JsonClass(generateAdapter = false)
data class CommonsPageDto(
    val title: String,
    val imageinfo: List<CommonsImageInfoDto>? = null,
)

@JsonClass(generateAdapter = false)
data class CommonsImageInfoDto(
    val url: String,
    val thumburl: String? = null,
    val descriptionurl: String? = null,
)

class WikimediaArtistImageService(
    private val api: WikidataSparqlApi,
    private val commonsApi: WikimediaCommonsApi,
    private val imageProperty: WikimediaImageProperty = WikimediaImageProperty.PHOTO,
) : ArtistImageService {
    private val imageCache = ConcurrentHashMap<String, ArtistImage>()
    private val knownMissing = ConcurrentHashMap.newKeySet<String>()

    override suspend fun imagesForArtistIds(
        artistIds: List<String>,
        forceRefresh: Boolean,
    ): Map<String, ArtistImage> {
        val validIds = artistIds.distinct().filter(MBID_REGEX::matches)
        if (validIds.isEmpty()) return emptyMap()
        val unresolved = if (forceRefresh) validIds else validIds.filterNot {
            imageCache.containsKey(it) || knownMissing.contains(it)
        }
        if (unresolved.isNotEmpty()) {
            val rows = api.findArtistImages(buildQuery(unresolved)).results.bindings
            val filesByArtist = rows.mapNotNull { row ->
                fileName(row.image.value)?.let { row.mbid.value to it }
            }.toMap()
            val pagesByFile = if (filesByArtist.isEmpty()) emptyMap() else {
                commonsApi.resolveImages(
                    titles = filesByArtist.values.distinct().joinToString("|") { "File:$it" }
                ).query?.pages.orEmpty().associateBy { normalizeFileName(it.title.removePrefix("File:")) }
            }
            val found = filesByArtist.mapNotNull { (artistId, fileName) ->
                val info = pagesByFile[normalizeFileName(fileName)]?.imageinfo?.firstOrNull()
                    ?: return@mapNotNull null
                artistId to ArtistImage(
                    imageUrl = info.thumburl ?: info.url,
                    sourceUrl = info.descriptionurl
                        ?: "https://commons.wikimedia.org/wiki/File:$fileName",
                )
            }.toMap()
            imageCache.putAll(found)
            knownMissing.addAll(unresolved - found.keys)
        }
        return validIds.mapNotNull { id -> imageCache[id]?.let { id to it } }.toMap()
    }

    private fun buildQuery(ids: List<String>): String = """
        SELECT ?mbid (SAMPLE(?image) AS ?image) WHERE {
          VALUES ?mbid { ${ids.joinToString(" ") { "\"$it\"" }} }
          ?item wdt:P434 ?mbid;
                wdt:${imageProperty.code} ?image.
        }
        GROUP BY ?mbid
    """.trimIndent()

    private fun fileName(rawUrl: String): String? = runCatching {
            URI(rawUrl).path.substringAfter("/wiki/Special:FilePath/", "")
        }.getOrNull()?.takeIf(String::isNotBlank)

    private fun normalizeFileName(value: String): String = value.replace('_', ' ').lowercase()

    private companion object {
        val MBID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }
}

enum class WikimediaImageProperty(val code: String) {
    LOGO("P154"),
    PHOTO("P18"),
}
