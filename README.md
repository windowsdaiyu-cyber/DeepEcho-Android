# DEEP-ECHO Android v0.1.0 Alpha

First native Android foundation for DeepEcho, built from the DeepEcho **v2.6.2** Windows release baseline.

## What works in this Alpha

- Native Kotlin + Jetpack Compose mobile UI.
- Media3 / ExoPlayer background playback foundation.
- MediaSession integration for Android media controls.
- Home, Search, Lyrics, Downloads and Settings screens.
- Sticky Mini Player with play/pause, seek bar and Lyrics shortcut.
- Synced line lyrics for tracks that provide timed lyric data.
- Floating Lyrics overlay permission flow and draggable overlay.
- Android DownloadManager path for resolved/direct audio URLs.
- Theme system with Golden Particle, Light, Dark, Molten Dragon, Purple Storm, Green Toxic, Red Blood, Silver, Transparent Glass, Fluids, RGB, Love RGB and Vibrant Space.
- Theme-reactive animated mobile backgrounds.
- Smart Autoplay setting foundation.
- No ads and no subscription code.
- Three direct-audio demo tracks so playback/UI can be tested even when the PC bridge is not running.

## Important Alpha architecture note

Mainstream catalog parity is tested through the included **local DeepEcho PC Bridge** in this first Alpha. This deliberately keeps the Windows v2.6.2 project unchanged while we validate Android UI, Media3 playback, latency, downloads and lyrics behavior.

The emulator default bridge URL is:

`http://10.0.2.2:17832`

`10.0.2.2` is the Android Emulator route back to the Windows host machine.

This is **not yet the final standalone Android catalog engine**. The production Android resolver will be moved on-device or to an approved DeepEcho service only after parity and reliability testing. Do not call this Alpha “99% catalog parity” until a real song-set parity test passes.

## Emulator test — recommended

1. Install **Android Studio Quail 4 (2026.1.4)** or newer compatible version.
2. Open this folder as an Android Studio project.
3. Install Android SDK / compile SDK **37** if Android Studio asks.
4. Create a normal Android phone emulator (API 35–37 is fine).
5. On Windows, make sure Node.js is available.
6. Run `bridge\Start-Bridge.bat`.
   - The bridge automatically checks common DeepEcho installation paths for `yt-dlp.exe`.
   - If needed, run: `Start-Bridge.bat "C:\full\path\to\yt-dlp.exe"`.
7. In Android Studio, Run `app` on the emulator.
8. Search a song. The app should show **LIVE** in the header when the bridge is connected.
9. Tap Play and test start latency, pause/resume, seek, background playback and notification controls.
10. Open Lyrics and grant “Display over other apps” when enabling Floating Lyrics.
11. Tap ⇩ on a resolved song to test Android DownloadManager.

## Build a debug APK

Run:

`Build-Debug-APK.bat`

The included lightweight Gradle bootstrap downloads Gradle 9.6.0 the first time. Android SDK components are still supplied by Android Studio / Android SDK Manager.

Expected output after a successful build:

`app\build\outputs\apk\debug\app-debug.apk`

## Real phone bridge test

Use `bridge\Start-Bridge-LAN.bat`, then set the Catalog Bridge URL in Android Settings to:

`http://YOUR-PC-LAN-IP:17832`

Keep Windows Firewall/LAN security in mind. The normal `Start-Bridge.bat` binds only to localhost and is the safer default for emulator testing.

## v0.1 limitations / next work

- Mainstream live Search/resolve currently depends on the local bridge.
- Album art is represented by lightweight themed placeholders in this Alpha; network artwork loading comes next.
- Word-by-word lyrics timing and Exact Lyrics rescue are not yet ported.
- Smart Autoplay toggle is present, but related-track queue generation is not yet wired.
- Download records persist and completed DownloadManager items can play back through their local `content://` URI. Full filesystem/library reconciliation and richer offline metadata scanning are still next-pass work.
- Floating Lyrics uses the current synced line; full theme-specific overlay animations come next.
- Full desktop recommendation/home shelves and playlist-open logic are not yet ported.

## Non-regression rule

The Windows DeepEcho v2.6.2 source remains untouched. Android development lives in this separate project. Future Android versions should preserve every working Android feature before adding new features, and Windows behavior must not be changed as a side effect of Android work.

## GitHub automatic APK build

This Android project includes `.github/workflows/android-apk.yml`. Put this project in a **separate Android-only GitHub repository** and GitHub Actions can produce an installable debug APK without Android Studio on your PC. See `GITHUB-AUTO-APK.md`.

The workflow includes an Android-repository safety guard and is not intended to be copied into the Windows DeepEcho repository.
