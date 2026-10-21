plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "ai.homa.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ai.homa.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 10
        versionName = "2.0.3"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField(
            "String",
            "WEB_APP_URL",
            "\"https://homa-ai-independent.pages.dev\""
        )

        buildConfigField(
            "String",
            "WORKER_API_URL",
            "\"https://homa-ai-core.shahramalidazeh6203.workers.dev\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            signingConfig = signingConfigs.create("release") {
                storeFile = file(
                    System.getenv("KEYSTORE_PATH")
                        ?: "keystore/homa-release.jks"
                )
                storePassword =
                    System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias =
                    System.getenv("KEY_ALIAS") ?: "homa"
                keyPassword =
                    System.getenv("KEY_PASSWORD") ?: ""

                // Also emit the classic JAR (v1) signature alongside the modern ones
                // so older store verification tools read the same certificate.
                enableV1Signing = true
                enableV2Signing = true
            }
        }

        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    flavorDimensions += "market"

    productFlavors {
        create("googleplay") {
            dimension = "market"

            buildConfigField(
                "String",
                "MARKET",
                "\"googleplay\""
            )
        }

        create("myket") {
            dimension = "market"

            buildConfigField(
                "String",
                "MARKET",
                "\"myket\""
            )

            manifestPlaceholders["marketApplicationId"] = "ir.myket"
            manifestPlaceholders["marketBindAddress"] = "ir.myket"
            manifestPlaceholders["marketPermission"] =
                "ir.myket.permission.PAYMENT"
        }

        create("bazaar") {
            dimension = "market"

            buildConfigField(
                "String",
                "MARKET",
                "\"bazaar\""
            )
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
        buildConfig = true
        viewBinding = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.browser:browser:1.8.0")

    // Splash Screen
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Firebase
    implementation(
        platform("com.google.firebase:firebase-bom:33.3.0")
    )
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-analytics")

    // Google Play Billing
    add(
        "googleplayImplementation",
        "com.android.billingclient:billing-ktx:7.1.1"
    )

    // Google Play In-App Review
    add(
        "googleplayImplementation",
        "com.google.android.play:review-ktx:2.0.2"
    )

    // Myket Billing SDK
    add(
        "myketImplementation",
        "com.github.myketstore:myket-billing-client:1.3"
    )

    // Coroutines
    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1"
    )

    // Testing
    testImplementation("junit:junit:4.13.2")

    androidTestImplementation(
        "androidx.test.ext:junit:1.2.1"
    )

    androidTestImplementation(
        "androidx.test.espresso:espresso-core:3.6.1"
    )
}
