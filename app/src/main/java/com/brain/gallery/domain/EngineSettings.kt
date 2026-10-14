package com.brain.gallery.domain

import com.brain.gallery.data.local.BrainDatabase
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the For You feed is into lately. Taught by the viewer, not guessed:
 * "Open" ranks exactly as before, the rest tilt the same weights.
 */
val FEED_MOODS = listOf("Open", "Nostalgic", "Hype", "Calm", "Funny")

/** A few accents that survive white text and video behind glass. */
val ACCENT_CHOICES = listOf(
    0xFF8B5CF6L, 0xFFEC4899L, 0xFF06B6D4L,
    0xFF10B981L, 0xFFF59E0BL, 0xFFF87171L
)

/**
 * The knobs the brain actually respects. Stored as one JSON blob in app_kv so
 * they travel in the memory bundle and survive a wipe.
 */
data class EngineSettings(
    val identityEnabled: Boolean = true,
    val semanticEnabled: Boolean = true,
    val duplicatesEnabled: Boolean = true,
    val semanticBudget: Int = 250,
    val matchSim: Float = 0.42f,
    val mergeSim: Float = 0.45f,
    val splitSensitivity: Float = 0.25f,
    val junkSensitivity: Float = 0.5f,
    val runBudgetSeconds: Int = 150,
    /**
     * Voice and delivery, from the audio track. Off by default: it only runs on
     * clips that look like speech, so it costs a little, never a lot.
     */
    val prosodyEnabled: Boolean = false,
    /** Monthly mood the viewer teaches the feed. "Open" changes nothing. */
    val mood: String = "Open",
    /** Accent color for glass and highlights, as ARGB. */
    val accentArgb: Long = 0xFF8B5CF6L
) {
    fun toJson(): String = JSONObject().apply {
        put("identityEnabled", identityEnabled)
        put("semanticEnabled", semanticEnabled)
        put("duplicatesEnabled", duplicatesEnabled)
        put("semanticBudget", semanticBudget)
        put("matchSim", matchSim.toDouble())
        put("mergeSim", mergeSim.toDouble())
        put("splitSensitivity", splitSensitivity.toDouble())
        put("junkSensitivity", junkSensitivity.toDouble())
        put("runBudgetSeconds", runBudgetSeconds)
        put("prosodyEnabled", prosodyEnabled)
        put("mood", mood)
        put("accentArgb", accentArgb)
    }.toString()

    companion object {
        fun fromJson(s: String?): EngineSettings {
            if (s.isNullOrBlank()) return EngineSettings()
            return runCatching {
                val o = JSONObject(s)
                val d = EngineSettings()
                EngineSettings(
                    identityEnabled = o.optBoolean("identityEnabled", d.identityEnabled),
                    semanticEnabled = o.optBoolean("semanticEnabled", d.semanticEnabled),
                    duplicatesEnabled = o.optBoolean("duplicatesEnabled", d.duplicatesEnabled),
                    semanticBudget = o.optInt("semanticBudget", d.semanticBudget),
                    matchSim = o.optDouble("matchSim", d.matchSim.toDouble()).toFloat(),
                    mergeSim = o.optDouble("mergeSim", d.mergeSim.toDouble()).toFloat(),
                    splitSensitivity = o.optDouble("splitSensitivity", d.splitSensitivity.toDouble()).toFloat(),
                    junkSensitivity = o.optDouble("junkSensitivity", d.junkSensitivity.toDouble()).toFloat(),
                    runBudgetSeconds = o.optInt("runBudgetSeconds", d.runBudgetSeconds),
                    prosodyEnabled = o.optBoolean("prosodyEnabled", d.prosodyEnabled),
                    mood = o.optString("mood", d.mood).takeIf { it in FEED_MOODS } ?: d.mood,
                    accentArgb = o.optLong("accentArgb", d.accentArgb)
                )
            }.getOrDefault(EngineSettings())
        }
    }
}

@Singleton
class SettingsStore @Inject constructor(private val db: BrainDatabase) {
    suspend fun load(): EngineSettings =
        EngineSettings.fromJson(db.supportDao().get(KEY))

    suspend fun save(s: EngineSettings) { db.supportDao().put(KEY, s.toJson()) }

    /** Emits on every save, so the feed re-ranks the moment a knob moves. */
    fun observe(): kotlinx.coroutines.flow.Flow<EngineSettings> =
        db.supportDao().observeValue(KEY).map { EngineSettings.fromJson(it) }
            .distinctUntilChanged()

    companion object { const val KEY = "engine.settings" }
}
