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
