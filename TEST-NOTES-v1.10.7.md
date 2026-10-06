# DEEP-ECHO Mobile v1.10.7 — Real-device test notes

1. Search: type `nahi m`, `arijit`, `week`, or another partial query. Suggestions should look like real YouTube autocomplete phrases, not a fixed list of `songs / official song / album / artist / playlist` suffixes.
2. Search: type quickly and verify older suggestion requests do not replace the newest query.
3. Search: tap a suggestion and verify categorized Top Result / Songs / Videos / Albums / Artists search still works.
4. Ambient Mode: turn Ambient ON, tap Play Video, and verify no YouTube WebView / Error 153 card appears.
5. Ambient Mode: start the song around the middle, then enable video. Video should begin close to the current audio position.
6. Ambient Mode: seek the song forward/backward and verify the muted video re-syncs within about one second.
7. Ambient Mode: pause/resume the song and verify video follows pause/resume while lyrics continue to use the normal synced lyrics clock.
8. Regression: Home, Recently Played Top 10, Speed Dial, Live Performances, normal Lyrics, Floating Lyrics, Like/Download, Queue, Downloads, and updater must still work.
