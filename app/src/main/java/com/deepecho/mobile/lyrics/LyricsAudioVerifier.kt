package com.deepecho.mobile.lyrics

import com.deepecho.mobile.net.Lyrics
import kotlin.math.abs

/**
 * Lightweight audio-grounded verification using captions from the actual playing upload.
 * Captions are imperfect, but when available they are far safer than trusting fuzzy metadata only.
 */
object LyricsAudioVerifier {
    fun verify(lyrics: Lyrics, captions: List<CaptionLine>): AudioVerification {
        if (captions.size < 2) {
            return AudioVerification(
                available = false,
                agreement = 0,
                timingAgreement = 0,
                firstVocalOnsetMs = null,
                suspicious = false,
                reason = "No usable captions"
            )
        }

        val lyricLines = if (lyrics.synced.isNotEmpty()) lyrics.synced.map { it.timeMs to it.text }
        else lyrics.plain.orEmpty().lines().mapIndexedNotNull { i, s ->
            val t = s.trim(); if (t.isBlank()) null else i.toLong() to t
        }
        if (lyricLines.size < 2) {
            return AudioVerification(true, 0, 0, captions.firstOrNull()?.startMs, true, "Candidate has too little text", captions)
        }

        val captionTokens = captions.joinToString(" ") { it.text }
        val lyricTokens = lyricLines.joinToString(" ") { it.second }
        val global = textAgreement(lyricTokens, captionTokens)

        var localTotal = 0.0
        var localWeight = 0.0
        var timingHits = 0
        var timingChecks = 0
        val sampled = captions.filterIndexed { index, _ -> index % ((captions.size / 18).coerceAtLeast(1)) == 0 }.take(24)

        for (cap in sampled) {
            val bestGlobal = lyricLines.maxOfOrNull { (_, txt) -> lineAgreement(txt, cap.text) } ?: 0.0
            if (bestGlobal > 0.18) {
                localTotal += bestGlobal
                localWeight += 1.0
            }
            if (lyrics.synced.isNotEmpty()) {
                val near = lyrics.synced.filter { abs(it.timeMs - cap.startMs) <= 18_000L }
                if (near.isNotEmpty()) {
                    timingChecks++
                    val nearBest = near.maxOf { lineAgreement(it.text, cap.text) }
                    if (nearBest >= 0.38) timingHits++
                }
            }
        }

        val local = if (localWeight > 0) localTotal / localWeight else 0.0
        val textScore = ((global * 0.62 + local * 0.38) * 100).toInt().coerceIn(0, 100)
        val timingScore = if (timingChecks > 0) (timingHits * 100 / timingChecks) else 50
        val firstVocalOnset = captions.firstOrNull { cap ->
            lyricLines.take(16).maxOfOrNull { (_, txt) -> lineAgreement(txt, cap.text) }?.let { it >= 0.34 } == true
        }?.startMs ?: captions.firstOrNull()?.startMs
        val suspicious = textScore < 28 || (lyrics.synced.isNotEmpty() && textScore < 40 && timingScore < 25)
        val reason = when {
            textScore >= 72 -> "Strong caption/lyric agreement"
            textScore >= 50 -> "Good caption/lyric agreement"
            textScore >= 32 -> "Partial caption/lyric agreement"
            else -> "Poor caption/lyric agreement"
        }
        return AudioVerification(
            available = true,
            agreement = textScore,
            timingAgreement = timingScore,
            firstVocalOnsetMs = firstVocalOnset,
            suspicious = suspicious,
            reason = reason,
            captions = captions
        )
    }

    fun lineAgreement(a: String, b: String): Double {
        val aa = LyricsMetadata.normalizeText(a)
        val bb = LyricsMetadata.normalizeText(b)
        if (aa.isBlank() || bb.isBlank()) return 0.0
        if (aa == bb) return 1.0
        val token = LyricsMetadata.tokenSimilarity(aa, bb)
        val aBigrams = ngrams(aa.split(' '), 2)
        val bBigrams = ngrams(bb.split(' '), 2)
        val bigram = if (aBigrams.isEmpty() || bBigrams.isEmpty()) 0.0 else {
            aBigrams.intersect(bBigrams).size.toDouble() / minOf(aBigrams.size, bBigrams.size).coerceAtLeast(1)
        }
        return (token * 0.68 + bigram * 0.32).coerceIn(0.0, 1.0)
    }

    private fun textAgreement(a: String, b: String): Double {
        val at = LyricsMetadata.normalizeText(a).split(' ').filter { it.length > 1 }
        val bt = LyricsMetadata.normalizeText(b).split(' ').filter { it.length > 1 }
        if (at.isEmpty() || bt.isEmpty()) return 0.0
        val aSet = at.toSet()
        val bSet = bt.toSet()
        val shared = aSet.intersect(bSet).size.toDouble()
        val containment = shared / minOf(aSet.size, bSet.size).coerceAtLeast(1)
        val aTri = ngrams(at, 3)
        val bTri = ngrams(bt, 3)
        val tri = if (aTri.isEmpty() || bTri.isEmpty()) 0.0 else {
            aTri.intersect(bTri).size.toDouble() / minOf(aTri.size, bTri.size).coerceAtLeast(1)
        }
        return (containment * 0.62 + tri * 0.38).coerceIn(0.0, 1.0)
    }

    private fun ngrams(tokens: List<String>, n: Int): Set<String> {
        if (tokens.size < n) return emptySet()
        return (0..tokens.size - n).map { i -> tokens.subList(i, i + n).joinToString(" ") }.toSet()
    }
}
