package com.focusflow.data.repository

import android.content.Context
import com.focusflow.data.remote.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val TABLE = "profiles"

/**
 * The user's profile as stored in Supabase's `profiles` table (see
 * SUPABASE_SETUP.md for the DDL + row-level security policies). [id] is the
 * Supabase auth user's UUID, so a profile is inseparable from its account —
 * which is the whole point: it survives a reinstall or a new device, unlike
 * the SharedPreferences-only copy [com.focusflow.data.local.UserPreferences]
 * keeps as a fast local cache.
 */
@Serializable
data class UserProfile(
    val id: String,
    @SerialName("full_name") val fullName: String? = null,
    val age: Int? = null,
    val sex: String? = null,
    val email: String? = null,
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean = false
)

/**
 * Reads/writes the signed-in user's [UserProfile]. Same shape as the other
 * repositories here (Context in, `Result` out) so call sites don't need
 * try/catch — see [AuthRepository].
 *
 * RLS means every query is implicitly scoped to the signed-in user; the
 * explicit `eq("id", userId)` filter below is belt-and-braces, and also what
 * makes [getProfile] a single-row lookup rather than a table scan.
 */
class ProfileRepository(context: Context) {
    private val client = SupabaseClientProvider.getInstance(context)

    private fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    /** Null means "signed in but hasn't finished onboarding yet". */
    suspend fun getProfile(): Result<UserProfile?> = runCatching {
        val userId = currentUserId() ?: return@runCatching null
        client.postgrest[TABLE]
            .select { filter { eq("id", userId) } }
            .decodeSingleOrNull<UserProfile>()
    }

    suspend fun upsertProfile(profile: UserProfile): Result<Unit> = runCatching {
        client.postgrest[TABLE].upsert(profile)
        Unit
    }

    /**
     * Saves the Phase 3 profile form and marks onboarding done, so future
     * sign-ins skip straight to the Dashboard (see [hasCompletedOnboarding]).
     */
    suspend fun saveOnboardingProfile(
        fullName: String,
        age: Int,
        sex: String,
        email: String
    ): Result<Unit> = runCatching {
        val userId = currentUserId() ?: error("You need to be signed in to save your profile.")
        client.postgrest[TABLE].upsert(
            UserProfile(
                id = userId,
                fullName = fullName,
                age = age,
                sex = sex,
                email = email,
                onboardingCompleted = true
            )
        )
        Unit
    }

    /**
     * Drives post-auth routing: Dashboard for a returning user, onboarding for
     * a brand-new one. Defaults to `false` on any failure (no network, RLS
     * misconfigured, table missing) so a user is never locked out of the app —
     * worst case they're asked to fill the profile form again, which is the
     * old behaviour rather than a hard error.
     */
    suspend fun hasCompletedOnboarding(): Boolean =
        getProfile().getOrNull()?.onboardingCompleted == true

    /** Signup-time name/email, used to pre-fill the profile form. */
    fun signupFullName(): String? =
        client.auth.currentUserOrNull()
            ?.userMetadata
            ?.get("full_name")
            ?.toString()
            ?.trim('"')
            ?.takeIf { it.isNotBlank() }

    fun signupEmail(): String? = client.auth.currentUserOrNull()?.email
}
