package com.deepecho.mobile.net

import com.deepecho.mobile.data.Song
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class LyricLine(val timeMs: Long, val text: String)
data class Lyrics(val synced: List<LyricLine>, val plain: String?)

/** Synced lyrics: LRCLIB (free, no key). */
object Lrclib {
    private val cache = ConcurrentHashMap<String, Lyrics>()
    private val NONE = Lyrics(emptyList(), null)

    private val noise = Regex(
        """\s*[\(\[][^)\]]*(official|video|audio|lyric|lyrics|lyrical|visuali[sz]er|\bhd\b|4k|\bmv\b|remaster|full song|music)[^)\]]*[\)\]]""",
        RegexOption.IGNORE_CASE
    )
    private val ts = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

    fun cleanTitle(t: String): String =
        t.replace(noise, "").replace(Regex("""\s*\|.*$"""), "").trim()

    private fun cleanArtist(a: String): String =
        a.replace(Regex("""(?i)\s*-\s*topic$|vevo$|\bofficial\b"""), "").trim()

    /** Returns (title, artist) guess. "Artist - Title" format handle karta hai. */
    fun guess(song: Song): Pair<String, String> {
        var title = cleanTitle(song.title)
        var artist = cleanArtist(song.artist)
        if (title.contains(" - ")) {
            val parts = title.split(" - ", limit = 2)
            artist = parts[0].trim()
            title = cleanTitle(parts[1])
        }
        return title to artist
    }

    fun fetch(song: Song): Lyrics? {
        cache[song.url]?.let { return if (it === NONE) null else it }
        val found = try { lookup(song) } catch (e: Exception) { null }
        if (found != null) cache[song.url] = found
        return found
    }

    private fun lookup(song: Song): Lyrics? {
        val (title, artist) = guess(song)

        // 1) exact match
        val exact = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .addQueryParameter("duration", song.durationSec.toString())
            .build()
        get(exact.toString())?.let { body ->
            parse(JSONObject(body))?.let { return it }
        }

        // 2) search
        val search = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", "$artist $title".trim())
            .build()
        val body = get(search.toString()) ?: return null
        val arr = JSONArray(body)
        var best: JSONObject? = null
        var bestDiff = Long.MAX_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optBoolean("instrumental")) continue
            val hasSynced = o.optString("syncedLyrics").isNotBlank()
            val diff = Math.abs(o.optDouble("duration", 0.0).toLong() - song.durationSec) + if (hasSynced) 0 else 1000
            if (diff < bestDiff) { bestDiff = diff; best = o }
        }
        return best?.let { if (bestDiff <= 8) parse(it) else null }
    }

    private fun get(url: String): String? {
        val req = Request.Builder().url(url).header("User-Agent", "DEEP-ECHO-Mobile/1.0").build()
        Net.client.newCall(req).execute().use { r ->
            return if (r.isSuccessful) r.body?.string() else null
        }
    }

    private fun parse(o: JSONObject): Lyrics? {
        val synced = o.optString("syncedLyrics")
        val plain = o.optString("plainLyrics").ifBlank { null }
        val lines = if (synced.isNotBlank()) parseLrc(synced) else emptyList()
        if (lines.isEmpty() && plain == null) return null
        return Lyrics(lines, plain)
    }

    fun parseLrc(lrc: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        for (raw in lrc.lines()) {
            val ms = ts.findAll(raw).toList()
            if (ms.isEmpty()) continue
            val text = raw.substring(ms.last().range.last + 1).trim().ifEmpty { "♪" }
            for (m in ms) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val milli = if (frac.isEmpty()) 0L else frac.padEnd(3, '0').take(3).toLong()
                out += LyricLine(min * 60_000 + sec * 1000 + milli, text)
            }
        }
        return out.sortedBy { it.timeMs }
    }
}
