package com.brain.gallery.ui.tune

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import com.brain.gallery.ui.theme.PALETTES
import com.brain.gallery.ui.theme.ChipFill
import com.brain.gallery.ui.theme.AccentSoft
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import com.brain.gallery.ui.glass.Glass
import com.brain.gallery.ui.glass.glass
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brain.gallery.domain.EngineSettings
import com.brain.gallery.domain.FEED_MOODS
import com.brain.gallery.domain.FeedTuning
import com.brain.gallery.domain.SettingsStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import com.brain.gallery.domain.ACCENT_CHOICES
import com.brain.gallery.ui.glass.GlassSegmented
import com.brain.gallery.ui.glass.GlassSlider
import com.brain.gallery.ui.glass.GlassSwitch
import com.brain.gallery.ui.glass.glassReserve
import com.brain.gallery.ui.theme.Accent
import com.brain.gallery.ui.theme.Bg
import com.brain.gallery.ui.theme.Hairline
import com.brain.gallery.ui.theme.Line
import com.brain.gallery.ui.theme.Cyan
import com.brain.gallery.ui.theme.Text1
import com.brain.gallery.ui.theme.Text2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns nothing but the settings store: the Tune tab is a window onto
 * [EngineSettings], and every change saves through it so the feed re-ranks
 * the moment a slider lets go.
 */
@HiltViewModel
class TuneViewModel @Inject constructor(private val store: SettingsStore) : ViewModel() {
    private val _settings = MutableStateFlow(EngineSettings())
    val settings: StateFlow<EngineSettings> = _settings.asStateFlow()

    init {
        // Room's observe emits the stored value on subscribe, so this both
        // loads and stays live (e.g. if BrainScreen saves elsewhere).
        viewModelScope.launch {
            store.observe().collect { _settings.value = it }
        }
    }

    fun update(s: EngineSettings) {
        _settings.value = s
        viewModelScope.launch { store.save(s) }
    }
}

/**
 * The Tune tab: feed presets and the raw weights behind them, plus the door
 * to the Brain control room. Every control here is read by the composer —
 * nothing on this screen is decorative.
 */
@Composable
fun TuneScreen(onOpenBrain: () -> Unit, vm: TuneViewModel = hiltViewModel()) {
    val cfg by vm.settings.collectAsState()

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(20.dp, 10.dp, 16.dp, 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Tune", color = Text1, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("Teach For You. Every slider is the real ranking.",
                    color = Text2, fontSize = 12.sp)
            }
            androidx.compose.material3.IconButton(onClick = onOpenBrain,
                modifier = Modifier.glass(CircleShape, Glass.pane(Accent)).size(38.dp)) {
                androidx.compose.material3.Icon(Icons.Default.BugReport, "Engine & diagnostics",
                    tint = Text1, modifier = Modifier.size(18.dp))
            }
        }

        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {

            // ---------------- Presets ----------------
            Head("For You")
            Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
                GlassSegmented(
                    labels = FEED_MOODS,
                    selectedIndex = FEED_MOODS.indexOf(cfg.mood).coerceAtLeast(0),
                    onSelect = { i ->
                        val pick = FEED_MOODS[i]
                        if (pick != "Custom") vm.update(
                            cfg.copy(mood = pick, tuning = FeedTuning.PRESETS[pick] ?: FeedTuning())
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    selectedColor = Accent
                )
                Text(
                    when (cfg.mood) {
                        "Open" -> "Balanced. Every signal at its natural weight."
                        "Nostalgic" -> "Buried gems and on-this-day resurfacings lead."
                        "Hype" -> "Music, sport, gaming and quick hits lead."
                        "Calm" -> "Faces and smiles, unhurried and gentle."
                        "Funny" -> "The ones where people are smiling."
                        else -> "Your own mix — the sliders below are the truth now."
                    },
                    color = Text2, fontSize = 11.5.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).clickable {
                vm.update(cfg.copy(mood = "Open", tuning = FeedTuning()))
            }, verticalAlignment = Alignment.CenterVertically) {
                Text("Reset to balanced", color = Cyan, fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("↺", color = Cyan, fontSize = 15.sp)
            }
            HorizontalDivider(color = Hairline)

            // ---------------- Weights ----------------
            Head("What matters")
            WeightSlider("Finish rate", cfg.tuning.completionWeight, cfg,
                help = "How much it counts that you watch clips to the end.") {
                vm.update(cfg.copy(mood = "Custom", tuning = cfg.tuning.copy(completionWeight = it)))
            }
            WeightSlider("Fresh finds", cfg.tuning.noveltyWeight, cfg,
                help = "How hard never-watched clips get pulled up.") {
                vm.update(cfg.copy(mood = "Custom", tuning = cfg.tuning.copy(noveltyWeight = it)))
            }
            WeightSlider("Favorites", cfg.tuning.favoritesWeight, cfg,
                help = "The ones you starred.") {
                vm.update(cfg.copy(mood = "Custom", tuning = cfg.tuning.copy(favoritesWeight = it)))
            }
            WeightSlider("People", cfg.tuning.peopleWeight, cfg,
                help = "Faces and smiles.") {
                vm.update(cfg.copy(mood = "Custom", tuning = cfg.tuning.copy(peopleWeight = it)))
            }
            WeightSlider("Memories", cfg.tuning.nostalgiaWeight, cfg,
                help = "Buried gems and on-this-day resurfacings.") {
                vm.update(cfg.copy(mood = "Custom", tuning = cfg.tuning.copy(nostalgiaWeight = it)))
            }
            WeightSlider("Time of day", cfg.tuning.timeOfDayWeight, cfg,
                help = "Shorts in the morning, longer clips at night.") {
                vm.update(cfg.copy(mood = "Custom", tuning = cfg.tuning.copy(timeOfDayWeight = it)))
            }
            WeightSlider("Energy", cfg.tuning.energyWeight, cfg,
                help = "Music, sport, gaming and other quick hits.") {
                vm.update(cfg.copy(mood = "Custom", tuning = cfg.tuning.copy(energyWeight = it)))
            }
            WeightSlider("Voices", cfg.tuning.voiceWeight, cfg,
                help = "Clips where the voice layer heard someone talking. " +
                    "Switch voice reading on in the Brain screen first.") {
                vm.update(cfg.copy(mood = "Custom", tuning = cfg.tuning.copy(voiceWeight = it)))
            }
            WeightSlider("Trust the brain", cfg.tuning.qualityWeight, cfg,
                help = "How hard clips the brain is still unsure about are held back. " +
                    "Zero ignores certainty completely.") {
                vm.update(cfg.copy(mood = "Custom", tuning = cfg.tuning.copy(qualityWeight = it)))
            }
            WeightSlider("Your taste", cfg.tuning.tasteWeight, cfg,
                help = "The brain quietly learns what you actually keep finishing — " +
                    "kinds of clips, people, places — and pulls those up. Learned on " +
                    "this phone only; zero means ignore it.") {
                vm.update(cfg.copy(mood = "Custom", tuning = cfg.tuning.copy(tasteWeight = it)))
            }

            // ---------------- Shape ----------------
            Head("The reel's shape")
            SliderRow("Reel length", "${cfg.tuning.feedLength} clips", 20f, 150f, 26,
                cfg.tuning.feedLength.toFloat(),
                help = "How many clips one composed reel holds.",
                preview = { "Shorter reels reshuffle sooner" }) {
                vm.update(cfg.copy(mood = "Custom", tuning = cfg.tuning.copy(feedLength = it.toInt())))
            }
            SliderRow("Replay demotion", "${(cfg.tuning.replayPenalty * 100).toInt()}%", 0f, 1f, 10,
                cfg.tuning.replayPenalty,
                help = "How hard clips you already watched this session sink.",
                preview = { if (it < 0.2f) "Replays rank almost as usual" else "Recently watched clips sink" }) {
                vm.update(cfg.copy(mood = "Custom", tuning = cfg.tuning.copy(replayPenalty = it)))
            }
            SliderRow("Variety", "${(cfg.tuning.variety * 100).toInt()}%", 0f, 1f, 10,
                cfg.tuning.variety,
                help = "How strongly the reel avoids the same category twice in a row.",
                preview = { if (it < 0.2f) "Anything can follow anything" else "Categories take turns" }) {
                vm.update(cfg.copy(mood = "Custom", tuning = cfg.tuning.copy(variety = it)))
            }
            SliderRow("Surprise me", "${(cfg.tuning.exploreChance * 100).toInt()}%", 0f, 50f, 10,
                cfg.tuning.exploreChance,
                help = "Chance an unlikely clip still makes the reel.",
                preview = { if (it < 0.05f) "Strictly the strongest clips" else "Some wildcards slip in" }) {
                vm.update(cfg.copy(mood = "Custom", tuning = cfg.tuning.copy(exploreChance = it)))
            }
            SliderRow("Clutter cut", "%.2f".format(cfg.tuning.junkCut), 0.50f, 0.95f, 9,
                cfg.tuning.junkCut,
                help = "Clips this junky never enter the reel.",
                preview = { "Nothing is deleted — clutter just waits in Organize" }) {
                vm.update(cfg.copy(mood = "Custom", tuning = cfg.tuning.copy(junkCut = it)))
            }
            Head("On the cards")
            SwitchRow("Why this ranked",
                "The small accent line on each reel card, saying which signals put it there.",
                cfg.showWhy) {
                vm.update(cfg.copy(showWhy = it))
            }

            // ---------------- Appearance ----------------
            Head("Appearance")
            Text("Theme", color = Text1, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
            // Six curated rooms. Each chip wears its own palette's colors:
            // you pick the room by seeing it, not by reading its name.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PALETTES.forEach { pal ->
                    val selected = cfg.theme == pal.id
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) AccentSoft else ChipFill)
                            .then(if (selected) Modifier.border(1.dp, Accent, RoundedCornerShape(50)) else Modifier)
                            .clickable { vm.update(cfg.copy(theme = pal.id)) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape)
                            .background(pal.bg).border(1.dp, pal.line, CircleShape))
                        Spacer(Modifier.width(4.dp))
                        Box(Modifier.size(10.dp).clip(CircleShape).background(pal.pink))
                        Spacer(Modifier.width(4.dp))
                        Box(Modifier.size(10.dp).clip(CircleShape).background(pal.cyan))
                        Spacer(Modifier.width(7.dp))
                        Text(pal.label, color = if (selected) Text1 else Text2,
                            fontSize = 12.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            AccentRow(cfg.accentArgb) { vm.update(cfg.copy(accentArgb = it)) }
            SwitchRow("The animated shelf",
                "The leaning card strip at the top of Organize. Folded away, it leaves a one-line door.",
                !cfg.shelfHidden) {
                vm.update(cfg.copy(shelfHidden = !it))
            }

            Spacer(Modifier.height(glassReserve() + 16.dp))
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
private fun ActionRow(label: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Text1, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = Text2, fontSize = 11.5.sp)
        }
        Text("→", color = Accent, fontSize = 16.sp)
    }
}

/** A weight slider. The value reads as a multiple: 1.0× is balanced. */
@Composable
private fun WeightSlider(
    title: String,
    value: Float,
    cfg: EngineSettings,
    help: String,
    onChange: (Float) -> Unit
) {
    val note = when {
        value < 0.15f -> "turned off — this signal no longer moves the reel"
        value < 0.85f -> "matters less than usual"
        value <= 1.15f -> "balanced"
        value <= 1.6f -> "matters more than usual"
        else -> "dominates the reel"
    }
    SliderRow(title, "%.1f×".format(value), 0f, 2f, 20, value,
        help = help, preview = { note }, onChange = onChange)
}

/**
 * A slider that explains itself — same contract as the Brain screen's:
 * the preview follows the thumb while dragging, the committed value only
 * changes when the gesture ends.
 */
@Composable
private fun SliderRow(
    title: String, value: String, min: Float, max: Float, steps: Int, current: Float,
    help: String = "",
    preview: ((Float) -> String)? = null,
    onChange: (Float) -> Unit
) {
    var dragging by remember { mutableFloatStateOf(current) }
    var isDragging by remember { mutableStateOf(false) }
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
private fun AccentRow(current: Long, onPick: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp)) {
        Text("Accent color", color = Text1, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text("Tints the glass and the highlights across the whole app.",
            color = Text2, fontSize = 11.5.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            ACCENT_CHOICES.forEach { argb ->
                val sel = argb == current
                Box(Modifier.size(36.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .then(if (sel) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                    .clickable { onPick(argb) },
                    contentAlignment = Alignment.Center) {
                    if (sel) Icon(Icons.Default.Check, null, tint = Color.White,
                        modifier = Modifier.size(16.dp))
                }
            }
        }
    }
    HorizontalDivider(color = Hairline)
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
