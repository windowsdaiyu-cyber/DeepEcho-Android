package com.deepecho.mobile.lyrics

/**
 * Internal rollout gates for the additive Universal Lyrics V2 pipeline.
 * These default to the stable stages that are implemented on-device today.
 * No flag changes playback; all stages are lyrics-only and may safely fail closed.
 */
object LyricsFeatureFlags {
    const val captionVerification = true
    const val dynamicAlignment = true
    const val runtimeVocalOnsetGuard = true
    const val captionRecovery = true
    const val contextualTextRepair = true
    const val uploadTranscriptionFallback = true
    const val manualVideoOcrRecovery = true
    // Free/open network fallbacks are queried only after the existing LRCLIB path fails.
    const val multiProviderFallback = true

    // Reserved for future model-backed stages. They are deliberately false until a real,
    // packaged implementation exists; the app never exposes fake transcription/fingerprinting.
    const val localTranscription = false
    const val acousticFingerprintRecovery = false
    const val mashupSourceSeparation = false
}
