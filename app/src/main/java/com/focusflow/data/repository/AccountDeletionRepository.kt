package com.focusflow.data.repository

import android.content.Context
import android.util.Log
import com.focusflow.data.local.AdhdSelfReportDao
import com.focusflow.data.local.AssessmentDao
import com.focusflow.data.local.UserPreferences
import com.focusflow.data.remote.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage

private const val TAG = "AccountDeletion"
private const val PROFILES_TABLE = "profiles"
private const val DOCUMENTS_BUCKET = "adhd-documents"

/**
 * The outcome of a deletion attempt.
 *
 * [LocalOnly] exists because the two halves can fail independently and the user
 * has to be told which happened. Wiping this device is always possible; removing
 * the server-side account needs a reachable backend and an installed
 * `delete_current_user` function, and reporting success when only half of it ran
 * would be a lie about the one thing the user most wants to be true.
 */
sealed interface AccountDeletionResult {
    /** Everything removed: server account, server data, and this device. */
    data object Complete : AccountDeletionResult

    /**
     * This device was wiped and the user signed out, but something on the
     * server survives — typically no connection, or the SQL function in
     * `docs/supabase-delete-account.sql` has not been installed yet.
     */
    data class LocalOnly(val reason: String) : AccountDeletionResult
}

/**
 * The individual destructive steps, named so the order below can be tested
 * without Android, Room or a network. Deletion is the one operation in this app
 * that cannot be re-run to fix a mistake, so the sequencing is worth pinning
 * down in a unit test rather than only on a device.
 */
internal interface AccountDeletionSteps {
    /** Null when no one is signed in. */
    fun currentUserId(): String?
    suspend fun deleteUploadedDocuments(userId: String)
    suspend fun deleteProfileRow(userId: String)
    suspend fun deleteAuthUser()
    suspend fun wipeLocalData()
    suspend fun signOut()
}

/**
 * Runs [steps] in the only order that works, and reports honestly on what did
 * not.
 *
 * **Why this order.** Storage objects and the profile row go first, because
 * both are scoped by row-level security to `auth.uid()`: once the auth user is
 * gone this client can no longer prove ownership, so anything left behind is
 * orphaned with no way to reach it again. The local wipe runs whatever
 * happened above — the user asked for their data gone from this phone and that
 * much is always achievable — and the sign-out runs last, because clearing the
 * session any earlier would strip the credentials every step depends on.
 *
 * Each step is caught individually: a failure to remove the document should not
 * prevent the account itself from being deleted.
 */
internal suspend fun runAccountDeletion(
    steps: AccountDeletionSteps,
    logFailure: (String, Throwable) -> Unit = { _, _ -> }
): AccountDeletionResult {
    val userId = steps.currentUserId()

    var failure: String? = null
    if (userId == null) {
        failure = "You were already signed out, so only this device was cleared."
    } else {
        // An uploaded diagnosis document is the only file this app ever sends
        // anywhere, so it matters most.
        runCatching { steps.deleteUploadedDocuments(userId) }.onFailure {
            logFailure("Could not delete uploaded documents", it)
            failure = "Your uploaded document could not be removed. Please contact support."
        }
        runCatching { steps.deleteProfileRow(userId) }.onFailure {
            logFailure("Could not delete profile row", it)
            failure = "Your profile could not be removed from the server. Please contact support."
        }
        // Reported last because it is the failure the user cares about most: if
        // the sign-in account survives, the account is not deleted, whatever
        // else succeeded.
        runCatching { steps.deleteAuthUser() }.onFailure {
            logFailure("delete_current_user RPC failed", it)
            failure = "Your sign-in account could not be removed. Email " +
                "bethanyivalerie@gmail.com and it will be deleted for you."
        }
    }

    runCatching { steps.wipeLocalData() }
        .onFailure { logFailure("Could not clear data on this device", it) }
    runCatching { steps.signOut() }
        .onFailure { logFailure("Sign-out after deletion failed", it) }

    return failure?.let { AccountDeletionResult.LocalOnly(it) } ?: AccountDeletionResult.Complete
}

/**
 * Deletes the signed-in user's account and everything associated with it.
 *
 * Required by Google Play: an app that lets users create an account must offer
 * a discoverable in-app way to delete it as well as a web link. The page behind
 * that link is `docs/delete-account.md`, and it describes exactly what this
 * does — the two must stay in step.
 *
 * **Why the auth user is deleted through an RPC.** A client holding only the
 * publishable (anon) key cannot delete its own row from `auth.users`; that
 * needs the service-role key, which must never ship inside an app. The
 * supported route is a `SECURITY DEFINER` function that deletes `auth.uid()`
 * and nothing else, so the privilege stays on the server and a client can only
 * ever ask for its own account. The SQL is in
 * `docs/supabase-delete-account.sql` and has to be run once in the Supabase SQL
 * editor; until it is, this reports [AccountDeletionResult.LocalOnly].
 */
class AccountDeletionRepository(
    context: Context,
    assessmentDao: AssessmentDao,
    selfReportDao: AdhdSelfReportDao
) {
    private val steps = SupabaseDeletionSteps(context, assessmentDao, selfReportDao)

    suspend fun deleteAccount(): Result<AccountDeletionResult> = runCatching {
        runAccountDeletion(steps) { message, error -> Log.w(TAG, message, error) }
    }
}

private class SupabaseDeletionSteps(
    private val context: Context,
    private val assessmentDao: AssessmentDao,
    private val selfReportDao: AdhdSelfReportDao
) : AccountDeletionSteps {
    private val client = SupabaseClientProvider.getInstance(context)

    override fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    override suspend fun deleteUploadedDocuments(userId: String) {
        // Documents are uploaded to "<user id>/<file name>" (see
        // AdhdDocumentRepository), so the user's own folder is the prefix.
        val bucket = client.storage.from(DOCUMENTS_BUCKET)
        val paths = bucket.list(userId).map { "$userId/${it.name}" }
        if (paths.isNotEmpty()) bucket.delete(paths)
    }

    override suspend fun deleteProfileRow(userId: String) {
        client.postgrest[PROFILES_TABLE].delete { filter { eq("id", userId) } }
    }

    override suspend fun deleteAuthUser() {
        client.postgrest.rpc("delete_current_user")
    }

    override suspend fun wipeLocalData() {
        assessmentDao.deleteAll()
        selfReportDao.deleteAll()
        UserPreferences(context).clearAll()
    }

    override suspend fun signOut() {
        client.auth.signOut()
    }
}
