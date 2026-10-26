package com.focusflow.ui.screens.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.focusflow.ui.theme.FocusFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose tests for the account-deletion entry point.
 *
 * Deletion cannot be exercised end to end here — that needs a real Supabase
 * account to destroy — so what is pinned is everything that happens before the
 * request leaves the device: that the route exists and is reachable, that it
 * cannot fire without a deliberate confirmation, and that cancelling really
 * does nothing. A deletion triggered by a stray tap is unrecoverable, so "the
 * dialog is required" is a correctness property, not a style one.
 *
 * Instrumented — requires a connected device or emulator
 * (`./gradlew :app:connectedDebugAndroidTest`).
 */
@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setProfile(
        isDeletingAccount: Boolean = false,
        onDeleteAccount: () -> Unit = {}
    ) {
        composeRule.setContent {
            FocusFlowTheme {
                ProfileScreen(
                    userName = "Sophia Martinez",
                    userEmail = "sophia.m@email.com",
                    darkModeOverride = null,
                    onDarkModeOverrideChange = {},
                    onBack = {},
                    onPersonalInformation = {},
                    onAssessmentHistory = {},
                    onAiRecommendations = {},
                    onNotifications = {},
                    onPrivacy = {},
                    onHelpSupport = {},
                    onLogout = {},
                    onDeleteAccount = onDeleteAccount,
                    isDeletingAccount = isDeletingAccount
                )
            }
        }
    }

    /**
     * Google Play requires the in-app route to be discoverable. It sits below
     * the fold on a phone, so being present in the tree is not enough — it has
     * to be scrollable into view.
     */
    @Test
    fun deleteAccountIsReachableFromProfile() {
        setProfile()

        composeRule.onNodeWithText("Delete account").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tappingDeleteAccountOnlyOpensTheConfirmation() {
        var deleteRequests = 0
        setProfile(onDeleteAccount = { deleteRequests++ })

        composeRule.onNodeWithText("Delete account").performScrollTo().performClick()

        composeRule.onNodeWithText("Delete your account?").assertIsDisplayed()
        // The single most important assertion in this file: one tap must never
        // delete anything.
        assertEquals(0, deleteRequests)
    }

    @Test
    fun confirmingInTheDialogRequestsDeletionExactlyOnce() {
        var deleteRequests = 0
        setProfile(onDeleteAccount = { deleteRequests++ })

        composeRule.onNodeWithText("Delete account").performScrollTo().performClick()
        // The dialog's confirm button carries the same label as the row that
        // opened it, so it is addressed through the dialog's own title first to
        // be sure the dialog is up, then by the last matching node.
        composeRule.onNodeWithText("Delete your account?").assertIsDisplayed()
        composeRule.onAllNodesWithText("Delete account")
            .onLast()
            .performClick()

        assertEquals(1, deleteRequests)
    }

    @Test
    fun cancellingTheDialogDeletesNothing() {
        var deleteRequests = 0
        setProfile(onDeleteAccount = { deleteRequests++ })

        composeRule.onNodeWithText("Delete account").performScrollTo().performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(0, deleteRequests)
        composeRule.onNodeWithText("Delete your account?").assertDoesNotExist()
    }

    /** A second tap while a deletion is in flight must not start another. */
    @Test
    fun theEntryPointIsDisabledWhileDeletionIsRunning() {
        setProfile(isDeletingAccount = true)

        composeRule.onNodeWithText("Deleting account…")
            .performScrollTo()
            .assertIsNotEnabled()
    }
}
