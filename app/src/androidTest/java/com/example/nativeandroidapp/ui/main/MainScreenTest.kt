package com.example.nativeandroidapp.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.nativeandroidapp.tracking.TrackingPreferences
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.activity
      .getSharedPreferences(TrackingPreferences.PREFERENCES_NAME, 0)
      .edit()
      .putBoolean(TrackingPreferences.SHOW_HAND_GESTURE_KEY, false)
      .commit()
    composeTestRule.setContent {
      TrackingSetupScreen(
        onRequestCamera = {},
        onRequestNotifications = {},
        onOpenOverlaySettings = {},
        onOpenAccessibilitySettings = {},
        onStartTracking = {},
        onStopTracking = {},
      )
    }
  }

  @Test
  fun trackingSetupExplainsRequiredPermissions() {
    composeTestRule.onNodeWithText("Spatial Touch").assertExists()
    composeTestRule.onNodeWithText("Camera permission", substring = true).assertExists()
    composeTestRule.onNodeWithText("Gesture accessibility service", substring = true).assertExists()
    composeTestRule.onNodeWithText("Tracking is stopped.").assertExists()
    composeTestRule.onNodeWithText("Start hand tracking").assertExists()
    composeTestRule.onNodeWithText("Gesture direction mode").assertDoesNotExist()
    composeTestRule.onNodeWithText("Gesture examples").assertExists()
    composeTestRule.onNodeWithText("Show hand gesture").assertExists()
    composeTestRule.onNodeWithText("Allow over other apps").assertDoesNotExist()
    composeTestRule.onNodeWithText("Pinch thumb + index finger: tap").assertDoesNotExist()
  }

  @Test
  fun enablingHandGestureShowsOverlayAccessOption() {
    composeTestRule.onNodeWithText("Show hand gesture").performClick()
    composeTestRule.onNodeWithText("Allow over other apps").assertExists()

    composeTestRule.onNodeWithText("Show hand gesture").performClick()
    composeTestRule.onNodeWithText("Allow over other apps").assertDoesNotExist()
  }

  @Test
  fun gestureExamplesCanBeExpandedAndCollapsed() {
    composeTestRule.onNodeWithText("Gesture examples").performClick()
    composeTestRule.onNodeWithText("Pinch thumb + index finger: tap").assertExists()

    composeTestRule.onNodeWithText("Gesture examples").performClick()
    composeTestRule.onNodeWithText("Pinch thumb + index finger: tap").assertDoesNotExist()
  }

  @Test
  fun expandingGestureExamplesShowsFingerGestureMappings() {
    composeTestRule.onNodeWithText("Gesture examples").performClick()

    composeTestRule.onNodeWithText("Bend index finger: scroll up").assertExists()
    composeTestRule.onNodeWithText("Thumbs up", substring = true).assertDoesNotExist()
    composeTestRule.onNodeWithText("Bend middle finger: scroll down").assertExists()
    composeTestRule.onNodeWithText("Bend ring finger: scroll right").assertExists()
    composeTestRule.onNodeWithText("Bend little finger: scroll left").assertExists()
  }
}
