package com.deepecho.mobile.player

import android.app.PendingIntent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.deepecho.mobile.MainActivity
import com.deepecho.mobile.data.Settings
import com.deepecho.mobile.net.Net
import com.deepecho.mobile.net.YouTubeApi
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Background playback. Songs "deepecho://song?u=<youtube url>" URI se queue hote hain
 * aur asli stream URL play hone ke time pe resolve hota hai (lazy) — isliye lambi queue bhi turant ban jaati hai.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var retryId: String? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var audioDeviceCallback: AudioDeviceCallback? = null
    private var crossfadeActive = false
    private var crossfadeBaseVolume = 1f
    private var pausedForBluetoothKey: String? = null
    private var pausedForBluetoothAt: Long = 0L

    override fun onCreate() {
        super.onCreate()

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Net.UA)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val resolving = ResolvingDataSource.Factory(http, ResolvingDataSource.Resolver { spec -> resolve(spec) })
        val dataFactory = DefaultDataSource.Factory(this, resolving)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000,
                50_000,
                700,
                1_400
            )
            .build()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setSkipSilenceEnabled(Settings.skipSilence.value || Settings.instantSkipSilence.value)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Expired / 403 URL: ek baar fresh URL ke saath dobara try.
                val id = player.currentMediaItem?.mediaId ?: return
                if (retryId != id) {
                    retryId = id
                    YouTubeApi.invalidate(id)
                    val pos = player.currentPosition
                    player.seekTo(player.currentMediaItemIndex, pos)
                    player.prepare()
                    player.play()
                } else if (Settings.autoSkipOnError.value && player.hasNextMediaItem()) {
                    retryId = null
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem?.mediaId != retryId) retryId = null
                if (crossfadeActive) {
                    val target = crossfadeBaseVolume.coerceIn(0f, 1f)
                    crossfadeActive = false
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && Settings.crossfadeSeconds.value > 0) {
                        player.volume = 0f
                        serviceScope.launch {
                            val steps = (Settings.crossfadeSeconds.value.coerceIn(1, 5) * 10).coerceAtLeast(1)
                            repeat(steps) { i ->
                                player.volume = target * ((i + 1f) / steps)
                                delay(100)
                            }
                            player.volume = target
                        }
                    } else {
                        player.volume = target
                    }
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                AudioFx.attach(audioSessionId)
            }
        })
        AudioFx.attach(player.audioSessionId)
        applyOffload(player, Settings.audioOffload.value)

        serviceScope.launch {
            combine(Settings.skipSilence, Settings.instantSkipSilence) { skip, instant -> skip || instant }
                .distinctUntilChanged()
                .collect { enabled -> player.setSkipSilenceEnabled(enabled) }
        }
        serviceScope.launch {
            Settings.audioOffload.collect { enabled ->
                applyOffload(player, enabled)
            }
        }
        serviceScope.launch {
            while (isActive) {
                val seconds = Settings.crossfadeSeconds.value.coerceIn(0, 5)
                if (seconds > 0 && player.isPlaying && player.duration > 0 && player.hasNextMediaItem()) {
                    val remaining = player.duration - player.currentPosition
                    val window = seconds * 1000L
                    if (remaining in 1..window) {
                        if (!crossfadeActive) {
                            crossfadeActive = true
                            crossfadeBaseVolume = player.volume.coerceIn(0f, 1f)
                        }
                        val factor = (remaining.toFloat() / window.toFloat()).coerceIn(0.04f, 1f)
                        player.volume = crossfadeBaseVolume * factor
                    } else if (crossfadeActive && remaining > window + 500L) {
                        player.volume = crossfadeBaseVolume
                        crossfadeActive = false
                    }
                } else if (crossfadeActive) {
                    player.volume = crossfadeBaseVolume
                    crossfadeActive = false
                }
                delay(100)
            }
        }

        serviceScope.launch {
            val audioManager = getSystemService(AudioManager::class.java)
            while (isActive) {
                if (Settings.pauseWhenMediaMuted.value && player.isPlaying &&
                    audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) <= 0
                ) {
                    player.pause()
                }
                delay(700)
            }
        }
        registerResumeOnAudioDevice(player)

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player).setSessionActivity(openApp).build()
    }

    private fun applyOffload(player: ExoPlayer, enabled: Boolean) {
        runCatching {
            val prefs = AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(
                    if (enabled) AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                    else AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                )
                .setIsGaplessSupportRequired(false)
                .build()
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setAudioOffloadPreferences(prefs)
                .build()
        }
    }

    private fun registerResumeOnAudioDevice(player: ExoPlayer) {
        val manager = getSystemService(AudioManager::class.java)
        fun isBt(info: AudioDeviceInfo): Boolean = info.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            info.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= 31 && (info.type == AudioDeviceInfo.TYPE_BLE_HEADSET || info.type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
        fun key(info: AudioDeviceInfo): String = "${info.type}:${info.productName}"
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                if (!Settings.resumeOnBluetoothConnect.value || !player.isPlaying) return
                val removed = removedDevices.firstOrNull(::isBt) ?: return
                pausedForBluetoothKey = key(removed)
                pausedForBluetoothAt = android.os.SystemClock.elapsedRealtime()
                player.pause()
            }

            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                if (!Settings.resumeOnBluetoothConnect.value || player.mediaItemCount == 0) return
                val expected = pausedForBluetoothKey ?: return
                val age = android.os.SystemClock.elapsedRealtime() - pausedForBluetoothAt
                if (age !in 0..10 * 60_000L) {
                    pausedForBluetoothKey = null
                    return
                }
                val matching = addedDevices.any { isBt(it) && (key(it) == expected || it.type.toString() == expected.substringBefore(':')) }
                if (matching && !player.isPlaying) {
                    pausedForBluetoothKey = null
                    player.play()
                }
            }
        }
        audioDeviceCallback = callback
        manager.registerAudioDeviceCallback(callback, null)
    }

    private fun resolve(spec: DataSpec): DataSpec {
        val uri = spec.uri
        if (uri.scheme != "deepecho") return spec
        val videoUrl = uri.getQueryParameter("u") ?: throw IOException("Bad song uri")
        val r = try {
            YouTubeApi.resolve(videoUrl)
        } catch (e: Exception) {
            throw IOException("Stream resolve fail: ${e.message}", e)
        }
        return spec.withUri(Uri.parse(r.url))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (Settings.stopMusicOnTaskClear.value) {
            p?.stop()
            stopSelf()
            return
        }
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        val manager = getSystemService(AudioManager::class.java)
        audioDeviceCallback?.let { runCatching { manager.unregisterAudioDeviceCallback(it) } }
        audioDeviceCallback = null
        serviceScope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
