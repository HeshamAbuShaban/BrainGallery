package com.brain.gallery.ui.organize

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brain.gallery.domain.EngineSettings
import com.brain.gallery.domain.organize.fmtSize
import com.brain.gallery.ui.theme.Accent
import com.brain.gallery.ui.theme.Bg
import com.brain.gallery.ui.theme.CardShape
import com.brain.gallery.ui.theme.Cyan
import com.brain.gallery.ui.theme.Danger
import com.brain.gallery.ui.theme.Green
import com.brain.gallery.ui.theme.Text1
import com.brain.gallery.ui.theme.Text2
import com.brain.gallery.ui.theme.Yellow

/**
 * The brain's control room. Every control here is read by the indexer — nothing
 * on this screen is decorative.
 */
@Composable
fun BrainScreen(vm: OrganizeViewModel, onBack: () -> Unit) {
    val d by vm.diagnostics.collectAsState()
    val stats by vm.stats.collectAsState()
    val cfg by vm.settings.collectAsState()
    val persons by vm.persons.collectAsState()
    val exempt by vm.batteryExempt.collectAsState()
    val ctx = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshDiagnostics(); vm.refreshBattery() }

    // Fragmentation repair offers: clusters sharing one name.
    val collisions = persons.filter { it.name.isBlank() && it.suggestedName.isNotBlank() }
        .groupingBy { it.suggestedName }.eachCount()
        .filterValues { it > 1 }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().padding(8.dp, 16.dp, 16.dp, 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack,
                modifier = Modifier.background(Color(0xFF1D2534), CircleShape).size(38.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Text1,
                    modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Brain", color = Text1, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("Everything runs on this device", color = Text2, fontSize = 12.sp)
            }
        }

        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {

            // ---------------- Engine ----------------
            Head("Engine")
            SwitchRow("On-device identity",
                "Detect and group people by face embeddings", cfg.identityEnabled) {
                vm.updateSettings(cfg.copy(identityEnabled = it))
            }
            SwitchRow("Semantic labels (ML Kit)",
                "Slower, fills in what each clip is about", cfg.semanticEnabled) {
                vm.updateSettings(cfg.copy(semanticEnabled = it))
            }
            SwitchRow("Duplicate detection",
                "Find and rank redundant near-identical clips", cfg.duplicatesEnabled) {
                vm.updateSettings(cfg.copy(duplicatesEnabled = it))
            }
            SliderRow("Semantic budget per run", cfg.semanticBudget.toString(), 25f, 600f, 24,
                cfg.semanticBudget.toFloat()) { vm.updateSettings(cfg.copy(semanticBudget = it.toInt())) }
            SliderRow("Run time budget", "${cfg.runBudgetSeconds}s", 30f, 420f, 14,
                cfg.runBudgetSeconds.toFloat()) { vm.updateSettings(cfg.copy(runBudgetSeconds = it.toInt())) }
            SliderRow("Match strictness (similarity)", "%.2f".format(cfg.matchSim), 0.30f, 0.70f, 40,
                cfg.matchSim) { vm.updateSettings(cfg.copy(matchSim = it)) }
            SliderRow("Cluster merge threshold", "%.2f".format(cfg.mergeSim), 0.35f, 0.75f, 40,
                cfg.mergeSim) { vm.updateSettings(cfg.copy(mergeSim = it)) }
            SliderRow("Split sensitivity", "%.2f".format(cfg.splitSensitivity), 0.10f, 0.50f, 40,
                cfg.splitSensitivity) { vm.updateSettings(cfg.copy(splitSensitivity = it)) }
            SliderRow("Clutter threshold", "%.2f".format(cfg.junkSensitivity), 0.20f, 0.90f, 14,
                cfg.junkSensitivity) { vm.updateSettings(cfg.copy(junkSensitivity = it)) }
            Text("Lower match strictness pulls more clips into a person; a lower merge " +
                "threshold repairs fragmentation. Clutter threshold decides what counts as junk.",
                color = Text2, fontSize = 11.5.sp, modifier = Modifier.padding(top = 6.dp))

            // ---------------- Index ----------------
            Head("Index")
            Stat("Videos", "${stats.total}")
            Stat("Memories / clutter", "${stats.memories} / ${stats.clutter}")
            Stat("Understood", "${stats.understoodPct}% (${stats.pending} pending)")
            Stat("Last run", "${d.lastL1a} perceptual · ${d.lastL1b} enriched · ${d.lastMs} ms")
            Stat("Clusters merged / adopted", "${d.merged} / ${d.adopted}")
            Stat("Unreadable files", "${d.unreadable}")
            ActionRow("Re-index now", Accent) { vm.rescan() }

            // ---------------- Identity ----------------
            Head("People")
            Stat("People found", "${d.persons}")
            Stat("Face vectors", "${d.vectors}")
            Stat("Needs a decision", "${d.unassigned} unassigned",
                if (d.unassigned > 0) Yellow else Green)
            Stat("Split warnings", "${d.splitsFlagged}", if (d.splitsFlagged > 0) Yellow else Green)
            if (collisions.isNotEmpty()) {
                Card(Yellow) {
                    Text("Looks like one person, split up", color = Text1, fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${collisions.size} name(s) were claimed by more than one cluster. " +
                            "That usually means the same face is in two groups.",
                        color = Text2, fontSize = 12.sp)
                    collisions.keys.take(4).forEach { name ->
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("\"$name\" · ${collisions[name]} clusters", color = Text1,
                                fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                            Text("MERGE", color = Green, fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.clickable { vm.mergeAllNamed(name) }
                                    .padding(6.dp))
                        }
                    }
                }
            }
            persons.filter { it.verified && it.name.isNotBlank() }.take(12).forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, color = Text1, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("renamed by you", color = Text2, fontSize = 11.sp)
                }
            }

            // ---------------- Storage ----------------
            Head("Storage")
            Stat("Database", fmtSize(d.dbBytes))
            Stat("Library on device", "${stats.total} videos")
            ActionRow("Export memory bundle", Accent) { vm.exportMemory() }
            Text("Saves names, favourites and watch history to a file you control. " +
                "If the app is wiped, import it back and the brain rebuilds itself.",
                color = Text2, fontSize = 11.5.sp, modifier = Modifier.padding(top = 4.dp))

            // ---------------- Reliability ----------------
            Head("Background")
            if (exempt) {
                Stat("Battery optimisation", "exempt", Green)
            } else {
                Card(Yellow) {
                    Text("Your phone's clean-up app kills the indexer", color = Text1,
                        fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("It stopped mid-index on this device. Exempting BrainGallery lets " +
                        "the library finish.", color = Text2, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("ALLOW", color = Yellow, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.clickable {
                            runCatching { ctx.startActivity(vm.batteryRequestIntent()) }
                        }.padding(4.dp))
                }
            }

            // ---------------- Danger ----------------
            Head("Reset")
            ActionRow("Rebuild everything the brain derived", Yellow) { confirmReset = true }
            Text("Clears tags, hashes and identity, keeps favourites and history, then re-indexes.",
                color = Text2, fontSize = 11.5.sp, modifier = Modifier.padding(top = 4.dp))

            // ---------------- About ----------------
            Head("About")
            Stat("Bundled models", "face detection · image labeling · MobileFaceNet")
            Stat("Database", "Room, on this device only")
            Stat("Network calls", "none")
            Text("Every model runs locally. Nothing about your library leaves the phone.",
                color = Text2, fontSize = 11.5.sp)

            Spacer(Modifier.height(30.dp))
        }
    }

    if (confirmReset) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { confirmReset = false }) {
            Column(Modifier.clip(CardShape).background(Color(0xFF151B26)).padding(20.dp)) {
                Text("Rebuild the index?", color = Text1, fontSize = 16.sp,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Tags, hashes, categories and identity will be cleared and recomputed. " +
                    "Favourites, names and watch history are kept.", color = Text2, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text("Cancel", color = Text2, fontSize = 14.sp,
                        modifier = Modifier.clickable { confirmReset = false }.padding(10.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Rebuild", color = Green, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            confirmReset = false; vm.resetDerived()
                        }.padding(10.dp))
                }
            }
        }
    }
}

@Composable
private fun Head(t: String) {
    Spacer(Modifier.height(22.dp))
    Text(t.uppercase(), color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun Stat(k: String, v: String, vc: Color = Text1) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(k, color = Text2, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(v, color = vc, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Card(accent: Color, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)
        .background(accent.copy(alpha = 0.10f), CardShape).padding(14.dp)) { content() }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Text1, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = Text2, fontSize = 11.5.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = Accent,
            uncheckedThumbColor = Text2, uncheckedTrackColor = Color(0xFF2A3446)
        ))
    }
    HorizontalDivider(color = Color(0xFF1A2231))
}

@Composable
private fun SliderRow(
    title: String, value: String, min: Float, max: Float, steps: Int, current: Float,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, color = Text1, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(value, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = current, onValueChange = onChange, valueRange = min..max,
            steps = (steps - 1).coerceAtLeast(0),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Color(0xFF2A3446)
            ))
    }
}

@Composable
private fun ActionRow(label: String, tint: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = tint, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f))
        Text("→", color = tint, fontSize = 15.sp)
    }
}
