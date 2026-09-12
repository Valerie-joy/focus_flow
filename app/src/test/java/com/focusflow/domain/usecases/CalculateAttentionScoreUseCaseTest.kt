package com.focusflow.domain.usecases

import com.focusflow.domain.models.AttentionCategory
import com.focusflow.domain.models.CategoryAssessmentResult
import com.focusflow.domain.models.GazeMetrics
import com.focusflow.domain.scoring.AttentionScoreWeights
import com.focusflow.domain.scoring.ScoreComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Attention-score calculation, using fixed inputs throughout so every
 * expected value can be derived by hand from the documented weighting.
 */
class CalculateAttentionScoreUseCaseTest {

    private val useCase = CalculateAttentionScoreUseCase()

    private fun result(
        category: AttentionCategory = AttentionCategory.MUSIC,
        screenAttention: Float = 100f,
        gazeShifts: Int = 0,
        firstDistractionMs: Long? = null,
        blinkCount: Int = 0,
        durationMs: Long = 60_000L,
        interest: Int? = 5,
        focus: Int? = 5,
        successful: Boolean = true,
        skipped: Boolean = false,
        analyzedFrames: Int = 600
    ) = CategoryAssessmentResult(
        category = category,
        gazeMetrics = GazeMetrics(
            screenAttentionPercentage = screenAttention,
            gazeShiftCount = gazeShifts,
            firstDistractionMs = firstDistractionMs,
            blinkCount = blinkCount,
            actualDurationMs = durationMs,
            analyzedFrameCount = analyzedFrames,
            analysisFrameRate = 10f
        ),
        successful = successful,
        interestRating = interest,
        focusRating = focus,
        skipped = skipped
    )

    // --- Bounds ---

    @Test
    fun `a perfect session scores 100`() {
        // 100% screen attention, no lapse, no shifts, 17 blinks in one minute
        // (the assumed resting rate), both ratings at 5 -> every component 100.
        val score = useCase(result(blinkCount = 17))
        assertEquals(100, score.score)
        assertEquals(100.0, score.preciseScore, 1e-6)
    }

    @Test
    fun `a worst-case session scores 0 and never goes negative`() {
        // 42 blinks in a minute is 25/min away from the 17/min resting rate,
        // which is exactly what drives blink consistency to 0. Note that a
        // *zero*-blink minute is only 17 away and so still scores 32 — not
        // blinking at all is less anomalous than blinking constantly.
        val score = useCase(
            result(
                screenAttention = 0f,
                gazeShifts = 100,
                firstDistractionMs = 0L,
                blinkCount = 42,
                durationMs = 60_000L,
                interest = 1,
                focus = 1
            )
        )
        assertEquals(0, score.score)
        assertEquals(0, score.components.blinkConsistency)
        assertTrue(score.preciseScore >= 0.0)
    }

    @Test
    fun `an early ratio-driven lapse is not scored as if attention never dropped`() {
        // Regression guard: a clip cut short for rapid look-away/look-back used
        // to leave firstDistractionMs null, which reads as "never lapsed" and
        // scored a perfect 100 for distraction delay.
        val lapsed = useCase(result(firstDistractionMs = 2_000L, durationMs = 60_000L))
        assertTrue(
            "An early lapse must score well below a clean session",
            lapsed.components.distractionDelay < 20
        )
    }

    @Test
    fun `score is always clamped within zero to one hundred`() {
        listOf(
            result(screenAttention = 250f, gazeShifts = -50),
            result(screenAttention = -80f, gazeShifts = 999, blinkCount = 100_000),
            result(firstDistractionMs = 999_999L, durationMs = 1L)
        ).forEach { r ->
            val score = useCase(r)
            assertTrue("score ${score.score} out of range", score.score in 0..100)
            assertTrue(score.preciseScore in 0.0..100.0)
        }
    }

    // --- Components ---

    @Test
    fun `never lapsing scores full distraction delay, lapsing immediately scores zero`() {
        assertEquals(100, useCase(result(firstDistractionMs = null)).components.distractionDelay)
        assertEquals(0, useCase(result(firstDistractionMs = 0L)).components.distractionDelay)
        // Halfway through a 60s clip.
        assertEquals(
            50,
            useCase(result(firstDistractionMs = 30_000L, durationMs = 60_000L))
                .components.distractionDelay
        )
    }

    @Test
    fun `gaze stability falls five points per shift and floors at zero`() {
        assertEquals(100, useCase(result(gazeShifts = 0)).components.gazeStability)
        assertEquals(50, useCase(result(gazeShifts = 10)).components.gazeStability)
        assertEquals(0, useCase(result(gazeShifts = 20)).components.gazeStability)
        assertEquals(0, useCase(result(gazeShifts = 40)).components.gazeStability)
    }

    @Test
    fun `self reported ratings map one to five onto zero to one hundred`() {
        assertEquals(0, useCase(result(interest = 1)).components.interest)
        assertEquals(50, useCase(result(interest = 3)).components.interest)
        assertEquals(100, useCase(result(interest = 5)).components.interest)
    }

    // --- Missing self-report redistribution ---

    @Test
    fun `missing self reports are redistributed, not zeroed`() {
        // Identical gaze signal, all components at 100. If the missing
        // self-report weight were simply dropped the score would fall to 85.
        val withRatings = useCase(result(blinkCount = 17, interest = 5, focus = 5))
        val withoutRatings = useCase(result(blinkCount = 17, interest = null, focus = null))

        assertEquals(100, withRatings.score)
        assertEquals(
            "A session with no ratings must not be scaled down for that alone",
            100,
            withoutRatings.score
        )
        assertNull(withoutRatings.components.interest)
        assertNull(withoutRatings.components.focus)
    }

    @Test
    fun `one missing rating redistributes only that rating's weight`() {
        val score = useCase(result(blinkCount = 17, interest = 5, focus = null))
        assertEquals(100, score.score)
        assertNotNull(score.components.interest)
        assertNull(score.components.focus)
    }

    @Test
    fun `redistributed weights still sum to one and preserve gaze proportions`() {
        val redistributed = AttentionScoreWeights.DEFAULT.redistributedFor(emptySet())
        assertEquals(1.0, redistributed.weights.values.sum(), 1e-9)
        assertEquals(0.0, redistributed[ScoreComponent.SELF_REPORT_INTEREST], 1e-9)
        assertEquals(0.0, redistributed[ScoreComponent.SELF_REPORT_FOCUS], 1e-9)

        // Screen attention had 0.40 of 0.85 gaze weight; it should still hold
        // that same share of the full 1.0 after redistribution.
        assertEquals(0.40 / 0.85, redistributed[ScoreComponent.SCREEN_ATTENTION], 1e-9)
    }

    @Test
    fun `default weight vector is well formed`() {
        assertEquals(1.0, AttentionScoreWeights.DEFAULT.weights.values.sum(), 1e-9)
        AttentionScoreWeights.SENSITIVITY_VECTORS.forEach { (name, vector) ->
            assertEquals("$name does not sum to 1", 1.0, vector.weights.values.sum(), 1e-9)
        }
    }

    // --- Degenerate captures ---

    @Test
    fun `zero analysis frames still produce a bounded score and a quality flag`() {
        val metrics = GazeMetrics(
            screenAttentionPercentage = 0f,
            gazeShiftCount = 0,
            firstDistractionMs = null,
            blinkCount = 0,
            actualDurationMs = 0L,
            analyzedFrameCount = 0
        )
        val score = useCase(
            CategoryAssessmentResult(AttentionCategory.MUSIC, metrics, successful = false)
        )
        assertTrue(score.score in 0..100)
        assertTrue(metrics.qualityFlags().contains("NO_FRAMES"))
        assertTrue(metrics.qualityFlags().contains("UNKNOWN_DURATION"))
    }

    @Test
    fun `very low frame counts are flagged as low confidence`() {
        val metrics = GazeMetrics(
            screenAttentionPercentage = 90f,
            gazeShiftCount = 0,
            firstDistractionMs = null,
            blinkCount = 1,
            actualDurationMs = 5_000L,
            analyzedFrameCount = 4,
            analysisFrameRate = 0.8f
        )
        assertTrue(metrics.qualityFlags().contains("LOW_FRAME_COUNT"))
        assertTrue(metrics.qualityFlags().contains("LOW_FRAME_RATE"))
        assertTrue(!metrics.hasSufficientFrames())
    }

    @Test
    fun `frequent face loss is flagged`() {
        val metrics = GazeMetrics(
            screenAttentionPercentage = 20f,
            gazeShiftCount = 5,
            firstDistractionMs = 1000L,
            blinkCount = 3,
            actualDurationMs = 30_000L,
            analyzedFrameCount = 100,
            faceLostFrameCount = 80,
            analysisFrameRate = 10f
        )
        assertTrue(metrics.qualityFlags().contains("FACE_OFTEN_LOST"))
    }

    @Test
    fun `a clean capture raises no quality flags`() {
        assertTrue(
            GazeMetrics(
                screenAttentionPercentage = 95f,
                gazeShiftCount = 1,
                firstDistractionMs = null,
                blinkCount = 15,
                actualDurationMs = 45_000L,
                analyzedFrameCount = 450,
                faceLostFrameCount = 3,
                analysisFrameRate = 10f
            ).qualityFlags().isEmpty()
        )
    }

    // --- Ranking ---

    @Test
    fun `scoreAll ranks categories by score, best first`() {
        val scored = useCase.scoreAll(
            listOf(
                result(AttentionCategory.HORROR, screenAttention = 20f, interest = 1, focus = 1),
                result(AttentionCategory.MUSIC, screenAttention = 95f, blinkCount = 17),
                result(AttentionCategory.GAMING, screenAttention = 60f, interest = 3, focus = 3)
            )
        )
        assertEquals(
            listOf(AttentionCategory.MUSIC, AttentionCategory.GAMING, AttentionCategory.HORROR),
            scored.map { it.result.category }
        )
        assertTrue(scored[0].score > scored[1].score)
        assertTrue(scored[1].score > scored[2].score)
    }

    @Test
    fun `ties break deterministically regardless of input order`() {
        val a = result(AttentionCategory.ROMANCE, blinkCount = 17)
        val b = result(AttentionCategory.MUSIC, blinkCount = 17)

        val forwards = useCase.scoreAll(listOf(a, b)).map { it.result.category }
        val backwards = useCase.scoreAll(listOf(b, a)).map { it.result.category }
        assertEquals(forwards, backwards)
        // Canonical order wins the tie: Music (ordinal 0) before Romance.
        assertEquals(AttentionCategory.MUSIC, forwards.first())
    }

    @Test
    fun `scoring an empty result list yields an empty ranking`() {
        assertTrue(useCase.scoreAll(emptyList()).isEmpty())
    }
}
