# DeepEcho Android v1.12.10 — Artwork + Player Cleanup Test Report

## Completed checks in packaging environment

- 23 focused static/regression assertions: PASS.
- `ArtworkQuality.kt` standalone Kotlin compile: PASS.
- High-resolution YouTube candidate/fallback behavior: PASS.
- Google image CDN size promotion behavior: PASS.
- Data Saver no-promotion behavior: PASS.
- Android XML parse check: PASS.
- Version: `1.12.10`, versionCode `39`.
- Root build entry point: `BUILD-TEST-APK-v1.12.10.bat`.

## Protected baseline files verified byte-for-byte unchanged from v1.12.9

- PlaybackService.kt
- PlayerClient.kt
- Downloads.kt
- YouTubeApi.kt
- LyricsOrchestrator.kt
- FloatingLyricsService.kt
- SmartCache.kt
- PlaybackLookahead.kt
- Theme.kt

## Functional intent

The Full Player selector exposes Artwork, Lyrics and Minimal only. The old Visualizer presentation remains internal/unreachable from the mode row so existing audio-reactive dependencies are not broken. Ambient is entered only from the existing Ambient switch.

All normal in-app artwork surfaces that use the shared `Art()` component now use high-resolution CDN candidates where available and surface-aware decode sizing with graceful fallback. This includes Home, Search, Full Player, mini-player, playlist/detail cards, song rows, queue and artist UI.

## Build limitation here

This packaging environment does not have the Android SDK 35 setup used by the Windows DeepEcho builder, so a real Android Gradle APK compile was not claimed here. Run the root BAT on the established Windows setup; it runs the project unit tests and `assembleDebug` and produces the test APK only on success.
