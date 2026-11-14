package com.ardrawing.sketch

import androidx.camera.core.ImageProxy
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.DMatch
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import kotlin.math.abs
import kotlin.math.hypot

object Frames {
    fun uprightGray(image: ImageProxy): Mat {
        val w = image.width
        val h = image.height
        val plane = image.planes[0]
        val buf = plane.buffer
        val rowStride = plane.rowStride
        val data = ByteArray(w * h)
        if (rowStride == w) {
            buf.get(data, 0, w * h)
        } else {
            for (row in 0 until h) {
                buf.position(row * rowStride)
                buf.get(data, row * w, w)
            }
        }
        val gray = Mat(h, w, CvType.CV_8UC1)
        gray.put(0, 0, data)

        val targetW = 480.0
        val small = Mat()
        Imgproc.resize(gray, small, Size(targetW, h * targetW / w))
        gray.release()

        val up = Mat()
        when (image.imageInfo.rotationDegrees) {
            90 -> Core.rotate(small, up, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(small, up, Core.ROTATE_180)
            270 -> Core.rotate(small, up, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> small.copyTo(up)
        }
        small.release()
        return up
    }
}

class PlaneTracker {
    class Out(val h: FloatArray, val rw: Int, val rh: Int, val good: Boolean)

    private var prev: Mat? = null
    private var anchor: Mat? = null
    private var anchorKp: MatOfKeyPoint? = null
    private var anchorDesc: Mat? = null

    private val orb = ORB.create(600)
    private val matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)

    private val rawH = DoubleArray(9)
    private var smoothCorners: Array<Point>? = null
    private val outH = FloatArray(9)

    private var locked = false
    private var captureNextAnchor = false
    private var frameCount = 0

    init {
        setIdentity()
    }

    private fun setIdentity() {
        for (i in 0 until 9) {
            rawH[i] = if (i % 4 == 0) 1.0 else 0.0
            outH[i] = rawH[i].toFloat()
        }
        smoothCorners = null
    }

    @Synchronized
    fun startLock() {
        clearAnchor()
        setIdentity()
        locked = true
        captureNextAnchor = true
        frameCount = 0
    }

    @Synchronized
    fun stopLock() {
        locked = false
        captureNextAnchor = false
        clearAnchor()
        setIdentity()
    }

    private fun clearAnchor() {
        anchor?.release(); anchor = null
        anchorKp?.release(); anchorKp = null
        anchorDesc?.release(); anchorDesc = null
    }

    @Synchronized
    fun process(image: ImageProxy): Out {
        val cur = Frames.uprightGray(image)
        val w = cur.cols()
        val h = cur.rows()
