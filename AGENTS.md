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
- No navigation library: one `view` state in `App.tsx` drives the seven sections.
- Commands:
  - `npm install`
  - `npx tsc --noEmit` (typecheck; the gate used by CI)
  - `npx expo start` (dev server)
  - `npm run test:hub` (Node smoke test against a disposable hub; added by T1)
- CI: `.github/workflows/android.yml` runs install → typecheck → `expo prebuild`
  → `assembleRelease` → artifact. Tags `v*` publish the APK.

## 4. Current architecture

```
App.tsx                  shell: responsive rail/bottom nav, 7 sections
src/theme.ts             design tokens, palette from brand + accent
src/data.ts              domain types, seeds, money()
src/store.tsx            single JSON store persisted to AsyncStorage
src/ui.tsx               Card/Btn/Pill/Field/Num/Bar vocabulary
src/views/*.tsx          Panel, Mesas, Catálogo, Proveedores, Gastos,
                         Conexión, Personalizar
```

The store is a single object persisted under `mott:app-state:v1`. Every field is
validated on load so a truncated payload falls back to seeds.

## 5. Target architecture for hub connectivity

New modules (do not put wire logic inside views):

```
src/hub/types.ts         wire DTOs (snake_case, cents, RFC3339 strings)
src/hub/pairing.ts       parse mitt://pair?url=...&token=... and url|token
src/hub/client.ts        typed fetch client, bearer token, timeout, HubError
src/hub/mappers.ts       hub <-> app domain mapping (pure functions)
src/hub/queue.ts         persisted outbox: enqueue/list/remove/bump
src/hub/sync.ts          pullSnapshot() + drainQueue() (idempotent)
```

Store additions:

- `pairing: { baseUrl, token } | null`
- `connection: 'unpaired' | 'connecting' | 'online' | 'offline' | 'error'`
- `lastSyncAt: string | null`, `hubError: string | null`, `pending: number`
- `hubToday: { date, count, totalCents } | null`
- `logoUrl: string | null`
- Actions: `connect(input)`, `disconnect()`, `syncNow()`, and write-through
  actions used by views.

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

In progress (this session):
- Hub connectivity re-implementation, code cleanup, living context file.

Planned work units:
- **T1 — Hub wire layer + smoke test.** `src/hub/types.ts`, `pairing.ts`,
  `client.ts`, `mappers.ts`, `scripts/hub-smoke.mjs`, `npm run test:hub`.
- **T2 — Connection state + pull sync + Conexión UI.** Store pairing/connection,
  pull snapshot, real connect/disconnect/sync screen.
- **T3 — Mesas write-through + offline outbox.** Open/add/close against tabs;
  idempotent SAVE_TAB/CLOSE_TAB drain.
- **T4 — Remaining sections + honest cleanup.** Catálogo, Gastos, Proveedores,
  Personalizar, Panel; remove fake supplier balances and unused seed data.
- **T5 — Android cleartext + docs.** `expo-build-properties`
  (`android.usesCleartextTraffic: true`), README, final typecheck/smoke.

## 9. Session log

Newest first. Each entry: date, work unit, what changed, verification, next step.

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
