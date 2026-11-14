package com.ardrawing.sketch

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.widget.Button

object UiTheme {
    var isDark = false

    val bg: Int get() = Color.parseColor(if (isDark) "#090D16" else "#F6F5FF")
    val surface: Int get() = Color.parseColor(if (isDark) "#131B2E" else "#FFFFFF")
    val surfaceAlt: Int get() = Color.parseColor(if (isDark) "#1C2640" else "#EFEAFE")
    val textMain: Int get() = Color.parseColor(if (isDark) "#F8FAFC" else "#17133B")
    val textSub: Int get() = Color.parseColor(if (isDark) "#94A3B8" else "#64748B")
    val accent: Int get() = Color.parseColor("#7C3AED")
    val accentSoft: Int get() = Color.parseColor(if (isDark) "#2E1065" else "#EDE9FE")
    val stroke: Int get() = Color.parseColor(if (isDark) "#26334D" else "#E4E0F8")

    fun dp(act: Activity, v: Int): Int = (v * act.resources.displayMetrics.density).toInt()

    fun round(act: Activity, color: Int, rDp: Int, strokeColor: Int? = null, strokeDp: Int = 1) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(act, rDp).toFloat()
            if (strokeColor != null) setStroke(dp(act, strokeDp), strokeColor)
        }

    fun grad(
        act: Activity,
        hex1: String,
        hex2: String,
        rDp: Int,
        strokeColor: Int? = null,
        strokeDp: Int = 1
    ) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.parseColor(hex1), Color.parseColor(hex2))
    ).apply {
        cornerRadius = dp(act, rDp).toFloat()
        if (strokeColor != null) setStroke(dp(act, strokeDp), strokeColor)
    }

    fun styleGradientBtn(
        act: Activity,
        btn: Button,
        hex1: String,
        hex2: String,
        rDp: Int = 16,
        textColor: Int = Color.WHITE,
        strokeColor: Int = Color.parseColor("#33FFFFFF")
    ) {
        btn.apply {
            stateListAnimator = null
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            isAllCaps = false
            setTextColor(textColor)
            background = grad(act, hex1, hex2, rDp, strokeColor, 1)
            elevation = dp(act, 4).toFloat()
            setPadding(dp(act, 12), dp(act, 8), dp(act, 12), dp(act, 8))
        }
    }

    fun styleSoftBtn(
        act: Activity,
        btn: Button,
        rDp: Int = 16,
        fillColor: Int = surfaceAlt,
        strokeColor: Int = stroke,
        textColor: Int = textMain
    ) {
        btn.apply {
            stateListAnimator = null
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            isAllCaps = false
            setTextColor(textColor)
            background = round(act, fillColor, rDp, strokeColor, 1)
            elevation = dp(act, 1).toFloat()
            setPadding(dp(act, 12), dp(act, 8), dp(act, 12), dp(act, 8))
        }
    }

    fun bounce(v: View, onClick: () -> Unit) {
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.95f).scaleY(0.95f).alpha(0.9f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start()
            }
            false
        }
        v.setOnClickListener {
            v.animate().scaleX(0.96f).scaleY(0.96f).alpha(0.92f).setDuration(65).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(100).start()
                onClick()
            }.start()
        }
    }
}
