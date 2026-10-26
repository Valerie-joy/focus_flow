package com.focusflow.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusflow.data.repository.AccountDeletionRepository
import com.focusflow.data.repository.AccountDeletionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountDeletionUiState(
    val isDeleting: Boolean = false,
    /**
     * Set when the device was cleared but the server account was not, so the
     * user can be told plainly instead of being shown the welcome screen and
     * left to assume their account is gone. Null in every other case.
     */
    val partialFailureMessage: String? = null
)

/**
 * Runs account deletion on [viewModelScope], for the same reason sign-out was
 * moved here (see [AuthViewModel.signOut]): the caller navigates away with
 * `popUpTo(0)` the moment this finishes, which tears down the composable. A
 * `rememberCoroutineScope()` would be cancelled with it, and a deletion
 * cancelled halfway leaves an account the user believes is deleted.
 *
 * Navigation therefore happens in [deleteAccount]'s callback, never alongside
 * the call.
 */
class AccountDeletionViewModel(
    private val repository: AccountDeletionRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AccountDeletionUiState())
    val state: StateFlow<AccountDeletionUiState> = _state.asStateFlow()

    /**
     * @param onFinished invoked once deletion has run to completion, whether or
     * not the server half succeeded. The local session is gone either way, so
     * the user always leaves the signed-in part of the app.
     */
    fun deleteAccount(onFinished: () -> Unit) {
        if (_state.value.isDeleting) return
        _state.update { it.copy(isDeleting = true, partialFailureMessage = null) }
        viewModelScope.launch {
            val outcome = repository.deleteAccount()
            val partial = outcome.fold(
                onSuccess = { result ->
                    when (result) {
                        is AccountDeletionResult.Complete -> null
                        is AccountDeletionResult.LocalOnly -> result.reason
                    }
                },
                // The repository already catches per-step failures, so landing
                // here means something unexpected broke the whole attempt. The
                // local wipe may not have run, which the user must not be left
                // believing it did.
                onFailure = {
                    "Your data on this device was cleared, but we couldn't confirm your " +
                        "account was deleted. Email support@focusflow.app and it will be " +
                        "deleted for you."
                }
            )
            _state.update { it.copy(isDeleting = false, partialFailureMessage = partial) }
            onFinished()
        }
    }

    fun clearPartialFailure() {
        _state.update { it.copy(partialFailureMessage = null) }
    }
}
