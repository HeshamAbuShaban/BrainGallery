package com.brain.gallery.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.ui.player.PlayerManager
import com.brain.gallery.ui.theme.Accent
import com.brain.gallery.ui.theme.Motion
import com.brain.gallery.ui.theme.Numeral
import com.brain.gallery.ui.theme.Pink
import com.brain.gallery.ui.theme.Text2
import kotlinx.coroutines.delay

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/**
 * Watch-from-anywhere with real controls: scrub, speed, loop, and a
 * More-like-this rail. One shared ExoPlayer — pages only hold it while active.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun SpotlightPlayer(
    video: VideoEntity,
    similar: List<VideoEntity>,
    manager: PlayerManager,
    onClose: () -> Unit,
    onPick: (VideoEntity) -> Unit,
    onFav: (VideoEntity) -> Unit
) {
    val exo = remember(video.uri) { manager.playerFor(video.uri) }
    val haptics = LocalHapticFeedback.current
    var controls by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    var posMs by remember { mutableFloatStateOf(0f) }
    var durationMs by remember { mutableFloatStateOf(0f) }
    var scrubbing by remember { mutableFloatStateOf(-1f) }
    var speed by remember { mutableFloatStateOf(1f) }
    var loopMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(video.uri) {
        exo.play()
        manager.setSpeed(speed)
        manager.setLoopMode(loopMode)
    }
    DisposableEffect(video.uri) { onDispose { exo.pause() } }

    LaunchedEffect(tick, controls) {
        delay(3200)
        controls = false
    }
    DisposableEffect(exo) {
        val l = object : Player.Listener {
            override fun onRenderedFirstFrame() { ready = true }
        }
        exo.addListener(l)
        onDispose { exo.removeListener(l) }
    }
    LaunchedEffect(exo) {
        while (true) {
            delay(200)
            if (scrubbing < 0f) {
                posMs = exo.currentPosition.toFloat().coerceAtLeast(0f)
                durationMs = exo.duration.toFloat().coerceAtLeast(0f)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)
        .clickable { controls = !controls; tick++ }) {
        AndroidView(factory = { c ->
            PlayerView(c).also { pv -> pv.player = exo; pv.useController = false } },
            modifier = Modifier.fillMaxSize())

        // top bar
        AnimatedVisibility(visible = controls, enter = fadeIn(tween<Float>(Motion.fastMs)),
            exit = fadeOut(tween<Float>(Motion.fastMs)),
            modifier = Modifier.align(Alignment.TopStart)) {
            Row(Modifier.fillMaxWidth().padding(12.dp, 18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .size(38.dp)) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(video.about.ifEmpty { video.displayName }, color = Color.White,
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    Text("${video.category} • ${video.folderName}", color = Text2, fontSize = 11.sp)
                }
                IconButton(onClick = { onFav(video); haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                    modifier = Modifier.size(38.dp)) {
                    Icon(if (video.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        null, tint = if (video.isFavorite) Pink else Color.White,
                        modifier = Modifier.size(22.dp))
                }
            }
        }

        // centre transport
        AnimatedVisibility(visible = controls, enter = fadeIn(tween<Float>(Motion.fastMs)),
            exit = fadeOut(tween<Float>(Motion.fastMs)),
            modifier = Modifier.align(Alignment.Center)) {
            Box(Modifier.background(Color.Black.copy(alpha = 0.45f), CircleShape)
                .clickable {
                    if (exo.isPlaying) exo.pause() else exo.play()
                    tick++
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                .padding(14.dp)) {
                Icon(if (exo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }

        // bottom: rail + full transport
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()
            .background(Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)), startY = 160f))
            .padding(bottom = 14.dp)) {
            if (similar.isNotEmpty()) {
                Text("More like this", color = Color.White, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(6.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(similar, key = { it.id }) { s ->
                        VideoThumb(s, Modifier.size(104.dp, 142.dp)) { onPick(s) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            AnimatedVisibility(visible = controls, enter = fadeIn(tween<Float>(Motion.fastMs)),
                exit = fadeOut(tween<Float>(Motion.fastMs))) {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    Slider(
                        value = if (scrubbing >= 0f) scrubbing * durationMs else posMs,
                        onValueChange = { scrubbing = if (durationMs > 0) it / durationMs else 0f },
                        onValueChangeFinished = {
                            if (durationMs > 0) { exo.seekTo((scrubbing * durationMs).toLong()) }
                            scrubbing = -1f; tick++
                        },
                        valueRange = 0f..(if (durationMs > 0) durationMs else 1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White, activeTrackColor = Accent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f))
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(fmtDur(posMs.toLong()), color = Color.White, fontSize = 11.sp,
                            fontFamily = Numeral)
                        Spacer(Modifier.weight(1f))
                        // speed
                        Text("${speed}x", color = if (speed != 1f) Accent else Color.White,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                val i = SPEEDS.indexOf(speed)
                                speed = SPEEDS[(i + 1) % SPEEDS.size]
                                manager.setSpeed(speed); tick++
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }.padding(horizontal = 10.dp, vertical = 4.dp))
                        // loop
                        Icon(
                            if (loopMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne
                            else Icons.Default.Repeat,
                            null,
                            tint = if (loopMode == Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.6f)
                            else Accent,
                            modifier = Modifier.size(22.dp).clickable {
                                loopMode = when (loopMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                }
                                manager.setLoopMode(loopMode); tick++
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }.padding(6.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(fmtDur(durationMs.toLong()), color = Text2, fontSize = 11.sp,
                            fontFamily = Numeral)
                    }
                }
            }
        }
    }
}

