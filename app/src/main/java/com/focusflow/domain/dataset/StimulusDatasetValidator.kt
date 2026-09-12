package com.focusflow.domain.dataset

import com.focusflow.domain.models.AttentionCategory
import com.focusflow.domain.models.Stimulus
import com.focusflow.domain.models.TraitCluster
import com.focusflow.domain.models.traitClusterWeights

/** A single problem found in the stimulus dataset. */
data class DatasetIssue(
    val severity: Severity,
    val code: String,
    val message: String,
    /** Stimulus id or category id the issue attaches to, when there is one. */
    val subject: String? = null
) {
    enum class Severity { ERROR, WARNING }
}

data class DatasetValidationReport(
    val totalRecords: Int,
    val recordsPerCategory: Map<AttentionCategory, Int>,
    val missingCategories: List<AttentionCategory>,
    val issues: List<DatasetIssue>
) {
    val errors: List<DatasetIssue> get() = issues.filter { it.severity == DatasetIssue.Severity.ERROR }
    val warnings: List<DatasetIssue> get() = issues.filter { it.severity == DatasetIssue.Severity.WARNING }
    val isValid: Boolean get() = errors.isEmpty()

    fun summary(): String = buildString {
        appendLine("Stimulus dataset: $totalRecords records across ${recordsPerCategory.size} categories")
        if (missingCategories.isNotEmpty()) {
            appendLine("Missing categories: ${missingCategories.joinToString { it.displayName }}")
        }
        issues.forEach { appendLine("[${it.severity}] ${it.code}: ${it.message}") }
    }
}

/**
 * Checks the curated stimulus library for the structural guarantees the rest
 * of the app assumes: all nine canonical categories present, stable unique
 * ids, real media identifiers, a sane duration cap, and trait-cluster
 * metadata that actually resolves.
 *
 * This *reports* rather than throws. A malformed row must never be able to
 * crash the app mid-assessment — [validRecords] gives callers the subset that
 * is safe to present, while the issue list keeps the bad rows visible so they
 * get fixed rather than silently dropped.
 *
 * Deliberately not a build-time-only check: it is cheap, and running it on the
 * real shipped dataset is the only way to catch a bad row that was added after
 * the last test run.
 */
object StimulusDatasetValidator {

    private val MEDIA_ID_PATTERN = Regex("^[A-Za-z0-9_-]{5,64}$")

    fun validate(records: List<Stimulus> = StimulusDataset.all): DatasetValidationReport {
        val issues = mutableListOf<DatasetIssue>()

        val presentCategories = records.map { it.category }.toSet()
        val missing = AttentionCategory.entries.filterNot { it in presentCategories }
        missing.forEach {
            issues += DatasetIssue(
                severity = DatasetIssue.Severity.ERROR,
                code = "MISSING_CATEGORY",
                message = "Canonical category '${it.displayName}' has no stimulus records.",
                subject = it.id
            )
        }

        // Duplicate stable ids — two rows claiming the same identity is the one
        // failure that silently corrupts every downstream lookup.
        records.groupBy { it.id }
            .filterValues { it.size > 1 }
            .forEach { (id, dupes) ->
                issues += DatasetIssue(
                    severity = DatasetIssue.Severity.ERROR,
                    code = "DUPLICATE_ID",
                    message = "Stimulus id '$id' is used by ${dupes.size} records.",
                    subject = id
                )
            }

        // Duplicate media ids are a warning, not an error: the same clip
        // legitimately could be reused, but it is nearly always a copy-paste slip.
        records.groupBy { it.mediaId }
            .filterValues { it.size > 1 }
            .forEach { (mediaId, dupes) ->
                issues += DatasetIssue(
                    severity = DatasetIssue.Severity.WARNING,
                    code = "DUPLICATE_MEDIA_ID",
                    message = "Media id '$mediaId' appears in ${dupes.size} records " +
                        "(${dupes.joinToString { it.id }}).",
                    subject = mediaId
                )
            }

        records.forEach { record -> issues += validateRecord(record) }

        AttentionCategory.entries.forEach { category ->
            val weights = traitClusterWeights(category)
            if (weights.isEmpty()) {
                issues += DatasetIssue(
                    severity = DatasetIssue.Severity.ERROR,
                    code = "MISSING_TRAIT_CLUSTER",
                    message = "Category '${category.displayName}' has no trait-cluster mapping, " +
                        "so it cannot contribute to learning-style inference.",
                    subject = category.id
                )
            }
            val unknown = weights.keys.filterNot { it in TraitCluster.entries }
            unknown.forEach {
                issues += DatasetIssue(
                    severity = DatasetIssue.Severity.ERROR,
                    code = "INVALID_TRAIT_CLUSTER",
                    message = "Category '${category.displayName}' maps to unknown trait cluster '$it'.",
                    subject = category.id
                )
            }
        }

        return DatasetValidationReport(
            totalRecords = records.size,
            recordsPerCategory = records.groupingBy { it.category }.eachCount(),
            missingCategories = missing,
            issues = issues
        )
    }

    private fun validateRecord(record: Stimulus): List<DatasetIssue> {
        val issues = mutableListOf<DatasetIssue>()

        if (record.id.isBlank()) {
            issues += DatasetIssue(
                DatasetIssue.Severity.ERROR, "BLANK_ID",
                "A stimulus in '${record.category.displayName}' has a blank id.", record.mediaId
            )
        }
        if (record.mediaId.isBlank()) {
            issues += DatasetIssue(
                DatasetIssue.Severity.ERROR, "EMPTY_MEDIA_ID",
                "Stimulus '${record.id}' has an empty media identifier.", record.id
            )
        } else if (!MEDIA_ID_PATTERN.matches(record.mediaId)) {
            issues += DatasetIssue(
                DatasetIssue.Severity.ERROR, "MALFORMED_MEDIA_ID",
                "Stimulus '${record.id}' has a malformed media identifier '${record.mediaId}'.",
                record.id
            )
        }
        // Placeholder rows from an unfinished edit must never reach a user.
        if (record.mediaId.startsWith("REPLACE_ME", ignoreCase = true) ||
            record.mediaId.startsWith("TODO", ignoreCase = true)
        ) {
            issues += DatasetIssue(
                DatasetIssue.Severity.ERROR, "PLACEHOLDER_MEDIA_ID",
                "Stimulus '${record.id}' still holds a placeholder media identifier.", record.id
            )
        }
        if (record.title.isBlank()) {
            issues += DatasetIssue(
                DatasetIssue.Severity.WARNING, "BLANK_TITLE",
                "Stimulus '${record.id}' has no title.", record.id
            )
        }
        if (record.durationLimitSeconds !in 1..600) {
            issues += DatasetIssue(
                DatasetIssue.Severity.ERROR, "INVALID_DURATION_LIMIT",
                "Stimulus '${record.id}' has an out-of-range duration cap " +
                    "(${record.durationLimitSeconds}s).",
                record.id
            )
        }
        return issues
    }

    /**
     * The subset safe to present: rows with no ERROR-level issue against them,
     * with duplicate ids collapsed to their first occurrence. An invalid row is
     * excluded from the assessment rather than crashing it.
     */
    fun validRecords(records: List<Stimulus> = StimulusDataset.all): List<Stimulus> {
        val badIds = validate(records).errors.mapNotNull { it.subject }.toSet()
        return records
            .filterNot { it.id in badIds }
            .distinctBy { it.id }
    }
}
