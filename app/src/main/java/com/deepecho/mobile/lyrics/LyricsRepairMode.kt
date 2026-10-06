package com.deepecho.mobile.lyrics

/**
 * v1.12.3 keeps text quality and synchronization as separate reliability domains.
 * A spelling/transcription repair must never retime already-good lyrics.
 */
enum class LyricsRepairMode {
    NONE,
    TEXT_REPAIR_ONLY,
    TIMING_REPAIR_ONLY,
    FULL_RECOVERY
}

object LyricsRepairPlanner {
    fun choose(textConfidence: Int, timingConfidence: Int, hasUsableText: Boolean): LyricsRepairMode = when {
        hasUsableText && textConfidence >= 72 && timingConfidence >= 72 -> LyricsRepairMode.NONE
        hasUsableText && textConfidence < 64 && timingConfidence >= 72 -> LyricsRepairMode.TEXT_REPAIR_ONLY
        hasUsableText && textConfidence >= 72 && timingConfidence < 64 -> LyricsRepairMode.TIMING_REPAIR_ONLY
        else -> LyricsRepairMode.FULL_RECOVERY
    }
}
