# Music Cards

A do-it-yourself, Slovak-catalog music party game.

- **Paper cards**: front — song title, artist, release year; back — QR code.
- **Android app** (Android 11+, Java): scan the QR, the song plays in the
  Spotify app on the same phone.
- **Card generator**: turn `data/songs.csv` into printable double-sided sheets.

Start here:

- Playing / building / printing: [RUNNING.md](RUNNING.md)
- Architecture: [docs/architecture/overview.md](docs/architecture/overview.md)
- AI agents: [AGENTS.md](AGENTS.md) — the repo is set up for AI-assisted
  development (routing manifest, rules, fast feedback loops via `make`).
