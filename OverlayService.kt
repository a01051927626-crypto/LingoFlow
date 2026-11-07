package com.example.lingoflow

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView

class OverlayService : Service() {
    companion object {
        const val ACTION_SHOW = "com.example.lingoflow.SHOW_OVERLAY"
        const val ACTION_UPDATE = "com.example.lingoflow.UPDATE_OVERLAY"
        const val EXTRA_TEXT = "translated_text"
    }

    private var windowManager: WindowManager? = null
    private var overlay: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE -> updateText(intent.getStringExtra(EXTRA_TEXT).orEmpty())
            ACTION_SHOW, null -> showOverlay()
        }
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (overlay != null) return
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = TextView(this).apply {
            text = "LingoFlow 준비 중…\\n일본어가 화면에 나타나면 번역합니다."
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xE6202B3D.toInt())
            setPadding(24, 18, 24, 18)
            maxWidth = resources.displayMetrics.widthPixels * 3 / 4
        }
        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 140
        }
        view.setOnTouchListener(object : android.view.View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var startX = 0
            private var startY = 0
            override fun onTouch(v: android.view.View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        startX = layout.x
                        startY = layout.y
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layout.x = startX + (event.rawX - downX).toInt()
                        layout.y = startY + (event.rawY - downY).toInt()
                        windowManager?.updateViewLayout(view, layout)
                        return true
                    }
                }
                return true
            }
        })
        windowManager?.addView(view, layout)
        overlay = view
        params = layout
    }

    private fun updateText(text: String) {
        if (overlay == null) showOverlay()
        overlay?.post {
            overlay?.text = if (text.isBlank()) "일본어 문장을 기다리는 중…" else text
        }
    }

    override fun onDestroy() {
        overlay?.let { windowManager?.removeView(it) }
        overlay = null
        params = null
        super.onDestroy()
    }
}
