# DeepEcho Android v1.12.5 — Test Notes

## New behavior
- Only strong lyric/lyrics/lyrical-video titles expose `Read lyrics from video`.
- The feature is OFF by default and never starts heavy OCR on a normal song/video.
- When enabled on a lyric video, the current upload captions are preferred and can be displayed immediately on their native timeline.
- If captions are absent, on-device OCR samples the current playback area first and keeps a short look-ahead buffer so lines can appear progressively.
- Existing normal lyrics stay on screen until video-derived lyrics have at least a usable result; a failed video reader does not blank or replace the normal engine.
- Song change / switch-off cancels the optional live reader and prevents stale results.

## Non-regression rule
The v1.12.4 normal lyrics engine and Exact Lyrics / Retry pipeline are preserved. The new live reader is an additive opt-in path and does not enter `Lrclib.fetch()` / normal lookup.
