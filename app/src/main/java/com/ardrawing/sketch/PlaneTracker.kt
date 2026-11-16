package com.ardrawing.sketch

import android.graphics.Rect
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.CLAHE
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object Frames {
    private val claheLocal = object : ThreadLocal<CLAHE>() {
        override fun initialValue(): CLAHE = Imgproc.createCLAHE(2.2, Size(8.0, 8.0))
    }

    fun uprightGray(image: ImageProxy): Mat {
        val w = image.width
        val h = image.height
        val plane = image.planes[0]
        val buf = plane.buffer.duplicate()
        buf.rewind()
        val stride = plane.rowStride
        val pixelStride = plane.pixelStride
        val data = ByteArray(w * h)
        if (pixelStride == 1 && stride == w) {
            buf.get(data, 0, w * h)
        } else if (pixelStride == 1) {
            for (r in 0 until h) {
                buf.position(r * stride)
                buf.get(data, r * w, w)
            }
        } else {
            val rowBuf = ByteArray(stride)
            for (r in 0 until h) {
                buf.position(r * stride)
                val toRead = minOf(buf.remaining(), stride)
                buf.get(rowBuf, 0, toRead)
                for (c in 0 until w) {
                    data[r * w + c] = rowBuf[c * pixelStride]
                }
            }
        }

        val fullGray = Mat(h, w, CvType.CV_8UC1).apply { put(0, 0, data) }

        // Crop strictly to CameraX ViewPort cropRect so tracker coordinates match PreviewView 1:1
        val cr: Rect = image.cropRect
        val cx = cr.left.coerceIn(0, (w - 1).coerceAtLeast(0))
        val cy = cr.top.coerceIn(0, (h - 1).coerceAtLeast(0))
        val cw = cr.width().coerceIn(1, (w - cx).coerceAtLeast(1))
        val ch = cr.height().coerceIn(1, (h - cy).coerceAtLeast(1))
        val cropped = if (cx == 0 && cy == 0 && cw == w && ch == h) fullGray else fullGray.submat(cy, cy + ch, cx, cx + cw)

        val targetLong = 580.0
        val scale = targetLong / maxOf(cw, ch).toDouble()
        val rw = (cw * scale).toInt().coerceAtLeast(64)
        val rh = (ch * scale).toInt().coerceAtLeast(64)

        val small = Mat()
        Imgproc.resize(cropped, small, Size(rw.toDouble(), rh.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
        if (cropped !== fullGray) cropped.release()
        fullGray.release()

        val enhanced = Mat()
        try {
            claheLocal.get()?.apply(small, enhanced) ?: small.copyTo(enhanced)
        } finally {
            small.release()
        }

        val up = Mat()
        when (image.imageInfo.rotationDegrees) {
            90 -> Core.rotate(enhanced, up, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(enhanced, up, Core.ROTATE_180)
            270 -> Core.rotate(enhanced, up, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> enhanced.copyTo(up)
        }
        enhanced.release()
        return up
    }
}

private class OneEuro1D(
    private val minCutoff: Double = 0.55,
    private val beta: Double = 0.028,
    private val dCutoff: Double = 1.0
) {
    private var hasPrev = false
    private var prevX = 0.0
    private var prevDx = 0.0

    fun reset(v: Double) {
        hasPrev = true
        prevX = v
        prevDx = 0.0
    }

    fun filter(x: Double, dt: Double): Double {
        if (!hasPrev) {
            reset(x)
            return x
        }
        val rawDx = (x - prevX) / dt
        val aD = alpha(dCutoff, dt)
        val dx = prevDx + (rawDx - prevDx) * aD
        val cutoff = minCutoff + beta * abs(dx)
        val a = alpha(cutoff, dt)
        val out = prevX + (x - prevX) * a
        prevX = out
        prevDx = dx
        return out
    }

    private fun alpha(cutoff: Double, dt: Double): Double {
        val tau = 1.0 / (2.0 * PI * cutoff)
        return 1.0 / (1.0 + tau / dt)
    }
}

class PlaneTracker {
    class Out(val h: FloatArray, val rw: Int, val rh: Int, val good: Boolean)

    private class KeyFrame(
        val kpPts: Array<Point>,
        val desc: Mat,
        val hFromAnchor: DoubleArray,
        val anchorCenter: Point,
        val anchorScale: Double,
        val anchorAngle: Double
    )

    private var prev: Mat? = null
    private val keyframes = ArrayList<KeyFrame>(MAX_KEYFRAMES)

    private val orbAnchor by lazy {
        ORB.create(1200, 1.2f, 8, 21, 0, 2, ORB.HARRIS_SCORE, 21, 14)
    }
    private val orbQuery by lazy {
        ORB.create(900, 1.2f, 8, 21, 0, 2, ORB.HARRIS_SCORE, 21, 14)
    }
    private val matcher by lazy { DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING) }

    private val lkWin = Size(21.0, 21.0)
    private val lkTerm = TermCriteria(TermCriteria.COUNT + TermCriteria.EPS, 25, 0.01)

    private val rawH = DoubleArray(9)
    private val outH = FloatArray(9)

    private val filterX = Array(4) { OneEuro1D() }
    private val filterY = Array(4) { OneEuro1D() }
    private var lastFilterNs = 0L
    private var smoothPts: Array<Point>? = null

    private var locked = false
    private var takeAnchor = false
    private var count = 0
    private var lostStreak = 0
    private var wasLost = false
    private var recoveryConfirm = 0
    private var pendingRecoverH: DoubleArray? = null
    private var lastFlowStep = 0.0

    companion object {
        private const val MAX_KEYFRAMES = 10
        private const val GRACE_FRAMES = 6
    }

    init { resetId() }

    private fun resetId() {
        for (i in 0..8) {
            rawH[i] = if (i % 4 == 0) 1.0 else 0.0
            outH[i] = rawH[i].toFloat()
        }
        smoothPts = null
        lastFilterNs = 0L
    }

    @Synchronized
    fun startLock() {
        clearAnchor()
        resetId()
        locked = true
        takeAnchor = true
        count = 0
        lostStreak = 0
        wasLost = false
        recoveryConfirm = 0
        pendingRecoverH = null
        lastFlowStep = 0.0
    }

    @Synchronized
    fun stopLock() {
        locked = false
        takeAnchor = false
        clearAnchor()
        resetId()
        lostStreak = 0
        wasLost = false
        recoveryConfirm = 0
        pendingRecoverH = null
    }

    private fun clearAnchor() {
        keyframes.forEach { it.desc.release() }
        keyframes.clear()
    }

    @Synchronized
    fun process(image: ImageProxy): Out {
        val cur = Frames.uprightGray(image)
        val w = cur.cols()
        val h = cur.rows()
        var good = true

        if (locked) {
            if (takeAnchor || keyframes.isEmpty()) {
                val added = captureKeyFrame(
                    frame = cur,
                    hFromAnchor = DoubleArray(9) { if (it % 4 == 0) 1.0 else 0.0 },
                    w = w.toDouble(),
                    h = h.toDouble(),
                    minFeatures = 18
                )
                if (added || !takeAnchor) {
                    takeAnchor = false
                    resetId()
                    lostStreak = 0
                    wasLost = false
                }
            } else if (wasLost) {
                // While camera is pointing away (2-3 minutes or fast pan), scan frames for instant re-lock
                count++
                val rec = relocalize(cur, strict = true)
                if (rec != null) {
                    val (recH, inliers) = rec
                    val pr = pendingRecoverH
                    val agrees = pr != null && maxCornerDist(pr, recH, w.toDouble(), h.toDouble()) < 26.0
                    recoveryConfirm = if (agrees) recoveryConfirm + 1 else 1
                    pendingRecoverH = recH

                    if (inliers >= 18 || recoveryConfirm >= 2) {
                        System.arraycopy(recH, 0, rawH, 0, 9)
                        wasLost = false
                        lostStreak = 0
                        recoveryConfirm = 0
                        pendingRecoverH = null
                        good = true
                        smooth(w.toDouble(), h.toDouble(), snap = true)
                    } else {
                        good = false
                    }
                } else {
                    recoveryConfirm = 0
                    pendingRecoverH = null
                    good = false
                }
            } else {
                count++
                val p = prev
                val flowOk = p != null && p.size() == cur.size() && trackFlow(p, cur)
                var stepOk = flowOk

                if (!flowOk || count % 8 == 0) {
                    val rec = relocalize(cur, strict = !flowOk)
                    if (rec != null) {
                        val (recH, _, matchedIdx) = rec
                        if (flowOk) {
                            // Gently pull flow toward exact anchor homography to remove any cumulative drift with zero snap
                            val dist = maxCornerDist(rawH, recH, w.toDouble(), h.toDouble())
                            if (dist < 42.0) {
                                val weight = if (matchedIdx == 0) 0.28 else 0.18
                                val blended = blendHomography(rawH, recH, weight, w.toDouble(), h.toDouble())
                                if (blended != null) System.arraycopy(blended, 0, rawH, 0, 9)
                            } else {
                                System.arraycopy(recH, 0, rawH, 0, 9)
                            }
                        } else {
                            System.arraycopy(recH, 0, rawH, 0, 9)
                            stepOk = true
                        }

                        // If steady & anchored directly to primary or high-overlap neighbor, expand surrounding keyframes
                        if (matchedIdx == 0 && lastFlowStep < 9.0 && keyframes.size < MAX_KEYFRAMES) {
                            maybeAddNeighborKeyFrame(cur, rawH, w.toDouble(), h.toDouble())
                        }
                    }
                } else if (flowOk && count % 15 == 4 && lastFlowStep < 7.0 && keyframes.size < MAX_KEYFRAMES) {
                    maybeAddNeighborKeyFrame(cur, rawH, w.toDouble(), h.toDouble())
                }

                if (stepOk) {
                    lostStreak = 0
                    good = true
                    smooth(w.toDouble(), h.toDouble(), snap = false)
                } else {
                    lostStreak++
                    if (lostStreak <= GRACE_FRAMES) {
                        // Brief fast-motion blur tolerance: hold last steady pose without flickering
                        good = true
                    } else {
                        wasLost = true
                        good = false
                    }
                }
            }
        }

        prev?.release()
        prev = cur
        return Out(outH.copyOf(), w, h, good)
    }

    private fun captureKeyFrame(
        frame: Mat,
        hFromAnchor: DoubleArray,
        w: Double,
        h: Double,
        minFeatures: Int
    ): Boolean {
        val kp = MatOfKeyPoint()
        val desc = Mat()
        val mask = Mat()
        return try {
            orbAnchor.detectAndCompute(frame, mask, kp, desc)
            if (desc.empty() || kp.rows() < minFeatures) {
                desc.release()
                false
            } else {
                val kpPts = kp.toArray().map { it.pt }.toTypedArray()
                val invH = invert3x3(hFromAnchor) ?: DoubleArray(9) { if (it % 4 == 0) 1.0 else 0.0 }
                val centerInAnchor = pt(invH, w * 0.5, h * 0.5)
                val sx = pt(invH, w * 0.5 + 100.0, h * 0.5)
                val scale = hypot(sx.x - centerInAnchor.x, sx.y - centerInAnchor.y) / 100.0
                val angle = atan2(sx.y - centerInAnchor.y, sx.x - centerInAnchor.x)
                keyframes.add(KeyFrame(kpPts, desc, hFromAnchor.copyOf(), centerInAnchor, scale, angle))
                true
            }
        } finally {
            kp.release()
            mask.release()
        }
    }

    private fun maybeAddNeighborKeyFrame(cur: Mat, curH: DoubleArray, w: Double, h: Double) {
        if (keyframes.size >= MAX_KEYFRAMES) return
        val invH = invert3x3(curH) ?: return
        val curCenter = pt(invH, w * 0.5, h * 0.5)
        val sx = pt(invH, w * 0.5 + 100.0, h * 0.5)
        val curScale = hypot(sx.x - curCenter.x, sx.y - curCenter.y) / 100.0
        val curAngle = atan2(sx.y - curCenter.y, sx.x - curCenter.x)

        // Ensure view still overlaps primary frame decently so anchor transform stays reliable
        val pAnchor = keyframes[0].anchorCenter
        val distFromPrimary = hypot(curCenter.x - pAnchor.x, curCenter.y - pAnchor.y)
        if (distFromPrimary > max(w, h) * 0.95) return

        val minDistThresh = min(w, h) * 0.24
        val tooClose = keyframes.any { kf ->
            val dPos = hypot(curCenter.x - kf.anchorCenter.x, curCenter.y - kf.anchorCenter.y)
            val dScale = abs(curScale - kf.anchorScale) / max(0.2, kf.anchorScale)
            var dAng = abs(curAngle - kf.anchorAngle)
            if (dAng > PI) dAng = 2.0 * PI - dAng
            dPos < minDistThresh && dScale < 0.22 && dAng < 0.28
        }
        if (!tooClose) {
            captureKeyFrame(cur, curH, w, h, minFeatures = 80)
        }
    }

    private data class RelocHit(val anchorToCurH: DoubleArray, val inliers: Int, val kfIndex: Int)

    private fun relocalize(cur: Mat, strict: Boolean): RelocHit? {
        if (keyframes.isEmpty()) return null
        val kp2 = MatOfKeyPoint()
        val d2 = Mat()
        val mask = Mat()
        try {
            orbQuery.detectAndCompute(cur, mask, kp2, d2)
            if (d2.empty() || d2.rows() < 12 || kp2.empty()) return null
            val p2 = kp2.toArray()
            val w = cur.cols().toDouble()
            val h = cur.rows().toDouble()

            var bestHit: RelocHit? = null

            // Check Primary Anchor (#0) first, then newest surrounding keyframes
            val order = if (keyframes.size <= 1) listOf(0)
            else listOf(0) + (keyframes.indices.drop(1).reversed())

            for (idx in order) {
                val kf = keyframes[idx]
                if (kf.desc.empty() || kf.desc.rows() < 12 || kf.desc.type() != d2.type()) continue

                val knnMatches = ArrayList<MatOfDMatch>()
                var hm: Mat? = null
                try {
                    matcher.knnMatch(kf.desc, d2, knnMatches, 2)
                    val ratio = if (strict) 0.74 else 0.78
                    val goodPts1 = ArrayList<Point>()
                    val goodPts2 = ArrayList<Point>()

                    for (mMat in knnMatches) {
                        val arr = mMat.toArray()
                        if (arr.size >= 2 && arr[0].distance < ratio * arr[1].distance && arr[0].distance < 58f) {
                            val m = arr[0]
                            if (m.queryIdx in kf.kpPts.indices && m.trainIdx in p2.indices) {
                                goodPts1.add(kf.kpPts[m.queryIdx])
                                goodPts2.add(p2[m.trainIdx].pt)
                            }
                        }
                    }

                    val minMatches = if (strict) 13 else 11
                    if (goodPts1.size < minMatches) continue

                    val sMat = MatOfPoint2f(*goodPts1.toTypedArray())
                    val tMat = MatOfPoint2f(*goodPts2.toTypedArray())
                    val rm = Mat()
                    hm = Calib3d.findHomography(sMat, tMat, Calib3d.RANSAC, 3.2, rm, 2000, 0.995)
                    val inliers = Core.countNonZero(rm)
                    sMat.release()
                    tMat.release()
                    rm.release()

                    val minInl = if (strict) 12 else 10
                    if (hm.empty() || inliers < minInl) continue

                    val relH = DoubleArray(9)
                    hm.get(0, 0, relH)
                    if (!validH(relH, w, h)) continue

                    // Compose back to Primary Anchor coordinates: H_anchor_to_cur = relH * kf.hFromAnchor
                    val finalH = if (idx == 0) {
                        relH
                    } else {
                        val comp = mul3x3(relH, kf.hFromAnchor) ?: continue
                        if (!validH(comp, w, h)) continue
                        comp
                    }

                    // Primary Anchor match wins immediately if confident
                    if (idx == 0 && inliers >= minInl) {
                        return RelocHit(finalH, inliers, 0)
                    }
                    if (bestHit == null || inliers > bestHit.inliers) {
                        bestHit = RelocHit(finalH, inliers, idx)
                    }
                } finally {
                    knnMatches.forEach { it.release() }
                    hm?.release()
                }
            }
            return bestHit
        } finally {
            kp2.release()
            d2.release()
            mask.release()
        }
    }

    private fun trackFlow(p: Mat, c: Mat): Boolean {
        val corners = MatOfPoint()
        val p0 = MatOfPoint2f()
        val p1 = MatOfPoint2f()
        val pBack = MatOfPoint2f()
        val stF = MatOfByte()
        val errF = MatOfFloat()
        val stB = MatOfByte()
        val errB = MatOfFloat()
        val mask = Mat()
        val s = MatOfPoint2f()
        val t = MatOfPoint2f()
        var hm: Mat? = null
        try {
            Imgproc.goodFeaturesToTrack(p, corners, 180, 0.01, 14.0)
            if (corners.empty() || corners.rows() < 16) return false
            p0.fromArray(*corners.toArray())

            // Bidirectional Forward-Backward LK optical flow eliminates drifting & noisy background corners
            Video.calcOpticalFlowPyrLK(p, c, p0, p1, stF, errF, lkWin, 3, lkTerm)
            Video.calcOpticalFlowPyrLK(c, p, p1, pBack, stB, errB, lkWin, 3, lkTerm)

            val a0 = p0.toArray()
            val a1 = p1.toArray()
            val aBack = pBack.toArray()
            val svF = stF.toArray()
            val svB = stB.toArray()

            val g0 = ArrayList<Point>()
            val g1 = ArrayList<Point>()
            var stepSum = 0.0
            for (i in a0.indices) {
                if (i < a1.size && i < aBack.size && i < svF.size && i < svB.size &&
                    svF[i].toInt() == 1 && svB[i].toInt() == 1
                ) {
                    val fbErr = hypot(a0[i].x - aBack[i].x, a0[i].y - aBack[i].y)
                    if (fbErr <= 1.45) {
                        g0.add(a0[i])
                        g1.add(a1[i])
                        stepSum += hypot(a1[i].x - a0[i].x, a1[i].y - a0[i].y)
                    }
                }
            }
            if (g0.size < 13) return false
            lastFlowStep = stepSum / g0.size

            s.fromList(g0)
            t.fromList(g1)
            hm = Calib3d.findHomography(s, t, Calib3d.RANSAC, 2.2, mask, 2000, 0.995)
            if (hm.empty() || Core.countNonZero(mask) < 11) return false

            val hd = DoubleArray(9)
            hm.get(0, 0, hd)
            val nt = mul3x3(hd, rawH) ?: return false
            if (!validH(nt, c.cols().toDouble(), c.rows().toDouble())) return false

            System.arraycopy(nt, 0, rawH, 0, 9)
            return true
        } finally {
            corners.release(); p0.release(); p1.release(); pBack.release()
            stF.release(); errF.release(); stB.release(); errB.release()
            mask.release(); s.release(); t.release(); hm?.release()
        }
    }

    private fun mul3x3(a: DoubleArray, b: DoubleArray): DoubleArray? {
        val res = DoubleArray(9)
        for (r in 0..2) {
            for (col in 0..2) {
                res[r * 3 + col] = a[r * 3] * b[col] + a[r * 3 + 1] * b[3 + col] + a[r * 3 + 2] * b[6 + col]
            }
        }
        val n = res[8]
        if (abs(n) < 1e-9) return null
        for (i in 0..8) res[i] /= n
        return res
    }

    private fun invert3x3(m: DoubleArray): DoubleArray? {
        val a = m[0]; val b = m[1]; val c = m[2]
        val d = m[3]; val e = m[4]; val f = m[5]
        val g = m[6]; val h = m[7]; val i = m[8]

        val c00 = e * i - f * h
        val c01 = -(d * i - f * g)
        val c02 = d * h - e * g
        val c10 = -(b * i - c * h)
        val c11 = a * i - c * g
        val c12 = -(a * h - b * g)
        val c20 = b * f - c * e
        val c21 = -(a * f - c * d)
        val c22 = a * e - b * d

        val det = a * c00 + b * c01 + c * c02
        if (abs(det) < 1e-10) return null
        val invDet = 1.0 / det
        val inv = doubleArrayOf(
            c00 * invDet, c10 * invDet, c20 * invDet,
            c01 * invDet, c11 * invDet, c21 * invDet,
            c02 * invDet, c12 * invDet, c22 * invDet
        )
        val n = inv[8]
        if (abs(n) < 1e-9) return null
        for (k in 0..8) inv[k] /= n
        return inv
    }

    private fun validH(hd: DoubleArray, w: Double, h: Double): Boolean {
        val det = hd[0] * hd[4] - hd[1] * hd[3]
        if (det < 0.08 || det > 12.0 || abs(hd[6]) > 0.0038 || abs(hd[7]) > 0.0038) return false
        val pts = arrayOf(pt(hd, 0.0, 0.0), pt(hd, w, 0.0), pt(hd, w, h), pt(hd, 0.0, h))
        if (pts.any { !it.x.isFinite() || !it.y.isFinite() }) return false

        // Ensure quad remains strictly convex
        var sign = 0.0
        for (i in 0..3) {
            val p0 = pts[i]
            val p1 = pts[(i + 1) % 4]
            val p2 = pts[(i + 2) % 4]
            val cross = (p1.x - p0.x) * (p2.y - p1.y) - (p1.y - p0.y) * (p2.x - p1.x)
            if (abs(cross) < 1e-3) return false
            if (i == 0) sign = cross else if (cross * sign <= 0.0) return false
        }

        val area = (0..3).sumOf { i -> val j = (i + 1) % 4; pts[i].x * pts[j].y - pts[j].x * pts[i].y } * 0.5
        return area > w * h * 0.08 && area < w * h * 12.0
    }

    private fun maxCornerDist(h1: DoubleArray, h2: DoubleArray, w: Double, h: Double): Double {
        val c1 = arrayOf(pt(h1, 0.0, 0.0), pt(h1, w, 0.0), pt(h1, w, h), pt(h1, 0.0, h))
        val c2 = arrayOf(pt(h2, 0.0, 0.0), pt(h2, w, 0.0), pt(h2, w, h), pt(h2, 0.0, h))
        return (0..3).maxOf { hypot(c1[it].x - c2[it].x, c1[it].y - c2[it].y) }
    }

    private fun blendHomography(hA: DoubleArray, hB: DoubleArray, weightB: Double, w: Double, h: Double): DoubleArray? {
        val pA = arrayOf(pt(hA, 0.0, 0.0), pt(hA, w, 0.0), pt(hA, w, h), pt(hA, 0.0, h))
        val pB = arrayOf(pt(hB, 0.0, 0.0), pt(hB, w, 0.0), pt(hB, w, h), pt(hB, 0.0, h))
        val dst = Array(4) { i ->
            Point(
                pA[i].x + (pB[i].x - pA[i].x) * weightB,
                pA[i].y + (pB[i].y - pA[i].y) * weightB
            )
        }
        val s4 = MatOfPoint2f(Point(0.0, 0.0), Point(w, 0.0), Point(w, h), Point(0.0, h))
        val d4 = MatOfPoint2f(*dst)
        val sm = Imgproc.getPerspectiveTransform(s4, d4)
        val out = DoubleArray(9)
        sm.get(0, 0, out)
        s4.release()
        d4.release()
        sm.release()
        val n = out[8]
        if (abs(n) < 1e-9) return null
        for (i in 0..8) out[i] /= n
        return if (validH(out, w, h)) out else null
    }

    private fun smooth(w: Double, h: Double, snap: Boolean) {
        val nowNs = SystemClock.elapsedRealtimeNanos()
        val dt = if (lastFilterNs == 0L) 0.033 else ((nowNs - lastFilterNs) / 1_000_000_000.0).coerceIn(0.006, 0.25)
        lastFilterNs = nowNs

        val trg = arrayOf(pt(rawH, 0.0, 0.0), pt(rawH, w, 0.0), pt(rawH, w, h), pt(rawH, 0.0, h))

        if (snap || smoothPts == null) {
            for (i in 0..3) {
                filterX[i].reset(trg[i].x)
                filterY[i].reset(trg[i].y)
            }
            smoothPts = trg
        } else {
            val filtered = Array(4) { i ->
                Point(filterX[i].filter(trg[i].x, dt), filterY[i].filter(trg[i].y, dt))
            }
            val cur = smoothPts!!
            val moveMax = (0..3).maxOf { hypot(filtered[it].x - cur[it].x, filtered[it].y - cur[it].y) }
            // Sub-pixel still deadband prevents micro-jitter while keeping fast motion completely fluid
            if (moveMax < 0.35) return
            for (i in 0..3) cur[i] = filtered[i]
        }

        val s4 = MatOfPoint2f(Point(0.0, 0.0), Point(w, 0.0), Point(w, h), Point(0.0, h))
        val d4 = MatOfPoint2f(*smoothPts!!)
        val sm = Imgproc.getPerspectiveTransform(s4, d4)
        val d = DoubleArray(9)
        sm.get(0, 0, d)
        for (i in 0..8) outH[i] = d[i].toFloat()
        s4.release()
        d4.release()
        sm.release()
    }

    private fun pt(hd: DoubleArray, x: Double, y: Double): Point {
        val den = hd[6] * x + hd[7] * y + hd[8]
        val safe = if (abs(den) < 1e-6) 1e-6 else den
        return Point((hd[0] * x + hd[1] * y + hd[2]) / safe, (hd[3] * x + hd[4] * y + hd[5]) / safe)
    }
}
