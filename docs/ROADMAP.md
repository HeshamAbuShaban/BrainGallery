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

## Phase 1 — Navigation becomes real (next, highest-leverage refactor)

**Goal:** replace the hand-rolled `AnimatedContent` tab switcher with
`androidx.navigation:navigation-compose` + type-safe routes
(`.agents/skills/android-navigation`), keeping the glass pill as the visual.

Route table:

```
feed              FeedScreen (full-bleed, pill auto-hides)
organize          OrganizeScreen (hub)
organize/group/{id}   GroupDetail (today: state flag inside OrganizeScreen)
search            SearchScreen
tune              TuneScreen
tune/brain        BrainScreen (today: overlay inside Tune/Organize)
```

- Cross-feature navigation via lambda callbacks; ids not objects.
- Back press finally behaves: group → organize → exit, instead of exiting the
  app from a detail screen.
- Per-destination ViewModels get `rememberSaveable`/SavedStateHandle survival.
- The spotlight stays an app-level overlay (it is a player, not a place).
- Risk: moderate; do it in one sitting, behind the compile gate.

## Phase 2 — Finish the half-built features (one definition of done each)

1. **Bundle import.** Settings row + file picker + restore summary
   ("123 names, 45 favorites, 312 watch events restored"); include
   `EngineSettings` + `group_overrides` in the bundle (the docstring already
   claims they travel). Done when a wiped device reconstructs itself.
2. **Adopt the glass widgets.** OrganizeScreen's hand-painted menu →
   `GlassMenuOverlay`; stats strips → `GlassStatTile` with big numerals
   (design research: oversized numerals). Done when `ui/glass` has no
   unused exports.
3. **Delete the dead code.** `DiagnosticsScreen.kt`, `MovePersonDialog.kt`,
   orphan glass stubs, `.bad` file (needs `sudo rm` — it is root-locked).
4. **Prosody earns its keep.** Add a `voiceWeight` to `FeedTuning`:
   "speaking"/"expressive" tags lift talking clips; wire into `score()` with
   reason phrase "someone talking". Done when voice search/filter works too.
5. **User albums.** `albums` table (id, name, kind, order); "New album" flow
   in Organize; multi-select → add-to-album; albums appear as groups.
   Replaces the `manual_<name>` override hack.
6. **Search upgrades.** Filter chips (people / category / folder / time),
   results grouped by person, and prosody tag search ("find clips where
   someone is emphatic").
7. **On-this-day surface.** A dedicated card/page in Organize, not just a
   scoring signal.

## Phase 3 — File-type coverage (Gate 2, the big one)

Design direction: **one more entity per medium, one scanner cascade per
medium, shared brain.** Do NOT shoehorn photos into `VideoEntity`.

- **3a. Photos** (biggest win): `READ_MEDIA_IMAGES`, `MediaStore.Images`
  scanner (cheap: no frame extraction — EXIF + ML Kit labels + face detection
  directly on the bitmap), `PhotoEntity` + `photo DAO`, migration v7→v8.
  UI: Organize/Search gain a media-type filter ("All · Videos · Photos");
  spotlight gains an image mode (Coil, pinch-zoom, same similar-rail);
  FeedComposer pools videos only by default (a reel is motion), with a
  "memories mix" toggle in Tune that lets photos into the reel as stills.
- **3b. GIFs**: land in `PhotoEntity` with `animated = true`; Coil handles
  playback.
- **3c. Audio**: `MediaStore.Audio`; reuse `AudioProbe` (prosody) for music
  vs speech tagging; a simple player sheet; searchable by voice tags.
- **3d. Documents/PDF**: MediaStore Downloads + manual picker; filename +
  (later) text extraction into MemoryDoc; a document browser screen.
- Estimated order: 3a → 3b in one pass; 3c and 3d independent, any time after.

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
