package com.brain.gallery.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class GlassTab(val label: String, val icon: ImageVector)

/** Breathing room between the pill and the edge of its tab. */
private val PillInset = 5.dp

/**
 * The nav, expressed purely in terms of the glass material.
 *
 * Everything visual comes from [GlassStyle] and [glass], so the same bar can be
 * dropped into another project by changing the style and the three colours.
 *
 * Selection works the way it does on iOS: one pill of glass travels between the
 * tabs rather than each tab painting itself selected, the icon takes a small pop
 * as it arrives, and the labels crossfade. It also recedes rather than vanishing:
 * at [visible] = 0 it is 82% scale and fully transparent, which reads as stepping
 * aside instead of blinking out.
 */
@Composable
fun GlassNav(
    tabs: List<GlassTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    visible: Float,
    modifier: Modifier = Modifier,
    style: GlassStyle = Glass.onDark(Color(0xFF8B5CF6)),
    selectedColor: Color = Color(0xFF8B5CF6),
    idleColor: Color = Color(0xFF9AA6B8),
    selectedLabelColor: Color = Color.White,
    height: Dp = 56.dp
) {
    if (tabs.isEmpty()) return
    val index = selected.coerceIn(0, tabs.lastIndex)

    val alpha = rememberGlassNavAlpha(visible)
    val trackWidth = remember { mutableStateOf(0f) }
    val tabWidths = remember { mutableStateMapOf<Int, Int>() }
    val pillShape = RoundedCornerShape(percent = 50)
    val pillStyle = rememberGlassNavIndicatorStyle(style)
    val motion = rememberGlassNavIndicatorMotion(
        selected = index,
        count = tabs.size,
        trackWidthPx = { trackWidth.value },
        selectedWidthPx = { (tabWidths[index] ?: 0).toFloat() },
        insetPx = with(LocalDensity.current) { PillInset.toPx() }
    )

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .glassNavReveal { alpha.value }
                .widthIn(min = 236.dp, max = 330.dp)
                .height(height)
                .glass(RoundedCornerShape(style.corner), style)
                .padding(horizontal = 5.dp)
        ) {
            GlassNavIndicatorLayer(
                motion = motion,
                shape = pillShape,
                style = pillStyle
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .onSizeChanged { trackWidth.value = it.width.toFloat() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { i, tab ->
                    GlassNavItem(
                        label = tab.label,
                        icon = tab.icon,
                        selected = i == index,
                        onClick = { onSelect(i) },
                        onWidth = { tabWidths[i] = it },
                        idleColor = idleColor,
                        selectedColor = selectedColor,
                        selectedLabelColor = selectedLabelColor
                    )
                }
            }
        }
    }
}

/**
 * One tab. It is the same whatever is selected, which is the point: the pill
 * behind it carries the selection, so switching tabs moves one thing instead of
 * two, and the label never changes width as it gains weight — a re-measured label
 * would drag the pill's target around mid-flight.
 */
@Composable
private fun RowScope.GlassNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    onWidth: (Int) -> Unit,
    idleColor: Color,
    selectedColor: Color,
    selectedLabelColor: Color
) {
    val iconScale = rememberGlassNavIconScale(selected)
    val labelAlpha = rememberGlassNavLabelAlpha(selected)
    val iconInk by animateColorAsState(
        targetValue = if (selected) selectedColor else idleColor,
        animationSpec = GlassMotion.enter(),
        label = "navIconInk"
    )
    val labelInk by animateColorAsState(
        targetValue = if (selected) selectedLabelColor else idleColor,
        animationSpec = GlassMotion.enter(),
        label = "navLabelInk"
    )

    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .padding(horizontal = 6.dp)
                .onSizeChanged { onWidth(it.width) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconInk,
                modifier = Modifier
                    .size(19.dp)
                    .glassNavIconLayer { iconScale.value }
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = labelInk,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.glassNavLabelLayer { labelAlpha.value }
            )
        }
    }
}
