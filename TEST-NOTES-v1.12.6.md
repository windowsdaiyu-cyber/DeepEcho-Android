# DeepEcho Android v1.12.6 — Test Notes

Primary fixes:
1. Active realtime lyric must stay vertically centered in the lyrics viewport and horizontally centered, with theme-aware glow.
2. Existing sync/timestamps must remain unchanged.
3. For strict lyric videos with normal lyrics unavailable, live video reader may auto-start. Normal videos and songs with good lyrics must not auto-start OCR.
4. Fast lyric videos should use denser live OCR sampling. Authored captions win immediately; auto-captions remain provisional until on-screen text is checked.

Device checks before release:
- Páaro / any known-good synced song: active line stays center while timing remains identical.
- Manual scroll suspends auto-follow; Sync restores centered follow.
- Word by Word and Romanized still work.
- General Levy - Dubplate (Lyrics): when normal lyrics fail, video reader starts without requiring an extra toggle tap; verify text begins appearing while playback continues.
- Toggle video reader OFF: it stays off for the current track.
- Normal Official Music Video: no live OCR auto-start.
- Existing-good lyric video: normal lyrics remain primary and OCR does not auto-start.

Release-gate follow-up:
- Full Windows unit tests caught `UniversalLyricsV2Test.sameTitleDifferentPerformerIsSuspicious`.
- Fixed performer-only metadata similarity so artist initials remain distinguishing tokens (`Artist A` != `Artist B`).
- This does not change lyric timestamps, alignment, rendering sync, playback, downloads, or Exact Lyrics/OCR timing.
