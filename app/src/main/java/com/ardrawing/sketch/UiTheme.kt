package com.ardrawing.sketch

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View

object UiTheme {
    var isDark = false

    val bg: Int get() = Color.parseColor(if (isDark) "#0B0F19" else "#F5F3FF")
    val surface: Int get() = Color.parseColor(if (isDark) "#151C2C" else "#FFFFFF")
    val surfaceAlt: Int get() = Color.parseColor(if (isDark) "#1E293B" else "#EDE9FE")
    val textMain: Int get() = Color.parseColor(if (isDark) "#F8FAFC" else "#1E1B4B")
    val textSub: Int get() = Color.parseColor(if (isDark) "#94A3B8" else "#64748B")
    val accent: Int get() = Color.parseColor("#7C3AED")
    val stroke: Int get() = Color.parseColor(if (isDark) "#273449" else "#E2E8F0")

    fun dp(act: Activity, v: Int): Int = (v * act.resources.displayMetrics.density).toInt()

    fun round(act: Activity, color: Int, rDp: Int, strokeColor: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(act, rDp).toFloat()
        if (strokeColor != null) setStroke(dp(act, 1), strokeColor)
    }

    fun grad(act: Activity, hex1: String, hex2: String, rDp: Int) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.parseColor(hex1), Color.parseColor(hex2))
    ).apply {
        cornerRadius = dp(act, rDp).toFloat()
    }

    fun bounce(v: View, onClick: () -> Unit) {
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(85).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
            }
            false
        }
        v.setOnClickListener {
            v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(70).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                onClick()
            }.start()
        }
    }
}
