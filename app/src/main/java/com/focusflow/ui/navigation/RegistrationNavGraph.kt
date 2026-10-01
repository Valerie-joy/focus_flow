package com.focusflow.ui.navigation

import android.util.Log
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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
        var saveError by remember { mutableStateOf<String?>(null) }

        UserRegistrationScreen(
            // Pre-filled from the account itself, so a user who already typed
            // their name on the Create Account screen just confirms it here.
            initialFullName = remember { profileRepository.signupFullName().orEmpty() },
            initialEmail = remember { profileRepository.signupEmail().orEmpty() },
            isLoading = isSaving,
            errorMessage = saveError,
            onCreateProfile = { form ->
                if (isSaving) return@UserRegistrationScreen
                saveError = null
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
                    // The Result was previously discarded and navigation happened
                    // unconditionally: a failed write left the user believing
                    // their profile was saved, and it was absent on their next
                    // device. The local copy above still stands, so the app keeps
                    // working offline — but the user is told and can retry.
                    val result = profileRepository.saveOnboardingProfile(
                        fullName = form.fullName,
                        age = form.age,
                        sex = sex,
                        email = form.email
                    )
                    isSaving = false
                    result.fold(
                        onSuccess = {
                            navController.navigate(FocusFlowDestinations.CAMERA_PERMISSION) {
                                launchSingleTop = true
                            }
                        },
                        onFailure = { error ->
                            Log.w("Registration", "Profile save failed", error)
                            saveError = profileSaveErrorMessage(error)
                        }
                    )
                }
            }
        )
    }

    composable(FocusFlowDestinations.CAMERA_PERMISSION) {
        CameraPermissionScreen(
            onPermissionGranted = {
                navController.navigate(FocusFlowDestinations.CAMERA_CALIBRATION) {
                    launchSingleTop = true
                }
            },
            // This was an empty lambda. That was harmless while the screen's
            // only action was "Allow", but it made the "Not now" exit added
            // later a button that did nothing — the exact dead end it was meant
            // to remove. Declining now completes onboarding and lands on the
            // Dashboard, where the app is still usable and the permission can be
            // granted later from an assessment.
            onPermissionDenied = {
                navController.navigate(FocusFlowDestinations.DASHBOARD) {
                    popUpTo(FocusFlowDestinations.ADHD_QUESTION) { inclusive = true }
                    launchSingleTop = true
                }
            }
        )
    }

    composable(FocusFlowDestinations.CAMERA_CALIBRATION) {
        CameraCalibrationScreen(
            onCalibrationComplete = {
                // popUpTo the start of onboarding: the assessment is the end of
                // the first-run flow, and backing out of it should reach the
                // Dashboard rather than walking the setup steps again.
                navController.navigate(FocusFlowDestinations.ASSESSMENT_GRAPH) {
                    launchSingleTop = true
                }
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
        // Guards both export actions. Generating a PDF takes long enough on a
        // mid-range device for an impatient second tap to land, which used to
        // start a second generation and write a second file.
        var isExporting by remember { mutableStateOf(false) }

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
            isExporting = isExporting,
            onStartAssessment = {
                navController.navigate(FocusFlowDestinations.ASSESSMENT_GRAPH)
            },
            onDownloadReport = {
                if (isExporting) return@ResultsHistoryScreen
                isExporting = true
                statusText = null
                scope.launch {
                    val content = buildReportContent()
                    // PDF rendering and the MediaStore write are both blocking
                    // file I/O. They ran on the main thread here, which is a
                    // dropped-frames-to-ANR risk on a multi-page report; the
                    // coroutine from rememberCoroutineScope is Main-dispatched,
                    // so `launch` alone did not move them off it.
                    statusText = withContext(Dispatchers.IO) {
                        runCatching {
                            val file = PdfReportGenerator(context).generate(content)
                            ReportDownloadService(context).saveToDownloads(file, file.name)
                        }.fold(
                            onSuccess = { result ->
                                when (result) {
                                    is DownloadResult.Success -> "Report saved to your Downloads folder."
                                    is DownloadResult.Failure -> "Couldn't save the report: ${result.message}"
                                }
                            },
                            onFailure = { "Couldn't create the report. Please try again." }
                        )
                    }
                    isExporting = false
                }
            },
            onEmailReport = {
                if (isExporting) return@ResultsHistoryScreen
                isExporting = true
                statusText = null
                scope.launch {
                    val content = buildReportContent()
                    val fileResult = withContext(Dispatchers.IO) {
                        runCatching { PdfReportGenerator(context).generate(content) }
                    }
                    statusText = fileResult.fold(
                        onSuccess = { file ->
                            // The chooser must be started from the main thread.
                            EmailReportService(context).shareViaEmail(
                                file = file,
                                subject = "Your FocusFlow Assessment Report",
                                body = "Attached is your latest FocusFlow attention assessment report."
                            ).fold(
                                onSuccess = { null }, // the chooser is its own feedback
                                onFailure = { error ->
                                    "Couldn't open your email app. ${error.message.orEmpty()}".trim()
                                }
                            )
                        },
                        onFailure = { "Couldn't create the report. Please try again." }
                    )
                    isExporting = false
                }
            }
        )
    }
}

/**
 * Why the profile write failed, without putting backend text on screen.
 *
 * The local copy has already been saved at this point, so the app is usable
 * either way; the message says what did and did not happen so the user can
 * decide whether to retry now or later.
 */
private fun profileSaveErrorMessage(error: Throwable): String {
    val chain = buildList {
        var cause: Throwable? = error
        while (cause != null && none { it === cause }) {
            add(cause)
            cause = cause.cause
        }
    }
    return when {
        chain.any { it is UnknownHostException } ->
            "Saved on this device, but we couldn't sync your profile \u2014 no internet " +
                "connection. Try again when you're back online."
        chain.any { it is SocketTimeoutException || it is IOException } ->
            "Saved on this device, but syncing your profile timed out. Try again shortly."
        else -> "Saved on this device, but we couldn't sync your profile. Please try again."
    }
}
