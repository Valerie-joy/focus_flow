package com.focusflow.domain.usecases

import com.focusflow.domain.models.AttentionCategory
import com.focusflow.domain.models.CategoryAssessmentResult
import com.focusflow.domain.models.GazeMetrics
import com.focusflow.domain.models.LearningStyle
import com.focusflow.domain.models.TraitCluster
import com.focusflow.domain.scoring.SensitivityAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Trait aggregation, learning-style inference and recommendation generation.
 * Scores are supplied directly so each test states exactly which categories
 * are strongest and nothing depends on the scoring pipeline.
 */
class GenerateRecommendationsUseCaseTest {

    private val useCase = GenerateRecommendationsUseCase()

    private fun scored(
        category: AttentionCategory,
        score: Int,
        successful: Boolean = true
    ) = CategoryAttentionScore(
        result = CategoryAssessmentResult(
            category = category,
            gazeMetrics = GazeMetrics(
                screenAttentionPercentage = score.toFloat(),
                gazeShiftCount = 0,
                firstDistractionMs = null,
                blinkCount = 17,
                actualDurationMs = 60_000L,
                analyzedFrameCount = 600
            ),
            successful = successful,
            interestRating = 4,
            focusRating = 4
        ),
        score = score,
        preciseScore = score.toDouble(),
        components = ScoreComponents(score, score, score, score, 75, 75)
    )

    /** Five successful assessments, so profiles are not provisional by default. */
    private fun fiveOf(vararg entries: Pair<AttentionCategory, Int>) =
        entries.map { (category, score) -> scored(category, score) }

    @Test
    fun `no results yields no profile`() {
        assertNull(useCase(emptyList()))
    }

    @Test
    fun `a clearly auditory profile infers the auditory style`() {
        val profile = useCase(
            fiveOf(
                AttentionCategory.MUSIC to 95,
                AttentionCategory.EDUCATION to 30,
                AttentionCategory.HORROR to 25,
                AttentionCategory.ROMANCE to 20,
                AttentionCategory.GAMING to 15
            )
        )
        assertNotNull(profile)
        assertEquals(LearningStyle.AUDITORY, profile!!.learningStyle)
        assertEquals(AttentionCategory.MUSIC, profile.bestPerformingCategories.first())
    }

    @Test
    fun `narrative categories dominating infer the narrative style`() {
        val profile = useCase(
            fiveOf(
                AttentionCategory.ROMANCE to 92,
                AttentionCategory.MELODRAMA to 88,
                AttentionCategory.SADNESS to 85,
                AttentionCategory.MUSIC to 20,
                AttentionCategory.GAMING to 15
            )
        )!!
        assertEquals(LearningStyle.NARRATIVE, profile.learningStyle)
        assertEquals(1.0, profile.clusterShares.getValue(TraitCluster.NARRATIVE), 1e-6)
    }

    @Test
    fun `education dominating infers the structured style`() {
        val profile = useCase(
            fiveOf(
                AttentionCategory.EDUCATION to 90,
                AttentionCategory.MUSIC to 20,
                AttentionCategory.GAMING to 18,
                AttentionCategory.HORROR to 15,
                AttentionCategory.ROMANCE to 10
            )
        )!!
        assertEquals(LearningStyle.STRUCTURED, profile.learningStyle)
    }

    @Test
    fun `attention spread evenly across three clusters infers multimodal`() {
        val profile = useCase(
            fiveOf(
                AttentionCategory.MUSIC to 80,      // auditory
                AttentionCategory.GAMING to 80,     // interactive
                AttentionCategory.CARTOON to 80,    // visual
                AttentionCategory.EDUCATION to 20,
                AttentionCategory.ROMANCE to 15
            )
        )!!
        assertEquals(LearningStyle.MULTIMODAL, profile.learningStyle)
        // Three-way even split: no cluster reaches the dominance threshold.
        assertEquals(3, profile.clusterShares.size)
    }

    @Test
    fun `a clear leader across three clusters is not forced to multimodal`() {
        val profile = useCase(
            fiveOf(
                AttentionCategory.MUSIC to 100,     // auditory
                AttentionCategory.GAMING to 20,     // interactive
                AttentionCategory.CARTOON to 15,    // visual
                AttentionCategory.EDUCATION to 10,
                AttentionCategory.ROMANCE to 5
            )
        )!!
        assertEquals(LearningStyle.AUDITORY, profile.learningStyle)
        assertTrue(profile.clusterShares.getValue(TraitCluster.AUDITORY) > 0.45)
    }

    @Test
    fun `a category spanning two clusters contributes weighted to both`() {
        // Science Fiction alone: visual + textual, half each.
        val profile = useCase(listOf(scored(AttentionCategory.SCIENCE_FICTION, 90)))!!
        assertEquals(2, profile.clusterShares.size)
        assertEquals(0.5, profile.clusterShares.getValue(TraitCluster.VISUAL), 1e-6)
        assertEquals(0.5, profile.clusterShares.getValue(TraitCluster.TEXTUAL), 1e-6)
    }

    @Test
    fun `cluster shares always sum to one`() {
        val profile = useCase(
            fiveOf(
                AttentionCategory.SCIENCE_FICTION to 70,
                AttentionCategory.MUSIC to 60,
                AttentionCategory.GAMING to 50,
                AttentionCategory.HORROR to 40,
                AttentionCategory.EDUCATION to 30
            )
        )!!
        assertEquals(1.0, profile.clusterShares.values.sum(), 1e-6)
    }

    @Test
    fun `profiles from fewer than five successful assessments are marked provisional`() {
        val provisional = useCase(
            listOf(
                scored(AttentionCategory.MUSIC, 90),
                scored(AttentionCategory.GAMING, 70),
                scored(AttentionCategory.HORROR, 40, successful = false)
            )
        )!!
        assertTrue(provisional.isProvisional)
    }

    @Test
    fun `a profile with exactly five successful assessments is not provisional`() {
        val profile = useCase(
            fiveOf(
                AttentionCategory.MUSIC to 90,
                AttentionCategory.GAMING to 80,
                AttentionCategory.CARTOON to 70,
                AttentionCategory.EDUCATION to 60,
                AttentionCategory.HORROR to 50
            )
        )!!
        assertFalse(profile.isProvisional)
    }

    @Test
    fun `failed sessions do not count toward the five successful assessments`() {
        val entries = listOf(
            scored(AttentionCategory.MUSIC, 90),
            scored(AttentionCategory.GAMING, 80),
            scored(AttentionCategory.CARTOON, 70),
            scored(AttentionCategory.EDUCATION, 60),
            scored(AttentionCategory.HORROR, 10, successful = false),
            scored(AttentionCategory.ROMANCE, 5, successful = false)
        )
        assertTrue(
            "Six results but only four successful — must remain provisional",
            useCase(entries)!!.isProvisional
        )
    }

    @Test
    fun `recommendations are populated and deterministic`() {
        val entries = fiveOf(
            AttentionCategory.MUSIC to 90,
            AttentionCategory.GAMING to 80,
            AttentionCategory.CARTOON to 70,
            AttentionCategory.EDUCATION to 60,
            AttentionCategory.HORROR to 50
        )
        val first = useCase(entries)!!
        val second = useCase(entries.reversed())!!

        assertTrue(first.suggestedStudyTechniques.isNotEmpty())
        assertTrue(first.recommendedContentTypes.isNotEmpty())
        assertTrue(first.weeklyGoals.isNotEmpty())

        // Same inputs in any order must give the same profile.
        assertEquals(first.learningStyle, second.learningStyle)
        assertEquals(first.suggestedStudyTechniques, second.suggestedStudyTechniques)
        assertEquals(first.weeklyGoals, second.weeklyGoals)
        assertEquals(first.bestPerformingCategories, second.bestPerformingCategories)
    }

    @Test
    fun `recommendations never make diagnostic claims`() {
        val profile = useCase(
            fiveOf(
                AttentionCategory.MUSIC to 90,
                AttentionCategory.GAMING to 80,
                AttentionCategory.CARTOON to 70,
                AttentionCategory.EDUCATION to 60,
                AttentionCategory.HORROR to 50
            )
        )!!
        val text = (
            profile.suggestedStudyTechniques +
                profile.recommendedContentTypes +
                profile.weeklyGoals +
                profile.learningStyle.description
            ).joinToString(" ").lowercase()

        listOf("adhd", "diagnos", "disorder", "symptom", "treatment", "prescri", "patient")
            .forEach { term ->
                assertFalse("Recommendation text must not contain '$term'", text.contains(term))
            }
    }

    @Test
    fun `attention strength is the mean of the top categories`() {
        val profile = useCase(
            fiveOf(
                AttentionCategory.MUSIC to 90,
                AttentionCategory.GAMING to 80,
                AttentionCategory.CARTOON to 70,
                AttentionCategory.EDUCATION to 10,
                AttentionCategory.HORROR to 10
            )
        )!!
        assertEquals(80, profile.attentionStrengthScore) // (90+80+70)/3
    }

    // --- Sensitivity ---

    @Test
    fun `a clear ranking survives reweighting`() {
        val results = listOf(
            CategoryAssessmentResult(
                AttentionCategory.MUSIC,
                GazeMetrics(98f, 0, null, 17, 60_000L, 600),
                successful = true, interestRating = 5, focusRating = 5
            ),
            CategoryAssessmentResult(
                AttentionCategory.HORROR,
                GazeMetrics(15f, 30, 500L, 60, 60_000L, 600),
                successful = true, interestRating = 1, focusRating = 1
            )
        )
        val report = SensitivityAnalysis.analyze(results)
        assertTrue("Top category flipped under reweighting", report.topCategoryIsStable)
        assertEquals(AttentionCategory.MUSIC, report.baseline.ranking.first())
        assertEquals(0, report.maxRankShift)
    }

    @Test
    fun `sensitivity analysis covers every configured weight vector`() {
        val results = listOf(
            CategoryAssessmentResult(
                AttentionCategory.MUSIC,
                GazeMetrics(80f, 2, 10_000L, 17, 60_000L, 600),
                successful = true, interestRating = 4, focusRating = 4
            )
        )
        val report = SensitivityAnalysis.analyze(results)
        assertEquals(4, report.alternatives.size) // five vectors, minus the baseline
        assertTrue(report.fullRankingIsStable) // single category cannot reorder
    }
}
