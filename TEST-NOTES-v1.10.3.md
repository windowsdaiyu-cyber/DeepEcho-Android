# TEST NOTES — v1.10.3

Focus: About Artist fullscreen isolation and responsive safe areas.

- Open About Artist from Now Playing on at least one small phone and one tall phone/emulator.
- Verify no Home content, Mini Player or bottom navigation is visible behind the artist page.
- Verify the top row clears the status bar and the last content clears the system navigation area.
- Verify theme particles remain visible but text/buttons stay readable.
- Open a playlist/album from About Artist and confirm it also has an isolated themed background.
- Back navigation should return Artist -> Player without closing playback.
- Regression: playback, downloads, lyrics, Floating Lyrics, search and updater should behave exactly as v1.10.2.
