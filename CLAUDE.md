# MoonLightControl for Android — project memory

Auto-loaded every session. Keep it short; details live in `docs/`.

## What it is
An Android app (tablet first, landscape) that opens the **MoonLightControl** web interface full screen.
MoonLightControl is a separate project: a DMX lighting server (Go, runs on a Raspberry Pi / mini PC)
with a touch-first web UI served at `http://<host>:8080`. This app does **not** reimplement that UI: it is
a native shell around a WebView ("option 1"), plus a **host registry**, because the server's IP changes
between venues (church, rehearsal room, home…). Personal use first; to be published (GPL-3.0-or-later).

Full brief, server facts and milestones: [docs/BRIEF.md](docs/BRIEF.md). Read it before working.

## Current state
**Milestone 1 done + recent servers + discovery** (2026-11-17): start screen lists up to 5 recently
used servers (one tap opens; login cookie persists 30 days, so no re-login), a "Find servers" subnet
scan (milestone 4, done early) and a typed address → WebView console; targetSdk 37 with the
`ACCESS_LOCAL_NETWORK` flow. Tested on the real tablet against the
user's server (10.1.1.200:8080). Next: rest of milestone 2 (see OPEN_QUESTIONS), then milestone 3.

## Commands (Windows, Git Bash)
New shells may lack the user env vars: prefix with
`export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"`.
- `./gradlew lint test` — lint + all unit tests (shared `commonTest` included).
- `./gradlew connectedAndroidTest` — Compose UI tests on a device/emulator
  (`$ANDROID_HOME/emulator/emulator -avd Medium_Phone_API_37.0 -no-window` for a headless emulator).
- `./gradlew assembleDebug` → `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.
- `./gradlew installDebug` puts it on the device (data kept). `connectedAndroidTest` leaves the app
  installed (gradle.properties) so the tablet keeps its login. Real tablet: Galaxy Tab S10 FE (SM-X520,
  Android 16), USB. Debug the page: `adb forward tcp:9222 localabstract:webview_devtools_remote_<pid>`.
Pending questions: docs/OPEN_QUESTIONS.md.
App ↔ web UI messages (e.g. logout returns to the host list): BRIEF §6 — a contract shared with the
server repo, **already implemented on the server side** (protocol 1); change it only together with the user.

## Stack (decided 2026-11-16, option A)
Package `br.com.moonaudio.moonlightcontrol` · Kotlin · **Kotlin Multiplatform `shared` module** for platform-free logic (hosts, validation, URLs,
discovery, bridge protocol, health client) — iOS is a future target · Compose for the Android screens ·
AndroidX WebView/`androidx.webkit` · DataStore + kotlinx.serialization (hosts) · Gradle wrapper ·
minSdk 26, target the latest SDK. Keep platform APIs out of `shared`.

## Working rules
- Talk to the user in **Brazilian Portuguese**. Code, comments, commits and docs in **English**.
- The user does not know Kotlin/Android: Claude owns quality. Every change ships with tests (unit tests
  for logic, UI tests where practical); before finishing run `./gradlew lint test` (and
  `connectedAndroidTest` when a device is attached).
- UI strings in `res/values/strings.xml` (en) and `res/values-pt-rBR/strings.xml` (pt-BR) — never hardcoded.
- Never change the server project from here; if the app needs something from the server, write it down
  in docs/OPEN_QUESTIONS.md for the user to bring to the server repo.
- Ask before sending anything to a real lighting server that could move or strobe fixtures.
- Commit/push only when the user asks.
- After each session, or whenever something is decided: update docs/DECISIONS.md,
  docs/OPEN_QUESTIONS.md, docs/SESSION_LOG.md and "Current state" above.
