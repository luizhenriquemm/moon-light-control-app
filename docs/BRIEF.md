# MoonLightControl for Android — brief

Written at the start of the project, from the MoonLightControl server repository (2026-11-16). Facts about
the server below were checked against its code at that date; the server's own `docs/API.md` is the
reference if anything differs.

## 1. Goal

Run the MoonLightControl web interface as a real app on Android tablets:

- full screen (no browser bars), landscape, screen kept on during a show;
- a **list of saved servers (hosts)**: the same tablet is used in different places where the server has a
  different IP; pick one, or open the usual one automatically;
- robust when the server is unreachable (clear message, retry, change host);
- everything the web UI does keeps working inside the app, including show import (file picker) and
  export (download).

**Not a goal:** reimplementing the UI natively. New server features must appear in the app with no app
change. Native code is only for what a browser can't do well (host management, full screen, files, TLS
trust, keep-awake).

Primary device: a 10.9" Android tablet (Samsung Galaxy Tab S10 FE class), landscape. Phones are supported
too (the native screens must work in portrait). iOS is a future target (decided 2026-11-16), Android first.
Must work **fully offline** on an isolated lighting network (no internet, no DNS).

## 2. Screens

1. **Hosts** (start screen unless auto-resume applies)
   - List of saved hosts: name, address, last used, a status dot (reachable / not) from a quick health
     check when the screen opens.
   - Tap = open. Edit / delete / reorder.
   - **Auto-resume** (decided 2026-11-16): on a cold start, the app checks the **last used host** with
     `GET /api/v1/health` (short timeout, with a "Connecting to <name>…" view that can be cancelled);
     if it answers, the console opens directly, otherwise the host list shows. Never when the user's
     last action on that host was an explicit logout or "Change server", and never right after leaving
     a server, or they would be thrown back into it.
   - "Add host": name, address (IP or hostname, e.g. `10.1.1.20`, `stage-pi.local`), port (default
     8080), HTTPS on/off. A **Test** button calls `GET /api/v1/health` and shows the server version.
   - **Find servers** (milestone 4, in v1): scan the local network for MoonLightControl servers and
     offer to add them.
2. **Console** (the WebView, full screen)
   - Loads `<scheme>://<address>:<port>/`.
   - System back: goes back in the web history (the web UI uses hash routes like `#/faders`, which
     create history entries); at the first page, returns to the host list.
   - If the page can't load: a native error view with **Retry** and **Change host**.
   - The web UI can send the app back to the host list itself (logout, "change server"): see §6.
3. **App settings** (small): keep screen on (default on), orientation (landscape default / follow
   sensor), auto-resume last host (default on), about (app version).

## 3. Host model

```kotlin
data class Host(
    val id: String,            // UUID
    val name: String,          // "Igreja", "Ensaio"
    val address: String,       // IPv4, IPv6 (stored without brackets) or hostname
    val port: Int = 8080,      // 1-65535
    val https: Boolean = false,
    val certSha256: String?,   // pinned certificate (HTTPS with a self-signed cert), see §5
    val lastUsedAt: Long?,     // epoch ms
)
```

App-level state (not per host): `lastHostId` and whether the user left it explicitly (logout /
"Change server"), for auto-resume (§2).

- Stored with DataStore (JSON via kotlinx.serialization). Order = list order.
- URL building must handle IPv6 (`http://[fe80::1]:8080/`) and trim spaces; validate before saving.
- Two hosts may share an address (different venues reusing the same IP). That is fine: the server login
  is a cookie per origin, so the second server simply asks for login again.

## 4. Facts about the server (the app must respect them)

- Default port **8080**, listens on all interfaces, IPv4 and IPv6. Plain **HTTP by default**. Optional
  HTTPS on the same port, often with a **self-signed** certificate (`sudo moonlightcontrol tls enable
  --self-signed`): ECDSA P-256, valid 2020-2049, names = the host's IPs, hostname, `hostname.local`,
  localhost. Hosts with avahi (Raspberry Pi OS) answer at `<hostname>.local`.
- `GET /api/v1/health` is **public** (no login): `{"status":"ok","version":"…","commit":"…",
  "uptimeSeconds":123}`. Use it for Test, status dots and discovery. Nothing else is needed from the API.
- The web UI is a single-page app at `/` (hash routing: `#/map`, `#/faders`, `#/scenes`, `#/fixtures`,
  `#/universes`, `#/shows`, `#/config`). It talks to `/api/v1/*` on the **same origin** and opens a
  WebSocket at `/api/v1/ws` (wss under HTTPS).
- Login: cookie `mlc_login` (HttpOnly, SameSite=Lax, Secure under HTTPS), valid 30 days and renewed while
  used. It must persist across app restarts (CookieManager + `flush()`).
- Cross-site protection: non-GET requests with a foreign `Origin` are refused. Loading the UI from the
  server itself (same origin) is exactly what avoids this — **do not** bundle the UI in the app or load
  it from another origin.
- The UI keeps device preferences (language, theme, channels per page, last fader page) in
  `localStorage` key `mlc.prefs.v1` → DOM storage must be enabled; it is per origin, so per host.
- The UI follows `prefers-color-scheme` (and has its own light/dark setting) and uses
  `env(safe-area-inset-*)` with `viewport-fit=cover`.
- Faders use pointer events with `touch-action: none` and multi-touch (several faders at once); the flash
  button suppresses the long-press context menu. These work in a WebView as in Chrome.
- **Show export** is a link `<a href="/api/v1/shows/{id}/export" download>`; the response has
  `Content-Disposition: attachment` with a UTF-8 `filename*` (names may have accents), extension
  `.mlcshow`, and needs the login cookie.
- **Show import** is `<input type="file" accept=".mlcshow,.json,application/json">`, read in JS with
  `file.text()`.
- The server never needs internet. The app must not either (no analytics, no remote config, no fonts
  from the web).

## 5. WebView checklist (the real work)

- **Cleartext HTTP** to arbitrary LAN addresses: Android blocks it by default since API 28 → network
  security config with cleartext permitted (hosts are user-entered IPs, so no fixed domain list).
- Settings: JavaScript on, DOM storage on, `textZoom = 100` (system font scaling must not break the
  fader layout), zoom controls off, `overScrollMode = OVER_SCROLL_NEVER`, append
  ` MoonLightControlAndroid/<version>` to the user agent.
- Disable algorithmic darkening (`WebSettingsCompat`), so the UI's own dark theme isn't inverted again.
- Cookies: `CookieManager.setAcceptCookie(true)`; `flush()` in `onPause`.
- **Downloads** (show export): `setDownloadListener` → save via `DownloadManager` (or SAF
  "create document"), passing the `Cookie` header from `CookieManager.getCookie(url)`, and the file name
  from `Content-Disposition` (parse `filename*=UTF-8''…`; `URLUtil.guessFileName` loses accents).
- **File chooser** (show import): implement `WebChromeClient.onShowFileChooser` with an Activity Result
  (`OpenDocument`, MIME `*/*` — `.mlcshow` has no registered type).
- **Self-signed HTTPS**: never call `handler.proceed()` blindly in `onReceivedSslError`. Trust on first
  use: show the certificate's SHA-256 fingerprint, let the user accept, store it in the host
  (`certSha256`) and proceed only when it matches; on mismatch, warn loudly. Verify that the WebSocket
  (`wss://`) is accepted after the page's certificate decision (to test early in milestone 3).
- **Full screen**: edge-to-edge + hide system bars (`WindowInsetsControllerCompat`, transient bars by
  swipe). Keep screen on with `FLAG_KEEP_SCREEN_ON` while the console is shown (setting).
- Orientation: landscape by default (the UI is designed for it); setting to follow the sensor.
- Main-frame load errors (`onReceivedError` for the main frame, HTTP errors on `/`) → native error view.
  After the page loaded, connection drops are handled by the web UI itself (it shows a status dot and
  reconnects) — don't intercept those.
- Keep the WebView alive across configuration changes (or handle them in the manifest) so a rotation
  doesn't reload the console mid-show.

## 6. App ↔ web UI bridge (protocol 1)

A small, versioned message channel so the web UI (served by the MoonLightControl host) and the app can
cooperate. First use: **logging out behaves according to how the user is connected** — in a browser the
user lands on the login screen again (unchanged); in the app, the app returns to its only screen of its
own, the list of saved / found servers.

### Transport

- The app registers `WebViewCompat.addWebMessageListener(webView, "MoonLightApp", allowedOrigins, …)`
  with `allowedOrigins` = **exactly the current host's origin** (e.g. `http://10.1.1.20:8080`), and
  registers it again (or uses a new WebView) when the host changes. AndroidX then injects
  `window.MoonLightApp` into that origin's pages **before any page script runs**, with `postMessage()` and
  `onmessage` / `addEventListener("message")` for the app's replies.
- Requires the `WEB_MESSAGE_LISTENER` WebView feature (`WebViewFeature.isFeatureSupported`); without
  it the app registers nothing and the web UI simply behaves as in a browser.
- Prefer this over `addJavascriptInterface` (which would expose Java methods to any page loaded).
- Messages are JSON **strings**: `{"v": 1, "type": "…", …}`. Both sides ignore unknown types and extra
  fields and accept any `v` ≥ 1 (messages without a `type` or without `v` ≥ 1 are ignored), so either
  side can be updated first.
- The web UI listens with `addEventListener("message")` when available, otherwise `onmessage`; reply
  with the `replyProxy` given to `onPostMessage` (or `postMessage` on the WebView's message port).

### Messages

| Direction | Message | When / what the receiver does |
|---|---|---|
| web → app | `{"v":1,"type":"ready","serverVersion":"…"}` | once per page load, right after the UI's `GET /api/v1/health` (`serverVersion` absent if that failed). The app replies `hello` — to **every** `ready` (a reload sends a new one). |
| app → web | `{"v":1,"type":"hello","app":"android","appVersion":"…","features":["logout","switchHost"]}` | reply to `ready`. The web UI shows app-only options for the listed features only. |
| web → app | `{"v":1,"type":"logout"}` | the user **explicitly** logged out (this device, or "log out everywhere"), sent after the server confirmed (cookie already cleared). App: go back to the host list; `CookieManager.flush()`; no auto-open. |
| web → app | `{"v":1,"type":"switchHost"}` | the user chose "Change server" (login kept). App: back to the host list, cookies untouched, no auto-open. |

Not sent on a login that **expires or is ended elsewhere** (HTTP 401, WebSocket close 4401): then the web
UI shows its own login screen, as in a browser — the user most likely wants to sign in to the same
server again. A "Change server" link on that login screen (below) still lets them leave.

### What the web UI does in the app — implemented in the server (2026-11-16)

Server builds from 2026-11-16 (after commit `1974a89`) speak protocol 1; older servers simply never
send `ready` (the app then behaves as a plain browser shell — keep the native back gesture working).
Source: `web/src/lib/appBridge.ts` and `appBridge.svelte.ts` in the server repo; reference:
its `docs/API.md`, section "Android app bridge".

- Detection: `window.MoonLightApp` with a `postMessage` function at startup → app mode; `ready` is sent;
  app-only options appear **only after `hello`**, and only for the features it lists (so an app that
  doesn't list `switchHost` gets no "Change server" button).
- Settings → Account: **Log out** → after the server answers, sends `logout`; a note says that in the app
  this returns to the server list. **Change server** button sends `switchHost`. "Log out everywhere"
  also sends `logout`.
- Login, first-admin setup and "server offline" screens: a **Change server** link (`switchHost`).
- Expired / ended logins (HTTP 401, WebSocket close 4401) send nothing: the web login screen shows.

### Security

- Only the current host's exact origin gets `MoonLightApp`; messages from any other origin are ignored.
- The bridge carries navigation commands only. The app never passes cookies, tokens or passwords to the
  page, and the page can't ask the app to do anything outside the table above.

### Testing

- App: unit tests for parsing and dispatch (unknown type ignored, wrong `v` ignored, `logout` and
  `switchHost` lead to the host list without auto-open); manual test against a server with the web side
  implemented.
- End to end: run a server on the PC (`moonlightcontrol serve`, from the server repo's `dist/`), open it in
  the app on the tablet (PC's LAN IP, port 8080), log in, then: Settings → Account → **Log out** → the app
  must show the host list (and not auto-open); log in again → **Change server** → host list, and opening
  the same host again needs no login. On the login screen, **Change server** must also leave.

## 7. Discovery (optional, milestone 4)

The server does not advertise itself (no mDNS service yet). Practical options:

1. Scan the tablet's current Wi-Fi subnet (usually a /24) on port 8080: `GET /api/v1/health` with a short
   timeout (~300 ms), ~32 requests in parallel; list hits with their version. Then also try the
   `.local` names of known hosts.
2. Later, if the server repo adds an mDNS/DNS-SD advertisement (e.g. `_moonlightcontrol._tcp`), use
   `NsdManager` instead. That is a server change: note it in OPEN_QUESTIONS for the user.

Check the current Android rules for local-network access (newer versions add permissions/prompts for
LAN scanning) before implementing.

## 8. Milestones

1. **Skeleton** — Gradle project, package id `br.com.moonaudio.moonlightcontrol` (was `io.github.luizhenriquemm.moonlightcontrol` until 2026-11-17;
   permanent once on Google Play), app name **Moon Light Control** and icon (the crescent moon of the web
   UI), one Activity, WebView opening a typed URL, cleartext config, unit-test setup,
   `./gradlew lint test` green. Check Android's local-network access rules for the target SDK.
2. **Hosts** — host model, storage, list/add/edit/delete/reorder, auto-resume of the last host, Test via
   `/api/v1/health`, status dots, URL building with IPv6. Unit tests for validation and URLs.
3. **Console polish** — full screen, keep-awake, orientation, back behavior, error view, downloads,
   file chooser, self-signed HTTPS trust on first use, dark mode, persistence of cookies, the **app
   bridge** (§6; the server side is already implemented). Manual test
   list on the real tablet against a real server (import, export, faders with several fingers, scene
   recall, HTTPS).
4. **Discovery** — subnet scan (and mDNS if the server adds it). Part of v1. Subnet scan done
   2026-11-17 (see DECISIONS).
5. **Release** — signing key (kept outside the repo), versioning, APK for sideloading first, then
   Google Play (developer account, AAB, Play App Signing, Data safety form, privacy policy, policies).
   Publisher = the user as an individual: 12-tester / 14-day closed test, developer verification
   (Brazil, since 2026-09-30); privacy policy and product page on moonaudio.com.br.
   CI (GitHub Actions) when the user asks.
6. **iOS** (later) — same shell on iOS (WKWebView). Needs a Mac with Xcode and an Apple Developer account.

## 9. Open questions

Answered 2026-11-16 (see DECISIONS.md): name MoonLightControl; APK then Google Play; phones too, iOS
later; discovery in v1; auto-resume of the last host when reachable; minimum Android 8.0 (API 26).
Still pending: see OPEN_QUESTIONS.md.

## 10. Development environment (Windows machine of the user)

Not installed yet: JDK 17+ (e.g. Temurin via winget), Android SDK (Android Studio, or command-line
tools), `adb`. Testing on the real tablet needs **USB debugging** (or wireless debugging) enabled. A
MoonLightControl server for testing can run on the same PC (`moonlightcontrol serve`, port 8080) — the
tablet reaches it on the PC's LAN IP. Don't send DMX to real fixtures without asking the user first.
