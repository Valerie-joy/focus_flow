package com.focusflow.ui.screens.onboarding

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusflow.R
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.GlassCard
import com.focusflow.ui.components.MessageBanner
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.components.SecondaryButton
import com.focusflow.ui.components.StatusTone
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.ui.theme.LocalFocusFlowColors
import com.focusflow.ui.theme.Sizing
import com.focusflow.ui.theme.Spacing

/**
 * One honest claim about what the camera does, paired with the icon that
 * carries it. Each is a statement about *this* implementation — see
 * GazeAnalyzer, which runs MediaPipe and the iTracker model locally and never
 * writes a frame anywhere — not boilerplate privacy copy.
 */
private data class PrivacyPoint(val icon: ImageVector, val title: String, val detail: String)

private val privacyPoints = listOf(
    PrivacyPoint(
        icon = Icons.Filled.PhoneAndroid,
        title = "Everything runs on this phone",
        detail = "Frames are analysed on-device and discarded immediately. No image ever leaves your phone."
    ),
    PrivacyPoint(
        icon = Icons.Filled.VideocamOff,
        title = "Nothing is recorded",
        detail = "No video or photo is saved, uploaded, or kept after a frame is measured."
    ),
    PrivacyPoint(
        icon = Icons.Filled.Lock,
        title = "Only during an assessment",
        detail = "The camera turns on when an assessment starts and off the moment it ends."
    )
)

/**
 * Explains why the camera is needed, then requests the permission.
 *
 * The important fix here is the **dead end**. Previously `alreadyGranted` was
 * read once during composition, so if the user was permanently denied, tapped
 * "Open Settings", granted the permission there and came back, the screen
 * still showed the Settings prompt with no way forward — the only escape was
 * killing the app. Permission state is now re-read on every `ON_RESUME`, so
 * returning from Settings lands on the granted state immediately.
 *
 * The second fix is an **exit**: a denial used to leave the user on this
 * screen with a single button they had already refused. There is now always a
 * way onward, and the consequence of declining is stated rather than implied.
 */
@Composable
fun CameraPermissionScreen(
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun checkGranted() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(checkGranted()) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var deniedOnce by rememberSaveable { mutableStateOf(false) }

    // Re-read on resume: the user can change this permission outside the app
    // at any time, in Settings or from the notification shade, and the screen
    // has to reflect that when they come back.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = checkGranted()
                if (granted) permanentlyDenied = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        granted = isGranted
        if (isGranted) {
            onPermissionGranted()
        } else {
            deniedOnce = true
            // shouldShowRequestPermissionRationale is false both before the
            // first request and after a "don't ask again" denial — checking it
            // immediately after this specific denial is the standard way to
            // tell those apart.
            val canStillAskAgain = (context as? Activity)?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } ?: true
            permanentlyDenied = !canStillAskAgain
            onPermissionDenied()
        }
    }

    BlurBackground(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = Sizing.maxContentWidth)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.gutter),
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(Spacing.xxl))
                Text(
                    text = "Camera access",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = "FocusFlow measures attention by following where your eyes " +
                        "go while you watch a short clip. That needs the front camera.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                GlassCard(modifier = Modifier.fillMaxWidth(), padding = Spacing.md) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        privacyPoints.forEach { point -> PrivacyRow(point) }
                    }
                }

                if (permanentlyDenied) {
                    Spacer(modifier = Modifier.height(Spacing.md))
                    MessageBanner(
                        message = "Camera access is turned off for FocusFlow. Turn it on in " +
                            "Settings → Permissions → Camera, then come back — this screen " +
                            "will update on its own.",
                        tone = StatusTone.WARNING
                    )
                } else if (deniedOnce) {
                    Spacer(modifier = Modifier.height(Spacing.md))
                    MessageBanner(
                        message = "Without the camera, FocusFlow can't measure attention. " +
                            "You can still browse the app, but assessments won't run.",
                        tone = StatusTone.WARNING
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                when {
                    granted -> PrimaryButton(
                        text = stringResource(R.string.action_continue),
                        onClick = onPermissionGranted
                    )

                    permanentlyDenied -> {
                        PrimaryButton(
                            text = "Open Settings",
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                )
                            }
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        // Always an exit. Being stuck on a permission screen you
                        // have already declined is worse than declining.
                        SecondaryButton(
                            text = stringResource(R.string.action_not_now),
                            onClick = onPermissionDenied
                        )
                    }

                    else -> {
                        PrimaryButton(
                            text = "Allow camera access",
                            onClick = { launcher.launch(Manifest.permission.CAMERA) }
                        )
                        if (deniedOnce) {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            SecondaryButton(
                                text = stringResource(R.string.action_not_now),
                                onClick = onPermissionDenied
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.xxl))
            }
        }
    }
}

@Composable
private fun PrivacyRow(point: PrivacyPoint) {
    val colors = LocalFocusFlowColors.current
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = point.icon,
            contentDescription = null, // title and detail carry the meaning
            tint = colors.success,
            modifier = Modifier.size(Sizing.iconMd)
        )
        Column(modifier = Modifier.padding(start = Spacing.sm)) {
            Text(
                text = point.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = point.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
