package com.jarvis.assistant.service

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.jarvis.assistant.MainActivity

/**
 * Gemini-style floating Jarvis handle.
 * - Drag to reposition anywhere on screen
 * - LONG-PRESS the bubble → opens the Jarvis chat screen
 * - Single tap → opens MainActivity, which starts listening for a voice command
 */
class OverlayService : Service() {

    companion object {
        const val ACTION_SHOW = "com.jarvis.assistant.overlay.SHOW"
        const val ACTION_HIDE = "com.jarvis.assistant.overlay.HIDE"
    }

    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> hide()
            else -> show()
        }
        return START_STICKY
    }

    private fun show() {
        if (bubble != null) return
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }

        val tv = TextView(this).apply {
            text = "J"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xCC00E5FF.toInt())
                setStroke(3, Color.WHITE)
            }
        }

        val p = WindowManager.LayoutParams(
            dp(52), dp(52),
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(24); y = dp(200)
        }
        params = p

        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        var longPressFired = false
        val handler = android.os.Handler(mainLooper)
        val longPress = Runnable {
            longPressFired = true
            openChat()
        }

        tv.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = p.x; startY = p.y
                    longPressFired = false
                    handler.postDelayed(longPress, 500)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (Math.abs(e.rawX - downX) > 20 || Math.abs(e.rawY - downY) > 20)
                        handler.removeCallbacks(longPress)
                    p.x = startX + (e.rawX - downX).toInt()
                    p.y = startY + (e.rawY - downY).toInt()
                    wm.updateViewLayout(v, p)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    if (!longPressFired &&
                        Math.abs(e.rawX - downX) < 20 && Math.abs(e.rawY - downY) < 20) {
                        // Route through the activity: Android 12+ blocks background
                        // services from starting a foreground mic service directly.
                        startActivity(
                            Intent(this@OverlayService, MainActivity::class.java)
                                .setAction("com.jarvis.assistant.action.WAKE_START")
                                .putExtra("route", "chat")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                    }
                    true
                }
                else -> false
            }
        }

        wm.addView(tv, p)
        bubble = tv
    }

    private fun openChat() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra("route", "chat")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    private fun hide() {
        bubble?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        bubble = null
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() { hide(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
