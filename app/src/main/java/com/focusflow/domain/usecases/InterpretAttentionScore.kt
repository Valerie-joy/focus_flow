package com.focusflow.domain.usecases

/**
 * Turns an attention score into words a person can act on.
 *
 * The results screens previously showed bare percentages. A number on its own
 * in an assessment context is the worst possible presentation: the user has to
 * invent a meaning for it, and the meaning they invent is usually clinical
 * ("is 62% bad? do I have something?"). Every band below therefore describes
 * *what was observed*, never what it implies about the person.
 *
 * This is presentation logic, not scoring. It never recomputes or adjusts a
 * score — [com.focusflow.domain.usecases.CalculateAttentionScoreUseCase]
 * remains the only thing that produces one. Keeping it here rather than in a
 * composable means the phrasing is unit-testable and identical across the
 * results screen, the history screen and the exported PDF.
 */
object InterpretAttentionScore {

    /**
     * Descriptive bands. The boundaries are presentation thresholds chosen to
     * split the observed score range into four readable groups — they are not
     * diagnostic cut-offs and carry no clinical meaning.
     */
    enum class Band(val label: String) {
        VERY_STRONG("Very steady"),
        STRONG("Steady"),
        MIXED("Mixed"),
        VARIABLE("Variable")
    }

    fun bandFor(score: Int): Band = when {
        score >= 85 -> Band.VERY_STRONG
        score >= 70 -> Band.STRONG
        score >= 50 -> Band.MIXED
        else -> Band.VARIABLE
    }

    /**
     * One sentence describing the observation behind a score, phrased about
     * the *content category* rather than about the person.
     */
    fun summaryFor(score: Int, categoryLabel: String): String = when (bandFor(score)) {
        Band.VERY_STRONG ->
            "Your gaze stayed on $categoryLabel content almost the whole time, with few breaks."
        Band.STRONG ->
            "You held attention on $categoryLabel content well, with occasional glances away."
        Band.MIXED ->
            "Your attention moved on and off $categoryLabel content through the clip."
        Band.VARIABLE ->
            "Your gaze left $categoryLabel content often, or left it early."
    }

    /**
     * Plain-language meaning for each metric the results screens show.
     *
     * These explain what was *measured*, in the units the user saw. Where a
     * metric is easy to misread as a verdict (blink rate especially, which
     * people associate with lying or fatigue), the explanation says what it is
     * actually used for.
     */
    fun explainScreenAttention(percentage: Float): String =
        "Share of the clip where your eyes were on the screen — ${percentage.toInt()}% of the time."

    fun explainGazeShifts(count: Int): String = when {
        count == 0 -> "Your gaze didn't leave the screen during this clip."
        count == 1 -> "Your gaze left the screen once and came back."
        else -> "Your gaze left the screen and came back $count times."
    }

    fun explainFirstDistraction(firstDistractionMs: Long?): String = when {
        firstDistractionMs == null -> "You watched the whole clip without a measured break."
        firstDistractionMs < 5_000 -> "Your first look away came within the first few seconds."
        else -> "Your first look away came after ${firstDistractionMs / 1000} seconds."
    }

    fun explainBlinks(count: Int): String =
        "$count blinks were detected. This is used to tell a blink apart from a genuine " +
            "look away, not as a measure of focus on its own."

    /**
     * The limitation notice shown alongside every result, on screen and in the
     * exported PDF.
     *
     * This app measures gaze behaviour during short video clips. That is a
     * long way from any clinical instrument, and the results screens sit
     * immediately downstream of an ADHD self-report questionnaire — which
     * makes it very easy for a user to read a low score as a diagnosis. Saying
     * so explicitly is the only responsible presentation.
     */
    const val LIMITATIONS: String =
        "FocusFlow measures where your eyes go during short video clips. It is not a " +
            "medical test and cannot diagnose anything, including ADHD. Results vary with " +
            "lighting, tiredness, how you held your phone, and how interesting you found " +
            "each clip. Use them to notice patterns in what holds your attention — not as " +
            "an assessment of ability."

    /**
     * Why a profile is provisional, or null once it is not.
     *
     * [com.focusflow.viewmodel.ResultsUiState.isProvisional] has existed and
     * been computed correctly since the results screens were written, but
     * nothing ever displayed it — so a profile built on one assessment was
     * presented with exactly the same confidence as one built on five.
     */
    fun provisionalNotice(successfulAssessments: Int, requiredAssessments: Int): String? {
        if (successfulAssessments >= requiredAssessments) return null
        val remaining = requiredAssessments - successfulAssessments
        val clips = if (remaining == 1) "one more category" else "$remaining more categories"
        return "Based on $successfulAssessments of $requiredAssessments assessments. " +
            "Complete $clips for a fuller picture — these results may shift."
    }
}
