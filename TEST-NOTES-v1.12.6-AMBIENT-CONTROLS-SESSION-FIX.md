# DEEP-ECHO Mobile v1.12.6 — Ambient Controls + Session Fix

This test build remains on v1.12.6 because v1.12.6 is still unreleased.

## Changes
- Ambient fullscreen now has five responsive transport controls: Previous, Back 10s, Play/Pause, Forward 10s, Next.
- Controls scale down on short/narrow phone landscapes so they stay inside the visible area.
- Ambient media content reserves bottom room for the transport bar.
- Ambient mode is now opt-in per player session. Opening the app/player never restores a previously persisted Ambient ON state or forces landscape automatically.
- Closing Ambient/player explicitly resets Ambient state.
- Existing phone fix remains: Ambient toggle is reachable from Lyrics view and Ambient video button is height-aware on real phones.

## Non-regression boundary
No playback engine, lyrics engine, Exact Lyrics/OCR, Word-by-Word, runtime sync, downloads, queue, Smart Autoplay, Floating Lyrics, updater, YouTube resolver, or theme engine code was changed.

## Validation performed in packaging environment
- PlayerScreen Kotlin delimiter/lexical smoke: PASS.
- Required five Ambient transport action bindings: PASS.
- Session-default Ambient OFF gate/reset: PASS.
- Previous phone Ambient UI fix still present: PASS.
- Version remains versionName 1.12.6 / versionCode 35: PASS.
- Production-code diff versus previous v1.12.6 Phone Ambient UI Fix: PlayerScreen.kt only.

Final Android/Compose compilation and phone behavior must still be verified with BUILD-TEST-APK-v1.12.6.bat on the Windows Android SDK environment.
