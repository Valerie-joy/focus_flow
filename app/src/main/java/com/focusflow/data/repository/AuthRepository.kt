package com.focusflow.data.repository

import android.content.Context
import com.focusflow.data.remote.SupabaseClientProvider
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Thin wrapper around the Supabase Auth plugin. Every call returns
 * `Result<Unit>` instead of throwing, so Compose call sites (via
 * [com.focusflow.viewmodel.AuthViewModel]) can surface a plain error
 * message without a try/catch at the UI layer.
 */
class AuthRepository(context: Context) {
    private val client = SupabaseClientProvider.getInstance(context)

    /** SplashScreen observes this to decide Dashboard vs Welcome at launch. */
    val sessionStatus: StateFlow<SessionStatus> = client.auth.sessionStatus

    /**
     * Returns `Result.success(true)` if signup logged the user in
     * immediately, or `Result.success(false)` if the account was created
     * but is pending email confirmation (Supabase's "Confirm email" setting
     * is on, so no session comes back until the link is clicked).
     *
     * [fullName] is attached as signup user metadata so the name typed on the
     * Create Account screen survives to the onboarding profile form, which
     * pre-fills from it instead of asking for the same thing twice.
     */
    suspend fun signUpWithEmail(
        email: String,
        password: String,
        fullName: String? = null
    ): Result<Boolean> = runCatching {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            if (!fullName.isNullOrBlank()) {
                data = buildJsonObject { put("full_name", fullName) }
            }
        }
        client.auth.currentSessionOrNull() != null
    }

    suspend fun resendConfirmationEmail(email: String): Result<Unit> = runCatching {
        client.auth.resendEmail(OtpType.Email.SIGNUP, email)
    }

    suspend fun signInWithEmail(email: String, password: String): Result<Unit> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit> = runCatching {
        client.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
        }
    }

    suspend fun resetPasswordForEmail(email: String): Result<Unit> = runCatching {
        client.auth.resetPasswordForEmail(email)
    }

    suspend fun signOut(): Result<Unit> = runCatching {
        client.auth.signOut()
    }
}
