package com.focusflow.ui.screens.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.GlassCard
import com.focusflow.ui.components.PremiumDialog
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.theme.FocusFlowRadius
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.ui.theme.LocalFocusFlowColors
import com.focusflow.ui.theme.Success

sealed interface UploadState {
    data object Idle : UploadState
    data class Selected(val fileName: String) : UploadState
    data object Validating : UploadState
    data object Valid : UploadState
    data class Invalid(val reason: String) : UploadState
}

/**
 * Document upload + validation UI for users who already have an ADHD
 * diagnosis. Actual validation (parsing the PDF/image, checking it looks
 * like a real diagnosis letter, virus scanning, etc.) belongs in a
 * repository/use-case layer — [onValidateDocument] is the seam where that
 * gets plugged in; this composable only reflects whatever state it's given.
 */
@Composable
fun AdhdUploadScreen(
    uploadState: UploadState,
    onFileSelected: (Uri) -> Unit,
    onContinue: () -> Unit,
    onDismissError: () -> Unit,
    onRetry: () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let(onFileSelected) }

    BlurBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Upload your diagnosis",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "PDF, PNG, or JPEG. We'll confirm it looks like a valid document before continuing.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            UploadDropZone(
                uploadState = uploadState,
                onTap = { launcher.launch("*/*") }
            )

            Spacer(modifier = Modifier.weight(1f))

            PrimaryButton(
                text = "Continue",
                onClick = onContinue,
                enabled = uploadState is UploadState.Valid
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (uploadState is UploadState.Invalid) {
        PremiumDialog(
            title = "Couldn't verify document",
            message = uploadState.reason,
            onDismiss = onDismissError,
            primaryActionLabel = "Try another file",
            onPrimaryAction = onRetry
        )
    }
}

@Composable
private fun UploadDropZone(uploadState: UploadState, onTap: () -> Unit) {
    val colors = LocalFocusFlowColors.current
    val shape = RoundedCornerShape(FocusFlowRadius.md)

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 160.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = uploadState !is UploadState.Validating,
                onClick = onTap
            ),
        blurBehind = false
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (uploadState) {
                is UploadState.Idle -> {
                    IconBadge(icon = Icons.Filled.UploadFile, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Tap to choose a file", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "PDF, PNG, or JPEG",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is UploadState.Selected -> {
                    IconBadge(icon = Icons.Filled.Description, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(uploadState.fileName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Ready to validate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is UploadState.Validating -> {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Validating document…", style = MaterialTheme.typography.titleMedium)
                }

                is UploadState.Valid -> {
                    IconBadge(icon = Icons.Filled.CheckCircle, tint = Success)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Document verified", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "You're all set to continue",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is UploadState.Invalid -> {
                    IconBadge(icon = Icons.Filled.ErrorOutline, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Tap to try another file", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun IconBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    val colors = LocalFocusFlowColors.current
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(colors.glassSurface)
            .border(width = 1.dp, color = colors.glassBorder, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun AdhdUploadScreenIdlePreview() {
    FocusFlowTheme {
        AdhdUploadScreen(
            uploadState = UploadState.Idle,
            onFileSelected = {},
            onContinue = {},
            onDismissError = {},
            onRetry = {}
        )
    }
}
