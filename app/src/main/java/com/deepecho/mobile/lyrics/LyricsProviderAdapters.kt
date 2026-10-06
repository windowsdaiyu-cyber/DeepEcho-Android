package com.deepecho.mobile.lyrics

import com.deepecho.mobile.data.Song
import com.deepecho.mobile.net.Lrclib
import com.deepecho.mobile.net.Lyrics
import com.deepecho.mobile.net.Net
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Free/open provider adapters used only after the existing LRCLIB path is exhausted.
 *
 * Active sources deliberately avoid API keys or scraping protected pages:
 *  - Better Lyrics public API: KuGou, QQ, and cache-readable TTML
 *  - Unison crowdsourced read API
 *
 * Genius/Musixmatch/SimpMusic-style scraping is intentionally not embedded here. If a provider
 * later offers a permitted stable endpoint, it can be added behind the same normalized contract.
 */
object OpenLyricsProviderResolver {
    private const val BL_BASE = "https://api.betterlyrics.org"
    private const val UNISON_BASE = "https://unison.betterlyrics.org"
    private const val PROVIDER_TIMEOUT_MS = 5_500L
    private const val FALLBACK_STAGE_TIMEOUT_MS = 7_000L

    private data class ProviderTask(val id: String, val run: () -> List<LyricsCandidate>)

    fun fetch(song: Song, meta: NormalizedTrackMetadata): List<LyricsCandidate> {
        if (!LyricsFeatureFlags.multiProviderFallback) return emptyList()

        // Do not fan out to every fallback blindly. The current LRCLIB path has already failed
        // before we get here, so try the cheapest/no-key synchronized pair first. If KuGou + QQ
        // independently agree strongly for a normal track, stop without touching secondary sources.
        val startedAt = System.currentTimeMillis()
        val primary = fetchTier(
            song,
            listOf(
                ProviderTask("kugou") { fetchBetterLyricsProvider(song, meta, "/kugou/getLyrics", "KuGou", "lrc") },
                ProviderTask("qq") { fetchBetterLyricsProvider(song, meta, "/qq/getLyrics", "QQ", "qrc") }
            ),
            3_800L
        )
        val rankedPrimary = rankAndApplyConsensus(primary, meta)
        val strongPrimaryConsensus = rankedPrimary.any { candidate ->
            !candidate.score.suspicious &&
                !candidate.requiresAudioVerification &&
                candidate.score.total >= 88 &&
                "cover" !in meta.qualifiers
        }
        if (strongPrimaryConsensus) {
            LyricsDiagnostics.add(song.url, "fallback stopped after KuGou/QQ consensus")
            return rankedPrimary
        }

        val elapsed = System.currentTimeMillis() - startedAt
        val remaining = (FALLBACK_STAGE_TIMEOUT_MS - elapsed).coerceAtLeast(1_200L)
        val secondary = fetchTier(
            song,
            listOf(
                ProviderTask("betterlyrics") { fetchBetterLyricsTtml(song, meta) },
                ProviderTask("unison") { fetchUnison(song, meta) }
            ),
            remaining
        )
        return rankAndApplyConsensus(primary + secondary, meta)
    }

    private fun fetchTier(song: Song, tasks: List<ProviderTask>, timeoutMs: Long): List<LyricsCandidate> {
        if (tasks.isEmpty()) return emptyList()
        val pool = Executors.newFixedThreadPool(minOf(2, tasks.size)) { r ->
            Thread(r, "DeepEchoLyricsProvider").apply { isDaemon = true }
        }
        return try {
            val futures = pool.invokeAll(tasks.map { task ->
                Callable {
                    LyricsDiagnostics.add(song.url, "provider tried: ${task.id}")
                    runCatching { task.run() }.getOrElse {
                        LyricsDiagnostics.add(song.url, "provider ${task.id} failed: ${it.javaClass.simpleName}")
                        emptyList()
                    }.also { results ->
                        LyricsDiagnostics.add(song.url, "provider result: ${task.id}=${results.size}")
                    }
                }
            }, timeoutMs, TimeUnit.MILLISECONDS)
            futures.flatMap { future ->
                if (future.isCancelled) emptyList() else runCatching { future.get() }.getOrDefault(emptyList())
            }
        } finally {
            pool.shutdownNow()
        }
    }

    /** Visible for pure unit tests; no network required. */
    fun rankAndApplyConsensus(input: List<LyricsCandidate>, meta: NormalizedTrackMetadata): List<LyricsCandidate> {
        if (input.isEmpty()) return emptyList()
        val distinct = input.distinctBy {
            "${it.provider.lowercase()}|${LyricsMetadata.normalizeText(it.title)}|${LyricsMetadata.normalizeText(it.artist)}|${textFingerprint(it)}"
        }
        val adjusted = distinct.map { candidate ->
            var confidence = candidate.providerConfidence.coerceIn(0, 100)
            var requiresAudio = candidate.requiresAudioVerification
            val ownText = candidateText(candidate)
            if (ownText.isNotBlank()) {
                val bestAgreement = distinct.asSequence()
                    .filter { it !== candidate && it.provider != candidate.provider }
                    .map { other -> LyricsMetadata.tokenSimilarity(ownText, candidateText(other)) }
                    .maxOrNull() ?: 0.0
                if (bestAgreement >= 0.78) {
                    confidence = maxOf(confidence, (82 + (bestAgreement - 0.78) * 70).roundToInt().coerceAtMost(94))
                    if (bestAgreement >= 0.86 && "cover" !in meta.qualifiers) requiresAudio = false
                }
            }
            if ("cover" in meta.qualifiers) requiresAudio = true
            val adjustedTotal = if (candidate.score.suspicious) {
                minOf(candidate.score.total, confidence)
            } else {
                maxOf(candidate.score.total, confidence).coerceAtMost(96)
            }
            candidate.copy(
                providerConfidence = confidence,
                requiresAudioVerification = requiresAudio,
                score = candidate.score.copy(total = adjustedTotal)
            )
        }
        return adjusted.sortedWith(
            compareByDescending<LyricsCandidate> { combinedRank(it) }
                .thenByDescending { it.lyrics.synced.count { line -> line.words.isNotEmpty() } }
                .thenByDescending { it.lyrics.synced.size }
        ).take(14)
    }

    /**
     * Pick verified-looking provider text as a spelling/reference authority without importing its
     * timestamps. For covers, wrong-performer text is allowed only when it strongly agrees with the
     * actual upload captions; this is how original-song text can help a faithful cover safely.
     */
    fun selectTextAuthority(
        candidates: List<LyricsCandidate>,
        meta: NormalizedTrackMetadata,
        captions: List<CaptionLine>
    ): String? {
        var best: Pair<Int, String>? = null
        for (candidate in candidates) {
            val text = candidateText(candidate)
            if (text.length < 12) continue
            var score = minOf(candidate.score.total, candidate.providerConfidence.takeIf { it > 0 } ?: 100)
            if (candidate.score.suspicious) score -= 25
            if (candidate.requiresAudioVerification) score -= 8

            if ("cover" in meta.qualifiers && captions.isNotEmpty()) {
                val agreement = LyricsAudioVerifier.verify(candidate.lyrics, captions).agreement
                if (agreement < 48) continue
                score = maxOf(score, agreement)
            } else if (candidate.score.suspicious) {
                continue
            }
            if (score >= 52 && (best?.first ?: Int.MIN_VALUE) < score) best = score to text
        }
        return best?.second
    }

    private fun fetchBetterLyricsProvider(
        song: Song,
        meta: NormalizedTrackMetadata,
        path: String,
        providerLabel: String,
        format: String
    ): List<LyricsCandidate> {
        val out = ArrayList<LyricsCandidate>()
        for ((title, artist) in queryPairs(meta).take(2)) {
            val url = (BL_BASE + path).toHttpUrl().newBuilder()
                .addQueryParameter("s", title)
                .addQueryParameter("a", artist)
                .addQueryParameter("d", song.durationSec.toString())
                .build()
            val response = httpGet(url.toString()) ?: continue
            if (response.code != 200) continue
            val o = runCatching { JSONObject(response.body) }.getOrNull() ?: continue
            val raw = o.optString("lyrics").trim()
            if (raw.isBlank()) continue
            val lines = when (format) {
                "lrc" -> Lrclib.parseLrc(raw)
                "qrc" -> LyricsFormatParsers.parseQrc(raw)
                else -> emptyList()
            }
            if (lines.isEmpty()) continue
            val plain = LyricsFormatParsers.plainFrom(lines)
            val score = LyricsMetadata.scoreCandidate(meta, title, artist, song.durationSec.toDouble(), lines.isNotEmpty())
            val providerConfidence = when (format) {
                "qrc" -> 84
                "lrc" -> 82
                else -> 78
            }
            out += LyricsCandidate(
                provider = providerLabel,
                title = title,
                artist = artist,
                album = null,
                durationSec = song.durationSec.toDouble(),
                lyrics = Lyrics(
                    synced = lines,
                    plain = plain,
                    estimatedSync = false,
                    confidence = minOf(score.total, providerConfidence),
                    source = "$providerLabel via BetterLyrics • Fallback",
                    metadataConfidence = minOf(score.total, providerConfidence),
                    verification = "provider_metadata_unverified",
                    verified = false,
                    variant = meta.qualifiers.joinToString(" + ").ifBlank { null }
                ),
                score = score.copy(total = minOf(score.total, providerConfidence)),
                exactLookup = false,
                id = "$providerLabel:${LyricsMetadata.normalizeText(title)}:${LyricsMetadata.normalizeText(artist)}",
                providerConfidence = providerConfidence,
                requiresAudioVerification = true
            )
            break
        }
        return out
    }

    private fun fetchBetterLyricsTtml(song: Song, meta: NormalizedTrackMetadata): List<LyricsCandidate> {
        val out = ArrayList<LyricsCandidate>()
        for ((title, artist) in queryPairs(meta).take(2)) {
            val builder = (BL_BASE + "/getLyrics").toHttpUrl().newBuilder()
                .addQueryParameter("s", title)
                .addQueryParameter("a", artist)
                .addQueryParameter("d", song.durationSec.toString())
            validVideoId(song)?.let { builder.addQueryParameter("videoId", it) }
            val response = httpGet(builder.build().toString()) ?: continue
            // Cache misses can legitimately be 401 without an API key. Skip; KuGou/QQ remain free.
            if (response.code != 200) continue
            val o = runCatching { JSONObject(response.body) }.getOrNull() ?: continue
            val raw = o.optString("ttml").trim()
            if (raw.isBlank()) continue
            val lines = LyricsFormatParsers.parseTtml(raw)
            if (lines.isEmpty()) continue
            val apiScore = when {
                o.has("score") -> (o.optDouble("score", 0.0) * 100.0).roundToInt().coerceIn(0, 100)
                else -> 82
            }
            val metadata = LyricsMetadata.scoreCandidate(meta, title, artist, song.durationSec.toDouble(), true)
            val confidence = minOf(metadata.total, maxOf(72, apiScore))
            out += LyricsCandidate(
                provider = "BetterLyrics",
                title = title,
                artist = artist,
                album = null,
                durationSec = song.durationSec.toDouble(),
                lyrics = Lyrics(
                    synced = lines,
                    plain = LyricsFormatParsers.plainFrom(lines),
                    estimatedSync = false,
                    confidence = confidence,
                    source = "BetterLyrics • TTML",
                    metadataConfidence = confidence,
                    verification = "betterlyrics_score_${apiScore}",
                    verified = false,
                    variant = meta.qualifiers.joinToString(" + ").ifBlank { null }
                ),
                score = metadata.copy(total = confidence),
                exactLookup = false,
                id = "BetterLyrics:${LyricsMetadata.normalizeText(title)}:${LyricsMetadata.normalizeText(artist)}",
                providerConfidence = apiScore.coerceAtLeast(72),
                requiresAudioVerification = apiScore < 90 || metadata.suspicious
            )
            break
        }
        return out
    }

    private fun fetchUnison(song: Song, meta: NormalizedTrackMetadata): List<LyricsCandidate> {
        val attempts = ArrayList<String>()
        validVideoId(song)?.let { id ->
            attempts += "$UNISON_BASE/lyrics".toHttpUrl().newBuilder().addQueryParameter("v", id).build().toString()
        }
        queryPairs(meta).firstOrNull()?.let { (title, artist) ->
            attempts += "$UNISON_BASE/lyrics".toHttpUrl().newBuilder()
                .addQueryParameter("song", title)
                .addQueryParameter("artist", artist)
                .addQueryParameter("duration", song.durationSec.toString())
                .build().toString()
        }
        for (url in attempts.distinct().take(2)) {
            val response = httpGet(url) ?: continue
            if (response.code != 200) continue
            val root = runCatching { JSONObject(response.body) }.getOrNull() ?: continue
            if (!root.optBoolean("success", false)) continue
            val data = root.optJSONObject("data") ?: continue
            val raw = data.optString("lyrics")
            val format = data.optString("format").lowercase()
            val lines = when (format) {
                "lrc" -> Lrclib.parseLrc(raw)
                "ttml" -> LyricsFormatParsers.parseTtml(raw)
                else -> emptyList()
            }
            val plain = when {
                lines.isNotEmpty() -> LyricsFormatParsers.plainFrom(lines)
                format == "plain" -> raw.trim().ifBlank { null }
                else -> null
            }
            if (lines.isEmpty() && plain.isNullOrBlank()) continue

            val title = data.optString("song").ifBlank { meta.coreTitle }
            val artist = data.optString("artist").ifBlank { meta.performerCandidates.firstOrNull().orEmpty() }
            val duration = data.optDouble("duration", song.durationSec.toDouble()).takeIf { it > 0 } ?: song.durationSec.toDouble()
            val score = LyricsMetadata.scoreCandidate(meta, title, artist, duration, lines.isNotEmpty())
            val communityConfidence = when (data.optString("confidence").lowercase()) {
                "high" -> 92
                "medium" -> 82
                "low" -> 68
                else -> 74
            }
            val total = minOf(score.total, communityConfidence)
            return listOf(
                LyricsCandidate(
                    provider = "Unison",
                    title = title,
                    artist = artist,
                    album = data.optString("album").ifBlank { null },
                    durationSec = duration,
                    lyrics = Lyrics(
                        synced = lines,
                        plain = plain,
                        estimatedSync = false,
                        confidence = total,
                        source = "Lyrics from Unison • unison.boidu.dev",
                        metadataConfidence = total,
                        verification = "unison_${data.optString("confidence").ifBlank { "unknown" }}",
                        verified = false,
                        variant = meta.qualifiers.joinToString(" + ").ifBlank { null }
                    ),
                    score = score.copy(total = total),
                    exactLookup = false,
                    id = "Unison:${data.optString("id")}",
                    providerConfidence = communityConfidence,
                    requiresAudioVerification = score.suspicious || communityConfidence < 82
                )
            )
        }
        return emptyList()
    }

    private fun queryPairs(meta: NormalizedTrackMetadata): List<Pair<String, String>> {
        val titleVariants = linkedSetOf(meta.coreTitle, meta.originalTitle)
            .map { it.trim() }.filter { it.length >= 2 }
        val performers = meta.performerCandidates.ifEmpty { listOf(meta.originalArtist) }
            .map { it.trim() }.filter { it.length >= 2 }
        val out = linkedSetOf<Pair<String, String>>()
        for (artist in performers.take(3)) for (title in titleVariants.take(2)) out += title to artist
        return out.toList()
    }

    private data class HttpResult(val code: Int, val body: String)

    private fun httpGet(url: String): HttpResult? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "DEEP-ECHO-Mobile/1.12.6 LyricsResolver")
            .header("Accept", "application/json, text/plain;q=0.9, */*;q=0.5")
            .build()
        val call = Net.client.newCall(req)
        call.timeout().timeout(PROVIDER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return runCatching {
            call.execute().use { response -> HttpResult(response.code, response.body?.string().orEmpty()) }
        }.getOrNull()
    }

    private fun validVideoId(song: Song): String? {
        if (!song.url.contains("youtube", true) && !song.url.contains("youtu.be", true)) return null
        return song.videoId.takeIf { it.matches(Regex("""[A-Za-z0-9_-]{11}""")) }
    }

    private fun candidateText(candidate: LyricsCandidate): String = candidate.lyrics.plain
        ?.takeIf { it.isNotBlank() }
        ?: candidate.lyrics.synced.joinToString("\n") { it.text }

    private fun textFingerprint(candidate: LyricsCandidate): String = LyricsMetadata.normalizeText(candidateText(candidate)).take(160)

    private fun combinedRank(candidate: LyricsCandidate): Int {
        val syncBonus = when {
            candidate.lyrics.synced.any { it.words.isNotEmpty() } -> 8
            candidate.lyrics.synced.size >= 2 -> 5
            !candidate.lyrics.plain.isNullOrBlank() -> 1
            else -> 0
        }
        val audioPenalty = if (candidate.requiresAudioVerification) 5 else 0
        return candidate.score.total + candidate.providerConfidence / 5 + syncBonus - audioPenalty
    }
}
