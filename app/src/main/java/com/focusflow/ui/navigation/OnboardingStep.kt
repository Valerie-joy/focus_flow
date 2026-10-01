package com.focusflow.ui.navigation

/**
 * The real first-run sequence, so a step label can state where the user
 * actually is.
 *
 * `AdhdQuestionScreen` carried `stepLabel: String = "Step 2 of 9"` as a default
 * the navigation layer never overrode. There are not nine onboarding steps and
 * the ADHD question is not the second one, so the only progress indicator in
 * the whole first-run flow was wrong in both numbers — and, being a hardcoded
 * default, it said "2 of 9" no matter which screen showed it.
 *
 * The count here is derived from [entries], so adding or removing a step cannot
 * leave the labels stale.
 *
 * [isOptional] marks the two branches a user can legitimately decline. The
 * brief for this app is explicit that optional steps must be labelled as such;
 * a user who cannot tell whether a step is required will complete it anyway,
 * which is a worse outcome than skipping it.
 */
enum class OnboardingStep(
    val title: String,
    val isOptional: Boolean = false
) {
    /** "Do you have an ADHD diagnosis?" — routes to one of the two branches. */
    BACKGROUND(title = "About you"),

    /**
     * Either the document upload or the self-report questionnaire. They are
     * alternatives, so they share one step: presenting them as separate steps
     * would make the total change depending on which branch was taken.
     */
    BACKGROUND_DETAIL(title = "Your background", isOptional = true),

    /** Name, age, sex — the profile the app actually needs. */
    PROFILE(title = "Your profile"),

    /** Camera permission. */
    CAMERA_ACCESS(title = "Camera access"),

    /**
     * Gaze calibration. Genuinely optional: without it the app measures
     * attention from eye deflection instead, which every calibration exit path
     * already falls back to.
     */
    CALIBRATION(title = "Eye tracking setup", isOptional = true);

    /** 1-based position, for display. */
    val number: Int get() = ordinal + 1

    companion object {
        val total: Int get() = entries.size

        /**
         * Which step a route belongs to, or null for routes outside the
         * first-run flow. Keeps the mapping in one place instead of each screen
         * hardcoding its own position.
         */
        fun forRoute(route: String?): OnboardingStep? = when (route) {
            FocusFlowDestinations.ADHD_QUESTION -> BACKGROUND
            FocusFlowDestinations.ADHD_UPLOAD,
            FocusFlowDestinations.ADHD_ASSESSMENT -> BACKGROUND_DETAIL
            FocusFlowDestinations.USER_REGISTRATION -> PROFILE
            FocusFlowDestinations.CAMERA_PERMISSION -> CAMERA_ACCESS
            FocusFlowDestinations.CAMERA_CALIBRATION -> CALIBRATION
            else -> null
        }
    }
}
