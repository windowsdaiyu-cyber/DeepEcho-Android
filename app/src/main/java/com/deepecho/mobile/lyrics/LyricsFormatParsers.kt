package com.deepecho.mobile.lyrics

import com.deepecho.mobile.net.LyricLine
import com.deepecho.mobile.net.LyricWord

/**
 * Small, dependency-free parsers for fallback provider formats.
 *
 * They intentionally only normalize provider output into DeepEcho's existing model; they never
 * decide whether provider timing is authoritative. The orchestrator remains responsible for
 * matching, verification and re-alignment to the currently playing audio.
 */
object LyricsFormatParsers {
    private val pTag = Regex("""<p\b([^>]*)>(.*?)</p>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val spanTag = Regex("""<span\b([^>]*)>(.*?)</span>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val beginAttr = Regex("""\bbegin\s*=\s*[\"']([^\"']+)[\"']""", RegexOption.IGNORE_CASE)
    private val qrcLine = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val qrcWord = Regex("""\((\d+),(\d+)\)(.*?)(?=\(\d+,\d+\)|$)""")

    fun parseTtml(ttml: String): List<LyricLine> {
        if (ttml.isBlank()) return emptyList()
        val out = ArrayList<LyricLine>()
        for (p in pTag.findAll(ttml)) {
            val attrs = p.groupValues[1]
            val body = p.groupValues[2]
            val lineBegin = beginAttr.find(attrs)?.groupValues?.getOrNull(1)?.let(::parseClock) ?: -1L

            val spans = spanTag.findAll(body).mapNotNull { span ->
                val raw = decodeXml(stripTags(span.groupValues[2]))
                if (raw.isBlank()) return@mapNotNull null
                val begin = beginAttr.find(span.groupValues[1])?.groupValues?.getOrNull(1)?.let(::parseClock)
                Triple(span.range.first, raw, begin)
            }.toList()

            val rawText = if (spans.isNotEmpty()) {
                buildString { spans.forEach { append(it.second) } }
            } else decodeXml(stripTags(body))
            val lineText = normalizeDisplay(rawText)
            if (lineText.isBlank()) continue

            val base = when {
                lineBegin >= 0 -> lineBegin
                spans.any { it.third != null && it.third!! >= 0 } -> spans.mapNotNull { it.third }.minOrNull() ?: -1L
                else -> -1L
            }
            if (base < 0) continue

            val words = timedWordsFromSpans(spans, rawText)
            out += LyricLine(base, lineText, words)
        }
        return out.sortedBy { it.timeMs }
            .distinctBy { it.timeMs to LyricsMetadata.normalizeText(it.text) }
            .take(420)
    }

    /** Parse QQ/QRC XML or a raw QRC body into DeepEcho line/word timing. */
    fun parseQrc(qrcOrXml: String): List<LyricLine> {
        if (qrcOrXml.isBlank()) return emptyList()
        val body = extractQrcBody(qrcOrXml)
        if (body.isBlank()) return emptyList()
        val out = ArrayList<LyricLine>()
        for (rawLine in body.replace("\r\n", "\n").replace('\r', '\n').lines()) {
            val raw = decodeXml(rawLine).trim()
            val m = qrcLine.matchEntire(raw) ?: continue
            val lineStart = m.groupValues[1].toLongOrNull() ?: continue
            val payload = m.groupValues[3]
            val words = qrcWord.findAll(payload).mapNotNull { wm ->
                val start = wm.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                val token = normalizeDisplay(decodeXml(wm.groupValues[3]))
                if (token.isBlank()) null else LyricWord(start, token, textConfidence = 88, timingConfidence = 90)
            }.toList()
            val clean = if (words.isNotEmpty()) {
                normalizeDisplay(qrcWord.findAll(payload).joinToString(" ") { decodeXml(it.groupValues[3]).trim() })
            } else {
                normalizeDisplay(payload.replace(Regex("""\(\d+,\d+\)"""), ""))
            }
            if (clean.isBlank() || isMetadataLine(clean)) continue
            out += LyricLine(lineStart, clean, words)
        }
        return out.sortedBy { it.timeMs }
            .distinctBy { it.timeMs to LyricsMetadata.normalizeText(it.text) }
            .take(420)
    }

    fun plainFrom(lines: List<LyricLine>): String? = lines
        .map { it.text.trim() }
        .filter { it.isNotBlank() && it != "♪" }
        .joinToString("\n")
        .ifBlank { null }

    private fun timedWordsFromSpans(
        spans: List<Triple<Int, String, Long?>>,
        rawText: String
    ): List<LyricWord> {
        if (spans.isEmpty()) return emptyList()

        // Recreate character offsets from concatenated span text. TTML providers often split one
        // word into several syllable spans. Mapping each whitespace-delimited word to the latest
        // span that started at/before that character gives stable word timing without exposing a
        // syllable count that would not match DeepEcho's word renderer.
        val offsets = ArrayList<Pair<Int, Long>>()
        var cursor = 0
        spans.forEach { (_, text, begin) ->
            if (begin != null && begin >= 0) offsets += cursor to begin
            cursor += text.length
        }
        if (offsets.isEmpty()) return emptyList()

        return Regex("""\S+""").findAll(rawText).mapNotNull { word ->
            val t = offsets.lastOrNull { it.first <= word.range.first }?.second ?: return@mapNotNull null
            val token = decodeXml(stripTags(word.value)).trim()
            if (token.isBlank()) null else LyricWord(t, token, textConfidence = 92, timingConfidence = 92)
        }.toList()
    }

    private fun extractQrcBody(raw: String): String {
        if (!raw.contains("LyricContent", true)) return raw
        val m = Regex(
            """LyricContent\s*=\s*([\"'])(.*?)\1""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(raw) ?: return ""
        return decodeXml(m.groupValues[2])
    }

    private fun parseClock(value: String): Long {
        val v = value.trim()
        if (v.endsWith("ms", true)) return v.dropLast(2).toDoubleOrNull()?.toLong() ?: -1L
        if (v.endsWith("s", true) && ':' !in v) {
            return v.dropLast(1).toDoubleOrNull()?.times(1000.0)?.toLong() ?: -1L
        }
        val bits = v.replace(',', '.').split(':')
        return runCatching {
            when (bits.size) {
                2 -> (bits[0].toLong() * 60_000L + bits[1].toDouble() * 1000.0).toLong()
                3 -> (bits[0].toLong() * 3_600_000L + bits[1].toLong() * 60_000L + bits[2].toDouble() * 1000.0).toLong()
                1 -> (bits[0].toDouble() * 1000.0).toLong()
                else -> -1L
            }
        }.getOrDefault(-1L)
    }

    private fun stripTags(value: String): String = value
        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""<[^>]+>"""), "")

    private fun normalizeDisplay(value: String): String = value
        .replace('\u00a0', ' ')
        .replace(Regex("""[\t ]+"""), " ")
        .replace(Regex("""\s*\n\s*"""), " ")
        .trim()

    private fun decodeXml(value: String): String {
        var out = value
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)
            .replace("&quot;", "\"", ignoreCase = true)
            .replace("&apos;", "'", ignoreCase = true)
            .replace("&#39;", "'", ignoreCase = true)
            .replace("&nbsp;", " ", ignoreCase = true)
        out = Regex("""&#(\d+);""").replace(out) { m ->
            m.groupValues[1].toIntOrNull()?.let { code -> runCatching { code.toChar().toString() }.getOrNull() } ?: m.value
        }
        out = Regex("""&#x([0-9a-fA-F]+);""").replace(out) { m ->
            m.groupValues[1].toIntOrNull(16)?.let { code -> runCatching { code.toChar().toString() }.getOrNull() } ?: m.value
        }
        return out
    }

    private fun isMetadataLine(text: String): Boolean {
        val n = LyricsMetadata.normalizeText(text)
        if (n.isBlank()) return true
        return n.startsWith("ti ") || n.startsWith("ar ") || n.startsWith("al ") || n.startsWith("by ") ||
            n == "ti" || n == "ar" || n == "al" || n == "by" || n.startsWith("offset ")
    }
}
