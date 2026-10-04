package com.deepecho.mobile.ui

import java.util.concurrent.ConcurrentHashMap

/**
 * Display-only romanization helper.
 * It never changes/replaces the original LRCLIB lyrics and uses Android ICU when available.
 */
object Romanizer {
    private val cache = ConcurrentHashMap<String, String>()

    fun romanize(text: String): String? {
        val clean = text.trim()
        if (clean.isBlank()) return null
        return cache.getOrPut(clean) { compute(clean).orEmpty() }.ifBlank { null }
    }

    private fun compute(text: String): String? {
        val result = runCatching {
            val cls = Class.forName("android.icu.text.Transliterator")
            val getInstance = cls.getMethod("getInstance", String::class.java)
            val instance = getInstance.invoke(
                null,
                "Any-Latin; NFD; [:Nonspacing Mark:] Remove; NFC"
            )
            val transliterate = cls.getMethod("transliterate", String::class.java)
            transliterate.invoke(instance, text) as? String
        }.getOrNull()
            ?.replace(Regex("\\s+"), " ")
            ?.trim()

        if (result.isNullOrBlank()) return null
        return if (comparable(text) == comparable(result)) null else result
    }

    private fun comparable(value: String): String = value
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "")
}
