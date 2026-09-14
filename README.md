# BrainGallery — your device remembers for you

> Every phone holds thousands of videos nobody ever rewatchs — buried memories,
> duplicated clips, moments you half-remember but can never find. Cloud galleries
> solve this by uploading your life to someone else's computer. **BrainGallery is
> the opposite bet: a private, on-device brain that understands your library and
> retrieves what you need before you finish wanting it.**

No account. No network calls. No tracking. Your videos never leave your pocket.

---

## The idea in one paragraph

You don't remember videos the way filesystems store them. You remember ***who*
was there, *what* was happening, *where* and *when* it was, *what was said*.**
BrainGallery's indexer compiles exactly that — one **Memory Document** per video —
then organizes, deduplicates, and surfaces your library around it: *People*,
*Events*, *Buried gems*, *On this day*, *Duplicates worth deleting*, and a feed
that learns what you actually finish watching. One found clip pulls its siblings.
Hours of diving become one query.

## What it does today

| Capability | How |
|---|---|
| **People** | On-device face detection + MobileFaceNet identity embeddings, clustered into Person A/B/C groups. Smiles ranked first. |
| **Duplicates** | dHash fingerprints + Brenner sharpness + keeper rules (sharpest → highest-res → most-watched). One tap frees the megabytes, with system consent. |
| **Events** | Same-folder bursts within 3-day gaps auto-cluster into trips, parties, weekends. |
| **Nostalgia** | Buried gems (unseen 6+ months) and On-this-day surfacing. |
| **Search** | Who/what/where/when-aware: people-questions route to the identity index, everything else ranks against Memory Documents. |
| **For You feed** | Completion-weighted, smile- and people-aware, 80/20 explore, never repeats a category twice. Every card explains *why*. |
| **Watch anywhere** | Any thumbnail opens a spotlight player with a More-like-this rail. |

## How the brain works (cascading, battery-first)

```
MediaStore scan ──▶ L0 instant (<5ms: filename, folder, duration)
                        │ confident? ──▶ done
                        ▼ uncertain
                   L1 vision (1 keyframe, one pass):
                     labels · faces/smiles · dHash · sharpness · identity vector
                        │ confident? ──▶ done
                        ▼ uncertain + worthy
                   L2 deep (3 keyframes, charging-friendly budget)
                        ▼
               Identity pass (cosine clustering, stable personIds)
```

Rules that keep it cheap: junk skips ML entirely, 40-video budget per run,
new videos trigger a small debounced pass — never a full rescan. A VLC-style
foreground service (`BrainScanService`) does the work with a progress
notification, like a media library should.

## Storage philosophy

The database grows by **~3 KB per video** (hashes, scores, one 768-byte identity
vector). Models are the real megabytes, so a model ships **iff it fills a
human-memory key** — nothing merely nice. Semantic text embeddings and speech
transcription are designed into the schema but deferred until they earn it.

## Architecture

```
app/src/main/java/com/brain/gallery/
├── data/
│   ├── scan/      MediaStore scanner (scoped-storage safe)
│   ├── brain/     L0/L1/L2 analyzers (sensors)
│   ├── vision/    dHash, Brenner sharpness, faces, identity embeddings
│   ├── service/   Foreground indexer (VLC-style)
│   └── local/     Room: videos + watch events (Memory Document storage)
├── engine/        Portable core: ClusterMath, DuplicateFinder,
│                  SimilarFinder, MemoryDoc — pure logic, zero Android
│                  imports, shaped for a future Rust port (UniFFI).
│                  ML sensors stay platform-native. Permanently.
├── domain/
│   ├── organize/  Smart-group builder (People, Events, Gems, …)
│   └── engine/    Feed composer (local reels algorithm)
└── ui/
    ├── organize/  Library, groups, search, spotlight player
    ├── feed/      Vertical reels (single shared ExoPlayer)
    └── components/Thumbnails, shimmer, why-chips
```

## Roadmap (gated — each phase funds the next)

- **Gate 0 · now:** video memory retrieval on Android. Nothing else exists.
- **Gate 1:** Rust core spike — translate `engine/` via UniFFI, Android consumes its own core.
- **Gate 2:** desktop scanner + any-file support (images, documents share the same machinery).
- **Gate 3:** on-device taste profile — big-corps' recommendation mechanic, private by construction.

## Build

On any machine with the Android SDK (versions match `gradle/libs.versions.toml`):

```bash
git clone https://github.com/HeshamAbuShaban/BrainGallery.git
cd BrainGallery
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

No SDK handy? Every push to `main` builds on GitHub Actions — grab
`brain-gallery-debug` from the run's Artifacts. Tags matching `v*` cut a release.

## Principles

1. **Offline is a feature**, not a limitation — the brain works on a plane.
2. **Explain every surface** — no black-box "recommended"; every card says why.
3. **Battery is a budget** — cascade from cheap to deep, never brute-force.
4. **Your data is the moat** — big tech feeds you the world's content; this feeds you *yours*.
