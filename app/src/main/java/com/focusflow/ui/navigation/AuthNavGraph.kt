package com.focusflow.ui.navigation

import androidx.compose.runtime.LaunchedEffect
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
import android.content.Context
import com.focusflow.auth.GoogleSignInHelper
import com.focusflow.data.local.UserPreferences
import com.focusflow.data.repository.AuthRepository
import com.focusflow.data.repository.ProfileRepository
import com.focusflow.ui.components.PremiumDialog
import com.focusflow.ui.screens.auth.LoginScreen
import com.focusflow.ui.screens.auth.RegisterScreen
import com.focusflow.ui.screens.auth.SplashScreen
import com.focusflow.ui.screens.auth.WelcomeScreen
import com.focusflow.viewmodel.AuthViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch

/**
 * Phase 1 (Auth) destinations. Registered into the app's root NavHost via
 * `NavHost(...) { authGraph(navController) }`.
 *
 * Each screen gets its own [AuthViewModel] instance (same per-destination
 * pattern `RegistrationNavGraph.kt` uses for `DashboardViewModel`/
 * `ResultsViewModel`) rather than a shared one — that's fine here because
 * every instance wraps the same underlying [com.focusflow.data.remote.SupabaseClientProvider]
 * singleton, so session state stays consistent across screens regardless.
 */
fun NavGraphBuilder.authGraph(navController: NavHostController) {
    composable(FocusFlowDestinations.SPLASH) {
        val context = LocalContext.current
        val authViewModel: AuthViewModel = viewModel(
            factory = viewModelFactory {
                initializer { AuthViewModel(AuthRepository(context.applicationContext)) }
            }
        )
        val sessionStatus by authViewModel.sessionStatus.collectAsStateWithLifecycle()
        val profileRepository = remember { ProfileRepository(context.applicationContext) }
        var splashFinished by remember { mutableStateOf(false) }

        SplashScreen(onFinished = { splashFinished = true })

        LaunchedEffect(splashFinished, sessionStatus) {
            if (!splashFinished) return@LaunchedEffect
            when (sessionStatus) {
                // A live session isn't the same as a finished profile — a
                // brand-new account lands here too, and belongs in onboarding
                // rather than on a Dashboard greeting them as "there".
                is SessionStatus.Authenticated -> navController.navigate(
                    postAuthDestination(context.applicationContext, profileRepository)
                ) {
                    popUpTo(FocusFlowDestinations.SPLASH) { inclusive = true }
                }
                is SessionStatus.NotAuthenticated, is SessionStatus.RefreshFailure -> {
                    navController.navigate(FocusFlowDestinations.WELCOME) {
                        popUpTo(FocusFlowDestinations.SPLASH) { inclusive = true }
                    }
                }
                SessionStatus.Initializing -> {
                    // Session is still loading from on-device storage — keep
                    // waiting rather than guessing; this effect re-runs once
                    // sessionStatus changes.
                }
            }
        }
    }

    composable(FocusFlowDestinations.WELCOME) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val authViewModel: AuthViewModel = viewModel(
            factory = viewModelFactory {
                initializer { AuthViewModel(AuthRepository(context.applicationContext)) }
            }
        )
        val googleSignInHelper = remember { GoogleSignInHelper(context) }
        val profileRepository = remember { ProfileRepository(context.applicationContext) }
        var googleErrorMessage by remember { mutableStateOf<String?>(null) }

        WelcomeScreen(
            onLogIn = { navController.navigate(FocusFlowDestinations.LOGIN) },
            onCreateAccount = { navController.navigate(FocusFlowDestinations.REGISTER) },
            onContinueWithGoogle = {
                scope.launch {
                    googleSignInHelper.getGoogleIdToken().fold(
                        onSuccess = { idToken ->
                            authViewModel.signInWithGoogleIdToken(idToken) {
                                scope.launch {
                                    navController.navigate(postAuthDestination(context.applicationContext, profileRepository))
                                }
                            }
                        },
                        onFailure = { e -> googleErrorMessage = e.message ?: "Google sign-in failed." }
                    )
                }
            },
            onContinueWithApple = { /* out of scope: needs an Apple Developer account + Services ID */ }
        )

        if (googleErrorMessage != null) {
            PremiumDialog(
                title = "Couldn't sign in with Google",
                message = googleErrorMessage.orEmpty(),
                onDismiss = { googleErrorMessage = null }
            )
        }
    }

    composable(FocusFlowDestinations.LOGIN) {
        val context = LocalContext.current
        val authViewModel: AuthViewModel = viewModel(
            factory = viewModelFactory {
                initializer { AuthViewModel(AuthRepository(context.applicationContext)) }
            }
        )
        val state by authViewModel.state.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        val profileRepository = remember { ProfileRepository(context.applicationContext) }
        var forgotPasswordNeedsEmail by remember { mutableStateOf(false) }
        var forgotPasswordResultMessage by remember { mutableStateOf<String?>(null) }
        var resendResultMessage by remember { mutableStateOf<String?>(null) }

        LoginScreen(
            onBack = { navController.popBackStack() },
            onForgotPassword = { email ->
                if (email.isBlank()) {
                    forgotPasswordNeedsEmail = true
                } else {
                    authViewModel.forgotPassword(email) { _, message ->
                        forgotPasswordResultMessage = message
                    }
                }
            },
            onSignIn = { email, password ->
                authViewModel.signIn(email, password) {
                    scope.launch {
                        navController.navigate(postAuthDestination(context.applicationContext, profileRepository))
                    }
                }
            },
            isLoading = state.isLoading,
            errorMessage = state.errorMessage
        )

        if (forgotPasswordNeedsEmail) {
            PremiumDialog(
                title = "Enter your email",
                message = "Type your email in the field above, then tap \"Forgot password?\" again.",
                onDismiss = { forgotPasswordNeedsEmail = false }
            )
        }
        if (forgotPasswordResultMessage != null) {
            PremiumDialog(
                title = "Password reset",
                message = forgotPasswordResultMessage.orEmpty(),
                onDismiss = { forgotPasswordResultMessage = null }
            )
        }
        val unconfirmedEmail = state.unconfirmedEmail
        if (unconfirmedEmail != null) {
            PremiumDialog(
                title = "Email not confirmed",
                message = "Check your inbox for the confirmation link, or we can resend it to $unconfirmedEmail.",
                onDismiss = { authViewModel.clearUnconfirmedEmail() },
                primaryActionLabel = "Resend email",
                onPrimaryAction = {
                    authViewModel.resendConfirmationEmail(unconfirmedEmail) { _, message ->
                        resendResultMessage = message
                    }
                    authViewModel.clearUnconfirmedEmail()
                }
            )
        }
        if (resendResultMessage != null) {
            PremiumDialog(
                title = "Confirmation email",
                message = resendResultMessage.orEmpty(),
                onDismiss = { resendResultMessage = null }
            )
        }
    }

    composable(FocusFlowDestinations.REGISTER) {
        val context = LocalContext.current
        val authViewModel: AuthViewModel = viewModel(
            factory = viewModelFactory {
                initializer { AuthViewModel(AuthRepository(context.applicationContext)) }
            }
        )
        val state by authViewModel.state.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        val profileRepository = remember { ProfileRepository(context.applicationContext) }
        var needsConfirmationForEmail by remember { mutableStateOf<String?>(null) }
        var confirmationDialogMessage by remember { mutableStateOf<String?>(null) }

        RegisterScreen(
            onBack = { navController.popBackStack() },
            onCreateAccount = { form ->
                authViewModel.register(
                    email = form.email,
                    password = form.password,
                    fullName = form.fullName,
                    onNeedsConfirmation = {
                        needsConfirmationForEmail = form.email
                        confirmationDialogMessage = "We sent a confirmation link to ${form.email}. Open it, then sign in."
                    },
                    onLoggedIn = {
                        scope.launch {
                            navController.navigate(postAuthDestination(context.applicationContext, profileRepository))
                        }
                    }
                )
            },
            isLoading = state.isLoading,
            errorMessage = state.errorMessage
        )

        if (needsConfirmationForEmail != null) {
            PremiumDialog(
                title = "Confirm your email",
                message = confirmationDialogMessage.orEmpty(),
                onDismiss = {
                    needsConfirmationForEmail = null
                    navController.navigate(FocusFlowDestinations.LOGIN) {
                        popUpTo(FocusFlowDestinations.REGISTER) { inclusive = true }
                    }
                },
                primaryActionLabel = "Back to sign in",
                secondaryActionLabel = "Resend email",
                onSecondaryAction = {
                    authViewModel.resendConfirmationEmail(needsConfirmationForEmail!!) { _, message ->
                        confirmationDialogMessage = message
                    }
                }
            )
        }
    }
}

/**
 * Where a freshly-authenticated user belongs: straight to the Dashboard if
 * they've already been through onboarding, otherwise into it.
 *
 * Before this existed every sign-in hardcoded [FocusFlowDestinations.ADHD_QUESTION],
 * which always ends at the "Create your profile" form — so returning users were
 * made to re-create a profile they already had on every single sign-in.
 *
 * Also re-syncs the on-device [UserPreferences] display cache from the fetched
 * Supabase profile before routing to Dashboard. Without this, signing in with a
 * *different* already-onboarded account on the same device would skip the
 * onboarding form (correctly) but Dashboard/Results/Profile would keep showing
 * whichever account's name was last written locally — the cache is only ever
 * populated by the onboarding form, so a second account never gets a chance to
 * overwrite it otherwise.
 *
 * Fails open: if the profile fetch fails (network blip, table missing), this
 * returns onboarding rather than locking the user out or showing stale data.
 */
private suspend fun postAuthDestination(
    context: Context,
    profileRepository: ProfileRepository
): String {
    val profile = profileRepository.getProfile().getOrNull()
    if (profile?.onboardingCompleted != true) return FocusFlowDestinations.ADHD_QUESTION

    UserPreferences(context).saveProfile(
        name = profile.fullName.orEmpty(),
        age = profile.age ?: 0,
        sex = profile.sex.orEmpty(),
        email = profile.email.orEmpty()
    )
    return FocusFlowDestinations.DASHBOARD
}
