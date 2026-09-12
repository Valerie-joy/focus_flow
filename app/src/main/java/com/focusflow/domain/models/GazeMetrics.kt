package com.focusflow.domain.models

/**
 * Raw attention measurements captured for a single video playback, produced
 * by [com.focusflow.ai.attention.AttentionTracker] from the camera's face
 * detection stream. Never includes video/image data — numbers only.
 */
data class GazeMetrics(
    val screenAttentionPercentage: Float, // 0f..100f — % of frames "looking at the screen"
    val gazeShiftCount: Int,               // number of look-away / look-back transitions
    val firstDistractionMs: Long?,         // ms into playback of the first sustained look-away, null if none
    val blinkCount: Int,
    val actualDurationMs: Long = 0L,       // real elapsed watched time at session end; 0 if unknown (see CalculateAttentionScoreUseCase's fallback)
    /** Frames actually analyzed for this clip (i.e. after face acquisition). */
    val analyzedFrameCount: Int = 0,
    /** Analyzed frames in which no face was found — the main acquisition-quality signal. */
    val faceLostFrameCount: Int = 0,
    /**
     * Effective analysis frame rate for this clip, in frames per second.
     *
     * The PID lists this as a constraint to be measured rather than assumed:
     * on-device inference runs at whatever rate the device sustains alongside
     * video playback, and it "varies across devices and must be logged per
     * session". It is persisted with the session for exactly that reason.
     */
    val analysisFrameRate: Float = 0f
) {
    /**
     * Whether enough frames were analyzed to trust the derived measures. A clip
     * that produced almost no frames (camera unavailable, inference starved)
     * still yields *a* number, and that number should not be read as if it
     * were a real observation.
     */
    fun hasSufficientFrames(minimumFrames: Int = MIN_FRAMES_FOR_CONFIDENCE): Boolean =
        analyzedFrameCount >= minimumFrames

    /** Short machine-readable quality notes, persisted alongside the session. */
    fun qualityFlags(): List<String> = buildList {
        if (analyzedFrameCount == 0) add("NO_FRAMES")
        else if (!hasSufficientFrames()) add("LOW_FRAME_COUNT")
        if (analyzedFrameCount > 0 && faceLostFrameCount * 2 > analyzedFrameCount) add("FACE_OFTEN_LOST")
        if (analysisFrameRate > 0f && analysisFrameRate < LOW_FPS_THRESHOLD) add("LOW_FRAME_RATE")
        if (actualDurationMs <= 0L) add("UNKNOWN_DURATION")
    }

    companion object {
        const val MIN_FRAMES_FOR_CONFIDENCE = 30
        const val LOW_FPS_THRESHOLD = 5f
    }
}

/**
 * Outcome for one category in the assessment loop. [successful] tracks
 * whether this counted toward the 5-successful-assessments goal per the
 * spec's decision logic; [interestRating]/[focusRating] are only present
 * when successful (the app doesn't ask rating questions after an
 * attention-shift interruption). [skipped] marks a category the user
 * repeatedly skipped rather than watched or attention-shifted away from —
 * itself a relevant "abandoned quickly" signal for the recommendation engine.
 */
data class CategoryAssessmentResult(
    val category: AttentionCategory,
    val gazeMetrics: GazeMetrics,
    val successful: Boolean,
    val interestRating: Int? = null, // 1..5
    val focusRating: Int? = null,    // 1..5
    val skipped: Boolean = false,
    /** How many times the user skipped this category before it was retired. */
    val skipCount: Int = 0
)
