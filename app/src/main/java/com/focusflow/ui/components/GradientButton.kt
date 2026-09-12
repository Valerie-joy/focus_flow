package com.focusflow.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.focusflow.ui.theme.Accent
import com.focusflow.ui.theme.FocusFlowRadius
import com.focusflow.ui.theme.Primary

/**
 * Hero call-to-action button with a diagonal primary→accent gradient and a
 * soft tinted glow shadow. Reserved for the single most important action on
 * a screen (e.g. "Start assessment", "View report") — using PrimaryButton
 * for everything else keeps this one meaningful.
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    gradientColors: List<Color> = listOf(Primary, Accent)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "gradientButtonScale"
    )
    val shape = RoundedCornerShape(FocusFlowRadius.pill)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale)
            .shadow(
                elevation = if (enabled) 16.dp else 0.dp,
                shape = shape,
                ambientColor = gradientColors.first().copy(alpha = 0.5f),
                spotColor = gradientColors.first().copy(alpha = 0.5f)
            )
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    if (enabled) gradientColors else gradientColors.map { it.copy(alpha = 0.4f) }
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(PaddingValues(horizontal = 24.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White
        )
    }
}
