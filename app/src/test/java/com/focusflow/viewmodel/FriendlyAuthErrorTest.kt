package com.focusflow.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * A phone with no working connection used to show the raw Ktor failure —
 * backend hostname, URL, query string and all — which reads as a server fault
 * and offers the user nothing to do about it.
 */
class FriendlyAuthErrorTest {

    private val fallback = "Couldn't sign in."

    @Test
    fun `dns failure reports no internet rather than the raw host`() {
        val real = UnknownHostException(
            "Unable to resolve host \"edmqbypcbinqtutgttpl.supabase.co\": No address associated with hostname"
        )
        val message = friendlyAuthError(real, fallback)

        assertEquals("No internet connection. Check your Wi-Fi or mobile data and try again.", message)
        assertFalse("The backend host must not leak into the UI", message.contains("supabase.co"))
        assertFalse(message.contains("Unable to resolve host"))
    }

    @Test
    fun `a wrapped dns failure is still recognised`() {
        // Ktor wraps the cause, which is how it actually reaches the ViewModel.
        val wrapped = RuntimeException(
            "HTTP request to https://project.supabase.co/auth/v1/token?grant_type=password (POST) failed",
            IOException("connection failure", UnknownHostException("No address associated with hostname"))
        )
        val message = friendlyAuthError(wrapped, fallback)

        assertTrue(message.startsWith("No internet connection"))
        assertFalse(message.contains("supabase.co"))
        assertFalse(message.contains("grant_type"))
    }

    @Test
    fun `timeouts and generic io failures get their own wording`() {
        assertEquals(
            "The connection timed out. Check your internet and try again.",
            friendlyAuthError(SocketTimeoutException("timeout"), fallback)
        )
        assertEquals(
            "Couldn't reach the server. Check your internet and try again.",
            friendlyAuthError(IOException("broken pipe"), fallback)
        )
    }

    @Test
    fun `meaningful server messages are preserved`() {
        // Supabase's own auth errors already say the useful thing.
        assertEquals(
            "Invalid login credentials",
            friendlyAuthError(RuntimeException("Invalid login credentials"), fallback)
        )
    }

    @Test
    fun `a message-less failure falls back to the caller's wording`() {
        assertEquals(fallback, friendlyAuthError(RuntimeException(), fallback))
    }

    @Test
    fun `a self-referencing cause chain terminates`() {
        // Guards the cycle-detection in the walk; a malformed chain must not hang.
        val a = RuntimeException("outer")
        val b = RuntimeException("inner", a)
        a.initCause(b)

        assertEquals("outer", friendlyAuthError(a, fallback))
    }
}
