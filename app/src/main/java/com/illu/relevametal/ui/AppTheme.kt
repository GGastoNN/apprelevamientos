package com.illu.relevametal.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B5E55),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA7F2E5),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4B635E),
    secondaryContainer = Color(0xFFCDE8E1),
    tertiary = Color(0xFF456179),
    background = Color(0xFFF7FAF8),
    surface = Color(0xFFF7FAF8),
    surfaceVariant = Color(0xFFDEE5E2),
    outline = Color(0xFF6F7976),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BD5C9),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005047),
    onPrimaryContainer = Color(0xFFA7F2E5),
    secondary = Color(0xFFB1CCC5),
    secondaryContainer = Color(0xFF344B47),
    tertiary = Color(0xFFADCBE6),
    background = Color(0xFF0F1513),
    surface = Color(0xFF0F1513),
    surfaceVariant = Color(0xFF3F4946),
    outline = Color(0xFF89938F),
    error = Color(0xFFFFB4AB)
)

@Composable
fun GrupoIdeaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
