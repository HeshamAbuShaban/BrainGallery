package com.brain.gallery.engine

import com.brain.gallery.data.local.BrainDatabase
import com.brain.gallery.data.local.FaceVectorEntity
import com.brain.gallery.data.vision.FaceEmbedder
import com.brain.gallery.data.vision.Spherical
import javax.inject.Inject
import javax.inject.Singleton

data class MatchDecision(val personId: Int, val ambiguous: Boolean, val best: Float, val margin: Float)

private data class PersonScore(val id: Int, val sim: Float)

/**
 * The pure matcher core, pinned by unit tests: one query hands in every
 * person's vectors, this decides. No database inside.
 */
internal fun decide(
    library: Map<Int, List<FloatArray>>,
    vec: FloatArray,
    minSim: Float,
    minMargin: Float
): MatchDecision {
    val scores = library.entries
        .map { (id, vs) ->
            var best = -2f
            for (v in vs) {
                val c = Spherical.cosine(v, vec)
                if (c > best) best = c
            }
            PersonScore(id, best)
        }
        .filter { it.sim > -2f }
        .sortedByDescending { it.sim }
    val top = scores.firstOrNull() ?: return MatchDecision(-1, false, 0f, 0f)
    val second = scores.getOrNull(1)?.sim ?: 0f
    val margin = top.sim - second
    if (top.sim < minSim) return MatchDecision(-1, false, top.sim, margin)
    if (margin < minMargin) return MatchDecision(-1, true, top.sim, margin)
    return MatchDecision(top.id, false, top.sim, margin)
}

/**
 * Incremental identity: match a new face vector against every person's stored
 * vectors by MAX similarity (not a centroid, which blurs a person into mush).
 * O(new x persons) instead of O(n^2) reclustering.
 *
 * Denial is how the engine shows it learned: `deny` lists persons the user
 * has explicitly rejected for a video — those clusters are simply absent
 * from the decision, like they were never matches to begin with.
 */
@Singleton
class PersonMatcher @Inject constructor(private val db: BrainDatabase) {

    /** One query, all assigned vectors, grouped (no per-person DB round trip). */
    suspend fun vectorsByPerson(deny: Set<Int> = emptySet()): Map<Int, List<FloatArray>> =
        db.personDao().assignedVectors()
            .groupBy { it.personId }
            .filterKeys { it !in deny }
            .mapValues { (_, rows) -> rows.mapNotNull { FaceEmbedder.fromBytes(it.vec) } }
            .filterValues { it.isNotEmpty() }

    suspend fun match(
        vec: FloatArray,
        minSim: Float = 0.42f,
        minMargin: Float = 0.05f,
        deny: Set<Int> = emptySet()
    ): MatchDecision {
        val library = vectorsByPerson(deny)
        if (library.isEmpty()) return MatchDecision(-1, false, 0f, 0f)
        return decide(library, vec, minSim, minMargin)
    }

    suspend fun assignOrCreate(
        videoId: Long, vec: FloatArray, quality: Float, minSim: Float = 0.42f
    ): Int {
        // The user may have already said "this clip is not that person".
        val denied = db.personDao().deniedPersonsFor(videoId).toSet()
        val decision = match(vec, minSim, deny = denied)
        val personId = if (decision.ambiguous) {
            -1 // surface to user: "Person A, or someone new?"
        } else if (decision.personId >= 0) {
            decision.personId
        } else {
            val id = db.personDao().insertPerson(
                com.brain.gallery.data.local.PersonEntity(createdAt = System.currentTimeMillis())
            )
            if (id > 0) id.toInt() else -1
        }
        db.personDao().insertVectors(listOf(
            FaceVectorEntity(
                videoId = videoId, personId = personId,
                vec = FaceEmbedder.toBytes(vec), quality = quality,
                createdAt = System.currentTimeMillis()
            )
        ))
        return personId
    }
}

/**
 * Fragmentation repair.
 *
 * Greedy insertion matching is order-dependent: whoever arrives first seeds a
 * person, and a face photographed under different lighting never quite clears
 * the bar, so one person ends up as five. This pass repeatedly merges the two
 * closest persons (highest max cross-similarity) until nothing is close enough,
 * which makes identity independent of insertion order.
 */
@Singleton
class PersonConsolidator @Inject constructor(
    private val db: BrainDatabase,
    private val matcher: PersonMatcher
) {

    suspend fun consolidate(mergeSim: Float = 0.45f, maxMerges: Int = 40): Int {
        var merges = 0
        while (merges < maxMerges) {
            val persons = db.personDao().allPersons()
            if (persons.size < 2) return merges
            // One hop for every person's vectors, instead of one query per person.
            val vecs = db.personDao().assignedVectors()
                .groupBy { it.personId }
                .mapValues { (_, rows) -> rows.mapNotNull { FaceEmbedder.fromBytes(it.vec) } }
            var bestA = -1
            var bestB = -1
            var bestSim = mergeSim
            for (i in persons.indices) {
                val a = vecs[persons[i].id].orEmpty()
                if (a.isEmpty()) continue
                for (j in i + 1 until persons.size) {
                    val b = vecs[persons[j].id].orEmpty()
                    if (b.isEmpty()) continue
                    var s = -2f
                    for (va in a) for (vb in b) {
                        val c = Spherical.cosine(va, vb)
                        if (c > s) s = c
                    }
                    if (s > bestSim) { bestSim = s; bestA = persons[i].id; bestB = persons[j].id }
                }
            }
            if (bestA < 0 || bestB < 0) return merges
            val na = vecs[bestA]?.size ?: 0
            val nb = vecs[bestB]?.size ?: 0
            val keep = if (na >= nb) bestA else bestB
            val drop = if (na >= nb) bestB else bestA
            db.personDao().mergePersons(drop, keep)
            db.videoDao().mergePersons(drop, keep)
            merges++
        }
        return merges
    }

    /** Second chance for vectors the ambiguity gate parked: adopt the clear winners. */
    suspend fun adoptUnassigned(minSim: Float = 0.50f, minMargin: Float = 0.08f): Int {
        val pending = db.personDao().unassignedVectors()
        if (pending.isEmpty()) return 0
        // Lessons enforced in bulk: video → persons the user rejected.
        val denied = db.personDao().allLessons()
            .groupBy { it.videoId }
            .mapValues { (_, l) -> l.map { it.personId }.toSet() }
        var adopted = 0
        for (row in pending) {
            val vec = FaceEmbedder.fromBytes(row.vec) ?: continue
            val d = matcher.match(vec, minSim, minMargin, deny = denied[row.videoId].orEmpty())
            if (d.personId >= 0 && !d.ambiguous) {
                db.personDao().assignVector(row.id, d.personId)
                adopted++
            }
        }
        return adopted
    }
}

/**
 * Detects when a single "person" is actually two people merged, by testing for
 * bimodality among that person's vectors. Self-correcting identity.
 */
@Singleton
class SplitDetector @Inject constructor(private val db: BrainDatabase) {

    suspend fun scanAll(minSeparation: Float = 0.25f): Int {
        val byPerson = db.personDao().assignedVectors()
            .groupBy { it.personId }
            .mapValues { (_, rows) -> rows.mapNotNull { FaceEmbedder.fromBytes(it.vec) } }
        var flagged = 0
        for (p in db.personDao().allPersons()) {
            // A person the user decided about stays decided. Silence is the
            // lesson; the engine has no authority to un-learn it.
            if (p.splitSilenced == true) continue
            val vs = byPerson[p.id].orEmpty()
            val bimodal = Spherical.looksLikeTwoPeople(vs, minSeparation = minSeparation)
            if (bimodal != p.splitSuggested) {
                if (bimodal) db.personDao().setSplitSuggested(p.id) else db.personDao().clearSplitSuggested(p.id)
                if (bimodal) flagged++
            }
        }
        return flagged
    }
}

/**
 * Infers a real name from folder/filename tokens co-occurring with a person.
 * Free signal, and it removes the "Person A" silliness.
 */
@Singleton
class NameSuggester @Inject constructor(private val db: BrainDatabase) {

    private val generic = setOf(
        "video", "videos", "vid", "img", "image", "images", "photo", "photos", "camera",
        "whatsapp", "telegram", "instagram", "tiktok", "snapchat", "download", "downloads",
        "screenrec", "screenrecordings", "screenshot", "screenshots", "movie", "movies",
        "clip", "clips", "media", "dcim", "final", "new", "media", "temp", "cache", "android"
    )

    suspend fun suggest(personId: Int): String? {
        val videos = db.videoDao().personGroup(personId)
        if (videos.size < 2) return null
        val counts = HashMap<String, Int>()
        for (v in videos) {
            val tokens = (v.displayName.substringBeforeLast(".") + " " + v.folderName)
                .lowercase().split(Regex("[^a-z]+")).filter { it.length > 2 && it !in generic }
                .filter { !it.all { ch -> ch.isDigit() } }
            for (t in tokens.toSet()) counts[t] = (counts[t] ?: 0) + 1
        }
        val best = counts.entries
            .filter { it.value >= 2 }
            .maxByOrNull { it.value } ?: return null
        return best.key.replaceFirstChar { it.uppercase() }
    }

    suspend fun applyAllSuggestions() {
        for (p in db.personDao().allPersons()) {
            if (p.verified || p.name.isNotBlank()) continue
            val s = suggest(p.id) ?: continue
            db.personDao().suggestName(p.id, s)
        }
    }
}
