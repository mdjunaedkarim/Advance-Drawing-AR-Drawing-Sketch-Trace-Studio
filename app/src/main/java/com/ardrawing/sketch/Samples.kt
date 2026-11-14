package com.ardrawing.sketch

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object Samples {
    const val COUNT = 5

    fun render(index: Int, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        c.scale(size / 600f, size / 600f)

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 9f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.parseColor("#18181B")
        }
        val fill = Paint(p).apply { style = Paint.Style.FILL }

        when (index) {
            0 -> house(c, p)
            1 -> flower(c, p)
            2 -> fish(c, p, fill)
            3 -> cat(c, p, fill)
            else -> star(c, p)
        }
        return bmp
    }

    private fun house(c: Canvas, p: Paint) {
        val roof = Path().apply { moveTo(120f, 280f); lineTo(300f, 120f); lineTo(480f, 280f); close() }
        c.drawPath(roof, p)
        c.drawRect(150f, 280f, 450f, 490f, p)
        c.drawRect(265f, 360f, 335f, 490f, p)
        c.drawRect(185f, 320f, 240f, 380f, p)
        c.drawRect(360f, 320f, 415f, 380f, p)
    }

    private fun flower(c: Canvas, p: Paint) {
        for (i in 0 until 6) {
            val a = i * PI / 3.0
            c.drawCircle(300f + (cos(a) * 85).toFloat(), 240f + (sin(a) * 85).toFloat(), 50f, p)
        }
        c.drawCircle(300f, 240f, 40f, p)
        c.drawLine(300f, 290f, 300f, 510f, p)
        c.drawOval(300f, 360f, 400f, 410f, p)
        c.drawOval(200f, 390f, 300f, 440f, p)
    }

    private fun fish(c: Canvas, p: Paint, fill: Paint) {
        c.drawOval(RectF(120f, 210f, 420f, 390f), p)
        val tail = Path().apply { moveTo(420f, 300f); lineTo(510f, 220f); lineTo(510f, 380f); close() }
        c.drawPath(tail, p)
        c.drawCircle(200f, 275f, 14f, fill)
        c.drawArc(RectF(230f, 250f, 310f, 350f), -60f, 120f, false, p)
    }

    private fun cat(c: Canvas, p: Paint, fill: Paint) {
        c.drawCircle(300f, 330f, 150f, p)
        val earL = Path().apply { moveTo(175f, 240f); lineTo(190f, 110f); lineTo(285f, 190f) }
        val earR = Path().apply { moveTo(425f, 240f); lineTo(410f, 110f); lineTo(315f, 190f) }
        c.drawPath(earL, p)
        c.drawPath(earR, p)
        c.drawCircle(250f, 305f, 15f, fill)
        c.drawCircle(350f, 305f, 15f, fill)
        val nose = Path().apply { moveTo(285f, 345f); lineTo(315f, 345f); lineTo(300f, 365f); close() }
        c.drawPath(nose, p)
        c.drawLine(140f, 340f, 225f, 350f, p)
        c.drawLine(140f, 375f, 225f, 365f, p)
        c.drawLine(460f, 340f, 375f, 350f, p)
        c.drawLine(460f, 375f, 375f, 365f, p)
    }

    private fun star(c: Canvas, p: Paint) {
        val path = Path()
        for (i in 0 until 10) {
            val a = -PI / 2 + i * PI / 5
            val r = if (i % 2 == 0) 220.0 else 95.0
            val x = 300f + (cos(a) * r).toFloat()
            val y = 310f + (sin(a) * r).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        c.drawPath(path, p)
    }
}
