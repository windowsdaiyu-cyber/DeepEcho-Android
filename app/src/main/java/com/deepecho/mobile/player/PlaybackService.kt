package com.deepecho.mobile.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
import com.deepecho.mobile.net.Net
import com.deepecho.mobile.net.YouTubeApi
import java.io.IOException

/**
 * Background playback. Songs "deepecho://song?u=<youtube url>" URI se queue hote hain
 * aur asli stream URL play hone ke time pe resolve hota hai (lazy) — isliye lambi queue bhi turant ban jaati hai.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var retryId: String? = null

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
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Expired / 403 URL: ek baar fresh URL ke saath dobara try
                val id = player.currentMediaItem?.mediaId ?: return
                if (retryId != id) {
                    retryId = id
                    YouTubeApi.invalidate(id)
                    val pos = player.currentPosition
                    player.seekTo(player.currentMediaItemIndex, pos)
                    player.prepare()
                    player.play()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem?.mediaId != retryId) retryId = null
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                AudioFx.attach(audioSessionId)
            }
        })
        AudioFx.attach(player.audioSessionId)

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player).setSessionActivity(openApp).build()
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
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
