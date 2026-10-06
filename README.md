# DEEP-ECHO Mobile v1.12.8 — Visible UX + Player Experience

This release is built directly on the successfully released v1.12.7 Instant Start + Rapid Skip baseline. The existing playback, lyrics, downloads, queue, Smart Autoplay, MediaSession and theme architecture are preserved and the v1.12.8 features are integrated on top.

## v1.12.8 highlights

- Dynamic Android lock-screen / media-session presentation.
- Quick Settings tiles for Play/Pause, Open DeepEcho, Floating Lyrics and Smart Autoplay.
- Home Mood Selector integrated as an additive recommendation context.
- Personalized Daily Mixes / Your Mixes.
- Five player presentation modes: Artwork, Lyrics, Visualizer, Ambient and Minimal.
- Optional player gestures with protected seek/lyrics/system-gesture regions.
- Theme-aware Android song share-card generator using the native share sheet.
- Expanded per-song Quick Actions with More Like This / Less Like This.
- Artwork-derived secondary player accents with cached palette extraction.
- Optional synced current-lyric line in the mini player.
- Listening Session cards using local listening data.
- Optional line-level lyrics in the expanded media notification where Android permits.
- Continue Your Vibe to restore a previous listening context without blindly replaying the old queue.

## Protected v1.12.7 behavior

The v1.12.7 instant-start / rapid-skip line remains the performance baseline. Playback stays higher priority than artwork, lyrics presentation, Home decoration and recommendation refresh work. Existing lyrics retrieval/sync/OCR logic is not replaced by the new lyrics surfaces.

## Build/test on Windows

Run the root-level file:

`BUILD-TEST-APK-v1.12.8.bat`

It uses the same permanent DeepEcho Android signing key path established by previous releases and produces:

`DEEP-ECHO-Mobile-v1.12.8-VISIBLE-UX-PLAYER-TEST.apk`

## One-click public release

After emulator/phone testing, run:

`RELEASE-v1.12.8.bat`

The release runner verifies the exact source manifest, checks that tag `v1.12.8` does not already exist, runs unit tests + a signed release build, verifies package/version/signing certificate, syncs the Android GitHub repository, pushes tag `v1.12.8`, and waits for the public GitHub Release APK + SHA256 assets.

The runner never force-overwrites an existing tag and does not touch the PC/Windows DeepEcho repository.

## Android package

- Package: `com.deepecho.mobile`
- Version name: `1.12.8`
- Version code: `37`
