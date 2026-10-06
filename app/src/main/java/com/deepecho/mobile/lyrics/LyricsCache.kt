package com.deepecho.mobile.lyrics

import android.content.Context
import com.deepecho.mobile.data.Song
import com.deepecho.mobile.net.LyricLine
import com.deepecho.mobile.net.LyricWord
import com.deepecho.mobile.net.Lyrics
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Variant-specific persistent cache for verified/repaired/generated lyrics. */
object LyricsCache {
    private const val ENGINE_VERSION = 2
    private lateinit var dir: File

    fun init(context: Context) {
        dir = File(context.filesDir, "lyrics_v2").apply { mkdirs() }
    }

    fun get(song: Song): Lyrics? {
        if (!::dir.isInitialized) return null
        val file = File(dir, fileName(song))
        if (!file.isFile) return null
        return runCatching {
            val o = JSONObject(file.readText())
            if (o.optInt("engine") != ENGINE_VERSION) return@runCatching null
            if (o.optLong("duration") != song.durationSec) return@runCatching null
            val lines = ArrayList<LyricLine>()
            val a = o.optJSONArray("lines") ?: JSONArray()
            for (i in 0 until a.length()) {
                val x = a.optJSONObject(i) ?: continue
                val words = ArrayList<LyricWord>()
                val wa = x.optJSONArray("words") ?: JSONArray()
                for (j in 0 until wa.length()) {
                    val w = wa.optJSONObject(j) ?: continue
                    words += LyricWord(w.optLong("t"), w.optString("x"))
                }
                lines += LyricLine(x.optLong("t"), x.optString("x"), words)
            }
            Lyrics(
                synced = lines,
                plain = o.optString("plain").ifBlank { null },
                estimatedSync = o.optBoolean("estimated"),
                confidence = o.optInt("confidence"),
                source = o.optString("source", "Local Cache • Verified"),
                metadataConfidence = o.optInt("metadataConfidence"),
                audioConfidence = o.optInt("audioConfidence"),
                alignmentConfidence = o.optInt("alignmentConfidence"),
                verification = o.optString("verification", "cached"),
                firstVocalOnsetMs = if (o.has("firstVocalOnsetMs")) o.optLong("firstVocalOnsetMs") else null,
                verified = o.optBoolean("verified"),
                variant = o.optString("variant").ifBlank { null }
            ).let { cached ->
                cached.copy(source = if (cached.verified) "Local Cache • Verified" else cached.source)
            }
        }.getOrNull()
    }

    fun put(song: Song, lyrics: Lyrics) {
        if (!::dir.isInitialized) return
        // Cache only results with useful timing/text; avoid persisting weak guesses as truth.
        if (lyrics.synced.isEmpty() && lyrics.plain.isNullOrBlank()) return
        if (!lyrics.verified && lyrics.alignmentConfidence < 50 && lyrics.audioConfidence < 50) return
        runCatching {
            val lines = JSONArray()
            lyrics.synced.take(360).forEach { line ->
                val words = JSONArray()
                line.words.take(120).forEach { word ->
                    words.put(JSONObject().put("t", word.timeMs).put("x", word.text))
                }
                lines.put(JSONObject().put("t", line.timeMs).put("x", line.text).put("words", words))
            }
            val o = JSONObject()
                .put("engine", ENGINE_VERSION)
                .put("duration", song.durationSec)
                .put("title", song.title)
                .put("artist", song.artist)
                .put("plain", lyrics.plain ?: "")
                .put("lines", lines)
                .put("estimated", lyrics.estimatedSync)
                .put("confidence", lyrics.confidence)
                .put("source", lyrics.source)
                .put("metadataConfidence", lyrics.metadataConfidence)
                .put("audioConfidence", lyrics.audioConfidence)
                .put("alignmentConfidence", lyrics.alignmentConfidence)
                .put("verification", lyrics.verification)
                .put("verified", lyrics.verified)
                .put("variant", lyrics.variant ?: "")
            lyrics.firstVocalOnsetMs?.let { o.put("firstVocalOnsetMs", it) }
            File(dir, fileName(song)).writeText(o.toString())
        }
    }

    fun invalidate(song: Song) {
        if (!::dir.isInitialized) return
        runCatching { File(dir, fileName(song)).delete() }
    }

    private fun fileName(song: Song): String {
        val raw = "${song.url}|${song.durationSec}|${song.title}|${song.artist}|v$ENGINE_VERSION"
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) } + ".json"
    }
}
