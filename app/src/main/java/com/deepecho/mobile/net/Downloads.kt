package com.deepecho.mobile.net

import android.content.Context
import android.os.Environment
import com.deepecho.mobile.data.DownloadItem
import com.deepecho.mobile.data.Song
import com.deepecho.mobile.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Songs ko app ke Music folder mein save karta hai (storage permission ki zaroorat nahi). */
object Downloads {
    private const val CHUNK = 8L * 1024L * 1024L // 8 MiB — fewer range round-trips on mobile while keeping chunked reliability

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dir: File

    /** url -> 0f..1f */
    val progress = MutableStateFlow<Map<String, Float>>(emptyMap())

    fun init(ctx: Context) {
        dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: ctx.filesDir, "DEEP-ECHO")
        dir.mkdirs()
    }

    fun folderPath(): String = dir.absolutePath

    fun start(song: Song) {
        if (progress.value.containsKey(song.url) || Store.downloadPath(song.url) != null) return
        setProgress(song.url, 0f)
        scope.launch {
            try {
                val r = YouTubeApi.resolve(song.url)
                val name = song.title.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(80) +
                    " [" + song.videoId + "]." + r.ext
                val target = File(dir, name)
                val tmp = File(dir, "$name.part")
                download(r.url, tmp) { setProgress(song.url, it) }
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) throw IOException("File save nahi hui")
                Store.addDownload(DownloadItem(song, target.absolutePath))
                Bus.toast("Downloaded: ${song.title}")
            } catch (e: Exception) {
                Bus.toast("Download fail: ${e.message ?: "unknown error"}")
            } finally {
                progress.value = progress.value - song.url
            }
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
                        // Server ne Range ignore kiya — poori file ek saath
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
