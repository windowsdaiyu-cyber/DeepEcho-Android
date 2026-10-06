# DeepEcho Mobile v1.12.6 — Phone Ambient UI Fix

## User-visible fixes

1. **Ambient control stays visible while Lyrics is open on real phones.**
   - Added a compact phone-safe Ambient row above the lyrics viewport.
   - Existing artwork-mode Ambient toggle remains available.
   - No playback or lyrics timing code is changed.

2. **Ambient landscape Play/Retry video control no longer falls below the screen.**
   - The artwork square is now bounded by both width and the available phone-height budget.
   - A fixed control budget is reserved for Play / Pause / Retry and its status label.
   - Short landscape screens use a slightly smaller 42dp action button so it remains reachable.

## Non-regression protection

Only app source file changed:
- `app/src/main/java/com/deepecho/mobile/ui/PlayerScreen.kt`

The following critical systems were hash-verified byte-identical to the previous v1.12.6 source:
- LyricsOrchestrator
- LyricsRuntimeSync
- LyricsAlignmentEngine
- LyricsLiveVideoReader
- LyricsVideoOcrProcessor
- LyricsVideoRecovery
- Lrclib
- PlaybackService
- Downloads
- YouTubeApi
- FloatingLyricsService
- TasteEngine

`versionName` remains `1.12.6` and `versionCode` remains `35` because v1.12.6 is still an unreleased test line.

## Layout simulations

The bounded Ambient media layout was checked against representative dp viewports:
- 640 x 320 — fits
- 568 x 256 — fits
- 800 x 360 — fits
- 1000 x 500 — fits
- 360 x 640 — fits

## Device test checklist

1. Open a song on a real phone and enter Lyrics.
2. Confirm the `Ambient` switch is visible above the lyrics viewport.
3. Turn Ambient ON.
4. Rotate/allow the Ambient player to enter landscape.
5. Confirm the artwork, Play video button, and Play video label are all visible at the same time.
6. Tap Play video and confirm playback remains synchronized to the existing master audio.
7. Exit Ambient and confirm the normal lyrics sync position is unchanged.
8. Test Word by Word, Romanized, Exact Lyrics / Retry, and Floating Lyrics for regression.
