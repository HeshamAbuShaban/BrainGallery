package com.brain.gallery.domain.engine

import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.data.local.WatchEventEntity
import com.brain.gallery.domain.FeedTuning
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

data class FeedItem(val video: VideoEntity, val score: Float, val reason: String = "")

/**
 * Builds the For You reel.
 *
 * Every contribution is a plain number times a weight from [FeedTuning] — there
 * are no hidden constants here, so anything the reel does wrong is fixable from
 * the Tune tab. Each item also carries a short "why", assembled from the same
 * signals that scored it, so the ranking can explain itself on the card.
 *
 * The ordering rule matters as much as the scoring. Sorting purely by score made
 * the same few clips lead every single session, which is what "the feed is stuck"
 * feels like. Instead the ranked list is cut into small score bands and shuffled
 * inside each band: strong matches still surface first, but the order within a
 * tier is different every run, so two sessions never play out the same way.
 */
@Singleton
class FeedComposer @Inject constructor() {

    fun compose(
        videos: List<VideoEntity>,
        events: List<WatchEventEntity>,
        suppressed: Set<Long> = emptySet(),
        tuning: FeedTuning = FeedTuning(),
        exclude: Set<Long> = emptySet(),
        taste: TasteProfile = TasteProfile(),
        random: Random = Random.Default
    ): List<FeedItem> {
        if (videos.isEmpty()) return emptyList()
        val base = videos.filter { it.id !in suppressed }
        if (base.isEmpty()) return emptyList()

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val recentIds = events.take(40).map { it.videoId }.toSet()
        val limit = tuning.feedLength.coerceIn(10, 300)

        // The session walk. Served-this-session clips stay out, so the reel
        // covers the library instead of cycling the same sixty. When the
        // unseen pool runs thin, the wrap is structural, not accidental:
        // what is left unseen leads, then the served clips return as a
        // second pass behind them.
        val unseen = base.filter { it.id !in exclude }
        if (unseen.size >= limit / 2) return reel(unseen, recentIds, hour, tuning, taste, limit, random)

        val head = reel(unseen, recentIds, hour, tuning, taste, limit, random)
        val tailNeed = limit - head.size
        if (tailNeed <= 0) return head
        val served = base.filter { it.id in exclude }
        return head + reel(served, recentIds, hour, tuning, taste, tailNeed, random)
    }

    /**
     * One ranked pass over a pool: junk-cut, scored, then walked in bands
     * with a shuffle inside each band. Adjacent bands never reorder relative
     * to each other, so quality ordering holds while the order within a tier
     * differs every run.
     */
    private fun reel(
        pool: List<VideoEntity>,
        recentIds: Set<Long>,
        hour: Int,
        t: FeedTuning,
        taste: TasteProfile,
        limit: Int,
        random: Random
    ): List<FeedItem> {
        if (pool.isEmpty() || limit <= 0) return emptyList()
        val ranked = pool.asSequence()
            .filter { it.junkScore < t.junkCut }
            .map { v -> score(v, recentIds, hour, t, taste) }
            .sortedByDescending { it.score }
            .take(limit * 2)
            .toList()

        val out = mutableListOf<FeedItem>()
        var lastCat = ""
        for (band in ranked.chunked(t.bandSize.coerceIn(4, 60))) {
            for (item in band.shuffled(random)) {
                if (out.size >= limit) break
                // Palette cleanse: the reel should not feel like one folder on
                // repeat. At variety 1 the same category never sits twice in a
                // row; the rule loosens as variety drops.
                if (item.video.category == lastCat && random.nextFloat() < t.variety) continue
                if (item.score < 0.25f && random.nextFloat() > t.exploreChance) continue
                out += item
                lastCat = item.video.category
            }
            if (out.size >= limit) break
        }
        // Fill any remainder without the cleanse rule, still unordered.
        if (out.size < limit) for (item in ranked.shuffled(random)) {
            if (out.size >= limit) break
            if (out.none { it.video.id == item.video.id }) out += item
        }
        return out
    }

    /** Internal, not private: the ranking is exactly what unit tests must pin. */
    internal fun score(
        v: VideoEntity,
        recentIds: Set<Long>,
        hour: Int,
        t: FeedTuning,
        taste: TasteProfile = TasteProfile()
    ): FeedItem {
        var s = 0.3f // base exploration
        val why = mutableListOf<String>()
        // Behavior: completion is king
        if (v.watchCount > 0) {
            s += v.avgCompletion * 0.35f * t.completionWeight
            if (v.avgCompletion > 0.7f) {
                s += 0.15f * t.completionWeight
                why += "you usually finish these"
            }
            if (v.skipCount > 2) s -= 0.2f * t.completionWeight
        } else {
            s += 0.12f * t.noveltyWeight
            why += "never seen"
        }
        if (v.isFavorite) {
            s += 0.2f * t.favoritesWeight
            why += "a favorite"
        }
        // People matter: faces, especially smiles, earn a lift.
        if (v.faceCount > 0 || v.smileCount > 0) {
            s += (if (v.faceCount > 0) 0.08f else 0f) * t.peopleWeight +
                    (if (v.smileCount > 0) 0.05f else 0f) * t.peopleWeight
            why += if (v.smileCount > 0) "all smiles" else "with people"
        }
        // Nostalgia: buried gem
        val ageDays = (System.currentTimeMillis() / 1000 - v.dateAddedSec) / 86400
        val buried = ageDays > 180 && v.watchCount == 0 && v.junkScore < 0.4f
        if (buried) {
            s += 0.25f * t.nostalgiaWeight
            why += "buried ${(ageDays / 30).coerceAtLeast(6)} months"
        }
        val today = isOnThisDay(v.dateAddedSec)
        if (today) {
            s += 0.2f * t.nostalgiaWeight
            why += "from this day"
        }
        // Time-of-day fit
        val short = v.durationMs < 25_000
        if ((hour < 11 && short) || (hour >= 20 && v.durationMs > 60_000)) {
            s += 0.1f * t.timeOfDayWeight
            why += if (hour < 11) "fits the morning" else "fits the evening"
        }
        // Energy: high-motion categories and quick hits
        if (v.category in ENERGY_CATEGORIES) {
            s += 0.2f * t.energyWeight
            why += "high energy"
        } else if (short && t.energyWeight > 1f) {
            s += 0.1f * (t.energyWeight - 1f)
        }
        // Voice: the prosody pass heard someone talking. Only clips that were
        // actually heard carry the tag, so silence never earns a lift.
        if (v.prosodyTags.contains("speaking")) {
            s += 0.12f * t.voiceWeight
            why += "someone talking"
        }
        // Taste: the learned profile. Neutral is 0.5 by construction, so an
        // uninformed profile contributes nothing and a disliked kind of clip
        // sinks. The "why" is earned by the single strongest learned
        // attribute, not by the diffuse blend.
        s += (taste.affinityFor(v) - 0.5f) * 0.35f * t.tasteWeight
        if (taste.strongestAffinityFor(v) > 0.68f) why += "matches what you keep watching"
        // Confidence: uncertain brain ranks lower until L1/L2 done. At quality 0
        // the brain's certainty stops mattering entirely.
        s *= lerp(1f, 0.7f + v.confidence * 0.3f, t.qualityWeight)        // Anti-repeat-session: watched recently sinks
        if (v.id in recentIds) {
            s *= 1f - t.replayPenalty * 0.65f
            why += "you saw this recently"
        }
        // The cap stays; but raw sums are divided by the realistic ceiling
        // first (see [rescaled]) so a fully-loaded clip still sits apart.
        return FeedItem(v, rescaled(s), why.take(2).joinToString(" · ").ifEmpty { "picked for balance" })
    }

    /**
     * Score headroom. The raw sum of a fully-loaded clip (watched+favorite+
     * people+gems+voice+taste at default weights) tops ~2.1, so a plain clamp
     * packed a dozen different clips at a flat 1.0 and hid which one was
     * actually best. Dividing by the realistic everyday ceiling keeps the
     * top of the scale from walling itself into a flat line — strictly
     * order-preserving, so bands and every pinned test keep their shape.
     */
    private fun rescaled(raw: Float): Float = (raw / SCORE_CEILING).coerceIn(0f, 1f)

    private companion object {
        val ENERGY_CATEGORIES = setOf("music", "sport", "gaming", "dance", "celebration")
        /** Realistic everyday ceiling of the raw sum; see [rescaled]. */
        const val SCORE_CEILING = 1.4f
    }

    private fun lerp(a: Float, b: Float, by: Float): Float = a + (b - a) * by.coerceIn(0f, 1f)

    private fun isOnThisDay(dateAddedSec: Long): Boolean {
        if (dateAddedSec <= 0) return false
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = dateAddedSec * 1000 }
        return now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) &&
            now.get(Calendar.YEAR) != then.get(Calendar.YEAR)
    }
}
