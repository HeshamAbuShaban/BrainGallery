package com.brain.gallery.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brain.gallery.ui.theme.Accent
import com.brain.gallery.ui.theme.Motion
import com.brain.gallery.ui.theme.Text2

data class GlassTab(val label: String, val icon: ImageVector)

/**
 * A floating frosted pill instead of a full-width bar.
 *
 * The old bar was opaque and 80dp tall, so it both hid a strip of the library
 * and, in the reel, fought the scrubber for space. This floats over the content
 * on a translucent gradient with a hairline edge, and it shrinks and fades out of
 * the way entirely while a reel is playing, coming back the moment the reel's own
 * chrome returns.
 *
 * True backdrop blur needs a platform window effect, so the glass is a layered
 * translucency instead: this is what actually reads as frosted on a dark UI, and
 * it costs one draw rather than a blurred copy of everything behind it.
 */
@Composable
fun GlassNav(
    tabs: List<GlassTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    visible: Float,
    modifier: Modifier = Modifier
) {
    val a by animateFloatAsState(
        targetValue = visible.coerceIn(0f, 1f),
        animationSpec = tween(Motion.mediumMs),
        label = "navAlpha"
    )
    val shape = RoundedCornerShape(30.dp)
    val bottomPad = 10.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            Modifier
                .padding(bottom = bottomPad)
                .graphicsLayer {
                    alpha = a
                    // Recede as it fades, so leaving the screen feels like it
                    // steps aside rather than simply disappearing.
                    val s = 0.82f + 0.18f * a
                    scaleX = s
                    scaleY = s
                    // Scale about the bottom edge, which is where the pill sits.
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .widthIn(min = 236.dp, max = 330.dp)
                .height(56.dp)
                .shadow(20.dp, shape, spotColor = Accent.copy(alpha = 0.30f), ambientColor = Color.Black)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.115f),
                        0.55f to Color.White.copy(alpha = 0.055f),
                        1f to Color.White.copy(alpha = 0.085f)
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.13f), shape)
                .padding(horizontal = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { i, t ->
                val sel = selected == i
                val pill = RoundedCornerShape(24.dp)
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(pill)
                        .background(
                            if (sel) Brush.horizontalGradient(
                                0f to Accent.copy(alpha = 0.34f),
                                1f to Accent.copy(alpha = 0.13f)
                            ) else SolidColor(Color.Transparent)
                        )
                        .clickable { onSelect(i) }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        t.icon, null,
                        tint = if (sel) Accent else Text2,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        t.label,
                        color = if (sel) Color.White else Text2,
                        fontSize = 12.sp,
                        maxLines = 1,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/** Height the floating pill takes from the bottom, for content padding. */
val GlassNavReserve = 56.dp + 10.dp + 24.dp
