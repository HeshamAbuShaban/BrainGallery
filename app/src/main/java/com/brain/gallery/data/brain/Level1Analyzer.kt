package com.brain.gallery.data.brain

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.brain.gallery.data.vision.FaceAnalyzer
import com.brain.gallery.data.vision.FaceEmbedder
import com.brain.gallery.data.vision.FaceReading
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
    private val faces: FaceAnalyzer,
    private val embedder: FaceEmbedder
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

            // Same bitmap, four cheap passes: hash, sharpness, faces, identity.
            val hash = try { VisionUtils.dHash(bmp) } catch (_: Exception) { 0L }
            val sharp = try { VisionUtils.brennerSharpness(bmp) } catch (_: Exception) { 0f }
            val reading = try {
                val small = VisionUtils.downscaleMaxWidth(bmp, 640)
                val r = faces.analyze(small)
                if (small !== bmp && !small.isRecycled) small.recycle()
                r
            } catch (_: Exception) { FaceReading(0, 0, emptyList()) }
            val faceCount = reading.count
            val smiles = reading.smiles
            // Dominant (largest) face -> identity embedding, cropped from full-res bitmap.
            // Scale box from 640-wide coords back to full bitmap coords.
            var faceBytes: ByteArray? = null
            try {
                val biggest = reading.boxes.maxByOrNull { it.area }
                if (biggest != null && bmp.width > 640) {
                    val k = bmp.width.toFloat() / 640f
                    val r = biggest.rect
                    val x = (r.left * k).toInt().coerceIn(0, bmp.width - 1)
                    val y = (r.top * k).toInt().coerceIn(0, bmp.height - 1)
                    val w = (r.width() * k).toInt().coerceIn(1, bmp.width - x)
                    val h = (r.height() * k).toInt().coerceIn(1, bmp.height - y)
                    val crop = android.graphics.Bitmap.createBitmap(bmp, x, y, w, h)
                    val emb = embedder.embed(crop)
                    if (!crop.isRecycled) crop.recycle()
                    if (emb != null) faceBytes = FaceEmbedder.toBytes(emb)
                } else if (biggest != null) {
                    val r = biggest.rect
                    val crop = android.graphics.Bitmap.createBitmap(bmp,
                        r.left.coerceIn(0, bmp.width - 1), r.top.coerceIn(0, bmp.height - 1),
                        r.width().coerceIn(1, bmp.width - r.left.coerceIn(0, bmp.width - 1)),
                        r.height().coerceIn(1, bmp.height - r.top.coerceIn(0, bmp.height - 1)))
                    val emb = embedder.embed(crop)
                    if (!crop.isRecycled) crop.recycle()
                    if (emb != null) faceBytes = FaceEmbedder.toBytes(emb)
                }
            } catch (_: Exception) { faceBytes = null }

            if (labels.isEmpty()) {
                return@withContext l0.copy(level = 1, faceCount = faceCount,
                    smileCount = smiles, phash = hash, sharpness = sharp,
                    faceEmbedding = faceBytes)
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
                faceCount = faceCount, smileCount = smiles, phash = hash, sharpness = sharp,
                faceEmbedding = faceBytes
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
