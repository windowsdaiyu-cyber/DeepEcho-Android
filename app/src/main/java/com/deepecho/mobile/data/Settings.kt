package com.deepecho.mobile.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

object Settings {
    private lateinit var sp: SharedPreferences

    val theme = MutableStateFlow("Golden")
    val liveTheme = MutableStateFlow(false)
    /** 0 = low, 1 = medium, 2 = high */
    val quality = MutableStateFlow(2)
    val smartAutoplay = MutableStateFlow(true)
    val wordByWordLyrics = MutableStateFlow(false)
    val romanizedLyrics = MutableStateFlow(false)
    val autoThemeWithSong = MutableStateFlow(false)
    val ambientMode = MutableStateFlow(true)
    val smartCache = MutableStateFlow(false)
    val autoUpdateCheck = MutableStateFlow(true)

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        theme.value = sp.getString("theme", "Golden") ?: "Golden"
        liveTheme.value = sp.getBoolean("live_theme", false)
        quality.value = sp.getInt("quality", 2)
        smartAutoplay.value = sp.getBoolean("smart_autoplay", true)
        wordByWordLyrics.value = sp.getBoolean("word_by_word_lyrics", false)
        romanizedLyrics.value = sp.getBoolean("romanized_lyrics", false)
        autoThemeWithSong.value = sp.getBoolean("auto_theme_with_song", false)
        ambientMode.value = sp.getBoolean("ambient_mode", true)
        smartCache.value = sp.getBoolean("smart_cache", false)
        autoUpdateCheck.value = sp.getBoolean("auto_update_check", true)
    }

    fun setTheme(name: String) {
        theme.value = name
        sp.edit().putString("theme", name).apply()
    }

    fun setLiveTheme(enabled: Boolean) {
        liveTheme.value = enabled
        sp.edit().putBoolean("live_theme", enabled).apply()
    }

    fun selectLiveTheme(name: String) {
        setTheme(name)
        setLiveTheme(true)
    }

    fun setQuality(q: Int) {
        quality.value = q
        sp.edit().putInt("quality", q).apply()
    }

    fun setSmartAutoplay(enabled: Boolean) {
        smartAutoplay.value = enabled
        sp.edit().putBoolean("smart_autoplay", enabled).apply()
    }

    fun setWordByWordLyrics(enabled: Boolean) {
        wordByWordLyrics.value = enabled
        sp.edit().putBoolean("word_by_word_lyrics", enabled).apply()
    }

    fun setRomanizedLyrics(enabled: Boolean) {
        romanizedLyrics.value = enabled
        sp.edit().putBoolean("romanized_lyrics", enabled).apply()
    }

    fun setAutoThemeWithSong(enabled: Boolean) {
        autoThemeWithSong.value = enabled
        sp.edit().putBoolean("auto_theme_with_song", enabled).apply()
    }

    fun setAmbientMode(enabled: Boolean) {
        ambientMode.value = enabled
        sp.edit().putBoolean("ambient_mode", enabled).apply()
    }

    fun setSmartCache(enabled: Boolean) {
        smartCache.value = enabled
        sp.edit().putBoolean("smart_cache", enabled).apply()
    }

    fun setAutoUpdateCheck(enabled: Boolean) {
        autoUpdateCheck.value = enabled
        sp.edit().putBoolean("auto_update_check", enabled).apply()
    }

    fun getString(key: String, def: String): String = sp.getString(key, def) ?: def
    fun putString(key: String, value: String) = sp.edit().putString(key, value).apply()
    fun getInt(key: String, def: Int): Int = sp.getInt(key, def)
    fun putInt(key: String, value: Int) = sp.edit().putInt(key, value).apply()
}
