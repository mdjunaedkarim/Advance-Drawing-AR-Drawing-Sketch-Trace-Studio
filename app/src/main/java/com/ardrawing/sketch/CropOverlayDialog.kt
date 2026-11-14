package com.ardrawing.sketch

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.Window
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

object CropOverlayDialog {

    private enum class DragMode { NONE, MOVE, TL, TR, BL, BR, L, T, R, B }

    fun show(act: Activity, src: Bitmap, onCropped: (Bitmap) -> Unit) {
        val dp = { v: Int -> UiTheme.dp(act, v) }
        val dialog = Dialog(act, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        var workingBmp = src
        var aspectRatio: Float? = null

        val root = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0F19"))
            setPadding(dp(14), dp(32), dp(14), dp(18))
        }

        // Top Bar
        val topBar = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        val cancelBtn = Button(act).apply {
            text = "✕"
            textSize = 16f
            UiTheme.styleSoftBtn(act, this, 14, Color.parseColor("#1E293B"), Color.parseColor("#334155"), Color.WHITE)
            UiTheme.bounce(this) { dialog.dismiss() }
        }
        val titleTv = TextView(act).apply {
            text = L.t("crop_resize")
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val rotateBtn = Button(act).apply {
            text = "↻ 90°"
            textSize = 13f
            UiTheme.styleSoftBtn(act, this, 14, Color.parseColor("#1E293B"), Color.parseColor("#334155"), Color.WHITE)
        }
        topBar.addView(cancelBtn, LinearLayout.LayoutParams(dp(44), dp(42)))
        topBar.addView(titleTv, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        topBar.addView(rotateBtn, LinearLayout.LayoutParams(WRAP_CONTENT, dp(42)))
        root.addView(topBar)

        val sizeHintTv = TextView(act).apply {
            text = "${workingBmp.width} × ${workingBmp.height} px"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(sizeHintTv)

        val cropView = CropCanvasView(act) { w, h ->
            sizeHintTv.text = "$w × $h px"
        }.apply {
            setBitmap(workingBmp)
        }
        root.addView(cropView, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        UiTheme.bounce(rotateBtn) {
            val m = Matrix().apply { postRotate(90f) }
            workingBmp = Bitmap.createBitmap(workingBmp, 0, 0, workingBmp.width, workingBmp.height, m, true)
            cropView.setBitmap(workingBmp)
        }

        // Aspect Ratio Chips
        val ratioRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        val ratios = listOf(
            L.t("free_crop") to null,
            "1:1" to 1f,
            "3:4" to 0.75f,
            "4:3" to 1.3333f,
            "9:16" to 0.5625f
        )
        val chipViews = ArrayList<TextView>()
        fun updateChips() {
            ratios.forEachIndexed { idx, (_, r) ->
                val active = aspectRatio == r
                chipViews[idx].apply {
                    setTextColor(if (active) Color.WHITE else Color.parseColor("#CBD5E1"))
                    background = if (active) {
                        UiTheme.grad(act, "#8B5CF6", "#6D28D9", 14)
                    } else {
                        UiTheme.round(act, Color.parseColor("#1E293B"), 14, Color.parseColor("#334155"))
                    }
                }
            }
        }
        ratios.forEach { (label, r) ->
            val chip = TextView(act).apply {
                text = label
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(16), dp(8), dp(16), dp(8))
                UiTheme.bounce(this) {
                    aspectRatio = r
                    cropView.setAspectRatio(r)
                    updateChips()
                }
            }
            chipViews.add(chip)
            ratioRow.addView(chip, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) })
        }
        updateChips()
        root.addView(
            HorizontalScrollView(act).apply {
                isHorizontalScrollBarEnabled = false
                addView(ratioRow)
            },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12); bottomMargin = dp(14) }
        )

        // Bottom Action Row
        val bottomRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        val resetBtn = Button(act).apply {
            text = L.t("reset_crop")
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            UiTheme.styleSoftBtn(act, this, 16, Color.parseColor("#1E293B"), Color.parseColor("#475569"), Color.WHITE)
            UiTheme.bounce(this) {
                aspectRatio = null
                cropView.setAspectRatio(null)
                cropView.resetCrop()
                updateChips()
            }
        }
        val applyBtn = Button(act).apply {
            text = "✓  ${L.t("apply_crop")}"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            UiTheme.styleGradientBtn(act, this, "#10B981", "#059669", 16)
            UiTheme.bounce(this) {
                val cropped = cropView.cropResult()
                dialog.dismiss()
                onCropped(cropped)
            }
        }
        bottomRow.addView(resetBtn, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(10) })
        bottomRow.addView(applyBtn, LinearLayout.LayoutParams(0, dp(50), 2f))
        root.addView(bottomRow)

        dialog.setContentView(FrameLayout(act).apply {
            addView(root, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        })
        dialog.show()
    }

    @SuppressLint("ViewConstructor")
    private class CropCanvasView(
        context: Context,
        private val onSizePreview: (Int, Int) -> Unit
    ) : View(context) {

        private var bmp: Bitmap? = null
        private val imgRect = RectF()
        private val cropRect = RectF()
        private var targetRatio: Float? = null
        private var dragMode = DragMode.NONE
        private var lastX = 0f
        private var lastY = 0f

        private val scrimPaint = Paint().apply { color = Color.parseColor("#AA000000") }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.WHITE
        }
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = Color.parseColor("#66FFFFFF")
        }
        private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 9f
            strokeCap = Paint.Cap.ROUND
            color = Color.parseColor("#A78BFA")
        }

        fun setBitmap(b: Bitmap) {
            bmp = b
            layoutImageBounds()
            invalidate()
        }

        fun setAspectRatio(ratio: Float?) {
            targetRatio = ratio
            if (ratio != null && !imgRect.isEmpty) {
                val cx = cropRect.centerX()
                val cy = cropRect.centerY()
                var w = cropRect.width()
                var h = w / ratio
                if (h > imgRect.height()) {
                    h = imgRect.height()
                    w = h * ratio
                }
                if (w > imgRect.width()) {
                    w = imgRect.width()
                    h = w / ratio
                }
                cropRect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
                clampMove()
                notifyPixelSize()
                invalidate()
            }
        }

        fun resetCrop() {
            if (!imgRect.isEmpty) {
                cropRect.set(imgRect)
                notifyPixelSize()
                invalidate()
            }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            layoutImageBounds()
        }

        private fun layoutImageBounds() {
            val b = bmp ?: return
            if (width <= 0 || height <= 0) return
            val pad = 36f
            val availW = (width - pad * 2).coerceAtLeast(10f)
            val availH = (height - pad * 2).coerceAtLeast(10f)
            val scale = minOf(availW / b.width, availH / b.height)
            val dw = b.width * scale
            val dh = b.height * scale
            val left = (width - dw) / 2f
            val top = (height - dh) / 2f
            imgRect.set(left, top, left + dw, top + dh)
            cropRect.set(imgRect)
            targetRatio?.let { setAspectRatio(it) }
            notifyPixelSize()
        }

        private fun notifyPixelSize() {
            val b = bmp ?: return
            if (imgRect.width() <= 0f || imgRect.height() <= 0f) return
            val pw = ((cropRect.width() / imgRect.width()) * b.width).toInt().coerceIn(1, b.width)
            val ph = ((cropRect.height() / imgRect.height()) * b.height).toInt().coerceIn(1, b.height)
            onSizePreview(pw, ph)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val b = bmp ?: return
            canvas.drawBitmap(b, null, imgRect, null)

            // Dim outside cropRect
            canvas.drawRect(imgRect.left, imgRect.top, imgRect.right, cropRect.top, scrimPaint)
            canvas.drawRect(imgRect.left, cropRect.bottom, imgRect.right, imgRect.bottom, scrimPaint)
            canvas.drawRect(imgRect.left, cropRect.top, cropRect.left, cropRect.bottom, scrimPaint)
            canvas.drawRect(cropRect.right, cropRect.top, imgRect.right, cropRect.bottom, scrimPaint)

            // Rule of thirds grid
            val w3 = cropRect.width() / 3f
            val h3 = cropRect.height() / 3f
            for (i in 1..2) {
                canvas.drawLine(cropRect.left + w3 * i, cropRect.top, cropRect.left + w3 * i, cropRect.bottom, gridPaint)
                canvas.drawLine(cropRect.left, cropRect.top + h3 * i, cropRect.right, cropRect.top + h3 * i, gridPaint)
            }

            canvas.drawRect(cropRect, borderPaint)

            // Corner L-handles
            val len = minOf(36f, cropRect.width() * 0.25f, cropRect.height() * 0.25f)
            // TL
            canvas.drawLine(cropRect.left, cropRect.top, cropRect.left + len, cropRect.top, handlePaint)
            canvas.drawLine(cropRect.left, cropRect.top, cropRect.left, cropRect.top + len, handlePaint)
            // TR
            canvas.drawLine(cropRect.right - len, cropRect.top, cropRect.right, cropRect.top, handlePaint)
            canvas.drawLine(cropRect.right, cropRect.top, cropRect.right, cropRect.top + len, handlePaint)
            // BL
            canvas.drawLine(cropRect.left, cropRect.bottom - len, cropRect.left, cropRect.bottom, handlePaint)
            canvas.drawLine(cropRect.left, cropRect.bottom, cropRect.left + len, cropRect.bottom, handlePaint)
            // BR
            canvas.drawLine(cropRect.right - len, cropRect.bottom, cropRect.right, cropRect.bottom, handlePaint)
            canvas.drawLine(cropRect.right, cropRect.bottom - len, cropRect.right, cropRect.bottom, handlePaint)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.x
            val y = event.y
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragMode = hitTest(x, y)
                    lastX = x
                    lastY = y
                    return dragMode != DragMode.NONE
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = x - lastX
                    val dy = y - lastY
                    lastX = x
                    lastY = y
                    applyDrag(dx, dy)
                    notifyPixelSize()
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragMode = DragMode.NONE
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun hitTest(x: Float, y: Float): DragMode {
            val tol = 56f
            val nearL = abs(x - cropRect.left) < tol
            val nearR = abs(x - cropRect.right) < tol
            val nearT = abs(y - cropRect.top) < tol
            val nearB = abs(y - cropRect.bottom) < tol
            if (nearL && nearT) return DragMode.TL
            if (nearR && nearT) return DragMode.TR
            if (nearL && nearB) return DragMode.BL
            if (nearR && nearB) return DragMode.BR
            if (nearL && y in cropRect.top..cropRect.bottom) return DragMode.L
            if (nearR && y in cropRect.top..cropRect.bottom) return DragMode.R
            if (nearT && x in cropRect.left..cropRect.right) return DragMode.T
            if (nearB && x in cropRect.left..cropRect.right) return DragMode.B
            if (cropRect.contains(x, y)) return DragMode.MOVE
            return DragMode.NONE
        }

        private fun applyDrag(dx: Float, dy: Float) {
            val minSize = 90f
            when (dragMode) {
                DragMode.MOVE -> {
                    cropRect.offset(dx, dy)
                    clampMove()
                }
                DragMode.TL -> resize(dx, dy, left = true, top = true, minSize = minSize)
                DragMode.TR -> resize(dx, dy, right = true, top = true, minSize = minSize)
                DragMode.BL -> resize(dx, dy, left = true, bottom = true, minSize = minSize)
                DragMode.BR -> resize(dx, dy, right = true, bottom = true, minSize = minSize)
                DragMode.L -> resize(dx, 0f, left = true, minSize = minSize)
                DragMode.R -> resize(dx, 0f, right = true, minSize = minSize)
                DragMode.T -> resize(0f, dy, top = true, minSize = minSize)
                DragMode.B -> resize(0f, dy, bottom = true, minSize = minSize)
                DragMode.NONE -> {}
            }
        }

        private fun resize(
            dx: Float,
            dy: Float,
            left: Boolean = false,
            top: Boolean = false,
            right: Boolean = false,
            bottom: Boolean = false,
            minSize: Float
        ) {
            val prev = RectF(cropRect)
            if (left) cropRect.left = (cropRect.left + dx).coerceIn(imgRect.left, cropRect.right - minSize)
            if (right) cropRect.right = (cropRect.right + dx).coerceIn(cropRect.left + minSize, imgRect.right)
            if (top) cropRect.top = (cropRect.top + dy).coerceIn(imgRect.top, cropRect.bottom - minSize)
            if (bottom) cropRect.bottom = (cropRect.bottom + dy).coerceIn(cropRect.top + minSize, imgRect.bottom)

            val ratio = targetRatio ?: return
            var newW = cropRect.width()
            var newH = newW / ratio
            if (top || bottom) {
                if (!left && !right) {
                    newH = cropRect.height()
                    newW = newH * ratio
                }
            }
            if (newW < minSize || newH < minSize) {
                cropRect.set(prev)
                return
            }
            if (left) cropRect.left = cropRect.right - newW else cropRect.right = cropRect.left + newW
            if (top) cropRect.top = cropRect.bottom - newH else cropRect.bottom = cropRect.top + newH
            if (!imgRect.contains(cropRect)) {
                cropRect.set(prev)
            }
        }

        private fun clampMove() {
            if (cropRect.left < imgRect.left) cropRect.offset(imgRect.left - cropRect.left, 0f)
            if (cropRect.right > imgRect.right) cropRect.offset(imgRect.right - cropRect.right, 0f)
            if (cropRect.top < imgRect.top) cropRect.offset(0f, imgRect.top - cropRect.top)
            if (cropRect.bottom > imgRect.bottom) cropRect.offset(0f, imgRect.bottom - cropRect.bottom)
        }

        fun cropResult(): Bitmap {
            val b = bmp ?: throw IllegalStateException("No bitmap")
            val sx = ((cropRect.left - imgRect.left) / imgRect.width()).coerceIn(0f, 1f)
            val sy = ((cropRect.top - imgRect.top) / imgRect.height()).coerceIn(0f, 1f)
            val sw = (cropRect.width() / imgRect.width()).coerceIn(0.05f, 1f)
            val sh = (cropRect.height() / imgRect.height()).coerceIn(0.05f, 1f)
            val px = (sx * b.width).toInt().coerceIn(0, b.width - 1)
            val py = (sy * b.height).toInt().coerceIn(0, b.height - 1)
            val pw = (sw * b.width).toInt().coerceIn(1, b.width - px)
            val ph = (sh * b.height).toInt().coerceIn(1, b.height - py)
            return Bitmap.createBitmap(b, px, py, pw, ph)
        }
    }
}
