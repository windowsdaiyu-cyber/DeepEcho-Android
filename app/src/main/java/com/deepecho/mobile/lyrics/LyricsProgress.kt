package com.deepecho.mobile.lyrics

import kotlinx.coroutines.flow.MutableStateFlow

object LyricsProgress {
    val states = MutableStateFlow<Map<String, String>>(emptyMap())

    fun set(songKey: String, status: String) {
        states.value = states.value.toMutableMap().apply { put(songKey, status) }
    }

    fun clear(songKey: String) {
        if (songKey !in states.value) return
        states.value = states.value.toMutableMap().apply { remove(songKey) }
    }
}
