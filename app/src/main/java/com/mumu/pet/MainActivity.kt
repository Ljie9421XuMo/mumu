package com.mumu.pet

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24), px(24), px(24), px(24))
        }

        val title = TextView(this).apply {
            text = "MuMu"
            textSize = 30f
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val emailLabel = TextView(this).apply {
            text = "邮箱"
            textSize = 14f
            setPadding(0, px(6), 0, 0)
        }

        emailInput = EditText(this).apply {
            hint = "you@example.com"
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine()
            textSize = 15f
        }

        val pwdLabel = TextView(this).apply {
            text = "密码（至少 6 位）"
            textSize = 14f
            setPadding(0, px(6), 0, 0)
        }

        passwordInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
            textSize = 15f
        }

        val loginButton = Button(this).apply {
            text = "登录"
            setOnClickListener { submitAuth(register = false) }
        }

        val registerButton = Button(this).apply {
            text = "注册新账号"
            setOnClickListener { submitAuth(register = true) }
        }

        statusText = TextView(this).apply {
            textSize = 13f
            setPadding(0, px(8), 0, px(12))
            setLineSpacing(0f, 1.3f)
        }

        val startButton = Button(this).apply {
            text = "叫 MuMu 出来"
            setOnClickListener { ensureOverlayPermissionThenStart() }
        }

        val stopButton = Button(this).apply {
            text = "让 MuMu 回家"
            setOnClickListener {
                stopService(Intent(this@MainActivity, PetOverlayService::class.java))
                toast("MuMu 回家了")
            }
        }

        val signOutButton = Button(this).apply {
            text = "退出登录"
            setOnClickListener {
                PetStore.signOut(this@MainActivity)
                refreshStatus()
                toast("已退出，MuMu 只在本地活着")
            }
        }

        layout.addView(title)
        layout.addView(emailLabel)
        layout.addView(emailInput)
        layout.addView(pwdLabel)
        layout.addView(passwordInput)
        layout.addView(loginButton)
        layout.addView(registerButton)
        layout.addView(statusText)
        layout.addView(startButton)
        layout.addView(stopButton)
        layout.addView(signOutButton)
        setContentView(layout)

        PetStore.email(this)?.let { emailInput.setText(it) }
        refreshStatus()
    }

    private fun refreshStatus() {
        val email = PetStore.email(this)
        statusText.text = if (email != null) {
            "已登录：$email\nMuMu 的状态会同步到云端。"
        } else {
            "未登录。MuMu 仍然会出来，但状态只存在本机。"
        }
    }

    private fun submitAuth(register: Boolean) {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()

        if (email.isEmpty() || password.isEmpty()) {
            toast("邮箱和密码都要填")
            return
        }

        statusText.text = if (register) "注册中…" else "登录中…"
        emailInput.isEnabled = false
        passwordInput.isEnabled = false

        Thread {
            try {
                if (register) {
                    PetStore.signUp(this, email, password)
                } else {
                    PetStore.signIn(this, email, password)
                }
                runOnUiThread {
                    toast(if (register) "注册好了" else "登录好了")
                    refreshStatus()
                    emailInput.isEnabled = true
                    passwordInput.isEnabled = true
                }
            } catch (e: Exception) {
                val msg = e.message ?: e.toString()
                runOnUiThread {
                    statusText.text = "失败：$msg"
                    emailInput.isEnabled = true
                    passwordInput.isEnabled = true
                }
            }
        }.start()
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
        toast("MuMu 出来了")
    }

    private fun toast(text: String) {
        runOnUiThread {
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        }
    }
}
