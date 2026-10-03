package com.deepecho.android.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class FloatingLyricsState(
    val enabled: Boolean = false,
    val title: String = "DEEP-ECHO",
    val line: String = "Floating Lyrics"
)

object FloatingLyricsBus {
    private val mutable = MutableStateFlow(FloatingLyricsState())
    val state: StateFlow<FloatingLyricsState> = mutable

    fun setEnabled(enabled: Boolean) {
        mutable.value = mutable.value.copy(enabled = enabled)
    }

    fun update(title: String, line: String) {
        mutable.value = mutable.value.copy(title = title.ifBlank { "DEEP-ECHO" }, line = line.ifBlank { "♪" })
    }
}
