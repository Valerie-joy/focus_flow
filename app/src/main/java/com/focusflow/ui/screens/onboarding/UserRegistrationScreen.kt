package com.focusflow.ui.screens.onboarding

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.focusflow.R
import com.focusflow.domain.validation.AuthInputValidator
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.MessageBanner
import com.focusflow.ui.components.NumberStepper
import com.focusflow.ui.components.PremiumTextField
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.components.SectionHeader
import com.focusflow.ui.components.SegmentedToggle
import com.focusflow.ui.components.StepHeader
import com.focusflow.ui.navigation.OnboardingStep
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.ui.theme.Sizing
import com.focusflow.ui.theme.Spacing

data class UserRegistrationFormState(
    val fullName: String = "",
    val age: Int = DEFAULT_AGE,
    /** Index into [sexOptions]; -1 until the user chooses. */
    val sexIndex: Int = UNSELECTED_SEX,
    val email: String = ""
)

const val UNSELECTED_SEX = -1
const val DEFAULT_AGE = 16
val AGE_RANGE = 5..30

/**
 * "Prefer not to say" is a real option, not a fallback.
 *
 * The previous list was `["Male", "Female"]` with a default `sexIndex = 0`, so
 * every profile arrived pre-set to Male unless the user noticed and changed it —
 * a silent wrong default on a field that is stored with their results. The
 * navigation layer already mapped an out-of-range index to "Prefer not to say",
 * so the data model supported this all along; only the UI didn't offer it.
 */
val sexOptions = listOf("Male", "Female", "Prefer not to say")

/**
 * Collects the profile stored alongside results.
 *
 * Changes of substance:
 *
 *  - **Nothing was pre-selected honestly.** See [sexOptions]. Submission now
 *    requires an explicit choice.
 *  - **Email wasn't validated.** Only checked for non-blankness, so a typo went
 *    to the backend and came back as an opaque failure. Now uses the same rule
 *    as sign-in ([AuthInputValidator]).
 *  - **The keyboard covered the form** and no field advanced to the next.
 *  - **Typed input was lost on rotation.**
 *  - **A failed save was invisible.** The caller ignored the repository's
 *    `Result` and navigated regardless; [errorMessage] now gives it somewhere to
 *    report, and the button can't be tapped twice.
 *  - **Why the app asks was unstated.** "This helps us personalize your
 *    assessment" is vague enough to read as data collection for its own sake.
 */
@Composable
fun UserRegistrationScreen(
    onCreateProfile: (UserRegistrationFormState) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    initialFullName: String = "",
    initialEmail: String = ""
) {
    var form by rememberSaveable(
        stateSaver = listSaver(
            save = { listOf(it.fullName, it.age.toString(), it.sexIndex.toString(), it.email) },
            restore = {
                UserRegistrationFormState(
                    fullName = it[0],
                    age = it[1].toIntOrNull() ?: DEFAULT_AGE,
                    sexIndex = it[2].toIntOrNull() ?: UNSELECTED_SEX,
                    email = it[3]
                )
            }
        )
    ) {
        mutableStateOf(
            UserRegistrationFormState(fullName = initialFullName, email = initialEmail)
        )
    }
    var submitAttempted by rememberSaveable { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val emailFocus = remember { FocusRequester() }

    val nameError = AuthInputValidator.fullNameError(form.fullName).takeIf { submitAttempted }
    val emailError = AuthInputValidator.emailError(form.email).takeIf { submitAttempted }
    val sexError = if (submitAttempted && form.sexIndex == UNSELECTED_SEX) {
        stringResource(R.string.registration_error_sex)
    } else null

    val canSubmit = AuthInputValidator.fullNameError(form.fullName) == null &&
        AuthInputValidator.emailError(form.email) == null &&
        form.sexIndex != UNSELECTED_SEX

    fun submit() {
        submitAttempted = true
        if (!canSubmit || isLoading) return
        keyboardController?.hide()
        onCreateProfile(
            form.copy(fullName = form.fullName.trim(), email = form.email.trim())
        )
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
                Spacer(modifier = Modifier.height(Spacing.lg))
                StepHeader(step = OnboardingStep.PROFILE)

                Spacer(modifier = Modifier.height(Spacing.xl))
                Text(
                    text = stringResource(R.string.registration_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(Spacing.xxs))
                Text(
                    text = stringResource(R.string.registration_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(Spacing.md))
                    MessageBanner(
                        message = errorMessage,
                        modifier = Modifier.testTag(RegistrationTestTags.ERROR_BANNER)
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xl))

                PremiumTextField(
                    value = form.fullName,
                    onValueChange = { form = form.copy(fullName = it) },
                    label = stringResource(R.string.registration_name_label),
                    placeholder = stringResource(R.string.registration_name_placeholder),
                    capitalization = KeyboardCapitalization.Words,
                    isRequired = true,
                    errorMessage = nameError,
                    enabled = !isLoading,
                    imeAction = ImeAction.Next,
                    onImeAction = { emailFocus.requestFocus() },
                    modifier = Modifier.testTag(RegistrationTestTags.NAME)
                )

                Spacer(modifier = Modifier.height(Spacing.sm))
                PremiumTextField(
                    value = form.email,
                    onValueChange = { form = form.copy(email = it) },
                    label = stringResource(R.string.registration_email_label),
                    placeholder = "you@example.com",
                    keyboardType = KeyboardType.Email,
                    isRequired = true,
                    errorMessage = emailError,
                    enabled = !isLoading,
                    imeAction = ImeAction.Done,
                    onImeAction = { submit() },
                    modifier = Modifier
                        .focusRequester(emailFocus)
                        .testTag(RegistrationTestTags.EMAIL)
                )

                Spacer(modifier = Modifier.height(Spacing.lg))
                SectionHeader(title = stringResource(R.string.registration_age_label))
                Spacer(modifier = Modifier.height(Spacing.xs))
                NumberStepper(
                    value = form.age,
                    onValueChange = { form = form.copy(age = it) },
                    range = AGE_RANGE,
                    label = "years old"
                )

                Spacer(modifier = Modifier.height(Spacing.lg))
                SectionHeader(title = stringResource(R.string.registration_sex_label))
                Spacer(modifier = Modifier.height(Spacing.xs))
                SegmentedToggle(
                    options = sexOptions,
                    selectedIndex = form.sexIndex,
                    onSelect = { form = form.copy(sexIndex = it) },
                    enabled = !isLoading
                )
                if (sexError != null) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = sexError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xl))
                PrimaryButton(
                    text = stringResource(R.string.registration_submit),
                    onClick = ::submit,
                    loading = isLoading,
                    enabled = canSubmit,
                    loadingContentDescription = stringResource(R.string.registration_saving_a11y),
                    modifier = Modifier.testTag(RegistrationTestTags.SUBMIT)
                )
                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }
    }
}

object RegistrationTestTags {
    const val NAME = "profile_name"
    const val EMAIL = "profile_email"
    const val SUBMIT = "profile_submit"
    const val ERROR_BANNER = "profile_error"
}

@Preview(showBackground = true, name = "Profile setup")
@Composable
private fun UserRegistrationScreenPreview() {
    FocusFlowTheme {
        UserRegistrationScreen(
            onCreateProfile = {},
            initialFullName = "Jordan Lee",
            initialEmail = "jordan@example.com"
        )
    }
}

@Preview(showBackground = true, name = "Profile setup — save failed, dark")
@Composable
private fun UserRegistrationErrorPreview() {
    FocusFlowTheme(darkTheme = true) {
        UserRegistrationScreen(
            onCreateProfile = {},
            errorMessage = "Couldn't save your profile. Check your connection and try again."
        )
    }
}
