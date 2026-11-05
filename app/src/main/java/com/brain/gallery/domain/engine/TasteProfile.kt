package com.brain.gallery.domain.engine

import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.data.local.WatchEventEntity
import org.json.JSONObject

/**
 * What this viewer demonstrably keeps finishing — and keeps skipping away
 * from — folded out of watch events into one small, explainable profile.
 *
 * Law of the house: this TEACHES the ranking, it never replaces it. The
 * profile is a set of evidence buckets over the attributes a clip carries
 * (category, person, folder, length); each bucket's affinity is a smoothed
 * like-ratio, biased toward *neutral* while the evidence is thin, so one
 * accidental watch of a cat video never turns the reel into a cat channel.
 * Buckets decay by half-life, so an obsession from March loosens its grip
 * by August. Nothing here leaves the phone — the profile is learned from
 * events that already lived in the local database.
 *
 * Pure by design: fold in events, read out affinities, round-trip through
 * JSON. The database and the exactly-once cursor live in `TasteLearner`.
 */
data class TasteProfile(
    val buckets: Map<String, Bucket> = emptyMap(),
    val learnedAtMs: Long = 0L
) {
    /** Evidence for one attribute: how often finishing/skipping touched it. */
    data class Bucket(val likes: Float, val dislikes: Float, val lastSeenMs: Long)

    /**
     * Affinity for one attribute key in 0..1, 0.5 = neutral. The smoothing
     * constant [SHRINK] is how much imaginary neutral evidence each bucket
     * starts with — larger means it takes more real watches to move the
     * needle, which is what keeps early learning honest.
     */
    fun affinity(key: String): Float {
        val b = buckets[key] ?: return 0.5f
        val n = b.likes + b.dislikes
        if (n <= 0f) return 0.5f
        return ((b.likes + SHRINK * 0.5f) / (n + SHRINK)).coerceIn(0f, 1f)
    }

    /** Blend over the attributes this clip actually carries; unknown clips read neutral. */
    fun affinityFor(v: VideoEntity): Float {
        val parts = buildList {
            if (v.category.isNotBlank()) add("cat:${v.category}" to 2f)
            if (v.personId >= 0) add("person:${v.personId}" to 2f)
            if (v.folderName.isNotBlank()) add("folder:${v.folderName}" to 1f)
            add((if (v.durationMs < SHORT_MS) "dur:short" else "dur:long") to 1f)
        }
        if (parts.isEmpty()) return 0.5f
        val total = parts.sumOf { it.second.toDouble() }.toFloat()
        return parts.sumOf { (k, w) -> affinity(k) * w.toDouble() }.toFloat() / total
    }

    /**
     * The one most-decided attribute this clip carries. Blend and "why" stay
     * apart on purpose: the *score* sums everything (a clip can match in
     * small ways), but the *reason* may claim "matches what you keep
     * watching" only when one attribute is genuinely, strongly learned.
     */
    fun strongestAffinityFor(v: VideoEntity): Float =
        keysFor(v).maxOf { affinity(it) }

    /**
     * Fold new events in. `like` = watched to (roughly) the end, `dislike` =
     * bailed before it began; the ambivalent middle teaches nothing. Counts
     * are floats so decay can walk them down gradually instead of in steps.
     */
    fun fold(
        events: List<WatchEventEntity>,
        library: Map<Long, VideoEntity>,
        nowMs: Long
    ): TasteProfile {
        if (events.isEmpty()) return this
        var result = if (learnedAtMs > 0) decayed(nowMs - learnedAtMs) else this
        var stamp = if (learnedAtMs > 0) learnedAtMs else nowMs
        for (e in events) {
            val v = library[e.videoId] ?: continue
            val like = e.completion >= LIKE_FRACTION
            val dislike = e.completion < DISLIKE_FRACTION
            if (!like && !dislike) continue
            for (key in keysFor(v)) {
                val b = result.buckets[key] ?: Bucket(0f, 0f, 0L)
                val nb = if (like) b.copy(likes = b.likes + 1f) else b.copy(dislikes = b.dislikes + 1f)
                result = result.copy(buckets = result.buckets + (key to
                    nb.copy(lastSeenMs = maxOf(b.lastSeenMs, e.atMs))))
            }
            stamp = maxOf(stamp, e.atMs)
        }
        return result.copy(learnedAtMs = stamp)
    }

    /** Gravity: buckets fade toward empty at [HALF_LIFE_DAYS]. Dead buckets vanish. */
    fun decayed(elapsedMs: Long): TasteProfile {
        if (buckets.isEmpty()) return this
        val halvings = elapsedMs / (HALF_LIFE_DAYS * 86_400_000.0)
        if (halvings < 0.5) return this
        val f = Math.pow(0.5, halvings)
        val kept = buckets.mapNotNull { (k, b) ->
            val likes = b.likes * f
            val dislikes = b.dislikes * f
            if (likes + dislikes < 1f) null else k to Bucket(likes.toFloat(), dislikes.toFloat(), b.lastSeenMs)
        }.toMap()
        return copy(buckets = kept)
    }

    fun toJson(): String = JSONObject().apply {
        put("learnedAtMs", learnedAtMs)
        val bs = JSONObject()
        for ((k, b) in buckets) {
            bs.put(k, JSONObject().put("l", b.likes).put("d", b.dislikes).put("t", b.lastSeenMs))
        }
        put("buckets", bs)
    }.toString()

    companion object {
        /** Watched most of the way = the viewer chose this. */
        const val LIKE_FRACTION = 0.7f
        /** Bailed almost immediately = the viewer rejected this. */
        const val DISLIKE_FRACTION = 0.2f
        /** Neutral-evidence weight per bucket — thin evidence stays near 0.5. */
        const val SHRINK = 6f
        /** Bucket half-life in days; taste is a moving thing. */
        const val HALF_LIFE_DAYS = 21.0
        /** Shorter than this and a clip is "a quick hit" for the length bucket. */
        const val SHORT_MS = 25_000L

        fun keysFor(v: VideoEntity): List<String> = buildList {
            if (v.category.isNotBlank()) add("cat:${v.category}")
            if (v.personId >= 0) add("person:${v.personId}")
            if (v.folderName.isNotBlank()) add("folder:${v.folderName}")
            add(if (v.durationMs < SHORT_MS) "dur:short" else "dur:long")
        }

        fun fromJson(s: String?): TasteProfile {
            if (s.isNullOrBlank()) return TasteProfile()
            return runCatching {
                val o = JSONObject(s)
                val bs = o.optJSONObject("buckets") ?: JSONObject()
                val buckets = buildMap {
                    for (k in bs.keys()) {
                        val b = bs.optJSONObject(k) ?: continue
                        put(k, Bucket(
                            likes = b.optDouble("l", 0.0).toFloat(),
                            dislikes = b.optDouble("d", 0.0).toFloat(),
                            lastSeenMs = b.optLong("t", 0L)))
                    }
                }.filter { it.value.likes + it.value.dislikes > 0f }
                TasteProfile(buckets, o.optLong("learnedAtMs", 0L))
            }.getOrDefault(TasteProfile())
        }
    }
}
