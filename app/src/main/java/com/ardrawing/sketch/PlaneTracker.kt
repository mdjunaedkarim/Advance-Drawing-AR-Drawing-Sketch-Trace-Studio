package com.ardrawing.sketch

import androidx.camera.core.ImageProxy
import org.opencv.calib3d.Calib3d
import org.opencv.core.*
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
        val stride = plane.rowStride
        val data = ByteArray(w * h)
        if (stride == w) buf.get(data, 0, w * h)
        else for (r in 0 until h) { buf.position(r * stride); buf.get(data, r * w, w) }

        val gray = Mat(h, w, CvType.CV_8UC1).apply { put(0, 0, data) }
        val small = Mat()
        Imgproc.resize(gray, small, Size(480.0, h * 480.0 / w))
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
    private var aKp: MatOfKeyPoint? = null
    private var aDesc: Mat? = null
    private val orb = ORB.create(500)
    private val matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)

    private val rawH = DoubleArray(9)
    private val outH = FloatArray(9)
    private var smoothPts: Array<Point>? = null
    private var locked = false
    private var takeAnchor = false
    private var count = 0

    init { resetId() }

    private fun resetId() {
        for (i in 0..8) {
            rawH[i] = if (i % 4 == 0) 1.0 else 0.0
            outH[i] = rawH[i].toFloat()
        }
        smoothPts = null
    }

    @Synchronized fun startLock() { clearAnchor(); resetId(); locked = true; takeAnchor = true; count = 0 }
    @Synchronized fun stopLock() { locked = false; takeAnchor = false; clearAnchor(); resetId() }

    private fun clearAnchor() {
        anchor?.release(); anchor = null
        aKp?.release(); aKp = null
        aDesc?.release(); aDesc = null
    }

    @Synchronized
    fun process(image: ImageProxy): Out {
        val cur = Frames.uprightGray(image)
        val w = cur.cols(); val h = cur.rows()
        var good = true

        if (locked) {
            if (takeAnchor) {
                takeAnchor = false
                clearAnchor()
                anchor = cur.clone()
                aKp = MatOfKeyPoint(); aDesc = Mat()
                val m = Mat()
                orb.detectAndCompute(anchor!!, m, aKp!!, aDesc!!)
                m.release()
                resetId()
            } else {
                count++
                val p = prev
                var ok = p != null && p.size() == cur.size() && trackFlow(p, cur)
                if (!ok || count % 10 == 0) {
                    val rel = relocalize(cur)
                    if (rel != null) { System.arraycopy(rel, 0, rawH, 0, 9); ok = true }
                }
                good = ok
                if (good) smooth(w.toDouble(), h.toDouble())
            }
        }
        prev?.release(); prev = cur
        return Out(outH.copyOf(), w, h, good)
    }

    private fun relocalize(cur: Mat): DoubleArray? {
        val kp1 = aKp ?: return null; val d1 = aDesc ?: return null
        if (d1.empty() || kp1.empty()) return null
        val kp2 = MatOfKeyPoint(); val d2 = Mat(); val mask = Mat(); val matches = MatOfDMatch()
        var hm: Mat? = null
        try {
            orb.detectAndCompute(cur, mask, kp2, d2)
            if (d2.empty() || kp2.empty() || d1.type() != d2.type()) return null
            matcher.match(d1, d2, matches)
            val arr = matches.toArray()
            if (arr.size < 16) return null
            val minD = arr.minOf { it.distance.toDouble() }
            val th = maxOf(38.0, minD * 2.5)
            val good = arr.filter { it.distance <= th }
            if (good.size < 14) return null

            val p1 = kp1.toArray(); val p2 = kp2.toArray()
            val sMat = MatOfPoint2f(*good.map { p1[it.queryIdx].pt }.toTypedArray())
            val tMat = MatOfPoint2f(*good.map { p2[it.trainIdx].pt }.toTypedArray())
            val rm = Mat()
            hm = Calib3d.findHomography(sMat, tMat, Calib3d.RANSAC, 4.0, rm)
            val inl = Core.countNonZero(rm)
            sMat.release(); tMat.release(); rm.release()
            if (hm.empty() || inl < 11) return null
            val hd = DoubleArray(9); hm.get(0, 0, hd)
            return if (validH(hd, cur.cols().toDouble(), cur.rows().toDouble())) hd else null
        } finally {
            kp2.release(); d2.release(); mask.release(); matches.release(); hm?.release()
        }
    }

    private fun trackFlow(p: Mat, c: Mat): Boolean {
        val corners = MatOfPoint(); val p0 = MatOfPoint2f(); val p1 = MatOfPoint2f()
        val st = MatOfByte(); val err = MatOfFloat(); val mask = Mat()
        val s = MatOfPoint2f(); val t = MatOfPoint2f()
        var hm: Mat? = null
        try {
            Imgproc.goodFeaturesToTrack(p, corners, 120, 0.01, 16.0)
            if (corners.empty() || corners.rows() < 14) return false
            p0.fromArray(*corners.toArray())
            Video.calcOpticalFlowPyrLK(p, c, p0, p1, st, err)
            val a0 = p0.toArray(); val a1 = p1.toArray(); val sv = st.toArray()
            val g0 = ArrayList<Point>(); val g1 = ArrayList<Point>()
            for (i in a0.indices) if (i < a1.size && i < sv.size && sv[i].toInt() == 1) { g0.add(a0[i]); g1.add(a1[i]) }
            if (g0.size < 12) return false
            s.fromList(g0); t.fromList(g1)
            hm = Calib3d.findHomography(s, t, Calib3d.RANSAC, 3.0, mask)
            if (hm.empty() || Core.countNonZero(mask) < 10) return false
            val hd = DoubleArray(9); hm.get(0, 0, hd)
            val nt = DoubleArray(9)
            for (r in 0..2) for (col in 0..2) nt[r * 3 + col] = (0..2).sumOf { k -> hd[r * 3 + k] * rawH[k * 3 + col] }
            if (abs(nt[8]) < 1e-9) return false
            for (i in 0..8) nt[i] /= nt[8]
            if (!validH(nt, c.cols().toDouble(), c.rows().toDouble())) return false
            System.arraycopy(nt, 0, rawH, 0, 9)
            return true
        } finally {
            corners.release(); p0.release(); p1.release(); st.release(); err.release()
            mask.release(); s.release(); t.release(); hm?.release()
        }
    }

    private fun validH(hd: DoubleArray, w: Double, h: Double): Boolean {
        val det = hd[0] * hd[4] - hd[1] * hd[3]
        if (det < 0.12 || det > 8.5 || abs(hd[6]) > 0.004 || abs(hd[7]) > 0.004) return false
        val pts = arrayOf(pt(hd, 0.0, 0.0), pt(hd, w, 0.0), pt(hd, w, h), pt(hd, 0.0, h))
        if (pts.any { !it.x.isFinite() || !it.y.isFinite() }) return false
        val area = (0..3).sumOf { i -> val j = (i + 1) % 4; pts[i].x * pts[j].y - pts[j].x * pts[i].y } * 0.5
        return area > w * h * 0.12 && area < w * h * 9.0
    }

    private fun smooth(w: Double, h: Double) {
        val trg = arrayOf(pt(rawH, 0.0, 0.0), pt(rawH, w, 0.0), pt(rawH, w, h), pt(rawH, 0.0, h))
        val cur = smoothPts
        if (cur == null) smoothPts = trg
        else {
            val md = (0..3).maxOf { hypot(trg[it].x - cur[it].x, trg[it].y - cur[it].y) }
            if (md < 2.2) return
            val a = if (md > 18.0) 0.55 else 0.28
            for (i in 0..3) cur[i] = Point(cur[i].x + (trg[i].x - cur[i].x) * a, cur[i].y + (trg[i].y - cur[i].y) * a)
        }
        val s4 = MatOfPoint2f(Point(0.0, 0.0), Point(w, 0.0), Point(w, h), Point(0.0, h))
        val d4 = MatOfPoint2f(*smoothPts!!)
        val sm = Imgproc.getPerspectiveTransform(s4, d4)
        val d = DoubleArray(9); sm.get(0, 0, d)
        for (i in 0..8) outH[i] = d[i].toFloat()
        s4.release(); d4.release(); sm.release()
    }

    private fun pt(hd: DoubleArray, x: Double, y: Double): Point {
        val den = hd[6] * x + hd[7] * y + hd[8]
        val safe = if (abs(den) < 1e-6) 1e-6 else den
        return Point((hd[0] * x + hd[1] * y + hd[2]) / safe, (hd[3] * x + hd[4] * y + hd[5]) / safe)
    }
}
