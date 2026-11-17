# Open questions

When answered, record the answer in DECISIONS.md and remove it from here.

## Pending
- Does the user want an option to **not** stay logged in (e.g. a shared tablet)? Logins persist
  30 days today. App-only option: forget a server's cookie when leaving it; a "remember me" checkbox
  on the web login would need the server repo.
- Milestone 2 scope after the recent-servers list: are names / editing / auto-resume of the last
  server still wanted, or is the recent list enough?
- To verify in milestone 3: on large screens (tablets), recent Android versions ignore an app's
  orientation lock for apps targeting new SDKs — "landscape by default" may not be enforceable; the
  native screens must work in portrait anyway. Check on the real tablet.
- To verify on an Android 17 device: discovery and the console with `ACCESS_LOCAL_NETWORK` (only
  tried on the API 37 emulator for the prompt). iOS will need the "Local Network" prompt
  (`NSLocalNetworkUsageDescription`).
- Phones: on a lighting Wi-Fi without internet while mobile data is on, Android may route the WebView's
  traffic through mobile data (default network). Discovery already binds to the Wi-Fi network; the
  console may need `bindProcessToNetwork`. Check on a phone.
- Discovery only finds servers on port 8080 in the device's /24. For the server repo (not this one):
  an mDNS/DNS-SD advertisement and/or a product field in `/api/v1/health` (e.g.
  `"product":"moonlightcontrol"`) would make discovery simpler and unambiguous.
- Before the Play release: add the GPL-3.0-or-later `LICENSE` file and per-file notices policy;
  write the privacy policy (no data collected, LAN only) for moonaudio.com.br; line up 12 testers for
  the 14-day closed test; create the Play Console account (individual, identity verification).
- If Moon Audio gets a CNPJ later: an organization account (D-U-N-S + website verified in Search
  Console) skips the 12-tester rule; the package id already fits.
