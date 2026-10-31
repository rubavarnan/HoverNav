package com.example.nativeandroidapp.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.nativeandroidapp.tracking.GestureBus
import com.example.nativeandroidapp.tracking.GestureEvent
import com.example.nativeandroidapp.tracking.SwipeDirection

class SpatialTouchAccessibilityService : AccessibilityService() {
  private val mainHandler = Handler(Looper.getMainLooper())
  private var gestureInProgress = false
  private val gestureListener: (GestureEvent) -> Unit = { event ->
    when (event) {
      is GestureEvent.PinchStarted -> dispatchPath(event.point.x.toFloat(), event.point.y.toFloat(), event.point.x.toFloat(), event.point.y.toFloat(), TAP_DURATION_MS)
      is GestureEvent.Scroll -> dispatchScroll(event.direction)
      is GestureEvent.PointerMoved, GestureEvent.HandLost -> Unit
    }
  }

  override fun onServiceConnected() {
    super.onServiceConnected()
    GestureBus.addListener(gestureListener)
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

  override fun onInterrupt() {
    gestureInProgress = false
  }

  override fun onDestroy() {
    GestureBus.removeListener(gestureListener)
    super.onDestroy()
  }

  private fun dispatchPath(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long) {
    mainHandler.post {
      if (gestureInProgress) return@post

      val path = Path().apply {
        moveTo(startX, startY)
        lineTo(endX, endY)
      }
      val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
      val gesture = GestureDescription.Builder().addStroke(stroke).build()
      val accepted = dispatchGesture(
        gesture,
        object : GestureResultCallback() {
          override fun onCompleted(gestureDescription: GestureDescription?) {
            gestureInProgress = false
          }

          override fun onCancelled(gestureDescription: GestureDescription?) {
            gestureInProgress = false
          }
        },
        mainHandler,
      )
      gestureInProgress = accepted
      if (!accepted) Log.w(TAG, "Android rejected a gesture injection request")
    }
  }

  private fun dispatchScroll(direction: SwipeDirection) {
    val width = resources.displayMetrics.widthPixels.toFloat()
    val height = resources.displayMetrics.heightPixels.toFloat()
    val centerX = width / 2f
    val centerY = height / 2f
    val insetX = width * SCROLL_INSET_FRACTION
    val insetY = height * SCROLL_INSET_FRACTION
    val path = when (direction) {
      SwipeDirection.UP -> floatArrayOf(centerX, centerY + insetY, centerX, centerY - insetY)
      SwipeDirection.DOWN -> floatArrayOf(centerX, centerY - insetY, centerX, centerY + insetY)
      SwipeDirection.RIGHT -> floatArrayOf(centerX - insetX, centerY, centerX + insetX, centerY)
      SwipeDirection.LEFT -> floatArrayOf(centerX + insetX, centerY, centerX - insetX, centerY)
    }
    dispatchPath(path[0], path[1], path[2], path[3], SCROLL_DURATION_MS)
  }

  private companion object {
    const val TAG = "SpatialTouchA11y"
    const val TAP_DURATION_MS = 70L
    const val SCROLL_DURATION_MS = 280L
    const val SCROLL_INSET_FRACTION = 0.22f
  }
}
