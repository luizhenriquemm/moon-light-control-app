# Open questions

When answered, record the answer in DECISIONS.md and remove it from here.

## Pending
- To verify in milestone 3: on large screens (tablets), recent Android versions ignore an app's
  orientation lock for apps targeting new SDKs — "landscape by default" may not be enforceable; the
  native screens must work in portrait anyway. Check on the real tablet.
- To verify in milestone 4: discovery with `ACCESS_LOCAL_NETWORK` granted (subnet scan); iOS will need
  the equivalent "Local Network" prompt (`NSLocalNetworkUsageDescription`).
- Before the Play release: add the GPL-3.0-or-later `LICENSE` file and per-file notices policy.
- For the server repo (not this one): an mDNS/DNS-SD advertisement would make discovery simpler.
