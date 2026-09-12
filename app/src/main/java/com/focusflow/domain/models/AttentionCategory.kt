package com.focusflow.domain.models

/**
 * The nine canonical content categories shown one at a time during the
 * attention assessment, in the naming fixed by the approved PID:
 * Music, Gaming, Cartoon, Science Fiction, Education, Horror, Sadness,
 * Melodrama, Romance.
 *
 * [id] is the stable identifier — it is what gets persisted, matched and
 * compared. [displayName] is presentation only and must never be used as a
 * key, because the same category has historically been written several
 * different ways ("Sci-Fi", "SCIENCE FICTION", "sci fi"). [fromRaw] folds all
 * of those onto one canonical constant so a spelling variant can never split
 * into a second pseudo-category.
 *
 * Age note: the approved PID records that age-appropriate filtering is NOT
 * implemented in the current build — all nine categories are presented to
 * every user regardless of age, and this is a stated known limitation carried
 * into the next iteration. Nothing here filters by age; see
 * [com.focusflow.domain.dataset.StimulusDataset] for the same note.
 */
enum class AttentionCategory(val id: String, val displayName: String) {
    MUSIC("music", "Music"),
    GAMING("gaming", "Gaming"),
    CARTOON("cartoon", "Cartoon"),
    SCIENCE_FICTION("science_fiction", "Science Fiction"),
    EDUCATION("education", "Education"),
    HORROR("horror", "Horror"),
    SADNESS("sadness", "Sadness"),
    MELODRAMA("melodrama", "Melodrama"),
    ROMANCE("romance", "Romance");

    companion object {
        /**
         * Historic spellings that must resolve to a canonical constant rather
         * than become categories of their own. Keys are already normalized by
         * [normalizeKey], so "Sci-Fi", "sci fi" and "SCIENCE FICTION" all
         * arrive here as "scifi"/"sciencefiction".
         *
         * `sad`/`SAD` and `sciFi`/`SCI_FI` in particular are the enum names
         * this app persisted to Room before the categories were renamed to the
         * PID's wording, so they must keep resolving for existing local rows.
         */
        private val ALIASES: Map<String, AttentionCategory> = mapOf(
            "scifi" to SCIENCE_FICTION,
            "sciencefiction" to SCIENCE_FICTION,
            "sciencefi" to SCIENCE_FICTION,
            "sf" to SCIENCE_FICTION,
            "sad" to SADNESS,
            "sadness" to SADNESS,
            "drama" to MELODRAMA,
            "educational" to EDUCATION,
            "cartoons" to CARTOON,
            "games" to GAMING,
            "gameplay" to GAMING
        )

        /** Lowercase, strip everything that isn't a letter or digit. */
        private fun normalizeKey(raw: String): String =
            raw.lowercase().filter { it.isLetterOrDigit() }

        /**
         * Resolves any reasonable spelling of a category — canonical id, enum
         * name, display name, or a known historic variant — to its canonical
         * constant. Returns null for genuinely unknown input so callers can
         * report a malformed record instead of silently inventing a category.
         */
        fun fromRawOrNull(raw: String?): AttentionCategory? {
            val key = normalizeKey(raw?.trim().orEmpty())
            if (key.isEmpty()) return null
            return entries.firstOrNull { normalizeKey(it.id) == key }
                ?: entries.firstOrNull { normalizeKey(it.name) == key }
                ?: entries.firstOrNull { normalizeKey(it.displayName) == key }
                ?: ALIASES[key]
        }

        /** Canonical display name for a stored/raw category string. */
        fun displayNameFor(raw: String): String = fromRawOrNull(raw)?.displayName ?: raw
    }
}
