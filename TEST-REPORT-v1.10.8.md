# DEEP-ECHO Mobile v1.10.8 Static Validation Report

## Scope

This environment does not contain the Android SDK/Gradle dependency cache used by the Windows build workflow, so a full Android APK compile was **not** claimed here. The source was prepared for the existing Windows build script and validated statically before packaging.

## Static validation performed

- Kotlin delimiter/string/comment-aware structural scan over all app `.kt` files: PASS.
- All `Settings.*` references resolve to a field/function in the expanded Settings object: PASS.
- Requested Player and audio labels are present in the dedicated screen: PASS (35/35 requested controls).
- Ambient fullscreen wiring present: immersive system bars, landscape orientation, cover/video + lyrics split, Ambient exit switch.
- Native Ambient-video wiring present: direct stream resolution, muted Media3 video, current-position seek, play/pause following, drift correction, fresh retry.
- Artwork quality wiring present: high-resolution thumbnail scoring + max-resolution YouTube image attempt with fallback.
- Lyrics timing wiring present: duration-based conservative LRCLIB alignment + persisted per-song timing offset + Word-by-Word/Romanized Ambient rendering.
- Existing package id remains `com.deepecho.mobile`.
- Version updated to `1.10.8` / versionCode `26`.

## Real-device validation still required

Run `BUILD-TEST-APK-v1.10.8.bat` on the normal DEEP-ECHO Android Windows build environment. If Gradle reports a compiler error, send `deepecho-build.log`. If compilation succeeds, test Ambient rotation/video sync and the audio-effect/device-behavior switches on at least one physical phone before release.
