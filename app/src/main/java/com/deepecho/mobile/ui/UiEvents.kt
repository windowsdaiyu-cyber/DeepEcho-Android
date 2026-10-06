package com.deepecho.mobile.ui

import kotlinx.coroutines.flow.MutableSharedFlow

sealed class UiAction {
    data class Search(val query: String) : UiAction()
    data class OpenPlayer(val lyrics: Boolean = false) : UiAction()
}

object UiEvents {
    val actions = MutableSharedFlow<UiAction>(extraBufferCapacity = 16)
    @Volatile private var lyricsOnNextPlayer = false

    fun search(query: String) { actions.tryEmit(UiAction.Search(query)) }
    fun openPlayer(lyrics: Boolean = false) {
        if (lyrics) lyricsOnNextPlayer = true
        actions.tryEmit(UiAction.OpenPlayer(lyrics))
    }
    fun consumeLyricsRequest(): Boolean {
        val v = lyricsOnNextPlayer
        lyricsOnNextPlayer = false
        return v
    }
}
