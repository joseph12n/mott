# mott

Bar POS Android app (waitstaff: pick table, add products, automatic bill). Offline-first against the mitt API.

## Quick path

Requirements: Java 17+, Android SDK with platform android-35 and build-tools 35 (set `ANDROID_HOME`, same value for `ANDROID_SDK_ROOT`).

```bash
export ANDROID_HOME=$HOME/android-sdk ANDROID_SDK_ROOT=$HOME/android-sdk
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

## Layout

- `settings.gradle.kts`, `build.gradle.kts` — root project `mott` with `:app` module.
- `gradle/libs.versions.toml` — version catalog (AGP, Kotlin, Compose BOM).
- `gradle.properties` — AndroidX, Kotlin style, JVM args; SDK path comes from the environment, never from committed files.
- `app/` — Android application module (`dev.mott.app`): Compose UI, shared design-token theme, unit tests.

## Notes

- No secrets in the repo: no `local.properties`, no keystores, no tokens. SDK location resolves via `ANDROID_HOME`.
- Theme roles copy the shared token set 1:1 (see mitt `docs/design-tokens.md`, reference only); dark is the default.

## Pairing

On first run the app asks to pair with the mitt hub. Scan the hub QR or paste the code; the app validates it against `GET /api/health` with the token and saves the pairing locally.

QR format (URL-encoded params):

```text
mitt://pair?url=http%3A%2F%2F192.168.1.20%3A8080&token=DUMMY-DUMMY-TOKEN-0000
```

Manual-paste fallback (`url|token`):

```text
http://192.168.1.20:8080|DUMMY-DUMMY-TOKEN-0000
```

Rules: scheme `mitt`, host `pair`, `http(s)` URL with explicit port, token of 16+ chars. The token above is a DUMMY placeholder — never commit a real one.
