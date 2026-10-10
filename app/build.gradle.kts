plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "ai.homa.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "ai.homa.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 6
        versionName = "1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signing config — provide keystore via environment or local.properties
            signingConfig = signingConfigs.create("release") {
                storeFile = file(System.getenv("KEYSTORE_PATH") ?: "keystore/homa-release.jks")
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: "homa"
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    // Flavor dimensions for different markets
    flavorDimensions += "market"
    productFlavors {
        create("googleplay") {
            dimension = "market"
            buildConfigField("String", "MARKET", "\"googleplay\"")
        }
        create("myket") {
            dimension = "market"
            buildConfigField("String", "MARKET", "\"myket\"")
        }
        create("bazaar") {
            dimension = "market"
            buildConfigField("String", "MARKET", "\"bazaar\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Splash Screen (Android 12+)
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Firebase BoM — ensures all Firebase libraries use compatible versions
    implementation(platform("com.google.firebase:firebase-bom:33.3.0"))
    // Firebase Cloud Messaging (Push Notifications) — version managed by BoM
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-analytics")

    // Google Play Billing (googleplay flavor only)
    googleplayImplementation("com.android.billingclient:billing-ktx:7.1.1")

    // Google Play In-App Review API (googleplay flavor only)
    googleplayImplementation("com.google.android.play:review-ktx:2.0.2")

    // Myket Payment SDK (myket flavor only)
    myketImplementation("ir.myket:myketsdk:1.3.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}