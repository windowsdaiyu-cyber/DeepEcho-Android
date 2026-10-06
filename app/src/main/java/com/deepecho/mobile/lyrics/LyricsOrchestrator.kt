package com.deepecho.mobile.lyrics

import com.deepecho.mobile.data.Song
import com.deepecho.mobile.net.Lyrics
import java.util.concurrent.ConcurrentHashMap

/**
 * Universal Lyrics V2 staged orchestrator.
 *
 * Fast exact provider results stay fast. Advanced caption/audio-grounded verification is invoked
 * only when metadata is ambiguous, timing looks suspicious, a variant is detected, or recovery is
 * needed. Playback is never touched.
 */
object LyricsOrchestrator {
    private data class Entry(val at: Long, val lyrics: Lyrics?)
    private val memory = ConcurrentHashMap<String, Entry>()
    private val trackLocks = ConcurrentHashMap<String, Any>()
    private const val HIT_TTL = 6 * 60 * 60_000L
    private const val MISS_TTL = 15 * 60_000L

    fun fetch(song: Song, forceFresh: Boolean = false): Lyrics? {
        val lock = trackLocks.getOrPut(song.url) { Any() }
        return synchronized(lock) { fetchLocked(song, forceFresh) }
    }

    /**
     * Explicit user-triggered Exact Lyrics recovery. The existing lookup/recovery pipeline always
     * runs first. Lyric-video/caption/OCR discovery is attempted only when that existing pipeline
     * still cannot produce a reliable result. Normal fetch() never calls this method.
     */
    fun fetchExactRecovery(song: Song): Lyrics? {
        val previous = LyricsCache.get(song)
        val existingRecovery = fetch(song, forceFresh = true)
        val bestExisting = better(previous, existingRecovery)

        // Never replace a current/previous good sync with a lower-confidence OCR recovery.
        if (bestExisting != null && isReliable(bestExisting)) {
            // A forced retry may have briefly evaluated/cached another candidate. Restore the
            // higher-quality pre-existing result so manual recovery can never downgrade good sync.
            cache(song, bestExisting)
            LyricsProgress.clear(song.url)
            LyricsDiagnostics.add(song.url, "manual recovery stopped: existing reliable lyrics kept")
            return bestExisting
        }
        if (!LyricsFeatureFlags.manualVideoOcrRecovery) return bestExisting

        val recovered = LyricsVideoRecovery.recover(song)
        val chosen = better(bestExisting, recovered)
        if (chosen != null && chosen === recovered) {
            cache(song, chosen)
            LyricsDiagnostics.add(song.url, "manual video recovery accepted: ${chosen.source}")
        } else if (recovered != null) {
            LyricsDiagnostics.add(song.url, "manual video recovery rejected: existing result scored higher")
        }
        if (chosen != null && isReliable(chosen)) cache(song, chosen)
        LyricsProgress.clear(song.url)
        return chosen
    }

    fun cancelExactRecovery(songUrl: String) {
        LyricsVideoRecovery.cancel(songUrl)
        LyricsProgress.clear(songUrl)
    }

    private fun fetchLocked(song: Song, forceFresh: Boolean): Lyrics? {
        val now = System.currentTimeMillis()
        if (!forceFresh) {
            memory[song.url]?.let { e ->
                val ttl = if (e.lyrics == null) MISS_TTL else HIT_TTL
                if (now - e.at < ttl) return e.lyrics
            }
            LyricsCache.get(song)?.let { cached ->
                memory[song.url] = Entry(now, cached)
                LyricsDiagnostics.add(song.url, "cache hit: ${cached.source}")
                return cached
            }
        } else {
            // Force a fresh verification pass without destroying the last known-good offline cache.
            memory.remove(song.url)
        }

        val previousCached = if (forceFresh) LyricsCache.get(song) else null
        val started = System.currentTimeMillis()
        LyricsProgress.set(song.url, "Checking lyrics…")
        val meta = LyricsMetadata.normalize(song)
        LyricsDiagnostics.add(song.url, "normalized title=${meta.coreTitle}; generic=${meta.genericTitle}; variants=${meta.qualifiers.joinToString()}")

        // Stage A: current-style exact provider path. This remains the no-extra-latency route for
        // strong normal songs such as Rap God.
        val exact = runCatching { LrclibProvider.exact(song, meta) }.getOrNull()
        if (exact != null) {
            LyricsDiagnostics.add(song.url, "exact LRCLIB metadata=${exact.score.total}; suspicious=${exact.score.suspicious}; ${exact.score.reason}")
            if (!forceFresh && isImmediateFastPath(exact, meta)) {
                val result = exact.lyrics.copy(
                    source = "LRCLIB • Verified",
                    verification = "fast_path_verified",
                    verified = true,
                    confidence = exact.score.total,
                    metadataConfidence = exact.score.total,
                    alignmentConfidence = if (exact.lyrics.synced.isNotEmpty()) 92 else 0
                )
                cache(song, result)
                LyricsProgress.clear(song.url)
                LyricsDiagnostics.add(song.url, "fast path accepted in ${System.currentTimeMillis() - started}ms")
                return result
            }
        }

        // Stage D: only suspicious/variant tracks pay the caption extraction cost.
        LyricsProgress.set(song.url, "Verifying audio…")
        var captions: List<CaptionLine>? = null
        fun captions(): List<CaptionLine> {
            val existing = captions
            if (existing != null) return existing
            val resolved = if (LyricsFeatureFlags.captionVerification) {
                runCatching { CaptionResolver.resolve(song, forceFresh) }.getOrDefault(emptyList())
            } else emptyList()
            captions = resolved
            LyricsDiagnostics.add(song.url, "caption verification source=${if (resolved.isEmpty()) "unavailable" else "available:${resolved.size}"}")
            return resolved
        }

        if (exact != null) {
            verifyAndRepair(exact, meta, captions())?.let { repaired ->
                cache(song, repaired)
                LyricsProgress.clear(song.url)
                LyricsDiagnostics.add(song.url, "exact candidate verified/repaired in ${System.currentTimeMillis() - started}ms")
                return repaired
            }
            LyricsProgress.set(song.url, "Lyrics mismatch detected • finding better lyrics…")
            LyricsDiagnostics.add(song.url, "exact candidate rejected")
        }

        // Search -> Lyrics may have already verified a fallback for this exact playback source.
        // The regular current provider above always gets first chance. Only after it cannot deliver
        // do we use the descriptor attached by the Lyrics tab, feeding the result into this SAME
        // lyrics model/UI rather than creating a special player or provider-specific renderer.
        var attachedTextFallback: Lyrics? = null
        LyricsSearchAvailabilityIndex.attachedForPlayback(song)?.let { attached ->
            val fallback = if (LyricsFeatureFlags.contextualTextRepair) {
                LyricsTextRefiner.refine(attached.lyrics).lyrics
            } else attached.lyrics
            if (attached.availabilityState.deliverable &&
                (fallback.synced.isNotEmpty() || !fallback.plain.isNullOrBlank())) {
                if (attached.availabilityState == LyricsAvailabilityState.VERIFIED_TEXT) {
                    // Keep plain verified text as a reference while the existing alignment/caption
                    // stages below get a chance to create timing for THIS playback version.
                    attachedTextFallback = fallback.copy(synced = emptyList(), estimatedSync = false)
                    LyricsDiagnostics.add(
                        song.url,
                        "lyrics-search attached text retained for current sync: ${attached.sourceProvider}"
                    )
                } else {
                    cache(song, fallback)
                    LyricsProgress.clear(song.url)
                    LyricsDiagnostics.add(
                        song.url,
                        "lyrics-search attached fallback used: ${attached.sourceProvider}; state=${attached.availabilityState}"
                    )
                    return fallback
                }
            }
        }

        // Stage E: rank alternative provider candidates. Generic titles never get metadata-only
        // trust; when captions are unavailable we require a strong performer+duration identity.
        LyricsProgress.set(song.url, "Finding better lyrics…")
        val alternatives = runCatching { LrclibProvider.search(song, meta) }.getOrDefault(emptyList())
        val providerReferenceCandidates = ArrayList<LyricsCandidate>(alternatives)
        LyricsDiagnostics.add(song.url, "provider alternatives=${alternatives.size}")
        for (candidate in alternatives) {
            if (candidate.score.total < 42) continue
            if (!candidate.score.suspicious && candidate.score.total >= 91 && meta.qualifiers.isEmpty()) {
                val result = candidate.lyrics.copy(
                    source = "LRCLIB • Verified",
                    verification = "strong_metadata_verified",
                    verified = true,
                    metadataConfidence = candidate.score.total,
                    confidence = candidate.score.total,
                    alignmentConfidence = if (candidate.lyrics.synced.isNotEmpty()) 88 else 0
                )
                cache(song, result)
                LyricsProgress.clear(song.url)
                return result
            }
            verifyAndRepair(candidate, meta, captions())?.let { repaired ->
                cache(song, repaired)
                LyricsProgress.clear(song.url)
                return repaired
            }
        }

        // Stage F: only after the current LRCLIB exact/search paths are exhausted do we query
        // additional free/open adapters. Provider responses are normalized into the same candidate
        // model and ranked; none is allowed to replace the existing timing authority by itself.
        LyricsProgress.set(song.url, "Checking alternate lyric sources…")
        val openFallbacks = runCatching { OpenLyricsProviderResolver.fetch(song, meta) }.getOrDefault(emptyList())
        providerReferenceCandidates += openFallbacks
        LyricsDiagnostics.add(song.url, "open provider alternatives=${openFallbacks.size}")
        for (candidate in openFallbacks) {
            if (candidate.score.total < 42) {
                LyricsDiagnostics.add(song.url, "provider rejected: ${candidate.provider}; score=${candidate.score.total}")
                continue
            }

            // Cross-provider consensus may establish strong text identity for a normal studio track.
            // Covers/variants never take this shortcut: they must be re-grounded to current audio.
            if (!candidate.requiresAudioVerification && !candidate.score.suspicious &&
                candidate.score.total >= 88 && meta.qualifiers.isEmpty()) {
                val result = candidate.lyrics.copy(
                    source = "${candidate.provider} • Provider consensus",
                    verification = "multi_provider_text_consensus",
                    verified = false,
                    metadataConfidence = candidate.score.total,
                    confidence = maxOf(candidate.lyrics.confidence, candidate.score.total)
                )
                cache(song, result)
                LyricsProgress.clear(song.url)
                LyricsDiagnostics.add(song.url, "provider selected by consensus: ${candidate.provider}")
                return result
            }

            val groundingCaptions = if (candidate.requiresAudioVerification || candidate.score.suspicious || meta.qualifiers.isNotEmpty()) {
                captions()
            } else emptyList()
            verifyAndRepair(candidate, meta, groundingCaptions)?.let { repaired ->
                cache(song, repaired)
                LyricsProgress.clear(song.url)
                LyricsDiagnostics.add(song.url, "provider selected after verification: ${candidate.provider}")
                return repaired
            }
            LyricsDiagnostics.add(song.url, "provider rejected after verification: ${candidate.provider}")
        }

        // Stage H fallback: do not stop after rejecting a wrong provider candidate. Provider text
        // can still be a spelling authority for the ACTUAL upload transcript while its timestamps
        // are discarded. This is especially important for cover versions.
        val ovh = runCatching { LrclibProvider.lyricsOvh(song, meta) }.getOrNull()

        // The exact upload transcript/captions are treated as transcription evidence, then cleaned
        // in a TEXT_REPAIR_ONLY-safe stage that never moves timestamps.
        LyricsProgress.set(song.url, "Listening to vocals…")
        val actualCaptions = captions()
        val providerTextAuthority = OpenLyricsProviderResolver.selectTextAuthority(
            providerReferenceCandidates, meta, actualCaptions
        )
        val textAuthority = attachedTextFallback?.plain
            ?: providerTextAuthority
            ?: ovh?.takeIf { !it.score.suspicious && it.score.total >= 52 }?.lyrics?.plain
        if (providerTextAuthority != null) {
            LyricsDiagnostics.add(song.url, "provider text authority selected; timing intentionally discarded")
        }
        if (actualCaptions.size >= 2) {
            LyricsProgress.set(song.url, "Transcribing lyrics…")
            LyricsTranscriptionFallback.recover(song, actualCaptions, textAuthority)?.let { recovery ->
                LyricsProgress.set(song.url, "Refining transcription…")
                cache(song, recovery.lyrics)
                LyricsProgress.clear(song.url)
                LyricsDiagnostics.add(
                    song.url,
                    "transcription fallback=${recovery.transcriptKind}; mode=${recovery.repairMode}; changedTokens=${recovery.changedTokens}; timing unchanged=${recovery.repairMode == LyricsRepairMode.TEXT_REPAIR_ONLY}"
                )
                return recovery.lyrics
            }
        }

        // A Lyrics-tab descriptor may carry verified plain text even when this upload exposes no
        // usable timing anchors. In that case keep the product promise by showing the text through
        // the existing Lyrics UI; do not invent timestamps or run OCR automatically.
        attachedTextFallback?.let { fallback ->
            memory[song.url] = Entry(now, fallback)
            LyricsProgress.clear(song.url)
            LyricsDiagnostics.add(song.url, "lyrics-search verified text fallback displayed without fabricated timing")
            return fallback
        }

        // Safe text-only fallback: if a provider looks correct but its timing could not be grounded,
        // show its text rather than importing unverified timestamps. For covers we additionally
        // require a strong current-performer match.
        val textFallback = providerReferenceCandidates
            .asSequence()
            .filter { !it.score.suspicious && it.score.total >= 62 }
            .filter { "cover" !in meta.qualifiers || it.score.performer >= 75 }
            .filter { !it.lyrics.plain.isNullOrBlank() }
            .maxByOrNull { it.score.total + it.providerConfidence / 6 }
        if (textFallback != null && !meta.genericTitle) {
            val result = textFallback.lyrics.copy(
                synced = emptyList(),
                estimatedSync = false,
                source = "${textFallback.provider} • Text fallback",
                verification = "provider_text_only_fallback",
                verified = false,
                alignmentConfidence = 0,
                audioConfidence = 0
            )
            memory[song.url] = Entry(now, result)
            LyricsProgress.clear(song.url)
            LyricsDiagnostics.add(song.url, "text-only fallback selected: ${textFallback.provider}")
            return result
        }

        // Final legacy plain-text fallback. For generic/ambiguous metadata we deliberately refuse
        // to pretend a weak same-title result is correct.
        if (ovh != null && !meta.genericTitle && !ovh.score.suspicious && ovh.score.total >= 38) {
            val result = ovh.lyrics.copy(
                source = "Lyrics.ovh • Unverified",
                verification = "plain_fallback",
                verified = false
            )
            memory[song.url] = Entry(now, result)
            LyricsProgress.clear(song.url)
            return result
        }

        if (previousCached != null) {
            memory[song.url] = Entry(now, previousCached)
            LyricsProgress.clear(song.url)
            LyricsDiagnostics.add(song.url, "fresh recovery failed; restored verified cache")
            return previousCached
        }
        memory[song.url] = Entry(now, null)
        LyricsProgress.clear(song.url)
        LyricsDiagnostics.add(song.url, "recovery exhausted; providers and upload transcription/captions unavailable or unusable")
        return null
    }

    private fun isImmediateFastPath(candidate: LyricsCandidate, meta: NormalizedTrackMetadata): Boolean {
        if (candidate.score.suspicious || meta.genericTitle || meta.qualifiers.isNotEmpty()) return false
        if (!candidate.exactLookup) return false
        if (candidate.score.total < 92 || candidate.score.title < 90) return false
        if (meta.performerCandidates.isNotEmpty() && candidate.score.performer < 80) return false
        if (candidate.score.duration < 72) return false
        return candidate.lyrics.synced.isNotEmpty() || !candidate.lyrics.plain.isNullOrBlank()
    }

    private fun verifyAndRepair(
        candidate: LyricsCandidate,
        meta: NormalizedTrackMetadata,
        captions: List<CaptionLine>
    ): Lyrics? {
        if (captions.isEmpty()) {
            // Provider adapters that do not expose enough returned metadata must not smuggle their
            // own timing in as truth. Cover recordings are stricter still: every provider timeline
            // must be rebuilt against the ACTUAL cover audio before it can be displayed as synced.
            if (candidate.requiresAudioVerification) return null
            if ("cover" in meta.qualifiers) return null
            // Without an audio-grounded signal, a generic-title collision is unsafe. This exact rule
            // prevents "Dubplate"-style 86% fuzzy matches being displayed as confidently correct.
            if (meta.genericTitle) return null
            if (candidate.score.suspicious) {
                val exceptionallyStrongIdentity = candidate.score.total >= 96 &&
                    candidate.score.performer >= 92 && candidate.score.duration >= 90 && candidate.score.title >= 96
                if (!exceptionallyStrongIdentity) return null
            }
            if (candidate.score.total < 82) return null
            return candidate.lyrics.copy(
                source = "${candidate.provider} • Metadata verified",
                metadataConfidence = candidate.score.total,
                confidence = candidate.score.total,
                verification = "metadata_verified_no_audio",
                verified = false
            )
        }

        val verification = LyricsAudioVerifier.verify(candidate.lyrics, captions)
        LyricsDiagnostics.add(
            candidate.id.ifBlank { candidate.title },
            "audio agreement=${verification.agreement}; timing=${verification.timingAgreement}; ${verification.reason}"
        )
        if (verification.suspicious || verification.agreement < 28) return null

        val combined = (candidate.score.total * 0.43 + verification.agreement * 0.57).toInt().coerceIn(0, 100)
        if (combined < 48) return null

        val needsAlignment = candidate.lyrics.synced.isNotEmpty() && (
            verification.timingAgreement < 60 ||
                meta.qualifiers.isNotEmpty() ||
                verification.firstVocalOnsetMs?.let { onset ->
                    val first = candidate.lyrics.synced.firstOrNull()?.timeMs ?: onset
                    kotlin.math.abs(onset - first) > 1_600L
                } == true
            )

        if (needsAlignment) {
            if (LyricsFeatureFlags.dynamicAlignment) LyricsAlignmentEngine.align(candidate.lyrics, captions)?.let { aligned ->
                return aligned.lyrics.copy(
                    confidence = maxOf(combined, aligned.confidence),
                    metadataConfidence = candidate.score.total,
                    audioConfidence = verification.agreement,
                    alignmentConfidence = aligned.confidence,
                    verified = verification.agreement >= 48 && aligned.confidence >= 48,
                    variant = meta.qualifiers.joinToString(" + ").ifBlank { null },
                    firstVocalOnsetMs = verification.firstVocalOnsetMs
                )
            }
        }

        // Text is verified against the actual upload but timing did not require repair.
        return candidate.lyrics.copy(
            source = "${candidate.provider} • Verified",
            confidence = combined,
            metadataConfidence = candidate.score.total,
            audioConfidence = verification.agreement,
            alignmentConfidence = if (candidate.lyrics.synced.isNotEmpty()) verification.timingAgreement else 0,
            verification = "audio_verified",
            firstVocalOnsetMs = verification.firstVocalOnsetMs,
            verified = true,
            variant = meta.qualifiers.joinToString(" + ").ifBlank { null }
        )
    }

    private fun isReliable(lyrics: Lyrics): Boolean =
        lyrics.verified || lyrics.alignmentConfidence >= 55 || lyrics.audioConfidence >= 60 ||
            (lyrics.synced.size >= 2 && lyrics.metadataConfidence >= 90 && lyrics.confidence >= 88)

    private fun quality(lyrics: Lyrics?): Int {
        if (lyrics == null) return -1
        var score = maxOf(lyrics.confidence, lyrics.metadataConfidence, lyrics.audioConfidence, lyrics.alignmentConfidence)
        if (lyrics.synced.size >= 2) score += 10
        if (lyrics.verified) score += 18
        if (lyrics.synced.any { it.words.isNotEmpty() }) score += 3
        if (lyrics.estimatedSync) score -= 8
        return score
    }

    private fun better(a: Lyrics?, b: Lyrics?): Lyrics? = when {
        a == null -> b
        b == null -> a
        quality(b) > quality(a) -> b
        else -> a
    }

    private fun cache(song: Song, lyrics: Lyrics) {
        val now = System.currentTimeMillis()
        memory[song.url] = Entry(now, lyrics)
        if (lyrics.verified || lyrics.alignmentConfidence >= 50 || lyrics.audioConfidence >= 55) {
            LyricsCache.put(song, lyrics)
        }
    }
}
