package com.focusflow.ui.screens.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.PremiumTextField
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.theme.FocusFlowTheme

data class RegisterFormState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = ""
)

@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    onCreateAccount: (RegisterFormState) -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var form by remember { mutableStateOf(RegisterFormState()) }
    val passwordsMismatch = form.confirmPassword.isNotEmpty() && form.confirmPassword != form.password
    val canSubmit = form.fullName.isNotBlank() && form.email.isNotBlank() &&
        form.password.isNotBlank() && !passwordsMismatch

    BlurBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Create account",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "A few details and you're on your way.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(28.dp))

            PremiumTextField(
                value = form.fullName,
                onValueChange = { form = form.copy(fullName = it) },
                label = "Full name",
                placeholder = "Jordan Lee",
                capitalization = KeyboardCapitalization.Words
            )
            Spacer(modifier = Modifier.height(16.dp))
            PremiumTextField(
                value = form.email,
                onValueChange = { form = form.copy(email = it) },
                label = "Email",
                placeholder = "you@example.com",
                keyboardType = KeyboardType.Email
            )
            Spacer(modifier = Modifier.height(16.dp))
            PremiumTextField(
                value = form.password,
                onValueChange = { form = form.copy(password = it) },
                label = "Password",
                placeholder = "••••••••",
                isPassword = true
            )
            Spacer(modifier = Modifier.height(16.dp))
            PremiumTextField(
                value = form.confirmPassword,
                onValueChange = { form = form.copy(confirmPassword = it) },
                label = "Confirm password",
                placeholder = "••••••••",
                isPassword = true,
                errorMessage = if (passwordsMismatch) "Passwords don't match" else null
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            PrimaryButton(
                text = "Create Account",
                onClick = { onCreateAccount(form) },
                loading = isLoading,
                enabled = canSubmit
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RegisterScreenPreview() {
    FocusFlowTheme {
        RegisterScreen(onBack = {}, onCreateAccount = {})
    }
}
