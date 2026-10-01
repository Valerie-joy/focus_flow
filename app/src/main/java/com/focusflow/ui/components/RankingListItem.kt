package com.focusflow.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.focusflow.domain.usecases.InterpretAttentionScore
import com.focusflow.ui.theme.FocusFlowRadius
import com.focusflow.ui.theme.LocalFocusFlowColors
import com.focusflow.ui.theme.MotionDurations
import com.focusflow.ui.theme.Spacing

/**
 * One row in a ranked category list: rank, label, a proportional bar, and the
 * percentage.
 *
 * Two changes beyond styling. The bar was filled with a primary-to-accent
 * gradient, which made every row the same color regardless of its value — bar
 * length carried the whole signal and color carried none. It now takes the
 * tone of its band, so a weak category is distinguishable from a strong one by
 * color *and* by length rather than by length alone.
 *
 * Second, the row is now a single semantics node. Previously TalkBack read
 * "Music", then "94 percent", as two unrelated nodes with a decorative bar
 * between them; it now reads as one sentence including the rank and band.
 */
@Composable
fun RankingListItem(
    rank: Int,
    label: String,
    percentage: Int, // 0..100
    modifier: Modifier = Modifier
) {
    val colors = LocalFocusFlowColors.current
    val clamped = percentage.coerceIn(0, 100)
    val band = InterpretAttentionScore.bandFor(clamped)
    val barColor = when (band) {
        InterpretAttentionScore.Band.VERY_STRONG,
        InterpretAttentionScore.Band.STRONG -> colors.success
        InterpretAttentionScore.Band.MIXED -> MaterialTheme.colorScheme.primary
        InterpretAttentionScore.Band.VARIABLE -> colors.warning
    }

    val animatedFraction by animateFloatAsState(
        targetValue = clamped / 100f,
        animationSpec = tween(MotionDurations.PROGRESS),
        label = "rankingBar"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
            .clearAndSetSemantics {
                contentDescription = "Rank $rank, $label, $clamped percent, ${band.label}"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = "$clamped%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = Spacing.xs)
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xxs))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BAR_HEIGHT)
                    .clip(RoundedCornerShape(FocusFlowRadius.pill))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedFraction)
                        .height(BAR_HEIGHT)
                        .clip(RoundedCornerShape(FocusFlowRadius.pill))
                        .background(barColor)
                )
            }
        }
    }
}

private val BAR_HEIGHT = 6.dp
