package com.brain.gallery.domain.engine

import com.brain.gallery.data.local.VideoEntity
import com.brain.gallery.data.local.WatchEventEntity
import com.brain.gallery.domain.FeedTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The taste profile teaches the ranking, so these tests guard the two pure
 * halves: what watching folds into the notebook, and how far the notebook
 * is allowed to move the reel.
 *
 * Break vectors covered: one accidental watch becoming a verdict, skip
 * evidence ignored, the ambivalent middle teaching something, an unknown
 * clip reading neutral, obsessions that never fade, a serialized profile
 * that forgets itself, a zeroed weight that still moves scores.
 */
class TasteProfileTest {

    private val composer = FeedComposer()

    private fun video(
        id: Long = 1,
        category: String = "family",
        personId: Int = -1,
        folder: String = "Camera",
        durationMs: Long = 30_000,
        ageDays: Long = 1
    ) = VideoEntity(
        id = id, uri = "uri://$id", displayName = "v$id", durationMs = durationMs,
        sizeBytes = 1, dateAddedSec = System.currentTimeMillis() / 1000 - ageDays * 86_400,
        folderName = folder,
        category = category, personId = personId
    )

    private fun event(
        videoId: Long = 1,
        completion: Float,
        atMs: Long = 1_700_000_000_000
    ) = WatchEventEntity(rowId = 1, videoId = videoId, atMs = atMs,
        completion = completion, skipped = completion < 0.2f, hourOfDay = 12)

    // ---- folding: what watching teaches -------------------------------------

    @Test
    fun `finishing a kind of clip raises its affinity, unknown kinds stay neutral`() {
        val p = TasteProfile().fold(
            listOf(event(videoId = 1, completion = 0.95f)), mapOf(1L to video(1)), 1L)
        assertTrue("watched kind should rise", p.affinity("cat:family") > 0.5f)
        assertEquals("never-touched kind stays neutral", 0.5f, p.affinity("cat:sport"), 1e-5f)
    }

    @Test
    fun `skipping early is a vote against, and it is heard`() {
        val p = TasteProfile().fold(
            listOf(event(videoId = 1, completion = 0.05f)), mapOf(1L to video(1)), 1L)
        assertTrue("skip should sink the kind", p.affinity("cat:family") < 0.5f)
    }

    @Test
    fun `one watch can never become certainty`() {
        // The shrinkage is the honesty: a single accidental finishing of a
        // cat video must not hand the reel to cats.
        val p = TasteProfile().fold(
            listOf(event(videoId = 1, completion = 1f)), mapOf(1L to video(1)), 1L)
        assertTrue("1 like = ${p.affinity("cat:family")}, must stay shy of 0.75",
            p.affinity("cat:family") < 0.75f)
    }

    @Test
    fun `the ambivalent middle teaches nothing`() {
        val p = TasteProfile().fold(
            listOf(event(videoId = 1, completion = 0.45f)), mapOf(1L to video(1)), 1L)
        assertTrue("half-watches must not count", p.buckets.isEmpty())
    }

    @Test
    fun `a profile that knows nothing reads exactly neutral, whatever the clip is`() {
        // No watching yet — even a clip wearing every category, person and
        // folder in the world must score dead-center.
        assertEquals(0.5f, TasteProfile().affinityFor(video()), 1e-5f)
        assertEquals(0.5f,
            TasteProfile().affinityFor(video(id = 2, personId = 3, folder = "X")), 1e-5f)
    }

    @Test
    fun `every attribute a clip carries gets its vote`() {
        val me = video(id = 2, personId = 3, folder = "Private", durationMs = 10_000)
        val p = TasteProfile().fold(
            listOf(event(videoId = 2, completion = 1f), event(videoId = 2, completion = 1f)),
            mapOf(2L to me), 1L)
        assertTrue("person lifted", p.affinity("person:3") > 0.5f)
        assertTrue("folder lifted", p.affinity("folder:Private") > 0.5f)
        assertTrue("length lifted", p.affinity("dur:short") > 0.5f)
    }

    @Test
    fun `repetition beats accident`() {
        val six = TasteProfile().fold(
            (1L..6L).map { event(videoId = it, completion = 1f) },
            (1L..6L).associateWith { video(it) }, 2L)
        val one = TasteProfile().fold(
            listOf(event(videoId = 1, completion = 1f)), mapOf(1L to video(1)), 2L)
        assertTrue("habit (${six.affinityFor(video())}) must outrank accident (${one.affinityFor(video())})",
            six.affinityFor(video()) > one.affinityFor(video()))
    }

    // ---- fading: taste is a moving thing ------------------------------------

    @Test
    fun `an obsession fades by half-life`() {
        val fan = TasteProfile().fold(
            (1L..60).map { event(videoId = it.toLong(), completion = 1f) },
            (1L..60).associate { it.toLong() to video(it.toLong()) }, 1L)
        assertTrue(fan.affinity("cat:family") > 0.68f)
        val faded = fan.decayed(42 * 86_400_000L) // two half-lives in
        assertTrue("two half-lives in must loosen the grip, got ${faded.affinity("cat:family")}",
            faded.affinity("cat:family") < fan.affinity("cat:family"))
    }

    @Test
    fun `dead buckets disappear instead of haunting the reel`() {
        val p = TasteProfile(buckets = mapOf("cat:x" to TasteProfile.Bucket(3f, 1f, 0L)))
        val gone = p.decayed(840 * 86_400_000L) // forty half-lives: dust
        assertTrue(gone.buckets.isEmpty())
    }

    // ---- persistence ---------------------------------------------------------

    @Test
    fun `the profile survives a json roundtrip`() {
        val p = TasteProfile().fold(
            listOf(event(videoId = 1, completion = 1f), event(videoId = 2, completion = 0.1f)),
            mapOf(1L to video(1), 2L to video(2, category = "sport")), 1L)
        val back = TasteProfile.fromJson(p.toJson())
        assertEquals(
            p.buckets.mapValues { it.value.copy(lastSeenMs = it.value.lastSeenMs) },
            back.buckets)
        assertEquals(0.5f, back.affinity("cat:nature"), 1e-5f)
    }

    @Test
    fun `garbage and blank blobs load as the empty, neutral profile`() {
        assertEquals(0.5f, TasteProfile.fromJson(null).affinity("cat:family"), 1e-5f)
        assertEquals(0.5f, TasteProfile.fromJson("").affinity("cat:family"), 1e-5f)
        assertEquals(0.5f, TasteProfile.fromJson("{\"buckets ta jkl}").affinity("cat:family"), 1e-5f)
    }

    // ---- the composer obeys the notebook and the weight ----------------------

    private fun tasteFor(category: String, likes: Int): TasteProfile =
        TasteProfile().fold(
            (1L..likes).map { event(videoId = it, completion = 1f) },
            (1L..likes).associate { it to video(it.toLong(), category = category) }, 1L)

    @Test
    fun `the learned profile moves the ranking when the weight is on`() {
        val zebra = video(1, category = "wildlife")
        val on = composer.score(zebra, emptySet(), 12,
            FeedTuning(tasteWeight = 1f), tasteFor("wildlife", 12))
        val control = composer.score(zebra, emptySet(), 12, FeedTuning(tasteWeight = 1f))
        assertTrue("taste should lift (${on.score} vs ${control.score})", on.score > control.score)
    }

    @Test
    fun `a zeroed taste weight makes the profile a no-op`() {
        val zebra = video(1, category = "wildlife")
        val withTaste = composer.score(zebra, emptySet(), 12, FeedTuning(tasteWeight = 0f), tasteFor("wildlife", 12))
        val plain = composer.score(zebra, emptySet(), 12, FeedTuning(tasteWeight = 0f), TasteProfile())
        assertEquals("zero weight must silence taste entirely", plain.score, withTaste.score, 0.0001f)
    }

    @Test
    fun `a disliked kind of clip sinks below neutral taste`() {
        val snake = video(1, category = "wildlife")
        val pushedAway = TasteProfile().fold(
            (1L..10L).map { event(videoId = it, completion = 0.05f) },
            (1L..10).associate { it.toLong() to video(it.toLong(), category = "wildlife") }, 1L)
        val on = composer.score(snake, emptySet(), 12, FeedTuning(tasteWeight = 1f), pushedAway)
        val control = composer.score(snake, emptySet(), 12, FeedTuning(tasteWeight = 1f), TasteProfile())
        assertTrue("dislike should sink (${on.score} vs ${control.score})", on.score < control.score)
    }

    @Test
    fun `strong taste earns its own honest reason`() {
        val liked = TasteProfile().fold(
            (1L..12).map { event(videoId = it.toLong(), completion = 1f) },
            (1L..12).associate { it.toLong() to video(it.toLong()) }, 1L)
        val why = composer.score(video(1), emptySet(), 12, FeedTuning(), liked).reason
        assertTrue("reason was '$why'", why.contains("keep watching"))
    }

    @Test
    fun `weak taste never claims a reason it did not earn`() {
        val thin = TasteProfile().fold(
            listOf(event(videoId = 1, completion = 1f)), mapOf(1L to video(1)), 1L)
        val why = composer.score(video(1), emptySet(), 12, FeedTuning(), thin).reason
        assertFalse("reason was '$why'", why.contains("keep watching"))
    }
}
