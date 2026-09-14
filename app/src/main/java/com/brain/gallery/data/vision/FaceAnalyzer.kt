package com.brain.gallery.data.vision

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

data class FaceBox(val rect: Rect, val smiling: Boolean) {
    val area: Int get() = rect.width() * rect.height()
}

data class FaceReading(val count: Int, val smiles: Int, val boxes: List<FaceBox>)

@Singleton
class FaceAnalyzer @Inject constructor() {

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.15f)
                .build()
        )
    }

    /** Never throws; empty reading when unavailable. Boxes are in bitmap coordinates. */
    suspend fun analyze(bmp: Bitmap): FaceReading {
        return try {
            withTimeoutOrNull(8000) {
                val faces = detector.process(InputImage.fromBitmap(bmp, 0)).await()
                val boxes = faces.mapNotNull { f ->
                    f.boundingBox?.let { box ->
                        // Clamp to bitmap bounds (detector can overshoot).
                        val safe = Rect(
                            box.left.coerceIn(0, bmp.width - 1),
                            box.top.coerceIn(0, bmp.height - 1),
                            box.right.coerceIn(1, bmp.width),
                            box.bottom.coerceIn(1, bmp.height)
                        )
                        if (safe.width() < 8 || safe.height() < 8) null
                        else FaceBox(safe, (f.smilingProbability ?: 0f) > 0.6f)
                    }
                }
                FaceReading(faces.size, boxes.count { it.smiling }, boxes)
            } ?: FaceReading(0, 0, emptyList())
        } catch (_: Exception) {
            FaceReading(0, 0, emptyList())
        }
    }
}
