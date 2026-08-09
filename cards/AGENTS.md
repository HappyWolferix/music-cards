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
- `ImportPlaylists` (`make import`) regenerates `data/songs.csv` from the
  Exportify playlist exports in `songs/` (source list: `IMPORT_SRCS` in the
  Makefile). It dedupes by track id and by diacritic-folded title+artist and
  marks compilation-album years with `# CHECK YEAR`. **It overwrites the file**
  — hand-edited years are lost, so prefer fixing years after the deck is final.
- After changes: `make cards` and eyeball `build/cards/cards.html` in a browser.
