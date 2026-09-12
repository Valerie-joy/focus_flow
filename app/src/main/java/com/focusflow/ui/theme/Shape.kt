package com.focusflow.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Reusable radius tokens — reference these directly in components
object FocusFlowRadius {
    val xs = 12.dp
    val sm = 16.dp
    val md = 24.dp
    val lg = 32.dp
    val pill = 100.dp // effectively fully rounded for buttons/nav
}

val FocusFlowShapes = Shapes(
    extraSmall = RoundedCornerShape(FocusFlowRadius.xs),
    small = RoundedCornerShape(FocusFlowRadius.sm),
    medium = RoundedCornerShape(FocusFlowRadius.md),
    large = RoundedCornerShape(FocusFlowRadius.lg),
    extraLarge = RoundedCornerShape(FocusFlowRadius.pill)
)
