package com.focusflow.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Category normalization. The whole point of the canonical id is that a
 * spelling variant can never become a tenth category, so this pins every
 * variant the project has actually produced.
 */
class AttentionCategoryTest {

    @Test
    fun `the nine canonical categories are present with PID display names`() {
        assertEquals(9, AttentionCategory.entries.size)
        assertEquals(
            listOf(
                "Music", "Gaming", "Cartoon", "Science Fiction", "Education",
                "Horror", "Sadness", "Melodrama", "Romance"
            ),
            AttentionCategory.entries.map { it.displayName }
        )
    }

    @Test
    fun `category ids are unique and stable`() {
        val ids = AttentionCategory.entries.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertEquals("science_fiction", AttentionCategory.SCIENCE_FICTION.id)
        assertEquals("sadness", AttentionCategory.SADNESS.id)
    }

    @Test
    fun `science fiction spelling variants all collapse to one category`() {
        listOf(
            "Science Fiction", "science_fiction", "SCIENCE FICTION",
            "Sci-Fi", "sci fi", "SCI_FI", "scifi", "SciFi"
        ).forEach { variant ->
            assertEquals(
                "Variant '$variant' did not normalize",
                AttentionCategory.SCIENCE_FICTION,
                AttentionCategory.fromRawOrNull(variant)
            )
        }
    }

    @Test
    fun `legacy persisted enum names still resolve`() {
        // Rows written before the categories were renamed to the PID wording.
        assertEquals(AttentionCategory.SADNESS, AttentionCategory.fromRawOrNull("SAD"))
        assertEquals(AttentionCategory.SCIENCE_FICTION, AttentionCategory.fromRawOrNull("SCI_FI"))
        assertEquals(AttentionCategory.MUSIC, AttentionCategory.fromRawOrNull("MUSIC"))
    }

    @Test
    fun `whitespace and casing are ignored`() {
        assertEquals(AttentionCategory.HORROR, AttentionCategory.fromRawOrNull("  horror  "))
        assertEquals(AttentionCategory.MELODRAMA, AttentionCategory.fromRawOrNull("MeLoDrAmA"))
    }

    @Test
    fun `unknown and empty input returns null rather than inventing a category`() {
        assertNull(AttentionCategory.fromRawOrNull("Documentary"))
        assertNull(AttentionCategory.fromRawOrNull(""))
        assertNull(AttentionCategory.fromRawOrNull("   "))
        assertNull(AttentionCategory.fromRawOrNull(null))
    }

    @Test
    fun `displayNameFor canonicalizes stored ids and passes through unknown values`() {
        assertEquals("Science Fiction", AttentionCategory.displayNameFor("science_fiction"))
        assertEquals("Sadness", AttentionCategory.displayNameFor("SAD"))
        // Unknown input is echoed rather than dropped, so a stale row still
        // shows something rather than an empty cell.
        assertEquals("Documentary", AttentionCategory.displayNameFor("Documentary"))
    }

    @Test
    fun `every category maps to trait clusters whose weights sum to one`() {
        AttentionCategory.entries.forEach { category ->
            val weights = traitClusterWeights(category)
            assertEquals(
                "${category.displayName} has no trait clusters",
                true,
                weights.isNotEmpty()
            )
            assertEquals(
                "${category.displayName} weights do not sum to 1",
                1.0f,
                weights.values.sum(),
                1e-4f
            )
        }
    }

    @Test
    fun `a category spanning two clusters splits its contribution`() {
        // Science Fiction is VISUAL + CONCEPTUAL, which cluster as visual and
        // textual — it must not be forced whole into either.
        val weights = traitClusterWeights(AttentionCategory.SCIENCE_FICTION)
        assertEquals(2, weights.size)
        assertEquals(0.5f, weights.getValue(TraitCluster.VISUAL), 1e-4f)
        assertEquals(0.5f, weights.getValue(TraitCluster.TEXTUAL), 1e-4f)
    }

    @Test
    fun `a single-cluster category contributes all of its weight there`() {
        val weights = traitClusterWeights(AttentionCategory.MUSIC)
        assertEquals(1, weights.size)
        assertEquals(1.0f, weights.getValue(TraitCluster.AUDITORY), 1e-4f)
    }
}
