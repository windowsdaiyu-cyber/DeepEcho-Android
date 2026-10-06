# v1.12.6 Floating Lyrics Sync Fix

## Regression being fixed
Turning Floating Lyrics ON enabled Android Visualizer capture. The existing runtime vocal guard could then switch from raw lyric time to an onset-gated path mid-song, producing `Instrumental` in the overlay and removing the active/glowing line in the main Lyrics UI. Floating Lyrics also independently fetched lyrics instead of always mirroring the exact payload already selected by the main Lyrics UI.

## Fix scope
- No rewrite of the lyrics engine or sync/alignment engine.
- `LyricsRuntimeSync.kt` remains byte-unchanged.
- Added a tiny presentation bridge so the overlay mirrors the main UI resolved `Lyrics` object.
- Added a policy that prevents overlay-only Visualizer activation from changing timing mode.
- Floating service still falls back to the normal `Lrclib.fetch(song)` facade if no main-UI payload is available (for background/service-only operation).

## Required phone checks
1. Start a song with verified synced lyrics. Confirm current line glows normally.
2. Toggle Floating Lyrics ON during an active lyric line. Main highlight must continue immediately with no `Instrumental` reset.
3. Overlay must show the same current line and next line as the main Lyrics screen.
4. Toggle Floating OFF/ON repeatedly; timing must not jump.
5. Change manual ±0.5s offset; both surfaces must follow the same offset.
6. Trigger Exact Lyrics/Retry while overlay is ON; when recovery succeeds, overlay must adopt the same resolved payload without requiring song change.
7. Verify Word-by-Word, Romanized, Search Lyrics tab, Ambient, playback and downloads remain unchanged.
