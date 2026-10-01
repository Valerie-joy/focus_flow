package com.focusflow.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the auth form rules.
 *
 * The cases worth writing here are the ones where a wrong answer has a real
 * consequence for a user: rejecting an address that is actually deliverable,
 * or refusing to let an existing account sign in because its password predates
 * a rule change. Both are locked down below.
 */
class AuthInputValidatorTest {

    @Test
    fun `blank email asks for one`() {
        assertEquals("Enter your email address.", AuthInputValidator.emailError(""))
        assertEquals("Enter your email address.", AuthInputValidator.emailError("   "))
    }

    @Test
    fun `malformed emails are rejected`() {
        listOf(
            "notanemail",          // no @ at all
            "missing@domain",      // no dot in the domain
            "@example.com",        // no local part
            "user@.com",           // empty domain label
            "user@example.c",      // single-character TLD
            "user name@example.com" // space in the local part
        ).forEach { input ->
            assertNotNull("expected '$input' to be rejected", AuthInputValidator.emailError(input))
        }
    }

    /**
     * The regression that matters most. A stricter "RFC-correct" pattern would
     * reject several of these, and every one is an address a real person uses.
     */
    @Test
    fun `real-world valid emails are accepted`() {
        listOf(
            "user@example.com",
            "first.last@example.co.uk",
            "user+tag@example.com",      // plus-addressing
            "user_name@example-host.com",
            "u@a.io",                    // very short, still valid
            "someone@sub.domain.example.museum" // long TLD
        ).forEach { input ->
            assertNull("expected '$input' to be accepted", AuthInputValidator.emailError(input))
        }
    }

    @Test
    fun `surrounding whitespace does not invalidate an email`() {
        assertNull(AuthInputValidator.emailError("  user@example.com  "))
    }

    @Test
    fun `password must meet the backend minimum`() {
        assertEquals("Enter your password.", AuthInputValidator.passwordError(""))
        assertNotNull(AuthInputValidator.passwordError("abc"))
        assertNull(AuthInputValidator.passwordError("a".repeat(AuthInputValidator.MIN_PASSWORD_LENGTH)))
    }

    @Test
    fun `confirmation must match`() {
        assertNotNull(AuthInputValidator.confirmPasswordError("secret123", ""))
        assertEquals(
            "These passwords don't match.",
            AuthInputValidator.confirmPasswordError("secret123", "secret124")
        )
        assertNull(AuthInputValidator.confirmPasswordError("secret123", "secret123"))
    }

    /**
     * Sign-in deliberately does not enforce the length minimum: an account
     * created before the rule changed would otherwise be locked out of itself
     * by a client-side check the server never asked for.
     */
    @Test
    fun `sign-in accepts a short existing password`() {
        assertTrue(AuthInputValidator.canSubmitSignIn("user@example.com", "old1"))
    }

    @Test
    fun `sign-in still requires a well-formed email and a non-empty password`() {
        assertFalse(AuthInputValidator.canSubmitSignIn("not-an-email", "whatever"))
        assertFalse(AuthInputValidator.canSubmitSignIn("user@example.com", ""))
    }

    @Test
    fun `registration requires every field to be valid`() {
        assertTrue(
            AuthInputValidator.canSubmitRegistration(
                fullName = "Jordan Lee",
                email = "jordan@example.com",
                password = "longenough",
                confirmPassword = "longenough"
            )
        )
        // A mismatched confirmation blocks submission even when all else passes.
        assertFalse(
            AuthInputValidator.canSubmitRegistration(
                fullName = "Jordan Lee",
                email = "jordan@example.com",
                password = "longenough",
                confirmPassword = "longenoug"
            )
        )
        // A one-character name is a typo, not a name.
        assertFalse(
            AuthInputValidator.canSubmitRegistration(
                fullName = "J",
                email = "jordan@example.com",
                password = "longenough",
                confirmPassword = "longenough"
            )
        )
    }

    @Test
    fun `password strength bands are ordered by length and variety`() {
        assertEquals(PasswordStrength.TOO_SHORT, AuthInputValidator.passwordStrength("abc"))
        assertEquals(PasswordStrength.WEAK, AuthInputValidator.passwordStrength("abcdef"))
        assertEquals(PasswordStrength.FAIR, AuthInputValidator.passwordStrength("abcdefghij"))
        assertEquals(PasswordStrength.FAIR, AuthInputValidator.passwordStrength("Abc123!"))
        assertEquals(PasswordStrength.STRONG, AuthInputValidator.passwordStrength("Abcdefgh1234"))
    }

    /**
     * The meter is advisory. A weak password must still be submittable, or the
     * meter becomes a gate that teaches users to append "1!" rather than to
     * choose a better password.
     */
    @Test
    fun `a weak but valid password can still be submitted`() {
        assertEquals(PasswordStrength.WEAK, AuthInputValidator.passwordStrength("abcdef"))
        assertNull(AuthInputValidator.passwordError("abcdef"))
    }
}
