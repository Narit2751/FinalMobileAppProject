# OtoNavi Japan — AI-Powered Japanese Band Discovery

OtoNavi Japan is an XML-based Android final project for discovering Japanese bands, members, albums, and songs. Music facts come from TheAudioDB and MusicBrainz; Gemini only interprets questions, ranks verified candidates, and explains metadata supplied by the app.

This project does **not** use Firebase. It has no Firebase dependency, Google Services Gradle plugin, App Check, authentication, or `google-services.json` requirement.

## Presentation and assessment overview

### 1. Project title

**OtoNavi Japan - AI-Powered Japanese Band Discovery**

### 2. Project description

OtoNavi Japan is a native Android application that helps listeners discover and understand Japanese music. Users can browse Japanese bands, search by name or genre, inspect verified band members and albums, save favorites, and ask an AI music assistant for band or song recommendations based on a chosen vibe. MusicBrainz and TheAudioDB provide the factual music data, while Gemini is limited to understanding requests, ranking verified candidates, and explaining supplied metadata.

The application is written in Kotlin with XML layouts and follows a single-Activity, multi-Fragment MVVM architecture. It uses RecyclerView for scrollable content, Navigation Component and Safe Args for navigation, StateFlow for UI state, Retrofit and coroutines for asynchronous API access, and Room for offline favorite persistence.

### 3. Issues and motivation

Japanese music is available across many services, but new listeners may not know which band, album, or song matches the sound they want. Search results can also be confusing because Japanese and romanized names may differ, several releases can share a title, and public music metadata is sometimes incomplete.

OtoNavi Japan was created to address these problems by:

- Providing one focused place to discover Japanese bands and their releases.
- Supporting natural requests such as "Recommend a Japanese song with an energetic vibe."
- Grounding all displayed artists, albums, songs, IDs, and member relationships in external music APIs instead of allowing the AI to invent facts.
- Preserving useful favorites locally so the saved collection remains available after the application restarts and while offline.
- Handling missing artwork, missing metadata, network errors, ambiguous results, and unavailable AI configuration without crashing.

### 4. Screen design

The interface follows a Japanese winter-concert visual direction. A dark navy readability layer separates content from the illustrated background, while vermilion and cyan accents identify actions and selected controls. Rounded Material cards, 48dp-or-larger touch targets, clear entity labels, designed artwork placeholders, Zen Tokyo Zoo headings, and Rampart One Japanese text provide a consistent visual hierarchy.

| Screen | Main UI and purpose |
|---|---|
| **Discover** | Search field, genre chips, pull-to-refresh, and a scrollable list of band-logo cards. Each card shows a verified band name, area, genres, and favorite state. |
| **Band Details** | Band image, area and activity information, genres, verified member cards and roles, albums, favorite control, and opaque AI summary actions. |
| **Album Details** | Cover Art Archive image or designed placeholder, album title, release type/date, MusicBrainz attribution, and Back navigation. |
| **AI Chat** | RecyclerView conversation, Band/Song recommendation categories, selectable vibe keywords, editable message composer, typing/error states, and verified result cards. |
| **Favorites** | Offline Room-backed list of saved bands with open, remove, Undo, and empty-state actions. |

Navigation design:

```text
MainActivity
|-- Discover --------> Band Details --------> Album Details
|-- AI Chat ---------> Band Details / Album Details
`-- Favorites -------> Band Details --------> Album Details
```

Only Discover, AI Chat, and Favorites appear in bottom navigation. Detail screens are secondary destinations and use normal Back behavior.

### 5. Live demonstration plan

1. Launch the application and show the three bottom-navigation destinations.
2. Scroll through Discover, select a genre, and search for a Japanese band.
3. Open Band Details and show its verified members, roles, metadata, and albums.
4. Open an album, demonstrate artwork or its fallback, and return with the Back button.
5. Save the band, open Favorites, restart the app, and demonstrate Room persistence.
6. Open AI Chat, select **Band** or **Song**, add one or more vibe keywords, edit the generated request, and send it.
7. Open a verified recommendation and explain that unknown Gemini IDs are rejected.
8. If practical, demonstrate a missing-artwork, offline, retry, or missing-Gemini-configuration state.

### Must-have feature mapping

| Course requirement | Implementation in OtoNavi Japan |
|---|---|
| **Multiple screens** | Discover, AI Chat, Favorites, Band Details, and Album Details are implemented as Fragments hosted by one Activity. |
| **Data persistence** | Room stores favorite bands and observes changes with Flow, allowing favorites to remain available after restart. |
| **External connectivity** | Retrofit, OkHttp, and Kotlin coroutines call MusicBrainz, TheAudioDB, Cover Art Archive, Wikimedia, and the configured Gemini endpoint asynchronously. |
| **AI-integrated functions** | Gemini parses selected requests, ranks only API-verified candidates, and creates grounded explanations. Invalid or invented IDs cannot become displayed results. |

Additional Android requirements are demonstrated through RecyclerView lists, View Binding, Navigation Component, Safe Args, ViewModel, StateFlow, `repeatOnLifecycle`, repository layers, and a shared MusicBrainz request-rate limiter.

## Features

- Discover bands with debounced search, genre filters, pull-to-refresh, loading/empty/error states, and a scrollable `RecyclerView`.
- Open band details with a band image, verified MusicBrainz members and roles, albums, favorite control, and grounded Gemini summaries.
- Open album details with Cover Art Archive artwork, a designed missing-art placeholder, attribution, and working Back navigation.
- Save favorites in Room for offline use after an app restart.
- Use AI Chat to enter a band, member, album, or song name, or ask a natural-language Japanese-music question.
- Build an editable recommendation request with Band/Song category controls and Energetic, Relaxing, Emotional, Dark, Happy, or Focused vibe keywords. Gemini ranks only verified candidates using their supplied metadata.
- Common lookups and recommendation requests are interpreted locally first, avoiding an extra Gemini round trip; ambiguous language still uses Gemini classification.
- Resolve entities through MusicBrainz, clarify ambiguous matches, maintain local conversation context, and navigate verified band/album cards to existing details.
- Validate Gemini IDs and entity types against the supplied candidate map; duplicate, unknown, invented, and mismatched results are discarded.
- Preserve the winter-concert artwork, dark readability scrim, Zen Tokyo Zoo display typography, and Rampart One Japanese-name typography.

The app never claims that API results are popular or trending, never treats album artwork as a band photo, and never uses Gemini as a factual music database.

## Architecture

```text
Fragment + RecyclerView
        ↓ StateFlow / repeatOnLifecycle
     ViewModel
        ↓
 Repository / MusicEntityResolver ── AiMusicAssistantService
       ↙          ↘                         ↓
    Room    MusicBrainz + TheAudioDB   Proxy or debug Gemini REST
```

`OtoNaviApplication` and `AppContainer` provide manual dependency injection. All MusicBrainz requests share one coroutine rate limiter and are serialized at approximately 1.1 seconds apart. Room work is not rate-limited. Retrofit uses Moshi consistently, and Coil is the only image-loading library.

AI access is isolated behind `AiMusicAssistantService`. `GeminiMusicAssistantService` is the real implementation; `FakeAiMusicAssistantService` exists under tests only. Conversation context and numbered-result resolution remain local in `AiFinderViewModel`.

## Data sources and attribution

- [TheAudioDB](https://www.theaudiodb.com/free_music_api) supplies existing artist discovery and available artwork/metadata.
- [MusicBrainz](https://musicbrainz.org/doc/MusicBrainz_API) supplies stable entity IDs, aliases, member relationships, release groups, and recordings.
- [Cover Art Archive](https://coverartarchive.org/) supplies release-group cover artwork.
- Wikimedia services provide verified image/text fallbacks already used by band details.
- [Gemini Developer API](https://ai.google.dev/gemini-api/docs) supplies structured language interpretation, candidate ranking, and grounded explanations only.

MusicBrainz requests use a descriptive User-Agent. Community-maintained fields and artwork may be incomplete.

## Build and run

Requirements: Android Studio compatible with AGP 9.3.3, JDK 17, Android SDK 37, and an API 25+ emulator/device.

1. Open the project root in Android Studio.
2. Let Gradle sync finish.
3. Select the `app` run configuration and a Medium Phone emulator (or physical device).
4. Press Run.

The app compiles and its non-AI features run without Gemini configuration.

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat compileDebugAndroidTestKotlin
.\gradlew.bat lintDebug
```

## Gemini configuration

Create a current Gemini Developer API authorization key in [Google AI Studio](https://aistudio.google.com/app/apikey). Two runtime modes are supported.

### Production backend proxy

Do not ship a Gemini key in an APK. Set `AI_PROXY_BASE_URL` in the build environment or ignored `local.properties`. The app expects HTTPS endpoints `POST ai/parse`, `POST ai/rank`, and `POST ai/explain`.

The backend must read `GEMINI_API_KEY` from an environment secret, restrict request sizes/rates, apply timeouts, validate Gemini JSON, and return only the required structured result. It must never return the key to Android. Keep the URL in `AI_PROXY_BASE_URL`; do not duplicate it in Kotlin.

### Direct debug/classroom mode

For a local classroom demo only, add the property named `GEMINI_API_KEY` with your own key as its value in root `local.properties`, or provide the same environment variable before building. This README intentionally contains no key or key-shaped placeholder.

Gradle exposes this value only to debug. Release builds receive no direct key. `BuildConfig` does not make a key secure: a key embedded in any APK can be extracted. Do not distribute that debug APK.

Set `GEMINI_MODEL` in `local.properties` or the environment to select a current fast text model. The checked default is `gemini-3.5-flash-lite`; change it if Google retires the model. The app uses one Retrofit REST client and structured output—no deprecated Android Gemini SDK is included.

When neither proxy nor debug key exists, AI Chat opens normally and shows a setup message. Discover, Details, Albums, and Favorites remain usable. To test it, remove only `GEMINI_API_KEY` and `AI_PROXY_BASE_URL` from ignored local configuration, rebuild debug, and open AI Chat.

If a key is exposed, revoke it in Google AI Studio/Google Cloud, create a replacement, update the local/server secret, and rebuild or redeploy. Never commit `local.properties`, credentials, screenshots containing credentials, or generated build artifacts.

## Gemini safety and privacy

The app sends the current chat message, compact structured context, selected preferences, recent verified IDs, and a small candidate list. It does not send device identifiers, credentials, raw logs, favorite notes, the entire database, or unlimited history.

Every response is size-limited, parsed as JSON, checked for required fields and known enums, and mapped to verified local objects. Ranking is deduplicated and limited to three. Direct debug calls are bounded to 25 seconds and use small structured outputs. Authentication failures, quota responses, timeouts, malformed JSON, offline state, and missing configuration become friendly messages without raw bodies or keys.

## Offline behavior

Available offline after saving: Favorites, stored band fields, favorite add/remove/undo, navigation, themes, and bundled placeholders.

Requires internet: Discover refresh, unsaved details, member/album/song resolution, remote images, Cover Art Archive, and Gemini chat/summaries.

## Known API limitations

- TheAudioDB free search can be narrow and incomplete.
- MusicBrainz aliases, relationships, roles, countries, dates, recordings, and release groups are community-maintained and may be missing.
- Song titles frequently need an artist for disambiguation.
- Cover Art Archive can return 404; the app keeps a designed placeholder.
- A release-group album view does not promise a track list because an exact release must be resolved first.
- Chat survives rotation and detail navigation through its ViewModel; full process-restart persistence is optional.

## Tests

Tests use fakes and never contact live APIs. They cover mapping, query construction, rate limiting, favorites, local request parsing, missing Gemini configuration, ranking validation, unknown/duplicate IDs, result limits, and conversation reset. Room instrumentation tests require an emulator.

See [docs/presentation-outline.md](docs/presentation-outline.md) and [docs/demo-script.md](docs/demo-script.md).
#   F i n a l M o b i l e A p p P r o j e c t  
 