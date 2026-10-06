package com.deepecho.mobile.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.deepecho.mobile.data.Settings
import com.deepecho.mobile.data.Song
import com.deepecho.mobile.data.Store
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Request

/**
 * Optional, stability-first offline cache.
 * Only caches complete audio files on an unmetered network and never replaces Downloads.
 */
object SmartCache {
    private const val MAX_BYTES = 256L * 1024L * 1024L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var appContext: Context
    private lateinit var dir: File

    fun init(context: Context) {
        appContext = context.applicationContext
        dir = File(context.cacheDir, "deepecho-smart-audio").apply { mkdirs() }
        // Cache eviction is maintenance, not a first-frame dependency.
        scope.launch { trim() }
    }

    fun path(song: Song): String? {
        if (!::dir.isInitialized) return null
        val prefix = "${song.videoId}."
        return dir.listFiles()
            ?.firstOrNull { it.isFile && !it.name.endsWith(".part") && it.name.startsWith(prefix) && it.length() > 32_768L }
            ?.also { it.setLastModified(System.currentTimeMillis()) }
            ?.absolutePath
    }

    fun cache(song: Song) {
        if (!::dir.isInitialized || !Settings.smartCache.value) return
        if (Store.downloadPath(song.url) != null || path(song) != null) return
        if (!isUnmetered()) return

        val resolved = YouTubeApi.resolve(song.url)
        val target = File(dir, "${song.videoId}.${resolved.ext}")
        if (target.exists() && target.length() > 32_768L) return
        val part = File(dir, "${target.name}.part")

        val request = Request.Builder()
            .url(resolved.url)
            .header("User-Agent", Net.UA)
            .build()

        try {
            Net.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty cache response")
                part.outputStream().buffered(256 * 1024).use { output ->
                    body.byteStream().use { input -> input.copyTo(output, 256 * 1024) }
                }
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            target.setLastModified(System.currentTimeMillis())
            trim()
        } catch (t: Throwable) {
            part.delete()
            throw t
        }
    }

    private fun isUnmetered(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun trim() {
        if (!::dir.isInitialized) return
        val files = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }
            ?.sortedBy { it.lastModified() }
            .orEmpty()
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= MAX_BYTES) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }
}
