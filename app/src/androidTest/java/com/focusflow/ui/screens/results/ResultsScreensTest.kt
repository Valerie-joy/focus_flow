package com.focusflow.ui.screens.results

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.focusflow.domain.models.AttentionCategory
import com.focusflow.domain.models.CategoryAssessmentResult
import com.focusflow.domain.models.GazeMetrics
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.viewmodel.CategoryRankEntry
import com.focusflow.viewmodel.ProgressPointData
import com.focusflow.viewmodel.ResultsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose tests for the two result-facing screens.
 *
 * These cover the behaviours that are a duty of care rather than a preference,
 * and that a JVM test on the domain layer cannot confirm actually reach the
 * screen: that a provisional session says so, that the limitations notice is
 * present, that an empty history explains itself instead of rendering blank,
 * and that a trend line is withheld until there is enough data to support one.
 *
 * Instrumented — requires a connected device or emulator
 * (`./gradlew :app:connectedDebugAndroidTest`).
 */
@RunWith(AndroidJUnit4::class)
class ResultsScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun result(
        category: AttentionCategory,
        attention: Float,
        successful: Boolean
    ) = CategoryAssessmentResult(
        category = category,
        gazeMetrics = GazeMetrics(attention, 2, null, 12),
        successful = successful,
        interestRating = if (successful) 4 else null,
        focusRating = if (successful) 4 else null
    )

    // ---- AttentionResultsScreen ------------------------------------------

    /**
     * `ResultsUiState.isProvisional` was computed correctly from the start and
     * displayed nowhere, so a profile built on one assessment was presented
     * with the same confidence as one built on five.
     */
    @Test
    fun aProvisionalSessionIsLabelledAsProvisional() {
        composeRule.setContent {
            FocusFlowTheme {
                AttentionResultsScreen(
                    results = listOf(result(AttentionCategory.MUSIC, 90f, successful = true)),
                    onViewFullAnalysis = {},
                    requiredAssessments = 5
                )
            }
        }

        composeRule.onNode(hasText("1 of 5", substring = true)).assertIsDisplayed()
    }

    @Test
    fun aCompleteSessionShowsNoProvisionalNotice() {
        val fiveSuccesses = listOf(
            AttentionCategory.MUSIC,
            AttentionCategory.GAMING,
            AttentionCategory.EDUCATION,
            AttentionCategory.CARTOON,
            AttentionCategory.HORROR
        ).map { result(it, 85f, successful = true) }

        composeRule.setContent {
            FocusFlowTheme {
                AttentionResultsScreen(
                    results = fiveSuccesses,
                    onViewFullAnalysis = {},
                    requiredAssessments = 5
                )
            }
        }

        assertEquals(
            0,
            composeRule.onAllNodesWithSubstring("of 5 assessments").fetchSemanticsNodes().size
        )
    }

    /**
     * The screen sits directly downstream of an ADHD self-report
     * questionnaire, which makes a low number very easy to misread. The
     * limitations notice is part of the result, not optional garnish.
     */
    @Test
    fun resultsAlwaysCarryTheLimitationsNotice() {
        composeRule.setContent {
            FocusFlowTheme {
                AttentionResultsScreen(
                    results = listOf(result(AttentionCategory.MUSIC, 90f, successful = true)),
                    onViewFullAnalysis = {}
                )
            }
        }

        composeRule.onNode(hasText("not a medical test", substring = true)).assertIsDisplayed()
        composeRule.onNode(hasText("cannot diagnose", substring = true)).assertIsDisplayed()
    }

    @Test
    fun aSessionWithNoCompletedCategoriesShowsAnEmptyStateNotAZeroScore() {
        composeRule.setContent {
            FocusFlowTheme {
                AttentionResultsScreen(results = emptyList(), onViewFullAnalysis = {})
            }
        }

        composeRule.onNodeWithText("No categories completed").assertIsDisplayed()
        // A score of 0% for "you completed nothing" would be a measurement claim
        // about a measurement that never happened.
        assertEquals(
            0,
            composeRule.onAllNodesWithSubstring("Session average").fetchSemanticsNodes().size
        )
    }

    // ---- ResultsHistoryScreen --------------------------------------------

    @Test
    fun emptyHistoryExplainsItselfAndOffersAWayForward() {
        var started = false
        composeRule.setContent {
            FocusFlowTheme {
                ResultsHistoryScreen(
                    uiState = ResultsUiState(hasData = false),
                    onBack = {},
                    onDownloadReport = {},
                    onEmailReport = {},
                    onStartAssessment = { started = true }
                )
            }
        }

        composeRule.onNodeWithText("No assessments yet").assertIsDisplayed()
        composeRule.onNodeWithText("Start an assessment").performClick()
        assertEquals(true, started)
    }

    /**
     * Two points always draw a straight line, and a straight line always reads
     * as a trend. The chart is withheld below three sessions.
     */
    @Test
    fun theTrendChartIsWithheldUntilThereAreEnoughSessions() {
        fun stateWith(points: List<ProgressPointData>) = ResultsUiState(
            hasData = true,
            latestSessionDateLabel = "July 25, 2026",
            latestSessionRanking = listOf(CategoryRankEntry("Music", 90)),
            progressHistory = points,
            successfulAssessments = 5
        )

        composeRule.setContent {
            FocusFlowTheme {
                ResultsHistoryScreen(
                    uiState = stateWith(
                        listOf(
                            ProgressPointData("Jul 10", 68),
                            ProgressPointData("Jul 17", 74)
                        )
                    ),
                    onBack = {},
                    onDownloadReport = {},
                    onEmailReport = {}
                )
            }
        }

        assertEquals(
            0,
            composeRule.onAllNodesWithSubstring("Progress over time").fetchSemanticsNodes().size
        )
    }

    @Test
    fun exportButtonsAreBlockedWhileAnExportIsRunning() {
        composeRule.setContent {
            FocusFlowTheme {
                ResultsHistoryScreen(
                    uiState = ResultsUiState(
                        hasData = true,
                        latestSessionRanking = listOf(CategoryRankEntry("Music", 90)),
                        successfulAssessments = 5
                    ),
                    onBack = {},
                    onDownloadReport = {},
                    onEmailReport = {},
                    isExporting = true
                )
            }
        }

        // A second tap used to start a second PDF generation and write a
        // second file.
        composeRule.onNodeWithText("Email report").assertIsNotEnabled()
    }
}

/** Convenience: substring matcher, since the built-in helper needs a full match. */
private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithSubstring(
    text: String
) = onAllNodes(hasText(text, substring = true))
