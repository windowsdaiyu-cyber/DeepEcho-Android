DEEP-ECHO Mobile v1.12.9 - PREMIUM HOME UI TEST SOURCE

BASELINE
- Built from the successfully compiling v1.12.8 golden baseline.
- Version: 1.12.9
- Version code: 38

WHAT CHANGED
- Home-only visual redesign inspired by the supplied reference screenshots.
- Existing Home sections, taste signals, recommendation queries and playback behavior are preserved.
- Premium DeepEcho header, compact Search/Settings actions and Smart Autoplay pill.
- Mood selector moved into a clean horizontal premium chip row.
- Recently Played now has a large hero card plus a Keep Listening row.
- Song shelves use larger artwork-first cards with clean title/artist text.
- Home playlist cards are image-first and no longer carry a separate Open button.
- Daily Mix cards are artwork-first with an optional direct Play control.
- Home mini-player and bottom navigation receive a rounded floating treatment only on Home.
- Home song cards no longer show Queue/Download/Like/More buttons below artwork.
- LONG PRESS / HOLD on song artwork opens the existing Song Actions sheet containing Like/Unlike, Play Next, Queue, Download, Add to Playlist, Share, More/Less Like This and other supported actions.

ZERO-REGRESSION RULE
- Theme engine was not redesigned or replaced.
- Playback engine, PlayerClient, Downloads, YouTube resolver, lyrics engine, Floating Lyrics, Smart Autoplay/taste engine and cache architecture are not rewritten.
- Search and Library retain their existing UI behavior.

BUILD
1. Fresh-extract this ZIP.
2. At the ROOT of the extracted folder, double-click BUILD-TEST-APK-v1.12.9.bat.
3. Expected APK: DEEP-ECHO-Mobile-v1.12.9-PREMIUM-HOME-TEST.apk
