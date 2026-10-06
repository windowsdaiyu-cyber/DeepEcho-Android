package com.deepecho.mobile.lyrics

import com.deepecho.mobile.net.Lyrics

/**
 * In-process handoff for the lyrics that the main Lyrics UI has already resolved.
 * Floating Lyrics is a mirror of that resolved payload, never a competing lyrics engine.
 *
 * The bridge deliberately stores only the most recently published track so it cannot leak
 * lyrics between songs and does not become a second cache. Provider/cache ownership remains
 * with the existing lyrics architecture.
 */
object LyricsPresentationBridge {
    private data class Snapshot(val songKey: String, val lyrics: Lyrics)

    @Volatile private var snapshot: Snapshot? = null

    fun publish(songKey: String, lyrics: Lyrics) {
        snapshot = Snapshot(songKey, lyrics)
    }

    fun current(songKey: String): Lyrics? =
        snapshot?.takeIf { it.songKey == songKey }?.lyrics

    /** Lightweight read-only lyric line lookup for mini-player/notification/share surfaces. */
    fun lineAt(songKey: String, positionMs: Long): String? {
        val lyrics = current(songKey) ?: return null
        val synced = lyrics.synced
        if (synced.isEmpty()) return null
        val pos = positionMs.coerceAtLeast(0L)
        val line = synced.lastOrNull { it.timeMs <= pos } ?: return null
        return line.text.trim().takeIf { it.isNotBlank() && it != "♪" }
    }

    fun nextLineAfter(songKey: String, positionMs: Long): String? {
        val lyrics = current(songKey) ?: return null
        return lyrics.synced.firstOrNull { it.timeMs > positionMs.coerceAtLeast(0L) }
            ?.text?.trim()?.takeIf { it.isNotBlank() && it != "♪" }
    }

    fun clear(songKey: String) {
        if (snapshot?.songKey == songKey) snapshot = null
    }
}
