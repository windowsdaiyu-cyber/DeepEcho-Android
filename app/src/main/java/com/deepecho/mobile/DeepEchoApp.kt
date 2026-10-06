package com.deepecho.mobile

import android.app.Application
import com.deepecho.mobile.data.Settings
import com.deepecho.mobile.data.LocalMusic
import com.deepecho.mobile.data.Podcasts
import com.deepecho.mobile.data.Store
import com.deepecho.mobile.net.Downloads
import com.deepecho.mobile.net.SmartCache
import com.deepecho.mobile.net.YtDownloader
import com.deepecho.mobile.lyrics.LyricsCache
import com.deepecho.mobile.player.PlaybackPerfMetrics
import com.deepecho.mobile.player.PlaybackLookahead
import org.schabi.newpipe.extractor.NewPipe

class DeepEchoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PlaybackPerfMetrics.mark("app-onCreate-start")
        Settings.init(this)
        Store.init(this)
        LocalMusic.init(this)
        Podcasts.init()
        Downloads.init(this)
        SmartCache.init(this)
        LyricsCache.init(this)
        com.deepecho.mobile.net.YouTubeApi.init(this)
        NewPipe.init(YtDownloader())
        PlaybackPerfMetrics.mark("app-onCreate-ready")
    }

    override fun onTrimMemory(level: Int) {
        PlaybackLookahead.noteMemoryPressure(level)
        super.onTrimMemory(level)
    }

    override fun onLowMemory() {
        PlaybackLookahead.noteMemoryPressure(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        super.onLowMemory()
    }
}
