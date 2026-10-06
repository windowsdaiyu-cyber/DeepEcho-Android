# DEEP-ECHO Mobile v1.12.7

## Instant Start + Rapid Skip Performance Upgrade

DeepEcho Android v1.12.7 focuses on making startup, restored-session playback, manual Next, and repeated rapid skipping feel dramatically faster while preserving the existing playback and lyrics architecture.

### Highlights

- Faster usable app startup with lightweight session restoration first.
- Restored current track is prepared in the background without unexpected autoplay.
- Multi-track lookahead pipeline with Candidate / WARM / READY preparation states.
- Default unmetered target of up to 3 READY + 4 WARM upcoming tracks, bounded by device/network conditions and preload settings.
- Rapid-skip burst handling for repeated Next presses with continuous lookahead refill.
- User Play/Next receives highest priority over lyrics, artwork, recommendations, statistics, and other background work.
- Existing YouTube stream-resolution cache/single-flight path is reused rather than duplicated.
- Stale speculative work is cancelled or deprioritized so skipped tracks cannot overwrite the final requested track.
- Playlist/manual Queue/Liked/Downloads/Local Music ordering remains intact.
- Local/downloaded tracks continue to use their direct local playback path.
- Data Saver, metered networks, memory pressure, and severe thermal conditions reduce speculative work automatically.
- Debug performance markers are available under `DeepEchoPerf` for startup and playback transition timing.

### Lyrics and UI continuity

This release carries forward the latest v1.12.6 work, including:

- Lyrics Ready Search tab.
- Multi-provider lyrics retrieval and contextual text repair.
- Compact Lyrics UI.
- Ambient Mode phone-layout and playback-control fixes.
- Floating Lyrics shared-timeline sync fix.

The existing lyrics timing/alignment engine remains protected; playback does not wait for lyrics work.

### Android package

- Package: `com.deepecho.mobile`
- Version name: `1.12.7`
- Version code: `36`
