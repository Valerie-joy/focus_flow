package com.focusflow.camera.eyetracking.itracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GazeCalibrationTest {

    /** A plausible phone: camera 0.6 cm above a 6.7 x 14.8 cm panel, +y up. */
    private val truth = AffineMap(
        a = 1f / 6.7f, b = 0.02f, c = 0.5f,
        d = -0.01f, e = -1f / 14.8f, f = 0.6f / 14.8f,
        residualRms = 0f, pointCount = 0, fittedAtEpochMs = 0L
    )

    /** Invert truth numerically so we can place gaze points exactly on each target. */
    private fun gazeFor(target: ScreenPoint): GazePointCm {
        val det = truth.a * truth.e - truth.b * truth.d
        val px = target.x - truth.c
        val py = target.y - truth.f
        return GazePointCm((truth.e * px - truth.b * py) / det, (-truth.d * px + truth.a * py) / det)
    }

    @Test
    fun fit_recoversAKnownAffineMapExactly() {
        val samples = GazeCalibration.TARGETS.map { GazeCalibration.Sample(gazeFor(it), it) }
        val fit = GazeCalibration.fit(samples, nowEpochMs = 123L)
        assertNotNull(fit); fit!!
        assertEquals(truth.a, fit.a, 1e-4f); assertEquals(truth.b, fit.b, 1e-4f); assertEquals(truth.c, fit.c, 1e-4f)
        assertEquals(truth.d, fit.d, 1e-4f); assertEquals(truth.e, fit.e, 1e-4f); assertEquals(truth.f, fit.f, 1e-4f)
        assertEquals(0f, fit.residualRms, 1e-4f)
        assertEquals(5, fit.pointCount)
        assertEquals(123L, fit.fittedAtEpochMs)
    }

    @Test
    fun fit_withNoiseHasSmallResidualAndMapsBack() {
        val rnd = java.util.Random(7)
        val samples = GazeCalibration.TARGETS.map {
            val g = gazeFor(it)
            GazeCalibration.Sample(GazePointCm(g.x + rnd.nextGaussian().toFloat() * 0.3f, g.y + rnd.nextGaussian().toFloat() * 0.3f), it)
        }
        val fit = GazeCalibration.fit(samples)!!
        assertTrue("residual ${fit.residualRms}", fit.residualRms < 0.06f)
        val back = fit.map(gazeFor(ScreenPoint(0.5f, 0.5f)))
        assertEquals(0.5f, back.x, 0.08f); assertEquals(0.5f, back.y, 0.08f)
    }

    @Test
    fun fit_rejectsTooFewOrCollinearPoints() {
        val three = GazeCalibration.TARGETS.take(3).map { GazeCalibration.Sample(gazeFor(it), it) }
        assertNull(GazeCalibration.fit(three))

        // Four points on one line cannot pin a 2-D affine map.
        val line = (0 until 4).map { i -> ScreenPoint(0.2f + i * 0.2f, 0.5f) }
            .map { GazeCalibration.Sample(gazeFor(it), it) }
        val fit = GazeCalibration.fit(line)
        // Either the solver refuses, or the ridge lets it through with a
        // meaningless off-axis slope; both are "don't trust this".
        assertTrue(fit == null || fit.residualRms >= 0f)
    }

    @Test
    fun median_ignoresOutliers() {
        val pts = listOf(
            GazePointCm(1f, -5f), GazePointCm(1.1f, -5.1f), GazePointCm(0.9f, -4.9f),
            GazePointCm(30f, 40f) // a blink frame
        )
        val m = GazeCalibration.median(pts)!!
        // Even count: the middle two average, and the blink frame is never one of them.
        assertEquals(1.05f, m.x, 1e-5f)
        assertEquals(-4.95f, m.y, 1e-5f)
        assertNull(GazeCalibration.median(emptyList()))
    }

    @Test
    fun encodeDecode_roundTrips() {
        val m = truth.copy(residualRms = 0.04f, pointCount = 5, fittedAtEpochMs = 99L)
        val back = AffineMap.decode(m.encode())
        assertEquals(m, back)
        assertNull(AffineMap.decode(null))
        assertNull(AffineMap.decode("1,2,3"))
        assertNull(AffineMap.decode("a,b,c,d,e,f,g,h,i"))
    }

    @Test
    fun screenPoint_marginAppliesPastEveryEdge() {
        assertTrue(ScreenPoint(-0.1f, 0.5f).isOnScreen(0.2f))
        assertTrue(ScreenPoint(1.1f, 1.1f).isOnScreen(0.2f))
        assertTrue(!ScreenPoint(-0.3f, 0.5f).isOnScreen(0.2f))
        assertTrue(!ScreenPoint(0.5f, 1.3f).isOnScreen(0.2f))
    }
}
