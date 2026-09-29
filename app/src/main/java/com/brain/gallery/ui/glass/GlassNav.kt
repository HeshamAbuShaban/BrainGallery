package com.brain.gallery.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brain.gallery.ui.glass.Glass
import com.brain.gallery.ui.glass.glass

data class GlassTab(val label: String, val icon: ImageVector)

/**
 * The nav, expressed purely in terms of the glass material.
 *
 * Everything visual comes from [GlassStyle] and [glass], so the same bar can be
 * dropped into another project by changing the style and the three colours. It
 * also recedes rather than vanishing: at [visible] = 0 it is 82% scale and fully
 * transparent, which reads as stepping aside instead of blinking out.
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
    height: androidx.compose.ui.unit.Dp = 56.dp
) {
    val a by animateFloatAsState(
        targetValue = visible.coerceIn(0f, 1f),
        animationSpec = tween(260),
        label = "navAlpha"
    )

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier
                .graphicsLayer {
                    alpha = a
                    val s = 0.82f + 0.18f * a
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .widthIn(min = 236.dp, max = 330.dp)
                .height(height)
                .glass(RoundedCornerShape(style.corner), style)
                .padding(horizontal = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { i, t ->
                val sel = selected == i
                val pill = RoundedCornerShape(height / 2)
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(pill)
                        .background(
                            if (sel) Brush.horizontalGradient(
                                0f to selectedColor.copy(alpha = 0.34f),
                                1f to selectedColor.copy(alpha = 0.13f)
                            ) else SolidColor(Color.Transparent)
                        )
                        .clickable { onSelect(i) }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(t.icon, null,
                        tint = if (sel) selectedColor else idleColor,
                        modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t.label,
                        color = if (sel) selectedLabelColor else idleColor,
                        fontSize = 12.sp, maxLines = 1,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}
