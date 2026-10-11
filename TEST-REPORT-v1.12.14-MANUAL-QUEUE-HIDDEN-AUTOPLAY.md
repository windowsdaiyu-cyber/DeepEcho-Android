# DeepEcho Android v1.12.14 — Manual Queue + Hidden Smart Autoplay

Validation completed before packaging:

- Queue architecture assertions: PASS
- Pure QueueVisibilityPolicy Kotlin compile/runtime: PASS
- Android XML parse: PASS (6 XML files)
- Modified Kotlin delimiter/syntax smoke: PASS
- Protected v1.12.13 files hash-identical: PlaybackService, PlaybackLookahead, PlaybackSeekPolicy, LyricsOrchestrator, LyricsRuntimeSync, FloatingLyricsService, FloatingLyricsSyncPolicy, YouTubeApi, SmartCache, TasteEngine, PlayerScreen, Screens, Theme, Downloads, YtDownloader.

Behavior implemented:
- Queue UI exposes only current track context plus manually queued upcoming tracks.
- Play Next / Add to Queue remain manual and take precedence.
- Smart Autoplay keeps max 4 hidden recommendation candidates and passively prewarms only the nearest 1.
- Smart Autoplay never bulk-appends its candidate list into Media3 / visible Queue.
- Only the immediate autoplay-next track enters Media3 when manual/playlist upcoming playback is exhausted.
- Smart autoplay playback history is bounded so long sessions do not grow into 100+ item queues.
- Legacy Smart Autoplay session lookahead is dropped on session restore rather than resurfacing as Queue.
- New queue persistence schema records manual origin going forward.

Actual Android Gradle/APK compilation must still be run on the established Windows Android SDK 35 setup using BUILD-TEST-APK-v1.12.14.bat.
