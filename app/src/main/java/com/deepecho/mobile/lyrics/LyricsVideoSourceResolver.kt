package com.deepecho.mobile.lyrics

import com.deepecho.mobile.data.Song
import com.deepecho.mobile.net.YouTubeApi

/** Manual-only discovery of a lyrics-oriented reference video. */
object LyricsVideoSourceResolver {
    data class Candidate(val song: Song, val score: Int, val reason: String)

    private val hardReject = Regex("(?i)\\b(karaoke|cover|reaction|tutorial|instrumental|piano\\s+cover|guitar\\s+cover|translation|translated|8d\\s+audio)\\b")
    private val lyricHint = Regex("(?i)\\b(lyrics?|lyrical|official\\s+lyrics?|lyric\\s+video)\\b")

    fun resolve(song: Song, limit: Int = 8): List<Candidate> {
        val meta = LyricsMetadata.normalize(song)
        val identityArtist = meta.performerCandidates.firstOrNull().orEmpty().ifBlank { song.artist }
        val title = meta.coreTitle.ifBlank { song.title }
        val queries = linkedSetOf<String>().apply {
            add(listOf(identityArtist, title, "lyrics").filter { it.isNotBlank() }.joinToString(" "))
            add(listOf(song.artist, song.title, "lyric video").filter { it.isNotBlank() }.joinToString(" "))
            add(listOf(identityArtist, title, "official lyrics").filter { it.isNotBlank() }.joinToString(" "))
            if (meta.qualifiers.isNotEmpty()) {
                add(listOf(identityArtist, title, meta.qualifiers.joinToString(" "), "lyrics").filter { it.isNotBlank() }.joinToString(" "))
            }
        }

        val seen = LinkedHashMap<String, Candidate>()
        for (query in queries.take(4)) {
            val results = runCatching { YouTubeApi.searchVideos(query) }.getOrDefault(emptyList())
            for (candidate in results.take(12)) {
                if (candidate.url == song.url) continue
                val text = "${candidate.title} ${candidate.artist}"
                if (hardReject.containsMatchIn(text)) continue
                val base = LyricsMetadata.scoreCandidate(
                    wanted = meta,
                    candidateTitle = candidate.title,
                    candidateArtist = candidate.artist,
                    candidateDurationSec = candidate.durationSec.toDouble(),
                    hasSynced = false
                )
                if (base.title < 45) continue
                if (meta.performerCandidates.isNotEmpty() && base.performer < 30) continue

                var score = base.total
                val reasons = mutableListOf<String>()
                if (lyricHint.containsMatchIn(candidate.title)) {
                    score += 17
                    reasons += "lyrics-oriented title"
                }
                val durationDiff = kotlin.math.abs(candidate.durationSec - song.durationSec)
                when {
                    durationDiff <= 4 -> { score += 8; reasons += "duration close" }
                    durationDiff <= 12 -> score += 4
                    durationDiff > 55 && song.durationSec > 0 -> score -= 22
                }
                if (base.variant >= 90) score += 5
                if (base.suspicious && meta.genericTitle) score -= 5
                score = score.coerceIn(0, 100)
                if (score < 48) continue

                val wrapped = Candidate(candidate, score, reasons.joinToString().ifBlank { base.reason })
                val old = seen[candidate.url]
                if (old == null || wrapped.score > old.score) seen[candidate.url] = wrapped
            }
        }
        return seen.values.sortedByDescending { it.score }.take(limit)
    }
}
