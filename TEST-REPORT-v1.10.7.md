# DEEP-ECHO Mobile v1.10.7 Test Report

## Target
Fix the poor generated Search suggestions and the Ambient Mode YouTube player Error 153 seen on real Android devices.

## Implemented
- Replaced generated Search suffix suggestions with NewPipe's YouTube `SuggestionExtractor` results.
- Added 170 ms debounce, request cancellation, 5-minute suggestion cache, and local DeepEcho history/taste fallback.
- Removed the WebView/YouTube iframe path from Ambient video.
- Added direct video-stream resolution through NewPipe, including video-only streams because Ambient video is muted.
- Added Media3 ExoPlayer rendering using the existing song position as the master clock.
- Added ~850 ms drift correction, pause/resume following, and retry behavior.
- Added `media3-ui` dependency for the native video surface.

## Regression protection
Byte-for-byte unchanged from v1.10.6:
- `PlaybackService.kt`
- `PlayerClient.kt`
- `Downloads.kt`
- `Lrclib.kt`
- `FloatingLyricsService.kt`

The existing audio `YouTubeApi.resolve(...)` implementation was not functionally changed; v1.10.7 only adds suggestion/video helpers around it.

## Static validation
- Kotlin delimiter scan: PASS (30 Kotlin files)
- AndroidManifest XML parse: PASS
- versionName/versionCode: 1.10.7 / 25 PASS
- Search suggestion wiring: PASS
- Fake generated suggestion templates removed: PASS
- Direct Ambient video resolver: PASS
- video-only fallback: PASS
- ExoPlayer Ambient renderer: PASS
- WebView Ambient player removed: PASS
- Current-position sync wiring: PASS

## Real-device requirement
This sandbox does not include the full Android SDK/Gradle dependency cache required to produce and run the APK here. Build with `BUILD-TEST-APK-v1.10.7.bat` on the existing DeepEcho Windows Android setup and verify the checklist in `TEST-NOTES-v1.10.7.md` before release.
