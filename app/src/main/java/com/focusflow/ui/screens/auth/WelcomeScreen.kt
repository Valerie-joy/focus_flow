package com.focusflow.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.components.SecondaryButton
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.ui.theme.Sizing
import com.focusflow.ui.theme.Spacing

/**
 * The unauthenticated landing screen.
 *
 * Three fixes here, all of them things a user would hit:
 *
 *  - **"Continue with Apple" did nothing.** It was wired to an empty lambda
 *    (`onContinueWithApple = { /* out of scope */ }`) — Apple sign-in needs an
 *    Apple Developer account and a Services ID that this project does not have.
 *    A visible button that silently swallows the tap is worse than no button,
 *    so both it and its parameter are gone. Re-adding it means adding an
 *    `onContinueWithApple` parameter here and a real implementation behind it,
 *    the same shape as the Google path.
 *  - **Google sign-in had no loading state.** Fetching a credential opens a
 *    system sheet after a round trip; until it appeared, the screen looked
 *    unresponsive and invited a second tap.
 *  - **The screen could not scroll.** `Arrangement.Bottom` with a weighted
 *    spacer meant that at large font scales the heading and the legal line
 *    pushed the buttons off the top and bottom respectively, with no way to
 *    reach them.
 */
@Composable
fun WelcomeScreen(
    onLogIn: () -> Unit,
    onCreateAccount: () -> Unit,
    onContinueWithGoogle: () -> Unit,
    modifier: Modifier = Modifier,
    isGoogleSignInLoading: Boolean = false
) {
    BlurBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = Sizing.maxContentWidth)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.gutter),
                verticalArrangement = Arrangement.Bottom
            ) {
                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "Welcome to",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "FocusFlow",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    // Says what the app does rather than only how it feels — this
                    // is the first thing a new user reads.
                    text = "See what holds your attention. Watch a few short clips while " +
                        "your phone's front camera follows where your eyes go.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(Spacing.xxl))

                PrimaryButton(
                    text = "Log in",
                    onClick = onLogIn,
                    enabled = !isGoogleSignInLoading
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                SecondaryButton(
                    text = "Create account",
                    onClick = onCreateAccount,
                    enabled = !isGoogleSignInLoading
                )

                Spacer(modifier = Modifier.height(Spacing.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HairlineDivider()
                    Text(
                        text = "  or  ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HairlineDivider()
                }
                Spacer(modifier = Modifier.height(Spacing.md))

                SecondaryButton(
                    text = "Continue with Google",
                    onClick = onContinueWithGoogle,
                    loading = isGoogleSignInLoading
                )

                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = "By continuing, you agree to our Terms of Service and Privacy Policy.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }
    }
}

@Composable
private fun RowScope.HairlineDivider() {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(Sizing.hairline)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Preview(showBackground = true, name = "Welcome")
@Composable
private fun WelcomeScreenPreview() {
    FocusFlowTheme {
        WelcomeScreen(onLogIn = {}, onCreateAccount = {}, onContinueWithGoogle = {})
    }
}

@Preview(showBackground = true, name = "Welcome — Google loading, dark")
@Composable
private fun WelcomeScreenLoadingPreview() {
    FocusFlowTheme(darkTheme = true) {
        WelcomeScreen(
            onLogIn = {},
            onCreateAccount = {},
            onContinueWithGoogle = {},
            isGoogleSignInLoading = true
        )
    }
}
