package com.focusflow.domain.models

/**
 * A single item in the in-app self-reflection questionnaire shown to users
 * who don't have an existing diagnosis (Phase 2, "No" branch).
 *
 * IMPORTANT — scope of this questionnaire:
 * This is a lightweight, in-app self-reflection tool loosely modeled on the
 * *style* of validated adult ADHD self-report screeners (e.g. ASRS-style
 * frequency questions), but it is NOT a validated instrument and does NOT
 * produce a diagnosis. Copy shown around this flow should say so explicitly
 * (see the disclaimer step wired into AdhdAssessmentScreen). Results should
 * only ever be framed as "things worth discussing with a professional if
 * they resonate," never as a clinical determination.
 */
data class AdhdSelfReportQuestion(
    val id: String,
    val prompt: String
)

enum class FrequencyAnswer(val label: String, val score: Int) {
    NEVER("Never", 0),
    RARELY("Rarely", 1),
    SOMETIMES("Sometimes", 2),
    OFTEN("Often", 3),
    VERY_OFTEN("Very often", 4)
}

/**
 * Default question bank. Kept short (6 items) since this is a self-reflection
 * gate, not a full clinical intake — a real deployment should have this
 * reviewed by a clinician before shipping.
 */
val defaultAdhdSelfReportQuestions = listOf(
    AdhdSelfReportQuestion("q1", "How often do you have trouble wrapping up the final details of a task, once the challenging parts are done?"),
    AdhdSelfReportQuestion("q2", "How often do you have difficulty getting things in order when you have a task that requires organization?"),
    AdhdSelfReportQuestion("q3", "How often do you have problems remembering appointments or obligations?"),
    AdhdSelfReportQuestion("q4", "How often do you fidget or squirm with your hands or feet when you have to sit down for a long time?"),
    AdhdSelfReportQuestion("q5", "How often do you feel overly active and compelled to do things, as if driven by a motor?"),
    AdhdSelfReportQuestion("q6", "How often do you find yourself talking too much in social situations?")
)
