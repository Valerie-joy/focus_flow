package com.focusflow.ai.attention

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The temporal layer: sustained lapses, gaze shifts, blinks, and the measured
 * analysis frame rate. Frame timestamps are supplied explicitly so every test
 * is deterministic rather than wall-clock dependent.
 */
class AttentionTrackerTest {

    private fun tracker() = AttentionTracker(
        distractionSustainMs = 1500L,
        attentionDropThreshold = 0.55f,
        rollingWindowMs = 3000L,
        minFramesBeforeEvaluating = 5
    )

    /** Feeds [count] frames at 100ms intervals starting at [startMs]. */
    private fun AttentionTracker.feed(
        count: Int,
        looking: Boolean,
        startMs: Long,
        faceDetected: Boolean = true,
        eyesClosed: Boolean = false,
        stepMs: Long = 100L
    ): Long {
        var t = startMs
        repeat(count) {
            recordFrame(faceDetected, looking, eyesClosed, t)
            t += stepMs
        }
        return t
    }

    @Test
    fun `frames before the first face are ignored rather than counted as away`() {
        val tracker = tracker()
        repeat(20) { tracker.recordFrame(false, false, false, it * 100L) }
        // Nothing scored yet — camera warm-up must not poison the ratio.
        assertEquals(0f, tracker.currentMetrics().screenAttentionPercentage, 1e-4f)
        assertEquals(0, tracker.currentMetrics().analyzedFrameCount)
    }

    @Test
    fun `sustained attention while looking never reports a drop`() {
        val tracker = tracker()
        var dropped = false
        var t = 0L
        repeat(100) {
            dropped = dropped || tracker.recordFrame(true, true, false, t).attentionDroppedSustained
            t += 100L
        }
        assertFalse(dropped)
        assertEquals(100f, tracker.currentMetrics().screenAttentionPercentage, 1e-4f)
        assertNull(tracker.currentMetrics().firstDistractionMs)
    }

    @Test
    fun `a single noisy frame does not end the clip`() {
        val tracker = tracker()
        var t = tracker.feed(30, looking = true, startMs = 0L)
        val blip = tracker.recordFrame(true, false, false, t)
        assertFalse("One away-frame must not terminate the clip", blip.attentionDroppedSustained)
    }

    @Test
    fun `a sustained look away past the threshold reports a drop`() {
        val tracker = tracker()
        var t = tracker.feed(30, looking = true, startMs = 0L)
        // 2 seconds away, past the 1500ms sustain threshold.
        var dropped = false
        repeat(20) {
            dropped = dropped || tracker.recordFrame(true, false, false, t).attentionDroppedSustained
            t += 100L
        }
        assertTrue(dropped)
        assertTrue(tracker.currentMetrics().firstDistractionMs != null)
    }

    @Test
    fun `gaze shifts count look-away and look-back transitions`() {
        val tracker = tracker()
        var t = tracker.feed(3, looking = true, startMs = 0L)
        t = tracker.feed(3, looking = false, startMs = t)
        t = tracker.feed(3, looking = true, startMs = t)
        // Two transitions: looking -> away -> looking.
        assertEquals(2, tracker.currentMetrics().gazeShiftCount)
    }

    @Test
    fun `blinks are counted once per closing edge`() {
        val tracker = tracker()
        var t = tracker.feed(2, looking = true, startMs = 0L)
        // Eyes closed for several frames = one blink, not several.
        t = tracker.feed(4, looking = true, startMs = t, eyesClosed = true)
        t = tracker.feed(2, looking = true, startMs = t)
        t = tracker.feed(3, looking = true, startMs = t, eyesClosed = true)
        assertEquals(2, tracker.currentMetrics().blinkCount)
    }

    @Test
    fun `analysis frame rate is measured from real frame timestamps`() {
        val tracker = tracker()
        // 11 frames at 100ms = 10 intervals over 1000ms = 10 fps.
        tracker.feed(11, looking = true, startMs = 0L, stepMs = 100L)
        assertEquals(10f, tracker.analysisFrameRate(), 0.01f)
        assertEquals(10f, tracker.currentMetrics().analysisFrameRate, 0.01f)
    }

    @Test
    fun `frame rate is zero when too few frames were analyzed to divide by`() {
        val tracker = tracker()
        assertEquals(0f, tracker.analysisFrameRate(), 1e-6f)
        tracker.recordFrame(true, true, false, 0L)
        assertEquals(0f, tracker.analysisFrameRate(), 1e-6f)
    }

    @Test
    fun `face loss during a clip is recorded as a quality signal`() {
        val tracker = tracker()
        var t = tracker.feed(10, looking = true, startMs = 0L)
        t = tracker.feed(10, looking = false, startMs = t, faceDetected = false)

        val metrics = tracker.currentMetrics()
        assertEquals(20, metrics.analyzedFrameCount)
        assertEquals(10, metrics.faceLostFrameCount)
        assertEquals(50f, metrics.screenAttentionPercentage, 1e-4f)
    }

    @Test
    fun `reset clears all accumulated state`() {
        val tracker = tracker()
        tracker.feed(20, looking = true, startMs = 0L, eyesClosed = false)
        tracker.reset()

        val metrics = tracker.currentMetrics()
        assertEquals(0, metrics.analyzedFrameCount)
        assertEquals(0, metrics.gazeShiftCount)
        assertEquals(0, metrics.blinkCount)
        assertEquals(0f, metrics.screenAttentionPercentage, 1e-4f)
        assertNull(metrics.firstDistractionMs)
    }
}
