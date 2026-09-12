package com.focusflow.ai.attention

import com.focusflow.domain.models.GazeMetrics

/**
 * Turns a stream of per-frame gaze results from
 * [com.focusflow.camera.eyetracking.GazeAnalyzer] into [GazeMetrics] for one
 * video playback, and flags when attention has dropped for long enough that
 * the assessment screen should stop the video early.
 *
 * The "is the user looking at the screen" decision itself lives in
 * [com.focusflow.camera.eyetracking.GazeAnalyzer], which combines iris/gaze
 * blendshapes with head yaw *and* pitch. This class is purely the temporal
 * layer on top: how long, how often, and whether that constitutes a drop.
 *
 * Not thread-safe by design — call [recordFrame] from a single analysis
 * thread/executor (the same one CameraX's ImageAnalysis already uses).
 */
class AttentionTracker(
    private val distractionSustainMs: Long = 1500L,
    private val attentionDropThreshold: Float = 0.55f,
    /** Width of the recent-history window the drop ratio is judged over. */
    private val rollingWindowMs: Long = 3000L,
    private val minFramesBeforeEvaluating: Int = 15
) {
    private var totalFrames = 0
    private var lookingFrames = 0
    private var gazeShiftCount = 0
    private var wasLookingLastFrame: Boolean? = null
    private var firstDistractionMs: Long? = null
    private var currentDistractionStartMs: Long? = null
    private var blinkCount = 0
    private var eyesClosedLastFrame = false
    private var sessionStartMs: Long? = null
    private var lastFrameMs: Long? = null
    private var faceLostFrames = 0
    private var gazePointDecidedFrames = 0
    private var gazeInferenceSumMs = 0f
    private var gazeInferenceCount = 0

    /** (timestampMs, wasLooking) for the last [rollingWindowMs] of frames. */
    private val window = ArrayDeque<Pair<Long, Boolean>>()

    data class FrameResult(
        val isLooking: Boolean,
        val runningAttentionPercentage: Float,
        val attentionDroppedSustained: Boolean
    )

    fun reset() {
        totalFrames = 0
        lookingFrames = 0
        gazeShiftCount = 0
        wasLookingLastFrame = null
        firstDistractionMs = null
        currentDistractionStartMs = null
        blinkCount = 0
        eyesClosedLastFrame = false
        sessionStartMs = null
        lastFrameMs = null
        faceLostFrames = 0
        gazePointDecidedFrames = 0
        gazeInferenceSumMs = 0f
        gazeInferenceCount = 0
        window.clear()
    }

    /**
     * Feed one frame. [timestampMs] must be a real monotonic frame timestamp —
     * an earlier version derived it from a 1-second UI tick, which quantised
     * every duration to whole seconds and made [distractionSustainMs] of 1500
     * impossible to honour.
     *
     * Frames before the first face acquisition are ignored entirely rather
     * than counted as "not looking": camera warm-up would otherwise poison the
     * ratio below and latch a false attention drop before the video is even
     * visible.
     */
    fun recordFrame(
        faceDetected: Boolean,
        isLookingAtScreen: Boolean,
        eyesClosed: Boolean,
        timestampMs: Long,
        /** Whether the calibrated gaze point (not the blendshape rule) made this frame's decision. */
        decidedByGazePoint: Boolean = false,
        /** iTracker wall time on this frame, when it ran. */
        gazeInferenceMs: Float? = null
    ): FrameResult {
        if (sessionStartMs == null) {
            if (!faceDetected) {
                // Still warming up / no one in frame yet — nothing to score.
                return FrameResult(
                    isLooking = false,
                    runningAttentionPercentage = 100f,
                    attentionDroppedSustained = false
                )
            }
            sessionStartMs = timestampMs
        }

        val isLooking = faceDetected && isLookingAtScreen
        totalFrames++
        if (isLooking) lookingFrames++
        if (!faceDetected) faceLostFrames++
        if (decidedByGazePoint) gazePointDecidedFrames++
        if (gazeInferenceMs != null) { gazeInferenceSumMs += gazeInferenceMs; gazeInferenceCount++ }
        lastFrameMs = timestampMs

        // Gaze shift: a transition between looking and not-looking. Seeded from
        // the first real frame so frame 1 can't invent a phantom shift.
        if (wasLookingLastFrame != null && isLooking != wasLookingLastFrame) {
            gazeShiftCount++
        }
        wasLookingLastFrame = isLooking

        // Blink: count on the closing edge, same as before.
        if (eyesClosed && !eyesClosedLastFrame) blinkCount++
        eyesClosedLastFrame = eyesClosed

        // Sustained-distraction tracking.
        var sustainedDrop = false
        if (!isLooking) {
            if (currentDistractionStartMs == null) currentDistractionStartMs = timestampMs
            val distractionDurationMs = timestampMs - (currentDistractionStartMs ?: timestampMs)
            if (distractionDurationMs >= distractionSustainMs) {
                if (firstDistractionMs == null) {
                    firstDistractionMs = currentDistractionStartMs?.minus(sessionStartMs ?: timestampMs)
                }
                if (totalFrames >= minFramesBeforeEvaluating) sustainedDrop = true
            }
        } else {
            currentDistractionStartMs = null
        }

        // Rolling-window ratio, for rapid flicking between looking and away.
        // Deliberately a *window* and not a lifetime cumulative: the old
        // version could never recover once early frames dragged it under the
        // threshold, so a bad first second ended the whole session.
        window.addLast(timestampMs to isLooking)
        while (window.isNotEmpty() && timestampMs - window.first().first > rollingWindowMs) {
            window.removeFirst()
        }
        val windowLooking = window.count { it.second }
        val ratioDrop = window.size >= minFramesBeforeEvaluating &&
            (windowLooking.toFloat() / window.size) < attentionDropThreshold

        // A ratio drop is a first sustained lapse too, and it must be recorded
        // as one. It previously wasn't: a clip ended early for rapid flicking
        // between screen and elsewhere left firstDistractionMs null, which
        // CalculateAttentionScoreUseCase reads as "never lapsed" and rewards
        // with a *perfect* distraction-delay score of 100. The least attentive
        // sessions were scoring best on that component.
        if (ratioDrop && firstDistractionMs == null) {
            val lapseStart = currentDistractionStartMs ?: timestampMs
            firstDistractionMs = lapseStart - (sessionStartMs ?: timestampMs)
        }

        val runningPercentage =
            if (totalFrames == 0) 100f else (lookingFrames.toFloat() / totalFrames) * 100f

        return FrameResult(
            isLooking = isLooking,
            runningAttentionPercentage = runningPercentage,
            attentionDroppedSustained = sustainedDrop || ratioDrop
        )
    }

    /**
     * Effective analysis frame rate over the frames actually observed, in fps.
     *
     * Measured rather than assumed: the PID treats this as a per-device
     * constraint that has to be logged per session, because on-device
     * inference running alongside video playback sustains very different rates
     * on different hardware, and every temporal measure above inherits that
     * resolution. Returns 0 when too little time has elapsed to divide by.
     */
    fun analysisFrameRate(): Float {
        val start = sessionStartMs ?: return 0f
        val end = lastFrameMs ?: return 0f
        val elapsedMs = end - start
        if (elapsedMs <= 0L || totalFrames < 2) return 0f
        // totalFrames - 1 intervals span the elapsed window.
        return (totalFrames - 1) * 1000f / elapsedMs
    }

    fun currentMetrics(): GazeMetrics {
        val percentage = if (totalFrames == 0) 0f else (lookingFrames.toFloat() / totalFrames) * 100f
        return GazeMetrics(
            screenAttentionPercentage = percentage,
            gazeShiftCount = gazeShiftCount,
            firstDistractionMs = firstDistractionMs,
            blinkCount = blinkCount,
            analyzedFrameCount = totalFrames,
            faceLostFrameCount = faceLostFrames,
            analysisFrameRate = analysisFrameRate(),
            gazePointDecidedFrames = gazePointDecidedFrames,
            gazePointMeanInferenceMs = if (gazeInferenceCount > 0) gazeInferenceSumMs / gazeInferenceCount else 0f
        )
    }
}
