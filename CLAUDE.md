# Rill (rill-browser) — Android TV browser

## Goal
A free, remote-first Android TV browser with built-in uBlock Origin + Privacy Badger, optimized for video streaming (including live streams like fctv33).

## Engine update
GeckoView was tried first and does not fit on the target device (Onn 4K stick, 1.9 GB RAM): Android's low-memory killer ended the app within seconds. The app now uses the system WebView with the built-in blocker in `AdBlocker.kt` (the fallback described below). Sections below that mention GeckoView/XPIs describe the original plan.

## Tech decisions (verify each before building on it)
- Language: Kotlin, single-Activity app, Android TV (Leanback launcher intent, banner, no touchscreen required in manifest).
- Engine: GeckoView (Mozilla). Reason: it runs WebExtensions, so we bundle the real uBlock Origin and Privacy Badger instead of writing a blocker.
  - Bundle the XPIs in assets, install via GeckoRuntime's WebExtensionController. Check the current GeckoView docs for the right call (ensureBuiltIn vs install) and for any extension restrictions.
  - Both extensions are GPLv3, so the app is GPLv3 if distributed.
  - Fallback if GeckoView extensions fail: Android WebView + adblock-rust request interception (weaker cosmetic filtering).
- Min SDK 26. Sideload APK via Downloader is the primary distribution path.
- UI: Jetpack Compose for TV (or plain Views if Compose focus handling fights us). The GeckoView sits in an AndroidView.

## Screens

### 1. Landing page (app launch)
Four large focusable tiles, D-pad navigable:
- Search: text input; input is turned into a Google search URL. If it already looks like a URL, open it directly.
- Enter URL
- Bookmarks
- Settings: adblock settings (per-extension toggles, filter list management), history (view/clear), cursor speed, default start behavior.

Session rule: never leave a page open after the app closes. On onStop/onDestroy, close all sessions and clear in-memory tab state so the next launch always starts on the landing page. (Cookies and history follow the user's settings.)

### 2. Browsing view
- Page fills the screen. No visible chrome.
- Circular cursor overlay driven by the D-pad arrows. Hold = accelerate.
- Center button: short press = left click, long press = right click (context menu).
- Scrolling: pushing the cursor against a screen edge scrolls the page in that direction.
- Inject synthetic touch/mouse events into GeckoView (MotionEvent via the session's panZoomController / dispatch). Hover events matter for many video players.
- Text fields: tapping one opens the system TV keyboard (voice input if available).

### 3. Pop-up menu (all in-browsing controls)
- Back button on a page opens the menu and takes focus from the page.
- Back button at the top level of the menu closes it and returns focus to the page. A collapse selector sits at the top right of the menu as well.
- Menu items: Back, Forward, Reload/Stop, URL bar (type or voice), Bookmark this page, Bookmarks, Settings, Home (return to landing page, which is how the user exits a page since back is used for the menu), Desktop/mobile site toggle, Fullscreen toggle.
- While the menu is open, the cursor is hidden and the page does not receive input.

## Video / streaming requirements (priority)
- Fullscreen video should be one press away. When a video goes fullscreen, hide the cursor until the user moves it.
- Media keys: play/pause, seek, fast-forward/rewind mapped to the player.
- Live streams: HLS/DASH must work. Keep screen awake during playback (wake lock / FLAG_KEEP_SCREEN_ON). Allow autoplay. Hardware decoding on.
- Widevine DRM enabled where the device supports it.
- Test targets: fctv33 live streams, YouTube, a plain HLS test stream, an embedded-iframe player.
- Block popups and redirect tricks common on streaming sites, but always allow user-initiated fullscreen.

## Milestones
1. Project scaffold, TV manifest, GeckoView loads a URL.
2. Landing page + search/URL parsing.
3. Cursor overlay, click, long-press, edge scrolling.
4. Pop-up menu with focus handoff and back-button behavior.
5. Bundle uBlock Origin + Privacy Badger and verify they block (test on a known ad-heavy page).
6. Bookmarks, history, settings screens.
7. Video polish: fullscreen, media keys, wake lock, live-stream testing.
8. Release build, signed APK, sideload instructions.
9. GitHub Actions workflow gated by `buildif.md` (see CI section). Consider doing this right after milestone 1 so every later milestone gets a downloadable APK.

## CI: automatic APK builds with GitHub Actions
Goal: pushing to the repo builds an APK only when a `buildif.md` file in the repo root contains the line `build=yes`. Any other value (or a missing file) skips the build.

Implementation:
- Workflow file: `.github/workflows/build-apk.yml`, trigger `on: push` (branches: main, plus manual `workflow_dispatch`).
- Workflows can't read file contents in the trigger itself, so the workflow always starts, and the first step checks the file:
  - Read `buildif.md`, match `^build=yes\s*$` (case-insensitive, ignore surrounding whitespace), and write `should_build=true/false` to `$GITHUB_OUTPUT`.
  - Every later step uses `if: steps.check.outputs.should_build == 'true'`. When false, log "Build skipped (buildif.md is not build=yes)" and finish successfully.
- Build steps: checkout, set up JDK 17 (temurin), set up Gradle with caching (gradle/actions/setup-gradle), run `./gradlew assembleRelease` (or `assembleDebug` until signing is set up).
- Upload the APK with actions/upload-artifact (keep 30 days). Name it with the commit short SHA.
- Optional: a `release=yes` line in `buildif.md` also publishes the APK to a GitHub Release, so the TV can download it via Downloader using a short URL.
- Signing: generate a keystore once. Store it base64-encoded in repo secrets (KEYSTORE_B64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD), decode it in the workflow, and configure Gradle to read them from environment variables. Never commit the keystore. Use the same keystore every build so updates install over the old version.
- GeckoView ships large native libraries. Configure ABI splits (arm64-v8a and armeabi-v7a, plus x86_64 for the emulator) so the TV APK stays small. Most current Android TV devices use arm64-v8a, but verify on the target device.
- Add a README section explaining `buildif.md` (examples of `build=yes` and `build=no`) and how to sideload the artifact with Downloader.
- Tell the user which repo secrets and settings they must add by hand (Settings > Secrets and variables > Actions), and give exact steps. Don't try to create secrets yourself.

## Working rules
- Build and test on an Android TV emulator and a real device where possible.
- Keep each milestone runnable before moving on.
- Do not store personal data beyond bookmarks, history, and settings, all local.
