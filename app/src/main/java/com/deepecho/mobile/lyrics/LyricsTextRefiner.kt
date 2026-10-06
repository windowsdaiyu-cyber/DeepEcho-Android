package com.deepecho.mobile.lyrics

import com.deepecho.mobile.net.LyricLine
import com.deepecho.mobile.net.LyricWord
import com.deepecho.mobile.net.Lyrics
import kotlin.math.abs

/**
 * Conservative post-transcription text refinement.
 *
 * This intentionally does NOT contain song-specific replacements (no General Levy, Dubplate,
 * zameem->zameen, etc.). It only changes a token when the same track supplies stronger evidence:
 * authoritative/reference lyrics, repeated-line consensus, or a high-confidence near-phonetic
 * token already observed elsewhere in the track. Timing is copied byte-for-byte.
 */
object LyricsTextRefiner {
    data class Result(
        val lyrics: Lyrics,
        val changedTokens: Int,
        val textConfidence: Int,
        val reason: String
    )

    fun refine(generated: Lyrics, referenceText: String? = null): Result {
        if (generated.synced.isEmpty() && generated.plain.isNullOrBlank()) {
            return Result(generated, 0, 0, "no text")
        }

        val referenceLines = referenceText.orEmpty().lines().map { cleanLine(it) }.filter { it.isNotBlank() }
        val trackCorpus = buildCorpus(generated, referenceLines)
        var changed = 0

        val refinedLines = generated.synced.map { line ->
            val bestReference = referenceLines.maxByOrNull { LyricsAudioVerifier.lineAgreement(line.text, it) }
                ?.takeIf { LyricsAudioVerifier.lineAgreement(line.text, it) >= 0.54 }

            val sourceTokens = tokens(line.text)
            val targetTokens = bestReference?.let(::tokens)
            val repairedTokens = when {
                targetTokens != null && abs(targetTokens.size - sourceTokens.size) <= maxOf(2, sourceTokens.size / 3) ->
                    repairAgainstReference(sourceTokens, targetTokens, trackCorpus)
                else -> repairByTrackConsensus(sourceTokens, trackCorpus)
            }
            changed += sourceTokens.zip(repairedTokens).count { (a,b) -> !a.equals(b, true) }
            val newText = rebuildLike(line.text, repairedTokens)

            val newWords = if (line.words.isNotEmpty()) {
                repairWordObjects(line.words, repairedTokens)
            } else line.words
            // CRITICAL: line.timeMs and every word.timeMs remain untouched in TEXT_REPAIR_ONLY.
            line.copy(text = newText, words = newWords)
        }

        val refinedPlain = if (refinedLines.isNotEmpty()) refinedLines.joinToString("\n") { it.text }
        else generated.plain

        val confidence = when {
            changed == 0 -> maxOf(generated.confidence, 70)
            referenceLines.isNotEmpty() -> 84
            else -> 72
        }
        return Result(
            generated.copy(
                synced = refinedLines,
                plain = refinedPlain,
                confidence = maxOf(generated.confidence, confidence),
                verification = if (changed > 0) "text_repaired_only" else generated.verification,
                source = if (changed > 0) appendLabel(generated.source, "Text refined") else generated.source
            ),
            changed,
            confidence,
            if (changed > 0) "contextual track-local correction" else "no safe text correction"
        )
    }

    private fun buildCorpus(lyrics: Lyrics, refs: List<String>): Map<String, Int> {
        val counts = linkedMapOf<String, Int>()
        fun add(s: String, weight: Int) {
            tokens(s).forEach { t ->
                val n = normToken(t)
                if (n.length >= 2) counts[n] = (counts[n] ?: 0) + weight
            }
        }
        lyrics.synced.forEach { add(it.text, 1) }
        lyrics.plain.orEmpty().lines().forEach { add(it, 1) }
        refs.forEach { add(it, 4) } // provider/reference text is stronger than generated ASR text.
        return counts
    }

    private fun repairAgainstReference(src: List<String>, ref: List<String>, corpus: Map<String, Int>): List<String> {
        if (src.isEmpty()) return src
        val out = src.toMutableList()
        var ri = 0
        for (i in src.indices) {
            if (ri >= ref.size) break
            val s = src[i]
            var bestIdx = ri
            var best = tokenSimilarity(s, ref[ri])
            for (j in ri until minOf(ref.size, ri + 3)) {
                val score = tokenSimilarity(s, ref[j])
                if (score > best) { best = score; bestIdx = j }
            }
            if (best >= 0.72 || (best >= 0.58 && isNasalConfusion(s, ref[bestIdx]))) {
                out[i] = preserveCaseAndPunctuation(s, ref[bestIdx])
                ri = bestIdx + 1
            }
        }
        return out.map { chooseTrackConsensus(it, corpus) }
    }

    private fun repairByTrackConsensus(src: List<String>, corpus: Map<String, Int>): List<String> =
        src.map { chooseTrackConsensus(it, corpus) }

    private fun chooseTrackConsensus(token: String, corpus: Map<String, Int>): String {
        val n = normToken(token)
        if (n.length < 3) return token
        val currentCount = corpus[n] ?: 0
        var winner = n
        var winnerCount = currentCount
        for ((candidate, count) in corpus) {
            if (count < 2 || count <= winnerCount) continue
            if (!isNasalConfusion(n, candidate)) continue
            val sim = tokenSimilarity(n, candidate)
            if (sim >= 0.72) { winner = candidate; winnerCount = count }
        }
        // Require materially stronger same-track evidence. This prevents a dumb global m<->n swap.
        return if (winner != n && winnerCount >= currentCount + 2) preserveCaseAndPunctuation(token, winner) else token
    }

    private fun repairWordObjects(words: List<LyricWord>, repairedTokens: List<String>): List<LyricWord> {
        if (words.size != repairedTokens.size) return words
        return words.mapIndexed { i, w ->
            val repaired = preserveCaseAndPunctuation(w.text, repairedTokens[i])
            if (repaired == w.text) w else w.copy(text = repaired, textConfidence = maxOf(w.textConfidence, 72))
        }
    }

    private fun tokens(s: String): List<String> = s.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
    private fun cleanLine(s: String) = s.replace(Regex("""^\s*\[[^]]+]\s*"""), "").trim()
    private fun normToken(s: String) = s.lowercase().replace(Regex("""[^\p{L}\p{N}']"""), "")

    private fun isNasalConfusion(a: String, b: String): Boolean {
        val x = normToken(a); val y = normToken(b)
        if (x == y || x.length < 2 || y.length < 2 || abs(x.length - y.length) > 2) return false
        fun skeleton(v: String) = v
            .replace("ng", "N")
            .replace('m', 'N')
            .replace('n', 'N')
        return skeleton(x) == skeleton(y) ||
            (tokenSimilarity(x, y) >= 0.74 && (x.contains('m') || x.contains('n')) && (y.contains('m') || y.contains('n')))
    }

    private fun tokenSimilarity(a: String, b: String): Double {
        val x = normToken(a); val y = normToken(b)
        if (x == y) return 1.0
        if (x.isEmpty() || y.isEmpty()) return 0.0
        val d = levenshtein(x, y)
        return (1.0 - d.toDouble() / maxOf(x.length, y.length)).coerceIn(0.0, 1.0)
    }

    private fun levenshtein(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in a.indices) {
            cur[0] = i + 1
            for (j in b.indices) cur[j + 1] = minOf(cur[j] + 1, prev[j + 1] + 1, prev[j] + if (a[i] == b[j]) 0 else 1)
            for (j in prev.indices) prev[j] = cur[j]
        }
        return prev[b.length]
    }

    private fun preserveCaseAndPunctuation(original: String, replacement: String): String {
        val prefix = original.takeWhile { !it.isLetterOrDigit() }
        val suffix = original.takeLastWhile { !it.isLetterOrDigit() }
        val core = when {
            original.all { !it.isLetter() || it.isUpperCase() } -> replacement.uppercase()
            original.firstOrNull()?.isUpperCase() == true -> replacement.replaceFirstChar { it.uppercase() }
            else -> replacement.lowercase()
        }
        return prefix + core + suffix
    }

    private fun rebuildLike(original: String, repaired: List<String>): String =
        if (repaired.isEmpty()) original else repaired.joinToString(" ")

    private fun appendLabel(source: String, label: String): String =
        if (source.contains(label, true)) source else "$source • $label"
}
