/*
 * Copyright (c) 2025 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.geeksville.mesh.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.material3.MaterialTheme as Material3Theme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorPalette = darkColors(
    primary = Color(0xFFEF4938),
    primaryVariant = Color(0xFFA42020),
    onPrimary = Color(0xFF180604),
    secondary = Color(0xFF85D1CF),
    background = Color(0xFF15161B),
    surface = Color(0xFF212329),
    onBackground = Color(0xFFF2EFF3),
    onSurface = Color(0xFFF2EFF3),
)

private val LightColorPalette = lightColors(
    primary = DarkRed,
    primaryVariant = Color(0xFF731717),
    onPrimary = Color.White,
    secondary = Color(0xFF176A70),
    onSecondary = Color.White,
    background = Color(0xFFF4F3F5),
    surface = Color.White,
    onBackground = Color(0xFF24232A),
    onSurface = Color(0xFF24232A),
)

private val DarkScheme = with(DarkColorPalette) {
    darkColorScheme(
        primary = primary, onPrimary = onPrimary, secondary = secondary,
        background = background, onBackground = onBackground, surface = surface, onSurface = onSurface,
        primaryContainer = Color(0xFF55251F), onPrimaryContainer = Color(0xFFFFEFEB),
    )
}

private val LightScheme = with(LightColorPalette) {
    lightColorScheme(
        primary = primary, onPrimary = onPrimary, secondary = secondary,
        background = background, onBackground = onBackground, surface = surface, onSurface = onSurface,
        primaryContainer = Color(0xFFE8E5DF), onPrimaryContainer = Color(0xFF341B16),
    )
}

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    Material3Theme(colorScheme = if (darkTheme) DarkScheme else LightScheme) {
        MaterialTheme(colors = colors, typography = Typography, shapes = Shapes) {
            CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
        }
    }
}