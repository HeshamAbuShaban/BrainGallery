package com.brain.gallery.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Floor for every tappable node in this file, per platform accessibility guidance. */
private val MinTouchTarget = 48.dp

/**
 * A content card in the same material as everything else on screen: a title, an
 * optional subtitle, and optional trailing content on the right.
 *
 * Pass [onClick] and the card takes a press. The ripple is clipped to the same
 * corner radius as the pane, otherwise it spills past the rounded corners.
 *
 * Interactive modifier first, size floor second: [minimumInteractiveComponentSize]
 * grows the node it wraps, so the semantics node has to sit outside it for the
 * announced bounds to be the 48.dp target rather than the card's own height.
 */
@Composable
fun GlassCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    style: GlassStyle = GlassStyle(),
    titleColor: Color = Color(0xFFF2F5FA),
    subtitleColor: Color = Color(0xFF98A4B8),
    corner: Dp = style.corner,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    trailing: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(corner)
    val pane = modifier.glass(shape, style)
    Row(
        modifier = (if (onClick != null) {
            pane
                .clip(shape)
                .clickable(role = Role.Button) { onClick() }
                .minimumInteractiveComponentSize()
        } else {
            pane
        })
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = subtitleColor,
                    fontSize = 12.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/**
 * A pill for tags and filters.
 *
 * The fill, the hairline and the label all cross-fade on [GlassMotion]'s
 * standard curve, so a chip reads as lighting up rather than switching. Colour
 * stays on a tween deliberately: an underdamped colour animation extrapolates
 * past its endpoints, and a label that flickers through an out-of-range hue
 * reads as broken rather than as liquid.
 *
 * The wash is built from [style] instead of the full [glass] modifier because a
 * row of chips, each casting a 20dp shadow, is not a row of chips any more — it
 * is a row of plates. The one layer kept back from [glass] is the scrim, since
 * a chip is often the only thing between its label and a bright frame.
 *
 * Toggled via [toggleable] so the state is announced as checked, not just
 * clicked, and the pill itself stays 34.dp while the tappable node grows to the
 * 48.dp floor around it.
 */
@Composable
fun GlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    style: GlassStyle = GlassStyle(),
    accentColor: Color = Color(0xFF8B5CF6),
    labelColor: Color = Color(0xFFE8ECF4),
    height: Dp = 34.dp
) {
    val pill = RoundedCornerShape(height / 2)
    val spec = GlassMotion.enter<Color>()
    val fill by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = 0.30f) else style.tint.copy(alpha = 0.07f),
        animationSpec = spec,
        label = "chipFill"
    )
    val edge by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = 0.55f) else style.borderColor(),
        animationSpec = spec,
        label = "chipEdge"
    )
    val ink by animateColorAsState(
        targetValue = when {
            !enabled -> labelColor.copy(alpha = 0.35f)
            selected -> accentColor
            else -> labelColor
        },
        animationSpec = spec,
        label = "chipInk"
    )
    val scrimAlpha = style.scrimAlpha

    Row(
        modifier
            .toggleable(
                value = selected,
                enabled = enabled,
                role = Role.Checkbox
            ) { onClick() }
            .minimumInteractiveComponentSize()
    ) {
        Row(
            Modifier
                .heightIn(min = height)
                .clip(pill)
                .then(
                    if (scrimAlpha > 0f) Modifier.background(Color.Black.copy(alpha = scrimAlpha))
                    else Modifier
                )
                .background(fill)
                .border(style.borderWidth, edge, pill)
                .padding(horizontal = 13.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, null, tint = ink, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                color = ink,
                fontSize = 12.5.sp,
                maxLines = 1,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

/**
 * A segmented control: one glass track with a lit indicator that slides between
 * the segments. The segments share the track equally, so the indicator's travel
 * is a single animated fraction of the track width — no per-segment measurement
 * to keep in sync.
 *
 * Track and hit area are two boxes. The track keeps the caller's [height]; the
 * segments live in a second row that is never shorter than 48.dp, which is what
 * makes the control tappable without making the artwork taller. Both share the
 * same horizontal inset, so a segment's label stays centred on the indicator
 * that lands under it.
 *
 * The indicator arrives on [GlassMotion.liquid] — it is the same droplet as the
 * nav pill and it should splash a little past its mark. The labels' colour
 * crossfades stay on a tween for the same reason as [GlassChip]'s: colour has
 * no meaningful overshoot.
 *
 * No reduced-motion signal is consulted: this Compose version exposes none to a
 * library, so the moves stay short instead of guessing at a platform setting.
 */
@Composable
fun GlassSegmented(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassStyle(),
    selectedColor: Color = Color(0xFF8B5CF6),
    labelColor: Color = Color(0xFFE8ECF4),
    height: Dp = 40.dp,
    corner: Dp = style.corner
) {
    if (labels.isEmpty()) return
    val count = labels.size
    val index = selectedIndex.coerceIn(0, count - 1)
    val cell = RoundedCornerShape(percent = 50)
    val travel by animateFloatAsState(
        targetValue = index.toFloat(),
        animationSpec = GlassMotion.liquid(),
        label = "segmentedTravel"
    )
    val targetHeight = maxOf(height, MinTouchTarget)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .glass(RoundedCornerShape(corner), style)
                .padding(3.dp)
        ) {
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val segment = if (constraints.hasBoundedWidth) {
                            constraints.maxWidth.toFloat() / count
                        } else {
                            placeable.width.toFloat()
                        }
                        layout(placeable.width, placeable.height) {
                            placeable.placeRelative((segment * travel).roundToInt(), 0)
                        }
                    }
                    .fillMaxHeight()
                    .fillMaxWidth(1f / count)
                    .clip(cell)
                    .background(selectedColor.copy(alpha = 0.30f))
                    .border(1.dp, selectedColor.copy(alpha = 0.45f), cell)
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(targetHeight)
                .padding(horizontal = 3.dp)
                .selectableGroup()
        ) {
            labels.forEachIndexed { i, label ->
                val isSelected = i == index
                val ink by animateColorAsState(
                    targetValue = if (isSelected) selectedColor else labelColor,
                    animationSpec = GlassMotion.enter<Color>(),
                    label = "segmentedLabel$i"
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(selected = isSelected, role = Role.Tab) { onSelect(i) }
                        .minimumInteractiveComponentSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = ink,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * A number and what the number is. [accentColor] tints the value and leaves the
 * label alone, which is how a stat that matters is picked out of a row of them.
 */
@Composable
fun GlassStatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    style: GlassStyle = GlassStyle(),
    valueColor: Color = Color(0xFFF2F5FA),
    labelColor: Color = Color(0xFF98A4B8),
    corner: Dp = 20.dp,
    contentPadding: PaddingValues = PaddingValues(vertical = 12.dp, horizontal = 14.dp)
) {
    Column(
        modifier
            .glass(RoundedCornerShape(corner), style)
            .padding(contentPadding)
    ) {
        Text(
            text = value,
            color = accentColor ?: valueColor,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = labelColor,
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
