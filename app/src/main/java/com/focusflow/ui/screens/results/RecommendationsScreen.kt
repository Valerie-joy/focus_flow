package com.focusflow.ui.screens.results

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.focusflow.domain.models.AttentionCategory
import com.focusflow.domain.models.CategoryAssessmentResult
import com.focusflow.domain.models.GazeMetrics
import com.focusflow.domain.usecases.CalculateAttentionScoreUseCase
import com.focusflow.domain.usecases.GenerateRecommendationsUseCase
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.GlassCard
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.theme.Accent
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.ui.theme.Primary

/**
 * Phase 7. Runs GenerateRecommendationsUseCase and lays out every field the
 * spec calls for: learning style, best-performing categories, attention
 * strength score, suggested study techniques, recommended content types,
 * and weekly goals — organized to match the reference design's
 * "Recommended for you" list + learning-style card, with the extra fields
 * (weekly goals, best categories) added below since the mockup doesn't
 * show every field at once.
 *
 * Two entry points: this one takes raw [CategoryAssessmentResult]s and
 * scores them (the normal Phase 7 flow, right after an assessment
 * completes); the other overload below takes already-scored entries
 * directly, for reuse from Profile's "AI Recommendations" where only the
 * final score — not raw gaze metrics — is available from persisted history.
 */
@Composable
fun RecommendationsScreen(
    results: List<CategoryAssessmentResult>,
    onContinue: () -> Unit,
    scoreUseCase: CalculateAttentionScoreUseCase = remember { CalculateAttentionScoreUseCase() },
    recommendationsUseCase: GenerateRecommendationsUseCase = remember { GenerateRecommendationsUseCase() }
) {
    val profile = remember(results) {
        recommendationsUseCase(scoreUseCase.scoreAll(results))
    }
    RecommendationsScreenContent(profile = profile, onContinue = onContinue)
}

/**
 * Overload for when scores already exist (e.g. Profile's "AI
 * Recommendations", built from persisted history) — skips
 * CalculateAttentionScoreUseCase entirely so the displayed scores match
 * exactly what was already shown/stored, rather than being re-derived from
 * synthetic gaze metrics.
 */
@Composable
fun RecommendationsScreen(
    scoredEntries: List<com.focusflow.domain.usecases.CategoryAttentionScore>,
    onContinue: () -> Unit,
    recommendationsUseCase: GenerateRecommendationsUseCase = remember { GenerateRecommendationsUseCase() }
) {
    val profile = remember(scoredEntries) { recommendationsUseCase(scoredEntries) }
    RecommendationsScreenContent(profile = profile, onContinue = onContinue)
}

@Composable
private fun RecommendationsScreenContent(
    profile: com.focusflow.domain.usecases.RecommendationProfile?,
    onContinue: () -> Unit
) {
    BlurBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "AI Recommendation",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Your mind has\na unique rhythm",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (profile == null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "We need at least one completed category to generate recommendations.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(28.dp))
                PrimaryButton(text = "Continue", onClick = onContinue, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(24.dp))
                return@Column
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = profile.learningStyle.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "Recommended for you",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    profile.suggestedStudyTechniques.forEach { technique ->
                        RecommendationRow(icon = iconFor(technique), text = technique)
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "Learning style",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = profile.learningStyle.displayName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Attention strength score: ${profile.attentionStrengthScore}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "Best performing categories",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = profile.bestPerformingCategories.joinToString(", ") { it.displayName },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Recommended content types",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = profile.recommendedContentTypes.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "This week's goals",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    profile.weeklyGoals.forEach { goal ->
                        Row {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = goal,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            PrimaryButton(text = "Continue", onClick = onContinue, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RecommendationRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Accent.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun iconFor(technique: String): ImageVector = when {
    technique.contains("music", ignoreCase = true) || technique.contains("podcast", ignoreCase = true) || technique.contains("audio", ignoreCase = true) -> Icons.Filled.MusicNote
    technique.contains("gamif", ignoreCase = true) || technique.contains("game", ignoreCase = true) || technique.contains("quiz", ignoreCase = true) -> Icons.Filled.SportsEsports
    technique.contains("visual", ignoreCase = true) || technique.contains("diagram", ignoreCase = true) || technique.contains("video", ignoreCase = true) -> Icons.Filled.Visibility
    technique.contains("hands-on", ignoreCase = true) || technique.contains("interactive", ignoreCase = true) || technique.contains("practice", ignoreCase = true) -> Icons.Filled.PanTool
    technique.contains("outline", ignoreCase = true) || technique.contains("step", ignoreCase = true) || technique.contains("section", ignoreCase = true) -> Icons.Filled.TextFields
    technique.contains("story", ignoreCase = true) || technique.contains("narrative", ignoreCase = true) || technique.contains("case stud", ignoreCase = true) -> Icons.Filled.Extension
    else -> Icons.Filled.AutoAwesome
}

@Preview(showBackground = true)
@Composable
private fun RecommendationsScreenPreview() {
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
            category = AttentionCategory.CARTOON,
            gazeMetrics = GazeMetrics(76f, 4, null, 15),
            successful = true, interestRating = 4, focusRating = 4
        )
    )
    FocusFlowTheme {
        RecommendationsScreen(results = sampleResults, onContinue = {})
    }
}
