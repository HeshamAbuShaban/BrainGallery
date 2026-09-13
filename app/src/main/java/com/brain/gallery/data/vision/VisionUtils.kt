package com.brain.gallery.data.vision

import android.graphics.Bitmap

object VisionUtils {

    /** 64-bit dHash: 9x8 grayscale, bit=1 when left pixel brighter than right. */
    fun dHash(bmp: Bitmap): Long {
        val w = 9
        val h = 8
        val small = Bitmap.createScaledBitmap(bmp, w, h, true)
        return try {
            val px = IntArray(w * h)
            small.getPixels(px, 0, w, 0, 0, w, h)
            var hash = 0L
            var bit = 0
            for (y in 0 until h) {
                for (x in 0 until w - 1) {
                    if (lum(px[y * w + x]) > lum(px[y * w + x + 1])) {
                        hash = hash or (1L shl bit)
                    }
                    bit++
                }
            }
            hash
        } finally {
            if (!small.isRecycled) small.recycle()
        }
    }

    fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /**
     * Brenner sharpness: mean squared horizontal gradient at step 2.
     * Fast, noise-robust, best speed/accuracy tradeoff for mobile (see literature).
     * Higher = sharper. Compare relatively within a duplicate set, not absolutely.
     */
    fun brennerSharpness(bmp: Bitmap): Float {
        val targetW = 160
        val scale = targetW.toFloat() / bmp.width.coerceAtLeast(1)
        val w = targetW
        val h = (bmp.height * scale).toInt().coerceIn(1, 160)
        val small = Bitmap.createScaledBitmap(bmp, w, h, true)
        return try {
            val px = IntArray(w * h)
            small.getPixels(px, 0, w, 0, 0, w, h)
            var sum = 0.0
            var n = 0
            for (y in 0 until h) {
                for (x in 0 until w - 2) {
                    val d = (lum(px[y * w + x + 2]) - lum(px[y * w + x])).toDouble()
                    sum += d * d
                    n++
                }
            }
            if (n == 0) 0f else (sum / n).toFloat()
        } finally {
            if (!small.isRecycled) small.recycle()
        }
    }

    fun downscaleMaxWidth(bmp: Bitmap, maxW: Int): Bitmap {
        if (bmp.width <= maxW) return bmp
        val h = (bmp.height * (maxW.toFloat() / bmp.width)).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bmp, maxW, h, true)
    }

    private fun lum(c: Int): Int =
        (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)).toInt()
}
