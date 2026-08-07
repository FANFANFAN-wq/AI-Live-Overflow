package com.ailiveoverflow.pet

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val OVERLAY_PERMISSION_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.status_text)
        val actionButton = findViewById<Button>(R.id.action_button)

        if (Settings.canDrawOverlays(this)) {
            statusText.text = "秦妄已经在你屏幕上了 🦀"
            actionButton.text = "再来一遍"
            startOverlayService()
        } else {
            statusText.text = "需要悬浮窗权限才能让我趴在你屏幕上"
            actionButton.text = "去授权"
        }

        actionButton.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                startOverlayService()
            } else {
                requestOverlayPermission()
            }
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
                findViewById<TextView>(R.id.status_text).text = "秦妄已经在你屏幕上了 🦀"
                findViewById<Button>(R.id.action_button).text = "再来一遍"
            }
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
