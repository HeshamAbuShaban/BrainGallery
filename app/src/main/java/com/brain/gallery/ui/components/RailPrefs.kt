package com.brain.gallery.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * "More like this" rails, folded by default.
 *
 * The feature is loved and the complaint is real: a rail pinned under the
 * content covers the very thing the viewer opened. So the rail lives behind
 * its own header — one thin line that is always there, one tap to unfold —
 * and the fold is remembered for the session in BOTH viewers (the video
 * spotlight and the photo viewer) through this one shared switch.
 */
object RailPrefs {
    var expanded by mutableStateOf(false)
}
