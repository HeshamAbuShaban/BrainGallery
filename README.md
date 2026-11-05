# BrainGallery — your device remembers for you

> Every phone holds thousands of videos nobody ever rewatches — buried memories,
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
*Events*, *Buried gems*, *Duplicates worth deleting*, and a feed that learns what
you actually finish watching. One found clip pulls its siblings. Hours of diving
become one query.

## What it does

| Capability | How |
|---|---|
| **People** | On-device face detection + MobileFaceNet identity embeddings, clustered by max-similarity into named people. Smiles ranked first. |
| **Identity you can correct** | Multi-vector per person, an ambiguity gate that asks instead of guessing, bimodality detection ("might be two people"), plus merge / split / rename — and your corrections stick: a dismissed warning and a split-out clip are *remembered*, so the engine never quietly undoes you. |
| **Names from context** | Folder and filename tokens co-occurring with a person are used to propose a real name. |
| **Duplicates** | dHash fingerprints + Brenner sharpness + keeper rules (sharpest → highest-res → most-watched). One tap frees the megabytes, with system consent. |
| **Events** | Same-folder bursts within 3-day gaps auto-cluster into trips, parties, weekends. |
| **Nostalgia** | Buried gems (unseen 6+ months) and On-this-day surfacing. |
| **Search** | Who/what/where/when-aware; FTS4 index over videos *and* photos, sanitized prefix queries (unicode-safe), a real name routes straight to that person's library. And it can search *words said out loud*: where the phone ships its own speech engine, clips are transcribed on-device (no mic permission, nothing leaves). |
| **Content ID** | Shazam, but for your own library: someone sends you a clip, you pick it, the brain fingerprints its frames and answers "this is your clip in *Bookmarks*, Mar 12" — or honestly, "new to you". No description possible or needed. |
| **GIFs, WebP** | Animated files land in the photo shelf as stills and *play* when opened — platform decode, no network, no extra artifact. |
| **More like this, for stills** | The photo viewer now grows the rail the player has had: same folder around the same week, shared category and tags, a close dHash fingerprint, animated frames sticking together — and the same shelf nearest in time as the last resort, so even an unread photo gets a neighbourhood. |
| **For You feed** | Completion-weighted, people-aware, 80/20 explore, never repeats a category twice — and it learns your taste from what you actually finish (decaying, evidence-honest, tunable). Every card explains *why* it ranked. |
| **Tune tab** | The ranking is yours: presets (Nostalgic, Hype, Calm, Funny) plus the raw weights behind them — finish rate, fresh finds, people, memories, energy, your taste, reel length, replay demotion, variety, surprise. No hidden constants. |
| **Watch anywhere** | Any thumbnail opens a spotlight player with a More-like-this rail. |
| **Not interested** | Actually works — suppresses that clip *and its duplicates*, and travels with your data. |
| **Portability** | Your human layer (names, favourites, history, taught lessons) exports as one file. Wiping the app costs you nothing that matters. |

## The brain: cascade ordered by cost, not importance

```
MediaStore scan ──▶ mark & sweep
                        │
                        ▼
                   L0  instant (<5ms: filename, folder, duration)
                        │  confident? ──▶ done
                        ▼
                   L1a perceptual — UNBUDGETED, every video
                        dHash · sharpness · faces/smiles · identity vectors
                        no ML model inference
                        │  document empty? ──▶ queue
                        ▼
                   L1b semantic — BUDGETED, expected-value ordered
                        ML Kit labels on the highest-value unknowns
                        ▼
                   identity maintenance (incremental, max-similarity)
                   split detection · name suggestions
```

Gating cheap perceptual work behind expensive model inference was the original
mistake. Now the expensive stage is the only rationed one, so **duplicate
detection and identity are complete on the first pass**.

## Storage philosophy

Models are the real megabytes, so a model ships **iff it fills a human-memory
key**. Bundled today: face detection, image labeling, MobileFaceNet. Designed
into the schema but deliberately deferred: semantic text embeddings, speech
transcription. Video files themselves are never copied or re-encoded.

Builds are filtered to **arm64-v8a** — the target phone's ABI. A universal APK
shipped four copies of the native libraries; the x86 pair exists only for
emulators.

## Architecture

```
app/src/main/java/com/brain/gallery/
├── data/
│   ├── scan/      MediaStore scanner (delta rescan, scoped-storage safe)
│   ├── brain/     L0 / L1a / L1b analyzers (sensors)
│   ├── vision/    dHash, Brenner sharpness, faces, identity embeddings, frames
│   ├── service/   Foreground indexer (VLC-style, progress notification)
│   ├── portability/ MemoryBundle — export/import of the human layer
│   └── local/     Room: videos, watch events, persons, face vectors
├── engine/        Portable core: ClusterMath, DuplicateFinder, SimilarFinder,
│                  MemoryDoc, Identity, Spherical. Pure logic, zero Android
│                  imports, shaped for a future Rust port (UniFFI).
│                  ML sensors stay platform-native. Permanently.
├── domain/
│   ├── organize/  Smart-group builder (People, Events, Gems, Duplicates…)
│   └── engine/    Feed composer
└── ui/
    ├── organize/  Library, groups, search, spotlight, Brain screen
    ├── feed/      Vertical reels (single shared ExoPlayer)
    ├── tune/      For You presets + the ranking weights, as sliders
    ├── glass/     The design system: layered-translucency chrome & widgets
    ├── components/Thumbnails, action sheets, hero transitions
    └── theme/     Colour, type, motion tokens
```

Working docs: [`docs/DESIGN-RESEARCH.md`](docs/DESIGN-RESEARCH.md) (the UI
direction) and [`docs/ROADMAP.md`](docs/ROADMAP.md) (the restructuring plan —
navigation, feature completion, file-type expansion).

## Build

```bash
git clone https://github.com/HeshamAbuShaban/BrainGallery.git
cd BrainGallery
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
```

No SDK handy? Every push to `main` builds on GitHub Actions — grab
`brain-gallery-debug` or `brain-gallery-release` from the run's Artifacts. The
release job also prints an APK size breakdown so regressions are visible.

## Roadmap (gated — each phase funds the next)

- **Gate 0 · now:** video memory retrieval on Android. Nothing else exists.
- **Gate 1:** Rust core spike — translate `engine/` via UniFFI; Android consumes its own core.
- **Gate 2:** desktop scanner + any-file support (images, documents share the machinery).
- **Gate 3:** on-device taste profile — big-corps' recommendation mechanic, private by construction.

Queued next: peak-moment extraction, life-duplicates, event highlight reels,
blind-spot detection, counterfactual "why this and not that", and a single
intent surface with a pre-buffered ready set.

## Principles

1. **Offline is a feature**, not a limitation — the brain works on a plane.
2. **Explain every surface** — no black-box "recommended"; every card says why.
3. **Battery is a budget** — cascade cheap-to-deep, never brute-force.
4. **Your data is the moat** — big tech feeds you the world's content; this feeds you *yours*.

## License

MIT — see [LICENSE](LICENSE). Third-party component licenses are listed there too.
