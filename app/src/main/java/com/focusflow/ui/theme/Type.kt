package com.focusflow.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Type scale.
 *
 * Previously this was an editorial serif scale (40sp light-weight serif
 * display) carried over from a luxury-brand reference. It has been replaced
 * with a single-family sans scale for three reasons that matter for this app
 * specifically:
 *
 *  - A clinical instrument should read as neutral. Serif display type at
 *    FontWeight.Light reads as *editorial*, and it also fails first under
 *    large-font accessibility settings because thin strokes at small optical
 *    sizes lose contrast.
 *  - Weight, not size, now carries hierarchy. Headings top out at 28sp instead
 *    of 40sp, so a heading plus its body text still fits on a 5" screen at
 *    200% font scale without the heading alone filling the viewport.
 *  - One family means text metrics stay consistent when styles are mixed in a
 *    single row (a score next to its label, say), which the old serif/sans mix
 *    made impossible to align cleanly.
 *
 * [FontFamily.SansSerif] resolves to the device's system UI font (Roboto on
 * most Android builds), so there are no font assets to ship and the app
 * inherits any system font the user has chosen.
 *
 * Every style sets [LineHeightStyle] with `Trim.None` so line height is applied
 * evenly above and below — without it, mixed-size rows sit visually off-centre.
 */

val DisplayFamily: FontFamily = FontFamily.SansSerif
val BodySans: FontFamily = FontFamily.SansSerif
val LabelSans: FontFamily = FontFamily.SansSerif

/** Retained so existing call sites keep resolving; both map to the sans family now. */
val EditorialSerif: FontFamily = DisplayFamily
val SecondarySerif: FontFamily = DisplayFamily

private val EvenLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private fun focusFlowStyle(
    family: FontFamily,
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    tracking: Double = 0.0
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = EvenLineHeight,
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

val FocusFlowTypography = Typography(
    // Reserved for a screen's single most important number (an overall score).
    displayLarge = focusFlowStyle(DisplayFamily, FontWeight.SemiBold, 44, 48, -1.0),
    displayMedium = focusFlowStyle(DisplayFamily, FontWeight.SemiBold, 32, 38, -0.5),
    displaySmall = focusFlowStyle(DisplayFamily, FontWeight.SemiBold, 26, 32, -0.25),

    // Screen titles.
    headlineLarge = focusFlowStyle(DisplayFamily, FontWeight.SemiBold, 28, 34, -0.25),
    headlineMedium = focusFlowStyle(DisplayFamily, FontWeight.SemiBold, 22, 28),
    headlineSmall = focusFlowStyle(DisplayFamily, FontWeight.SemiBold, 19, 25),

    // Section headers and card titles.
    titleLarge = focusFlowStyle(DisplayFamily, FontWeight.SemiBold, 18, 24),
    titleMedium = focusFlowStyle(BodySans, FontWeight.SemiBold, 16, 22, 0.1),
    titleSmall = focusFlowStyle(BodySans, FontWeight.Medium, 14, 20, 0.1),

    bodyLarge = focusFlowStyle(BodySans, FontWeight.Normal, 16, 24, 0.15),
    bodyMedium = focusFlowStyle(BodySans, FontWeight.Normal, 14, 21, 0.15),
    bodySmall = focusFlowStyle(BodySans, FontWeight.Normal, 12, 17, 0.2),

    labelLarge = focusFlowStyle(LabelSans, FontWeight.SemiBold, 15, 20, 0.1),
    labelMedium = focusFlowStyle(LabelSans, FontWeight.Medium, 13, 17, 0.3),
    labelSmall = focusFlowStyle(LabelSans, FontWeight.Medium, 11, 15, 0.4)
)
