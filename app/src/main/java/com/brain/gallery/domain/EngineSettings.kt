package com.brain.gallery.domain

import com.brain.gallery.data.local.BrainDatabase
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Named tilts for For You. Each one is just a hand of weights, nothing hidden. */
val FEED_PRESETS = listOf("Open", "Nostalgic", "Hype", "Calm", "Funny")

/** Presets plus the state you land in after moving any slider by hand. */
val FEED_MOODS = FEED_PRESETS + "Custom"

/** A few accents that survive white text and video behind glass. */
val ACCENT_CHOICES = listOf(
    0xFF8B5CF6L, 0xFFEC4899L, 0xFF06B6D4L,
    0xFF10B981L, 0xFFF59E0BL, 0xFFF87171L
)

/**
 * The viewer's hand on the ranking.
 *
 * Every contribution in [com.brain.gallery.domain.engine.FeedComposer] is a
 * plain number times one of these weights, so 1.0 everywhere means "balanced"
 * and any slider reads as "how much does this signal matter to me". There are
 * no hidden constants left in the ranking: if the reel is wrong, some knob
 * here is the reason, and the feed card itself says which ones spoke.
 */
data class FeedTuning(
    /** How much finishing a clip says "more like this". */
    val completionWeight: Float = 1f,
    /** Pulling never-watched clips up the reel. */
    val noveltyWeight: Float = 1f,
    val favoritesWeight: Float = 1f,
    /** Faces and smiles. */
    val peopleWeight: Float = 1f,
    /** Buried gems and on-this-day resurfacings. */
    val nostalgiaWeight: Float = 1f,
    /** Morning shorts, evening longs. */
    val timeOfDayWeight: Float = 1f,
    /** Music, sport, gaming and other quick hits. */
    val energyWeight: Float = 1f,
    /** Clips where someone is talking, from the voice layer's audio read. */
    val voiceWeight: Float = 1f,
    /** How hard clips the brain is unsure about are held back. 0 ignores confidence. */
    val qualityWeight: Float = 1f,
    /** 0 = replays rank as usual, 1 = anything seen this session sinks hard. */
    val replayPenalty: Float = 0.65f,
    /** Clips whose junkScore reaches this never enter the reel at all. */
    val junkCut: Float = 0.85f,
    /** How many clips one composed reel holds. */
    val feedLength: Int = 60,
    /** Similarly-scored clips are shuffled together in bands of this size. */
    val bandSize: Int = 12,
    /** 0 = categories may sit side by side, 1 = never the same category twice in a row. */
    val variety: Float = 1f,
    /** Chance a below-the-line clip still makes the reel (the explore pass). */
    val exploreChance: Float = 0.2f
) {
    fun toJson(): String = JSONObject().apply {
        put("completionWeight", completionWeight.toDouble())
        put("noveltyWeight", noveltyWeight.toDouble())
        put("favoritesWeight", favoritesWeight.toDouble())
        put("peopleWeight", peopleWeight.toDouble())
        put("nostalgiaWeight", nostalgiaWeight.toDouble())
        put("timeOfDayWeight", timeOfDayWeight.toDouble())
        put("energyWeight", energyWeight.toDouble())
        put("voiceWeight", voiceWeight.toDouble())
        put("qualityWeight", qualityWeight.toDouble())
        put("replayPenalty", replayPenalty.toDouble())
        put("junkCut", junkCut.toDouble())
        put("feedLength", feedLength)
        put("bandSize", bandSize)
        put("variety", variety.toDouble())
        put("exploreChance", exploreChance.toDouble())
    }.toString()

    companion object {
        fun fromJson(o: JSONObject?): FeedTuning {
            if (o == null) return FeedTuning()
            return runCatching {
                FeedTuning(
                    completionWeight = o.optDouble("completionWeight", 1.0).toFloat(),
                    noveltyWeight = o.optDouble("noveltyWeight", 1.0).toFloat(),
                    favoritesWeight = o.optDouble("favoritesWeight", 1.0).toFloat(),
                    peopleWeight = o.optDouble("peopleWeight", 1.0).toFloat(),
                    nostalgiaWeight = o.optDouble("nostalgiaWeight", 1.0).toFloat(),
                    timeOfDayWeight = o.optDouble("timeOfDayWeight", 1.0).toFloat(),
                    energyWeight = o.optDouble("energyWeight", 1.0).toFloat(),
                    voiceWeight = o.optDouble("voiceWeight", 1.0).toFloat(),
                    qualityWeight = o.optDouble("qualityWeight", 1.0).toFloat(),
                    replayPenalty = o.optDouble("replayPenalty", 0.65).toFloat(),
                    junkCut = o.optDouble("junkCut", 0.85).toFloat(),
                    feedLength = o.optInt("feedLength", 60),
                    bandSize = o.optInt("bandSize", 12),
                    variety = o.optDouble("variety", 1.0).toFloat(),
                    exploreChance = o.optDouble("exploreChance", 0.2).toFloat()
                )
            }.getOrDefault(FeedTuning())
        }

        /**
         * The named tilts. A preset is a weights snapshot — picking one copies
         * it into the settings, after which the sliders own the truth. That
         * keeps the ranking honest: it only ever reads stored numbers.
         */
        val PRESETS: Map<String, FeedTuning> = mapOf(
            "Open" to FeedTuning(),
            "Nostalgic" to FeedTuning(
                nostalgiaWeight = 2.2f, noveltyWeight = 1.3f,
                timeOfDayWeight = 0.6f, energyWeight = 0.6f
            ),
            "Hype" to FeedTuning(
                energyWeight = 1.8f, completionWeight = 1.2f, noveltyWeight = 1.2f,
                peopleWeight = 0.8f, nostalgiaWeight = 0.6f
            ),
            "Calm" to FeedTuning(
                peopleWeight = 1.4f, timeOfDayWeight = 1.3f, energyWeight = 0.4f,
                nostalgiaWeight = 0.8f, noveltyWeight = 0.9f
            ),
            "Funny" to FeedTuning(
                peopleWeight = 1.8f, energyWeight = 0.9f, nostalgiaWeight = 0.8f
            )
        )
    }
}

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
    /** The taught tilt. "Custom" once any feed slider moves by hand. */
    val mood: String = "Open",
    /** The weights behind the mood. A preset pick copies the preset here. */
    val tuning: FeedTuning = FeedTuning(),
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
        put("tuning", org.json.JSONObject(tuning.toJson()))
        put("accentArgb", accentArgb)
    }.toString()

    companion object {
        fun fromJson(s: String?): EngineSettings {
            if (s.isNullOrBlank()) return EngineSettings()
            return runCatching {
                val o = JSONObject(s)
                val d = EngineSettings()
                val tuning = FeedTuning.fromJson(o.optJSONObject("tuning"))
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
                    tuning = tuning,
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
