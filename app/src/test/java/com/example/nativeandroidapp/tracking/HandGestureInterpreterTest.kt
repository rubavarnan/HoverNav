package com.example.nativeandroidapp.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandGestureInterpreterTest {
  @Test
  fun pinchingThumbAndIndexTipsStartsOneTap() {
    val interpreter = HandGestureInterpreter()
    val landmarks = handWithBentFinger(null).toMutableList().apply {
      this[4] = HandLandmark(0.59f, 0.5f)
      this[8] = HandLandmark(0.60f, 0.5f)
    }

    val first = interpreter.process(landmarks, 1000, 2000)
    val second = interpreter.process(landmarks, 1000, 2000)

    assertTrue(first.any { it is GestureEvent.PinchStarted })
    assertEquals(0, second.count { it is GestureEvent.PinchStarted })
  }

  @Test
  fun eachBentFingerMapsToItsScrollDirection() {
    val cases = listOf(
      8 to SwipeDirection.UP,
      12 to SwipeDirection.DOWN,
      16 to SwipeDirection.RIGHT,
      20 to SwipeDirection.LEFT,
    )

    cases.forEach { (tipIndex, expectedDirection) ->
      val interpreter = HandGestureInterpreter()
      val events = interpreter.process(handWithBentFinger(tipIndex), 1000, 1000)
      val scroll = events.filterIsInstance<GestureEvent.Scroll>().single()

      assertEquals(expectedDirection, scroll.direction)
    }
  }

  @Test
  fun bentFingerIsRecognizedWhenPointingSideways() {
    val landmarks = handWithBentFinger(8).toMutableList()
    val mcp = landmarks[5]
    val pip = landmarks[6]
    val tip = landmarks[8]
    landmarks[6] = rotateAround(mcp, pip)
    landmarks[8] = rotateAround(mcp, tip)

    val events = HandGestureInterpreter().process(landmarks, 1000, 1000)

    assertEquals(
      SwipeDirection.UP,
      events.filterIsInstance<GestureEvent.Scroll>().single().direction,
    )
  }

  @Test
  fun scrollEmitsOnceUntilFingerStraightens() {
    val interpreter = HandGestureInterpreter()

    val first = interpreter.process(handWithBentFinger(12), 1000, 1000)
    val stillBent = interpreter.process(handWithBentFinger(12), 1000, 1000)
    val straight = interpreter.process(handWithBentFinger(null), 1000, 1000)
    val bentAgain = interpreter.process(handWithBentFinger(12), 1000, 1000)

    assertEquals(1, first.count { it is GestureEvent.Scroll })
    assertEquals(0, stillBent.count { it is GestureEvent.Scroll })
    assertEquals(1, straight.count { it is GestureEvent.PointerMoved })
    assertEquals(1, bentAgain.count { it is GestureEvent.Scroll })
  }

  @Test
  fun pinchTakesPriorityOverBentIndexFinger() {
    val interpreter = HandGestureInterpreter()
    val landmarks = handWithBentFinger(8).toMutableList().apply {
      this[4] = HandLandmark(0.59f, 0.55f)
      this[8] = HandLandmark(0.60f, 0.55f)
    }

    val events = interpreter.process(landmarks, 1000, 1000)

    assertEquals(1, events.count { it is GestureEvent.PinchStarted })
    assertEquals(0, events.count { it is GestureEvent.Scroll })
  }

  @Test
  fun multipleCurledFingersDoNotGuessAScrollDirection() {
    val landmarks = handWithBentFinger(8).toMutableList().apply {
      this[12] = bendTip(this[9], this[10])
    }

    val events = HandGestureInterpreter().process(landmarks, 1000, 1000)

    assertEquals(0, events.count { it is GestureEvent.Scroll })
  }

  @Test
  fun lostHandResetsPinchState() {
    val interpreter = HandGestureInterpreter()
    interpreter.process(handWithBentFinger(null), 1000, 1000)

    assertEquals(listOf(GestureEvent.HandLost), interpreter.process(emptyList(), 1000, 1000))
    val pinched = handWithBentFinger(null).toMutableList().apply {
      this[4] = HandLandmark(0.59f, 0.5f)
      this[8] = HandLandmark(0.60f, 0.5f)
    }
    assertTrue(interpreter.process(pinched, 1000, 1000)
      .any { it is GestureEvent.PinchStarted })
  }

  private fun handWithBentFinger(bentTipIndex: Int?): List<HandLandmark> =
    List(21) { HandLandmark(0.5f, 0.5f) }.toMutableList().apply {
      this[0] = HandLandmark(0.5f, 0.9f)
      this[4] = HandLandmark(0.15f, 0.6f)

      this[5] = HandLandmark(0.4f, 0.6f)
      this[6] = HandLandmark(0.39f, 0.45f)
      this[8] = HandLandmark(0.38f, 0.3f)

      this[9] = HandLandmark(0.48f, 0.58f)
      this[10] = HandLandmark(0.48f, 0.42f)
      this[12] = HandLandmark(0.48f, 0.26f)

      this[13] = HandLandmark(0.56f, 0.6f)
      this[14] = HandLandmark(0.58f, 0.45f)
      this[16] = HandLandmark(0.6f, 0.3f)

      this[17] = HandLandmark(0.64f, 0.62f)
      this[18] = HandLandmark(0.68f, 0.48f)
      this[20] = HandLandmark(0.72f, 0.34f)
      if (bentTipIndex != null) {
        val pipIndex = bentTipIndex - 2
        val mcpIndex = bentTipIndex - 3
        this[bentTipIndex] = bendTip(this[mcpIndex], this[pipIndex])
      }
    }

  private fun bendTip(mcp: HandLandmark, pip: HandLandmark) =
    HandLandmark(pip.x - (pip.x - mcp.x), pip.y - (pip.y - mcp.y), pip.z - (pip.z - mcp.z))

  private fun rotateAround(origin: HandLandmark, point: HandLandmark) =
    HandLandmark(
      origin.x - (point.y - origin.y),
      origin.y + (point.x - origin.x),
      point.z,
    )
}
