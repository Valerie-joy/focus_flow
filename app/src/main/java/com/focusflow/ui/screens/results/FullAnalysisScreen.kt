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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.focusflow.domain.models.AttentionCategory
import com.focusflow.domain.models.CategoryAssessmentResult
import com.focusflow.domain.models.GazeMetrics
import com.focusflow.domain.usecases.CalculateAttentionScoreUseCase
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.GlassCard
import com.focusflow.ui.components.ProgressRing
import com.focusflow.ui.components.RadarChart
import com.focusflow.ui.components.RadarChartEntry
import com.focusflow.ui.components.RankingListItem
import com.focusflow.ui.theme.FocusFlowTheme

/**
 * The deeper analysis view reached via "View full analysis" — combines all
 * four visualizations the spec calls for: animated progress rings (top 3),
 * a radar chart across every completed category, and the full ranked list
 * with horizontal bars (category ranking cards live inside the same
 * GlassCard list here rather than as separate cards, to avoid the screen
 * turning into a wall of duplicate glass panels).
 */
@Composable
fun FullAnalysisScreen(
    results: List<CategoryAssessmentResult>,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    scoreUseCase: CalculateAttentionScoreUseCase = remember { CalculateAttentionScoreUseCase() }
) {
    val scored = remember(results) { scoreUseCase.scoreAll(results) }
    val top3 = scored.take(3)
    val radarEntries = remember(scored) {
        scored.map { RadarChartEntry(it.result.category.displayName, it.score / 100f) }
    }

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
                text = "Full analysis",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Top categories",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                top3.forEach { entry ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ProgressRing(
                            progress = entry.score / 100f,
                            size = 84.dp,
                            label = "${entry.score}%"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = entry.result.category.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "Attention across categories",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            GlassCard(modifier = Modifier.fillMaxWidth(), blurBehind = false) {
                RadarChart(entries = radarEntries)
            }

            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "Full ranking",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    scored.forEachIndexed { index, entry ->
                        RankingListItem(
                            rank = index + 1,
                            label = entry.result.category.displayName,
                            percentage = entry.score
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            com.focusflow.ui.components.PrimaryButton(
                text = "See your recommendations",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FullAnalysisScreenPreview() {
    val sampleResults = AttentionCategory.entries.mapIndexed { index, category ->
        CategoryAssessmentResult(
            category = category,
            gazeMetrics = GazeMetrics(
                screenAttentionPercentage = (95 - index * 6).toFloat(),
                gazeShiftCount = index,
                firstDistractionMs = null,
                blinkCount = 14
            ),
            successful = true,
            interestRating = 4,
            focusRating = 4
        )
    }
    FocusFlowTheme {
        FullAnalysisScreen(results = sampleResults, onBack = {}, onContinue = {})
    }
}
