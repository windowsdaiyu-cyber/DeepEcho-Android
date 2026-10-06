# DeepEcho Android v1.12.6 — Floating Lyrics Sync Fix Test Report

## Root cause
Floating Lyrics enabled Android Visualizer capture. The main Lyrics UI fed that new `realCapture=true` state into the existing runtime vocal-onset guard, so toggling the overlay could change the active lyric clock mid-song and temporarily return `-1` / Instrumental. The overlay also performed its own lyrics fetch instead of always preferring the exact payload already rendered by the main Lyrics UI.

## Fix
- Existing `LyricsRuntimeSync` algorithm is not rewritten.
- When Floating Lyrics is active, overlay-only Visualizer capture is excluded as a timing authority on both surfaces.
- Main Lyrics publishes its resolved `Lyrics` payload to a tiny in-process presentation bridge.
- Floating Lyrics adopts that exact payload immediately, including same-song Exact Lyrics/live-video recovery updates.
- If the main Lyrics UI is unavailable (for example background/service-only use), Floating Lyrics still falls back to the existing `Lrclib.fetch(song)` facade.
- Both surfaces keep using `PlayerClient.positionMs`, the same `lyrics_offset_<videoId>`, and existing lyric timestamps.

## Local validation
- Helper Kotlin compile/runtime harness: PASS (`FLOATING_SYNC_HELPERS_OK`).
- Static integration checks: PASS.
- Protected core byte-identity checks: PASS.
  - UNCHANGED `app/src/main/java/com/deepecho/mobile/lyrics/LyricsRuntimeSync.kt` — `b90182e22649f676db9c27397a320e6e95b05a7266edc48a3971600281eacee6`
  - UNCHANGED `app/src/main/java/com/deepecho/mobile/lyrics/LyricsOrchestrator.kt` — `7933153824b9ab4e8b6f44145d78ee225437a393a17a425e5750d42bbdfc6d05`
  - UNCHANGED `app/src/main/java/com/deepecho/mobile/lyrics/LyricsAlignmentEngine.kt` — `6193bcec93da03081261c0c783efaf54eb2a05006bd32d928268f114040f99a5`
  - UNCHANGED `app/src/main/java/com/deepecho/mobile/lyrics/LyricsLiveVideoReader.kt` — `455698569d32cb7d29a5a5b073eae4038fb15ee93453de578e27457546d82017`
  - UNCHANGED `app/src/main/java/com/deepecho/mobile/lyrics/LyricsVideoOcrProcessor.kt` — `8cc2c02ecf55103a6958496e5d35756a4b6250a7cb1efd28a7d60303e84c5cd1`
  - UNCHANGED `app/src/main/java/com/deepecho/mobile/player/PlaybackService.kt` — `84c94b4354332b171de01e049c0d71f2ab8ef0f4275c1a99eadb0bd77a0201fc`
  - UNCHANGED `app/src/main/java/com/deepecho/mobile/net/Lrclib.kt` — `520ab7db71eff5cebff018ba778e3e6616d2031eae3cb10ae3a4d1bcb1061aa5`
  - UNCHANGED `app/src/main/java/com/deepecho/mobile/net/Downloads.kt` — `e794e24cff71edd518ebaaa3f9b536070b3c6cf79ca325c4d39c0df108ace61d`

## Android build gate
A full Android SDK/phone build is not claimed in this container. Run `BUILD-TEST-APK-v1.12.6.bat` on the normal Windows test machine; it runs unit tests before producing the APK.

## Required phone regression
1. With a lyric line currently glowing, toggle Floating ON. The main glow must continue without an Instrumental reset.
2. Floating must show the same current/next lyrics as the main Lyrics screen.
3. Toggle Floating OFF/ON repeatedly; lyric timing must not jump.
4. Change manual ±0.5 s timing; both surfaces must follow together.
5. Exact Lyrics/Retry while Floating is ON must propagate the recovered payload without restarting the song.
