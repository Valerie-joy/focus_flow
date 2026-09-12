package com.focusflow.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One category's result from a completed assessment session, persisted to
 * Room so the Dashboard can read history after the assessment nested nav
 * graph — and its in-memory AssessmentViewModel — is popped off the back
 * stack. [sessionId] groups all categories from the same run together.
 *
 * Privacy boundary: this is the *only* thing that outlives a clip, and it is
 * derived numbers exclusively. No frame, no landmark, no per-frame gaze
 * sample and no image of any kind is written here or anywhere else — those
 * exist in memory for the duration of one frame and are discarded. Nothing in
 * this table is transmitted off the device.
 */
@Entity(tableName = "assessment_history")
data class AssessmentHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val timestampMs: Long,
    /** Canonical AttentionCategory.id — a stable key, never a display string. */
    val categoryId: String,
    val score: Int, // 0..100, from CalculateAttentionScoreUseCase
    val successful: Boolean,
    val interestRating: Int?,
    val focusRating: Int?,
    val skipped: Boolean = false,
    val skipCount: Int = 0,

    // Component sub-scores, so a stored score stays explainable after the fact
    // rather than being an unauditable single number.
    val screenAttentionScore: Int = 0,
    val distractionDelayScore: Int = 0,
    val gazeStabilityScore: Int = 0,
    val blinkConsistencyScore: Int = 0,

    // Underlying measurements.
    val screenAttentionPercentage: Float = 0f,
    val gazeShiftCount: Int = 0,
    val blinkCount: Int = 0,
    val firstDistractionMs: Long? = null,
    val actualDurationMs: Long = 0L,

    /** Measured per-session, per the PID's "must be logged rather than assumed". */
    val analysisFrameRate: Float = 0f,
    val analyzedFrameCount: Int = 0,
    /** Comma-separated GazeMetrics.qualityFlags(); empty when the capture was clean. */
    val qualityFlags: String = ""
)
