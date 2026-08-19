// SPDX-FileCopyrightText: 2026 quendoris
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.quendoris.aeris

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AerisDarkColors = darkColorScheme(
    primary = Color(0xFF8FA7C3),
    onPrimary = Color(0xFF111418),
    secondary = Color(0xFFA392BA),
    background = Color(0xFF17191D),
    onBackground = Color(0xFFF0F1ED),
    surface = Color(0xFF1D2025),
    onSurface = Color(0xFFF0F1ED),
    surfaceVariant = Color(0xFF272B31),
    onSurfaceVariant = Color(0xFFB9BEC6),
    error = Color(0xFFEB8585),
)

@Composable
fun AerisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AerisDarkColors,
        content = content,
    )
}
