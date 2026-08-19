// SPDX-FileCopyrightText: 2026 quendoris
// SPDX-License-Identifier: AGPL-3.0-only

buildscript {
    dependencies {
        // AGP 9 compiles Kotlin through built-in Kotlin. Compose remains an
        // explicit compiler plugin and is pinned independently to Kotlin 2.3.21.
        classpath(
            "org.jetbrains.kotlin.plugin.compose:" +
                "org.jetbrains.kotlin.plugin.compose.gradle.plugin:2.3.21"
        )
    }
}

plugins {
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
