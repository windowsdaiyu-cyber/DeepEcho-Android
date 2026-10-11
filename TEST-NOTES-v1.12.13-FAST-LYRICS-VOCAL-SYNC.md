# v1.12.13 Fast Lyrics + Vocal Start Sync

Goals:
1. A strong provider result that Search already labels SYNCED LYRICS must not be rejected by a stricter player-only threshold.
2. Normal studio songs can use safe LRCLIB line timing immediately without waiting for caption verification.
3. Covers/live/remix/slowed/sped-up/explicit-video variants still require current-upload grounding.
4. Synced lyrics remain in waiting/instrumental state until the first meaningful provider lyric timestamp or verified vocal onset.
5. Playback files and v1.12.12 instant playback/fast seek tuning remain unchanged.
