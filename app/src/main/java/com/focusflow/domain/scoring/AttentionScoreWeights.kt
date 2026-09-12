package com.focusflow.domain.scoring

/** The six components the PID names as inputs to the 0-100 category score. */
enum class ScoreComponent(val displayName: String) {
    SCREEN_ATTENTION("Screen attention"),
    DISTRACTION_DELAY("Distraction delay"),
    GAZE_STABILITY("Gaze stability"),
    BLINK_CONSISTENCY("Blink consistency"),
    SELF_REPORT_INTEREST("Self-reported interest"),
    SELF_REPORT_FOCUS("Self-reported focus");

    val isSelfReported: Boolean
        get() = this == SELF_REPORT_INTEREST || this == SELF_REPORT_FOCUS

    val isGazeBased: Boolean get() = !isSelfReported
}

/**
 * The weight vector for the per-category attention score.
 *
 * **On where these numbers come from — read before citing them.** The approved
 * PID specifies the *structure* of the score (a documented weighted combination
 * of these six components, on a 0-100 scale, with the self-report weight
 * redistributed proportionally across the gaze-based components when ratings
 * are unavailable) but it deliberately does not fix numeric values. It records
 * the opposite, in fact, under Constraints: "In the absence of an external
 * criterion measure of attention, the attention-score component weights are a
 * stated design prior rather than empirically calibrated values."
 *
 * So [DEFAULT] is not a specification-mandated vector and must not be presented
 * as one. It is the weighting this project has been using, preserved verbatim
 * from the original CalculateAttentionScoreUseCase, with the reasoning it was
 * chosen for:
 *
 *  - Screen attention is the most direct evidence a person was actually
 *    engaged, so it carries the largest single weight.
 *  - Distraction delay and gaze stability are both proxies for *sustained*
 *    engagement, weighted equally and moderately.
 *  - Blink consistency is the weakest and most indirect signal, so it carries
 *    the smallest gaze-based weight.
 *  - The two self-reports together carry the same weight as blink consistency
 *    plus a little: they are meaningful but subjective, and they are absent
 *    entirely for unsuccessful sessions.
 *
 * Because they are a prior rather than a measurement, the honest thing to do is
 * make them easy to change and to check the conclusions against alternatives —
 * hence [SensitivityAnalysis], which answers the question the PID's objectives
 * actually ask: does the category *ranking* survive plausible reweighting?
 */
data class AttentionScoreWeights(
    val weights: Map<ScoreComponent, Double>
) {
    init {
        require(weights.keys.containsAll(ScoreComponent.entries)) {
            "A weight vector must assign every score component; missing: " +
                ScoreComponent.entries.filterNot { it in weights.keys }
        }
        require(weights.values.all { it >= 0.0 }) { "Weights must be non-negative." }
        require(kotlin.math.abs(weights.values.sum() - 1.0) < 1e-6) {
            "Weights must sum to 1.0 but summed to ${weights.values.sum()}."
        }
    }

    operator fun get(component: ScoreComponent): Double = weights.getValue(component)

    /**
     * Redistributes the weight of any missing self-report *proportionally*
     * across the gaze-based components, per the PID.
     *
     * Proportionally is the operative word. Setting a missing component's
     * weight to zero and leaving it there would silently rescale the whole
     * score — a session with no ratings would be scored out of 85 rather than
     * 100 and would look worse than an identical session that had them, which
     * would punish unsuccessful sessions twice for the same signal. After this
     * call the weights still sum to 1.0.
     */
    fun redistributedFor(availableSelfReports: Set<ScoreComponent>): AttentionScoreWeights {
        val missing = ScoreComponent.entries.filter { it.isSelfReported && it !in availableSelfReports }
        if (missing.isEmpty()) return this

        val freed = missing.sumOf { this[it] }
        val gazeComponents = ScoreComponent.entries.filter { it.isGazeBased }
        val gazeTotal = gazeComponents.sumOf { this[it] }
        // Degenerate vector (all weight on self-reports): fall back to an even
        // split rather than dividing by zero.
        if (gazeTotal <= 0.0) {
            return AttentionScoreWeights(
                ScoreComponent.entries.associateWith {
                    if (it.isGazeBased) 1.0 / gazeComponents.size else 0.0
                }
            )
        }

        return AttentionScoreWeights(
            ScoreComponent.entries.associateWith { component ->
                when {
                    component in missing -> 0.0
                    component.isGazeBased -> this[component] + freed * (this[component] / gazeTotal)
                    else -> this[component]
                }
            }
        )
    }

    companion object {
        /** The project's long-standing weighting. A design prior — see the class docs. */
        val DEFAULT = AttentionScoreWeights(
            mapOf(
                ScoreComponent.SCREEN_ATTENTION to 0.40,
                ScoreComponent.DISTRACTION_DELAY to 0.20,
                ScoreComponent.GAZE_STABILITY to 0.20,
                ScoreComponent.BLINK_CONSISTENCY to 0.05,
                ScoreComponent.SELF_REPORT_INTEREST to 0.075,
                ScoreComponent.SELF_REPORT_FOCUS to 0.075
            )
        )

        /**
         * Alternative plausible vectors for the sensitivity analysis the PID's
         * objectives call for. Each is a defensible reading of the same design
         * argument, not a random perturbation — if the ranking holds across all
         * of them, it is not an artefact of [DEFAULT]'s exact numbers.
         */
        val SENSITIVITY_VECTORS: Map<String, AttentionScoreWeights> = mapOf(
            "default" to DEFAULT,
            // Trusts the single most direct signal much more heavily.
            "screen-attention-dominant" to AttentionScoreWeights(
                mapOf(
                    ScoreComponent.SCREEN_ATTENTION to 0.60,
                    ScoreComponent.DISTRACTION_DELAY to 0.15,
                    ScoreComponent.GAZE_STABILITY to 0.10,
                    ScoreComponent.BLINK_CONSISTENCY to 0.05,
                    ScoreComponent.SELF_REPORT_INTEREST to 0.05,
                    ScoreComponent.SELF_REPORT_FOCUS to 0.05
                )
            ),
            // Treats every component as equally informative — the maximally
            // agnostic prior, and the strongest test of the ranking.
            "uniform" to AttentionScoreWeights(
                ScoreComponent.entries.associateWith { 1.0 / ScoreComponent.entries.size }
            ),
            // Weights what the user says about the clip far more heavily.
            "self-report-heavy" to AttentionScoreWeights(
                mapOf(
                    ScoreComponent.SCREEN_ATTENTION to 0.25,
                    ScoreComponent.DISTRACTION_DELAY to 0.15,
                    ScoreComponent.GAZE_STABILITY to 0.10,
                    ScoreComponent.BLINK_CONSISTENCY to 0.05,
                    ScoreComponent.SELF_REPORT_INTEREST to 0.225,
                    ScoreComponent.SELF_REPORT_FOCUS to 0.225
                )
            ),
            // Drops the weakest signal entirely, redistributing across the rest.
            "no-blink" to AttentionScoreWeights(
                mapOf(
                    ScoreComponent.SCREEN_ATTENTION to 0.42,
                    ScoreComponent.DISTRACTION_DELAY to 0.21,
                    ScoreComponent.GAZE_STABILITY to 0.21,
                    ScoreComponent.BLINK_CONSISTENCY to 0.00,
                    ScoreComponent.SELF_REPORT_INTEREST to 0.08,
                    ScoreComponent.SELF_REPORT_FOCUS to 0.08
                )
            )
        )
    }
}
