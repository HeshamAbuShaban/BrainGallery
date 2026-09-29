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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * A content card in the same material as everything else on screen: a title, an
 * optional subtitle, and optional trailing content on the right.
 *
 * Pass [onClick] and the card takes a press. The ripple is clipped to the same
 * corner radius as the pane, otherwise it spills past the rounded corners.
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
        modifier = (if (onClick != null) pane.clip(shape).clickable { onClick() } else pane)
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
 * standard curve, so a chip reads as lighting up rather than switching. The wash
 * is built
 * from [style] instead of the full [glass] modifier because a row of chips, each
 * casting a 20dp shadow, is not a row of chips any more — it is a row of plates.
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

    Row(
        modifier
            .clip(pill)
            .background(fill)
            .border(style.borderWidth, edge, pill)
            .clickable(enabled = enabled) { onClick() }
            .heightIn(min = height)
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

/**
 * A segmented control: one glass track with a lit indicator that slides between
 * the segments. The segments share the track equally, so the indicator's travel
 * is a single animated fraction of the track width — no per-segment measurement
 * to keep in sync.
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
        animationSpec = GlassMotion.enter(),
        label = "segmentedTravel"
    )

    Box(
        modifier
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
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
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
                        .clickable { onSelect(i) },
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
