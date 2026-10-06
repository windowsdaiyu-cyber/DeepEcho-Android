# DEEP-ECHO Mobile v1.12.7 — Instant Start + Rapid Skip Test Line

This v1.12.7 test source builds directly on the latest v1.12.6 Lyrics Ready Search + Multi-Provider Lyrics + Floating Lyrics Sync Fix line.

## What v1.12.7 adds

- First-UI-before-player-connect startup ordering.
- Restored current track + bounded upcoming session lookahead without startup autoplay.
- Current restored track pre-prepare while paused.
- Candidate / WARM / READY future-track pipeline.
- Default 7-track source lookahead (3 READY + 4 WARM on normal unmetered conditions).
- Rapid-skip burst policy that keeps a larger future window where possible.
- Continuous lookahead refill on media transitions and queue changes.
- Existing YouTubeApi single-flight resolver/cache reused; no competing playback engine.
- Manual Play/Next promotion to highest-priority source preparation.
- Repeated Next avoids unconditional ExoPlayer re-prepare when the player is already prepared.
- Stale speculative jobs cancelled/demoted when queue intent changes.
- Data Saver / metered / memory-pressure / thermal-aware lookahead reduction.
- Local/download/direct media recognized as already source-ready.
- Optional lyrics prefetch and SmartCache moved out of the Play/Next critical path and delayed until playback is stable.
- Debug-only startup / Play / Next latency markers.
- Smart Autoplay async results revalidated before queue append.
- Previous v1.12.6 Floating Lyrics synchronization fix remains included.

## Protected systems

The lyrics package, PlaybackService, YouTubeApi resolver, Downloads, FloatingLyricsService, PlayerScreen, Screens, and TasteEngine remain byte-identical to the latest v1.12.6 base except where explicitly listed in CHANGED-FILES.

## Build on Windows

Run:

`BUILD-TEST-APK-v1.12.7.bat`

The builder runs all local unit tests first and then assembles the signed debug test APK using the existing permanent DeepEcho Android signing key.

Expected APK:

`DEEP-ECHO-Mobile-v1.12.7-INSTANT-START-RAPID-SKIP-TEST.apk`

Do not release until cold-start, restored-session Play, 1/2/5/7 rapid Next, queue order, Smart Autoplay, Downloads/Local Music, lyrics sync, Floating Lyrics, notification/Bluetooth Next, and background playback have been tested on a real phone.
