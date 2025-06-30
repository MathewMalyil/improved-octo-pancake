import java.util.Properties
plugins {
    id("com.android.application") version "8.11.0"
    id("org.jetbrains.kotlin.android") version "1.9.0"
    id("com.google.devtools.ksp") version "1.9.0-1.0.13" // ✅ This is the correct plugin ID and version
}


// ✅ Add this at the top of the file


// ✅ Define localProperties BEFORE the android { } block
val localProperties = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        load(localPropsFile.inputStream())
    }
}

android {
    namespace = "com.mmalyil.smartdocai"
    compileSdk = 34

    val groqApiKey = localProperties["GROQ_API_KEY"] ?: "MISSING_KEY"

    defaultConfig {
        applicationId = "com.mmalyil.smartdocai"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ✅ Inject into BuildConfig
        buildConfigField("String", "GROQ_API_KEY", "\"$groqApiKey\"")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main").java.srcDirs("build/generated/ksp/main/kotlin")
    }
}






android {
    namespace = "com.mmalyil.smartdocai"
    compileSdk = 34

    val groqApiKey = localProperties["GROQ_API_KEY"] ?: "MISSING_KEY"

    defaultConfig {
        applicationId = "com.mmalyil.smartdocai"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ✅ Inject your API key here
        buildConfigField("String", "GROQ_API_KEY", "\"$groqApiKey\"")
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main").java.srcDirs("build/generated/ksp/main/kotlin")
    }


    defaultConfig {
        buildConfigField(
            "String",
            "GROQ_API_KEY",
            "\"${localProperties["GROQ_API_KEY"] ?: "MISSING_KEY"}\""
        )

        buildConfigField("String", "OPENAI_API_KEY", "\"DUMMY_OPENAI_KEY\"")


    }
}


    dependencies {
        // Core Android
        implementation("androidx.core:core-ktx:1.12.0")
        implementation("androidx.appcompat:appcompat:1.6.1")
        implementation("com.google.android.material:material:1.9.0")

        // RecyclerView
        implementation("androidx.recyclerview:recyclerview:1.3.2")

        // Lifecycle & ViewModel (consistent version 2.7.0)
        implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
        implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
        implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

        // Room + KSP
        implementation("androidx.room:room-runtime:2.6.1")
        implementation("androidx.room:room-ktx:2.6.1")
        ksp("androidx.room:room-compiler:2.6.1")

        // Coroutines
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

        // CameraX
        implementation("androidx.camera:camera-core:1.3.1")
        implementation("androidx.camera:camera-camera2:1.3.1")
        implementation("androidx.camera:camera-lifecycle:1.3.1")
        implementation("androidx.camera:camera-view:1.3.1")

        // ML Kit
        implementation("com.google.mlkit:text-recognition:16.0.0")
        implementation("com.google.mlkit:barcode-scanning:17.2.0")

        // File picker and permissions
        implementation("com.github.dhaval2404:imagepicker:2.1")
        implementation("com.karumi:dexter:6.2.3")

        // PDF support
        implementation("com.itextpdf:itextpdf:5.5.13.3")
        implementation("com.tom-roush:pdfbox-android:1.8.10.3")
        implementation("com.madgag:scpkix-jdk15on:1.47.0.1")
        implementation("org.apache.poi:poi:5.2.5")
        implementation("org.apache.poi:poi-ooxml:5.2.5")
        implementation("org.apache.xmlbeans:xmlbeans:5.1.1")
        implementation("org.apache.commons:commons-collections4:4.4")

        // Networking
        implementation("com.squareup.retrofit2:retrofit:2.9.0")
        implementation("com.squareup.retrofit2:converter-gson:2.9.0")
        implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")


        implementation("com.google.android.material:material:1.12.0") // or latest

    }