# v1.12.6 Multi-Provider Lyrics Validation Report

- Baseline existing app-main files: 60
- Byte-identical existing app-main files: 56
- Intentionally changed existing app-main files: 4
- Missing baseline files: 0
- New app-main files: 2

## Changed existing app-main files
- app/src/main/java/com/deepecho/mobile/lyrics/LyricsCandidate.kt
- app/src/main/java/com/deepecho/mobile/lyrics/LyricsFeatureFlags.kt
- app/src/main/java/com/deepecho/mobile/lyrics/LyricsMetadata.kt
- app/src/main/java/com/deepecho/mobile/lyrics/LyricsOrchestrator.kt

## New app-main files
- app/src/main/java/com/deepecho/mobile/lyrics/LyricsFormatParsers.kt
- app/src/main/java/com/deepecho/mobile/lyrics/LyricsProviderAdapters.kt

## Regression harness
- 30 PASS / 0 FAIL
- Includes prior live-video, manual OCR, Universal Lyrics V2, M/N text-repair/timing-preservation, multi-provider, and centered-lyrics UI tests.

## Compile smoke
- Provider adapters: PASS
- LyricsOrchestrator integration: PASS
- New unit-test source: PASS

## Android build
- Not executed in this container (no full Android SDK/Gradle Android environment).
- BUILD-TEST-APK-v1.12.6.bat now runs :app:testDebugUnitTest + :app:assembleDebug on the user Windows Android toolchain.
