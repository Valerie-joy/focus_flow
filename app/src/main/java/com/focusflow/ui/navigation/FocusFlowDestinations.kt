package com.focusflow.ui.navigation

/**
 * Central route registry. Keeping every screen's route in one place avoids
 * magic strings scattered across NavHost wiring as more phases get built.
 */
object FocusFlowDestinations {
    // Phase 1 — Auth
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val REGISTER = "register"

    // Phase 2 — ADHD verification (stubs for now, wired in a later stage)
    const val ADHD_QUESTION = "adhd_question"
    const val ADHD_UPLOAD = "adhd_upload"
    const val ADHD_ASSESSMENT = "adhd_assessment"

    // Phase 3 — Registration
    const val USER_REGISTRATION = "user_registration"

    // Phase 4+ — Calibration, assessment, dashboard, etc. added in later stages
    const val CAMERA_PERMISSION = "camera_permission"
    const val CAMERA_CALIBRATION = "camera_calibration"

    // Phase 5 — Adaptive attention assessment
    const val ASSESSMENT_GRAPH = "assessment_graph"
    const val ASSESSMENT_PLAYBACK = "assessment_playback"
    const val ASSESSMENT_SHIFTED = "assessment_shifted"
    const val ASSESSMENT_RATING = "assessment_rating"

    // Phase 6 — AI analysis / results
    const val ATTENTION_RESULTS = "attention_results"
    const val FULL_ANALYSIS = "full_analysis"

    // Phase 7 — Personalized recommendations
    const val RECOMMENDATIONS = "recommendations"

    // Phase 9 — Results history (PDF/email export)
    const val RESULTS_HISTORY = "results_history"

    // Phase 10 — Profile
    const val PROFILE = "profile"
    const val PERSONAL_INFORMATION = "personal_information"
    const val NOTIFICATION_SETTINGS = "notification_settings"
    const val PRIVACY_SETTINGS = "privacy_settings"
    const val HELP_SUPPORT = "help_support"
    // Distinct from RECOMMENDATIONS (nested inside ASSESSMENT_GRAPH) since
    // Navigation Compose route strings must be unique across the whole graph.
    const val PROFILE_RECOMMENDATIONS = "profile_recommendations"

    const val DASHBOARD = "dashboard"
}
