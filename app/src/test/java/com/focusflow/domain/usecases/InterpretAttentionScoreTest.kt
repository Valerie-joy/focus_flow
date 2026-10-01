package com.focusflow.domain.usecases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the result-presentation layer.
 *
 * The behaviours pinned here are the ones with a duty of care attached: that
 * the app never states or implies a diagnosis, that a result built on too few
 * assessments is always labelled provisional, and that band boundaries stay
 * where the UI assumes they are.
 */
class InterpretAttentionScoreTest {

    @Test
    fun `band boundaries are inclusive at the lower edge`() {
        assertEquals(InterpretAttentionScore.Band.VERY_STRONG, InterpretAttentionScore.bandFor(85))
        assertEquals(InterpretAttentionScore.Band.STRONG, InterpretAttentionScore.bandFor(84))
        assertEquals(InterpretAttentionScore.Band.STRONG, InterpretAttentionScore.bandFor(70))
        assertEquals(InterpretAttentionScore.Band.MIXED, InterpretAttentionScore.bandFor(69))
        assertEquals(InterpretAttentionScore.Band.MIXED, InterpretAttentionScore.bandFor(50))
        assertEquals(InterpretAttentionScore.Band.VARIABLE, InterpretAttentionScore.bandFor(49))
    }

    @Test
    fun `extremes stay within the band range`() {
        assertEquals(InterpretAttentionScore.Band.VARIABLE, InterpretAttentionScore.bandFor(0))
        assertEquals(InterpretAttentionScore.Band.VERY_STRONG, InterpretAttentionScore.bandFor(100))
    }

    @Test
    fun `summary names the category rather than judging the person`() {
        val summary = InterpretAttentionScore.summaryFor(92, "Music")
        assertTrue("should mention the category", summary.contains("Music"))
        // Phrased about gaze and content; never about the person's ability.
        listOf("disorder", "deficit", "ADHD", "abnormal", "poor focus").forEach { term ->
            assertTrue(
                "summary must not contain '$term'",
                !summary.contains(term, ignoreCase = true)
            )
        }
    }

    @Test
    fun `every band produces a non-empty summary`() {
        listOf(95, 75, 60, 20).forEach { score ->
            assertTrue(InterpretAttentionScore.summaryFor(score, "Gaming").isNotBlank())
        }
    }

    /**
     * The limitations text ships on screen and inside the exported PDF, which
     * may be shown to a teacher or a clinician. It has to disclaim diagnosis
     * explicitly, and name ADHD, because the app asks an ADHD self-report
     * questionnaire earlier in the flow.
     */
    @Test
    fun `limitations text disclaims diagnosis explicitly`() {
        val text = InterpretAttentionScore.LIMITATIONS
        assertTrue(text.contains("not a medical test", ignoreCase = true))
        assertTrue(text.contains("diagnose", ignoreCase = true))
        assertTrue(text.contains("ADHD"))
    }

    @Test
    fun `provisional notice appears below the required count`() {
        val notice = InterpretAttentionScore.provisionalNotice(
            successfulAssessments = 2,
            requiredAssessments = 5
        )
        assertNotNull(notice)
        assertTrue(notice!!.contains("2 of 5"))
        assertTrue("should say results may change", notice.contains("may shift"))
    }

    @Test
    fun `provisional notice pluralises the remaining count`() {
        val one = InterpretAttentionScore.provisionalNotice(4, 5)
        assertTrue(one!!.contains("one more category"))

        val several = InterpretAttentionScore.provisionalNotice(1, 5)
        assertTrue(several!!.contains("4 more categories"))
    }

    @Test
    fun `provisional notice disappears once the requirement is met`() {
        assertNull(InterpretAttentionScore.provisionalNotice(5, 5))
        // More than required is still not provisional.
        assertNull(InterpretAttentionScore.provisionalNotice(7, 5))
    }

    @Test
    fun `gaze shift explanation reads correctly at zero one and many`() {
        assertTrue(InterpretAttentionScore.explainGazeShifts(0).contains("didn't leave"))
        assertTrue(InterpretAttentionScore.explainGazeShifts(1).contains("once"))
        assertTrue(InterpretAttentionScore.explainGazeShifts(5).contains("5 times"))
    }

    @Test
    fun `a null first distraction is described as no measured break`() {
        assertTrue(
            InterpretAttentionScore.explainFirstDistraction(null).contains("without a measured break")
        )
        assertTrue(
            InterpretAttentionScore.explainFirstDistraction(12_000L).contains("12 seconds")
        )
    }

    /**
     * Blink count is the metric most likely to be misread as a verdict, so its
     * explanation must say what it is actually for.
     */
    @Test
    fun `blink explanation states what the metric is used for`() {
        val text = InterpretAttentionScore.explainBlinks(14)
        assertTrue(text.contains("14"))
        assertTrue(text.contains("not as a measure of focus", ignoreCase = true))
    }
}
