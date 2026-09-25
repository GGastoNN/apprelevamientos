package com.illu.relevametal.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.illu.relevametal.personalization.AppThemeMode
import com.illu.relevametal.personalization.DEFAULT_ACCENT
import com.illu.relevametal.personalization.PersonalizationSettings
import com.illu.relevametal.personalization.colorInt

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

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private fun blend(from: Color, to: Color, amount: Float): Color {
    val a = amount.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * a,
        green = from.green + (to.green - from.green) * a,
        blue = from.blue + (to.blue - from.blue) * a,
        alpha = 1f
    )
}

private fun onColor(background: Color): Color =
    if (background.luminance() > 0.46f) Color(0xFF111111) else Color.White

private fun withAccent(base: ColorScheme, accent: Color, dark: Boolean): ColorScheme {
    val primary = if (dark) blend(accent, Color.White, 0.22f) else accent
    val container = if (dark) blend(accent, Color.Black, 0.46f) else blend(accent, Color.White, 0.78f)
    val secondary = if (dark) blend(primary, Color(0xFFD5D5D5), 0.38f) else blend(primary, Color(0xFF5A5A5A), 0.42f)
    return base.copy(
        primary = primary,
        onPrimary = onColor(primary),
        primaryContainer = container,
        onPrimaryContainer = onColor(container),
        secondary = secondary,
        secondaryContainer = if (dark) blend(secondary, Color.Black, 0.5f) else blend(secondary, Color.White, 0.8f)
    )
}

@Composable
fun GrupoIdeaTheme(
    settings: PersonalizationSettings = PersonalizationSettings(),
    content: @Composable () -> Unit
) {
    val dark = when (settings.themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val accent = Color(colorInt(settings.accentColorHex, DEFAULT_ACCENT))
    val colors = withAccent(if (dark) DarkColors else LightColors, accent, dark)
    MaterialTheme(
        colorScheme = colors,
        shapes = AppShapes,
        content = content
    )
}
