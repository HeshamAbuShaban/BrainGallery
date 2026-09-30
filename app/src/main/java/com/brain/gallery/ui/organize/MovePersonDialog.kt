package com.brain.gallery.ui.organize

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.brain.gallery.data.local.PersonEntity
import com.brain.gallery.ui.components.VideoThumb
import com.brain.gallery.ui.glass.Glass
import com.brain.gallery.ui.glass.glass
import com.brain.gallery.ui.theme.Accent
import com.brain.gallery.ui.theme.CardShape
import com.brain.gallery.ui.theme.Cyan
import com.brain.gallery.ui.theme.Green
import com.brain.gallery.ui.theme.Text1
import com.brain.gallery.ui.theme.Text2

/** Put a wrongly-clustered clip into the right person, or start a new one. */
@Composable
fun MovePersonDialog(
    persons: List<PersonEntity>,
    counts: Map<Int, Int>,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.glass(CardShape, Glass.raised(Accent))
            .padding(20.dp).verticalScroll(rememberScrollState())) {
            Text("Move to which person?", color = Text1, fontSize = 16.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Pick the right face, or start a new person.", color = Text2, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            persons.sortedByDescending { counts[it.id] ?: 0 }.forEach { p ->
                Row(Modifier.fillMaxWidth().clickable { onPick(p.id) }.padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box1(p, counts[p.id] ?: 0)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name.ifBlank { p.suggestedName.ifBlank { "Person ${p.id}" } },
                            color = Text1, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("${counts[p.id] ?: 0} videos" +
                            if (p.verified) " · you named this" else "",
                            color = Text2, fontSize = 11.5.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().clickable { onPick(-1) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonAdd, null, tint = Green, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Text("Someone new", color = Green, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Box1(p: PersonEntity, count: Int) {
    val initial = p.name.ifBlank { p.suggestedName.ifBlank { "?" } }.take(1).uppercase()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box2(initial)
        Spacer(Modifier.height(2.dp))
        Text("$count", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Box2(initial: String) {
    Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF26324A)),
        contentAlignment = Alignment.Center) {
        Text(initial, color = Text1, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
    }
}

