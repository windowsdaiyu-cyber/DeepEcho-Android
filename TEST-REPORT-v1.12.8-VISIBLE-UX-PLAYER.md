# DeepEcho Mobile v1.12.8 — Build/Test Report

## Verified Windows debug build

The v1.12.8 golden-baseline source completed the user's Windows Gradle build successfully:

- `:app:compileDebugKotlin` completed / was up-to-date.
- `:app:testDebugUnitTest` completed / was up-to-date.
- `:app:packageDebug` completed.
- `:app:assembleDebug` completed.
- Gradle result: `BUILD SUCCESSFUL`.
- 44 actionable tasks were reported (2 executed, 42 up-to-date).

The earlier terminal `BUILD FAILED` footer was a builder control-flow issue after a successful diagnostic retry, not a Gradle build failure. `build_apk.bat` in this release package now treats a successful diagnostic retry as success and continues to APK verification/copy.

## Release-time validation

`RELEASE-v1.12.8.bat` performs an additional clean release gate on the user's release machine: exact-source hash verification, debug unit tests, signed `assembleRelease`, `apksigner` verification, package/version inspection, permanent-key certificate comparison, safe Git sync/tag creation and public release asset verification.
