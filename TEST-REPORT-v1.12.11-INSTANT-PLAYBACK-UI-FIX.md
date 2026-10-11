# DeepEcho Android v1.12.11 — Instant Playback + UI Fix Test Report

## Packaging-environment checks

- Version check: `1.12.11`, versionCode `40` — PASS.
- Pure Kotlin compile: `ArtworkQuality.kt` + `PlaybackLookaheadPolicy.kt` — PASS.
- Modified Kotlin syntax smoke (no parser/syntax diagnostics): PASS.
- Android XML parse: PASS.
- Focused playback/UI source assertions: PASS.
- Bad Compose `layout.weight` import regression scan: PASS.
- Root build entry point: `BUILD-TEST-APK-v1.12.11.bat` — PASS.
- No keystore, local.properties, or local build log included in package.

## Protected files verified byte-for-byte unchanged from v1.12.10

- Downloads.kt
- LyricsOrchestrator.kt
- FloatingLyricsService.kt
- SmartCache.kt
- Theme.kt
- Store.kt
- TasteEngine.kt
- Common.kt shared artwork renderer
- ArtworkQuality.kt

## Playback changes verified

- Foreground user Play/Next/Previous resolution has priority over speculative lookahead.
- Smart Autoplay recommendation work is deferred while the selected song gets its startup head start.
- Previous avoids an unconditional `prepare()` and only prepares from IDLE.
- Startup buffer threshold reduced from 700 ms to 320 ms; rebuffer threshold remains larger at 1000 ms.
- Weak Android-reported links may temporarily choose a lighter audio stream for faster first audio.
- Nearest-next CDN warm probe is 8 KiB on metered connections and 32 KiB on unmetered; Data Saver disables it.

## UI changes verified

- Home Smart Autoplay pill removed; Settings control preserved.
- Home quote is full-width and can use up to three lines.
- Visible player modes: Artwork / Lyrics / Minimal / Ambient.
- Visualizer remains hidden as a visible player tab.
- Normal player has no standalone Ambient switch.
- Fullscreen Ambient keeps its own switch.
- Artwork stage is clipped and square-sized to the smaller of available width/height to prevent metadata overlap.

## Real Android build note

This environment does not contain the Windows Android SDK 35 + permanent DeepEcho signing setup, so a successful Android Gradle APK build is not claimed here. Run the root BAT on the established Windows build machine; it runs the unit tests and `assembleDebug` and only copies the test APK on Gradle success.
