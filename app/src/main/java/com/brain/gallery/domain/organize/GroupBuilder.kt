package com.brain.gallery.domain.organize

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
    val savingsBytes: Long = 0L
)

enum class GroupKind { MEMORIES, PEOPLE, DUPLICATES, JUNK, FAVORITES, CATEGORY, EVENT, GEMS, ON_THIS_DAY, UNREVIEWED }

@Singleton
class GroupBuilder @Inject constructor(private val dups: DuplicateFinder) {

    fun build(all: List<VideoEntity>): List<SmartGroup> {
        if (all.isEmpty()) return emptyList()
        val out = mutableListOf<SmartGroup>()
        val memories = all.filter { it.junkScore < 0.5f }
        val junk = all.filter { it.junkScore >= 0.5f }
        val favs = all.filter { it.isFavorite }

        out += SmartGroup("memories", "Memories", "${memories.size} videos worth keeping",
            GroupKind.MEMORIES, memories.sortedByDescending { it.dateAddedSec }.take(30), 0xFF8B5CF6)

        // Identity: clustered dominant faces -> Person groups (the Google Photos key).
        val persons = all.filter { it.personId >= 0 }.groupBy { it.personId }
            .entries.sortedByDescending { it.value.size }
        for ((pid, list) in persons) {
            if (list.size < 2) continue
            val label = com.brain.gallery.engine.MemoryDocBuilder.personLabel(pid)
            val smiles = list.sumOf { it.smileCount }
            out += SmartGroup("person_$pid", label,
                "${list.size} videos${if (smiles > 0) " • $smiles smiles" else ""}",
                GroupKind.PEOPLE,
                list.sortedByDescending { it.dateAddedSec }.take(30), 0xFFEC4899)
        }
        // Fallback when identity hasn't clustered yet: face-count group.
        if (persons.isEmpty()) {
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
        val dupSets = dups.find(all)
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

        // Top categories.
        all.groupBy { it.category }
            .filter { (cat, list) -> cat != "unknown" && list.size >= 2 }
            .entries.sortedByDescending { it.value.size }.take(4)
            .forEach { (cat, list) ->
                out += SmartGroup("cat_$cat", cat.replaceFirstChar { it.uppercase() },
                    "${list.size} videos", GroupKind.CATEGORY,
                    list.sortedByDescending { it.dateAddedSec }.take(30), accentFor(cat))
            }

        val unreviewed = all.filter { it.brainLevel < 1 }.take(30)
        if (unreviewed.isNotEmpty()) out += SmartGroup("unreviewed", "Needs a look",
            "${unreviewed.size} not understood yet", GroupKind.UNREVIEWED, unreviewed, 0xFF6E7681)
        if (junk.isNotEmpty()) out += SmartGroup("junk", "Clutter drawer",
            "${junk.size} memes, screenshots, downloads", GroupKind.JUNK,
            junk.sortedByDescending { it.dateAddedSec }.take(30), 0xFF30363D)
        return out
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
