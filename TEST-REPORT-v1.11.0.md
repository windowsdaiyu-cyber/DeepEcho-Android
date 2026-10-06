# DEEP-ECHO Mobile v1.11.0 — Ruby Live UI static validation

## Feature wiring
- PASS: Ruby palette
- PASS: Ruby live backdrop
- PASS: Ruby ambient
- PASS: Ruby floating lyrics
- PASS: Ruby theme hot-swap
- PASS: Golden default preserved
- PASS: Live-only preserved
- PASS: v1.11.0

## Protected core compared to v1.10.9
- PASS: `app/src/main/java/com/deepecho/mobile/player/PlaybackService.kt`
- PASS: `app/src/main/java/com/deepecho/mobile/player/PlayerClient.kt`
- PASS: `app/src/main/java/com/deepecho/mobile/net/Downloads.kt`
- PASS: `app/src/main/java/com/deepecho/mobile/net/AppUpdater.kt`
- PASS: `app/src/main/java/com/deepecho/mobile/net/YouTubeApi.kt`
- PASS: `app/src/main/java/com/deepecho/mobile/net/Lrclib.kt`

## Notes
- Kotlin syntax smoke: modified files were parsed with `kotlinc`; no syntax/"expecting" errors were found. Android/Compose symbols cannot fully compile in this container because the Android SDK/Gradle dependency environment is not installed.
- Ruby particle count is capped lower while idle and higher while playing to reduce Home/Search scroll cost.
- Ruby root chrome uses translucent/glass surfaces; readability remains protected by dark-red base + text-safe contrast.
- Final APK build must be run on the normal Windows Android build setup using `BUILD-TEST-APK-v1.11.0.bat`.
