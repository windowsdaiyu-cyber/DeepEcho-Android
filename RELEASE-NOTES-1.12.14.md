# DEEP-ECHO Mobile v1.12.14

## Manual Queue + Hidden Smart Autoplay

DeepEcho Android v1.12.14 keeps the v1.12.13 fast-lyrics/vocal-sync behavior and the earlier instant-playback, fast-seek, large-artwork and Premium Home work, then separates the visible manual Queue from Smart Autoplay recommendations.

### Clean manual Queue

- The visible Queue contains the current-song context plus songs the user explicitly adds with **Play Next** or **Add to Queue**.
- Smart Autoplay recommendations no longer bulk-fill the visible Queue with large random-looking lists.
- **Play Next** has priority, followed by manually queued songs in their intended order.
- Smart Autoplay continues only after intentional upcoming playback is exhausted.
- Legacy Smart-Autoplay lookahead is not restored as a visible manual Queue.

### Hidden Smart Autoplay lane

- Smart Autoplay remains enabled as a recommendation feature; it is not removed.
- Its candidate pool stays internal and is capped to a small hidden set.
- Only the nearest likely next recommendation is passively prewarmed.
- Only the immediate autoplay-next track enters the playback timeline when it is actually needed.
- Long autoplay sessions keep bounded playback history instead of accumulating 50/100+ upcoming items.

### Playback and lyrics preserved

- v1.12.11 instant Home/Search playback and responsive Next/Previous behavior remain in place.
- v1.12.12 large responsive artwork and fast seek remain in place.
- v1.12.13 fast synced-lyrics acceptance and vocal-entry timing remain in place.
- Lyrics, Word-by-Word, Romanized Lyrics, Floating Lyrics, Exact Lyrics, Downloads, artwork quality, themes, Premium Home/Search and long-press actions remain protected.

### Release verification performed by the one-click runner

Before the public tag is created, the release script will:

1. Verify the exact tested source-file SHA-256 manifest.
2. Confirm package `com.deepecho.mobile`, version name `1.12.14` and version code `43`.
3. Run debug unit tests and build the signed release APK.
4. Verify the APK signature and permanent DeepEcho signing certificate.
5. Verify APK package/version using Android build tools.
6. Push the Android source to the official Android repository.
7. Create tag `v1.12.14` only if that tag does not already exist.
8. Wait for and verify the public GitHub Release APK and SHA-256 assets.

### Android package

- Package: `com.deepecho.mobile`
- Version name: `1.12.14`
- Version code: `43`
