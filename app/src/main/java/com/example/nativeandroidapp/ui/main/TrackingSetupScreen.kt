package com.example.nativeandroidapp.ui.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.nativeandroidapp.tracking.TrackingPreferences

@Composable
fun TrackingSetupScreen(
  onRequestCamera: () -> Unit,
  onRequestNotifications: () -> Unit,
  onOpenCameraSettings: () -> Unit,
  onOpenNotificationSettings: () -> Unit,
  onOpenOverlaySettings: () -> Unit,
  onOpenAccessibilitySettings: () -> Unit,
  onStartTracking: () -> Unit,
  onStopTracking: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val trackingStatus by TrackingStatusStore.status.collectAsStateWithLifecycle()
  val currentTrackingStatus = trackingStatus
  var gestureExamplesExpanded by rememberSaveable { mutableStateOf(false) }
  val preferences = remember(context) {
    context.getSharedPreferences(TrackingPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
  }
  var showHandGesture by remember {
    mutableStateOf(preferences.getBoolean(TrackingPreferences.SHOW_HAND_GESTURE_KEY, false))
  }
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
  val requiredAccessGranted = cameraGranted && accessibilityEnabled && notificationsGranted
  val allAccessGranted = requiredAccessGranted && (!showHandGesture || overlayGranted)
  val permissionsEnabled = listOf(cameraGranted, accessibilityEnabled, notificationsGranted, overlayGranted).count { it }
  var accessExpanded by rememberSaveable { mutableStateOf(permissionsEnabled < 4) }
  LaunchedEffect(permissionsEnabled) {
    if (permissionsEnabled == 4) accessExpanded = false
  }

  val trackingActionLabel = when (currentTrackingStatus) {
    TrackingStatus.Idle -> "Start hand tracking"
    TrackingStatus.Starting -> "Starting hand tracking…"
    TrackingStatus.Running -> "Stop hand tracking"
    is TrackingStatus.Failed -> "Retry hand tracking"
  }
  val trackingActionMessage = when (currentTrackingStatus) {
    TrackingStatus.Idle -> if (allAccessGranted) null else "Enable required permissions to start."
    TrackingStatus.Starting -> "Opening the front camera…"
    TrackingStatus.Running -> "Hand tracking is running."
    is TrackingStatus.Failed -> currentTrackingStatus.message
  }
  val trackingActionEnabled = when (currentTrackingStatus) {
    TrackingStatus.Idle, is TrackingStatus.Failed -> allAccessGranted
    TrackingStatus.Starting -> false
    TrackingStatus.Running -> true
  }
  val trackingActionShape = RoundedCornerShape(24.dp)
  val trackingActionColors = when {
    !trackingActionEnabled -> listOf(Color(0xFF667085), Color(0xFF475467))
    currentTrackingStatus == TrackingStatus.Running -> listOf(Color(0xFFFF7043), Color(0xFFD81B60))
    else -> listOf(Color(0xFF00A896), Color(0xFF2962FF))
  }
  val trackingActionTextColor = if (trackingActionEnabled) Color.White else Color(0xFFE4E7EC)

  Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Box(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        shape = MaterialTheme.shapes.extraLarge,
        contentColor = MaterialTheme.colorScheme.onPrimary,
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text("HoverNav - Gesture Control", style = MaterialTheme.typography.headlineMedium)
          Text(
            "Navigate your screen with simple hand movements. Camera frames and landmark detection stay on this device.",
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          TextButton(onClick = { accessExpanded = !accessExpanded }, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Required permissions", style = MaterialTheme.typography.titleMedium)
                Text(if (accessExpanded) "Hide ▲" else "Show ▼", style = MaterialTheme.typography.labelLarge)
              }
              Text(
                "$permissionsEnabled of 4 permissions enabled",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          if (accessExpanded) {
            PermissionRow(
              "Camera permission",
              cameraGranted,
              if (cameraGranted) onOpenCameraSettings else onRequestCamera,
            )
            PermissionRow("Gesture accessibility service", accessibilityEnabled, onOpenAccessibilitySettings)
            PermissionRow(
              "Tracking notification",
              notificationsGranted,
              if (notificationsGranted) onOpenNotificationSettings else onRequestNotifications,
            )
            PermissionRow("Allow over other apps", overlayGranted, onOpenOverlaySettings)
          }
        }
      }
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Gesture overlay", style = MaterialTheme.typography.titleMedium)
          Text(
            "Choose whether the detected hand stays visible on top of other apps.",
            style = MaterialTheme.typography.bodySmall,
          )
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .toggleable(
                value = showHandGesture,
                role = Role.Switch,
                onValueChange = { checked ->
                  showHandGesture = checked
                  preferences.edit()
                    .putBoolean(TrackingPreferences.SHOW_HAND_GESTURE_KEY, checked)
                    .apply()
                },
              )
              .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Show hand gesture", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = showHandGesture, onCheckedChange = null)
          }
        }
      }
      GestureExamples(
        expanded = gestureExamplesExpanded,
        onToggle = { gestureExamplesExpanded = !gestureExamplesExpanded },
      )
      Text(
        "Tracking uses a foreground camera notification. Keep this app open when starting; Android may block starting camera access from the background.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      }
      Column(
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(20.dp)
          .shadow(12.dp, trackingActionShape)
          .clip(trackingActionShape)
          .background(Brush.linearGradient(trackingActionColors))
          .border(1.dp, Color.White.copy(alpha = 0.32f), trackingActionShape)
          .clickable(enabled = trackingActionEnabled) {
            when (currentTrackingStatus) {
              TrackingStatus.Running -> onStopTracking()
              TrackingStatus.Starting -> Unit
              TrackingStatus.Idle, is TrackingStatus.Failed -> onStartTracking()
            }
          }
          .widthIn(min = 190.dp, max = 280.dp)
          .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Text(trackingActionLabel, style = MaterialTheme.typography.titleSmall, color = trackingActionTextColor)
        trackingActionMessage?.let {
          Text(
            it,
            style = MaterialTheme.typography.labelSmall,
            color = trackingActionTextColor.copy(alpha = 0.88f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun GestureExamples(expanded: Boolean, onToggle: () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
  ) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
      TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Gesture examples", style = MaterialTheme.typography.titleMedium)
            Text(if (expanded) "Hide ▲" else "Show ▼", style = MaterialTheme.typography.labelLarge)
          }
          Text("Quick guide to taps and directional scrolling", style = MaterialTheme.typography.bodySmall)
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
        .clickable(onClick = onRequest)
        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(
          if (granted) "Enabled · tap to manage" else "Tap to enable",
          style = MaterialTheme.typography.bodySmall,
          color = if (granted) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(
        checked = granted,
        onCheckedChange = null,
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
