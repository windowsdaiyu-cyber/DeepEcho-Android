package com.deepecho.mobile.lyrics

import com.deepecho.mobile.data.Song
import com.deepecho.mobile.net.Lyrics
import com.deepecho.mobile.net.YouTubeApi
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manual-only last-resort Exact Lyrics recovery. Never called by normal lookup.
 * Search/captions/OCR are deliberately behind the user's Exact Lyrics / Retry action.
 */
object LyricsVideoRecovery {
    private class Token { val cancelled = AtomicBoolean(false) }
    private val tokens = ConcurrentHashMap<String, Token>()
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<Lyrics?>>()

    fun recover(song: Song): Lyrics? {
        val key = trackKey(song)
        val mine = CompletableFuture<Lyrics?>()
        val existing = inFlight.putIfAbsent(key, mine)
        if (existing != null) return runCatching { existing.get() }.getOrNull()

        val token = Token()
        tokens[key] = token
        return try {
            val result = recoverInternal(song, token)
            mine.complete(result)
            result
        } catch (t: Throwable) {
            mine.completeExceptionally(t)
            null
        } finally {
            tokens.remove(key, token)
            inFlight.remove(key, mine)
        }
    }

    fun cancel(song: Song) = cancel(song.url)

    fun cancel(songUrl: String) {
        tokens.entries
            .filter { it.key.startsWith("$songUrl|") }
            .forEach { it.value.cancelled.set(true) }
    }

    private fun recoverInternal(song: Song, token: Token): Lyrics? {
        fun cancelled() = token.cancelled.get() || Thread.currentThread().isInterrupted
        if (cancelled()) return null

        LyricsProgress.set(song.url, "Searching for a lyric source…")
        val candidates = LyricsVideoSourceResolver.resolve(song)
        if (candidates.isEmpty() || cancelled()) return null

        // Exact playing-upload captions are only fetched here because this whole method is manual-gated.
        // They provide the strongest text/timing anchors when the official/current upload has them.
        val currentCaptions = runCatching { CaptionResolver.resolve(song, forceFresh = true) }.getOrDefault(emptyList())

        for (candidate in candidates) {
            if (cancelled()) return null
            LyricsDiagnostics.add(song.url, "manual lyric source candidate=${candidate.song.title}; score=${candidate.score}; ${candidate.reason}")
            LyricsProgress.set(song.url, "Checking captions…")
            var sourceLines = runCatching {
                CaptionResolver.resolve(candidate.song, forceFresh = true)
            }.getOrDefault(emptyList())
            var sourceKind = "Lyric video captions"

            if (sourceLines.size < 2) {
                if (cancelled()) return null
                LyricsProgress.set(song.url, "Reading lyric video…")
                val video = runCatching { YouTubeApi.resolveVideo(candidate.song.url, forceFresh = true) }.getOrNull()
                if (video != null && !cancelled()) {
                    LyricsProgress.set(song.url, "Extracting lyrics…")
                    sourceLines = LyricsVideoOcrProcessor.extract(
                        directVideoUrl = video.url,
                        expectedDurationMs = candidate.song.durationSec * 1000L,
                        isCancelled = ::cancelled
                    )
                    sourceKind = "Video OCR"
                }
            }
            if (sourceLines.size < 2 || cancelled()) continue

            LyricsProgress.set(song.url, "Cleaning lyrics…")
            val sourceLyrics = LyricsAlignmentEngine.fromCaptions(sourceLines, null) ?: continue
            val refined = LyricsTextRefiner.refine(sourceLyrics).lyrics
            if (refined.synced.size < 2) continue

            LyricsProgress.set(song.url, "Matching lyrics to this version…")
            var aligned: Lyrics? = null
            if (currentCaptions.size >= 2) {
                aligned = LyricsAlignmentEngine.align(refined, currentCaptions)?.lyrics
                if (aligned != null) {
                    val verification = LyricsAudioVerifier.verify(aligned, currentCaptions)
                    if (verification.suspicious || verification.agreement < 30) aligned = null
                }
            }
            if (aligned == null && !cancelled()) {
                // Current video may be cinematic and expose no useful captions. Match the reference
                // audio envelope to the exact playing audio instead of copying lyric-video timestamps.
                aligned = LyricsAudioFingerprintAligner.align(song, candidate.song, refined, ::cancelled)
            }
            if (aligned == null || cancelled()) continue

            LyricsProgress.set(song.url, "Syncing lines…")
            val confidence = maxOf(aligned.confidence, aligned.audioConfidence, aligned.alignmentConfidence)
            if (confidence < 42) continue
            val final = aligned.copy(
                source = "Exact Lyrics • $sourceKind",
                verification = if (sourceKind == "Video OCR") "manual_video_ocr_recovery" else "manual_lyric_video_caption_recovery",
                verified = aligned.verified || confidence >= 56,
                confidence = confidence.coerceIn(0, 100)
            )
            // OCR normally provides line timing, not genuine word boundaries. Do not fabricate them.
            LyricsProgress.set(song.url, if (final.synced.any { it.words.isNotEmpty() }) "Refining Word by Word…" else "Exact Lyrics ready")
            return final
        }
        return null
    }

    private fun trackKey(song: Song): String = "${song.url}|${song.durationSec}|${song.title}|${song.artist}"
}
