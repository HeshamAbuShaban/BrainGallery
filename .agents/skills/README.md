# Skills adopted for BrainGallery

Copied from two sources on 2026-10-27:

- `android-*` — PhillipLackner's architecture suite
  (`~/WorkingOn/Use/SKILLS/PhillipLackner Skills/skills_extracted/`)
- `android-cli`, `mobile-android-design`, `frontend-design` — iqra's skill folder
  (`~/WorkingOn/SideProject/iqra/.opencode/skill/`)

## Adaptations for THIS project (read before applying any of them)

These skills were written for other setups. Applied verbatim they will fight
this codebase. The deltas:

1. **Hilt, not Koin.** `android-di-koin` and the Koin parts of
   `android-module-structure` were deliberately NOT adopted. Where
   `android-presentation-mvi` says `koinViewModel()`, read `hiltViewModel()`.
2. **Single module.** Where `android-navigation` / `android-data-layer` assume
   multi-module features (`:feature:x`, `:core:design-system`), keep everything
   in `:app`. The package `ui/glass` plays the role of the design-system module;
   `domain/` and `engine/` play the role of `:core:domain`.
3. **Glass kit is the design system — the M3-first rule has an exemption.**
   `android-compose-components` says "Material 3 first, raw Box composition is
   the fallback". For surfaces styled with the glass kit (`ui/glass`,
   `Modifier.glass(...)`), the glass kit IS the correct first choice. The M3
   rule applies to everything else (Switch, Slider, Dialogs on non-glass
   surfaces). Do not "fix" glass screens into stock M3.
4. **Offline app.** `android-error-handling`'s and `android-data-layer`'s
   Ktor/network halves do not apply. Adopt only the typed `Result` +
   `DataError.Local` patterns for the scan/vision pipeline.
5. **Settings are a JSON blob in Room `app_kv`, not DataStore.** Never introduce
   DataStore or SharedPreferences; follow the evolution rule in the
   `braingallery` project skill.
6. **No WorkManager.** Indexing is a foreground service (`BrainScanService`) +
   AlarmManager re-kick, deliberately. Don't migrate.
7. `android-navigation` is **guidance for the planned NavHost migration** (see
   `docs/ROADMAP.md`) — the app still uses hand-rolled tab switching today.
8. Dangling references inside the PhillipLackner skills
   (`compose-adaptive-layouts`, `figma-to-android-vector`, `android-ui-testing`,
   `android-integration-testing`, `android-background`) point at skills that
   were never in the pack. Ignore those pointers.
