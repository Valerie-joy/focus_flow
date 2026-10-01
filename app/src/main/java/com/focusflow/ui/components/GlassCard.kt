package com.focusflow.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.focusflow.ui.theme.Elevation
import com.focusflow.ui.theme.FocusFlowRadius
import com.focusflow.ui.theme.LocalFocusFlowColors
import com.focusflow.ui.theme.Sizing
import com.focusflow.ui.theme.Spacing

/**
 * The standard content surface across FocusFlow.
 *
 * This was previously a translucent, `Modifier.blur`-backed "glass" card with
 * an 18dp shadow. That has been replaced with an opaque Material 3 surface
 * carrying a hairline border and 1dp of elevation, because the glass treatment
 * had three concrete costs:
 *
 *  - **Legibility.** Body text sat on whatever happened to be behind the card,
 *    so its effective contrast changed as the page scrolled. Contrast can't be
 *    guaranteed against an unknown backdrop, and this app shows numbers people
 *    are meant to read carefully.
 *  - **Cost.** `Modifier.blur` forces the node into an offscreen render pass on
 *    every frame it's composed in. Several cards per screen meant several such
 *    passes competing with the camera pipeline during an assessment.
 *  - **Structure.** A 1dp border states the card's edge exactly; a large soft
 *    shadow only implies it, and a screen full of them reads as haze.
 *
 * The name and parameter list are unchanged so existing call sites keep
 * working. `blurBehind` is retained and ignored — see its docs.
 *
 * @param onClick makes the whole card a single tappable target. Prefer this
 * over wrapping the card in `Modifier.clickable`, which produces no ripple,
 * no button role for TalkBack and no minimum touch target.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = FocusFlowRadius.md,
    padding: Dp = Spacing.md,
    @Suppress("UNUSED_PARAMETER")
    blurBehind: Boolean = true,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    content: @Composable () -> Unit
) {
    val colors = LocalFocusFlowColors.current
    val shape = RoundedCornerShape(cornerRadius)
    val border = BorderStroke(Sizing.hairline, colors.glassBorder)

    val description = contentDescription
    val semanticsModifier = if (description != null) {
        Modifier.semantics { this.contentDescription = description }
    } else {
        Modifier
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.then(semanticsModifier),
            shape = shape,
            border = border,
            colors = CardDefaults.cardColors(
                containerColor = colors.glassSurface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = Elevation.card,
                pressedElevation = Elevation.raised
            )
        ) {
            Box(modifier = Modifier.padding(padding)) { content() }
        }
    } else {
        Surface(
            modifier = modifier.then(semanticsModifier),
            shape = shape,
            border = border,
            color = colors.glassSurface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = Elevation.none,
            shadowElevation = Elevation.card
        ) {
            Box(modifier = Modifier.padding(padding)) { content() }
        }
    }
}
