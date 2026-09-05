plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.chrisgrou.mytube"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.chrisgrou.mytube"
        minSdk = 26
        targetSdk = 34
        // CI sets GITHUB_RUN_NUMBER, giving every build a unique, increasing code the
        // in-app update checker can compare against; local builds fall back to 1.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0.0"

        buildConfigField("String", "GITHUB_REPO", "\"chrisgrou/mytube\"")
    }

    signingConfigs {
        // Committed on purpose: this is a debug-only key (never used for a Play
        // Store release), fixed so every CI build is signed identically. Without
        // this, AGP falls back to ~/.android/debug.keystore, which CI regenerates
        // fresh on every run — a new signature each time means Android treats
        // the next APK as a different app and refuses to "update" over the last
        // one, forcing an uninstall before every install.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
