# DeepEcho Android v1.12.9 — Premium Home UI

## Scope

Home visual/interaction update only, based on the supplied reference screenshots.

### Home visual changes
- Premium DeepEcho header with Search, Settings and Smart Autoplay state.
- Mood/Vibes pill selector.
- Large Recently Played hero and Keep Listening shelf.
- Larger image-first song cards with cleaner title/artist hierarchy.
- Image-first Daily Mix and playlist cards.
- Premium section spacing/typography while continuing to use MaterialTheme tokens.
- Rounded floating mini-player and bottom navigation only while Home is active.

### Song interaction change
Home song cards no longer render Queue / Download / Like / More buttons underneath the artwork.
- Tap artwork -> normal playback.
- Long-press/hold artwork -> existing SongActionsSheet.
- SongActionsSheet remains the single source for Like/Unlike, Play Next, Queue, Download, Add to Playlist, Share, More/Less Like This and other supported actions.

## Zero-regression protections checked in source
Byte-identical to the v1.12.8 golden baseline:
- PlaybackService.kt
- PlayerClient.kt
- Downloads.kt
- YouTubeApi.kt
- LyricsOrchestrator.kt
- SmartCache.kt
- Theme.kt

Static checks performed:
- Android XML parse: PASS
- Changed Kotlin delimiter scan: PASS
- No invalid explicit Compose layout.weight import: PASS
- Home Shelf inline Queue/Download/Like actions removed: PASS
- Home artwork long-press action wiring present: PASS
- Fixed diagnostic builder retry logic retained: PASS

## Build status
This environment does not contain the Android SDK platform needed to run the real Gradle Android build. Use the root BUILD-TEST-APK-v1.12.9.bat on the established Windows DeepEcho build setup. Do not call the update release-ready until that build and emulator/phone regression tests pass.
