package com.deepecho.mobile.data

/**
 * Local song -> theme advisor.
 * Uses title, artist and artwork identity; when a previous theme is supplied it avoids repeating the same colour on consecutive songs so Auto Theme visibly reacts.
 */
object ThemeAdvisor {
    private val themes = listOf("Golden", "Rose", "Violet", "Ocean", "Emerald", "AMOLED")

    private val keywords = mapOf(
        "Rose" to listOf("love", "romantic", "romance", "ishq", "pyaar", "pyar", "mohabbat", "dil", "heart", "jaan", "lover", "tum", "tera", "meri", "mein"),
        "Violet" to listOf("sad", "alone", "broken", "yaad", "memory", "memories", "moon", "khamosh", "lonely", "dream", "midnight", "phir", "judai"),
        "Ocean" to listOf("rain", "baarish", "barish", "blue", "ocean", "sea", "wave", "chill", "lofi", "lo-fi", "acoustic", "sukoon", "calm", "soft"),
        "Emerald" to listOf("nature", "earth", "green", "fresh", "folk", "desi", "safar", "zindagi", "journey", "village", "mountain", "forest", "mitti"),
        "AMOLED" to listOf("dark", "black", "rage", "trap", "metal", "doom", "villain", "shadow", "nightmare", "hard bass", "drill"),
        "Golden" to listOf("dance", "party", "wedding", "shaadi", "celebration", "gold", "sun", "sunshine", "bhangra", "hit", "festival", "happy", "remix")
    )

    private val artistHints = mapOf(
        "Rose" to listOf("arijit", "anuv jain", "mithoon", "pritam", "atif", "shreya"),
        "Golden" to listOf("badshah", "diljit", "yo yo", "neha kakkar", "karan aujla"),
        "Violet" to listOf("kk", "mohit chauhan", "jubin"),
        "Ocean" to listOf("prateek kuhad", "lofi", "acoustic"),
        "Emerald" to listOf("a.r. rahman", "ar rahman", "folk")
    )

    fun suggest(song: Song, previousTheme: String? = null): String {
        val text = "${song.title} ${song.artist}".lowercase()
        val score = linkedMapOf<String, Int>()
        themes.forEach { score[it] = 0 }

        themes.forEach { theme ->
            val keywordScore = keywords[theme].orEmpty().count { word -> text.contains(word) } * 3
            val artistScore = artistHints[theme].orEmpty().count { word -> text.contains(word) } * 2
            score[theme] = keywordScore + artistScore
        }

        // Artwork URL is stable per result and gives otherwise-neutral tracks a repeatable identity.
        val artworkSeed = song.thumb.orEmpty().lowercase().hashCode()
        val metaSeed = (song.title.lowercase() + "|" + song.artist.lowercase()).hashCode()
        val seed = (metaSeed xor artworkSeed) and Int.MAX_VALUE
        score[themes[seed % themes.size]] = (score[themes[seed % themes.size]] ?: 0) + 1

        val ranked = themes.sortedWith(
            compareByDescending<String> { score[it] ?: 0 }
                .thenBy { ((it.hashCode() xor seed) and Int.MAX_VALUE) }
        )
        var pick = ranked.firstOrNull() ?: "Golden"

        // Auto-theme should visibly react to a new song. Avoid identical consecutive themes when
        // the current match isn't strong enough to justify keeping it.
        if (previousTheme != null && pick == previousTheme) {
            pick = ranked.firstOrNull { it != previousTheme } ?: pick
        }
        return pick
    }
}
