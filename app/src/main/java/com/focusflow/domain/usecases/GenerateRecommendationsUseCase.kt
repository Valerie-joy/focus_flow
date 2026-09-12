package com.focusflow.domain.usecases

import com.focusflow.domain.models.AttentionCategory
import com.focusflow.domain.models.LearningStyle
import com.focusflow.domain.models.TraitCluster
import com.focusflow.domain.models.traitClusterWeights

/**
 * A generated profile. All text is plain strings — the results screen maps
 * them to icons locally rather than the domain layer knowing about UI.
 */
data class RecommendationProfile(
    val learningStyle: LearningStyle,
    val bestPerformingCategories: List<AttentionCategory>,
    val attentionStrengthScore: Int, // 0..100, mean of the best-performing categories
    val suggestedStudyTechniques: List<String>,
    val recommendedContentTypes: List<String>,
    val weeklyGoals: List<String>,
    /** Cluster totals behind [learningStyle], normalized to shares summing to 1.0. */
    val clusterShares: Map<TraitCluster, Double> = emptyMap(),
    /**
     * True when the profile rests on fewer than the five successful
     * assessments the PID requires, so the UI and report can mark it
     * provisional rather than presenting it as a settled result.
     */
    val isProvisional: Boolean = false
)

/**
 * Turns scored category results into a personalized profile.
 *
 * Deterministic and fully offline by design: the same inputs always give the
 * same profile, and every step is inspectable. No model call is involved —
 * the PID frames this as an assistive self-reflection tool, and a
 * recommendation a user cannot interrogate would not serve that.
 *
 * Learning style comes from tallying trait *clusters* across the top-ranked
 * categories, weighted both by each category's score and by how much of that
 * category belongs to each cluster (see
 * [com.focusflow.domain.models.traitClusterWeights]) — never by mapping one
 * category directly onto one style.
 *
 * Nothing produced here is diagnostic. The output describes content
 * preference and study habits; it does not screen for, classify, or indicate
 * ADHD or any other condition.
 */
class GenerateRecommendationsUseCase(
    private val topCategoryCount: Int = 3,
    private val requiredSuccessfulAssessments: Int = 5,
    /**
     * A single cluster must hold at least this share of the weighted total to
     * be called dominant; otherwise attention is genuinely spread and the
     * honest answer is multimodal. 0.45 sits just under an even two-way split
     * (0.5) so a clear leader between two clusters still wins, while a
     * three-way spread does not.
     */
    private val dominanceThreshold: Double = 0.45
) {
    operator fun invoke(scored: List<CategoryAttentionScore>): RecommendationProfile? {
        if (scored.isEmpty()) return null

        val ranked = scored.sortedWith(
            compareByDescending<CategoryAttentionScore> { it.preciseScore }
                .thenBy { it.result.category.ordinal }
        )
        val top = ranked.take(topCategoryCount)

        // Weighted cluster tally: a category contributes its score to each of
        // its clusters in proportion to how much of it belongs there, so
        // Science Fiction (visual + textual) splits its contribution rather
        // than being forced whole into one.
        val clusterTotals = mutableMapOf<TraitCluster, Double>()
        top.forEach { entry ->
            traitClusterWeights(entry.result.category).forEach { (cluster, share) ->
                clusterTotals[cluster] = (clusterTotals[cluster] ?: 0.0) + entry.preciseScore * share
            }
        }

        val total = clusterTotals.values.sum()
        val shares = if (total > 0.0) clusterTotals.mapValues { it.value / total } else emptyMap()

        val ordered = shares.entries.sortedWith(
            compareByDescending<Map.Entry<TraitCluster, Double>> { it.value }
                .thenBy { it.key.ordinal } // deterministic tie-break
        )
        val leader = ordered.firstOrNull()

        val learningStyle = when {
            leader == null -> LearningStyle.MULTIMODAL
            // Attention spread across three or more clusters with no clear
            // leader is genuinely multimodal, not a weak version of one style.
            ordered.size >= 3 && leader.value < dominanceThreshold -> LearningStyle.MULTIMODAL
            else -> mapClusterToStyle(leader.key)
        }

        val successfulCount = scored.count { it.result.successful }

        return RecommendationProfile(
            learningStyle = learningStyle,
            bestPerformingCategories = top.map { it.result.category },
            attentionStrengthScore = top.map { it.score }.average().let { Math.round(it).toInt() },
            suggestedStudyTechniques = studyTechniquesFor(learningStyle),
            recommendedContentTypes = contentTypesFor(ordered.map { it.key }),
            weeklyGoals = weeklyGoalsFor(ranked, learningStyle),
            clusterShares = shares,
            isProvisional = successfulCount < requiredSuccessfulAssessments
        )
    }

    private fun mapClusterToStyle(cluster: TraitCluster): LearningStyle = when (cluster) {
        TraitCluster.AUDITORY -> LearningStyle.AUDITORY
        TraitCluster.VISUAL -> LearningStyle.VISUAL
        TraitCluster.INTERACTIVE -> LearningStyle.INTERACTIVE
        TraitCluster.NARRATIVE -> LearningStyle.NARRATIVE
        TraitCluster.TEXTUAL -> LearningStyle.STRUCTURED
    }

    private fun studyTechniquesFor(style: LearningStyle): List<String> = when (style) {
        LearningStyle.AUDITORY -> listOf(
            "Play background music while studying",
            "Record yourself explaining a concept and listen back",
            "Use podcasts or audiobooks for review"
        )
        LearningStyle.VISUAL -> listOf(
            "Use diagrams, color-coding, and mind maps",
            "Break material into short visual segments",
            "Watch short explainer videos instead of reading long text"
        )
        LearningStyle.INTERACTIVE -> listOf(
            "Try gamified learning apps or quizzes",
            "Use hands-on practice problems instead of passive review",
            "Turn review sessions into a challenge or game"
        )
        LearningStyle.NARRATIVE -> listOf(
            "Frame new material as a story with characters and stakes",
            "Connect concepts to real-world examples",
            "Study with case studies rather than abstract rules"
        )
        LearningStyle.STRUCTURED -> listOf(
            "Use outlines and numbered steps",
            "Study in a consistent, distraction-free setting",
            "Break material into clearly labeled sections"
        )
        LearningStyle.MULTIMODAL -> listOf(
            "Use music while studying",
            "Try gamified learning tools",
            "Keep sessions short and interactive",
            "Mix visual and hands-on activities"
        )
    }

    private fun contentTypesFor(clusters: List<TraitCluster>): List<String> {
        if (clusters.isEmpty()) return listOf("Short, interactive content", "Visual & hands-on activities")
        return clusters.map { cluster ->
            when (cluster) {
                TraitCluster.AUDITORY -> "Audio & music-based content"
                TraitCluster.VISUAL -> "Short, visual content"
                TraitCluster.INTERACTIVE -> "Gamified & interactive tools"
                TraitCluster.NARRATIVE -> "Story-driven content"
                TraitCluster.TEXTUAL -> "Structured, text-based content"
            }
        }
    }

    private fun weeklyGoalsFor(
        ranked: List<CategoryAttentionScore>,
        style: LearningStyle
    ): List<String> {
        val goals = mutableListOf(
            "Complete 3 focused study sessions of 20 minutes this week using " +
                "${style.displayName.lowercase()}-friendly formats"
        )
        ranked.lastOrNull()?.let { weakest ->
            goals += "Spend 10-15 minutes this week easing into " +
                "${weakest.result.category.displayName.lowercase()}-adjacent material " +
                "to build focus stamina in a lower-scoring area"
        }
        ranked.firstOrNull()?.let { strongest ->
            goals += "Use ${strongest.result.category.displayName.lowercase()}-style material " +
                "to introduce one topic you have been putting off"
        }
        return goals
    }
}
