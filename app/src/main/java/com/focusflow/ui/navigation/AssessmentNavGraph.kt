package com.focusflow.ui.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.focusflow.ui.screens.assessment.AttentionAssessmentScreen
import com.focusflow.ui.screens.assessment.AttentionShiftedScreen
import com.focusflow.ui.screens.assessment.PostVideoRatingScreen
import com.focusflow.ui.screens.results.AttentionResultsScreen
import com.focusflow.ui.screens.results.FullAnalysisScreen
import com.focusflow.ui.screens.results.RecommendationsScreen
import com.focusflow.viewmodel.AssessmentPhase
import com.focusflow.viewmodel.AssessmentViewModel
import kotlinx.coroutines.launch

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
            val scope = rememberCoroutineScope()
            val repository = remember {
                AssessmentRepository(FocusFlowDatabase.getInstance(context).assessmentDao())
            }
            val scoreUseCase = remember { CalculateAttentionScoreUseCase() }

            when (state.phase) {
                AssessmentPhase.COMPLETE, AssessmentPhase.EXHAUSTED -> {
                    LaunchedEffect(state.phase) {
                        // Save now, at the true end of the session, rather than
                        // waiting for the final "Continue" tap on Recommendations —
                        // three more screens sit between here and there with no
                        // BackHandler, so waiting risks silently losing a finished
                        // session if the user backs out along the way.
                        scope.launch { repository.saveSession(scoreUseCase.scoreAll(state.results)) }
                        navController.navigate(FocusFlowDestinations.ATTENTION_RESULTS)
                    }
                }
                AssessmentPhase.ATTENTION_SHIFTED -> {
                    LaunchedEffect(state.phase) {
                        navController.navigate(FocusFlowDestinations.ASSESSMENT_SHIFTED)
                    }
                }
                AssessmentPhase.RATING -> {
                    LaunchedEffect(state.phase) {
                        navController.navigate(FocusFlowDestinations.ASSESSMENT_RATING)
                    }
                }
                AssessmentPhase.PLAYING -> {
                    val category = state.currentCategory
                    if (category != null) {
                        AttentionAssessmentScreen(
                            category = category,
                            onAttentionMaintained = viewModel::onAttentionMaintained,
                            onAttentionShifted = viewModel::onAttentionShifted,
                            onSkip = viewModel::skipCategory
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

            AttentionResultsScreen(
                results = state.results,
                onViewFullAnalysis = { navController.navigate(FocusFlowDestinations.FULL_ANALYSIS) }
            )
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
