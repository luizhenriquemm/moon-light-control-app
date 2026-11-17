plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    // iOS targets are added in the iOS milestone (they only build on macOS). Until then, keep
    // commonMain free of JVM/Android APIs so it stays portable.
    android {
        namespace = "io.github.luizhenriquemm.moonlightcontrol.shared"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// `./gradlew test` (the project's standard check, see CLAUDE.md) also runs the shared tests.
tasks.register("test") {
    group = "verification"
    description = "Runs the shared module's unit tests (commonTest on the JVM)."
    dependsOn("testAndroidHostTest")
}
