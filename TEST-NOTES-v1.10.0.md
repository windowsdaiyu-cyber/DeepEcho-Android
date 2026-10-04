# DEEP-ECHO Mobile v1.10.0 Feature Test Notes

## Required regression tests
1. Install over the current v1.9.0/v1.9.1 test app; verify data, likes, downloads and playlists remain.
2. Play streamed + downloaded songs for 10+ minutes; seek, pause/resume, next/previous, queue, shuffle and repeat.
3. Toggle Ambient Mode using the new button beside Repeat and from Settings; test Golden, Violet, Ocean, Rose, Emerald and AMOLED.
4. Turn Live Theme on and inspect every screen for readable text/buttons while particles move.
5. Search several Hindi/English/Punjabi tracks; verify Top Result, Songs, Videos, Albums and Artists chips and labels.
6. Open album/playlist results and play tracks.
7. Lyrics: synced track, unsynced fallback, Word by Word ON/OFF, Romanized ON/OFF, timing SYNC.
8. Home: fast repeated scrolling for at least 60 seconds; verify no severe frame hitching and shelves still load.
9. Library > Stats: listen, pause, switch songs, verify time/play counts and reset.
10. Background/foreground the app, rotate if enabled, receive update check, then verify playback still works.

## Important
The lyrics pipeline improves coverage but does **not** claim a literal 99% or perfect-sync guarantee. Real synced timing still depends on source availability; estimated fallback is labelled as estimated.
