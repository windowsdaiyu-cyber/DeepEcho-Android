# DEEP-ECHO Mobile v1.10.4 test notes

## Requested UI/navigation changes
- Bottom navigation is now Home / Search / Recognize / Library.
- Settings was removed from bottom navigation and is opened from the Home-only top-right gear button.
- New Recognize Music screen sits directly beside Search in bottom navigation.

## Recognize Music
- Requests microphone permission only when recognition starts.
- Records an 8-second mono PCM sample, wraps it as WAV in memory, sends it to the configured recognition provider, discards the sample after the request, and resolves a playable DeepEcho catalog result.
- Recognized tracks can be played directly in DeepEcho.
- Test provider hook uses `DEEPECHO_AUDD_TOKEN` -> BuildConfig. No token is bundled in source. For a public release use a private backend/proxy or another credential-safe provider.

## Ambient Mode redesign
- Ambient ON/OFF control moved beside About Artist in Now Playing.
- Removed the duplicate Ambient icon from the lower shuffle/queue/repeat row.
- Ambient mode now renders a theme-aware split stage: cover/video on one side and stylish synced lyrics on the other.
- Lyrics use theme-specific color/glow and subtle audio-reactive bounce.
- A play/video button under the cover swaps the cover for the song's YouTube video.
- Video starts at the current playback position, stays muted so DeepEcho remains the audio source, follows play/pause, and periodically re-seeks if drift exceeds ~1.25 seconds.

## Continuity
- Built from v1.10.3 Artist Overlay Fix.
- PlaybackService, Downloads, AppUpdater, FloatingLyricsService and the existing lyrics provider implementation were not rewritten.
