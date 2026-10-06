# DEEP-ECHO Mobile v1.12.6 — Multi-Provider Lyrics + Text Repair Test Notes

## Goal
Increase lyrics retrieval coverage without replacing or weakening the existing DeepEcho timing/synchronization system.

## Retrieval order / behavior
- Existing DeepEcho LRCLIB exact fast path remains first and unchanged.
- Existing LRCLIB search remains second and unchanged.
- Only after both current LRCLIB paths fail, the new open-provider fallback layer runs.
- Fallback tier 1: KuGou + QQ through Better Lyrics provider-specific endpoints.
- If KuGou + QQ independently agree strongly on a normal track, the fallback stage stops early.
- Fallback tier 2 (only when needed): Better Lyrics TTML cache-readable results + Unison public corpus.
- Existing YouTube caption verification/transcription remains the audio-grounding path.
- Existing Lyrics.ovh plain-text fallback remains available.
- Existing Exact Lyrics / Retry video-OCR recovery remains manual-only.

## Provider safety
Every provider response is normalized into DeepEcho's existing Lyrics/LyricLine/LyricWord model and ranked with existing title/artist/duration/version matching. Provider-specific timing is never allowed to bypass cover/version verification rules.

This build deliberately does NOT embed page scraping/private or unclear upstream access for Genius, Musixmatch, SimpMusic hidden endpoints, or community mirrors whose availability/rights are not stable enough for a default production dependency. The adapter architecture remains extensible if a stable permitted endpoint becomes available.

## Cover handling
- No separate cover provider exists.
- Exact cover performer is preferred when metadata exposes one.
- Original-song text may be used only as a text reference when it agrees with the actual cover transcription/captions.
- Original-song timestamps are never accepted as final cover timing.
- Cover/provider timelines require actual-audio/caption grounding and existing DeepEcho alignment.
- If cover words/sections differ, existing transcription/Exact Lyrics recovery is used instead of forcing original lyrics.

## Romanized M/N text repair
The existing LyricsTextRefiner is preserved. New verified provider text can now act as a stronger text authority for contextual repairs (e.g. M/N nasal confusions) while line and word timestamps remain unchanged in TEXT_REPAIR_ONLY mode.

## Performance
- No extra provider traffic on a strong current-provider hit.
- Fallback queries are bounded and tiered.
- KuGou/QQ tier runs first; secondary sources run only if the first tier does not establish a strong normal-track consensus.
- Per-provider calls are bounded; fallback stage is bounded.
- Existing per-track single-flight orchestration/cache remains in place.

## Local validation in build environment
- Kotlin provider-adapter compile smoke: PASS.
- Kotlin orchestrator compile smoke: PASS.
- New unit-test source compile smoke: PASS.
- Combined lyrics/UI regression harness: 30 PASS / 0 FAIL.
- Baseline protected-source audit: 56/60 existing app-main files byte-identical; only 4 intended existing lyrics files changed, plus 2 new lyrics files.
- No playback, downloads, queue, Smart Autoplay, PlayerScreen, Ambient, Floating Lyrics, current alignment engine, current runtime sync, CaptionResolver, or LyricsTextRefiner production file was modified by this feature.

## Final Android gate
The package builder now runs both:
- :app:testDebugUnitTest
- :app:assembleDebug

A real Windows/Android SDK build and phone/emulator test remains the final release gate.
