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

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Set when sign-in fails specifically because the account's email
     * isn't confirmed yet — drives a "resend confirmation email" prompt. */
    val unconfirmedEmail: String? = null
)

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
                            it.copy(errorMessage = e.message ?: "Couldn't sign in — check your email and password.")
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
                onFailure = { e -> _state.update { it.copy(errorMessage = e.message ?: "Couldn't create your account.") } }
            )
        }
    }

    fun resendConfirmationEmail(email: String, onResult: (success: Boolean, message: String) -> Unit) {
        viewModelScope.launch {
            repository.resendConfirmationEmail(email).fold(
                onSuccess = { onResult(true, "Confirmation email resent — check your inbox.") },
                onFailure = { e -> onResult(false, e.message ?: "Couldn't resend the confirmation email.") }
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
                onFailure = { e -> onResult(false, e.message ?: "Couldn't send a reset link.") }
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
                onFailure = { e -> _state.update { it.copy(errorMessage = e.message ?: defaultErrorMessage) } }
            )
        }
    }
}
