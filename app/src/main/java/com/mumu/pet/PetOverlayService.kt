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
 * \u628a\u684c\u5ba0\u753b\u5230\u5c4f\u5e55\u4e0a\u7684\u524d\u53f0\u670d\u52a1\u3002
 *
 * \u804c\u8d23\uff1a\u5efa\u4e00\u4e2a\u900f\u660e\u7684 WebView \u60ac\u6d6e\u7a97\u52a0\u8f7d assets/web/index.html\uff0c
 * \u5e76\u628a\u5b83\u548c Supabase \u91cc\u7684 pet_state / pet_events \u63a5\u8d77\u6765\u3002
 * \u60c5\u7eea\u7684\u8ba1\u7b97\u5168\u90e8\u59d4\u6258\u7ed9 MoodEngine\uff0c\u8fd9\u91cc\u53ea\u7ba1\u8c03\u5b83\u548c\u5b58\u76d8\u3002
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

        // \u7ed9\u6b7b\u5c3a\u5bf8\u3002WebView \u5728 WRAP_CONTENT \u4e0b\u91cf\u4e0d\u51fa\u5185\u5bb9\u9ad8\u5ea6\uff0c
        // \u60ac\u6d6e\u7a97\u4f1a\u88ab\u5b9a\u6210 0 \u9ad8\uff0c\u5c0f\u72d0\u72f8\u5c31\u4e00\u76f4\u4e0d\u53ef\u89c1\u3002
        val side = (PET_WINDOW_DP * resources.displayMetrics.density).toInt()

        val params = WindowManager.LayoutParams(
            side,
            side,
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

    // ---------- \u548c\u7f51\u9875\u62fc\u63a5 ----------

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

    /** \u62c9\u4e91\u7aef\u72b6\u6001\uff0c\u5148\u628a\u79bb\u5f00\u8fd9\u6bb5\u65f6\u95f4\u7684\u8d26\u7ed3\u7b97\u6389\uff0c\u518d\u5b58\u56de\u53bb\u3002 */
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
                    // \u79bb\u7ebf\u5c31\u7b97\u4e86\uff0cMuMu \u5148\u7528\u672c\u5730\u9ed8\u8ba4\u503c\u6d3b\u7740
                }
        }
    }

    /**
     * \u5468\u671f\u7ed3\u7b97\u3002\u670d\u52a1\u6d3b\u7740\u7684\u65f6\u5019\u6bcf\u9694\u4e00\u6bb5\u5c31\u628a\u7cbe\u529b/\u5fc3\u60c5\u6309\u65f6\u95f4\u63a8\u4e00\u63a8\uff0c
     * \u5c31\u7b97\u4e3b\u4eba\u4e00\u76f4\u4e0d\u78b0\u5b83\uff0c\u5b83\u4e5f\u4f1a\u56f0\u3001\u4e5f\u4f1a\u7d2f\u3002
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

    /** \u6478\u4e00\u4e0b\uff1a\u4ea4\u7ed9 MoodEngine \u7b97\u65b0\u7684\u5fc3\u60c5/\u4eb2\u5bc6/\u7cbe\u529b\uff0c\u7136\u540e\u4e0a\u62a5\u3002 */
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
        /** \u5468\u671f\u7ed3\u7b97\u7684\u95f4\u9694\uff08\u5206\u949f\uff09\u3002 */
        const val SETTLE_INTERVAL_MIN = 5L

        /** \u60ac\u6d6e\u7a97\u8fb9\u957f\uff08dp\uff09\u3002 */
        const val PET_WINDOW_DP = 132
    }
}
