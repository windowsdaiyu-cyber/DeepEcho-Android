# v1.12.4 Test Report

## PASS — sandbox checks
- Version wiring: versionCode 33 / versionName 1.12.4.
- OCR cleanup executable harness: PASS (duplicate-frame merge, stationary-corner watermark rejection, no dangerous spelling replacement).
- LyricsVideoSourceResolver executable harness: PASS (lyrics-oriented result wins; karaoke/cover rejected).
- Modified LyricsOrchestrator type/syntax harness: PASS.
- Manual LyricsVideoRecovery type/syntax harness: PASS.
- Android MediaCodec/MediaExtractor fingerprint aligner API-shape harness: PASS.
- ML Kit OCR processor API-shape harness: PASS.
- Combined modified-Kotlin parser smoke: PASS (no syntax/"expecting" errors).
- v1.12.2/v1.12.3 focused regression executable harness: PASS (metadata safety, Dubplate ambiguity, piecewise timing alignment, yaham→yahan contextual repair without timestamp movement, repair planner, upload-transcription fallback).
- Manual gate source audit: normal lyrics fetch does not call LyricsVideoRecovery; only fetchExactRecovery can reach it.
- Existing reliable lyrics quality guard restores/keeps the higher-quality result before any OCR fallback.
- No temporary OCR/video files are created, so there are no hidden artifacts to leak storage.
- Protected playback/download/queue/recommendation/theme/updater files are unchanged from v1.12.3 (13-file SHA-256 comparison PASS).

## Dependency verification
- Bundled ML Kit Latin text recognition coordinate is `com.google.mlkit:text-recognition:16.0.1`; app minSdk 26 satisfies the SDK requirement.

## Environment limitation
This sandbox does not contain Android SDK Platform 35 / the full Android Gradle toolchain, so a real `:app:assembleDebug`, emulator/device run, and release APK signing cannot honestly be marked PASS here. The included Windows builder uses the user's existing Android SDK + permanent v1.9.0 signing key and must be the final build/device gate before release.

## Release status
TEST SOURCE ONLY. Do not publish v1.12.4 until the Windows build and device checks pass.
