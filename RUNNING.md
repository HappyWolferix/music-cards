# Running & building music-cards

## Requirements

- **JDK 17–21** (AGP 8.5 rejects newer). The Makefile auto-falls-back to
  `/opt/java/jdk-21*` if `JAVA_HOME` is unset or points at a JRE.
- **Android SDK** for building the APK (platform 34, build-tools 34). Point at it
  with either:
  - `local.properties` in the repo root: `sdk.dir=/path/to/android-sdk`, or
  - environment: `export ANDROID_HOME=/path/to/android-sdk`

  Without an SDK, `make test` and `make cards` still work — `settings.gradle`
  skips the `:app` module.

### Installing the SDK without Android Studio

```sh
mkdir -p /opt/android-sdk && cd /opt/android-sdk
curl -sL -o ct.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q ct.zip && rm ct.zip
mkdir -p cmdline-tools && mv cmdline-tools latest && mv latest cmdline-tools/
yes | cmdline-tools/latest/bin/sdkmanager --sdk_root=$PWD \
    "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

## Commands

```sh
make test    # JVM unit tests (fast agent feedback loop)
make build   # compile everything
make apk     # app/build/outputs/apk/debug/app-debug.apk
make cards   # build/cards/cards.pdf — duplex-printable card sheets
make cards-html  # HTML preview variant (+ QR pngs)
make lint    # Android lint
```

## Installing on a phone

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The phone needs the Spotify app installed and logged in (Premium for full
tracks). First scan will ask for the camera permission.

## Making a real deck

1. Put your songs into `data/songs.csv` (`title;artist;year;spotify-link`).
   Use Spotify's *Share → Copy Song Link* URLs directly.
2. `make cards`
3. Print `build/cards/cards.pdf`: A4, **actual size (100% scale)**, double-sided,
   **flip on long edge**. 20 cards per sheet, fronts and backs alternate.
4. Cut along the grid lines.
