import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val localProperties = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        load(localPropsFile.inputStream())
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.mmalyil.smartdocai"
    compileSdk = 35

    val groqApiKey = localProperties["GROQ_API_KEY"] ?: "MISSING_KEY"
    val openAiApiKey = localProperties["OPENAI_API_KEY"] ?: "DUMMY_OPENAI_KEY"

    defaultConfig {
        applicationId = "com.mmalyil.smartdocai"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "1.7"


        // 🚩 Feature flag for Google Docs
        buildConfigField ("boolean", "USE_GOOGLE_DOCS", "false")



        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GROQ_API_KEY", "\"$groqApiKey\"")
        buildConfigField("String", "OPENAI_API_KEY", "\"$openAiApiKey\"")
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }
    composeOptions {
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
    sourceSets {
        getByName("main").java.srcDirs("build/generated/ksp/main/kotlin")
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            // EITHER exclude duplicates:
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module"
            )

            // OR (alternative) keep only the first copy of a file:
            // pickFirsts += listOf("META-INF/DEPENDENCIES")


        }


    }

    buildTypes {
        debug {
            buildConfigField("boolean", "USE_GOOGLE_DOCS", "true")
            // leave shrink/minify off in debug by default
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            buildConfigField("boolean", "USE_GOOGLE_DOCS", "true")

            // 🚨 Turn OFF minify & shrink to match the working behavior on device
            isMinifyEnabled = false
            isShrinkResources = false

            // You can keep the file — it’s ignored when minify=false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Keep this minimal. Don’t force/strip random things that POI needs.
    configurations.all {
        // This one prevents an unwanted SLF4J impl from sneaking in
        exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j-impl")
    }
}



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
    implementation("com.android.billingclient:billing:6.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.6.4")

    // CameraX
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    // ML Kit
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // File & permissions
    implementation("com.github.dhaval2404:imagepicker:2.1")
    implementation("com.karumi:dexter:6.2.3")

    // PDF & document support
    implementation("com.itextpdf:itextpdf:5.5.13.3")
    implementation("com.tom-roush:pdfbox-android:1.8.10.3")
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

    // MediaPipe
    implementation("com.google.mediapipe:tasks-vision:0.10.26")

    // Compose
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
    implementation("androidx.compose.ui:ui:1.5.4")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    //implementation("androidx.multidex:multidex:2.0.1")


    // ✅ Android GMS Auth (already likely present)
    implementation("com.google.android.gms:play-services-auth:21.0.0")


    // Apache POI – DOCX, PPTX, XLSX support (OOXML)
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")

// Needed for PPTX, DOCX text extraction (XSLF, XWPF)
    implementation("org.apache.poi:poi-ooxml-lite:5.2.5") // optional if size matters
    // POI deps for OPC/ZIP/XML parsing
    implementation("org.apache.xmlbeans:xmlbeans:5.1.1")
    implementation("org.apache.commons:commons-compress:1.26.1")
    implementation("com.github.virtuald:curvesapi:1.07")

    implementation("org.tukaani:xz:1.9")

    // StAX (Woodstox)
    implementation("com.fasterxml.woodstox:woodstox-core:6.5.1")
    implementation("org.codehaus.woodstox:stax2-api:4.2.1")
    implementation("javax.xml.stream:stax-api:1.0-2")

    // Log4j API (used by POI; API only, not the impl)
    implementation("org.apache.logging.log4j:log4j-api:2.20.0")

    // Desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}