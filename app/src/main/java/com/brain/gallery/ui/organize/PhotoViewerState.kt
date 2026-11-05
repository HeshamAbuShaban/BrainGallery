package com.brain.gallery.ui.organize

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether a photo is open full-screen.
 *
 * The pill reads this and steps aside, exactly as it does for the spotlight:
 * one viewer at a time, and chrome that belongs to the grid has no business
 * over a full-frame still. It lives here rather than in a ViewModel because
 * every surface that opens a photo (hub, search, and whatever comes next)
 * must light the same signal.
 */
object PhotoViewerState {
    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open

    fun set(open: Boolean) { _open.value = open }
}
