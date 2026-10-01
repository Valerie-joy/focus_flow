package com.focusflow.ui.screens.results

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TextFields
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
import androidx.compose.ui.res.stringResource
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
import com.focusflow.domain.usecases.ExplainRecommendations
import com.focusflow.domain.usecases.GenerateRecommendationsUseCase
import com.focusflow.domain.usecases.RecommendationProfile
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.EmptyState
import com.focusflow.ui.components.GlassCard
import com.focusflow.ui.components.MessageBanner
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.components.SectionHeader
import com.focusflow.ui.components.StatusTone
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.ui.theme.FocusFlowRadius
import com.focusflow.ui.theme.Sizing
import com.focusflow.ui.theme.Spacing

/**
 * Suggestions drawn from the session.
 *
 * The recommendation *algorithm* is unchanged — see
 * [GenerateRecommendationsUseCase], which is deterministic and offline by
 * design. What changed is that the screen now shows the reasoning the engine
 * already produced:
 *
 *  - **Nothing said why.** `clusterShares` and `isProvisional` were computed on
 *    every profile and displayed nowhere, so a fully inspectable model was
 *    presented as an unexplained verdict: a learning style, a flat list of
 *    techniques, and "Attention strength score: 78" with no units, no basis and
 *    no indication it came from three categories.
 *  - **Everything was one undifferentiated list** of cards at equal weight —
 *    techniques, style, best categories, content types, goals — so the reader
 *    had no way to tell a "try this today" from a description of themselves.
 *    They are now grouped by intent (habits, formats, this week), each with the
 *    one-line reason it appears.
 *  - **"Your mind has a unique rhythm"** over an "AI Recommendation" eyebrow
 *    claimed both more insight and more machinery than a local weighted tally
 *    has. Retitled to what it is: things that might work, and why.
 *  - **The empty state was a grey sentence** under that same flourish.
 */
@Composable
fun RecommendationsScreen(
    results: List<CategoryAssessmentResult>,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    scoreUseCase: CalculateAttentionScoreUseCase = remember { CalculateAttentionScoreUseCase() },
    recommendationsUseCase: GenerateRecommendationsUseCase = remember { GenerateRecommendationsUseCase() }
) {
    val profile = remember(results) {
        recommendationsUseCase(scoreUseCase.scoreAll(results))
    }
    RecommendationsScreenContent(profile = profile, onContinue = onContinue, modifier = modifier)
}

/**
 * Overload for when scores already exist (Profile's recommendations view, built
 * from persisted history) — skips scoring entirely so the numbers match what was
 * already shown and stored, rather than being re-derived from synthetic metrics.
 */
@Composable
fun RecommendationsScreen(
    scoredEntries: List<CategoryAttentionScore>,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    recommendationsUseCase: GenerateRecommendationsUseCase = remember { GenerateRecommendationsUseCase() }
) {
    val profile = remember(scoredEntries) { recommendationsUseCase(scoredEntries) }
    RecommendationsScreenContent(profile = profile, onContinue = onContinue, modifier = modifier)
}

@Composable
private fun RecommendationsScreenContent(
    profile: RecommendationProfile?,
    onContinue: () -> Unit,
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
                Spacer(modifier = Modifier.height(Spacing.xl))
                Text(
                    text = stringResource(R.string.recommendations_eyebrow),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.xxs))
                Text(
                    text = stringResource(R.string.recommendations_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )

                if (profile == null) {
                    EmptyState(
                        icon = Icons.Filled.Lightbulb,
                        title = stringResource(R.string.recommendations_empty_title),
                        description = stringResource(R.string.recommendations_empty_body),
                        modifier = Modifier.padding(top = Spacing.xl)
                    )
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    PrimaryButton(
                        text = stringResource(R.string.action_continue),
                        onClick = onContinue
                    )
                    Spacer(modifier = Modifier.height(Spacing.xl))
                    return@Column
                }

                ExplainRecommendations.provisionalNotice(profile)?.let { notice ->
                    Spacer(modifier = Modifier.height(Spacing.md))
                    MessageBanner(message = notice, tone = StatusTone.WARNING)
                }

                Spacer(modifier = Modifier.height(Spacing.lg))
                BasisCard(profile = profile)

                Spacer(modifier = Modifier.height(Spacing.xl))
                RecommendationGroup(
                    title = stringResource(R.string.recommendations_group_habits),
                    why = stringResource(
                        R.string.recommendations_group_habits_why,
                        profile.bestPerformingCategories.firstOrNull()?.displayName
                            ?: "the clips you watched"
                    ),
                    items = profile.suggestedStudyTechniques,
                    iconFor = ::techniqueIcon
                )

                Spacer(modifier = Modifier.height(Spacing.lg))
                RecommendationGroup(
                    title = stringResource(R.string.recommendations_group_formats),
                    why = stringResource(R.string.recommendations_group_formats_why),
                    items = profile.recommendedContentTypes,
                    iconFor = { Icons.Filled.PlayCircleOutline }
                )

                Spacer(modifier = Modifier.height(Spacing.lg))
                RecommendationGroup(
                    title = stringResource(R.string.recommendations_group_goals),
                    why = stringResource(R.string.recommendations_group_goals_why),
                    items = profile.weeklyGoals,
                    iconFor = { Icons.Filled.Flag }
                )

                Spacer(modifier = Modifier.height(Spacing.xl))
                GlassCard(modifier = Modifier.fillMaxWidth(), padding = Spacing.md) {
                    Text(
                        text = ExplainRecommendations.NOT_ADVICE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xl))
                PrimaryButton(
                    text = stringResource(R.string.action_continue),
                    onClick = onContinue
                )
                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }
    }
}

/**
 * The reasoning behind the whole profile, above the suggestions themselves.
 *
 * Leads with the observation, then the style it implies — that order matters. A
 * label first ("Auditory Learner") reads as a category the app has put the user
 * in; the observation first makes it a conclusion they can check against their
 * own experience of the session.
 */
@Composable
private fun BasisCard(profile: RecommendationProfile, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth(), padding = Spacing.md) {
        Column {
            Text(
                text = stringResource(R.string.recommendations_basis_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = ExplainRecommendations.basis(profile),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = ExplainRecommendations.styleRationale(profile),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ExplainRecommendations.clusterBreakdown(profile)?.let { breakdown ->
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = breakdown,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.recommendations_style_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = profile.learningStyle.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Spacing.xxs))
            Text(
                text = profile.learningStyle.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * One intent group: a heading, the reason it's here, then its items.
 *
 * Grouping uses the three lists the engine already returns — techniques,
 * content types and weekly goals — rather than inventing categories the domain
 * model doesn't support.
 */
@Composable
private fun RecommendationGroup(
    title: String,
    why: String,
    items: List<String>,
    iconFor: (String) -> ImageVector,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = title, subtitle = why)
        Spacer(modifier = Modifier.height(Spacing.sm))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items.forEach { item ->
                    RecommendationRow(icon = iconFor(item), text = item)
                }
            }
        }
    }
}

@Composable
private fun RecommendationRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(Sizing.iconLg - Spacing.xs)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                // The adjacent text is the recommendation; the icon is a bullet.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(Sizing.iconSm)
            )
        }
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

private fun techniqueIcon(technique: String): ImageVector = when {
    technique.contains("music", ignoreCase = true) ||
        technique.contains("podcast", ignoreCase = true) ||
        technique.contains("audio", ignoreCase = true) -> Icons.Filled.MusicNote
    technique.contains("gamif", ignoreCase = true) ||
        technique.contains("game", ignoreCase = true) ||
        technique.contains("quiz", ignoreCase = true) -> Icons.Filled.SportsEsports
    technique.contains("visual", ignoreCase = true) ||
        technique.contains("diagram", ignoreCase = true) ||
        technique.contains("video", ignoreCase = true) -> Icons.Filled.Visibility
    technique.contains("hands-on", ignoreCase = true) ||
        technique.contains("interactive", ignoreCase = true) ||
        technique.contains("practice", ignoreCase = true) -> Icons.Filled.PanTool
    technique.contains("outline", ignoreCase = true) ||
        technique.contains("step", ignoreCase = true) ||
        technique.contains("section", ignoreCase = true) -> Icons.Filled.TextFields
    technique.contains("story", ignoreCase = true) ||
        technique.contains("narrative", ignoreCase = true) ||
        technique.contains("case stud", ignoreCase = true) -> Icons.Filled.Extension
    else -> Icons.Filled.AutoAwesome
}

private val sampleResults = listOf(
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

@Preview(showBackground = true, name = "Recommendations", heightDp = 1400)
@Composable
private fun RecommendationsScreenPreview() {
    FocusFlowTheme {
        RecommendationsScreen(results = sampleResults, onContinue = {})
    }
}

@Preview(showBackground = true, name = "Recommendations — dark", heightDp = 1400)
@Composable
private fun RecommendationsScreenDarkPreview() {
    FocusFlowTheme(darkTheme = true) {
        RecommendationsScreen(results = sampleResults, onContinue = {})
    }
}

@Preview(showBackground = true, name = "Recommendations — empty")
@Composable
private fun RecommendationsEmptyPreview() {
    FocusFlowTheme {
        RecommendationsScreen(results = emptyList(), onContinue = {})
    }
}
