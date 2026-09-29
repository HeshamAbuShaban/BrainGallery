package com.brain.gallery.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A floating glass surface, anchored to the bottom of whatever it is given.
 *
 * This is the generic form: content, alignment, insets. The nav is one use; a
 * toolbar, a floating action cluster or a scrubber would be others, and none of
 * them should have to re-derive how to sit at the bottom of a screen without
 * covering it.
 *
 * The window is filled so the surface can be pinned to an edge. Callers do not
 * reserve space themselves, which is the whole point: the pane is allowed to
 * float over content, and the content decides how much padding it needs.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassStyle(),
    corner: Dp = style.corner,
    alignment: Alignment = Alignment.BottomCenter,
    /** Distance from the anchored edge, plus whatever the system bars need. */
    edgePadding: Dp = 10.dp,
    respectNavigationBars: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottom = if (respectNavigationBars) edgePadding + systemBottom else edgePadding

    Box(modifier.fillMaxSize(), contentAlignment = alignment) {
        Box(
            Modifier.glass(RoundedCornerShape(corner), style)
                .paddingByEdge(alignment, bottom)
        ) {
            content()
        }
    }
}

/** Pads only the anchored edge, so the pane is flush with the other sides. */
private fun Modifier.paddingByEdge(alignment: Alignment, bottom: Dp): Modifier =
    this.padding(bottom = bottom)

/** Padding a caller should add so its content clears a floating surface. */
fun glassReserve(
    style: GlassStyle = GlassStyle(),
    edgePadding: Dp = 10.dp,
    respectNavigationBars: Boolean = true
): Dp = 56.dp + edgePadding + if (respectNavigationBars) 24.dp else 0.dp
