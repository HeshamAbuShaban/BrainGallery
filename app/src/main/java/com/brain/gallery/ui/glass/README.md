# Glass

The frosted material the app floats over its content with. It started life as the
bottom nav, then turned out to be worth having on its own, so it lives here
instead of inside one screen.

## What it is

Layered translucency, not a blur. Real backdrop blur needs a platform window
effect, which means drawing a blurred copy of everything behind the pane. On a
full-screen video player that is the difference between smooth and not, so the
material is built from three cheap things that together read as glass:

- a low-opacity white wash, ramped in vertical bands
- a hairline edge, which is what actually makes the shape legible
- a soft shadow underneath, to lift the pane off the content

One draw, no blur, no extra surface. It is what Telegram-style chrome does at a
fraction of the cost.

## Using it

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

`GlassSurface` fills its window and pins the content to an edge, so callers do not
reserve space themselves. That is the point: the pane is allowed to float over
content, and the content decides its own padding. `glassReserve()` gives that
padding a value so it cannot drift from the surface.

The nav is just a styled use of the same material:

```kotlin
GlassNav(
    tabs = tabs,
    selected = tab,
    onSelect = ::go,
    visible = if (immersive) 0f else 1f,
    style = Glass.onDark(Accent),
)
```

## Styles

| Preset | Use |
| --- | --- |
| `Glass.onDark(accent)` | Panels over dark content; the accent colours the shadow as a glow. |
| `Glass.subtle()` | Dense content that must stay readable; lower alpha, tighter corner. |
| `Glass.raised(accent)` | Content that should sit clearly above the page. |

Every field on `GlassStyle` is a knob: tint, alpha, border alpha and width, the
band stops, corner, elevation, and the shadow colour. `alpha` scales the whole
wash, so one value governs the pane rather than three independent ones.

## Taking it elsewhere

The kit's only dependency is itself. Nothing in the package references this
app's theme, palette, or navigation, and the motion tokens travel with it as
`GlassMotion` — so the app's `Motion.kt` is not needed. The only app-specific
values are passed in at the call site: `selectedColor`, `idleColor`, and the
accent used for the shadow glow.

To reuse it: copy this folder into the other Compose project, nothing else.
Then hand it that project's colours, and the material comes with it.

## What is in the kit

**Material**

- `GlassStyle.kt` — the values that make the material read as glass
- `Glass.kt` — `Modifier.glass(shape, style)`, the paint itself
- `GlassSurface.kt` — generic edge-anchored floating pane, plus `glassReserve()`

**Navigation and menus**

- `GlassNav.kt` / `GlassNavMotion.kt` — the tab bar, and the motion that drives it:
  one glass pill that travels between slots, stretching into the wider selected tab
  rather than sliding as a rigid block, with the icon scaling and the label
  crossfading underneath it
- `GlassMenu.kt` — an overflow/overflow menu pane with a scrim overlay, per-item
  tint for destructive rows, disabled states and dividers

**Widgets**

- `GlassWidgets.kt` — `GlassCard`, `GlassChip`, `GlassSegmented` (moving indicator),
  `GlassStatTile`

**Motion**

- `GlassMotion.kt` — durations and easings as named tokens, the kit's own copy of
  the app's motion system, so nothing outside this folder is imported

## Notes for reuse

- `GlassMenu` is a pane in the caller's overlay, not a real `Popup`. A Popup cannot
  read what is behind it, so there would be nothing for the glass to tint.
- `GlassChip` builds its own wash from the style's tint rather than using the full
  `glass` modifier. A row of chips each casting a 20dp shadow stops being chips.
- `GlassSegmented` moves one animated float across the track, so there is no
  per-segment measurement to keep in sync.
