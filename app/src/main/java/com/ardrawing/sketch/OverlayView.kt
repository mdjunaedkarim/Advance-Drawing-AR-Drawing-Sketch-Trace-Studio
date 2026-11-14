package com.ardrawing.sketch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.hypot

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var bitmap: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val userMatrix = Matrix()
    private val drawMatrix = Matrix()
    private val lastGoodMatrix = Matrix()
    private val camToView = Matrix()
    private val viewToCam = Matrix()
    private val homography = Matrix()

    private var imageAlpha = 180
    var isLocked = false
        private set
    private var trackingGood = true
    private var initializedPlacement = false

    private enum class Mode { NONE, DRAG, ZOOM_ROTATE }
    private var mode = Mode.NONE
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var lastX = 0f
    private var lastY = 0f
    private var prevMidX = 0f
    private var prevMidY = 0f
    private var prevDist = 0f
    private var prevAngle = 0f

    fun setBitmap(bmp: Bitmap?, resetPos: Boolean = true) {
        bitmap = bmp
        if (resetPos) {
            initializedPlacement = false
            isLocked = false
            trackingGood = true
            resetPlacement()
        }
        invalidate()
    }

    fun setOpacity(percent: Int) {
        imageAlpha = (percent.coerceIn(5, 100) * 255) / 100
        invalidate()
    }

    fun lock() {
        isLocked = true
        trackingGood = true
        lastGoodMatrix.set(userMatrix)
        drawMatrix.set(userMatrix)
        invalidate()
    }

    fun unlock() {
        isLocked = false
        trackingGood = true
        drawMatrix.set(userMatrix)
        invalidate()
    }

    fun updateTracking(out: PlaneTracker.Out) {
        if (!isLocked) return
        trackingGood = out.good
        if (!out.good || width == 0 || height == 0 || out.rw == 0 || out.rh == 0) {
            invalidate()
            return
        }

        val vw = width.toFloat()
        val vh = height.toFloat()
        val rw = out.rw.toFloat()
        val rh = out.rh.toFloat()

        val scale = maxOf(vw / rw, vh / rh)
        val ox = (vw - rw * scale) / 2f
        val oy = (vh - rh * scale) / 2f

        camToView.reset()
        camToView.setScale(scale, scale)
        camToView.postTranslate(ox, oy)
        camToView.invert(viewToCam)

        homography.setValues(out.h)

        drawMatrix.set(userMatrix)
        drawMatrix.postConcat(viewToCam)
        drawMatrix.postConcat(homography)
        drawMatrix.postConcat(camToView)

        lastGoodMatrix.set(drawMatrix)
        invalidate()
    }

    private fun resetPlacement() {
        val bmp = bitmap ?: return
        if (width == 0 || height == 0) return

        val s = minOf((width * 0.68f) / bmp.width, (height * 0.55f) / bmp.height)
        val dx = (width - bmp.width * s) / 2f
        val dy = (height - bmp.height * s) / 2f

        userMatrix.reset()
        userMatrix.postScale(s, s)
        userMatrix.postTranslate(dx, dy)
        drawMatrix.set(userMatrix)
        lastGoodMatrix.set(userMatrix)
        initializedPlacement = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!initializedPlacement && bitmap != null) resetPlacement()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        if (!initializedPlacement) resetPlacement()

        paint.alpha = if (isLocked && !trackingGood) (imageAlpha * 0.3f).toInt() else imageAlpha
        val m = if (isLocked) {
            if (trackingGood) drawMatrix else lastGoodMatrix
        } else {
            userMatrix
        }
        canvas.drawBitmap(bmp, m, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isLocked || bitmap == null) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                lastX = event.x
                lastY = event.y
                mode = Mode.DRAG
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    prevMidX = (event.getX(0) + event.getX(1)) / 2f
                    prevMidY = (event.getY(0) + event.getY(1)) / 2f
                    prevDist = hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0))
                    prevAngle = atan2(event.getY(1) - event.getY(0), event.getX(1) - event.getX(0))
                    mode = Mode.ZOOM_ROTATE
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == Mode.DRAG) {
                    val idx = event.findPointerIndex(activePointerId)
                    if (idx >= 0) {
                        val x = event.getX(idx)
                        val y = event.getY(idx)
                        userMatrix.postTranslate(x - lastX, y - lastY)
                        lastX = x
                        lastY = y
                        invalidate()
                    }
                } else if (mode == Mode.ZOOM_ROTATE && event.pointerCount >= 2) {
                    val midX = (event.getX(0) + event.getX(1)) / 2f
                    val midY = (event.getY(0) + event.getY(1)) / 2f
                    val dist = hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0))
                    val ang = atan2(event.getY(1) - event.getY(0), event.getX(1) - event.getX(0))

                    userMatrix.postTranslate(midX - prevMidX, midY - prevMidY)
                    if (prevDist > 12f && dist > 12f) {
                        val s = (dist / prevDist).coerceIn(0.5f, 2.0f)
                        userMatrix.postScale(s, s, midX, midY)
                    }
                    val dDeg = Math.toDegrees((ang - prevAngle).toDouble()).toFloat()
                    userMatrix.postRotate(dDeg, midX, midY)

                    prevMidX = midX; prevMidY = midY; prevDist = dist; prevAngle = ang
                    invalidate()
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val upIdx = event.actionIndex
                val rem = if (upIdx == 0) 1 else 0
                if (event.pointerCount > 1 && rem < event.pointerCount) {
                    activePointerId = event.getPointerId(rem)
                    lastX = event.getX(rem)
                    lastY = event.getY(rem)
                    mode = Mode.DRAG
                } else {
                    mode = Mode.NONE
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mode = Mode.NONE
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
        return true
    }
}
