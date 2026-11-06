# Roadmap: restructuring BrainGallery

*Written 2026-10-27 after the full feature/IA audit and the skills/design
research. This is the working plan; phase order is bottom-up. Anything marked
✅ shipped in the Oct 27 wave.*

## Where the app stands

Solid and finished: video scanning cascade (L0 → L1a → L1b → prosody), person
identity with merge/split repair, duplicate finding with keeper reasons,
grouping (10 kinds), search via MemoryDoc, the spotlight overlay player, the
glass kit (nav + dialogs), export of the memory bundle, battery-exemption
recovery.

Half-baked or buried (the honest list):

- Import of the memory bundle — `MemoryBundle.importJson()` exists, no UI, no
  call site. Export without restore is half a feature.
- Settings had no real home — reached through a bug-report icon; the feed was
  "tuned" by a single fake "monthly mood" dropdown. ✅ **Tune tab shipped**;
  BrainScreen keeps engine/diagnostics.
- The feed never explained itself (README promised "why this ranked"). ✅
  **✦ reason line shipped** on reel cards.
- Dead code: `DiagnosticsScreen.kt`, `MovePersonDialog.kt` (unreferenced),
  `GlassSettingsScreen.kt.bad` (root-locked, unreadable), three orphan glass
  stubs (`GlassTheme.kt`, `AppleSpecular.kt`, `GlassWiggle.kt`) — the design
  research rejects them; delete when confirmed.
- Built-but-unused glass widgets: `GlassMenu` (OrganizeScreen still paints an
  M3 `DropdownMenu` glass-colored by hand), `GlassCard`, `GlassChip`,
  `GlassStatTile`, `GlassSurface`.
- `semantic_vectors` table: schema only, never written.
- Prosody tags surface in a details dialog but don't affect anything.
- Zero user albums (manual groups are free-text strings riding the category
  slot).
- Video-only scanning (README "Gate 2: any-file support" is aspirational).

## Phase 0 — ✅ The feed becomes the user's (shipped Oct 27)

- `FeedTuning`: 14 explicit weights/knobs; zero hidden constants left in
  `FeedComposer`.
- Presets (`Open/Nostalgic/Hype/Calm/Funny`) are weight snapshots; touching
  any slider flips to `Custom`, which is the honest state.
- Tune tab (4th destination): presets + 8 weight sliders + reel shape
  (length, replay demotion, variety, surprise, clutter cut) + reset.
- Every reel card shows its top ranking signals (✦ line).
- `FeedComposerTest`: 16 tests pinning pool rules, weight effects, reasons,
  JSON round-trip (first tests in the repo).
- Skills merged into `.agents/skills/` (9 adopted + `braingallery` router);
  `AGENTS.md` at root; `docs/DESIGN-RESEARCH.md` for the UI direction.

## Phase 1 — ✅ Navigation becomes real (shipped Nov 1)

Done: `androidx.navigation:navigation-compose` (2.8.5) with one NavHost in
`MainActivity`; route table in `ui/Routes.kt` (feed · organize ·
organize/group/{groupId} · search · tune · brain); tab switches use the
canonical pop-to-start + `saveState`/`restoreState` pattern so each tab's
state survives; GroupDetail and BrainScreen are stacked destinations with
real back-stack behavior (group → organize → exit); the glass pill stays the
visual, now driven by the back stack; Motion-token slide/fade transitions
direction-aware by route rank; the spotlight stays an app-level overlay.

Two deliberate deviations (upgrade paths, not oversights):

- **String routes, not `@Serializable` type-safe routes** — that needs the
  kotlinx-serialization plugin for exactly one string argument today. Revisit
  when a third argument appears.
- **ViewModels stay activity-scoped** (created in `Root`, passed down), same
  as the pre-NavHost shell — flipping to per-destination scoping now would
  fork the organize state between hub and detail. Phase 1b: per-destination
  ViewModels with SavedStateHandle (see `android-presentation-mvi`).

## Phase 2 — Finish the half-built features (one definition of done each)

1. **Bundle import.** ✅ Shipped (Oct 27): settings row + document picker +
   result feedback in BrainScreen; bundles v2 now carry `EngineSettings` (feed
   weights included) and `group_overrides`; import restores both (older v1
   bundles still load). Done when a wiped device reconstructs itself — the
   restore side is now reachable; a wipe-and-restore test on a real device
   remains.
2. **Adopt the glass widgets.** OrganizeScreen's hand-painted menu →
   `GlassMenuOverlay`; stats strips → `GlassStatTile` with big numerals
   (design research: oversized numerals). Done when `ui/glass` has no
   unused exports.
3. **Delete the dead code.** `DiagnosticsScreen.kt`, `MovePersonDialog.kt`,
   orphan glass stubs, `.bad` file (needs `sudo rm` — it is root-locked).
4. **Prosody earns its keep.** ✅ Shipped (Oct 27): `voiceWeight` in
   `FeedTuning` + a Voices slider in Tune; clips the voice layer heard
   speaking get a lift with reason "someone talking"; "mostly music or
   ambience" never earns it. Remaining: voice tag search/filter.
5. **User albums.** ✅ Shipped (Nov 1): `albums` table + `videos.albumId`
   (migration v7→v8, additive); "New album" in the hub header; the selection
   bar gains an "Album" action (pick existing, or create-and-fill in one
   gesture); albums render as groups the engine never reorders, rename via
   the existing override system, and delete leaves their clips alone. The
   `manual_<name>` hack now has a real home to graduate to.
6. **Search upgrades.** Filter chips (people / category / folder / time),
   results grouped by person, and prosody tag search ("find clips where
   someone is emphatic").
7. **On-this-day surface.** A dedicated card/page in Organize, not just a
   scoring signal.

## Phase 3 — File-type coverage (Gate 2, the big one)

Design direction: **one more entity per medium, one scanner cascade per
medium, shared brain.** Do NOT shoehorn photos into `VideoEntity`.

- **3a. Photos** ✅ Shipped (Nov 1): `photos` table + `PhotoDao` (migration
  v8→v9, own table — a photo is not a degenerate video); `PhotoScanner`
  (MediaStore.Images, L0 tokens in the scanner itself); `PhotoAnalyzer`
  (one 512px decode, FaceAnalyzer + ML Kit labels, 40/run budget) wired into
  the service after identity, with its own sweep and reschedule accounting;
  permissions incl. 34+ partial access; the hub gains a Videos/Photos switch
  with a plain photo grid and a mechanical full-screen viewer (favorite +
  delete-with-consent). Deferred, deliberately: ~~photos in search~~ ✅
  Shipped (Nov 5, FTS index + `SearchHit` mixed results — see below),
  photos in albums (albums are video shelves today), and the "memories
  mix" reel toggle (a reel is motion; mixing stills means a second page
  type on the sacred surface — deserves its own sitting).
- **3b. GIFs** ✅ Shipped (Nov 5, second sitting): `photos.animated` (L0 is the
  only pass that needs to know — MIME + extension is the whole verdict), and
  the viewer branches to `ImageDecoder.decodeDrawable` +
  `AnimatedImageDrawable` inside an `AndroidView`, decode-and-play, guarded
  `SDK ≥ 28`. No new Coil artifact was possible in an offline sitting and none
  is wanted: ImageDecoder is platform, permissionless and already there. The
  grid stays a still frame — opening one is the moment it moves.
- **3c. Audio**: `MediaStore.Audio`; reuse `AudioProbe` (prosody) for music
  vs speech tagging; a simple player sheet; searchable by voice tags.
- **3d. Documents/PDF**: MediaStore Downloads + manual picker; filename +
  (later) text extraction into MemoryDoc; a document browser screen.
- Estimated order: 3a → 3b in one pass; 3c and 3d independent, any time after.

## A new sense and a new question (Nov 5, second sitting)

Two features that share one honest engine idea — *the query is evidence, not
a description* — shipped together with a storage migration (v11 → v12, one
batch: `photos.animated`, `photos.phash`, `videos.transcript`,
`videos.transcriptTried`):

- **Content ID — "Shazam for your own library"** (`engine/ContentId.kt`,
  `data/brain/ContentHasher.kt`, route + banner in `SearchScreen.kt`).
  Someone sends you a clip; you cannot name it. Pick it from the system
  photo picker (no storage permission, nothing copied) and the brain
  fingerprints it — three frames for video (0.15/0.5/0.85 of duration, so a
  trim cannot fool the ends), one decode for images — in the same dHash
  dialect every library item already speaks. Matches rank by hamming
  distance against every fingerprint in the library, keeper bar ≤ 6
  (identical meaning to DuplicateFinder), "near-identical" bar ≤ 3, and the
  answer is a verdict sentence, not a grid dump: *almost-exact copy of your
  clip in "Bookmarks" — Mar 12, 2026*, or the honest nothing: *new to you*.
  No audio fingerprints — the brand forbids mic land-grabs and the visual
  engine was already paid for. Photos gained `phash` in the same migration,
  so the image pass now fingerprints while it reads, one decode, free.
- **Transcripts — the words a clip says** (`data/brain/Transcriber.kt`,
  `engine/TranscriptShaper.kt`, service phase `transcripts`). A third sense
  for the voice layer: Android's on-device `SpeechRecognizer`, with the
  audio fed from a *file descriptor* (`EXTRA_AUDIO_SOURCE`) instead of the
  microphone — no RECORD_AUDIO permission is asked, ever, and nothing is
  downloaded or sent. Availability is runt-verified and strict (API 33+,
  engine installed); below that the pass is silently off, the queue stays
  honestly unattempted, and the Brain screen says precisely that. The queue
  is triple-gated so recognition never dogpiles: the audio pass must have
  actually heard speech (`speechRatio ≥ 0.1`, `durationMs ≤ 120 s`,
  `transcriptTried IS NULL` — one attempt per clip, ever, so a broken
  engine cannot retry-storm), attention-ordered like the rest of the scan,
  and capped at 8 clips per run. `TranscriptShaper` shapes once, at write
  time (whitespace collapse, recogniser residue off, whole-word cap 240,
  ≥ 3 letters/digits or the line is honest nothing and is not stored twice
  or indexed). The stored words then flow the same channel everything else
  speaks: `MemoryDoc.text`, the FTS `VIDEO_DOC` SQL, the LIKE fallback net
  — so "search the words you said" works through every existing path, and
  `rebuildVideoIds` reindexes only the clips that gained words.

## The brain starts learning (Nov 5)

Three tiers of "the brain learns", on-device only:

- **Tier 1 — taste** (`domain/engine/TasteProfile.kt`): watch events fold
  into evidence buckets — category, person, folder, short/long. Affinity
  is a smoothed like-ratio biased hard toward neutral while the evidence
  is thin (shrinkage 6), so one accidental finishing never hands the reel
  to cats; buckets decay by a 21-day half-life so an obsession fades. The
  learner folds exactly-once (app_kv cursor `taste.cursor`), called
  debounced from the feed after each watch and as end-of-run maintenance.
  The reel obeys through one new weight, `tasteWeight` ("Your taste" in
  Tune): the score reads the attribute blend, and only a genuinely
  learned single attribute may earn the "matches what you keep watching"
  card. Learned state lives in its own app_kv key, apart from
  EngineSettings — the settings are the user's hand, this is the brain's
  notebook.
- **Tier 2 — identity obeys corrections** (`engine/Identity.kt`, migration
  v10→v11): dismissing a split warning is now a *lesson*
  (`persons.splitSilenced`) and the splitter never re-flags that person;
  splitting a clip out of a person records an `identity_lessons` row the
  adopt pass honors forever; renames and hand-placed clips verify the
  person. The same pass killed the N+1 cliff: matcher, consolidator and
  splitter now read their vectors in ONE query per pass instead of one
  query per person, and the match decision is a pure, pinned function
  (`decide`).
- **Tier 3 — attention** (`data/brain/AttentionPolicy.kt`): an honest
  watch (or a favorite, for more) bumps the clip's `attention` column; the
  L1b deepening queue bends toward what the viewer actually goes to,
  capped at 3.0 so one obsessive session cannot monopolize the budget.
  Attention is the scan queue's signal only — the feed is taught by
  FeedComposer's tunable weights and the two never share a number.

Cheap optinals that rode along: the divider Hairline is theme-aware (the
one token the light theme missed); the feed score divides by its
realistic ceiling
(1.4) before clamping, so a dozen fully-loaded clips no longer read a
flat 1.0; bundle import no longer trusts ids on a lived-in library —
persons merge by verified name and `identity_lessons` restore only into a
fresh install (the lessons now travel in the bundle at all). 74 tests
green; every tier has its pinned suite (TasteProfileTest,
IdentityMatchTest, AttentionPolicyTest).

## Search gets an index (Nov 5)

Search was the weakest link against the goal *think it → it's there
instantly*: every query swept the whole library by LIKE, and photos could
not be searched at all. Now two FTS4 tables hold the searchable document
of every row — video AND photo — with the document written in SQL
(`data/search/SearchIndex.kt`) so the Kotlin MemoryDoc builder and the
stored text cannot drift apart; the photo document (`buildPhoto`) finally
exists. Recall from the index (candidate ids, capped at 500), precision
unchanged from `matchScore` — the index *narrows*, the scorer you can
explain still *ranks*. Room deliberately knows nothing about the virtual
tables (schema it didn't generate is schema it rejects): they are created
by the v10 migration and the fresh-install callback, read only via raw
queries, rebuilt wholesale after every service run, and healed on open
when row counts disagree (`ensureFresh`); a rename or merge refreshes
exactly that person's documents. Results are one mixed grid (`SearchHit`):
videos keep the tall thumb and the long-press sheet, photos open the
photo viewer, keys carry v_/p_ prefixes because MediaStore ids collide
across media. A human query is sanitized into prefix terms (`FtsQuery`),
unicode-safe by construction — an Arabic filename is found in the script
you typed it in, and operators cannot smuggle through as tokens. The
whole general path degrades to yesterday's behavior if the index is
unavailable for any reason. 47 tests green (11 new pin the tokenizer and
the photo document).

## The photo brain, upgraded (Nov 5)

Photos were the one medium whose two senses never compared notes: L0 read
the folder, L1 read the pixels, and whichever ran last won. Four fixes,
no schema change, no new dependency:

- **EXIF orientation** (`PhotoAnalyzer.decode`). API 28+ decodes through
  `ImageDecoder`, which applies the camera's rotation; the old
  `BitmapFactory` path stays below 28 and as the fallback for files the
  decoder rejects. A portrait shot used to reach the face detector on its
  side — a rotated face is a missed face. The fingerprint deliberately
  keeps the old unrotated decode: `ContentHasher` still hashes picked
  images through BitmapFactory, and a hash in a different dialect would
  turn every picked portrait into "new to you".
- **One fusion rule** (`PhotoPolicy.decide`, pure). Category precedence is
  filename (0.85) → folder (0.70) → the strongest label that speaks the
  ontology → a raw label only when nothing else does. The losing claim
  stays in `tags`, so a dog at a birthday is filed under birthday and is
  still findable by "dog". Empty labels, Unicode names and unmeasured
  sharpness are pinned.
- **Honest confidence**. The service used to floor every known-looking
  row at 0.6; that floor is gone, and the stored number is the winning
  claim's own confidence. A decode with no detail (Brenner < 8, L1a's
  existing mush bar) earns no label at all — `brainLevel` never retries,
  so a blurry guess would have been permanent.
- **Junk by shape** (`PhotoPolicy.junkScore`). Filename/folder tokens
  (screenshot, scan, receipt, …) plus exact phone-screen dimensions and a
  20:9 aspect, camera-named files exempt. An exact screen-sized still
  prices at 0.9 — the photo pass's "don't spend ML here" bar — so a
  library of screenshots no longer crowds photographs out of the 40-per-
  run read budget. Those rows are marked settled in the same phase,
  otherwise `pendingCount` would keep rescheduling the service over work
  `pendingBrain` refuses to return.

Skipped deliberately: sampling a later GIF/WebP frame. The platform API
already in the project exposes frame count through `AnimatedImageDrawable`
but no seek, so a representative later frame is not reachable without
writing a decoder of our own. The first frame stays. 20 tests pin the new
policy (`PhotoPolicyTest`); no migration, no column, no dependency.

## The photo half gets its mind (Nov 5, evening)

The user's five-point critique, shipped and device-verified: nav pill reads
the live accent (no more stuck purple), both "More like this" rails fold
behind their headers via a shared RailPrefs (content first, neighbours on
request), search suggestions are drawn from the library with weight gates
(3+/5+) under a "Drawn from your library" heading, PhotoGroupBuilder builds
the still half of the smart groups (With people / All smiles / labeled
subjects / Being read / Clutter) as filter cards above the month grid, and
the single-photo viewer carries an info panel (date, dimensions, brain
label, tags), the foldable neighbours rail, and share-out-of-device.

## Six curated themes, device-verified (Nov 5)

The two-theme switch grew into six hand-tuned palettes — Midnight, Catppuccin
Mocha, Tokyo Night, GitHub Dark, Paper, Catppuccin Latte — picked from their
official specs and retuned for a gallery. Palette/PALETTES/PaletteHolder.theme;
every token getter reads the active palette (accents included, so Mocha reads
as Mocha); hairline became a per-theme field; Tune's Appearance wears the
themes as color-dot chips; EngineSettings.THEME_IDS is the canonical list with
dark→midnight / light→paper graduation, pinned by a coherence test.

Device verification caught two bugs the lab never showed: the palette sync
still carried the two-theme boolean (five of six themes could never apply —
fixed through PaletteHolder.byId on both sync paths), and bare GlassStyle()
defaults were dark-tuned, skipping the dispatch (stat tiles rendered dark on
Paper — the defaults now read the palette themselves, so light GlassStyle()
is a true white frost). Verified live: Midnight ↔ Mocha ↔ Tokyo ↔ Paper, frost
over paper, dark status-bar ink.

## Light theme, from settings (Nov 4)

The palette became a switch: PaletteHolder (the AccentHolder pattern)
drives every Theme.kt token as a getter, with a designed light palette —
cool paper, white surfaces, ink text, accents a step darker for weight.
The glass material reads the palette and returns the tuned pane (stronger
white frost, soft cool shadow, whisper scrim). Dark/Light segmented in
Tune > Appearance, persisted as EngineSettings.theme, applied live via
OrganizeViewModel's continuous settings observe — which also fixed the
latent live-accent bug from Tune. Reel/spotlight/photo viewer stay dark
(video surfaces); status-bar ink follows the theme. Device verification
pending reconnection (36 tests green, build ready).

## Theming as law (Nov 2)

The palette became semantic tokens enforced across every screen — Line,
Hairline, Surface2, Elevated, Danger/DangerContainer (newly named), with
zero raw hex left outside the theme and glass internals. Two new
design-system controls ended the stock-M3 cheapness: GlassSlider (10dp
accent pill track, step dots that light as the thumb passes, glass thumb)
and GlassSwitch (one definition, used everywhere). Verified on device in
the Learning room.

## The reel, session-stable (Nov 2)

The feed screen's structural flaws — every watch event reshuffling the
running reel, reshuffles recycling the same sixty, deletions shifting the
pager onto a stranger, per-page speed amnesia, finished clips
auto-advancing on replay — are all fixed. The composition signature now
covers only membership + suppression + taught weights + explicit
reshuffle; watch events shape the next composition. The composer walks
the library (served clips excluded, structural wrap when the unseen pool
thins). Reel form: caption hierarchy, depth counter (1 · 60), speaking
loading state, session-scoped speed. Five new tests pin the semantics.

## The three-mechanic hero system (shipped Nov 1)

`ui/hero/` holds the constitution's three mechanics, each reviewed by a
separate pass before shipping: **A** the cylindrical shelf (Organize hub,
ceremonial browse), **B** the spring card wave (GroupDetail hero, review
handoff), **C** the screen-locked portal (the session's first grid→player
entry, consumed on completion). All math lives in `HeroMath.kt` (14 tests),
transforms run in graphicsLayer/frame loops so gestures never recompose,
haptics fire on every interaction, reduced motion freezes A and B and routes
C direct, and the sacred screens (reel + spotlight) are byte-identical
throughout — verified by diff.

Ruling recorded for the reviewer's question: the two-radii rule governs the
mechanics' own surfaces (they add exactly one, the portal's 44dp). App
surfaces keep the theme's existing shapes — `CardShape` (20dp) for
containers, the pill for interactive — and new app code should reach for
those before inventing a radius.

## Phase 4 — Presentation & the name

The name question, with options (decision is the owner's; renaming the
**package** is a near-permanent cost — the display label is a one-line change):

| Option | Why | Risk |
|---|---|---|
| **Keep BrainGallery** (recommended) | Descriptive, distinctive, zero migration cost; "your device remembers for you" already does the positioning | None |
| Rename label only (e.g. **Reelcall**, **Mnema**, **Hippocampus**, **Remery**) | Fresher presentation | One-line manifest change; store listing is new anyway |
| Full rename incl. package | Only if the store brand must match | `com.brain.gallery` → everything, keystore, migrations; not worth it now |

Presentation changes that matter more than the name: the app's opening beat
(For You as the launch tab, not Organize), consistent voice in copy ("the
brain" already gives it one), and the big-numeral stat tiles from Phase 2.

## Operating rules for this roadmap

- One phase per sitting; compile + test gates green before calling anything
  done (`./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`).
- Settings JSON only ever *adds* `opt*` fields — old blobs must load forever.
- Every new ranking signal needs: a weight, a slider, a reason phrase, a test.
- Commit after each finished phase; the working tree currently mixes the
  glass WIP with the Oct 27 wave — split them when committing.
