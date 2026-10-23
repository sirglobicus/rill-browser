<p align="center"><img src="content/rill-browser-logo.svg" width="96" alt="Rill logo"></p>

# Rill

Rill (repository: `rill-browser`, Android package: `com.rillbrowser.app`) is a free, remote-first Android TV browser built on Android's system WebView (Chromium), with a built-in ad and tracker
blocker (EasyList, EasyPrivacy, Peter Lowe's list). GPLv3.

> Why not GeckoView + uBlock Origin? It was the original plan, but on the Onn 4K stick (1.9 GB RAM) Android's
> low-memory killer ended the app within seconds of launch, even on the landing page.

## Controls

| Remote | Landing page | Browsing |
|---|---|---|
| D-pad arrows | move between tiles | move the cursor (hold to accelerate); push against a screen edge to scroll |
| Center | open tile | short press = click, hold = right-click / context menu |
| Back | exit app | open the menu; Back again (or the "Close" item) closes it |
| Media keys | | play/pause, fast-forward, rewind |

## Testing on your PC first

You don't need the TV for most of this. The Onn 4K stick is a 32-bit ARM device with ~2 GB RAM. The APK has no native code, so one APK runs everywhere,
including the emulator (use an x86_64 image).

### Option A: Android TV emulator (recommended, closest to the real thing)
1. Install [Android Studio](https://developer.android.com/studio) and open this folder.
   It brings the JDK and SDK; let Gradle sync finish.
2. Device Manager > Create Virtual Device > **Television > Television (1080p)** > pick an **x86_64** Android TV image
   (API 34 is fine) > Finish. Turn on virtualization if the emulator complains
   (Windows: enable "Windows Hypervisor Platform" under Windows Features, and virtualization in BIOS).
3. Run the `app` configuration. The emulator keyboard maps to the remote:
   **arrow keys = D-pad, Enter = center, Esc = Back**. Hold Enter for right-click.
4. Unit tests (URL parsing) run without any emulator: `./gradlew testDebugUnitTest`.

### Option B: Install a CI-built APK on the emulator
Download the APK from the Actions run and run `adb install -r rill-browser-<sha>.apk`.

What the emulator **cannot** tell you: real Widevine DRM, hardware video decoding performance, and the feel of the
real remote. Check those on the stick (below) before trusting them.

### Remote debugging a page
In desktop Chrome open `chrome://inspect` with the emulator or TV connected over adb to inspect the running page.

## Putting it on the Onn stick

1. On the stick: Settings > System > About > tap "Android TV OS build" 7 times to enable developer options.
2. Settings > Apps > Security & restrictions > Unknown sources > allow **Downloader**.
3. Get the APK and install it using either:
   - **Downloader app**: enter the direct URL of the APK (the GitHub Release asset, see below), or
   - **adb**: Settings > System > Developer options > USB/network debugging, then
     `adb connect <tv-ip>:5555` and `adb install -r rill-browser-<sha>.apk`.
4. Updates install over the old version as long as the same keystore signed them.
5. If you still have the earlier "TV Browser" build installed, uninstall it: Rill uses a different package name, so it installs
   as a separate app and does not inherit its bookmarks or settings.

## Automatic APK builds (`buildif.md`)

Every push to `main` starts the workflow, but it only builds when `buildif.md` in the repo root contains a line
`build=yes` (case-insensitive). Anything else, or a missing file, skips the build and finishes green.

```
build=yes     <- build and upload APKs as a workflow artifact (kept 30 days)
release=yes   <- optional extra line: also publish the APKs as a GitHub Release (tag build-<short sha>)
release=1.0.0 <- instead of yes: publish a Release tagged v1.0.0 and set the app's versionName to 1.0.0
```
```
build=no      <- push without building
```

The APK is named `rill-browser-<short sha>.apk`. Find them under the run's **Artifacts** section, or under
**Releases** when `release=yes` (the release asset URL works in Downloader; use a short redirect like bit.ly if you
don't want to type it).

### One-time setup you must do by hand

Without these the build still works but is signed with the debug key, so a later properly signed build can't install
over it.

1. Create a keystore (run once, keep the file somewhere safe, never commit it):
   `keytool -genkeypair -v -keystore rill-browser.jks -alias rill-browser -keyalg RSA -keysize 2048 -validity 10000`
2. Base64 it. PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("rill-browser.jks")) | Set-Clipboard`
3. GitHub repo > **Settings > Secrets and variables > Actions > New repository secret**, add four secrets:
   `KEYSTORE_B64` (the base64 text), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`rill-browser` above), `KEY_PASSWORD`.
4. Repo **Settings > Actions > General > Workflow permissions**: set to **Read and write** (needed for `release=yes`).

## Ad and tracker blocking

WebView has no extension support, so `AdBlocker.kt` implements the useful subset of Adblock Plus syntax: host rules
(`||host^`), exceptions, and element-hiding CSS. Path-based rules are skipped, so it is weaker than uBlock Origin but
fast on a small device. Lists live in `app/src/main/assets/filters/` (refresh with `scripts/fetch-filters.sh`) and can be
re-downloaded on the device from Settings > Update filters.

Player keys: media keys, and the menu's Fullscreen item, are sent into the page as key presses (F9-F12) that a small
script injected into every frame turns into play/pause, seek and fullscreen on the largest video.
