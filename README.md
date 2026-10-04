# DEEP-ECHO Mobile 1.9.0 — Final Release Source

This folder is the release-converted form of the user-tested v1.9.0 Android app.

## Release
Double-click:

`RELEASE-ANDROID-1.9.0.bat`

Wait until the terminal shows:

`[9/9] DONE - RELEASE VERIFIED`

The BAT:
1. verifies the exact tested source;
2. confirms `v1.9.0` is unused;
3. requires the permanent Android signing key;
4. runs Gradle tests + signed release build;
5. verifies/prepares the APK + SHA256;
6. clones only `windowsdaiyu-cyber/DeepEcho-Android`;
7. commits/pushes `main` without force;
8. creates/pushes annotated tag `v1.9.0`;
9. waits for GitHub Actions to publish and verify the Release.

The Windows/PC DEEP-ECHO repository is not used.

## Future versions
For 1.9.1 and later:
- develop/test first;
- keep package `com.deepecho.mobile`;
- increment versionCode and versionName;
- reuse the same permanent signing key;
- convert the exact tested build into a new release;
- push a new unique `vX.Y.Z` tag.

Installed v1.9.0+ builds check the public Android GitHub Releases endpoint for newer versions.
