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
- 2026-11-17 — **Recent servers on the start screen** (user request): up to 5, most recent first, one
  per address (scheme + host + port), added/moved to the top only when the console's main frame
  finished loading without error; each can be removed. One tap opens it. Stored with DataStore as a
  versioned JSON file (`recent_servers.json`, unknown fields ignored, invalid entries skipped, corrupted
  file → empty list). First step of milestone 2; named hosts / editing / auto-resume still to come.
- 2026-11-17 — **"Keep me logged in" needs no change on either side**: the server's `mlc_login` cookie
  is already persistent (Max-Age 30 days, renewed while used) and the WebView keeps it across app
  restarts (`CookieManager.flush()` in `onPause`; checked in the tablet's cookie store). Tapping a
  recent server therefore logs in automatically. No checkbox added; the server repo was not changed.
- 2026-11-17 — `connectedAndroidTest` leaves the app installed
  (`android.injected.androidTest.leaveApksInstalledAfterRun`): uninstalling wiped the real tablet's
  login and recent servers.
- 2026-11-17 — Display name (launcher, start screen, system dialogs): **"Moon Light Control"**, with
  spaces (supersedes "MoonLightControl" of 2026-11-16 for the app label only; the product, package id,
  user-agent token `MoonLightControlAndroid` and docs keep the one-word name).
- 2026-11-17 — **Discovery ("Find servers") moved forward from milestone 4** (user request): a button
  on the start screen scans the device's IPv4 subnet(s) on every Wi-Fi/Ethernet network (all of them,
  not only the default one), limited to the device's own /24, port 8080, `GET /api/v1/health`, 64 in
  parallel, connect timeout 700 ms / read 1.5 s, body capped at 4 KB, through the network it belongs to
  (`Network.openConnection`). A host counts as a MoonLightControl server only if the body has the exact
  health shape (status "ok", version, commit, uptimeSeconds). Results appear as they answer, show the
  server version and open with one tap; they are kept in memory only (the recent list is the
  persistent one). Android 17: the scan asks for `ACCESS_LOCAL_NETWORK` first, like opening a server.
  No server change.
- 2026-11-17 — **Package / applicationId `br.com.moonaudio.moonlightcontrol`** (supersedes
  `io.github.luizhenriquemm.moonlightcontrol`; Kotlin packages and the shared namespace follow). The
  user owns moonaudio.com.br; changed before any release because it is permanent on Google Play.
- 2026-11-17 — **Publisher: the user as an individual** (Moon Audio has no CNPJ yet). Consequences for
  the release milestone: Google Play personal account → closed test with ≥ 12 testers opted in for
  14 consecutive days before production; Android developer verification (enforced in Brazil since
  2026-09-30) as an individual, which also covers APKs distributed outside Play (or "limited
  distribution" to ≤ 20 devices without verification). The domain hosts the privacy policy and the
  product page; it is not required for an individual account (organizations must verify a website).
- 2026-11-17 — **Milestone 3 (console polish)**, as built:
  - Full screen always while the console is shown (system bars hidden, transient on swipe; restored on
    the start screen); only the display cutout is padded, so a swipe never resizes the page mid-show.
  - Screen kept on always while the console is shown (`View.keepScreenOn` on the WebView).
  - No app settings screen yet (keep-awake/full screen are not optional); orientation not locked:
    Android 16+ ignores locks on large screens for apps targeting new SDKs, and the UI works rotated.
  - Native error view for main-frame failures (network error, HTTP error status, refused
    certificate): address + reason, "Try again" and "Change server".
  - Show export: the WebView's download → system "save as" (SAF `CreateDocument`) with the name from
    `Content-Disposition` (`filename*` UTF-8 first), fetched by the app with the page's cookie and
    user agent (pinned TLS for self-signed HTTPS). Not `DownloadManager` (it cannot use the pin).
  - Show import: `onShowFileChooser` → SAF `OpenDocument("*/*")`; the callback is always answered.
  - Self-signed HTTPS, trust on first use: only the chosen server's exact origin can be trusted; the
    dialog shows the SHA-256 fingerprint; a different certificate later gets a red "certificate
    changed" warning where Cancel is the prominent button. Accepted fingerprints persist per origin
    (`trusted_certificates.json`). Note: Chromium itself remembers an accepted certificate per host
    for the rest of the process.
  - App bridge protocol 1 via `WebViewCompat.addWebMessageListener` for the server's exact origin,
    main frame only: `ready` → `hello` (features logout, switchHost); `logout` → flush cookies + start
    screen; `switchHost` → start screen. Verified against the real server (the web UI's "Change
    server" link appears and works).
- 2026-11-17 — Release build: R8 + resource shrinking; signed with the user's **upload key** (Play App
  Signing holds the app signing key), created by `tools/create-upload-key.ps1` into
  `%USERPROFILE%\.android-signing\` (never in the repo; password only in that properties file).
  Debug builds get `applicationIdSuffix ".debug"` and the label "Moon Light Control (debug)", so
  development and tests never touch the installed real app.
- 2026-11-17 — "About" dialog (info button on the start screen): version, GPL notice, source link,
  "© 2026 Moon Audio". Store graphics generated from the launcher crescent (`tools/store_graphics.py`).
- 2026-11-17 — Contact e-mail for the store and privacy policy: contato@moonaudio.com.br. App
  repository made public by the user (GPL source link in About). Google's reviewers get an
  explanation only ("App access" text in docs/store/listing.md), no demo server.
- 2026-11-17 — The server repository stays private; the reviewers' "App access" text only covers the app's own screens and names contato@moonaudio.com.br.
