# DeepEcho Android v1.12.6 - Lyrics Ready Search Tab

This update is additive. It does not replace the current Search, playback, lyrics, timing,
Word-by-Word, Romanized, Floating Lyrics, Exact Lyrics, Ambient, queue, download, theme, or
visualizer systems.

## Product behavior

Search now exposes:

`All | Songs | Lyrics | Videos | Albums | Artists`

The Lyrics tab means "DeepEcho already has a deliverable in-app lyrics path for this exact
result/version". A title/thumbnail containing the word "lyrics" is not enough.

Eligible sources are cache-first and may include a strongly matched current LRCLIB result,
a high-confidence existing fallback-provider result, captions from the actual upload, or a
previously cached Exact Lyrics/OCR result. Provider timing is not borrowed for cover/live/remix/
slowed/sped-up/music-video variants until the current upload grounds/re-aligns it.

No OCR, video-frame extraction, heavy transcription, or Exact Lyrics recovery is started by Search
or by opening the Lyrics tab. Exact Lyrics/Retry remains the manual recovery path.

When a user taps a Lyrics result, DeepEcho starts the normal player. The current lyrics provider
still gets first chance. If it later fails, the exact verified descriptor attached to that result
can supply its already-known fallback through the existing lyrics model/UI.

## Performance / async safety

- Cache-first.
- Only top/visible Search candidates are probed (max 12).
- At most two availability probes are started per batch.
- LRCLIB Search probe is bounded to 3 query forms / 6 candidates.
- Query edits cancel the prior probe job.
- Request generation + submitted query + live query are checked before publishing results.
- Switching tabs does not rerun provider discovery for the same indexed result.

## Manual phone checks

1. Search a normal popular song with current-provider lyrics -> it should appear in Lyrics.
2. Search a song where the current provider fails but an existing fallback succeeds -> Lyrics tab should populate.
3. Search a title containing "Lyrics" with no verified source -> it must not be falsely labeled Lyrics Ready.
4. Tap a Lyrics result -> normal DeepEcho player, normal Lyrics UI.
5. Test official audio and official video separately; keep them as distinct playback identities.
6. Test a cover; borrowed original timing must not be used directly.
7. Change query quickly while Lyrics availability is loading; old query results must never leak into the new query.
8. Confirm Exact Lyrics/OCR does not start just by searching/opening Lyrics tab.
9. Confirm Word-by-Word/Romanized/Floating Lyrics behave as before.
