package com.schoolproject.screenclose.ui

import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolproject.screenclose.ScreenCloseApp
import com.schoolproject.screenclose.capture.ScreenCaptureService
import com.schoolproject.screenclose.ui.theme.ScreenCloseTheme

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as ScreenCloseApp).container }
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory(container) }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    /** MediaProjection consent. The result Intent is single-use and handed straight to the service. */
    private val projectionConsent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                startForegroundService(ScreenCaptureService.startIntent(this, result.resultCode, data))
                container.metrics.increment("projection_consent_granted")
            } else {
                container.metrics.increment("projection_consent_denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScreenCloseTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                MainScreen(
                    state = state,
                    actions = MainActions(
                        onSignIn = { viewModel.signIn(this) },
                        onSignOut = { viewModel.signOut() },
                        onRequestNotifications = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        onOpenOverlaySettings = {
                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                        },
                        onOpenAccessibilitySettings = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        onStartCapture = {
                            val mpm = getSystemService(MediaProjectionManager::class.java)
                            projectionConsent.launch(mpm.createScreenCaptureIntent())
                        },
                        onStopCapture = { startService(ScreenCaptureService.stopIntent(this)) },
                    ),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        viewModel.refresh(PermissionStatus.read(this))
    }
}
