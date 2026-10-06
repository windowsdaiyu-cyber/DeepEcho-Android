# DEEP-ECHO Mobile 1.12.6

DEEP-ECHO Mobile 1.12.6 expands in-app lyrics coverage and adds a Lyrics-ready Search category while preserving the existing playback, lyrics synchronization, and timing architecture.

## Highlights
- Adds a new **Lyrics** Search tab alongside the existing Search categories.
- Lyrics Search shows results only when DeepEcho has a verified, deliverable in-app lyrics path; a title merely containing “lyrics” is not enough.
- Lyrics-ready results play through the normal DeepEcho player and render through the existing Live Lyrics / Lyrics UI, including Word-by-Word, Romanized mode, and Floating Lyrics when available.
- Search availability checks are lightweight and cache-first. Search does not run OCR or heavy transcription.
- If the primary lyrics provider unexpectedly fails after a Lyrics-ready result is selected, the verified attached fallback can feed the existing DeepEcho lyrics model/UI.
- Current provider/LRCLIB remains the first fast path. Multi-provider fallbacks are queried only when needed.
- Multi-provider candidate normalization, metadata cleanup, wrong-song rejection, multilingual handling, and version-aware matching remain included.
- Cover-song text may use a verified original/reference text only when appropriate; original timestamps are never reused for a cover. Timing follows the actual playing audio through DeepEcho's existing alignment system.
- Context-aware Romanized M/N text repair remains text-only when timing is already correct.
- Exact Lyrics / Retry and video OCR remain manual-only after normal lyrics retrieval fails. Opening Search or the Lyrics tab never starts OCR.
- Previously verified/cached Exact Lyrics or OCR results can make that exact track/version Lyrics-ready on future searches without rerunning OCR.
- Compact phone Lyrics UI remains included so more lyric lines fit on screen.
- Ambient Mode phone layout fixes, explicit user-open behavior, and compact Previous / -10s / Play-Pause / +10s / Next controls remain included.
- Existing playback, fast Play, Next/Previous, queue, Smart Autoplay, Home, normal Search tabs, downloads, local music, themes, background playback, notifications/media controls, and updater flow are preserved.

## Update compatibility
Package: `com.deepecho.mobile`

Version: `1.12.6` (`versionCode 35`)

Release tag: `v1.12.6`

The release APK must be signed with the same permanent Android signing key used by the existing DeepEcho Android update chain.
