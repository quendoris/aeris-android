// SPDX-FileCopyrightText: 2026 quendoris
// SPDX-License-Identifier: AGPL-3.0-only

buildscript {
    dependencies {
        // AGP 9 provides built-in Kotlin. Pin a newer KGP runtime so the
        // Compose compiler plugin can use the same Kotlin toolchain version.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

plugins {
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
