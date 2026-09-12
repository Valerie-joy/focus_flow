package com.focusflow.domain.dataset

import com.focusflow.domain.models.AttentionCategory
import com.focusflow.domain.models.Stimulus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Dataset validation. The shipped library is checked as-is, and the validator
 * itself is checked against deliberately malformed rows — a validator that
 * only ever sees good data proves nothing.
 */
class StimulusDatasetTest {

    private fun stimulus(
        id: String = "music_01",
        category: AttentionCategory = AttentionCategory.MUSIC,
        title: String = "Music clip 1",
        mediaId: String = "abcdefghijk",
        durationLimitSeconds: Int = 45
    ) = Stimulus(id, category, title, mediaId, durationLimitSeconds)

    // --- The shipped dataset ---

    @Test
    fun `shipped dataset passes validation`() {
        val report = StimulusDatasetValidator.validate()
        assertTrue(
            "Shipped stimulus dataset has errors:\n${report.summary()}",
            report.isValid
        )
    }

    @Test
    fun `shipped dataset covers all nine canonical categories`() {
        val report = StimulusDatasetValidator.validate()
        assertEquals(9, AttentionCategory.entries.size)
        assertTrue(
            "Missing: ${report.missingCategories.map { it.displayName }}",
            report.missingCategories.isEmpty()
        )
        AttentionCategory.entries.forEach { category ->
            assertTrue(
                "${category.displayName} has no stimuli",
                StimulusDataset.forCategory(category).isNotEmpty()
            )
        }
    }

    @Test
    fun `shipped dataset has unique stable ids and no placeholder media ids`() {
        val all = StimulusDataset.all
        assertEquals(all.size, all.map { it.id }.distinct().size)
        assertTrue(all.none { it.mediaId.isBlank() })
        assertTrue(all.none { it.mediaId.startsWith("REPLACE_ME") })
    }

    @Test
    fun `every stimulus is retrievable by its stable id`() {
        StimulusDataset.all.forEach { stimulus ->
            assertEquals(stimulus, StimulusDataset.byId(stimulus.id))
        }
        assertNull(StimulusDataset.byId("no_such_id"))
    }

    // --- Validator behaviour on malformed input ---

    @Test
    fun `duplicate ids are reported as an error`() {
        val report = StimulusDatasetValidator.validate(
            listOf(stimulus(id = "dup"), stimulus(id = "dup", mediaId = "zzzzzzzzzzz"))
        )
        assertFalse(report.isValid)
        assertTrue(report.errors.any { it.code == "DUPLICATE_ID" })
    }

    @Test
    fun `empty and malformed media ids are reported as errors`() {
        val empty = StimulusDatasetValidator.validate(listOf(stimulus(mediaId = "")))
        assertTrue(empty.errors.any { it.code == "EMPTY_MEDIA_ID" })

        val malformed = StimulusDatasetValidator.validate(listOf(stimulus(mediaId = "has spaces!")))
        assertTrue(malformed.errors.any { it.code == "MALFORMED_MEDIA_ID" })
    }

    @Test
    fun `placeholder media id is reported as an error`() {
        val report = StimulusDatasetValidator.validate(listOf(stimulus(mediaId = "REPLACE_ME_01")))
        assertTrue(report.errors.any { it.code == "PLACEHOLDER_MEDIA_ID" })
    }

    @Test
    fun `missing categories are reported per category`() {
        val report = StimulusDatasetValidator.validate(listOf(stimulus()))
        assertEquals(8, report.missingCategories.size)
        assertFalse(report.missingCategories.contains(AttentionCategory.MUSIC))
        assertEquals(8, report.errors.count { it.code == "MISSING_CATEGORY" })
    }

    @Test
    fun `out of range duration cap is reported as an error`() {
        assertTrue(
            StimulusDatasetValidator.validate(listOf(stimulus(durationLimitSeconds = 0)))
                .errors.any { it.code == "INVALID_DURATION_LIMIT" }
        )
        assertTrue(
            StimulusDatasetValidator.validate(listOf(stimulus(durationLimitSeconds = 5000)))
                .errors.any { it.code == "INVALID_DURATION_LIMIT" }
        )
    }

    @Test
    fun `blank title is a warning rather than an error`() {
        val report = StimulusDatasetValidator.validate(listOf(stimulus(title = "")))
        assertTrue(report.warnings.any { it.code == "BLANK_TITLE" })
        assertTrue(report.errors.none { it.code == "BLANK_TITLE" })
    }

    @Test
    fun `invalid records are excluded rather than crashing the assessment`() {
        val records = listOf(
            stimulus(id = "good_01", mediaId = "abcdefghijk"),
            stimulus(id = "bad_01", mediaId = "")
        )
        val valid = StimulusDatasetValidator.validRecords(records)
        assertEquals(listOf("good_01"), valid.map { it.id })
    }

    @Test
    fun `validating an empty dataset reports every category missing without throwing`() {
        val report = StimulusDatasetValidator.validate(emptyList())
        assertEquals(0, report.totalRecords)
        assertEquals(9, report.missingCategories.size)
        assertFalse(report.isValid)
    }

    // --- Selection ---

    @Test
    fun `selection returns a clip from the requested category only`() {
        AttentionCategory.entries.forEach { category ->
            val picked = StimulusDataset.selectFor(category, Random(42))
            assertNotNull("No clip selected for ${category.displayName}", picked)
            assertEquals(category, picked!!.category)
        }
    }

    @Test
    fun `selection with a seeded random is deterministic`() {
        val first = StimulusDataset.selectFor(AttentionCategory.HORROR, Random(7))
        val second = StimulusDataset.selectFor(AttentionCategory.HORROR, Random(7))
        assertEquals(first, second)
    }
}
