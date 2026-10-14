# OtoNavi Japan presentation outline

## 1. Project title

OtoNavi Japan — AI-Powered Japanese Band Discovery

## 2. Project description

- Kotlin/XML Android application for discovering Japanese music.
- TheAudioDB and MusicBrainz provide verified artists, members, albums, songs, aliases, IDs, and relationships.
- Room keeps favorites available after restart.
- Gemini interprets chat requests, ranks supplied candidates, and explains supplied metadata; it is not the factual database.

## 3. Issues and motivation

- Large catalogs are difficult for new listeners to navigate.
- Song/album names and romanized/Japanese aliases are ambiguous.
- Generative systems can hallucinate plausible music facts.
- Network-only apps lose usefulness during poor connectivity.
- OtoNavi uses verified IDs, clarification, transparent limitations, and durable favorites.

## 4. Screen design

- Discover: search, genres, pull-to-refresh, logo cards, favorite state, and complete UI states.
- Band Details: band image, verified member roles, albums, favorite action, and grounded AI summaries.
- Album Details: Cover Art Archive image or placeholder, MusicBrainz metadata, attribution, and Back navigation.
- AI Chat: RecyclerView conversation, streamlined fixed composer, fast local intent detection, verified entity cards, and contextual follow-ups.
- Favorites: offline saved list, remove/undo, and empty-state route to Discover.
- Visual system: illustrated winter concert background, dark navy scrim, vermilion/cyan accents, rounded cards, Zen Tokyo Zoo headings, and Rampart One Japanese names.

## 5. Architecture

```text
Fragment → ViewModel → Repository / EntityResolver → MusicBrainz + TheAudioDB
                         ↘ Room
AI Chat ViewModel → AiMusicAssistantService → secure proxy or debug Gemini REST
```

- Single Activity, Fragments, Navigation Component, and Safe Args.
- StateFlow plus `repeatOnLifecycle`.
- Coroutines for remote/database work.
- Manual AppContainer dependency injection.
- One global 1.1-second MusicBrainz limiter.
- Moshi for JSON and Coil for images.
- No Firebase; release AI access uses a backend proxy.

## 6. Required-feature mapping

| Requirement | Implementation |
|---|---|
| Multiple screens | Discover, AI Chat, Favorites, Band Details, Album Details |
| Scrollable lists | RecyclerViews for discovery, conversation, albums, members, and favorites |
| Architecture | ViewModel, StateFlow, Lifecycle, Room, repository/resolver layers |
| Navigation | Bottom navigation, nested details, Safe Args, Back behavior |
| Internet/async | Retrofit, OkHttp, coroutines, serialized MusicBrainz access |
| Persistence | Room `favorite_bands` and observable favorite state |
| AI | Gemini structured parsing/ranking/explanation over verified music data |
| Polished UI | Material components, readable background scrim, placeholders, accessible controls |

## 7. Live demonstration

1. Launch, browse, filter, and search.
2. Open a band, inspect verified members, and open an album.
3. Save a favorite and prove restart persistence.
4. Ask AI Chat a natural-language question.
5. Choose a verified result and run a contextual Members/Albums/Songs action.
6. Explain local context and candidate-ID validation.
7. Demonstrate an offline, missing-art, or missing-Gemini state.
