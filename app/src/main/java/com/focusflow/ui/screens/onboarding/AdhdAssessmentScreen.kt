package com.focusflow.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.focusflow.domain.models.AdhdSelfReportQuestion
import com.focusflow.domain.models.FrequencyAnswer
import com.focusflow.domain.models.defaultAdhdSelfReportQuestions
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.GlassCard
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.ui.theme.LocalFocusFlowColors

/**
 * One-question-per-screen self-reflection questionnaire. Opens on a plain
 * disclaimer step so it's never mistaken for a diagnosis — this framing is
 * intentional (see the note on ADHD-assessment framing in the project
 * README) and shouldn't be removed without a deliberate product decision.
 *
 * Answers are surfaced via [onComplete] as a Map<questionId, FrequencyAnswer>;
 * persistence (Room, per the spec's "save answers locally") belongs in the
 * ViewModel/repository layer, not here.
 */
@Composable
fun AdhdAssessmentScreen(
    questions: List<AdhdSelfReportQuestion> = defaultAdhdSelfReportQuestions,
    onComplete: (Map<String, FrequencyAnswer>) -> Unit,
    onExit: () -> Unit
) {
    var showDisclaimer by remember { mutableStateOf(true) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var answers by remember { mutableStateOf(mapOf<String, FrequencyAnswer>()) }

    if (showDisclaimer) {
        DisclaimerStep(onContinue = { showDisclaimer = false }, onExit = onExit)
        return
    }

    val question = questions[currentIndex]
    val progress by animateFloatAsState(
        targetValue = (currentIndex + 1f) / questions.size,
        label = "assessmentProgress"
    )

    BlurBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            ProgressBar(progress = progress)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Question ${currentIndex + 1} of ${questions.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedContent(
                targetState = question,
                transitionSpec = {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it } + fadeOut())
                },
                label = "questionTransition"
            ) { q ->
                Text(
                    text = q.prompt,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            FrequencyAnswer.entries.forEach { option ->
                AnswerRow(
                    label = option.label,
                    selected = answers[question.id] == option,
                    onClick = {
                        answers = answers + (question.id to option)
                        if (currentIndex < questions.lastIndex) {
                            currentIndex += 1
                        } else {
                            onComplete(answers)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DisclaimerStep(onContinue: () -> Unit, onExit: () -> Unit) {
    BlurBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Before you begin",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This is a short self-reflection questionnaire, not a medical assessment. " +
                            "It won't diagnose or rule out ADHD. If any of the questions resonate with you, " +
                            "it may be worth bringing them up with a doctor or licensed clinician.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            PrimaryButton(text = "I understand, continue", onClick = onContinue)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Go back",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onExit
                    )
                    .padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    val colors = LocalFocusFlowColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(colors.glassBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun AnswerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalFocusFlowColors.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else colors.glassSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AdhdAssessmentScreenPreview() {
    FocusFlowTheme {
        AdhdAssessmentScreen(onComplete = {}, onExit = {})
    }
}
