# DeepEcho Android v1.12.4 — Test Notes

## Purpose
Adds a manually-triggered last-resort lyric-video/caption/OCR recovery path behind Exact Lyrics / Retry. It does not enter the normal lyrics lookup path.

## Required behavior
1. Normal successful lyrics continue through the existing v1.12.3 engine only.
2. Before Exact Lyrics / Retry is pressed, no lyric-video search, video resolve, frame extraction, or OCR is started by v1.12.4.
3. Manual retry first re-runs the existing recovery path. A reliable result stops the new pipeline.
4. If still unavailable, lyric-oriented candidate videos are ranked; karaoke/covers/reactions/translations are rejected.
5. Candidate captions are preferred over OCR.
6. OCR samples frames adaptively, skips unchanged visual signatures, caps OCR work, cleans duplicates and stationary corner watermarks.
7. Reference timestamps are never blindly copied to the playing track.
8. Playing-upload captions are used for piecewise alignment where available; otherwise decoded-audio envelope/fingerprint alignment maps the reference to the actual playing audio.
9. Low-confidence alignment is rejected.
10. Word timestamps are not fabricated from OCR-only line timing.
11. Successful verified/aligned results use the existing variant-specific LyricsCache.
12. Song change/disposal cancels stale manual recovery; rapid requests share one in-flight video recovery job.

## Device tests to run from the included builder
- normal provider lyrics
- synced lyrics
- Word by Word
- Romanized
- manual offset/sync controls
- Floating Lyrics
- unavailable-lyrics track before Retry (must stay idle)
- unavailable-lyrics track after Retry
- lyric video with captions
- lyric video with visible lyrics but no captions
- official/current video with no visible lyrics
- song change during recovery
- cache reuse after restart
- Play/Pause/Seek/Next during recovery
- battery/CPU sanity during OCR
