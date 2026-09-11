package com.mumu.pet

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var statusText: TextView

    private val density get() = resources.displayMetrics.density
    private fun px(v: Int) = (v * density).toInt()
    private fun color(hex: Long) = hex.toInt()

    private val bg = color(0xFFF5F2FB)
    private val cardBg = color(0xFFFFFFFF)
    private val primary = color(0xFF7B5BD6)
    private val primaryDark = color(0xFF4E3690)
    private val ink = color(0xFF1F1B2E)
    private val ink2 = color(0xFF6E6885)
    private val hintColor = color(0xFF9A93A8)
    private val lineColor = color(0xFFE4DEF3)
    private val white = color(0xFFFFFFFF)
    private val softPink = color(0xFFFFF1EA)
    private val softPinkLine = color(0xFFFFD8C0)
    private val softPinkText = color(0xFFBC5F35)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(20), px(20), px(28))
            setBackgroundColor(bg)
        }

        root.addView(TextView(this).apply {
            text = "MuMu"
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(primaryDark)
            layoutParams = wrap(top = 16)
        })

        root.addView(TextView(this).apply {
            text = "一只黏人的小狐狸，会一直陪着你"
            textSize = 13f
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(ink2)
            layoutParams = wrap(top = 6)
        })

        // ---------- 账号 ----------
        val accountCard = card()
        accountCard.addView(tag("账号"))

        emailInput = input("邮箱，例如 you@example.com", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        passwordInput = input(
            "密码，至少 6 位",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        accountCard.addView(emailInput)
        accountCard.addView(passwordInput)

        accountCard.addView(button("登录", primary, white) { submitAuth(register = false) })
        accountCard.addView(button("注册新账号", cardBg, primary, primary) { submitAuth(register = true) })

        statusText = TextView(this).apply {
            textSize = 13f
            setTextColor(ink2)
            setLineSpacing(0f, 1.35f)
            layoutParams = wrap(top = 12)
        }
        accountCard.addView(statusText)
        root.addView(accountCard)

        // ---------- 桌宠 ----------
        val petCard = card()
        petCard.addView(tag("桌宠"))
        petCard.addView(TextView(this).apply {
            text = "MuMu 需要「悬浮窗」权限才能站在屏幕上。叫出来之后，可以用手指把它拖到任意位置，它也会自己溜达。"
            textSize = 13f
            setTextColor(ink2)
            setLineSpacing(0f, 1.4f)
            layoutParams = wrap(top = 8)
        })

        petCard.addView(button("叫 MuMu 出来", primary, white) { ensureOverlayPermissionThenStart() })
        petCard.addView(button("让 MuMu 回家", cardBg, ink, lineColor) {
            stopService(Intent(this@MainActivity, PetOverlayService::class.java))
            toast("MuMu 回家了")
        })
        petCard.addView(button("退出登录", softPink, softPinkText, softPinkLine) {
            PetStore.signOut(this@MainActivity)
            refreshStatus()
            toast("已退出，MuMu 只在本地活着")
        })
        root.addView(petCard)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(
                root,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        setContentView(scroll)

        PetStore.email(this)?.let { emailInput.setText(it) }
        refreshStatus()
    }

    // ---------- 小样式工具 ----------

    private fun rounded(fill: Int, radiusDp: Int, stroke: Int? = null, strokeDp: Int = 1): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = radiusDp * density
        d.setColor(fill)
        if (stroke != null) d.setStroke((strokeDp * density).toInt().coerceAtLeast(1), stroke)
        return d
    }

    private fun wrap(top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = px(top) }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(px(16), px(16), px(16), px(18))
        background = rounded(cardBg, 18)
        layoutParams = wrap(top = 16)
    }

    private fun tag(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(ink)
    }

    private fun input(hintText: String, type: Int): EditText = EditText(this).apply {
        hint = hintText
        inputType = type
        setSingleLine()
        textSize = 15f
        setTextColor(ink)
        setHintTextColor(hintColor)
        setPadding(px(14), 0, px(14), 0)
        background = rounded(cardBg, 12, lineColor)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            px(48)
        ).apply { topMargin = px(10) }
    }

    private fun button(
        text: String,
        fill: Int,
        fg: Int,
        stroke: Int? = null,
        onClick: () -> Unit
    ): TextView = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 15f
        setTextColor(fg)
        typeface = Typeface.DEFAULT_BOLD
        background = rounded(fill, 13, stroke)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            px(48)
        ).apply { topMargin = px(12) }
    }

    // ---------- 逻辑 ----------

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
