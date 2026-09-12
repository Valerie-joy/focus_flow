package com.focusflow.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.focusflow.data.local.FocusFlowDatabase
import com.focusflow.data.repository.AdhdDocumentRepository
import com.focusflow.data.repository.AdhdSelfReportRepository
import com.focusflow.ui.screens.onboarding.AdhdAssessmentScreen
import com.focusflow.ui.screens.onboarding.AdhdQuestionScreen
import com.focusflow.ui.screens.onboarding.AdhdUploadScreen
import com.focusflow.ui.screens.onboarding.UploadState
import kotlinx.coroutines.launch

/**
 * Phase 2 (ADHD verification) destinations.
 *
 * Document upload is a real upload to Supabase Storage (see
 * [com.focusflow.data.repository.AdhdDocumentRepository] and
 * SUPABASE_SETUP.md) behind a fast client-side extension pre-filter
 * ([looksLikeValidDocument]) — not a claim that the document has been
 * reviewed as a genuine diagnosis letter; that's still a future clinician
 * review queue, not implemented here. Self-report answers are persisted to
 * Room via [com.focusflow.data.repository.AdhdSelfReportRepository].
 */
fun NavGraphBuilder.onboardingGraph(navController: NavHostController) {
    composable(FocusFlowDestinations.ADHD_QUESTION) {
        AdhdQuestionScreen(
            onHasDiagnosis = { navController.navigate(FocusFlowDestinations.ADHD_UPLOAD) },
            onNoDiagnosis = { navController.navigate(FocusFlowDestinations.ADHD_ASSESSMENT) }
        )
    }

    composable(FocusFlowDestinations.ADHD_UPLOAD) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val documentRepository = remember { AdhdDocumentRepository(context.applicationContext) }
        var uploadState by remember { mutableStateOf<UploadState>(UploadState.Idle) }

        AdhdUploadScreen(
            uploadState = uploadState,
            onFileSelected = { uri ->
                val fileName = uri.lastPathSegment ?: "document"
                if (!looksLikeValidDocument(fileName)) {
                    uploadState = UploadState.Invalid("That file doesn't look like a PDF, PNG, or JPEG. Try another file.")
                } else {
                    uploadState = UploadState.Validating
                    scope.launch {
                        uploadState = documentRepository.uploadDocument(uri, fileName).fold(
                            onSuccess = { UploadState.Valid },
                            onFailure = { e ->
                                UploadState.Invalid(e.message ?: "Couldn't upload the document. Check your connection and try again.")
                            }
                        )
                    }
                }
            },
            onContinue = { navController.navigate(FocusFlowDestinations.USER_REGISTRATION) },
            onDismissError = { uploadState = UploadState.Idle },
            onRetry = { uploadState = UploadState.Idle }
        )
    }

    composable(FocusFlowDestinations.ADHD_ASSESSMENT) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val repository = remember {
            AdhdSelfReportRepository(FocusFlowDatabase.getInstance(context).adhdSelfReportDao())
        }

        AdhdAssessmentScreen(
            onComplete = { answers ->
                scope.launch {
                    repository.saveAnswers(answers)
                    navController.navigate(FocusFlowDestinations.USER_REGISTRATION)
                }
            },
            onExit = { navController.popBackStack() }
        )
    }
}

/** Fast client-side pre-filter before attempting an upload — not a real validation. */
private fun looksLikeValidDocument(fileName: String): Boolean =
    fileName.substringAfterLast('.', "").lowercase() in setOf("pdf", "png", "jpg", "jpeg")
