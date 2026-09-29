package com.brain.gallery.domain.organize

import com.brain.gallery.data.local.PersonEntity
import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.engine.DuplicateFinder
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

data class SmartGroup(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: GroupKind,
    val videos: List<VideoEntity>,
    val accent: Long, // ARGB for card gradient
    val keeperIds: Set<Long> = emptySet(),
    val redundantIds: Set<Long> = emptySet(),
    val savingsBytes: Long = 0L,
    val personId: Int = -1,
    val splitSuggested: Boolean = false,
    /** Several clusters independently named the same thing => probably one person. */
    val nameCollision: String? = null
)

enum class GroupKind { MEMORIES, PEOPLE, DUPLICATES, JUNK, FAVORITES, CATEGORY, EVENT, GEMS, ON_THIS_DAY, UNREVIEWED }

@Singleton
class GroupBuilder @Inject constructor(private val dups: DuplicateFinder) {

    fun build(
        all: List<VideoEntity>,
        persons: List<PersonEntity> = emptyList(),
        duplicatesOn: Boolean = true,
        junkThreshold: Float = 0.5f,
        /** Names the user chose, keyed by group id. Always wins over the engine's label. */
        customNames: Map<String, String> = emptyMap()
    ): List<SmartGroup> {
        if (all.isEmpty()) return emptyList()
        val out = mutableListOf<SmartGroup>()
        val memories = all.filter { it.junkScore < junkThreshold }
        val junk = all.filter { it.junkScore >= junkThreshold }
        val favs = all.filter { it.isFavorite }
        val byId = persons.associateBy { it.id }

        out += SmartGroup("memories", "Memories", "${memories.size} videos worth keeping",
            GroupKind.MEMORIES, memories.sortedByDescending { it.dateAddedSec }.take(30), 0xFF8B5CF6)

        // Identity: real names when known, split warnings when bimodality detected.
        // How many distinct clusters independently claim each name? More than one
        // for the same name is a fragmentation signal we can offer to repair.
        val nameHits = persons.filter { it.name.isBlank() && it.suggestedName.isNotBlank() }
            .groupingBy { it.suggestedName.lowercase() }.eachCount()

        val grouped = all.filter { it.personId >= 0 }.groupBy { it.personId }
        for ((pid, list) in grouped.entries.sortedByDescending { it.value.size }) {
            if (list.size < 2) continue
            val p = byId[pid]
            val label = p?.name?.takeIf { it.isNotBlank() }
                ?: p?.suggestedName?.takeIf { it.isNotBlank() }
                ?: "Unknown person"
            val smiles = list.sumOf { it.smileCount }
            val note = when {
                p?.splitSuggested == true -> "might be two people"
                p?.suggestedName?.isNotBlank() == true && p.name.isBlank() -> "named \"${p.suggestedName}\" by context"
                else -> "${list.size} videos${if (smiles > 0) " • $smiles smiles" else ""}"
            }
            val key = (p?.suggestedName ?: p?.name ?: "").lowercase()
            val collision = if (p?.name.isNullOrBlank() && (nameHits[key] ?: 0) > 1)
                p?.suggestedName else null
            out += SmartGroup("person_$pid", label, note, GroupKind.PEOPLE,
                list.sortedByDescending { it.dateAddedSec }.take(30), 0xFFEC4899,
                personId = pid, splitSuggested = p?.splitSuggested == true,
                nameCollision = collision)
        }
        if (grouped.isEmpty()) {
            val people = memories.filter { it.faceCount > 0 }
            if (people.isNotEmpty()) {
                val smiles = people.sumOf { it.smileCount }
                out += SmartGroup("people", "People",
                    "${people.size} videos • $smiles smiles", GroupKind.PEOPLE,
                    people.sortedWith(compareByDescending<VideoEntity> { it.smileCount }
                        .thenByDescending { it.faceCount }).take(30), 0xFFEC4899)
            }
        }

        // Duplicates: keeper first per set, actionable savings.
        val dupSets = if (duplicatesOn) dups.find(all) else emptyList()
        if (dupSets.isNotEmpty()) {
            val redundantCount = dupSets.sumOf { it.redundant.size }
            val savings = dupSets.sumOf { it.savingsBytes }
            out += SmartGroup("dups", "Duplicates",
                "${dupSets.size} sets • free ${fmtSize(savings)}", GroupKind.DUPLICATES,
                dupSets.flatMap { it.all }.take(30),
                0xFFF59E0B,
                keeperIds = dupSets.map { it.keeper.id }.toSet(),
                redundantIds = dupSets.flatMap { it.redundant }.map { it.id }.toSet(),
                savingsBytes = savings)
        }
        val gems = memories.filter { it.watchCount == 0 && ageDays(it) > 180 }
        if (gems.isNotEmpty()) out += SmartGroup("gems", "Buried gems",
            "${gems.size} unseen for 6+ months", GroupKind.GEMS,
            gems.sortedBy { it.dateAddedSec }.take(30), 0xFF06B6D4)
        val otd = memories.filter { isOnThisDay(it.dateAddedSec) }
        if (otd.isNotEmpty()) out += SmartGroup("otd", "On this day",
            "From years past, today", GroupKind.ON_THIS_DAY, otd, 0xFFF59E0B)
        if (favs.isNotEmpty()) out += SmartGroup("favs", "Favorites",
            "${favs.size} hand-picked", GroupKind.FAVORITES, favs, 0xFFEC4899)

        // Events: cluster same-folder videos added within 3 days of each other.
        out += clusterEvents(memories).take(4)

        // Subject categories, but only where the evidence actually holds up.
        //
        // A category is a guess from labels on single frames, so a raw count
        // means little: "baby" once fired on an adult speaker and "pet" on a
        // picture of text, and a group titled that is worse than no group at all.
        // A category is only offered when the clips agree, the brain is confident
        // in them, and the bucket is big enough to be a real theme. Everything
        // else stays in the library, unlabelled and unhurried, until it does.
        all.groupBy { it.effectiveGroup }
            .filter { (cat, list) -> cat != "unknown" && list.size >= MIN_CATEGORY_VIDEOS }
            .mapNotNull { (cat, list) ->
                // Anything the user placed by hand is their decision already, so
                // it is offered as-is; only engine-inferred buckets face the gate.
                if (list.any { it.manualGroup.isNotBlank() }) {
                    return@mapNotNull Triple(cat, list, 1f)
                }
                val confident = list.count { it.confidence >= CATEGORY_MIN_CONFIDENCE }
                // A hand-placed clip counts as agreement with its own group; there
                // is no label to agree with and the user already decided.
                val tagAgree = list.count {
                    cat in it.tagList || it.manualGroup.isNotBlank()
                }.toFloat() / list.size
                // Share of the library this bucket claims, and how it is spread.
                val spread = list.map { it.folderName.lowercase() }.distinct().size
                if (confident.toFloat() / list.size < CATEGORY_MIN_CONFIDENT_SHARE) null
                else Triple(cat, list, tagAgree)
            }
            .sortedByDescending { (_, list, agree) -> list.size * (0.5f + agree) }
            .take(4)
            .forEach { (cat, list, agree) ->
                val solid = agree >= 0.6f
                out += SmartGroup(
                    "cat_$cat", cat.replaceFirstChar { it.uppercase() },
                    "${list.size} videos" + if (solid) "" else " • still learning",
                    GroupKind.CATEGORY,
                    list.sortedByDescending { it.dateAddedSec }.take(30), accentFor(cat)
                )
            }

        val unreviewed = all.filter { it.brainLevel < 1 }.take(30)
        if (unreviewed.isNotEmpty()) out += SmartGroup("unreviewed", "Needs a look",
            "${unreviewed.size} not understood yet", GroupKind.UNREVIEWED, unreviewed, 0xFF6E7681)
        if (junk.isNotEmpty()) out += SmartGroup("junk", "Clutter drawer",
            "${junk.size} memes, screenshots, downloads", GroupKind.JUNK,
            junk.sortedByDescending { it.dateAddedSec }.take(30), 0xFF30363D)

        // A name the user chose beats any engine label, for every kind of group.
        if (customNames.isEmpty()) return out
        return out.map { g ->
            customNames[g.id]?.takeIf { it.isNotBlank() }?.let { g.copy(title = it) } ?: g
        }
    }

    private fun clusterEvents(memories: List<VideoEntity>): List<SmartGroup> {
        // Group by folder, then split into bursts separated by >3 day gaps.
        val events = mutableListOf<SmartGroup>()
        memories.groupBy { it.folderName.lowercase() }.forEach { (folder, list) ->
            if (list.size < 2) return@forEach
            val sorted = list.sortedBy { it.dateAddedSec }
            var burst = mutableListOf(sorted[0])
            sorted.zipWithNext().forEach { (a, b) ->
                if (b.dateAddedSec - a.dateAddedSec < 3 * 86400) burst += b
                else {
                    if (burst.size >= 2) events += burst.toEvent(folder)
                    burst = mutableListOf(b)
                }
            }
            if (burst.size >= 2) events += burst.toEvent(folder)
        }
        return events.sortedByDescending { it.videos.size }.take(6)
    }

    private fun List<VideoEntity>.toEvent(folder: String): SmartGroup {
        val label = first().category.takeIf { it != "unknown" }?.replaceFirstChar { it.uppercase() }
            ?: folder.replaceFirstChar { it.uppercase() }
        return SmartGroup("event_${folder}_${first().dateAddedSec}", label,
            "$size clips • ${folder}", GroupKind.EVENT,
            sortedByDescending { it.dateAddedSec }.take(30), 0xFF10B981)
    }

    private fun ageDays(v: VideoEntity) =
        (System.currentTimeMillis() / 1000 - v.dateAddedSec) / 86400

    private companion object {
        /** A bucket smaller than this is a coincidence, not a theme. */
        const val MIN_CATEGORY_VIDEOS = 8
        /** The brain has to be reasonably sure about the clip. */
        const val CATEGORY_MIN_CONFIDENCE = 0.5f
        /** ...and reasonably sure about most of the bucket, not just one or two. */
        const val CATEGORY_MIN_CONFIDENT_SHARE = 0.6f
    }

    private fun isOnThisDay(dateAddedSec: Long): Boolean {
        if (dateAddedSec <= 0) return false
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = dateAddedSec * 1000 }
        return now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) &&
            now.get(Calendar.YEAR) != then.get(Calendar.YEAR)
    }
    private fun accentFor(cat: String): Long = when (cat) {
        "birthday", "wedding" -> 0xFFEC4899
        "trip", "beach", "travel" -> 0xFF06B6D4
        "food" -> 0xFFF59E0B
        "sport", "gaming" -> 0xFF10B981
        "music" -> 0xFF8B5CF6
        else -> 0xFF8B5CF6
    }
}

fun fmtSize(bytes: Long): String {
    if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb < 1024) "%.0f MB".format(mb) else "%.1f GB".format(mb / 1024)
}
