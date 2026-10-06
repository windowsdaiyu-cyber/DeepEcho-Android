package com.deepecho.mobile.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import com.deepecho.mobile.data.DownloadItem
import com.deepecho.mobile.data.Settings
import com.deepecho.mobile.data.Song
import com.deepecho.mobile.data.Store
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** Songs ko app ke Music folder mein save karta hai (storage permission ki zaroorat nahi). */
object Downloads {
    private const val CHUNK = 8L * 1024L * 1024L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dir: File
    private lateinit var appContext: Context

    /** url -> 0f..1f */
    val progress = MutableStateFlow<Map<String, Float>>(emptyMap())

    fun init(ctx: Context) {
        appContext = ctx.applicationContext
        dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: ctx.filesDir, "DEEP-ECHO")
        dir.mkdirs()
    }

    fun folderPath(): String = dir.absolutePath

    fun start(song: Song) {
        if (progress.value.containsKey(song.url) || Store.downloadPath(song.url) != null) return
        if (Settings.downloadWifiOnly.value && !isOnWifi()) {
            Bus.toast("Wi-Fi only download ON hai")
            return
        }
        setProgress(song.url, 0f)
        scope.launch {
            try {
                val parsed = runCatching { android.net.Uri.parse(song.url) }.getOrNull()
                val host = parsed?.host.orEmpty().lowercase()
                val isDirectRemote = parsed?.scheme in setOf("http", "https") &&
                    host != "youtu.be" && !host.endsWith("youtube.com") && !host.endsWith("youtube-nocookie.com")
                val r = if (isDirectRemote) {
                    val path = parsed?.lastPathSegment.orEmpty().substringBefore('?')
                    val ext = path.substringAfterLast('.', "m4a").lowercase()
                        .takeIf { it in setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac", "webm") } ?: "m4a"
                    Resolved(song.url, ext)
                } else YouTubeApi.resolveForDownload(song.url)
                val safeId = if (isDirectRemote) kotlin.math.abs(song.url.hashCode()).toString() else song.videoId
                val name = song.title.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(80) +
                    " [" + safeId + "]." + r.ext
                val target = File(dir, name)
                val tmp = File(dir, "$name.part")
                download(r.url, tmp) { setProgress(song.url, it) }
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) throw IOException("File save nahi hui")
                if (Settings.downloadWithMetadata.value) writeMetadataSidecar(song, target)
                Store.addDownload(DownloadItem(song, target.absolutePath))
                Bus.toast("Downloaded: ${song.title}")
            } catch (e: Exception) {
                Bus.toast("Download fail: ${e.message ?: "unknown error"}")
            } finally {
                progress.value = progress.value - song.url
            }
        }
    }

    private fun isOnWifi(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /**
     * Store song info + available lyrics next to the audio without mutating the media container.
     * This is deterministic and avoids corrupting M4A/WebM files on device-specific encoders.
     */
    private fun writeMetadataSidecar(song: Song, target: File) {
        runCatching {
            val lyrics = Lrclib.fetch(song)
            val root = JSONObject()
                .put("title", song.title)
                .put("artist", song.artist)
                .put("sourceUrl", song.url)
                .put("durationSec", song.durationSec)
                .put("artwork", song.thumb ?: "")
            if (lyrics != null) {
                root.put("lyricsSource", lyrics.source)
                root.put("lyricsConfidence", lyrics.confidence)
                root.put("plainLyrics", lyrics.plain ?: "")
                val lines = JSONArray()
                lyrics.synced.forEach { line ->
                    lines.put(JSONObject().put("timeMs", line.timeMs).put("text", line.text))
                }
                root.put("syncedLyrics", lines)
            }
            File(target.parentFile, target.name + ".deepecho.json").writeText(root.toString())
        }
    }

    private fun setProgress(url: String, p: Float) {
        progress.value = progress.value + (url to p)
    }

    private fun download(url: String, out: File, onProgress: (Float) -> Unit) {
        out.outputStream().use { os ->
            var start = 0L
            var total = -1L
            var done = false
            while (!done) {
                val req = Request.Builder().url(url)
                    .header("User-Agent", Net.UA)
                    .header("Range", "bytes=$start-${start + CHUNK - 1}")
                    .build()
                Net.client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    val body = resp.body ?: throw IOException("Empty response")
                    if (resp.code == 206) {
                        total = resp.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L
                        val n = copy(body.byteStream(), os)
                        if (n == 0L) done = true
                        start += n
                        if (total > 0) onProgress((start.toFloat() / total).coerceIn(0f, 1f))
                        if (total in 1..start) done = true
                    } else {
                        val len = body.contentLength()
                        var written = 0L
                        val buf = ByteArray(256 * 1024)
                        val ins = body.byteStream()
                        while (true) {
                            val r = ins.read(buf)
                            if (r < 0) break
                            os.write(buf, 0, r)
                            written += r
                            if (len > 0) onProgress((written.toFloat() / len).coerceIn(0f, 1f))
                        }
                        done = true
                    }
                }
            }
        }
    }

    private fun copy(input: InputStream, output: OutputStream): Long {
        val buf = ByteArray(256 * 1024)
        var n = 0L
        while (true) {
            val r = input.read(buf)
            if (r < 0) break
            output.write(buf, 0, r)
            n += r
        }
        return n
    }
}
