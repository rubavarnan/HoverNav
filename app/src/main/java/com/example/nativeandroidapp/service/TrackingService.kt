package com.example.nativeandroidapp.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.example.nativeandroidapp.R
import com.example.nativeandroidapp.tracking.GestureBus
import com.example.nativeandroidapp.tracking.GestureEvent
import com.example.nativeandroidapp.tracking.HandGestureInterpreter
import com.example.nativeandroidapp.tracking.HandLandmark
import com.example.nativeandroidapp.tracking.ScreenPoint
import com.example.nativeandroidapp.tracking.SwipeDirection
import com.example.nativeandroidapp.tracking.TrackingStatus
import com.example.nativeandroidapp.tracking.TrackingStatusStore
import com.example.nativeandroidapp.tracking.TrackingPreferences
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max

class TrackingService : LifecycleService() {
  private lateinit var cameraExecutor: ExecutorService
  private lateinit var handLandmarker: HandLandmarker
  private val mainHandler = Handler(Looper.getMainLooper())
  private var cursorOverlay: CursorOverlay? = null
  private var gesturePaletteOverlay: GesturePaletteOverlay? = null
  private var cameraProvider: ProcessCameraProvider? = null
  private var lastTimestamp = 0L
  private val interpreter = HandGestureInterpreter()

  override fun onCreate() {
    super.onCreate()
    cameraExecutor = Executors.newSingleThreadExecutor()
    GestureBus.addListener(overlayListener)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    super.onStartCommand(intent, flags, startId)
    if (intent?.action == ACTION_STOP) {
      TrackingStatusStore.update(TrackingStatus.Idle)
      stopSelf()
      return START_NOT_STICKY
    }
    if (isRunning) return START_NOT_STICKY

    if (!hasCameraPermission()) {
      Log.e(TAG, "Cannot start tracking: camera permission is required")
      TrackingStatusStore.update(TrackingStatus.Failed("Camera permission is required."))
      stopSelf()
      return START_NOT_STICKY
    }

    try {
      TrackingStatusStore.update(TrackingStatus.Starting)
      startTrackingForeground()
      syncOverlays()
      createHandLandmarker()
      bindCamera()
      isRunning = true
    } catch (error: Exception) {
      Log.e(TAG, "Unable to start hand tracking", error)
      TrackingStatusStore.update(TrackingStatus.Failed(error.localizedMessage ?: "Could not start camera tracking."))
      stopSelf()
    }
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    isRunning = false
    GestureBus.removeListener(overlayListener)
    cursorOverlay?.remove()
    cursorOverlay = null
    gesturePaletteOverlay?.remove()
    gesturePaletteOverlay = null
    cameraProvider?.unbindAll()
    cameraProvider = null
    if (::cameraExecutor.isInitialized) {
      if (::handLandmarker.isInitialized) cameraExecutor.execute { handLandmarker.close() }
      cameraExecutor.shutdown()
    }
    if (TrackingStatusStore.status.value !is TrackingStatus.Failed) {
      TrackingStatusStore.update(TrackingStatus.Idle)
    }
    GestureBus.publish(GestureEvent.HandLost)
    super.onDestroy()
  }

  override fun onBind(intent: Intent): IBinder? {
    super.onBind(intent)
    return null
  }

  private fun hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

  private fun startTrackingForeground() {
    createNotificationChannel()
    val notification = buildNotification()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        NOTIFICATION_CHANNEL_ID,
        getString(R.string.tracking_notification_channel),
        NotificationManager.IMPORTANCE_LOW,
      )
      getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
  }

  private fun buildNotification(): Notification {
    val stopIntent = Intent(this, TrackingService::class.java).setAction(ACTION_STOP)
    val stopPendingIntent = PendingIntent.getService(
      this,
      STOP_REQUEST_CODE,
      stopIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_menu_camera)
      .setContentTitle(getString(R.string.tracking_notification_title))
      .setContentText(getString(R.string.tracking_notification_text))
      .setOngoing(true)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .addAction(android.R.drawable.ic_media_pause, getString(R.string.tracking_notification_stop), stopPendingIntent)
      .build()
  }

  private fun createHandLandmarker() {
    val baseOptions = BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build()
    val options = HandLandmarker.HandLandmarkerOptions.builder()
      .setBaseOptions(baseOptions)
      .setNumHands(1)
      .setMinHandDetectionConfidence(MIN_HAND_CONFIDENCE)
      .setMinTrackingConfidence(MIN_HAND_CONFIDENCE)
      .setMinHandPresenceConfidence(MIN_HAND_CONFIDENCE)
      .setRunningMode(RunningMode.VIDEO)
      .build()
    handLandmarker = HandLandmarker.createFromOptions(this, options)
  }

  private fun bindCamera() {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
    cameraProviderFuture.addListener(
      {
        try {
          val cameraProvider = cameraProviderFuture.get()
          this@TrackingService.cameraProvider = cameraProvider
          val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
              ResolutionSelector.Builder()
                .setResolutionStrategy(
                  ResolutionStrategy(
                    Size(TARGET_WIDTH, TARGET_HEIGHT),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                  ),
                )
                .build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
          analysis.setAnalyzer(cameraExecutor, ::analyzeFrame)
          cameraProvider.unbindAll()
          cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
          TrackingStatusStore.update(TrackingStatus.Running)
        } catch (error: Exception) {
          Log.e(TAG, "Unable to bind the front camera", error)
          TrackingStatusStore.update(TrackingStatus.Failed(error.localizedMessage ?: "Could not open the front camera."))
          stopSelf()
        }
      },
      ContextCompat.getMainExecutor(this),
    )
  }

  private fun analyzeFrame(image: ImageProxy) {
    try {
      if (image.planes.isEmpty()) return
      val bitmap = image.toBitmap()
      try {
        val mpImage = BitmapImageBuilder(bitmap).build()
        try {
          val options = ImageProcessingOptions.builder()
            .setRotationDegrees(image.imageInfo.rotationDegrees)
            .build()
          val now = SystemClock.uptimeMillis()
          val timestamp = max(now, lastTimestamp + 1L)
          lastTimestamp = timestamp
          val result = handLandmarker.detectForVideo(mpImage, options, timestamp)
          val hand = result.landmarks().firstOrNull()
          if (hand == null) {
            interpreter.reset()
            GestureBus.publish(GestureEvent.HandLost)
          } else {
            val landmarks = hand.map { HandLandmark(it.x(), it.y(), it.z()) }
            screenSize().let { (width, height) ->
              interpreter.process(landmarks, width, height).forEach(GestureBus::publish)
            }
          }
        } finally {
          mpImage.close()
        }
      } finally {
        bitmap.recycle()
      }
    } catch (error: RuntimeException) {
      Log.e(TAG, "Hand-landmark frame processing failed", error)
    } finally {
      image.close()
    }
  }

  private fun screenSize(): Pair<Int, Int> {
    val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val bounds = windowManager.currentWindowMetrics.bounds
      bounds.width() to bounds.height()
    } else {
      @Suppress("DEPRECATION")
      val metrics = android.util.DisplayMetrics().also { windowManager.defaultDisplay.getRealMetrics(it) }
      metrics.widthPixels to metrics.heightPixels
    }
  }

  private fun syncOverlays() {
    val showHandGesture = getSharedPreferences(TrackingPreferences.PREFERENCES_NAME, MODE_PRIVATE)
      .getBoolean(TrackingPreferences.SHOW_HAND_GESTURE_KEY, false)
    if (showHandGesture && Settings.canDrawOverlays(this)) {
      if (cursorOverlay == null) cursorOverlay = CursorOverlay(this)
      if (gesturePaletteOverlay == null) gesturePaletteOverlay = GesturePaletteOverlay(this)
    } else {
      cursorOverlay?.remove()
      cursorOverlay = null
      gesturePaletteOverlay?.remove()
      gesturePaletteOverlay = null
    }
  }

  private val overlayListener: (GestureEvent) -> Unit = { event ->
    mainHandler.post {
      syncOverlays()
      when (event) {
        is GestureEvent.PointerMoved -> {
          cursorOverlay?.moveTo(event.point)
          gesturePaletteOverlay?.showHandDetected()
        }
        GestureEvent.HandLost -> {
          cursorOverlay?.hide()
          gesturePaletteOverlay?.showWaiting()
        }
        is GestureEvent.PinchStarted -> gesturePaletteOverlay?.showGesture(PaletteGesture.PINCH)
        is GestureEvent.Scroll -> gesturePaletteOverlay?.showGesture(
          when (event.direction) {
            SwipeDirection.LEFT -> PaletteGesture.SCROLL_LEFT
            SwipeDirection.RIGHT -> PaletteGesture.SCROLL_RIGHT
            SwipeDirection.UP -> PaletteGesture.SCROLL_UP
            SwipeDirection.DOWN -> PaletteGesture.SCROLL_DOWN
          },
        )
      }
    }
  }

  private companion object {
    const val TAG = "SpatialTouchCamera"
    const val ACTION_STOP = "com.example.nativeandroidapp.action.STOP_TRACKING"
    const val MODEL_ASSET = "hand_landmarker.task"
    const val NOTIFICATION_CHANNEL_ID = "spatial_touch_tracking"
    const val NOTIFICATION_ID = 841
    const val STOP_REQUEST_CODE = 1
    const val TARGET_WIDTH = 640
    const val TARGET_HEIGHT = 480
    const val MIN_HAND_CONFIDENCE = 0.5f

    @Volatile
    var isRunning: Boolean = false
      private set
  }
}

private class CursorOverlay(context: Context) {
  private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
  private val view = CursorOverlayView(context)
  private val params = WindowManager.LayoutParams(
    OVERLAY_SIZE,
    OVERLAY_SIZE,
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
      @Suppress("DEPRECATION")
      WindowManager.LayoutParams.TYPE_PHONE
    },
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
      WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    PixelFormat.TRANSLUCENT,
  ).apply {
    gravity = Gravity.TOP or Gravity.START
    alpha = OVERLAY_ALPHA
  }

  init {
    windowManager.addView(view, params)
    view.visibility = View.INVISIBLE
  }

  fun moveTo(point: ScreenPoint) {
    params.x = point.x - OVERLAY_SIZE / 2
    params.y = point.y - OVERLAY_SIZE / 2
    view.visibility = View.VISIBLE
    windowManager.updateViewLayout(view, params)
  }

  fun hide() {
    view.visibility = View.INVISIBLE
  }

  fun remove() {
    if (view.isAttachedToWindow) windowManager.removeView(view)
  }

  private companion object {
    const val OVERLAY_SIZE = 56
    const val OVERLAY_ALPHA = 0.75f
  }
}

private class CursorOverlayView(context: Context) : View(context) {
  private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.WHITE
    style = Paint.Style.STROKE
    strokeWidth = 4f
  }
  private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.rgb(0, 190, 255)
    style = Paint.Style.FILL
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val center = width / 2f
    canvas.drawCircle(center, height / 2f, 13f, ringPaint)
    canvas.drawCircle(center, height / 2f, 6f, dotPaint)
  }
}

private enum class PaletteGesture {
  WAITING,
  HAND,
  PINCH,
  SCROLL_LEFT,
  SCROLL_RIGHT,
  SCROLL_UP,
  SCROLL_DOWN,
}

private class GesturePaletteOverlay(context: Context) {
  private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
  private val density = context.resources.displayMetrics.density
  private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
  private var gestureShownAt = 0L
  private val view = GesturePaletteView(context)
  private var downRawX = 0f
  private var downRawY = 0f
  private var downX = 0
  private var downY = 0
  private val params = WindowManager.LayoutParams(
    dp(PALETTE_WIDTH_DP),
    dp(PALETTE_HEIGHT_DP),
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
      @Suppress("DEPRECATION")
      WindowManager.LayoutParams.TYPE_PHONE
    },
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    PixelFormat.TRANSLUCENT,
  ).apply {
    gravity = Gravity.TOP or Gravity.START
    val screenWidth = context.resources.displayMetrics.widthPixels
    val screenHeight = context.resources.displayMetrics.heightPixels
    x = preferences.getInt(KEY_X, (screenWidth - dp(PALETTE_WIDTH_DP) - dp(16)).coerceAtLeast(0))
      .coerceIn(0, (screenWidth - dp(PALETTE_WIDTH_DP)).coerceAtLeast(0))
    y = preferences.getInt(KEY_Y, dp(72))
      .coerceIn(0, (screenHeight - dp(PALETTE_HEIGHT_DP)).coerceAtLeast(0))
  }

  init {
    view.setOnTouchListener { _, event ->
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          downRawX = event.rawX
          downRawY = event.rawY
          downX = params.x
          downY = params.y
          true
        }
        MotionEvent.ACTION_MOVE -> {
          val screen = view.resources.displayMetrics
          params.x = (downX + event.rawX - downRawX).toInt().coerceIn(0, (screen.widthPixels - view.width).coerceAtLeast(0))
          params.y = (downY + event.rawY - downRawY).toInt().coerceIn(0, (screen.heightPixels - view.height).coerceAtLeast(0))
          windowManager.updateViewLayout(view, params)
          true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          preferences.edit().putInt(KEY_X, params.x).putInt(KEY_Y, params.y).apply()
          true
        }
        else -> true
      }
    }
    windowManager.addView(view, params)
  }

  fun showWaiting() {
    gestureShownAt = 0L
    view.setGesture(PaletteGesture.WAITING)
  }

  fun showHandDetected() {
    if (gestureShownAt == 0L || android.os.SystemClock.uptimeMillis() - gestureShownAt > GESTURE_LABEL_DURATION_MS) {
      view.setGesture(PaletteGesture.HAND)
      gestureShownAt = 0L
    }
  }

  fun showGesture(gesture: PaletteGesture) {
    view.setGesture(gesture)
    gestureShownAt = android.os.SystemClock.uptimeMillis()
  }

  fun remove() {
    if (view.isAttachedToWindow) windowManager.removeView(view)
  }

  private fun dp(value: Int): Int = (value * density).toInt()

  private companion object {
    const val GESTURE_LABEL_DURATION_MS = 1400L
    const val PALETTE_WIDTH_DP = 72
    const val PALETTE_HEIGHT_DP = 84
    const val PREFERENCES_NAME = "gesture_palette"
    const val KEY_X = "x"
    const val KEY_Y = "y"
  }
}

private class GesturePaletteView(context: Context) : View(context) {
  private var gesture = PaletteGesture.WAITING
  private val scaleDensity = resources.displayMetrics.density
  private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.argb(135, 20, 24, 33)
    style = Paint.Style.FILL
  }
  private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.argb(95, 255, 255, 255)
    style = Paint.Style.STROKE
    strokeWidth = 0.75f * scaleDensity
  }
  private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.rgb(99, 218, 255)
    style = Paint.Style.FILL
  }
  private val handStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.rgb(99, 218, 255)
    style = Paint.Style.STROKE
    strokeWidth = 6.5f
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
  }
  private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE
    style = Paint.Style.FILL
  }
  private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.rgb(255, 199, 91)
    style = Paint.Style.FILL
  }
  private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE
    style = Paint.Style.STROKE
    strokeWidth = 3.5f
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
  }
  fun setGesture(gesture: PaletteGesture) {
    if (this.gesture != gesture) {
      this.gesture = gesture
      invalidate()
    }
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val scale = minOf(width / DESIGN_WIDTH, height / DESIGN_HEIGHT)
    val left = (width - DESIGN_WIDTH * scale) / 2f
    val top = (height - DESIGN_HEIGHT * scale) / 2f
    val saveCount = canvas.save()
    canvas.translate(left, top)
    canvas.scale(scale, scale)
    canvas.drawRoundRect(1f, 1f, DESIGN_WIDTH - 1f, DESIGN_HEIGHT - 1f, 14f, 14f, backgroundPaint)
    canvas.drawRoundRect(1f, 1f, DESIGN_WIDTH - 1f, DESIGN_HEIGHT - 1f, 14f, 14f, borderPaint)
    drawHand(canvas)
    when (gesture) {
      PaletteGesture.SCROLL_LEFT -> drawDirectionArrow(canvas, SwipeDirection.LEFT)
      PaletteGesture.SCROLL_RIGHT -> drawDirectionArrow(canvas, SwipeDirection.RIGHT)
      PaletteGesture.SCROLL_UP -> drawDirectionArrow(canvas, SwipeDirection.UP)
      PaletteGesture.SCROLL_DOWN -> drawDirectionArrow(canvas, SwipeDirection.DOWN)
      else -> Unit
    }
    canvas.restoreToCount(saveCount)
  }

  private fun drawHand(canvas: Canvas) {
    val isWaiting = gesture == PaletteGesture.WAITING
    val alpha = if (isWaiting) 125 else 255
    handPaint.alpha = alpha
    handStrokePaint.alpha = alpha

    canvas.drawRoundRect(34f, 51f, 64f, 78f, 13f, 13f, handPaint)
    canvas.drawRoundRect(42f, 72f, 57f, 91f, 7f, 7f, handPaint)

    drawFinger(canvas, 38f, 56f, if (gesture == PaletteGesture.PINCH) 47f else 31f, if (gesture == PaletteGesture.PINCH) 48f else 27f)
    drawFinger(canvas, 46f, 53f, 47f, 19f)
    drawFinger(canvas, 54f, 53f, 58f, 23f)
    drawFinger(canvas, 61f, 56f, 68f, 34f)

    val thumbPath = Path().apply {
      moveTo(35f, 64f)
      if (gesture == PaletteGesture.PINCH) {
        lineTo(27f, 58f)
        lineTo(43f, 48f)
      } else {
        lineTo(26f, 62f)
        lineTo(18f, 53f)
      }
    }
    canvas.drawPath(thumbPath, handStrokePaint)
    val thumbTip = if (gesture == PaletteGesture.PINCH) 43f to 48f else 18f to 53f
    canvas.drawCircle(thumbTip.first, thumbTip.second, 4.2f, handPaint)

    if (gesture == PaletteGesture.PINCH) {
      canvas.drawCircle(45f, 48f, 3f, accentPaint)
      canvas.drawCircle(43f, 48f, 2f, highlightPaint)
    } else if (!isWaiting) {
      canvas.drawCircle(38f, 27f, 2.4f, highlightPaint)
      canvas.drawCircle(47f, 19f, 2.4f, highlightPaint)
      canvas.drawCircle(58f, 23f, 2.4f, highlightPaint)
      canvas.drawCircle(68f, 34f, 2.4f, highlightPaint)
    }
  }

  private fun drawFinger(canvas: Canvas, startX: Float, startY: Float, endX: Float, endY: Float) {
    canvas.drawLine(startX, startY, endX, endY, handStrokePaint)
    canvas.drawCircle(endX, endY, 4f, handPaint)
  }

  private fun drawDirectionArrow(canvas: Canvas, direction: SwipeDirection) {
    val isVertical = direction == SwipeDirection.UP || direction == SwipeDirection.DOWN
    val fromX = when (direction) {
      SwipeDirection.LEFT -> 70f
      SwipeDirection.RIGHT -> 18f
      SwipeDirection.UP, SwipeDirection.DOWN -> 74f
    }
    val fromY = when (direction) {
      SwipeDirection.UP -> 88f
      SwipeDirection.DOWN -> 18f
      SwipeDirection.LEFT, SwipeDirection.RIGHT -> 94f
    }
    val toX = when (direction) {
      SwipeDirection.LEFT -> 18f
      SwipeDirection.RIGHT -> 70f
      SwipeDirection.UP, SwipeDirection.DOWN -> fromX
    }
    val toY = when (direction) {
      SwipeDirection.UP -> 18f
      SwipeDirection.DOWN -> 88f
      SwipeDirection.LEFT, SwipeDirection.RIGHT -> fromY
    }
    canvas.drawLine(fromX, fromY, toX, toY, arrowPaint)
    if (isVertical) {
      val headY = if (direction == SwipeDirection.UP) toY else toY - 1f
      val offset = if (direction == SwipeDirection.UP) 7f else -7f
      canvas.drawLine(toX, toY, toX - 6f, headY + offset, arrowPaint)
      canvas.drawLine(toX, toY, toX + 6f, headY + offset, arrowPaint)
    } else {
      val headX = if (direction == SwipeDirection.LEFT) toX + 1f else toX - 1f
      val offset = if (direction == SwipeDirection.LEFT) 7f else -7f
      canvas.drawLine(toX, toY, headX + offset, toY - 6f, arrowPaint)
      canvas.drawLine(toX, toY, headX + offset, toY + 6f, arrowPaint)
    }
  }

  private companion object {
    const val DESIGN_WIDTH = 88f
    const val DESIGN_HEIGHT = 104f
  }
}
