package com.focusflow.domain.models

/**
 * One curated clip in the stimulus library.
 *
 * The approved PID calls for "a curated stimulus library of nine content
 * categories delivered through an embedded video player using fixed, manually
 * verified content identifiers" — so every clip is a reviewable record with a
 * stable [id], never a programmatic search result.
 *
 * [id] is what code, persistence and tests key on; [mediaId] is the embedded
 * player's content identifier and may in principle be re-pointed (e.g. if an
 * uploader removes a clip) without the stimulus itself changing identity.
 *
 * [durationLimitSeconds] is the per-clip cap from the PID. The clip is cut at
 * this point regardless of the source video's real length — five uncapped
 * clips would otherwise mean 15-50 minutes of forced viewing, which is a poor
 * ask generally and self-defeating in an attention assessment.
 */
data class Stimulus(
    val id: String,
    val category: AttentionCategory,
    /**
     * A human-readable label for this dataset row.
     *
     * Rows carried over from the original library have positional labels
     * ("Music clip 1") rather than the source videos' real titles: that
     * library stored bare content identifiers with no titles, and inventing
     * titles for clips nobody re-verified would make the dataset less
     * trustworthy, not more. See [titleVerified].
     */
    val title: String,
    /** Embedded-player content identifier. Manually verified; never generated. */
    val mediaId: String,
    val durationLimitSeconds: Int = DEFAULT_DURATION_LIMIT_SECONDS,
    /**
     * True only when a person has re-watched this clip and written its real
     * title. Defaults to false so the gap is visible by default rather than
     * assumed closed — the validator reports every unverified row, so "we
     * still owe this dataset a review pass" is a fact the tooling states
     * rather than something buried in a commit message.
     *
     * When you re-review a clip, replace the positional label with the real
     * title and set this to true.
     */
    val titleVerified: Boolean = false
) {
    companion object {
        /** Matches the assessment screen's long-standing 45-second clip cap. */
        const val DEFAULT_DURATION_LIMIT_SECONDS = 45
    }
}
