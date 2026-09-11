package com.mumu.pet

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * 把桌宠画到屏幕上的前台服务。
 *
 * 职责：建一个透明的 WebView 悬浮窗加载 assets/web/index.html，
 * 并把它和 Supabase 里的 pet_state / pet_events 接起来。
 */
class PetOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var petView: WebView? = null

    private val io = Executors.newSingleThreadExecutor()
    private var state = PetState()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        addPetToWindow()
        syncFromCloud()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startAsForeground() {
        val channelId = "mumu_pet"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "MuMu",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.mumu_notif_title))
            .setContentText(getString(R.string.mumu_notif_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(1, notification)
        }
    }

    private fun addPetToWindow() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val webView = WebView(this).apply {
            setBackgroundColor(0x00000000)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(Bridge(), "MuMuNative")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    pushStateToWeb()
                }
            }
            loadUrl("file:///android_asset/web/index.html")
        }

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 32
            y = 320
        }

        wm.addView(webView, params)
        petView = webView
    }

    // ---------- 和网页拼接 ----------

    private inner class Bridge {
        @JavascriptInterface
        fun onPetTap() {
            handleTap()
        }
    }

    private fun pushStateToWeb() {
        val view = petView ?: return
        val json = JSONObject()
            .put("name", state.name)
            .put("mood", state.mood)
            .put("moodValue", state.moodValue)
            .put("energy", state.energy)
            .put("intimacy", state.intimacy)
            .put("synced", PetStore.isSignedIn(this))
            .toString()
        view.post {
            view.evaluateJavascript(
                "window.MuMu && window.MuMu.applyState($json);",
                null
            )
        }
    }

    private fun syncFromCloud() {
        if (!PetStore.isSignedIn(this)) return
        io.execute {
            runCatching { PetStore.loadState(this) }
                .onSuccess { remote ->
                    state = remote
                    pushStateToWeb()
                }
                .onFailure {
                    // 离线就算了，MuMu 先用本地默认值活着
                }
        }
    }

    /** 摸一下：心情涨、亲密涨、精力掉一点，然后上报事件。 */
    private fun handleTap() {
        val newMoodValue = (state.moodValue + 3).coerceAtMost(100)
        state = state.copy(
            moodValue = newMoodValue,
            intimacy = (state.intimacy + 1).coerceAtMost(100),
            energy = (state.energy - 1).coerceAtLeast(0),
            mood = when {
                newMoodValue >= 70 -> "happy"
                newMoodValue >= 30 -> "idle"
                else -> "sad"
            }
        )
        pushStateToWeb()

        if (!PetStore.isSignedIn(this)) return
        io.execute {
            runCatching {
                PetStore.appendEvent(
                    this,
                    "tap",
                    JSONObject()
                        .put("mood_value", state.moodValue)
                        .put("intimacy", state.intimacy)
                )
                PetStore.saveState(this, state)
            }
        }
    }

    override fun onDestroy() {
        petView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        petView = null
        io.shutdown()
        super.onDestroy()
    }
}
