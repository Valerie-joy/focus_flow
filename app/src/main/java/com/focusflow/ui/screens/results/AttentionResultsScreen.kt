package com.focusflow.ui.screens.results

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.focusflow.R
import com.focusflow.domain.models.AttentionCategory
import com.focusflow.domain.models.CategoryAssessmentResult
import com.focusflow.domain.models.GazeMetrics
import com.focusflow.domain.usecases.CalculateAttentionScoreUseCase
import com.focusflow.domain.usecases.InterpretAttentionScore
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.EmptyState
import com.focusflow.ui.components.GlassCard
import com.focusflow.ui.components.MessageBanner
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.components.RankingListItem
import com.focusflow.ui.components.SectionHeader
import com.focusflow.ui.components.SegmentedToggle
import com.focusflow.ui.components.StatusPill
import com.focusflow.ui.components.StatusTone
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.ui.theme.LocalFocusFlowColors
import com.focusflow.ui.theme.Sizing
import com.focusflow.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * The session-overview screen.
 *
 * Scores every completed category — successful *and* low-attention, since a
 * category that triggered an early stop is exactly the "weakest attention"
 * signal this screen needs — via [CalculateAttentionScoreUseCase], then lets
 * the user flip between strongest-first and weakest-first.
 *
 * What changed, beyond styling:
 *
 *  - **There was no overall result.** The screen opened straight into a ranked
 *    list, so the first thing the user saw was a comparison with no anchor.
 *    A single session average now leads, with a descriptive band and a
 *    sentence of interpretation.
 *  - **Numbers had no meaning attached.** Percentages were shown bare. Each is
 *    now accompanied by plain language from [InterpretAttentionScore].
 *  - **Nothing said what this isn't.** The screen sits directly downstream of
 *    an ADHD questionnaire, which makes a low number very easy to misread as a
 *    diagnosis. A limitations note is now part of the result, not a footnote.
 *  - **A provisional result looked settled.** A session with fewer than the
 *    required five successful assessments is now labelled as such.
 */
@Composable
fun AttentionResultsScreen(
    results: List<CategoryAssessmentResult>,
    onViewFullAnalysis: () -> Unit,
    modifier: Modifier = Modifier,
    requiredAssessments: Int = 5,
    scoreUseCase: CalculateAttentionScoreUseCase = remember { CalculateAttentionScoreUseCase() }
) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) } // 0 = Strongest, 1 = Weakest
    val scored = remember(results) { scoreUseCase.scoreAll(results) }
    val ordered = remember(scored, tabIndex) {
        if (tabIndex == 0) scored else scored.reversed()
    }

    val successfulCount = remember(results) { results.count { it.successful } }
    val overallScore = remember(scored) {
        if (scored.isEmpty()) null else scored.map { it.score }.average().roundToInt()
    }
    val provisionalNotice = remember(successfulCount, requiredAssessments) {
        InterpretAttentionScore.provisionalNotice(successfulCount, requiredAssessments)
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
                Spacer(modifier = Modifier.height(Spacing.xl))
                Text(
                    text = stringResource(R.string.results_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )

                if (overallScore == null) {
                    EmptyState(
                        icon = Icons.Filled.Insights,
                        title = stringResource(R.string.results_empty_title),
                        description = stringResource(R.string.results_empty_body),
                        modifier = Modifier.padding(top = Spacing.xl)
                    )
                    return@Column
                }

                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    // A plural resource rather than a manual singular/plural
                    // branch, which only ever works for English.
                    text = pluralStringResource(
                        R.plurals.results_across_categories,
                        results.size,
                        results.size
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (provisionalNotice != null) {
                    Spacer(modifier = Modifier.height(Spacing.md))
                    MessageBanner(message = provisionalNotice, tone = StatusTone.WARNING)
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                OverallScoreCard(
                    score = overallScore,
                    topCategory = scored.firstOrNull()?.result?.category?.displayName
                )

                Spacer(modifier = Modifier.height(Spacing.xl))

                SectionHeader(
                    title = stringResource(R.string.results_by_category),
                    subtitle = stringResource(R.string.results_by_category_subtitle)
                )
                Spacer(modifier = Modifier.height(Spacing.sm))

                SegmentedToggle(
                    options = listOf("Strongest", "Weakest"),
                    selectedIndex = tabIndex,
                    onSelect = { tabIndex = it }
                )

                Spacer(modifier = Modifier.height(Spacing.sm))

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        ordered.forEachIndexed { index, entry ->
                            RankingListItem(
                                rank = index + 1,
                                label = entry.result.category.displayName,
                                percentage = entry.score
                            )
                            if (index != ordered.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    thickness = Sizing.hairline
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xl))

                LimitationsNote()

                Spacer(modifier = Modifier.height(Spacing.xl))
                PrimaryButton(
                    text = stringResource(R.string.results_view_full_analysis),
                    onClick = onViewFullAnalysis
                )
                Spacer(modifier = Modifier.height(Spacing.xxl))
            }
        }
    }
}

/**
 * The session's headline result.
 *
 * One number, at display size, with the band and a sentence explaining what it
 * describes. This is the only place on the screen where type gets this large —
 * that is the hierarchy doing its job rather than decoration.
 */
@Composable
private fun OverallScoreCard(
    score: Int,
    topCategory: String?,
    modifier: Modifier = Modifier
) {
    val colors = LocalFocusFlowColors.current
    val band = InterpretAttentionScore.bandFor(score)
    val tone = when (band) {
        InterpretAttentionScore.Band.VERY_STRONG,
        InterpretAttentionScore.Band.STRONG -> StatusTone.SUCCESS
        InterpretAttentionScore.Band.MIXED -> StatusTone.NEUTRAL
        InterpretAttentionScore.Band.VARIABLE -> StatusTone.WARNING
    }
    val accent = when (tone) {
        StatusTone.SUCCESS -> colors.success
        StatusTone.WARNING -> colors.warning
        else -> MaterialTheme.colorScheme.onSurface
    }

    val averageDescription = stringResource(
        R.string.results_a11y_average,
        score,
        band.label
    )

    GlassCard(modifier = modifier.fillMaxWidth(), padding = Spacing.lg) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.results_session_average),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$score",
                    style = MaterialTheme.typography.displayLarge,
                    color = accent,
                    // One node for the number and its unit, so TalkBack reads
                    // "72 percent" rather than "72" then "percent".
                    modifier = Modifier.semantics {
                        contentDescription = averageDescription
                    }
                )
                Text(
                    text = "%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = accent,
                    modifier = Modifier.padding(bottom = Spacing.xs)
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            StatusPill(text = band.label, tone = tone)
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = topCategory?.let {
                    InterpretAttentionScore.summaryFor(score, it)
                } ?: "Averaged across every category you completed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** What these results are, and what they are not. Shown with every result. */
@Composable
internal fun LimitationsNote(modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth(), padding = Spacing.md) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                text = stringResource(R.string.results_how_to_read),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = InterpretAttentionScore.LIMITATIONS,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true, name = "Results")
@Composable
private fun AttentionResultsScreenPreview() {
    val sampleResults = listOf(
        CategoryAssessmentResult(
            category = AttentionCategory.MUSIC,
            gazeMetrics = GazeMetrics(94f, 2, null, 14),
            successful = true, interestRating = 5, focusRating = 4
        ),
        CategoryAssessmentResult(
            category = AttentionCategory.GAMING,
            gazeMetrics = GazeMetrics(92f, 3, null, 12),
            successful = true, interestRating = 5, focusRating = 5
        ),
        CategoryAssessmentResult(
            category = AttentionCategory.EDUCATION,
            gazeMetrics = GazeMetrics(51f, 9, 8000L, 20),
            successful = false
        )
    )
    FocusFlowTheme {
        AttentionResultsScreen(results = sampleResults, onViewFullAnalysis = {})
    }
}

@Preview(showBackground = true, name = "Results — empty, dark")
@Composable
private fun AttentionResultsEmptyPreview() {
    FocusFlowTheme(darkTheme = true) {
        AttentionResultsScreen(results = emptyList(), onViewFullAnalysis = {})
    }
}
