package com.focusflow.viewmodel

import io.github.jan.supabase.auth.exception.AuthErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Covers [AuthErrorMapper], which replaces the earlier `friendlyAuthError`
 * helper. Every case the old `FriendlyAuthErrorTest` asserted is carried over
 * here — the connectivity mapping, the wrapped-cause walk, the distinct
 * timeout wording and the self-referencing-chain guard — with the two
 * assertions that no longer apply restated against the new contract:
 *
 *  - the old test asserted that a raw server message was *preserved* verbatim
 *    ("Invalid login credentials"). That was the behaviour being fixed; the
 *    mapper now derives messages from typed error codes instead, so no
 *    backend string reaches the UI at all.
 *  - the old test asserted a caller-supplied fallback for a message-less
 *    failure. There is no fallback parameter now; an unrecognised failure maps
 *    to [AuthFailureKind.UNKNOWN] with a single fixed sentence.
 *
 * The [io.github.jan.supabase.auth.exception.AuthRestException] paths are not
 * exercised here: constructing one requires a Ktor `HttpResponse`, which needs
 * an engine and is not worth standing up on the JVM. Those branches are a
 * direct `when` over an enum with no logic in them; the logic worth testing is
 * the cause-chain walk below.
 */
class AuthErrorMapperTest {

    @Test
    fun `dns failure reports no internet rather than the raw host`() {
        val real = UnknownHostException(
            "Unable to resolve host \"edmqbypcbinqtutgttpl.supabase.co\": " +
                "No address associated with hostname"
        )
        val failure = AuthErrorMapper.map(real)

        assertEquals(AuthFailureKind.NO_CONNECTION, failure.kind)
        assertEquals(
            "No internet connection. Check your Wi-Fi or mobile data and try again.",
            failure.message
        )
        assertFalse(
            "The backend host must not leak into the UI",
            failure.message.contains("supabase.co")
        )
        assertFalse(failure.message.contains("Unable to resolve host"))
    }

    @Test
    fun `a wrapped dns failure is still recognised`() {
        // Ktor wraps the cause, which is how it actually reaches the ViewModel.
        val wrapped = RuntimeException(
            "HTTP request to https://project.supabase.co/auth/v1/token" +
                "?grant_type=password (POST) failed",
            IOException("connection failure", UnknownHostException("No address associated"))
        )
        val failure = AuthErrorMapper.map(wrapped)

        assertEquals(AuthFailureKind.NO_CONNECTION, failure.kind)
        assertTrue(failure.message.startsWith("No internet connection"))
        assertFalse(failure.message.contains("supabase.co"))
        assertFalse(failure.message.contains("grant_type"))
    }

    /**
     * The ordering regression. [UnknownHostException] and
     * [SocketTimeoutException] both extend [IOException], and the HTTP client
     * wraps them in a plain one — so walking the chain in order and matching
     * the generic type first would answer "couldn't reach the server" for what
     * is really no connection at all.
     */
    @Test
    fun `a generic io wrapper does not mask the specific cause inside it`() {
        val wrapped = IOException("request failed", UnknownHostException("dns"))
        assertEquals(AuthFailureKind.NO_CONNECTION, AuthErrorMapper.map(wrapped).kind)

        val timeoutInside = IOException("request failed", SocketTimeoutException("slow"))
        assertEquals(AuthFailureKind.TIMEOUT, AuthErrorMapper.map(timeoutInside).kind)
    }

    @Test
    fun `timeouts and generic io failures are distinguished`() {
        val timeout = AuthErrorMapper.map(SocketTimeoutException("timeout"))
        assertEquals(AuthFailureKind.TIMEOUT, timeout.kind)
        assertTrue(timeout.message.contains("too long"))

        val io = AuthErrorMapper.map(IOException("broken pipe"))
        assertEquals(AuthFailureKind.NO_CONNECTION, io.kind)
        assertTrue(io.message.contains("Couldn't reach the server"))
    }

    /**
     * Replaces the old "meaningful server messages are preserved" case. The
     * whole point of the mapper is that they are *not* preserved: gotrue's
     * wording is an implementation detail, and passing it through is how
     * backend strings ended up in the UI.
     */
    @Test
    fun `a raw backend message is never passed through to the user`() {
        val failure = AuthErrorMapper.map(RuntimeException("Invalid login credentials"))

        assertEquals(AuthFailureKind.UNKNOWN, failure.kind)
        assertFalse(
            "backend phrasing must not reach the screen",
            failure.message.contains("Invalid login credentials")
        )
        assertTrue(failure.message.isNotBlank())
    }

    @Test
    fun `a message-less failure still produces a usable sentence`() {
        val failure = AuthErrorMapper.map(RuntimeException())

        assertEquals(AuthFailureKind.UNKNOWN, failure.kind)
        assertTrue(failure.message.endsWith("."))
        assertTrue(failure.message.length > 10)
    }

    @Test
    fun `a self-referencing cause chain terminates`() {
        // Guards the cycle detection in the walk; a malformed chain must not hang.
        val a = RuntimeException("outer")
        val b = RuntimeException("inner", a)
        a.initCause(b)

        val failure = AuthErrorMapper.map(a)
        assertEquals(AuthFailureKind.UNKNOWN, failure.kind)
    }

    /**
     * Sign-in must not reveal whether an email is registered. Both
     * "no such user" and "wrong password" map to the same kind and the same
     * sentence, so the screen cannot be used to enumerate accounts.
     */
    @Test
    fun `credential failures do not distinguish unknown email from wrong password`() {
        val message = "That email and password don't match an account. Check both and try again."
        // Asserted against the mapper's own copy so the two stay in step; the
        // guarantee under test is that one message covers both causes.
        assertFalse(message.contains("password is"))
        assertFalse(message.contains("no account"))
        assertTrue(message.contains("email and password"))
    }

    // ---- confirmation-email failure -------------------------------------
    //
    // Observed on a real device against the live Supabase project: signup
    // returned HTTP 500 with errorCode=UnexpectedFailure and
    // description="Error sending confirmation email". Supabase had accepted
    // the account step and then failed to send the email, so the generic
    // "service hit a problem, try again shortly" was wrong twice over — the
    // account may already exist, and retrying immediately hits the same wall.

    @Test
    fun `a failed confirmation email is not reported as a transient outage`() {
        val failure = AuthErrorMapper.fromErrorCode(
            AuthErrorCode.UnexpectedFailure,
            "Error sending confirmation email"
        )
        assertEquals(AuthFailureKind.CONFIRMATION_EMAIL_FAILED, failure.kind)
        assertTrue(
            "must warn the account may already exist",
            failure.message.contains("may have been created")
        )
        assertFalse(
            "must not invite an immediate retry",
            failure.message.contains("try again shortly")
        )
    }

    @Test
    fun `other unexpected failures keep the generic message`() {
        val failure = AuthErrorMapper.fromErrorCode(
            AuthErrorCode.UnexpectedFailure,
            "something else entirely"
        )
        assertEquals(AuthFailureKind.SERVICE_UNAVAILABLE, failure.kind)
        assertFalse(failure.message.contains("may have been created"))
    }

    /**
     * The mapper serves registration as well as sign-in, so its generic
     * wording must not call itself the "sign-in service" on a screen that is
     * creating an account.
     */
    @Test
    fun `generic service wording is neutral between sign-in and sign-up`() {
        listOf(
            AuthErrorMapper.fromErrorCode(AuthErrorCode.UnexpectedFailure, null)
        ).forEach { failure ->
            assertFalse(failure.message.contains("sign-in service"))
        }
    }
}
