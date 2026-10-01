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
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.focusflow.domain.usecases.InterpretAttentionScore
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.EmptyState
import com.focusflow.ui.components.GlassCard
import com.focusflow.ui.components.MessageBanner
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.components.ProgressLineChart
import com.focusflow.ui.components.ProgressPoint
import com.focusflow.ui.components.RadarChart
import com.focusflow.ui.components.RadarChartEntry
import com.focusflow.ui.components.RankingListItem
import com.focusflow.ui.components.SecondaryButton
import com.focusflow.ui.components.SectionHeader
import com.focusflow.ui.components.StatusTone
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.ui.theme.Sizing
import com.focusflow.ui.theme.Spacing
import com.focusflow.viewmodel.CategoryRankEntry
import com.focusflow.viewmodel.ProgressPointData
import com.focusflow.viewmodel.ResultsUiState

/**
 * Persisted assessment history and the export actions.
 *
 * Changes of substance:
 *
 *  - **"AI insight" was not an AI insight.** The text under that heading is a
 *    template string assembled in [com.focusflow.viewmodel.ResultsViewModel]
 *    from the highest and lowest scoring categories. Labelling a template as
 *    AI overstates what the app did and invites the user to trust it more than
 *    it has earned. It now says what it is: something we noticed.
 *  - **The empty state was one grey sentence** below a heading and a date,
 *    which looks like a screen that failed to load rather than one with
 *    nothing in it yet.
 *  - **A trend was implied from two points.** The progress chart appeared at
 *    two sessions. Two points always draw a line, and a line always reads as a
 *    trend; it now needs at least three and says how many sessions it covers.
 *  - **Export gave no real feedback.** `downloadStatus` was plain grey text
 *    with no role, easily missed and silent to TalkBack. It is now a banner in
 *    an assertive live region.
 *  - **The provisional flag was never shown**, and the limitations note that
 *    accompanies the in-session results was missing here entirely.
 */
@Composable
fun ResultsHistoryScreen(
    uiState: ResultsUiState,
    onBack: () -> Unit,
    onDownloadReport: () -> Unit,
    onEmailReport: () -> Unit,
    modifier: Modifier = Modifier,
    downloadStatus: String? = null,
    isExporting: Boolean = false,
    onStartAssessment: (() -> Unit)? = null
) {
    val provisionalNotice = InterpretAttentionScore.provisionalNotice(
        uiState.successfulAssessments,
        uiState.requiredAssessments
    )

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
                        contentDescription = "Go back"
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = "Assessment report",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                if (uiState.latestSessionDateLabel != null) {
                    Spacer(modifier = Modifier.height(Spacing.xxs))
                    Text(
                        text = uiState.latestSessionDateLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!uiState.hasData) {
                    EmptyState(
                        icon = Icons.Filled.Assessment,
                        title = "No assessments yet",
                        description = "Once you finish an assessment, your attention profile, " +
                            "category breakdown and progress over time appear here.",
                        actionLabel = if (onStartAssessment != null) "Start an assessment" else null,
                        onAction = onStartAssessment,
                        modifier = Modifier.padding(top = Spacing.xxl)
                    )
                    Spacer(modifier = Modifier.height(Spacing.xxl))
                    return@Column
                }

                if (provisionalNotice != null) {
                    Spacer(modifier = Modifier.height(Spacing.md))
                    MessageBanner(message = provisionalNotice, tone = StatusTone.WARNING)
                }

                Spacer(modifier = Modifier.height(Spacing.xl))
                SectionHeader(
                    title = "Attention ranking",
                    subtitle = "Your most recent session, strongest first."
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        uiState.latestSessionRanking.forEachIndexed { index, entry ->
                            RankingListItem(
                                rank = index + 1,
                                label = entry.category,
                                percentage = entry.score
                            )
                            if (index != uiState.latestSessionRanking.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    thickness = Sizing.hairline
                                )
                            }
                        }
                    }
                }

                if (uiState.insightText != null) {
                    Spacer(modifier = Modifier.height(Spacing.xl))
                    SectionHeader(title = "What we noticed")
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = uiState.insightText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xl))
                SectionHeader(
                    title = "This session across categories",
                    subtitle = "Each spoke is one category; further out means steadier attention."
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    RadarChart(
                        entries = uiState.latestSessionRanking.map {
                            RadarChartEntry(it.category, it.score / 100f)
                        }
                    )
                }

                // Three points minimum. Two points always render as a straight
                // line, and a straight line reads as a trend regardless of what
                // the data supports.
                if (uiState.progressHistory.size >= MIN_POINTS_FOR_TREND) {
                    Spacer(modifier = Modifier.height(Spacing.xl))
                    SectionHeader(
                        title = "Progress over time",
                        subtitle = "Session averages across your last " +
                            "${uiState.progressHistory.size} sessions. Day-to-day variation " +
                            "is normal — look at the shape, not single points."
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        ProgressLineChart(
                            points = uiState.progressHistory.map {
                                ProgressPoint(it.dateLabel, it.score)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xl))
                LimitationsNote()

                Spacer(modifier = Modifier.height(Spacing.xl))
                SectionHeader(
                    title = "Export",
                    subtitle = "A PDF of this report, to keep or to share."
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SecondaryButton(
                        text = "Save PDF",
                        onClick = onDownloadReport,
                        modifier = Modifier.weight(1f),
                        loading = isExporting
                    )
                    PrimaryButton(
                        text = "Email report",
                        onClick = onEmailReport,
                        modifier = Modifier.weight(1f),
                        enabled = !isExporting
                    )
                }
                if (downloadStatus != null) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    MessageBanner(
                        message = downloadStatus,
                        // Export reports both outcomes through this one string;
                        // a failure is worded as one, so anything that isn't a
                        // failure is success.
                        tone = if (downloadStatus.startsWith("Couldn't", ignoreCase = true)) {
                            StatusTone.ERROR
                        } else {
                            StatusTone.SUCCESS
                        }
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.xxl))
            }
        }
    }
}

/** Fewest points before a line chart is allowed to suggest a direction. */
private const val MIN_POINTS_FOR_TREND = 3

@Preview(showBackground = true, name = "Report")
@Composable
private fun ResultsHistoryScreenPreview() {
    val sampleState = ResultsUiState(
        hasData = true,
        latestSessionDateLabel = "July 25, 2026",
        latestSessionRanking = listOf(
            CategoryRankEntry("Music", 94),
            CategoryRankEntry("Gaming", 91),
            CategoryRankEntry("Education", 52)
        ),
        insightText = "Your attention is strongest with Music content (94%). It dips most " +
            "with Education (52%) — that's a good place to try shorter, more interactive material.",
        progressHistory = listOf(
            ProgressPointData("Jul 10", 68),
            ProgressPointData("Jul 17", 74),
            ProgressPointData("Jul 25", 79)
        ),
        successfulAssessments = 5
    )
    FocusFlowTheme {
        ResultsHistoryScreen(
            uiState = sampleState,
            onBack = {},
            onDownloadReport = {},
            onEmailReport = {}
        )
    }
}

@Preview(showBackground = true, name = "Report — empty")
@Composable
private fun ResultsHistoryEmptyPreview() {
    FocusFlowTheme {
        ResultsHistoryScreen(
            uiState = ResultsUiState(hasData = false),
            onBack = {},
            onDownloadReport = {},
            onEmailReport = {},
            onStartAssessment = {}
        )
    }
}
