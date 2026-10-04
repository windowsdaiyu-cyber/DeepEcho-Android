package com.deepecho.mobile

import android.app.Application
import com.deepecho.mobile.data.Settings
import com.deepecho.mobile.data.Store
import com.deepecho.mobile.net.Downloads
import com.deepecho.mobile.net.SmartCache
import com.deepecho.mobile.net.YtDownloader
import org.schabi.newpipe.extractor.NewPipe

class DeepEchoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        Store.init(this)
        Downloads.init(this)
        SmartCache.init(this)
        NewPipe.init(YtDownloader())
    }
}
