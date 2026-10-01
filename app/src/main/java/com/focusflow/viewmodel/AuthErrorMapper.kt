package com.focusflow.viewmodel

import android.util.Log
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * What went wrong, in a form the auth screens can act on.
 *
 * Separate from the message so a caller can behave differently — an
 * unconfirmed email needs a "resend" prompt, a rate limit needs the retry
 * button disabled for a moment, a cancelled sign-in needs no UI at all.
 */
enum class AuthFailureKind {
    INVALID_CREDENTIALS,
    EMAIL_NOT_CONFIRMED,
    EMAIL_ALREADY_REGISTERED,
    INVALID_EMAIL,
    WEAK_PASSWORD,
    RATE_LIMITED,
    SIGNUP_DISABLED,
    /** The account step succeeded but the confirmation email could not be sent. */
    CONFIRMATION_EMAIL_FAILED,
    SERVICE_UNAVAILABLE,
    NO_CONNECTION,
    TIMEOUT,
    CANCELLED,
    UNKNOWN
}

data class AuthFailure(val kind: AuthFailureKind, val message: String)

/**
 * Maps an auth throwable onto a kind and a user-facing message.
 *
 * This replaces `error.message ?: fallback`, which let backend text reach the
 * screen verbatim. The worst case was a phone with no connection: Ktor's
 * message is
 *
 * > HTTP request to https://<project>.supabase.co/auth/v1/token?grant_type=password
 * > (POST) failed with message: Unable to resolve host … No address associated
 * > with hostname
 *
 * which names the backend host in the UI, reads as a server fault when the
 * server is fine, and tells the user nothing they can do. Even the tidier
 * cases leaked implementation detail ("Invalid login credentials" is
 * gotrue's phrasing, not a sentence written for anyone).
 *
 * Every branch below is driven by something the API actually reports — a real
 * [AuthErrorCode], an HTTP status, or an exception type in the cause chain. No
 * failure mode is guessed at from string matching on a message, because those
 * strings are a backend implementation detail that can change under us.
 */
object AuthErrorMapper {

    fun map(error: Throwable): AuthFailure {
        // Log the real failure before it is replaced by a friendly sentence.
        //
        // Deliberately one-directional: the mapped message is written for the
        // person using the app and must never carry backend detail, but with
        // nothing logged there was no way to tell *why* a generic failure
        // happened — a 500 from signup and a 500 from a database trigger look
        // identical on screen. This writes the cause chain, the HTTP status and
        // the AuthErrorCode to logcat under TAG, where a developer can read it
        // and a user never sees it.
        logCause(error)
        // The chain is collected once, guarding against a self-referencing
        // cause. The HTTP client wraps transport failures, so the interesting
        // exception is rarely the outermost one.
        val chain = buildList {
            var cause: Throwable? = error
            while (cause != null && none { it === cause }) {
                add(cause)
                cause = cause.cause
            }
        }

        // Connectivity is checked first, and by *specificity* rather than by
        // position in the chain. UnknownHostException and SocketTimeoutException
        // both extend IOException, and the HTTP client wraps them in a plain
        // IOException — so walking the chain in order and taking the first
        // match lets the generic wrapper answer first, reporting "couldn't
        // reach the server" for what is really no connection at all. Each
        // specific type is therefore searched across the whole chain before the
        // generic one is considered.
        connectivityFailure(chain)?.let { return it }

        chain.filterIsInstance<AuthRestException>().firstOrNull()?.let { rest ->
            return fromErrorCode(rest.errorCode, rest.errorDescription)
        }

        // A non-auth REST failure: 5xx means the service is having a problem,
        // which is worth distinguishing from "your details are wrong".
        chain.filterIsInstance<RestException>().firstOrNull()?.let { rest ->
            if (rest.statusCode >= 500) {
                return AuthFailure(
                    AuthFailureKind.SERVICE_UNAVAILABLE,
                    "The account service isn't responding right now. Please try again shortly."
                )
            }
        }

        return AuthFailure(
            AuthFailureKind.UNKNOWN,
            "Something went wrong signing you in. Please try again."
        )
    }

    private const val TAG = "FocusFlowAuth"

    /**
     * Writes everything known about a failure to logcat.
     *
     * `adb logcat -s FocusFlowAuth` is the one command that answers "what did
     * the backend actually say".
     */
    private fun logCause(error: Throwable) {
        val detail = buildString {
            var cause: Throwable? = error
            val seen = mutableListOf<Throwable>()
            while (cause != null && seen.none { it === cause }) {
                seen += cause
                append(cause::class.java.simpleName)
                cause.message?.let { append(": ").append(it) }
                if (cause is AuthRestException) {
                    append(" [errorCode=").append(cause.errorCode)
                    append(", description=").append(cause.errorDescription).append(']')
                }
                if (cause is RestException) {
                    append(" [status=").append(cause.statusCode).append(']')
                }
                cause = cause.cause
                if (cause != null) append("\n  caused by ")
            }
        }
        Log.w(TAG, "Auth failure: $detail", error)
    }

    private fun connectivityFailure(chain: List<Throwable>): AuthFailure? = when {
        // DNS resolution failed, which in practice means no working connection.
        chain.any { it is UnknownHostException } -> AuthFailure(
            AuthFailureKind.NO_CONNECTION,
            "No internet connection. Check your Wi-Fi or mobile data and try again."
        )
        chain.any { it is SocketTimeoutException } -> AuthFailure(
            AuthFailureKind.TIMEOUT,
            "That took too long to respond. Check your connection and try again."
        )
        // Only now the generic transport failure.
        chain.any { it is HttpRequestException || it is IOException } -> AuthFailure(
            AuthFailureKind.NO_CONNECTION,
            "Couldn't reach the server. Check your internet connection and try again."
        )
        else -> null
    }

    /**
     * Only the codes this app can actually reach are named. Everything else
     * falls through to a single honest "something went wrong" rather than a
     * long tail of messages for MFA, SAML and SSO paths FocusFlow does not use
     * — a message for an impossible state is worse than a generic one, because
     * it sends the reader looking for a setting that isn't there.
     */
    /**
     * Internal rather than private so the mapping can be unit tested directly.
     * Building a real [AuthRestException] needs a Ktor `HttpResponse`, which
     * would mean standing up an engine just to check a `when` branch.
     */
    internal fun fromErrorCode(
        code: AuthErrorCode?,
        description: String? = null
    ): AuthFailure = when {
        // Checked before the plain `code` branches: UnexpectedFailure is
        // Supabase's catch-all, and this particular one is neither unexpected
        // nor transient — see the branch body.
        code == AuthErrorCode.UnexpectedFailure &&
            description?.contains("confirmation email", ignoreCase = true) == true ->
            AuthFailure(
                AuthFailureKind.CONFIRMATION_EMAIL_FAILED,
                "Your account may have been created, but the confirmation email " +
                    "couldn't be sent. Try signing in — if that doesn't work, the " +
                    "email service needs attention before new accounts can be confirmed."
            )

        else -> fromCodeOnly(code)
    }

    private fun fromCodeOnly(code: AuthErrorCode?): AuthFailure = when (code) {
        AuthErrorCode.InvalidCredentials, AuthErrorCode.UserNotFound -> AuthFailure(
            AuthFailureKind.INVALID_CREDENTIALS,
            // Deliberately does not say which of the two was wrong: confirming
            // that an email exists but the password doesn't match hands an
            // attacker a way to enumerate registered accounts.
            "That email and password don't match an account. Check both and try again."
        )

        AuthErrorCode.EmailNotConfirmed -> AuthFailure(
            AuthFailureKind.EMAIL_NOT_CONFIRMED,
            "Confirm your email address before signing in — check your inbox for the link."
        )

        AuthErrorCode.EmailExists, AuthErrorCode.UserAlreadyExists -> AuthFailure(
            AuthFailureKind.EMAIL_ALREADY_REGISTERED,
            "An account already uses that email. Try signing in instead."
        )

        AuthErrorCode.EmailAddressInvalid, AuthErrorCode.ValidationFailed -> AuthFailure(
            AuthFailureKind.INVALID_EMAIL,
            "That email address wasn't accepted. Check it for typos."
        )

        AuthErrorCode.WeakPassword, AuthErrorCode.SamePassword -> AuthFailure(
            AuthFailureKind.WEAK_PASSWORD,
            "Choose a stronger password — longer is better than more symbols."
        )

        AuthErrorCode.OverRequestRateLimit,
        AuthErrorCode.OverEmailSendRateLimit -> AuthFailure(
            AuthFailureKind.RATE_LIMITED,
            "Too many attempts. Wait a minute or two, then try again."
        )

        AuthErrorCode.SignupDisabled,
        AuthErrorCode.EmailProviderDisabled,
        AuthErrorCode.EmailAddressNotAuthorized -> AuthFailure(
            AuthFailureKind.SIGNUP_DISABLED,
            "New accounts aren't being accepted at the moment."
        )

        AuthErrorCode.UserBanned -> AuthFailure(
            AuthFailureKind.INVALID_CREDENTIALS,
            "This account can't be used to sign in. Contact support if that's unexpected."
        )

        AuthErrorCode.SessionExpired,
        AuthErrorCode.SessionNotFound,
        AuthErrorCode.RefreshTokenNotFound,
        AuthErrorCode.RefreshTokenAlreadyUsed -> AuthFailure(
            AuthFailureKind.INVALID_CREDENTIALS,
            "Your session expired. Please sign in again."
        )

        AuthErrorCode.RequestTimeout -> AuthFailure(
            AuthFailureKind.TIMEOUT,
            "The server took too long to respond. Please try again."
        )

        AuthErrorCode.UnexpectedFailure -> AuthFailure(
            AuthFailureKind.SERVICE_UNAVAILABLE,
            "The account service hit a problem. Please try again shortly."
        )

        else -> AuthFailure(
            AuthFailureKind.UNKNOWN,
            "Something went wrong. Please try again."
        )
    }
}
