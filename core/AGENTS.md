# core/ — pure-Java logic

- Zero Android imports; must compile and test on a plain JVM.
- `SpotifyLink.parseTrackId` is the only place track references are parsed or
  validated. Extend it there; never duplicate the logic.
- Input parsers are tolerant of their source, not of the catalog: `ChartExport`
  skips rows it cannot read, while `SongCatalog` throws with a line number.
- Parsing may get more lenient over time, never stricter — printed cards must
  keep scanning forever.
- Every public method gets JUnit coverage in `src/test/java`, including the
  rejection/failure paths. Run `make test`.
