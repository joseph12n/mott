# mott — bar app (React Native)

Native mobile app for a small bar: tables, orders, catalogue, suppliers, expenses,
branding and a day panel. React Native (Expo) + TypeScript, one codebase for Android
(and iOS). **The phone works standalone with no PC and no Wi-Fi**, and when paired
with the `mitt` PC hub over LAN, uses the hub as the source of truth.

The visual language is a direct port of the web/Figma master in
[`mottandmittdesing/`](mottandmittdesing) — same sections, same tokens, same copy —
so the PC dashboard and the app stay visually identical.

> **Status:** React Native app with optional `mitt` PC hub pairing; standalone mode
> still works without a server. APKs are built by GitHub Actions
> ([`.github/workflows/android.yml`](.github/workflows/android.yml)) and published as
> release assets. Not field-tested in a real bar yet.

## Sections

Seven sections behind one shell, in the master's order:

| Tab | What it does |
|-----|--------------|
| Panel | KPI cards, sales per hour, top products, sales per table, recent orders and registered suppliers |
| Mesas | Hub-backed tables and open tabs when paired, or local tables when unpaired; opening a table shows its bill **inside the screen** (right pane on wide screens, full width with a back button on phones), add items with steppers, close with a two-tap confirm |
| Catálogo | Products grouped by category, create products and toggle availability (`Disponible` / `Dada de baja`) |
| Proveedores | Flat supplier record (`name`, `phone`, `note`) with in-screen detail pane to edit or delete; no balances or ledger |
| Gastos | Day expenses with a running total |
| Conexión | Pair with the PC hub, inspect connection status and last sync time, trigger `Sincronizar ahora`, and view the pending offline outbox count and hub API routes |
| Personalizar | Shop name, light/dark theme, custom colors and the 22 design presets |

Rules kept from the design work: no dialogs, sheets or dropdown menus anywhere
(destructive actions confirm with a second tap), one tap target per tile, every
control at least 48dp, long names ellipsize instead of overlapping, and status is
always a text label with a dot — never color alone.

## PC hub

`mott` pairs optionally with a `mitt` PC hub over the local network:

- **Default URL:** `http://<pc-lan-ip>:8080` (plain HTTP on the LAN; Android cleartext
  traffic is enabled via `expo-build-properties`).
- **Pairing code format:** `mitt://pair?url=<urlencoded-url>&token=<urlencoded-token>`
  (or `http://<ip>:<port>|<token>`), scanned with the in-screen QR camera scanner
  (`expo-camera`) in `Conexión`, or entered via copy/paste or manual URL + token fields.
- **Token storage:** the pairing token is stored in plain `AsyncStorage` on the device
  (`mott:pairing:v1`); this is documented as a current limitation.
- **Hub-first rule:** when paired and online, pulling a snapshot replaces catalogue,
  tables, open tabs, sales, expenses, suppliers and branding with hub data. When
  offline, the app keeps displaying the last good snapshot and queues operational writes
  in the local outbox instead of blanking the UI.

## Offline outbox

When paired with a hub that is temporarily unreachable, operational writes are persisted
to `AsyncStorage` (`mott:pending:v1`) and drained in FIFO order on the next sync:

- **Queued operations:** `UPSERT_PRODUCT`, `SAVE_TAB`, `CLOSE_TAB`, `ADD_EXPENSE`.
  Table and tab mutations coalesce per `tableId` so repeated offline edits or a tab
  close send the final state.
- **Online-only admin actions:** creating/deleting tables, supplier CRUD and saving
  branding require an active connection when paired and fail inline with a clear message.
- **Retry rules:**
  - `2xx`: operation succeeded and is removed from the outbox.
  - `4xx`: treated as a poison operation and dropped so it does not block the queue.
  - `5xx` or network/timeout error: increments `attempts` and stops draining at the
    first retryable failure to preserve ordering.
- **Max attempts:** `5` (`MAX_PENDING_ATTEMPTS`). Operations that reach 5 failed
  attempts are dropped as dead letters so `pending` can return to zero.

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
- The logo cannot be uploaded from the app (that needs an image-picker dependency);
  when paired with a PC hub that has a logo configured, the app displays the hub logo,
  and otherwise falls back to the shop's initial.

## Local data

State lives on the phone (`src/store.tsx`) and is persisted to `AsyncStorage` as one JSON
object (`mott:app-state:v1`), validated field by field on load so a truncated or older
payload falls back to the seeds instead of crashing. Writes are coalesced so rapid taps
never queue dozens of whole-state writes.

## Build

Local (Node 22+ and the Android SDK):

```bash
npm install
npx tsc --noEmit                      # strict typecheck
npm run test:hub                      # smoke test against a disposable mitt hub on :8099
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
| PC hub | optional: the app works alone, or pairs with `mitt` over LAN as the source of truth |

## Contributing

- Conventional Commits, one work unit per commit.
- Artifact language is English; user-visible copy stays Spanish, like the master.
- Run `npx tsc --noEmit` and `npm run test:hub` before pushing.

## Limitations

- Pairing token is stored in plain `AsyncStorage`.
- `GET /api/sales/today` on the hub uses the UTC day, not the bar's local timezone.
- No item removal/void from an open tab, no table rename, no product delete (availability toggle only), and no supplier ledger or balances.
- Not field-tested in a real bar yet.
