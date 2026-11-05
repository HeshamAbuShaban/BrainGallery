package com.brain.gallery.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit pins for the matcher's language. The one-frame real-bitmap roundtrip
 * (decode -> dHash -> match) is a device-level check by nature — android
 * graphics stay out of this plain JUnit suite — but the matcher's contract
 * on the hashes dHash produces is pinned here in full.
 */
class ContentIdTest {

    private fun h(id: Long, phash: Long, video: Boolean = true) =
        ContentId.LibraryHash(id, video, phash)

    @Test
    fun `an exact fingerprint is a near-identical hit`() {
        val out = ContentId.match(listOf(0b1111_0000L), listOf(h(1, 0b1111_0000L)))
        assertEquals(listOf(1L), out.map { it.hash.id })
        assertTrue(ContentId.nearIdentical(out))
    }

    @Test
    fun `same content under a re-encode stays inside the keeper bar`() {
        // Four flips: wear from a re-encode — inside the keeper bar (six),
        // but past the near-identical bar (three), so a cousin and not a twin.
        val noisy = 0b1111_0000L xor 0b0000_1111L
        val out = ContentId.match(listOf(noisy), listOf(h(1, 0b1111_0000L)))
        assertEquals(listOf(1L), out.map { it.hash.id })
        assertEquals(4, out[0].distance)
        assertFalse("gap ${out[0].distance} is a cousin, not a twin",
            ContentId.nearIdentical(out))
    }

    @Test
    fun `beyond the bar is honestly nothing`() {
        val far = 0b1111_1111_1111_1111L // twelve-plus flips from the query
        assertEquals(emptyList<ContentId.Scored>(),
            ContentId.match(listOf(far), listOf(h(1, 0b1111_0000L))))
    }

    @Test
    fun `any of the picked frames can carry the match`() {
        val a = 0b1111_0000L
        val b = 0b0101_0101L
        val c = 0b1010_1010L
        val out = ContentId.match(listOf(a, b, c), listOf(h(9, c)))
        assertEquals(listOf(9L), out.map { it.hash.id })
        assertEquals(0, out[0].distance)
    }

    @Test
    fun `closest wins and ties keep a stable order`() {
        val q = 0b1111_0000L
        val library = listOf(h(5, q xor 0b11L), h(2, q xor 0b111L), h(1, q))
        val out = ContentId.match(listOf(q), library)
        assertEquals(listOf(1L, 5L, 2L), out.map { it.hash.id })
        assertEquals(listOf(0, 2, 3), out.map { it.distance })
    }

    @Test
    fun `uncomputed fingerprints are not evidence of anything`() {
        // A phash of 0 means "never hashed" — on both sides it must never
        // accidentally act as a match (hamming against 0 has no meaning).
        assertEquals(emptyList<ContentId.Scored>(),
            ContentId.match(listOf(0L), listOf(h(1, 123L))))
        assertEquals(emptyList<ContentId.Scored>(),
            ContentId.match(listOf(123L), listOf(h(1, 0L))))
    }

    @Test
    fun `a hit list is honest about having nothing`() {
        // No library, no query, no hits — every degenerate shape stays empty,
        // never a crash and never a fake 100% match.
        assertTrue(ContentId.match(emptyList(), listOf(h(1, 5L))).isEmpty())
        assertTrue(ContentId.match(listOf(5L), emptyList()).isEmpty())
    }
}
