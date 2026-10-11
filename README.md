# DEEP-ECHO Mobile v1.12.14 — Manual Queue + Hidden Smart Autoplay Test

Built on the v1.12.13 Fast Lyrics + Vocal Sync line without replacing playback, lyrics, downloads, themes, Home/Search or artwork systems.

## What changed

- Visible Queue now contains only the current-song context plus songs explicitly added with **Play Next** or **Add to Queue**.
- Smart Autoplay recommendations use a separate hidden lane and never bulk-fill the visible Queue.
- Manual queue playback has priority. Smart Autoplay continues only after intentional upcoming playback is exhausted.
- Hidden autoplay is capped at 4 candidates and passively prewarms only the nearest 1 track.
- Only the immediate autoplay-next item is inserted into Media3 when it is actually needed.
- Long Smart Autoplay sessions keep a bounded Previous history instead of growing to 100+ media items.
- New session/persistent queue data records whether an item was manually queued.
- Legacy Smart-Autoplay session lookahead is not restored as a visible queue.

## Build

From the extracted project root, run:

`BUILD-TEST-APK-v1.12.14.bat`

Expected APK:

`DEEP-ECHO-Mobile-v1.12.14-MANUAL-QUEUE-HIDDEN-AUTOPLAY-TEST.apk`

## Test first

1. Play from Home/Search with Smart Autoplay ON and open Queue — random autoplay songs must not appear.
2. Use Play Next and Add to Queue — only those manually added songs should appear as upcoming.
3. Manual songs must play before Smart Autoplay continues.
4. Retest Home/Search tap-to-play, rapid Next/Previous, seek, lyrics, Floating Lyrics, Downloads, themes and playlists.
