package com.deepecho.mobile.quicktiles

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.deepecho.mobile.MainActivity
import com.deepecho.mobile.data.Settings
import com.deepecho.mobile.overlay.FloatingLyricsController
import com.deepecho.mobile.player.PlaybackService

private fun TileService.openAndCollapse(intent: Intent) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    if (Build.VERSION.SDK_INT >= 34) {
        val pending = PendingIntent.getActivity(
            this, 9128, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        startActivityAndCollapse(pending)
    } else {
        @Suppress("DEPRECATION")
        startActivityAndCollapse(intent)
    }
}

private inline fun TileService.withController(crossinline action: (MediaController) -> Unit) {
    val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
    val future = MediaController.Builder(this, token).buildAsync()
    future.addListener({
        runCatching {
            val controller = future.get()
            action(controller)
            controller.release()
        }
    }, ContextCompat.getMainExecutor(this))
}

class PlayPauseTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        withController { c ->
            qsTile?.apply {
                label = if (c.isPlaying) "Pause DeepEcho" else "Play DeepEcho"
                state = if (c.isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                updateTile()
            }
        }
    }

    override fun onClick() {
        super.onClick()
        withController { c ->
            if (c.isPlaying) c.pause() else {
                if (c.mediaItemCount > 0) {
                    c.prepare()
                    c.play()
                } else {
                    openAndCollapse(Intent(this, MainActivity::class.java))
                }
            }
            qsTile?.apply {
                label = if (c.isPlaying) "Pause DeepEcho" else "Play DeepEcho"
                state = if (c.isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                updateTile()
            }
        }
    }
}

class OpenDeepEchoTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply { state = Tile.STATE_ACTIVE; label = "Open DeepEcho"; updateTile() }
    }

    override fun onClick() {
        super.onClick()
        openAndCollapse(Intent(this, MainActivity::class.java))
    }
}

class FloatingLyricsTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (FloatingLyricsController.enabled.value) {
            FloatingLyricsController.stop(this)
        } else if (!FloatingLyricsController.start(this)) {
            openAndCollapse(FloatingLyricsController.permissionIntent(this))
        }
        refresh()
    }

    private fun refresh() {
        val enabled = FloatingLyricsController.enabled.value
        qsTile?.apply {
            label = "Floating Lyrics"
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}

class SmartAutoplayTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        Settings.setSmartAutoplay(!Settings.smartAutoplay.value)
        refresh()
    }

    private fun refresh() {
        val enabled = Settings.smartAutoplay.value
        qsTile?.apply {
            label = "Smart Autoplay"
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
