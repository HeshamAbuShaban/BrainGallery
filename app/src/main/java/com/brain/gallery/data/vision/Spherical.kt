package com.brain.gallery.data.vision

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Spherical clustering helpers for identity. Pure math, no Android — part of the
 * portable engine seam.
 */
object Spherical {

    fun dot(a: FloatArray, b: FloatArray): Float {
        val n = min(a.size, b.size)
        var s = 0f
        for (i in 0 until n) s += a[i] * b[i]
        return s
    }

    fun cosine(a: FloatArray, b: FloatArray): Float = dot(a, b).coerceIn(-1f, 1f)

    fun centroid(vectors: List<FloatArray>): FloatArray {
        if (vectors.isEmpty()) return FloatArray(0)
        val n = vectors[0].size
        val out = FloatArray(n)
        for (v in vectors) for (i in 0 until min(n, v.size)) out[i] += v[i]
        return normalize(out)
    }

    fun normalize(v: FloatArray): FloatArray {
        var s = 0f
        for (x in v) s += x * x
        val n = sqrt(s)
        if (n < 1e-9f) return v
        val out = FloatArray(v.size)
        for (i in v.indices) out[i] = v[i] / n
        return out
    }

    fun meanSimilarityTo(vectors: List<FloatArray>, centroid: FloatArray): Float {
        if (vectors.isEmpty()) return 0f
        var s = 0f
        for (v in vectors) s += cosine(v, centroid)
        return s / vectors.size
    }

    /**
     * Spherical 2-means. Returns the two clusters. Used to detect when a single
     * "person" is actually two people merged (bimodal identity).
     */
    fun twoMeans(vectors: List<FloatArray>, iterations: Int = 12): Pair<List<FloatArray>, List<FloatArray>>? {
        if (vectors.size < 4) return null
        // Seed with the most distant pair.
        var bestI = 0
        var bestJ = 1
        var bestD = -2f
        for (i in vectors.indices) {
            for (j in i + 1 until vectors.size) {
                val d = cosine(vectors[i], vectors[j])
                if (d < bestD) { bestD = d; bestI = i; bestJ = j }
            }
        }
        var c1 = vectors[bestI].copyOf()
        var c2 = vectors[bestJ].copyOf()
        var g1 = mutableListOf<FloatArray>()
        var g2 = mutableListOf<FloatArray>()
        repeat(iterations) {
            g1 = mutableListOf(); g2 = mutableListOf()
            for (v in vectors) {
                val a = cosine(v, c1)
                val b = cosine(v, c2)
                if (a >= b) g1.add(v) else g2.add(v)
            }
            if (g1.isEmpty() || g2.isEmpty()) return null
            c1 = centroid(g1)
            c2 = centroid(g2)
        }
        return if (g1.isNotEmpty() && g2.isNotEmpty()) Pair(g1, g2) else null
    }

    /**
     * Bimodality verdict: two tight, well-separated modes => likely two people.
     * Requires enough vectors to avoid reading noise as structure.
     */
    fun looksLikeTwoPeople(
        vectors: List<FloatArray>,
        minVectors: Int = 6,
        minSeparation: Float = 0.25f,
        minWithin: Float = 0.35f
    ): Boolean {
        if (vectors.size < minVectors) return false
        val split = twoMeans(vectors) ?: return false
        val (g1, g2) = split
        if (g1.size < 2 || g2.size < 2) return false
        val c1 = centroid(g1)
        val c2 = centroid(g2)
        val separation = 1f - cosine(c1, c2)
        val within1 = meanSimilarityTo(g1, c1)
        val within2 = meanSimilarityTo(g2, c2)
        val minWithinActual = min(within1, within2)
        return separation >= minSeparation && minWithinActual >= minWithin
    }
}
