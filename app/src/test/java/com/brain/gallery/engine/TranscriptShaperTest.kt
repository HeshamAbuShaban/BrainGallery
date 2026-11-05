package com.brain.gallery.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shaper's language is what a recogniser actually emits — including its
 * junk. Pins: collapse, residue, the whole-word cap, and the honesty gate.
 */
class TranscriptShaperTest {

    @Test
    fun `null and blank collapse to nothing`() {
        assertEquals("", TranscriptShaper.shape(null))
        assertEquals("", TranscriptShaper.shape("   "))
        assertFalse(TranscriptShaper.usable(null))
        assertFalse(TranscriptShaper.usable(""))
    }

    @Test
    fun `recognizer residue is never mistaken for words`() {
        // What an engine writes down when it heard silence or noise.
        assertFalse(TranscriptShaper.usable("…"))
        assertFalse(TranscriptShaper.usable("..."))
        assertFalse(TranscriptShaper.usable(","))
        assertFalse(TranscriptShaper.usable("- — -"))
        // One word is a keynote, not a transcript: the gate wants matter.
        assertFalse(TranscriptShaper.usable("ab"))
    }

    @Test
    fun `whitespace collapses to one honest line`() {
        assertEquals("hello there world", TranscriptShaper.shape("hello  there\n\nworld"))
        assertEquals("one", TranscriptShaper.shape("  one  \n"))
    }

    @Test
    fun `a too-long line cuts at the last whole word`() {
        val many = List(200) { "word" }.joinToString(" ")
        val shaped = TranscriptShaper.shape(many)
        assertTrue(shaped.length <= TranscriptShaper.CAP)
        // Cut on a boundary, never mid-word.
        assertTrue(shaped.endsWith("word"))
        assertFalse(shaped.endsWith("word "))
        // Idempotent: the stored line re-shapes to itself.
        assertEquals(shaped, TranscriptShaper.shape(shaped))
    }

    @Test
    fun `a line that fits mostly survives the boundary`() {
        val long = "x".repeat(TranscriptShaper.CAP)
        assertEquals(long, TranscriptShaper.shape(long))
        // One over: falls back to a hard cut when there is no space to cut on.
        val over = "y".repeat(TranscriptShaper.CAP + 40)
        assertEquals(TranscriptShaper.CAP, TranscriptShaper.shape(over).length)
    }

    @Test
    fun `real speech passes through untouched`() {
        assertEquals("happy birthday to you",
            TranscriptShaper.shape(" happy birthday to you "))
        assertTrue(TranscriptShaper.usable("happy birthday to you"))
        assertTrue(TranscriptShaper.usable("let's go home now"))
    }

    @Test
    fun `the gate counts matter not length alone`() {
        // Three letter-characters is the floor — a real word or two.
        assertTrue(TranscriptShaper.usable("yes"))
        assertFalse(TranscriptShaper.usable("a."))
        stillNonLatin()
    }

    private fun stillNonLatin() {
        // The phone may transcribe in any script; the gate must not be Latin.
        assertTrue(TranscriptShaper.usable("Дома никого нет"))
        assertFalse(TranscriptShaper.usable("।।।"))
    }
}
