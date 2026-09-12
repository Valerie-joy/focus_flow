package com.focusflow.domain.scoring

import com.focusflow.domain.models.AttentionCategory
import com.focusflow.domain.models.CategoryAssessmentResult
import com.focusflow.domain.usecases.CalculateAttentionScoreUseCase

data class RankingUnderWeights(
    val vectorName: String,
    val ranking: List<AttentionCategory>,
    val scores: Map<AttentionCategory, Double>
)

data class SensitivityReport(
    val baseline: RankingUnderWeights,
    val alternatives: List<RankingUnderWeights>
) {
    /** True when the top-ranked category is the same under every weight vector. */
    val topCategoryIsStable: Boolean
        get() = alternatives.all { it.ranking.firstOrNull() == baseline.ranking.firstOrNull() }

    /** True when the full ordering is identical under every weight vector. */
    val fullRankingIsStable: Boolean
        get() = alternatives.all { it.ranking == baseline.ranking }

    /**
     * Largest number of positions any single category moves relative to the
     * baseline ranking. 0 means every vector agrees exactly.
     */
    val maxRankShift: Int
        get() = alternatives.maxOfOrNull { alternative ->
            baseline.ranking.withIndex().maxOfOrNull { (baselineIndex, category) ->
                val altIndex = alternative.ranking.indexOf(category)
                if (altIndex < 0) 0 else kotlin.math.abs(altIndex - baselineIndex)
            } ?: 0
        } ?: 0

    fun summary(): String = buildString {
        appendLine("Sensitivity over ${alternatives.size + 1} weight vectors")
        appendLine("Baseline ranking: ${baseline.ranking.joinToString { it.displayName }}")
        appendLine("Top category stable: $topCategoryIsStable")
        appendLine("Full ranking stable: $fullRankingIsStable")
        appendLine("Max rank shift: $maxRankShift")
    }
}

/**
 * Re-scores one session under several plausible weight vectors and reports
 * whether the category ranking survives.
 *
 * This exists because of what the PID concedes: the component weights are a
 * design prior, not calibrated against any external criterion measure. A
 * ranking that flips when the weights are nudged would be an artefact of those
 * arbitrary numbers rather than a finding about the user, and the PID's
 * objectives accordingly ask for "an accompanying sensitivity analysis over
 * plausible weight vectors" and for evaluating "the stability of category
 * rankings under varied score weightings".
 *
 * Nothing here changes what the user is shown — [AttentionScoreWeights.DEFAULT]
 * remains the scoring vector. This is an evaluation instrument, and the honest
 * way to report a ranking is alongside whether it held up.
 */
object SensitivityAnalysis {

    fun analyze(
        results: List<CategoryAssessmentResult>,
        vectors: Map<String, AttentionScoreWeights> = AttentionScoreWeights.SENSITIVITY_VECTORS,
        baselineName: String = "default"
    ): SensitivityReport {
        fun rank(name: String, weights: AttentionScoreWeights): RankingUnderWeights {
            val scored = CalculateAttentionScoreUseCase(weights = weights).scoreAll(results)
            return RankingUnderWeights(
                vectorName = name,
                ranking = scored.map { it.result.category },
                scores = scored.associate { it.result.category to it.preciseScore }
            )
        }

        val baselineWeights = vectors[baselineName] ?: AttentionScoreWeights.DEFAULT
        return SensitivityReport(
            baseline = rank(baselineName, baselineWeights),
            alternatives = vectors
                .filterKeys { it != baselineName }
                .map { (name, weights) -> rank(name, weights) }
        )
    }
}
