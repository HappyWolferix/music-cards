# Architecture overview

## What the system is

A physical music quiz game (Slovak catalog). Each song has a paper
card: front = title + artist + release year, back = QR code. The Android app turns
a phone into the "record player": scan the QR, the song starts playing in the
Spotify app installed on the same phone (Premium account assumed).

## End-to-end flow

```
songs/*.csv (Exportify playlist exports)
     │ (make import: merge, dedupe, flag compilation years)
     ▼
data/songs.csv ──(make cards)──> build/cards/cards.pdf ──duplex print──> paper cards
                                                                       │
                                                              player scans QR
                                                                       │
MainActivity ──ScanContract(ZXing)──> QR payload "spotify:track:<id>"
      │                                        │
      │                          core/SpotifyLink.parseTrackId
      │                                        │
      └──SpotifyPlayer.play──> ACTION_VIEW spotify:track:<id> ──> Spotify app plays
```

## The three modules

- **`core/`** — pure Java, zero Android imports. `SpotifyLink` (the single
  authority on what a valid track reference is), `Song`, `SongCatalog` (CSV
  parser). Everything here is JVM-unit-tested (`make test`), which is the fast
  feedback loop for agents.
- **`app/`** — two small classes. `MainActivity`: one button, launches the
  ZXing scanner via `ScanContract`, shows status text. `SpotifyPlayer`: fires
  the deep-link intent, falls back to the web player URL if Spotify is missing.
- **`cards/`** — card tooling CLIs. `GenerateCardsPdf` (`make cards`): a duplex
  A4 PDF, 4×5 = 20 cards per sheet, alternating front (title/artist/year) and
  back (QR) pages; back pages mirror column order so cards align when the sheet
  is flipped on the long edge. `GenerateCards` (`make cards-html`): HTML preview
  + per-song QR PNGs. `ImportPlaylists` (`make import`): playlist merger.

## Decisions and why

- **QR encodes a plain `spotify:track:` URI**, not a custom format: printed
  cards are immutable, and plain URIs mean any scanner app can also play them —
  no lock-in to this app.
- **Playback via ACTION_VIEW intent**, not the Spotify App Remote SDK or Web
  API: zero auth, zero secrets, works the moment Spotify is installed. Cost:
  the app cannot pause/seek or hide the track name (players should not stare at
  the phone anyway — the phone is face-down/held by the DJ by the game rules).
  Revisit only if hiding metadata becomes a hard requirement.
- **ZXing (`zxing-android-embedded`), not ML Kit**: no Play Services
  dependency, fully offline, Java-native, handles camera permission itself.
- **minSdk 30**: the requirement is Android 11+; staying exactly there keeps
  the `<queries>` package-visibility handling honest (it is mandatory from 30).
- **`settings.gradle` conditionally includes `:app`** so the JVM feedback loops
  (`make test`, `make cards`) run on machines with no Android SDK — e.g. CI or
  an agent sandbox.

## Data

`data/songs.csv` — `title;artist;year;spotify-link`, `#` comments. This file is
the single source of truth for the deck; the app itself ships no song data (it
just resolves whatever QR it sees).

`songs/` holds raw Spotify playlist exports (Exportify CSV format). `make import`
(`cards/ImportPlaylists` + `core/PlaylistExport`) merges the SK/CZ ones listed in
the Makefile's `IMPORT_SRCS` into `data/songs.csv`: dedupes by track id and by
diacritic-folded title+artist, sorts by year, and prefixes songs whose album looks
like a compilation with a `# CHECK YEAR` comment — Exportify only knows the album
release date, and for this game the original year is what matters.
