# DeepEcho Android v1.12.7 — Instant Start + Rapid Skip Test Report

## Base

Built from the latest v1.12.6 Floating Lyrics Sync Fix source, which already contains the Lyrics Ready Search tab, multi-provider lyrics/text repair, compact Lyrics UI, Ambient fixes/controls, and Floating Lyrics shared-timeline fix.

## Version

- versionName: `1.12.7`
- versionCode: `36`
- applicationId: `com.deepecho.mobile`

## Implemented performance layer

- UI shell is installed before MediaController connection.
- Animated Live Theme backdrop is deferred briefly while correct theme colors render immediately.
- Restored session remains paused; current media is prepared in the background without audible autoplay.
- Up to 8 upcoming track identities are saved with the lightweight song session and restored as a bounded lookahead.
- `PlaybackLookahead` maintains Candidate / WARM / READY states using bounded concurrency.
- Normal unmetered default policy: 3 READY + 4 WARM (capped by the user Preload Limit, now default 7).
- Rapid Next burst increases lookahead intent while keeping resource use bounded.
- Source resolution reuses the existing `YouTubeApi.resolve()` cache/single-flight path.
- Closest READY remote item may receive a tiny 32 KiB unmetered network probe; no full multi-track download is performed.
- Manual Play/Next promotes the requested track and cancels queued speculative warm work.
- Manual Next no longer unconditionally calls `prepare()` when ExoPlayer is already prepared; IDLE recovery remains.
- Media transitions and queue mutations continuously refill lookahead.
- Smart Autoplay results are revalidated after async recommendation work before appending.
- Data Saver, metered network, memory pressure and severe thermal state reduce speculative windows.
- Local/download/direct media bypass remote source resolution.
- Optional Lyrics prefetch and SmartCache work are delayed until playback is stable, outside Play/Next critical paths.
- Debug-only `DeepEchoPerf` markers track startup and Play/Next-to-READY timing.

## Local validation completed in this environment

- Pure `PlaybackLookaheadPolicy` executable checks: **6/6 PASS**.
- Modified Kotlin source parser smoke (syntax-focused): **8/8 PASS**.
- Protected lyrics package: **27/27 files byte-identical** to the v1.12.6 base.
- Additional protected core files byte-identical: `PlaybackService.kt`, `YouTubeApi.kt`, `Downloads.kt`, `FloatingLyricsService.kt`, `PlayerScreen.kt`, `Screens.kt`, `TasteEngine.kt`.
- New Android unit test file adds 6 focused v1.12.7 tests; project now contains 47 `@Test` methods across 10 test files.

## Full build limitation

The current container does not provide Android SDK Platform 35 / the Windows Java-17/Gradle toolchain used by the project, so a full Android Gradle build/device run is **not claimed here**.

Run `BUILD-TEST-APK-v1.12.7.bat` on the normal Windows test machine. It runs `:app:testDebugUnitTest :app:assembleDebug` before copying the test APK.

## Required device gate before release

Verify cold/warm startup, restored session pause/position, restored Play, 1/2/5/7/10 rapid Next, playlist/manual Queue/Liked/Downloads/Local ordering, Smart Autoplay, expired stream recovery, Data Saver, background/locked screen, notification/Bluetooth Next, lyrics sync, Word-by-Word, Romanized, Floating Lyrics, Exact Lyrics, Lyrics Search, Ambient, themes, and MediaSession controls.
