plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.mmalyil.smartdocai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mmalyil.smartdocai"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val localProps = rootProject.file("local.properties").reader().useLines { lines ->
        lines.mapNotNull {
            val parts = it.split("=")
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
        }.toMap()
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField("String", "GROQ_API_KEY", "\"${localProps["GROQ_API_KEY"]}\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "GROQ_API_KEY", "\"${localProps["GROQ_API_KEY"]}\"")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true // Enable buildConfig to access API keys
    }




    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }


    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false

    }
    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"

            exclude("META-INF/DEPENDENCIES")
            exclude("META-INF/LICENSE")
            exclude("META-INF/LICENSE.txt")
            exclude("META-INF/NOTICE")
            exclude("META-INF/NOTICE.txt")
        }



    }

    dependencies {

        implementation(libs.androidx.core.ktx)
        implementation(libs.androidx.appcompat)
        implementation(libs.material)
        implementation(libs.androidx.activity)
        implementation(libs.androidx.constraintlayout)
        testImplementation(libs.junit)
        androidTestImplementation(libs.androidx.junit)
        androidTestImplementation(libs.androidx.espresso.core)


        // PDF text extraction


        // Networking
        implementation("com.squareup.retrofit2:retrofit:2.9.0")
        implementation("com.squareup.retrofit2:converter-gson:2.9.0")
        implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")
        // Coroutines
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
        // File picker
        implementation("com.github.dhaval2404:imagepicker:2.1")
        // Permissions
        implementation("com.karumi:dexter:6.2.3")
        // Optional: JitPack for any dependencies
        // Networking
        implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")
        // Working PDFBox implementation
        implementation("com.tom-roush:pdfbox-android:1.8.10.3")
        // Required additional dependencies
        implementation("com.madgag:scpkix-jdk15on:1.47.0.1")
        implementation("org.apache.poi:poi-ooxml:5.2.3")
        implementation("org.apache.xmlbeans:xmlbeans:5.1.1")
        implementation("org.apache.commons:commons-collections4:4.4")


       // Or use 2.1.7 if sticking to older version

        implementation("com.itextpdf:itextpdf:5.5.13.3") // Or use 2.1.7 if sticking to older version





        implementation("androidx.camera:camera-core:1.3.1")
        implementation("androidx.camera:camera-camera2:1.3.1")
        implementation("androidx.camera:camera-lifecycle:1.3.1")
        implementation("androidx.camera:camera-view:1.3.1")

        implementation("com.google.mlkit:text-recognition:16.0.0")
        }


}



















