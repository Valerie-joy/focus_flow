package com.focusflow.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.focusflow.domain.validation.AuthInputValidator
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.MessageBanner
import com.focusflow.ui.components.PremiumTextField
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.components.TextAction
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.ui.theme.Sizing
import com.focusflow.ui.theme.Spacing

/** Stable test tags so the Compose UI tests don't match on user-visible copy. */
object LoginTestTags {
    const val EMAIL = "login_email"
    const val PASSWORD = "login_password"
    const val SUBMIT = "login_submit"
    const val ERROR_BANNER = "login_error"
}

/**
 * Email/password sign-in.
 *
 * Changes from the previous version, all of which were real usability faults
 * rather than styling:
 *
 *  - **The error went to the wrong place.** `errorMessage` was passed to the
 *    *password* field, so "No internet connection" rendered as though the
 *    password were wrong. Server and network errors now appear in a banner
 *    above the form, which is also an assertive live region so TalkBack
 *    announces a failed sign-in.
 *  - **No validation before submit.** A malformed email was sent to Supabase
 *    and came back as a generic auth failure. It's now caught locally, but
 *    only *after* the field has been visited — validating as the user types
 *    their first character means the form is red before they've finished.
 *  - **The keyboard was a dead end.** Every field showed a newline key and
 *    nothing advanced focus. Email now advances to password, and password
 *    submits.
 *  - **The form could be submitted twice.** A slow network left the button
 *    live; [PrimaryButton] now rejects clicks while loading.
 *  - **The keyboard covered the form.** No scroll and no IME padding meant the
 *    password field could sit behind the keyboard on a short screen.
 */
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onForgotPassword: (email: String) -> Unit,
    onSignIn: (email: String, password: String) -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    // rememberSaveable so a rotation mid-form doesn't discard what was typed.
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    // "Touched" gating: a field only shows its error once the user has left it
    // or tried to submit, so the form is never pre-emptively red.
    var emailTouched by rememberSaveable { mutableStateOf(false) }
    var submitAttempted by rememberSaveable { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val passwordFocusRequester = remember { FocusRequester() }

    val emailError = AuthInputValidator.emailError(email)
        .takeIf { emailTouched || submitAttempted }
    val canSubmit = AuthInputValidator.canSubmitSignIn(email, password)

    fun submit() {
        submitAttempted = true
        if (!canSubmit || isLoading) return
        keyboardController?.hide()
        onSignIn(email.trim(), password)
    }

    // A fresh error means the previous attempt finished; re-arm the form so a
    // corrected value can be submitted without the stale "attempted" flag
    // keeping an unrelated field marked invalid.
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) submitAttempted = false
    }

    BlurBackground(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Constrained so the form doesn't stretch into an
                    // unreadable full-width line on a tablet or in landscape.
                    .widthIn(max = Sizing.maxContentWidth)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.gutter)
            ) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back"
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = "Sign in",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    // Marked as a heading so TalkBack users can jump between
                    // screen sections instead of reading linearly.
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(Spacing.xxs))
                Text(
                    text = "Welcome back — let's pick up where you left off.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    MessageBanner(
                        message = errorMessage,
                        modifier = Modifier.testTag(LoginTestTags.ERROR_BANNER)
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xl))

                PremiumTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        // Clear the error as soon as they start fixing it,
                        // rather than making them leave the field to find out.
                        if (emailTouched && AuthInputValidator.emailError(it) == null) {
                            emailTouched = false
                        }
                    },
                    label = "Email",
                    placeholder = "you@example.com",
                    keyboardType = KeyboardType.Email,
                    errorMessage = emailError,
                    enabled = !isLoading,
                    imeAction = ImeAction.Next,
                    onImeAction = {
                        emailTouched = true
                        passwordFocusRequester.requestFocus()
                    },
                    modifier = Modifier.testTag(LoginTestTags.EMAIL)
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                PremiumTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    isPassword = true,
                    enabled = !isLoading,
                    imeAction = ImeAction.Done,
                    onImeAction = { submit() },
                    modifier = Modifier
                        .focusRequester(passwordFocusRequester)
                        .testTag(LoginTestTags.PASSWORD)
                )

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    TextAction(
                        text = "Forgot password?",
                        onClick = { onForgotPassword(email.trim()) },
                        enabled = !isLoading
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.sm))
                PrimaryButton(
                    text = "Sign in",
                    onClick = ::submit,
                    loading = isLoading,
                    enabled = canSubmit,
                    loadingContentDescription = "Signing in",
                    modifier = Modifier.testTag(LoginTestTags.SUBMIT)
                )
                Spacer(modifier = Modifier.height(Spacing.xxl))
            }
        }
    }
}

@Preview(showBackground = true, name = "Sign in")
@Composable
private fun LoginScreenPreview() {
    FocusFlowTheme {
        LoginScreen(onBack = {}, onForgotPassword = {}, onSignIn = { _, _ -> })
    }
}

@Preview(showBackground = true, name = "Sign in — error, dark")
@Composable
private fun LoginScreenErrorPreview() {
    FocusFlowTheme(darkTheme = true) {
        LoginScreen(
            onBack = {},
            onForgotPassword = {},
            onSignIn = { _, _ -> },
            errorMessage = "No internet connection. Check your Wi-Fi or mobile data and try again."
        )
    }
}
