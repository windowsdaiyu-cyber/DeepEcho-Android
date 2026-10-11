# DeepEcho Android v1.12.13 — Fast Lyrics + Vocal Start Sync Test Report

## Targeted fixes
- Search `SYNCED LYRICS` and playback acceptance now share the same safe provider-timing thresholds for normal studio tracks.
- Explicit Lyrics-search synced attachments are consumed before caption verification, eliminating the badge-present/player-empty mismatch for that path.
- Strong LRCLIB synced candidates can return without the slow caption-verification stage when metadata is safe.
- Covers/live/remix/slowed/sped-up/explicit-video variants still fail closed into current-upload grounding.
- Synced lyrics remain in instrumental/waiting state until the first meaningful provider/verified vocal timestamp.
- Ambient lyrics do not display the first lyric line during the instrumental intro.

## Validation performed here
- Version `1.12.13`, versionCode `42` — PASS.
- Root `BUILD-TEST-APK-v1.12.13.bat` — PASS.
- LyricsOrchestrator actual source syntax-compiled with dependency stubs — PASS.
- LyricsRuntimeSync actual source compiled + vocal-onset behavior harness — PASS.
- Focused source policy checks — 10/10 PASS.
- Android resource XML parse — 5/5 PASS.
- Protected v1.12.12 playback/download/artwork/theme/search/floating-lyrics files hash-identical — 15/15 PASS.

## Protected behavior
PlaybackService, PlayerClient, PlaybackLookahead, PlaybackSeekPolicy, Downloads, YouTubeApi/YtDownloader, SmartCache, TasteEngine, ArtworkQuality, Theme, HomeExperience, LyricsSearchAvailability and FloatingLyricsService were not modified.

## Environment limitation
This environment does not include the user's Android SDK/signing setup, so the final Android Gradle APK compile must be run with the root Windows builder. Packaging must not be treated as a substitute for that real compile.
