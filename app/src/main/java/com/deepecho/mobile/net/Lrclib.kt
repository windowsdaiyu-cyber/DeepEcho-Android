package com.deepecho.mobile.net

import com.deepecho.mobile.data.Song
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

data class LyricWord(val timeMs: Long, val text: String)
data class LyricLine(val timeMs: Long, val text: String, val words: List<LyricWord> = emptyList())
data class Lyrics(
    val synced: List<LyricLine>,
    val plain: String?,
    val estimatedSync: Boolean = false,
    val confidence: Int = 0,
    val source: String = "LRCLIB"
)

/**
 * DeepEcho lyrics resolver.
 *
 * Coverage strategy:
 * 1) strong exact LRCLIB lookup,
 * 2) several metadata-normalized LRCLIB searches with title/artist/duration scoring,
 * 3) Lyrics.ovh plain-text fallback,
 * 4) conservative smart timing for plain-only lyrics so line + word animation can still work.
 *
 * No source is capable of guaranteeing 99% perfect lyrics/timing, so low-confidence results are
 * explicitly marked as estimated instead of pretending that generated timestamps are exact.
 */
object Lrclib {
    private data class CacheEntry(val at: Long, val lyrics: Lyrics?)
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private const val HIT_TTL = 6 * 60 * 60_000L
    private const val MISS_TTL = 20 * 60_000L

    private val noise = Regex(
        """\s*[\(\[][^)\]]*(official|video|audio|lyric|lyrics|lyrical|visuali[sz]er|\bhd\b|4k|\bmv\b|remaster|full song|music)[^)\]]*[\)\]]""",
        RegexOption.IGNORE_CASE
    )
    private val versionNoise = Regex(
        """(?i)\s*[\(\[]?(slowed\s*\+?\s*reverb|slowed|sped\s*up|nightcore|8d\s*audio|lyrics?|official\s*(audio|video)?)[\)\]]?\s*"""
    )
    private val ts = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val wordTs = Regex("""<(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?>""")

    fun cleanTitle(t: String): String = t
        .replace(noise, "")
        .replace(Regex("""\s*\|.*$"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '-', '|')

    private fun cleanArtist(a: String): String = a
        .replace(Regex("""(?i)\s*-\s*topic$|vevo$|\bofficial\b"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()

    /** Returns (title, artist) guess. "Artist - Title" format is handled. */
    fun guess(song: Song): Pair<String, String> {
        var title = cleanTitle(song.title)
        var artist = cleanArtist(song.artist)
        if (title.contains(" - ")) {
            val parts = title.split(" - ", limit = 2)
            val left = cleanArtist(parts[0])
            val right = cleanTitle(parts[1])
            // Only flip when the uploader/artist is empty or resembles the left side.
            if (artist.isBlank() || tokenSimilarity(artist, left) >= 0.45) {
                artist = left.ifBlank { artist }
                title = right
            }
        }
        return title to artist
    }

    fun fetch(song: Song, forceFresh: Boolean = false): Lyrics? {
        val now = System.currentTimeMillis()
        if (!forceFresh) {
            cache[song.url]?.let { entry ->
                val ttl = if (entry.lyrics == null) MISS_TTL else HIT_TTL
                if (now - entry.at < ttl) return entry.lyrics
            }
        }
        val found = try { lookup(song) } catch (_: Exception) { null }
        cache[song.url] = CacheEntry(now, found)
        return found
    }

    private fun lookup(song: Song): Lyrics? {
        val (title, artist) = guess(song)
        if (title.isBlank()) return null

        // 1) Exact LRCLIB path first: fastest + highest confidence.
        if (artist.isNotBlank()) {
            val exact = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", title)
                .addQueryParameter("artist_name", artist)
                .addQueryParameter("duration", song.durationSec.toString())
                .build()
            get(exact.toString())?.let { body ->
                runCatching { JSONObject(body) }.getOrNull()?.let { json ->
                    parse(json, song.durationSec, confidence = 100)?.let { return it }
                }
            }
        }

        // 2) Broader metadata variants. We collect candidates and score title + artist + duration,
        // strongly preferring real synchronized lyrics.
        val titleVariants = linkedSetOf(title)
        val simplifiedTitle = title.replace(versionNoise, " ").replace(Regex("""\s+"""), " ").trim()
        if (simplifiedTitle.length >= 2) titleVariants += simplifiedTitle
        title.replace(Regex("""(?i)\s+(feat\.|ft\.).*$"""), "").trim()
            .takeIf { it.length >= 2 }?.let(titleVariants::add)

        val artistVariants = linkedSetOf(artist).filter { it.isNotBlank() }.toMutableList()
        artist.split(',', '&', '×').firstOrNull()?.trim()?.takeIf { it.length >= 2 }?.let {
            if (artistVariants.none { existing -> existing.equals(it, true) }) artistVariants += it
        }

        val queries = linkedSetOf<String>()
        for (t in titleVariants.take(3)) {
            if (artistVariants.isNotEmpty()) queries += "${artistVariants.first()} $t".trim()
            queries += t
        }

        val candidates = linkedMapOf<String, JSONObject>()
        for (q in queries.take(4)) {
            val search = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", q)
                .build()
            val body = get(search.toString()) ?: continue
            val arr = runCatching { JSONArray(body) }.getOrNull() ?: continue
            for (i in 0 until arr.length().coerceAtMost(35)) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optBoolean("instrumental")) continue
                val key = o.optString("id").ifBlank {
                    "${o.optString("trackName")}|${o.optString("artistName")}|${o.optDouble("duration")}".lowercase()
                }
                candidates.putIfAbsent(key, o)
            }
        }

        val scored = candidates.values.map { candidate ->
            candidate to scoreCandidate(candidate, title, artist, song.durationSec)
        }.sortedByDescending { it.second }

        for ((candidate, score) in scored.take(5)) {
            if (score < 44.0) continue
            val confidence = score.toInt().coerceIn(1, 99)
            parse(candidate, song.durationSec, confidence)?.let { return it }
        }

        // 3) Plain-text fallback. This increases coverage; timing is intentionally marked estimated.
        if (artist.isNotBlank()) {
            lyricsOvh(artistVariants.firstOrNull() ?: artist, simplifiedTitle.ifBlank { title }, song.durationSec)?.let { return it }
        }
        return null
    }

    private fun scoreCandidate(o: JSONObject, wantedTitle: String, wantedArtist: String, wantedDuration: Long): Double {
        val gotTitle = o.optString("trackName")
        val gotArtist = o.optString("artistName")
        val titleScore = tokenSimilarity(wantedTitle, gotTitle)
        val artistScore = if (wantedArtist.isBlank()) 0.65 else tokenSimilarity(wantedArtist, gotArtist)
        val duration = o.optDouble("duration", 0.0)
        val diff = if (duration > 0 && wantedDuration > 0) abs(duration - wantedDuration) else 999.0
        val durationScore = when {
            diff <= 2.0 -> 1.0
            diff <= 5.0 -> 0.9
            diff <= 9.0 -> 0.72
            diff <= 15.0 -> 0.45
            diff <= 25.0 -> 0.20
            else -> 0.0
        }
        val syncedBonus = if (o.optString("syncedLyrics").isNotBlank()) 12.0 else 0.0
        val plainBonus = if (o.optString("plainLyrics").isNotBlank()) 3.0 else 0.0
        return titleScore * 53.0 + artistScore * 24.0 + durationScore * 18.0 + syncedBonus + plainBonus
    }

    private fun tokenSimilarity(a: String, b: String): Double {
        val aa = normalize(a)
        val bb = normalize(b)
        if (aa.isBlank() || bb.isBlank()) return 0.0
        if (aa == bb) return 1.0
        if (aa.contains(bb) || bb.contains(aa)) return 0.88
        val at = aa.split(' ').filter { it.length > 1 }.toSet()
        val bt = bb.split(' ').filter { it.length > 1 }.toSet()
        if (at.isEmpty() || bt.isEmpty()) return 0.0
        val intersection = at.intersect(bt).size.toDouble()
        val union = at.union(bt).size.toDouble().coerceAtLeast(1.0)
        return intersection / union
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun lyricsOvh(artist: String, title: String, durationSec: Long): Lyrics? {
        val url = "https://api.lyrics.ovh".toHttpUrl().newBuilder()
            .addPathSegment("v1")
            .addPathSegment(artist)
            .addPathSegment(title)
            .build()
        val body = get(url.toString()) ?: return null
        val plain = runCatching { JSONObject(body).optString("lyrics") }.getOrNull()?.trim().orEmpty()
        if (plain.isBlank()) return null
        val synced = estimatePlainSync(plain, durationSec)
        return Lyrics(
            synced = synced,
            plain = plain,
            estimatedSync = synced.isNotEmpty(),
            confidence = 42,
            source = "Lyrics.ovh"
        )
    }

    private fun get(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "DEEP-ECHO-Mobile/1.10.1")
            .build()
        Net.client.newCall(req).execute().use { r ->
            return if (r.isSuccessful) r.body?.string() else null
        }
    }

    private fun parse(o: JSONObject, durationSec: Long, confidence: Int): Lyrics? {
        val syncedRaw = o.optString("syncedLyrics")
        val plain = o.optString("plainLyrics").ifBlank { null }
        val parsed = if (syncedRaw.isNotBlank()) parseLrc(syncedRaw) else emptyList()
        if (parsed.isNotEmpty()) {
            return Lyrics(parsed, plain, estimatedSync = false, confidence = confidence, source = "LRCLIB")
        }
        if (plain.isNullOrBlank()) return null
        val estimated = estimatePlainSync(plain, durationSec)
        return Lyrics(
            synced = estimated,
            plain = plain,
            estimatedSync = estimated.isNotEmpty(),
            confidence = confidence.coerceAtMost(68),
            source = "LRCLIB"
        )
    }

    /** Standard LRC + enhanced-LRC word timestamp support (<mm:ss.xx>word). */
    fun parseLrc(lrc: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        for (raw in lrc.lines()) {
            val lineTimes = ts.findAll(raw).toList()
            if (lineTimes.isEmpty()) continue
            val payload = raw.substring(lineTimes.last().range.last + 1).trim()
            val words = parseEnhancedWords(payload)
            val cleanText = if (words.isNotEmpty()) {
                payload.replace(wordTs, "").replace(Regex("""\s+"""), " ").trim()
            } else payload
            val text = cleanText.ifEmpty { "♪" }
            for (m in lineTimes) {
                val lineMs = stampToMs(m.groupValues[1], m.groupValues[2], m.groupValues[3])
                out += LyricLine(lineMs, text, words)
            }
        }
        return out.sortedBy { it.timeMs }.distinctBy { it.timeMs to it.text }
    }

    private fun parseEnhancedWords(payload: String): List<LyricWord> {
        val matches = wordTs.findAll(payload).toList()
        if (matches.isEmpty()) return emptyList()
        val out = ArrayList<LyricWord>()
        matches.forEachIndexed { index, match ->
            val from = match.range.last + 1
            val to = matches.getOrNull(index + 1)?.range?.first ?: payload.length
            val word = payload.substring(from, to).replace(wordTs, "").trim()
            if (word.isNotBlank()) {
                out += LyricWord(
                    timeMs = stampToMs(match.groupValues[1], match.groupValues[2], match.groupValues[3]),
                    text = word
                )
            }
        }
        return out
    }

    private fun stampToMs(min: String, sec: String, frac: String): Long {
        val milli = if (frac.isEmpty()) 0L else frac.padEnd(3, '0').take(3).toLong()
        return min.toLong() * 60_000L + sec.toLong() * 1000L + milli
    }

    /**
     * Conservative plain-lyrics timing. It is a fallback only; real LRC always wins.
     * Line duration is weighted by words/punctuation and a small intro/outro reserve is kept.
     */
    private fun estimatePlainSync(plain: String, durationSec: Long): List<LyricLine> {
        if (durationSec <= 15L) return emptyList()
        val lines = plain.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.matches(Regex("""^\[[^]]{1,40}]$""")) }
            .take(260)
        if (lines.size < 2) return emptyList()

        val totalMs = durationSec * 1000L
        val intro = (totalMs * 0.035).toLong().coerceIn(1_500L, 9_000L)
        val outro = (totalMs * 0.025).toLong().coerceIn(1_000L, 7_000L)
        val usable = (totalMs - intro - outro).coerceAtLeast(lines.size * 700L)
        val weights = lines.map { line ->
            val wordCount = Regex("""\S+""").findAll(line).count().coerceAtLeast(1)
            val punctuation = line.count { it == ',' || it == ';' || it == ':' || it == '!' || it == '?' }
            (wordCount * 1.0 + punctuation * 0.55 + if (line.endsWith("…")) 0.8 else 0.0).coerceAtLeast(1.0)
        }
        val sum = weights.sum().coerceAtLeast(1.0)
        var cursor = intro.toDouble()
        return lines.mapIndexed { index, line ->
            val time = cursor.toLong().coerceIn(0L, totalMs - 1L)
            cursor += usable * (weights[index] / sum)
            LyricLine(time, line)
        }
    }
}
