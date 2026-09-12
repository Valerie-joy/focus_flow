package com.focusflow.camera.eyetracking.itracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GazeFusionTest {
    private val on = ScreenPoint(0.5f, 0.5f)
    private val off = ScreenPoint(1.8f, 0.5f)

    @Test
    fun turnedAwayHeadAlwaysWins() {
        val f = GazeFusion()
        val d = f.decide(blendshapeLooking = true, headTurnedAway = true, screenPoint = on, timestampMs = 0)
        assertFalse(d.isLooking)
        assertFalse(d.fromGazePoint)
    }

    @Test
    fun gazePointDecidesWhenPresent() {
        // Window of 1 so the median filter (tested separately) stays out of this.
        val f = GazeFusion(medianWindow = 1)
        assertTrue(f.decide(false, false, on, 0).isLooking)   // blendshape said no, point says yes
        assertFalse(f.decide(true, false, off, 33).isLooking) // and the reverse
        assertTrue(f.decide(true, false, off, 33).fromGazePoint)
    }

    @Test
    fun verdictCarriesForwardWhileFreshThenFallsBackToBlendshape() {
        val f = GazeFusion(staleAfterMs = 400)
        assertFalse(f.decide(true, false, off, 1000).isLooking)
        val carried = f.decide(true, false, null, 1300)
        assertFalse(carried.isLooking)
        assertTrue(carried.fromGazePoint)
        val stale = f.decide(true, false, null, 1500)
        assertTrue(stale.isLooking)
        assertFalse(stale.fromGazePoint)
    }

    @Test
    fun medianFilterRemovesASingleJitterFrame() {
        val f = GazeFusion(medianWindow = 3)
        f.decide(true, false, on, 0)
        f.decide(true, false, on, 33)
        val d = f.decide(true, false, off, 66) // one wild frame among two good ones
        assertTrue(d.isLooking)
        assertEquals(on.x, d.screenPoint!!.x, 1e-6f)
    }

    @Test
    fun resetDropsTheCarriedVerdict() {
        val f = GazeFusion()
        f.decide(true, false, off, 0)
        f.reset()
        val d = f.decide(true, false, null, 10)
        assertTrue(d.isLooking)
        assertFalse(d.fromGazePoint)
    }
}
