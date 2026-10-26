package com.focusflow.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Account deletion is the one action in this app with no undo, so the order of
 * its steps and its honesty about partial failure are pinned here rather than
 * only checked by hand on a device.
 */
class AccountDeletionTest {

    private class RecordingSteps(
        private val userId: String? = "user-1",
        private val failOn: Set<String> = emptySet()
    ) : AccountDeletionSteps {
        val calls = mutableListOf<String>()

        private fun record(name: String) {
            calls += name
            if (name in failOn) error("forced failure in $name")
        }

        override fun currentUserId(): String? = userId
        override suspend fun deleteUploadedDocuments(userId: String) = record("documents")
        override suspend fun deleteProfileRow(userId: String) = record("profile")
        override suspend fun deleteAuthUser() = record("authUser")
        override suspend fun wipeLocalData() = record("local")
        override suspend fun signOut() = record("signOut")
    }

    @Test
    fun `happy path removes server data before the auth user, then clears the device`() = runBlocking {
        val steps = RecordingSteps()

        val result = runAccountDeletion(steps)

        assertEquals(AccountDeletionResult.Complete, result)
        // Documents and the profile row are only reachable while the auth user
        // exists, so they must precede it; the session must survive until the
        // very end.
        assertEquals(listOf("documents", "profile", "authUser", "local", "signOut"), steps.calls)
    }

    @Test
    fun `a failed document delete still deletes the account and the device`() = runBlocking {
        val steps = RecordingSteps(failOn = setOf("documents"))

        val result = runAccountDeletion(steps)

        assertTrue(result is AccountDeletionResult.LocalOnly)
        assertEquals(listOf("documents", "profile", "authUser", "local", "signOut"), steps.calls)
    }

    @Test
    fun `a failed auth-user delete is reported even when every other step worked`() = runBlocking {
        val steps = RecordingSteps(failOn = setOf("authUser"))

        val result = runAccountDeletion(steps)

        val reason = (result as AccountDeletionResult.LocalOnly).reason
        // The user must be given a way to finish what the app could not.
        assertTrue(reason, reason.contains("bethanyivalerie@gmail.com"))
        // The device is still cleared — that part never depends on the server.
        assertTrue(steps.calls.contains("local"))
    }

    @Test
    fun `the auth-user failure wins over an earlier one, because it is the one that matters`() =
        runBlocking {
            val steps = RecordingSteps(failOn = setOf("documents", "authUser"))

            val reason = (runAccountDeletion(steps) as AccountDeletionResult.LocalOnly).reason

            assertTrue(reason, reason.contains("sign-in account"))
        }

    @Test
    fun `an already signed-out user still gets this device cleared`() = runBlocking {
        val steps = RecordingSteps(userId = null)

        val result = runAccountDeletion(steps)

        assertTrue(result is AccountDeletionResult.LocalOnly)
        // No server call is attempted: without a session none of them could
        // prove ownership, and a 401 is not worth reporting as a failure.
        assertEquals(listOf("local", "signOut"), steps.calls)
    }

    @Test
    fun `a failure clearing the device does not stop the sign-out`() = runBlocking {
        val steps = RecordingSteps(failOn = setOf("local"))

        val result = runAccountDeletion(steps)

        // The server account is gone, which is what the user asked for; leaving
        // them signed in to a deleted account would be worse than either.
        assertEquals(AccountDeletionResult.Complete, result)
        assertTrue(steps.calls.contains("signOut"))
    }
}
