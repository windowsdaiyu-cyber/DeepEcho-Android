package com.deepecho.mobile.data

import com.deepecho.mobile.net.YouTubeApi
import java.util.Calendar

data class HomeMix(val title: String, val query: String)
data class TopArtist(val name: String, val thumb: String?, val plays: Int)

/**
 * Lightweight on-device taste engine.
 * No account/server is required: history, likes and downloads are the signals.
 */
object TasteEngine {
    private fun signals(): List<Song> =
        Store.history.value.take(100) +
            Store.liked.value.take(100) +
            Store.downloads.value.map { it.song }.take(60)

    fun topArtists(limit: Int = 10): List<TopArtist> {
        val source = signals()
        val counts = linkedMapOf<String, Int>()
        source.filter { it.artist.isNotBlank() }.forEach { s ->
            counts[s.artist] = (counts[s.artist] ?: 0) + 1
        }
        return counts.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { entry ->
                val art = source.firstOrNull { it.artist.equals(entry.key, true) }?.thumb
                TopArtist(entry.key, art, entry.value)
            }
    }


    /**
     * Expands the purely local taste ranking with related artists only when the user has
     * listened to fewer than [limit] unique artists. Call from Dispatchers.IO.
     */
    fun topArtistsExpanded(limit: Int = 10): List<TopArtist> {
        val local = topArtists(limit)
        if (local.size >= limit) return local.take(limit)

        val out = linkedMapOf<String, TopArtist>()
        local.forEach { out[it.name.lowercase()] = it }

        val seedNames = local.take(3).map { it.name }
        val queries = buildList {
            seedNames.forEach { add("$it similar songs") }
            if (seedNames.isNotEmpty()) add("${seedNames.joinToString(" ")} music mix")
            add("top Bollywood singers songs")
            add("Hindi pop artists hits")
            add("trending singers India songs")
            add("top international singers songs")
        }.distinct()

        for (query in queries) {
            val songs = runCatching { YouTubeApi.search(query) }.getOrDefault(emptyList())
            for (song in songs) {
                val artist = song.artist.trim()
                if (artist.isBlank() || artist.equals("YouTube Music", true)) continue
                val key = artist.lowercase()
                if (key !in out) {
                    out[key] = TopArtist(artist, song.thumb, 0)
                    if (out.size >= limit) break
                }
            }
            if (out.size >= limit) break
        }
        return out.values.take(limit)
    }

    /**
     * Personalized local typeahead used only as a fallback/boost for the real YouTube
     * autocomplete feed. Never manufactures low-value suffixes such as "songs",
     * "official song", "album", or "artist".
     */
    fun searchSuggestions(input: String, extraSongs: List<Song> = emptyList(), limit: Int = 8): List<String> {
        val q = input.trim().replace(Regex("\\s+"), " ")
        if (q.length < 2) return emptyList()
        val ql = q.lowercase()
        val source = (signals() + extraSongs).distinctBy { it.url }
        val recentSearches = Store.searchHistory.value

        data class Candidate(val text: String, val weight: Int)
        val candidates = mutableListOf<Candidate>()

        recentSearches.forEachIndexed { index, text ->
            candidates += Candidate(text, 320 - index * 4)
        }
        source.take(220).forEachIndexed { index, song ->
            val decay = index / 10
            if (song.title.isNotBlank()) candidates += Candidate(song.title, 260 - decay)
            if (song.artist.isNotBlank()) candidates += Candidate(song.artist, 225 - decay)
            if (song.title.isNotBlank() && song.artist.isNotBlank()) {
                candidates += Candidate("${song.title} ${song.artist}", 245 - decay)
            }
        }

        return candidates.mapNotNull { candidate ->
            val text = candidate.text.trim().replace(Regex("\\s+"), " ")
            if (text.isBlank() || text.equals(q, true)) return@mapNotNull null
            val lower = text.lowercase()
            val tokenMatch = ql.split(' ').filter { it.length >= 2 }.all { lower.contains(it) }
            val matchBoost = when {
                lower.startsWith(ql) -> 180
                lower.split(Regex("\\s+")).any { it.startsWith(ql) } -> 125
                lower.contains(ql) -> 90
                tokenMatch -> 45
                else -> return@mapNotNull null
            }
            Triple(text, candidate.weight + matchBoost, lower)
        }
            .sortedByDescending { it.second }
            .distinctBy { it.third }
            .map { it.first }
            .take(limit)
    }

    /** Taste-aware compact mood/genre chips on Home. */
    fun moodQuery(label: String): String {
        val artists = topArtists(3).map { it.name }
        val taste = artists.joinToString(" ")
        val mood = when (label.lowercase()) {
            "feel good" -> "feel good happy uplifting songs"
            "romance", "romantic" -> "romantic love songs"
            "relax" -> "relax calm soothing chill songs"
            "bad mood", "sad" -> "sad emotional heartbreak songs"
            "energize", "energy" -> "energetic upbeat power songs"
            "party" -> "party dance hits"
            "workout" -> "workout gym high energy songs"
            "focus" -> "focus concentration soft music"
            "chill" -> "chill lofi acoustic songs"
            "bollywood" -> "Bollywood hits"
            "pop" -> "pop hits"
            "devotional" -> "devotional peaceful songs"
            else -> "$label songs"
        }
        return listOf(taste, mood).filter { it.isNotBlank() }.joinToString(" ")
    }

    fun latestReleaseQuery(): String {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val artists = topArtists(3).map { it.name }
        return if (artists.isNotEmpty()) {
            "${artists.joinToString(" ")} latest new songs releases $year"
        } else {
            "latest new music releases $year India"
        }
    }

    fun homeMixes(history: List<Song>, liked: List<Song>): List<HomeMix> {
        val downloaded = Store.downloads.value.map { it.song }
        val signals = history.take(120) + liked.take(120) + downloaded.take(80)

        val artistCounts = linkedMapOf<String, Int>()
        signals.filter { it.artist.isNotBlank() }.forEach { song ->
            artistCounts[song.artist] = (artistCounts[song.artist] ?: 0) + 1
        }

        val topArtists = artistCounts.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(6)

        val recent = history.firstOrNull()
        val secondRecent = history.drop(1).firstOrNull()
        val recentLiked = liked.firstOrNull()
        val rediscover = history.drop(10).firstOrNull() ?: liked.lastOrNull()
        val downloadedSeed = downloaded.firstOrNull()

        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val day = calendar.get(Calendar.DAY_OF_YEAR)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        val timeVibes = when (hour) {
            in 5..9 -> listOf(
                "Morning lift" to "soft morning feel good songs",
                "Easy start" to "acoustic chill morning songs",
                "Fresh morning" to "fresh upbeat morning music"
            )
            in 10..13 -> listOf(
                "Midday energy" to "upbeat feel good songs",
                "Work flow" to "focus pop chill songs",
                "Sunny mix" to "happy daytime songs"
            )
            in 14..17 -> listOf(
                "Afternoon boost" to "afternoon energy songs",
                "Keep it moving" to "drive upbeat hits",
                "Focus mix" to "work focus music songs"
            )
            in 18..21 -> listOf(
                "Evening unwind" to "evening chill hits",
                "Golden hour" to "romantic evening songs",
                "After-hours mix" to "evening feel good songs"
            )
            else -> listOf(
                "Late-night vibe" to "late night soft songs",
                "Midnight mix" to "midnight chill romantic songs",
                "Night drive" to "night drive songs"
            )
        }
        val timePick = timeVibes[day % timeVibes.size]

        val primaryArtist = topArtists.getOrNull(0)
        val secondArtist = topArtists.getOrNull(1)
        val thirdArtist = topArtists.getOrNull(2)
        val fourthArtist = topArtists.getOrNull(3)
        val artistsText = topArtists.take(3).joinToString(" ")

        val out = mutableListOf<HomeMix>()

        out += HomeMix(
            "For you right now",
            listOf(artistsText, timePick.second)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { timePick.second }
        )

        primaryArtist?.let {
            out += HomeMix("Your ${it.take(22)} radio", "$it radio similar songs")
            out += HomeMix("Because you listen to $it", "$it songs mix")
        }

        recent?.let {
            out += HomeMix(
                "More like ${it.title.take(28)}",
                "${it.artist} ${it.title} similar songs"
            )
        }

        recentLiked?.let {
            out += HomeMix(
                "Because you liked ${it.title.take(26)}",
                "${it.artist} ${it.title} songs like this"
            )
        }

        secondArtist?.let {
            out += HomeMix("More from your taste", "$it best songs mix")
        }

        out += HomeMix(
            timePick.first,
            listOf(primaryArtist.orEmpty(), timePick.second)
                .filter { it.isNotBlank() }
                .joinToString(" ")
        )

        thirdArtist?.let {
            out += HomeMix("Inspired by $it", "$it similar artists songs")
        }

        fourthArtist?.let {
            out += HomeMix("Deep cuts for you", "$it underrated best songs")
        }

        secondRecent?.let {
            out += HomeMix(
                "Continue your vibe",
                "${secondRecent.artist} ${secondRecent.title} radio mix"
            )
        }

        downloadedSeed?.let {
            out += HomeMix(
                "From your offline taste",
                "${it.artist} similar songs mix"
            )
        }

        rediscover?.let {
            out += HomeMix(
                "Rediscover",
                "${it.artist} classics songs"
            )
        }

        val weekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
        out += HomeMix(
            if (weekend) "Weekend soundtrack" else "Daily discovery",
            if (weekend) {
                listOf(primaryArtist.orEmpty(), "weekend feel good hits")
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            } else {
                listOf(secondArtist.orEmpty(), "fresh recommended songs")
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            }
        )

        out += HomeMix(
            "Fresh for you",
            if (primaryArtist != null) "$primaryArtist new songs releases"
            else "new music releases India"
        )

        out += HomeMix(
            "Hidden gems",
            if (artistsText.isNotBlank()) "$artistsText underrated songs hidden gems"
            else "underrated songs hidden gems"
        )

        out += HomeMix("Trending now", "top trending songs India")

        val moodContext = ExperienceV1128.activeMoodTerms()
        return out
            .map { mix ->
                if (moodContext.isBlank()) mix
                else mix.copy(query = "${mix.query} $moodContext".trim())
            }
            .filter { it.query.isNotBlank() }
            .distinctBy { it.query.lowercase() }
            .take(11)
    }

    fun playlistQuery(): String {
        val artists = topArtists(3).map { it.name }
        return if (artists.isNotEmpty()) {
            "${artists.joinToString(" ")} best playlist"
        } else {
            "top music playlists India"
        }
    }

    /** Blocking; call from Dispatchers.IO. */
    fun searchRecommendations(limit: Int = 10): List<Song> {
        val top = topArtists(3).map { it.name }
        val recent = Store.history.value.firstOrNull()
        val queries = buildList {
            if (top.isNotEmpty()) add("${top.joinToString(" ")} songs mix")
            recent?.let { add("${it.artist} similar songs") }
            add("recommended songs India")
        }.distinct()

        val out = linkedMapOf<String, Song>()
        for (q in queries) {
            runCatching { YouTubeApi.search(q) }.getOrDefault(emptyList()).forEach { song ->
                if (song.durationSec in 45L..900L) out.putIfAbsent(song.url, song)
            }
            if (out.size >= limit * 2) break
        }
        return out.values.take(limit)
    }

    private fun titleKey(title: String): String = title
        .lowercase()
        .replace(Regex("\\([^)]*\\)|\\[[^\\]]*\\]"), " ")
        .replace(
            Regex(
                "\\b(official|video|lyrics?|audio|song|music|hd|4k|remastered|version)\\b"
            ),
            " "
        )
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun scriptBucket(text: String): String = when {
        text.any { it in '\u0900'..'\u097F' } -> "devanagari"
        text.any { it in '\u0980'..'\u09FF' } -> "bengali"
        text.any { it in '\u0B80'..'\u0BFF' } -> "tamil"
        text.any { it in '\u0C00'..'\u0C7F' } -> "telugu"
        text.any { it in '\u0D00'..'\u0D7F' } -> "malayalam"
        text.any { it in '\uAC00'..'\uD7AF' } -> "korean"
        else -> "latin"
    }

    private fun moodTokens(text: String): Set<String> {
        val value = text.lowercase()
        val groups = mapOf(
            "romantic" to listOf("love", "romantic", "ishq", "pyaar", "pyar", "dil", "mohabbat", "jaan"),
            "sad" to listOf("sad", "alone", "broken", "yaad", "cry", "khamosh", "judai"),
            "chill" to listOf("chill", "lofi", "lo-fi", "acoustic", "sukoon", "calm", "rain"),
            "energy" to listOf("dance", "party", "remix", "club", "bhangra", "rock", "workout"),
            "devotional" to listOf("bhajan", "aarti", "devotional", "mantra", "shiv", "krishna", "ram")
        )
        return groups.filterValues { words -> words.any { value.contains(it) } }.keys
    }

    /** Blocking; call from Dispatchers.IO. */
    fun smartNext(seed: Song, excludedUrls: Set<String>, limit: Int = 10): List<Song> {
        val history = Store.history.value
        val liked = Store.liked.value
        val downloaded = Store.downloads.value.map { it.song }
        val tastePool = history.take(120) + liked.take(120) + downloaded.take(80)

        val artistCounts = tastePool
            .filter { it.artist.isNotBlank() }
            .groupingBy { it.artist }
            .eachCount()

        val favoriteArtists = artistCounts.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .filterNot { it.equals(seed.artist, ignoreCase = true) }
            .take(5)

        val seedScript = scriptBucket(seed.title + " " + seed.artist)
        val seedMood = moodTokens(seed.title + " " + seed.artist)
        val activeMoodTerms = ExperienceV1128.activeMoodTerms()
        val activeMoodTokens = moodTokens(activeMoodTerms)
        val feedback = ExperienceV1128.feedbackSnapshot()
        val seedTitleKey = titleKey(seed.title)
        val recentUrls = history.take(28).map { it.url }.toSet()
        val recentArtists = history.take(35).map { it.artist.lowercase() }.toSet()
        val likedArtists = liked.map { it.artist.lowercase() }.toSet()

        val queries = buildList {
            if (seed.artist.isNotBlank()) add("${seed.artist} similar songs")
            add("${seed.title} ${seed.artist} radio")
            if (seedMood.isNotEmpty()) {
                add("${seed.artist} ${seedMood.first()} songs")
            }
            if (activeMoodTerms.isNotBlank()) {
                add("${seed.artist} $activeMoodTerms songs")
                favoriteArtists.getOrNull(0)?.let { add("$it $activeMoodTerms mix") }
            }
            favoriteArtists.getOrNull(0)?.let { add("$it mix") }
            favoriteArtists.getOrNull(1)?.let { add("$it songs") }
            if (seed.artist.isNotBlank()) add("${seed.artist} best songs")
            favoriteArtists.getOrNull(2)?.let { add("$it similar artists") }
            add("songs like ${seed.title} ${seed.artist}")
        }.map { it.trim() }.filter { it.isNotBlank() }.distinct()

        val candidates = linkedMapOf<String, Song>()
        for (query in queries) {
            runCatching { YouTubeApi.search(query) }
                .getOrDefault(emptyList())
                .forEach { song ->
                    if (
                        song.url !in excludedUrls &&
                        song.url != seed.url &&
                        song.durationSec in 45L..900L
                    ) {
                        candidates.putIfAbsent(song.url, song)
                    }
                }
            if (candidates.size >= limit * 5) break
        }

        val scored = candidates.values.mapIndexed { index, song ->
            var score = 2200 - index
            val artist = song.artist.lowercase()
            val candidateKey = titleKey(song.title)
            val candidateMood = moodTokens(song.title + " " + song.artist)
            val candidateScript = scriptBucket(song.title + " " + song.artist)

            if (song.artist.equals(seed.artist, true) && seed.artist.isNotBlank()) score += 260
            if (artist in favoriteArtists.map { it.lowercase() }) score += 230
            if (artist in likedArtists) score += 180
            if (artist in recentArtists) score += 80
            score += (artistCounts[song.artist] ?: 0).coerceAtMost(8) * 22

            if (candidateScript == seedScript) score += 140
            if (seedMood.isNotEmpty() && candidateMood.any { it in seedMood }) score += 150
            if (activeMoodTokens.isNotEmpty() && candidateMood.any { it in activeMoodTokens }) score += 210
            score += ExperienceV1128.affinityAdjustment(song, feedback)

            if (song.url in recentUrls) score -= 900
            if (candidateKey.isNotBlank() && candidateKey == seedTitleKey) score -= 1000

            score to song
        }.sortedByDescending { it.first }

        val result = mutableListOf<Song>()
        val usedTitleKeys = linkedSetOf<String>()
        var sameSeedArtist = 0

        for ((_, song) in scored) {
            val key = titleKey(song.title)
            if (key.isNotBlank() && !usedTitleKeys.add(key)) continue

            val sameArtist = song.artist.equals(seed.artist, true) && seed.artist.isNotBlank()
            if (sameArtist && sameSeedArtist >= 1) continue
            if (sameArtist) sameSeedArtist++

            result += song
            if (result.size >= limit) break
        }
        return result
    }

}
