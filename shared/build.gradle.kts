plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // iOS targets are added in the iOS milestone (they only build on macOS). Until then, keep
    // commonMain free of JVM/Android APIs so it stays portable.
    android {
        namespace = "br.com.moonaudio.moonlightcontrol.shared"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// `./gradlew test` (the project's standard check, see CLAUDE.md) also runs the shared tests.
tasks.register("test") {
    group = "verification"
    description = "Runs the shared module's unit tests (commonTest on the JVM)."
    dependsOn("testAndroidHostTest")
}
