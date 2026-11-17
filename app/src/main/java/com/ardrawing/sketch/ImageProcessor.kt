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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object ImageProcessor {
    private val worker = Executors.newSingleThreadExecutor()

    private val segmenter by lazy {
        val opts = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        Segmentation.getClient(opts)
    }

    /**
     * Smart auto background removal:
     * 1. Checks border/corner uniformity to detect paper, white, or solid studio backdrops.
     * 2. If uniform/paper, extracts fine lines, text, and colored artwork cleanly without erasing details.
     * 3. Otherwise tries ML Kit Selfie Segmentation (if clear human subject) or border-guided GrabCut,
     *    with a safe border-color fallback if OpenCV is unavailable.
     */
    fun removeBackgroundAsync(src: Bitmap, onDone: (Bitmap) -> Unit) {
        worker.execute {
            val out = try {
                val border = analyzeBorder(src)
                if (border.isLightPaper) {
                    extractInkAndArt(src, border.r, border.g, border.b, sensitivity = 52)
                } else if (border.uniformity >= 0.55f) {
                    removeColorPerceptual(src, Color.rgb(border.r, border.g, border.b), tolerance = 38)
                } else {
                    removeWithSelfieSegmentation(src)
                        ?: removeWithGrabCut(src)
                        ?: removeColorPerceptual(src, Color.rgb(border.r, border.g, border.b), tolerance = 32)
                }
            } catch (_: Throwable) {
                try {
                    val border = analyzeBorder(src)
                    removeColorPerceptual(src, Color.rgb(border.r, border.g, border.b), tolerance = 35)
                } catch (_: Throwable) {
                    src
                }
            }
            onDone(out)
        }
    }

    /**
     * Ultra-crisp extraction that removes white/light paper and keeps dark lines, text, and ink strokes.
     */
    fun extractInkLinesAsync(src: Bitmap, sensitivity: Int = 55, onDone: (Bitmap) -> Unit) {
        worker.execute {
            val out = try {
                val border = analyzeBorder(src)
                val bgR = if (border.lum > 155) border.r else 250
                val bgG = if (border.lum > 155) border.g else 250
                val bgB = if (border.lum > 155) border.b else 250
                extractInkAndArt(src, bgR, bgG, bgB, sensitivity)
            } catch (_: Throwable) {
                src
            }
            onDone(out)
        }
    }

    /**
     * Removes any user-picked color across the image using perceptual Redmean color distance
     * and smooth anti-aliased edge feathering.
     */
    fun removePickedColorAsync(src: Bitmap, targetColor: Int, tolerance: Int, onDone: (Bitmap) -> Unit) {
        worker.execute {
            val out = try {
                removeColorPerceptual(src, targetColor, tolerance)
            } catch (_: Throwable) {
                src
            }
            onDone(out)
        }
    }

    private data class BorderInfo(
        val r: Int,
        val g: Int,
        val b: Int,
        val lum: Int,
        val uniformity: Float,
        val isLightPaper: Boolean
    )

    private fun analyzeBorder(src: Bitmap): BorderInfo {
        val w = src.width
        val h = src.height
        val samples = ArrayList<Int>(160)
        val stepX = (w / 40).coerceAtLeast(1)
        val stepY = (h / 40).coerceAtLeast(1)
        val padX = (w * 0.02f).toInt().coerceAtMost(4)
        val padY = (h * 0.02f).toInt().coerceAtMost(4)

        for (x in 0 until w step stepX) {
            samples.add(src.getPixel(x, padY.coerceAtMost(h - 1)))
            samples.add(src.getPixel(x, (h - 1 - padY).coerceAtLeast(0)))
        }
        for (y in 0 until h step stepY) {
            samples.add(src.getPixel(padX.coerceAtMost(w - 1), y))
            samples.add(src.getPixel((w - 1 - padX).coerceAtLeast(0), y))
        }

        if (samples.isEmpty()) return BorderInfo(255, 255, 255, 255, 1f, true)

        // Find dominant color bucket (quantized by 16) to ignore occasional border strokes
        val buckets = HashMap<Int, MutableList<Int>>()
        for (c in samples) {
            if (Color.alpha(c) < 30) continue
            val key = ((Color.red(c) shr 4) shl 8) or ((Color.green(c) shr 4) shl 4) or (Color.blue(c) shr 4)
            buckets.getOrPut(key) { ArrayList() }.add(c)
        }
        if (buckets.isEmpty()) return BorderInfo(255, 255, 255, 255, 1f, true)

        val topBucket = buckets.values.maxByOrNull { it.size } ?: samples
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        for (c in topBucket) {
            sumR += Color.red(c)
            sumG += Color.green(c)
            sumB += Color.blue(c)
        }
        val cnt = topBucket.size.coerceAtLeast(1)
        val r = (sumR / cnt).toInt()
        val g = (sumG / cnt).toInt()
        val b = (sumB / cnt).toInt()

        var matchCount = 0
        var opaqueCount = 0
        for (c in samples) {
            if (Color.alpha(c) < 30) continue
            opaqueCount++
            if (perceptualDist(Color.red(c), Color.green(c), Color.blue(c), r, g, b) < 75f) {
                matchCount++
            }
        }
        val uniformity = if (opaqueCount > 0) matchCount.toFloat() / opaqueCount else 0f
        val lum = (r * 299 + g * 587 + b * 114) / 1000
        val sat = maxOf(r, g, b) - minOf(r, g, b)
        val isLightPaper = lum > 170 && sat < 45 && uniformity >= 0.45f
        return BorderInfo(r, g, b, lum, uniformity, isLightPaper)
    }

    private fun extractInkAndArt(src: Bitmap, bgR: Int, bgG: Int, bgB: Int, sensitivity: Int): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        val bgLum = (bgR * 299 + bgG * 587 + bgB * 114) / 1000
        // Higher sensitivity removes slightly darker off-whites/shadows while protecting dark ink
        val sensFactor = sensitivity.coerceIn(10, 95)
        val clearCutoff = (bgLum - (18 + sensFactor * 0.72f)).coerceIn(115f, 245f)
        val solidCutoff = (clearCutoff - 55f).coerceIn(40f, clearCutoff - 15f)
        val colorDistLimit = 35f + sensFactor * 1.15f

        for (i in px.indices) {
            val c = px[i]
            val origA = Color.alpha(c)
            if (origA == 0) continue

            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            val lum = (r * 299 + g * 587 + b * 114) / 1000
            val sat = max(r, max(g, b)) - min(r, min(g, b))

            // Protect colorful ink/art (saturated colors) and dark lines/text
            if (sat > 38 && lum < bgLum - 12) {
                continue
            }

            val dist = perceptualDist(r, g, b, bgR, bgG, bgB)
            if (lum >= clearCutoff && dist <= colorDistLimit) {
                px[i] = Color.TRANSPARENT
            } else if (lum > solidCutoff && dist <= colorDistLimit * 1.35f) {
                val tLum = ((clearCutoff - lum) / (clearCutoff - solidCutoff)).coerceIn(0f, 1f)
                val tDist = (dist / (colorDistLimit * 1.35f)).coerceIn(0f, 1f)
                val keepRatio = max(tLum, tDist * 0.85f).coerceIn(0f, 1f)
                val newA = (origA * keepRatio).toInt().coerceIn(0, 255)
                px[i] = (newA shl 24) or (c and 0x00FFFFFF)
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun removeColorPerceptual(src: Bitmap, targetColor: Int, tolerance: Int): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        val tr = Color.red(targetColor)
        val tg = Color.green(targetColor)
        val tb = Color.blue(targetColor)

        val tol = tolerance.coerceIn(5, 100)
        val threshold = tol * 3.1f
        val feather = (threshold * 0.35f).coerceAtLeast(12f)
        val hardClear = (threshold - feather).coerceAtLeast(4f)
        val softEdge = threshold + feather

        for (i in px.indices) {
            val c = px[i]
            val origA = Color.alpha(c)
            if (origA == 0) continue

            val dist = perceptualDist(Color.red(c), Color.green(c), Color.blue(c), tr, tg, tb)
            if (dist <= hardClear) {
                px[i] = Color.TRANSPARENT
            } else if (dist < softEdge) {
                val ratio = ((dist - hardClear) / (softEdge - hardClear)).coerceIn(0f, 1f)
                val newA = (origA * ratio).toInt().coerceIn(0, 255)
                px[i] = (newA shl 24) or (c and 0x00FFFFFF)
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun perceptualDist(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Float {
        val rmean = (r1 + r2) * 0.5f
        val dr = (r1 - r2).toFloat()
        val dg = (g1 - g2).toFloat()
        val db = (b1 - b2).toFloat()
        val wr = 2f + rmean / 256f
        val wg = 4f
        val wb = 2f + (255f - rmean) / 256f
        return sqrt(wr * dr * dr + wg * dg * dg + wb * db * db)
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
            if (p > 0.55f) fg++
        }
        // Only accept selfie segmentation when a clear human/portrait subject is detected (not almost empty or all-foreground)
        if (fg < total * 0.06f || fg > total * 0.92f) return null

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
                } else if (p < 0.78f) {
                    val a = ((p - 0.35f) / 0.43f * Color.alpha(c)).toInt().coerceIn(0, 255)
                    px[idx] = (a shl 24) or (c and 0x00FFFFFF)
                }
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun removeWithGrabCut(src: Bitmap): Bitmap? {
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
            val mx = (w * 0.06).toInt().coerceAtLeast(2)
            val my = (h * 0.06).toInt().coerceAtLeast(2)
            val rect = Rect(mx, my, (w - 2 * mx).coerceAtLeast(10), (h - 2 * my).coerceAtLeast(10))
            Imgproc.grabCut(bgr, mask, rect, bg, fg, 4, Imgproc.GC_INIT_WITH_RECT)

            val mData = ByteArray(w * h)
            mask.get(0, 0, mData)
            var fgCount = 0
            for (b in mData) {
                val v = b.toInt()
                if (v == Imgproc.GC_FGD || v == Imgproc.GC_PR_FGD) fgCount++
            }
            if (fgCount < mData.size * 0.03) return null

            val px = IntArray(w * h)
            src.getPixels(px, 0, w, 0, 0, w, h)
            for (i in px.indices) {
                val v = mData[i].toInt()
                if (v != Imgproc.GC_FGD && v != Imgproc.GC_PR_FGD) {
                    px[i] = Color.TRANSPARENT
                }
            }
            Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) {
            null
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

    /**
     * Strictly verifies that a bitmap is a genuine black-and-white sketch / line-art drawing
     * on white/light paper. Rejects colored pictures, colored screenshots, dark posters,
     * and muddy grayscale photos so colored artwork is NEVER converted into fake B&W.
     */
    fun isLikelyBwDrawing(src: Bitmap): Boolean {
        val w = src.width
        val h = src.height
        if (w < 80 || h < 80) return false
        val stepX = (w / 48).coerceAtLeast(1)
        val stepY = (h / 48).coerceAtLeast(1)
        var total = 0
        var vivid = 0
        var tintColor = 0
        var lightPaper = 0
        var darkInk = 0
        var midGray = 0

        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val c = src.getPixel(x, y)
                val a = Color.alpha(c)
                if (a < 30) {
                    lightPaper++
                } else {
                    val r = Color.red(c)
                    val g = Color.green(c)
                    val b = Color.blue(c)
                    val lum = (r * 299 + g * 587 + b * 114) / 1000
                    val sat = max(r, max(g, b)) - min(r, min(g, b))
                    if (sat > 30 && lum < 238) vivid++
                    if (sat > 18 && lum in 40..235) tintColor++
                    if (lum >= 198 && sat <= 24) lightPaper++
                    else if (lum < 95 && sat <= 36) darkInk++
                    else if (lum in 95..185 && sat <= 28) midGray++
                }
                total++
                x += stepX
            }
            y += stepY
        }
        if (total == 0) return false
        val vividRatio = vivid.toFloat() / total
        val tintRatio = tintColor.toFloat() / total
        val lightRatio = lightPaper.toFloat() / total
        val darkRatio = darkInk.toFloat() / total
        val midRatio = midGray.toFloat() / total

        // Reject colored photos/renders strictly
        if (vividRatio > 0.045f || tintRatio > 0.10f) return false
        // Require real white/light sketch paper background
        if (lightRatio < 0.42f) return false
        // Reject blank white images and dark black screenshots/posters
        if (darkRatio < 0.008f || darkRatio > 0.50f) return false
        // Reject muddy grayscale photographs
        if (midRatio > 0.45f) return false
        return true
    }

    /**
     * Cuts out the white/light paper background cleanly to Color.TRANSPARENT, keeps crisp pure
     * black (#000000) sketch ink lines, and trims excess empty blank outer margins so the
     * drawing sits cleanly centered without awkward border whitespace.
     */
    fun toCleanBwSketch(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val n = w * h
        val px = IntArray(n)
        src.getPixels(px, 0, w, 0, 0, w, h)

        val lums = IntArray(n)
        val sats = IntArray(n)
        var hasColoredFills = false

        for (i in 0 until n) {
            val c = px[i]
            val a = Color.alpha(c)
            if (a < 20) {
                lums[i] = 255
                sats[i] = 0
            } else {
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                val lum = (r * 299 + g * 587 + b * 114) / 1000
                val sat = max(r, max(g, b)) - min(r, min(g, b))
                lums[i] = lum
                sats[i] = sat
                if (sat > 18 && lum in 30..235) {
                    hasColoredFills = true
                }
            }
        }

        val integral = if (hasColoredFills) {
            val stride = w + 1
            val satGrid = IntArray((h + 1) * stride)
            for (y in 0 until h) {
                var rowSum = 0
                val yOff = y * w
                val gCur = (y + 1) * stride
                val gPrev = y * stride
                for (x in 0 until w) {
                    rowSum += lums[yOff + x]
                    satGrid[gCur + x + 1] = satGrid[gPrev + x + 1] + rowSum
                }
            }
            satGrid
        } else null

        val out = IntArray(n)
        val radius = 3
        val stride = w + 1
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1

        for (y in 0 until h) {
            val y1 = (y - radius).coerceAtLeast(0)
            val y2 = (y + radius).coerceAtMost(h - 1)
            val rowOff = y * w
            for (x in 0 until w) {
                val idx = rowOff + x
                val origA = Color.alpha(px[idx])
                if (origA < 20) {
                    out[idx] = Color.TRANSPARENT
                    continue
                }

                val lum = lums[idx]
                val sat = sats[idx]

                val outPixel = if (sat > 16) {
                    val keepAsLine = if (lum <= 42) {
                        true
                    } else if (integral != null && lum < 210) {
                        val x1 = (x - radius).coerceAtLeast(0)
                        val x2 = (x + radius).coerceAtMost(w - 1)
                        val area = (x2 - x1 + 1) * (y2 - y1 + 1)
                        val sum = integral[(y2 + 1) * stride + (x2 + 1)] -
                            integral[y1 * stride + (x2 + 1)] -
                            integral[(y2 + 1) * stride + x1] +
                            integral[y1 * stride + x1]
                        val localMean = sum / area
                        (localMean - lum) >= 12
                    } else {
                        false
                    }

                    if (keepAsLine) {
                        val edgeAlpha = if (lum <= 70) 255 else ((215 - lum) * 255 / 145).coerceIn(80, 255)
                        val finalA = (edgeAlpha * origA / 255).coerceIn(0, 255)
                        (finalA shl 24)
                    } else {
                        Color.TRANSPARENT
                    }
                } else {
                    if (lum >= 220) {
                        Color.TRANSPARENT
                    } else if (lum <= 135) {
                        (origA shl 24)
                    } else {
                        val lineA = ((220 - lum) * 255 / 85).coerceIn(0, 255)
                        val finalA = (lineA * origA / 255).coerceIn(0, 255)
                        (finalA shl 24)
                    }
                }
                out[idx] = outPixel
                if ((outPixel ushr 24) >= 48) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        val fullBmp = Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
        if (maxX > minX && maxY > minY) {
            val bw = maxX - minX + 1
            val bh = maxY - minY + 1
            // Trim excess outer blank margins while preserving balanced 5% breathing space
            if (bw >= (w * 0.16f).toInt() && bh >= (h * 0.16f).toInt() &&
                (bw < (w * 0.88f).toInt() || bh < (h * 0.88f).toInt())
            ) {
                val padX = (bw * 0.05f).toInt().coerceAtLeast(4)
                val padY = (bh * 0.05f).toInt().coerceAtLeast(4)
                val cx1 = (minX - padX).coerceAtLeast(0)
                val cy1 = (minY - padY).coerceAtLeast(0)
                val cx2 = (maxX + padX).coerceAtMost(w - 1)
                val cy2 = (maxY + padY).coerceAtMost(h - 1)
                val cw = (cx2 - cx1 + 1).coerceAtLeast(1)
                val ch = (cy2 - cy1 + 1).coerceAtLeast(1)
                if (cw < w || ch < h) {
                    return Bitmap.createBitmap(fullBmp, cx1, cy1, cw, ch)
                }
            }
        }
        return fullBmp
    }
}

