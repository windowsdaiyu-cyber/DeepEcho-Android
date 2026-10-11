package com.deepecho.mobile.player

/**
 * v1.12.12 seek tuning constants kept separate so playback tests can lock the policy.
 * Startup/seek threshold intentionally stays at the already-proven v1.12.11 value.
 */
object PlaybackSeekPolicy {
    const val START_OR_SEEK_BUFFER_MS = 320
    const val BACK_BUFFER_MS = 20_000
    const val SEEK_NETWORK_QUIET_MS = 1_200L

    fun clampTarget(targetMs: Long, durationMs: Long): Long {
        val max = if (durationMs > 0L) durationMs else Long.MAX_VALUE
        return targetMs.coerceAtLeast(0L).coerceAtMost(max)
    }
}
