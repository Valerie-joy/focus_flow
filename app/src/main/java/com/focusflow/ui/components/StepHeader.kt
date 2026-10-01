package com.focusflow.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.focusflow.R
import com.focusflow.ui.navigation.OnboardingStep
import com.focusflow.ui.theme.MotionDurations
import com.focusflow.ui.theme.Spacing

/**
 * "Step 2 of 5" plus a progress bar, for the first-run flow.
 *
 * Every number comes from [OnboardingStep], so the label cannot disagree with
 * the real sequence — which is what went wrong with the hardcoded
 * `"Step 2 of 9"` this replaces.
 *
 * The whole header is one semantics node: read as three separate nodes, TalkBack
 * announced "Step 2 of 5", then "Optional", then a bare progress percentage,
 * which is the same fact three times in a row before the screen's actual
 * question.
 */
@Composable
fun StepHeader(
    step: OnboardingStep,
    modifier: Modifier = Modifier
) {
    val total = OnboardingStep.total
    val targetFraction = step.number.toFloat() / total
    val fraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(MotionDurations.PROGRESS),
        label = "onboardingProgress"
    )

    val stepLabel = stringResource(R.string.onboarding_step_label, step.number, total)
    val optionalLabel = stringResource(R.string.onboarding_step_optional)
    val description = if (step.isOptional) {
        stringResource(
            R.string.onboarding_step_a11y_optional,
            step.number,
            total,
            step.title
        )
    } else {
        stringResource(R.string.onboarding_step_a11y, step.number, total, step.title)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stepLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Optional steps are labelled positively. A user who cannot tell
            // whether a step is required completes it anyway.
            if (step.isOptional) {
                StatusPill(text = optionalLabel, tone = StatusTone.NEUTRAL)
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            drawStopIndicator = {}
        )
    }
}
