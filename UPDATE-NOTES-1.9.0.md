# UPDATE NOTES — 1.9.0

This release freezes the user-tested DEEP-ECHO Mobile 1.9.0 app code and adds release-only automation.

Release conversion changes only:
- clean release documentation;
- protected-source SHA-256 manifest;
- version-specific `RELEASE-ANDROID-1.9.0.bat`;
- tag-triggered GitHub Actions workflow;
- release helper scripts;
- updater-compatible APK/SHA256 publication flow.

No application source file under `app/src/main` was changed during release conversion.
`app/build.gradle.kts`, root Gradle configuration, package name, versionName and versionCode are unchanged from the tested 1.9.0 source.
