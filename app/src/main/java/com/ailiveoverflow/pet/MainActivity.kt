package com.ailiveoverflow.pet

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val OVERLAY_PERMISSION_REQUEST = 1001
        private const val TAG = "MainActivity"
    }

    private var isUpdatingSwitch = false
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var statusText: TextView
    private lateinit var actionButton: Button
    private lateinit var overlaySwitch: SwitchCompat
    private lateinit var petStatusLabel: TextView
    private lateinit var reloadButton: Button
    private lateinit var debugWebview: TextView
    private lateinit var debugAction: TextView
    private lateinit var debugJs: TextView

    private val actionMap = linkedMapOf(
        "idle" to R.id.btn_idle, "happy" to R.id.btn_happy, "love" to R.id.btn_love,
        "angry" to R.id.btn_angry, "shy" to R.id.btn_shy, "sleep" to R.id.btn_sleep,
        "wake" to R.id.btn_wake, "coding" to R.id.btn_coding, "coffee" to R.id.btn_coffee,
        "reading" to R.id.btn_reading, "eating" to R.id.btn_eating, "gaming" to R.id.btn_gaming,
        "photo" to R.id.btn_photo, "singing" to R.id.btn_singing, "guitar" to R.id.btn_guitar,
        "shower" to R.id.btn_shower, "watering" to R.id.btn_watering, "exercise" to R.id.btn_exercise
    )

    private val actionLabels = mapOf(
        "idle" to "待机中 💤", "happy" to "开心 😊",
        "love" to "爱心 🥰", "angry" to "生气 😠",
        "shy" to "害羞 ☺️", "sleep" to "睡觉 😴",
        "wake" to "醒来 🌅", "coding" to "写代码 💻",
        "coffee" to "喝咖啡 ☕", "reading" to "看书 📖",
        "eating" to "吃饭 🍽️", "gaming" to "打游戏 🎮",
        "photo" to "拍照 📸", "singing" to "唱歌 🎤",
        "guitar" to "弹吉他 🎸", "shower" to "洗澡 🚿",
        "watering" to "浇花 🌱", "exercise" to "锻炼 🏃"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        actionButton = findViewById(R.id.action_button)
        overlaySwitch = findViewById(R.id.overlay_switch)
        petStatusLabel = findViewById(R.id.pet_status_label)
        reloadButton = findViewById(R.id.reload_button)
        debugWebview = findViewById(R.id.debug_webview)
        debugAction = findViewById(R.id.debug_action)
        debugJs = findViewById(R.id.debug_js)
        val sizeSeekbar = findViewById<SeekBar>(R.id.size_seekbar)
        val alphaSeekbar = findViewById<SeekBar>(R.id.alpha_seekbar)

        val hasPermission = Settings.canDrawOverlays(this)
        val isRunning = if (hasPermission) isServiceRunning() else false

        if (!hasPermission) {
            statusText.text = "需要悬浮窗权限才能让我趴在你屏幕上"
            actionButton.visibility = Button.VISIBLE
            actionButton.text = "去授权"
            overlaySwitch.isEnabled = false
            overlaySwitch.isChecked = false
            petStatusLabel.text = "等待授权…"
        } else {
            statusText.text = if (isRunning) "秦妄在你屏幕上 🦀" else "秦妄藏起来了"
            overlaySwitch.isChecked = isRunning
            overlaySwitch.isEnabled = true
            actionButton.visibility = Button.GONE
            petStatusLabel.text = if (isRunning) "在线 ✨" else "等待唤醒…"
        }

        overlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSwitch) return@setOnCheckedChangeListener
            if (isChecked) {
                startOverlayService()
                statusText.text = "秦妄在你屏幕上 🦀"
                petStatusLabel.text = "在线 ✨"
            } else {
                stopOverlayService()
                statusText.text = "秦妄藏起来了"
                petStatusLabel.text = "已关闭"
            }
        }

        actionButton.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) requestOverlayPermission()
        }

        // 重载按钮
        reloadButton.setOnClickListener {
            val svc = OverlayService.instance
            if (svc != null) {
                svc.callPetEngine("reload")
                debugJs.text = "最近JS: reload \u2192 ok"
            } else {
                stopOverlayService()
                handler.postDelayed({ startOverlayService() }, 300)
                debugJs.text = "最近JS: reload \u2192 restart"
            }
            reloadButton.text = "已重载"
            handler.postDelayed({ reloadButton.text = "重载" }, 1500)
        }

        // 18个动作按钮
        actionMap.forEach { (action, btnId) ->
            findViewById<Button>(btnId).setOnClickListener {
                val result = OverlayService.instance?.playPetAction(action) ?: "no_service"
                debugAction.text = "当前动作: $action"
                debugJs.text = "最近JS: playAction($action) \u2192 $result"
                petStatusLabel.text = actionLabels[action] ?: action
                Log.i(TAG, "Action: $action => $result")
            }
        }

        // SeekBar - 尺寸
        sizeSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) OverlayService.instance?.updateSize(p)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // SeekBar - 透明度
        alphaSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) OverlayService.instance?.updateAlpha(p / 100f)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // 定时刷新调试状态
        refreshDebugLoop()
    }

    private fun refreshDebugLoop() {
        val svc = OverlayService.instance
        debugWebview.text = if (svc != null) {
            "WebView: 已连接 (${if (OverlayService.isPetEngineReady) "引擎就绪" else "加载中…"})"
        } else {
            "WebView: 等待加载…"
        }
        debugAction.text = "当前动作: ${OverlayService.currentAction}"
        debugJs.text = "最近JS: ${OverlayService.lastJsResult}"
        handler.postDelayed({ refreshDebugLoop() }, 2000)
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this)) {
            overlaySwitch.isEnabled = true
            actionButton.visibility = Button.GONE
            val isRunning = isServiceRunning()
            isUpdatingSwitch = true
            overlaySwitch.isChecked = isRunning
            isUpdatingSwitch = false
            statusText.text = if (isRunning) "秦妄在你屏幕上 🦀" else "秦妄藏起来了"
            petStatusLabel.text = if (isRunning) "在线 ✨" else "等待唤醒…"
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
    }

    private fun requestOverlayPermission() {
        startActivityForResult(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            OVERLAY_PERMISSION_REQUEST
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST && Settings.canDrawOverlays(this)) {
            startOverlayService()
            overlaySwitch.isEnabled = true
            isUpdatingSwitch = true
            overlaySwitch.isChecked = true
            isUpdatingSwitch = false
            actionButton.visibility = Button.GONE
            statusText.text = "秦妄在你屏幕上 🦀"
            petStatusLabel.text = "在线 ✨"
        }
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == OverlayService::class.java.name }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
        handler.postDelayed({ refreshDebugLoop() }, 1000)
    }

    private fun stopOverlayService() {
        stopService(Intent(this, OverlayService::class.java))
    }
}
