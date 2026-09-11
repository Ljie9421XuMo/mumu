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
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 把桌宠画到屏幕上的前台服务。
 *
 * 职责：建一个透明的 WebView 悬浮窗加载 assets/web/index.html，
 * 并把它和 Supabase 里的 pet_state / pet_events 接起来。
 * 情绪的计算全部委托给 MoodEngine，这里只管调它和存盘。
 */
class PetOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var petView: WebView? = null

    private val io = Executors.newSingleThreadScheduledExecutor()
    private var state = PetState()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        addPetToWindow()
        syncFromCloud()
        startSettleLoop()
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

    /** 拉云端状态，先把离开这段时间的账结算掉，再存回去。 */
    private fun syncFromCloud() {
        if (!PetStore.isSignedIn(this)) return
        io.execute {
            runCatching { PetStore.loadState(this) }
                .onSuccess { remote ->
                    val settled = MoodEngine.settle(remote, Instant.now())
                    state = settled
                    pushStateToWeb()
                    runCatching { PetStore.saveState(this, settled) }
                }
                .onFailure {
                    // 离线就算了，MuMu 先用本地默认值活着
                }
        }
    }

    /**
     * 周期结算。服务活着的时候每隔一段就把精力/心情按时间推一推，
     * 就算主人一直不碰它，它也会困、也会累。
     */
    private fun startSettleLoop() {
        io.scheduleAtFixedRate(
            { runCatching { settleOnce() } },
            SETTLE_INTERVAL_MIN,
            SETTLE_INTERVAL_MIN,
            TimeUnit.MINUTES
        )
    }

    private fun settleOnce() {
        if (!PetStore.isSignedIn(this)) return
        state = MoodEngine.settle(state, Instant.now())
        pushStateToWeb()
        runCatching { PetStore.saveState(this, state) }
    }

    /** 摸一下：交给 MoodEngine 算新的心情/亲密/精力，然后上报。 */
    private fun handleTap() {
        state = MoodEngine.onTap(state, Instant.now())
        pushStateToWeb()

        if (!PetStore.isSignedIn(this)) return
        val snapshot = state
        io.execute {
            runCatching {
                PetStore.appendEvent(
                    this,
                    "tap",
                    JSONObject()
                        .put("mood_value", snapshot.moodValue)
                        .put("intimacy", snapshot.intimacy)
                )
                PetStore.saveState(this, snapshot)
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

    private companion object {
        /** 周期结算的间隔（分钟）。 */
        const val SETTLE_INTERVAL_MIN = 5L
    }
}
