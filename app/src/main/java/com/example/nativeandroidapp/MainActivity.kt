package com.example.nativeandroidapp

import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.example.nativeandroidapp.service.TrackingService
import com.example.nativeandroidapp.theme.SpatialTouchTheme
import com.example.nativeandroidapp.ui.main.TrackingSetupScreen

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      SpatialTouchTheme {
        val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        TrackingSetupScreen(
          onRequestCamera = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
          onRequestNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
          },
          onOpenOverlaySettings = {
            startActivity(
              Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
              ),
            )
          },
          onOpenAccessibilitySettings = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
          onStartTracking = {
            try {
              ContextCompat.startForegroundService(this, Intent(this, TrackingService::class.java))
            } catch (error: Exception) {
              Log.e("SpatialTouch", "Could not start the tracking service", error)
              com.example.nativeandroidapp.tracking.TrackingStatusStore.update(
                com.example.nativeandroidapp.tracking.TrackingStatus.Failed(
                  error.localizedMessage ?: "Android prevented the tracking service from starting.",
                ),
              )
            }
          },
          onStopTracking = { stopService(Intent(this, TrackingService::class.java)) },
        )
      }
    }
  }
}
