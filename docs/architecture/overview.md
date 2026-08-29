# Architecture overview

## What the system is

A physical music quiz game (Slovak catalog). Each song has a paper
card: front = title + artist + release year, back = QR code. The Android app turns
a phone into the "record player": scan the QR, the song starts playing in the
Spotify app installed on the same phone (Premium account assumed).

## End-to-end flow

```
data/hitparada*.xls (IFPI SK + CZ radio chart snapshots, really HTML)
     │ (make import-charts: charts + wishlist merged, minus what the deck has)
     ▼
data/candidates.csv  (songs we want; no Spotify link yet)
     │ (by hand: paste a Spotify link, copy the first four fields across)
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
  + per-song QR PNGs. `ImportCharts` (`make import-charts`): chart merger.

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

`data/songs.csv` is maintained **by hand**. Nothing generates it, so nothing can
overwrite it: rows are added by pasting a finished line from `data/candidates.csv`.
(An earlier `make import` regenerated it wholesale from Spotify playlist exports;
it was removed because it silently destroyed hand-edited rows and years.)

IFPI publishes "SK - RADIO - TOP 50 SK" and "CZ - RADIO - TOP 50 CZ" weekly
snapshots as `hitparada.xls` — despite the extension they are HTML tables, parsed by
`core/ChartExport`. They carry no Spotify track id, so they can never become cards
directly. `make import-charts` (`cards/ImportCharts`) diffs them against the catalog
and writes `data/candidates.csv`, a shopping list ordered by best chart
position; the ids arrive later through the playlist route above.

There is one deck: SK and CZ charts merge into that single candidate file and into
one `data/songs.csv`. A song that charted in both countries is one card — the
`charts` column records where it appeared (`cz sk`) and the `weeks` column keeps
every placing (`cz:201351#2 sk:202351,52#23`). Which chart a snapshot holds is read
from its header, never from its name: every download is called `hitparada.xls`.

**The repo keeps only CSVs — the raw .xls snapshots are not stored.**
`data/candidates.csv` is therefore the durable record, and `ImportCharts` reads it
back in as one of its own inputs (fully, before writing). Runs accumulate: dropping
a new snapshot into `data/` adds to the list, and a song leaves it only by reaching
the catalog or being deleted by hand. Because the file is its own input, every
column must round-trip — the `charts` column is what tells a later run that a song
came from a wishlist rather than a chart.

There are exactly two song files: `data/songs.csv` (real cards, every row has a
Spotify link) and `data/candidates.csv` (everything we still want). Wishlist songs
and charted songs live together in the second one, sorted by best chart position
with never-charted entries (position 999) last.

The candidate file's first four columns are exactly the catalog shape
(`title;artist;year;link`), so finishing a song is: paste its Spotify link into the
link column, copy those four fields into `data/songs.csv`, delete the line. A pasted
`https://open.spotify.com/track/...` URL is normalized to `spotify:track:<id>` on the
next `make import-charts`, and anything unparsable is blanked so it stays visibly
unfinished rather than silently broken.

Nothing without a link may enter `data/songs.csv`: `SongCatalog` rejects a row
without a valid `spotify:track:` link, so parking a wish there breaks `make cards`.
That is why `data/candidates.csv` exists as the waiting room.
