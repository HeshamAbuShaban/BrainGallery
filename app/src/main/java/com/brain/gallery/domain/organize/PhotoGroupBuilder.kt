package com.brain.gallery.domain.organize

import com.brain.gallery.data.local.PhotoEntity

/**
 * The brain's shelves for stills — the photo half of what the video groups
 * do. Photos carry a lighter brain (no embeddings, no prosody), so the
 * signals are the shoebox ones: who is in the frame, what it was labeled,
 * whether it reads as clutter, and how far the reading has come.
 *
 * Pure logic, no Android — same contract as GroupBuilder. Evidence gates
 * apply here too: a shelf with fewer than a handful of photos is noise, not
 * a shelf.
 */
enum class PhotoGroupKind { FACES, CATEGORY, CLUTTER, LEARNING }

data class PhotoSmartGroup(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: PhotoGroupKind,
    val photos: List<PhotoEntity>,
    val accent: Long
)

object PhotoGroupBuilder {

    fun build(all: List<PhotoEntity>, junkThreshold: Float = 0.5f): List<PhotoSmartGroup> {
        if (all.isEmpty()) return emptyList()
        val out = mutableListOf<PhotoSmartGroup>()

        // People, at the photo level: the face pass counted who is in frame.
        val faces = all.filter { it.faceCount > 0 }.sortedByDescending { it.dateAddedSec }
        if (faces.size >= 3) out += PhotoSmartGroup(
            "faces", "With people",
            "${faces.size} photos with faces in them",
            PhotoGroupKind.FACES, faces, 0xFFEC4899
        )
        val smiles = all.filter { it.smileCount > 0 }.sortedByDescending { it.dateAddedSec }
        if (smiles.size >= 3) out += PhotoSmartGroup(
            "smiles", "All smiles",
            "${smiles.size} caught smiling",
            PhotoGroupKind.FACES, smiles, 0xFFF472B6
        )

        // Labeled subjects: only shelves with real weight survive the gate —
        // a stray filename token never gets to speak for four of your photos.
        all.map { it.category }
            .filter { it != "unknown" }
            .groupingBy { it }.eachCount()
            .entries.filter { it.value >= 4 }.sortedByDescending { it.value }
            .take(4)
            .forEach { (cat, n) ->
                val list = all.filter { it.category == cat }.sortedByDescending { it.dateAddedSec }
                out += PhotoSmartGroup(
                    "cat_$cat", cat.replaceFirstChar { it.uppercase() },
                    "$n photos",
                    PhotoGroupKind.CATEGORY, list, accentFor(cat)
                )
            }

        // The reading queue, told honestly.
        val learning = all.filter { it.brainLevel < 1 }.sortedByDescending { it.dateAddedSec }
        if (learning.isNotEmpty()) out += PhotoSmartGroup(
            "learning", "Being read",
            "${learning.size} still being understood",
            PhotoGroupKind.LEARNING, learning, 0xFF6E7681
        )

        val clutter = all.filter { it.junkScore >= junkThreshold }.sortedByDescending { it.dateAddedSec }
        if (clutter.size >= 3) out += PhotoSmartGroup(
            "clutter", "Clutter",
            "${clutter.size} screenshots and downloads",
            PhotoGroupKind.CLUTTER, clutter, 0xFF30363D
        )

        return out
    }

    private fun accentFor(cat: String): Long = when (cat) {
        "music", "gaming" -> 0xFF8B5CF6
        "birthday", "wedding" -> 0xFFEC4899
        "trip", "beach", "travel" -> 0xFF06B6D4
        "food" -> 0xFFF59E0B
        "pet" -> 0xFF10B981
        "sport" -> 0xFF3B82F6
        else -> 0xFF6366F1
    }
}
