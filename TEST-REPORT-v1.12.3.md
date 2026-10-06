# v1.12.3 Test Report

## PASS
- Version wiring: versionCode 32 / versionName 1.12.3.
- New lyrics modules compile in an isolated Kotlin type/syntax smoke harness.
- Text-repair smoke: repeated same-track `yahan` evidence corrects low-confidence `yaham` while preserving line timing.
- Repair planner smoke: text-only, timing-only, no-repair and full-recovery paths classify independently.
- Orchestrator compiles in a Kotlin type/syntax harness with dependency stubs.
- No song-specific General Levy/Dubplate conditional or global m->n replacement in the lyrics modules.
- Protected playback/download/updater/core Store/SongActions files are byte-identical to v1.12.2.
- v1.12.2 Universal Lyrics regression tests remain included.
- New focused v1.12.3 tests added for timestamp-preserving text repair, genuine final-M preservation, repair-mode separation, provider-text authority and upload-transcription recovery.

## Environment limitation
The sandbox has Kotlin but no Android SDK/Gradle Android toolchain, so a full `assembleDebug`/APK build cannot be executed here. The included Windows BAT performs the real Android build on the user's Android SDK setup.

## Honest scope
No fake local Whisper/source-separation implementation was added. Recovery can use exact-upload creator captions or auto-generated upload transcription when available. If neither providers nor exact-upload transcript evidence exists, the app still returns an honest unavailable state rather than inventing lyrics.
