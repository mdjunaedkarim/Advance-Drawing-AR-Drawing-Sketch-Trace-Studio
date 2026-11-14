package com.ardrawing.sketch

import android.graphics.Bitmap
import android.graphics.Color
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import java.util.concurrent.Executors

object ImageProcessor {
    private val worker = Executors.newSingleThreadExecutor()

    private val segmenter by lazy {
        val opts = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        Segmentation.getClient(opts)
    }

    fun removeBackgroundAsync(src: Bitmap, onDone: (Bitmap) -> Unit) {
        worker.execute {
            val out = try {
                removeWithSelfieSegmentation(src) ?: removeWithGrabCut(src)
            } catch (_: Exception) {
                src
            }
            onDone(out)
        }
    }

    private fun removeWithSelfieSegmentation(src: Bitmap): Bitmap? {
        val input = InputImage.fromBitmap(src, 0)
        val mask = Tasks.await(segmenter.process(input))
        val buf = mask.buffer
        val mw = mask.width
        val mh = mask.height
        buf.rewind()

        val total = mw * mh
        val probs = FloatArray(total)
        var fg = 0
        for (i in 0 until total) {
            val p = buf.float
            probs[i] = p
            if (p > 0.5f) fg++
        }
        if (fg < total * 0.03f) return null

        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        for (y in 0 until h) {
            val my = (y * mh / h).coerceIn(0, mh - 1)
            for (x in 0 until w) {
                val mx = (x * mw / w).coerceIn(0, mw - 1)
                val p = probs[my * mw + mx]
                val idx = y * w + x
                val c = px[idx]
                if (p < 0.35f) {
                    px[idx] = Color.TRANSPARENT
                } else if (p < 0.75f) {
                    val a = ((p - 0.35f) / 0.40f * Color.alpha(c)).toInt().coerceIn(0, 255)
                    px[idx] = (a shl 24) or (c and 0x00FFFFFF)
                }
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun removeWithGrabCut(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val rgba = Mat()
        val bgr = Mat()
        val mask = Mat(h, w, CvType.CV_8UC1)
        val bg = Mat()
        val fg = Mat()

        return try {
            Utils.bitmapToMat(src, rgba)
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            val mx = (w * 0.08).toInt().coerceAtLeast(2)
            val my = (h * 0.08).toInt().coerceAtLeast(2)
            val rect = Rect(mx, my, (w - 2 * mx).coerceAtLeast(10), (h - 2 * my).coerceAtLeast(10))
            Imgproc.grabCut(bgr, mask, rect, bg, fg, 3, Imgproc.GC_INIT_WITH_RECT)

            val mData = ByteArray(w * h)
            mask.get(0, 0, mData)
            val px = IntArray(w * h)
            src.getPixels(px, 0, w, 0, 0, w, h)
            for (i in px.indices) {
                val v = mData[i].toInt()
                if (v != Imgproc.GC_FGD && v != Imgproc.GC_PR_FGD) {
                    px[i] = Color.TRANSPARENT
                }
            }
            Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        } finally {
            rgba.release(); bgr.release(); mask.release(); bg.release(); fg.release()
        }
    }

    fun applyBrightness(src: Bitmap, brightness: Int): Bitmap {
        if (brightness == 0) return src
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val shift = (brightness * 1.8f).toInt()
        for (i in px.indices) {
            val c = px[i]
            val a = Color.alpha(c)
            if (a == 0) continue
            val r = (Color.red(c) + shift).coerceIn(0, 255)
            val g = (Color.green(c) + shift).coerceIn(0, 255)
            val b = (Color.blue(c) + shift).coerceIn(0, 255)
            px[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
}
