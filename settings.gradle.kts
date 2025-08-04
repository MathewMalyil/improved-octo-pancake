pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }

    plugins {
        id("com.android.application") version "8.12.0" apply false
        id("com.android.library") version "8.12.0" apply false
        id("org.jetbrains.kotlin.android") version "1.9.22" apply false
        id("com.google.devtools.ksp") version "1.9.21-1.0.15" apply false
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

