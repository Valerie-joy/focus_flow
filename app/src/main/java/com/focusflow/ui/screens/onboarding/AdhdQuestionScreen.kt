package com.focusflow.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.focusflow.R
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.SelectableOptionCard
import com.focusflow.ui.components.StepHeader
import com.focusflow.ui.navigation.OnboardingStep
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.ui.theme.Sizing
import com.focusflow.ui.theme.Spacing

/**
 * The first onboarding step: which background question set to ask.
 *
 * Changes of substance:
 *
 *  - **The progress label was fiction.** `stepLabel: String = "Step 2 of 9"` was
 *    a default the navigation layer never overrode. There are five onboarding
 *    steps and this is the first. Now derived from [OnboardingStep].
 *  - **The wording implied a gate.** "You can upload a medical document, or take
 *    a short self-reflection questionnaire instead" reads as though one path is
 *    the real one and the other is a fallback, and it did not say that both
 *    arrive at the same place. A user with a diagnosis but no document to hand
 *    had no idea which to pick.
 *  - **"No, I don't have a diagnosis" forced a disclosure.** Some users simply
 *    don't want to answer. The option now covers both, which costs the app
 *    nothing — the branch is only choosing which questions come next.
 *  - **"Your data is secure and confidential" was an unbacked claim.** Replaced
 *    with what is actually true of each branch: the questionnaire is stored
 *    locally, and only an uploaded document leaves the device.
 */
@Composable
fun AdhdQuestionScreen(
    onHasDiagnosis: () -> Unit,
    onNoDiagnosis: () -> Unit,
    modifier: Modifier = Modifier
) {
    BlurBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = Sizing.maxContentWidth)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.gutter)
            ) {
                Spacer(modifier = Modifier.height(Spacing.lg))
                StepHeader(step = OnboardingStep.BACKGROUND)

                Spacer(modifier = Modifier.height(Spacing.xl))
                Text(
                    text = stringResource(R.string.background_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.background_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(Spacing.xl))

                SelectableOptionCard(
                    title = stringResource(R.string.background_yes_title),
                    subtitle = stringResource(R.string.background_yes_subtitle),
                    icon = Icons.Filled.UploadFile,
                    selected = false,
                    onClick = onHasDiagnosis
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                SelectableOptionCard(
                    title = stringResource(R.string.background_no_title),
                    subtitle = stringResource(R.string.background_no_subtitle),
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    selected = false,
                    onClick = onNoDiagnosis
                )

                Spacer(modifier = Modifier.height(Spacing.xl))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        // Decorative: the sentence beside it carries the meaning.
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Sizing.iconSm)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xxs))
                    Text(
                        text = stringResource(R.string.background_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }
    }
}

@Preview(showBackground = true, name = "Background question")
@Composable
private fun AdhdQuestionScreenPreview() {
    FocusFlowTheme {
        AdhdQuestionScreen(onHasDiagnosis = {}, onNoDiagnosis = {})
    }
}

@Preview(showBackground = true, name = "Background question — dark, large font", fontScale = 1.5f)
@Composable
private fun AdhdQuestionScreenLargeFontPreview() {
    FocusFlowTheme(darkTheme = true) {
        AdhdQuestionScreen(onHasDiagnosis = {}, onNoDiagnosis = {})
    }
}
