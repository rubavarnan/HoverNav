package com.example.nativeandroidapp.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = Blue80,
    secondary = BlueGrey80,
    tertiary = Cyan80,
    background = Color(0xFF101725),
    surface = Color(0xFF182235),
    surfaceVariant = Color(0xFF27354C),
    primaryContainer = Color(0xFF223A66),
    secondaryContainer = Color(0xFF203D4C),
    tertiaryContainer = Color(0xFF34415A),
    errorContainer = Color(0xFF5C3035),
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Blue40,
    secondary = BlueGrey40,
    tertiary = Cyan40,
    background = Color(0xFFF4F7FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9EFF8),
    primaryContainer = Color(0xFFE2EBFF),
    secondaryContainer = Color(0xFFE0F3F8),
    tertiaryContainer = Color(0xFFE9EEF8),
    errorContainer = Color(0xFFFFE9E8),
  )

@Composable
fun SpatialTouchTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
