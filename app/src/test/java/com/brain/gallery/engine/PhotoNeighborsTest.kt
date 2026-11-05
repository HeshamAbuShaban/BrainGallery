package com.brain.gallery.engine

import com.brain.gallery.data.local.PhotoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins for the photo rail's language. The rules are deliberately plain —
 * folder, week, label, tags, fingerprint — so each one can be broken alone
 * here rather than discovered on a phone.
 */
class PhotoNeighborsTest {

    private fun photo(
        id: Long,
        folder: String = "Camera",
        day: Long = 0,
        category: String = "unknown",
        tags: String = "",
        phash: Long = 0L,
        animated: Boolean? = null
    ) = PhotoEntity(
        id = id,
        uri = "content://photo/$id",
        displayName = "photo-$id.jpg",
        folderName = folder,
        dateAddedSec = day,
        category = category,
        tags = tags,
        phash = phash,
        animated = animated
    )

    @Test
    fun `an empty library has no rail`() {
        assertEquals(emptyList<PhotoEntity>(), PhotoNeighbors.of(photo(1), emptyList()))
    }

    @Test
    fun `a photo never suggests itself`() {
        val seed = photo(1)
        assertEquals(emptyList<PhotoEntity>(), PhotoNeighbors.of(seed, listOf(seed)))
    }

    @Test
    fun `the same folder in the same week beats a stranger`() {
        val seed = photo(1, folder = "Camera", day = 1_000)
        val stranger = photo(2, folder = "Downloads", day = 1_000)
        val sibling = photo(3, folder = "Camera", day = 2_000)
        assertEquals(listOf(3L), PhotoNeighbors.of(seed, listOf(seed, stranger, sibling)).map { it.id })
    }

    @Test
    fun `a shared category is a reason`() {
        val seed = photo(1, folder = "Camera", day = 0, category = "food")
        val other = photo(2, folder = "Screenshots", day = 900_000, category = "food")
        assertEquals(listOf(2L), PhotoNeighbors.of(seed, listOf(seed, other)).map { it.id })
    }

    @Test
    fun `unknown category is not a reason`() {
        val seed = photo(1, folder = "Camera", day = 0)
        val other = photo(2, folder = "Downloads", day = 900_000)
        assertEquals(0f, PhotoNeighbors.score(seed, other))
    }

    @Test
    fun `shared tags pull two photos together`() {
        val seed = photo(1, folder = "A", day = 0, tags = "beach,sunset")
        val other = photo(2, folder = "B", day = 900_000, tags = "beach,sunset")
        assertTrue(PhotoNeighbors.score(seed, other) >= 0.5f)
        assertEquals(listOf(2L), PhotoNeighbors.of(seed, listOf(seed, other)).map { it.id })
    }

    @Test
    fun `a close fingerprint outranks a far one`() {
        val seed = photo(1, folder = "A", day = 0, phash = 0b1111_1111L)
        val close = photo(2, folder = "B", day = 900_000, phash = 0b1111_1110L)
        val far = photo(3, folder = "B", day = 900_000, phash = 0b0000_0000L)
        assertEquals(listOf(2L), PhotoNeighbors.of(seed, listOf(seed, far, close)).map { it.id })
    }

    @Test
    fun `zero fingerprint means unhashed, not identical`() {
        val seed = photo(1, folder = "A", day = 0, phash = 0L)
        val other = photo(2, folder = "B", day = 900_000, phash = 0L)
        assertEquals(0f, PhotoNeighbors.score(seed, other))
    }

    @Test
    fun `folder kinship ignores case`() {
        val seed = photo(1, folder = "Camera", day = 0)
        val other = photo(2, folder = "camera", day = 1_000)
        assertEquals(2f, PhotoNeighbors.score(seed, other))
    }

    @Test
    fun `blank folders do not make a family`() {
        val seed = photo(1, folder = "", day = 0)
        val other = photo(2, folder = "", day = 1_000)
        assertEquals(0f, PhotoNeighbors.score(seed, other))
    }

    @Test
    fun `unicode folder names survive the comparison`() {
        val seed = photo(1, folder = "Été/2024", day = 0)
        val other = photo(2, folder = "Été/2024", day = 1_000)
        assertEquals(listOf(2L), PhotoNeighbors.of(seed, listOf(seed, other)).map { it.id })
    }

    @Test
    fun `two animated frames are neighbours even without a label`() {
        val seed = photo(1, folder = "A", day = 0, animated = true)
        val animated = photo(2, folder = "B", day = 900_000, animated = true)
        val still = photo(3, folder = "B", day = 900_000)
        assertEquals(listOf(2L), PhotoNeighbors.of(seed, listOf(seed, still, animated)).map { it.id })
    }

    @Test
    fun `the fallback is the same folder nearest in time`() {
        val seed = photo(1, folder = "Trips", day = 0)
        val week = photo(2, folder = "Trips", day = 5 * 86_400L)
        val month = photo(3, folder = "Trips", day = 30 * 86_400L)
        val elsewhere = photo(4, folder = "Camera", day = 1 * 86_400L)
        assertEquals(
            listOf(2L, 3L),
            PhotoNeighbors.of(seed, listOf(seed, month, elsewhere, week)).map { it.id })
    }

    @Test
    fun `the rail never exceeds its cap`() {
        val seed = photo(1, folder = "Camera", day = 0)
        val crowd = (2L..30L).map { photo(it, folder = "Camera", day = it * 10) }
        assertEquals(PhotoNeighbors.CAP,
            PhotoNeighbors.of(seed, listOf(seed) + crowd).size)
        assertEquals(3, PhotoNeighbors.of(seed, listOf(seed) + crowd, cap = 3).size)
    }

    @Test
    fun `equal scores go to the nearer day`() {
        val seed = photo(1, folder = "Camera", day = 0)
        val near = photo(2, folder = "Camera", day = 1_000)
        val far = photo(3, folder = "Camera", day = 2_000)
        assertEquals(listOf(2L, 3L),
            PhotoNeighbors.of(seed, listOf(seed, far, near)).map { it.id })
    }
}
