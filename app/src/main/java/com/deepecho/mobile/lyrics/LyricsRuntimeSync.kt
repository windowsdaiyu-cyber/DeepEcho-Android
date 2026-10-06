package com.deepecho.mobile.lyrics

import com.deepecho.mobile.net.Lyrics
import java.util.concurrent.ConcurrentHashMap

/**
 * Runtime vocal-activity guard. It is intentionally conservative and only activates when Android's
 * real audio Visualizer capture is available. It never changes playback or provider data.
 */
object LyricsRuntimeSync {
    private data class State(
        var vocalHits: Int = 0,
        var quietHits: Int = 0,
        var observedFirstVocalMs: Long? = null,
        var onsetShiftMs: Long = 0L,
        var lastEffectiveMs: Long = 0L,
        var lastRawMs: Long = 0L
    )

    private val states = ConcurrentHashMap<String, State>()

    /**
     * Returns a timeline position for lyric highlighting. -1 means "upcoming, not sung yet".
     * Caption/audio-aligned results already carry corrected timestamps, so this guard mostly helps
     * provider-only fallbacks on instrumental intros/breaks.
     */
    fun effectivePosition(
        songKey: String,
        rawPositionMs: Long,
        lyrics: Lyrics,
        realCapture: Boolean,
        vocalLikelihood: Float
    ): Long {
        val raw = rawPositionMs.coerceAtLeast(0L)
        if (!LyricsFeatureFlags.runtimeVocalOnsetGuard) return raw
        val knownOnset = lyrics.firstVocalOnsetMs
        if (knownOnset != null && raw + 80L < knownOnset) return -1L

        // Caption/audio aligned timing already contains real timeline anchors. Don't double-shift.
        if (lyrics.verification in setOf("audio_aligned", "caption_generated") || !realCapture) return raw

        val state = states.getOrPut(songKey) { State() }
        if (raw + 2_000L < state.lastRawMs) {
            state.vocalHits = 0
            state.quietHits = 0
            state.lastEffectiveMs = raw
        }
        state.lastRawMs = raw

        val isVocal = vocalLikelihood >= 0.38f
        val veryQuiet = vocalLikelihood <= 0.16f
        val firstLyric = lyrics.synced.firstOrNull()?.timeMs ?: 0L

        if (isVocal) {
            state.vocalHits++
            state.quietHits = 0
            if (state.observedFirstVocalMs == null && state.vocalHits >= 3) {
                val onset = (raw - 500L).coerceAtLeast(0L)
                state.observedFirstVocalMs = onset
                // Runtime fallback gets one first-vocal anchor. Full V2 caption alignment uses
                // multiple anchors and remains preferred whenever available.
                state.onsetShiftMs = (onset - firstLyric).coerceIn(-20_000L, 90_000L)
            }
        } else {
            state.vocalHits = 0
            if (veryQuiet) state.quietHits++ else state.quietHits = (state.quietHits - 1).coerceAtLeast(0)
        }

        val observed = state.observedFirstVocalMs
        if (observed == null && raw >= firstLyric && raw < 75_000L) return -1L

        var effective = (raw - state.onsetShiftMs).coerceAtLeast(0L)
        // Sustained low-vocal region on a weak/unverified timeline freezes progress through likely
        // instrumental breaks. Verified caption timelines are excluded above.
        if (!lyrics.verified && state.quietHits >= 6 && state.lastEffectiveMs > 0L) {
            effective = state.lastEffectiveMs
        } else if (isVocal || state.quietHits < 6 || lyrics.verified) {
            state.lastEffectiveMs = effective
        }
        return effective
    }

    fun clear(songKey: String) { states.remove(songKey) }
}
