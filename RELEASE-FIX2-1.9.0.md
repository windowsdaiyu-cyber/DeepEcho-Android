# DEEP-ECHO Mobile 1.9.0 — Release Script Fix 2

Fixed the `[7/9] sync_to_clone.ps1` interactive `Destination:` prompt.

Cause:
- The release BAT passed `%ROOT%` as a quoted command-line argument.
- `%ROOT%` ends in a trailing backslash.
- On some Windows PowerShell/cmd argument parsing paths, this can absorb/break the next named parameter.
- PowerShell therefore received `Source` but not `Destination`, and prompted interactively.

Fix:
- `sync_to_clone.ps1` now auto-detects the release source from its own `release-tools` location.
- It auto-targets `%USERPROFILE%\DeepEcho-Android-Local\release-work-1.9.0\repo`.
- The BAT calls it with no parameters.
- `wait_for_release.ps1` was also made self-defaulting so `[9/9]` cannot hit the same class of prompt.

No app source, package/version, signing configuration, playback, lyrics, downloads,
updater, queue, Smart Autoplay, themes, or other working feature was changed.
