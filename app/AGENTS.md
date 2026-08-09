# app/ — Android module

- Thin glue only: parsing/decisions live in `core/`. If you are writing an `if`
  worth testing inside an Activity, move it to `core/` instead.
- The `<queries>` block in `AndroidManifest.xml` is load-bearing (Android 11
  package visibility for the Spotify deep link). Do not remove or "clean it up".
- Scanner is `zxing-android-embedded` via `ScanContract`; it manages the camera
  permission itself — do not add manual permission plumbing.
- User-visible strings: Slovak, in `res/values/strings.xml`.
- Verify: `make lint`, `make apk`; scanning/playback changes need a real device.
