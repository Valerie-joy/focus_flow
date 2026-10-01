package com.focusflow.ui.navigation

import android.util.Log
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
import com.focusflow.data.repository.PickedDocument
import com.focusflow.ui.screens.onboarding.AdhdUploadScreen
import com.focusflow.ui.screens.onboarding.UploadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

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
                // Metadata comes from the content provider, not from the URI.
                // See PickedDocument: the previous extension check read
                // `uri.lastPathSegment`, which for a content URI is a provider
                // document id with no extension, so it rejected every file.
                val picked = PickedDocument.resolve(context, uri)
                uploadState = when {
                    picked == null -> UploadState.Invalid(
                        "That file couldn't be opened. Try picking it again."
                    )
                    !picked.isAcceptedType -> UploadState.Invalid(
                        "That file is a ${picked.mimeType ?: "type we don't recognise"}. " +
                            "Please choose a PDF, PNG, or JPEG."
                    )
                    picked.isTooLarge -> UploadState.Invalid(
                        "That file is too large. Please choose one under 10 MB."
                    )
                    else -> UploadState.Validating
                }
                if (picked != null && uploadState is UploadState.Validating) {
                    scope.launch {
                        // Reading the file and uploading it are both blocking
                        // IO; they ran on the main-dispatched composable scope.
                        uploadState = withContext(Dispatchers.IO) {
                            documentRepository
                                .uploadDocument(picked.uri, picked.displayName)
                                .fold(
                                    onSuccess = { UploadState.Valid },
                                    // e.message here is a Supabase/Ktor string.
                                    // It is logged, not shown.
                                    onFailure = { e ->
                                        Log.w("AdhdUpload", "Document upload failed", e)
                                        UploadState.Invalid(uploadFailureMessage(e))
                                    }
                                )
                        }
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

/**
 * Turns an upload failure into something actionable, without putting backend
 * text on screen.
 *
 * The connectivity cases are by far the most common here, and they are the ones
 * the user can do something about; everything else gets one honest sentence.
 */
private fun uploadFailureMessage(error: Throwable): String {
    val chain = buildList {
        var cause: Throwable? = error
        while (cause != null && none { it === cause }) {
            add(cause)
            cause = cause.cause
        }
    }
    return when {
        chain.any { it is UnknownHostException } ->
            "No internet connection. Connect and try the upload again."
        chain.any { it is SocketTimeoutException } ->
            "The upload timed out. Check your connection and try again."
        chain.any { it is IOException } ->
            "Couldn't reach the server to upload your document. Try again shortly."
        // Thrown by AdhdDocumentRepository when there is no signed-in user.
        chain.any { it is IllegalStateException } ->
            error.message ?: "Couldn't upload the document. Please try again."
        else -> "Couldn't upload the document. Please try again."
    }
}
