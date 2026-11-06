package com.brain.gallery.ui.organize

/**
 * One slice of a searched library, person by person. The grid draws these in
 * order; a null title means "no header — flat grid", which is what a single
 * bucket earns, since a header over its own contents is a caption and not a
 * grouping.
 */
data class ResultSection(val title: String?, val hits: List<SearchHit>)

/** The honest fallback for a face nobody has named yet. */
private const val UNNAMED_TITLE = "Someone in particular"

/**
 * Groups search results by the person the pixels know.
 *
 * The library knows who is in a frame far more confidently than it knows what
 * anything is: names are hand-confirmed, categories are guesses. So when a
 * search lands more than one named face, the grid says so; everything with no
 * face to attribute goes at the end under an honest label, and stills get
 * their own trailing section rather than pretending to be someone.
 *
 * Photos carry no person, so they keep their own section; clips without a
 * person trail at the very end. A single bucket collapses back to a flat grid —
 * one section is not a grouping, it is a caption.
 *
 * Order: most clips first; a named face outranks the nameless fallback; ties
 * keep the order results arrived in.
 */
fun resultSections(
    results: List<SearchHit>,
    personNames: Map<Int, String>
): List<ResultSection> {
    if (results.isEmpty()) return emptyList()
    val byPerson = linkedMapOf<Int, MutableList<SearchHit>>()
    val noFaces = mutableListOf<SearchHit>()
    val photos = mutableListOf<SearchHit>()
    for (hit in results) when (hit) {
        is SearchHit.VideoHit ->
            if (hit.video.personId >= 0) {
                byPerson.getOrPut(hit.video.personId) { mutableListOf() }.add(hit)
            } else {
                noFaces.add(hit)
            }
        is SearchHit.PhotoHit -> photos.add(hit)
    }
    val named = byPerson.entries
        .map { (pid, hits) ->
            // The person the pixels know who no one has renamed yet. Skipping
            // the bucket would hide their clips; naming them blank teaches no
            // word — "Someone" says exactly the truth.
            val name = personNames[pid]?.trim().orEmpty()
            ResultSection(name.ifEmpty { UNNAMED_TITLE }, hits)
        }
        .sortedWith(
            compareByDescending<ResultSection> { it.hits.size }
                .thenBy { it.title == UNNAMED_TITLE }
        )
    val out = buildList {
        addAll(named)
        if (photos.isNotEmpty()) add(ResultSection("Photos", photos))
        if (noFaces.isNotEmpty()) add(ResultSection("No faces yet", noFaces))
    }
    return if (out.size >= 2) out else listOf(ResultSection(null, results))
}
