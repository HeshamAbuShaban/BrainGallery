package com.brain.gallery.engine

/**
 * Portable engine seam: pure ranking/clustering math, zero Android imports.
 * This package is shaped for a future Rust port (logic ports, sensors stay native).
 */
object ClusterMath {

    /**
     * Single-linkage agglomerative clustering over cosine similarity.
     * Returns cluster index per item (-1 for singletons when [keepSingletons] is false).
     */
    fun clusterCosine(
        vectors: List<FloatArray>,
        threshold: Float = 0.55f,
        keepSingletons: Boolean = false
    ): IntArray {
        val n = vectors.size
        if (n == 0) return IntArray(0)
        val parent = IntArray(n) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            return r
        }
        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                if (cosine(vectors[i], vectors[j]) >= threshold) union(i, j)
            }
        }
        val roots = linkedMapOf<Int, Int>()
        val out = IntArray(n) { -1 }
        for (i in 0 until n) {
            val r = find(i)
            val members = (0 until n).count { find(it) == r }
            if (members < 2 && !keepSingletons) {
                out[i] = -1
            } else {
                out[i] = roots.getOrPut(r) { roots.size }
            }
        }
        return out
    }
    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val d = kotlin.math.sqrt(na * nb)
        return if (d < 1e-9f) 0f else (dot / d).coerceIn(-1f, 1f)
    }

    fun hamming(a: Long, b: Long): Int {
        var x = a xor b
        var c = 0
        while (x != 0L) {
            c++
            x = x and (x - 1)
        }
        return c
    }
}
