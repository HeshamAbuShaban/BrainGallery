package com.brain.gallery.data.brain

import android.graphics.Bitmap
import com.brain.gallery.data.vision.FaceAnalyzer
import com.brain.gallery.data.vision.FaceBox
import com.brain.gallery.data.vision.FaceEmbedder
import com.brain.gallery.data.vision.FaceReading
import com.brain.gallery.data.vision.FrameSource
import com.brain.gallery.data.vision.VisionUtils
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

data class PerceptualResult(
    val phash: Long,
    val sharpness: Float,
    val faceCount: Int,
    val smileCount: Int,
    val about: String,
    val category: String,
    val tags: List<String>,
    val confidence: Float,
    val vectors: List<VectorCandidate>,
    val priority: Float,
    val pendingSemantic: Boolean,
    /** False only when no frame could be decoded at all (dead/corrupt file). */
    val frameRead: Boolean = true
)

data class VectorCandidate(val vec: FloatArray, val quality: Float) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = vec.contentHashCode()
}

/**
 * L1a — perceptual pass. Unbudgeted, runs on every pending video.
 * No ML model inference: dHash + sharpness + face boxes + identity embeddings.
 * Gives complete duplicate detection and complete identity on the first pass.
 */
@Singleton
class L1aAnalyzer @Inject constructor(
    private val frames: FrameSource,
    private val faces: FaceAnalyzer,
    private val embedder: FaceEmbedder
) {

    suspend fun analyze(uri: String, durationMs: Long, fileName: String, folder: String): PerceptualResult {
        val l0 = Level0Analyzer.analyze(fileName, folder, durationMs)
        val raw = frames.mid(uri, durationMs)
        if (raw == null) {
            return PerceptualResult(0L, 0f, 0, 0, l0.about, l0.category, l0.tags,
                l0.confidence, emptyList(),
                PriorityScorer.score(l0.junkScore, durationMs, 0, l0.category),
                l0.category == "unknown" || l0.confidence < 0.6f,
                frameRead = false)
        }
        val bmp: Bitmap = raw

        return try {
            val phash = runCatching { VisionUtils.dHash(bmp) }.getOrDefault(0L)
            val sharpness = runCatching { VisionUtils.brennerSharpness(bmp) }.getOrDefault(0f)

            val reading = runCatching {
                val small = VisionUtils.downscaleMaxWidth(bmp, 640)
                val r = faces.analyze(small)
                if (small !== bmp && !small.isRecycled) small.recycle()
                r
            }.getOrElse { FaceReading(0, 0, emptyList()) }

            val vectors = extractVectors(bmp, reading.boxes, sharpness)

            val priority = PriorityScorer.score(l0.junkScore, durationMs, reading.count, l0.category)
            PerceptualResult(
                phash = phash,
                sharpness = sharpness,
                faceCount = reading.count,
                smileCount = reading.smiles,
                about = l0.about,
                category = l0.category,
                tags = l0.tags,
                confidence = max(l0.confidence, if (reading.count > 0) 0.6f else 0f),
                vectors = vectors,
                priority = priority,
                pendingSemantic = l0.category == "unknown" || l0.confidence < 0.6f
            )
        } catch (_: Exception) {
            PerceptualResult(0L, 0f, 0, 0, l0.about, l0.category, l0.tags, l0.confidence,
                emptyList(), 0.3f, true)
        } finally {
            runCatching { if (!bmp.isRecycled) bmp.recycle() }
        }
    }

    /** Up to 2 largest faces from up to 3 frames, quality-gated. */
    private suspend fun extractVectors(
        bmp: Bitmap, boxes: List<FaceBox>, sharpness: Float
    ): List<VectorCandidate> {
        if (boxes.isEmpty()) return emptyList()
        val out = mutableListOf<VectorCandidate>()
        // Quality gate: faces must be big enough and the frame sharp enough.
        if (sharpness < 8f) return emptyList()
        val k = bmp.width.toFloat() / 640f
        for (box in boxes.sortedByDescending { it.area }.take(2)) {
            val w = (box.rect.width() * k).toInt()
            val h = (box.rect.height() * k).toInt()
            if (w < 48 || h < 48) continue
            val x = (box.rect.left * k).toInt().coerceIn(0, max(0, bmp.width - w))
            val y = (box.rect.top * k).toInt().coerceIn(0, max(0, bmp.height - h))
            val crop = runCatching {
                Bitmap.createBitmap(bmp, x, y, min(w, bmp.width - x), min(h, bmp.height - y))
            }.getOrNull() ?: continue
            val emb = embedder.embed(crop)
            runCatching { if (!crop.isRecycled) crop.recycle() }
            if (emb == null) continue
            val areaScore = ((w * h).toFloat() / (640f * 640f)).coerceIn(0f, 1f)
            val sharpScore = (sharpness / 400f).coerceIn(0f, 1f)
            out += VectorCandidate(emb, areaScore * 0.6f + sharpScore * 0.4f)
        }
        return out.sortedByDescending { it.quality }.take(2)
    }
}

/** Expected-value queue: spend the semantic budget where labels add the most. */
object PriorityScorer {
    fun score(junk: Float, durationMs: Long, faces: Int, category: String): Float {
        var s = 0f
        s += (1f - junk).coerceIn(0f, 1f) * 0.40f          // memories beat clutter
        if (category == "unknown") s += 0.20f                 // document is empty
        if (faces > 0) s += 0.20f                             // identity value
        if (durationMs > 60_000) s += 0.10f                   // long-form context
        s += 0.10f * min(1f, durationMs / 600_000f)            // cap
        return s.coerceIn(0f, 1f)
    }
}
