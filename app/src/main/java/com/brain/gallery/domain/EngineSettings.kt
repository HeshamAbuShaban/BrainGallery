package com.brain.gallery.domain

import com.brain.gallery.data.local.BrainDatabase
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

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
    val runBudgetSeconds: Int = 150
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
                    runBudgetSeconds = o.optInt("runBudgetSeconds", d.runBudgetSeconds)
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

    companion object { const val KEY = "engine.settings" }
}
