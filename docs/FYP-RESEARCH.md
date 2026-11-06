# FYP research — the TikTok question (queued for a future sitting)

Source links, kept exactly as given:

- <https://www.reddit.com/r/antiai/comments/1xo2bic/a_developer_reverseengineered_tiktok_and_says_it/>
  A write-up titled (reader's words): a developer reverse-engineered TikTok
  and explains what makes the For You page work. Reddit returned nothing for
  the unauthenticated fetch at the time of this note — the content itself is
  to be retrieved in the research sitting, not the design read.
- <https://rea.tools/> — REA, the reverse-engineering agent suite, and its
  product site. The session already has an MCP build of it available and the
  user has used an older one as an MCP server before; the tool surface in
  this project includes opening/decompiling APK artifacts, decompiling to
  pseudocode, strings/symbols queries and an ontology over the whole binary.
- <https://github.com/morluto/rea> — the source repository for REA.

## The ambition, in the owner's words

"A real Redis-like cache… the FYP algorithm… boost the algorithm of my feed
and find." Operationalized: study how TikTok's For You ranking is built —
candidate generation → feature computation → selection → re-ranking — and
carry over the *ideas*, with honest mappings onto the pieces this app owns:

- `FeedComposer` (the ranking), `TasteProfile` (evidence with decaying
  weights), `FeedTuning`, `EngineSettings`, the reason phrases on every card
- The guards our constitution sets: on-device, no network, explainable
  ranking, nothing distributed either way

Nothing of TikTok's code gets copied, linked or redistributed. This is
black-box/static study of a public artifact for competitive understanding,
then an upgrade of our own files with our own evidence — the same rigor the
pg, Content ID and PhotoPolicy work already had.

## The work plan for the sitting

1. Acquire the TikTok APK for study (the artifact, not the store listing).
2. With REA on the APK: find the FYP candidate pipeline,
   the feature names on the selection stage (watch time, rewatches,
   negative/positive signals), the ranking model shape, the filtering
   stages, and the cold-start path.
3. Write what is actually found — not the folklore — into this document,
   with the artifact's provenance and the limits of the reading.
4. Translate each finding onto an existing BrainGallery component:
   CandidateComposer, TasteProfile, the reason phrases, the replay
   demotion, the 80/20 explore band, the negative-signal path.
   Where the app lacks a mechanism, design one that is on-device,
   tunable and explainable, and add tests.
5. Upgrade all corresponding files to spec — tests green, no stubs.
6. Long-running pieces may run as background subagents; the sitting's
   constitution still applies (compile + test gates green, commit after
   each finished phase, the working tree left clean).
7. Close on this file: record what was verified and what was assumed.

## Status

Queued, untouched. The sitting starts when the owner calls the turn — and
REA's cost is the model's context, so the heavy decompiles go to
subagents while the main line returns to the mission at hand.

## Status log

- 2026-11-06 — queued per the owner's instruction; references recorded;
  no acquisition attempted. The sitting runs as a mode of long-running
  agents when the owner calls it; the main line stays on the app mission.
