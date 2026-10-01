package com.focusflow.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * FocusFlow's palette.
 *
 * The visual direction is *calm clinical*: a desaturated blue-teal primary on
 * near-neutral slate surfaces. An attention-assessment tool is asking the user
 * to hold their gaze on a stimulus for minutes at a time, so the chrome around
 * that stimulus has to recede. Saturated fills, wide gradients and glows all
 * compete with the thing being measured, so the accent is reserved for
 * interactive affordances and the single most important number on a screen.
 *
 * Contrast: every on* pairing below is at or above WCAG AA (4.5:1) against its
 * container in the mode it belongs to. Status colors come in a text-safe tone
 * and a soft container tone so status is never signalled by fill alone — see
 * [FocusFlowColors] in Theme.kt, which pairs each with an icon and a label.
 */

// ─────────────────────────────────────────────────────────
// Brand seed — a muted teal. Reads as measured/instrumented
// rather than medical-sterile or consumer-playful.
// ─────────────────────────────────────────────────────────
val Primary = Color(0xFF1F6F6B)          // light-mode primary, 5.4:1 on white
val PrimaryContainerLight = Color(0xFFCFE9E6)
val OnPrimaryContainerLight = Color(0xFF04322F)

val PrimaryDarkMode = Color(0xFF7FD3CB)  // dark-mode primary, 9.1:1 on DarkSurface
val PrimaryContainerDark = Color(0xFF12504C)
val OnPrimaryContainerDark = Color(0xFFCFE9E6)

/**
 * Secondary accent — a slate indigo. Used for the *non-primary* data series in
 * charts and for secondary chips, never for calls to action, so "teal means
 * you can act on it" stays true across the app.
 */
val Secondary = Color(0xFF4A5B7A)
val SecondaryContainerLight = Color(0xFFDDE3EF)
val Accent = Color(0xFF5B7FA6)           // chart/data accent
val AccentDarkMode = Color(0xFF9FC0DE)

// ─────────────────────────────────────────────────────────
// Light surfaces — a faint cool tint keeps large white areas
// from glaring during a sustained-attention session.
// ─────────────────────────────────────────────────────────
val LightBackground = Color(0xFFF7F9F9)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceContainer = Color(0xFFF1F4F5)
val LightSurfaceContainerHigh = Color(0xFFE8ECEE)
val LightSurfaceVariant = Color(0xFFE3E9EA)
val LightOutline = Color(0xFF6F7C7E)
val LightOutlineVariant = Color(0xFFCCD5D6)

val TextPrimaryLight = Color(0xFF16201F)   // 15.8:1 on LightSurface
val TextSecondaryLight = Color(0xFF4C5A59)  // 8.0:1 on LightSurface

// ─────────────────────────────────────────────────────────
// Dark surfaces — near-neutral, deliberately not pure black.
// OLED black against a bright video stimulus is a harsh edge
// to stare at, and it hides elevation entirely.
// ─────────────────────────────────────────────────────────
val DarkBackground = Color(0xFF0F1415)
val DarkSurface = Color(0xFF151B1C)
val DarkSurfaceContainer = Color(0xFF1C2324)
val DarkSurfaceContainerHigh = Color(0xFF262E2F)
val DarkSurfaceVariant = Color(0xFF3F4849)
val DarkOutline = Color(0xFF899597)
val DarkOutlineVariant = Color(0xFF3F4849)

val TextPrimaryDark = Color(0xFFE3E6E6)     // 13.6:1 on DarkSurface
val TextSecondaryDark = Color(0xFFB6C0C0)   // 8.7:1 on DarkSurface

// ─────────────────────────────────────────────────────────
// Status — each tone is contrast-checked for *text* use in its
// own mode, so a status label never relies on the fill alone.
// ─────────────────────────────────────────────────────────
val Success = Color(0xFF2E6B45)
val SuccessDarkMode = Color(0xFF86D6A3)
val SuccessContainerLight = Color(0xFFD4EBDD)
val SuccessContainerDark = Color(0xFF1B3D28)

val Warning = Color(0xFF8A5A11)
val WarningDarkMode = Color(0xFFF0C07A)
val WarningContainerLight = Color(0xFFF7E6CB)
val WarningContainerDark = Color(0xFF45320F)

val Error = Color(0xFFA33341)
val ErrorDarkMode = Color(0xFFF2A3AC)
val ErrorContainerLight = Color(0xFFF8DDE0)
val ErrorContainerDark = Color(0xFF4F1A21)

/**
 * Ambient background stops. Deliberately near-flat — a 2-3% shift across the
 * screen, enough to give a page depth without becoming a gradient the eye
 * tracks. See [com.focusflow.ui.components.BlurBackground].
 */
val GradientLightStart = Color(0xFFF9FBFB)
val GradientLightEnd = Color(0xFFEDF2F2)
val GradientDarkStart = Color(0xFF141A1B)
val GradientDarkEnd = Color(0xFF0D1213)

/** Hairline separators used by cards and dividers. */
val BorderLight = Color(0xFFDCE3E4)
val BorderDark = Color(0xFF2C3536)

/** Scrim behind dialogs and over camera surfaces. */
val ScrimLight = Color(0x66101414)
val ScrimDark = Color(0x99000000)
