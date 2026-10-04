# DEEP-ECHO Mobile v1.10.1 Test Notes

1. Install over the current signed Android DeepEcho build.
2. Verify normal playback, next/previous, seek, queue, downloads and background playback.
3. Test Lyrics on at least one narrow/low-height device and one normal phone. Active lyric should stay readable and animated.
4. Toggle Floating lyrics ON/OFF. Grant overlay permission when asked. Grant microphone/audio-capture permission only for the real FFT visualizer; denying it must not break playback or lyrics.
5. With Floating lyrics ON, verify graph motion, lyric bounce and shine while music plays.
6. Switch Golden/Violet/Ocean/Rose/Emerald/AMOLED and confirm particles remain bright but text/buttons remain readable.
7. Tap Like and Download in Now Playing and verify each theme uses a distinct burst.
8. Open Library and use the visible Listening Stats shortcut; confirm Today/7 days/All time/top songs/top artists.
9. Re-test Romanized and Word by Word independently.
10. Do not release if any stable playback/download regression appears.
