# v1.12.0 Major Utility Test Notes

## Non-regression baseline
Built directly on the v1.11.0 Ruby Live UI source. Existing player, fast playback path, Next/Previous, Smart Autoplay, lyrics, Word-by-Word, Romanized lyrics, Floating Lyrics, Ambient Mode/video, Ruby/Live themes, downloads, updater, notification/media session and song-only session restore are preserved rather than replaced.

## Implemented in this test source
1. Universal per-song 3-dot action sheet.
2. Start Radio, Play Next, Add Queue, Add Playlist, Share, Artist/Album discovery, Library toggle, Ambient, Lyrics, Queue clear, Download, Ringtone, Lyrics Export, Details, EQ, Advanced tempo/pitch, Block Artist.
3. Ringtone trim selector: maximum 30 seconds, preview, Android system-write permission flow, MediaStore ringtone registration. AAC/M4A, MP3, Opus/Vorbis containers are handled without re-encoding where compatible; unsupported local codecs report a clear error rather than corrupting audio.
4. Local Music MediaStore browser with Songs/Albums/Artists/Folders and include/exclude behavior.
5. Podcast search/follow/RSS episode playback with persisted episode position.
6. Wi-Fi/mobile streaming quality controls and Data Saver-aware artwork/prefetch/video behavior.
7. 0–5 second crossfade setting implemented as a safe fade-through transition; manual Next remains immediate.
8. Block Artist persistence and filtering for automatic discovery/autoplay surfaces plus marked direct search results.
9. Universal sharing with Song.link/Odesli lookup and fallback.
10. Settings Search.
11. TXT/LRC lyric export and Android share/save flows.
12. Listening Summary expansion.
13. Player/Audio existing behavior integrations retained: pause-on-mute, Bluetooth reconnect resume guard, skip silence, normalization, offload, preloading, duplicate prevention, history retention, etc.

## Explicitly excluded by latest user decision
- Google Cast
- Listen Together
- Song Recognition
- Classic Themes

## Known limitations in this test build
- Crossfade is a stability-first fade-out/fade-in transition, not a two-player overlapping DJ mix.
- Podcast discovery uses the public iTunes podcast search endpoint plus RSS feeds; feed metadata quality varies.
- Ringtone trimming remuxes/copies compatible encoded audio without quality loss. Some uncommon local codecs (for example certain FLAC/WAV configurations) need a future transcoder; DeepEcho refuses rather than creating a broken ringtone.
- Local music genre is omitted when MediaStore does not expose reliable genre metadata in the current lightweight index.
- View Artist/View Album from the universal action sheet routes through DeepEcho Search/discovery rather than replacing the existing artist/playlist navigation system.
- Automix remains disabled until a real beat-matched dual-player transition engine is safe enough not to regress playback.

## Test focus
Fresh install; upgrade from previous build; streaming/local/podcast playback; queue; Smart Autoplay; downloads; lyrics; Floating Lyrics; Ambient Mode; Ruby and every Live theme; song-session restore; ringtone flow; Local Music permissions; Settings Search; Block Artist; network quality/Data Saver; crossfade; Bluetooth resume; background playback; different screen sizes.
