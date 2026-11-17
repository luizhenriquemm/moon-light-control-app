# Session log

## 2026-11-16 — Handoff from the server repository
- Project defined in the MoonLightControl server repo (docs/android-app-handoff): goals, screens, host
  model, server facts, WebView checklist, milestones. Nothing built yet.
- Bridge protocol 1 added to the brief (§6) and implemented on the server side the same day: the app
  only needs its half (inject `MoonLightApp`, answer `ready` with `hello`, act on `logout`/`switchHost`).

## 2026-11-16 — Open questions answered
- User answered BRIEF §9: name MoonLightControl; APK sideload then Google Play; phones too and iOS as a
  future target; discovery in v1; auto-resume of the last host when reachable (replaces default host);
  minimum API 26. Recorded in DECISIONS.md; BRIEF §2, §3, §8, §9 updated.
- Then decided: stack option A (KMP shared logic + Compose on Android); icon = white crescent on black.
- Pending before milestone 1: local-network permission
  rules. Dev environment: JDK 17 found; Android SDK and adb not installed.

## 2026-11-17 — Dev environment + milestone 1 (skeleton)
- Installed Android Studio (user ran the installer; winget's silent install from Claude's shell did
  nothing — the elevation prompt never reached the user). SDK at `%LOCALAPPDATA%\Android\Sdk`
  (platform 37, build-tools 36, adb, emulator AVD `Medium_Phone_API_37.0`). User env vars set:
  `ANDROID_HOME`, `JAVA_HOME` = Android Studio's JBR (JDK 25), `platform-tools` on PATH.
- Built milestone 1: `:shared` (`ServerAddress.parse` + 17 tests) and `:androidApp` (connect screen,
  WebView console, local-network permission flow, adaptive icon white crescent on black, en + pt-BR
  strings, network security config with cleartext, renderer-crash recovery).
- Checks: `./gradlew lint test` green (0 lint issues, 18 unit tests); `connectedAndroidTest` green on
  the API 37 emulator (4 Compose UI tests). Manually on the emulator: icon/splash, invalid-address
  error, Android 17 permission prompt and the "denied" explanation.
- Not yet tested: the real tablet (USB debugging not enabled yet) and a real MoonLightControl server.
- Later the same day: tablet attached over USB (SM-X520, Android 16 / API 36, Samsung ADB driver OK);
  4 UI tests green on it; app installed. Test server running on the user's PC at 10.1.1.200:8080
  (version 4b9dade), reachable from the tablet (10.1.1.202) through the Windows firewall.
- First run against the real server: faders, scenes and fixtures views were empty/collapsed. Cause
  (found with WebView remote debugging over adb): the WebView had AndroidView's default WRAP_CONTENT
  layout params, so CSS `100vh` was 0. Fixed with MATCH_PARENT; regression test
  `ConsoleScreenTest.viewportHeightUnitsAreNotZero` (verified to fail without the fix). Faders view
  confirmed on the tablet (WebView 157).
- Recent servers (user request): start screen lists the last 5 servers that loaded successfully;
  tap = open, × = remove. `shared`: `RecentServers` (MRU logic + JSON codec, 10 tests),
  `ServerAddress.label/fromParts`; app: `RecentServersStore` (DataStore), `ConsoleScreen.onLoaded`
  (main frame finished without error). Login persistence verified: `mlc_login` is a 30-day persistent
  cookie and the WebView keeps it — no server change. 15 device tests green on the tablet.
- Found: `connectedAndroidTest` uninstalled the app at the end, wiping the tablet's login; now kept.
- App label changed to "Moon Light Control" (with spaces), user request.
- Discovery (user request, milestone 4 done early): "Find servers" on the start screen. `shared`:
  `Discovery` (candidate hosts, scan range, parallel scan, health parsing; 12 tests); app:
  `ServerDiscovery` + `httpHealthProbe` (tested against a loopback HTTP server on the device),
  discovery section with progress / none / no-network states. On the tablet the scan of 10.1.1.0/24
  found 10.1.1.200 (version 4b9dade) in a few seconds. Totals: 44 unit tests, 25 device tests, lint clean.
- Package id changed to `br.com.moonaudio.moonlightcontrol` (user owns moonaudio.com.br; publishing as an
  individual). Old app uninstalled from the tablet, new one installed; 44 unit + 25 device tests green.
