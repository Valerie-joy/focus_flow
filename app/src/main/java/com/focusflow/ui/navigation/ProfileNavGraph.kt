package com.focusflow.ui.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.focusflow.data.local.AppSettings
import com.focusflow.data.local.FocusFlowDatabase
import com.focusflow.data.local.UserPreferences
import com.focusflow.data.repository.AssessmentRepository
import com.focusflow.data.repository.AuthRepository
import com.focusflow.domain.models.AttentionCategory
import com.focusflow.domain.models.CategoryAssessmentResult
import com.focusflow.domain.models.GazeMetrics
import com.focusflow.domain.usecases.CategoryAttentionScore
import com.focusflow.domain.usecases.ScoreComponents
import com.focusflow.services.EmailReportService
import com.focusflow.ui.components.PremiumDialog
import com.focusflow.ui.screens.profile.HelpSupportScreen
import com.focusflow.ui.screens.profile.NotificationSettingsScreen
import com.focusflow.ui.screens.profile.NotificationSettingsState
import com.focusflow.ui.screens.profile.PersonalInfo
import com.focusflow.ui.screens.profile.PersonalInformationScreen
import com.focusflow.ui.screens.profile.PrivacySettingsScreen
import com.focusflow.ui.screens.profile.ProfileScreen
import com.focusflow.ui.screens.results.RecommendationsScreen
import com.focusflow.viewmodel.AuthViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Phase 10 destinations: the Profile hub plus its five sub-screens.
 * "Your suggestions" rebuilds CategoryAttentionScore entries directly
 * from the latest persisted session's exact stored scores (see the inline
 * comment) rather than routing through CalculateAttentionScoreUseCase
 * again, so the numbers shown match what the user already saw in Results.
 */
fun NavGraphBuilder.profileGraph(navController: NavHostController) {
    composable(FocusFlowDestinations.PROFILE) {
        val context = LocalContext.current
        val prefs = remember { UserPreferences(context) }
        // Sign-out goes through the ViewModel so the request runs on
        // viewModelScope. It previously ran on rememberCoroutineScope() with a
        // popUpTo(0) navigation on the very next line, which tore this
        // composable down and cancelled the request mid-flight: the user
        // appeared to be signed out, but the Supabase session often survived,
        // so the next launch saw an authenticated session and routed them
        // straight back to the Dashboard.
        val authViewModel: AuthViewModel = viewModel(
            factory = viewModelFactory {
                initializer { AuthViewModel(AuthRepository(context.applicationContext)) }
            }
        )
        val authState by authViewModel.state.collectAsStateWithLifecycle()
        var darkModeOverride by remember { mutableStateOf(prefs.getDarkModeOverride()) }

        ProfileScreen(
            userName = prefs.getName() ?: "there",
            userEmail = prefs.getEmail(),
            darkModeOverride = darkModeOverride,
            onDarkModeOverrideChange = { newValue ->
                darkModeOverride = newValue
                prefs.setDarkModeOverride(newValue)
                AppSettings.darkModeOverride.value = newValue
            },
            onBack = { navController.popBackStack() },
            onPersonalInformation = { navController.navigate(FocusFlowDestinations.PERSONAL_INFORMATION) },
            onAssessmentHistory = { navController.navigate(FocusFlowDestinations.RESULTS_HISTORY) },
            onAiRecommendations = { navController.navigate(FocusFlowDestinations.PROFILE_RECOMMENDATIONS) },
            onNotifications = { navController.navigate(FocusFlowDestinations.NOTIFICATION_SETTINGS) },
            onPrivacy = { navController.navigate(FocusFlowDestinations.PRIVACY_SETTINGS) },
            onHelpSupport = { navController.navigate(FocusFlowDestinations.HELP_SUPPORT) },
            isSigningOut = authState.isLoading,
            onLogout = {
                // Navigation waits for the sign-out to finish, so the local
                // cache is only cleared once the session actually is.
                authViewModel.signOut {
                    prefs.clearAll()
                    navController.navigate(FocusFlowDestinations.WELCOME) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        )
    }

    composable(FocusFlowDestinations.PERSONAL_INFORMATION) {
        val context = LocalContext.current
        val prefs = remember { UserPreferences(context) }
        PersonalInformationScreen(
            info = PersonalInfo(
                fullName = prefs.getName() ?: "—",
                age = prefs.getAge(),
                sex = prefs.getSex(),
                email = prefs.getEmail()
            ),
            onBack = { navController.popBackStack() }
        )
    }

    composable(FocusFlowDestinations.NOTIFICATION_SETTINGS) {
        val context = LocalContext.current
        val prefs = remember { UserPreferences(context) }
        var state by remember {
            mutableStateOf(
                NotificationSettingsState(
                    pushEnabled = prefs.isPushEnabled(),
                    dailyReminderEnabled = prefs.isDailyReminderEnabled(),
                    weeklySummaryEnabled = prefs.isWeeklySummaryEnabled()
                )
            )
        }
        NotificationSettingsScreen(
            state = state,
            onBack = { navController.popBackStack() },
            onPushChanged = { enabled ->
                prefs.setPushEnabled(enabled)
                state = state.copy(pushEnabled = enabled)
            },
            onDailyReminderChanged = { enabled ->
                prefs.setDailyReminderEnabled(enabled)
                state = state.copy(dailyReminderEnabled = enabled)
            },
            onWeeklySummaryChanged = { enabled ->
                prefs.setWeeklySummaryEnabled(enabled)
                state = state.copy(weeklySummaryEnabled = enabled)
            }
        )
    }

    composable(FocusFlowDestinations.PRIVACY_SETTINGS) {
        val context = LocalContext.current
        val prefs = remember { UserPreferences(context) }
        var analyticsEnabled by remember { mutableStateOf(prefs.isAnonymizedAnalyticsEnabled()) }
        PrivacySettingsScreen(
            anonymizedAnalyticsEnabled = analyticsEnabled,
            onBack = { navController.popBackStack() },
            onAnonymizedAnalyticsChanged = { enabled ->
                prefs.setAnonymizedAnalyticsEnabled(enabled)
                analyticsEnabled = enabled
            }
        )
    }

    composable(FocusFlowDestinations.HELP_SUPPORT) {
        val context = LocalContext.current
        var supportError by remember { mutableStateOf<String?>(null) }

        HelpSupportScreen(
            onBack = { navController.popBackStack() },
            onContactSupport = {
                // The Result was discarded, so on a device with no mail app the
                // button did nothing at all and said nothing about why.
                EmailReportService(context).contactSupport().onFailure {
                    supportError = "No email app is set up on this device. You can reach us " +
                        "at support@focusflow.app."
                }
            }
        )

        supportError?.let { message ->
            PremiumDialog(
                title = "Couldn't open your email app",
                message = message,
                onDismiss = { supportError = null }
            )
        }
    }

    composable(FocusFlowDestinations.PROFILE_RECOMMENDATIONS) {
        val context = LocalContext.current
        val repository = remember {
            AssessmentRepository(FocusFlowDatabase.getInstance(context).assessmentDao())
        }
        var scoredEntries by remember { mutableStateOf<List<CategoryAttentionScore>>(emptyList()) }

        LaunchedEffect(Unit) {
            val history = repository.observeHistory().first()
            val latestSessionId = history.maxByOrNull { it.timestampMs }?.sessionId
            scoredEntries = history
                .filter { it.sessionId == latestSessionId }
                // A row whose category no longer resolves is dropped rather
                // than crashing the screen — see AttentionCategory.fromRawOrNull.
                .mapNotNull { entity ->
                    val category = AttentionCategory.fromRawOrNull(entity.categoryId)
                        ?: return@mapNotNull null
                    CategoryAttentionScore(
                        result = CategoryAssessmentResult(
                            category = category,
                            gazeMetrics = GazeMetrics(
                                screenAttentionPercentage = entity.screenAttentionPercentage,
                                gazeShiftCount = entity.gazeShiftCount,
                                firstDistractionMs = entity.firstDistractionMs,
                                blinkCount = entity.blinkCount,
                                actualDurationMs = entity.actualDurationMs,
                                analyzedFrameCount = entity.analyzedFrameCount,
                                analysisFrameRate = entity.analysisFrameRate
                            ),
                            successful = entity.successful,
                            interestRating = entity.interestRating,
                            focusRating = entity.focusRating,
                            skipped = entity.skipped,
                            skipCount = entity.skipCount
                        ),
                        // The stored score is authoritative — it is what the
                        // user was shown — so it is carried through as-is
                        // rather than recomputed from the restored metrics.
                        score = entity.score,
                        preciseScore = entity.score.toDouble(),
                        components = ScoreComponents(
                            screenAttention = entity.screenAttentionScore,
                            distractionDelay = entity.distractionDelayScore,
                            gazeStability = entity.gazeStabilityScore,
                            blinkConsistency = entity.blinkConsistencyScore,
                            interest = entity.interestRating,
                            focus = entity.focusRating
                        )
                    )
                }
        }

        RecommendationsScreen(
            scoredEntries = scoredEntries,
            onContinue = { navController.popBackStack() }
        )
    }
}
