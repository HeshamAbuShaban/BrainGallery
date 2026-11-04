package com.brain.gallery.ui.organize

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brain.gallery.domain.organize.fmtSize
import com.brain.gallery.ui.glass.Glass
import com.brain.gallery.ui.glass.GlassSlider
import com.brain.gallery.ui.glass.GlassSwitch
import com.brain.gallery.ui.glass.glass
import com.brain.gallery.ui.theme.Accent
import com.brain.gallery.ui.theme.Bg
import com.brain.gallery.ui.theme.Hairline
import com.brain.gallery.ui.theme.Line
import com.brain.gallery.ui.theme.CardShape
import com.brain.gallery.ui.theme.Cyan
import com.brain.gallery.ui.theme.Green
import com.brain.gallery.ui.theme.Text1
import com.brain.gallery.ui.theme.Text2
import com.brain.gallery.ui.theme.Yellow

/**
 * The brain's rooms. One screen used to hold every switch, slider, stat and
 * repair the brain offers — a wall nobody reads. Now: this hub keeps what is
 * actionable (the switches and what needs a decision), and each room owns
 * its depth. Every control is still read by the indexer — nothing here is
 * decorative.
 */
@Composable
fun BrainScreen(
    vm: OrganizeViewModel,
    onBack: () -> Unit,
    onOpenLearning: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onOpenStorage: () -> Unit = {}
) {
    val d by vm.diagnostics.collectAsState()
    val stats by vm.stats.collectAsState()
    val cfg by vm.settings.collectAsState()
    val persons by vm.persons.collectAsState()
    val photoPending by vm.photoPending.collectAsState()

    BackHandler(enabled = true) { onBack() }
    LaunchedEffect(Unit) { vm.refreshDiagnostics(); vm.refreshBattery() }

    // Fragmentation repair offers: clusters sharing one name.
    val collisions = persons.filter { it.name.isBlank() && it.suggestedName.isNotBlank() }
        .groupingBy { it.suggestedName }.eachCount()
        .filterValues { it > 1 }

    BrainScaffold(title = "Brain", subtitle = "Everything runs on this device", onBack = onBack) {

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
        SwitchRow("Voice and delivery",
            "Reads the audio of clips that look like someone talking, and notes the " +
                "pitch, pace and emphasis. Off by default; it only ever looks at clips " +
                "with a person in them.",
            cfg.prosodyEnabled) {
            vm.updateSettings(cfg.copy(prosodyEnabled = it))
        }

        // ---------------- Repairs ----------------
        val unassignedCount = d.unassigned
        if (unassignedCount > 0 || collisions.isNotEmpty() || d.splitsFlagged > 0) {
            Head("Needs your input")
            Text("The brain is not certain about these. Answering once teaches it.",
                color = Text2, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            if (unassignedCount > 0) {
                Card(Cyan) {
                    Text("$unassignedCount faces couldn't be placed",
                        color = Text1, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("They looked like two people at once, so the brain refused to " +
                        "guess. Open a person group and long-press the clip to move it, " +
                        "or use \"Move to…\" from the clip menu.",
                        color = Text2, fontSize = 12.sp)
                }
            }
            if (collisions.isNotEmpty()) {
                Card(Yellow) {
                    Text("One person, split into several groups", color = Text1,
                        fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    collisions.keys.take(5).forEach { name ->
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
            if (d.splitsFlagged > 0) {
                Card(Yellow) {
                    Text("${d.splitsFlagged} group(s) may be two different people",
                        color = Text1, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Open the group: tap the wrong clip to split it out, or dismiss.",
                        color = Text2, fontSize = 12.sp)
                }
            }
        }

        // ---------------- Snapshot ----------------
        Head("At a glance")
        Stat("Videos", fmtCount(stats.total))
        Stat("Understood", "${stats.understoodPct}% (${stats.pending} pending)")
        Stat("Photos waiting", "$photoPending", if (photoPending > 0) Yellow else Green)

        // ---------------- Rooms ----------------
        Head("Rooms")
        ActionRow("Learning & thresholds",
            "Budgets, similarity, and what counts as clutter.", Accent) { onOpenLearning() }
        ActionRow("Diagnostics & people",
            "Timings, clusters, the voice pass, re-index.", Accent) { onOpenDiagnostics() }
        ActionRow("Storage, backup & reset",
            "The memory bundle, battery, and the rebuild button.", Accent) { onOpenStorage() }

        // ---------------- About ----------------
        Head("About")
        Stat("Bundled models", "face detection · labeling · MobileFaceNet")
        Stat("Network calls", "none")
        Text("Every model runs locally. Nothing about your library leaves the phone.",
            color = Text2, fontSize = 11.5.sp)

        Spacer(Modifier.height(30.dp))
    }
}

/** How the brain learns: the six thresholds, each with its live consequence. */
@Composable
fun BrainLearningScreen(vm: OrganizeViewModel, onBack: () -> Unit) {
    val cfg by vm.settings.collectAsState()
    BackHandler(enabled = true) { onBack() }

    BrainScaffold(title = "Learning", subtitle = "How the brain reads your library", onBack = onBack) {
        Head("Budgets")
        SliderRow("Semantic budget per run", cfg.semanticBudget.toString(), 25f, 600f, 24,
            cfg.semanticBudget.toFloat(),
            help = "How many clips the slow labeler may look at in one pass.",
            preview = { "${vm.previewSemanticPending()} clips still to learn" }) {
            vm.updateSettings(cfg.copy(semanticBudget = it.toInt()))
        }
        SliderRow("Run time budget", "${cfg.runBudgetSeconds}s", 30f, 420f, 14,
            cfg.runBudgetSeconds.toFloat(),
            help = "How long a pass may run before it yields to the battery.",
            preview = { "Raising this finishes sooner but uses more battery" }) {
            vm.updateSettings(cfg.copy(runBudgetSeconds = it.toInt()))
        }
        Head("Identity thresholds")
        SliderRow("Match strictness (similarity)", "%.2f".format(cfg.matchSim), 0.30f, 0.70f, 40,
            cfg.matchSim,
            help = "How alike two faces must be to count as the same person.",
            preview = {
                val n = vm.previewPeopleAt(it)
                "$n clusters hold at least this many clips"
            }) {
            vm.updateSettings(cfg.copy(matchSim = it))
        }
        SliderRow("Cluster merge threshold", "%.2f".format(cfg.mergeSim), 0.35f, 0.75f, 40,
            cfg.mergeSim,
            help = "How alike two existing clusters must be before they become one. Use this to undo an over-merge.",
            preview = { "${vm.previewUnplaced()} faces still waiting to be placed" }) {
            vm.updateSettings(cfg.copy(mergeSim = it))
        }
        SliderRow("Split sensitivity", "%.2f".format(cfg.splitSensitivity), 0.10f, 0.50f, 40,
            cfg.splitSensitivity,
            help = "How different two faces inside one person have to look before the brain suspects two people.",
            preview = { "A warning is only a suggestion; merging is always your call" }) {
            vm.updateSettings(cfg.copy(splitSensitivity = it))
        }
        Head("Clutter")
        SliderRow("Clutter threshold", "%.2f".format(cfg.junkSensitivity), 0.20f, 0.90f, 14,
            cfg.junkSensitivity,
            help = "How sure a clip has to look like junk before it leaves your memories.",
            preview = { val (m, j) = vm.previewJunkSplit(it)
                "Right now that is $m memories and $j clutter" }) {
            vm.updateSettings(cfg.copy(junkSensitivity = it))
        }
        Text("Nothing here deletes anything. Clutter only means the brain sets a clip aside; " +
            "the file stays on your phone until you delete it yourself.",
            color = Text2, fontSize = 11.5.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(30.dp))
    }
}

/** What the brain did, and how long it took. */
@Composable
fun BrainDiagnosticsScreen(vm: OrganizeViewModel, onBack: () -> Unit) {
    val d by vm.diagnostics.collectAsState()
    val stats by vm.stats.collectAsState()
    val cfg by vm.settings.collectAsState()
    val persons by vm.persons.collectAsState()
    BackHandler(enabled = true) { onBack() }
    LaunchedEffect(Unit) { vm.refreshDiagnostics() }

    BrainScaffold(title = "Diagnostics", subtitle = "What the brain did, and when", onBack = onBack) {
        Head("Last run")
        Stat("Videos", fmtCount(stats.total))
        Stat("Perceptual pass", "${d.lastL1a} clips")
        Stat("Semantic pass", "${d.lastL1b} clips · ${d.lastMs} ms")
        Stat("Clusters merged / adopted", "${d.merged} / ${d.adopted}")
        Stat("Unreadable files", "${d.unreadable}")
        Stat("Model timeouts", "${d.stalled}", if (d.stalled > 0) Yellow else Green)
        ActionRow("Re-index now", "Scan MediaStore, merge what changed.", Accent) { vm.rescan() }

        Head("People")
        Stat("People found", "${d.persons}")
        Stat("Face vectors", "${d.vectors}")
        Stat("Needs a decision", "${d.unassigned} unassigned",
            if (d.unassigned > 0) Yellow else Green)
        Stat("Split warnings", "${d.splitsFlagged}", if (d.splitsFlagged > 0) Yellow else Green)
        persons.filter { it.verified && it.name.isNotBlank() }.take(12).forEach { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, color = Text1, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("renamed by you", color = Text2, fontSize = 11.sp)
            }
        }

        if (cfg.prosodyEnabled) {
            Head("Voice reading")
            Stat("Waiting to be heard", "${vm.prosodyPending()}")
            Stat("Already heard", "${vm.prosodyLearned()}")
            ActionRow("Re-learn the ones it got wrong",
                "Only clips whose delivery read came out unclear.", Cyan) { vm.relearnUnclear() }
            ActionRow("Re-learn every clip's voice",
                "Throws away all delivery tags and listens again.", Text2) { vm.relearnAllVoice() }
        } else {
            Head("Voice reading")
            Text("Off. Turn on \"Voice and delivery\" on the Brain screen and the audio " +
                "pass will queue here.",
                color = Text2, fontSize = 11.5.sp)
        }

        Spacer(Modifier.height(30.dp))
    }
}

/** Where the brain's memory lives, how it travels, and the one dangerous button. */
@Composable
fun BrainStorageScreen(vm: OrganizeViewModel, onBack: () -> Unit) {
    val d by vm.diagnostics.collectAsState()
    val stats by vm.stats.collectAsState()
    val exempt by vm.batteryExempt.collectAsState()
    val exportMsg by vm.exportMsg.collectAsState()
    val importMsg by vm.importMsg.collectAsState()
    val ctx = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    val importPick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importMemory(it) } }

    BackHandler(enabled = true) { onBack() }
    LaunchedEffect(Unit) { vm.refreshBattery() }

    BrainScaffold(title = "Storage", subtitle = "Backup, battery, and the rebuild", onBack = onBack) {
        Head("On this device")
        Stat("Database", fmtSize(d.dbBytes))
        Stat("Library", fmtCount(stats.total) + " videos")

        Head("Memory bundle")
        ActionRow("Export memory bundle",
            "One file: names, favourites, history, settings.", Accent) { vm.exportMemory() }
        ActionRow("Import memory bundle",
            "Restore on this device — or after a wipe.", Green) {
            importPick.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
        val portMsg = exportMsg ?: importMsg
        if (portMsg != null) {
            Text(portMsg, color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp))
            LaunchedEffect(portMsg) {
                kotlinx.coroutines.delay(6000)
                vm.consumeExportMsg(); vm.consumeImportMsg()
            }
        }
        Text("Saves names, favourites, watch history, settings and group names to a " +
            "file you control. If the app is wiped, import it back and the brain " +
            "rebuilds itself.",
            color = Text2, fontSize = 11.5.sp, modifier = Modifier.padding(top = 4.dp))

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

        Head("Reset")
        ActionRow("Rebuild everything the brain derived",
            "Tags, hashes and identity are recomputed; what you taught is kept.",
            Yellow) { confirmReset = true }
        Text("Clears tags, hashes and identity, keeps favourites and history, then re-indexes.",
            color = Text2, fontSize = 11.5.sp, modifier = Modifier.padding(top = 4.dp))

        Spacer(Modifier.height(30.dp))
    }

    if (confirmReset) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { confirmReset = false }) {
            Column(Modifier.glass(CardShape, Glass.raised(Accent)).padding(20.dp)) {
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

/** The shared brain-room chrome: header, back, one scrolling column. */
@Composable
private fun BrainScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().padding(8.dp, 16.dp, 16.dp, 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack,
                modifier = Modifier.glass(CircleShape, Glass.pane(Accent)).size(38.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Text1,
                    modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Text1, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = Text2, fontSize = 12.sp)
            }
        }
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            content()
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
        .glass(CardShape, Glass.pane(accent)).padding(14.dp)) { content() }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Text1, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = Text2, fontSize = 11.5.sp)
        }
        GlassSwitch(checked = checked, onCheckedChange = onChange)
    }
    HorizontalDivider(color = Hairline)
}

/**
 * A slider that explains itself.
 *
 * The name alone does not tell you what a number like 0.45 does to your library,
 * so each one carries a plain sentence and a live consequence. The preview is
 * computed for the value under the thumb, which means the reader can drag and
 * watch the effect before letting go, instead of committing and re-indexing to
 * find out.
 */
@Composable
private fun SliderRow(
    title: String, value: String, min: Float, max: Float, steps: Int, current: Float,
    help: String = "",
    preview: ((Float) -> String)? = null,
    onChange: (Float) -> Unit
) {
    // Local drag state: the preview follows the thumb immediately, while the
    // committed value only updates when the gesture ends.
    var dragging by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(current) }
    var isDragging by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val shown = if (isDragging) dragging else current

    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, color = Text1, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(value, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        if (help.isNotBlank()) {
            Text(help, color = Text2, fontSize = 11.5.sp,
                modifier = Modifier.padding(top = 2.dp, end = 4.dp))
        }
        GlassSlider(
            value = shown,
            onValueChange = { dragging = it; isDragging = true },
            onValueChangeFinished = { isDragging = false; onChange(dragging) },
            valueRange = min..max,
            steps = (steps - 1).coerceAtLeast(0))
        val note = preview?.invoke(shown)
        if (!note.isNullOrBlank()) {
            Row(Modifier.fillMaxWidth().padding(top = 1.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(note, color = Cyan, fontSize = 11.5.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f))
                if (isDragging) {
                    Text("let go to apply", color = Text2, fontSize = 10.5.sp)
                }
            }
        }
    }
}

@Composable
private fun ActionRow(label: String, sub: String, tint: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = tint, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = Text2, fontSize = 11.5.sp)
        }
        Text("→", color = tint, fontSize = 15.sp)
    }
}
