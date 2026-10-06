# DEEP-ECHO Mobile v1.10.6 Test Report

## Static validation
- app versionName/versionCode: 1.10.6 / 24
- Recognize Music code/token references removed from Android source: PASS
- Home wiring: large Recently played top-10 cover shelf, Speed dial, Live performances: PASS
- Ambient control: icon + switch only: PASS
- Ambient lyrics pane remains wired: PASS
- Synced video path: direct YouTube embed + start time + muted playback + seek/play/pause sync: PASS (static)
- Kotlin modified-file delimiter scan: PASS
- Kotlin parser syntax-token scan: 0 syntax-token errors

## Protected regression check versus v1.10.5
Byte-identical:
- PlaybackService.kt
- PlayerClient.kt
- Downloads.kt
- AppUpdater.kt
- Lrclib.kt
- YouTubeApi.kt
- FloatingLyricsService.kt

## Device test required
The current sandbox does not include the Android SDK/Gradle build environment used for the signed APK. Run BUILD-TEST-APK-v1.10.6.bat on the existing Windows Android build setup, then verify video playback on a real phone because YouTube WebView behavior can differ by Android System WebView version/device.
