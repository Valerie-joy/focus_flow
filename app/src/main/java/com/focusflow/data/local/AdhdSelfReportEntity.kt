package com.focusflow.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One completed self-reflection questionnaire run (Phase 2's "No diagnosis"
 * branch — see [com.focusflow.domain.models.AdhdSelfReportQuestion]).
 * [answersJson] is a `Map<questionId, FrequencyAnswer.name>` serialized via
 * kotlinx.serialization — kept as a single JSON blob rather than one row per
 * question since these answers are never queried individually, only shown
 * back to the person or exported as a whole.
 */
@Entity(tableName = "adhd_self_report")
data class AdhdSelfReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val answersJson: String
)
