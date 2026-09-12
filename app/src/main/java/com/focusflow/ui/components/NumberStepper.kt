package com.focusflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.focusflow.ui.theme.FocusFlowRadius
import com.focusflow.ui.theme.LocalFocusFlowColors

/**
 * Glass pill stepper for a bounded integer (e.g. age 5-30). Prefer this
 * over a continuous slider when the range is small enough that discrete
 * +/- taps are more precise and accessible than dragging.
 */
@Composable
fun NumberStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val colors = LocalFocusFlowColors.current
    val shape = RoundedCornerShape(FocusFlowRadius.pill)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(colors.glassSurface)
            .border(width = 1.dp, color = colors.glassBorder, shape = shape)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepperButton(
            symbol = "–",
            enabled = value > range.first,
            onClick = { onValueChange((value - 1).coerceIn(range)) }
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                text = if (label != null) "$value $label" else value.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        StepperButton(
            symbol = "+",
            enabled = value < range.last,
            onClick = { onValueChange((value + 1).coerceIn(range)) }
        )
    }
}

@Composable
private fun StepperButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = symbol, style = MaterialTheme.typography.titleLarge, color = androidx.compose.ui.graphics.Color.White)
    }
}
