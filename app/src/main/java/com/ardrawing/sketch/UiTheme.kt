package com.ardrawing.sketch

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat

object UiTheme {
    var isDark = false

    private var cachedDisplayFont: Typeface? = null
    private var cachedUiFont: Typeface? = null

    fun displayFont(ctx: Context): Typeface? {
        if (cachedDisplayFont == null) {
            cachedDisplayFont = try { ResourcesCompat.getFont(ctx, R.font.outfit) } catch (_: Throwable) { null }
        }
        return cachedDisplayFont
    }

    fun uiFont(ctx: Context): Typeface? {
        if (cachedUiFont == null) {
            cachedUiFont = try { ResourcesCompat.getFont(ctx, R.font.plus_jakarta_sans) } catch (_: Throwable) { null }
        }
        return cachedUiFont
    }

    fun applyDisplayFont(tv: TextView, bold: Boolean = true) {
        val base = displayFont(tv.context)
        tv.setTypeface(base, if (bold) Typeface.BOLD else Typeface.NORMAL)
        if (bold) tv.letterSpacing = -0.02f
    }

    fun applyUiFont(tv: TextView, bold: Boolean = false) {
        val base = uiFont(tv.context)
        tv.setTypeface(base, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    // Swiss Editorial Light & Pro Atelier Obsidian Dark Palettes
    val bg: Int get() = Color.parseColor(if (isDark) "#07090F" else "#F7F8FC")
    val surface: Int get() = Color.parseColor(if (isDark) "#0F1422" else "#FFFFFF")
    val surfaceAlt: Int get() = Color.parseColor(if (isDark) "#171F33" else "#EFF2FA")
    val surfaceElevated: Int get() = Color.parseColor(if (isDark) "#1C263E" else "#FFFFFF")
    val textMain: Int get() = Color.parseColor(if (isDark) "#F8FAFC" else "#0F172A")
    val textSub: Int get() = Color.parseColor(if (isDark) "#94A3B8" else "#64748B")
    val accent: Int get() = Color.parseColor("#5B46F6")
    val accentBright: Int get() = Color.parseColor("#7C3AED")
    val accentSoft: Int get() = Color.parseColor(if (isDark) "#21194A" else "#EEEBFF")
    val pinkAccent: Int get() = Color.parseColor("#EC4899")
    val emeraldAccent: Int get() = Color.parseColor("#10B981")
    val amberAccent: Int get() = Color.parseColor("#F59E0B")
    val skyAccent: Int get() = Color.parseColor("#0EA5E9")
    val stroke: Int get() = Color.parseColor(if (isDark) "#222D45" else "#E2E6F3")
    val strokeStrong: Int get() = Color.parseColor(if (isDark) "#334366" else "#CBD2E8")

    private val seriesColors = listOf(
        "#F97316" to "#EA580C",
        "#EC4899" to "#DB2777",
        "#8B5CF6" to "#6D28D9",
        "#0EA5E9" to "#2563EB",
        "#F59E0B" to "#D97706",
        "#10B981" to "#059669",
        "#EF4444" to "#DC2626",
        "#6366F1" to "#4F46E5"
    )

    fun seriesPalette(index: Int): Pair<String, String> =
        seriesColors[(index % seriesColors.size + seriesColors.size) % seriesColors.size]

    fun dp(act: Context, v: Int): Int = (v * act.resources.displayMetrics.density).toInt()

    fun round(act: Context, color: Int, rDp: Int, strokeColor: Int? = null, strokeDp: Int = 1) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(act, rDp).toFloat()
            if (strokeColor != null) setStroke(dp(act, strokeDp), strokeColor)
        }

    fun grad(
        act: Context,
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
        act: Context,
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
            applyUiFont(this, bold = true)
            setTextColor(textColor)
            background = grad(act, hex1, hex2, rDp, strokeColor, 1)
            elevation = dp(act, 4).toFloat()
            setPadding(dp(act, 14), dp(act, 8), dp(act, 14), dp(act, 8))
        }
    }

    fun styleSoftBtn(
        act: Context,
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
            applyUiFont(this, bold = true)
            setTextColor(textColor)
            background = round(act, fillColor, rDp, strokeColor, 1)
            elevation = dp(act, 1).toFloat()
            setPadding(dp(act, 14), dp(act, 8), dp(act, 14), dp(act, 8))
        }
    }

    fun studioGradientAction(
        act: Context,
        icon: StudioIcons.Type,
        label: String,
        hex1: String,
        hex2: String,
        rDp: Int = 16,
        textColor: Int = Color.WHITE,
        trailingIcon: StudioIcons.Type? = null,
        iconSizeDp: Int = 18,
        textSizeSp: Float = 14.5f,
        onClick: () -> Unit
    ): LinearLayout {
        return StudioIcons.iconPill(
            context = act,
            iconType = icon,
            label = label,
            textColor = textColor,
            iconColor = textColor,
            iconSizeDp = iconSizeDp,
            textSizeSp = textSizeSp,
            trailingIcon = trailingIcon
        ).apply {
            background = grad(act, hex1, hex2, rDp, Color.parseColor("#3BFFFFFF"), 1)
            elevation = dp(act, 6).toFloat()
            setPadding(dp(act, 14), dp(act, 10), dp(act, 14), dp(act, 10))
            bounce(this, onClick)
        }
    }

    fun studioSoftAction(
        act: Context,
        icon: StudioIcons.Type,
        label: String,
        fillColor: Int = surfaceAlt,
        strokeColor: Int = stroke,
        textColor: Int = textMain,
        iconColor: Int = accentBright,
        rDp: Int = 15,
        iconSizeDp: Int = 17,
        textSizeSp: Float = 13.5f,
        onClick: () -> Unit
    ): LinearLayout {
        return StudioIcons.iconPill(
            context = act,
            iconType = icon,
            label = label,
            textColor = textColor,
            iconColor = iconColor,
            iconSizeDp = iconSizeDp,
            textSizeSp = textSizeSp
        ).apply {
            background = round(act, fillColor, rDp, strokeColor, 1)
            elevation = dp(act, 1).toFloat()
            setPadding(dp(act, 12), dp(act, 9), dp(act, 12), dp(act, 9))
            bounce(this, onClick)
        }
    }

    fun studioIconBtn(
        act: Context,
        icon: StudioIcons.Type,
        sizeDp: Int = 44,
        iconSizeDp: Int = 20,
        rDp: Int = 14,
        fillColor: Int = surface,
        strokeColor: Int? = stroke,
        iconTint: Int = textMain,
        filled: Boolean = false,
        contentDesc: String = "",
        onClick: () -> Unit
    ): FrameLayout {
        return FrameLayout(act).apply {
            contentDescription = contentDesc
            minimumWidth = dp(act, sizeDp)
            minimumHeight = dp(act, sizeDp)
            isClickable = true
            isFocusable = true
            background = round(act, fillColor, rDp, strokeColor, 1)
            elevation = dp(act, 2).toFloat()
            val iv = StudioIcons.imageView(act, icon, iconSizeDp, iconTint, filled)
            addView(iv, FrameLayout.LayoutParams(dp(act, iconSizeDp), dp(act, iconSizeDp), Gravity.CENTER))
            bounce(this, onClick)
        }
    }

    fun monogramBadge(
        act: Context,
        code: String,
        c1: String,
        c2: String,
        sizeDp: Int = 40,
        rDp: Int = 12,
        textSizeSp: Float = 13f
    ): TextView = TextView(act).apply {
        text = code
        textSize = textSizeSp
        applyDisplayFont(this, bold = true)
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        background = grad(act, c1, c2, rDp, Color.parseColor("#2AFFFFFF"), 1)
        layoutParams = LinearLayout.LayoutParams(dp(act, sizeDp), dp(act, sizeDp))
    }

    fun sectionHeader(
        act: Context,
        icon: StudioIcons.Type,
        title: String,
        badgeText: String? = null,
        iconTint: Int = Color.WHITE,
        c1: String = "#7C3AED",
        c2: String = "#4F46E5"
    ): LinearLayout {
        return LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(act, 6), 0, dp(act, 10))
            val well = FrameLayout(act).apply {
                background = grad(act, c1, c2, 10, Color.parseColor("#33FFFFFF"))
                addView(
                    StudioIcons.imageView(act, icon, 15, iconTint),
                    FrameLayout.LayoutParams(dp(act, 15), dp(act, 15), Gravity.CENTER)
                )
            }
            addView(well, LinearLayout.LayoutParams(dp(act, 28), dp(act, 28)).apply { marginEnd = dp(act, 10) })
            addView(TextView(act).apply {
                text = title
                textSize = 17f
                applyDisplayFont(this, bold = true)
                setTextColor(textMain)
                maxLines = 1
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            if (badgeText != null) {
                addView(TextView(act).apply {
                    text = badgeText
                    fontFeatureSettings = "tnum"
                    textSize = 11.5f
                    applyDisplayFont(this, bold = true)
                    setTextColor(accentBright)
                    background = round(act, accentSoft, 10, stroke)
                    setPadding(dp(act, 10), dp(act, 3), dp(act, 10), dp(act, 3))
                })
            }
        }
    }

    fun bounce(v: View, onClick: () -> Unit) {
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.955f).scaleY(0.955f).alpha(0.9f).setDuration(75).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start()
            }
            false
        }
        v.setOnClickListener {
            v.animate().scaleX(0.96f).scaleY(0.96f).alpha(0.92f).setDuration(60).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(100).start()
                onClick()
            }.start()
        }
    }
}
