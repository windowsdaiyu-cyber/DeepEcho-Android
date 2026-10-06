package com.deepecho.mobile.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.deepecho.mobile.data.Settings
import com.deepecho.mobile.data.Podcasts
import com.deepecho.mobile.data.Song
import com.deepecho.mobile.data.Store
import com.deepecho.mobile.data.TasteEngine
import com.deepecho.mobile.data.ThemeAdvisor
import com.deepecho.mobile.net.Bus
import com.deepecho.mobile.net.Lrclib
import com.deepecho.mobile.net.SmartCache
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class QueueEntry(
    val index: Int,
    val song: Song,
    val reason: String,
    val current: Boolean
)

/** UI <-> PlaybackService bridge. Compose state yahin se aata hai. */
object PlayerClient {
    private var controller: MediaController? = null
    private val songs = HashMap<String, Song>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var smartJob: Job? = null
    private var stableBackgroundJob: Job? = null
    private var smartContext = false
    private var tickerStarted = false
    private val warmSet = ConcurrentHashMap.newKeySet<String>()
    private val queueReasons = ConcurrentHashMap<String, String>()
    private var sleepJob: Job? = null
    private var lastProgressiveSeekAt = 0L
    private var progressiveSeekLevel = 0
    private var progressiveSeekSign = 0
    private var lastSessionPersistAt = 0L
    private var restoredSessionMediaId: String? = null

    // Listening stats are accumulated in memory and flushed in coarse batches.
    // This avoids JSON/file writes on the 250 ms playback ticker and keeps Home scrolling smooth.
    private var statsLastTickAt = 0L
    private var statsSong: Song? = null
    private var statsAccumulatedMs = 0L

    var item by mutableStateOf<MediaItem?>(null)
    var isPlaying by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var shuffle by mutableStateOf(false)
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var volume by mutableFloatStateOf(1f)
    var queueRevision by mutableIntStateOf(0)
    var sleepUntilMs by mutableLongStateOf(0L)
    var currentSuggestedTheme by mutableStateOf<String?>(null)

    val currentSong: Song? get() = item?.mediaId?.let { songs[it] }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(player)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            flushListeningStats()
            mediaItem?.mediaId?.let { songs[it] }?.let { song ->
                queueRevision++
                val restored = restoredSessionMediaId == song.url
                if (restored) {
                    // Do not count a restored session as a new play and do not replace the
                    // user's saved Live Theme via Auto Theme.
                    restoredSessionMediaId = null
                } else {
                    Store.addHistory(song)
                    Store.recordPlayStart(song)
                    val suggestion = ThemeAdvisor.suggest(song, Settings.theme.value)
                    currentSuggestedTheme = suggestion
                    if (Settings.autoThemeWithSong.value) {
                        Settings.setTheme(suggestion)
                    }
                    if (smartContext) scheduleSmartQueue(song)
                }
                persistSongSession(force = true)
                controller?.let {
                    refreshLookahead(it, "media-transition")
                    scheduleStableBackgroundWork(it)
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && controller?.playWhenReady == true) {
                PlaybackPerfMetrics.audioReady(currentSong?.url)
            }
            if (playbackState == Player.STATE_ENDED && smartContext) {
                currentSong?.let { scheduleSmartQueue(it, force = true) }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Bus.toast("Play nahi ho paya: ${error.cause?.message ?: error.message ?: "error"}")
            if (Settings.autoSkipOnError.value) {
                val failedId = currentSong?.url
                scope.launch {
                    delay(1100) // PlaybackService gets one clean retry before we skip.
                    if (failedId != null && currentSong?.url == failedId) {
                        controller?.let { live ->
                            if (live.hasNextMediaItem()) advanceToNextAndPlay(live)
                        }
                    }
                }
            }
        }
    }

    fun connect(ctx: Context) {
        if (controller != null) return
        PlaybackLookahead.init(ctx)
        PlaybackPerfMetrics.mark("player_connect_start")
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(listener)
                // Default startup restore keeps the current song plus a bounded upcoming lookahead.
                // Persistent Queue still controls restoration of the full queue. Navigation state is
                // never restored and playback remains paused until the user chooses Play.
                if (c.mediaItemCount == 0) {
                    if (Settings.persistentQueue.value) restorePersistentQueue(c)
                    else restoreSongSession(c)
                }
                if (Settings.rememberShuffleRepeat.value) {
                    c.shuffleModeEnabled = Settings.getBoolean("last_shuffle", false)
                    c.repeatMode = Settings.getInt("last_repeat", Player.REPEAT_MODE_OFF)
                        .coerceIn(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL)
                } else if (Settings.persistentShuffle.value) {
                    c.shuffleModeEnabled = true
                }
                sync(c)
                refreshLookahead(c, "controller-connected")
                PlaybackPerfMetrics.mark("player_connect_ready")
                startTicker()
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun startTicker() {
        if (tickerStarted) return
        tickerStarted = true
        scope.launch {
            while (isActive) {
                tick()
                delay(250)
            }
        }
    }

    private fun sync(p: Player) {
        item = p.currentMediaItem
        isPlaying = p.isPlaying
        buffering = p.playbackState == Player.STATE_BUFFERING
        durationMs = if (p.duration == C.TIME_UNSET) 0L else p.duration
        positionMs = p.currentPosition
        shuffle = p.shuffleModeEnabled
        repeat = p.repeatMode
        volume = p.volume.coerceIn(0f, 1f)
    }

    fun tick() {
        val c = controller ?: return
        positionMs = c.currentPosition.coerceAtLeast(0L)
        if (c.duration != C.TIME_UNSET) durationMs = c.duration.coerceAtLeast(0L)
        isPlaying = c.isPlaying
        buffering = c.playbackState == Player.STATE_BUFFERING
        volume = c.volume.coerceIn(0f, 1f)
        AudioReactive.updateFallback(positionMs, isPlaying)
        collectListeningStats(c)
        if (SystemClock.elapsedRealtime() - lastSessionPersistAt >= 3_000L) {
            persistSongSession()
        }
    }

    private fun collectListeningStats(c: Player) {
        val now = SystemClock.elapsedRealtime()
        val song = currentSong
        if (song == null) {
            statsLastTickAt = now
            return
        }

        if (statsSong?.url != song.url) {
            flushListeningStats()
            statsSong = song
            statsLastTickAt = now
            return
        }

        if (statsLastTickAt == 0L) {
            statsLastTickAt = now
            return
        }

        val delta = (now - statsLastTickAt).coerceIn(0L, 1_500L)
        statsLastTickAt = now
        if (c.isPlaying && c.playbackState == Player.STATE_READY && !buffering) {
            statsAccumulatedMs += delta
            if (statsAccumulatedMs >= 15_000L) flushListeningStats()
        } else if (statsAccumulatedMs >= 3_000L) {
            flushListeningStats()
        }
    }

    private fun flushListeningStats() {
        val song = statsSong
        val amount = statsAccumulatedMs
        if (song != null && amount > 0L) Store.recordListening(song, amount)
        statsAccumulatedMs = 0L
        statsLastTickAt = SystemClock.elapsedRealtime()
        persistQueueState()
    }

    private fun toItem(song: Song): MediaItem {
        songs[song.url] = song
        val local = Store.downloadPath(song.url) ?: SmartCache.path(song)
        val directUri = runCatching { Uri.parse(song.url) }.getOrNull()
        val host = directUri?.host.orEmpty().lowercase()
        val isYoutubeHost = host == "youtu.be" || host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com")
        val isDirect = directUri?.scheme in setOf("content", "file") ||
            (directUri?.scheme in setOf("http", "https") && !isYoutubeHost)
        val uri = when {
            local != null -> Uri.fromFile(File(local))
            isDirect -> directUri!!
            else -> Uri.Builder().scheme("deepecho").authority("song").appendQueryParameter("u", song.url).build()
        }
        return MediaItem.Builder()
            .setMediaId(song.url)
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumArtist(song.artist) // stable original artist for optional notification lyric composition
                    .setArtworkUri(song.thumb?.let { Uri.parse(it) })
                    .build()
            )
            .build()
    }

    /** Library/playlists preserve their exact ordering. */
    fun playSongs(list: List<Song>, index: Int) {
        val c = controller ?: run { Bus.toast("Player abhi connect ho raha hai, dobara try karo"); return }
        if (list.isEmpty()) return
        smartContext = false
        smartJob?.cancel()
        val safeIndex = index.coerceIn(0, list.lastIndex)
        val requested = list[safeIndex]
        PlaybackPerfMetrics.beginTrackAction("play-tap", requested.url)
        PlaybackLookahead.promoteUserRequested(requested, "library-play")
        list.forEach { queueReasons[it.url] = "From your playlist / library" }
        c.setMediaItems(list.map { toItem(it) }, safeIndex, 0L)
        if (Settings.persistentShuffle.value) c.shuffleModeEnabled = true
        queueRevision++
        c.prepare()
        c.play()
        Store.addHistory(requested)
        refreshLookahead(c, "library-play")
        scheduleStableBackgroundWork(c)
    }

    /** Shuffle a collection first; optionally continue with similar content afterward. */
    fun playShuffledCollection(list: List<Song>) {
        val c = controller ?: run { Bus.toast("Player abhi connect ho raha hai, dobara try karo"); return }
        if (list.isEmpty()) return
        val shuffled = list.shuffled()
        smartContext = Settings.shufflePlaylistFirst.value &&
            Settings.enableSimilarContent.value && Settings.autoLoadMoreSongs.value
        smartJob?.cancel()
        queueReasons.clear()
        shuffled.forEach { queueReasons[it.url] = "Shuffled from playlist / album" }
        PlaybackPerfMetrics.beginTrackAction("play-tap", shuffled.first().url)
        PlaybackLookahead.promoteUserRequested(shuffled.first(), "shuffle-play")
        c.setMediaItems(shuffled.map { toItem(it) }, 0, 0L)
        c.shuffleModeEnabled = false // already shuffled once; prevents double-random order
        queueRevision++
        c.prepare()
        c.play()
        Store.addHistory(shuffled.first())
        refreshLookahead(c, "shuffle-play")
        scheduleStableBackgroundWork(c)
        if (smartContext) scheduleSmartQueue(shuffled.first(), force = true)
    }

    /** Home/Search discovery: seed song only, then taste-aware queue is generated. */
    fun playDiscovery(song: Song) {
        val c = controller ?: run { Bus.toast("Player abhi connect ho raha hai, dobara try karo"); return }
        smartContext = true
        smartJob?.cancel()
        // Give the user-triggered playback resolve first priority. Background warming
        // happens only for likely NEXT tracks, never for the song they just tapped.
        queueReasons.clear()
        queueReasons[song.url] = "You chose this song"
        PlaybackPerfMetrics.beginTrackAction("play-tap", song.url)
        PlaybackLookahead.promoteUserRequested(song, "discovery-play")
        c.setMediaItem(toItem(song))
        if (Settings.persistentShuffle.value) c.shuffleModeEnabled = true
        queueRevision++
        c.prepare()
        c.play()
        Store.addHistory(song)
        refreshLookahead(c, "discovery-play")
        scheduleStableBackgroundWork(c)
        scheduleSmartQueue(song, force = true)
    }

    private fun advanceToNextAndPlay(c: Player) {
        if (!c.hasNextMediaItem()) return
        c.playWhenReady = true
        c.seekToNextMediaItem()
        // A prepared Media3 playlist does not need a full re-prepare for every Next.
        // Only recover from IDLE; repeated prepare() calls are costly during skip bursts.
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    private fun scheduleSmartQueue(
        seed: Song,
        force: Boolean = false,
        advanceAfterAppend: Boolean = false
    ) {
        if (!smartContext || !Settings.smartAutoplay.value ||
            !Settings.enableSimilarContent.value || !Settings.autoLoadMoreSongs.value) return
        val c = controller ?: return
        val remaining = c.mediaItemCount - c.currentMediaItemIndex - 1
        if (!force && remaining > 2) return

        if (smartJob?.isActive == true) {
            if (advanceAfterAppend) {
                val activeJob = smartJob
                scope.launch {
                    activeJob?.join()
                    controller?.let { live ->
                        if (live.hasNextMediaItem()) advanceToNextAndPlay(live)
                    }
                }
            }
            return
        }

        val excluded = buildSet {
            for (i in 0 until c.mediaItemCount) add(c.getMediaItemAt(i).mediaId)
        }
        smartJob = scope.launch {
            val recs = withContext(Dispatchers.IO) {
                runCatching { TasteEngine.smartNext(seed, excluded, 10) }.getOrDefault(emptyList())
            }
            if (recs.isEmpty()) return@launch
            if (!smartContext || !Settings.smartAutoplay.value) return@launch
            val live = controller ?: return@launch
            val stillExcluded = buildSet {
                for (i in 0 until live.mediaItemCount) add(live.getMediaItemAt(i).mediaId)
            }
            val fresh = recs.filterNot { it.url in stillExcluded || Settings.isArtistBlocked(it.artist) }.take(8)
            if (fresh.isEmpty()) return@launch
            fresh.forEach { queueReasons[it.url] = "Smart Autoplay • because of ${seed.artist.ifBlank { seed.title }}" }
            live.addMediaItems(fresh.map { toItem(it) })
            queueRevision++
            refreshLookahead(live, "smart-autoplay-append")

            if (advanceAfterAppend && live.hasNextMediaItem()) {
                advanceToNextAndPlay(live)
            } else if (live.playbackState == Player.STATE_ENDED && live.hasNextMediaItem()) {
                advanceToNextAndPlay(live)
            }
        }
    }

    private fun prepareDuplicateForInsert(c: MediaController, song: Song): Boolean {
        if (!Settings.preventDuplicateTracks.value) return true
        val existing = (0 until c.mediaItemCount).firstOrNull { c.getMediaItemAt(it).mediaId == song.url }
            ?: return true
        if (existing == c.currentMediaItemIndex) {
            Bus.toast("Ye gaana abhi play ho raha hai")
            return false
        }
        c.removeMediaItem(existing)
        queueRevision++
        return true
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        smartContext = false
        smartJob?.cancel()
        if (c.mediaItemCount == 0) playSongs(listOf(song), 0) else {
            if (!prepareDuplicateForInsert(c, song)) return
            queueReasons[song.url] = "Added by you"
            c.addMediaItem(toItem(song))
            queueRevision++
            persistQueueState()
            refreshLookahead(c, "queue-add")
            Bus.toast("Queue mein add hua")
        }
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        smartContext = false
        smartJob?.cancel()
        if (c.mediaItemCount == 0) playSongs(listOf(song), 0) else {
            if (!prepareDuplicateForInsert(c, song)) return
            queueReasons[song.url] = "Play next • added by you"
            c.addMediaItem((c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount), toItem(song))
            queueRevision++
            persistQueueState()
            refreshLookahead(c, "play-next-insert")
            Bus.toast("Agla gaana set")
        }
    }

    fun addManyToQueue(items: List<Song>) {
        val c = controller ?: return
        val fresh = if (Settings.preventDuplicateTracks.value) {
            items.filter { song ->
                (0 until c.mediaItemCount).none { i -> c.getMediaItemAt(i).mediaId == song.url }
            }.distinctBy { it.url }
        } else items
        if (fresh.isEmpty()) return
        smartContext = false
        smartJob?.cancel()
        fresh.forEach { queueReasons[it.url] = "Added by you" }
        c.addMediaItems(fresh.map(::toItem))
        queueRevision++
        persistQueueState()
        refreshLookahead(c, "queue-add-many")
        Bus.toast("${fresh.size} songs queue mein add hue")
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            PlaybackPerfMetrics.beginTrackAction("manual-play-tap", currentSong?.url)
            PlaybackLookahead.promoteUserRequested(currentSong, "manual-play")
            c.play()
            refreshLookahead(c, "manual-play")
            scheduleStableBackgroundWork(c)
        }
    }

    /**
     * Manual NEXT always means "go to the next track and play it".
     * We explicitly restore playWhenReady because some devices/controllers retain a paused state
     * across a media-item transition.
     */
    fun next() {
        val c = controller ?: return
        stableBackgroundJob?.cancel()
        val burst = PlaybackLookahead.noteManualNext()
        PlaybackPerfMetrics.mark("next-tap-$burst")
        when {
            c.hasNextMediaItem() -> {
                val targetIndex = c.nextMediaItemIndex
                val target = if (targetIndex in 0 until c.mediaItemCount) {
                    songs[c.getMediaItemAt(targetIndex).mediaId]
                } else null
                PlaybackPerfMetrics.beginTrackAction("next-tap", target?.url)
                PlaybackLookahead.promoteUserRequested(target, "manual-next")
                advanceToNextAndPlay(c)
                refreshLookahead(c, if (burst >= 2) "skip-burst" else "manual-next")
            }
            smartContext && Settings.smartAutoplay.value -> {
                currentSong?.let { seed ->
                    scheduleSmartQueue(seed, force = true, advanceAfterAppend = true)
                }
            }
        }
    }

    fun prev() {
        val c = controller ?: return
        if (c.currentPosition > 3000) {
            c.seekTo(0)
            c.playWhenReady = true
            c.play()
        } else if (c.hasPreviousMediaItem()) {
            c.playWhenReady = true
            c.seekToPreviousMediaItem()
            c.prepare()
            c.play()
        }
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms.coerceAtLeast(0))
        positionMs = ms.coerceAtLeast(0)
        persistSongSession(force = true)
    }

    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        val now = SystemClock.elapsedRealtime()
        val sign = if (deltaMs >= 0) 1 else -1
        if (!Settings.progressiveSeek.value || now - lastProgressiveSeekAt > 2200L || sign != progressiveSeekSign) {
            progressiveSeekLevel = 0
        } else {
            progressiveSeekLevel = (progressiveSeekLevel + 1).coerceAtMost(5)
        }
        lastProgressiveSeekAt = now
        progressiveSeekSign = sign
        val extra = if (Settings.progressiveSeek.value) progressiveSeekLevel * 5_000L else 0L
        val effectiveDelta = sign * (kotlin.math.abs(deltaMs) + extra)
        val duration = if (c.duration == C.TIME_UNSET) Long.MAX_VALUE else c.duration.coerceAtLeast(0L)
        val target = (c.currentPosition + effectiveDelta).coerceAtLeast(0L).coerceAtMost(duration)
        c.seekTo(target)
        positionMs = target
        persistQueueState()
    }


    fun setPlaybackParameters(speed: Float, pitch: Float) {
        val safeSpeed = speed.coerceIn(0.5f, 2.0f)
        val safePitch = pitch.coerceIn(0.5f, 2.0f)
        controller?.playbackParameters = PlaybackParameters(safeSpeed, safePitch)
    }

    fun resetPlaybackParameters() {
        controller?.playbackParameters = PlaybackParameters.DEFAULT
    }

    fun changeVolume(level: Float) {
        val safe = level.coerceIn(0f, 1f)
        volume = safe
        controller?.volume = safe
        if (safe <= 0.001f && Settings.pauseWhenMediaMuted.value) controller?.pause()
    }

    /**
     * Passive Home/Search warmup now shares the v1.12.7 lookahead coordinator.
     * It warms only playback source state; lyrics/OCR/artwork/cache downloads never ride on this path.
     */
    fun prewarm(songsToWarm: List<Song>, max: Int = 1) {
        PlaybackLookahead.passiveWarm(songsToWarm, max, "ui-visible")
    }

    private fun upcomingSongs(c: MediaController, limit: Int = 12): List<Song> {
        val start = (c.currentMediaItemIndex + 1).coerceAtLeast(0)
        if (start >= c.mediaItemCount) return emptyList()
        return (start until minOf(c.mediaItemCount, start + limit)).mapNotNull { i ->
            songs[c.getMediaItemAt(i).mediaId]
        }
    }

    private fun refreshLookahead(c: MediaController, reason: String) {
        val current = c.currentMediaItem?.mediaId?.let { songs[it] } ?: currentSong
        PlaybackLookahead.plan(current, upcomingSongs(c), reason)
    }

    /**
     * Preserve optional lyrics/SmartCache prefetch, but keep it OUT of Play/Next critical paths.
     * Rapidly skipped tracks never start this work; it begins only after the user settles.
     */
    private fun scheduleStableBackgroundWork(c: MediaController) {
        stableBackgroundJob?.cancel()
        val mediaId = c.currentMediaItem?.mediaId ?: return
        stableBackgroundJob = scope.launch {
            delay(2_200L)
            if (controller?.currentMediaItem?.mediaId != mediaId || PlaybackLookahead.inSkipBurst()) return@launch
            val next = controller?.let { upcomingSongs(it, 1).firstOrNull() }
            if (Settings.preloadLyrics.value && next != null) {
                launch(Dispatchers.IO) { runCatching { Lrclib.fetch(next) } }
            }
            if (Settings.smartCache.value) {
                delay(5_800L)
                if (controller?.currentMediaItem?.mediaId == mediaId && !buffering && !PlaybackLookahead.inSkipBurst()) {
                    val current = songs[mediaId]
                    if (current != null) launch(Dispatchers.IO) { runCatching { SmartCache.cache(current) } }
                }
            }
        }
    }

    fun queueSnapshot(): List<QueueEntry> {
        val c = controller ?: return emptyList()
        val current = c.currentMediaItemIndex
        return (0 until c.mediaItemCount).mapNotNull { index ->
            val media = c.getMediaItemAt(index)
            val song = songs[media.mediaId] ?: return@mapNotNull null
            QueueEntry(
                index = index,
                song = song,
                reason = queueReasons[song.url] ?: if (index == current) "Now playing" else "Up next",
                current = index == current
            )
        }
    }

    fun moveQueueItem(from: Int, to: Int) {
        val c = controller ?: return
        if (from !in 0 until c.mediaItemCount || to !in 0 until c.mediaItemCount || from == to) return
        c.moveMediaItem(from, to)
        queueRevision++
        persistQueueState()
        refreshLookahead(c, "queue-reorder")
    }

    fun removeQueueItem(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount || index == c.currentMediaItemIndex) return
        c.removeMediaItem(index)
        queueRevision++
        persistQueueState()
        refreshLookahead(c, "queue-remove")
    }

    fun playQueueItem(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.playWhenReady = true
        val target = songs[c.getMediaItemAt(index).mediaId]
        PlaybackLookahead.promoteUserRequested(target, "queue-item")
        c.seekToDefaultPosition(index)
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
        queueRevision++
        refreshLookahead(c, "queue-item")
    }

    fun clearUpcomingQueue() {
        val c = controller ?: return
        val from = c.currentMediaItemIndex + 1
        if (from < c.mediaItemCount) {
            c.removeMediaItems(from, c.mediaItemCount)
            queueRevision++
            persistQueueState()
            refreshLookahead(c, "queue-clear")
            Bus.toast("Upcoming queue clear ho gayi")
        }
    }

    fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            sleepUntilMs = 0L
            return
        }
        val delayMs = minutes * 60_000L
        sleepUntilMs = System.currentTimeMillis() + delayMs
        sleepJob = scope.launch {
            delay(delayMs)
            controller?.pause()
            sleepUntilMs = 0L
            Bus.toast("Sleep timer • playback paused")
        }
    }

    fun sleepAtEndOfSong() {
        if (durationMs <= 0L || durationMs <= positionMs) {
            Bus.toast("Sleep timer ke liye pehle gaana play karo")
            return
        }
        val remain = (durationMs - positionMs).coerceAtLeast(1_000L)
        sleepJob?.cancel()
        sleepUntilMs = System.currentTimeMillis() + remain
        sleepJob = scope.launch {
            delay(remain)
            controller?.pause()
            sleepUntilMs = 0L
            Bus.toast("Sleep timer • end of song")
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        sleepUntilMs = 0L
    }

    fun toggleShuffle() {
        controller?.let { c ->
            c.shuffleModeEnabled = !c.shuffleModeEnabled
            if (Settings.rememberShuffleRepeat.value) Settings.putBoolean("last_shuffle", c.shuffleModeEnabled)
        }
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        if (Settings.rememberShuffleRepeat.value) Settings.putInt("last_repeat", c.repeatMode)
    }

    /**
     * Save current playback state plus a bounded upcoming lookahead for fast restored Play/Next.
     * Full queue persistence remains an explicit setting; UI/navigation is never restored.
     */
    fun persistSongSession(force: Boolean = false) {
        val c = controller ?: return
        val song = currentSong ?: return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastSessionPersistAt < 2_500L) return
        lastSessionPersistAt = now
        runCatching {
            val upcoming = JSONArray()
            val start = c.currentMediaItemIndex + 1
            for (i in start until minOf(c.mediaItemCount, start + 8)) {
                val next = songs[c.getMediaItemAt(i).mediaId] ?: continue
                upcoming.put(
                    JSONObject()
                        .put("url", next.url)
                        .put("title", next.title)
                        .put("artist", next.artist)
                        .put("duration", next.durationSec)
                        .put("thumb", next.thumb ?: "")
                )
            }
            val root = JSONObject()
                .put("url", song.url)
                .put("title", song.title)
                .put("artist", song.artist)
                .put("duration", song.durationSec)
                .put("thumb", song.thumb ?: "")
                .put("position", c.currentPosition.coerceAtLeast(0L))
                .put("theme", Settings.theme.value)
                .put("smartContext", smartContext)
                .put("upcoming", upcoming)
            Settings.putString("last_song_session", root.toString())
            if (song.artist.startsWith("Podcast •")) Podcasts.saveProgress(song.url, c.currentPosition.coerceAtLeast(0L))
        }
    }

    private fun restoreSongSession(c: MediaController): Boolean {
        return runCatching {
            val raw = Settings.getString("last_song_session", "")
            if (raw.isBlank()) return@runCatching false
            val root = JSONObject(raw)
            val url = root.optString("url").trim()
            if (url.isBlank()) return@runCatching false

            val song = Song(
                url = url,
                title = root.optString("title", "Unknown"),
                artist = root.optString("artist", ""),
                durationSec = root.optLong("duration", 0L).coerceAtLeast(0L),
                thumb = root.optString("thumb").ifBlank { null }
            )
            val maxMs = song.durationSec.takeIf { it > 0L }?.times(1000L) ?: Long.MAX_VALUE
            val position = root.optLong("position", 0L).coerceAtLeast(0L).coerceAtMost(maxMs)
            val savedTheme = root.optString("theme", Settings.theme.value)
            if (savedTheme.isNotBlank()) Settings.setTheme(savedTheme)
            Settings.setLiveTheme(true)

            val upcomingJson = root.optJSONArray("upcoming") ?: JSONArray()
            val upcoming = buildList {
                for (i in 0 until upcomingJson.length().coerceAtMost(8)) {
                    val o = upcomingJson.optJSONObject(i) ?: continue
                    val nextUrl = o.optString("url").trim()
                    if (nextUrl.isBlank() || nextUrl == song.url) continue
                    add(
                        Song(
                            url = nextUrl,
                            title = o.optString("title", "Unknown"),
                            artist = o.optString("artist", ""),
                            durationSec = o.optLong("duration", 0L).coerceAtLeast(0L),
                            thumb = o.optString("thumb").ifBlank { null }
                        )
                    )
                }
            }.distinctBy { it.url }

            restoredSessionMediaId = song.url
            smartContext = root.optBoolean("smartContext", false)
            queueReasons[song.url] = "Restored session"
            upcoming.forEach { queueReasons[it.url] = if (smartContext) "Restored Smart Autoplay" else "Restored up next" }
            c.playWhenReady = false
            c.setMediaItems((listOf(song) + upcoming).map(::toItem), 0, position)
            // prepare() while paused pre-buffers the restored current item without audible autoplay.
            c.prepare()
            c.pause()
            PlaybackLookahead.promoteUserRequested(song, "session-restore-current")
            refreshLookahead(c, "session-restore")
            PlaybackPerfMetrics.mark("session-restored")
            queueRevision++
            true
        }.getOrDefault(false)
    }

    private fun persistQueueState() {
        val c = controller ?: return
        if (!Settings.persistentQueue.value || c.mediaItemCount == 0) return
        runCatching {
            val arr = JSONArray()
            for (i in 0 until c.mediaItemCount) {
                val song = songs[c.getMediaItemAt(i).mediaId] ?: continue
                arr.put(
                    JSONObject()
                        .put("url", song.url)
                        .put("title", song.title)
                        .put("artist", song.artist)
                        .put("duration", song.durationSec)
                        .put("thumb", song.thumb ?: "")
                )
            }
            val root = JSONObject()
                .put("index", c.currentMediaItemIndex.coerceAtLeast(0))
                .put("position", c.currentPosition.coerceAtLeast(0L))
                .put("items", arr)
            Settings.putString("persistent_queue_state", root.toString())
        }
    }

    private fun restorePersistentQueue(c: MediaController) {
        runCatching {
            val raw = Settings.getString("persistent_queue_state", "")
            if (raw.isBlank()) return@runCatching
            val root = JSONObject(raw)
            val arr = root.optJSONArray("items") ?: return@runCatching
            val restored = buildList {
                for (i in 0 until arr.length().coerceAtMost(150)) {
                    val o = arr.optJSONObject(i) ?: continue
                    val url = o.optString("url")
                    if (url.isBlank()) continue
                    add(
                        Song(
                            url = url,
                            title = o.optString("title", "Unknown"),
                            artist = o.optString("artist", ""),
                            durationSec = o.optLong("duration", 0L),
                            thumb = o.optString("thumb").ifBlank { null }
                        )
                    )
                }
            }
            if (restored.isEmpty()) return@runCatching
            val index = root.optInt("index", 0).coerceIn(0, restored.lastIndex)
            val position = root.optLong("position", 0L).coerceAtLeast(0L)
            restored.forEach { queueReasons[it.url] = "Restored queue" }
            c.setMediaItems(restored.map(::toItem), index, position)
            c.prepare()
            c.pause()
            smartContext = false
            PlaybackLookahead.promoteUserRequested(restored[index], "persistent-queue-restore-current")
            refreshLookahead(c, "persistent-queue-restore")
            queueRevision++
        }
    }
}
