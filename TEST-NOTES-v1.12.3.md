# DEEP-ECHO Mobile v1.12.3 — Lyrics Recovery + Text Quality

Additive upgrade on top of v1.12.2 Universal Lyrics V2.

## Implemented
- Keeps the existing provider fast path unchanged for already-correct songs.
- Adds explicit NONE / TEXT_REPAIR_ONLY / TIMING_REPAIR_ONLY / FULL_RECOVERY planning.
- Wrong-provider rejection no longer jumps directly to unavailable when the exact upload exposes usable timed transcription/captions.
- Auto-generated captions from the exact playing upload are treated as transcription hypotheses, cleaned, aligned and cached.
- Adds conservative contextual text refinement using provider/reference text and same-track repetition consensus.
- No global m->n or n->m replacement and no song-specific Dubplate/General Levy/word hardcoding.
- TEXT_REPAIR_ONLY preserves line timestamps, word timestamps and manual offset behavior.
- LyricWord now has separate textConfidence and timingConfidence fields with backwards-compatible defaults.
- Retry still forces fresh provider/caption resolution and does not simply reuse an in-memory miss.

## Honest limitation
v1.12.3 does not bundle a Whisper/source-separation model. If the exact upload has no usable transcript/captions and providers fail, the app can still reach the honest unavailable state. The code does not fake a local transcription result.
