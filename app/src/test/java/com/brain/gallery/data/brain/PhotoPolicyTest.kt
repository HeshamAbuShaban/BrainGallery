package com.brain.gallery.data.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The photo brain's fusion rule, pinned without a device. Break vectors:
 * a weak label erasing a named folder, a filename losing to its folder,
 * empty/unicode words inventing a category, a mush decode lending its label
 * confidence, and a screenshot priced as a memory.
 */
class PhotoPolicyTest {

    private fun decision(
        folder: String = "",
        name: String = "IMG_0001.jpg",
        labels: List<PhotoPolicy.Label> = emptyList(),
        sharpness: Float = 0f
    ) = PhotoPolicy.decide(folder, name, labels, sharpness)

    private fun tags(v: PhotoPolicy.Verdict) =
        v.tags.split(",").filter { it.isNotBlank() }

    @Test
    fun `a named folder beats a weak label`() {
        val v = decision(
            folder = "Birthday 2024",
            labels = listOf(PhotoPolicy.Label("Dog", 0.62f))
        )
        assertEquals("birthday", v.category)
        assertEquals(PhotoPolicy.FOLDER_CONF, v.confidence, 1e-5f)
        assertEquals("the losing claim stays searchable",
            listOf("dog", "birthday", "pet"), tags(v))
    }

    @Test
    fun `a filename outranks its folder`() {
        val v = decision(folder = "Pets", name = "birthday_cake.jpg")
        assertEquals("birthday", v.category)
        assertEquals(PhotoPolicy.NAME_CONF, v.confidence, 1e-5f)
        assertEquals(listOf("birthday", "pet"), tags(v))
    }

    @Test
    fun `empty labels keep the folder's word`() {
        val v = decision(folder = "Wedding")
        assertEquals("wedding", v.category)
        assertEquals(PhotoPolicy.FOLDER_CONF, v.confidence, 1e-5f)
        assertEquals("Wedding • Wedding", v.about)
    }

    @Test
    fun `no evidence at all is honestly unknown`() {
        val v = decision(name = "IMG_1234.jpg")
        assertEquals("unknown", v.category)
        assertEquals(0f, v.confidence, 1e-5f)
        assertEquals("", v.tags)
        assertEquals("", v.about)
    }

    @Test
    fun `a label that speaks the ontology lands in its curated bucket`() {
        val v = decision(labels = listOf(PhotoPolicy.Label("Beach", 0.82f)))
        assertEquals("trip", v.category)
        assertEquals(0.82f, v.confidence, 1e-5f)
        assertEquals(listOf("beach", "trip"), tags(v))
        assertEquals("Beach", v.about)
    }

    @Test
    fun `a raw label carries the category when no bucket exists`() {
        val v = decision(labels = listOf(PhotoPolicy.Label("Dress", 0.90f)))
        assertEquals("dress", v.category)
        assertEquals(listOf("dress"), tags(v))
        assertEquals("Dress", v.about)
    }

    @Test
    fun `a whisper of a label is not evidence`() {
        val v = decision(labels = listOf(PhotoPolicy.Label("Dress", 0.55f)))
        assertEquals("unknown", v.category)
        assertEquals(0f, v.confidence, 1e-5f)
    }

    @Test
    fun `a mush decode cannot promote its label`() {
        val v = decision(
            labels = listOf(PhotoPolicy.Label("Dog", 0.95f)),
            sharpness = 3f
        )
        assertEquals("unknown", v.category)
        assertEquals(0f, v.confidence, 1e-5f)
        assertEquals("", v.tags)
    }

    @Test
    fun `a mush decode still keeps the folder's word`() {
        val v = decision(
            folder = "Birthday",
            labels = listOf(PhotoPolicy.Label("Dog", 0.95f)),
            sharpness = 3f
        )
        assertEquals("birthday", v.category)
        assertEquals(PhotoPolicy.FOLDER_CONF, v.confidence, 1e-5f)
        assertEquals(listOf("birthday"), tags(v))
    }

    @Test
    fun `zero sharpness means unmeasured, not mush`() {
        val v = decision(
            labels = listOf(PhotoPolicy.Label("Beach", 0.82f)),
            sharpness = 0f
        )
        assertEquals("trip", v.category)
        assertEquals(0.82f, v.confidence, 1e-5f)
    }

    @Test
    fun `a unicode folder cannot invent an English category`() {
        val v = decision(folder = "生日的照片", name = "IMG_0001.jpg")
        assertEquals("unknown", v.category)
        assertEquals(0f, v.confidence, 1e-5f)
        assertEquals("", v.tags)
    }

    @Test
    fun `a unicode label survives normalization intact`() {
        val v = decision(labels = listOf(PhotoPolicy.Label("Café", 0.80f)))
        assertEquals("café", v.category)
        assertEquals(listOf("café"), tags(v))
        assertEquals("Café", v.about)
    }

    @Test
    fun `tags stay capped even when every sense has something to say`() {
        val v = decision(
            folder = "Wedding",
            name = "birthday_dinner.jpg",
            labels = listOf(
                PhotoPolicy.Label("Cake", 0.95f),
                PhotoPolicy.Label("Table", 0.90f),
                PhotoPolicy.Label("Dress", 0.85f),
                PhotoPolicy.Label("Chair", 0.80f),
                PhotoPolicy.Label("Wine", 0.75f)
            )
        )
        assertEquals("birthday", v.category)
        assertTrue("no runaway tags: ${v.tags}", tags(v).size <= PhotoPolicy.MAX_TAGS)
    }

    @Test
    fun `an exact phone screen is priced out of the pixel pass`() {
        val junk = PhotoPolicy.junkScore("Saved", "holiday_photo.png", 1080, 2400)
        assertTrue("screen-shaped still scored $junk", junk >= 0.9f)
    }

    @Test
    fun `a screenshot filename is clutter even without pixels`() {
        val junk = PhotoPolicy.junkScore("Saved", "Screenshot_2024-01-01.png", 0, 0)
        assertTrue("screenshot scored $junk", junk >= 0.75f)
    }

    @Test
    fun `a camera-named file is never judged by its shape`() {
        val junk = PhotoPolicy.junkScore("Saved", "IMG_20240101.jpg", 1080, 1920)
        assertEquals(0f, junk, 1e-5f)
    }

    @Test
    fun `a panorama is not a phone screen`() {
        val junk = PhotoPolicy.junkScore("Saved", "holiday_pano.jpg", 8000, 2000)
        assertEquals(0f, junk, 1e-5f)
    }

    @Test
    fun `receipts and scans are utility, not memories`() {
        val junk = PhotoPolicy.junkScore("Saved", "receipt_2024.jpg", 4000, 3000)
        assertTrue("receipt scored $junk", junk >= 0.75f)
    }

    @Test
    fun `Scandinavia is a trip, not a scan`() {
        val junk = PhotoPolicy.junkScore("Trips", "Scandinavia_2023.jpg", 4000, 3000)
        assertEquals(0f, junk, 1e-5f)
    }

    @Test
    fun `an unknown size leaves the folder's price alone`() {
        val junk = PhotoPolicy.junkScore("Screenshots", "IMG_1.jpg", 0, 0)
        assertEquals(0.75f, junk, 1e-5f)
    }
}
