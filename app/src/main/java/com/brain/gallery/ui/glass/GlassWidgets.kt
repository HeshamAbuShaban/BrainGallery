package com.brain.gallery.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.brain.gallery.ui.theme.Accent

/** Floor for every tappable node in this file, per platform accessibility guidance. */
private val MinTouchTarget = 48.dp

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
 * crossfades stay on a tween for the same reason: colour has no meaningful
 * overshoot.
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
    selectedColor: Color = Accent,
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
    /** Hero numerals read bigger than labels; callers decide how much. */
    valueSize: TextUnit = 19.sp,
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
            fontSize = valueSize,
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
