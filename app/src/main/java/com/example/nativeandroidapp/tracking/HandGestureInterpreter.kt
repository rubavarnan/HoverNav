package com.example.nativeandroidapp.tracking

import kotlin.math.abs
import kotlin.math.hypot

data class HandLandmark(val x: Float, val y: Float)

class HandGestureInterpreter {
  private var isPinching = false
  private var activeBentFinger: Finger? = null
  private var smoothedX: Float? = null
  private var smoothedY: Float? = null

  fun process(landmarks: List<HandLandmark>, width: Int, height: Int): List<GestureEvent> {
    require(width > 0 && height > 0)
    if (landmarks.size < LANDMARK_COUNT) {
      reset()
      return listOf(GestureEvent.HandLost)
    }

    val indexTip = landmarks[INDEX_FINGER_TIP]
    val pinchRatio = pinchRatio(landmarks)
    val pointer = smoothPointer(indexTip, width, height)
    val events = mutableListOf<GestureEvent>(GestureEvent.PointerMoved(pointer))

    if (pinchRatio < PINCH_START_RATIO) {
      if (!isPinching) events += GestureEvent.PinchStarted(pointer)
      isPinching = true
      activeBentFinger = null
      return events
    }
    if (isPinching && pinchRatio <= PINCH_END_RATIO) return events
    isPinching = false

    val bentFinger = Finger.entries.firstOrNull { finger ->
      landmarks[finger.tip].y - landmarks[finger.pip].y > FINGER_BEND_THRESHOLD
    }
    if (bentFinger != activeBentFinger) {
      activeBentFinger = bentFinger
      bentFinger?.let { events += GestureEvent.Scroll(it.direction) }
    }
    return events
  }

  fun reset() {
    isPinching = false
    activeBentFinger = null
    smoothedX = null
    smoothedY = null
  }

  private fun pinchRatio(landmarks: List<HandLandmark>): Float {
    val thumbTip = landmarks[THUMB_TIP]
    val indexTip = landmarks[INDEX_FINGER_TIP]
    val palmWidth = distance(landmarks[INDEX_MCP], landmarks[PINKY_MCP])
    val palmLength = distance(landmarks[WRIST], landmarks[MIDDLE_MCP])
    val palmScale = maxOf(palmWidth, palmLength, MIN_PALM_SCALE)
    return distance(thumbTip, indexTip) / palmScale
  }

  private fun smoothPointer(indexTip: HandLandmark, width: Int, height: Int): ScreenPoint {
    val nextX = 1f - indexTip.x
    val nextY = indexTip.y
    val x = smoothedX?.let { it + POINTER_SMOOTHING * (nextX - it) } ?: nextX
    val y = smoothedY?.let { it + POINTER_SMOOTHING * (nextY - it) } ?: nextY
    smoothedX = x
    smoothedY = y
    return ScreenPoint(
      (x * width).toInt().coerceIn(0, width - 1),
      (y * height).toInt().coerceIn(0, height - 1),
    )
  }

  private fun distance(first: HandLandmark, second: HandLandmark): Float =
    hypot(first.x - second.x, first.y - second.y)

  private enum class Finger(val tip: Int, val pip: Int, val direction: SwipeDirection) {
    INDEX(INDEX_FINGER_TIP, 6, SwipeDirection.UP),
    MIDDLE(12, 10, SwipeDirection.DOWN),
    RING(16, 14, SwipeDirection.RIGHT),
    LITTLE(20, 18, SwipeDirection.LEFT),
  }

  private companion object {
    const val LANDMARK_COUNT = 21
    const val THUMB_TIP = 4
    const val INDEX_FINGER_TIP = 8
    const val WRIST = 0
    const val INDEX_MCP = 5
    const val MIDDLE_MCP = 9
    const val PINKY_MCP = 17
    const val MIN_PALM_SCALE = 0.08f
    const val PINCH_START_RATIO = 0.3f
    const val PINCH_END_RATIO = 0.43f
    const val FINGER_BEND_THRESHOLD = 0.035f
    const val POINTER_SMOOTHING = 0.4f
  }
}
