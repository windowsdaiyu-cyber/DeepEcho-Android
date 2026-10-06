# DEEP-ECHO Mobile v1.12.6 — Compact Lyrics UI test build

## Scope
Presentation-only Lyrics screen refinement on top of the working v1.12.6 Ambient Controls + Session Fix build.

## Changes
- Lyrics utility controls are consolidated into one compact horizontal strip: Floating, Word, Romanized, and Video when eligible.
- Exact/Repair/Sync/Re-align/Missing recovery actions remain in one compact single-line strip.
- Timing row and lyric-row vertical spacing are slightly reduced to expose more synchronized lyric lines on phone displays.
- Active lyric remains centered and glowing.

## Protected behavior
No changes were made to lyric timestamps, runtime sync, provider lookup, Exact Lyrics/OCR recovery, Word-by-Word timing logic, playback, queue, downloads, Ambient transport, or streaming.

## Final build gate
Run BUILD-TEST-APK-v1.12.6.bat on Windows and test the Lyrics screen on a real phone before release.
