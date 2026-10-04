# TEST REPORT — DEEP-ECHO Mobile 1.9.0 RELEASE

## User acceptance
PASS — the user confirmed that v1.9.0 is working and approved this exact version for release.

## Tested source freeze
PASS — release conversion preserves the exact tested app source. A SHA-256 manifest covers every file under `app/src/main` plus core Gradle/version files.

## Existing local build
PASS — the user’s previous release attempt progressed past the local APK build stage and reached Git repository synchronization, confirming the v1.9.0 source compiled on the user’s Android toolchain.

## Automated test coverage in repository
The source currently contains no dedicated Kotlin/JUnit test files. The release BAT therefore runs Gradle's `:app:testDebugUnitTest` task (which may report NO-SOURCE) and requires `:app:assembleRelease` to succeed before any Git push/tag.

## Release-gate checks enforced by RELEASE-ANDROID-1.9.0.bat
- package must be `com.deepecho.mobile`;
- versionName must be `1.9.0`;
- protected-source SHA-256 manifest must match;
- `v1.9.0` must not already exist remotely;
- permanent tested signing key must exist;
- Gradle unit-test task + signed release build must succeed;
- APK signature must verify;
- APK package/version are checked when `aapt.exe` is available;
- signed APK + `.sha256` are prepared;
- Android repository remote is hard-checked;
- no force-push is used;
- private keystore staging is blocked;
- GitHub Actions must publish and expose APK + SHA256 before `[9/9] DONE`.

## GitHub release state at packaging time
The Android repository is public and currently has no published GitHub Releases for `v1.9.0`. The existing main branch contains an older alpha-era Android project, so the release BAT clones that repository and replaces its working tree through a normal commit on `main` while preserving Git history. It does not force-push.
