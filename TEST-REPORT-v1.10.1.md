# DEEP-ECHO Mobile v1.10.1 FEATURE TEST REPORT

## Implemented
- Dedicated Floating Lyrics ON/OFF switch in the Lyrics screen.
- Responsive Floating Lyrics overlay width for smaller phones.
- Audio-reactive FFT visualizer path using Android `Visualizer` when RECORD_AUDIO is granted.
- Safe non-audio fallback animation when capture permission/session is unavailable; playback is never blocked by the visualizer.
- Compact reactive graph in both in-app lyrics and Floating Lyrics.
- Floating lyric text beat-energy bounce, stronger glow, and moving theme-coloured shine.
- More defensive lyrics sizing on narrow/short devices; theme colours are no longer washed toward plain white on OEM/device variations.
- Brighter Live Theme and Ambient particles while retaining foreground readability veils.
- Theme-specific Like/Download celebration bursts in Now Playing: Golden sparkles, Rose hearts, Ocean ripples, Violet orbit particles, Emerald leaf-like particles, AMOLED rays.
- Visible Library header shortcut to Listening Stats; existing Stats tab retained.
- Version bumped to 1.10.1 / versionCode 19.

## Regression protection
The following core files are byte-for-byte unchanged from v1.10.0:
- `player/PlaybackService.kt`
- `net/Downloads.kt`
- `net/YouTubeApi.kt`
- `net/AppUpdater.kt`

`PlayerClient.kt` only received the visual-only fallback signal update inside the existing 250 ms ticker; playback control/queue/stream behavior was not redesigned.

## Static validation performed in this environment
- 30 Kotlin source files scanned for balanced Kotlin delimiters after stripping comments/strings: PASS.
- AndroidManifest XML parse: PASS.
- 10/10 requested feature-wiring checks: PASS.
- Protected core-file comparison listed above: PASS.
- ZIP integrity check: run after packaging.

## Build limitation
This container does not include the Android SDK / Gradle dependency cache needed for a real Android APK compile. Run `BUILD-TEST-APK-v1.10.1.bat` on the normal DeepEcho Android build PC and test the APK on real devices before release.

Do not publish v1.10.1 as a GitHub release until playback, downloads, lyrics, floating overlay permissions, theme switching and upgrade-install behavior pass the real-device checklist in `TEST-NOTES-v1.10.1.md`.
