package com.focusflow.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the "what we noticed" line on the results and history screens.
 *
 * The case that matters is a session where every category scored the same. That
 * is reachable in ordinary use — skipping every category gives them all an
 * identical score — and the old wording then claimed one category was
 * "strongest" and another "dips most" at the very same percentage, asserting a
 * difference the numbers do not show. It was observed on device in exactly that
 * state: "strongest with Music (49%) ... dips most with Romance (49%)".
 */
class ResultsInsightTest {

    private fun entry(category: String, score: Int) = CategoryRankEntry(category, score)

    @Test
    fun `an identical-score session never claims a strongest or weakest category`() {
        val insight = buildInsight(
            listOf(entry("Music", 49), entry("Gaming", 49), entry("Romance", 49))
        )
        assertNotNull(insight)
        assertFalse("must not rank equal scores", insight!!.contains("strongest"))
        assertFalse(insight.contains("dips most"))
        assertTrue(insight.contains("evenly"))
        assertTrue("should still report the level", insight.contains("49"))
    }

    @Test
    fun `a spread narrower than the threshold is still treated as even`() {
        val insight = buildInsight(
            listOf(entry("Music", 72), entry("Gaming", 70), entry("Romance", 69))
        )
        // 3 points apart: real enough to store, too small to narrate as a gap.
        assertFalse(insight!!.contains("strongest"))
    }

    @Test
    fun `a genuine spread is described as one`() {
        val insight = buildInsight(
            listOf(entry("Music", 92), entry("Gaming", 70), entry("Education", 41))
        )
        assertNotNull(insight)
        assertTrue(insight!!.contains("strongest"))
        assertTrue(insight.contains("Music"))
        assertTrue(insight.contains("92"))
        assertTrue(insight.contains("dips most"))
        assertTrue(insight.contains("Education"))
        assertTrue(insight.contains("41"))
    }

    @Test
    fun `the threshold boundary is inclusive of the even wording`() {
        val justUnder = buildInsight(
            listOf(entry("Music", 50), entry("Gaming", 50 - (MIN_MEANINGFUL_SPREAD - 1)))
        )
        assertFalse(justUnder!!.contains("strongest"))

        val atThreshold = buildInsight(
            listOf(entry("Music", 50), entry("Gaming", 50 - MIN_MEANINGFUL_SPREAD))
        )
        assertTrue(atThreshold!!.contains("strongest"))
    }

    @Test
    fun `a single category is not compared against itself`() {
        val insight = buildInsight(listOf(entry("Music", 80)))
        assertNotNull(insight)
        assertFalse(insight!!.contains("dips most"))
    }

    @Test
    fun `no categories produces no insight`() {
        assertNull(buildInsight(emptyList()))
    }
}
