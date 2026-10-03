# DeepEcho Android v0.1.0 Alpha — Test Checklist

Mark each item PASS / FAIL and keep the exact song/search used for failures.

## Startup and UI
- App opens without crash.
- Golden Particle is the default theme.
- All 13 theme choices switch without restart.
- Home, Search, Lyrics, Downloads and Settings tabs open.
- Animated background remains smooth while scrolling.

## Bridge
- `bridge\Start-Bridge.bat` prints listening on 127.0.0.1:17832.
- App header changes from ALPHA to LIVE.
- Search returns real results.
- Repeating the same search is faster because the bridge cache is warm.

## Playback
- First selected result starts.
- Play/pause works.
- Seek works.
- Audio continues after leaving the app.
- Android media notification shows the song title/artist.
- Headset/Bluetooth play-pause should be checked on a real phone later.

## Lyrics / overlay
- Demo tracks advance synced test lines.
- Lyrics screen highlights the active line.
- Floating Lyrics permission screen opens.
- Floating overlay appears above another app and can be dragged.

## Downloads
- Download button starts an Android system download.
- Completion notification appears.
- File appears under Music/DeepEcho when Android allows public media access.

## Performance
- Theme animation does not block taps.
- Search remains responsive while playback is active.
- Background playback survives screen off on a real phone test.

## Parity test for next milestone
Create a fixed list of at least 100 songs used on DeepEcho Windows v2.6.2 and record:
- Search found: Windows / Android
- Playback success: Windows / Android
- Time-to-first-audio
- Download success
- Lyrics availability / timing

Do not claim desktop-equivalent catalog coverage until this parity sheet is measured.
