package com.focusflow.domain.usecases

import com.focusflow.domain.models.TraitCluster
import kotlin.math.roundToInt

/**
 * Explains *why* a recommendation profile came out the way it did.
 *
 * [RecommendationProfile] already carries everything needed for this —
 * `bestPerformingCategories`, `clusterShares` and `isProvisional` — and none of
 * it reached the screen. The recommendations page showed a learning style, a
 * list of techniques and a bare "Attention strength score: 78" with no
 * indication of where any of it came from, which makes a deterministic,
 * inspectable model look like an opaque verdict. That is the opposite of what
 * a self-reflection tool should feel like.
 *
 * Nothing is invented here. Every sentence is assembled from values the
 * recommendation engine already computed. In particular there are no
 * percentiles, no population comparisons and no normative claims — the app has
 * no reference data, so any such statement would be fabricated.
 */
object ExplainRecommendations {

    /**
     * The one-sentence basis for the whole profile: which categories drove it.
     *
     * Uses the real `bestPerformingCategories` list, which is the top-N the
     * engine actually tallied clusters from.
     */
    fun basis(profile: RecommendationProfile): String {
        val names = profile.bestPerformingCategories.map { it.displayName }
        val categoryPhrase = when (names.size) {
            0 -> return "Based on the categories you completed in this session."
            1 -> names[0]
            2 -> "${names[0]} and ${names[1]}"
            else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
        }
        return "Your gaze stayed steadiest on $categoryPhrase. These suggestions follow " +
            "from what those clips have in common."
    }

    /**
     * How the attention held up across clusters, as a short readable list —
     * "mostly auditory, some interactive".
     *
     * Only clusters holding a meaningful share are named, so a rounding
     * artefact at 2% doesn't get a mention it hasn't earned.
     */
    fun clusterBreakdown(profile: RecommendationProfile): String? {
        val meaningful = profile.clusterShares
            .filterValues { it >= MIN_REPORTABLE_SHARE }
            .entries
            .sortedByDescending { it.value }
        if (meaningful.isEmpty()) return null

        return meaningful.joinToString(", ") { (cluster, share) ->
            "${label(cluster)} ${(share * 100).roundToInt()}%"
        }
    }

    /**
     * Why *this* learning style, phrased against the real dominance rule in
     * [GenerateRecommendationsUseCase] rather than as an assertion about the
     * person.
     */
    fun styleRationale(profile: RecommendationProfile): String {
        val top = profile.clusterShares.maxByOrNull { it.value }
            ?: return "Not enough spread across categories to lean one way."
        val share = (top.value * 100).roundToInt()
        val spreadAcross = profile.clusterShares.count { it.value >= MIN_REPORTABLE_SHARE }

        return if (spreadAcross >= 3 && top.value < 0.45) {
            // Mirrors the engine's own multimodal branch.
            "Your attention was spread fairly evenly across $spreadAcross kinds of content, " +
                "with no single one leading — so no one format is being singled out."
        } else {
            "${label(top.key).replaceFirstChar { it.uppercase() }} content accounted for " +
                "about $share% of where your attention held, which is what this leans on."
        }
    }

    /**
     * The caveat for a profile built on fewer than the required assessments.
     * Null once the profile is complete.
     */
    fun provisionalNotice(profile: RecommendationProfile): String? =
        if (profile.isProvisional) {
            "These are early suggestions from a partial session. Completing a full " +
                "assessment will sharpen them."
        } else {
            null
        }

    /**
     * A closing note on what these suggestions are.
     *
     * Study suggestions derived from which clips held someone's gaze are not
     * educational assessment and not medical advice, and the app should say so
     * rather than leaving the reader to assume otherwise.
     */
    const val NOT_ADVICE: String =
        "These are study suggestions based on which clips held your attention during one " +
            "session. They describe content preference, not ability, and they are not " +
            "medical or educational advice."

    private fun label(cluster: TraitCluster): String = when (cluster) {
        TraitCluster.AUDITORY -> "sound-led"
        TraitCluster.VISUAL -> "visual"
        TraitCluster.INTERACTIVE -> "interactive"
        TraitCluster.NARRATIVE -> "story-led"
        TraitCluster.TEXTUAL -> "structured"
    }

    /** Below this share a cluster is noise rather than a signal worth naming. */
    private const val MIN_REPORTABLE_SHARE = 0.08
}
