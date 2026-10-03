# DeepEcho Android — automatic APK builds on GitHub

This project is intentionally separate from the Windows/PC DeepEcho application.
Do not place this workflow inside the Windows repository.

## One-time setup

1. Create a new GitHub repository named `DeepEcho-Android` (or another clearly Android-only name).
2. Upload/push the CONTENTS of this project folder to that repository root.
3. Open the repository's **Actions** tab.
4. Open **DeepEcho Android APK** and choose **Run workflow**.
5. When the workflow is green, open that run and download the artifact named:
   `DeepEcho-Android-v0.1.0-alpha-APK`
6. Extract the artifact ZIP. It contains the installable APK and its SHA-256 checksum.

## Automatic builds

A push to `main` or `master` that changes Android build/app files automatically builds a fresh debug APK.

## GitHub Releases

Push a tag such as:

```text
android-v0.1.0-alpha
```

The same workflow will build the APK and attach it to a GitHub Release automatically.

## PC safety

The workflow runs `ci/verify-android-repo.sh` before building. It refuses to proceed unless it finds the expected DeepEcho Android Gradle project identifiers, and it refuses a repository containing known Windows/Electron root entry files.

This workflow never downloads, modifies, packages, or publishes the Windows DeepEcho project.
