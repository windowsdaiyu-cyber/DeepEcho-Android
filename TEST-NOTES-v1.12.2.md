# DEEP-ECHO Mobile v1.12.2 — Universal Lyrics V2 test build

This build is based on v1.12.1 and preserves playback, downloads, Smart Autoplay, Search/Home, Live themes/Ruby, Ambient Mode, Floating Lyrics, session restore, and the updater chain.

Implemented lyrics changes:
- Existing LRCLIB fast path remains first and can return immediately for strong exact metadata.
- Multi-factor metadata normalization/scoring distinguishes performer/uploader/variant and rejects generic-title collisions more aggressively.
- Actual-upload YouTube captions are used, when available, as a lightweight audio-grounded verification/timing source for ambiguous tracks.
- Wrong candidate rejection uses lyric/caption text agreement rather than fuzzy title similarity alone.
- Piecewise timeline repair uses multiple caption anchors instead of one global offset.
- First-vocal-onset is stored and used to prevent early highlighting during instrumental intros.
- Runtime Visualizer vocal-band likelihood provides a conservative onset/gap guard when no caption-aligned timeline is available.
- Exact Lyrics / Repair Sync actions force a fresh recovery pass.
- Verified/re-aligned results are cached per URL/title/artist/duration/engine version.
- Source labels distinguish Verified, Re-aligned, Captions, Metadata verified, and Unverified instead of displaying a misleading generic match percentage.
- Word-by-word is only used when real word timing exists or alignment confidence is high.

Important limitation:
- No bundled offline ASR/fingerprinting/source-separation model is included in this test build. The architecture has explicit feature flags for those future heavy stages, but they remain disabled rather than pretending to work. Recovery today uses providers, actual-upload captions when available, piecewise alignment, and runtime vocal-onset analysis.

Manual recovery UI now exposes: Exact Lyrics, Repair Lyrics, Repair Sync, Re-align, and Generate Missing. These all re-enter the staged recovery pipeline; no fake completed result is shown when verification cannot succeed.
