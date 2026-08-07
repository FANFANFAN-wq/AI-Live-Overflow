package com.ailiveoverflow.pet

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.sqrt

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: WebView? = null
    private var params: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "OverlayService"
        private const val CHANNEL_ID = "pet_overlay_channel"
        private const val NOTIFICATION_ID = 1001
        private const val PET_SIZE_DP = 120
        private const val PET_HEIGHT_DP = 155
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("在你身边 🦀"))
        setupOverlay()
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        params = WindowManager.LayoutParams(
            dpToPx(PET_SIZE_DP),
            dpToPx(PET_HEIGHT_DP),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 400
        }

        overlayView = WebView(this).apply {
            setBackgroundColor(0x00000000)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
                setSupportZoom(false)
                builtInZoomControls = false
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // 检查 petEngine 是否就绪
                    view?.evaluateJavascript(
                        "(function(){return typeof window.petEngine !== 'undefined' && typeof window.petEngine.onTap === 'function'})()"
                    ) { result ->
                        if (result == "true") {
                            petEngineInitialized = true
                            Log.d(TAG, "petEngine 就绪 ✅")
                        } else {
                            Log.w(TAG, "petEngine 未就绪，800ms后重试")
                            handler.postDelayed({
                                view?.evaluateJavascript(
                                    "(function(){return typeof window.petEngine !== 'undefined' && typeof window.petEngine.onTap === 'function'})()"
                                ) { retry ->
                                    if (retry == "true") {
                                        petEngineInitialized = true
                                        Log.d(TAG, "petEngine 重试就绪 ✅")
                                    } else {
                                        Log.e(TAG, "petEngine 仍未就绪，请检查 pet.html")
                                    }
                                }
                            }, 800)
                        }
                    }
                }
            }
            loadUrl("file:///android_asset/pet.html")
            setOnTouchListener(createTouchListener())
        }

        windowManager?.addView(overlayView, params)
    }

    // === GESTURE HANDLING ===

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastTapTime = 0L
    private var touchStartTime = 0L
    private var hasMoved = false
    private var petEngineInitialized = false

    private fun createTouchListener(): View.OnTouchListener {
        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    hasMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        hasMoved = true
                        params?.x = initialX + dx
                        params?.y = initialY + dy
                        windowManager?.updateViewLayout(overlayView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - touchStartTime
                    val distance = sqrt(
                        ((event.rawX - initialTouchX) * (event.rawX - initialTouchX) +
                         (event.rawY - initialTouchY) * (event.rawY - initialTouchY)).toDouble()
                    )
                    // 精准手势：distance<20 + <200ms=单击 | <400ms间隔=双击 | >600ms+<30px=长按
                    if (distance < 20 && elapsed < 200) {
                        if (System.currentTimeMillis() - lastTapTime < 400) {
                            onDoubleTap()
                            lastTapTime = 0L
                        } else {
                            lastTapTime = System.currentTimeMillis()
                            onTap()
                        }
                    } else if (elapsed > 600 && distance < 30) {
                        onLongPress()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun onTap() {
        if (petEngineInitialized) {
            overlayView?.evaluateJavascript("petEngine.onTap()", null)
        } else {
            Log.w(TAG, "petEngine 未就绪，跳过 onTap")
        }
    }

    private fun onDoubleTap() {
        if (petEngineInitialized) {
            overlayView?.evaluateJavascript("petEngine.onDoubleTap()", null)
        } else {
            Log.w(TAG, "petEngine 未就绪，跳过 onDoubleTap")
        }
    }

    private fun onLongPress() {
        if (petEngineInitialized) {
            overlayView?.evaluateJavascript("petEngine.onLongPress()", null)
        } else {
            Log.w(TAG, "petEngine 未就绪，跳过 onLongPress")
        }
    }

    // === NOTIFICATION ===

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🦀 秦妄")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "秦妄桌宠",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                description = "秦妄悬浮窗运行中"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    // === UTILS ===

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        overlayView?.let {
            windowManager?.removeView(it)
            it.destroy()
        }
        overlayView = null
        super.onDestroy()
    }
}
