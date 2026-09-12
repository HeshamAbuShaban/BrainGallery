package com.brain.gallery.data.brain

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
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
class Level2Analyzer @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.6f).build())
    }

    suspend fun analyze(uri: String, durationMs: Long, prev: BrainResult): BrainResult = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(ctx, Uri.parse(uri))
            val stamps = listOf(durationMs * 1000 / 4, durationMs * 1000 / 2, durationMs * 1000 * 3 / 4)
            val all = mutableSetOf<String>()
            for (t in stamps) {
                try {
                    val bmp = retriever.getFrameAtTime(t) ?: continue
                    val ls = labeler.process(InputImage.fromBitmap(bmp, 0)).await()
                    ls.filter { it.confidence > 0.6f }.take(4).forEach { all += it.text.lowercase().replace(" ", "_") }
                } catch (_: Exception) {}
            }
            retriever.release()
            if (all.isEmpty()) return@withContext prev.copy(level = 2, confidence = prev.confidence)
            val merged = (prev.tags + all).distinct().take(12)
            val cat = CategoryOntology.matchFile(all.joinToString(" ")) ?: prev.category
            prev.copy(category = cat, tags = merged,
                about = "${cat.replaceFirstChar { it.uppercase() }} • ${all.take(3).joinToString(", ")}",
                confidence = 0.92f, level = 2)
        } catch (_: Exception) { prev.copy(level = 2) }
    }
}
