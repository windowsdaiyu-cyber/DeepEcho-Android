package com.deepecho.mobile.data

import java.time.LocalDate
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

/**
 * v1.12.8 additive experience state.
 *
 * This intentionally does not own playback, lyrics, queue or download state. It only supplies
 * lightweight presentation/recommendation context to the existing systems.
 */
data class PersonalMixSpec(
    val title: String,
    val description: String,
    val query: String,
    val seedThumb: String? = null
)

data class ListeningSessionInsight(
    val headline: String,
    val detail: String
)

data class TasteFeedbackSnapshot(
    val moreUrls: Set<String>,
    val lessUrls: Set<String>,
    val moreArtists: Set<String>,
    val lessArtists: Set<String>
)

object ExperienceV1128 {
    private val _selectedMood = MutableStateFlow<String?>(null)
    val selectedMood: StateFlow<String?> = _selectedMood

    /** Mood is deliberately session-scoped: a cold app start returns to normal taste ranking. */
    fun setMood(label: String?) {
        _selectedMood.value = label?.trim()?.takeIf { it.isNotBlank() }
    }

    fun moodTerms(label: String?): String = when (label?.trim()?.lowercase()) {
        "chill" -> "chill lofi acoustic calm soft"
        "romantic", "romance" -> "romantic love ishq pyaar soft"
        "sad", "bad mood" -> "sad emotional heartbreak mellow"
        "energy", "energize" -> "energetic upbeat power workout"
        "focus" -> "focus concentration calm instrumental soft"
        "party" -> "party dance club upbeat hits"
        else -> ""
    }

    fun activeMoodTerms(): String = moodTerms(_selectedMood.value)

    private fun readSet(key: String): Set<String> = runCatching {
        val raw = Settings.getString(key, "[]")
        val arr = JSONArray(raw)
        buildSet {
            for (i in 0 until arr.length()) arr.optString(i).trim().takeIf { it.isNotBlank() }?.let(::add)
        }
    }.getOrDefault(emptySet())

    private fun writeSet(key: String, value: Set<String>) {
        val arr = JSONArray()
        value.take(120).forEach(arr::put)
        Settings.putString(key, arr.toString())
    }

    fun markMoreLikeThis(song: Song) {
        val urls = readSet("v1128_more_like_urls").toMutableSet().apply { add(song.url) }
        val artists = readSet("v1128_more_like_artists").toMutableSet().apply {
            song.artist.trim().takeIf { it.isNotBlank() }?.let(::add)
        }
        writeSet("v1128_more_like_urls", urls)
        writeSet("v1128_more_like_artists", artists)
        // Explicit positive feedback should cancel the matching soft-negative signal.
        writeSet("v1128_less_like_urls", readSet("v1128_less_like_urls") - song.url)
        writeSet("v1128_less_like_artists", readSet("v1128_less_like_artists").filterNot { it.equals(song.artist, true) }.toSet())
    }

    fun markLessLikeThis(song: Song) {
        val urls = readSet("v1128_less_like_urls").toMutableSet().apply { add(song.url) }
        val artists = readSet("v1128_less_like_artists").toMutableSet().apply {
            song.artist.trim().takeIf { it.isNotBlank() }?.let(::add)
        }
        writeSet("v1128_less_like_urls", urls)
        writeSet("v1128_less_like_artists", artists)
        writeSet("v1128_more_like_urls", readSet("v1128_more_like_urls") - song.url)
        writeSet("v1128_more_like_artists", readSet("v1128_more_like_artists").filterNot { it.equals(song.artist, true) }.toSet())
    }

    fun feedbackSnapshot(): TasteFeedbackSnapshot = TasteFeedbackSnapshot(
        moreUrls = readSet("v1128_more_like_urls"),
        lessUrls = readSet("v1128_less_like_urls"),
        moreArtists = readSet("v1128_more_like_artists"),
        lessArtists = readSet("v1128_less_like_artists")
    )

    /** Additive rank adjustment only; this never bans an artist. */
    fun affinityAdjustment(song: Song, snapshot: TasteFeedbackSnapshot = feedbackSnapshot()): Int {
        var score = 0
        val artist = song.artist.trim()
        if (song.url in snapshot.moreUrls) score += 420
        if (song.url in snapshot.lessUrls) score -= 520
        if (snapshot.moreArtists.any { it.equals(artist, true) }) score += 170
        if (snapshot.lessArtists.any { it.equals(artist, true) }) score -= 220
        return score
    }

    fun dailyMixes(limit: Int = 8): List<PersonalMixSpec> {
        val history = Store.history.value
        val liked = Store.liked.value
        val downloads = Store.downloads.value.map { it.song }
        val signals = (history.take(100) + liked.take(100) + downloads.take(60))
        val artistCounts = signals.filter { it.artist.isNotBlank() }
            .groupingBy { it.artist }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        val primary = artistCounts.getOrNull(0)
        val secondary = artistCounts.getOrNull(1)
        val recent = history.firstOrNull()
        val likedSeed = liked.firstOrNull()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeName = when (hour) {
            in 5..10 -> "Morning Mix"
            in 11..16 -> "Daytime Mix"
            in 17..21 -> "Evening Mix"
            else -> "Late Night Mix"
        }
        val timeTerms = when (hour) {
            in 5..10 -> "soft fresh morning feel good"
            in 11..16 -> "daytime upbeat focus pop"
            in 17..21 -> "evening chill romantic"
            else -> "late night chill soft"
        }
        val mood = activeMoodTerms()

        val specs = buildList {
            if (primary != null) add(PersonalMixSpec(
                title = "${primary.take(20)} Mix",
                description = "Built around one of your strongest artist signals",
                query = listOf(primary, secondary.orEmpty(), mood, "best songs mix").filter { it.isNotBlank() }.joinToString(" "),
                seedThumb = signals.firstOrNull { it.artist.equals(primary, true) }?.thumb
            ))
            add(PersonalMixSpec(
                title = timeName,
                description = "Your taste shaped for this time of day",
                query = listOf(primary.orEmpty(), secondary.orEmpty(), mood, timeTerms, "songs").filter { it.isNotBlank() }.joinToString(" "),
                seedThumb = recent?.thumb
            ))
            likedSeed?.let {
                add(PersonalMixSpec(
                    title = "Recently Loved Mix",
                    description = "More music around songs you chose to keep",
                    query = "${it.artist} ${it.title} similar songs ${mood}".trim(),
                    seedThumb = it.thumb
                ))
            }
            recent?.let {
                add(PersonalMixSpec(
                    title = "Repeat Mix",
                    description = "Familiar favorites with closely related tracks",
                    query = "${it.artist} ${it.title} radio best songs ${mood}".trim(),
                    seedThumb = it.thumb
                ))
            }
            add(PersonalMixSpec(
                title = "Discovery Mix",
                description = "Taste-compatible picks with more room for something new",
                query = listOf(primary.orEmpty(), secondary.orEmpty(), mood, "fresh recommended underrated songs").filter { it.isNotBlank() }.joinToString(" "),
                seedThumb = history.drop(3).firstOrNull()?.thumb ?: liked.drop(2).firstOrNull()?.thumb
            ))
            add(PersonalMixSpec(
                title = "Chill Mix",
                description = "A softer version of your current taste",
                query = listOf(primary.orEmpty(), secondary.orEmpty(), "chill acoustic soft lofi songs").filter { it.isNotBlank() }.joinToString(" "),
                seedThumb = history.drop(1).firstOrNull()?.thumb
            ))
            add(PersonalMixSpec(
                title = "Energy Mix",
                description = "Upbeat tracks without abandoning your artist/language taste",
                query = listOf(primary.orEmpty(), secondary.orEmpty(), "energetic upbeat workout party songs").filter { it.isNotBlank() }.joinToString(" "),
                seedThumb = history.drop(2).firstOrNull()?.thumb
            ))
            if (artistCounts.isNotEmpty()) add(PersonalMixSpec(
                title = "Your Language & Artist Mix",
                description = "Local taste signals lead; generic trends stay secondary",
                query = "${artistCounts.take(4).joinToString(" ")} popular songs ${mood}".trim(),
                seedThumb = signals.firstOrNull()?.thumb
            ))
        }
        return specs.filter { it.query.isNotBlank() }.distinctBy { it.query.lowercase() }.take(limit)
    }

    fun continueVibe(): PersonalMixSpec? {
        val recent = Store.history.value.take(12)
        if (recent.isEmpty()) return null
        val dominantArtist = recent.filter { it.artist.isNotBlank() }
            .groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key
        val seed = recent.first()
        val mood = activeMoodTerms()
        val smartLabel = if (Settings.smartAutoplay.value) "Smart Autoplay context included" else "Starts a fresh discovery context"
        val query = listOf(
            dominantArtist.orEmpty(),
            seed.artist,
            seed.title,
            mood,
            "similar songs radio"
        ).filter { it.isNotBlank() }.joinToString(" ")
        return PersonalMixSpec(
            title = if (_selectedMood.value != null) "Continue your ${_selectedMood.value} vibe" else "Continue Your Vibe",
            description = "$smartLabel • based on your recent session",
            query = query,
            seedThumb = seed.thumb
        )
    }

    fun listeningInsight(): ListeningSessionInsight? {
        val todayMs = Store.dailyListeningMs.value[LocalDate.now().toString()] ?: 0L
        if (todayMs >= 60_000L) {
            val minutes = todayMs / 60_000L
            return ListeningSessionInsight("Listening today: $minutes min", "Real on-device playback time from this device")
        }
        val top = Store.listeningStats.value.maxByOrNull { it.playCount }
        if (top != null && top.playCount > 1) {
            return ListeningSessionInsight("Most replayed: ${top.song.title.take(28)}", "${top.playCount} recorded play starts")
        }
        val recent = Store.history.value.take(8)
        val artist = recent.filter { it.artist.isNotBlank() }.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key
        return artist?.let { ListeningSessionInsight("Recent focus: ${it.take(30)}", "Based only on your local recent history") }
    }
}
