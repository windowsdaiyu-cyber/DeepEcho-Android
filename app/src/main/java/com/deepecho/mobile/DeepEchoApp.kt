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
import org.schabi.newpipe.extractor.NewPipe

class DeepEchoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        Store.init(this)
        LocalMusic.init(this)
        Podcasts.init()
        Downloads.init(this)
        SmartCache.init(this)
        LyricsCache.init(this)
        com.deepecho.mobile.net.YouTubeApi.init(this)
        NewPipe.init(YtDownloader())
    }
}
