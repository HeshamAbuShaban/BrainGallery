package com.brain.gallery.ui.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** How small the pane is at the start of its entrance. Enough to read as growing. */
private const val EnterScale = 0.94f

/** How much of [selectedColor] a press lays down. Felt, not announced. */
private const val PressWashAlpha = 0.22f

/** Ink multiplier on a row that cannot be tapped. */
private const val DisabledInk = 0.36f

/** The hairline between groups of rows. */
private const val DividerAlpha = 0.09f

/**
 * One row of a menu.
 *
 * [tint] is the whole reason this is a data class rather than a list of strings:
 * a row that carries its own colour and a semibold label is how "Delete" reads as
 * destructive without a second widget, and a caller that never passes a tint gets
 * a plain neutral row.
 */
data class GlassMenuItem(
    val label: String,
    val icon: ImageVector? = null,
    val tint: Color? = null,
    val enabled: Boolean = true,
    val dividerAfter: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Grows a list of [GlassMenuItem] one row at a time.
 *
 * A DSL rather than a list because the common case is short and the alternative
 * is a screen's worth of index bookkeeping. [divider] attaches to the row above
 * it, so the caller writes the separator where it belongs in reading order
 * instead of setting a flag on a row that has already been declared.
 */
class GlassMenuScope internal constructor() {
    private val rows = mutableListOf<GlassMenuItem>()

    fun item(
        label: String,
        icon: ImageVector? = null,
        tint: Color? = null,
        enabled: Boolean = true,
        dividerAfter: Boolean = false,
        onClick: () -> Unit
    ) {
        rows += GlassMenuItem(
            label = label,
            icon = icon,
            tint = tint,
            enabled = enabled,
            dividerAfter = dividerAfter,
            onClick = onClick
        )
    }

    fun divider() {
        val last = rows.lastOrNull() ?: return
        rows[rows.lastIndex] = last.copy(dividerAfter = true)
    }

    internal fun build(): List<GlassMenuItem> = rows.toList()
}

/** Runs a [GlassMenuScope] block and hands back the rows it declared. */
fun glassMenuItems(block: GlassMenuScope.() -> Unit): List<GlassMenuItem> =
    GlassMenuScope().apply(block).build()

/**
 * A menu pane in the same material as everything else.
 *
 * This is a pane inside the caller's overlay Box, not a [androidx.compose.ui.window.Popup].
 * A Popup is a separate window: it cannot read what is behind it, so the wash this
 * material is made of would have nothing to tint and the menu would read as an
 * opaque card pasted on the screen. Living in the same window is what makes the
 * glass work at all, and the cost is that the caller owns the scrim — see
 * [GlassMenuOverlay], which is one.
 *
 * The pane holds a fixed [anchorWidth] rather than hugging its content, so opening
 * a menu whose labels are longer does not move the rows as it measures them.
 *
 * Arrival and exit are deliberately different springs. The pane growing in is a
 * droplet landing — [GlassMotion.liquid] lets it overshoot a little, which reads
 * as the menu arriving rather than being switched on. The fade and the whole
 * exit stay on tweens: opacity that overshoots flashes brighter than fully
 * visible, and a pane that bounces on its way out reads as coming back.
 *
 * No reduced-motion signal is consulted: this Compose version exposes none to a
 * library, and reading platform settings from a component would be a guess. The
 * durations are kept short instead ([GlassMotion.mediumMs]/[GlassMotion.fastMs]).
 */
@Composable
fun GlassMenu(
    items: List<GlassMenuItem>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    style: GlassStyle = GlassStyle(),
    selectedColor: Color = Color(0xFF8B5CF6),
    contentColor: Color = Color(0xFFE8ECF4),
    anchorWidth: Dp = 220.dp,
    corner: Dp = style.corner
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.width(anchorWidth),
        enter = fadeIn(tween(GlassMotion.mediumMs, easing = GlassMotion.emphasized)) +
            scaleIn(initialScale = EnterScale, animationSpec = GlassMotion.liquid()),
        exit = fadeOut(tween(GlassMotion.fastMs, easing = GlassMotion.exit)) +
            scaleOut(targetScale = EnterScale, animationSpec = tween(GlassMotion.fastMs, easing = GlassMotion.exit))
    ) {
        if (items.isEmpty()) return@AnimatedVisibility
        Column(
            Modifier
                .glass(RoundedCornerShape(corner), style)
                .padding(vertical = 6.dp)
        ) {
            items.forEach { item ->
                GlassMenuRow(
                    item = item,
                    onDismiss = onDismiss,
                    selectedColor = selectedColor,
                    contentColor = contentColor
                )
            }
        }
    }
}

/**
 * One row: an optional icon, the label, and the press.
 *
 * The press is a wash rather than a ripple because a ripple inside a translucent
 * pane draws its own circle over the wash and the two disagree on the edges. The
 * indicator is nulled out for that reason — [collectIsPressedAsState] already
 * knows about the press, and letting the ripple run as well would double it.
 *
 * The tappable node and the artwork are deliberately not the same box: the wash
 * keeps its 40.dp, and the hit node grows to the 48.dp floor around it
 * ([minimumInteractiveComponentSize] enlarges the node without touching what it
 * measures). The interactive modifier comes first in the chain so the semantics
 * node owns the grown bounds — TalkBack and the accessibility scanner then see
 * the target, not the artwork.
 */
@Composable
private fun GlassMenuRow(
    item: GlassMenuItem,
    onDismiss: () -> Unit,
    selectedColor: Color,
    contentColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val wash by animateColorAsState(
        targetValue = selectedColor.copy(alpha = if (pressed) PressWashAlpha else 0f),
        animationSpec = tween(GlassMotion.fastMs, easing = GlassMotion.standard),
        label = "menuRowWash"
    )

    val dim = if (item.enabled) 1f else DisabledInk
    val base = item.tint ?: contentColor
    val ink = base.copy(alpha = base.alpha * dim)

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = item.enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button
                ) {
                    item.onClick()
                    onDismiss()
                }
                .minimumInteractiveComponentSize()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(wash)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.icon != null) {
                    Icon(
                        imageVector = item.icon,
                        // Beside a label, so it is decoration: the row already
                        // announces its text, and a second description would
                        // read the icon twice.
                        contentDescription = null,
                        tint = ink,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(11.dp))
                }
                Text(
                    text = item.label,
                    color = ink,
                    fontSize = 13.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (item.tint != null) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
        if (item.dividerAfter) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 3.dp)
                    .height(1.dp)
                    .background(contentColor.copy(alpha = DividerAlpha))
            )
        }
    }
}

/**
 * The scrim and the pane, so a caller with a menu gets a working menu.
 *
 * The scrim is what dismisses, and it stops taking taps the moment [visible]
 * goes false: the exit has to keep playing, and a transparent scrim still eating
 * presses would make the screen behind it feel dead for a fifth of a second. A
 * disabled `clickable` installs no pointer input at all, so those taps fall
 * through to the content underneath instead.
 *
 * The scrim fades on a tween in both directions. A scrim is pure opacity: an
 * underdamped spring would carry it past its target and flash the screen
 * brighter than fully covered, which reads as a glitch rather than as motion.
 */
@Composable
fun GlassMenuOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.TopEnd,
    anchorPadding: PaddingValues = PaddingValues(12.dp),
    scrimColor: Color = Color(0xFF05070C).copy(alpha = 0.42f),
    style: GlassStyle = GlassStyle(),
    selectedColor: Color = Color(0xFF8B5CF6),
    contentColor: Color = Color(0xFFE8ECF4),
    anchorWidth: Dp = 220.dp,
    corner: Dp = style.corner,
    content: GlassMenuScope.() -> Unit
) {
    val items = glassMenuItems(content)
    val scrim by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(GlassMotion.mediumMs, easing = GlassMotion.standard),
        label = "menuScrim"
    )

    Box(
        modifier
            .fillMaxSize()
            // The pane is a child, so it wins the hit test and the scrim only
            // catches the taps that land beside it. Menu rows are merging nodes
            // of their own, so they stay separate TalkBack targets rather than
            // being flattened into the scrim's node.
            .clickable(
                interactionSource = null,
                indication = null,
                enabled = visible
            ) { onDismiss() },
        contentAlignment = alignment
    ) {
        if (scrim > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(scrimColor.copy(alpha = scrimColor.alpha * scrim))
            )
        }
        GlassMenu(
            items = items,
            onDismiss = onDismiss,
            modifier = Modifier.padding(anchorPadding),
            visible = visible,
            style = style,
            selectedColor = selectedColor,
            contentColor = contentColor,
            anchorWidth = anchorWidth,
            corner = corner
        )
    }
}
