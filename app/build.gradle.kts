plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// A shared signing key keeps every build installable in place. Override by
// dropping a keystore.properties next to this file (never commit a real one).
val keystoreProps = java.util.Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasSharedKey = keystoreProps.isNotEmpty()

android {
    namespace = "com.brain.gallery"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.brain.gallery"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // Target only the phone's ABI. A universal APK shipped four copies of the
        // native libs (arm64-v8a, armeabi-v7a, x86, x86_64) — the x86 pair is
        // emulator-only dead weight that cost ~65 MB.
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        if (hasSharedKey) {
            create("shared") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Same key as release so upgrades work between CI runs.
            if (hasSharedKey) signingConfig = signingConfigs.getByName("shared")
        }
        release {
            if (hasSharedKey) signingConfig = signingConfigs.getByName("shared")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        localeFilters += "en"
    }

    aaptOptions {
        noCompress += "tflite"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    implementation(libs.mlkit.imagelabeling)
    implementation(libs.mlkit.facedetection)
    implementation(libs.tensorflow.lite)

    debugImplementation(libs.androidx.ui.tooling)
}
