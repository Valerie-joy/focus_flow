package com.focusflow.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.focusflow.ui.theme.LocalFocusFlowColors
import com.focusflow.ui.theme.Primary

/**
 * 1–5 rating selector used for post-video questions ("How interesting was
 * this video?" / "How easy was it to stay focused?"). Discrete pill dots
 * rather than a continuous Material slider, per the reference design.
 */
@Composable
fun CustomSlider(
    value: Int, // 1..5
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 1..5,
    leftLabel: String = "",
    rightLabel: String = ""
) {
    val colors = LocalFocusFlowColors.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            range.forEach { i ->
                RatingDot(
                    number = i,
                    selected = i == value,
                    onClick = { onValueChange(i) }
                )
            }
        }
        if (leftLabel.isNotEmpty() || rightLabel.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(leftLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(rightLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RatingDot(number: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalFocusFlowColors.current
    val bg by animateColorAsState(
        targetValue = if (selected) Primary else colors.glassSurface,
        label = "ratingDotColor"
    )
    val textColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = number.toString(), style = MaterialTheme.typography.titleMedium, color = textColor)
    }
}
