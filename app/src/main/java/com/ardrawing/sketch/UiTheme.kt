package com.ardrawing.sketch

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
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
        if (bold) tv.letterSpacing = -0.025f
    }

    fun applyUiFont(tv: TextView, bold: Boolean = false) {
        val base = uiFont(tv.context)
        tv.setTypeface(base, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    // Swiss Editorial Light & Pro Atelier Obsidian Dark Palettes
    val bg: Int get() = Color.parseColor(if (isDark) "#06080F" else "#F5F7FC")
    val surface: Int get() = Color.parseColor(if (isDark) "#0F1526" else "#FFFFFF")
    val surfaceAlt: Int get() = Color.parseColor(if (isDark) "#182136" else "#EEF2FA")
    val surfaceElevated: Int get() = Color.parseColor(if (isDark) "#1E2942" else "#FFFFFF")
    val textMain: Int get() = Color.parseColor(if (isDark) "#F8FAFC" else "#0B1120")
    val textSub: Int get() = Color.parseColor(if (isDark) "#94A3B8" else "#64748B")
    val accent: Int get() = Color.parseColor("#5B46F6")
    val accentBright: Int get() = Color.parseColor("#7C3AED")
    val accentSoft: Int get() = Color.parseColor(if (isDark) "#221A4F" else "#EFEBFF")
    val pinkAccent: Int get() = Color.parseColor("#EC4899")
    val emeraldAccent: Int get() = Color.parseColor("#10B981")
    val amberAccent: Int get() = Color.parseColor("#F59E0B")
    val skyAccent: Int get() = Color.parseColor("#0EA5E9")
    val stroke: Int get() = Color.parseColor(if (isDark) "#24304B" else "#E2E7F4")
    val strokeStrong: Int get() = Color.parseColor(if (isDark) "#374870" else "#CBD3EA")

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

    // Layered Flagship Button Drawable: Rich 2-stop gradient + Top Specular Gloss + Native Material Ripple
    fun premiumGrad(
        act: Context,
        hex1: String,
        hex2: String,
        rDp: Int,
        strokeColor: Int = Color.parseColor("#47FFFFFF"),
        strokeDp: Int = 1
    ): Drawable {
        val rPx = dp(act, rDp).toFloat()
        val base = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor(hex1), Color.parseColor(hex2))
        ).apply {
            cornerRadius = rPx
            setStroke(dp(act, strokeDp), strokeColor)
        }
        val gloss = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#36FFFFFF"), Color.parseColor("#08FFFFFF"), Color.TRANSPARENT)
        ).apply {
            cornerRadius = rPx
        }
        val layered = LayerDrawable(arrayOf(base, gloss)).apply {
            setLayerInset(1, dp(act, 1), dp(act, 1), dp(act, 1), dp(act, 1))
        }
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = rPx
        }
        return RippleDrawable(ColorStateList.valueOf(Color.parseColor("#44FFFFFF")), layered, mask)
    }

    // Soft Tactile Surface with Native Ripple
    fun premiumSurface(
        act: Context,
        fillColor: Int = surface,
        rDp: Int = 16,
        strokeColor: Int? = stroke,
        strokeDp: Int = 1
    ): Drawable {
        val rPx = dp(act, rDp).toFloat()
        val base = round(act, fillColor, rDp, strokeColor, strokeDp)
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = rPx
        }
        val rippleColor = Color.parseColor(if (isDark) "#28FFFFFF" else "#185B46F6")
        return RippleDrawable(ColorStateList.valueOf(rippleColor), base, mask)
    }

    fun applyColoredShadow(v: View, shadowColor: Int, elevationDp: Int) {
        v.elevation = dp(v.context, elevationDp).toFloat()
        v.outlineAmbientShadowColor = shadowColor
        v.outlineSpotShadowColor = shadowColor
    }

    fun styleGradientBtn(
        act: Context,
        btn: Button,
        hex1: String,
        hex2: String,
        rDp: Int = 16,
        textColor: Int = Color.WHITE,
        strokeColor: Int = Color.parseColor("#42FFFFFF")
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
            background = premiumGrad(act, hex1, hex2, rDp, strokeColor, 1)
            applyColoredShadow(this, Color.parseColor(hex1), 7)
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
            background = premiumSurface(act, fillColor, rDp, strokeColor, 1)
            elevation = dp(act, 2).toFloat()
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
            background = premiumGrad(act, hex1, hex2, rDp, Color.parseColor("#47FFFFFF"), 1)
            applyColoredShadow(this, Color.parseColor(hex1), 8)
            setPadding(dp(act, 16), dp(act, 11), dp(act, 16), dp(act, 11))
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
            background = premiumSurface(act, fillColor, rDp, strokeColor, 1)
            elevation = dp(act, 2).toFloat()
            setPadding(dp(act, 13), dp(act, 10), dp(act, 13), dp(act, 10))
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
            background = premiumSurface(act, fillColor, rDp, strokeColor, 1)
            elevation = dp(act, 3).toFloat()
            val iv = StudioIcons.imageView(act, icon, iconSizeDp, iconTint, filled)
            addView(iv, FrameLayout.LayoutParams(dp(act, iconSizeDp), dp(act, iconSizeDp), Gravity.CENTER))
            bounce(this, onClick)
        }
    }

    // Flagship Custom Pill SeekBar with Gradient Track & Luminous Halo Thumb
    fun styleProSeekBar(
        act: Context,
        sb: SeekBar,
        c1Hex: String = "#8B5CF6",
        c2Hex: String = "#EC4899",
        darkSurface: Boolean = isDark
    ): SeekBar {
        val trackH = dp(act, 7)
        val trackBg = GradientDrawable().apply {
            setColor(Color.parseColor(if (darkSurface) "#33FFFFFF" else "#E2E8F0"))
            cornerRadius = trackH / 2f
            setSize(-1, trackH)
        }
        val progGrad = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor(c1Hex), Color.parseColor(c2Hex))
        ).apply {
            cornerRadius = trackH / 2f
            setSize(-1, trackH)
        }
        val progClip = ClipDrawable(progGrad, Gravity.START, ClipDrawable.HORIZONTAL)
        val trackLayer = LayerDrawable(arrayOf(trackBg, progClip)).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }
        val thumbSize = dp(act, 22)
        val thumb = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
            setStroke(dp(act, 3), Color.parseColor(c1Hex))
            setSize(thumbSize, thumbSize)
        }
        sb.apply {
            progressDrawable = trackLayer
            setThumb(thumb)
            splitTrack = false
            thumbOffset = dp(act, 2)
            setPadding(dp(act, 10), dp(act, 8), dp(act, 10), dp(act, 8))
            minimumHeight = dp(act, 30)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                minHeight = dp(act, 30)
                maxHeight = dp(act, 30)
            }
        }
        return sb
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
        background = premiumGrad(act, c1, c2, rDp, Color.parseColor("#38FFFFFF"), 1)
        applyColoredShadow(this, Color.parseColor(c1), 4)
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
            setPadding(0, dp(act, 8), 0, dp(act, 10))
            val well = FrameLayout(act).apply {
                background = premiumGrad(act, c1, c2, 11, Color.parseColor("#44FFFFFF"))
                applyColoredShadow(this, Color.parseColor(c1), 5)
                addView(
                    StudioIcons.imageView(act, icon, 15, iconTint),
                    FrameLayout.LayoutParams(dp(act, 15), dp(act, 15), Gravity.CENTER)
                )
            }
            addView(well, LinearLayout.LayoutParams(dp(act, 30), dp(act, 30)).apply { marginEnd = dp(act, 10) })
            addView(TextView(act).apply {
                text = title
                textSize = 17.5f
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
                    background = round(act, accentSoft, 11, stroke)
                    setPadding(dp(act, 10), dp(act, 4), dp(act, 10), dp(act, 4))
                })
            }
        }
    }

    // Smooth Page Reveal Animation (Fade + Gentle Upward Glide)
    fun animatePageIn(v: View, dyDp: Int = 14, durationMs: Long = 240L) {
        v.alpha = 0f
        v.translationY = dp(v.context, dyDp).toFloat()
        v.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator(2.2f))
            .start()
    }

    // Staggered Card Reveal for Galleries & Tool Rows
    fun animateStaggered(v: View, index: Int, baseDelayMs: Long = 28L) {
        val delay = (index.coerceAtMost(8) * baseDelayMs)
        v.alpha = 0f
        v.translationY = dp(v.context, 16).toFloat()
        v.scaleX = 0.965f
        v.scaleY = 0.965f
        v.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(delay)
            .setDuration(290L)
            .setInterpolator(DecelerateInterpolator(2.2f))
            .start()
    }

    // Modal Backdrop + Spring Card Entrance
    fun animateModalIn(backdrop: View, card: View) {
        backdrop.alpha = 0f
        backdrop.animate().alpha(1f).setDuration(180L).start()
        card.alpha = 0f
        card.scaleX = 0.88f
        card.scaleY = 0.88f
        card.translationY = dp(card.context, 24).toFloat()
        card.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(290L)
            .setInterpolator(OvershootInterpolator(1.25f))
            .start()
    }

    fun animateModalOut(backdrop: View, card: View, onEnd: () -> Unit) {
        card.animate()
            .alpha(0f)
            .scaleX(0.92f)
            .scaleY(0.92f)
            .translationY(dp(card.context, 16).toFloat())
            .setDuration(150L)
            .start()
        backdrop.animate()
            .alpha(0f)
            .setDuration(160L)
            .withEndAction { onEnd() }
            .start()
    }

    // Subtle Periodic Breathing Glow for Hero CTA Badges / Coins
    fun attachGentleFloat(v: View, offsetDp: Float = 3f, durationMs: Long = 2400L) {
        val px = offsetDp * v.resources.displayMetrics.density
        val anim = ValueAnimator.ofFloat(-px, px).apply {
            duration = durationMs
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator(1.2f)
            addUpdateListener { v.translationY = it.animatedValue as Float }
        }
        v.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) { anim.start() }
            override fun onViewDetachedFromWindow(view: View) { anim.cancel() }
        })
        if (v.isAttachedToWindow) anim.start()
    }

    // Crisp Tactile Spring Bounce + Haptic Micro-Click (Zero-latency instant click response!)
    fun bounce(v: View, onClick: () -> Unit) {
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.animate()
                        .scaleX(0.945f)
                        .scaleY(0.945f)
                        .alpha(0.92f)
                        .setDuration(80L)
                        .setInterpolator(DecelerateInterpolator(2f))
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(220L)
                        .setInterpolator(OvershootInterpolator(2.1f))
                        .start()
                }
            }
            false
        }
        v.setOnClickListener {
            try {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } catch (_: Throwable) {}
            v.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(190L)
                .setInterpolator(OvershootInterpolator(1.9f))
                .start()
            onClick()
        }
    }

    // Helper: Apply smooth LayoutTransition or child cleanup safely
    fun wrapShimmerCta(act: Context, btn: View, radiusDp: Int = 16): FrameLayout {
        val rPx = dp(act, radiusDp).toFloat()
        return FrameLayout(act).apply {
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(v: View, o: android.graphics.Outline) {
                    o.setRoundRect(0, 0, v.width, v.height, rPx)
                }
            }
            addView(btn, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(
                StudioIcons.ShimmerSweepView(act),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
    }
}
