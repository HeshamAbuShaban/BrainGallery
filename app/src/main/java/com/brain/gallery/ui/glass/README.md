# Glass

The frosted material the app floats over its content with, plus the widgets that
are made of it. One folder, copyable into any Compose project.

## The one rule

Nothing outside this folder is referenced. No app theme, no app palette, no app
motion file: `GlassMotion` is the only motion source and every colour arrives as
a parameter (`GlassStyle`, `selectedColor`, `accentColor`, the shadow accent).
Copy this folder, hand it the host project's colours, and it works.

## What is in the folder

| File | Contents |
| --- | --- |
| `GlassStyle.kt` | `GlassStyle` — every value of the material in one data class — and the `Glass.onDark` / `Glass.subtle` / `Glass.raised` presets |
| `Glass.kt` | `Modifier.glass(shape, style)`, the paint itself |
| `GlassSurface.kt` | generic edge-anchored floating pane, plus `glassReserve()` |
| `GlassNav.kt` / `GlassNavMotion.kt` | the tab bar: one shared gesture, `semantics { role = Tab; selected; onClick }` per tab, and the pill that travels between them — velocity-driven squash/stretch, moving specular (`glassSheen`), drag handling |
| `GlassMenu.kt` | menu pane, `glassMenuItems { }` DSL, and `GlassMenuOverlay` with its scrim |
| `GlassWidgets.kt` | `GlassCard`, `GlassChip`, `GlassSegmented`, `GlassStatTile` |
| `GlassMotion.kt` | durations and easings as named tokens, and `liquid()` |

## The material

Layered translucency, not a blur. Real backdrop blur needs a platform window
effect — a blurred copy of everything behind the pane — which on a full-screen
player is the difference between smooth and not. `glass()` instead draws, in
order:

1. a shadow, cast by the unclipped shape, to lift the pane off the content
2. a dark base, `GlassStyle.scrimAlpha` (default 0.6). The contrast guarantee:
   a white wash alone only ever *lightens* its backdrop, and a pane that
   brightens a bright video frame is a pane whose labels stop being readable on
   it. Over dark content this layer is nearly invisible; it does its work
   exactly where contrast fails.
3. the white wash, in vertical bands scaled by `alpha`
4. the lit top rim, `GlassStyle.rimAlpha` — the static light catch every pane
   gets, drawn from a brush built when the modifier is built, so it costs no
   per-frame allocation
5. a hairline edge, which is what actually makes the shape legible

The moving half of the reflection is opt-in: `Modifier.glassSheen(shape) {
position }` (`GlassNavMotion.kt`) layers the moving specular over any pane —
use it on something that is in motion; a pane at rest simply does not carry it.

Any shape:

```kotlin
Box(Modifier.glass(RoundedCornerShape(30.dp), Glass.onDark(Accent)))
```

A bottom-anchored floating surface:

```kotlin
GlassSurface(edgePadding = 12.dp) {
    MyControls()
}
```

`GlassSurface` fills its window and pins the content to an edge, so callers do
not reserve space themselves. `glassReserve()` gives the content's padding a
value so it cannot drift from the surface.

## How the motion works

`GlassMotion` holds durations (`fastMs` 180 … `deliberateMs` 620), named
easings, and two springs. The rule the kit follows:

- **`liquid()` — underdamped — where the overshoot is the point.** An arrival
  should land like an object, not be switched on: the menu scaling in, the
  segmented indicator sliding to its segment, the nav pill between tabs. The
  small splash past the mark is what makes it read as something being carried.
- **Tween where overshoot would be a defect.** Opacity past 1 flashes brighter
  than fully visible, so fades — the menu's enter/exit and the overlay's scrim —
  are tweens, and so is the exit overall: a pane on its way out must not bounce
  back. Colour is tweened for the same reason: an underdamped colour animation
  extrapolates past its endpoints and the label flickers through an out-of-range
  hue.
- **Short, always.** This Compose version (BOM 2024.09.00) exposes no
  reduced-motion signal a library can read cleanly, so instead of guessing at
  platform settings every move stays inside `fastMs`/`mediumMs`.

The nav adds the one thing tweens cannot do: `GlassNavMotion` reads velocity off
its `Animatable` (and off the finger during a drag) to stretch and squash the
pill and to drag the specular with it.

## Accessibility guarantees

- **Semantics on every interactive piece.** Menu rows and cards are
  `Role.Button`; segmented segments are `selectable` with `Role.Tab` inside a
  `selectableGroup`; chips are `toggleable` with `Role.Checkbox`, so their state
  is announced as checked rather than merely clicked. `selected` comes from the
  selectable/toggleable state, `disabled` from the interactive modifier's
  `enabled`, and `onClick` from the same modifier — or from explicit
  `semantics { onClick }` where pointer input is handled elsewhere, as the nav
  does.
- **48.dp touch targets, artwork untouched.** Every tappable node carries
  `minimumInteractiveComponentSize()`. It grows the *node*, never what it draws:
  the menu row still paints 40.dp of wash, the chip still paints a 34.dp pill,
  the segmented track still keeps the caller's height. The interactive modifier
  is ordered before the floor so the semantics node owns the grown bounds, and
  TalkBack and the accessibility scanner see the target instead of the artwork.
- **One node per control.** The interactive modifier merges its descendants, so
  a row announces as one target ("Delete, button"). Nested merging nodes are
  not flattened upwards, so the menu rows stay separately focusable under the
  overlay's scrim.
- **Icons beside a label are decoration** (`contentDescription = null`); the
  label already speaks. The kit draws no standalone icon that would need its own
  description.
- **Contrast over bright content.** Text defaults are fully opaque, and
  `GlassStyle.scrimAlpha` is the pane's answer to a bright frame: raise it for
  text over video. Two honest limits: disabled states are deliberately faded
  (and exempt), and no translucent fill can carry *light* text over a fully
  white frame — for that, pass a darker `tint` or a stronger scrim.

## Styles

| Preset | Use |
| --- | --- |
| `Glass.onDark(accent)` | Panels over dark content; the accent colours the shadow as a glow. |
| `Glass.subtle()` | Dense content that must stay readable; lower alpha, tighter corner. |
| `Glass.raised(accent)` | Content that should sit clearly above the page. |

Every field on `GlassStyle` is a knob: tint, alpha, border alpha and width, the
band stops, corner, elevation, shadow colour, `scrimAlpha`, `rimAlpha`. `alpha`
scales the whole wash, so one value governs the pane rather than three
independent ones.

## Notes for reuse

- `GlassMenu` is a pane in the caller's overlay, not a real `Popup`. A Popup
  cannot read what is behind it, so there would be nothing for the glass to
  tint.
- `GlassChip` builds its own wash from the style's tint rather than using the
  full `glass` modifier. A row of chips each casting a 20dp shadow stops being
  chips — but it takes the style's scrim back, since a chip is often the only
  thing between its label and a bright frame.
- `GlassSegmented` draws the track and the hit row as two boxes: the track keeps
  the visual height, the segments never go below 48.dp, and both share the same
  horizontal inset so a label stays centred on the indicator under it.
- `GlassSegmented` moves one animated float across the track, so there is no
  per-segment measurement to keep in sync.
