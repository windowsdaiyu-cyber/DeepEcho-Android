# DEEP-ECHO Mobile v1.12.10

## Premium Home + Artwork / Player Cleanup

DeepEcho Android v1.12.10 builds on the stable v1.12.8 player/UX release and carries forward the Premium Home redesign plus a focused artwork-quality and player-mode cleanup. The existing playback, lyrics, download, queue, Smart Autoplay and theme systems are preserved rather than replaced.

### Premium Home experience

- Home uses a cleaner premium music-app layout while keeping DeepEcho's existing sections, recommendation logic and theme identity.
- Refined top header, mood/vibe pills, featured/recent hero presentation, cleaner image-first shelves, larger artwork cards and improved spacing/typography.
- Home mini-player and bottom navigation receive a floating rounded treatment without changing the underlying playback session.
- Redundant inline Like / Queue / Download controls are removed from Home song cards to reduce clutter.
- Long-pressing a song cover opens the existing full Quick Actions sheet for Like/Unlike, Play Next, Add to Queue, Download, Add to Playlist, Share, More Like This, Less Like This and other supported actions.

### Player cleanup

- The visible Full Player mode selector now exposes Artwork, Lyrics and Minimal.
- Visualizer is no longer exposed as a selectable player mode.
- Ambient is no longer exposed as a player-mode tab; the existing Ambient switch/toggle remains the intended entry point.
- No second player, visualizer engine or Ambient implementation is introduced.

### Sharper artwork across the app

- Shared artwork loading now prefers higher-resolution CDN candidates where available.
- YouTube artwork can promote to max-resolution candidates with graceful fallback when a higher-resolution image is unavailable.
- Google/YouTube Music image URLs are promoted to more suitable display sizes where supported.
- Decode sizing remains surface-aware so small cards do not unnecessarily decode huge images.
- Data Saver behavior remains respected.
- The shared artwork path improves Home, Search, Full Player, mini-player, playlists/details, song rows, queue and artist UI without blocking playback on artwork.

### Regression protection

- PlaybackService and PlayerClient behavior are preserved.
- Instant Start / Rapid Skip playback-performance work is preserved.
- Downloads and YouTube stream resolution are not rewritten.
- LyricsOrchestrator, Floating Lyrics and current lyrics synchronization/recovery systems are preserved.
- Queue, Smart Autoplay, recommendation logic and existing DeepEcho themes remain in place.
- The release runner verifies the exact packaged source before building and refuses to publish on a failed build, wrong version, wrong package or signing-key mismatch.

### Release verification performed by the one-click runner

Before the public tag is created, the release script will:

1. Verify the exact source-file SHA-256 manifest.
2. Confirm package `com.deepecho.mobile`, version name `1.12.10` and version code `39`.
3. Run debug unit tests and build the signed release APK.
4. Verify the APK signature and permanent DeepEcho signing certificate.
5. Verify the APK package/version with Android build tools.
6. Push the Android source to the official Android repository.
7. Create tag `v1.12.10` only if that tag does not already exist.
8. Wait for and verify the public GitHub Release APK and SHA-256 assets.

### Android package

- Package: `com.deepecho.mobile`
- Version name: `1.12.10`
- Version code: `39`
