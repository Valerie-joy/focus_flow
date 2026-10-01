package com.focusflow.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.focusflow.domain.validation.AuthInputValidator
import com.focusflow.domain.validation.PasswordStrength
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.MessageBanner
import com.focusflow.ui.components.PremiumTextField
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.ui.theme.LocalFocusFlowColors
import com.focusflow.ui.theme.Sizing
import com.focusflow.ui.theme.Spacing

data class RegisterFormState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = ""
)

object RegisterTestTags {
    const val NAME = "register_name"
    const val EMAIL = "register_email"
    const val PASSWORD = "register_password"
    const val CONFIRM = "register_confirm"
    const val SUBMIT = "register_submit"
    const val ERROR_BANNER = "register_error"
}

/**
 * Account creation.
 *
 * The previous version only checked for non-blank fields and a matching
 * confirmation, so a malformed email or a five-character password was sent to
 * Supabase and returned as an opaque server error the user had to guess at.
 * Validation now runs locally against the same rules the backend enforces (see
 * [AuthInputValidator]), each field shows its own message, and a strength
 * meter gives feedback while typing without ever blocking submission.
 *
 * Field errors appear only after a field has been left or submit was pressed,
 * so the form doesn't turn red while the first character is being typed.
 */
@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    onCreateAccount: (RegisterFormState) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    /**
     * Offered in the error banner when the failure was specifically that the
     * email is already registered. Null for every other failure, so the banner
     * never shows an action that wouldn't help.
     */
    onGoToSignIn: (() -> Unit)? = null
) {
    var form by rememberSaveable(
        stateSaver = androidx.compose.runtime.saveable.listSaver(
            save = { listOf(it.fullName, it.email, it.password, it.confirmPassword) },
            restore = { RegisterFormState(it[0], it[1], it[2], it[3]) }
        )
    ) { mutableStateOf(RegisterFormState()) }

    var touched by rememberSaveable { mutableStateOf(setOf<String>()) }
    var submitAttempted by rememberSaveable { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }

    fun errorFor(field: String, compute: () -> String?): String? =
        if (submitAttempted || field in touched) compute() else null

    val nameError = errorFor(RegisterTestTags.NAME) {
        AuthInputValidator.fullNameError(form.fullName)
    }
    val emailError = errorFor(RegisterTestTags.EMAIL) {
        AuthInputValidator.emailError(form.email)
    }
    val passwordError = errorFor(RegisterTestTags.PASSWORD) {
        AuthInputValidator.passwordError(form.password)
    }
    // Mismatch is shown as soon as the confirmation is long enough to be a real
    // attempt, rather than waiting for focus to leave — catching it a keystroke
    // earlier saves retyping the whole field.
    val confirmError = if (form.confirmPassword.isNotEmpty() || submitAttempted) {
        AuthInputValidator.confirmPasswordError(form.password, form.confirmPassword)
    } else null

    val canSubmit = AuthInputValidator.canSubmitRegistration(
        form.fullName, form.email, form.password, form.confirmPassword
    )

    fun submit() {
        submitAttempted = true
        if (!canSubmit || isLoading) return
        keyboardController?.hide()
        onCreateAccount(form.copy(fullName = form.fullName.trim(), email = form.email.trim()))
    }

    BlurBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                    text = "Create account",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(Spacing.xxs))
                Text(
                    text = "A few details and you're on your way.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    MessageBanner(
                        message = errorMessage,
                        actionLabel = if (onGoToSignIn != null) "Sign in instead" else null,
                        onAction = onGoToSignIn,
                        modifier = Modifier.testTag(RegisterTestTags.ERROR_BANNER)
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xl))

                PremiumTextField(
                    value = form.fullName,
                    onValueChange = { form = form.copy(fullName = it) },
                    label = "Full name",
                    placeholder = "Jordan Lee",
                    capitalization = KeyboardCapitalization.Words,
                    isRequired = true,
                    errorMessage = nameError,
                    enabled = !isLoading,
                    imeAction = ImeAction.Next,
                    onImeAction = {
                        touched = touched + RegisterTestTags.NAME
                        emailFocus.requestFocus()
                    },
                    modifier = Modifier.testTag(RegisterTestTags.NAME)
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                PremiumTextField(
                    value = form.email,
                    onValueChange = { form = form.copy(email = it) },
                    label = "Email",
                    placeholder = "you@example.com",
                    keyboardType = KeyboardType.Email,
                    isRequired = true,
                    errorMessage = emailError,
                    supportingText = "We'll send a confirmation link here.",
                    enabled = !isLoading,
                    imeAction = ImeAction.Next,
                    onImeAction = {
                        touched = touched + RegisterTestTags.EMAIL
                        passwordFocus.requestFocus()
                    },
                    modifier = Modifier
                        .focusRequester(emailFocus)
                        .testTag(RegisterTestTags.EMAIL)
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                PremiumTextField(
                    value = form.password,
                    onValueChange = { form = form.copy(password = it) },
                    label = "Password",
                    isPassword = true,
                    isRequired = true,
                    errorMessage = passwordError,
                    supportingText = if (passwordError == null) {
                        "At least ${AuthInputValidator.MIN_PASSWORD_LENGTH} characters."
                    } else null,
                    enabled = !isLoading,
                    imeAction = ImeAction.Next,
                    onImeAction = {
                        touched = touched + RegisterTestTags.PASSWORD
                        confirmFocus.requestFocus()
                    },
                    modifier = Modifier
                        .focusRequester(passwordFocus)
                        .testTag(RegisterTestTags.PASSWORD)
                )

                AnimatedVisibility(visible = form.password.isNotEmpty()) {
                    PasswordStrengthMeter(
                        strength = AuthInputValidator.passwordStrength(form.password)
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.sm))
                PremiumTextField(
                    value = form.confirmPassword,
                    onValueChange = { form = form.copy(confirmPassword = it) },
                    label = "Confirm password",
                    isPassword = true,
                    isRequired = true,
                    errorMessage = confirmError,
                    enabled = !isLoading,
                    imeAction = ImeAction.Done,
                    onImeAction = { submit() },
                    modifier = Modifier
                        .focusRequester(confirmFocus)
                        .testTag(RegisterTestTags.CONFIRM)
                )

                Spacer(modifier = Modifier.height(Spacing.lg))
                PrimaryButton(
                    text = "Create account",
                    onClick = ::submit,
                    loading = isLoading,
                    enabled = canSubmit,
                    loadingContentDescription = "Creating your account",
                    modifier = Modifier.testTag(RegisterTestTags.SUBMIT)
                )
                Spacer(modifier = Modifier.height(Spacing.xxl))
            }
        }
    }
}

/**
 * Advisory password-strength meter.
 *
 * The bar is paired with a text label, never color alone, so the signal
 * survives a color-vision deficiency and a grayscale display filter.
 */
@Composable
private fun PasswordStrengthMeter(strength: PasswordStrength, modifier: Modifier = Modifier) {
    val colors = LocalFocusFlowColors.current
    val (fraction, tint) = when (strength) {
        PasswordStrength.TOO_SHORT -> 0.15f to MaterialTheme.colorScheme.error
        PasswordStrength.WEAK -> 0.35f to colors.warning
        PasswordStrength.FAIR -> 0.7f to colors.warning
        PasswordStrength.STRONG -> 1f to colors.success
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.weight(1f),
            color = tint,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            gapSize = 0.dp,
            drawStopIndicator = {}
        )
        Text(
            text = strength.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true, name = "Create account")
@Composable
private fun RegisterScreenPreview() {
    FocusFlowTheme {
        RegisterScreen(onBack = {}, onCreateAccount = {})
    }
}
