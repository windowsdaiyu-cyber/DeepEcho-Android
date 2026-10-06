package com.deepecho.mobile.lyrics

import com.deepecho.mobile.BuildConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.ArrayDeque

/** Debug-only, metadata-safe diagnostics. Raw lyrics/audio are intentionally not logged. */
object LyricsDiagnostics {
    private val events = ConcurrentHashMap<String, ArrayDeque<String>>()

    fun add(trackKey: String, event: String) {
        if (!BuildConfig.DEBUG) return
        val q = events.getOrPut(trackKey) { ArrayDeque() }
        synchronized(q) {
            q.addLast("${System.currentTimeMillis()}: $event")
            while (q.size > 50) q.removeFirst()
        }
    }

    fun snapshot(trackKey: String): List<String> {
        if (!BuildConfig.DEBUG) return emptyList()
        val q = events[trackKey] ?: return emptyList()
        return synchronized(q) { q.toList() }
    }
}
