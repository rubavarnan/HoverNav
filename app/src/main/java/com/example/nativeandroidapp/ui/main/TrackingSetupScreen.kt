package com.example.nativeandroidapp.ui.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.nativeandroidapp.service.SpatialTouchAccessibilityService
import com.example.nativeandroidapp.service.TrackingService
import com.example.nativeandroidapp.tracking.TrackingStatus
import com.example.nativeandroidapp.tracking.TrackingStatusStore
import com.example.nativeandroidapp.tracking.SwipeDirection

@Composable
fun TrackingSetupScreen(
  onRequestCamera: () -> Unit,
  onRequestNotifications: () -> Unit,
  onOpenOverlaySettings: () -> Unit,
  onOpenAccessibilitySettings: () -> Unit,
  onStartTracking: () -> Unit,
  onStopTracking: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val trackingStatus by TrackingStatusStore.status.collectAsStateWithLifecycle()
  var gestureExamplesExpanded by rememberSaveable { mutableStateOf(false) }
  var permissionRefresh by remember { mutableIntStateOf(0) }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionRefresh++ }

  val cameraGranted = remember(permissionRefresh) {
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
  }
  val overlayGranted = remember(permissionRefresh) { Settings.canDrawOverlays(context) }
  val accessibilityEnabled = remember(permissionRefresh) { isAccessibilityEnabled(context) }
  val notificationsGranted = remember(permissionRefresh) {
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
  }
  val allAccessGranted = cameraGranted && overlayGranted && accessibilityEnabled && notificationsGranted

  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text("HoverNav", style = MaterialTheme.typography.headlineMedium)
    Text(
      "Control your screen with front-camera hand gestures. Camera frames and landmark detection stay on this device.",
      style = MaterialTheme.typography.bodyLarge,
    )
    GestureExamples(
      expanded = gestureExamplesExpanded,
      onToggle = { gestureExamplesExpanded = !gestureExamplesExpanded },
    )
    if (!allAccessGranted) {
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          PermissionRow("Camera permission", cameraGranted, onRequestCamera)
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PermissionRow("Display over other apps", overlayGranted, onOpenOverlaySettings)
            if (!overlayGranted) {
              Text(
                "On some devices, if this switch is blocked, open Settings > Apps > App management > HoverNav > tap the three-dot menu > Allow restricted settings. Then return here and enable Display over other apps.",
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
          PermissionRow("Gesture accessibility service", accessibilityEnabled, onOpenAccessibilitySettings)
          if (!accessibilityEnabled) {
            Text(
              "In Accessibility settings, open Downloaded apps > Spatial Touch gesture control, then enable the service.",
              style = MaterialTheme.typography.bodySmall,
            )
          }
          PermissionRow("Tracking notification", notificationsGranted, onRequestNotifications)
        }
      }
    }
    when (val status = trackingStatus) {
      TrackingStatus.Idle -> Text("Tracking is stopped.", style = MaterialTheme.typography.bodyMedium)
      TrackingStatus.Starting -> Text("Starting hand tracking and opening the front camera…", style = MaterialTheme.typography.bodyMedium)
      TrackingStatus.Running -> Text("Hand tracking is running. The camera notification should be visible.", style = MaterialTheme.typography.bodyMedium)
      is TrackingStatus.Failed -> Text(
        "Could not start tracking: ${status.message}",
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
      )
    }
    Spacer(modifier = Modifier.height(4.dp))
    Button(
      onClick = onStartTracking,
      enabled = allAccessGranted,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("Start hand tracking")
    }
    OutlinedButton(onClick = onStopTracking, modifier = Modifier.fillMaxWidth()) {
      Text("Stop hand tracking")
    }
    Text(
      "Tracking uses a foreground camera notification. Keep this app open when starting; Android may block starting camera access from the background.",
      style = MaterialTheme.typography.bodySmall,
    )
  }
}

@Composable
private fun GestureExamples(expanded: Boolean, onToggle: () -> Unit) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
      TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Gesture examples", style = MaterialTheme.typography.titleMedium)
          Text(if (expanded) "Hide ▲" else "Show ▼", style = MaterialTheme.typography.labelLarge)
        }
      }
      if (expanded) {
        Column(
          modifier = Modifier.padding(bottom = 12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          GestureInstruction(
            description = "Pinch thumb + index finger to tap",
            finger = GestureFinger.PINCH,
            direction = null,
          )
          GestureInstruction("Bend index finger to scroll up", GestureFinger.INDEX, SwipeDirection.UP)
          GestureInstruction("Bend middle finger to scroll down", GestureFinger.MIDDLE, SwipeDirection.DOWN)
          GestureInstruction("Bend ring finger to scroll right", GestureFinger.RING, SwipeDirection.RIGHT)
          GestureInstruction("Bend little finger to scroll left", GestureFinger.LITTLE, SwipeDirection.LEFT)
        }
      }
    }
  }
}

@Composable
private fun GestureInstruction(description: String, finger: GestureFinger, direction: SwipeDirection?) {
  val handColor = MaterialTheme.colorScheme.primary
  val accentColor = MaterialTheme.colorScheme.tertiary
  val directionColor = MaterialTheme.colorScheme.secondary
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    shape = MaterialTheme.shapes.medium,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      androidx.compose.foundation.Canvas(modifier = Modifier.size(64.dp)) {
        val scale = minOf(size.width, size.height) / ILLUSTRATION_SIZE
        withTransform({ scale(scale, scale, Offset.Zero) }) {
          drawGestureHand(finger, direction, handColor, accentColor, directionColor)
        }
      }
      Text(description, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

private enum class GestureFinger {
  PINCH,
  INDEX,
  MIDDLE,
  RING,
  LITTLE,
}

private fun DrawScope.drawGestureHand(
  finger: GestureFinger,
  direction: SwipeDirection?,
  handColor: Color,
  accentColor: Color,
  directionColor: Color,
) {
  val palmPaint = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f, cap = StrokeCap.Round)
  drawRoundRect(handColor, topLeft = Offset(33f, 53f), size = androidx.compose.ui.geometry.Size(36f, 31f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f), style = palmPaint)
  drawLine(handColor, Offset(44f, 79f), Offset(48f, 94f), 5f, StrokeCap.Round)
  drawLine(handColor, Offset(58f, 79f), Offset(54f, 94f), 5f, StrokeCap.Round)

  val fingerLines = listOf(
    GestureFinger.INDEX to listOf(Offset(38f, 56f), Offset(34f, 39f), Offset(33f, 20f)),
    GestureFinger.MIDDLE to listOf(Offset(47f, 54f), Offset(46f, 35f), Offset(46f, 14f)),
    GestureFinger.RING to listOf(Offset(56f, 54f), Offset(58f, 37f), Offset(60f, 18f)),
    GestureFinger.LITTLE to listOf(Offset(65f, 57f), Offset(72f, 43f), Offset(75f, 29f)),
  )

  fingerLines.forEach { (whichFinger, points) ->
    val selected = whichFinger == finger && finger != GestureFinger.PINCH
    val pinchingIndex = finger == GestureFinger.PINCH && whichFinger == GestureFinger.INDEX
    val color = if (selected) accentColor else handColor
    val mid = if (pinchingIndex) Offset(35f, 46f) else points[1]
    val tip = if (pinchingIndex) {
      Offset(40f, 47f)
    } else if (selected) {
      Offset(points[2].x + 7f, points[1].y + 6f)
    } else {
      points[2]
    }
    drawLine(if (pinchingIndex) accentColor else color, points[0], mid, 6f, StrokeCap.Round)
    drawLine(if (pinchingIndex) accentColor else color, mid, tip, 6f, StrokeCap.Round)
    drawCircle(color, 3f, tip)
  }

  val thumbBase = Offset(34f, 67f)
  val thumbMid = if (finger == GestureFinger.PINCH) Offset(28f, 56f) else Offset(22f, 60f)
  val thumbTip = if (finger == GestureFinger.PINCH) Offset(40f, 47f) else Offset(14f, 52f)
  drawLine(handColor, thumbBase, thumbMid, 6f, StrokeCap.Round)
  drawLine(if (finger == GestureFinger.PINCH) accentColor else handColor, thumbMid, thumbTip, 6f, StrokeCap.Round)
  drawCircle(if (finger == GestureFinger.PINCH) accentColor else handColor, 3f, thumbTip)

  if (finger == GestureFinger.PINCH) {
    drawLine(accentColor, Offset(40f, 47f), Offset(42f, 48f), 3f, StrokeCap.Round)
    drawCircle(directionColor, 3f, Offset(41f, 47f))
  } else if (direction != null) {
    drawDirectionArrow(direction, directionColor)
  }
}

private fun DrawScope.drawDirectionArrow(direction: SwipeDirection, color: Color) {
  val (start, end, wingOne, wingTwo) = when (direction) {
    SwipeDirection.UP -> listOf(Offset(88f, 44f), Offset(88f, 18f), Offset(82f, 26f), Offset(94f, 26f))
    SwipeDirection.DOWN -> listOf(Offset(88f, 18f), Offset(88f, 44f), Offset(82f, 36f), Offset(94f, 36f))
    SwipeDirection.LEFT -> listOf(Offset(94f, 31f), Offset(72f, 31f), Offset(80f, 25f), Offset(80f, 37f))
    SwipeDirection.RIGHT -> listOf(Offset(72f, 31f), Offset(94f, 31f), Offset(86f, 25f), Offset(86f, 37f))
  }
  drawLine(color, start, end, 4f, StrokeCap.Round)
  drawLine(color, end, wingOne, 4f, StrokeCap.Round)
  drawLine(color, end, wingTwo, 4f, StrokeCap.Round)
}

private const val ILLUSTRATION_SIZE = 100f

@Composable
private fun PermissionRow(label: String, granted: Boolean, onRequest: () -> Unit) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = if (granted) MaterialTheme.colorScheme.primaryContainer
      else MaterialTheme.colorScheme.surfaceVariant,
    contentColor = if (granted) MaterialTheme.colorScheme.onPrimaryContainer
      else MaterialTheme.colorScheme.onSurfaceVariant,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(enabled = !granted, onClick = onRequest)
        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(
          if (granted) "Enabled" else "Tap to enable",
          style = MaterialTheme.typography.bodySmall,
          color = if (granted) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Checkbox(
        checked = granted,
        onCheckedChange = { checked ->
          if (checked && !granted) onRequest()
        },
        colors = CheckboxDefaults.colors(
          checkedColor = MaterialTheme.colorScheme.primary,
          uncheckedColor = MaterialTheme.colorScheme.outline,
          checkmarkColor = MaterialTheme.colorScheme.onPrimary,
        ),
      )
    }
  }
}

private fun isAccessibilityEnabled(context: Context): Boolean {
  val component = android.content.ComponentName(context, SpatialTouchAccessibilityService::class.java)
  val enabledServices = Settings.Secure.getString(
    context.contentResolver,
    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
  ) ?: return false
  return enabledServices.split(':').any { enabled ->
    android.content.ComponentName.unflattenFromString(enabled) == component
  }
}
