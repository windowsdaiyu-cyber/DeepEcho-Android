package com.deepecho.android.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.deepecho.android.model.Track
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PlayerConnection(private val context: Context) {
    data class PlayerState(
        val connected: Boolean = false,
        val isPlaying: Boolean = false,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val currentTrackId: String = ""
    )

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val mutableState = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = mutableState

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = publish()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = publish()
        override fun onPlaybackStateChanged(playbackState: Int) = publish()
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, DeepEchoPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, token).buildAsync()
        future = controllerFuture
        controllerFuture.addListener({
            runCatching { controllerFuture.get() }.onSuccess {
                controller = it
                it.addListener(listener)
                publish()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun play(track: Track) {
        val c = controller ?: return
        val item = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(track.streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .build()
            )
            .build()
        c.setMediaItem(item)
        c.prepare()
        c.play()
        publish()
    }

    fun toggle() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
        publish()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0))
        publish()
    }

    fun tick() = publish()

    fun release() {
        controller?.removeListener(listener)
        controller = null
        future?.let { MediaController.releaseFuture(it) }
        future = null
    }

    private fun publish() {
        val c = controller
        if (c == null) {
            mutableState.value = PlayerState()
            return
        }
        mutableState.value = PlayerState(
            connected = true,
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: 0,
            currentTrackId = c.currentMediaItem?.mediaId.orEmpty()
        )
    }
}
