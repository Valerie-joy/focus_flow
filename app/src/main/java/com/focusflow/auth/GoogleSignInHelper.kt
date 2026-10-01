package com.focusflow.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.focusflow.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

private const val PLACEHOLDER_CLIENT_ID = "REPLACE_ME_GOOGLE_WEB_CLIENT_ID"

/**
 * Wraps Android's Credential Manager to obtain a Google ID token for
 * Supabase's native Google sign-in (`auth.signInWith(IDToken)`). Requires a
 * real Google Cloud "Web application" OAuth client ID in
 * BuildConfig.GOOGLE_WEB_CLIENT_ID — see SUPABASE_SETUP.md. Until that's
 * configured, [getGoogleIdToken] fails fast with a clear message instead of
 * a confusing Credential Manager error deep in a system dialog.
 */
class GoogleSignInHelper(private val context: Context) {

    suspend fun getGoogleIdToken(): Result<String> {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank() || BuildConfig.GOOGLE_WEB_CLIENT_ID == PLACEHOLDER_CLIENT_ID) {
            return Result.failure(
                IllegalStateException(
                    "Google sign-in isn't configured yet — set a real GOOGLE_WEB_CLIENT_ID in local.properties (see SUPABASE_SETUP.md)."
                )
            )
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val response = CredentialManager.create(context).getCredential(
                request = request,
                context = context
            )
            val credential = GoogleIdTokenCredential.createFrom(response.credential.data)
            Result.success(credential.idToken)
        } catch (e: GetCredentialCancellationException) {
            // The user dismissed the account sheet. That is a choice, not a
            // fault — surfacing it as an error dialog ("Couldn't sign in with
            // Google") tells them off for changing their mind.
            Result.failure(SignInCancelled)
        } catch (e: NoCredentialException) {
            // No Google account on the device, or none that can be offered.
            // The platform message for this is unhelpfully generic, so it is
            // replaced with the actual next step.
            Result.failure(
                IllegalStateException(
                    "No Google account is available on this device. Add one in " +
                        "Settings, or sign in with an email address instead."
                )
            )
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: GoogleIdTokenParsingException) {
            Result.failure(
                IllegalStateException("Google sign-in returned something unreadable. Please try again.")
            )
        }
    }
}

/**
 * Marks a sign-in the user backed out of.
 *
 * A distinct type rather than a message string so callers can tell "the user
 * cancelled" apart from "it failed", and stay silent for the former.
 */
object SignInCancelled : Exception("Sign-in cancelled")
