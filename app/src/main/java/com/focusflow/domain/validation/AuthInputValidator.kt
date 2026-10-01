package com.focusflow.domain.validation

/**
 * Validation for the auth forms.
 *
 * Lives in `domain` rather than inside the composables so it can be unit
 * tested on the JVM without a device, and so the sign-in and registration
 * screens can't drift into disagreeing about what a valid email is.
 *
 * The rules deliberately mirror what the backend will actually reject —
 * Supabase enforces a six-character minimum password by default — because a
 * client rule that is *stricter* than the server invents a failure the user
 * cannot explain, and one that is *looser* wastes a round trip to learn
 * something we already knew.
 *
 * Every message is written for the person reading it: it says what is wrong
 * and what to do, and never names a field the user can't see.
 */
object AuthInputValidator {

    /** Supabase Auth's default minimum. Keep in sync with the project setting. */
    const val MIN_PASSWORD_LENGTH = 6

    /**
     * Deliberately permissive: one or more non-space, non-@ characters, an @,
     * a domain with at least one dot, and a 2+ character TLD.
     *
     * Full RFC 5322 conformance is not the goal and is actively harmful here —
     * a regex strict enough to be "correct" rejects real, deliverable
     * addresses (plus-addressing, new TLDs, unicode domains). This only aims
     * to catch the genuine typos: a missing @, a trailing comma, "gmail.con"
     * is *not* something a client can know is wrong. The server remains the
     * authority on whether an address exists.
     */
    private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

    fun emailError(email: String): String? = when {
        email.isBlank() -> "Enter your email address."
        !EMAIL_PATTERN.matches(email.trim()) -> "That doesn't look like an email address."
        else -> null
    }

    fun passwordError(password: String): String? = when {
        password.isEmpty() -> "Enter your password."
        password.length < MIN_PASSWORD_LENGTH ->
            "Passwords need at least $MIN_PASSWORD_LENGTH characters."
        else -> null
    }

    fun confirmPasswordError(password: String, confirmPassword: String): String? = when {
        confirmPassword.isEmpty() -> "Re-enter your password."
        confirmPassword != password -> "These passwords don't match."
        else -> null
    }

    fun fullNameError(fullName: String): String? = when {
        fullName.isBlank() -> "Enter your name."
        fullName.trim().length < 2 -> "Enter your full name."
        else -> null
    }

    /**
     * Whether the sign-in form can be submitted.
     *
     * Sign-in intentionally does *not* apply [passwordError]'s length rule: an
     * account created before the minimum changed may legitimately have a
     * shorter password, and refusing to even attempt sign-in would lock that
     * user out of their own account with a message about a rule they never
     * agreed to. Length is a rule for *choosing* a password, not for typing an
     * existing one.
     */
    fun canSubmitSignIn(email: String, password: String): Boolean =
        emailError(email) == null && password.isNotEmpty()

    fun canSubmitRegistration(
        fullName: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Boolean = fullNameError(fullName) == null &&
        emailError(email) == null &&
        passwordError(password) == null &&
        confirmPasswordError(password, confirmPassword) == null

    /**
     * A coarse 0..3 strength band, shown as a meter while the user types.
     *
     * Presented as guidance only — it never blocks submission, because a
     * strength meter that gates the form trains users to append "1!" to a weak
     * password rather than choose a better one. Bands are length-led, since
     * length dominates real-world guess resistance far more than character
     * classes do.
     */
    fun passwordStrength(password: String): PasswordStrength {
        if (password.length < MIN_PASSWORD_LENGTH) return PasswordStrength.TOO_SHORT
        val classes = listOf(
            password.any { it.isLowerCase() },
            password.any { it.isUpperCase() },
            password.any { it.isDigit() },
            password.any { !it.isLetterOrDigit() }
        ).count { it }
        return when {
            password.length >= 12 && classes >= 2 -> PasswordStrength.STRONG
            password.length >= 10 || classes >= 3 -> PasswordStrength.FAIR
            else -> PasswordStrength.WEAK
        }
    }
}

enum class PasswordStrength(val label: String) {
    TOO_SHORT("Too short"),
    WEAK("Weak"),
    FAIR("Fair"),
    STRONG("Strong")
}
