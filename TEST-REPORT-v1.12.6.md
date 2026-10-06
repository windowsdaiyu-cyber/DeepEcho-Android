# v1.12.6 Test Report

## Version
- versionCode 35
- versionName 1.12.6

## Requested fixes
- PASS: active synced/realtime lyric is horizontally centered.
- PASS: auto-follow uses measured viewport/item geometry to place the active lyric at the vertical center instead of `idx - 2`.
- PASS: active line keeps theme-aware glow and uses a stronger focus treatment.
- PASS: presentation changes do not modify lyric timestamps or runtime sync calculations.
- PASS: strict lyric videos can auto-start the live reader only after the normal lyrics engine returns no usable lyrics.
- PASS: normal videos and tracks with existing good lyrics do not auto-start video OCR.
- PASS: authored captions remain trusted fast-path.
- PASS: auto-generated captions are provisional; on-screen OCR is allowed to verify/replace them.
- PASS: live OCR near-playback sampling tightened from ~650 ms buckets to ~320 ms buckets with bounded work for fast lyric videos.
- PASS: manual Exact Lyrics / Retry non-live OCR path is unchanged; only `extractLive()` was altered in LyricsVideoOcrProcessor.

## Executed checks
- v1.12.6 lyric-video policy + center geometry Kotlin harness: PASS.
- LyricsVideoOcrProcessor Android/MLKit API-shape Kotlin compile harness: PASS.
- LyricsLiveVideoReader Kotlin API-shape compile harness: PASS.
- PlayerScreen Kotlin parser smoke: PASS (no parser/syntax errors; Android/Compose dependencies unavailable in this container).
- v1.12.2/v1.12.3 Universal Lyrics regression harness using current protected source: PASS.
- v1.12.4 lyric-video source resolver harness using current protected source: PASS.
- Protected critical files: 18/18 byte-identical to v1.12.5 baseline.

## Protected byte-identical areas
- LyricsOrchestrator normal provider/recovery engine
- LyricsVideoRecovery Exact Lyrics / Retry orchestration
- CaptionResolver
- LyricsAlignmentEngine
- LyricsRuntimeSync
- LrclibProvider / Lrclib facade / LyricsCache
- PlaybackService / PlayerClient
- YouTubeApi playback/search resolver
- Downloads / YtDownloader
- TasteEngine / LocalMusic
- FloatingLyricsService
- AppUpdater / UpdateUi

## Build gate
A real Android Gradle/SDK build cannot be executed in this Linux container because the project package intentionally relies on the Windows builder setup and Android SDK Platform 35 is not present here. Run `BUILD-TEST-APK-v1.12.6.bat` on the normal DeepEcho Windows build machine. Do not publish until APK build + emulator/device checks pass.
## Release-gate regression fix
- The full Windows Gradle release gate exposed `UniversalLyricsV2Test.sameTitleDifferentPerformerIsSuspicious`.
- Root cause: generic `tokenSimilarity()` intentionally ignores one-character tokens, so `Artist A` and `Artist B` both collapsed to the shared token `artist` for performer scoring.
- Fixed only performer matching by retaining meaningful one-character artist initials/suffix tokens; title similarity, lyric timing, alignment, provider ordering, playback and OCR timing were not changed.
- Focused Kotlin regression after the fix: `Artist A` vs `Artist B` => performer score 39, suspicious=true; exact `Eminem`, `The Weeknd`, Dubplate metadata and slowed-variant checks remain PASS.
- The TRUE ONE-CLICK release still runs the complete `:app:testDebugUnitTest` gate before it can push/tag anything.
