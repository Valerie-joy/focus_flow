package com.focusflow.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.focusflow.data.local.FocusFlowDatabase
import com.focusflow.data.local.UserPreferences
import com.focusflow.data.repository.AssessmentRepository
import com.focusflow.data.repository.ProfileRepository
import com.focusflow.reports.PdfReportGenerator
import com.focusflow.reports.ReportContent
import com.focusflow.reports.ReportProgressRow
import com.focusflow.reports.ReportRankingRow
import com.focusflow.services.DownloadResult
import com.focusflow.services.EmailReportService
import com.focusflow.services.ReportDownloadService
import com.focusflow.ui.screens.dashboard.DashboardScreen
import com.focusflow.ui.screens.onboarding.CameraCalibrationScreen
import com.focusflow.ui.screens.onboarding.CameraPermissionScreen
import com.focusflow.ui.screens.onboarding.UserRegistrationScreen
import com.focusflow.ui.screens.onboarding.sexOptions
import com.focusflow.ui.screens.results.ResultsHistoryScreen
import com.focusflow.viewmodel.DashboardViewModel
import com.focusflow.viewmodel.ResultsViewModel
import kotlinx.coroutines.launch

/**
 * Phase 3 (user registration), Phase 4 (camera calibration), Phase 8
 * (Dashboard), and Phase 9 (Results history + export) destinations.
 * Registration/calibration are kept together since they're a linear
 * continuation of onboarding; Dashboard and Results live here too since
 * they're this graph's natural landing points once an assessment session
 * completes and gets popped off the assessment nested graph.
 */
fun NavGraphBuilder.registrationGraph(navController: NavHostController) {
    composable(FocusFlowDestinations.USER_REGISTRATION) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val profileRepository = remember { ProfileRepository(context.applicationContext) }
        var isSaving by remember { mutableStateOf(false) }

        UserRegistrationScreen(
            // Pre-filled from the account itself, so a user who already typed
            // their name on the Create Account screen just confirms it here.
            initialFullName = remember { profileRepository.signupFullName().orEmpty() },
            initialEmail = remember { profileRepository.signupEmail().orEmpty() },
            isLoading = isSaving,
            onCreateProfile = { form ->
                val sex = sexOptions.getOrElse(form.sexIndex) { "Prefer not to say" }
                // Local copy first so Dashboard/Profile render instantly and
                // still work offline; Supabase is the durable source of truth
                // that survives a reinstall or a new device.
                UserPreferences(context).saveProfile(
                    name = form.fullName,
                    age = form.age,
                    sex = sex,
                    email = form.email
                )
                isSaving = true
                scope.launch {
                    profileRepository.saveOnboardingProfile(
                        fullName = form.fullName,
                        age = form.age,
                        sex = sex,
                        email = form.email
                    )
                    isSaving = false
                    navController.navigate(FocusFlowDestinations.CAMERA_PERMISSION)
                }
            }
        )
    }

    composable(FocusFlowDestinations.CAMERA_PERMISSION) {
        CameraPermissionScreen(
            onPermissionGranted = { navController.navigate(FocusFlowDestinations.CAMERA_CALIBRATION) },
            // CameraPermissionScreen tracks its own permanently-denied state
            // and shows a real settings deep-link button; nothing to do here.
            onPermissionDenied = {}
        )
    }

    composable(FocusFlowDestinations.CAMERA_CALIBRATION) {
        CameraCalibrationScreen(
            onCalibrationComplete = {
                navController.navigate(FocusFlowDestinations.ASSESSMENT_GRAPH)
            }
        )
    }

    composable(FocusFlowDestinations.DASHBOARD) {
        val context = LocalContext.current
        val repository = remember {
            AssessmentRepository(FocusFlowDatabase.getInstance(context).assessmentDao())
        }
        val viewModel: DashboardViewModel = viewModel(
            factory = viewModelFactory {
                initializer { DashboardViewModel(repository) }
            }
        )
        val uiState by viewModel.state.collectAsStateWithLifecycle()
        val userName = remember { UserPreferences(context).getName() ?: "there" }

        DashboardScreen(
            uiState = uiState,
            userName = userName,
            onStartAssessment = { navController.navigate(FocusFlowDestinations.ASSESSMENT_GRAPH) },
            onViewResults = { navController.navigate(FocusFlowDestinations.RESULTS_HISTORY) },
            onViewProfile = { navController.navigate(FocusFlowDestinations.PROFILE) }
        )
    }

    composable(FocusFlowDestinations.RESULTS_HISTORY) {
        val context = LocalContext.current
        val repository = remember {
            AssessmentRepository(FocusFlowDatabase.getInstance(context).assessmentDao())
        }
        val viewModel: ResultsViewModel = viewModel(
            factory = viewModelFactory {
                initializer { ResultsViewModel(repository) }
            }
        )
        val uiState by viewModel.state.collectAsStateWithLifecycle()
        val userName = remember { UserPreferences(context).getName() ?: "there" }
        val scope = rememberCoroutineScope()
        var statusText by remember { mutableStateOf<String?>(null) }

        fun buildReportContent() = ReportContent(
            userName = userName,
            generatedAtMs = System.currentTimeMillis(),
            ranking = uiState.latestSessionRanking.map { ReportRankingRow(it.category, it.score) },
            insightText = uiState.insightText,
            progressHistory = uiState.progressHistory.map { ReportProgressRow(it.dateLabel, it.score) },
            learningStyle = uiState.recommendation?.learningStyle?.displayName,
            learningStyleDescription = uiState.recommendation?.learningStyle?.description,
            studyTechniques = uiState.recommendation?.suggestedStudyTechniques.orEmpty(),
            recommendedContentFormats = uiState.recommendation?.recommendedContentTypes.orEmpty(),
            weeklyGoals = uiState.recommendation?.weeklyGoals.orEmpty(),
            successfulAssessments = uiState.successfulAssessments,
            requiredAssessments = uiState.requiredAssessments,
            isProvisional = uiState.isProvisional
        )

        ResultsHistoryScreen(
            uiState = uiState,
            onBack = { navController.popBackStack() },
            downloadStatus = statusText,
            onDownloadReport = {
                scope.launch {
                    val file = PdfReportGenerator(context).generate(buildReportContent())
                    val result = ReportDownloadService(context).saveToDownloads(file, file.name)
                    statusText = when (result) {
                        is DownloadResult.Success -> "Saved to Downloads"
                        is DownloadResult.Failure -> "Couldn't save report: ${result.message}"
                    }
                }
            },
            onEmailReport = {
                scope.launch {
                    val file = PdfReportGenerator(context).generate(buildReportContent())
                    EmailReportService(context).shareViaEmail(
                        file = file,
                        subject = "Your FocusFlow Assessment Report",
                        body = "Attached is your latest FocusFlow attention assessment report."
                    )
                }
            }
        )
    }
}
