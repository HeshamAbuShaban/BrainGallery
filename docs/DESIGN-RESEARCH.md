# Design research: what BrainGallery's UI should be

*Written 2026-10-27, after the glass-liquid-enhance experiment. Companion to
`ROADMAP.md`. Every claim about a named app below is from public design-system
writing and release coverage; sources at the end.*

## The question

We built a glass material kit (layered translucency, no blur, contrast
guarantee) because Apple shipped Liquid Glass and it looked like the future.
But the kit is uncommitted WIP on one branch, three of its ideas
(`GlassTheme` flavor tints, `AppleSpecular`, `GlassWiggle`) are orphan stubs,
and it never felt quite like *ours*. Should we double down on glass, or find
the actual modern standard?

## Verdict

**Keep glass as an accent, not as an identity. The identity is the content.**

Every one of the seven reference apps — including the ones famous for
personality — is built on the same base: **flat, quiet chrome; loud,
full-bleed content; one accent; strong type.** None of them are glass. Even on
iOS 26, the big cross-platform apps mostly render flat chrome and let Apple's
system components carry whatever the OS trend is. The apps that *do* feel
distinctive (Wise, Cash App) spend their boldness in exactly one place — color
or shape — and stay disciplined everywhere else.

Liquid Glass's real-world reception also matters: the launch was dominated by
readability/contrast complaints ("too opaque literally"), and the eventual
settle was Apple refining *toward* legibility (thicker scrims, tint controls).
Our kit's own README already encodes that lesson — it enforces a scrim-based
contrast guarantee precisely because translucent chrome over video is hard.
That is: **we already built the correct, chastened version of glass.** The
right move is to keep that, skip the gimmicks (specular highlights, wiggles,
flavor tints), and build the content surfaces flat.

## What the seven apps actually do

**Spotify (Encore design system).** Black canvas; the artwork *is* the
interface — colors and imagery come from content, chrome is nearly invisible.
Three-tab bottom nav with labels; big typographic scale; "why am I seeing
this" transparency for editorial picks; algorithmic feeds that reshuffle and
explain. Encore's most interesting idea: layout/spacing *themes* generated
dynamically so the home surfaces don't feel static. Lesson for us: the reel is
already our content-first surface; what's missing is the *explanation* (now
shipped — the ✦ reason line) and a home that visibly reshuffles with
intelligence.

**Uber (Base design system).** The most disciplined system in the set: flat
black/white, one green, the Uber Move typeface, giant touch targets, map-first
screens where chrome floats over content with strong scrims. Purposeful motion
only. Lesson: functional screens (our Brain/Tune) should be *flat cards on a
flat canvas* with one accent — decoration on control surfaces reads as
untrustworthy.

**Airbnb (DLS, Cereal typeface).** Photography-first cards with generous
rounding, line icons, lots of air, text that explains before you commit, trust
signals everywhere. Their 2025 refresh doubled down on calm and clarity.
Lesson: organize/library surfaces are photography surfaces — thumbnails should
be huge, cards should breathe, and every grouping needs a *human* name and
description ("buried gems", not "score bucket 3").

**Wise.** Bright green (#9FE870), bold flat surfaces with hard edges and
offset shadows, oversized numbers, radical fee transparency shown in-line.
Their signature is explaining the *why* of every number at the exact moment
you see it. Lesson: our Tune tab is a Wise surface — sliders with live
consequence previews ("Right now that is 512 memories and 83 clutter") are
exactly the Wise pattern, and it's the direction for everything numeric we
show.

**Cash App.** Blocky, high-contrast dark, one green, huge numerals, playful
physics-based motion, navigation reduced to almost nothing. Lesson: motion
should be felt, not seen — a few springy moments (our liquid nav pill) carry
more personality than any material ever will.

**Telegram.** Utilitarian speed-first: content fills everything, chrome is
minimal and translucent over content, icons over labels, and it treats
translucency as *material efficiency* — panels that let content glow through
so scrolling feels fast. Lesson: translucent chrome over full-bleed content is
legitimate and modern — it's what our glass nav pill already is. Telegram
proves the accent use-case, which is exactly where we should keep glass.

**McDonald's (Speedee design system).** Huge imagery, huge type, few choices
per screen, cards that state one thing each. Named after the original
"Speedee Service System" — the design philosophy *is* the brand's operational
philosophy. Lesson: per-screen choice budget. A screen that offers 3 things
gets used; a screen that offers 13 gets a back-press. (Our old BrainScreen
offered 13+; that's why it's being split into Tune + Brain.)

## The common playbook (the actual "modern standard")

1. **Content is the UI.** Chrome recedes: full-bleed media, controls as
   overlays, scrims for legibility.
2. **Flat base + selective depth.** Elevation only for things that float
   (nav, sheets, players). Depth on static content is out of style.
3. **One accent, neutrals elsewhere.** Accent appears in <5% of pixels.
4. **Strong type scale.** Few sizes, bold weights, numerals treated as
   display elements.
5. **4–5 labeled bottom destinations, search first-class.** Deep hierarchies
   move into stacked screens, never into the bar.
6. **Transparency about the algorithm.** "Why this / why now" lines next to
   ranked content (Spotify, Wise).
7. **Controls explain their consequence live** (Wise, Airbnb's
   "explains before you commit").
8. **Purposeful, physics-based motion**; 150–400 ms; springs over easing.

## What we adopt, concretely

| Playbook item | State in BrainGallery | Action |
|---|---|---|
| Content-first reel | FeedScreen already full-bleed | Keep; done |
| "Why ranked" transparency | Missing (README promised it) | ✅ Shipped: ✦ reason line on the reel card |
| Controls with live consequence | BrainScreen sliders have previews | ✅ Tune tab extends the pattern to every feed weight |
| Flat control surfaces | BrainScreen is flat cards | Keep flat; glass only on chrome/dialogs |
| Glass accent chrome | GlassNav pill + dialogs | Keep — Telegram pattern |
| 4 labeled destinations | Was 3, settings buried behind a bug icon | ✅ Tune is now a first-class tab |
| Oversized numerals/type | Stats are small rows | Roadmap: index stats as big-numeral cards |
| One accent | AccentHolder system | Keep; reject the `GlassTheme` flavor tints (redundant with accent) |

## What we reject

- **Glass-as-identity**: flavor tints, specular highlights, wiggle animations
  (the three orphan stubs). They add novelty without adding meaning; Wise and
  Cash App prove personality comes from color/motion, not surface effects.
- **Full M3-default look**: we keep our identity; the reference apps all have
  one. `ui/glass` stays the design system (see `.agents/skills/README.md`
  for the written exemption).
- **Blur**: expensive and accessibility-hostile on the devices this app
  targets; the scrim approach stays.

## Sources

- Spotify design blog via [Encore, three years on](https://nicolas-hoizey.com/notes/2023/10/10/can-i-get-an-encore-spotify-s-design-system-three-years-on.html)
- [Liquid Glass icon redesign — adopt or not](https://medium.com/@shahriarhoque09/liquid-glass-in-ios-26-do-you-really-need-to-redesign-your-app-icon-9f8f4d3f44ff) (Medium)
- [iDrop News: reasons to update to iOS 26](https://www.idropnews.com/news/still-avoiding-ios-26-6-reasons-its-finally-time-to-update/) (Dec 2025 reception)
- MacRumors/Reddit launch-period criticism threads (contrast/readability complaints, Sept 2025)
- Public design-system write-ups: Uber Base, Airbnb DLS/Cereal, Wise brand refresh (2023, #9FE870), Cash App, Telegram, McDonald's Speedee
