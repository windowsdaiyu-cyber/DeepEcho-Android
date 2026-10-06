# DEEP-ECHO Mobile v1.10.9 Static Test Report

## Requested behavior
- Ambient video should stop micro-stuttering while remaining synced to the song.
- Remove Classic Theme category; Live Themes only.
- First-launch default is Golden Live.
- Remember selected theme.
- Restore only the last song/timestamp in PAUSED state; do not restore UI section/navigation.

## Validation performed
- 31 Kotlin source files: delimiter/string/comment structural scan PASS.
- `versionName = 1.10.9`, `versionCode = 27` PASS.
- Settings UI contains no `Classic Theme` section PASS.
- `liveTheme` defaults/forces TRUE and Golden remains default PASS.
- Song session key + timestamp/theme persistence present PASS.
- Fresh navigation uses non-saveable Home/Search/Library/Settings/player state PASS.
- Ambient video has 10s/35s buffering and soft playback-speed drift correction PASS.
- Hard video seek threshold raised to >4s and suppressed during buffering PASS.
- PlaybackService, Downloads, YouTubeApi and FloatingLyricsService are byte-identical to v1.10.8 PASS.

## Full build limitation
The execution sandbox does not contain Android SDK 35 / Gradle dependency caches, so a real Android compile cannot be honestly claimed here. `BUILD-TEST-APK-v1.10.9.bat` is included for the Windows build environment used for prior DeepEcho tests.
