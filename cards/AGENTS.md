# cards/ — printable card generator (and data/songs.csv)

- JVM CLIs, run via `make cards` (PDF, the real deliverable) and `make cards-html`
  (preview); output goes to `build/cards/` (never commit it).
- QR payload is `SpotifyLink.toUri(trackId)` — the same string the app parses.
  Changing what gets encoded breaks the printed/physical contract; don't.
- Duplex contract (both generators): front and back pages alternate, back pages
  mirror the COLUMN order (long-edge flip on portrait A4 mirrors horizontally).
  Front slot (row, col) must equal back slot (row, COLS-1-col) — if you touch the
  grid, re-verify by decoding a corner QR from a rendered page (gs + zxing).
- `GenerateCardsPdf`: 4x5 grid, 20 cards/A4, PDFBox, bundled DejaVu fonts (PDF
  base-14 fonts cannot render Slovak diacritics — keep the TTFs embedded).
- `data/songs.csv` format: `title;artist;year;spotify-link` — validated by
  `core/SongCatalog`; `make cards` fails with a line number on bad data.
- `data/songs.csv` is hand-maintained; **no tool writes it**. There used to be a
  `make import` that regenerated it from Spotify playlist exports — it was deleted
  because it wiped hand-edited rows. Do not reintroduce a generator for this file.
- Two song files, no more: `data/songs.csv` (real cards, every row has a link) and
  `data/candidates.csv` (everything we still want, no links yet — charted songs and
  wishlist entries together). Do not reintroduce per-country or per-origin files.
- `ImportCharts` (`make import-charts`) reads everything in `CANDIDATE_SRCS` — any IFPI
  chart snapshots dropped in `data/` (`hitparada*.xls`, SK or CZ) plus the candidate
  list itself — and rewrites `data/candidates.csv`, best chart position first, wishlist
  entries (position 999) last. It never touches `data/songs.csv`: chart rows have no
  track id. A candidate becomes a card by hand: paste a Spotify link into its link
  column, copy the first four fields into `data/songs.csv`, delete the line.
- It accepts two row shapes: 4 columns is a wishlist row (`title;artist;year;TODO`,
  the catalog shape without a link), 7 columns is a candidate row. A song wanted for
  several reasons merges into one row (`charts` reads e.g. `cz sk` or `sk wishlist`).
- **Only CSVs are kept**; the `.xls` downloads are deleted after import. That makes
  `data/candidates.csv` the durable record — `ImportCharts` reads its own output back
  in, so runs accumulate and never drop a song whose snapshot is gone. Do not "clean
  up" that self-reference in `CANDIDATE_SRCS`; it is what prevents data loss.
- Because the output is also an input, **every column must round-trip**. The `charts`
  column is the only record that a song came from the wishlist; infer that flag from
  anything else and a re-run silently deletes every wishlist song (it once did).
- Every download is called `hitparada.xls` whatever chart it holds, so a second batch
  silently overwrites the first. Import and delete them promptly.
- `data/candidates.csv` is the parking lot for wanted songs with no link yet.
  Never put a link-less or `TODO` row in `data/songs.csv` — `make cards` fails on it.
- After changes: `make cards` and eyeball `build/cards/cards.html` in a browser.
