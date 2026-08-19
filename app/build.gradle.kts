// SPDX-FileCopyrightText: 2026 quendoris
// SPDX-License-Identifier: AGPL-3.0-only

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.quendoris.aeris"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "io.github.quendoris.aeris"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-dev"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = providers.environmentVariable("AERIS_CMAKE_VERSION")
                .orElse("3.31.6")
                .get()
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
