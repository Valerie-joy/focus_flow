package com.focusflow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Extra design tokens Material3's ColorScheme doesn't model natively:
 * glass fills, ambient gradients, semantic success/warning colors.
 * Access anywhere via `LocalFocusFlowColors.current`.
 */
data class FocusFlowColors(
    val glassSurface: Color,
    val glassBorder: Color,
    val success: Color,
    val warning: Color,
    val heroGradient: Brush,
    val isDark: Boolean
)

private val LightExtendedColors = FocusFlowColors(
    glassSurface = LightSurfaceGlass,
    glassBorder = GlassBorderLight,
    success = Success,
    warning = Warning,
    heroGradient = Brush.linearGradient(
        listOf(GradientLightStart, GradientLightMid, GradientLightEnd)
    ),
    isDark = false
)

private val DarkExtendedColors = FocusFlowColors(
    glassSurface = DarkSurfaceGlass,
    glassBorder = GlassBorderDark,
    success = Success,
    warning = Warning,
    heroGradient = Brush.linearGradient(
        listOf(GradientDarkStart, GradientDarkMid, GradientDarkEnd)
    ),
    isDark = true
)

val LocalFocusFlowColors = staticCompositionLocalOf { LightExtendedColors }

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    secondary = Secondary,
    onSecondary = Color.White,
    tertiary = Accent,
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceGlass,
    onSurfaceVariant = TextSecondaryLight,
    error = Error,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDarkMode,
    onPrimary = Color(0xFF2B1522),
    secondary = AccentDarkMode,
    onSecondary = Color(0xFF211531),
    tertiary = AccentDarkMode,
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceGlass,
    onSurfaceVariant = TextSecondaryDark,
    error = Error,
    onError = Color.White
)

@Composable
fun FocusFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalFocusFlowColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FocusFlowTypography,
            shapes = FocusFlowShapes,
            content = content
        )
    }
}
