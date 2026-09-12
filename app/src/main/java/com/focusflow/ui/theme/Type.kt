package com.focusflow.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * ADDING REAL EDITORIAL FONTS (optional):
 * Right now these fall back to FontFamily.Serif / FontFamily.SansSerif so
 * the project compiles with zero extra assets. To get the real editorial
 * look from the design spec:
 *
 *   1. Download (all free/open-licensed, from Google Fonts):
 *        Instrument Serif        -> res/font/instrument_serif_regular.ttf
 *        Cormorant Garamond (Medium) -> res/font/cormorant_garamond_medium.ttf
 *        Inter (Regular + Medium)    -> res/font/inter_regular.ttf, inter_medium.ttf
 *        Manrope (SemiBold)          -> res/font/manrope_semibold.ttf
 *   2. Create the res/font/ directory and drop those files in.
 *   3. Replace the four FontFamily vals below with, e.g.:
 *        val EditorialSerif = FontFamily(Font(R.font.instrument_serif_regular, FontWeight.Normal))
 *      (add `import androidx.compose.ui.text.font.Font` and `import com.focusflow.R`)
 *
 * Deliberately NOT referencing R.font.* here until those files actually
 * exist — R.font wouldn't resolve at all without them, which is exactly
 * the build error this file used to cause.
 */

val EditorialSerif: FontFamily = FontFamily.Serif
val SecondarySerif: FontFamily = FontFamily.Serif
val BodySans: FontFamily = FontFamily.SansSerif
val LabelSans: FontFamily = FontFamily.SansSerif

val FocusFlowTypography = Typography(
    // Large editorial titles — thin weight, generous tracking, luxury feel
    displayLarge = TextStyle(
        fontFamily = EditorialSerif,
        fontWeight = FontWeight.Light,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = 0.sp
    ),
    displayMedium = TextStyle(
        fontFamily = EditorialSerif,
        fontWeight = FontWeight.Light,
        fontSize = 32.sp,
        lineHeight = 38.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = EditorialSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SecondarySerif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = BodySans,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = BodySans,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = BodySans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.01.em
    ),
    bodyMedium = TextStyle(
        fontFamily = BodySans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = BodySans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = LabelSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = 0.02.em
    ),
    labelMedium = TextStyle(
        fontFamily = LabelSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.02.em
    ),
    labelSmall = TextStyle(
        fontFamily = LabelSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.03.em
    )
)
