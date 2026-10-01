package com.focusflow.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the onboarding step model, which replaced a hardcoded `"Step 2 of 9"`
 * default that was wrong in both numbers and identical on every screen.
 *
 * The point of these tests is that the numbers can no longer drift from the real
 * sequence: the total is derived, the positions are unique and contiguous, and
 * every first-run route maps to exactly one step.
 */
class OnboardingStepTest {

    @Test
    fun `total matches the number of declared steps`() {
        assertEquals(OnboardingStep.entries.size, OnboardingStep.total)
    }

    @Test
    fun `step numbers are one-based contiguous and unique`() {
        val numbers = OnboardingStep.entries.map { it.number }
        assertEquals((1..OnboardingStep.total).toList(), numbers)
    }

    @Test
    fun `no step number exceeds the total`() {
        // The failure mode of the old hardcoded label: a position outside the
        // range, producing "Step 2 of 9" against a five-step flow.
        OnboardingStep.entries.forEach { step ->
            assertTrue(
                "${step.name} has number ${step.number} but total is ${OnboardingStep.total}",
                step.number in 1..OnboardingStep.total
            )
        }
    }

    @Test
    fun `every first-run route maps to a step`() {
        listOf(
            FocusFlowDestinations.ADHD_QUESTION,
            FocusFlowDestinations.ADHD_UPLOAD,
            FocusFlowDestinations.ADHD_ASSESSMENT,
            FocusFlowDestinations.USER_REGISTRATION,
            FocusFlowDestinations.CAMERA_PERMISSION,
            FocusFlowDestinations.CAMERA_CALIBRATION
        ).forEach { route ->
            assertNotNull("no step for $route", OnboardingStep.forRoute(route))
        }
    }

    /**
     * The upload and the questionnaire are alternatives, not sequential steps.
     * If they mapped to different steps the total would appear to change
     * depending on which branch the user took.
     */
    @Test
    fun `the two background branches share one step`() {
        assertEquals(
            OnboardingStep.forRoute(FocusFlowDestinations.ADHD_UPLOAD),
            OnboardingStep.forRoute(FocusFlowDestinations.ADHD_ASSESSMENT)
        )
    }

    @Test
    fun `routes outside the first-run flow have no step`() {
        listOf(
            FocusFlowDestinations.DASHBOARD,
            FocusFlowDestinations.LOGIN,
            FocusFlowDestinations.PROFILE,
            FocusFlowDestinations.ASSESSMENT_PLAYBACK,
            null
        ).forEach { route ->
            assertNull("unexpected step for $route", OnboardingStep.forRoute(route))
        }
    }

    @Test
    fun `optional steps are exactly the ones a user can decline`() {
        val optional = OnboardingStep.entries.filter { it.isOptional }.toSet()
        assertEquals(
            setOf(OnboardingStep.BACKGROUND_DETAIL, OnboardingStep.CALIBRATION),
            optional
        )
        // The profile and camera-access steps are required to run an assessment
        // at all, so labelling them optional would be misleading.
        assertFalse(OnboardingStep.PROFILE.isOptional)
        assertFalse(OnboardingStep.CAMERA_ACCESS.isOptional)
    }

    @Test
    fun `every step has a usable title`() {
        OnboardingStep.entries.forEach { step ->
            assertTrue("${step.name} has a blank title", step.title.isNotBlank())
        }
    }
}
