package com.focusflow.domain.models

/**
 * Loose signal tags describing *why* a content category tends to hold
 * attention. Several categories can share traits (e.g. Gaming and Cartoon
 * are both partly VISUAL) — GenerateRecommendationsUseCase aggregates
 * these across a person's best-performing categories to infer an overall
 * learning style, rather than mapping one category directly to one style.
 */
enum class AttentionTrait {
    AUDITORY, RHYTHMIC,
    VISUAL, SHORT_FORM, HIGH_STIMULATION,
    INTERACTIVE, GAMIFIED,
    NARRATIVE, EMOTIONAL,
    TEXTUAL, STRUCTURED, CONCEPTUAL
}

/** Which broad cluster a trait belongs to, for tallying dominant style. */
enum class TraitCluster { AUDITORY, VISUAL, INTERACTIVE, NARRATIVE, TEXTUAL }

val attentionCategoryTraits: Map<AttentionCategory, Set<AttentionTrait>> = mapOf(
    AttentionCategory.MUSIC to setOf(AttentionTrait.AUDITORY, AttentionTrait.RHYTHMIC),
    AttentionCategory.GAMING to setOf(AttentionTrait.INTERACTIVE, AttentionTrait.GAMIFIED),
    AttentionCategory.CARTOON to setOf(AttentionTrait.VISUAL, AttentionTrait.SHORT_FORM),
    AttentionCategory.HORROR to setOf(AttentionTrait.VISUAL, AttentionTrait.HIGH_STIMULATION),
    AttentionCategory.SADNESS to setOf(AttentionTrait.NARRATIVE, AttentionTrait.EMOTIONAL),
    AttentionCategory.MELODRAMA to setOf(AttentionTrait.NARRATIVE, AttentionTrait.EMOTIONAL),
    AttentionCategory.ROMANCE to setOf(AttentionTrait.NARRATIVE, AttentionTrait.EMOTIONAL),
    AttentionCategory.SCIENCE_FICTION to setOf(AttentionTrait.VISUAL, AttentionTrait.CONCEPTUAL),
    AttentionCategory.EDUCATION to setOf(AttentionTrait.TEXTUAL, AttentionTrait.STRUCTURED)
)

/**
 * A category's trait clusters with weights summing to 1.0, derived from the
 * trait metadata already in [attentionCategoryTraits] rather than from a
 * second hand-written table — one source of truth, so the two can't drift.
 *
 * A category whose traits all land in one cluster contributes its whole score
 * there (Music -> auditory 1.0). A category straddling two contributes
 * proportionally: Science Fiction's traits are VISUAL and CONCEPTUAL, which
 * cluster as visual and textual, so it contributes 0.5 to each rather than
 * being forced entirely into whichever cluster happened to be listed first.
 * This is what the PID means by weighted contributions for categories with
 * multiple trait clusters.
 */
fun traitClusterWeights(category: AttentionCategory): Map<TraitCluster, Float> {
    val traits = attentionCategoryTraits[category].orEmpty()
    if (traits.isEmpty()) return emptyMap()
    val counts = traits.groupingBy { it.cluster() }.eachCount()
    val total = traits.size.toFloat()
    return counts.mapValues { (_, count) -> count / total }
}

private val traitToCluster: Map<AttentionTrait, TraitCluster> = mapOf(
    AttentionTrait.AUDITORY to TraitCluster.AUDITORY,
    AttentionTrait.RHYTHMIC to TraitCluster.AUDITORY,
    AttentionTrait.VISUAL to TraitCluster.VISUAL,
    AttentionTrait.SHORT_FORM to TraitCluster.VISUAL,
    AttentionTrait.HIGH_STIMULATION to TraitCluster.VISUAL,
    AttentionTrait.INTERACTIVE to TraitCluster.INTERACTIVE,
    AttentionTrait.GAMIFIED to TraitCluster.INTERACTIVE,
    AttentionTrait.NARRATIVE to TraitCluster.NARRATIVE,
    AttentionTrait.EMOTIONAL to TraitCluster.NARRATIVE,
    AttentionTrait.TEXTUAL to TraitCluster.TEXTUAL,
    AttentionTrait.STRUCTURED to TraitCluster.TEXTUAL,
    AttentionTrait.CONCEPTUAL to TraitCluster.TEXTUAL
)

fun AttentionTrait.cluster(): TraitCluster = traitToCluster.getValue(this)

enum class LearningStyle(val displayName: String, val description: String) {
    AUDITORY(
        "Auditory Learner",
        "You focus best when information comes through sound and rhythm — podcasts, spoken explanations, and music-backed study sessions tend to hold your attention."
    ),
    VISUAL(
        "Visual Learner",
        "You focus best with strong visual stimulation — imagery, motion, and short, punchy visual formats tend to hold your attention longest."
    ),
    INTERACTIVE(
        "Interactive Learner",
        "You focus best when you're actively doing something rather than passively watching — hands-on, game-like, or interactive formats work well for you."
    ),
    NARRATIVE(
        "Narrative Learner",
        "You focus best when there's a story or emotional throughline — content framed as a narrative tends to hold your attention longer than dry facts."
    ),
    STRUCTURED(
        "Structured Learner",
        "You focus best with clear, organized, text-based material — structured explanations and step-by-step formats work well for you."
    ),
    MULTIMODAL(
        "Multimodal Learner",
        "You naturally maintain attention with a mix of visual, auditory, and interactive experiences. Learning methods that combine music, gamification, and short visual lessons are likely to improve your engagement and retention."
    )
}
