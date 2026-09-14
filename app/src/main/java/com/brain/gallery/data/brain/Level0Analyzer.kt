package com.brain.gallery.data.brain

import com.brain.gallery.data.local.VideoEntity

data class BrainResult(
    val category: String,
    val tags: List<String>,
    val about: String,
    val confidence: Float,
    val junkScore: Float,
    val level: Int,
    val faceCount: Int = 0,
    val smileCount: Int = 0,
    val phash: Long = 0L,
    val sharpness: Float = 0f,
    val faceEmbedding: ByteArray? = null
)

object Level0Analyzer {
    fun analyze(displayName: String, folder: String, durationMs: Long): BrainResult {
        val nameNoExt = displayName.substringBeforeLast(".")
        val tokens = nameNoExt.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(" ", "_", "-")
            .filter { it.length > 2 }
            .distinct().take(6)
        val cat = CategoryOntology.matchFile(displayName)
        val junk = CategoryOntology.junkScore(folder, displayName)
        return if (cat != null) {
            BrainResult(cat, (tokens + cat).distinct().take(8),
                "${cat.replaceFirstChar { it.uppercase() }} • ${folder}", 0.85f, junk, 0)
        } else {
            val folderCat = CategoryOntology.matchFile(folder)
            if (folderCat != null) {
                BrainResult(folderCat, (tokens + folderCat).distinct().take(8),
                    "${folderCat.replaceFirstChar { it.uppercase() }} • ${folder}", 0.7f, junk, 0)
            } else {
                val short = durationMs < 20_000
                BrainResult("unknown", tokens.take(5),
                    if (short) "Quick clip • ${folder}" else "Video • ${folder}",
                    0.35f, junk, 0)
            }
        }
    }

    fun needsDeeper(r: BrainResult, entity: VideoEntity): Boolean {
        if (r.confidence >= 0.85f) return false
        if (r.junkScore >= 0.8f) return false // junk stays junk, skip ML to save battery
        if (entity.watchCount > 0) return true // user cares -> worth L1
        val ageDays = (System.currentTimeMillis() / 1000 - entity.dateAddedSec) / 86400
        if (ageDays > 180 && r.junkScore < 0.4f) return true // buried gem candidate
        return r.confidence < 0.6f
    }
}
