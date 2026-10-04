# DEEP-ECHO Mobile 1.9.0 — Release Script Fix

Fixed the release verifier invocation.

Previous behavior:
- `verify_release_source.ps1` could be launched without a resolved value for `-Manifest`.
- PowerShell then prompted interactively with `Supply values for the following parameters: Manifest:`.

Fixed behavior:
- the verifier now auto-detects the project root from its own `release-tools` directory;
- it automatically loads `PROTECTED-SOURCE-SHA256-1.9.0.txt`;
- `RELEASE-ANDROID-1.9.0.bat` calls the verifier with no interactive parameters;
- the protected-source hash checks are unchanged.

No Android app code, version, package, playback, lyrics, updater, downloads, themes, or signing configuration was changed.
