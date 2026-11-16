package com.ardrawing.sketch

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout

object StudioIcons {

    enum class Type {
        HOME, GALLERY, STORE, SETTINGS,
        SEARCH, GLOBE, CAMERA, PHOTO, FOLDER,
        CREATE_PEN, SPARK, ARROW_RIGHT, ARROW_LEFT, CHEVRON_RIGHT,
        CHECK, CLOSE, PLUS,
        MAGIC_BG, INK_PEN, CROP, EYEDROPPER, UNDO, RESTORE, ROTATE_90,
        DROPLET, SUN, ERASER,
        FLIP_H, FLASH, LOCK, UNLOCK, EYE_HIDE, EXPAND, GRID, RESET_POS,
        COLLECTION, ANIME_STAR, IMPORT, EXPORT_ZIP, LINK, EDIT, SHARE, TRASH, COMPASS,
        COIN, CROWN, TIMER, SHIELD, SHIELD_LOCK, MOON, INFO, STAR, MAIL, COPY, CHILD_SHIELD,
        CAT_ALL, CAT_ANIME, CAT_CAR, CAT_FOOD, CAT_FLOWER, CAT_SCENERY, CAT_ANIMALS,
        TROPHY
    }

    fun icon(
        context: Context,
        type: Type,
        sizeDp: Int,
        tintColor: Int,
        filled: Boolean = false,
        secondaryTint: Int? = null
    ): Drawable {
        val px = (sizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        return VectorIconDrawable(type, px, tintColor, filled, secondaryTint)
    }

    fun imageView(
        context: Context,
        type: Type,
        sizeDp: Int,
        tintColor: Int,
        filled: Boolean = false,
        secondaryTint: Int? = null
    ): ImageView = ImageView(context).apply {
        setImageDrawable(icon(context, type, sizeDp, tintColor, filled, secondaryTint))
        scaleType = ImageView.ScaleType.CENTER_INSIDE
    }

    fun brandLogoDrawable(context: Context, sizeDp: Int): Drawable {
        val px = (sizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        return BrandLogoDrawable(px)
    }

    // ---------------- PROGRAMMATIC BRAND LOGO MARK ----------------
    private class BrandLogoDrawable(private val sizePx: Int) : Drawable() {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun getIntrinsicWidth(): Int = sizePx
        override fun getIntrinsicHeight(): Int = sizePx

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat().coerceAtLeast(1f)
            val h = b.height().toFloat().coerceAtLeast(1f)
            val s = minOf(w, h)
            val cx = b.centerX().toFloat()
            val cy = b.centerY().toFloat()

            canvas.save()
            canvas.translate(cx - s / 2f, cy - s / 2f)

            // Squircle studio badge background
            val r = s * 0.24f
            bgPaint.shader = LinearGradient(
                0f, 0f, s, s,
                intArrayOf(Color.parseColor("#312E81"), Color.parseColor("#6D28D9"), Color.parseColor("#DB2777")),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(RectF(0f, 0f, s, s), r, r, bgPaint)

            // Subtle inner specular rim
            strokePaint.shader = null
            strokePaint.color = Color.parseColor("#44FFFFFF")
            strokePaint.strokeWidth = s * 0.025f
            val inset = s * 0.025f
            canvas.drawRoundRect(RectF(inset, inset, s - inset, s - inset), r * 0.92f, r * 0.92f, strokePaint)

            // 4 AR Viewfinder corner brackets
            framePaint.color = Color.parseColor("#CCFFFFFF")
            framePaint.strokeWidth = s * 0.055f
            val pad = s * 0.17f
            val arm = s * 0.14f
            // TL
            canvas.drawLine(pad, pad + arm, pad, pad, framePaint)
            canvas.drawLine(pad, pad, pad + arm, pad, framePaint)
            // TR
            canvas.drawLine(s - pad - arm, pad, s - pad, pad, framePaint)
            canvas.drawLine(s - pad, pad, s - pad, pad + arm, framePaint)
            // BL
            canvas.drawLine(pad, s - pad - arm, pad, s - pad, framePaint)
            canvas.drawLine(pad, s - pad, pad + arm, s - pad, framePaint)
            // BR
            canvas.drawLine(s - pad - arm, s - pad, s - pad, s - pad, framePaint)
            canvas.drawLine(s - pad, s - pad - arm, s - pad, s - pad, framePaint)

            // Tilted Precision Calligraphy Pen Nib / AR Prism in center
            canvas.save()
            canvas.rotate(-22f, s * 0.5f, s * 0.5f)

            val nibPath = Path().apply {
                moveTo(s * 0.5f, s * 0.22f)
                lineTo(s * 0.67f, s * 0.54f)
                lineTo(s * 0.59f, s * 0.74f)
                lineTo(s * 0.41f, s * 0.74f)
                lineTo(s * 0.33f, s * 0.54f)
                close()
            }
            fillPaint.shader = LinearGradient(
                s * 0.33f, s * 0.22f, s * 0.67f, s * 0.74f,
                Color.parseColor("#FFFFFF"),
                Color.parseColor("#DDD6FE"),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(nibPath, fillPaint)

            // Nib slit & breather hole
            strokePaint.color = Color.parseColor("#5B21B6")
            strokePaint.strokeWidth = s * 0.042f
            canvas.drawLine(s * 0.5f, s * 0.26f, s * 0.5f, s * 0.47f, strokePaint)
            fillPaint.shader = null
            fillPaint.color = Color.parseColor("#5B21B6")
            canvas.drawCircle(s * 0.5f, s * 0.50f, s * 0.048f, fillPaint)

            // Gold collar band
            fillPaint.color = Color.parseColor("#FBBF24")
            canvas.drawRoundRect(
                RectF(s * 0.39f, s * 0.71f, s * 0.61f, s * 0.79f),
                s * 0.03f, s * 0.03f, fillPaint
            )
            canvas.restore()

            // Crisp cyan AR spark dot at top-right
            fillPaint.color = Color.parseColor("#38BDF8")
            canvas.drawCircle(s * 0.74f, s * 0.27f, s * 0.045f, fillPaint)

            canvas.restore()
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    // ---------------- PROGRAMMATIC VECTOR ICON ENGINE (24x24 VIEWBOX) ----------------
    private class VectorIconDrawable(
        private val type: Type,
        private val sizePx: Int,
        private val tint: Int,
        private val filled: Boolean,
        private val secondaryTint: Int?
    ) : Drawable() {

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = tint
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = tint
        }
        private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = secondaryTint ?: tint
        }

        override fun getIntrinsicWidth(): Int = sizePx
        override fun getIntrinsicHeight(): Int = sizePx

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat().coerceAtLeast(1f)
            val h = b.height().toFloat().coerceAtLeast(1f)
            val s = minOf(w, h)
            val sc = s / 24f

            canvas.save()
            canvas.translate(b.centerX() - s / 2f, b.centerY() - s / 2f)
            canvas.scale(sc, sc)

            strokePaint.strokeWidth = 2.05f
            strokePaint.color = tint
            fillPaint.color = tint
            fillPaint.shader = null
            accentPaint.color = secondaryTint ?: tint

            when (type) {
                Type.HOME -> {
                    val p = Path().apply {
                        moveTo(3.5f, 10.5f)
                        lineTo(12f, 3.5f)
                        lineTo(20.5f, 10.5f)
                        lineTo(20.5f, 19.5f)
                        quadTo(20.5f, 20.5f, 19.5f, 20.5f)
                        lineTo(4.5f, 20.5f)
                        quadTo(3.5f, 20.5f, 3.5f, 19.5f)
                        close()
                    }
                    if (filled) {
                        canvas.drawPath(p, fillPaint)
                        fillPaint.color = secondaryTint ?: Color.parseColor("#44FFFFFF")
                        canvas.drawRoundRect(RectF(9.6f, 13.5f, 14.4f, 20.5f), 1.4f, 1.4f, fillPaint)
                    } else {
                        canvas.drawPath(p, strokePaint)
                        canvas.drawLine(10f, 20.5f, 10f, 14f, strokePaint)
                        canvas.drawLine(14f, 20.5f, 14f, 14f, strokePaint)
                        canvas.drawLine(10f, 14f, 14f, 14f, strokePaint)
                    }
                }
                Type.GALLERY -> {
                    if (filled) {
                        val folder = Path().apply {
                            moveTo(3f, 6.5f)
                            quadTo(3f, 5f, 4.5f, 5f)
                            lineTo(9.4f, 5f)
                            lineTo(11.3f, 7.2f)
                            lineTo(19.5f, 7.2f)
                            quadTo(21f, 7.2f, 21f, 8.7f)
                            lineTo(21f, 18.5f)
                            quadTo(21f, 20f, 19.5f, 20f)
                            lineTo(4.5f, 20f)
                            quadTo(3f, 20f, 3f, 18.5f)
                            close()
                        }
                        canvas.drawPath(folder, fillPaint)
                    } else {
                        canvas.drawRoundRect(RectF(3f, 4.5f, 21f, 19.5f), 3f, 3f, strokePaint)
                        canvas.drawCircle(8.5f, 9.5f, 1.5f, fillPaint)
                        val mnt = Path().apply {
                            moveTo(4.5f, 17.5f)
                            lineTo(9.5f, 12.5f)
                            lineTo(13f, 15.8f)
                            lineTo(16f, 13f)
                            lineTo(19.5f, 17.5f)
                        }
                        canvas.drawPath(mnt, strokePaint)
                    }
                }
                Type.STORE -> {
                    val bag = Path().apply {
                        moveTo(5.2f, 8f)
                        lineTo(18.8f, 8f)
                        lineTo(20f, 19.5f)
                        quadTo(20.1f, 20.8f, 18.8f, 20.8f)
                        lineTo(5.2f, 20.8f)
                        quadTo(3.9f, 20.8f, 4f, 19.5f)
                        close()
                    }
                    val handle = Path().apply {
                        moveTo(8.5f, 8f)
                        cubicTo(8.5f, 3.8f, 15.5f, 3.8f, 15.5f, 8f)
                    }
                    if (filled) {
                        canvas.drawPath(bag, fillPaint)
                        canvas.drawPath(handle, strokePaint)
                        fillPaint.color = secondaryTint ?: Color.parseColor("#66FFFFFF")
                        canvas.drawCircle(12f, 14.2f, 2.2f, fillPaint)
                    } else {
                        canvas.drawPath(bag, strokePaint)
                        canvas.drawPath(handle, strokePaint)
                    }
                }
                Type.SETTINGS -> {
                    val cx = 12f
                    val cy = 12f
                    for (i in 0 until 8) {
                        val a = Math.toRadians(i * 45.0)
                        val x1 = (cx + Math.cos(a) * 6.9f).toFloat()
                        val y1 = (cy + Math.sin(a) * 6.9f).toFloat()
                        val x2 = (cx + Math.cos(a) * 9.3f).toFloat()
                        val y2 = (cy + Math.sin(a) * 9.3f).toFloat()
                        strokePaint.strokeWidth = 2.6f
                        canvas.drawLine(x1, y1, x2, y2, strokePaint)
                    }
                    strokePaint.strokeWidth = 2.05f
                    if (filled) {
                        canvas.drawCircle(cx, cy, 6.8f, fillPaint)
                        fillPaint.color = secondaryTint ?: Color.parseColor("#55FFFFFF")
                        canvas.drawCircle(cx, cy, 2.6f, fillPaint)
                    } else {
                        canvas.drawCircle(cx, cy, 6.2f, strokePaint)
                        canvas.drawCircle(cx, cy, 2.5f, strokePaint)
                    }
                }
                Type.SEARCH -> {
                    canvas.drawCircle(10.5f, 10.5f, 6.2f, strokePaint)
                    strokePaint.strokeWidth = 2.3f
                    canvas.drawLine(15.2f, 15.2f, 20.2f, 20.2f, strokePaint)
                }
                Type.GLOBE -> {
                    canvas.drawCircle(12f, 12f, 8.8f, strokePaint)
                    canvas.drawOval(RectF(8.2f, 3.3f, 15.8f, 20.7f), strokePaint)
                    canvas.drawLine(3.5f, 12f, 20.5f, 12f, strokePaint)
                    canvas.drawLine(5.2f, 7.6f, 18.8f, 7.6f, strokePaint)
                    canvas.drawLine(5.2f, 16.4f, 18.8f, 16.4f, strokePaint)
                }
                Type.CAMERA -> {
                    val body = Path().apply {
                        moveTo(3.5f, 8.5f)
                        quadTo(3.5f, 7f, 5f, 7f)
                        lineTo(7.5f, 7f)
                        lineTo(9f, 4.8f)
                        lineTo(15f, 4.8f)
                        lineTo(16.5f, 7f)
                        lineTo(19f, 7f)
                        quadTo(20.5f, 7f, 20.5f, 8.5f)
                        lineTo(20.5f, 18f)
                        quadTo(20.5f, 19.5f, 19f, 19.5f)
                        lineTo(5f, 19.5f)
                        quadTo(3.5f, 19.5f, 3.5f, 18f)
                        close()
                    }
                    canvas.drawPath(body, strokePaint)
                    canvas.drawCircle(12f, 13.2f, 3.4f, strokePaint)
                }
                Type.PHOTO -> {
                    canvas.drawRoundRect(RectF(3.5f, 4.5f, 20.5f, 19.5f), 2.8f, 2.8f, strokePaint)
                    canvas.drawCircle(8.5f, 9.2f, 1.4f, fillPaint)
                    val p = Path().apply {
                        moveTo(4.5f, 17.8f)
                        lineTo(9.8f, 12.6f)
                        lineTo(13.2f, 15.8f)
                        lineTo(16.4f, 12.2f)
                        lineTo(19.6f, 17.8f)
                    }
                    canvas.drawPath(p, strokePaint)
                }
                Type.FOLDER -> {
                    val p = Path().apply {
                        moveTo(3.5f, 6.8f)
                        quadTo(3.5f, 5.2f, 5.1f, 5.2f)
                        lineTo(9.8f, 5.2f)
                        lineTo(11.8f, 7.5f)
                        lineTo(19f, 7.5f)
                        quadTo(20.5f, 7.5f, 20.5f, 9f)
                        lineTo(20.5f, 17.8f)
                        quadTo(20.5f, 19.3f, 19f, 19.3f)
                        lineTo(5f, 19.3f)
                        quadTo(3.5f, 19.3f, 3.5f, 17.8f)
                        close()
                    }
                    canvas.drawPath(p, strokePaint)
                }
                Type.CREATE_PEN -> {
                    val p = Path().apply {
                        moveTo(15.8f, 4.2f)
                        lineTo(19.8f, 8.2f)
                        lineTo(8.5f, 19.5f)
                        lineTo(4f, 20f)
                        lineTo(4.5f, 15.5f)
                        close()
                    }
                    canvas.drawPath(p, strokePaint)
                    canvas.drawLine(13.2f, 6.8f, 17.2f, 10.8f, strokePaint)
                }
                Type.SPARK -> {
                    val star = Path().apply {
                        moveTo(12f, 2.5f)
                        lineTo(14.3f, 9.7f)
                        lineTo(21.5f, 12f)
                        lineTo(14.3f, 14.3f)
                        lineTo(12f, 21.5f)
                        lineTo(9.7f, 14.3f)
                        lineTo(2.5f, 12f)
                        lineTo(9.7f, 9.7f)
                        close()
                    }
                    canvas.drawPath(star, fillPaint)
                }
                Type.ARROW_RIGHT -> {
                    canvas.drawLine(4.5f, 12f, 19f, 12f, strokePaint)
                    val p = Path().apply {
                        moveTo(13.5f, 6.5f)
                        lineTo(19.5f, 12f)
                        lineTo(13.5f, 17.5f)
                    }
                    canvas.drawPath(p, strokePaint)
                }
                Type.ARROW_LEFT -> {
                    canvas.drawLine(19.5f, 12f, 5f, 12f, strokePaint)
                    val p = Path().apply {
                        moveTo(10.5f, 6.5f)
                        lineTo(4.5f, 12f)
                        lineTo(10.5f, 17.5f)
                    }
                    canvas.drawPath(p, strokePaint)
                }
                Type.CHEVRON_RIGHT -> {
                    val p = Path().apply {
                        moveTo(9f, 6f)
                        lineTo(15.2f, 12f)
                        lineTo(9f, 18f)
                    }
                    canvas.drawPath(p, strokePaint)
                }
                Type.CHECK -> {
                    strokePaint.strokeWidth = 2.5f
                    val p = Path().apply {
                        moveTo(4.8f, 12.6f)
                        lineTo(9.8f, 17.4f)
                        lineTo(19.2f, 7f)
                    }
                    canvas.drawPath(p, strokePaint)
                }
                Type.CLOSE -> {
                    strokePaint.strokeWidth = 2.3f
                    canvas.drawLine(6f, 6f, 18f, 18f, strokePaint)
                    canvas.drawLine(18f, 6f, 6f, 18f, strokePaint)
                }
                Type.PLUS -> {
                    strokePaint.strokeWidth = 2.4f
                    canvas.drawLine(12f, 5f, 12f, 19f, strokePaint)
                    canvas.drawLine(5f, 12f, 19f, 12f, strokePaint)
                }
                Type.MAGIC_BG -> {
                    canvas.drawLine(4.5f, 19.5f, 14.5f, 9.5f, strokePaint)
                    val tip = Path().apply {
                        moveTo(16.5f, 3f)
                        lineTo(17.7f, 6.3f)
                        lineTo(21f, 7.5f)
                        lineTo(17.7f, 8.7f)
                        lineTo(16.5f, 12f)
                        lineTo(15.3f, 8.7f)
                        lineTo(12f, 7.5f)
                        lineTo(15.3f, 6.3f)
                        close()
                    }
                    canvas.drawPath(tip, fillPaint)
                    canvas.drawCircle(7f, 6.5f, 1.2f, fillPaint)
                    canvas.drawCircle(19.2f, 15.5f, 1.2f, fillPaint)
                }
                Type.INK_PEN -> {
                    val nib = Path().apply {
                        moveTo(12f, 3f)
                        lineTo(17.5f, 13.5f)
                        lineTo(15f, 19.5f)
                        lineTo(9f, 19.5f)
                        lineTo(6.5f, 13.5f)
                        close()
                    }
                    canvas.drawPath(nib, strokePaint)
                    canvas.drawLine(12f, 3.5f, 12f, 10.5f, strokePaint)
                    canvas.drawCircle(12f, 12f, 1.5f, fillPaint)
                }
                Type.CROP -> {
                    val p1 = Path().apply {
                        moveTo(7f, 3f)
                        lineTo(7f, 17f)
                        lineTo(21f, 17f)
                    }
                    val p2 = Path().apply {
                        moveTo(3f, 7f)
                        lineTo(17f, 7f)
                        lineTo(17f, 21f)
                    }
                    canvas.drawPath(p1, strokePaint)
                    canvas.drawPath(p2, strokePaint)
                }
                Type.EYEDROPPER -> {
                    val p = Path().apply {
                        moveTo(15.5f, 3.8f)
                        lineTo(20.2f, 8.5f)
                        lineTo(17.5f, 11.2f)
                        lineTo(12.8f, 6.5f)
                        close()
                    }
                    canvas.drawPath(p, fillPaint)
                    val tip = Path().apply {
                        moveTo(14.5f, 8.2f)
                        lineTo(15.8f, 9.5f)
                        lineTo(7.5f, 17.8f)
                        lineTo(4.2f, 19.8f)
                        lineTo(6.2f, 16.5f)
                        close()
                    }
                    canvas.drawPath(tip, strokePaint)
                }
                Type.UNDO -> {
                    val arc = Path().apply {
                        moveTo(8f, 8f)
                        lineTo(4.2f, 11.5f)
                        lineTo(8f, 15f)
                    }
                    canvas.drawPath(arc, strokePaint)
                    val curve = Path().apply {
                        moveTo(5f, 11.5f)
                        lineTo(14.5f, 11.5f)
                        cubicTo(19f, 11.5f, 19.5f, 18.5f, 14f, 18.5f)
                        lineTo(10f, 18.5f)
                    }
                    canvas.drawPath(curve, strokePaint)
                }
                Type.RESTORE, Type.ROTATE_90 -> {
                    canvas.drawArc(RectF(4.5f, 4.5f, 19.5f, 19.5f), -45f, 275f, false, strokePaint)
                    val arrow = Path().apply {
                        moveTo(15.5f, 4f)
                        lineTo(19.5f, 6.2f)
                        lineTo(16.8f, 9.5f)
                    }
                    canvas.drawPath(arrow, strokePaint)
                }
                Type.DROPLET -> {
                    val p = Path().apply {
                        moveTo(12f, 3.2f)
                        cubicTo(12f, 3.2f, 5.5f, 10.6f, 5.5f, 14.8f)
                        cubicTo(5.5f, 18.6f, 8.4f, 21f, 12f, 21f)
                        cubicTo(15.6f, 21f, 18.5f, 18.6f, 18.5f, 14.8f)
                        cubicTo(18.5f, 10.6f, 12f, 3.2f, 12f, 3.2f)
                        close()
                    }
                    canvas.drawPath(p, strokePaint)
                }
                Type.SUN -> {
                    canvas.drawCircle(12f, 12f, 4.2f, strokePaint)
                    for (i in 0 until 8) {
                        val a = Math.toRadians(i * 45.0)
                        canvas.drawLine(
                            (12f + Math.cos(a) * 6.8f).toFloat(),
                            (12f + Math.sin(a) * 6.8f).toFloat(),
                            (12f + Math.cos(a) * 9.5f).toFloat(),
                            (12f + Math.sin(a) * 9.5f).toFloat(),
                            strokePaint
                        )
                    }
                }
                Type.ERASER -> {
                    val p = Path().apply {
                        moveTo(14.5f, 4.5f)
                        lineTo(20f, 10f)
                        lineTo(10.5f, 19.5f)
                        lineTo(5f, 19.5f)
                        lineTo(3.8f, 18.3f)
                        close()
                    }
                    canvas.drawPath(p, strokePaint)
                    canvas.drawLine(9.5f, 9.5f, 15f, 15f, strokePaint)
                    canvas.drawLine(10.5f, 19.5f, 19.5f, 19.5f, strokePaint)
                }
                Type.FLIP_H -> {
                    strokePaint.strokeWidth = 1.9f
                    canvas.drawLine(12f, 3.8f, 12f, 20.2f, strokePaint)
                    val left = Path().apply {
                        moveTo(9f, 6.5f)
                        lineTo(4f, 17f)
                        lineTo(9f, 17f)
                        close()
                    }
                    val right = Path().apply {
                        moveTo(15f, 6.5f)
                        lineTo(20f, 17f)
                        lineTo(15f, 17f)
                        close()
                    }
                    canvas.drawPath(left, fillPaint)
                    canvas.drawPath(right, strokePaint)
                }
                Type.FLASH -> {
                    val p = Path().apply {
                        moveTo(13.5f, 2.8f)
                        lineTo(5.8f, 13.2f)
                        lineTo(11.6f, 13.2f)
                        lineTo(10.2f, 21.2f)
                        lineTo(18.2f, 10.6f)
                        lineTo(12.4f, 10.6f)
                        close()
                    }
                    if (filled) canvas.drawPath(p, fillPaint) else canvas.drawPath(p, strokePaint)
                }
                Type.LOCK -> {
                    canvas.drawRoundRect(RectF(5.5f, 10.5f, 18.5f, 20.2f), 2.5f, 2.5f, if (filled) fillPaint else strokePaint)
                    val shackle = Path().apply {
                        moveTo(8f, 10.5f)
                        lineTo(8f, 7.5f)
                        cubicTo(8f, 3.8f, 16f, 3.8f, 16f, 7.5f)
                        lineTo(16f, 10.5f)
                    }
                    canvas.drawPath(shackle, strokePaint)
                }
                Type.UNLOCK -> {
                    canvas.drawRoundRect(RectF(5.5f, 10.5f, 18.5f, 20.2f), 2.5f, 2.5f, if (filled) fillPaint else strokePaint)
                    val shackle = Path().apply {
                        moveTo(8f, 10.5f)
                        lineTo(8f, 7.2f)
                        cubicTo(8f, 3.8f, 15.5f, 3.8f, 16f, 6.5f)
                    }
                    canvas.drawPath(shackle, strokePaint)
                }
                Type.EYE_HIDE -> {
                    val p = Path().apply {
                        moveTo(3f, 12f)
                        quadTo(12f, 4.5f, 21f, 12f)
                        quadTo(12f, 19.5f, 3f, 12f)
                    }
                    canvas.drawPath(p, strokePaint)
                    canvas.drawCircle(12f, 12f, 2.5f, strokePaint)
                }
                Type.EXPAND -> {
                    val tl = Path().apply { moveTo(4.5f, 9f); lineTo(4.5f, 4.5f); lineTo(9f, 4.5f) }
                    val tr = Path().apply { moveTo(15f, 4.5f); lineTo(19.5f, 4.5f); lineTo(19.5f, 9f) }
                    val bl = Path().apply { moveTo(4.5f, 15f); lineTo(4.5f, 19.5f); lineTo(9f, 19.5f) }
                    val br = Path().apply { moveTo(15f, 19.5f); lineTo(19.5f, 19.5f); lineTo(19.5f, 15f) }
                    canvas.drawPath(tl, strokePaint)
                    canvas.drawPath(tr, strokePaint)
                    canvas.drawPath(bl, strokePaint)
                    canvas.drawPath(br, strokePaint)
                }
                Type.GRID -> {
                    canvas.drawRoundRect(RectF(4f, 4f, 20f, 20f), 2.5f, 2.5f, strokePaint)
                    canvas.drawLine(9.3f, 4.2f, 9.3f, 19.8f, strokePaint)
                    canvas.drawLine(14.7f, 4.2f, 14.7f, 19.8f, strokePaint)
                    canvas.drawLine(4.2f, 9.3f, 19.8f, 9.3f, strokePaint)
                    canvas.drawLine(4.2f, 14.7f, 19.8f, 14.7f, strokePaint)
                }
                Type.RESET_POS -> {
                    canvas.drawCircle(12f, 12f, 5.5f, strokePaint)
                    canvas.drawCircle(12f, 12f, 1.8f, fillPaint)
                    canvas.drawLine(12f, 3f, 12f, 6.5f, strokePaint)
                    canvas.drawLine(12f, 17.5f, 12f, 21f, strokePaint)
                    canvas.drawLine(3f, 12f, 6.5f, 12f, strokePaint)
                    canvas.drawLine(17.5f, 12f, 21f, 12f, strokePaint)
                }
                Type.COLLECTION, Type.CAT_ALL -> {
                    val r = 2.2f
                    canvas.drawRoundRect(RectF(4f, 4f, 10.5f, 10.5f), r, r, strokePaint)
                    canvas.drawRoundRect(RectF(13.5f, 4f, 20f, 10.5f), r, r, strokePaint)
                    canvas.drawRoundRect(RectF(4f, 13.5f, 10.5f, 20f), r, r, strokePaint)
                    canvas.drawRoundRect(RectF(13.5f, 13.5f, 20f, 20f), r, r, fillPaint)
                }
                Type.ANIME_STAR, Type.CAT_ANIME -> {
                    val star = Path().apply {
                        moveTo(12f, 2.8f); lineTo(14.7f, 8.6f); lineTo(21f, 9.4f)
                        lineTo(16.4f, 13.8f); lineTo(17.6f, 20f); lineTo(12f, 17f)
                        lineTo(6.4f, 20f); lineTo(7.6f, 13.8f); lineTo(3f, 9.4f)
                        lineTo(9.3f, 8.6f); close()
                    }
                    canvas.drawPath(star, strokePaint)
                }
                Type.IMPORT -> {
                    canvas.drawLine(12f, 3.8f, 12f, 15.2f, strokePaint)
                    val arr = Path().apply {
                        moveTo(7.5f, 11.2f); lineTo(12f, 15.8f); lineTo(16.5f, 11.2f)
                    }
                    canvas.drawPath(arr, strokePaint)
                    val tray = Path().apply {
                        moveTo(4.5f, 15.5f); lineTo(4.5f, 19.5f); lineTo(19.5f, 19.5f); lineTo(19.5f, 15.5f)
                    }
                    canvas.drawPath(tray, strokePaint)
                }
                Type.EXPORT_ZIP -> {
                    canvas.drawLine(12f, 15.2f, 12f, 3.8f, strokePaint)
                    val arr = Path().apply {
                        moveTo(7.5f, 8.2f); lineTo(12f, 3.6f); lineTo(16.5f, 8.2f)
                    }
                    canvas.drawPath(arr, strokePaint)
                    val tray = Path().apply {
                        moveTo(4.5f, 15.5f); lineTo(4.5f, 19.5f); lineTo(19.5f, 19.5f); lineTo(19.5f, 15.5f)
                    }
                    canvas.drawPath(tray, strokePaint)
                }
                Type.LINK -> {
                    canvas.drawArc(RectF(3.5f, 7.5f, 12.5f, 16.5f), 60f, 240f, false, strokePaint)
                    canvas.drawArc(RectF(11.5f, 7.5f, 20.5f, 16.5f), -120f, 240f, false, strokePaint)
                    canvas.drawLine(8.5f, 12f, 15.5f, 12f, strokePaint)
                }
                Type.EDIT -> {
                    val p = Path().apply {
                        moveTo(14.5f, 4.5f); lineTo(19.5f, 9.5f); lineTo(9f, 20f)
                        lineTo(4f, 20f); lineTo(4f, 15f); close()
                    }
                    canvas.drawPath(p, strokePaint)
                    canvas.drawLine(12.5f, 6.5f, 17.5f, 11.5f, strokePaint)
                }
                Type.SHARE -> {
                    canvas.drawCircle(17.5f, 5.5f, 2.5f, strokePaint)
                    canvas.drawCircle(6.5f, 12f, 2.5f, strokePaint)
                    canvas.drawCircle(17.5f, 18.5f, 2.5f, strokePaint)
                    canvas.drawLine(8.8f, 10.8f, 15.2f, 6.8f, strokePaint)
                    canvas.drawLine(8.8f, 13.2f, 15.2f, 17.2f, strokePaint)
                }
                Type.TRASH -> {
                    canvas.drawLine(4.5f, 6.8f, 19.5f, 6.8f, strokePaint)
                    canvas.drawLine(9.5f, 4f, 14.5f, 4f, strokePaint)
                    val bin = Path().apply {
                        moveTo(6.5f, 6.8f); lineTo(7.5f, 19.8f); lineTo(16.5f, 19.8f); lineTo(17.5f, 6.8f)
                    }
                    canvas.drawPath(bin, strokePaint)
                    canvas.drawLine(10f, 10.5f, 10f, 16.5f, strokePaint)
                    canvas.drawLine(14f, 10.5f, 14f, 16.5f, strokePaint)
                }
                Type.COMPASS -> {
                    canvas.drawCircle(12f, 12f, 8.8f, strokePaint)
                    val needle = Path().apply {
                        moveTo(15.6f, 8.4f); lineTo(13.2f, 13.2f); lineTo(8.4f, 15.6f); lineTo(10.8f, 10.8f); close()
                    }
                    canvas.drawPath(needle, fillPaint)
                }
                Type.COIN -> {
                    fillPaint.shader = LinearGradient(
                        3f, 3f, 21f, 21f,
                        Color.parseColor("#FDE047"),
                        Color.parseColor("#D97706"),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawCircle(12f, 12f, 9.2f, fillPaint)
                    fillPaint.shader = null
                    strokePaint.color = Color.parseColor("#FEF9C3")
                    strokePaint.strokeWidth = 1.6f
                    canvas.drawCircle(12f, 12f, 6.6f, strokePaint)
                    fillPaint.color = Color.parseColor("#92400E")
                    val diamond = Path().apply {
                        moveTo(12f, 7.6f); lineTo(15.2f, 12f); lineTo(12f, 16.4f); lineTo(8.8f, 12f); close()
                    }
                    canvas.drawPath(diamond, fillPaint)
                }
                Type.CROWN -> {
                    val p = Path().apply {
                        moveTo(3.5f, 17.5f)
                        lineTo(4.8f, 7.5f)
                        lineTo(9f, 12f)
                        lineTo(12f, 5.5f)
                        lineTo(15f, 12f)
                        lineTo(19.2f, 7.5f)
                        lineTo(20.5f, 17.5f)
                        close()
                    }
                    canvas.drawPath(p, if (filled) fillPaint else strokePaint)
                    canvas.drawLine(4.5f, 20f, 19.5f, 20f, strokePaint)
                }
                Type.TIMER -> {
                    canvas.drawCircle(12f, 13f, 7.8f, strokePaint)
                    canvas.drawLine(9.5f, 3.2f, 14.5f, 3.2f, strokePaint)
                    canvas.drawLine(12f, 13f, 12f, 8.8f, strokePaint)
                    canvas.drawLine(12f, 13f, 15.2f, 14.8f, strokePaint)
                }
                Type.SHIELD, Type.SHIELD_LOCK, Type.CHILD_SHIELD -> {
                    val sh = Path().apply {
                        moveTo(12f, 3f)
                        lineTo(19.5f, 6.2f)
                        lineTo(19.5f, 12.2f)
                        cubicTo(19.5f, 16.8f, 15.8f, 19.8f, 12f, 21f)
                        cubicTo(8.2f, 19.8f, 4.5f, 16.8f, 4.5f, 12.2f)
                        lineTo(4.5f, 6.2f)
                        close()
                    }
                    canvas.drawPath(sh, strokePaint)
                    if (type == Type.SHIELD_LOCK) {
                        canvas.drawRoundRect(RectF(9.2f, 11.2f, 14.8f, 15.8f), 1.2f, 1.2f, fillPaint)
                        canvas.drawArc(RectF(10f, 8.2f, 14f, 12.2f), 180f, 180f, false, strokePaint)
                    } else if (type == Type.CHILD_SHIELD) {
                        canvas.drawCircle(12f, 10.2f, 2f, fillPaint)
                        canvas.drawArc(RectF(8.5f, 12.5f, 15.5f, 17.5f), 180f, 180f, false, strokePaint)
                    } else {
                        val chk = Path().apply { moveTo(8.8f, 12.2f); lineTo(11.2f, 14.6f); lineTo(15.6f, 9.6f) }
                        canvas.drawPath(chk, strokePaint)
                    }
                }
                Type.MOON -> {
                    val m = Path().apply {
                        moveTo(14.5f, 4.2f)
                        cubicTo(9.2f, 4.8f, 5.5f, 9.4f, 6.2f, 14.6f)
                        cubicTo(6.9f, 19.4f, 11.8f, 21.8f, 16.6f, 19.8f)
                        cubicTo(17.8f, 19.3f, 18.8f, 18.5f, 19.5f, 17.5f)
                        cubicTo(14.8f, 18.2f, 11f, 14.5f, 11.5f, 9.8f)
                        cubicTo(11.8f, 7.2f, 13f, 5.4f, 14.5f, 4.2f)
                        close()
                    }
                    canvas.drawPath(m, if (filled) fillPaint else strokePaint)
                }
                Type.INFO -> {
                    canvas.drawCircle(12f, 12f, 8.8f, strokePaint)
                    canvas.drawLine(12f, 11f, 12f, 16.5f, strokePaint)
                    canvas.drawCircle(12f, 7.8f, 1.1f, fillPaint)
                }
                Type.STAR -> {
                    val star = Path().apply {
                        moveTo(12f, 3f); lineTo(14.8f, 8.8f); lineTo(21.2f, 9.6f)
                        lineTo(16.5f, 14f); lineTo(17.7f, 20.4f); lineTo(12f, 17.3f)
                        lineTo(6.3f, 20.4f); lineTo(7.5f, 14f); lineTo(2.8f, 9.6f)
                        lineTo(9.2f, 8.8f); close()
                    }
                    canvas.drawPath(star, if (filled) fillPaint else strokePaint)
                }
                Type.MAIL -> {
                    canvas.drawRoundRect(RectF(3.5f, 5.5f, 20.5f, 18.5f), 2.5f, 2.5f, strokePaint)
                    val flap = Path().apply {
                        moveTo(4.2f, 6.8f); lineTo(12f, 13f); lineTo(19.8f, 6.8f)
                    }
                    canvas.drawPath(flap, strokePaint)
                }
                Type.COPY -> {
                    canvas.drawRoundRect(RectF(8.5f, 8.5f, 19.5f, 19.5f), 2.2f, 2.2f, strokePaint)
                    val back = Path().apply {
                        moveTo(15.5f, 8.5f); lineTo(15.5f, 5.8f)
                        quadTo(15.5f, 4.5f, 14.2f, 4.5f)
                        lineTo(5.8f, 4.5f)
                        quadTo(4.5f, 4.5f, 4.5f, 5.8f)
                        lineTo(4.5f, 14.2f)
                        quadTo(4.5f, 15.5f, 5.8f, 15.5f)
                        lineTo(8.5f, 15.5f)
                    }
                    canvas.drawPath(back, strokePaint)
                }
                Type.CAT_CAR -> {
                    val body = Path().apply {
                        moveTo(4f, 13f); lineTo(6.2f, 7.5f); lineTo(17.8f, 7.5f); lineTo(20f, 13f)
                        lineTo(20.5f, 17f); lineTo(3.5f, 17f); close()
                    }
                    canvas.drawPath(body, strokePaint)
                    canvas.drawCircle(7.2f, 17.5f, 1.8f, fillPaint)
                    canvas.drawCircle(16.8f, 17.5f, 1.8f, fillPaint)
                }
                Type.CAT_FOOD -> {
                    canvas.drawArc(RectF(4.5f, 6f, 19.5f, 15f), 180f, 180f, true, strokePaint)
                    canvas.drawLine(4.5f, 13.5f, 19.5f, 13.5f, strokePaint)
                    canvas.drawRoundRect(RectF(4.5f, 15.8f, 19.5f, 19.2f), 1.6f, 1.6f, strokePaint)
                }
                Type.CAT_FLOWER -> {
                    canvas.drawCircle(12f, 12f, 2.3f, fillPaint)
                    for (i in 0 until 6) {
                        val a = Math.toRadians(i * 60.0)
                        val px = (12f + Math.cos(a) * 5.2f).toFloat()
                        val py = (12f + Math.sin(a) * 5.2f).toFloat()
                        canvas.drawCircle(px, py, 2.6f, strokePaint)
                    }
                }
                Type.CAT_SCENERY -> {
                    canvas.drawCircle(16.8f, 7.2f, 2.1f, strokePaint)
                    val peaks = Path().apply {
                        moveTo(3.5f, 18.5f); lineTo(9.2f, 9.5f); lineTo(13.5f, 15f)
                        lineTo(16.5f, 11.8f); lineTo(20.5f, 18.5f); close()
                    }
                    canvas.drawPath(peaks, strokePaint)
                }
                Type.CAT_ANIMALS -> {
                    canvas.drawCircle(12f, 14.5f, 4.2f, strokePaint)
                    canvas.drawCircle(7f, 9.5f, 1.5f, fillPaint)
                    canvas.drawCircle(10.2f, 6.8f, 1.5f, fillPaint)
                    canvas.drawCircle(13.8f, 6.8f, 1.5f, fillPaint)
                    canvas.drawCircle(17f, 9.5f, 1.5f, fillPaint)
                }
                Type.TROPHY -> {
                    val cup = Path().apply {
                        moveTo(7f, 4.5f); lineTo(17f, 4.5f); lineTo(16f, 12f)
                        cubicTo(15.5f, 15f, 8.5f, 15f, 8f, 12f); close()
                    }
                    canvas.drawPath(cup, if (filled) fillPaint else strokePaint)
                    canvas.drawArc(RectF(4f, 5.5f, 8f, 10.5f), 90f, 180f, false, strokePaint)
                    canvas.drawArc(RectF(16f, 5.5f, 20f, 10.5f), -90f, 180f, false, strokePaint)
                    canvas.drawLine(12f, 14.5f, 12f, 18.8f, strokePaint)
                    canvas.drawLine(8.2f, 19.2f, 15.8f, 19.2f, strokePaint)
                }
            }
            canvas.restore()
        }

        override fun setAlpha(alpha: Int) {
            strokePaint.alpha = alpha
            fillPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            strokePaint.colorFilter = colorFilter
            fillPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    // ---------------- CUSTOM PROGRAMMATIC HERO ARCHITECTURAL BACKDROP ----------------
    class HeroStudioBackdropView(context: Context) : View(context) {
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#18FFFFFF")
            strokeWidth = 1.5f
        }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#24FFFFFF")
            strokeWidth = 2f
        }
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val reticlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = Color.parseColor("#44FDE047")
            strokeWidth = 3.5f
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            // Soft radial ambient studio glow top-right
            glowPaint.shader = RadialGradient(
                w * 0.85f, h * 0.22f, maxOf(w, h) * 0.55f,
                Color.parseColor("#44EC4899"), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(w * 0.85f, h * 0.22f, maxOf(w, h) * 0.55f, glowPaint)

            // Subtle isometric drafting grid lines
            val step = 36f
            var x = 0f
            while (x < w) {
                canvas.drawLine(x, 0f, x, h, gridPaint)
                x += step
            }
            var y = 0f
            while (y < h) {
                canvas.drawLine(0f, y, w, y, gridPaint)
                y += step
            }

            // Concentric AR tracking rings in top-right
            val rx = w * 0.86f
            val ry = h * 0.25f
            canvas.drawCircle(rx, ry, h * 0.22f, ringPaint)
            canvas.drawCircle(rx, ry, h * 0.36f, ringPaint)

            // Precision viewfinder target brackets
            val bx = w * 0.86f
            val by = h * 0.25f
            val s = h * 0.11f
            val c = s * 0.38f
            canvas.drawLine(bx - s, by - s + c, bx - s, by - s, reticlePaint)
            canvas.drawLine(bx - s, by - s, bx - s + c, by - s, reticlePaint)
            canvas.drawLine(bx + s - c, by - s, bx + s, by - s, reticlePaint)
            canvas.drawLine(bx + s, by - s, bx + s, by - s + c, reticlePaint)
            canvas.drawLine(bx - s, by + s - c, bx - s, by + s, reticlePaint)
            canvas.drawLine(bx - s, by + s, bx - s + c, by + s, reticlePaint)
            canvas.drawLine(bx + s - c, by + s, bx + s, by + s, reticlePaint)
            canvas.drawLine(bx + s, by + s - c, bx + s, by + s, reticlePaint)
        }
    }

    // ---------------- PROGRAMMATIC ANIMATED SPLASH EMBLEM ----------------
    class BrandSplashView(context: Context) : View(context) {
        private var pulse = 0f
        private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                pulse = it.animatedValue as Float
                invalidate()
            }
        }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val logo = BrandLogoDrawable(256)

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            animator.start()
        }

        override fun onDetachedFromWindow() {
            animator.cancel()
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val s = minOf(w, h)
            val cx = w / 2f
            val cy = h / 2f

            // Two expanding subtle AR pulse rings
            for (i in 0..1) {
                val ph = (pulse + i * 0.5f) % 1f
                val r = s * (0.28f + ph * 0.21f)
                val alpha = ((1f - ph) * 110).toInt().coerceIn(0, 255)
                ringPaint.color = Color.argb(alpha, 139, 92, 246)
                ringPaint.strokeWidth = s * 0.018f
                canvas.drawCircle(cx, cy, r, ringPaint)
            }

            // Center crisp programmatic Brand Logo
            val logoHalf = (s * 0.30f).toInt()
            logo.setBounds(
                (cx - logoHalf).toInt(),
                (cy - logoHalf).toInt(),
                (cx + logoHalf).toInt(),
                (cy + logoHalf).toInt()
            )
            logo.draw(canvas)
        }
    }

    // ---------------- PROGRAMMATIC GRADIENT PROGRESS BAR ----------------
    class StudioProgressView(context: Context) : View(context) {
        var progress: Int = 0
            set(value) {
                field = value.coerceIn(0, 100)
                invalidate()
            }
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val r = h / 2f
            trackPaint.color = if (UiTheme.isDark) Color.parseColor("#1E293B") else Color.parseColor("#DDD6FE")
            canvas.drawRoundRect(RectF(0f, 0f, w, h), r, r, trackPaint)

            val fw = (w * (progress / 100f)).coerceAtLeast(h)
            fillPaint.shader = LinearGradient(
                0f, 0f, fw, 0f,
                Color.parseColor("#8B5CF6"),
                Color.parseColor("#EC4899"),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(RectF(0f, 0f, fw, h), r, r, fillPaint)
        }
    }

    // ---------------- PROGRAMMATIC CELEBRATION ILLUSTRATION (NO EMOJI) ----------------
    class CelebrationArtView(context: Context) : View(context) {
        private var phase = 0f
        private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2800L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
        }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val trophy = VectorIconDrawable(Type.TROPHY, 120, Color.WHITE, filled = true, secondaryTint = null)

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            anim.start()
        }

        override fun onDetachedFromWindow() {
            anim.cancel()
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val s = minOf(w, h)

            // Radiating subtle gold glow
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(cx, cy, s * 0.48f, Color.parseColor("#55FBBF24"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, s * 0.48f, paint)
            paint.shader = null

            // Orbiting geometric studio confetti shapes (lines & diamonds, zero emojis!)
            val colors = intArrayOf(
                Color.parseColor("#8B5CF6"),
                Color.parseColor("#EC4899"),
                Color.parseColor("#10B981"),
                Color.parseColor("#38BDF8"),
                Color.parseColor("#F59E0B")
            )
            for (i in 0 until 10) {
                val deg = i * 36f + phase * 36f
                val rad = Math.toRadians(deg.toDouble())
                val dist = s * (0.37f + (i % 2) * 0.06f)
                val px = (cx + Math.cos(rad) * dist).toFloat()
                val py = (cy + Math.sin(rad) * dist).toFloat()
                paint.color = colors[i % colors.size]
                if (i % 2 == 0) {
                    canvas.drawCircle(px, py, s * 0.025f, paint)
                } else {
                    canvas.save()
                    canvas.rotate(deg, px, py)
                    canvas.drawRoundRect(
                        RectF(px - s * 0.03f, py - s * 0.014f, px + s * 0.03f, py + s * 0.014f),
                        4f, 4f, paint
                    )
                    canvas.restore()
                }
            }

            // Inner gold medal circle
            paint.shader = LinearGradient(
                cx - s * 0.25f, cy - s * 0.25f, cx + s * 0.25f, cy + s * 0.25f,
                Color.parseColor("#FDE047"), Color.parseColor("#F59E0B"), Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, s * 0.26f, paint)
            paint.shader = null

            val iconR = (s * 0.145f).toInt()
            trophy.setBounds((cx - iconR).toInt(), (cy - iconR).toInt(), (cx + iconR).toInt(), (cy + iconR).toInt())
            trophy.draw(canvas)
        }
    }

    // ---------------- PROGRAMMATIC EMPTY GALLERY ARTWORK ----------------
    class EmptyGalleryArtView(context: Context) : View(context) {
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val s = minOf(w, h)
            val cx = w / 2f
            val cy = h / 2f

            fillPaint.color = UiTheme.accentSoft
            canvas.drawCircle(cx, cy, s * 0.44f, fillPaint)

            // Tilted back card
            canvas.save()
            canvas.rotate(-9f, cx, cy)
            fillPaint.color = UiTheme.surface
            strokePaint.color = UiTheme.strokeStrong
            strokePaint.strokeWidth = s * 0.028f
            val rBack = RectF(cx - s * 0.24f, cy - s * 0.24f, cx + s * 0.20f, cy + s * 0.22f)
            canvas.drawRoundRect(rBack, s * 0.06f, s * 0.06f, fillPaint)
            canvas.drawRoundRect(rBack, s * 0.06f, s * 0.06f, strokePaint)
            canvas.restore()

            // Front sketch card
            canvas.save()
            canvas.rotate(5f, cx, cy)
            fillPaint.color = UiTheme.surface
            strokePaint.color = UiTheme.accent
            strokePaint.strokeWidth = s * 0.032f
            val rFront = RectF(cx - s * 0.21f, cy - s * 0.20f, cx + s * 0.25f, cy + s * 0.24f)
            canvas.drawRoundRect(rFront, s * 0.06f, s * 0.06f, fillPaint)
            canvas.drawRoundRect(rFront, s * 0.06f, s * 0.06f, strokePaint)

            // Sketch mountains inside
            val m = Path().apply {
                moveTo(rFront.left + s * 0.06f, rFront.bottom - s * 0.09f)
                lineTo(rFront.left + s * 0.17f, rFront.centerY() - s * 0.02f)
                lineTo(rFront.centerX() + s * 0.04f, rFront.bottom - s * 0.14f)
                lineTo(rFront.right - s * 0.11f, rFront.centerY() + s * 0.01f)
                lineTo(rFront.right - s * 0.06f, rFront.bottom - s * 0.09f)
            }
            strokePaint.color = UiTheme.pinkAccent
            strokePaint.strokeWidth = s * 0.026f
            canvas.drawPath(m, strokePaint)
            fillPaint.color = UiTheme.amberAccent
            canvas.drawCircle(rFront.right - s * 0.12f, rFront.top + s * 0.11f, s * 0.035f, fillPaint)
            canvas.restore()
        }
    }

    // ---------------- BESPOKE FLAGSHIP AR PROJECTION ARTWORK FOR HERO ----------------
    class HeroProjectionArtView(context: Context) : View(context) {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val s = minOf(w, h)
            val cx = w * 0.54f
            val cy = h * 0.5f

            // Ambient cyan/magenta aura
            fillPaint.shader = RadialGradient(
                cx, cy, s * 0.52f,
                Color.parseColor("#4438BDF8"), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, s * 0.52f, fillPaint)
            fillPaint.shader = null

            // Tilted translucent paper plane
            canvas.save()
            canvas.rotate(-10f, cx, cy)
            val sheet = RectF(cx - s * 0.30f, cy - s * 0.36f, cx + s * 0.30f, cy + s * 0.36f)
            fillPaint.shader = LinearGradient(
                sheet.left, sheet.top, sheet.right, sheet.bottom,
                Color.parseColor("#2AFFFFFF"), Color.parseColor("#10FFFFFF"), Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(sheet, s * 0.08f, s * 0.08f, fillPaint)
            fillPaint.shader = null
            strokePaint.color = Color.parseColor("#66FFFFFF")
            strokePaint.strokeWidth = s * 0.022f
            canvas.drawRoundRect(sheet, s * 0.08f, s * 0.08f, strokePaint)

            // Precision drawing grid lines inside paper
            strokePaint.color = Color.parseColor("#28FFFFFF")
            strokePaint.strokeWidth = s * 0.012f
            canvas.drawLine(sheet.left + sheet.width() * 0.33f, sheet.top, sheet.left + sheet.width() * 0.33f, sheet.bottom, strokePaint)
            canvas.drawLine(sheet.left + sheet.width() * 0.66f, sheet.top, sheet.left + sheet.width() * 0.66f, sheet.bottom, strokePaint)
            canvas.drawLine(sheet.left, sheet.top + sheet.height() * 0.5f, sheet.right, sheet.top + sheet.height() * 0.5f, strokePaint)

            // Artistic flowing vector bezier stroke on the sheet
            val artStroke = Path().apply {
                moveTo(sheet.left + sheet.width() * 0.18f, sheet.bottom - sheet.height() * 0.22f)
                cubicTo(
                    sheet.left + sheet.width() * 0.24f, sheet.top + sheet.height() * 0.22f,
                    sheet.right - sheet.width() * 0.25f, sheet.top + sheet.height() * 0.20f,
                    sheet.right - sheet.width() * 0.16f, sheet.bottom - sheet.height() * 0.30f
                )
                quadTo(
                    sheet.centerX(), sheet.bottom - sheet.height() * 0.12f,
                    sheet.left + sheet.width() * 0.36f, sheet.centerY()
                )
            }
            strokePaint.color = Color.parseColor("#FDE047")
            strokePaint.strokeWidth = s * 0.032f
            canvas.drawPath(artStroke, strokePaint)

            // AR tracking anchor nodes on paper corners
            fillPaint.color = Color.parseColor("#34D399")
            canvas.drawCircle(sheet.left + s * 0.05f, sheet.top + s * 0.05f, s * 0.026f, fillPaint)
            canvas.drawCircle(sheet.right - s * 0.05f, sheet.top + s * 0.05f, s * 0.026f, fillPaint)
            canvas.drawCircle(sheet.left + s * 0.05f, sheet.bottom - s * 0.05f, s * 0.026f, fillPaint)
            canvas.drawCircle(sheet.right - s * 0.05f, sheet.bottom - s * 0.05f, s * 0.026f, fillPaint)
            canvas.restore()

            // Floating Stylus Nib on lower-right
            canvas.save()
            canvas.translate(cx + s * 0.18f, cy + s * 0.14f)
            canvas.rotate(32f)
            val stylus = Path().apply {
                moveTo(0f, -s * 0.24f)
                lineTo(s * 0.055f, 0f)
                lineTo(0f, s * 0.11f)
                lineTo(-s * 0.055f, 0f)
                close()
            }
            fillPaint.shader = LinearGradient(
                0f, -s * 0.24f, 0f, s * 0.11f,
                Color.WHITE, Color.parseColor("#C4B5FD"), Shader.TileMode.CLAMP
            )
            canvas.drawPath(stylus, fillPaint)
            fillPaint.shader = null
            canvas.restore()
        }
    }

    // ---------------- GEOMETRIC ANIME WORLD CARD CREST WATERMARK ----------------
    class AnimeWorldCrestView(
        context: Context,
        private val hex1: String,
        private val hex2: String,
        private val variant: Int
    ) : View(context) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val f = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val s = minOf(w, h)
            val cx = w * 0.72f
            val cy = h * 0.36f

            f.color = Color.parseColor(hex1)
            f.alpha = if (UiTheme.isDark) 34 else 22
            canvas.drawCircle(cx, cy, s * 0.42f, f)

            p.color = Color.parseColor(hex2)
            p.alpha = if (UiTheme.isDark) 75 else 50
            p.strokeWidth = s * 0.045f
            p.strokeCap = Paint.Cap.ROUND
            when (variant % 3) {
                0 -> {
                    canvas.save()
                    canvas.rotate(18f, cx, cy)
                    canvas.drawRoundRect(
                        RectF(cx - s * 0.24f, cy - s * 0.24f, cx + s * 0.24f, cy + s * 0.24f),
                        s * 0.06f, s * 0.06f, p
                    )
                    canvas.restore()
                }
                1 -> {
                    val tri = Path().apply {
                        moveTo(cx, cy - s * 0.28f)
                        lineTo(cx + s * 0.26f, cy + s * 0.20f)
                        lineTo(cx - s * 0.26f, cy + s * 0.20f)
                        close()
                    }
                    canvas.drawPath(tri, p)
                }
                else -> {
                    canvas.drawCircle(cx, cy, s * 0.25f, p)
                    canvas.drawLine(cx - s * 0.32f, cy, cx + s * 0.32f, cy, p)
                }
            }
        }
    }

    // Helper: Build a crisp Button-like Pill container with programmatic Vector Icon + Text
    fun iconPill(
        context: Context,
        iconType: Type,
        label: String,
        textColor: Int,
        iconColor: Int = textColor,
        iconSizeDp: Int = 18,
        textSizeSp: Float = 14f,
        trailingIcon: Type? = null,
        filled: Boolean = false
    ): LinearLayout {
        val d = context.resources.displayMetrics.density
        val dp = { v: Int -> (v * d).toInt() }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setPadding(dp(12), dp(8), dp(12), dp(8))
            addView(
                imageView(context, iconType, iconSizeDp, iconColor, filled),
                LinearLayout.LayoutParams(dp(iconSizeDp), dp(iconSizeDp)).apply {
                    if (label.isNotEmpty()) marginEnd = dp(7)
                }
            )
            if (label.isNotEmpty()) {
                addView(android.widget.TextView(context).apply {
                    text = label
                    textSize = textSizeSp
                    UiTheme.applyUiFont(this, bold = true)
                    setTextColor(textColor)
                    maxLines = 1
                })
            }
            if (trailingIcon != null) {
                addView(
                    imageView(context, trailingIcon, iconSizeDp - 2, iconColor, filled),
                    LinearLayout.LayoutParams(dp(iconSizeDp - 2), dp(iconSizeDp - 2)).apply {
                        marginStart = dp(6)
                    }
                )
            }
        }
    }
}
