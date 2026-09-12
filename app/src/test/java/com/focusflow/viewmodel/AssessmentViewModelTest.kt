package com.focusflow.viewmodel

import com.focusflow.domain.models.GazeMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the skip-bound fix for the "endless assessment loop" bug: repeatedly
 * skipping the same category used to requeue it forever with no way to reach
 * COMPLETE/EXHAUSTED, and skips were never recorded as results at all.
 */
class AssessmentViewModelTest {

    private fun zeroMetrics() = GazeMetrics(
        screenAttentionPercentage = 0f,
        gazeShiftCount = 0,
        firstDistractionMs = null,
        blinkCount = 0,
        actualDurationMs = 0L
    )

    @Test
    fun `skipping the same category twice retires it with exactly one recorded result`() {
        val viewModel = AssessmentViewModel()
        val initialCategory = viewModel.state.value.currentCategory!!

        var skipsOfInitial = 0
        var guard = 0
        while (skipsOfInitial < 2) {
            val current = viewModel.state.value.currentCategory!!
            if (current == initialCategory) skipsOfInitial++
            viewModel.skipCategory(zeroMetrics())
            guard++
            assertTrue("Safety limit exceeded — possible infinite loop", guard <= 30)
        }

        val state = viewModel.state.value
        assertFalse(state.remainingCategories.contains(initialCategory))
        assertTrue(state.currentCategory != initialCategory)

        val resultsForInitial = state.results.filter { it.category == initialCategory }
        assertEquals(1, resultsForInitial.size)
        assertTrue(resultsForInitial.first().skipped)
        assertFalse(resultsForInitial.first().successful)
    }

    @Test
    fun `category skipped once then completed successfully produces a single non-skipped result`() {
        val viewModel = AssessmentViewModel()
        val initialCategory = viewModel.state.value.currentCategory!!

        // First (grace) skip — requeues initialCategory, records nothing yet.
        viewModel.skipCategory(zeroMetrics())
        assertTrue(viewModel.state.value.results.none { it.category == initialCategory })

        // Skip every other category once so initialCategory rotates back to current.
        var guard = 0
        while (viewModel.state.value.currentCategory != initialCategory) {
            viewModel.skipCategory(zeroMetrics())
            guard++
            assertTrue("Safety limit exceeded — possible infinite loop", guard <= 20)
        }
        assertTrue(viewModel.state.value.results.none { it.category == initialCategory })

        viewModel.onAttentionMaintained(zeroMetrics())
        viewModel.submitRating(interestRating = 4, focusRating = 5)

        val resultsForInitial = viewModel.state.value.results.filter { it.category == initialCategory }
        assertEquals(1, resultsForInitial.size)
        assertTrue(resultsForInitial.first().successful)
        assertFalse(resultsForInitial.first().skipped)
    }

    @Test
    fun `repeated skips terminate at EXHAUSTED with exactly one recorded result per category`() {
        val viewModel = AssessmentViewModel()
        val totalCategories = viewModel.state.value.remainingCategories.size + 1

        var guard = 0
        while (viewModel.state.value.phase != AssessmentPhase.EXHAUSTED) {
            viewModel.skipCategory(zeroMetrics())
            guard++
            assertTrue("Session never reached EXHAUSTED — possible infinite loop", guard <= 30)
        }

        val state = viewModel.state.value
        assertNull(state.currentCategory)
        assertEquals(totalCategories, state.results.size)
        assertTrue(state.results.all { it.skipped })
        assertEquals(
            "Every category should have exactly one recorded result, never a duplicate",
            totalCategories,
            state.results.map { it.category }.distinct().size
        )
    }

    // --- The five-successful-assessment rule ---

    @Test
    fun `a profile is generated only after exactly five successful assessments`() {
        val viewModel = AssessmentViewModel()
        assertEquals(5, viewModel.state.value.targetSuccessfulCount)

        repeat(4) { index ->
            viewModel.onAttentionMaintained(zeroMetrics())
            viewModel.submitRating(interestRating = 4, focusRating = 4)
            val state = viewModel.state.value
            assertEquals(index + 1, state.successfulCount)
            assertTrue(
                "Session completed early at ${state.successfulCount} successes",
                state.phase != AssessmentPhase.COMPLETE
            )
        }

        viewModel.onAttentionMaintained(zeroMetrics())
        viewModel.submitRating(interestRating = 5, focusRating = 5)

        val finalState = viewModel.state.value
        assertEquals(5, finalState.successfulCount)
        assertEquals(AssessmentPhase.COMPLETE, finalState.phase)
        assertEquals(5, finalState.results.count { it.successful })
    }

    @Test
    fun `attention-shifted sessions do not count toward the five`() {
        val viewModel = AssessmentViewModel()

        repeat(3) {
            viewModel.onAttentionShifted(zeroMetrics())
            viewModel.acknowledgeAttentionShift()
        }

        val state = viewModel.state.value
        assertEquals(0, state.successfulCount)
        assertEquals(3, state.results.size)
        assertTrue(state.results.none { it.successful })
        assertTrue(state.phase != AssessmentPhase.COMPLETE)
    }

    @Test
    fun `skipped categories do not count toward the five`() {
        val viewModel = AssessmentViewModel()
        repeat(6) { viewModel.skipCategory(zeroMetrics()) }
        assertEquals(0, viewModel.state.value.successfulCount)
        assertTrue(viewModel.state.value.results.none { it.successful })
    }

    @Test
    fun `a mix of failures and successes still requires five successes`() {
        val viewModel = AssessmentViewModel()

        viewModel.onAttentionShifted(zeroMetrics())
        viewModel.acknowledgeAttentionShift()
        viewModel.skipCategory(zeroMetrics())

        repeat(4) {
            viewModel.onAttentionMaintained(zeroMetrics())
            viewModel.submitRating(interestRating = 3, focusRating = 3)
        }
        assertTrue(
            "Four successes plus failures must not complete the session",
            viewModel.state.value.phase != AssessmentPhase.COMPLETE
        )

        viewModel.onAttentionMaintained(zeroMetrics())
        viewModel.submitRating(interestRating = 3, focusRating = 3)
        assertEquals(AssessmentPhase.COMPLETE, viewModel.state.value.phase)
        assertEquals(5, viewModel.state.value.successfulCount)
    }

    @Test
    fun `successful results carry both self-reported ratings`() {
        val viewModel = AssessmentViewModel()
        viewModel.onAttentionMaintained(zeroMetrics())
        viewModel.submitRating(interestRating = 2, focusRating = 5)

        val result = viewModel.state.value.results.single()
        assertTrue(result.successful)
        assertEquals(2, result.interestRating)
        assertEquals(5, result.focusRating)
    }

    @Test
    fun `unsuccessful results carry no ratings so scoring redistributes their weight`() {
        val viewModel = AssessmentViewModel()
        viewModel.onAttentionShifted(zeroMetrics())

        val result = viewModel.state.value.results.single()
        assertFalse(result.successful)
        assertNull(result.interestRating)
        assertNull(result.focusRating)
    }

    @Test
    fun `skip counts are recorded on the result for later analysis`() {
        val viewModel = AssessmentViewModel()
        val initialCategory = viewModel.state.value.currentCategory!!

        var guard = 0
        while (viewModel.state.value.results.none { it.category == initialCategory }) {
            viewModel.skipCategory(zeroMetrics())
            guard++
            assertTrue("Safety limit exceeded", guard <= 30)
        }

        val result = viewModel.state.value.results.first { it.category == initialCategory }
        assertEquals(2, result.skipCount)
        assertTrue(result.skipped)
    }

    @Test
    fun `exhausting the category pool terminates gracefully rather than crashing`() {
        val viewModel = AssessmentViewModel()

        var guard = 0
        while (viewModel.state.value.phase != AssessmentPhase.EXHAUSTED) {
            viewModel.onAttentionShifted(zeroMetrics())
            viewModel.acknowledgeAttentionShift()
            guard++
            assertTrue("Session never terminated", guard <= 30)
        }

        val state = viewModel.state.value
        assertEquals(AssessmentPhase.EXHAUSTED, state.phase)
        assertNull(state.currentCategory)
        assertTrue(state.successfulCount < state.targetSuccessfulCount)
        // Acting on an exhausted session must stay a no-op, not throw.
        viewModel.acknowledgeAttentionShift()
        viewModel.skipCategory(zeroMetrics())
        assertEquals(AssessmentPhase.EXHAUSTED, viewModel.state.value.phase)
    }

    @Test
    fun `every session presents categories in a randomized order from the valid dataset`() {
        val orders = (1..40).map { AssessmentViewModel().state.value.let { s ->
            listOfNotNull(s.currentCategory) + s.remainingCategories
        } }

        // Every session offers all nine canonical categories...
        orders.forEach { order ->
            assertEquals(9, order.size)
            assertEquals(9, order.distinct().size)
        }
        // ...but not always in the same order.
        assertTrue(
            "Category presentation order should be randomized per session",
            orders.distinct().size > 1
        )
    }
}
