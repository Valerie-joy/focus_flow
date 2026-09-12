package com.focusflow.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.GradientButton
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.theme.FocusFlowRadius
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.ui.theme.LocalFocusFlowColors

@Composable
fun WelcomeScreen(
    onLogIn: () -> Unit,
    onCreateAccount: () -> Unit,
    onContinueWithGoogle: () -> Unit,
    onContinueWithApple: () -> Unit
) {
    BlurBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Welcome to",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "FocusFlow",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Discover what truly captures your attention.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(36.dp))

            GradientButton(text = "Log In", onClick = onLogIn)
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryButton(
                text = "Create account",
                onClick = onCreateAccount,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Divider()
                Text(
                    text = "  or  ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Divider()
            }
            Spacer(modifier = Modifier.height(20.dp))

            SocialSignInButton(label = "Continue with Google", onClick = onContinueWithGoogle)
            Spacer(modifier = Modifier.height(12.dp))
            SocialSignInButton(label = "Continue with Apple", onClick = onContinueWithApple)

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "By continuing, you agree to our Terms of Service and Privacy Policy.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun RowScope.Divider() {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(1.dp)
            .background(LocalFocusFlowColors.current.glassBorder)
    )
}

@Composable
private fun SocialSignInButton(label: String, onClick: () -> Unit) {
    val colors = LocalFocusFlowColors.current
    val shape = RoundedCornerShape(FocusFlowRadius.pill)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(colors.glassSurface)
            .border(width = 1.dp, color = colors.glassBorder, shape = shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    FocusFlowTheme {
        WelcomeScreen(onLogIn = {}, onCreateAccount = {}, onContinueWithGoogle = {}, onContinueWithApple = {})
    }
}
