package com.deepecho.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.deepEchoDataStore by preferencesDataStore(name = "deepecho_settings")

class SettingsStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val bridgeUrlKey = stringPreferencesKey("bridge_url")
    private val smartAutoplayKey = booleanPreferencesKey("smart_autoplay")
    private val lyricsFxKey = booleanPreferencesKey("lyrics_fx")

    val theme: Flow<String> = context.deepEchoDataStore.data.map { it[themeKey] ?: "golden" }
    val bridgeUrl: Flow<String> = context.deepEchoDataStore.data.map { it[bridgeUrlKey] ?: DEFAULT_BRIDGE_URL }
    val smartAutoplay: Flow<Boolean> = context.deepEchoDataStore.data.map { it[smartAutoplayKey] ?: true }
    val lyricsFx: Flow<Boolean> = context.deepEchoDataStore.data.map { it[lyricsFxKey] ?: true }

    suspend fun setTheme(value: String) { context.deepEchoDataStore.edit { it[themeKey] = value } }
    suspend fun setBridgeUrl(value: String) { context.deepEchoDataStore.edit { it[bridgeUrlKey] = value.trim().trimEnd('/') } }
    suspend fun setSmartAutoplay(value: Boolean) { context.deepEchoDataStore.edit { it[smartAutoplayKey] = value } }
    suspend fun setLyricsFx(value: Boolean) { context.deepEchoDataStore.edit { it[lyricsFxKey] = value } }

    companion object {
        const val DEFAULT_BRIDGE_URL = "http://10.0.2.2:17832"
    }
}
