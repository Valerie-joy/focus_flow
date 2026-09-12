package com.focusflow.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Glassy modal used for validation errors ("that document couldn't be
 * verified"), confirmations, and other blocking prompts. Wraps GlassCard
 * inside a Dialog so it gets proper scrim + focus trapping for free.
 *
 * @param icon optional leading element, e.g. a Lottie composable or Icon
 */
@Composable
fun PremiumDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    primaryActionLabel: String = "Got it",
    onPrimaryAction: () -> Unit = onDismiss,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(modifier = modifier.fillMaxWidth(), blurBehind = false) {
            Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                icon?.let {
                    it()
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))

                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PrimaryButton(
                            text = secondaryActionLabel,
                            onClick = onSecondaryAction,
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                        PrimaryButton(
                            text = primaryActionLabel,
                            onClick = onPrimaryAction,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    PrimaryButton(
                        text = primaryActionLabel,
                        onClick = onPrimaryAction,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
