# BrainGallery — your local For You feed

Offline-first smart gallery. The product is **organization**: a VLC-style foreground
indexer (`BrainScanService`, progress notification, ContentObserver-triggered) feeds a
cascading on-device brain (L0 filename → L1 thumbnail → L2 deep only if needed),
which powers auto **Smart Groups** (Memories, Buried gems, On this day, Events,
Categories, Clutter drawer), search, and a reels feed — all with explainable "why" cards.

## Build on Windows (your SSD with SDK + cached libs)
```bat
cd BrainGallery
gradlew assembleDebug
rem APK: app\build\outputs\apk\debug\app-debug.apk
```

## Build via GitHub Actions (no local SDK needed)
Push to `main` → Actions tab → download `brain-gallery-debug` artifact.
Tag `v1.0` → automatic GitHub Release with release APK.

## How it works
- **L0 instant:** filename + folder + duration → category/tags/confidence/junkScore (<5ms, on scan)
- **L1 fast:** 1 mid-frame ML Kit label (~200ms, background worker)
- **L2 deep:** 3 frames, only if `confidence<0.75` + not junk + >8s (battery cap 25 vids/run)
- **Feed:** completion-weighted + nostalgia + on-this-day + 80/20 explore + palette cleanse (no repeat category)
- **Player:** single ExoPlayer singleton, thumbnails via Coil VideoFrameDecoder offscreen

## Project layout
```
app/src/main/java/com/brain/gallery/
  BrainGalleryApp.kt, MainActivity.kt
  data/local/   (Room: videos + watch_events)
  data/scan/    (MediaStore scanner, scoped-storage safe)
  data/brain/   (CategoryOntology, Level0/1/2 analyzers)
  data/work/    (IndexWorker — Hilt + WorkManager cascade)
  domain/engine/(FeedComposer — local reels algorithm)
  ui/feed/      (FeedScreen + FeedViewModel, VerticalPager)
  ui/player/    (PlayerManager singleton)
  di/           (Hilt module)
```
