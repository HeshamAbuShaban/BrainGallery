---
name: braingallery
description: Project router for BrainGallery — the offline Android video gallery in this repo. Use for ANY work in this project: it routes tasks and symptoms to the right files, states the non-negotiable conventions (no network, Hilt, glass kit as design system, JSON settings in Room), and maps the domain vocabulary (L0/L1a/L1b, MemoryDoc, FeedTuning).
---

# BrainGallery — project router

One-sentence identity: **a private, offline Android gallery that indexes the
phone's videos into "Memory Documents" (people, events, duplicates, buried
gems) and serves them through a For You reel, organize surfaces and search.**
No account, no network (the manifest has no INTERNET permission), everything
on-device.

## Non-negotiables

1. **No network. Ever.** No INTERNET permission, no analytics, no CDN. Any
   change that phones home is a bug.
2. **Hilt for DI** (`di/AppModule.kt`), not Koin. `hiltViewModel()` at Root
   composables only.
3. **`ui/glass` is the design system.** Screens use `Modifier.glass(shape,
   Glass.onDark(Accent))` — layered translucency, not blur. Do not convert
   glass surfaces to stock M3, and do not add blur (contrast guarantee lives in
   `ui/glass/README.md`).
4. **Settings are one JSON blob** (`EngineSettings.toJson()`) in Room table
   `app_kv`, key `"engine.settings"`, via `SettingsStore`. Evolution rule:
   `fromJson` must read every field with `opt*(field, default)` so old blobs
   load forever; never bump a schema, only add fields. Feed weights live in
   `EngineSettings.tuning: FeedTuning` — presets in `FeedTuning.PRESETS` are
   snapshots copied into settings on pick; `mood == "Custom"` means the sliders
   own the truth.
5. **Ranking must be tunable and explainable.** `FeedComposer` has zero hidden
   constants — every contribution is `number * FeedTuning weight`, and every
   `FeedItem` carries a human `reason`. If you add a signal, add a weight, a
   Tune-tab slider, a reason phrase, and a test.
6. **Tests guard the pure logic** (`domain/engine/*`, `FeedComposer`). See
   `app/src/test/.../FeedComposerTest.kt` for the pattern: break vectors, one
   angle per test, seeded `Random`.
7. **The indexer is a foreground service**, not WorkManager. `BrainScanService`
   + `RescheduleReceiver` (AlarmManager re-kick for OEM cleaner-killed runs).

## Symptom → where to look

| Symptom / task | First files |
|---|---|
| Feed ranks wrong, "same clips every session" | `domain/engine/FeedComposer.kt` (bands + shuffle + explore), `FeedTuning` in `domain/EngineSettings.kt`, tests in `app/src/test/.../FeedComposerTest.kt` |
| Feed settings UI | `ui/tune/TuneScreen.kt` (presets + weights), `ui/organize/BrainScreen.kt` (engine switches) |
| Scan misses videos / stuck | `data/scan/MediaScanner.kt`, `data/service/BrainScanService.kt`, `BrainGalleryApp.kt` (ContentObserver, 8 s debounce) |
| People wrong (merge/split/naming) | `engine/Identity.kt` (PersonMatcher, PersonConsolidator, SplitDetector, NameSuggester), thresholds in `EngineSettings` (matchSim, mergeSim, splitSensitivity) |
| Duplicates wrong | `engine/DuplicateFinder.kt`, `engine/ClusterMath.kt` (dHash hamming ≤ 6) |
| Glass looks broken / contrast | `ui/glass/Glass.kt` + `ui/glass/README.md`, accent via `ui/theme/Theme.kt` `AccentHolder` |
| Search weak | `engine/MemoryDoc.kt` + `MemoryDocBuilder.kt`, `ui/organize/SearchScreen.kt` |
| Voice/prosody nonsense | `data/brain/AudioProbe.kt` (fixed in 406f5cd), gated by `prosodyEnabled` |
| Export/import bundle | `data/portability/MemoryBundle.kt` — **import has no UI yet** (roadmap item) |
| Adding a new screen | Today: hand-rolled tabs in `MainActivity.kt` `Root()`. Planned: NavHost per `.agents/skills/android-navigation` + `docs/ROADMAP.md` |

## Vocabulary

- **L0** filename/folder + ontology pass · **L1a** frames, dHash, sharpness,
  faces, MobileFaceNet vectors · **L1b** ML Kit labels (budgeted queue) ·
  **prosody** audio delivery tags · **MemoryDoc** searchable per-video document ·
  **buried gem** > 180 d, unwatched, junk < 0.4 · **junkScore** clutter score,
  ≥ `junkCut` (0.85) never enters the reel.
- Room db `BrainDatabase` v7: `videos`, `watch_events`, `persons`,
  `face_vectors`, `not_interested`, `semantic_vectors` (reserved, unwritten),
  `app_kv`, `group_overrides`, `selection`. Migrations in `data/local/Migrations.kt`.

## Build & verify

```bash
./gradlew :app:compileDebugKotlin     # fast correctness gate
./gradlew :app:testDebugUnitTest      # ranking tests
./gradlew :app:assembleDebug          # full apk (arm64-v8a only)
```

Device checks via the `android-cli` skill (`android run`, `android screen`).
