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
            270 -> Core.rotate(small, up,
