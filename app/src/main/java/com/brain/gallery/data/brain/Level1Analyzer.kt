package com.brain.gallery.data.brain

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.brain.gallery.data.vision.FaceAnalyzer
import com.brain.gallery.data.vision.VisionUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Level1Analyzer @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val faces: FaceAnalyzer
) {
    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.65f).build())
    }

    suspend fun analyze(uri: String, l0: BrainResult): BrainResult = withContext(Dispatchers.IO) {
        var bmp: Bitmap? = null
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(ctx, Uri.parse(uri))
            bmp = retriever.getFrameAtTime(1_000_000) ?: retriever.getFrameAtTime(0)
            retriever.release()
            if (bmp == null) return@withContext l0.copy(level = 1, confidence = l0.confidence)

            val labels = try {
                labeler.process(InputImage.fromBitmap(bmp, 0)).await()
                    .filter { it.confidence > 0.65f }.take(6)
            } catch (_: Exception) { emptyList() }

            // Same bitmap, three cheap passes: hash, sharpness, faces.
            val hash = try { VisionUtils.dHash(bmp) } catch (_: Exception) { 0L }
            val sharp = try { VisionUtils.brennerSharpness(bmp) } catch (_: Exception) { 0f }
            val (faceCount, smiles) = try {
                val small = VisionUtils.downscaleMaxWidth(bmp, 640)
                val r = faces.analyze(small)
                if (small !== bmp && !small.isRecycled) small.recycle()
                r
            } catch (_: Exception) { 0 to 0 }

            if (labels.isEmpty()) {
                return@withContext l0.copy(level = 1, faceCount = faceCount,
                    smileCount = smiles, phash = hash, sharpness = sharp)
            }
            val mlTags = labels.map { it.text.lowercase().replace(" ", "_") }
            val cat = CategoryOntology.matchFile(mlTags.joinToString(" ")) ?: l0.category.takeIf { it != "unknown" }
                ?: guessFromLabels(mlTags)
            val merged = (l0.tags + mlTags).distinct().take(10)
            val peopleSuffix = if (faceCount > 0) " • $faceCount ${if (faceCount == 1) "face" else "faces"}" else ""
            l0.copy(
                category = cat ?: "lifestyle",
                tags = merged,
                about = "${(cat ?: "Moment").replaceFirstChar { it.uppercase() }} • ${mlTags.take(2).joinToString(", ")}$peopleSuffix",
                confidence = 0.8f, level = 1,
                faceCount = faceCount, smileCount = smiles, phash = hash, sharpness = sharp
            )
        } catch (_: Exception) {
            l0.copy(level = 1)
        } finally {
            try { if (bmp != null && !bmp.isRecycled) bmp.recycle() } catch (_: Exception) {}
        }
    }

    private fun guessFromLabels(tags: List<String>): String? {
        val j = tags.joinToString(" ")
        return CategoryOntology.MAP.entries.firstOrNull { (_, keys) -> keys.any { j.contains(it) } }?.key
    }
}
