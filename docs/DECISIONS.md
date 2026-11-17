# Decisions

Append-only: date — decision (rationale). Superseded decisions stay, with a later entry saying so.

- 2026-11-16 — Separate repository from the MoonLightControl server.
- 2026-11-16 — **Option 1: native shell around a WebView** that loads the web UI from the server itself
  (same origin), instead of bundling the UI (would hit the server's cross-site protection and version
  skew) or rewriting it natively (every feature twice). New server features appear with no app change.
- 2026-11-16 — **Host registry** in the app: several saved servers (name, address, port, HTTPS), one
  default that can open automatically — the server's IP changes between venues.
- 2026-11-16 — Self-signed HTTPS handled by trust on first use (pinned SHA-256 per host), never by
  accepting every certificate.
- 2026-11-16 — Fully offline: no analytics, no remote config, nothing fetched from the internet.
- 2026-11-16 — **App ↔ web UI bridge, protocol 1** (BRIEF §6): JSON string messages over
  `addWebMessageListener` ("MoonLightApp", current host origin only). First use: an explicit logout in the
  app returns to the host list (browser: login screen, unchanged); "Change server" leaves while keeping the
  login. Expired/ended logins still show the web login screen. Auto-open never right after leaving a host.
- 2026-11-16 — Server side of the bridge implemented (server repo, `web/src/lib/appBridge.ts`); app-only
  options appear only for features listed in the app's `hello`; receivers accept any `v` ≥ 1.
- 2026-11-16 — Launcher name: **MoonLightControl**.
- 2026-11-16 — Distribution: sideloaded APK first, **Google Play** once v1 is finished (so: release signing,
  Play policies, Data safety form and a privacy policy are part of the release milestone).
- 2026-11-16 — Form factors: tablets **and phones** (layouts must work in portrait too; tablet landscape
  stays the primary target). **iOS is a future target**; Android first (the only device the user has).
- 2026-11-16 — Discovery (BRIEF §7) is **in v1**, before the first release.
- 2026-11-16 — Start behavior (replaces "default host + auto-open"): on a cold start, if the **last used
  host** answers `GET /api/v1/health`, the app opens it directly; otherwise it shows the host list. No
  auto-resume when the user's last action on that host was an explicit logout or "Change server".
  The per-host `isDefault` flag is dropped; a setting can turn auto-resume off.
- 2026-11-16 — Minimum Android version: **8.0 (API 26)**.
- 2026-11-16 — **Stack: option A** — Kotlin; a **Kotlin Multiplatform shared module** for platform-free
  logic (host model, validation, URL building, discovery scan, bridge protocol, health client) so a
  future iOS app reuses it; Android UI in Compose (portable to Compose Multiplatform later); WebView
  glue stays per platform. Rejected: plain Android only (iOS would rewrite everything), Flutter/React
  Native (WebView TLS pinning, bridge and downloads would depend on third-party plugins).
- 2026-11-16 — Icon: a white crescent moon on a black background (two colors only).
- 2026-11-17 — Project layout: Gradle modules `:shared` (Kotlin Multiplatform, Android target only
  for now; tests in `commonTest`, run by `./gradlew test` through an alias of `testAndroidHostTest`)
  and `:androidApp` (Compose + WebView). Package/applicationId `io.github.luizhenriquemm.moonlightcontrol`.
  Toolchain: AGP 9.5.1, Kotlin 2.4.21, Gradle 9.9.1 (wrapper, checksum pinned), Compose BOM 2026.09.00.
- 2026-11-17 — **targetSdk 37 + `ACCESS_LOCAL_NETWORK`**: Android 17 blocks all LAN traffic (WebView
  included) for apps targeting 37 unless the user grants this runtime permission ("Nearby devices").
  The app requests it before opening a server and explains how to enable it if denied. On Android < 17
  nothing is asked. (Source: developer.android.com/privacy-and-security/local-network-permission.)
- 2026-11-17 — Android UI strings stay in `res/values*/strings.xml` (Jetpack Compose in `:androidApp`);
  if screens move to Compose Multiplatform for iOS, strings move with them then.
- 2026-11-17 — Addresses typed by the user are canonicalized like a browser would (lower-case host,
  compressed IPv6, default port 8080 even with an explicit scheme, path dropped, default ports omitted
  from the origin) so the stored origin matches the WebView's for the app bridge.
- 2026-11-17 — The console survives a WebView renderer crash (`onRenderProcessGone` → fresh WebView
  reloading the server) instead of crashing the app mid-show.
