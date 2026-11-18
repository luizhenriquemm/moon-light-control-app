# R8 rules for the release build. The libraries in use (Compose, WebKit, DataStore,
# kotlinx.serialization, coroutines) ship their own consumer rules; nothing app-specific is needed:
# no reflection, no JavaScript interface (the app bridge uses WebMessageListener).
