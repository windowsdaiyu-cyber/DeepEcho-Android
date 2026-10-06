package com.deepecho.mobile.player

import android.content.ComponentCallbacks2
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import com.deepecho.mobile.data.Settings
import com.deepecho.mobile.data.Song
import com.deepecho.mobile.data.Store
import com.deepecho.mobile.net.Net
import com.deepecho.mobile.net.YouTubeApi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request

/**
 * v1.12.7 playback performance layer.
 *
 * It NEVER owns playback. PlaybackService/ExoPlayer remain the only playback authority.
 * This coordinator only gets future media technically ready before the player needs it:
 * Candidate -> Warm (source resolution requested) -> Ready (source resolved; nearest item may
 * receive a tiny network probe on unmetered connections). The existing YouTubeApi single-flight
 * cache is intentionally reused so a real Play/Next joins pre-warm work instead of resolving twice.
 */
object PlaybackLookahead {
    enum class Level { CANDIDATE, WARM, READY, LOCAL_READY, FAILED }

    data class Prepared(
        val songUrl: String,
        val level: Level,
        val generation: Long,
        val updatedAtMs: Long,
        val reason: String
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(2)
    private val generation = AtomicLong(0L)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val states = ConcurrentHashMap<String, Prepared>()
    private var appContext: Context? = null

    @Volatile private var lastNextTapAt = 0L
    @Volatile private var burstCount = 0
    @Volatile private var burstUntil = 0L
    @Volatile private var memoryPressureUntil = 0L

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun snapshot(url: String): Prepared? = states[url]

    /** Called on every manual Next from app/MediaController path. */
    fun noteManualNext(): Int {
        val now = SystemClock.elapsedRealtime()
        burstCount = if (now - lastNextTapAt <= 1_350L) (burstCount + 1).coerceAtMost(12) else 1
        lastNextTapAt = now
        if (burstCount >= 2) burstUntil = now + 2_000L
        return burstCount
    }

    fun inSkipBurst(): Boolean = SystemClock.elapsedRealtime() < burstUntil

    fun noteMemoryPressure(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            memoryPressureUntil = SystemClock.elapsedRealtime() + 60_000L
            // Keep the closest prepared work; stop farther speculative jobs immediately.
            val ready = states.entries
                .filter { it.value.level == Level.READY || it.value.level == Level.LOCAL_READY }
                .map { it.key }
                .take(2)
                .toSet()
            cancelWaitingWarmJobsExcept(ready)
        }
    }

    /**
     * Rebuild the conveyor belt around the CURRENT queue generation.
     * Old waiting jobs are cancelled; already-running extractor work is harmless because it can
     * only populate the shared stream cache and never mutates player/queue/UI state.
     */
    fun plan(current: Song?, upcoming: List<Song>, reason: String) {
        if (!Settings.preloadNextSong.value) {
            cancelSpeculative(emptySet())
            return
        }
        val gen = generation.incrementAndGet()
        val window = windowForCurrentConditions()
        val totalLimit = minOf(window.total, Settings.preloadLimit.value.coerceIn(1, 10))
        val desired = upcoming.take(totalLimit)
        val keep = mutableSetOf<String>()
        current?.let { keep.add(it.url) }
        desired.forEach { keep.add(it.url) }
        cancelSpeculative(keep)

        current?.let { song ->
            states[song.url] = Prepared(song.url, if (isLocal(song)) Level.LOCAL_READY else Level.CANDIDATE, gen, now(), "$reason/current")
        }

        desired.forEachIndexed { index, song ->
            val level = if (index < window.ready) Level.READY else Level.WARM
            states[song.url] = Prepared(song.url, Level.CANDIDATE, gen, now(), reason)
            schedule(song, level, gen, reason, index)
        }
    }

    /** Low-priority visible/Home/Search hint. Never brings lyrics/OCR/artwork work with it. */
    fun passiveWarm(songs: List<Song>, max: Int = 1, reason: String = "passive") {
        if (!Settings.preloadNextSong.value || songs.isEmpty()) return
        val gen = generation.get()
        songs.distinctBy { it.url }.take(max.coerceIn(0, 2)).forEachIndexed { index, song ->
            if (states[song.url]?.level in setOf(Level.WARM, Level.READY, Level.LOCAL_READY)) return@forEachIndexed
            schedule(song, Level.WARM, gen, reason, index + 3, passive = true)
        }
    }

    /**
     * A real user Play/Next has priority over speculative work. Cancel queued speculative jobs and
     * start source resolution immediately. PlaybackService still performs the authoritative resolve;
     * YouTubeApi single-flight makes both paths share the same extraction when they race.
     */
    fun promoteUserRequested(song: Song?, reason: String) {
        song ?: return
        val keep = setOf(song.url)
        cancelWaitingWarmJobsExcept(keep)
        if (isLocal(song)) {
            states[song.url] = Prepared(song.url, Level.LOCAL_READY, generation.get(), now(), reason)
            return
        }
        scope.launch {
            runCatching { YouTubeApi.resolve(song.url) }
                .onSuccess {
                    states[song.url] = Prepared(song.url, Level.READY, generation.get(), now(), reason)
                }
                .onFailure {
                    states[song.url] = Prepared(song.url, Level.FAILED, generation.get(), now(), reason)
                }
        }
    }

    fun invalidateQueue() {
        generation.incrementAndGet()
        cancelSpeculative(emptySet())
    }

    private fun schedule(
        song: Song,
        target: Level,
        gen: Long,
        reason: String,
        ordinal: Int,
        passive: Boolean = false
    ) {
        if (isLocal(song)) {
            states[song.url] = Prepared(song.url, Level.LOCAL_READY, gen, now(), reason)
            return
        }
        if (jobs[song.url]?.isActive == true) return
        jobs[song.url] = scope.launch {
            try {
                // READY starts first; WARM/passive are intentionally staggered so a real playback
                // request can take the network before speculation does.
                val baseDelay = when {
                    passive -> 650L
                    target == Level.READY -> (ordinal * 35L).coerceAtMost(120L)
                    else -> 180L + ((ordinal - 2).coerceAtLeast(0) * 80L).coerceAtMost(400L)
                }
                if (baseDelay > 0) delay(baseDelay)
                gate.withPermit {
                    val resolved = YouTubeApi.resolve(song.url)
                    if (gen != generation.get() && !passive) return@withPermit
                    states[song.url] = Prepared(song.url, target, gen, now(), reason)
                    if (target == Level.READY && ordinal == 0 && shouldProbeNetwork()) {
                        // Tiny bounded range probe. It warms DNS/TLS/CDN state without downloading
                        // whole tracks. Failure is ignored; source resolution is already useful.
                        runCatching { probeInitialBytes(resolved.url) }
                    }
                }
            } catch (_: Throwable) {
                if (gen == generation.get() || passive) {
                    states[song.url] = Prepared(song.url, Level.FAILED, gen, now(), reason)
                }
            } finally {
                jobs.remove(song.url)
            }
        }
    }

    private fun cancelSpeculative(keep: Set<String>) {
        for ((url, job) in jobs.entries.toList()) {
            if (url !in keep) {
                job.cancel()
                jobs.remove(url, job)
            }
        }
        for (url in states.keys.toList()) {
            if (url !in keep) states.remove(url)
        }
    }

    private fun cancelWaitingWarmJobsExcept(keep: Set<String>) {
        for ((url, job) in jobs.entries.toList()) {
            val level = states[url]?.level
            if (url !in keep && level != Level.READY && level != Level.LOCAL_READY) {
                job.cancel()
                jobs.remove(url, job)
            }
        }
    }

    private fun windowForCurrentConditions(): PlaybackLookaheadPolicy.Window {
        val pressured = SystemClock.elapsedRealtime() < memoryPressureUntil || isThermallyConstrained()
        if (pressured) {
            return if (inSkipBurst()) PlaybackLookaheadPolicy.Window(2, 2)
            else PlaybackLookaheadPolicy.Window(1, 2)
        }
        return PlaybackLookaheadPolicy.window(
            dataSaver = Settings.dataSaverMode.value,
            metered = isMetered(),
            burst = inSkipBurst()
        )
    }

    private fun isThermallyConstrained(): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val ctx = appContext ?: return false
        val pm = ctx.getSystemService(PowerManager::class.java) ?: return false
        return pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
    }

    private fun isLocal(song: Song): Boolean {
        if (Store.downloadPath(song.url) != null) return true
        val uri = runCatching { Uri.parse(song.url) }.getOrNull() ?: return false
        if (uri.scheme == "content" || uri.scheme == "file") return true
        val host = uri.host.orEmpty().lowercase()
        val youtube = host == "youtu.be" || host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com")
        return uri.scheme in setOf("http", "https") && !youtube
    }

    private fun isMetered(): Boolean {
        val ctx = appContext ?: return true
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val caps = cm.activeNetwork?.let(cm::getNetworkCapabilities) ?: return true
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun shouldProbeNetwork(): Boolean = !Settings.dataSaverMode.value && !isMetered()

    private fun probeInitialBytes(url: String) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", Net.UA)
            .header("Range", "bytes=0-32767")
            .build()
        Net.client.newCall(request).execute().use responseBlock@ { response ->
            if (!response.isSuccessful && response.code != 206) return@responseBlock
            val input = response.body?.byteStream() ?: return@responseBlock
            val buffer = ByteArray(8 * 1024)
            var remaining = 32 * 1024
            while (remaining > 0) {
                val n = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (n <= 0) break
                remaining -= n
            }
        }
    }

    private fun now(): Long = SystemClock.elapsedRealtime()
}
