package com.brain.gallery.ui.organize

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brain.gallery.domain.organize.fmtSize
import com.brain.gallery.ui.glass.Glass
import com.brain.gallery.ui.glass.glass
import com.brain.gallery.ui.theme.Accent
import com.brain.gallery.ui.theme.Bg
import com.brain.gallery.ui.theme.CardShape
import com.brain.gallery.ui.theme.Cyan
import com.brain.gallery.ui.theme.Green
import com.brain.gallery.ui.theme.Text1
import com.brain.gallery.ui.theme.Text2
import com.brain.gallery.ui.theme.Yellow

/** Measure, don't guess: engine health, storage, and identity quality at a glance. */
@Composable
fun DiagnosticsScreen(vm: OrganizeViewModel, onBack: () -> Unit) {
    val d by vm.diagnostics.collectAsState()
    val stats by vm.stats.collectAsState()
    val exempt by vm.batteryExempt.collectAsState()
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { vm.refreshDiagnostics(); vm.refreshBattery() }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().padding(8.dp, 16.dp, 16.dp, 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack,
                modifier = Modifier.glass(CircleShape, Glass.onDark(Accent)).size(38.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Text1,
                    modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.size(12.dp))
            Text("Engine health", color = Text1, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
            Section("Library")
            Stat("Videos", "${stats.total}")
            Stat("Memories / clutter", "${stats.memories} / ${stats.clutter}")
            Stat("Understood", "${stats.understoodPct}% (${stats.pending} pending)")
            Stat("Database size", fmtSize(d.dbBytes))
            Spacer(Modifier.height(16.dp))
            Section("Last index run")
            Stat("Perceptual (L1a)", "${d.lastL1a} videos")
            Stat("Semantic (L1b)", "${d.lastL1b} videos")
            Stat("Total wall clock", "${d.lastMs} ms")
            Stat("Scan / L1a / L1b", "${d.tScan} / ${d.tL1a} / ${d.tL1b} ms")
            Spacer(Modifier.height(16.dp))
            Section("Identity")
            Stat("People", "${d.persons}")
            Stat("Face vectors", "${d.vectors}")
            Stat("Needs a decision", "${d.unassigned} unassigned",
                if (d.unassigned > 0) Yellow else Green)
            Stat("Split warnings", "${d.splitsFlagged}",
                if (d.splitsFlagged > 0) Yellow else Green)
            Stat("Clusters merged (last run)", "${d.merged}")
            Stat("Ambiguous adopted", "${d.adopted}")
            Stat("Unreadable files", "${d.unreadable}",
                if (d.unreadable > 0) Yellow else Green)
            Spacer(Modifier.height(16.dp))
            Section("Background reliability")
            if (exempt) {
                Stat("Battery optimisation", "exempt", Green)
            } else {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    .glass(CardShape, Glass.onDark(Accent))
                    .clickable {
                        runCatching { ctx.startActivity(vm.batteryRequestIntent()) }
                    }
                    .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Allow background indexing", color = Text1, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold)
                        Text("Your phone's clean-up app kills the indexer mid-run. " +
                            "Exempting it lets the library finish.", color = Text2, fontSize = 11.5.sp)
                    }
                    Text("FIX", color = Yellow, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Perceptual runs unbudgeted so duplicates and identity are complete on the " +
                    "first pass; only the semantic stage is budgeted and priority-ordered. " +
                    "Unassigned vectors are faces the brain could not confidently place — " +
                    "the app asks instead of guessing.",
                color = Text2, fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun Section(t: String) {
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
