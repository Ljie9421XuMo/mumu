package com.mumu.pet

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dp = resources.displayMetrics.density
        val pad = (24 * dp).toInt()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }

        val title = TextView(this).apply {
            text = "MuMu"
            textSize = 34f
        }

        val hint = TextView(this).apply {
            text = "把 MuMu 叫到桌面上。\n需要先允许「显示在其他应用上层」。"
            textSize = 14f
            setPadding(0, (12 * dp).toInt(), 0, (24 * dp).toInt())
        }

        val startButton = Button(this).apply {
            text = "叫 MuMu 出来"
            setOnClickListener { ensureOverlayPermissionThenStart() }
        }

        val stopButton = Button(this).apply {
            text = "让 MuMu 回家"
            setOnClickListener {
                stopService(Intent(this@MainActivity, PetOverlayService::class.java))
            }
        }

        layout.addView(title)
        layout.addView(hint)
        layout.addView(startButton)
        layout.addView(stopButton)
        setContentView(layout)
    }

    private fun ensureOverlayPermissionThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        startForegroundService(Intent(this, PetOverlayService::class.java))
    }
}
