# Verification — run the loops before you claim "done"

Cheapest first; run every loop that matches your change:

1. `make validate-ai-docs` — after any change to AGENTS.md files or `docs/`.
2. `make test` — after any `core/` change. New logic in `core/` **must** come
   with unit tests in the same commit; aim to cover the failure paths too.
3. `make build` — after any Java or resource change anywhere. This compiles
   `:app` when an Android SDK is configured; make sure it is (see RUNNING.md),
   otherwise you have only compiled the JVM half.
4. `make lint` — after `app/` changes; fix or explicitly justify new warnings.
5. `make cards` — after `cards/` or `data/songs.csv` changes; then eyeball
   `build/cards/cards.html` (font fits on card? QR present for every song?).
6. On-device (`make apk`, `adb install`) — required for changes to scanning or
   playback behaviour; there is no emulator harness for the camera. If you have
   no device, say so in your summary instead of pretending it was verified.

A failing loop you did not cause: stop and report it, do not paper over it.
