# DeepEcho Android v1.12.12 — Large Artwork + Fast Seek Test Report

## Packaging/static checks

- Version: `1.12.12`, versionCode `41` — PASS.
- Root build entry: `BUILD-TEST-APK-v1.12.12.bat` — PASS.
- Pure Kotlin `PlaybackSeekPolicy` compile/runtime assertions — PASS.
- Modified Kotlin parser smoke: no syntax/parser diagnostics — PASS.
- Android XML parse — PASS.
- Focused source assertions for large Artwork layout and seek priority — PASS.
- Bad Compose `layout.weight` import regression scan — PASS.
- No keystore, `local.properties`, build log or APK bundled — PASS.

## Playback protection

- v1.12.11 320 ms user-action startup/seek threshold is preserved.
- YouTube stream selection / weak-network quality logic is unchanged.
- Downloads and SmartCache implementations are unchanged.
- Timeline seek now yields unresolved speculative network work briefly and restores lookahead automatically.
- Media3 seeks use closest sync-point policy and retain a bounded 20-second recent back buffer.

## Player UI protection

- Artwork mode uses a large width-responsive square cover up to 392dp.
- Artwork mode becomes vertically scrollable on short devices rather than shrinking the cover into metadata.
- Lyrics / Minimal / Ambient player mode structure remains unchanged.
- Shared artwork quality loader and theme engine are unchanged.

## Protected files byte-for-byte unchanged from v1.12.11

- `YouTubeApi.kt`
- `Downloads.kt`
- `SmartCache.kt`
- `LyricsOrchestrator.kt`
- `FloatingLyricsService.kt`
- `Theme.kt`
- `Store.kt`
- `TasteEngine.kt`
- `Common.kt` shared artwork loader
- `Screens.kt` Premium Home/Search surfaces
- `PlayerExperienceV1128.kt` player-mode definitions

## Real Android build note

This container does not contain the established Windows Android SDK 35 + permanent DeepEcho signing environment, so a successful Android Gradle APK build is not claimed here. Run the root BAT on the established Windows build machine; it runs unit tests and `assembleDebug` before copying the test APK.
