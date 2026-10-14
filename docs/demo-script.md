# OtoNavi Japan demo script

## Preparation

- Use a networked device with either the secure proxy configured or a debug-only Gemini key in ignored `local.properties`.
- Confirm one TheAudioDB/MusicBrainz request and one Gemini request before presenting.
- Keep a known group such as “BAND-MAID” ready; public API data can change.

## Demo

1. Launch the app and point out the concert visual system, title, and three bottom destinations.
2. Scroll the Discover `RecyclerView` and filter by genre.
3. Search for a Japanese band.
4. Open Band Details and show the band image, area/date/status, genres, and verified MusicBrainz members/roles.
5. Open an album and show Cover Art Archive artwork or its designed placeholder, MusicBrainz attribution, and Back button.
6. Save the band as a favorite.
7. Generate a grounded band or origins summary and explain that Gemini receives only verified metadata/source text.
8. Open Favorites, remove the band, demonstrate Snackbar Undo, and restore it.
9. Restart the app and show Room persistence.
10. Open AI Chat, type a natural-language lookup or recommendation in the single composer, then send.
11. Show a verified entity/recommendation card and use a contextual action such as Members, Albums, or Recommend song. Open the validated band/album result.
12. Demonstrate airplane mode, retry, missing artwork, or missing Gemini configuration.

Explain that MusicBrainz/TheAudioDB supply names, IDs, relationships, roles, albums, and songs. Gemini only interprets, ranks, and explains; unknown IDs and mismatched entity types cannot reach the UI.

## Missing Gemini configuration

AI Chat opens and displays its setup message. Discover, details, albums, members, Favorites, and offline persistence continue working. No fake recommendation is substituted.
