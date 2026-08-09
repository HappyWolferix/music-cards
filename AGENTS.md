# AGENTS.md — music-cards

Android companion app for a physical music quiz game (Slovak songs).
Players scan a QR code on a paper card; the app starts that song in the Spotify app
on the same phone. Java, Android 11+ (minSdk 30). This file is the canonical entry
point for all coding agents; the AI configuration lives in `docs/ai/`.

## How to load context (do this literally)

1. Read this file.
2. Match the task against **[`docs/ai/manifest.yml`](docs/ai/manifest.yml)** — the single
   routing manifest. Its `keywords` and `folders` sections tell you which docs to read.
   The codebase is small; crawl source beyond the files you are changing only if needed.
3. Load the `AGENTS.md` overlay of every module you touch (`core/`, `app/`, `cards/`).
4. The always-applied rules are in `docs/ai/rules/`: `code-style.md`, `boundaries.md`,
   `verification.md`, `documentation.md`.
5. [`docs/architecture/overview.md`](docs/architecture/overview.md) explains the whole
   system in one page — read it for anything non-trivial.

## Modules

| Module | What | Runs on |
| ------ | ---- | ------- |
| `core/` | Pure-Java logic: Spotify link parsing, song catalog CSV | JVM (unit-tested) |
| `app/`  | Android app: scan QR (ZXing) → play via Spotify intent | Android 11+ |
| `cards/`| CLI tool: `data/songs.csv` → printable QR card sheets | JVM |

## Build and run

```sh
make test    # fast loop: JVM unit tests, no Android SDK needed
make apk     # debug APK (needs Android SDK, see RUNNING.md)
make cards   # printable card sheets -> build/cards/cards.html
```

Full setup (JDK, Android SDK paths): [`RUNNING.md`](RUNNING.md).

## Feedback loops — verify before you finish

Run every loop that matches your change, cheapest first
(details: [`docs/ai/rules/verification.md`](docs/ai/rules/verification.md)):

| Loop | Command | When |
| ---- | ------- | ---- |
| Docs validation | `make validate-ai-docs` | any docs/ or AGENTS.md change |
| Unit tests | `make test` | any `core/` change (and add tests for new logic) |
| Full compile | `make build` | any Java or resource change |
| Android lint | `make lint` | any `app/` change |
| Cards smoke | `make cards` | any `cards/` or `data/songs.csv` change |
| On-device | `make apk` + install | any scanning/playback behaviour change |

Deliver complete, verified changes. If a loop fails for reasons outside your change,
or a requirement is ambiguous, **ask the user** instead of guessing.

## Non-negotiables

- **Java only** (source level 11). No Kotlin.
- **minSdk stays 30** (Android 11). Do not raise it without asking.
- **All parseable/testable logic goes in `core/`**, not in Android classes.
  Android classes should be thin glue (see `SpotifyPlayer`, `MainActivity`).
- **QR payloads are plain Spotify track links** (`spotify:track:<id>` on printed
  cards). Never invent a custom QR format — cards already printed must keep working.
- **Playback is a deep-link intent to the Spotify app.** No Spotify Web API, no
  OAuth, no secrets in the repo. If richer control is ever needed, that is a
  user-level decision (App Remote SDK) — ask first.
- Full list: [`docs/ai/rules/boundaries.md`](docs/ai/rules/boundaries.md).

## Traps worth knowing

- **Android 11 package visibility**: the `<queries>` block in
  `app/src/main/AndroidManifest.xml` is what lets the app see Spotify. Removing it
  makes `startActivity` silently fall back to the browser on some devices.
- **`settings.gradle` skips `:app` when no Android SDK is configured** so that
  `make test`/`make cards` work anywhere. If `:app` "disappears" from a build,
  check `ANDROID_HOME`/`local.properties` first.

## Keeping the docs true

`docs/architecture/` describes current behaviour. If you change behaviour, update the
relevant doc in the same commit; add keywords to `docs/ai/manifest.yml` for new
concepts. If a doc and the code disagree, the code is right — fix the doc. Then run
`make validate-ai-docs`.
