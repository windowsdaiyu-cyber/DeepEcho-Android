# DEEP-ECHO Mobile v1.10.0 FEATURE TEST REPORT

## Feature wiring checks
- PASS — Ambient Mode quick toggle is beside Repeat in Now Playing.
- PASS — theme-specific Ambient visuals for Golden, Violet, Ocean, Rose, Emerald and AMOLED.
- PASS — Live Theme background uses full-screen moving particle fields plus a foreground readability guard.
- PASS — Listening Stats persistence and Library > Stats UI.
- PASS — Search chips: All / Songs / Videos / Albums / Artists.
- PASS — Top Result and per-result Song / Video / Album / Playlist / Artist labels.
- PASS — dedicated YouTube Music song, video, album and artist search buckets with safe fallbacks.
- PASS — lyrics resolver now uses normalized/scored LRCLIB candidates plus Lyrics.ovh plain-text fallback.
- PASS — enhanced-LRC word timestamps are preserved for Word by Word; line-level weighting remains as fallback.
- PASS — Romanized display remains available without overwriting original lyrics.
- PASS — Home performance pass removes the duplicate app-level 250 ms player ticker, reduces scroll-time prewarming and caches shelf results.

## Protected core checks
The following v1.9.1 files are byte-for-byte unchanged:
- `PlaybackService.kt` — `0da58cf50666b1f6388fdc307c9e030ae3c6e1cd2f0f22510ffdf38d472c7be5`
- `Downloads.kt` — `68c1bd421434362961db5e3c6666aa1416fea72f1f59ee74080eaea69a995381`
- `YtDownloader.kt` — `6da3977fae8e82a8d75c4455be9803c8e82965e032430e94f57871b12eb9c6da`
- `FloatingLyricsService.kt` — `0ddfadcf5495e1cc28d17184b1206bc313d462a9a3368b5e9e6044290b70ccaa`

## Static validation
- PASS — Kotlin delimiter scan across 29 Kotlin files.
- PASS — 13 feature-wiring assertions.
- PASS — Kotlin frontend scan found no parser-style `expecting`, unclosed-string/comment, or syntax errors.

## Real Android build limitation
This container does not contain an Android SDK/Gradle dependency cache, so it cannot perform the final Android Gradle APK compile. Run `BUILD-TEST-APK-v1.10.0.bat` on the existing DeepEcho Android development PC; the builder retains the permanent v1.9.0 signing key so the test APK can install over the stable line.

Do **not** publish this as a GitHub release until the resulting APK passes the real-device regression checklist in `TEST-NOTES-v1.10.0.md`.
