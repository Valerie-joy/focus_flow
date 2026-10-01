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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.focusflow.R
import com.focusflow.domain.models.AttentionCategory
import com.focusflow.domain.models.CategoryAssessmentResult
import com.focusflow.domain.models.GazeMetrics
import com.focusflow.domain.usecases.CalculateAttentionScoreUseCase
import com.focusflow.domain.usecases.CategoryAttentionScore
import com.focusflow.domain.usecases.InterpretAttentionScore
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.EmptyState
import com.focusflow.ui.components.GlassCard
import com.focusflow.ui.components.MetricRow
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.components.ProgressRing
import com.focusflow.ui.components.RadarChart
import com.focusflow.ui.components.RadarChartEntry
import com.focusflow.ui.components.RankingListItem
import com.focusflow.ui.components.SectionHeader
import com.focusflow.ui.components.StatusTone
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.ui.theme.Sizing
import com.focusflow.ui.theme.Spacing

/**
 * The "what actually happened" step, between the session average and the
 * suggestions.
 *
 * This screen previously answered a question nobody asked. It showed three
 * progress rings, a radar chart and a ranked list — three views of the same
 * single number, per category — and never once said what that number was made
 * of. A user who tapped "View full analysis" wanting to know *why* Education
 * scored 52% learned only that it scored 52% in three more shapes.
 *
 * It now leads with the four things that were actually measured, in plain
 * language, for the strongest and weakest category — drawn from the real
 * [GazeMetrics] on each result, with wording from [InterpretAttentionScore].
 * The visual summaries stay, below, where they belong: they are good at
 * comparison and useless at explanation.
 *
 * No normative language anywhere. There are no percentiles or population
 * comparisons because the app holds no reference data; a phrase like "below
 * average" would be fabricated.
 */
@Composable
fun FullAnalysisScreen(
    results: List<CategoryAssessmentResult>,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    scoreUseCase: CalculateAttentionScoreUseCase = remember { CalculateAttentionScoreUseCase() }
) {
    val scored = remember(results) { scoreUseCase.scoreAll(results) }
    val radarEntries = remember(scored) {
        scored.map { RadarChartEntry(it.result.category.displayName, it.score / 100f) }
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
                Spacer(modifier = Modifier.height(Spacing.xs))
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.a11y_go_back)
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.analysis_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )

                if (scored.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.Insights,
                        title = stringResource(R.string.analysis_empty_title),
                        description = stringResource(R.string.analysis_empty_body),
                        modifier = Modifier.padding(top = Spacing.xl)
                    )
                    Spacer(modifier = Modifier.height(Spacing.xl))
                    return@Column
                }

                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.analysis_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // The strongest and weakest categories are the two worth
                // breaking down: together they bracket the session, and showing
                // all nine would be the wall of numbers this screen replaces.
                Spacer(modifier = Modifier.height(Spacing.xl))
                CategoryBreakdown(entry = scored.first())

                if (scored.size > 1) {
                    Spacer(modifier = Modifier.height(Spacing.md))
                    CategoryBreakdown(entry = scored.last())
                }

                Spacer(modifier = Modifier.height(Spacing.xl))
                SectionHeader(title = stringResource(R.string.analysis_strongest))
                Spacer(modifier = Modifier.height(Spacing.sm))
                TopCategoryRings(entries = scored.take(3))

                Spacer(modifier = Modifier.height(Spacing.xl))
                SectionHeader(
                    title = stringResource(R.string.analysis_shape),
                    subtitle = stringResource(R.string.analysis_shape_subtitle)
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    RadarChart(entries = radarEntries)
                }

                Spacer(modifier = Modifier.height(Spacing.xl))
                SectionHeader(title = stringResource(R.string.analysis_every_category))
                Spacer(modifier = Modifier.height(Spacing.sm))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        scored.forEachIndexed { index, entry ->
                            RankingListItem(
                                rank = index + 1,
                                label = entry.result.category.displayName,
                                percentage = entry.score
                            )
                            if (index != scored.lastIndex) {
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
                    text = stringResource(R.string.analysis_continue),
                    onClick = onContinue
                )
                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }
    }
}

/**
 * One category, broken into the four measurements behind its score.
 *
 * Every value comes from the stored [GazeMetrics]; every explanation comes from
 * [InterpretAttentionScore], so the phrasing matches the results screen and the
 * exported PDF rather than being written again here.
 */
@Composable
private fun CategoryBreakdown(
    entry: CategoryAttentionScore,
    modifier: Modifier = Modifier
) {
    val metrics = entry.result.gazeMetrics
    val band = InterpretAttentionScore.bandFor(entry.score)
    val tone = when (band) {
        InterpretAttentionScore.Band.VERY_STRONG,
        InterpretAttentionScore.Band.STRONG -> StatusTone.SUCCESS
        InterpretAttentionScore.Band.MIXED -> StatusTone.NEUTRAL
        InterpretAttentionScore.Band.VARIABLE -> StatusTone.WARNING
    }

    GlassCard(modifier = modifier.fillMaxWidth(), padding = Spacing.md) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.result.category.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = "${entry.score}%",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xxs))
            Text(
                text = InterpretAttentionScore.summaryFor(
                    entry.score,
                    entry.result.category.displayName
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.md))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = Sizing.hairline
            )
            Spacer(modifier = Modifier.height(Spacing.md))

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                MetricRow(
                    label = stringResource(R.string.analysis_metric_attention),
                    value = "${metrics.screenAttentionPercentage.toInt()}%",
                    explanation = InterpretAttentionScore.explainScreenAttention(
                        metrics.screenAttentionPercentage
                    ),
                    tone = tone
                )
                MetricRow(
                    label = stringResource(R.string.analysis_metric_shifts),
                    value = metrics.gazeShiftCount.toString(),
                    explanation = InterpretAttentionScore.explainGazeShifts(metrics.gazeShiftCount)
                )
                MetricRow(
                    label = stringResource(R.string.analysis_metric_first_look),
                    value = metrics.firstDistractionMs
                        ?.let { "${it / 1000}s" }
                        ?: "—",
                    explanation = InterpretAttentionScore.explainFirstDistraction(
                        metrics.firstDistractionMs
                    )
                )
                MetricRow(
                    label = stringResource(R.string.analysis_metric_blinks),
                    value = metrics.blinkCount.toString(),
                    explanation = InterpretAttentionScore.explainBlinks(metrics.blinkCount)
                )
            }
        }
    }
}

/**
 * The top three as rings.
 *
 * Wrapped in one semantics node per ring so TalkBack reads "Music, 94 percent"
 * rather than a bare number followed by an unrelated label — the ring and its
 * caption were separate nodes.
 */
@Composable
private fun TopCategoryRings(
    entries: List<CategoryAttentionScore>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        entries.forEach { entry ->
            val label = entry.result.category.displayName
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = "$label, ${entry.score} percent"
                }
            ) {
                ProgressRing(
                    progress = entry.score / 100f,
                    size = 78.dp,
                    label = "${entry.score}%"
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private val previewResults = AttentionCategory.entries.mapIndexed { index, category ->
    CategoryAssessmentResult(
        category = category,
        gazeMetrics = GazeMetrics(
            screenAttentionPercentage = (95 - index * 6).toFloat(),
            gazeShiftCount = index,
            firstDistractionMs = if (index > 3) (index * 2000L) else null,
            blinkCount = 14
        ),
        successful = true,
        interestRating = 4,
        focusRating = 4
    )
}

@Preview(showBackground = true, name = "Full analysis", heightDp = 1800)
@Composable
private fun FullAnalysisScreenPreview() {
    FocusFlowTheme {
        FullAnalysisScreen(results = previewResults, onBack = {}, onContinue = {})
    }
}

@Preview(showBackground = true, name = "Full analysis — dark", heightDp = 1800)
@Composable
private fun FullAnalysisScreenDarkPreview() {
    FocusFlowTheme(darkTheme = true) {
        FullAnalysisScreen(results = previewResults, onBack = {}, onContinue = {})
    }
}

@Preview(showBackground = true, name = "Full analysis — empty")
@Composable
private fun FullAnalysisEmptyPreview() {
    FocusFlowTheme {
        FullAnalysisScreen(results = emptyList(), onBack = {}, onContinue = {})
    }
}
