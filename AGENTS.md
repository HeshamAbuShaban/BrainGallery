# AGENTS.md — standing instructions for working in BrainGallery

Read `.agents/skills/braingallery/SKILL.md` first for every task in this repo.
It routes symptoms to files and states the project's non-negotiables (no
network, Hilt not Koin, glass kit as design system, JSON settings in Room,
tunable + explainable ranking).

`.agents/skills/README.md` lists the 9 adopted skills (sources + the
adaptations that keep them from fighting this codebase — the glass-kit M3
exemption and the no-Koin/no-DataStore/no-WorkManager rules matter most).

Key docs:

- `docs/DESIGN-RESEARCH.md` — the UI direction research (glass verdict, the
  seven reference apps, what the app adopts).
- `docs/ROADMAP.md` — the restructuring plan: IA/navigation migration,
  feature-completion ledger, file-type expansion design, naming decision.

Build gates: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`
must pass before any UI or engine change is called done.
