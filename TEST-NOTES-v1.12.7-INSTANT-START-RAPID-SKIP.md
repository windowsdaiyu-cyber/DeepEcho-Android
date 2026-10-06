# v1.12.7 Test Notes — Instant Start / Rapid Skip

Primary manual acceptance sequence:

1. Play A with B C D E F G H in queue, pause, close according to normal app behavior.
2. Cold-launch DeepEcho.
3. Confirm Home/UI appears quickly and session player metadata restores without audible autoplay.
4. Press Play: restored A should start as quickly as the device/network permits and resume using existing position semantics.
5. Immediately press Next six times. Logical queue position must advance exactly six positions, without freeze/duplicate/reorder.
6. Repeat with playlist/manual Queue/Liked/Downloads/Local Music and Smart Autoplay.
7. Repeat Next from notification/lock screen/Bluetooth; the same pre-resolved queue should benefit those transitions.
8. While rapidly skipping, verify skipped tracks do not trigger visible stale artwork/lyrics and final track wins.
9. Verify Lyrics, Word-by-Word, Romanized, Floating Lyrics, Exact Lyrics, Lyrics Search tab and Ambient Mode remain correct.
10. Verify Data Saver reduces speculative work; Wi-Fi keeps a larger source lookahead.

Debug builds log performance markers under `DeepEchoPerf`, including app startup markers and user Play/Next to player READY timing.
