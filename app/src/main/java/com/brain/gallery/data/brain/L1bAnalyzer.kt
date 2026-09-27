package com.brain.gallery.data.brain

import android.content.Context
import android.net.Uri
import com.brain.gallery.data.vision.FrameCache
import com.brain.gallery.data.vision.FrameSource
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class SemanticResult(
    val category: String,
    val tags: List<String>,
    val about: String,
    val confidence: Float
)

/**
 * L1b — semantic pass. Budgeted and expected-value ordered: this is the only
 * stage that runs an ML *model*, so it must not gate perceptual knowledge.
 */
@Singleton
class L1bAnalyzer @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val frames: FrameSource,
    private val cache: FrameCache
) {
    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.6f).build())
    }

    suspend fun analyze(
        videoId: Long, uri: String, durationMs: Long, prev: PerceptualResult
    ): SemanticResult = withContext(Dispatchers.IO) {
        try {
            val stamps = if (durationMs > 15_000)
                listOf(durationMs * 1000L / 3, durationMs * 1000L * 2 / 3) else emptyList()
            val framesToUse = mutableListOf<android.graphics.Bitmap>()
            try {
                for (t in stamps) frames.mid(uri, durationMs)?.let { framesToUse.add(it) }
                if (framesToUse.isEmpty()) frames.mid(uri, durationMs)?.let { framesToUse.add(it) }
                if (framesToUse.isEmpty()) {
                    return@withContext prev.toSemantic(0.6f)
                }
                // Conditional frame cache: only escalated videos (the ones we decode
                // 3-4x) persist a frame, and the cache is a bounded LRU.
                cache.put(videoId, framesToUse.first())
                val all = linkedSetOf<String>()
                for (b in framesToUse) {
                    val labels = runCatching {
                        labeler.process(InputImage.fromBitmap(b, 0)).await()
                    }.getOrNull() ?: continue
                    labels.filter { it.confidence > 0.6f }.take(5)
                        .forEach { all += it.text.lowercase().replace(" ", "_") }
                }
                if (all.isEmpty()) return@withContext prev.toSemantic(0.7f)
                val cat = CategoryOntology.matchFile(all.joinToString(" ")) ?: prev.category
                val merged = (prev.tags + all).distinct().take(12)
                SemanticResult(
                    category = cat,
                    tags = merged,
                    about = "${cat.replaceFirstChar { it.uppercase() }} • ${all.take(2).joinToString(", ")}",
                    confidence = 0.85f
                )
            } finally {
                framesToUse.forEach { runCatching { if (!it.isRecycled) it.recycle() } }
            }
        } catch (_: Exception) {
            prev.toSemantic(prev.confidence)
        }
    }

    private fun PerceptualResult.toSemantic(conf: Float) = SemanticResult(
        category, tags, about, conf
    )
}
