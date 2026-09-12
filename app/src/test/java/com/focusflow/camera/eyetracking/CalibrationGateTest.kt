package com.focusflow.camera.eyetracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The camera setup gate: face present, both irises resolved, head facing the
 * screen, held steady — before any assessment is allowed to start.
 */
class CalibrationGateTest {

    private fun gate() = CalibrationGate(requiredStableFrames = 5)

    private fun CalibrationGate.feedGood(
        times: Int,
        yaw: Float = 2f,
        pitch: Float = -1f
    ): CalibrationGate.Progress {
        var last = accept(true, 10, yaw, pitch)
        repeat(times - 1) { last = accept(true, 10, yaw, pitch) }
        return last
    }

    @Test
    fun `no face reports searching and never becomes ready`() {
        val gate = gate()
        repeat(50) {
            val progress = gate.accept(faceDetected = false, irisPointCount = 0, null, null)
            assertEquals(CalibrationGate.Status.SEARCHING_FOR_FACE, progress.status)
            assertFalse(progress.isReady)
        }
        assertNull(gate.currentBaseline)
    }

    @Test
    fun `a face without both irises does not qualify`() {
        val gate = gate()
        repeat(50) {
            // Only one iris resolved.
            val progress = gate.accept(faceDetected = true, irisPointCount = 5, 0f, 0f)
            assertEquals(CalibrationGate.Status.IRISES_NOT_DETECTED, progress.status)
        }
        assertFalse(gate.accept(true, 5, 0f, 0f).isReady)
    }

    @Test
    fun `a head turned away does not qualify`() {
        val gate = gate()
        assertEquals(
            CalibrationGate.Status.NOT_FACING_SCREEN,
            gate.accept(true, 10, headYawDegrees = 60f, headPitchDegrees = 0f).status
        )
        assertEquals(
            CalibrationGate.Status.NOT_FACING_SCREEN,
            gate.accept(true, 10, headYawDegrees = 0f, headPitchDegrees = 45f).status
        )
    }

    @Test
    fun `a single good frame is not enough`() {
        val progress = gate().accept(true, 10, 0f, 0f)
        assertEquals(CalibrationGate.Status.STABILIZING, progress.status)
        assertFalse(progress.isReady)
    }

    @Test
    fun `enough consecutive good frames becomes ready and captures a baseline`() {
        val gate = gate()
        val progress = gate.feedGood(5, yaw = 4f, pitch = -2f)

        assertTrue(progress.isReady)
        assertEquals(CalibrationGate.Status.READY, progress.status)
        assertEquals(1f, progress.fraction, 1e-6f)

        val baseline = gate.currentBaseline
        assertNotNull(baseline)
        assertEquals(4f, baseline!!.baselineYawDegrees, 1e-4f)
        assertEquals(-2f, baseline.baselinePitchDegrees, 1e-4f)
        assertEquals(5, baseline.framesObserved)
    }

    @Test
    fun `a bad frame resets the stable run`() {
        val gate = gate()
        gate.feedGood(4)
        // One dropped frame mid-run — must not be averaged away.
        assertEquals(
            CalibrationGate.Status.SEARCHING_FOR_FACE,
            gate.accept(false, 0, null, null).status
        )
        assertFalse(gate.accept(true, 10, 0f, 0f).isReady)
        // A fresh full run is required.
        assertTrue(gate.feedGood(5).isReady)
    }

    @Test
    fun `progress fraction advances toward ready`() {
        val gate = gate()
        assertEquals(0.2f, gate.accept(true, 10, 0f, 0f).fraction, 1e-6f)
        assertEquals(0.4f, gate.accept(true, 10, 0f, 0f).fraction, 1e-6f)
        assertEquals(0.6f, gate.accept(true, 10, 0f, 0f).fraction, 1e-6f)
    }

    @Test
    fun `a missing head pose does not block acquisition`() {
        // Some devices report the transformation matrix only sporadically;
        // stalling setup on that would make the app unusable there.
        val gate = gate()
        repeat(5) { gate.accept(true, 10, headYawDegrees = null, headPitchDegrees = null) }
        assertNotNull(gate.currentBaseline)
    }

    @Test
    fun `reset clears the baseline and the run`() {
        val gate = gate()
        gate.feedGood(5)
        assertNotNull(gate.currentBaseline)

        gate.reset()
        assertNull(gate.currentBaseline)
        assertFalse(gate.accept(true, 10, 0f, 0f).isReady)
    }
}
