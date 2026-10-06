package com.deepecho.mobile.player

import android.os.SystemClock
import android.util.Log
import com.deepecho.mobile.BuildConfig
import java.util.concurrent.ConcurrentHashMap

/** Debug-only performance markers; zero production UI and no playback decisions. */
object PlaybackPerfMetrics {
    private const val TAG = "DeepEchoPerf"
    private val marks = ConcurrentHashMap<String, Long>()
    private val pendingTracks = ConcurrentHashMap<String, Pair<String, Long>>()

    fun mark(name: String) {
        if (!BuildConfig.DEBUG) return
        marks[name] = SystemClock.elapsedRealtime()
        Log.d(TAG, name)
    }

    fun latency(from: String, to: String) {
        if (!BuildConfig.DEBUG) return
        val start = marks[from] ?: return
        val end = SystemClock.elapsedRealtime()
        marks[to] = end
        Log.d(TAG, "$from -> $to = ${end - start} ms")
    }

    fun beginTrackAction(kind: String, id: String?) {
        if (!BuildConfig.DEBUG || id.isNullOrBlank()) return
        if (pendingTracks.size > 20) pendingTracks.clear()
        pendingTracks[id] = kind to SystemClock.elapsedRealtime()
        Log.d(TAG, "$kind target=${id.takeLast(18)}")
    }

    fun audioReady(id: String?) {
        if (!BuildConfig.DEBUG || id.isNullOrBlank()) return
        val pending = pendingTracks.remove(id) ?: return
        val elapsed = SystemClock.elapsedRealtime() - pending.second
        Log.d(TAG, "${pending.first} -> first READY audio [${id.takeLast(18)}] = $elapsed ms")
    }
}
