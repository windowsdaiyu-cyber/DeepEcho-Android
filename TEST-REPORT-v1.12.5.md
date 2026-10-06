# v1.12.5 Test Report

## PASS — implementation / sandbox checks
- Version wiring: versionCode 34 / versionName 1.12.5.
- Strict lyric-video eligibility executable harness: PASS.
  - Accepts Lyrics / Lyric Video / Lyrical Video titles.
  - Rejects normal Official Music Video / Official Audio.
  - Rejects karaoke, covers and local files.
- Live reader Kotlin type/compile harness: PASS.
- Live OCR processor Android/ML-Kit API-shape Kotlin compile harness: PASS.
- Live captions fast-path executable harness: PASS; OCR is not invoked when usable current-video captions exist.
- Live OCR fallback executable harness: PASS; progressive OCR results become `Video OCR • Live` lyrics.
- PlayerScreen syntax parser smoke: no Kotlin syntax/`expecting` errors (Android/Compose symbols are unavailable in this sandbox, as expected).
- v1.12.2/v1.12.3 focused regression executable harness: PASS.
- v1.12.4 lyric-source resolver executable harness: PASS.
- v1.12.4 OCR cleaner executable harness: PASS.
- Source audit: `LyricsLiveVideoReader` / `extractLive` are not referenced by `LyricsOrchestrator` or `Lrclib.fetch()`.
- Source audit: the live feature is OFF by default and can only start through the lyric-video-only UI switch.
- Protected non-regression hash audit: 19/19 critical files unchanged from v1.12.4, including normal lyrics orchestration, Exact Lyrics / Retry recovery, caption/alignment/runtime sync, playback, downloads, updater, Floating Lyrics, TasteEngine and theme engines.

## Design safeguards
- Existing normal lyrics continue loading regardless of the live-video switch.
- Video-derived lyrics replace the display only after a usable live result exists.
- Captions are preferred over OCR.
- OCR sampling prioritizes the current line and short look-ahead window instead of pre-scanning the whole video.
- Visually unchanged samples are skipped to reduce OCR load.
- Per-song cancellation prevents stale old-song results from replacing a new song.
- v1.12.4 Exact Lyrics / Retry pipeline is unchanged.

## Environment limitation
This sandbox does not contain Android SDK Platform 35 / the full Android Gradle toolchain, so a real `:app:assembleDebug`, emulator/device run, and signed APK cannot honestly be marked PASS here. The included Windows builder uses the user's Android SDK + permanent v1.9.0 signing key and is the final build/device gate before release.

## Release status
TEST SOURCE ONLY. Do not publish v1.12.5 until the Windows build and device checks pass.
