# DEEP-ECHO Android v0.1.0 Alpha

Baseline: DEEP-ECHO Windows v2.6.2 Final Release Source. The Windows project is unchanged.

## Included
- Native Kotlin + Jetpack Compose Android shell.
- Media3 playback service and MediaSession foundation.
- Home, Search, Lyrics, Downloads and Settings screens.
- Sticky Mini Player with seek and playback controls.
- 13 DeepEcho theme identities with animated mobile backgrounds.
- Local PC bridge for live search, stream resolution and YouTube caption lyrics during emulator testing.
- Search-result stream pre-warming to reduce tap-to-audio delay.
- Synced line lyrics and draggable floating lyrics overlay.
- Android DownloadManager integration with persistent download records and completed local URI playback.
- Demo direct-stream tracks so the player can be exercised even without the bridge.

## Alpha limitations
- Mainstream catalog resolution currently uses the included PC bridge; standalone on-device parity is a later milestone.
- Word-by-word and Exact Lyrics rescue are not ported yet.
- Smart Autoplay settings exist, but related-track queue generation is not yet wired.
- Artwork still uses themed placeholders.
- Full desktop Home shelves/playlists and complete offline-library reconciliation are not yet ported.

## First emulator test
Run `bridge\Start-Bridge.bat`, then open/run the project in Android Studio on an emulator. The emulator bridge URL defaults to `http://10.0.2.2:17832`.
