package com.focusflow.ui.screens.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.focusflow.ui.theme.FocusFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for sign-in.
 *
 * These cover the parts of the form that only exist in a real composition and
 * so cannot be reached by the JVM tests on [com.focusflow.domain.validation.AuthInputValidator]:
 * whether the submit button is actually wired to the validation result,
 * whether a server error is rendered where the user will see it, and whether
 * the double-submit guard holds.
 *
 * Instrumented — these require a connected device or emulator
 * (`./gradlew :app:connectedDebugAndroidTest`).
 */
@RunWith(AndroidJUnit4::class)
class LoginScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        isLoading: Boolean = false,
        errorMessage: String? = null,
        onSignIn: (String, String) -> Unit = { _, _ -> }
    ) {
        composeRule.setContent {
            FocusFlowTheme {
                LoginScreen(
                    onBack = {},
                    onForgotPassword = {},
                    onSignIn = onSignIn,
                    isLoading = isLoading,
                    errorMessage = errorMessage
                )
            }
        }
    }

    @Test
    fun submitIsDisabledUntilTheFormIsValid() {
        setContent()

        composeRule.onNodeWithTag(LoginTestTags.SUBMIT).assertIsNotEnabled()

        // A malformed address must not enable submission — this is the check
        // that used to be missing entirely, sending junk to the backend.
        composeRule.onNodeWithTag(LoginTestTags.EMAIL).performTextInput("not-an-email")
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD).performTextInput("secret123")
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT).assertIsNotEnabled()
    }

    @Test
    fun submitIsEnabledForAValidEmailAndPassword() {
        setContent()

        composeRule.onNodeWithTag(LoginTestTags.EMAIL).performTextInput("user@example.com")
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD).performTextInput("secret123")
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT).assertIsEnabled()
    }

    @Test
    fun signInIsInvokedOnceWithTrimmedCredentials() {
        val submissions = mutableListOf<Pair<String, String>>()
        setContent(onSignIn = { email, password -> submissions += email to password })

        composeRule.onNodeWithTag(LoginTestTags.EMAIL).performTextInput("  user@example.com  ")
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD).performTextInput("secret123")
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT).performClick()

        assertEquals(1, submissions.size)
        // Trimmed: a trailing space picked up from an autofill or a paste
        // should not become part of the address sent to the backend.
        assertEquals("user@example.com" to "secret123", submissions.first())
    }

    /**
     * The regression this exists for: the error used to be passed into the
     * *password field's* error slot, so a connectivity failure was presented
     * as though the password were wrong.
     */
    @Test
    fun serverErrorIsShownInItsOwnBannerNotOnAField() {
        val message = "No internet connection. Check your Wi-Fi or mobile data and try again."
        setContent(errorMessage = message)

        composeRule.onNodeWithTag(LoginTestTags.ERROR_BANNER).assertIsDisplayed()
        composeRule.onNodeWithText(message).assertIsDisplayed()
    }

    /**
     * The double-submit guard, exercised the way it actually happens: the first
     * tap starts a request, the screen becomes `isLoading`, and the second tap
     * lands before it finishes.
     *
     * Deliberately *not* written by rendering with `isLoading = true` and typing
     * into the form — the fields are disabled while loading, so
     * `performTextInput` would fail on a disabled node and the test would be
     * measuring its own setup rather than the guard.
     */
    @Test
    fun aSecondTapWhileTheRequestIsInFlightDoesNotSubmitAgain() {
        var submissions = 0
        composeRule.setContent {
            var loading by remember { mutableStateOf(false) }
            FocusFlowTheme {
                LoginScreen(
                    onBack = {},
                    onForgotPassword = {},
                    onSignIn = { _, _ ->
                        submissions++
                        loading = true
                    },
                    isLoading = loading
                )
            }
        }

        composeRule.onNodeWithTag(LoginTestTags.EMAIL).performTextInput("user@example.com")
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD).performTextInput("secret123")
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT).performClick()
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT).performClick()

        assertEquals(1, submissions)
    }
}
