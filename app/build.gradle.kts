import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

// ✅ Room schema export (for future migrations)
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.mmalyil.smartdocai"
    compileSdk = 35

    ndkVersion = "28.0.12433566" // any r28 build you have installed is fine


    val groqApiKey = (localProperties["GROQ_API_KEY"] ?: "MISSING_KEY").toString()
    val openAiApiKey = (localProperties["OPENAI_API_KEY"] ?: "DUMMY_OPENAI_KEY").toString()

    defaultConfig {
        applicationId = "com.mmalyil.smartdocai"
        minSdk = 26
        targetSdk = 35
        versionCode = 23
        versionName = "2.2"


        ndk {
            // Ship only what you support. This still satisfies Play’s 64-bit (arm64-v8a) requirement.
            abiFilters += listOf("arm64-v8a")
        }

        // 🚩 Feature flags
        buildConfigField("boolean", "USE_GOOGLE_DOCS", "false")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 🔐 Keys (placeholders if local.properties missing)
        buildConfigField("String", "GROQ_API_KEY", "\"$groqApiKey\"")
        buildConfigField("String", "OPENAI_API_KEY", "\"$openAiApiKey\"")


    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }
    composeOptions {
        // Keep aligned with your Compose libs (1.5.x UI is fine with 1.5.10)
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // ❌ Not needed; KSP wires sources automatically and this can cause dupes
    // sourceSets { getByName("main").java.srcDirs("build/generated/ksp/main/kotlin") }

    packaging {
        jniLibs {
            // Use modern packaging for Play
            useLegacyPackaging = false
        }
        resources {
            // Prevent META-INF collisions from POI/XmlBeans/etc.
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module"
            )
            // Or use pickFirsts if a specific file keeps colliding:
            // pickFirsts += listOf("META-INF/DEPENDENCIES")
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "USE_GOOGLE_DOCS", "false")
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            buildConfigField("boolean", "USE_GOOGLE_DOCS", "false")

            // Keep off to avoid POI/XmlBeans obfuscation issues (you can revisit later)
            isMinifyEnabled = false
            isShrinkResources = false

            // Keep a proguard file ready if you turn minify on later
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // ✅ Make sure you’ve set up a proper signingConfig in your project for Play
            // signingConfig = signingConfigs.getByName("release")
        }
    }

    bundle {
        abi { enableSplit = true }
    }

}

    // Keep POI logging clean; avoid bringing a SLF4J impl accidentally
    configurations.all {
        resolutionStrategy.force(
            "androidx.camera:camera-core:1.5.0",
            "androidx.camera:camera-camera2:1.5.0",
            "androidx.camera:camera-lifecycle:1.5.0",
            "androidx.camera:camera-view:1.5.0"
            // add video/mlkit-vision here if you use them
        )


        exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j-impl")
    }

    // Optional: don’t fail build on minor lint (can enable before publish if clean)
    // lint { abortOnError = false }


dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")

    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Billing
    implementation("com.android.billingclient:billing:7.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // CameraX
    val camerax = "1.5.0" // or newer stable
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // ML Kit
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // PDF & documents
    implementation("com.itextpdf:itextpdf:5.5.13.4")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.madgag:scpkix-jdk15on:1.47.0.1")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0")

    // UI & Onboarding
    implementation("com.github.AppIntro:AppIntro:6.3.1")

    // Play Integrity
    implementation("com.google.android.play:integrity:1.3.0")

    // Compose (matches compiler 1.5.10)
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
    implementation("androidx.compose.ui:ui:1.5.4")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Google Sign-In (for future Google Docs import)
    implementation("com.google.android.gms:play-services-auth:21.1.1")

    // Apache POI – DOCX/PPTX/XLSX
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    implementation("org.apache.xmlbeans:xmlbeans:5.1.1")
    implementation("org.apache.commons:commons-compress:1.26.1")
    implementation("com.github.virtuald:curvesapi:1.07")
    implementation("org.tukaani:xz:1.9")

    // StAX
    implementation("com.fasterxml.woodstox:woodstox-core:6.5.1")
    implementation("org.codehaus.woodstox:stax2-api:4.2.1")
    implementation("javax.xml.stream:stax-api:1.0-2")

    // Log4j API only (no impl)
    implementation("org.apache.logging.log4j:log4j-api:2.20.0")

    // Desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Helpful AndroidX updates
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.fragment:fragment-ktx:1.8.2")


    implementation("androidx.datastore:datastore-preferences:1.1.1")



        implementation(platform("androidx.compose:compose-bom:2025.01.00")) // or your current stable
        implementation("androidx.compose.ui:ui")

        implementation("androidx.compose.foundation:foundation") // <-- pager lives here
        implementation("androidx.compose.ui:ui-tooling-preview")
        debugImplementation("androidx.compose.ui:ui-tooling")


}