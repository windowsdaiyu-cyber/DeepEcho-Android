package com.deepecho.mobile.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Central app preferences. Keep values as StateFlows so Compose and the playback service can
 * react immediately without polling disk.
 */
object Settings {
    private lateinit var sp: SharedPreferences

    val theme = MutableStateFlow("Golden")
    val liveTheme = MutableStateFlow(true)

    /** 0 = low, 1 = medium, 2 = high */
    val quality = MutableStateFlow(2)
    /** 0 = data saver, 1 = balanced, 2 = best available */
    val downloadQuality = MutableStateFlow(2)
    val wifiQuality = MutableStateFlow(2)
    val mobileQuality = MutableStateFlow(1)

    val smartAutoplay = MutableStateFlow(true)
    val wordByWordLyrics = MutableStateFlow(false)
    val romanizedLyrics = MutableStateFlow(false)
    val autoThemeWithSong = MutableStateFlow(false)
    val ambientMode = MutableStateFlow(true)
    val smartCache = MutableStateFlow(false)
    val autoUpdateCheck = MutableStateFlow(true)

    // Player & audio
    val dataSaverMode = MutableStateFlow(false)
    val downloadWithMetadata = MutableStateFlow(true)
    val downloadWifiOnly = MutableStateFlow(false)
    val playbackEngine = MutableStateFlow("Auto")
    val crossfade = MutableStateFlow(false)
    val crossfadeSeconds = MutableStateFlow(0)
    val automix = MutableStateFlow(false)
    /** History retention window in days. */
    val historyDurationDays = MutableStateFlow(90)
    val skipSilence = MutableStateFlow(false)
    val instantSkipSilence = MutableStateFlow(false)
    val audioNormalization = MutableStateFlow(true)
    /** Quiet / Normal / Loud */
    val loudnessPreset = MutableStateFlow("Normal")
    val spatialAudio = MutableStateFlow(false)
    val audioOffload = MutableStateFlow(false)
    val preloadNextSong = MutableStateFlow(true)
    val preloadLimit = MutableStateFlow(4)
    val preloadLyrics = MutableStateFlow(true)
    val progressiveSeek = MutableStateFlow(false)
    val persistentQueue = MutableStateFlow(false)
    val autoLoadMoreSongs = MutableStateFlow(true)
    val autoDownloadOnLike = MutableStateFlow(false)
    val enableSimilarContent = MutableStateFlow(true)
    val persistentShuffle = MutableStateFlow(false)
    val rememberShuffleRepeat = MutableStateFlow(true)
    val shufflePlaylistFirst = MutableStateFlow(false)
    val preventDuplicateTracks = MutableStateFlow(true)
    val autoSkipOnError = MutableStateFlow(true)
    val stopMusicOnTaskClear = MutableStateFlow(false)
    val pauseWhenMediaMuted = MutableStateFlow(false)
    val resumeOnBluetoothConnect = MutableStateFlow(false)
    val keepScreenOnExpanded = MutableStateFlow(false)
    val exportAsMp3 = MutableStateFlow(false)
    val blockedArtists = MutableStateFlow<Set<String>>(emptySet())

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        theme.value = sp.getString("theme", "Golden") ?: "Golden"
        // v1.10.9+: Classic themes were retired. Live themes are always active.
        liveTheme.value = true
        if (!sp.getBoolean("live_theme", true)) {
            sp.edit().putBoolean("live_theme", true).apply()
        }
        quality.value = sp.getInt("quality", 2).coerceIn(0, 2)
        downloadQuality.value = sp.getInt("download_quality", 2).coerceIn(0, 2)
        wifiQuality.value = sp.getInt("wifi_quality", 2).coerceIn(0, 2)
        mobileQuality.value = sp.getInt("mobile_quality", 1).coerceIn(0, 2)
        smartAutoplay.value = sp.getBoolean("smart_autoplay", true)
        wordByWordLyrics.value = sp.getBoolean("word_by_word_lyrics", false)
        romanizedLyrics.value = sp.getBoolean("romanized_lyrics", false)
        autoThemeWithSong.value = sp.getBoolean("auto_theme_with_song", false)
        ambientMode.value = sp.getBoolean("ambient_mode", true)
        smartCache.value = sp.getBoolean("smart_cache", false)
        autoUpdateCheck.value = sp.getBoolean("auto_update_check", true)

        dataSaverMode.value = sp.getBoolean("data_saver_mode", false)
        downloadWithMetadata.value = sp.getBoolean("download_with_metadata", true)
        downloadWifiOnly.value = sp.getBoolean("download_wifi_only", false)
        playbackEngine.value = sp.getString("playback_engine", "Auto") ?: "Auto"
        crossfade.value = sp.getBoolean("crossfade", false)
        crossfadeSeconds.value = sp.getInt("crossfade_seconds", if (crossfade.value) 3 else 0).coerceIn(0, 5)
        crossfade.value = crossfadeSeconds.value > 0
        automix.value = sp.getBoolean("automix", false)
        historyDurationDays.value = sp.getInt("history_duration_days", 90).coerceIn(1, 365)
        skipSilence.value = sp.getBoolean("skip_silence", false)
        instantSkipSilence.value = sp.getBoolean("instant_skip_silence", false)
        audioNormalization.value = sp.getBoolean("audio_normalization", true)
        loudnessPreset.value = sp.getString("loudness_preset", "Normal") ?: "Normal"
        spatialAudio.value = sp.getBoolean("spatial_audio", false)
        audioOffload.value = sp.getBoolean("audio_offload", false)
        preloadNextSong.value = sp.getBoolean("preload_next_song", true)
        preloadLimit.value = sp.getInt("preload_limit", 4).coerceIn(1, 10)
        preloadLyrics.value = sp.getBoolean("preload_lyrics", true)
        progressiveSeek.value = sp.getBoolean("progressive_seek", false)
        // v1.10.9 migrates automatic startup restore to song-only. The optional full-queue
        // restore can be re-enabled later from Player & audio settings.
        if (!sp.getBoolean("song_only_session_restore_v1109", false)) {
            persistentQueue.value = false
            sp.edit()
                .putBoolean("persistent_queue", false)
                .putBoolean("song_only_session_restore_v1109", true)
                .apply()
        } else {
            persistentQueue.value = sp.getBoolean("persistent_queue", false)
        }
        autoLoadMoreSongs.value = sp.getBoolean("auto_load_more_songs", true)
        autoDownloadOnLike.value = sp.getBoolean("auto_download_on_like", false)
        enableSimilarContent.value = sp.getBoolean("enable_similar_content", true)
        persistentShuffle.value = sp.getBoolean("persistent_shuffle", false)
        rememberShuffleRepeat.value = sp.getBoolean("remember_shuffle_repeat", true)
        shufflePlaylistFirst.value = sp.getBoolean("shuffle_playlist_first", false)
        preventDuplicateTracks.value = sp.getBoolean("prevent_duplicate_tracks", true)
        autoSkipOnError.value = sp.getBoolean("auto_skip_on_error", true)
        stopMusicOnTaskClear.value = sp.getBoolean("stop_music_on_task_clear", false)
        pauseWhenMediaMuted.value = sp.getBoolean("pause_when_media_muted", false)
        resumeOnBluetoothConnect.value = sp.getBoolean("resume_on_bluetooth_connect", false)
        keepScreenOnExpanded.value = sp.getBoolean("keep_screen_on_expanded", false)
        exportAsMp3.value = sp.getBoolean("export_as_mp3", false)
        blockedArtists.value = sp.getStringSet("blocked_artists", emptySet()).orEmpty().map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    fun setTheme(name: String) = saveString(theme, "theme", name)
    fun setLiveTheme(enabled: Boolean) {
        // Compatibility API: Classic mode no longer exists, so Live Theme cannot be disabled.
        liveTheme.value = true
        sp.edit().putBoolean("live_theme", true).apply()
    }
    fun selectLiveTheme(name: String) { setTheme(name); setLiveTheme(true) }
    fun setQuality(q: Int) = saveInt(quality, "quality", q.coerceIn(0, 2))
    fun setDownloadQuality(q: Int) = saveInt(downloadQuality, "download_quality", q.coerceIn(0, 2))
    fun setWifiQuality(q: Int) = saveInt(wifiQuality, "wifi_quality", q.coerceIn(0, 2))
    fun setMobileQuality(q: Int) = saveInt(mobileQuality, "mobile_quality", q.coerceIn(0, 2))
    fun setSmartAutoplay(enabled: Boolean) = saveBoolean(smartAutoplay, "smart_autoplay", enabled)
    fun setWordByWordLyrics(enabled: Boolean) = saveBoolean(wordByWordLyrics, "word_by_word_lyrics", enabled)
    fun setRomanizedLyrics(enabled: Boolean) = saveBoolean(romanizedLyrics, "romanized_lyrics", enabled)
    fun setAutoThemeWithSong(enabled: Boolean) = saveBoolean(autoThemeWithSong, "auto_theme_with_song", enabled)
    fun setAmbientMode(enabled: Boolean) = saveBoolean(ambientMode, "ambient_mode", enabled)
    fun setSmartCache(enabled: Boolean) = saveBoolean(smartCache, "smart_cache", enabled)
    fun setAutoUpdateCheck(enabled: Boolean) = saveBoolean(autoUpdateCheck, "auto_update_check", enabled)

    fun setDataSaverMode(v: Boolean) = saveBoolean(dataSaverMode, "data_saver_mode", v)
    fun setDownloadWithMetadata(v: Boolean) = saveBoolean(downloadWithMetadata, "download_with_metadata", v)
    fun setDownloadWifiOnly(v: Boolean) = saveBoolean(downloadWifiOnly, "download_wifi_only", v)
    fun setPlaybackEngine(v: String) = saveString(playbackEngine, "playback_engine", v)
    fun setCrossfade(v: Boolean) {
        saveBoolean(crossfade, "crossfade", v)
        if (!v) setCrossfadeSeconds(0) else if (crossfadeSeconds.value == 0) setCrossfadeSeconds(3)
    }
    fun setCrossfadeSeconds(v: Int) {
        val sec = v.coerceIn(0, 5)
        saveInt(crossfadeSeconds, "crossfade_seconds", sec)
        saveBoolean(crossfade, "crossfade", sec > 0)
    }
    fun setAutomix(v: Boolean) = saveBoolean(automix, "automix", v)
    fun setHistoryDurationDays(v: Int) = saveInt(historyDurationDays, "history_duration_days", v.coerceIn(1, 365))
    fun setSkipSilence(v: Boolean) = saveBoolean(skipSilence, "skip_silence", v)
    fun setInstantSkipSilence(v: Boolean) = saveBoolean(instantSkipSilence, "instant_skip_silence", v)
    fun setAudioNormalization(v: Boolean) = saveBoolean(audioNormalization, "audio_normalization", v)
    fun setLoudnessPreset(v: String) = saveString(loudnessPreset, "loudness_preset", v)
    fun setSpatialAudio(v: Boolean) = saveBoolean(spatialAudio, "spatial_audio", v)
    fun setAudioOffload(v: Boolean) = saveBoolean(audioOffload, "audio_offload", v)
    fun setPreloadNextSong(v: Boolean) = saveBoolean(preloadNextSong, "preload_next_song", v)
    fun setPreloadLimit(v: Int) = saveInt(preloadLimit, "preload_limit", v.coerceIn(1, 10))
    fun setPreloadLyrics(v: Boolean) = saveBoolean(preloadLyrics, "preload_lyrics", v)
    fun setProgressiveSeek(v: Boolean) = saveBoolean(progressiveSeek, "progressive_seek", v)
    fun setPersistentQueue(v: Boolean) = saveBoolean(persistentQueue, "persistent_queue", v)
    fun setAutoLoadMoreSongs(v: Boolean) = saveBoolean(autoLoadMoreSongs, "auto_load_more_songs", v)
    fun setAutoDownloadOnLike(v: Boolean) = saveBoolean(autoDownloadOnLike, "auto_download_on_like", v)
    fun setEnableSimilarContent(v: Boolean) = saveBoolean(enableSimilarContent, "enable_similar_content", v)
    fun setPersistentShuffle(v: Boolean) = saveBoolean(persistentShuffle, "persistent_shuffle", v)
    fun setRememberShuffleRepeat(v: Boolean) = saveBoolean(rememberShuffleRepeat, "remember_shuffle_repeat", v)
    fun setShufflePlaylistFirst(v: Boolean) = saveBoolean(shufflePlaylistFirst, "shuffle_playlist_first", v)
    fun setPreventDuplicateTracks(v: Boolean) = saveBoolean(preventDuplicateTracks, "prevent_duplicate_tracks", v)
    fun setAutoSkipOnError(v: Boolean) = saveBoolean(autoSkipOnError, "auto_skip_on_error", v)
    fun setStopMusicOnTaskClear(v: Boolean) = saveBoolean(stopMusicOnTaskClear, "stop_music_on_task_clear", v)
    fun setPauseWhenMediaMuted(v: Boolean) = saveBoolean(pauseWhenMediaMuted, "pause_when_media_muted", v)
    fun setResumeOnBluetoothConnect(v: Boolean) = saveBoolean(resumeOnBluetoothConnect, "resume_on_bluetooth_connect", v)
    fun setKeepScreenOnExpanded(v: Boolean) = saveBoolean(keepScreenOnExpanded, "keep_screen_on_expanded", v)
    fun setExportAsMp3(v: Boolean) = saveBoolean(exportAsMp3, "export_as_mp3", v)
    fun isArtistBlocked(name: String): Boolean = blockedArtists.value.any { it.equals(name.trim(), true) }
    fun toggleBlockedArtist(name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        val now = blockedArtists.value.toMutableSet()
        val existing = now.firstOrNull { it.equals(clean, true) }
        if (existing != null) now.remove(existing) else now.add(clean)
        blockedArtists.value = now
        sp.edit().putStringSet("blocked_artists", now).apply()
    }
    fun clearBlockedArtists() { blockedArtists.value = emptySet(); sp.edit().remove("blocked_artists").apply() }

    private fun saveBoolean(flow: MutableStateFlow<Boolean>, key: String, value: Boolean) {
        flow.value = value
        sp.edit().putBoolean(key, value).apply()
    }

    private fun saveInt(flow: MutableStateFlow<Int>, key: String, value: Int) {
        flow.value = value
        sp.edit().putInt(key, value).apply()
    }

    private fun saveString(flow: MutableStateFlow<String>, key: String, value: String) {
        flow.value = value
        sp.edit().putString(key, value).apply()
    }

    fun getString(key: String, def: String): String = sp.getString(key, def) ?: def
    fun putString(key: String, value: String) = sp.edit().putString(key, value).apply()
    fun getInt(key: String, def: Int): Int = sp.getInt(key, def)
    fun putInt(key: String, value: Int) = sp.edit().putInt(key, value).apply()
    fun getBoolean(key: String, def: Boolean): Boolean = sp.getBoolean(key, def)
    fun putBoolean(key: String, value: Boolean) = sp.edit().putBoolean(key, value).apply()
}
