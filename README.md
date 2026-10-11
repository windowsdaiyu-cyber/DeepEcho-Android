# DEEP-ECHO Mobile v1.12.10 — Artwork + Player Cleanup Test Line

This build is based directly on the successfully compiling v1.12.9 Premium Home source.

## What changed

- Full Player mode row now exposes only **Artwork**, **Lyrics** and **Minimal**.
- **Visualizer** is no longer a visible player mode. Its internal audio-reactive support remains available for existing features such as Floating Lyrics.
- **Ambient** is no longer a visible player mode. Ambient remains available through the existing **Ambient switch** only.
- Central `Art()` rendering now requests larger image decodes and promotes known YouTube/Google image-CDN URLs to higher-resolution artwork with graceful fallback.
- The artwork improvement applies automatically to Full Player, Home, Search, mini-player, playlist/detail cards, song rows, artist surfaces, queue and other in-app surfaces that already use the shared `Art()` component.
- Data Saver keeps its conservative artwork behavior instead of forcing high-resolution promotion.

## Protected systems

PlaybackService, PlayerClient, YouTube stream resolution, Downloads, SmartCache, PlaybackLookahead, lyrics retrieval/sync/OCR, Floating Lyrics service, Smart Autoplay/queue logic, Home recommendation logic and the theme implementation are intentionally unchanged.

## Build on Windows

Fresh-extract the ZIP and run this file from the **ROOT** of the project folder:

`BUILD-TEST-APK-v1.12.10.bat`

The builder runs unit tests and then assembles the signed debug APK using your existing permanent DeepEcho Android signing key.

Expected APK:

`DEEP-ECHO-Mobile-v1.12.10-ARTWORK-PLAYER-CLEANUP-TEST.apk`

## Quick emulator checklist

1. Full Player top mode row should show only Artwork / Lyrics / Minimal.
2. Ambient switch should still enter and exit Ambient normally.
3. Test 3–5 songs with visibly different covers in Full Player, Home and Search; high-resolution artwork should replace low-resolution sources when available.
4. Check mini-player, playlist/detail, queue and artist artwork.
5. Regression-test Play/Pause, Next/Rapid Next, Previous, Seek, Queue, Downloads, Smart Autoplay, Lyrics, Word-by-Word, Romanized, Floating Lyrics, Exact Lyrics, themes, notification/lock screen and background playback.
