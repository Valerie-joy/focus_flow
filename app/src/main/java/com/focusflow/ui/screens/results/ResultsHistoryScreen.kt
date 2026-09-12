package com.focusflow.ui.screens.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.GlassCard
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.components.ProgressLineChart
import com.focusflow.ui.components.ProgressPoint
import com.focusflow.ui.components.RadarChart
import com.focusflow.ui.components.RadarChartEntry
import com.focusflow.ui.components.RankingListItem
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.viewmodel.CategoryRankEntry
import com.focusflow.viewmodel.ProgressPointData
import com.focusflow.viewmodel.ResultsUiState

/**
 * Phase 9. Everything the spec calls for: attention ranking, AI insights,
 * interactive charts (radar + progress-history line chart), and the two
 * export actions (PDF download, email). onDownloadReport/onEmailReport
 * are plain callbacks so this composable stays free of Context/Intent
 * concerns — the nav graph wires them to PdfReportGenerator/
 * EmailReportService/ReportDownloadService.
 */
@Composable
fun ResultsHistoryScreen(
    uiState: ResultsUiState,
    onBack: () -> Unit,
    onDownloadReport: () -> Unit,
    onEmailReport: () -> Unit,
    downloadStatus: String? = null
) {
    BlurBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "FocusFlow",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Assessment Report",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (uiState.latestSessionDateLabel != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uiState.latestSessionDateLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!uiState.hasData) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Complete an assessment to see your results here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                return@Column
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Attention ranking",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    uiState.latestSessionRanking.forEachIndexed { index, entry ->
                        RankingListItem(rank = index + 1, label = entry.category, percentage = entry.score)
                    }
                }
            }

            if (uiState.insightText != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "AI insight",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = uiState.insightText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "This session across categories",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            GlassCard(modifier = Modifier.fillMaxWidth(), blurBehind = false) {
                RadarChart(
                    entries = uiState.latestSessionRanking.map { RadarChartEntry(it.category, it.score / 100f) }
                )
            }

            if (uiState.progressHistory.size >= 2) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Progress history",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    ProgressLineChart(
                        points = uiState.progressHistory.map { ProgressPoint(it.dateLabel, it.score) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(
                    text = "Download PDF",
                    onClick = onDownloadReport,
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
                PrimaryButton(
                    text = "Email report",
                    onClick = onEmailReport,
                    modifier = Modifier.weight(1f)
                )
            }
            if (downloadStatus != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = downloadStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Preview(showBackground = true)
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
        insightText = "Your attention is strongest with Music content (94%). It dips most with Education (52%) — that's a good place to try shorter, more interactive material.",
        progressHistory = listOf(
            ProgressPointData("Jul 10", 68),
            ProgressPointData("Jul 17", 74),
            ProgressPointData("Jul 25", 79)
        )
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
