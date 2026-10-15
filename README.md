# mott — bar app (React Native)

Native mobile app for a small bar: tables, orders, catalogue, suppliers, expenses,
branding and a day panel. React Native (Expo) + TypeScript, one codebase for Android
(and iOS), and it owns its own data: **the phone works with no PC and no Wi-Fi**.

The visual language is a direct port of the web/Figma master in
[`mottandmittdesing/`](mottandmittdesing) — same sections, same tokens, same copy —
so the PC dashboard and the app stay visually identical.

> **Status:** ported from the Kotlin client to React Native. APKs are built by
> GitHub Actions ([`.github/workflows/android.yml`](.github/workflows/android.yml))
> and published as release assets. Not field-tested in a real bar yet.

## Sections

Seven sections behind one shell, in the master's order:

| Tab | What it does |
|-----|--------------|
| Panel | KPI cards, sales per hour, top products, sales per table, recent orders and supplier balances |
| Mesas | Table tiles with occupancy; opening a table shows its bill **inside the screen** (right pane on wide screens, full width with a back button on phones), add items with steppers, close with a two-tap confirm |
| Catálogo | Products grouped by category, add and remove |
| Proveedores | Suppliers with what they sell, purchases, what is owed, and settling the balance |
| Gastos | Day expenses with a running total |
| Conexión | The bar server's address, how to link the phone, and the hub's public API |
| Personalizar | Shop name, light/dark theme, custom colors and the 22 design presets |

Rules kept from the design work: no dialogs, sheets or dropdown menus anywhere
(destructive actions confirm with a second tap), one tap target per tile, every
control at least 48dp, long names ellipsize instead of overlapping, and status is
always a text label with a dot — never color alone.

## Responsive layout

Breakpoints live in [`src/theme.ts`](src/theme.ts) and follow the web master's `lg:`
sidebar and the responsive work before it:

| Width | Navigation | Bodies |
|-------|------------|--------|
| < 840dp | One-row header plus a scrollable bottom pill strip | Single column |
| >= 840dp | Permanent 256dp rail (shop name, sections, data summary) | Content capped at 1400dp |
| >= 720dp of body width | — | Two panes side by side (Mesas list + bill, Proveedores list + fiche) |

## Theme tokens

`src/theme.ts` carries the master's tokens (`mottandmittdesing/src/index.css`) 1:1:
the same `color-mix()` recipes for paper/surface/sunk/line/brand-soft are computed in
JS from the shop's brand color, for both the light and dark role sets. Changing the
brand in Personalizar repaints the app.

Two honest deviations from the web, both stated in the UI instead of faked:

- The master's webfonts (Bricolage Grotesque, Figtree, DM Mono) are not bundled yet;
  text uses the platform font and numbers keep tabular figures in the platform mono.
- The logo cannot be uploaded from the app (that needs an image-picker dependency), so
  the tile shows the shop's initial and says the logo is uploaded from the web.

## Local data

State lives on the phone (`src/store.tsx`) and is persisted to AsyncStorage as one JSON
object, validated field by field on load so a truncated or older payload falls back to
the seeds instead of crashing. Writes are coalesced so rapid taps never queue dozens of
whole-state writes.

## Build

Local (Node 22+ and the Android SDK):

```bash
npm install
npx expo start                        # dev server, JS only
npx expo prebuild --platform android
cd android && ./gradlew assembleRelease
```

In CI, every push that touches the app runs
`.github/workflows/android.yml`: install, `expo install --fix` (aligns native versions
with the SDK), typecheck, `expo prebuild`, `assembleRelease`, and the APK is uploaded as
a workflow artifact. Tagging `v*` also attaches that APK to a GitHub release, so a
build can be downloaded straight from the repository.

The release APK is signed with the debug keystore the Expo template generates: fine for
testing, **not** for a store upload.

## Compatibility

| What | Supported from |
|------|----------------|
| Android | whatever the current Expo SDK supports (7.0+ today) |
| iOS | same codebase (`npx expo run:ios`) |
| PC hub | optional: the app works alone; the hub is an add-on for a shared catalogue |

## Contributing

- Conventional Commits, one work unit per commit.
- Artifact language is English; user-visible copy stays Spanish, like the master.
- Run `npx tsc --noEmit` before pushing.

## Limitations

- No on-device pass yet: the port is verified by typecheck and the CI build, not by
  using the app on a phone.
- The share sheet hands off the server address; the app has no QR scanner yet.
- There is no LAN sync with the PC hub in this version: the phone's data is its own.
