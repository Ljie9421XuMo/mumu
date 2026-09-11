package com.mumu.pet

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.random.Random

/**
 * 把桌宠画到屏幕上的前台服务。
 *
 * 职责：建一个透明的 WebView 悬浮窗加载 assets/web/index.html，
 * 并把它和 Supabase 里的 pet_state / pet_events 接起来。
 * 情绪的计算全部委托给 MoodEngine，这里只管调它、存盘，
 * 另外负责它的“身体行为”：自己溜达、可以被拖动、偶尔说话。
 */
class PetOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var petView: WebView? = null
    private var params: WindowManager.LayoutParams? = null

    private val io = Executors.newSingleThreadScheduledExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private var state = PetState()

    // 屏幕 / 窗口尺寸
    private var screenW = 0
    private var screenH = 0
    private var winW = 0
    private var winH = 0

    // 自主溜达
    private var targetX = 0f
    private var restUntil = 0L
    private var walking = false

    // 拖拽
    private var downRawX = 0f
    private var downRawY = 0f
    private var grabX = 0
    private var grabY = 0
    private var dragging = false
    private var touchSlop = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        addPetToWindow()
        syncFromCloud()
        startSettleLoop()
        ui.postDelayed(wanderRunnable, FRAME_MS)
        ui.postDelayed(talkRunnable, FIRST_TALK_MS)
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

    @SuppressLint("ClickableViewAccessibility")
    private fun addPetToWindow() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenW = metrics.widthPixels
        screenH = metrics.heightPixels

        val density = resources.displayMetrics.density
        winW = (PET_WINDOW_W_DP * density).toInt()
        winH = (PET_WINDOW_H_DP * density).toInt()
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()

        val webView = WebView(this).apply {
            setBackgroundColor(0x00000000)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(Bridge(), "MuMuNative")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    pushStateToWeb()
                    say(greetingLine())
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

        // 给死尺寸。WebView 在 WRAP_CONTENT 下量不出内容高度，
        // 悬浮窗会被定成 0 高，小狐狸就一直不可见。
        val p = WindowManager.LayoutParams(
            winW,
            winH,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ((screenW - winW) / 2).coerceAtLeast(0)
            y = (screenH - winH - (BOTTOM_MARGIN_DP * density).toInt()).coerceAtLeast(0)
        }
        params = p

        webView.setOnTouchListener { _, ev -> onPetTouch(ev) }

        wm.addView(webView, p)
        petView = webView
        targetX = p.x.toFloat()
    }

    // ---------- 触摸：轻点是互动，拖动是搬家 ----------

    private fun onPetTouch(ev: MotionEvent): Boolean {
        val p = params ?: return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = ev.rawX
                downRawY = ev.rawY
                grabX = p.x
                grabY = p.y
                dragging = false
                restUntil = Long.MAX_VALUE // 手按住时先别自己跑
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downRawX
                val dy = ev.rawY - downRawY
                if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    dragging = true
                    setWalking(false)
                }
                if (dragging) {
                    p.x = (grabX + dx).toInt().coerceIn(0, (screenW - winW).coerceAtLeast(0))
                    p.y = (grabY + dy).toInt().coerceIn(0, (screenH - winH).coerceAtLeast(0))
                    runCatching { windowManager?.updateViewLayout(petView, p) }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) {
                    hop()
                    handleTap()
                } else {
                    targetX = p.x.toFloat()
                }
                restUntil = SystemClock.uptimeMillis() + REST_AFTER_TOUCH_MS
                return true
            }
        }
        return false
    }

    // ---------- 自己溜达 ----------

    private val wanderRunnable = object : Runnable {
        override fun run() {
            runCatching { wanderStep() }
            ui.postDelayed(this, FRAME_MS)
        }
    }

    private fun wanderStep() {
        val p = params ?: return
        val now = SystemClock.uptimeMillis()
        val maxX = (screenW - winW).coerceAtLeast(0)

        if (now < restUntil) {
            setWalking(false)
            return
        }

        val dx = targetX - p.x
        if (abs(dx) < 2f) {
            // 走到了，原地歇一会儿，再挑个新方向
            restUntil = now + REST_MIN_MS + Random.nextLong(REST_RAND_MS)
            targetX = Random.nextInt(0, maxX + 1).toFloat()
            setWalking(false)
            return
        }

        setWalking(true)
        val step = resources.displayMetrics.density * 1.0f
        p.x = (p.x + dx.coerceIn(-step, step)).toInt().coerceIn(0, maxX)
        runCatching { windowManager?.updateViewLayout(petView, p) }
    }

    private fun setWalking(w: Boolean) {
        if (walking == w) return
        walking = w
        evalJs("window.MuMu && window.MuMu.setWalking($w);")
    }

    // ---------- 偶尔说句话 ----------

    private val talkRunnable = object : Runnable {
        override fun run() {
            if (SystemClock.uptimeMillis() >= restUntil) say(randomLine())
            ui.postDelayed(this, TALK_MIN_MS + Random.nextLong(TALK_RAND_MS))
        }
    }

    private fun greetingLine(): String = when (state.mood) {
        "happy" -> "我回来啦～"
        "sad" -> "…你终于叫我了"
        "tired" -> "呼…有点累"
        "sleepy" -> "唔…好困"
        else -> "MuMu 来啦"
    }

    private fun randomLine(): String {
        val lines = when (state.mood) {
            "happy" -> HAPPY_LINES
            "sad" -> SAD_LINES
            "tired" -> TIRED_LINES
            "sleepy" -> SLEEPY_LINES
            else -> IDLE_LINES
        }
        return lines[Random.nextInt(lines.size)]
    }

    // ---------- 和网页拼接 ----------

    private inner class Bridge {
        @JavascriptInterface
        fun onPetTap() {
            handleTap()
        }
    }

    private fun evalJs(js: String) {
        val view = petView ?: return
        view.post { runCatching { view.evaluateJavascript(js, null) } }
    }

    private fun hop() = evalJs("window.MuMu && window.MuMu.hop();")

    private fun say(text: String) {
        evalJs("window.MuMu && window.MuMu.say(${JSONObject.quote(text)});")
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
            runCatching {
                view.evaluateJavascript(
                    "window.MuMu && window.MuMu.applyState($json);",
                    null
                )
            }
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
        ui.removeCallbacks(wanderRunnable)
        ui.removeCallbacks(talkRunnable)
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

        /** 悬浮窗宽/高（dp）。 */
        const val PET_WINDOW_W_DP = 150
        const val PET_WINDOW_H_DP = 150

        /** 悬浮窗离屏幕底部的距离（dp）。 */
        const val BOTTOM_MARGIN_DP = 24

        /** 溜达动画的帧间隔（毫秒）。 */
        const val FRAME_MS = 33L

        /** 走一段之后的休息时间。 */
        const val REST_MIN_MS = 1500L
        const val REST_RAND_MS = 5000L

        /** 被摸/被拖之后的安静时间。 */
        const val REST_AFTER_TOUCH_MS = 2500L

        /** 说话的间隔。 */
        const val FIRST_TALK_MS = 9000L
        const val TALK_MIN_MS = 22000L
        const val TALK_RAND_MS = 26000L

        val HAPPY_LINES = arrayOf("今天心情超好！", "陪着你最开心啦", "嘿嘿，摸摸头～")
        val SAD_LINES = arrayOf("有点想你了…", "别不理我好不好", "呜…想要抱抱")
        val TIRED_LINES = arrayOf("有点累了…", "歇会儿好不好", "眼皮好重呀")
        val SLEEPY_LINES = arrayOf("呼…好困…", "我先眯一会儿", "早点睡哦")
        val IDLE_LINES = arrayOf("在忙什么呀？", "我一直都在哦", "记得喝水", "陪我玩会儿嘛")
    }
}
