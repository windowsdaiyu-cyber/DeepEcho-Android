# DeepEcho Android v1.12.11 — Test Notes

## Playback tests
1. Search a song and tap it: audio should begin faster than v1.12.10.
2. From Home, rapidly tap different songs: the most recently selected song should get playback priority.
3. Press Next repeatedly 5–10 times: transitions must remain ordered and responsive.
4. Press Previous within the first 3 seconds of a track: previous track should start without a full unnecessary re-prepare.
5. Press Previous after 3+ seconds: current track should restart from 0, same as before.
6. Test on weak/mobile network if possible: startup may temporarily choose a lighter stream to reduce delay.
7. Data Saver ON: tiny next-track network probe must stay disabled.

## Home tests
1. Home must not show a Smart Autoplay ON/OFF pill.
2. Settings > Smart Autoplay must still exist and work.
3. The rotating Home quote should show fully across the row, up to three lines, without the old half-hidden ellipsis.

## Full Player tests
1. Mode row should show Artwork / Lyrics / Minimal / Ambient.
2. Visualizer must not be visible as a mode.
3. There should be no standalone Ambient switch in normal Artwork/Lyrics player UI.
4. Tap Ambient: fullscreen Ambient should open.
5. The switch inside fullscreen Ambient must remain and should exit Ambient when turned off.
6. On short/narrow phone layouts, artwork must stay inside its stage; title/artist/actions must not draw over the artwork.

## Regression tests
- Lyrics
- Word-by-Word
- Romanized Lyrics
- Floating Lyrics
- Exact Lyrics / OCR recovery
- Downloads
- Queue / Play Next
- Smart Autoplay engine
- Search
- Premium Home shelves
- Artwork quality
- All themes / Live themes
- Background playback / notification / lock screen
