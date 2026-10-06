package com.deepecho.mobile.net

import com.deepecho.mobile.data.Song
import com.deepecho.mobile.lyrics.LyricsOrchestrator

data class LyricWord(
    val timeMs: Long,
    val text: String,
    val textConfidence: Int = 0,
    val timingConfidence: Int = 0
)
data class LyricLine(val timeMs: Long, val text: String, val words: List<LyricWord> = emptyList())

data class Lyrics(
    val synced: List<LyricLine>,
    val plain: String?,
    val estimatedSync: Boolean = false,
    val confidence: Int = 0,
    val source: String = "LRCLIB",
    val metadataConfidence: Int = confidence,
    val audioConfidence: Int = 0,
    val alignmentConfidence: Int = 0,
    val verification: String = "unverified",
    val firstVocalOnsetMs: Long? = null,
    val verified: Boolean = false,
    val variant: String? = null
) {
    /** Human-readable source label; avoids misleading "86% match = correct" language. */
    fun displayLabel(): String {
        if (estimatedSync && !verified) return "$source • Estimated sync"
        return source
    }
}

/**
 * Public lyrics facade retained for non-regression. The implementation is now the staged
 * Universal Lyrics V2 orchestrator while all callers keep using Lrclib.fetch(song).
 */
object Lrclib {
    private val ts = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val wordTs = Regex("""<(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?>""")

    fun fetch(song: Song, forceFresh: Boolean = false): Lyrics? =
        LyricsOrchestrator.fetch(song, forceFresh)

    /** Manual Exact Lyrics / Retry entry point; normal fetch() never starts video OCR. */
    fun fetchExactRecovery(song: Song): Lyrics? = LyricsOrchestrator.fetchExactRecovery(song)

    fun cancelExactRecovery(songUrl: String) = LyricsOrchestrator.cancelExactRecovery(songUrl)

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
}
