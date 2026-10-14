package com.example.finalproject1

import android.content.Context
import com.example.finalproject1.data.local.OtoNaviDatabase
import com.example.finalproject1.data.remote.AiMusicAssistantService
import com.example.finalproject1.data.remote.AiProxyApi
import com.example.finalproject1.data.remote.GeminiApi
import com.example.finalproject1.data.remote.GeminiMusicAssistantService
import com.example.finalproject1.data.remote.MusicBrainzApi
import com.example.finalproject1.data.remote.MusicBrainzRateLimiter
import com.example.finalproject1.data.remote.TheAudioDbApi
import com.example.finalproject1.data.remote.TheAudioDbRateLimiter
import com.example.finalproject1.data.remote.WikidataSparqlApi
import com.example.finalproject1.data.remote.WikimediaArtistImageService
import com.example.finalproject1.data.remote.WikimediaCommonsApi
import com.example.finalproject1.data.remote.WikimediaImageProperty
import com.example.finalproject1.data.remote.WikidataArticleApi
import com.example.finalproject1.data.remote.WikipediaBandInfoService
import com.example.finalproject1.data.remote.WikipediaSummaryApi
import com.example.finalproject1.data.repository.MusicRepository
import com.example.finalproject1.data.repository.MusicEntityResolver
import com.example.finalproject1.data.repository.MusicBrainzEntityResolver
import com.example.finalproject1.data.repository.TheAudioDbMusicRepository
import com.example.finalproject1.util.NetworkStatus
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val database = OtoNaviDatabase.create(context)
    val networkStatus = NetworkStatus(context)

    private val metadataClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "OtoNaviJapan/1.0 (university project; contact: replace-with-email@example.com)",
                    )
                    .header("Accept", "application/json")
                    .build()
            )
        }
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
        }
        .build()

    private val audioDbClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "OtoNaviJapan/1.0 university-project")
                    .header("Accept", "application/json")
                    .build()
            )
        }
        .build()

    private val audioDbApi = Retrofit.Builder()
        .baseUrl("https://www.theaudiodb.com/")
        .client(audioDbClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(TheAudioDbApi::class.java)

    private val musicBrainzApi = Retrofit.Builder()
        .baseUrl("https://musicbrainz.org/ws/2/")
        .client(metadataClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(MusicBrainzApi::class.java)
    private val musicBrainzRateLimiter = MusicBrainzRateLimiter()

    private val wikidataApi = Retrofit.Builder()
        .baseUrl("https://query.wikidata.org/")
        .client(metadataClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(WikidataSparqlApi::class.java)

    private val commonsApi = Retrofit.Builder()
        .baseUrl("https://commons.wikimedia.org/w/")
        .client(metadataClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(WikimediaCommonsApi::class.java)

    private val wikidataArticleApi = Retrofit.Builder()
        .baseUrl("https://query.wikidata.org/")
        .client(metadataClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(WikidataArticleApi::class.java)

    private val wikipediaSummaryApi = Retrofit.Builder()
        .baseUrl("https://en.wikipedia.org/")
        .client(metadataClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(WikipediaSummaryApi::class.java)

    val bandInfoService = WikipediaBandInfoService(wikidataArticleApi, wikipediaSummaryApi)

    private val geminiApi = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .callTimeout(25, TimeUnit.SECONDS)
                .apply {
                    if (BuildConfig.DEBUG) {
                        // BASIC logs the endpoint/status without logging the API-key header.
                        addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                    }
                }
                .build()
        )
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GeminiApi::class.java)

    val musicRepository: MusicRepository = TheAudioDbMusicRepository(
        api = audioDbApi,
        apiKey = BuildConfig.THEAUDIODB_API_KEY,
        favoriteDao = database.favoriteBandDao(),
        rateLimiter = TheAudioDbRateLimiter(),
        logoFallbackService = WikimediaArtistImageService(
            wikidataApi,
            commonsApi,
            WikimediaImageProperty.LOGO,
        ),
        musicBrainzApi = musicBrainzApi,
        musicBrainzRateLimiter = musicBrainzRateLimiter,
        memberPhotoService = WikimediaArtistImageService(
            wikidataApi,
            commonsApi,
            WikimediaImageProperty.PHOTO,
        ),
    )

    val entityResolver: MusicEntityResolver = MusicBrainzEntityResolver(
        api = musicBrainzApi,
        rateLimiter = musicBrainzRateLimiter,
        repository = musicRepository,
    )

    private val aiProxyApi: AiProxyApi? = BuildConfig.AI_PROXY_BASE_URL
        .trim()
        .takeIf(String::isNotEmpty)
        ?.let { baseUrl -> runCatching {
            Retrofit.Builder()
                .baseUrl(if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/")
                .client(
                    OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .callTimeout(30, TimeUnit.SECONDS)
                        .build()
                )
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(AiProxyApi::class.java)
        }.getOrNull() }

    val aiService: AiMusicAssistantService = GeminiMusicAssistantService(
        model = BuildConfig.GEMINI_MODEL,
        moshi = moshi,
        directApi = if (BuildConfig.DEBUG) geminiApi else null,
        directDebugApiKey = if (BuildConfig.DEBUG) BuildConfig.GEMINI_API_KEY else "",
        proxyApi = aiProxyApi,
    )
}
