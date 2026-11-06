package com.brain.gallery.ui.organize

import com.brain.gallery.data.local.PhotoEntity
import com.brain.gallery.data.local.VideoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins for person-grouped search results. The grouping decides what the
 * search page says about the library — it must never invent a person, lose a
 * result or caption an empty bucket.
 */
class SearchSectionsTest {

    private fun video(
        id: Long,
        personId: Int = -1,
        faceCount: Int = 1,
        day: Long = 0
    ) = VideoEntity(
        id = id, uri = "content://video/$id", displayName = "clip-$id.mp4",
        durationMs = 30_000, sizeBytes = 0, dateAddedSec = day, folderName = "Camera",
        faceCount = faceCount, personId = personId
    )

    private fun photo(id: Long, day: Long = 0) = PhotoEntity(
        id = id, uri = "content://photo/$id", displayName = "photo-$id.jpg",
        folderName = "Camera", dateAddedSec = day
    )

    private fun v(id: Long, p: Int = -1, faces: Int = 1) =
        SearchHit.VideoHit(video(id, p, faces))

    private fun photoHit(id: Long) = SearchHit.PhotoHit(photo(id))

    @Test
    fun `no results means no sections`() {
        assertEquals(emptyList<ResultSection>(), resultSections(emptyList(), emptyMap()))
    }

    @Test
    fun `one bucket means a flat grid, not a caption`() {
        val hits = listOf(v(1), v(2), v(3))
        val sections = resultSections(hits, emptyMap())
        assertEquals(1, sections.size)
        assertNull(sections[0].title)
        assertEquals(hits, sections[0].hits)
    }

    @Test
    fun `same person stays one flat section when nobody else is present`() {
        val hits = listOf(v(1, 7), v(2, 7), v(3, 7))
        val sections = resultSections(hits, mapOf(7 to "Hesham"))
        assertEquals(1, sections.size)
        assertNull(sections[0].title)
    }

    @Test
    fun `two people earn headers, larger one first`() {
        val hits = listOf(v(1, 7), v(2, 7), v(3, 9), v(4, 9), v(5, 9))
        val sections = resultSections(hits, mapOf(7 to "Hesham", 9 to "Sara"))
        assertEquals(listOf("Sara", "Hesham"), sections.map { it.title })
        assertEquals(listOf(3, 2), sections.map { it.hits.size })
    }

    @Test
    fun `unnamed person says someone, never a blank`() {
        val hits = listOf(v(1, 7), v(2, 8))
        val sections = resultSections(hits, mapOf(7 to "", 8 to "Sara"))
        assertEquals(listOf("Sara", "Someone in particular"), sections.map { it.title })
    }

    @Test
    fun `photos keep their own section, trailing faces`() {
        val hits = listOf(photoHit(1), v(2, 7), v(3, 9))
        val sections = resultSections(hits, mapOf(7 to "Hesham", 9 to "Sara"))
        assertEquals(
            listOf("Hesham", "Sara", "Photos"),
            sections.map { it.title })
        assertEquals("Photos", sections.last().title)
    }

    @Test
    fun `faces without a face trail at the end`() {
        val hits = listOf(v(1), v(2), v(3, 7), v(4, 9))
        val sections = resultSections(hits, mapOf(7 to "Hesham", 9 to "Sara"))
        assertEquals(
            listOf("Hesham", "Sara", "No faces yet"),
            sections.map { it.title })
        assertEquals(listOf(1L, 2L),
            sections.last().hits.map { (it as SearchHit.VideoHit).video.id })
    }

    private fun keyOf(h: SearchHit): String =
        when (h) {
            is SearchHit.VideoHit -> "v_${h.video.id}"
            is SearchHit.PhotoHit -> "p_${h.photo.id}"
        }

    @Test
    fun `every result lands in exactly one section`() {
        val hits = listOf(v(1, 7), v(2), v(3, 9), photoHit(4), v(5, 7), photoHit(6))
        val sections = resultSections(hits, mapOf(7 to "Hesham", 9 to "Sara"))
        val all = sections.flatMap { it.hits }
        assertEquals(hits.size, all.size)
        assertEquals(hits.map(::keyOf).toSet(), all.map(::keyOf).toSet())
    }

    @Test
    fun `order within a section is preserved`() {
        val hits = listOf(v(9, 7), v(8, 7), v(7, 9), v(6, 9), v(5, 7))
        val sections = resultSections(hits, mapOf(7 to "Hesham", 9 to "Sara"))
        val hesham = sections.first { it.title == "Hesham" }
        assertEquals(listOf(9L, 8L, 5L),
            hesham.hits.map { (it as SearchHit.VideoHit).video.id })
    }

    @Test
    fun `unicode person names survive`() {
        val hits = listOf(v(1, 7), v(2, 8))
        val sections = resultSections(hits, mapOf(7 to "حجازي", 8 to ""))
        assertEquals(listOf("حجازي", "Someone in particular"), sections.map { it.title })
    }

    @Test
    fun `photos only stays flat`() {
        val hits = listOf(photoHit(1), photoHit(2))
        val sections = resultSections(hits, emptyMap())
        assertEquals(1, sections.size)
        assertNull(sections[0].title)
        assertEquals(hits, sections[0].hits)
    }

    @Test
    fun `truly empty photos and no faces do not earn headers`() {
        // One named person plus nobody else: flat.
        val hits = listOf(v(1, 7), v(2, 7))
        assertEquals(
            1,
            resultSections(hits, mapOf(7 to "Hesham")).size)
        // But a face and a still stand as two groups, so they earn theirs.
        val mixed = listOf(v(1, 7), photoHit(3))
        val sections = resultSections(mixed, mapOf(7 to "Hesham"))
        assertEquals(listOf("Hesham", "Photos"), sections.map { it.title })
    }

    @Test
    fun `equal person sizes keep discovery order`() {
        val hits = listOf(v(1, 7), v(2, 9), v(3, 8))
        val sections = resultSections(hits,
            mapOf(7 to "A", 8 to "C", 9 to "B"))
        // All size 1 — stable order by map occupation order, which is the
        // order first seen in the results list.
        assertEquals(listOf(7, 9, 8),
            sections.map { s -> (s.hits.first() as SearchHit.VideoHit).video.personId })
    }

    @Test
    fun `empty names never produce an empty header`() {
        val hits = listOf(v(1, 7), v(2, 8))
        val sections = resultSections(hits, mapOf(7 to "", 8 to ""))
        assertTrue(sections.all { !it.title.isNullOrBlank() })
        assertTrue(sections.all { it.title != "" })
    }
}
