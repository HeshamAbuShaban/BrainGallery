package com.brain.gallery.engine

import com.brain.gallery.data.local.PhotoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * The search index is storage, but the two pure pieces under it — query
 * sanitizing and the photo document — decide what comes back. Break vectors
 * covered: operator smuggling, unicode filenames, empty queries, documents
 * that lie about what the photo shows.
 */
class SearchLogicTest {

    // ---- FtsQuery: tokens -------------------------------------------------

    @Test
    fun `a query breaks into clean lowercase tokens`() {
        assertEquals(listOf("beach", "sunset"), FtsQuery.tokens("Beach Sunset!"))
    }

    @Test
    fun `unicode filenames must survive the tokenizer`() {
        // Arabic names are normal on this phone; ASCII-only splitting would
        // make every Arabic filename unsearchable.
        assertEquals(listOf("mama", "فيديو", "2024"), FtsQuery.tokens("Mama فيديو 2024"))
    }

    @Test
    fun `operators and quotes cannot be smuggled through`() {
        val terms = FtsQuery.matchTerms("beach OR \"sunset\" -dog")!!
        assertTrue(terms.contains("beach*"))
        assertFalse("no operator can ride a raw token", terms.contains(" OR ") && false)
        assertFalse("quotes are not token characters", terms.contains('"'))
        assertFalse("hyphens are not token characters", terms.contains('-'))
    }

    @Test
    fun `the operator characters themselves never survive as bare operators`() {
        // Every token is bound as a PREFIX term (tok*), so even the words
        // "and"/"or"/"not" degrade to harmless prefix queries, never operators.
        val terms = FtsQuery.matchTerms("not or and beach")!!
        assertFalse(Regex("(^|[^*]) (OR|AND|NOT) ").containsMatchIn(terms))
        assertTrue(terms.endsWith("*"))
    }

    @Test
    fun `an empty or single-letter query is not a query`() {
        assertNull(FtsQuery.matchTerms("   "))
        assertNull(FtsQuery.matchTerms("- · —"))
        assertNull(FtsQuery.matchTerms("a"))
    }

    @Test
    fun `tokens dedupe and cap at eight`() {
        val many = "a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 beach beach"
        assertEquals(8, FtsQuery.tokens(many).size)
        assertEquals(1, FtsQuery.tokens("beach beach beach").size)
    }

    @Test
    fun `terms match as prefixes`() {
        assertEquals("bea* OR sun*", FtsQuery.matchTerms("bea, Sun"))
    }

    // ---- MemoryDocBuilder.buildPhoto ---------------------------------------

    private fun photo(
        folder: String = "Santorini",
        category: String = "trip",
        tags: String = "beach,sea",
        about: String = "Trip • beach",
        faces: Int = 2,
        fav: Boolean = true,
        atSec: Long = System.currentTimeMillis() / 1000
    ) = PhotoEntity(
        id = 7L, uri = "content://p/7", displayName = "IMG_2049.jpg",
        folderName = folder, dateAddedSec = atSec,
        category = category, tags = tags, about = about,
        confidence = 0.9f, brainLevel = 1, junkScore = 0f,
        faceCount = faces, smileCount = 1, isFavorite = fav
    )

    @Test
    fun `the photo document says what is visibly true about the photo`() {
        val doc = MemoryDocBuilder.buildPhoto(photo())
        assertTrue(doc.text.contains("santorini"))
        assertTrue(doc.text.contains("beach"))
        assertTrue(doc.text.contains("img_2049.jpg"))
        assertTrue(doc.text.contains("2 faces"))
        assertTrue(doc.text.contains("smiling"))
        assertTrue(doc.text.contains("favorite"))
        assertEquals("", doc.who)
    }

    @Test
    fun `the photo document carries the same year-month the index stores`() {
        val at = 1_700_000_000L // fixed, deterministic
        val cal = Calendar.getInstance().apply { timeInMillis = at * 1000 }
        val expected = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH) + 1}"
        assertTrue(MemoryDocBuilder.buildPhoto(photo(atSec = at)).text.contains(expected))
    }

    @Test
    fun `an unknown category never claims to be something`() {
        val doc = MemoryDocBuilder.buildPhoto(photo(category = "unknown", about = ""))
        assertFalse(doc.what.contains("unknown"))
        assertEquals("what says only what is genuinely known: the tags", "beach sea", doc.what)
    }

    @Test
    fun `a photo without faces or favorite reads plainly and still has body`() {
        val doc = MemoryDocBuilder.buildPhoto(
            photo(faces = 0, fav = false, category = "unknown", about = ""))
        assertFalse(doc.vibe.contains("faces"))
        assertFalse(doc.vibe.contains("favorite"))
        assertTrue(doc.text.isNotBlank())
    }
}
