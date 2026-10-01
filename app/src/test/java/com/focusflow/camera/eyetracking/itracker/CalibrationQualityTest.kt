package com.focusflow.camera.eyetracking.itracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [CalibrationQuality], added to fix the calibration screen reporting
 * its RMS *residual* as though it were an accuracy figure.
 *
 * The assertions worth having here are about direction and framing — that a
 * smaller residual is described as better, and that the number is always
 * labelled as an error margin — because getting those backwards is invisible in
 * a screenshot and wrong in a way a user would act on.
 */
class CalibrationQualityTest {

    @Test
    fun `a residual worse than the usable threshold is rejected`() {
        val justOver = GazeCalibration.MAX_ACCEPTABLE_RESIDUAL + 0.01f
        assertEquals(
            CalibrationQuality.Band.REJECTED,
            CalibrationQuality.bandFor(justOver)
        )
    }

    @Test
    fun `a residual exactly at the threshold is still usable`() {
        // The fit code saves anything <= MAX_ACCEPTABLE_RESIDUAL, so the band
        // must agree with what actually gets persisted.
        assertEquals(
            CalibrationQuality.Band.USABLE,
            CalibrationQuality.bandFor(GazeCalibration.MAX_ACCEPTABLE_RESIDUAL)
        )
    }

    @Test
    fun `a small residual is good and a large one is not`() {
        assertEquals(CalibrationQuality.Band.GOOD, CalibrationQuality.bandFor(0.02f))
        assertEquals(CalibrationQuality.Band.GOOD, CalibrationQuality.bandFor(0.05f))
        assertEquals(CalibrationQuality.Band.USABLE, CalibrationQuality.bandFor(0.15f))
    }

    /**
     * Smaller must always mean better. If this inverts, the screen tells users
     * a bad calibration is a good one.
     */
    @Test
    fun `band never improves as the residual grows`() {
        val order = listOf(
            CalibrationQuality.Band.GOOD,
            CalibrationQuality.Band.USABLE,
            CalibrationQuality.Band.REJECTED
        )
        var previous = 0
        var residual = 0.01f
        while (residual < 0.4f) {
            val rank = order.indexOf(CalibrationQuality.bandFor(residual))
            assertTrue(
                "band improved at residual $residual",
                rank >= previous
            )
            previous = rank
            residual += 0.01f
        }
    }

    @Test
    fun `margin is reported as a whole percent and never zero`() {
        assertEquals(8, CalibrationQuality.marginPercent(0.08f))
        assertEquals(18, CalibrationQuality.marginPercent(0.185f))
        // A very good fit must not report "within 0% of the screen", which
        // would claim perfect precision from a centimetre-level estimate.
        assertEquals(1, CalibrationQuality.marginPercent(0.004f))
    }

    /**
     * The original defect. The old copy read:
     *
     *     "We can now tell where on the screen you're looking (≈18% of screen)."
     *
     * which presents an error as a precision claim. The replacement must name
     * the number as a margin the estimate lands *within*.
     */
    @Test
    fun `description frames the number as an error margin not an accuracy`() {
        val good = CalibrationQuality.describe(residualRms = 0.06f, pointCount = 5)
        assertTrue("should say the estimate lands within the margin", good.contains("within"))
        assertTrue(good.contains("6%"))
        assertFalse(
            "must not claim an accuracy percentage",
            good.contains("accurate", ignoreCase = true)
        )
    }

    @Test
    fun `a barely usable fit is described more cautiously than a good one`() {
        val good = CalibrationQuality.describe(0.04f, 5)
        val usable = CalibrationQuality.describe(0.18f, 5)

        // The near-threshold case has to hedge and suggest a redo; the good one
        // should not. Previously both used identical confident wording.
        assertTrue(usable.contains("only roughly") || usable.contains("Redoing"))
        assertFalse(good.contains("only roughly"))
    }

    @Test
    fun `description reports the real point count`() {
        assertTrue(CalibrationQuality.describe(0.05f, 5).contains("5 points"))
        assertTrue(CalibrationQuality.describe(0.05f, 4).contains("4 points"))
    }

    /**
     * Failure advice used to be one generic "try again in better light" for
     * every cause, including the case where no gaze reading arrived at all —
     * which is a different problem with a different fix.
     */
    @Test
    fun `failure advice distinguishes no readings from a poor fit`() {
        val none = CalibrationQuality.failureAdvice(collectedPoints = 0)
        val tooFew = CalibrationQuality.failureAdvice(collectedPoints = 2)
        val poorFit = CalibrationQuality.failureAdvice(
            collectedPoints = GazeCalibration.TARGETS.size
        )

        assertTrue(none.contains("No gaze readings"))
        assertTrue(none.contains("covering the front camera"))

        assertTrue(tooFew.contains("2 of ${GazeCalibration.TARGETS.size}"))

        assertTrue(poorFit.contains("didn't line up"))
        // The three must actually differ, which is the point of the change.
        assertEquals(3, setOf(none, tooFew, poorFit).size)
    }
}
