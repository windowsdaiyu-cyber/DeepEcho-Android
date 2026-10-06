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

    fun clear(songKey: String) {
        if (snapshot?.songKey == songKey) snapshot = null
    }
}
