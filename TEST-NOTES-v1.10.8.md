# DEEP-ECHO Mobile v1.10.8 Test Notes

## Ambient fullscreen

1. Start any song and open Now Playing.
2. Turn Ambient Mode ON beside About Artist.
3. Confirm the activity rotates to an immersive landscape player and hides system bars.
4. Confirm artwork is on the left and synced lyrics are on the right.
5. Turn Ambient OFF from the fullscreen switch and confirm normal player/orientation returns.
6. Test Golden, Ocean, Violet, Rose, Emerald and AMOLED themes for readable text and themed visuals.

## Synced video

1. In Ambient fullscreen, tap Play Video under the cover.
2. Confirm the cover is replaced in-place by video (no YouTube iframe/WebView error card).
3. Confirm DeepEcho audio remains the only audible source.
4. Seek the song by at least 30 seconds; video should follow/re-sync.
5. Pause/resume song; video should follow.
6. If a stream fails, use Retry and confirm a fresh stream attempt occurs.

## Lyrics

- Test a song with real synced LRCLIB lyrics near the beginning and near the end.
- Test Word by Word ON/OFF.
- Test Romanized ON/OFF.
- Test manual timing +/-0.5 s and confirm the saved offset also affects Ambient lyrics.

## Artwork

- Compare Home/Search/Now Playing art with the previous build.
- Existing low-resolution YouTube thumbnail URLs should attempt max-resolution art and safely fall back when unavailable.

## Player and audio

Exercise the new Settings → Player and audio screen, especially:
- streaming/download quality
- Wi-Fi-only downloads
- metadata sidecar downloads
- history duration
- skip silence
- normalization/loudness/spatial effects
- audio offload
- preloading and preload limit
- progressive seek
- persistent queue
- Smart Autoplay similar-content controls
- duplicate queue protection
- error auto-skip
- task-clear behavior
- muted-volume pause
- Bluetooth resume
- keep-screen-on

Crossfade, Automix, Google Cast and Export as MP3 are intentionally disabled in this stability build and must not claim successful activation.
