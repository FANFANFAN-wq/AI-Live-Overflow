package com.ailiveoverflow.pet

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val OVERLAY_PERMISSION_REQUEST = 1001
    }

    private var isUpdatingSwitch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.status_text)
        val actionButton = findViewById<Button>(R.id.action_button)
        val overlaySwitch = findViewById<SwitchCompat>(R.id.overlay_switch)

        if (!Settings.canDrawOverlays(this)) {
            statusText.text = "需要悬浮窗权限才能让我趴在你屏幕上"
            actionButton.visibility = Button.VISIBLE
            actionButton.text = "去授权"
            overlaySwitch.isEnabled = false
            overlaySwitch.isChecked = false
        } else {
            val isRunning = isServiceRunning()
            statusText.text = if (isRunning) "秦妄在你屏幕上 🦀" else "秦妄藏起来了"
            overlaySwitch.isChecked = isRunning
            overlaySwitch.isEnabled = true
            actionButton.visibility = Button.GONE
        }

        overlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSwitch) return@setOnCheckedChangeListener
            if (isChecked) {
                startOverlayService()
                statusText.text = "秦妄在你屏幕上 🦀"
            } else {
                stopOverlayService()
                statusText.text = "秦妄藏起来了"
            }
        }

        actionButton.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                requestOverlayPermission()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this)) {
            val overlaySwitch = findViewById<SwitchCompat>(R.id.overlay_switch)
            val statusText = findViewById<TextView>(R.id.status_text)
            val actionButton = findViewById<Button>(R.id.action_button)

            overlaySwitch.isEnabled = true
            actionButton.visibility = Button.GONE

            val isRunning = isServiceRunning()
            isUpdatingSwitch = true
            overlaySwitch.isChecked = isRunning
            isUpdatingSwitch = false
            statusText.text = if (isRunning) "秦妄在你屏幕上 🦀" else "秦妄藏起来了"
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            if (Settings.canDrawOverlays(this)) {
                startOverlayService()
                val overlaySwitch = findViewById<SwitchCompat>(R.id.overlay_switch)
                val statusText = findViewById<TextView>(R.id.status_text)
                val actionButton = findViewById<Button>(R.id.action_button)

                overlaySwitch.isEnabled = true
                isUpdatingSwitch = true
                overlaySwitch.isChecked = true
                isUpdatingSwitch = false
                actionButton.visibility = Button.GONE
                statusText.text = "秦妄在你屏幕上 🦀"
            }
        }
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == OverlayService::class.java.name }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
    }
}
