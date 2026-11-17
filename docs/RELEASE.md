# Release checklist — Google Play closed test

Publisher: the user as an individual (personal Play Console account). Package
`br.com.moonaudio.moonlightcontrol` (permanent). Status as of 2026-11-17.

## App (Claude)
- [x] Release signing wired: `androidApp/build.gradle.kts` reads
      `%USERPROFILE%\.android-signing\moonlightcontrol.properties` (or `MLC_SIGNING_PROPERTIES`);
      without it the release build is unsigned.
- [x] Upload key created by the user (2026-11-17): `CN=Moon Light Control, O=Moon Audio, C=BR`,
      certificate SHA-256 `83a90b75a07b9514595f679ae2edf3c5c04ff8869ffe070b54a97073bdcb2398`.
      **Back up `%USERPROFILE%\.android-signing\` and the password.**
- [x] Release build: R8 + resource shrinking on (APK 12.7 MB → 1.4 MB); smoke-tested on the tablet
      (debug-key-signed copy): discovery, open server, recent list, bridge "Change server".
- [x] `./gradlew bundleRelease` → `androidApp/build/outputs/bundle/release/androidApp-release.aab`
      (versionCode 1, versionName 0.1.0), signed and verified; signed APK installed on the tablet.
      Bump `versionCode` for every upload.
- [x] `LICENSE` (GPL-3.0 text) in the repo; "About" dialog (info button on the start screen):
      version, license, source link, © Moon Audio.
- [x] App repository is public (the About dialog links to it).
- [x] The server repository stays private (user's decision): the "App access" text does not point
      to it; reviewers can review the app's own screens without a server.
- [ ] Remaining manual checks on the tablet (OPEN_QUESTIONS): logout, export, import, multi-touch
      faders, self-signed HTTPS; one phone in portrait; one Android 17 device or emulator for the
      local network permission.
- [x] Store graphics: `docs/store/icon-512.png`, `docs/store/feature-graphic.png`
      (`python tools/store_graphics.py`).
- [x] Texts: `docs/store/listing.md` (pt-BR + en).
- [x] Screenshots (2026-11-17), Play format (RGB PNG, longest side ≤ 2× shortest):
      `docs/store/screenshots/tablet/` (6, real tablet 2304×1440 — use for both 7" and 10" slots) and
      `docs/store/screenshots/phone/` (2, API 37 emulator, start screen + console login; the phone
      emulator was not logged in). Faders shot shows the server's "BLACKOUT ATIVO" banner.
- [x] Privacy policy: `docs/store/privacy-policy.html` (pt-BR + en), contact contato@moonaudio.com.br.
- [x] "App access" instructions for reviewers: `docs/store/listing.md` (explanation only, no demo server).

## Play Console (the user)
- [ ] Create the developer account (one-time US$ 25), individual identity verification; Google may
      also ask to confirm access to a real Android device through the Play Console app.
- [ ] Publish the privacy policy at a moonaudio.com.br URL.
- [ ] App content: privacy policy URL, Data safety (no data collected/shared), ads (none), content
      rating questionnaire, target audience (adults; not for children), **App access**: explain that
      the app controls a self-hosted MoonLightControl server on the local network and how a reviewer
      can try it (risk: reviewers cannot reach a server — give clear instructions / a demo option).
- [ ] Internal testing track first (the user only, no requirements), then the closed track.
- [ ] Closed test: **≥ 12 testers opted in for 14 consecutive days**, then apply for production.
