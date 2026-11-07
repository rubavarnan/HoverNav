package com.example.nativeandroidapp.tracking

import kotlin.math.hypot

data class HandLandmark(val x: Float, val y: Float, val z: Float = 0f)

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

    val bentFinger = activeBentFinger
      ?.takeIf { bendCosine(landmarks, it) < BEND_RELEASE_COSINE }
      ?: Finger.entries
        .map { finger -> finger to bendCosine(landmarks, finger) }
        .filter { (_, cosine) -> cosine < BEND_START_COSINE }
        .sortedBy { (_, cosine) -> cosine }
        .let { candidates ->
          val strongest = candidates.firstOrNull()
          val nextStrongest = candidates.getOrNull(1)
          if (strongest != null &&
            (nextStrongest == null || nextStrongest.second - strongest.second >= BEND_DOMINANCE_MARGIN)
          ) strongest.first else null
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

  private fun bendCosine(landmarks: List<HandLandmark>, finger: Finger): Float {
    val mcp = landmarks[finger.mcp]
    val pip = landmarks[finger.pip]
    val tip = landmarks[finger.tip]
    val proximal = pip - mcp
    val distal = tip - pip
    val proximalLength = vectorLength(proximal)
    val distalLength = vectorLength(distal)
    if (proximalLength < MIN_FINGER_SEGMENT_LENGTH || distalLength < MIN_FINGER_SEGMENT_LENGTH) return 1f
    return dot(proximal, distal) / (proximalLength * distalLength)
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
    hypot(hypot(first.x - second.x, first.y - second.y), first.z - second.z)

  private data class Vector3(val x: Float, val y: Float, val z: Float)

  private operator fun HandLandmark.minus(other: HandLandmark) =
    Vector3(x - other.x, y - other.y, z - other.z)

  private fun vectorLength(vector: Vector3): Float =
    hypot(hypot(vector.x, vector.y), vector.z)

  private fun dot(first: Vector3, second: Vector3): Float =
    first.x * second.x + first.y * second.y + first.z * second.z

  private enum class Finger(val mcp: Int, val pip: Int, val tip: Int, val direction: SwipeDirection) {
    INDEX(5, 6, INDEX_FINGER_TIP, SwipeDirection.UP),
    MIDDLE(9, 10, 12, SwipeDirection.DOWN),
    RING(13, 14, 16, SwipeDirection.RIGHT),
    LITTLE(17, 18, 20, SwipeDirection.LEFT),
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
    const val BEND_START_COSINE = 0.65f
    const val BEND_RELEASE_COSINE = 0.8f
    const val BEND_DOMINANCE_MARGIN = 0.15f
    const val MIN_FINGER_SEGMENT_LENGTH = 0.005f
    const val POINTER_SMOOTHING = 0.4f
  }
}
