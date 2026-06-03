plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}


android {
    namespace = "xyz.hvdw.mapplayer"
    compileSdk = 34

    defaultConfig {
        applicationId = "xyz.hvdw.mapplayer"
        minSdk = 29  // Android 10
        targetSdk = 33
        versionCode = 5
        versionName = "1.0.4"

        // Only include the ABIs you want in the final APK
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    // Enable shrinking + minification
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        getByName("debug") {
            // Keep debug builds fast
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    // Remove unwanted native libs if any dependency tries to include them
    packaging {
        jniLibs {
            excludes += listOf(
                "**/armeabi/**",
                "**/armeabi-v7a/**",
                "**/x86/**"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = false
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // --- AndroidX core UI ---
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // --- MediaSession + notifications ---
    implementation("androidx.media:media:1.6.0")

    // --- ExoPlayer (core + UI) ---
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.19.1")

    // --- Coroutines ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // --- Optional but recommended ---
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    implementation("com.google.code.gson:gson:2.10.1")
}
