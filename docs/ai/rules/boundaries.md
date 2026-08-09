# Boundaries — things you must not do without explicit user approval

- Do not raise `minSdk` above 30 (Android 11) or change `applicationId`.
- Do not change the QR payload format. Printed cards encode `spotify:track:<id>`
  URIs; the parser may become more lenient, never stricter.
- Do not add Spotify Web API calls, OAuth flows, API keys or any secrets.
  Playback stays an ACTION_VIEW deep link handled by the installed Spotify app.
- Do not add Kotlin, Compose, or Play Services dependencies (ML Kit included) —
  scanning stays on ZXing so the app works offline and store-free.
- Do not move logic from `core/` into `app/`; direction of travel is the opposite.
- Do not commit `local.properties`, keystores, or anything under `build/`.
- Dependency and Gradle/AGP version bumps are user decisions — propose, don't apply.
