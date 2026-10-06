package com.deepecho.mobile.lyrics

import com.deepecho.mobile.data.Song
import com.deepecho.mobile.net.Lrclib
import com.deepecho.mobile.net.LyricLine
import com.deepecho.mobile.net.Lyrics
import com.deepecho.mobile.net.Net
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Provider adapter. It never decides final correctness by itself. */
object LrclibProvider {
    fun exact(song: Song, meta: NormalizedTrackMetadata): LyricsCandidate? {
        val artists = meta.performerCandidates.ifEmpty { listOf(meta.originalArtist) }.filter { it.isNotBlank() }
        for (artist in artists.take(3)) {
            val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", meta.coreTitle.ifBlank { song.title })
                .addQueryParameter("artist_name", artist)
                .addQueryParameter("duration", song.durationSec.toString())
                .build()
            val body = get(url.toString()) ?: continue
            val o = runCatching { JSONObject(body) }.getOrNull() ?: continue
            if (o.optBoolean("instrumental")) continue
            return candidateFromJson(o, song, meta, exact = true)
        }
        return null
    }

    fun search(song: Song, meta: NormalizedTrackMetadata): List<LyricsCandidate> {
        val queries = linkedSetOf<String>()
        val performers = meta.performerCandidates.ifEmpty { listOf(meta.originalArtist) }.filter { it.isNotBlank() }
        performers.take(3).forEach { performer ->
            queries += "$performer ${meta.coreTitle}".trim()
            if (!meta.originalTitle.equals(meta.coreTitle, true)) queries += "$performer ${meta.originalTitle}".trim()
        }
        queries += meta.coreTitle
        if (!meta.originalTitle.equals(meta.coreTitle, true)) queries += meta.originalTitle

        val raw = linkedMapOf<String, JSONObject>()
        for (q in queries.filter { it.length >= 2 }.take(7)) {
            val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder().addQueryParameter("q", q).build()
            val body = get(url.toString()) ?: continue
            val arr = runCatching { JSONArray(body) }.getOrNull() ?: continue
            for (i in 0 until arr.length().coerceAtMost(45)) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optBoolean("instrumental")) continue
                val key = o.optString("id").ifBlank {
                    "${o.optString("trackName")}|${o.optString("artistName")}|${o.optDouble("duration")}".lowercase()
                }
                raw.putIfAbsent(key, o)
            }
        }
        return raw.values.mapNotNull { candidateFromJson(it, song, meta, exact = false) }
            .sortedByDescending { it.score.total }
            .take(12)
    }

    /**
     * Search-tab probe: same matching/scoring contract as search(), but deliberately bounded to
     * a few lightweight queries so normal Search does not fan out across LRCLIB for every result.
     */
    fun searchAvailability(song: Song, meta: NormalizedTrackMetadata): List<LyricsCandidate> {
        val performers = meta.performerCandidates.ifEmpty { listOf(meta.originalArtist) }.filter { it.isNotBlank() }
        val queries = linkedSetOf<String>()
        performers.firstOrNull()?.let { performer ->
            queries += "$performer ${meta.coreTitle}".trim()
            if (!meta.originalTitle.equals(meta.coreTitle, true)) queries += "$performer ${meta.originalTitle}".trim()
        }
        queries += meta.coreTitle

        val raw = linkedMapOf<String, JSONObject>()
        for (q in queries.filter { it.length >= 2 }.take(3)) {
            val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder().addQueryParameter("q", q).build()
            val body = get(url.toString()) ?: continue
            val arr = runCatching { JSONArray(body) }.getOrNull() ?: continue
            for (i in 0 until arr.length().coerceAtMost(18)) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optBoolean("instrumental")) continue
                val key = o.optString("id").ifBlank {
                    "${o.optString("trackName")}|${o.optString("artistName")}|${o.optDouble("duration")}".lowercase()
                }
                raw.putIfAbsent(key, o)
            }
        }
        return raw.values.mapNotNull { candidateFromJson(it, song, meta, exact = false) }
            .sortedByDescending { it.score.total }
            .take(6)
    }

    fun lyricsOvh(song: Song, meta: NormalizedTrackMetadata): LyricsCandidate? {
        val artist = meta.performerCandidates.firstOrNull().orEmpty().ifBlank { meta.originalArtist }
        val title = meta.coreTitle.ifBlank { song.title }
        if (artist.isBlank() || title.isBlank()) return null
        val url = "https://api.lyrics.ovh".toHttpUrl().newBuilder()
            .addPathSegment("v1").addPathSegment(artist).addPathSegment(title).build()
        val body = get(url.toString()) ?: return null
        val plain = runCatching { JSONObject(body).optString("lyrics") }.getOrNull()?.trim().orEmpty()
        if (plain.isBlank()) return null
        val lyrics = Lyrics(
            synced = estimatePlainSync(plain, song.durationSec),
            plain = plain,
            estimatedSync = true,
            confidence = 40,
            source = "Lyrics.ovh • Unverified",
            metadataConfidence = 40,
            verification = "unverified",
            verified = false,
            variant = meta.qualifiers.joinToString(" + ").ifBlank { null }
        )
        return LyricsCandidate(
            provider = "Lyrics.ovh",
            title = title,
            artist = artist,
            album = null,
            durationSec = song.durationSec.toDouble(),
            lyrics = lyrics,
            score = CandidateScore(40, 50, 50, 50, 50, true, "plain fallback")
        )
    }

    private fun candidateFromJson(o: JSONObject, song: Song, meta: NormalizedTrackMetadata, exact: Boolean): LyricsCandidate? {
        val title = o.optString("trackName")
        val artist = o.optString("artistName")
        val duration = o.optDouble("duration", 0.0)
        val syncedRaw = o.optString("syncedLyrics")
        val plain = o.optString("plainLyrics").ifBlank { null }
        val parsed = if (syncedRaw.isNotBlank()) Lrclib.parseLrc(syncedRaw) else emptyList()
        if (parsed.isEmpty() && plain.isNullOrBlank()) return null

        val score = LyricsMetadata.scoreCandidate(meta, title, artist, duration, parsed.isNotEmpty())
        val safeAligned = if (parsed.isNotEmpty() && !score.suspicious && meta.qualifiers.isEmpty()) {
            conservativeDurationAlign(parsed, duration, song.durationSec)
        } else parsed
        val sync = if (safeAligned.isNotEmpty()) safeAligned else estimatePlainSync(plain.orEmpty(), song.durationSec)
        val source = if (exact && score.total >= 90 && !score.suspicious) "LRCLIB • Metadata verified" else "LRCLIB • Verifying"
        val lyrics = Lyrics(
            synced = sync,
            plain = plain,
            estimatedSync = parsed.isEmpty() && sync.isNotEmpty(),
            confidence = score.total,
            source = source,
            metadataConfidence = score.total,
            verification = if (score.suspicious) "metadata_suspicious" else "metadata_only",
            verified = exact && score.total >= 94 && !score.suspicious,
            variant = meta.qualifiers.joinToString(" + ").ifBlank { null }
        )
        return LyricsCandidate(
            provider = "LRCLIB",
            title = title,
            artist = artist,
            album = o.optString("albumName").ifBlank { null },
            durationSec = duration,
            lyrics = lyrics,
            score = score,
            exactLookup = exact,
            id = o.optString("id")
        )
    }

    private fun conservativeDurationAlign(lines: List<LyricLine>, sourceDurationSec: Double, targetDurationSec: Long): List<LyricLine> {
        if (sourceDurationSec <= 20 || targetDurationSec <= 20) return lines
        val diff = abs(sourceDurationSec - targetDurationSec)
        if (diff < 1.25 || diff > 5.5) return lines
        val ratio = (targetDurationSec / sourceDurationSec).coerceIn(0.975, 1.025)
        return lines.map { line ->
            line.copy(
                timeMs = (line.timeMs * ratio).toLong().coerceAtLeast(0L),
                words = line.words.map { it.copy(timeMs = (it.timeMs * ratio).toLong().coerceAtLeast(0L)) }
            )
        }
    }

    private fun estimatePlainSync(plain: String, durationSec: Long): List<LyricLine> {
        if (durationSec <= 15L) return emptyList()
        val lines = plain.lines().map { it.trim() }.filter { it.isNotBlank() }.take(260)
        if (lines.size < 2) return emptyList()
        val totalMs = durationSec * 1000L
        // Keep fallback deliberately conservative. Runtime/caption alignment will replace this when
        // actual vocal anchors are available; it is never labelled as verified.
        val intro = (totalMs * 0.035).toLong().coerceIn(1_500L, 8_000L)
        val outro = (totalMs * 0.025).toLong().coerceIn(1_000L, 6_000L)
        val usable = (totalMs - intro - outro).coerceAtLeast(lines.size * 700L)
        val weights = lines.map { line ->
            val words = Regex("""\S+""").findAll(line).count().coerceAtLeast(1)
            val punctuation = line.count { it in charArrayOf(',', ';', ':', '!', '?') }
            (words + punctuation * 0.5).coerceAtLeast(1.0)
        }
        val sum = weights.sum().coerceAtLeast(1.0)
        var cursor = intro.toDouble()
        return lines.mapIndexed { i, line ->
            val time = cursor.toLong().coerceIn(0L, totalMs - 1L)
            cursor += usable * (weights[i] / sum)
            LyricLine(time, line)
        }
    }

    private fun get(url: String): String? {
        val req = Request.Builder().url(url).header("User-Agent", "DEEP-ECHO-Mobile/1.12.2 UniversalLyricsV2").build()
        Net.client.newCall(req).execute().use { r -> return if (r.isSuccessful) r.body?.string() else null }
    }
}
