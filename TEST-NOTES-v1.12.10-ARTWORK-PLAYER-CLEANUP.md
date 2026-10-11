# DeepEcho Android v1.12.10 - Test Notes

## Requested fixes
- Visualizer removed from visible Full Player mode tabs.
- Ambient removed from visible Full Player mode tabs; Ambient switch remains the only entry.
- Central artwork rendering upgraded for Full Player, Home, Search, mini-player, playlists, rows, artist surfaces and other UI surfaces that use `Art()`.

## Regression intent
Do not change playback, stream resolution, downloads, lyrics, Floating Lyrics visual capture, queue, Smart Autoplay, Premium Home layout, or themes.

## Device checks
1. Full Player mode row only shows Artwork / Lyrics / Minimal.
2. Ambient switch still enters/exits Ambient normally.
3. Full Player artwork is crisp after high-resolution image resolves.
4. Home and Search covers are crisp and do not remain on tiny thumbnail decodes.
5. Mini-player, playlist/detail, artist and queue artwork still loads.
6. Data Saver still avoids forced high-resolution URL promotion.
7. Rapid Next, lyrics, Floating Lyrics, downloads, queue, Smart Autoplay and themes regressions: none.
