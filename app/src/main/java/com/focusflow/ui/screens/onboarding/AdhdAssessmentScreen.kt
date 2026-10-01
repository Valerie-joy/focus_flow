package com.focusflow.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.focusflow.R
import com.focusflow.domain.models.AdhdSelfReportQuestion
import com.focusflow.domain.models.FrequencyAnswer
import com.focusflow.domain.models.defaultAdhdSelfReportQuestions
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.GlassCard
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.components.SecondaryButton
import com.focusflow.ui.components.StepHeader
import com.focusflow.ui.navigation.OnboardingStep
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.ui.theme.MotionDurations
import com.focusflow.ui.theme.Sizing
import com.focusflow.ui.theme.Spacing

/**
 * The self-reflection questionnaire, one question per screen.
 *
 * **Question wording and scoring are untouched.** The items in
 * [defaultAdhdSelfReportQuestions] and the 0-4 [FrequencyAnswer] values are
 * carried through exactly as they were; only the surrounding presentation
 * changed. The instrument is documented as not validated, and rewording items
 * for UX would make that worse, not better.
 *
 * Changes of substance:
 *
 *  - **A mis-tap was permanent.** Choosing an answer immediately advanced, with
 *    no way back. On the last question it submitted the whole questionnaire on
 *    that same tap. There is now an explicit Next/Previous pair, selection is
 *    visible before committing, and the final question says "Finish".
 *  - **Answers were silently discarded on exit.** Pressing Back left the flow
 *    with no warning; Back now asks, and says the answers won't be kept.
 *  - **Answers were lost on rotation.** Held in plain `remember`.
 *  - **Options were anonymous taps.** `clickable(indication = null)` with white
 *    text on a fill — no radio role, no selected state for TalkBack, and
 *    selection signalled by colour alone. Now a real `selectableGroup` with a
 *    check icon, so the choice survives a colour filter.
 *  - **The disclaimer didn't say the two assessments stay separate**, which is
 *    the single most important thing for a user not to misread.
 */
@Composable
fun AdhdAssessmentScreen(
    onComplete: (Map<String, FrequencyAnswer>) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    questions: List<AdhdSelfReportQuestion> = defaultAdhdSelfReportQuestions
) {
    var showDisclaimer by rememberSaveable { mutableStateOf(true) }
    var currentIndex by rememberSaveable { mutableIntStateOf(0) }
    // Kept as a list of answer names so it survives process death; a Map of
    // enums is not natively saveable.
    var answerNames by rememberSaveable { mutableStateOf(listOf<String>()) }
    var showExitConfirm by rememberSaveable { mutableStateOf(false) }

    val answers: Map<String, FrequencyAnswer> = remember(answerNames) {
        answerNames.mapNotNull { encoded ->
            val id = encoded.substringBefore('=')
            val answer = FrequencyAnswer.entries.firstOrNull {
                it.name == encoded.substringAfter('=')
            }
            answer?.let { id to it }
        }.toMap()
    }

    fun setAnswer(questionId: String, answer: FrequencyAnswer) {
        answerNames = answerNames.filterNot { it.startsWith("$questionId=") } +
            "$questionId=${answer.name}"
    }

    if (showDisclaimer) {
        DisclaimerStep(
            onContinue = { showDisclaimer = false },
            onExit = onExit,
            modifier = modifier
        )
        return
    }

    val question = questions[currentIndex]
    val selected = answers[question.id]
    val isLast = currentIndex == questions.lastIndex
    val progress by animateFloatAsState(
        targetValue = (currentIndex + 1f) / questions.size,
        animationSpec = tween(MotionDurations.PROGRESS),
        label = "questionProgress"
    )

    // Back moves through the questionnaire rather than out of it, and only
    // leaves — with a warning — from the first question.
    BackHandler {
        if (currentIndex > 0) currentIndex -= 1 else showExitConfirm = true
    }

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

                val counter = stringResource(
                    R.string.selfreport_question_counter,
                    currentIndex + 1,
                    questions.size
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics { contentDescription = counter }
                ) {
                    Text(
                        text = counter,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        drawStopIndicator = {}
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xl))

                AnimatedContent(
                    targetState = question,
                    transitionSpec = {
                        (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 4 } + fadeOut())
                    },
                    label = "questionTransition"
                ) { q ->
                    Text(
                        // Verbatim from the question bank — see the class docs.
                        text = q.prompt,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.selfreport_answer_prompt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.sm))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    FrequencyAnswer.entries.forEach { option ->
                        AnswerRow(
                            label = option.label,
                            selected = selected == option,
                            onClick = { setAnswer(question.id, option) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xl))

                // Next is disabled until an answer exists, rather than an answer
                // silently advancing the screen. The user sees their choice
                // land, then commits it.
                PrimaryButton(
                    text = stringResource(
                        if (isLast) R.string.selfreport_finish else R.string.selfreport_next
                    ),
                    onClick = {
                        if (isLast) onComplete(answers) else currentIndex += 1
                    },
                    enabled = selected != null
                )
                if (currentIndex > 0) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    SecondaryButton(
                        text = stringResource(R.string.selfreport_previous),
                        onClick = { currentIndex -= 1 }
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(stringResource(R.string.selfreport_exit_title)) },
            text = { Text(stringResource(R.string.selfreport_exit_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    onExit()
                }) { Text(stringResource(R.string.selfreport_exit_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text(stringResource(R.string.selfreport_exit_dismiss))
                }
            }
        )
    }
}

/**
 * The framing step.
 *
 * Kept as a mandatory stop rather than a dismissible banner: this is the one
 * place the app can state, before any answer is given, that these questions do
 * not diagnose anything and are not merged with the gaze assessment.
 */
@Composable
private fun DisclaimerStep(
    onContinue: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onExit() }

    BlurBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = Sizing.maxContentWidth)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.gutter),
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(Spacing.lg))
                StepHeader(step = OnboardingStep.BACKGROUND_DETAIL)

                Spacer(modifier = Modifier.height(Spacing.xl))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Sizing.iconMd)
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = stringResource(R.string.selfreport_disclaimer_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = stringResource(R.string.selfreport_disclaimer_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = stringResource(R.string.selfreport_disclaimer_separate),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.lg))
                PrimaryButton(
                    text = stringResource(R.string.selfreport_disclaimer_continue),
                    onClick = onContinue
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                SecondaryButton(text = stringResource(R.string.action_back), onClick = onExit)
                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }
    }
}

/**
 * One frequency option.
 *
 * Selection is carried by a check icon *and* a container colour, so it survives
 * a colour-vision deficiency or a display filter — the previous version signalled
 * it with fill colour alone.
 */
@Composable
private fun AnswerRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Sizing.minTouchTarget)
            .clip(MaterialTheme.shapes.small)
            .background(container)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (selected) {
                Icons.Filled.CheckCircle
            } else {
                Icons.Filled.RadioButtonUnchecked
            },
            // The selectable role already announces selected state; describing
            // the icon too would have TalkBack say it twice.
            contentDescription = null,
            tint = if (selected) content else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(Sizing.iconMd)
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = content
        )
    }
}

@Preview(showBackground = true, name = "Self-report — disclaimer")
@Composable
private fun AdhdAssessmentDisclaimerPreview() {
    FocusFlowTheme {
        AdhdAssessmentScreen(onComplete = {}, onExit = {})
    }
}

@Preview(showBackground = true, name = "Self-report — answer row states")
@Composable
private fun AnswerRowStatesPreview() {
    FocusFlowTheme {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            FrequencyAnswer.entries.forEachIndexed { index, option ->
                AnswerRow(label = option.label, selected = index == 2, onClick = {})
            }
        }
    }
}

@Preview(showBackground = true, name = "Self-report — answer rows, dark large font", fontScale = 1.5f)
@Composable
private fun AnswerRowLargeFontPreview() {
    FocusFlowTheme(darkTheme = true) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            AnswerRow(label = FrequencyAnswer.VERY_OFTEN.label, selected = true, onClick = {})
            AnswerRow(label = FrequencyAnswer.NEVER.label, selected = false, onClick = {})
        }
    }
}
