package com.focusflow.ui.screens.onboarding

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.GlassCard
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.ui.theme.Success

private val privacyPoints = listOf(
    "Eye tracking is only used during assessments.",
    "No video recordings are permanently stored.",
    "Only gaze/attention metrics are analyzed, on-device."
)

/**
 * Shown before requesting CAMERA permission. Spells out the privacy scope
 * plainly per the spec, then requests permission via the standard
 * ActivityResultContracts API (no extra library needed for a single
 * permission — Accompanist Permissions is only worth pulling in once
 * multiple runtime permissions need coordinating).
 */
@Composable
fun CameraPermissionScreen(
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit
) {
    val context = LocalContext.current
    var permanentlyDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onPermissionGranted()
        } else {
            // shouldShowRequestPermissionRationale is false both before the
            // first request and after a "don't ask again" denial — checking
            // it immediately after this specific denial is the standard way
            // to tell those apart (the system only offers a rationale again
            // if the user didn't permanently deny).
            val canStillAskAgain = (context as? Activity)?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } ?: true
            permanentlyDenied = !canStillAskAgain
            onPermissionDenied()
        }
    }

    val alreadyGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    BlurBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Camera access",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (permanentlyDenied) {
                    "Camera access was denied. FocusFlow can't run assessments without it — enable it in Settings to continue."
                } else {
                    "FocusFlow uses your front camera to see where your attention goes during assessments."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    privacyPoints.forEachIndexed { index, point ->
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Success,
                                modifier = Modifier.height(20.dp)
                            )
                            Text(
                                text = point,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 10.dp)
                            )
                        }
                        if (index != privacyPoints.lastIndex) {
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            if (alreadyGranted) {
                PrimaryButton(text = "Continue", onClick = onPermissionGranted)
            } else if (permanentlyDenied) {
                PrimaryButton(
                    text = "Open Settings",
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            } else {
                PrimaryButton(
                    text = "Allow camera access",
                    onClick = { launcher.launch(Manifest.permission.CAMERA) },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CameraPermissionScreenPreview() {
    FocusFlowTheme {
        CameraPermissionScreen(onPermissionGranted = {}, onPermissionDenied = {})
    }
}
