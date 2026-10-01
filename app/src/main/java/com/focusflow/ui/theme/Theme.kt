package com.focusflow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Semantic tokens Material 3's [androidx.compose.material3.ColorScheme] has no
 * slot for: success/warning status pairs, the ambient page wash, and the card
 * surface/border pair used by [com.focusflow.ui.components.GlassCard].
 *
 * Every field is resolved per theme rather than computed at the call site, so a
 * screen never has to ask "am I in dark mode?" to pick a color — that question
 * has exactly one answer per token and it lives here.
 *
 * Access via `LocalFocusFlowColors.current`.
 */
@Immutable
data class FocusFlowColors(
    /** Card/raised surface fill. Replaces the old translucent "glass" fill. */
    val glassSurface: Color,
    /** Hairline card border. Carries the card edge where elevation can't. */
    val glassBorder: Color,
    val success: Color,
    val onSuccessContainer: Color,
    val successContainer: Color,
    val warning: Color,
    val onWarningContainer: Color,
    val warningContainer: Color,
    val errorContainerSoft: Color,
    val onErrorContainerSoft: Color,
    /** Near-flat page wash. Not a decorative gradient — see Color.kt. */
    val heroGradient: Brush,
    /** Scrim for overlays on top of camera/video surfaces. */
    val scrim: Color,
    val isDark: Boolean
)

private val LightExtendedColors = FocusFlowColors(
    glassSurface = LightSurface,
    glassBorder = BorderLight,
    success = Success,
    onSuccessContainer = Color(0xFF10301D),
    successContainer = SuccessContainerLight,
    warning = Warning,
    onWarningContainer = Color(0xFF3B2705),
    warningContainer = WarningContainerLight,
    errorContainerSoft = ErrorContainerLight,
    onErrorContainerSoft = Color(0xFF4A1219),
    heroGradient = Brush.verticalGradient(listOf(GradientLightStart, GradientLightEnd)),
    scrim = ScrimLight,
    isDark = false
)

private val DarkExtendedColors = FocusFlowColors(
    glassSurface = DarkSurfaceContainer,
    glassBorder = BorderDark,
    success = SuccessDarkMode,
    onSuccessContainer = Color(0xFFCCEBD8),
    successContainer = SuccessContainerDark,
    warning = WarningDarkMode,
    onWarningContainer = Color(0xFFF7E6CB),
    warningContainer = WarningContainerDark,
    errorContainerSoft = ErrorContainerDark,
    onErrorContainerSoft = Color(0xFFF8DDE0),
    heroGradient = Brush.verticalGradient(listOf(GradientDarkStart, GradientDarkEnd)),
    scrim = ScrimDark,
    isDark = true
)

val LocalFocusFlowColors = staticCompositionLocalOf { LightExtendedColors }

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = Secondary,
    onSecondary = Color.White,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = Color(0xFF16203A),
    tertiary = Accent,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHigh,
    surfaceContainerLow = LightSurface,
    surfaceContainerLowest = Color.White,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = Error,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = Color(0xFF4A1219),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2B3334),
    inverseOnSurface = Color(0xFFEFF2F2),
    inversePrimary = PrimaryDarkMode
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDarkMode,
    onPrimary = Color(0xFF00201E),
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = AccentDarkMode,
    onSecondary = Color(0xFF12203A),
    secondaryContainer = Color(0xFF2C3C58),
    onSecondaryContainer = Color(0xFFDDE3EF),
    tertiary = AccentDarkMode,
    onTertiary = Color(0xFF12203A),
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHigh,
    surfaceContainerLow = DarkSurface,
    surfaceContainerLowest = DarkBackground,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = ErrorDarkMode,
    onError = Color(0xFF3A0A10),
    errorContainer = ErrorContainerDark,
    onErrorContainer = Color(0xFFF8DDE0),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE3E6E6),
    inverseOnSurface = Color(0xFF16201F),
    inversePrimary = Primary
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
