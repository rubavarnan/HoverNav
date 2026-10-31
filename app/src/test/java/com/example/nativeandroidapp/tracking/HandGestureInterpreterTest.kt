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
    List(21) { HandLandmark(0.5f, 0.35f) }.toMutableList().apply {
      this[4] = HandLandmark(0.1f, 0.5f)
      this[8] = HandLandmark(0.6f, 0.35f)
      listOf(6, 10, 14, 18).forEach { this[it] = HandLandmark(0.5f, 0.45f) }
      if (bentTipIndex != null) {
        this[bentTipIndex] = HandLandmark(0.5f, 0.55f)
      }
    }
}
