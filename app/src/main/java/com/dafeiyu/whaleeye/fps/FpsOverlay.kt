package com.dafeiyu.whaleeye.fps

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import java.util.Locale

class FpsOverlay(private val context: Context) {

    private var wm: WindowManager? = null
    private var view: TextView? = null

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    fun show(): Boolean {
        if (view != null) return true
        if (!canDraw()) return false
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        val tv = TextView(context).apply {
            text = "— FPS"
            setTextColor(Color.parseColor("#7CFFB2"))
            textSize = 11f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setBackgroundColor(Color.parseColor("#B3121420"))
            setPadding(16, 8, 16, 8)
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 160
        }
        return try {
            manager.addView(tv, lp)
            wm = manager
            view = tv
            true
        } catch (e: Exception) {
            view = null
            wm = null
            false
        }
    }

    fun update(fps: Float) {
        try {
            view?.text = String.format(Locale.US, "%.0f FPS", fps)
        } catch (e: Exception) {
        }
    }

    fun hide() {
        try {
            view?.let { v -> wm?.removeView(v) }
        } catch (e: Exception) {
        }
        view = null
        wm = null
    }
}