package com.focusflow.ui.screens.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.focusflow.ui.components.NumberStepper
import com.focusflow.ui.components.PremiumTextField
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.components.SegmentedToggle
import com.focusflow.ui.theme.FocusFlowTheme

data class UserRegistrationFormState(
    val fullName: String = "",
    val age: Int = 12,
    val sexIndex: Int = 0, // 0 = Male, 1 = Female — matches sexOptions below
    val email: String = ""
)

val sexOptions = listOf("Male", "Female")

/**
 * Phase 3 — collects the profile details used to personalize the
 * assessment and recommendations later. Age is clamped to 5-30 per spec
 * via [NumberStepper]'s range rather than free text, so there's no invalid
 * state to validate against.
 */
@Composable
fun UserRegistrationScreen(
    onCreateProfile: (UserRegistrationFormState) -> Unit,
    isLoading: Boolean = false,
    initialFullName: String = "",
    initialEmail: String = ""
) {
    var form by remember {
        mutableStateOf(
            UserRegistrationFormState(fullName = initialFullName, email = initialEmail)
        )
    }
    val canSubmit = form.fullName.isNotBlank() && form.email.isNotBlank()

    BlurBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "Create your profile",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "This helps us personalize your assessment.",
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

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Age",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            NumberStepper(
                value = form.age,
                onValueChange = { form = form.copy(age = it) },
                range = 5..30,
                label = "years old"
            )

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Sex",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            SegmentedToggle(
                options = sexOptions,
                selectedIndex = form.sexIndex,
                onSelect = { form = form.copy(sexIndex = it) }
            )

            Spacer(modifier = Modifier.height(20.dp))
            PremiumTextField(
                value = form.email,
                onValueChange = { form = form.copy(email = it) },
                label = "Email",
                placeholder = "you@example.com",
                keyboardType = KeyboardType.Email
            )

            Spacer(modifier = Modifier.height(28.dp))
            PrimaryButton(
                text = "Create Profile",
                onClick = { onCreateProfile(form) },
                loading = isLoading,
                enabled = canSubmit
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UserRegistrationScreenPreview() {
    FocusFlowTheme {
        UserRegistrationScreen(onCreateProfile = {})
    }
}
