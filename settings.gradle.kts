pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MoonLightControl"

// Platform-free logic shared with a future iOS app (Kotlin Multiplatform).
include(":shared")
// The Android app: native screens + the WebView console.
include(":androidApp")
