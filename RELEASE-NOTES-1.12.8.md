# DEEP-ECHO Mobile v1.12.8

## Visible UX + Player Experience Upgrade

DeepEcho Android v1.12.8 builds on the v1.12.7 Instant Start + Rapid Skip performance baseline and adds a large set of visible, Android-focused player and discovery improvements without replacing the existing playback or lyrics architecture.

### Highlights

- Improved lock-screen / MediaSession presentation using the existing playback service.
- Four Android Quick Settings tiles: Play/Pause, Open DeepEcho, Floating Lyrics, Smart Autoplay.
- Home Mood Selector as a temporary additive signal to the existing taste/recommendation system.
- Personalized Daily Mixes / Your Mixes generated from existing local taste/history signals.
- Five visual modes inside the same player session: Artwork, Lyrics, Visualizer, Ambient and Minimal.
- Optional player swipe/double-tap/long-press gestures designed not to interfere with seek, lyrics scroll or Android edge navigation.
- Theme-aware song Share Cards with native Android share-sheet handoff.
- Expanded per-song Quick Actions with More Like This / Less Like This signals.
- Cached artwork-based secondary color accents while preserving the selected DeepEcho theme and readable contrast.
- Optional synced lyric line in the mini player using the existing lyrics state.
- Local Listening Session insight cards.
- Optional current lyric line in expanded media notifications at line-level update frequency.
- Continue Your Vibe to resume a compatible recommendation context from a recent session without blindly restoring the old queue.

### Performance and regression protection

- v1.12.7 Instant Start + Rapid Skip remains the playback-performance baseline.
- Playback actions remain higher priority than Home decoration, palette extraction, sharing and recommendation refreshes.
- No second playback service, MediaSession, queue, Smart Autoplay engine or lyrics engine is introduced.
- Downloads and local tracks keep their existing direct playback paths.
- New lyric surfaces consume the existing resolved/synchronized lyrics state rather than starting their own provider/OCR work.

### Build verification

The golden-baseline v1.12.8 Windows debug build completed successfully through `:app:assembleDebug` with unit tests completed/up-to-date. The one-click release runner repeats tests and builds/verifies the signed release APK before tag creation.

### Android package

- Package: `com.deepecho.mobile`
- Version name: `1.12.8`
- Version code: `37`
