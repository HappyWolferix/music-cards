# Code style

- Java 11 source level, plain Java — no Kotlin, no annotation processors.
- 4-space indent, `final` where practical, package `sk.musiccards.*`.
- Android classes are thin: no business logic in Activities. Anything with a
  branch worth testing goes to `core/` with a JUnit test.
- Javadoc on every public class: one short paragraph saying what it is for.
- Logging in `app/` uses `android.util.Log` with tag `"MusicCards"`; `core/` and
  `cards/` do not log (they return values or throw).
- User-visible strings live in `app/src/main/res/values/strings.xml` and are Slovak.
- No new third-party dependencies without asking. Current allowed set:
  appcompat, zxing-android-embedded (app), zxing core+javase + pdfbox (cards),
  junit (test).
