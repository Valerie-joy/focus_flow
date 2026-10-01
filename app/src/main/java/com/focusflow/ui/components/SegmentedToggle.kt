package com.focusflow.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focusflow.ui.theme.FocusFlowRadius
import com.focusflow.ui.theme.MotionDurations
import com.focusflow.ui.theme.Sizing

/**
 * Segmented control for a small set of mutually exclusive options (2-4).
 *
 * Rebuilt on `Modifier.selectable` inside a `selectableGroup`. The previous
 * version used a bare `clickable` with the ripple suppressed, so TalkBack
 * announced each segment as an anonymous button and never said which one was
 * selected — the selection was conveyed purely by fill color, which is the one
 * channel a color-vision deficiency or a display filter can remove entirely.
 * `Role.RadioButton` plus the group puts the selected state into the
 * announcement ("selected, Strongest, 1 of 2").
 *
 * Segments are at least a minimum touch target tall and their labels wrap
 * rather than clip, so a long or translated option still fits at large font
 * scales — the previous fixed 52dp height truncated them.
 */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val outerShape = RoundedCornerShape(FocusFlowRadius.md)
    val innerShape = RoundedCornerShape(FocusFlowRadius.sm)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(outerShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp)
            .selectableGroup()
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val container by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                animationSpec = tween(MotionDurations.QUICK),
                label = "segmentContainer"
            )
            val content = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = Sizing.minTouchTarget)
                    .clip(innerShape)
                    .background(container)
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(index) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                )
            }
        }
    }
}
