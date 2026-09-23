package com.focusflow.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusflow.data.repository.AuthRepository
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Set when sign-in fails specifically because the account's email
     * isn't confirmed yet — drives a "resend confirmation email" prompt. */
    val unconfirmedEmail: String? = null
)

/**
 * Turns a failure into something a user can act on.
 *
 * Without this, `e.message` goes straight to the screen, and a phone that
 * simply has no working connection shows the raw Ktor text — "HTTP request to
 * https://<project>.supabase.co/auth/v1/token?grant_type=password (POST)
 * failed with message: Unable to resolve host ... No address associated with
 * hostname". That names the backend host in the UI, reads as a server fault
 * when the server is fine, and tells the user nothing they can do.
 *
 * Connectivity problems surface as [UnknownHostException] (DNS failed —
 * nearly always no internet), timeouts, or a bare [IOException], and any of
 * them can arrive wrapped by the HTTP client, so the whole cause chain is
 * checked. Anything else keeps its own message: Supabase's auth errors
 * ("Invalid login credentials") are already meaningful.
 */
internal fun friendlyAuthError(error: Throwable, fallback: String): String {
    // Collect the chain once, guarding against a self-referencing cause.
    val chain = buildList {
        var cause: Throwable? = error
        while (cause != null && none { it === cause }) {
            add(cause)
            cause = cause.cause
        }
    }

    // Specific causes are looked for across the whole chain *before* the
    // generic one. UnknownHostException and SocketTimeoutException both extend
    // IOException, and the HTTP client wraps them in a plain IOException, so
    // matching in chain order would let the generic wrapper answer first and
    // report "couldn't reach the server" for what is really no connection.
    return when {
        chain.any { it is UnknownHostException } ->
            "No internet connection. Check your Wi-Fi or mobile data and try again."
        chain.any { it is SocketTimeoutException } ->
            "The connection timed out. Check your internet and try again."
        chain.any { it is IOException } ->
            "Couldn't reach the server. Check your internet and try again."
        else -> error.message ?: fallback
    }
}

/**
 * Owns loading/error state for the auth screens and forwards to
 * [AuthRepository]. Mirrors the shape of the other manually-instantiated
 * ViewModels in this codebase (e.g. AssessmentViewModel) rather than using
 * Hilt, which isn't wired app-wide yet.
 */
class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    /** SplashScreen observes this directly to route Dashboard vs Welcome. */
    val sessionStatus: StateFlow<SessionStatus> = repository.sessionStatus

    fun signIn(email: String, password: String, onSuccess: () -> Unit) {
        _state.update { it.copy(isLoading = true, errorMessage = null, unconfirmedEmail = null) }
        viewModelScope.launch {
            val result = repository.signInWithEmail(email, password)
            _state.update { it.copy(isLoading = false) }
            result.fold(
                onSuccess = { onSuccess() },
                onFailure = { e ->
                    if (e is AuthRestException && e.errorCode == AuthErrorCode.EmailNotConfirmed) {
                        _state.update {
                            it.copy(
                                errorMessage = "Please confirm your email before signing in.",
                                unconfirmedEmail = email
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(errorMessage = friendlyAuthError(e, "Couldn't sign in — check your email and password."))
                        }
                    }
                }
            )
        }
    }

    /**
     * @param onNeedsConfirmation account was created but Supabase's "Confirm
     * email" setting means no session comes back until the link is clicked.
     * @param onLoggedIn signup returned an active session immediately.
     */
    fun register(
        email: String,
        password: String,
        fullName: String? = null,
        onNeedsConfirmation: () -> Unit,
        onLoggedIn: () -> Unit
    ) {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = repository.signUpWithEmail(email, password, fullName)
            _state.update { it.copy(isLoading = false) }
            result.fold(
                onSuccess = { loggedInImmediately -> if (loggedInImmediately) onLoggedIn() else onNeedsConfirmation() },
                onFailure = { e -> _state.update { it.copy(errorMessage = friendlyAuthError(e, "Couldn't create your account.")) } }
            )
        }
    }

    fun resendConfirmationEmail(email: String, onResult: (success: Boolean, message: String) -> Unit) {
        viewModelScope.launch {
            repository.resendConfirmationEmail(email).fold(
                onSuccess = { onResult(true, "Confirmation email resent — check your inbox.") },
                onFailure = { e -> onResult(false, friendlyAuthError(e, "Couldn't resend the confirmation email.")) }
            )
        }
    }

    fun clearUnconfirmedEmail() {
        _state.update { it.copy(unconfirmedEmail = null, errorMessage = null) }
    }

    fun signInWithGoogleIdToken(idToken: String, onSuccess: () -> Unit) {
        runAuthAction(
            action = { repository.signInWithGoogleIdToken(idToken) },
            defaultErrorMessage = "Google sign-in failed.",
            onSuccess = onSuccess
        )
    }

    fun forgotPassword(email: String, onResult: (success: Boolean, message: String) -> Unit) {
        viewModelScope.launch {
            repository.resetPasswordForEmail(email).fold(
                onSuccess = { onResult(true, "Check your email for a reset link.") },
                onFailure = { e -> onResult(false, friendlyAuthError(e, "Couldn't send a reset link.")) }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch { repository.signOut() }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun runAuthAction(
        action: suspend () -> Result<Unit>,
        defaultErrorMessage: String,
        onSuccess: () -> Unit
    ) {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = action()
            _state.update { it.copy(isLoading = false) }
            result.fold(
                onSuccess = { onSuccess() },
                onFailure = { e -> _state.update { it.copy(errorMessage = friendlyAuthError(e, defaultErrorMessage)) } }
            )
        }
    }
}
