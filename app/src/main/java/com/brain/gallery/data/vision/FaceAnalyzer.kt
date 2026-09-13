package com.brain.gallery.data.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

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

    /** Returns (faceCount, smilingCount). Never throws; (0,0) when unavailable. */
    suspend fun analyze(bmp: Bitmap): Pair<Int, Int> {
        return try {
            withTimeoutOrNull(8000) {
                val faces = detector.process(InputImage.fromBitmap(bmp, 0)).await()
                faces.size to faces.count { (it.smilingProbability ?: 0f) > 0.6f }
            } ?: (0 to 0)
        } catch (_: Exception) {
            0 to 0
        }
    }
}
