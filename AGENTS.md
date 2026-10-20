# mott — agent context (living document)

This file is the single source of truth for any agent working in this repository.
It is read automatically by Antigravity (`agy`) and other agents that support
`AGENTS.md`. Update the **Status** and **Session log** sections after every work
unit. Keep it under 24 KB.

Last updated: 2026-10-20

## 1. What this repo is

`mott` is the **React Native (Expo) mobile app** for a small bar. It is the mobile
companion of `mitt`, the **PC hub** (Go + SQLite) that lives at
`/home/joseph/Documentos/mitt`.

- The app must work **standalone** (phone-only, no PC) and, when paired, use the
  PC hub as the **source of truth** for catalogue, tables, open tabs, sales,
  expenses, suppliers and branding.
- Kotlin is gone. Do not add Kotlin, Gradle app modules, or Android native code
  unless a task explicitly requires it. The old Kotlin client was deleted in
  commit `c259ce5`; it remains available in git history as a reference only.
- The visual master is `mottandmittdesing/` (web/Figma). The RN app is a port of
  it. Do not redesign sections without an explicit task.

## 2. Non-negotiable rules

- **Language:** code, comments, docs, commit messages and agent artifacts in
  English. User-visible copy stays Spanish (the bar's language).
- **No dialogs, sheets, dropdowns or modals.** Destructive actions confirm with a
  second tap inside the screen. Table detail is an in-screen pane.
- **One tap target per tile.** Every control is at least 48 dp tall. Long names
  ellipsize; they never overlap. Status is always text + dot, never color alone.
- **One writer at a time.** Do not run parallel writers on this worktree.
- **Do not touch the production hub.** A real `mitt` instance runs on `:8080`
  with the bar's data (`/var/lib/mitt/mitt.db`). Read-only requests are fine;
  never write to it. For write tests, start a disposable hub on `:8099` with a
  temp DB (see §7).
- **Do not commit secrets.** The pairing token is never committed, logged, or
  written into docs. It only lives in AsyncStorage on the device.
- **Conventional Commits**, one work unit per commit. Do not add AI attribution.
- **TypeScript strict.** Run `npx tsc --noEmit` before reporting done.

## 3. Stack and commands

- Expo SDK 58, React Native 0.88, React 19.3, TypeScript 5.9.
- State: React context + AsyncStorage (`src/store.tsx`).
- Camera: `expo-camera` (`~58.0.11`) for QR pairing; the camera permission is
  configured in `app.json`.
- No navigation library: one `view` state in `App.tsx` drives the seven sections.
- Commands:
  - `npm install`
  - `npx tsc --noEmit` (typecheck; the gate used by CI)
  - `npx expo start` (dev server)
  - `npm run test:hub` (Node smoke test against a disposable hub; added by T1)
- CI: `.github/workflows/android.yml` runs install → typecheck → `expo prebuild`
  → `assembleRelease` → artifact. Tags `v*` publish the APK.

## 4. Architecture

```
App.tsx                  shell: responsive rail/bottom nav, 7 sections
app.json                 Expo config + expo-build-properties (android cleartext) +
                         expo-camera (QR permission)
src/theme.ts             design tokens, palette from brand + accent
src/presets.ts           22 design presets from the web master
src/usePalette.ts        palette hook derived from store brand + dark mode
src/data.ts              domain types, seeds, money(), isToday(), formatDay()
src/store.tsx            single JSON store + hub connection/sync + write-through actions
                         + debounced persistence + AppState revalidation
src/ui.tsx               Card/Btn/LinkBtn/Pill/StatusPill/Header/Field/Num/Bar vocabulary
                         (Pressable buttons with busy/loading states)
src/views/*.tsx          Panel, Mesas, Catálogo, Proveedores, Gastos,
                         Conexión (manual + in-screen QR scanner), Personalizar
src/hub/types.ts         wire DTOs (snake_case, cents, RFC3339 strings)
src/hub/model.ts         app-facing view models for hub snapshots
src/hub/pairing.ts       parse mitt://pair?url=...&token=... and url|token
src/hub/client.ts        typed fetch client, bearer token, 4s timeout, HubError
src/hub/mappers.ts       hub <-> app domain mapping (pure functions)
src/hub/queue.ts         persisted outbox: enqueue/coalesce/list/remove/bump
src/hub/sync.ts          pullSnapshot() + drainQueue() (idempotent)
scripts/hub-smoke.mjs    Node smoke test against a disposable mitt hub on :8099
```

Persistence keys in `AsyncStorage`:
- `mott:app-state:v1` (with legacy fallback `mitt:app-state:v1`): domain state,
  `productAvailable`, `productCategories`, `logoUrl`, `lastSyncAt`, `hubToday`.
  Validated field by field on load so a truncated payload falls back to seeds.
  Writes are debounced (~400ms); at most 100 orders are persisted while the
  in-memory state keeps the full snapshot.
- `mott:pairing:v1`: `{ baseUrl, token }`.
- `mott:pending:v1`: FIFO offline outbox queue (`PendingOp[]`).

## 5. Hub connectivity rules

Store connection state and actions:

- `pairing: { baseUrl, token } | null`
- `connection: 'unpaired' | 'connecting' | 'online' | 'offline' | 'error'`
- `lastSyncAt: string | null`, `hubError: string | null`, `pending: number`
- `hubToday: { date, count, totalCents } | null`
- `logoUrl: string | null`
- Actions: `connect(input)`, `disconnect()`, `syncNow()`, `createTable`,
  `deleteTable`, `openTable`, `addItem`, `closeTable`, `createProduct`,
  `setProductAvailable`, `addExpense`, `createSupplier`, `updateSupplier`,
  `deleteSupplier`, `saveBranding`. `connect` accepts a scanned or pasted
  `mitt://pair?...` code, the `url|token` form, or `{ baseUrl, token }`.

**Pairing.** The `Conexión` screen scans the hub QR with an in-screen `expo-camera`
pane (no modal); `parsePairingCode` validates it and `connect` starts the pull.
Manual paste/URL remains available as the fallback.

**Persistence and liveness.** Store writes are debounced (~400ms) and happen off
the tap path; on `background`/`inactive` the latest state is flushed immediately.
Persistence keeps at most 100 orders while the in-memory state keeps the full
snapshot. When paired, returning to `active` revalidates with `GET /api/pairing`
(2s timeout) and updates the connection badge without a full pull. The hub client
defaults to a 4s timeout; network failures mark the connection `offline`.

**Hub-first rule.** When paired and online, a pull replaces catalogue, tables,
orders, expenses, suppliers and branding with hub data. Local seeds remain only
for the unpaired/offline case. A pull failure keeps the last good snapshot and
sets `connection: 'offline'`; never blank the UI.

**Offline outbox.** Only these operations are queued:
`UPSERT_PRODUCT`, `SAVE_TAB`, `CLOSE_TAB`, `ADD_EXPENSE`.
Admin actions (create/delete table, supplier CRUD, branding) require connectivity
and fail inline with a clear message. Queue rules: 2xx = done, 4xx = poison
(drop), 5xx/network = retry with attempt bump; stop at the first retryable
failure to preserve order; max 5 attempts.

## 6. mitt hub API — essentials

Base URL is `http://<pc-lan-ip>:8080` by default. Plain HTTP, no CORS, no mDNS.
Auth is `Authorization: Bearer <pairing-token>` on everything except:
`GET /api/health`, `GET /api/branding`, `GET /api/branding/logo`.

Pairing QR content (rendered by the PC) is:

```
mitt://pair?url=<urlencoded base URL>&token=<urlencoded token>
```

Routes used by the app:

| Method | Path | Notes |
|---|---|---|
| GET | `/api/health` | public, `{"status":"ok"}` |
| GET | `/api/pairing` | authed; returns `{url, pairing_code}` |
| GET | `/api/products` | `{"products":[{id,name,price_cents,available}]}` |
| POST | `/api/products` | `{name, price_cents, available?}` |
| PATCH | `/api/products/{id}` | partial; no DELETE exists |
| GET | `/api/tables` | `{"tables":[{id,label,occupied}]}` |
| POST | `/api/tables` | `{label}`; 1..40 runes |
| DELETE | `/api/tables/{id}` | 409 `table_occupied` |
| GET | `/api/tabs/open` | `{"tabs":[{id,table_id,status,opened_at,items,total_cents}]}` |
| POST | `/api/tabs` | `{table_id}`; 200 existing / 201 new |
| POST | `/api/tabs/{id}/items` | `{product_id, qty}`; same product adds qty |
| POST | `/api/tabs/{id}/close` | returns the sale; id = tab id |
| GET | `/api/sales?limit=` | newest first, max 500 |
| GET | `/api/sales/today` | `{date,count,total_cents}` in **UTC** |
| GET/POST | `/api/expenses` | `{description, qty, cost_cents, date}` |
| GET/POST/PATCH/DELETE | `/api/suppliers` | flat `{id,name,phone,note}` |
| GET | `/api/branding` | `{shop_name,primary,accent,background,has_logo,updated_at}` |
| GET | `/api/branding/logo` | raw image bytes; public |
| PATCH | `/api/branding` | `{shop_name?,primary?,accent?,background?,logo_data_url?}` |

Gotchas:
- Money is **integer cents** on the wire. The app domain currently uses whole
  pesos/dollars; convert at the boundary (`cents / 100`).
- IDs are server-generated 32-char hex. Never send `id` in create bodies.
- `POST /api/tabs` may return 200 or 201. Accept both.
- 204 responses have no body; do not call `.json()`.
- PATCH uses pointers: omitted key keeps the stored value.
- `GET /api/sales/today` uses the **UTC** day, not the bar's local day.
- There is no bulk table registration, no item removal, no table rename, no
  supplier catalog/ledger, no products DELETE, no WebSocket/SSE.
- Validate a token with `GET /api/products` or `/api/pairing`; `/api/health`
  accepts any token.

## 7. Disposable hub for tests

Never test writes against `:8080`. Use:

```bash
mkdir -p /tmp/mott-hub-test
/home/joseph/Documentos/mitt/mitt \
  -db /tmp/mott-hub-test/mitt.db \
  -addr 127.0.0.1:8099 \
  -token test-token-0123456789abcdef
```

The smoke test (`npm run test:hub`) starts/stops this itself. Read-only checks
against `:8080` are allowed.

## 8. Status

Current branch: `feat/react-native-app` (React Native port already merged into
the branch; Kotlin deleted). Working tree clean at the start of this session.

Done:
- RN port of the seven sections with the web master's tokens.
- Local standalone store + AsyncStorage.
- Android CI build.
- **T1 — Hub wire layer + smoke test.** `src/hub/types.ts`, `src/hub/pairing.ts`,
  `src/hub/client.ts`, `src/hub/model.ts`, `src/hub/mappers.ts`,
  `scripts/hub-smoke.mjs`, `npm run test:hub`.
- **T2 — Connection state + pull sync + Conexión UI.** `src/hub/queue.ts`,
  `src/hub/sync.ts`, `src/store.tsx`, `src/views/Conexion.tsx`, `src/ui.tsx`,
  `src/data.ts`, `src/views/Panel.tsx`.
- **T3 — Mesas write-through + offline outbox.** `src/store.tsx`,
  `src/views/Mesas.tsx`, `src/hub/queue.ts`, `src/hub/sync.ts`, `src/data.ts`,
  `scripts/hub-smoke.mjs`.
- **T4 — Remaining sections + honest cleanup.** `src/data.ts`, `src/store.tsx`,
  `src/views/Catalogo.tsx`, `src/views/Gastos.tsx`, `src/views/Proveedores.tsx`,
  `src/views/Panel.tsx`, `src/views/Personalizar.tsx`, `src/views/Mesas.tsx`.
- **T5 — Android cleartext + docs + final verification.** `app.json`,
  `package.json`, `package-lock.json`, `README.md`, `.gitignore`, `App.tsx`,
  `src/store.tsx`, `src/views/Panel.tsx`, `AGENTS.md`.
- **P1 — Mobile performance and touch reliability (T1).** `src/ui.tsx` (`Pressable`
  buttons with ripple/pressed/disabled/loading), busy guards in `Catalogo`,
  `Conexion`, `Gastos`, `Mesas`, `Personalizar`, `Proveedores`, debounced
  persistence + 100-order cap + `AppState` foreground revalidation in
  `src/store.tsx`, and a 4s hub client timeout in `src/hub/client.ts`.
  Commits `cc3a594`, `db188a1`.
- **P2 — QR pairing (T2).** `expo-camera` (`~58.0.11`), `app.json` camera plugin
  with `recordAudioAndroid: false`, in-screen QR scanner in
  `src/views/Conexion.tsx`, README pairing docs.
- **R1 — Test release `v0.5.0-rn-perf-qr`.** Version bumped to `0.5.0`; CI
  published the prerelease APK from commit `cbdb91a` (Android run `39503095218`
  succeeded in 8m36s).

In progress (this session):
- None (T1–T5, P1/P2, and R1 complete).

Next steps:
- On-device test of the P1/P2 release APK: first-tap response, no duplicate
  submits, and QR pairing against a LAN `mitt` hub.
- If the app still feels heavy on device after P1, evaluate local-first/optimistic
  operational writes and `FlatList` virtualization for large lists.
- Test APK: `v0.5.0-rn-perf-qr`
  (https://github.com/joseph12n/mott/releases/tag/v0.5.0-rn-perf-qr).
- Open PR for `feat/react-native-app` and verify the GitHub Actions release APK build.

## 9. Session log

Newest first. Each entry: date, work unit, what changed, verification, next step.

- **2026-10-20 — R1 (test release).** Bumped `package.json`, `package-lock.json`,
  and `app.json` to `0.5.0` (commit `cbdb91a`), pushed the branch and tag
  `v0.5.0-rn-perf-qr`. CI Android run `39503095218` succeeded and published the
  prerelease APK `mott-v0.5.0-rn-perf-qr.apk` (91,538,812 bytes).
  Release: https://github.com/joseph12n/mott/releases/tag/v0.5.0-rn-perf-qr
  Next: on-device test of first-tap response, duplicate prevention, and QR pairing.

- **2026-10-20 — P2 (T2 QR).** Added `expo-camera` (`~58.0.11`) and the in-screen
  QR scanner in `Conexión`: `CameraView` with QR `barcodeScannerSettings`,
  `useCameraPermissions` inline states, a synchronous scan lock with a 1.8s retry,
  `parsePairingCode` + `connect`, and the manual paste/URL fallback. Added the
  `expo-camera` plugin with `recordAudioAndroid: false` and the Spanish camera
  permission; updated the README pairing section.
  Verification: `npx tsc --noEmit` exited 0; `npm run test:hub` passed 18/18;
  `npx expo export --platform android` exited 0 (657 modules, 1.8 MB);
  `npx expo config --type public --json` resolved `android.permission.CAMERA`;
  the prebuild manifest had `CAMERA`, no `RECORD_AUDIO`, and kept
  `usesCleartextTraffic="true"`; the generated `android/` was removed.
  Next: on-device QR scan test against a LAN `mitt` hub.

- **2026-10-20 — P1 (T1 performance).** Replaced `Text.onPress` with full-target
  `Pressable` buttons (ripple, pressed/disabled/loading, accessibility) and added
  state + ref busy guards to the six async views to prevent double submissions.
  Moved persistence to a 400ms debounced effect with a 100-order cap and immediate
  background/inactive flush with the latest state; added `AppState` foreground
  revalidation with `GET /api/pairing` (2s) and changed the client default timeout
  to 4s.
  Verification: `npx tsc --noEmit` exited 0; `npm run test:hub` passed 18/18;
  `npx expo export --platform android` exited 0 (650 modules, 1.7 MB).
  Commits `cc3a594`, `db188a1`.
  Known tradeoff: the 100-order cap chooses the newest end by pairing state; after
  a disconnect with more than 100 hub orders it can keep the wrong end until the
  next pull. The hub remains the source of truth.
  Next: P2 QR pairing.

- **2026-10-20 — T5.** Installed `expo-build-properties` (`~58.0.11`) and configured
  `android.usesCleartextTraffic: true` in `app.json` so the release APK can reach the
  LAN `mitt` hub over plain HTTP. Rewrote `README.md` to document optional `mitt` PC
  hub pairing, the flat `Proveedores` model, `Conexión`, `Mesas`, the `Offline outbox`
  queue and retry rules, `npm run test:hub`, and current limitations. Removed unused
  store fields/setters (`hubSuppliers`, `logo`, `setProducts`, `setTables`, `setOrders`,
  `setExpenses`, `setSuppliers`, `setName`, `setAccent`, `setAccent2`, `setLogo`) in
  `src/store.tsx`, unused default `React` imports in `App.tsx` and `src/views/Panel.tsx`,
  and the leftover Gradle `local.properties` entry in `.gitignore`.
  Verification: `npx expo config --type public --json` resolved the `expo-build-properties`
  plugin with `{"android":{"usesCleartextTraffic":true}}`; `npm install` exited 0
  (`up to date, audited 471 packages`); `npx tsc --noEmit` (and `--noUnusedLocals
  --noUnusedParameters`) exited 0 with no output; `npm run test:hub` passed all 18
  checks (`PASS 1. health()` .. `PASS 18. outbox ...`);
  `grep -rn "SupProduct\|Purchase\|method\|purchases" src App.tsx` returned only
  HTTP request `method` references in `src/views/Conexion.tsx` and `src/hub/client.ts`.
  A Metro production export (`npx expo export --platform android`) exited 0
  (650 modules, 1.7 MB Hermes bundle), and `npx expo prebuild --platform android
  --no-install` exited 0 with `android:usesCleartextTraffic="true"` in the generated
  manifest (the generated `android/` was removed). Local Android APK build was not
  run (no local Android SDK installed and `android/` is regenerated in CI via
  `.github/workflows/android.yml`).
  Next: QR scanner, on-device field test in a bar, and PR.

- **2026-10-20 — T4.** Completed hub wiring for `Catálogo`, `Gastos`,
  `Proveedores`, `Panel`, and `Personalizar`, and cleaned up the domain in
  `src/data.ts` (flat `Supplier { id, name, phone, note }`, removed `SupProduct`
  and `Purchase`, no `Order.method`, added `isToday` and `formatDay`). Added
  `productCategories: Record<string, string>` and write-through/offline actions
  (`createProduct`, `setProductAvailable`, `addExpense`, `createSupplier`,
  `updateSupplier`, `deleteSupplier`, `saveBranding`) in `src/store.tsx`, plus
  unavailable-product disable state in `src/views/Mesas.tsx`.
  Verification: `grep -rn "method\|purchases\|SupProduct\|owed(" src App.tsx`
  returned only HTTP request `method` references in `src/views/Conexion.tsx` and
  `src/hub/client.ts`; `npx tsc --noEmit` exited 0 with no output; `npm run test:hub`
  passed all 18 checks (`PASS 1. health()` .. `PASS 18. outbox ...`).
  Orchestrator follow-up: offline `createProduct`/`addExpense` now return true
  after queuing so the views clear the form on success instead of reading a
  stale `hubError`; `setProductAvailable` distinguishes unpaired from
  paired-offline and shows an inline error instead of silently losing the
  change; the hub logo falls back to the initial on load error; Panel labels
  the hub-day order count as UTC.
  Next: T5 Android cleartext config, README, and final verification.

- **2026-10-20 — T3.** Implemented `Mesas` write-through and offline outbox
  actions (`createTable`, `deleteTable`, `openTable`, `addItem`, `closeTable`) in
  `src/store.tsx`, per-table outbox coalescing (`enqueueSaveTab`,
  `enqueueCloseTab`) in `src/hub/queue.ts`, offline `CLOSE_TAB` line reconciliation
  in `src/hub/sync.ts`, non-optional `Table.hubTabId: string | null` and complete
  removal of `Order.method` in `src/data.ts`, and wired `src/views/Mesas.tsx` to
  the store actions with status pills and inline error/queued feedback. Extended
  check 18 in `scripts/hub-smoke.mjs` with live disposable-hub assertions for
  coalescing, idempotent `SAVE_TAB` re-drain, and `CLOSE_TAB` drain.
  Verification: `npx tsc --noEmit` exited 0 with no output; `npm run test:hub`
  passed all 18 checks (`PASS 1. health()` .. `PASS 18. outbox ...`).
  Known tradeoff: a `CLOSE_TAB` retried after the hub closed the tab but the
  response was lost can reopen and re-close it. The alternative (treat "no open
  tab" as done) would lose every sale created and closed while offline.
  Next: T4 remaining sections and honest cleanup.

- **2026-10-20 — T2.** Implemented the offline outbox queue (`src/hub/queue.ts`
  persisted under `mott:pending:v1`), snapshot pull and idempotent FIFO queue
  drain (`src/hub/sync.ts`), store connection/pairing state and `connect` /
  `disconnect` / `syncNow` actions (`src/store.tsx` with `mott:pairing:v1` and
  `mott:app-state:v1`), real `Conexión` screen (`src/views/Conexion.tsx`), and
  `Field` (`secure`, `autoCapitalize`, `url` keyboard) + `Pill` (`dotColor`) in
  `src/ui.tsx`. Updated `src/data.ts` (`Table.hubTabId`, `seedTables` with
  `hubTabId: null`, `seedOrders` without `method`) and `src/views/Panel.tsx`
  (replaced `o.method` pill with `Cobrada`); kept `hubTabId?: string | null` and
  `method?: string` optional on the types so `src/views/Mesas.tsx` stays untouched
  until T3.
  Verification: `npx tsc --noEmit` exited 0 with no output; `npm run test:hub`
  passed all 18 checks (`PASS 1. health()` .. `PASS 18. outbox ...`).
  Orchestrator follow-up: fixed `drainQueue` dead-letter handling (exhausted
  ops were never removed, so `pending` could never reach zero) and added smoke
  check 18 for delta, idempotent SAVE_TAB, poison drop, retry and exhaustion.
  Next: T3 Mesas write-through and offline outbox.

- **2026-10-20 — T1.** Implemented the hub wire layer (`src/hub/types.ts`,
  `src/hub/pairing.ts`, `src/hub/client.ts`, `src/hub/model.ts`,
  `src/hub/mappers.ts`) and disposable-hub smoke script (`scripts/hub-smoke.mjs`,
  `npm run test:hub` in `package.json`, `allowImportingTsExtensions` in
  `tsconfig.json`). Note: `scripts/hub-smoke.mjs` resolves `MITT_BIN` first, then
  prefers `/home/joseph/Documentos/mitt/bin/mitt-linux` (and `/usr/bin/mitt`)
  before `/home/joseph/Documentos/mitt/mitt` because the root `mitt` binary was a
  stale pre-API stub while `bin/mitt-linux` is the v0.4.0 build from `make build-linux`.
  Verification: `npm run test:hub` passed all 17 checks (`PASS 1. health()` ..
  `PASS 17. HubError on 401`) against a disposable hub on `127.0.0.1:8099`;
  `npx tsc --noEmit` exited 0 with no output.
  Next: T2 connection state, pull sync, and Conexión UI.

- **2026-10-20 — T0.** Created this living context file and the ODD task document.
  Confirmed Orca can drive the `agy` terminal (Antigravity CLI 1.4.3, Gemini 4
  Argon High) and that `mitt` is running on `:8080`. Mapped the hub API and
  recovered the old Kotlin sync architecture from `c259ce5^` for reference.
  Next: T1 hub wire layer.

## 10. Update protocol

After every task:

1. Update **Status** (done / in progress / next) and prepend a **Session log**
   entry with the verification command and its observed result.
2. Record failed, skipped or pending checks honestly. Never claim a check you
   did not run.
3. Do not overwrite another feature's entry. This file is shared; append.
4. If you change the hub contract, update §6 in the same commit.
