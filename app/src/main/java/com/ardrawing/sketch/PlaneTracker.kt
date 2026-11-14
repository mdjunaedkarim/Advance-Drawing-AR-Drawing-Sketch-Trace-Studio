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

    init { setIdentity() }

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
        var good = true

        if (locked) {
            if (captureNextAnchor) {
                captureNextAnchor = false
                setAnchor(cur)
                setIdentity()
                good = true
            } else {
                frameCount++
                val p = prev
                var flowOk = p != null && p.size() == cur.size() && trackFlow(p, cur)

                if (!flowOk || frameCount % 10 == 0) {
                    val reloc = relocalizeFromAnchor(cur)
                    if (reloc != null) {
                        System.arraycopy(reloc, 0, rawH, 0, 9)
                        flowOk = true
                    }
                }
                good = flowOk
                if (good) applyDeadzoneSmooth(w.toDouble(), h.toDouble())
            }
        }

        prev?.release()
        prev = cur
        return Out(outH.copyOf(), w, h, good)
    }

    private fun setAnchor(frame: Mat) {
        clearAnchor()
        val copy = frame.clone()
        val kp = MatOfKeyPoint()
        val desc = Mat()
        val mask = Mat()
        orb.detectAndCompute(copy, mask, kp, desc)
        mask.release()
        anchor = copy
        anchorKp = kp
        anchorDesc = desc
    }

    private fun relocalizeFromAnchor(cur: Mat): DoubleArray? {
        val aKp = anchorKp ?: return null
        val aDesc = anchorDesc ?: return null
        if (aDesc.empty() || aKp.empty()) return null

        val cKp = MatOfKeyPoint()
        val cDesc = Mat()
        val mask = Mat()
        val matchesMat = MatOfDMatch()
        var hMat: Mat? = null

        try {
            orb.detectAndCompute(cur, mask, cKp, cDesc)
            if (cDesc.empty() || cKp.empty() || aDesc.type() != cDesc.type()) return null

            matcher.match(aDesc, cDesc, matchesMat)
            val all = matchesMat.toArray()
            if (all.size < 16) return null

            var minDist = Double.MAX_VALUE
            for (m in all) if (m.distance < minDist) minDist = m.distance.toDouble()
            val thresh = maxOf(38.0, minDist * 2.5)

            val goodMatches = ArrayList<DMatch>()
            for (m in all) if (m.distance <= thresh) goodMatches.add(m)
            if (goodMatches.size < 14) return null

            val aPts = aKp.toArray()
            val cPts = cKp.toArray()
            val srcList = ArrayList<Point>(goodMatches.size)
            val dstList = ArrayList<Point>(goodMatches.size)
            for (m in goodMatches) {
                srcList.add(aPts[m.queryIdx].pt)
                dstList.add(cPts[m.trainIdx].pt)
            }

            val srcMat = MatOfPoint2f()
            val dstMat = MatOfPoint2f()
            val ransacMask = Mat()
            srcMat.fromList(srcList)
            dstMat.fromList(dstList)

            hMat = Calib3d.findHomography(srcMat, dstMat, Calib3d.RANSAC, 4.0, ransacMask)
            val inliers = Core.countNonZero(ransacMask)
            srcMat.release(); dstMat.release(); ransacMask.release()

            if (hMat.empty() || inliers < 11) return null
            val hd = DoubleArray(9)
            hMat.get(0, 0, hd)
            if (!isValidHomography(hd, cur.cols().toDouble(), cur.rows().toDouble())) return null
            return hd
        } finally {
            cKp.release(); cDesc.release(); mask.release(); matchesMat.release(); hMat?.release()
        }
    }

    private fun trackFlow(prevFrame: Mat, cur: Mat): Boolean {
        val corners = MatOfPoint()
        val p0 = MatOfPoint2f()
        val p1 = MatOfPoint2f()
        val st = MatOfByte()
        val err = MatOfFloat()
        val mask = Mat()
        val src = MatOfPoint2f()
        val dst = MatOfPoint2f()
        var hMat: Mat? = null

        try {
            Imgproc.goodFeaturesToTrack(prevFrame, corners, 120, 0.01, 16.0)
            if (corners.empty() || corners.rows() < 14) return false

            p0.fromArray(*corners.toArray())
            Video.calcOpticalFlowPyrLK(prevFrame, cur, p0, p1, st, err)

            val a0 = p0.toArray()
            val a1 = p1.toArray()
            val s = st.toArray()
            val g0 = ArrayList<Point>()
            val g1 = ArrayList<Point>()
            for (i in a0.indices) {
                if (i < a1.size && i < s.size && s[i].toInt() == 1) {
                    g0.add(a0[i]); g1.add(a1[i])
                }
            }
            if (g0.size < 12) return false

            src.fromList(g0); dst.fromList(g1)
            hMat = Calib3d.findHomography(src, dst, Calib3d.RANSAC, 3.0, mask)
            if (hMat.empty() || Core.countNonZero(mask) < 10) return false

            val hd = DoubleArray(9)
            hMat.get(0, 0, hd)

            val nt = DoubleArray(9)
            for (r in 0 until 3) {
                for (c in 0 until 3) {
                    var sum = 0.0
                    for (k in 0 until 3) sum += hd[r * 3 + k] * rawH[k * 3 + c]
                    nt[r * 3 + c] = sum
                }
            }
            val w = nt[8]
            if (abs(w) < 1e-9) return false
            for (i in 0 until 9) nt[i] /= w

            if (!isValidHomography(nt, cur.cols().toDouble(), cur.rows().toDouble())) return false
            System.arraycopy(nt, 0, rawH, 0, 9)
            return true
        } finally {
            corners.release(); p0.release(); p1.release(); st.release(); err.release()
            mask.release(); src.release(); dst.release(); hMat?.release()
        }
    }

    private fun isValidHomography(hd: DoubleArray, w: Double, h: Double): Boolean {
        val det2 = hd[0] * hd[4] - hd[1] * hd[3]
        if (det2 < 0.12 || det2 > 8.5) return false
        if (abs(hd[6]) > 0.004 || abs(hd[7]) > 0.004) return false

        val pts = arrayOf(
            transformPt(hd, 0.0, 0.0), transformPt(hd, w, 0.0),
            transformPt(hd, w, h), transformPt(hd, 0.0, h)
        )
        for (p in pts) if (!p.x.isFinite() || !p.y.isFinite()) return false
        val area = polygonArea(pts)
        val base = w * h
        return area > base * 0.12 && area < base * 9.0
    }

    private fun applyDeadzoneSmooth(w: Double, h: Double) {
        val target = arrayOf(
            transformPt(rawH, 0.0, 0.0), transformPt(rawH, w, 0.0),
            transformPt(rawH, w, h), transformPt(rawH, 0.0, h)
        )
        val prevSm = smoothCorners
        if (prevSm == null) {
            smoothCorners = target
        } else {
            var maxDelta = 0.0
            for (i in 0..3) {
                val d = hypot(target[i].x - prevSm[i].x, target[i].y - prevSm[i].y)
