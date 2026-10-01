package com.focusflow.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.focusflow.camera.eyetracking.itracker.CalibrationQuality
import com.focusflow.camera.eyetracking.itracker.GazeCalibration
import com.focusflow.domain.models.FrequencyAnswer
import com.focusflow.domain.usecases.InterpretAttentionScore
import com.focusflow.ui.components.EmptyState
import com.focusflow.ui.components.GlassCard
import com.focusflow.ui.components.MessageBanner
import com.focusflow.ui.components.MetricRow
import com.focusflow.ui.components.PremiumTextField
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.components.SecondaryButton
import com.focusflow.ui.components.SectionHeader
import com.focusflow.ui.components.SegmentedToggle
import com.focusflow.ui.components.StatusPill
import com.focusflow.ui.components.StatusTone
import com.focusflow.ui.components.StepHeader
import com.focusflow.ui.navigation.OnboardingStep
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.ui.theme.Spacing

/**
 * Previews for states that are otherwise hard to reach.
 *
 * Several of the app's most important states need a real device to produce: a
 * permanently-denied camera permission, a calibration that failed to converge,
 * a gaze gate mid-stabilisation. Rendering their *presentation* here means the
 * copy, the tone pairing and the large-font behaviour can be reviewed in the
 * IDE without an emulator — which is the only review currently possible.
 *
 * These render components with fabricated state, not whole screens, precisely
 * because the screens own camera and lifecycle work that will not run in a
 * preview. Nothing in production was changed to make these easier to write.
 */

@Preview(name = "Camera permission — denial states", showBackground = true, widthDp = 360)
@Composable
private fun PermissionStatesPreview() {
    FocusFlowTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                SectionHeader(title = "Declined once")
                MessageBanner(
                    message = "Without the camera, FocusFlow can't measure attention. " +
                        "You can still browse the app, but assessments won't run.",
                    tone = StatusTone.WARNING
                )
                SectionHeader(title = "Permanently denied")
                MessageBanner(
                    message = "Camera access is turned off for FocusFlow. Turn it on in " +
                        "Settings → Permissions → Camera, then come back — this screen " +
                        "will update on its own.",
                    tone = StatusTone.WARNING
                )
                PrimaryButton(text = "Open Settings", onClick = {})
                SecondaryButton(text = "Not now", onClick = {})
            }
        }
    }
}

@Preview(name = "Assessment readiness — gate states", showBackground = true, widthDp = 360)
@Composable
private fun ReadinessStatesPreview() {
    FocusFlowTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                StatusPill(text = "Looking for your face…", tone = StatusTone.NEUTRAL)
                StatusPill(
                    text = "Move a little closer so both eyes are visible.",
                    tone = StatusTone.WARNING
                )
                StatusPill(text = "Face the screen straight on.", tone = StatusTone.WARNING)
                StatusPill(text = "Hold still…", tone = StatusTone.NEUTRAL)
                StatusPill(
                    text = "Tracking steadily — you're good to go.",
                    tone = StatusTone.SUCCESS
                )
                PrimaryButton(text = "Start the clip", onClick = {}, enabled = false)
                PrimaryButton(text = "Start the clip", onClick = {})
            }
        }
    }
}

@Preview(name = "Calibration — success and failure", showBackground = true, widthDp = 360)
@Composable
private fun CalibrationOutcomePreview() {
    FocusFlowTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Real numbers from CalibrationQuality, so the wording under
                // review is the wording that ships.
                SectionHeader(title = "Good fit")
                StatusPill(
                    text = CalibrationQuality.Band.GOOD.label,
                    tone = StatusTone.SUCCESS
                )
                Text(
                    text = CalibrationQuality.describe(residualRms = 0.05f, pointCount = 5),
                    style = MaterialTheme.typography.bodyMedium
                )

                SectionHeader(title = "Barely usable")
                StatusPill(
                    text = CalibrationQuality.Band.USABLE.label,
                    tone = StatusTone.WARNING
                )
                Text(
                    text = CalibrationQuality.describe(
                        residualRms = GazeCalibration.MAX_ACCEPTABLE_RESIDUAL - 0.01f,
                        pointCount = 5
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )

                SectionHeader(title = "Failed — no readings")
                MessageBanner(
                    message = CalibrationQuality.failureAdvice(collectedPoints = 0),
                    tone = StatusTone.WARNING
                )
                SectionHeader(title = "Failed — too few dots")
                MessageBanner(
                    message = CalibrationQuality.failureAdvice(collectedPoints = 2),
                    tone = StatusTone.WARNING
                )
            }
        }
    }
}

@Preview(name = "Metric rows — plain-language readout", showBackground = true, widthDp = 360)
@Composable
private fun MetricRowsPreview() {
    FocusFlowTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            GlassCard(modifier = Modifier.padding(Spacing.md)) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MetricRow(
                        label = "Time on screen",
                        value = "94%",
                        explanation = InterpretAttentionScore.explainScreenAttention(94f),
                        tone = StatusTone.SUCCESS
                    )
                    MetricRow(
                        label = "Looks away",
                        value = "3",
                        explanation = InterpretAttentionScore.explainGazeShifts(3)
                    )
                    MetricRow(
                        label = "First look away",
                        value = "12s",
                        explanation = InterpretAttentionScore.explainFirstDistraction(12_000L)
                    )
                    MetricRow(
                        label = "Blinks",
                        value = "14",
                        explanation = InterpretAttentionScore.explainBlinks(14)
                    )
                }
            }
        }
    }
}

@Preview(name = "Empty states", showBackground = true, widthDp = 360)
@Composable
private fun EmptyStatesPreview() {
    FocusFlowTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            EmptyState(
                icon = Icons.Filled.Assessment,
                title = "No assessments yet",
                description = "Once you finish an assessment, your attention profile, " +
                    "category breakdown and progress over time appear here.",
                actionLabel = "Start an assessment",
                onAction = {}
            )
        }
    }
}

@Preview(name = "Onboarding step headers", showBackground = true, widthDp = 360)
@Composable
private fun StepHeaderPreview() {
    FocusFlowTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                // Every step, so a wrong total or a missing "Optional" tag is
                // obvious at a glance.
                OnboardingStep.entries.forEach { step ->
                    Column {
                        Text(text = step.title, style = MaterialTheme.typography.titleSmall)
                        StepHeader(step = step)
                    }
                }
            }
        }
    }
}

@Preview(
    name = "Form fields — narrow, large font",
    showBackground = true,
    widthDp = 320,
    fontScale = 1.5f
)
@Composable
private fun FormFieldsStressPreview() {
    FocusFlowTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // The combination most likely to clip: a narrow screen, 150%
                // font, a required label and a two-line error.
                PremiumTextField(
                    value = "not-an-email",
                    onValueChange = {},
                    label = "Email",
                    isRequired = true,
                    errorMessage = "That doesn't look like an email address."
                )
                SegmentedToggle(
                    options = listOf("Male", "Female", "Prefer not to say"),
                    selectedIndex = 2,
                    onSelect = {}
                )
                PrimaryButton(text = "Save and continue", onClick = {})
                PrimaryButton(text = "Save and continue", onClick = {}, loading = true)
            }
        }
    }
}

@Preview(name = "Self-report answer scale", showBackground = true, widthDp = 360)
@Composable
private fun FrequencyScalePreview() {
    FocusFlowTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                // Confirms the five-point scale renders in order with its
                // scoring intact.
                FrequencyAnswer.entries.forEach { answer ->
                    Text(
                        text = "${answer.label} (score ${answer.score})",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
