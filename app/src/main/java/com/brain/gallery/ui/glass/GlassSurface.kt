package com.brain.gallery.ui.glass

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Padding a caller should add so its content clears the floating nav.
 *
 * The system bar is read, never guessed. The old 24dp stand-in was a guess
 * that both over-reserved on gesture navigation and sat under a real
 * three-button bar — a dead band on one phone, covered content on another.
 * This is pill + breathing room + the actual bottom inset, and the nav
 * itself uses the same inset, so the two can never drift apart.
 */
@Composable
fun glassReserve(
    edgePadding: Dp = 10.dp,
    respectNavigationBars: Boolean = true
): Dp {
    val systemBottom = if (respectNavigationBars) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else 0.dp
    return GlassNavHeight + edgePadding + systemBottom
}
