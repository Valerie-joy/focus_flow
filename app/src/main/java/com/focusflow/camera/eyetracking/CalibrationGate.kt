package com.focusflow.camera.eyetracking

/**
 * The result of the camera setup step: the user's neutral, screen-facing head
 * orientation, captured once acquisition is stable.
 *
 * This is a *baseline*, not a per-user gaze-point fit. The PID is explicit
 * that the system measures gaze deflection rather than calibrated
 * point-of-regard, and this does not change that: it records what "facing the
 * screen" looks like for this person in this sitting position, so later head
 * yaw/pitch can be judged relative to their neutral rather than to an assumed
 * zero.
 */
data class CalibrationBaseline(
    val baselineYawDegrees: Float,
    val baselinePitchDegrees: Float,
    val framesObserved: Int
)

/**
 * Gates the start of an assessment on stable face and iris acquisition, per
 * the PID's camera setup step: bind the front camera, detect a face, verify
 * both irises are detected stably, establish a baseline orientation, and only
 * then allow the assessment to begin.
 *
 * Stability is the point. A single good frame proves nothing — a face can be
 * detected for one frame as someone walks past, and starting a measured clip
 * on that produces an instant false "attention shifted" before the video is
 * even visible. So the gate requires [requiredStableFrames] *consecutive*
 * qualifying frames, and any bad frame resets the run rather than being
 * averaged away.
 *
 * Pure logic with no Android or MediaPipe types, so the acquisition rules can
 * be tested on the JVM rather than only on a device with a real face in front
 * of it.
 *
 * Not thread-safe: feed it from the single camera-analysis thread.
 */
class CalibrationGate(
    private val requiredStableFrames: Int = 15,
    /** 5 landmarks per iris; both irises present means all 10. */
    private val requiredIrisPoints: Int = 10,
    /** Head must be within this of centre to count as a neutral pose. */
    private val maxBaselineYawDegrees: Float = 20f,
    private val maxBaselinePitchDegrees: Float = 15f
) {
    private var consecutiveStableFrames = 0
    private val yawSamples = mutableListOf<Float>()
    private val pitchSamples = mutableListOf<Float>()
    private var baseline: CalibrationBaseline? = null

    enum class Status {
        /** No face in frame yet. */
        SEARCHING_FOR_FACE,

        /** Face found, but the irises aren't both being resolved. */
        IRISES_NOT_DETECTED,

        /** Face and irises fine, but the head is turned away from the screen. */
        NOT_FACING_SCREEN,

        /** Acquisition is good; still accumulating consecutive frames. */
        STABILIZING,

        /** Enough stable frames — the assessment may begin. */
        READY
    }

    data class Progress(
        val status: Status,
        /** 0f..1f toward [requiredStableFrames]. */
        val fraction: Float,
        val baseline: CalibrationBaseline?
    ) {
        val isReady: Boolean get() = status == Status.READY
    }

    val currentBaseline: CalibrationBaseline? get() = baseline

    fun reset() {
        consecutiveStableFrames = 0
        yawSamples.clear()
        pitchSamples.clear()
        baseline = null
    }

    /**
     * Feed one analyzed frame.
     *
     * [headYawDegrees]/[headPitchDegrees] may be null when the transformation
     * matrix wasn't produced for a frame; that is treated as an unknown-but-
     * acceptable pose (0 contribution) rather than a failure, because the
     * iris/face checks already carry the acquisition decision and failing on a
     * missing matrix would stall setup on devices that report it sporadically.
     */
    fun accept(
        faceDetected: Boolean,
        irisPointCount: Int,
        headYawDegrees: Float?,
        headPitchDegrees: Float?
    ): Progress {
        val status = when {
            !faceDetected -> Status.SEARCHING_FOR_FACE
            irisPointCount < requiredIrisPoints -> Status.IRISES_NOT_DETECTED
            headYawDegrees != null && kotlin.math.abs(headYawDegrees) > maxBaselineYawDegrees ->
                Status.NOT_FACING_SCREEN
            headPitchDegrees != null && kotlin.math.abs(headPitchDegrees) > maxBaselinePitchDegrees ->
                Status.NOT_FACING_SCREEN
            else -> null
        }

        if (status != null) {
            // Any non-qualifying frame restarts the run — see the class docs on
            // why this is a consecutive-frame requirement and not a tally.
            consecutiveStableFrames = 0
            yawSamples.clear()
            pitchSamples.clear()
            return Progress(status, 0f, baseline)
        }

        consecutiveStableFrames++
        yawSamples += headYawDegrees ?: 0f
        pitchSamples += headPitchDegrees ?: 0f

        return if (consecutiveStableFrames >= requiredStableFrames) {
            // Baseline is the mean neutral pose across the stable run, which is
            // steadier than whichever single frame happened to cross the line.
            baseline = CalibrationBaseline(
                baselineYawDegrees = yawSamples.average().toFloat(),
                baselinePitchDegrees = pitchSamples.average().toFloat(),
                framesObserved = consecutiveStableFrames
            )
            Progress(Status.READY, 1f, baseline)
        } else {
            Progress(
                Status.STABILIZING,
                consecutiveStableFrames.toFloat() / requiredStableFrames,
                baseline
            )
        }
    }
}
