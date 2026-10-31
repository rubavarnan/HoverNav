package com.example.nativeandroidapp.tracking

import java.util.concurrent.CopyOnWriteArraySet

data class ScreenPoint(val x: Int, val y: Int)

enum class SwipeDirection {
  LEFT,
  RIGHT,
  UP,
  DOWN,
}

sealed interface GestureEvent {
  data class PointerMoved(val point: ScreenPoint) : GestureEvent

  data object HandLost : GestureEvent

  data class PinchStarted(val point: ScreenPoint) : GestureEvent

  data class Scroll(val direction: SwipeDirection) : GestureEvent
}

object GestureBus {
  private val listeners = CopyOnWriteArraySet<(GestureEvent) -> Unit>()

  fun addListener(listener: (GestureEvent) -> Unit) {
    listeners.add(listener)
  }

  fun removeListener(listener: (GestureEvent) -> Unit) {
    listeners.remove(listener)
  }

  fun publish(event: GestureEvent) {
    listeners.forEach { it(event) }
  }
}
