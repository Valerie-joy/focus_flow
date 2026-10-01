package com.focusflow.ui.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.focusflow.data.local.FocusFlowDatabase
import com.focusflow.data.repository.AssessmentRepository
import com.focusflow.domain.usecases.CalculateAttentionScoreUseCase
import com.focusflow.ui.components.PremiumDialog
import com.focusflow.ui.screens.assessment.AttentionAssessmentScreen
import com.focusflow.ui.screens.assessment.AttentionShiftedScreen
import com.focusflow.ui.screens.assessment.PostVideoRatingScreen
import com.focusflow.ui.screens.results.AttentionResultsScreen
import com.focusflow.ui.screens.results.FullAnalysisScreen
import com.focusflow.ui.screens.results.RecommendationsScreen
import com.focusflow.viewmodel.AssessmentPhase
import com.focusflow.viewmodel.AssessmentViewModel

/**
 * Phase 5 (adaptive attention assessment) as a *nested* nav graph so its
 * three screens can share one [AssessmentViewModel] instance — scoped to
 * [FocusFlowDestinations.ASSESSMENT_GRAPH]'s own back stack entry rather
 * than each individual screen's — via `viewModel(parentEntry)`. That's what
 * lets state (current category, results so far, successful count) survive
 * bouncing between Playback -> Shifted -> Playback -> Rating -> Playback...
 * without any manual state-passing through nav arguments.
 *
 * On COMPLETE (5 successes) or EXHAUSTED (ran out of untested categories
 * before reaching 5 — an edge case the spec doesn't explicitly cover),
 * navigates to DASHBOARD. Swap that for Phase 6 (AI Analysis) once it's
 * built; the collected results in AssessmentSessionState are exactly what
 * that phase needs to consume.
 */
fun NavGraphBuilder.assessmentGraph(navController: NavHostController) {
    navigation(
        startDestination = FocusFlowDestinations.ASSESSMENT_PLAYBACK,
        route = FocusFlowDestinations.ASSESSMENT_GRAPH
    ) {
        composable(FocusFlowDestinations.ASSESSMENT_PLAYBACK) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(FocusFlowDestinations.ASSESSMENT_GRAPH)
            }
            val viewModel: AssessmentViewModel = viewModel(parentEntry)
            val state by viewModel.state.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val repository = remember {
                AssessmentRepository(FocusFlowDatabase.getInstance(context).assessmentDao())
            }
            val scoreUseCase = remember { CalculateAttentionScoreUseCase() }

            when (state.phase) {
                AssessmentPhase.COMPLETE, AssessmentPhase.EXHAUSTED -> {
                    LaunchedEffect(state.phase) {
                        // Save at the true end of the session rather than
                        // waiting for the final "Continue" on Recommendations —
                        // three screens sit between here and there, so waiting
                        // risks silently losing a finished session if the user
                        // backs out along the way.
                        //
                        // The write now lives on the ViewModel's scope and is
                        // latched against repeats; see persistSessionOnce for
                        // the duplicate-session and cancelled-write bugs that
                        // fixes.
                        viewModel.persistSessionOnce { results ->
                            repository.saveSession(scoreUseCase.scoreAll(results))
                        }
                        // popUpTo: a finished assessment must not be reachable
                        // by pressing Back from its own results. Leaving
                        // playback on the stack meant Back re-entered a screen
                        // whose phase was still COMPLETE, which re-fired this
                        // very effect.
                        navController.navigate(FocusFlowDestinations.ATTENTION_RESULTS) {
                            popUpTo(FocusFlowDestinations.ASSESSMENT_PLAYBACK) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
                AssessmentPhase.ATTENTION_SHIFTED -> {
                    LaunchedEffect(state.phase) {
                        // launchSingleTop: a recomposition arriving before the
                        // navigation settles would otherwise stack a second
                        // copy of the same screen.
                        navController.navigate(FocusFlowDestinations.ASSESSMENT_SHIFTED) {
                            launchSingleTop = true
                        }
                    }
                }
                AssessmentPhase.RATING -> {
                    LaunchedEffect(state.phase) {
                        navController.navigate(FocusFlowDestinations.ASSESSMENT_RATING) {
                            launchSingleTop = true
                        }
                    }
                }
                AssessmentPhase.PLAYING -> {
                    val category = state.currentCategory
                    if (category != null) {
                        AttentionAssessmentScreen(
                            category = category,
                            onAttentionMaintained = viewModel::onAttentionMaintained,
                            onAttentionShifted = viewModel::onAttentionShifted,
                            onSkip = viewModel::skipCategory,
                            onExit = {
                                // Anything already measured is worth keeping and
                                // showing; a session with nothing in it just
                                // leaves, rather than landing the user on an
                                // empty results screen.
                                val hasResults = viewModel.endSessionEarly()
                                if (!hasResults) {
                                    navController.navigate(FocusFlowDestinations.DASHBOARD) {
                                        popUpTo(FocusFlowDestinations.ASSESSMENT_GRAPH) {
                                            inclusive = true
                                        }
                                        launchSingleTop = true
                                    }
                                }
                                // With results, endSessionEarly() moves the
                                // phase to EXHAUSTED and the effect above saves
                                // and routes to results.
                            }
                        )
                    }
                }
            }
        }

        composable(FocusFlowDestinations.ASSESSMENT_SHIFTED) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(FocusFlowDestinations.ASSESSMENT_GRAPH)
            }
            val viewModel: AssessmentViewModel = viewModel(parentEntry)

            AttentionShiftedScreen(
                onContinue = {
                    viewModel.acknowledgeAttentionShift()
                    navController.popBackStack(FocusFlowDestinations.ASSESSMENT_PLAYBACK, inclusive = false)
                }
            )
        }

        composable(FocusFlowDestinations.ASSESSMENT_RATING) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(FocusFlowDestinations.ASSESSMENT_GRAPH)
            }
            val viewModel: AssessmentViewModel = viewModel(parentEntry)
            val state by viewModel.state.collectAsStateWithLifecycle()

            PostVideoRatingScreen(
                successfulCount = state.successfulCount,
                targetCount = state.targetSuccessfulCount,
                onSubmit = { interest, focus ->
                    viewModel.submitRating(interest, focus)
                    navController.popBackStack(FocusFlowDestinations.ASSESSMENT_PLAYBACK, inclusive = false)
                }
            )
        }

        composable(FocusFlowDestinations.ATTENTION_RESULTS) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(FocusFlowDestinations.ASSESSMENT_GRAPH)
            }
            val viewModel: AssessmentViewModel = viewModel(parentEntry)
            val state by viewModel.state.collectAsStateWithLifecycle()
            val persistFailed by viewModel.persistFailed.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val repository = remember {
                AssessmentRepository(FocusFlowDatabase.getInstance(context).assessmentDao())
            }
            val scoreUseCase = remember { CalculateAttentionScoreUseCase() }

            AttentionResultsScreen(
                results = state.results,
                onViewFullAnalysis = {
                    navController.navigate(FocusFlowDestinations.FULL_ANALYSIS) {
                        launchSingleTop = true
                    }
                }
            )

            // A failed save used to be swallowed entirely: the user saw their
            // results, left, and the session was simply absent from history
            // with no explanation. They now get the choice to retry.
            if (persistFailed) {
                PremiumDialog(
                    title = "Couldn't save this session",
                    message = "Your results are shown below, but they weren't written to " +
                        "your history. Retry now, or they'll be lost when you leave.",
                    onDismiss = { viewModel.clearPersistFailure() },
                    primaryActionLabel = "Retry",
                    onPrimaryAction = {
                        viewModel.clearPersistFailure()
                        viewModel.persistSessionOnce { results ->
                            repository.saveSession(scoreUseCase.scoreAll(results))
                        }
                    },
                    secondaryActionLabel = "Not now",
                    onSecondaryAction = { viewModel.clearPersistFailure() }
                )
            }
        }

        composable(FocusFlowDestinations.FULL_ANALYSIS) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(FocusFlowDestinations.ASSESSMENT_GRAPH)
            }
            val viewModel: AssessmentViewModel = viewModel(parentEntry)
            val state by viewModel.state.collectAsStateWithLifecycle()

            FullAnalysisScreen(
                results = state.results,
                onBack = { navController.popBackStack() },
                onContinue = { navController.navigate(FocusFlowDestinations.RECOMMENDATIONS) }
            )
        }

        composable(FocusFlowDestinations.RECOMMENDATIONS) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(FocusFlowDestinations.ASSESSMENT_GRAPH)
            }
            val viewModel: AssessmentViewModel = viewModel(parentEntry)
            val state by viewModel.state.collectAsStateWithLifecycle()

            RecommendationsScreen(
                results = state.results,
                onContinue = {
                    // Already saved when the session reached COMPLETE/EXHAUSTED
                    // (see the ASSESSMENT_PLAYBACK composable above) — just navigate.
                    navController.navigate(FocusFlowDestinations.DASHBOARD) {
                        popUpTo(FocusFlowDestinations.ASSESSMENT_GRAPH) { inclusive = true }
                    }
                }
            )
        }
    }
}
