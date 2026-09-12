package com.focusflow.ui.screens.results

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.components.RankingListItem
import com.focusflow.ui.components.SegmentedToggle
import com.focusflow.ui.theme.FocusFlowTheme

/**
 * Phase 6's overview screen. Scores every completed category (successful
 * AND low-attention — a category that triggered an early stop is exactly
 * the "weakest attention" signal this screen needs to show) via
 * [CalculateAttentionScoreUseCase], then lets the user flip between
 * strongest-first and weakest-first ranking, matching the reference
 * design's tab toggle.
 */
@Composable
fun AttentionResultsScreen(
    results: List<CategoryAssessmentResult>,
    onViewFullAnalysis: () -> Unit,
    scoreUseCase: CalculateAttentionScoreUseCase = remember { CalculateAttentionScoreUseCase() }
) {
    var tabIndex by remember { mutableIntStateOf(0) } // 0 = Strongest, 1 = Weakest
    val scored = remember(results) { scoreUseCase.scoreAll(results) }
    val ordered = if (tabIndex == 0) scored else scored.reversed()

    BlurBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "Your Results",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Here's your\nattention profile",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "We analyzed your focus across ${results.size} content categories.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            SegmentedToggle(
                options = listOf("Strongest", "Weakest"),
                selectedIndex = tabIndex,
                onSelect = { tabIndex = it }
            )

            Spacer(modifier = Modifier.height(20.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ordered.forEachIndexed { index, entry ->
                        RankingListItem(
                            rank = index + 1,
                            label = entry.result.category.displayName,
                            percentage = entry.score
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            PrimaryButton(
                text = "View full analysis",
                onClick = onViewFullAnalysis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Preview(showBackground = true)
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
