# DEEP-ECHO Mobile v1.12.6 — Centered Live Lyrics + Fast Lyric Video Reader

This test-source build is based directly on the v1.12.5 working source.

## v1.12.6 fixes
- Current synced lyric is auto-followed to the real vertical center of the lyrics viewport.
- Lyrics are horizontally centered; active line is larger with a stronger theme-aware glow.
- No lyric timestamps, provider ordering, runtime sync math, Word-by-Word timing, Romanized logic, or playback timing were changed.
- Strict lyric videos auto-start `Read lyrics from video` only after the normal lyrics engine returns no usable lyrics.
- Existing good lyrics never auto-start OCR. User can still turn the video reader off/on.
- Authored captions remain the fastest trusted path. Auto-captions are provisional and are checked against on-screen OCR instead of blindly winning.
- Live OCR uses denser ~320 ms near-playback sampling, follows changing playback position continuously, and has a larger bounded frame budget for fast lyric videos.

## Build
Run `BUILD-TEST-APK-v1.12.6.bat` (or `build_apk.bat`).
Expected APK: `DEEP-ECHO-Mobile-v1.12.6-CENTERED-LIVE-LYRICS-TEST.apk`.

Do not publish until the Windows Android build and device/emulator checks pass.
