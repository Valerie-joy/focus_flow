package com.focusflow.domain.dataset

import com.focusflow.domain.models.AttentionCategory
import com.focusflow.domain.models.Stimulus

/**
 * The curated stimulus library: fixed, manually verified content identifiers
 * across the nine canonical categories, with a stable id per clip.
 *
 * Every [Stimulus.mediaId] here is carried over verbatim from the project's
 * original hand-vetted library — each one was confirmed to exist and to allow
 * embedding (via the player's oEmbed endpoint) and watched end to end by a
 * person before being added. None of them are generated, and nothing in this
 * app ever searches for a clip at runtime: the PID requires a deterministic,
 * reviewable dataset, so a clip that isn't in this file cannot be shown.
 *
 * To add a clip:
 *   1. Pick the video and copy its content identifier from the URL.
 *   2. Confirm embedding is allowed (Share > Embed on the video itself).
 *   3. Watch the whole thing. This app puts a specific clip in front of a
 *      specific person's eyes for up to [Stimulus.DEFAULT_DURATION_LIMIT_SECONDS]
 *      seconds — "a reputable channel" is not a substitute for having watched
 *      *this* clip.
 *   4. Give it the next free stable id for its category and add the row.
 *
 * Rows added through the [addCategory] helper get a positional label and
 * `titleVerified = false`, which the validator reports until someone does the
 * review pass. A clip that *has* been re-reviewed should be written out in
 * full instead, with its real title:
 *
 *     Stimulus(
 *         id = "science_fiction_03",
 *         category = AttentionCategory.SCIENCE_FICTION,
 *         title = "<the clip's actual title>",
 *         mediaId = "<verified content id>",
 *         titleVerified = true
 *     )
 *
 * Known gap: Science Fiction currently holds fewer clips than every other
 * category, which the validator reports as THIN_CATEGORY. Closing it needs
 * clips someone has actually watched — it is deliberately not closed by
 * generating identifiers.
 *
 * Age note: the approved PID states that age-appropriate filtering is not
 * implemented in the current build — all nine categories are offered to every
 * user regardless of age, and that is a recorded known limitation rather than
 * an oversight. Horror, Sadness, Melodrama and Romance in particular would
 * need real editorial judgement before any age gating is introduced; adding
 * such a filter is an enhancement beyond the approved build, not a
 * reinstatement of something that was always meant to be here.
 */
object StimulusDataset {

    /**
     * Category presentation order is randomized per session (see
     * AssessmentViewModel), but this dataset itself is deterministic and
     * declaration-ordered: the same id always means the same clip.
     */
    val all: List<Stimulus> = buildList {
        addCategory(
            AttentionCategory.MUSIC,
            "yebNIHKAC4A", "e_04ZrNroTo", "A0azOIk0Kvg", "RTWhvp_OD6s", "ILRs2r6lcHY"
        )
        addCategory(
            AttentionCategory.GAMING,
            "HsnJ9HaYvPA", "ZggspAgMsyE", "MX7rTq9pupU", "PGH5qB7HqVU"
        )
        addCategory(
            AttentionCategory.CARTOON,
            "P9r_T1BP0zc", "GNoVgbPNL2E", "NXrfWL6joj0", "YpI0jgqNJGc"
        )
        addCategory(
            AttentionCategory.SCIENCE_FICTION,
            "VzFpg271sm8", "Rvns5DaW-ug"
        )
        addCategory(
            AttentionCategory.EDUCATION,
            "OoiOZ8YPvjk", "xyQY8a-ng6g", "tFbuCrBftyA", "2W85Dwxx218", "II5h6uJPvvs"
        )
        addCategory(
            AttentionCategory.HORROR,
            "FUQhNGEu2KA", "hUr_jObR1vE", "e1VNlS39me0", "JV0pNU9htnw"
        )
        addCategory(
            AttentionCategory.SADNESS,
            "f5CcgFTO274", "Bl1FOKpFY2Q", "kNw8V_Fkw28", "2REkk9SCRn0", "mV_w9Zv-TdM"
        )
        addCategory(
            AttentionCategory.MELODRAMA,
            "bVZl51CqUXk", "K_ctJ0dptQU", "olK5STwZjfc", "AWONhF5nQyo", "A8aH51z0xZs"
        )
        addCategory(
            AttentionCategory.ROMANCE,
            "XrqSF2OOz_M", "qGARfYz2X8U", "t5aziLb8IrI", "_qTZRD1_ybQ"
        )
    }

    private val byCategory: Map<AttentionCategory, List<Stimulus>> = all.groupBy { it.category }

    /** Every clip for a category, in stable declaration order. */
    fun forCategory(category: AttentionCategory): List<Stimulus> =
        byCategory[category].orEmpty()

    fun byId(id: String): Stimulus? = all.firstOrNull { it.id == id }

    /**
     * Picks one clip for a category.
     *
     * Which of a category's vetted clips a given session gets is drawn at
     * random, but note what is and isn't random here: the *dataset* is fixed
     * and every candidate was reviewed by hand. Randomization never
     * synthesizes a clip, a category or a score — it only decides the order
     * and selection of already-approved material, exactly as the PID requires.
     *
     * [random] is injectable so tests can pin the choice.
     */
    fun selectFor(
        category: AttentionCategory,
        random: kotlin.random.Random = kotlin.random.Random.Default
    ): Stimulus? {
        val candidates = forCategory(category)
        return if (candidates.isEmpty()) null else candidates[random.nextInt(candidates.size)]
    }

    private fun MutableList<Stimulus>.addCategory(
        category: AttentionCategory,
        vararg mediaIds: String
    ) {
        mediaIds.forEachIndexed { index, mediaId ->
            add(
                Stimulus(
                    id = "${category.id}_%02d".format(index + 1),
                    category = category,
                    title = "${category.displayName} clip ${index + 1}",
                    mediaId = mediaId,
                    // Positional label, not the clip's real title — reported
                    // by the validator until someone re-reviews it.
                    titleVerified = false
                )
            )
        }
    }
}
