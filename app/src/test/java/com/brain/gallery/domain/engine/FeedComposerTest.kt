package com.brain.gallery.domain.engine

import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.data.local.WatchEventEntity
import com.brain.gallery.domain.EngineSettings
import com.brain.gallery.domain.FeedTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The ranking is the product here, so these tests pin its behavior the way the
 * unit-testing discipline asks: one angle each, break vectors spelled out.
 *
 * Break vectors covered: junk leaking past the cut, suppressed clips returning,
 * the reel ignoring its own length, weights that do nothing, a preset that
 * can't outrank what the brain knows, reasons that lie, settings that
 * forget their weights across a save.
 */
class FeedComposerTest {

    private val composer = FeedComposer()

    private fun video(
        id: Long,
        junk: Float = 0f,
        fav: Boolean = false,
        watched: Boolean = false,
        completion: Float = 0.9f,
        ageDays: Long = 1,
        category: String = "family",
        faces: Int = 0,
        smiles: Int = 0,
        confidence: Float = 1f,
        prosody: String = ""
    ) = VideoEntity(
        id = id, uri = "uri://$id", displayName = "v$id", durationMs = 30_000,
        sizeBytes = 1, dateAddedSec = System.currentTimeMillis() / 1000 - ageDays * 86_400,
        folderName = "f", junkScore = junk, isFavorite = fav,
        watchCount = if (watched) 10 else 0,
        completionSum = if (watched) completion * 10 else 0f,
        category = category, faceCount = faces, smileCount = smiles, confidence = confidence,
        prosodyTags = prosody
    )

    // ---- pool rules ---------------------------------------------------------

    @Test
    fun `junk at or above the cut never enters the reel`() {
        val clips = listOf(video(1, junk = 0.84f), video(2, junk = 0.85f), video(3, junk = 0.95f))
        val out = composer.compose(clips, emptyList(), tuning = FeedTuning(junkCut = 0.85f), random = Random(7))
        assertEquals(listOf(1L), out.map { it.video.id })
    }

    @Test
    fun `lowering the cut lets borderline clips back in`() {
        val clips = listOf(video(1, junk = 0.85f))
        val out = composer.compose(clips, emptyList(), tuning = FeedTuning(junkCut = 0.90f), random = Random(7))
        assertEquals(1, out.size)
    }

    @Test
    fun `suppressed ids never surface even with a fresh nonce`() {
        val clips = listOf(video(1), video(2))
        val out = composer.compose(clips, emptyList(), suppressed = setOf(1L), random = Random(7))
        assertFalse(out.any { it.video.id == 1L })
    }

    @Test
    fun `the reel respects its length`() {
        val clips = (1L..200).map { video(it) }
        val out = composer.compose(clips, emptyList(), tuning = FeedTuning(feedLength = 30), random = Random(7))
        assertTrue("expected <= 30, got ${out.size}", out.size <= 30)
    }

    @Test
    fun `an empty library composes nothing and crashes nothing`() {
        assertTrue(composer.compose(emptyList(), emptyList()).isEmpty())
    }

    // ---- weights actually move the ranking ----------------------------------

    @Test
    fun `replay demotion at zero lets a just-watched clip win`() {
        val watched = video(1, watched = true, completion = 0.95f)
        val fresh = video(2)
        val forgiving = FeedTuning(replayPenalty = 0f)
        val a = composer.score(watched, recentIds = setOf(1L), hour = 12, t = forgiving)
        val b = composer.score(fresh, recentIds = setOf(1L), hour = 12, t = forgiving)
        assertTrue("watched ($a) should outrank fresh ($b) with no penalty", a.score > b.score)
    }

    @Test
    fun `replay demotion at full sinks a just-watched clip`() {
        val watched = video(1, watched = true, completion = 0.95f)
        val fresh = video(2)
        val harsh = FeedTuning(replayPenalty = 1f)
        val a = composer.score(watched, recentIds = setOf(1L), hour = 12, t = harsh)
        val b = composer.score(fresh, recentIds = setOf(1L), hour = 12, t = harsh)
        assertTrue("fresh ($b) should outrank just-watched ($a)", b.score > a.score)
    }

    @Test
    fun `zeroed people weight ignores faces completely`() {
        val withFaces = video(1, faces = 3, smiles = 3)
        val tOff = FeedTuning(peopleWeight = 0f)
        val tOn = FeedTuning(peopleWeight = 1f)
        val off = composer.score(withFaces, emptySet(), 12, tOff)
        val on = composer.score(withFaces, emptySet(), 12, tOn)
        assertTrue(on.score > off.score)
    }

    @Test
    fun `nostalgic preset lifts a buried gem above a plain clip`() {
        val gem = video(1, ageDays = 400)
        val plain = video(2, ageDays = 2, watched = true, completion = 0.5f)
        val nostalgic = FeedTuning.PRESETS.getValue("Nostalgic")
        val g = composer.score(gem, emptySet(), 12, nostalgic)
        val p = composer.score(plain, emptySet(), 12, nostalgic)
        assertTrue("gem ($g) should outrank plain ($p) when nostalgic", g.score > p.score)
    }

    @Test
    fun `a preset never outranks certainty the brain lacks`() {
        // Even the strongest nostalgic lift cannot rescue a clip the brain
        // knows nothing about, if trust-the-brain is at its default.
        val unknownGem = video(1, ageDays = 400, confidence = 0f)
        val certainPlain = video(2, ageDays = 2, watched = true, completion = 0.95f, confidence = 1f)
        val nostalgic = FeedTuning.PRESETS.getValue("Nostalgic").copy(noveltyWeight = 0f)
        val g = composer.score(unknownGem, emptySet(), 12, nostalgic)
        val p = composer.score(certainPlain, emptySet(), 12, nostalgic)
        assertTrue("certain ($p) should outrank unknown ($g)", p.score > g.score)
    }

    // ---- the feed explains itself -------------------------------------------

    @Test
    fun `a favorite says so`() {
        val out = composer.compose(listOf(video(1, fav = true)), emptyList(), random = Random(7))
        assertTrue(out.first().reason.contains("favorite"))
    }

    @Test
    fun `a buried gem says how long it slept`() {
        val out = composer.compose(listOf(video(1, ageDays = 400)), emptyList(), random = Random(7))
        assertTrue(out.first().reason.contains("buried"))
    }

    @Test
    fun `a plain clip still gets an honest reason`() {
        val out = composer.compose(listOf(video(1)), emptyList(), random = Random(7))
        assertNotEquals("", out.first().reason)
    }

    @Test
    fun `a talking clip rises with the voices weight and says so`() {
        val talky = video(1, prosody = "speaking, expressive")
        val on = composer.score(talky, emptySet(), 12, FeedTuning(voiceWeight = 1f))
        val off = composer.score(talky, emptySet(), 12, FeedTuning(voiceWeight = 0f))
        assertTrue("voice weight should move the score", on.score > off.score)
        assertTrue(on.reason.contains("talking"))
    }

    @Test
    fun `music or ambience never earns the talking lift`() {
        val music = video(1, prosody = "mostly music or ambience")
        val off = composer.score(music, emptySet(), 12, FeedTuning(voiceWeight = 0f))
        val on = composer.score(music, emptySet(), 12, FeedTuning(voiceWeight = 2f))
        assertEquals(off.score, on.score, 0.0001f)
    }

    @Test
    fun `engine settings carry their tuning through a save`() {
        val cfg = EngineSettings(
            mood = "Custom",
            tuning = FeedTuning(energyWeight = 1.7f, voiceWeight = 0.3f,
                feedLength = 99, replayPenalty = 0.1f)
        )
        val back = EngineSettings.fromJson(cfg.toJson())
        assertEquals("Custom", back.mood)
        assertEquals(1.7f, back.tuning.energyWeight, 0.0001f)
        assertEquals(0.3f, back.tuning.voiceWeight, 0.0001f)
        assertEquals(99, back.tuning.feedLength)
        assertEquals(0.1f, back.tuning.replayPenalty, 0.0001f)
    }

    @Test
    fun `old settings without a tuning blob load with defaults`() {
        val legacy = EngineSettings(mood = "Hype").toJson()
        val back = EngineSettings.fromJson(legacy)
        assertEquals("Hype", back.mood)
        assertEquals(FeedTuning(), back.tuning)
    }

    @Test
    fun `every preset is a complete weights hand`() {
        assertEquals(5, FeedTuning.PRESETS.size)
        FeedTuning.PRESETS.values.forEach { t ->
            assertTrue(t.feedLength in 10..300)
            assertTrue(t.junkCut in 0.5f..0.95f)
            assertTrue(t.bandSize in 4..60)
        }
    }
}
