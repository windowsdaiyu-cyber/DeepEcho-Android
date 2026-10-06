package com.deepecho.mobile.lyrics

import com.deepecho.mobile.data.Song
import com.deepecho.mobile.net.LyricLine
import com.deepecho.mobile.net.Lyrics
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight discovery/index layer for Search -> Lyrics.
 *
 * This object never invokes Exact Lyrics, OCR, ML Kit, video-frame extraction, or the manual
 * recovery pipeline. It only reuses already cached lyrics plus lightweight provider/caption
 * evidence and keeps the existing lyrics/sync engine as the playback-time authority.
 */
enum class LyricsAvailabilityState {
    VERIFIED_WORD_SYNC,
    VERIFIED_SYNCED,
    VERIFIED_TEXT,
    VERIFIED_CAPTIONS,
    CACHED_EXACT_LYRICS,
    UNVERIFIED_RECOVERABLE,
    UNAVAILABLE;

    val deliverable: Boolean
        get() = this == VERIFIED_WORD_SYNC || this == VERIFIED_SYNCED ||
            this == VERIFIED_TEXT || this == VERIFIED_CAPTIONS || this == CACHED_EXACT_LYRICS
}

data class LyricsResolutionDescriptor(
    val trackIdentity: String,
    val playbackSourceIdentity: String,
    val availabilityState: LyricsAvailabilityState,
    val sourceType: String,
    val sourceProvider: String,
    val sourceIdentity: String,
    val textConfidence: Int,
    val timingConfidence: Int,
    val hasLineTiming: Boolean,
    val hasWordTiming: Boolean,
    val language: String? = null,
    val script: String? = null,
    val variantIdentity: String? = null,
    val cacheIdentity: String,
    val lyrics: Lyrics
) {
    val badge: String
        get() = when (availabilityState) {
            LyricsAvailabilityState.VERIFIED_WORD_SYNC -> "WORD SYNC"
            LyricsAvailabilityState.VERIFIED_SYNCED -> "SYNCED LYRICS"
            LyricsAvailabilityState.VERIFIED_TEXT -> "LYRICS READY"
            LyricsAvailabilityState.VERIFIED_CAPTIONS -> "CAPTIONS"
            LyricsAvailabilityState.CACHED_EXACT_LYRICS -> "EXACT LYRICS"
            LyricsAvailabilityState.UNVERIFIED_RECOVERABLE -> "CAN TRY EXACT LYRICS"
            LyricsAvailabilityState.UNAVAILABLE -> "UNAVAILABLE"
        }
}

data class LyricsSearchResult(val song: Song, val descriptor: LyricsResolutionDescriptor)

object LyricsSearchAvailabilityIndex {
    private data class Entry(val at: Long, val value: LyricsSearchResult?)
    private data class Attachment(val at: Long, val descriptor: LyricsResolutionDescriptor)

    private val index = ConcurrentHashMap<String, Entry>()
    private val playbackAttachments = ConcurrentHashMap<String, Attachment>()
    private const val HIT_TTL_MS = 6 * 60 * 60_000L
    private const val MISS_TTL_MS = 8 * 60_000L
    private const val ATTACHMENT_TTL_MS = 30 * 60_000L

    /** Cache-only check used to populate the Lyrics tab immediately without network work. */
    fun cached(song: Song): LyricsSearchResult? {
        val now = System.currentTimeMillis()
        val existing = index[key(song)]
        if (existing?.value != null && now - existing.at < HIT_TTL_MS) return existing.value

        // Check the persistent lyrics cache even after a recent availability miss. A manual
        // Exact Lyrics/Retry may have succeeded since that miss and must become discoverable now.
        LyricsCache.get(song)?.let { persisted ->
            return describeCached(song, persisted).also { result -> index[key(song)] = Entry(now, result) }
        }
        if (existing != null && existing.value == null && now - existing.at < MISS_TTL_MS) return null
        return null
    }

    /**
     * Probe one result. This is intended for a small set of visible/top Search results only.
     * No OCR/transcription recovery is reachable from this method.
     */
    fun probe(song: Song, forceFresh: Boolean = false): LyricsSearchResult? {
        val now = System.currentTimeMillis()
        if (!forceFresh) {
            cached(song)?.let { return it }
            index[key(song)]?.let { entry -> if (now - entry.at < MISS_TTL_MS) return entry.value }
        }

        val meta = LyricsMetadata.normalize(song)
        val providerCandidates = ArrayList<LyricsCandidate>()

        val exact = runCatching { LrclibProvider.exact(song, meta) }.getOrNull()
        if (exact != null) {
            providerCandidates += exact
            describeProviderCandidate(song, meta, exact)?.let { ready ->
                index[key(song)] = Entry(now, ready)
                return ready
            }
        }

        // Search-specific LRCLIB fallback intentionally uses fewer queries/results than playback's
        // full resolver so the normal Search experience is not blocked by availability checks.
        val lrclibAlternatives = runCatching { LrclibProvider.searchAvailability(song, meta) }
            .getOrDefault(emptyList())
        providerCandidates += lrclibAlternatives
        lrclibAlternatives.firstNotNullOfOrNull { describeProviderCandidate(song, meta, it) }?.let { ready ->
            index[key(song)] = Entry(now, ready)
            return ready
        }

        val openFallbacks = runCatching { OpenLyricsProviderResolver.fetch(song, meta) }
            .getOrDefault(emptyList())
        providerCandidates += openFallbacks
        openFallbacks.firstNotNullOfOrNull { describeProviderCandidate(song, meta, it) }?.let { ready ->
            index[key(song)] = Entry(now, ready)
            return ready
        }

        // Captions belong to the exact playback upload. Fetching subtitle text is lightweight
        // compared with OCR/transcription and lets us verify variants/covers without borrowing
        // another recording's timestamps.
        val captions = if (song.url.startsWith("http", true)) {
            runCatching { CaptionResolver.resolve(song) }.getOrDefault(emptyList())
        } else emptyList()

        if (captions.size >= 2) {
            // If provider text exists but its timing belongs to another recording, verify the text
            // against this upload and let the existing DeepEcho alignment engine rebuild timing.
            val grounded = providerCandidates.asSequence()
                .filter { !it.score.suspicious && it.score.total >= 58 }
                .mapNotNull { candidate ->
                    val verification = LyricsAudioVerifier.verify(candidate.lyrics, captions)
                    if (verification.suspicious || verification.agreement < 48) return@mapNotNull null
                    val aligned = if (candidate.lyrics.synced.size >= 2) {
                        LyricsAlignmentEngine.align(candidate.lyrics, captions)?.takeIf { it.confidence >= 48 }?.lyrics
                    } else null
                    val lyrics = aligned ?: if (captionsAreReliable(captions)) {
                        LyricsAlignmentEngine.fromCaptions(captions, candidate.lyrics.plain)
                    } else null
                    lyrics?.let { candidate to it }
                }
                .maxByOrNull { (candidate, lyrics) ->
                    candidate.score.total + maxOf(lyrics.alignmentConfidence, lyrics.audioConfidence) / 3
                }

            if (grounded != null) {
                val (candidate, lyrics) = grounded
                val ready = describeLyrics(
                    song = song,
                    meta = meta,
                    lyrics = lyrics,
                    sourceType = "provider+captions",
                    sourceProvider = candidate.provider,
                    sourceIdentity = candidate.id.ifBlank { "${candidate.provider}:${candidate.title}:${candidate.artist}" },
                    forcedState = null,
                    language = captions.firstOrNull { it.languageTag.isNotBlank() }?.languageTag
                )
                index[key(song)] = Entry(now, ready)
                return ready
            }

            // Manual/non-auto captions can be a deliverable source on their own. Auto-generated
            // captions alone are not promoted to "Lyrics Ready" without provider agreement.
            if (captionsAreReliable(captions)) {
                val lyrics = LyricsAlignmentEngine.fromCaptions(captions)?.copy(
                    source = "YouTube Captions • Search verified",
                    verification = "search_verified_captions",
                    verified = true
                )
                if (lyrics != null) {
                    val ready = describeLyrics(
                        song = song,
                        meta = meta,
                        lyrics = lyrics,
                        sourceType = "captions",
                        sourceProvider = "YouTube",
                        sourceIdentity = song.videoId,
                        forcedState = LyricsAvailabilityState.VERIFIED_CAPTIONS,
                        language = captions.firstOrNull { it.languageTag.isNotBlank() }?.languageTag
                    )
                    index[key(song)] = Entry(now, ready)
                    return ready
                }
            }
        }

        index[key(song)] = Entry(now, null)
        return null
    }

    /** Called immediately before normal playback when the user taps a Lyrics result. */
    fun attachForPlayback(result: LyricsSearchResult) {
        val descriptor = result.descriptor
        if (!descriptor.availabilityState.deliverable) return
        if (descriptor.playbackSourceIdentity != result.song.url) return
        playbackAttachments[result.song.url] = Attachment(System.currentTimeMillis(), descriptor)
    }

    /** Playback-time fallback. The regular current provider is still tried first by the orchestrator. */
    fun attachedForPlayback(song: Song): LyricsResolutionDescriptor? {
        val attachment = playbackAttachments[song.url] ?: return null
        if (System.currentTimeMillis() - attachment.at > ATTACHMENT_TTL_MS) {
            playbackAttachments.remove(song.url)
            return null
        }
        val d = attachment.descriptor
        if (!d.availabilityState.deliverable || d.playbackSourceIdentity != song.url) return null
        if (d.cacheIdentity != cacheIdentity(song)) return null
        return d
    }

    fun rankForQuery(results: List<LyricsSearchResult>, query: String): List<LyricsSearchResult> {
        val q = LyricsMetadata.normalizeText(query)
        return results.distinctBy { it.song.url }.sortedByDescending { result ->
            val d = result.descriptor
            val title = LyricsMetadata.normalizeText(result.song.title)
            val artist = LyricsMetadata.normalizeText(result.song.artist)
            val titleSimilarity = (maxOf(
                LyricsMetadata.tokenSimilarity(q, title),
                LyricsMetadata.tokenSimilarity(q, "$title $artist")
            ) * 100).toInt()
            val artistBonus = if (q.isNotBlank() && (artist.contains(q) || q.contains(artist))) 12 else 0
            val state = when (d.availabilityState) {
                LyricsAvailabilityState.VERIFIED_WORD_SYNC -> 65
                LyricsAvailabilityState.VERIFIED_SYNCED -> 58
                LyricsAvailabilityState.VERIFIED_CAPTIONS -> 52
                LyricsAvailabilityState.CACHED_EXACT_LYRICS -> 50
                LyricsAvailabilityState.VERIFIED_TEXT -> 44
                else -> 0
            }
            state + titleSimilarity + artistBonus + d.textConfidence / 4 + d.timingConfidence / 5
        }
    }

    /** Pure policy helper used by tests. */
    fun describeProviderCandidate(
        song: Song,
        meta: NormalizedTrackMetadata,
        candidate: LyricsCandidate
    ): LyricsSearchResult? {
        val exceptionallyStrongGeneric = meta.genericTitle && candidate.exactLookup &&
            candidate.score.title >= 96 && candidate.score.performer >= 92 &&
            candidate.score.duration >= 90 && candidate.score.total >= 90
        if (candidate.score.suspicious && !exceptionallyStrongGeneric) return null
        if (candidate.score.title < 86 || candidate.score.total < 86) return null
        if (meta.performerCandidates.isNotEmpty() && candidate.score.performer < 72) return null
        if (song.durationSec > 20 && candidate.durationSec > 20 && candidate.score.duration < 58) return null

        // Provider timing cannot establish a cover/live/remix/slowed/sped-up/music-video version
        // by itself. It may still be used later as text reference if captions from THIS upload agree.
        if (requiresCurrentUploadGrounding(song, meta, candidate)) return null

        val sourceSafe = candidate.exactLookup && candidate.score.total >= 90 ||
            (!candidate.requiresAudioVerification && candidate.score.total >= 88)
        if (!sourceSafe) return null

        val candidateLyrics = if (candidate.lyrics.synced.isNotEmpty() && !candidate.lyrics.estimatedSync) {
            candidate.lyrics.copy(
                source = "${candidate.provider} • Search verified",
                verification = "search_provider_verified",
                verified = candidate.exactLookup && candidate.score.total >= 92,
                metadataConfidence = maxOf(candidate.lyrics.metadataConfidence, candidate.score.total),
                confidence = maxOf(candidate.lyrics.confidence, candidate.score.total)
            )
        } else {
            candidate.lyrics.copy(
                synced = emptyList(),
                estimatedSync = false,
                source = "${candidate.provider} • Search text verified",
                verification = "search_provider_text_verified",
                verified = false,
                metadataConfidence = maxOf(candidate.lyrics.metadataConfidence, candidate.score.total),
                confidence = maxOf(candidate.lyrics.confidence, candidate.score.total),
                alignmentConfidence = 0,
                audioConfidence = 0
            )
        }
        return describeLyrics(
            song = song,
            meta = meta,
            lyrics = candidateLyrics,
            sourceType = "provider",
            sourceProvider = candidate.provider,
            sourceIdentity = candidate.id.ifBlank { "${candidate.provider}:${candidate.title}:${candidate.artist}" },
            forcedState = null
        )
    }

    private fun describeCached(song: Song, lyrics: Lyrics): LyricsSearchResult {
        val meta = LyricsMetadata.normalize(song)
        val isExact = lyrics.source.contains("Exact Lyrics", true) ||
            lyrics.verification.contains("video_ocr", true) ||
            lyrics.verification.contains("lyric_video", true)
        return describeLyrics(
            song = song,
            meta = meta,
            lyrics = lyrics,
            sourceType = "cache",
            sourceProvider = lyrics.source.substringBefore('•').trim().ifBlank { "DeepEcho Cache" },
            sourceIdentity = lyrics.verification,
            forcedState = if (isExact) LyricsAvailabilityState.CACHED_EXACT_LYRICS else null
        )
    }

    private fun describeLyrics(
        song: Song,
        meta: NormalizedTrackMetadata,
        lyrics: Lyrics,
        sourceType: String,
        sourceProvider: String,
        sourceIdentity: String,
        forcedState: LyricsAvailabilityState?,
        language: String? = null
    ): LyricsSearchResult {
        val hasWords = lyrics.synced.any { it.words.isNotEmpty() }
        val hasLines = lyrics.synced.size >= 2
        val state = forcedState ?: when {
            hasWords -> LyricsAvailabilityState.VERIFIED_WORD_SYNC
            hasLines -> LyricsAvailabilityState.VERIFIED_SYNCED
            !lyrics.plain.isNullOrBlank() -> LyricsAvailabilityState.VERIFIED_TEXT
            else -> LyricsAvailabilityState.UNAVAILABLE
        }
        val text = lyrics.plain ?: lyrics.synced.joinToString(" ") { it.text }
        val timing = if (hasLines) {
            maxOf(lyrics.alignmentConfidence, lyrics.audioConfidence, if (lyrics.verified) 72 else lyrics.confidence)
        } else 0
        return LyricsSearchResult(
            song = song,
            descriptor = LyricsResolutionDescriptor(
                trackIdentity = trackIdentity(song, meta),
                playbackSourceIdentity = song.url,
                availabilityState = state,
                sourceType = sourceType,
                sourceProvider = sourceProvider,
                sourceIdentity = sourceIdentity,
                textConfidence = maxOf(lyrics.confidence, lyrics.metadataConfidence).coerceIn(0, 100),
                timingConfidence = timing.coerceIn(0, 100),
                hasLineTiming = hasLines,
                hasWordTiming = hasWords,
                language = language,
                script = detectScript(text),
                variantIdentity = meta.qualifiers.joinToString(" + ").ifBlank { null },
                cacheIdentity = cacheIdentity(song),
                lyrics = lyrics
            )
        )
    }

    private fun requiresCurrentUploadGrounding(
        song: Song,
        meta: NormalizedTrackMetadata,
        candidate: LyricsCandidate
    ): Boolean {
        if (candidate.requiresAudioVerification || meta.qualifiers.isNotEmpty()) return true
        val original = meta.originalTitle.lowercase()
        val explicitVideoVariant = listOf(
            "official video", "music video", "lyric video", "lyrics video", "visualizer", "live video"
        ).any { original.contains(it) }
        if (explicitVideoVariant) return true
        if (candidate.durationSec > 20 && song.durationSec > 20) {
            val diff = kotlin.math.abs(candidate.durationSec - song.durationSec.toDouble())
            if (diff > 5.5) return true
        }
        return false
    }

    private fun captionsAreReliable(captions: List<CaptionLine>): Boolean {
        if (captions.size < 3) return false
        val meaningful = captions.filter { LyricsMetadata.normalizeText(it.text).length >= 2 }
        if (meaningful.size < 3) return false
        val manual = meaningful.count { !it.autoGenerated }
        if (manual < 2 || manual * 100 / meaningful.size < 60) return false
        val normalized = meaningful.map { LyricsMetadata.normalizeText(it.text) }.filter { it.isNotBlank() }
        return normalized.distinct().size >= 3 && normalized.sumOf { it.length } >= 28
    }

    private fun trackIdentity(song: Song, meta: NormalizedTrackMetadata): String = buildString {
        append(LyricsMetadata.normalizeText(meta.coreTitle))
        append('|')
        append(meta.performerCandidates.firstOrNull()?.let(LyricsMetadata::normalizeText).orEmpty())
        append('|')
        append(song.durationSec)
    }

    private fun cacheIdentity(song: Song): String =
        "${song.url}|${song.durationSec}|${song.title}|${song.artist}"

    private fun key(song: Song): String = cacheIdentity(song)

    private fun detectScript(text: String): String? {
        if (text.isBlank()) return null
        var latin = 0
        var devanagari = 0
        var arabic = 0
        for (c in text) {
            when (c.code) {
                in 0x0041..0x024F -> latin++
                in 0x0900..0x097F -> devanagari++
                in 0x0600..0x06FF -> arabic++
            }
        }
        val present = listOf(latin, devanagari, arabic).count { it > 0 }
        return when {
            present > 1 -> "Mixed"
            devanagari > 0 -> "Devanagari"
            arabic > 0 -> "Arabic"
            latin > 0 -> "Latin"
            else -> null
        }
    }
}
