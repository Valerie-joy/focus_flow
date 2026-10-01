package com.focusflow.domain.usecases

import com.focusflow.domain.models.AttentionCategory
import com.focusflow.domain.models.LearningStyle
import com.focusflow.domain.models.TraitCluster
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the recommendation rationale.
 *
 * The behaviours pinned here are the ones with a duty of care: that the text
 * explains itself from real model values, that it never invents a comparison
 * against other people, and that it never drifts into diagnostic phrasing.
 */
class ExplainRecommendationsTest {

    private fun profile(
        style: LearningStyle = LearningStyle.AUDITORY,
        categories: List<AttentionCategory> = listOf(
            AttentionCategory.MUSIC,
            AttentionCategory.GAMING,
            AttentionCategory.CARTOON
        ),
        shares: Map<TraitCluster, Double> = mapOf(
            TraitCluster.AUDITORY to 0.55,
            TraitCluster.INTERACTIVE to 0.30,
            TraitCluster.VISUAL to 0.15
        ),
        provisional: Boolean = false
    ) = RecommendationProfile(
        learningStyle = style,
        bestPerformingCategories = categories,
        attentionStrengthScore = 87,
        suggestedStudyTechniques = listOf("Play background music while studying"),
        recommendedContentTypes = listOf("Audio & music-based content"),
        weeklyGoals = listOf("Complete 3 focused study sessions"),
        clusterShares = shares,
        isProvisional = provisional
    )

    @Test
    fun `basis names the categories the profile was actually built from`() {
        val text = ExplainRecommendations.basis(profile())

        assertTrue(text.contains("Music"))
        assertTrue(text.contains("Gaming"))
        assertTrue(text.contains("Cartoon"))
        // Reads as a list, not a dump.
        assertTrue(text.contains(" and "))
    }

    @Test
    fun `basis reads correctly with one and two categories`() {
        val one = ExplainRecommendations.basis(
            profile(categories = listOf(AttentionCategory.MUSIC))
        )
        assertTrue(one.contains("Music"))
        assertFalse("no dangling conjunction for a single item", one.contains(" and Music"))

        val two = ExplainRecommendations.basis(
            profile(categories = listOf(AttentionCategory.MUSIC, AttentionCategory.GAMING))
        )
        assertTrue(two.contains("Music and Gaming"))
    }

    @Test
    fun `basis falls back gracefully with no categories`() {
        val text = ExplainRecommendations.basis(profile(categories = emptyList()))
        assertTrue(text.isNotBlank())
        assertFalse(text.contains("null"))
    }

    @Test
    fun `cluster breakdown reports real shares and drops noise`() {
        val text = ExplainRecommendations.clusterBreakdown(
            profile(
                shares = mapOf(
                    TraitCluster.AUDITORY to 0.60,
                    TraitCluster.INTERACTIVE to 0.38,
                    // Below the reportable floor: naming a 2% share would imply
                    // a precision the tally doesn't have.
                    TraitCluster.TEXTUAL to 0.02
                )
            )
        )
        assertNotNull(text)
        assertTrue(text!!.contains("60%"))
        assertTrue(text.contains("38%"))
        assertFalse(text.contains("2%"))
    }

    @Test
    fun `cluster breakdown is null when there is nothing to report`() {
        assertNull(ExplainRecommendations.clusterBreakdown(profile(shares = emptyMap())))
    }

    /**
     * Mirrors the engine's own multimodal branch: three or more clusters with
     * no leader above the dominance threshold.
     */
    @Test
    fun `style rationale explains a genuine spread rather than naming a winner`() {
        val text = ExplainRecommendations.styleRationale(
            profile(
                style = LearningStyle.MULTIMODAL,
                shares = mapOf(
                    TraitCluster.AUDITORY to 0.35,
                    TraitCluster.VISUAL to 0.34,
                    TraitCluster.INTERACTIVE to 0.31
                )
            )
        )
        assertTrue(text.contains("spread"))
        assertTrue(text.contains("no single one leading"))
    }

    @Test
    fun `style rationale names the leading cluster when one dominates`() {
        val text = ExplainRecommendations.styleRationale(
            profile(shares = mapOf(TraitCluster.AUDITORY to 0.80, TraitCluster.VISUAL to 0.20))
        )
        assertTrue(text.contains("80%"))
        assertTrue(text.contains("Sound-led", ignoreCase = true))
    }

    @Test
    fun `provisional notice appears only for a partial session`() {
        assertNotNull(ExplainRecommendations.provisionalNotice(profile(provisional = true)))
        assertNull(ExplainRecommendations.provisionalNotice(profile(provisional = false)))
    }

    /**
     * The app holds no reference data, so any comparison against other people
     * would be fabricated. This guards the whole surface at once.
     */
    @Test
    fun `no explanation implies a population comparison or a diagnosis`() {
        val p = profile()
        val everything = listOf(
            ExplainRecommendations.basis(p),
            ExplainRecommendations.styleRationale(p),
            ExplainRecommendations.clusterBreakdown(p).orEmpty(),
            ExplainRecommendations.provisionalNotice(profile(provisional = true)).orEmpty(),
            ExplainRecommendations.NOT_ADVICE
        ).joinToString(" ")

        listOf(
            "percentile", "average person", "compared to others", "population",
            "above average", "below average", "normal range",
            "ADHD", "diagnos", "disorder", "deficit", "symptom"
        ).forEach { term ->
            assertFalse(
                "explanation must not contain '$term'",
                everything.contains(term, ignoreCase = true)
            )
        }
    }

    @Test
    fun `the closing note states these are not advice`() {
        val text = ExplainRecommendations.NOT_ADVICE
        assertTrue(text.contains("not", ignoreCase = true))
        assertTrue(text.contains("medical", ignoreCase = true))
        // Says what it describes, so "preference, not ability" is explicit.
        assertTrue(text.contains("not ability", ignoreCase = true))
    }

    private fun String.contains(other: String, ignoreCase: Boolean) =
        indexOf(other, ignoreCase = ignoreCase) >= 0
}
