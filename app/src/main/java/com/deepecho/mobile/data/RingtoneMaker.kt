package com.deepecho.mobile.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.nio.ByteBuffer

object RingtoneMaker {
    data class Result(val uri: Uri, val displayName: String)

    fun sourceUri(context: Context, song: Song): Uri? {
        Store.downloadPath(song.url)?.let { return Uri.fromFile(File(it)) }
        val u = runCatching { Uri.parse(song.url) }.getOrNull() ?: return null
        return if (u.scheme == "content" || u.scheme == "file") u else null
    }

    fun makeAndSet(context: Context, song: Song, startMs: Long, endMs: Long): Result {
        val source = sourceUri(context, song) ?: error("Song ko pehle download karo")
        val safeStart = startMs.coerceAtLeast(0L)
        val safeEnd = endMs.coerceAtLeast(safeStart + 1000L).coerceAtMost(safeStart + 30_000L)
        val tmpDir = File(context.cacheDir, "ringtones").apply { mkdirs() }
        val base = song.title.replace(Regex("""[\\/:*?\"<>|]"""), "_").trim().take(60).ifBlank { "DeepEcho" }

        val extractor = MediaExtractor()
        if (source.scheme == "content") {
            context.contentResolver.openFileDescriptor(source, "r")?.use { pfd -> extractor.setDataSource(pfd.fileDescriptor) }
                ?: error("Audio file open nahi hui")
        } else {
            extractor.setDataSource(source.path ?: error("Audio path missing"))
        }

        var audioTrack = -1
        var inputFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) {
                audioTrack = i
                inputFormat = f
                break
            }
        }
        if (audioTrack < 0 || inputFormat == null) {
            extractor.release()
            error("Audio track nahi mili")
        }

        val mime = inputFormat.getString(MediaFormat.KEY_MIME).orEmpty()
        val outputKind = when {
            mime.equals("audio/mpeg", true) -> "mp3"
            mime.contains("aac", true) || mime.contains("mp4a", true) || mime.contains("mp4", true) -> "m4a"
            mime.contains("opus", true) || mime.contains("vorbis", true) -> "webm"
            else -> "unsupported"
        }
        if (outputKind == "unsupported") {
            extractor.release()
            error("Is local audio codec ko trim karne ke liye conversion chahiye. DeepEcho download copy try karo.")
        }
        val ext = outputKind
        val mimeOut = when (outputKind) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            else -> "audio/webm"
        }
        val tmp = File(tmpDir, "$base-${safeStart / 1000}-${safeEnd / 1000}.$ext")
        if (tmp.exists()) tmp.delete()

        extractor.selectTrack(audioTrack)
        extractor.seekTo(safeStart * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        val capacity = inputFormat.getIntegerOrDefault(MediaFormat.KEY_MAX_INPUT_SIZE, 512 * 1024).coerceAtLeast(64 * 1024)
        val buffer = ByteBuffer.allocateDirect(capacity)

        if (outputKind == "mp3") {
            // MP3 is already an elementary stream. MediaExtractor returns complete encoded frames,
            // so copy only the selected frames instead of trying to put MP3 inside an MP4 muxer.
            tmp.outputStream().buffered().use { out ->
                while (true) {
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    val pts = extractor.sampleTime
                    if (pts < 0 || pts > safeEnd * 1000L) break
                    if (pts >= safeStart * 1000L) {
                        val bytes = ByteArray(size)
                        buffer.position(0)
                        buffer.get(bytes, 0, size)
                        out.write(bytes)
                    }
                    extractor.advance()
                }
            }
            extractor.release()
        } else {
            val muxerFormat = if (outputKind == "m4a") MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 else MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            val muxer = MediaMuxer(tmp.absolutePath, muxerFormat)
            try {
                val outTrack = muxer.addTrack(inputFormat)
                muxer.start()
                val info = android.media.MediaCodec.BufferInfo()
                var firstPts = -1L
                while (true) {
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    val pts = extractor.sampleTime
                    if (pts < 0 || pts > safeEnd * 1000L) break
                    if (pts < safeStart * 1000L) {
                        extractor.advance()
                        continue
                    }
                    if (firstPts < 0) firstPts = pts
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = (pts - firstPts).coerceAtLeast(0L)
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(outTrack, buffer, info)
                    extractor.advance()
                }
            } finally {
                runCatching { muxer.stop() }
                runCatching { muxer.release() }
                extractor.release()
            }
        }
        if (!tmp.exists() || tmp.length() <= 0) error("Ringtone clip create nahi hui")

        val resolver = context.contentResolver
        val display = "$base - DeepEcho.$ext"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, display)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeOut)
            put(MediaStore.Audio.Media.TITLE, song.title)
            put(MediaStore.Audio.Media.ARTIST, song.artist)
            put(MediaStore.Audio.Media.IS_RINGTONE, true)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, false)
            put(MediaStore.Audio.Media.IS_ALARM, false)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES + "/DeepEcho")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            } else {
                val legacyDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES), "DeepEcho").apply { mkdirs() }
                put(MediaStore.Audio.Media.DATA, File(legacyDir, display).absolutePath)
            }
        }
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values) ?: error("Ringtone save nahi hui")
        try {
            resolver.openOutputStream(uri, "w")?.use { out -> tmp.inputStream().use { it.copyTo(out) } }
                ?: error("Ringtone write nahi hui")
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            }
            RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, uri)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return Result(uri, display)
    }

    private fun MediaFormat.getIntegerOrDefault(key: String, def: Int): Int =
        runCatching { if (containsKey(key)) getInteger(key) else def }.getOrDefault(def)
}
