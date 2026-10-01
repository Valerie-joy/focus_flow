package com.focusflow.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The single highest-emphasis action on a screen ("Start assessment").
 *
 * This used to draw a primary→accent gradient with a 16dp colored glow. Both
 * are gone: a gradient fill under white label text has no single measurable
 * contrast ratio (it changes across the label), and a colored glow is
 * decoration that competes with the content for attention.
 *
 * What distinguishes the hero action now is *placement and solitude* — it is
 * the only filled button in its region — which is a hierarchy the user can
 * still read at a glance, in dark mode, and with a color-vision deficiency.
 *
 * Kept as a distinct composable rather than folded into [PrimaryButton] so
 * call sites still record which action is the hero one, and so the two can
 * diverge again later without touching every screen. [gradientColors] is
 * retained for source compatibility; only its first entry is used, as the
 * solid container color.
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    gradientColors: List<Color> = emptyList()
) {
    PrimaryButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        containerColor = gradientColors.firstOrNull() ?: MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    )
}
