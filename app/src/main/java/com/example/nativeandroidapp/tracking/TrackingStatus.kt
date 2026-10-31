package com.example.nativeandroidapp.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface TrackingStatus {
  data object Idle : TrackingStatus

  data object Starting : TrackingStatus

  data object Running : TrackingStatus

  data class Failed(val message: String) : TrackingStatus
}

object TrackingStatusStore {
  private val mutableStatus = MutableStateFlow<TrackingStatus>(TrackingStatus.Idle)
  val status: StateFlow<TrackingStatus> = mutableStatus.asStateFlow()

  fun update(status: TrackingStatus) {
    mutableStatus.value = status
  }
}
