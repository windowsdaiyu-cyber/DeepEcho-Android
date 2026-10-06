# TEST REPORT — DEEP-ECHO Mobile v1.12.2 Universal Lyrics V2

## Static / focused validation
- Version wiring: PASS — versionCode 31, versionName 1.12.2.
- Pure Kotlin lyrics regression harness: PASS.
  - Rap God-style exact metadata stays strong/non-suspicious.
  - General Levy / generic Dubplate metadata is treated as suspicious.
  - Wrong Dubplate text is rejected by caption/text agreement.
  - Instrumental-intro fixture moves first lyric from 5.0s to ~13.8s.
  - Piecewise alignment handles an inserted instrumental gap without one global offset.
  - Correct timing remains near the provider timestamps.
- LyricsRuntimeSync isolated Kotlin compile: PASS.
- CaptionResolver syntax/API-shape compile against local stubs: PASS.
- Feature wiring checks: PASS — orchestrator, metadata verifier, caption verifier, dynamic alignment, vocal onset, cache, diagnostics, recovery controls, unit test source.
- Protected core hash comparison vs v1.12.1: PASS — PlaybackService, Downloads, AppUpdater, YouTubeApi and Store unchanged.
- No signing key/local.properties packaged: PASS.

## Full Android build
Not executed in this packaging environment because Android SDK Platform 35 / the project Android Gradle environment is not installed here. `BUILD-TEST-APK-v1.12.2.bat` is the authoritative Windows compile/device test.

## Known limitation
This build does not claim bundled local ASR/Whisper, acoustic fingerprint matching, vocal source separation or multi-source mashup transcription. Those advanced stages are explicitly disabled rather than mocked. Actual recovery uses existing providers plus actual-upload caption verification where available, multi-anchor alignment, and runtime audio onset/gap heuristics.
