pluginManagement {
    repositories {
        google() // ✅ Required for 'com.android.application'
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io") // ✅ Add this
    }
}
rootProject.name = "SmartDocAI"
include(":app")