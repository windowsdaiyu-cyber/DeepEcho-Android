package com.deepecho.mobile.lyrics

/**
 * Floating Lyrics may enable Android Visualizer capture for its animation. That visual-only
 * side effect must never switch the main lyric timeline into a different runtime timing mode.
 *
 * When the overlay is active, both the main Lyrics UI and Floating Lyrics use the already
 * resolved lyric timestamps + the existing manual offset as their shared timing authority.
 */
object FloatingLyricsSyncPolicy {
    fun timingCaptureEnabled(overlayEnabled: Boolean, realCapture: Boolean): Boolean =
        realCapture && !overlayEnabled
}
