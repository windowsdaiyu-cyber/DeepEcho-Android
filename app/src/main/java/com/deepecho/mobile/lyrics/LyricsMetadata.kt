package com.deepecho.mobile.lyrics

import com.deepecho.mobile.data.Song
import kotlin.math.abs

/**
 * Metadata normalization for Universal Lyrics V2.
 *
 * Important: qualifiers are classified, not blindly removed. This lets the resolver distinguish
 * an exact studio track from a slowed/live/remix/dubplate variant while still finding the core
 * song identity.
 */
data class NormalizedTrackMetadata(
    val originalTitle: String,
    val originalArtist: String,
    val coreTitle: String,
    val performerCandidates: List<String>,
    val uploaderContext: String,
    val qualifiers: Set<String>,
    val genericTitle: Boolean,
    val durationSec: Long
)

data class CandidateScore(
    val total: Int,
    val title: Int,
    val performer: Int,
    val duration: Int,
    val variant: Int,
    val suspicious: Boolean,
    val reason: String
)

object LyricsMetadata {
    private val bracket = Regex("""[\[(]([^\)\]]{1,100})[\)\]]""")
    private val separators = Regex("""\s*[|•·]\s*""")
    private val feat = Regex("""(?i)\b(?:feat\.?|ft\.?|featuring|with)\s+([^()\[\]|]+)""")
    private val coverBy = Regex("""(?i)\b(?:cover|covered)\s+by\s+([^()\[\]|•]+)""")

    private val neutralNoise = setOf(
        "official", "official video", "official audio", "music video", "lyric video", "lyrics",
        "lyrical", "visualizer", "hd", "hq", "4k", "topic", "full song", "audio", "video"
    )

    private val variantAliases = linkedMapOf(
        "slowed + reverb" to listOf("slowed + reverb", "slowed and reverb", "slowed reverb"),
        "slowed" to listOf("slowed"),
        "sped up" to listOf("sped up", "speed up", "nightcore"),
        "remix" to listOf("remix"),
        "mashup" to listOf("mashup"),
        "live" to listOf("live", "concert", "performance"),
        "acoustic" to listOf("acoustic"),
        "cover" to listOf("cover"),
        "dubplate" to listOf("dubplate"),
        "freestyle" to listOf("freestyle"),
        "edit" to listOf("edit", "radio edit", "dj edit"),
        "extended" to listOf("extended", "extended mix", "extended version"),
        "bass boosted" to listOf("bass boosted", "bassboosted"),
        "8d" to listOf("8d", "8d audio"),
        "reverb" to listOf("reverb"),
        "instrumental" to listOf("instrumental"),
        "karaoke" to listOf("karaoke"),
        "demo" to listOf("demo")
    )

    private val genericTitles = setOf(
        "dubplate", "intro", "freestyle", "remix", "mashup", "live", "untitled", "track 1",
        "track one", "mix", "edit", "version", "interlude", "outro", "instrumental", "demo"
    )

    fun normalize(song: Song): NormalizedTrackMetadata {
        val rawTitle = song.title.trim()
        val rawArtist = cleanArtist(song.artist)
        val qualifiers = linkedSetOf<String>()
        val performers = linkedSetOf<String>()
        val explicitCoverPerformers = linkedSetOf<String>()
        if (rawArtist.isNotBlank()) performers += rawArtist

        // Extract an "Artist - Title" prefix when it looks intentional. We do not blindly replace
        // the uploader because YouTube uploader/channel and real performer are often different.
        var title = rawTitle
        val dash = rawTitle.split(Regex("""\s+[-–—]\s+"""), limit = 2)
        if (dash.size == 2 && dash[0].length in 2..60 && dash[1].length >= 2) {
            val prefix = cleanArtist(dash[0])
            if (prefix.isNotBlank()) performers += prefix
            title = dash[1]
        }

        feat.findAll(rawTitle).forEach { m ->
            m.groupValues.getOrNull(1)
                ?.split(',', '&', '×', '/')
                ?.map(::cleanArtist)
                ?.filter { it.length >= 2 }
                ?.forEach(performers::add)
        }
        coverBy.findAll(rawTitle).forEach { m ->
            m.groupValues.getOrNull(1)
                ?.let(::cleanArtist)
                ?.takeIf { it.length >= 2 }
                ?.let { performer ->
                    explicitCoverPerformers += performer
                    performers += performer
                }
        }

        fun classify(text: String) {
            val n = normalizeText(text)
            variantAliases.forEach { (canonical, aliases) ->
                if (aliases.any { alias -> n.contains(normalizeText(alias)) }) qualifiers += canonical
            }
        }
        classify(rawTitle)

        // Remove only presentation noise and bracket chunks that are clearly presentation noise or
        // already-classified variants. Preserve meaningful parenthetical song identity.
        title = title.replace(bracket) { match ->
            val inside = match.groupValues[1]
            val n = normalizeText(inside)
            val isNoise = neutralNoise.any { n == normalizeText(it) || n.contains(normalizeText(it)) }
            val isVariant = qualifiers.any { q -> n.contains(normalizeText(q)) }
            if (isNoise || isVariant) " " else match.value
        }
        title = separators.split(title).firstOrNull().orEmpty()
        neutralNoise.sortedByDescending { it.length }.forEach { noise ->
            title = title.replace(Regex("""(?i)(^|\s+)${Regex.escape(noise)}($|\s+)"""), " ")
        }
        // Remove an explicit "cover by Performer" credit from the title identity after capturing
        // that performer above. This is generic metadata handling, never a song-specific rule.
        title = title.replace(coverBy, " ")

        // Keep the identity after uploader/performer prefix removal. For a generic title such as
        // "General Levy - Dubplate", removing the only word "Dubplate" as a variant would erase
        // the actual title and accidentally restore the entire raw upload title.
        val identityBeforeVariantRemoval = title.replace(feat, " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '–', '—', '|', '•')

        // Variant words are removed from the core identity but retained in qualifiers above.
        variantAliases.values.flatten().sortedByDescending { it.length }.forEach { alias ->
            title = title.replace(Regex("""(?i)(^|\s+)${Regex.escape(alias)}($|\s+)"""), " ")
        }
        title = title.replace(feat, " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '–', '—', '|', '•')

        if (title.isBlank()) title = identityBeforeVariantRemoval.ifBlank { rawTitle }
        val core = normalizeDisplay(title)
        return NormalizedTrackMetadata(
            originalTitle = rawTitle,
            originalArtist = rawArtist,
            coreTitle = core,
            performerCandidates = (if ("cover" in qualifiers) explicitCoverPerformers + performers else performers)
                .filter { it.isNotBlank() }.distinctBy { it.lowercase() },
            uploaderContext = rawArtist,
            qualifiers = qualifiers,
            genericTitle = normalizeText(core) in genericTitles || normalizeText(core).length <= 4,
            durationSec = song.durationSec
        )
    }

    fun scoreCandidate(
        wanted: NormalizedTrackMetadata,
        candidateTitle: String,
        candidateArtist: String,
        candidateDurationSec: Double,
        hasSynced: Boolean
    ): CandidateScore {
        val cTitle = normalizeDisplay(candidateTitle)
        val cArtist = cleanArtist(candidateArtist)
        val candidateQualifiers = linkedSetOf<String>()
        val nAll = normalizeText("$candidateTitle $candidateArtist")
        variantAliases.forEach { (canonical, aliases) ->
            if (aliases.any { nAll.contains(normalizeText(it)) }) candidateQualifiers += canonical
        }

        val titleSimilarity = tokenSimilarity(wanted.coreTitle, cTitle)
        val performerSimilarity = if (wanted.performerCandidates.isEmpty()) 0.55 else {
            // Artist identity needs slightly stricter token handling than song titles. In particular,
            // single-character initials are meaningful ("Artist A" must not match "Artist B").
            // Keep the existing title matcher unchanged so normal title/provider behavior is preserved.
            wanted.performerCandidates.maxOfOrNull { performerSimilarity(it, cArtist) } ?: 0.0
        }
        val durationDiff = if (candidateDurationSec > 0 && wanted.durationSec > 0) {
            abs(candidateDurationSec - wanted.durationSec)
        } else Double.NaN
        val durationScore = when {
            durationDiff.isNaN() -> 0.55
            durationDiff <= 2.5 -> 1.0
            durationDiff <= 6.0 -> 0.90
            durationDiff <= 11.0 -> 0.72
            durationDiff <= 18.0 -> 0.45
            durationDiff <= 30.0 -> 0.18
            else -> 0.0
        }

        val variantAgreement = when {
            wanted.qualifiers.isEmpty() && candidateQualifiers.isEmpty() -> 1.0
            wanted.qualifiers.isEmpty() && candidateQualifiers.isNotEmpty() -> 0.65
            candidateQualifiers.isEmpty() -> 0.68
            wanted.qualifiers.intersect(candidateQualifiers).isNotEmpty() -> 1.0
            else -> 0.25
        }

        // Generic titles are unsafe. A generic "Dubplate" should not win because an uploader/channel
        // name happens to fuzzily match. Performer and duration become much more important.
        val weights = if (wanted.genericTitle) {
            doubleArrayOf(0.30, 0.38, 0.22, 0.10)
        } else {
            doubleArrayOf(0.48, 0.27, 0.17, 0.08)
        }
        var score = (
            titleSimilarity * weights[0] +
                performerSimilarity * weights[1] +
                durationScore * weights[2] +
                variantAgreement * weights[3]
            ) * 100.0
        if (hasSynced) score += 3.0

        val reasons = mutableListOf<String>()
        var suspicious = false
        if (wanted.genericTitle) {
            suspicious = true
            reasons += "generic title"
        }
        if (titleSimilarity < 0.58) {
            suspicious = true
            reasons += "weak title identity"
        }
        if (wanted.performerCandidates.isNotEmpty() && performerSimilarity < 0.42) {
            suspicious = true
            reasons += "performer mismatch"
        }
        if (!durationDiff.isNaN() && durationDiff > 18.0) {
            suspicious = true
            reasons += "duration mismatch"
        }
        if (variantAgreement < 0.5) {
            suspicious = true
            reasons += "variant mismatch"
        }
        if (wanted.genericTitle && performerSimilarity < 0.72) score -= 18.0

        return CandidateScore(
            total = score.toInt().coerceIn(0, 100),
            title = (titleSimilarity * 100).toInt().coerceIn(0, 100),
            performer = (performerSimilarity * 100).toInt().coerceIn(0, 100),
            duration = (durationScore * 100).toInt().coerceIn(0, 100),
            variant = (variantAgreement * 100).toInt().coerceIn(0, 100),
            suspicious = suspicious,
            reason = reasons.joinToString().ifBlank { "strong metadata" }
        )
    }


    private fun performerSimilarity(a: String, b: String): Double {
        val aa = normalizeText(a)
        val bb = normalizeText(b)
        if (aa.isBlank() || bb.isBlank()) return 0.0
        if (aa == bb) return 1.0

        // Unlike tokenSimilarity(), retain one-character tokens because initials/suffixes can
        // distinguish different performers. Example: "Artist A" vs "Artist B".
        val at = aa.split(' ').filter { it.isNotBlank() }.toSet()
        val bt = bb.split(' ').filter { it.isNotBlank() }.toSet()
        if (at.isEmpty() || bt.isEmpty()) return 0.0

        val intersection = at.intersect(bt).size.toDouble()
        val jaccard = intersection / at.union(bt).size.toDouble().coerceAtLeast(1.0)
        val containment = intersection / minOf(at.size, bt.size).coerceAtLeast(1).toDouble()
        return (jaccard * 0.65 + containment * 0.35).coerceIn(0.0, 1.0)
    }

    fun tokenSimilarity(a: String, b: String): Double {
        val aa = normalizeText(a)
        val bb = normalizeText(b)
        if (aa.isBlank() || bb.isBlank()) return 0.0
        if (aa == bb) return 1.0
        if (aa.contains(bb) || bb.contains(aa)) {
            val short = minOf(aa.length, bb.length).toDouble()
            val long = maxOf(aa.length, bb.length).toDouble().coerceAtLeast(1.0)
            return 0.82 + 0.16 * (short / long)
        }
        val at = aa.split(' ').filter { it.length > 1 }.toSet()
        val bt = bb.split(' ').filter { it.length > 1 }.toSet()
        if (at.isEmpty() || bt.isEmpty()) return 0.0
        val jaccard = at.intersect(bt).size.toDouble() / at.union(bt).size.toDouble().coerceAtLeast(1.0)
        val containment = at.intersect(bt).size.toDouble() / minOf(at.size, bt.size).coerceAtLeast(1).toDouble()
        return (jaccard * 0.65 + containment * 0.35).coerceIn(0.0, 1.0)
    }

    fun normalizeText(value: String): String = value.lowercase()
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun normalizeDisplay(value: String): String = value
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun cleanArtist(value: String): String = value
        .replace(Regex("""(?i)\s*-\s*topic$|\bvevo$|\bofficial$"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
}
