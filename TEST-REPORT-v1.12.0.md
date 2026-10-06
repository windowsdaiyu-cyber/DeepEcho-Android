# v1.12.0 Static Test Report

## Result in packaging environment
- Version wiring: PASS (`versionCode 29`, `versionName 1.12.0`).
- Kotlin parser/syntax scan: PASS (no parser-level `expecting`, unclosed-token or syntax errors detected).
- Cast / Listen Together visible source references: PASS (removed from current source/UI).
- Classic theme mode: PASS (remains retired; compatibility flag forces Live mode).
- New feature source wiring: PASS for Local Music, Podcasts, Ringtone, Block Artist, Settings Search, Lyrics Export, Data Saver quality split, Crossfade and universal song actions.
- Protected baseline comparison: additive/targeted edits only; Windows/PC DeepEcho project not touched.

## Full Android build
NOT executed in the packaging sandbox because Android SDK Platform 35 and the Android Gradle dependency cache are unavailable here. Run `BUILD-TEST-APK-v1.12.0.bat` on the existing Windows DeepEcho Android build machine. The BAT generates `deepecho-build.log` automatically on failure.

## Required real-device validation before release
This is a TEST source, not a public release. Verify ringtone system permission behavior, local MediaStore permissions on Android 13+, podcast RSS playback/download, crossfade, Bluetooth reconnect, background playback, update-compatible signing, and all existing DeepEcho player/lyrics/theme regressions before promoting it to a GitHub release.
