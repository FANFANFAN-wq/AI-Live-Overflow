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
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.sqrt
import org.json.JSONObject

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
        @Volatile var instance: OverlayService? = null
        @Volatile var currentAction: String = "idle"
        @Volatile var lastJsResult: String = "\u2014"
        @Volatile var isPetEngineReady: Boolean = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("\u5728\u4f60\u8eab\u8fb9 \uD83E\uDD80"))
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
                    checkPetEngine(view, 0)
                }
            }
            loadUrl("file:///android_asset/pet.html")
            setOnTouchListener(createTouchListener())
        }

        windowManager?.addView(overlayView, params)
    }

    private fun checkPetEngine(view: WebView?, attempt: Int) {
        view?.evaluateJavascript(
            "(function(){return typeof window.petEngine !== 'undefined' && typeof window.petEngine.onTap === 'function'})()"
        ) { result ->
            if (result == "true") {
                petEngineInitialized = true
                isPetEngineReady = true
                Log.d(TAG, "petEngine ready (attempt=$attempt)")
            } else if (attempt < 3) {
                Log.w(TAG, "petEngine not ready, retry ${attempt + 1}")
                handler.postDelayed({ checkPetEngine(view, attempt + 1) }, 800)
            } else {
                Log.e(TAG, "petEngine failed after $attempt retries")
            }
        }
    }

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
        if (petEngineInitialized) overlayView?.evaluateJavascript("petEngine.onTap()", null)
        else Log.w(TAG, "petEngine not ready, skip onTap")
    }

    private fun onDoubleTap() {
        if (petEngineInitialized) overlayView?.evaluateJavascript("petEngine.onDoubleTap()", null)
        else Log.w(TAG, "petEngine not ready, skip onDoubleTap")
    }

    private fun onLongPress() {
        if (petEngineInitialized) overlayView?.evaluateJavascript("petEngine.onLongPress()", null)
        else Log.w(TAG, "petEngine not ready, skip onLongPress")
    }

    fun callPetEngine(method: String): String {
        if (!petEngineInitialized) return "not_ready"
        overlayView?.evaluateJavascript("petEngine.${method}()") { r ->
            lastJsResult = "$method => $r"
            Log.i(TAG, "callPetEngine($method) => $r")
        }
        return "ok"
    }

    fun playPetAction(action: String): String {
        if (!petEngineInitialized) return "not_ready"
        currentAction = action
        val safe = JSONObject.quote(action)
        overlayView?.evaluateJavascript("petEngine.playAction($safe)") { r ->
            lastJsResult = "playAction($action) => $r"
            Log.i(TAG, "playPetAction($action) => $r")
        }
        return "ok"
    }

    fun updateSize(sizeDp: Int) {
        params?.let { p ->
            p.width = dpToPx(sizeDp)
            p.height = dpToPx((sizeDp * PET_HEIGHT_DP.toFloat() / PET_SIZE_DP.toFloat()).toInt())
            try { windowManager?.updateViewLayout(overlayView, p) }
            catch (e: Exception) { Log.e(TAG, "updateSize err", e) }
        }
    }

    fun updateAlpha(alpha: Float) {
        params?.let { p ->
            p.alpha = alpha.coerceIn(0.3f, 1.0f)
            try { windowManager?.updateViewLayout(overlayView, p) }
            catch (e: Exception) { Log.e(TAG, "updateAlpha err", e) }
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("\u79E6\u5984")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi)
            .setOngoing(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(
                    CHANNEL_ID, "\u79E6\u5984\u684C\u5BA0", NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false); description = "\u79E6\u5984\u60AC\u6D6E\u7A97\u8FD0\u884C\u4E2D" })
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        instance = null
        isPetEngineReady = false
        handler.removeCallbacksAndMessages(null)
        overlayView?.let { windowManager?.removeView(it); it.destroy() }
        overlayView = null
        super.onDestroy()
    }
}
