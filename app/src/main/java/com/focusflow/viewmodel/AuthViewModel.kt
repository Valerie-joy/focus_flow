package com.focusflow.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusflow.data.repository.AuthRepository
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /**
     * What kind of failure produced [errorMessage], so a screen can respond
     * rather than only display. A rate limit wants the retry button held back
     * for a moment; an already-registered email wants a route to sign in
     * instead; a cancellation wants no UI at all.
     */
    val errorKind: AuthFailureKind? = null,
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
        // Guard a second submission while one is in flight. The screens disable
        // their buttons too, but the ViewModel is the only place that can be
        // certain: a rapid double tap can land before recomposition has applied
        // the disabled state.
        if (_state.value.isLoading) return
        _state.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                errorKind = null,
                unconfirmedEmail = null
            )
        }
        viewModelScope.launch {
            val result = repository.signInWithEmail(email, password)
            _state.update { it.copy(isLoading = false) }
            result.fold(
                onSuccess = { onSuccess() },
                onFailure = { e ->
                    val failure = AuthErrorMapper.map(e)
                    _state.update {
                        it.copy(
                            errorMessage = failure.message,
                            errorKind = failure.kind,
                            // Detected from the mapped kind rather than an
                            // inline type check, so sign-in and registration
                            // agree on what an unconfirmed account looks like.
                            unconfirmedEmail = email.takeIf {
                                failure.kind == AuthFailureKind.EMAIL_NOT_CONFIRMED
                            }
                        )
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
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, errorMessage = null, errorKind = null) }
        viewModelScope.launch {
            val result = repository.signUpWithEmail(email, password, fullName)
            _state.update { it.copy(isLoading = false) }
            result.fold(
                onSuccess = { loggedInImmediately ->
                    if (loggedInImmediately) onLoggedIn() else onNeedsConfirmation()
                },
                onFailure = { e ->
                    val failure = AuthErrorMapper.map(e)
                    _state.update { it.copy(errorMessage = failure.message, errorKind = failure.kind) }
                }
            )
        }
    }

    fun resendConfirmationEmail(email: String, onResult: (success: Boolean, message: String) -> Unit) {
        viewModelScope.launch {
            repository.resendConfirmationEmail(email).fold(
                onSuccess = { onResult(true, "Confirmation email resent — check your inbox.") },
                onFailure = { e -> onResult(false, AuthErrorMapper.map(e).message) }
            )
        }
    }

    fun clearUnconfirmedEmail() {
        _state.update { it.copy(unconfirmedEmail = null, errorMessage = null, errorKind = null) }
    }

    fun signInWithGoogleIdToken(idToken: String, onSuccess: () -> Unit) {
        runAuthAction(
            action = { repository.signInWithGoogleIdToken(idToken) },
            onSuccess = onSuccess
        )
    }

    fun forgotPassword(email: String, onResult: (success: Boolean, message: String) -> Unit) {
        viewModelScope.launch {
            repository.resetPasswordForEmail(email).fold(
                onSuccess = { onResult(true, "Check your email for a reset link.") },
                onFailure = { e -> onResult(false, AuthErrorMapper.map(e).message) }
            )
        }
    }

    /**
     * @param onSignedOut invoked once the session is cleared, whether or not
     * the server call succeeded.
     *
     * A failed sign-out used to be swallowed silently, leaving the user on a
     * screen still showing their data with no indication the tap did anything.
     * The Supabase client clears the local session regardless, so proceeding is
     * correct — but the caller is now told, and can navigate.
     */
    fun signOut(onSignedOut: () -> Unit = {}) {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, errorMessage = null, errorKind = null) }
        viewModelScope.launch {
            repository.signOut()
            _state.update { it.copy(isLoading = false) }
            onSignedOut()
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null, errorKind = null) }
    }

    private fun runAuthAction(
        action: suspend () -> Result<Unit>,
        onSuccess: () -> Unit
    ) {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, errorMessage = null, errorKind = null) }
        viewModelScope.launch {
            val result = action()
            _state.update { it.copy(isLoading = false) }
            result.fold(
                onSuccess = { onSuccess() },
                onFailure = { e ->
                    val failure = AuthErrorMapper.map(e)
                    _state.update { it.copy(errorMessage = failure.message, errorKind = failure.kind) }
                }
            )
        }
    }
}
