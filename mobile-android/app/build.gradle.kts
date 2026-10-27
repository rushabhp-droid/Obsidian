plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release builds (scripts/build.sh android): the server address the app starts with, and the
// signing key. Without OBSIDIAN_ANDROID_KEYSTORE the release APK is unsigned.
val releaseServerUrl = System.getenv("OBSIDIAN_SERVER_URL").orEmpty().trim().trimEnd('/')
require(releaseServerUrl.isEmpty() || Regex("^https?://[A-Za-z0-9.:\\[\\]_-]+(/[A-Za-z0-9._~/-]*)?$").matches(releaseServerUrl)) {
    "OBSIDIAN_SERVER_URL must be an http(s) URL such as https://obsidian.example.com"
}
val releaseKeystore = System.getenv("OBSIDIAN_ANDROID_KEYSTORE")?.takeIf { it.isNotBlank() }

android {
    namespace = "com.obsidian.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.obsidian.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The B2C API origin until the user changes it in Settings (or on the sign-in screen).
        buildConfigField("String", "DEFAULT_SERVER_URL", "\"$releaseServerUrl\"")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("OBSIDIAN_ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("OBSIDIAN_ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("OBSIDIAN_ANDROID_KEY_PASSWORD")
                    ?: System.getenv("OBSIDIAN_ANDROID_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // The host machine as seen from the Android emulator (gradle bootTestRun on :8080).
            buildConfigField("String", "DEFAULT_SERVER_URL", "\"http://10.0.2.2:8080\"")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Security & Biometrics
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)

    // Google sign-in through Credential Manager
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Networking & Serialization
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.json)
    implementation(libs.okhttp.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
