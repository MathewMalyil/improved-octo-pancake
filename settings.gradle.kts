pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()

        // ✅ Required for docx4j
        maven(url = "https://repo.docx4java.org/public")

        // ✅ Required for Google Docs/Drive API artifacts
        maven(url = "https://maven.google.com")

        maven(url = "https://repo1.maven.org/maven2") // ✅ REQUIRED for google-api-services-*
    }

    plugins {
        id("com.android.application") version "8.12.2" apply false
        id("com.android.library") version "8.12.2" apply false
        id("org.jetbrains.kotlin.android") version "1.9.22" apply false
        id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io") // Required for JitPack-based OpenCV or extras
    }
}

rootProject.name = "SmartDocAI"

// Include modules
include(":app")

