package com.focusflow.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radii.
 *
 * Reduced from the previous scale (12/16/24/32/pill). Very large radii on
 * large surfaces read as decorative rather than structural, and a fully
 * rounded "pill" primary button is the single strongest signal that a UI is
 * styled rather than engineered. Buttons now use [md]; [pill] is kept for the
 * things it genuinely suits — chips, badges and progress tracks.
 */
object FocusFlowRadius {
    /** Badges, inline tags. */
    val xs = 6.dp
    /** Text fields, list rows, small controls. */
    val sm = 10.dp
    /** Buttons and standard cards — the default. */
    val md = 14.dp
    /** Sheets, dialogs, hero/media surfaces. */
    val lg = 20.dp
    /** Chips, pills and progress tracks only. */
    val pill = 100.dp
}

val FocusFlowShapes = Shapes(
    extraSmall = RoundedCornerShape(FocusFlowRadius.xs),
    small = RoundedCornerShape(FocusFlowRadius.sm),
    medium = RoundedCornerShape(FocusFlowRadius.md),
    large = RoundedCornerShape(FocusFlowRadius.lg),
    extraLarge = RoundedCornerShape(FocusFlowRadius.lg)
)
