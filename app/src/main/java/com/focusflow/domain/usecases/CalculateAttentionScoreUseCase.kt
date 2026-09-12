package com.focusflow.domain.usecases

import com.focusflow.domain.models.CategoryAssessmentResult
import com.focusflow.domain.scoring.AttentionScoreWeights
import com.focusflow.domain.scoring.ScoreComponent
import kotlin.math.roundToInt

/**
 * A single category's result reduced to one 0-100 attention score, plus the
 * component scores that fed it, so the number stays explainable.
 */
data class CategoryAttentionScore(
    val result: CategoryAssessmentResult,
    /** 0..100, rounded — what the UI and persistence use. */
    val score: Int,
    /** Unrounded 0.0..100.0, for ranking and sensitivity comparisons. */
    val preciseScore: Double,
    val components: ScoreComponents
)

data class ScoreComponents(
    val screenAttention: Int,
    val distractionDelay: Int,
    val gazeStability: Int,
    val blinkConsistency: Int,
    val interest: Int?,
    val focus: Int?
)

/**
 * Computes the per-category attention score the PID specifies: a documented
 * weighted combination of screen attention, distraction delay, gaze stability,
 * blink consistency and two post-clip self-reported ratings, on a 0-100 scale.
 *
 * Each component is first normalized to its own 0-100 sub-score, then combined
 * under [weights]. Because every sub-score is already bounded to 0..100 and the
 * weights always sum to 1.0 (including after redistribution), the combination
 * is mathematically bounded to 0..100 as well — the final clamp is a guard
 * against a future component forgetting to normalize, not load-bearing.
 *
 * The weight vector itself lives in [AttentionScoreWeights] and is a stated
 * design prior, not a specification-mandated or empirically calibrated set of
 * numbers; see that class for the full caveat.
 */
class CalculateAttentionScoreUseCase(
    private val weights: AttentionScoreWeights = AttentionScoreWeights.DEFAULT,
    /** Fallback only, for old rows where GazeMetrics.actualDurationMs wasn't captured. */
    private val assumedVideoDurationMs: Long = 45_000L,
    private val idealBlinksPerMinute: Float = 17f
) {

    operator fun invoke(result: CategoryAssessmentResult): CategoryAttentionScore {
        val metrics = result.gazeMetrics
        val effectiveDurationMs =
            if (metrics.actualDurationMs > 0) metrics.actualDurationMs else assumedVideoDurationMs

        val screenAttention = metrics.screenAttentionPercentage.roundToInt().coerceIn(0, 100)

        // How long the user lasted before the first *sustained* lapse, as a
        // proportion of the time actually watched. Never lapsing scores 100.
        val distractionDelay = when (val ms = metrics.firstDistractionMs) {
            null -> 100
            else -> ((ms.toFloat() / effectiveDurationMs) * 100f).roundToInt().coerceIn(0, 100)
        }

        // Fewer look-away/look-back transitions = steadier attention.
        // 0 shifts -> 100, 20 or more -> 0.
        val gazeStability = (100 - (metrics.gazeShiftCount * 5)).coerceIn(0, 100)

        // Distance from a relaxed resting blink rate over the watched duration.
        // A soft signal, not a clinical measure — hence its small weight.
        val minutes = effectiveDurationMs / 60_000f
        val observedBlinksPerMinute = if (minutes > 0) metrics.blinkCount / minutes else 0f
        val blinkDeviation = kotlin.math.abs(observedBlinksPerMinute - idealBlinksPerMinute)
        val blinkConsistency = (100 - (blinkDeviation * 4)).roundToInt().coerceIn(0, 100)

        // 1..5 Likert -> 0..100.
        val interest = result.interestRating?.let { ((it - 1) / 4f * 100f).roundToInt().coerceIn(0, 100) }
        val focus = result.focusRating?.let { ((it - 1) / 4f * 100f).roundToInt().coerceIn(0, 100) }

        val available = buildSet {
            if (interest != null) add(ScoreComponent.SELF_REPORT_INTEREST)
            if (focus != null) add(ScoreComponent.SELF_REPORT_FOCUS)
        }
        val effectiveWeights = weights.redistributedFor(available)

        val subScores = mapOf(
            ScoreComponent.SCREEN_ATTENTION to screenAttention,
            ScoreComponent.DISTRACTION_DELAY to distractionDelay,
            ScoreComponent.GAZE_STABILITY to gazeStability,
            ScoreComponent.BLINK_CONSISTENCY to blinkConsistency,
            ScoreComponent.SELF_REPORT_INTEREST to (interest ?: 0),
            ScoreComponent.SELF_REPORT_FOCUS to (focus ?: 0)
        )

        val precise = subScores.entries
            .sumOf { (component, value) -> value * effectiveWeights[component] }
            .coerceIn(0.0, 100.0)

        return CategoryAttentionScore(
            result = result,
            score = precise.roundToInt().coerceIn(0, 100),
            preciseScore = precise,
            components = ScoreComponents(
                screenAttention = screenAttention,
                distractionDelay = distractionDelay,
                gazeStability = gazeStability,
                blinkConsistency = blinkConsistency,
                interest = interest,
                focus = focus
            )
        )
    }

    /**
     * Scores every result, best first. Ties break on the canonical category
     * order rather than input order, so ranking is deterministic for identical
     * scores instead of depending on which category happened to be presented
     * first in a randomized session.
     */
    fun scoreAll(results: List<CategoryAssessmentResult>): List<CategoryAttentionScore> =
        results.map { invoke(it) }
            .sortedWith(
                compareByDescending<CategoryAttentionScore> { it.preciseScore }
                    .thenBy { it.result.category.ordinal }
            )
}
