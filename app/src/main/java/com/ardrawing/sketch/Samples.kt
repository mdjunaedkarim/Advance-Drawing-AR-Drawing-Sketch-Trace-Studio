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

    enum class Cat(val key: String) {
        ALL("cat_all"), ANIME("cat_anime"), SCENERY("cat_scenery"),
        VEHICLES("cat_vehicles"), PEOPLE("cat_people"),
        NATURE("cat_nature"), ANIMALS("cat_animals")
    }

    data class Item(val id: Int, val title: String, val cat: Cat)

    val items = listOf(
        // Anime & Cartoon (0..13)
        Item(0, "Shinobi Ninja", Cat.ANIME), Item(1, "Anime Hero", Cat.ANIME),
        Item(2, "Magical Girl", Cat.ANIME), Item(3, "Chibi Boy", Cat.ANIME),
        Item(4, "Samurai Mask", Cat.ANIME), Item(5, "Neko Girl", Cat.ANIME),
        Item(6, "Cute Robot", Cat.ANIME), Item(7, "Mecha Helmet", Cat.ANIME),
        Item(8, "Panda Cute", Cat.ANIME), Item(9, "Teddy Bear", Cat.ANIME),
        Item(10, "Baby Dino", Cat.ANIME), Item(11, "Wizard Hat", Cat.ANIME),
        Item(12, "Kawaii Bunny", Cat.ANIME), Item(13, "Ninja Kunai", Cat.ANIME),
        // Scenery & Boats (14..21)
        Item(14, "River & Boat", Cat.SCENERY), Item(15, "Mountain Sun", Cat.SCENERY),
        Item(16, "Village Hut", Cat.SCENERY), Item(17, "Sailboat Sea", Cat.SCENERY),
        Item(18, "River Bridge", Cat.SCENERY), Item(19, "Lighthouse", Cat.SCENERY),
        Item(20, "Camp Night", Cat.SCENERY), Item(21, "Island Palm", Cat.SCENERY),
        // Vehicles (22..29)
        Item(22, "Sports Car", Cat.VEHICLES), Item(23, "Racing Bike", Cat.VEHICLES),
        Item(24, "Airplane", Cat.VEHICLES), Item(25, "Space Rocket", Cat.VEHICLES),
        Item(26, "Bicycle", Cat.VEHICLES), Item(27, "Train Engine", Cat.VEHICLES),
        Item(28, "Cargo Ship", Cat.VEHICLES), Item(29, "Mini Jeep", Cat.VEHICLES),
        // People (30..35)
        Item(30, "Girl Portrait", Cat.PEOPLE), Item(31, "Boy Portrait", Cat.PEOPLE),
        Item(32, "Princess", Cat.PEOPLE), Item(33, "King Crown", Cat.PEOPLE),
        Item(34, "Dancer", Cat.PEOPLE), Item(35, "Happy Kid", Cat.PEOPLE),
        // Flowers & Fruits (36..45)
        Item(36, "Rose", Cat.NATURE), Item(37, "Lotus", Cat.NATURE),
        Item(38, "Sunflower", Cat.NATURE), Item(39, "Tulip", Cat.NATURE),
        Item(40, "Hibiscus", Cat.NATURE), Item(41, "Mango", Cat.NATURE),
        Item(42, "Apple", Cat.NATURE), Item(43, "Pineapple", Cat.NATURE),
        Item(44, "Grapes", Cat.NATURE), Item(45, "Watermelon", Cat.NATURE),
        // Birds & Animals (46..53)
        Item(46, "Parrot", Cat.ANIMALS), Item(47, "Peacock", Cat.ANIMALS),
        Item(48, "Flying Dove", Cat.ANIMALS), Item(49, "Cute Owl", Cat.ANIMALS),
        Item(50, "Butterfly", Cat.ANIMALS), Item(51, "Cute Cat", Cat.ANIMALS),
        Item(52, "Puppy", Cat.ANIMALS), Item(53, "Goldfish", Cat.ANIMALS)
    )

    fun render(id: Int, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        c.scale(size / 600f, size / 600f)

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.parseColor("#18181B")
        }
        val f = Paint(p).apply { style = Paint.Style.FILL }

        when (id) {
            0 -> drawNinja(c, p, f)
            1 -> drawAnimeHero(c, p, f)
            2 -> drawMagicalGirl(c, p, f)
            3 -> drawChibi(c, p, f)
            4 -> drawSamurai(c, p)
            5 -> drawNekoGirl(c, p, f)
            6 -> drawRobot(c, p, f)
            7 -> drawMecha(c, p)
            8 -> drawPanda(c, p, f)
            9 -> drawBear(c, p, f)
            10 -> drawDino(c, p, f)
            11 -> drawWizard(c, p)
            12 -> drawBunny(c, p, f)
            13 -> drawKunai(c, p)
            14 -> drawRiverBoat(c, p)
            15 -> drawMountainSun(c, p)
            16 -> drawVillageHut(c, p)
            17 -> drawSailboat(c, p)
            18 -> drawBridge(c, p)
            19 -> drawLighthouse(c, p)
            20 -> drawCampNight(c, p)
            21 -> drawIsland(c, p)
            22 -> drawSportsCar(c, p)
            23 -> drawMotorbike(c, p)
            24 -> drawAirplane(c, p)
            25 -> drawRocket(c, p)
            26 -> drawBicycle(c, p)
            27 -> drawTrain(c, p)
            28 -> drawShip(c, p)
            29 -> drawJeep(c, p)
            30 -> drawGirlPortrait(c, p, f)
            31 -> drawBoyPortrait(c, p, f)
            32 -> drawPrincess(c, p, f)
            33 -> drawKing(c, p, f)
            34 -> drawDancer(c, p, f)
            35 -> drawHappyKid(c, p, f)
            36 -> drawRose(c, p)
            37 -> drawLotus(c, p)
            38 -> drawSunflower(c, p)
            39 -> drawTulip(c, p)
            40 -> drawHibiscus(c, p)
            41 -> drawMango(c, p)
            42 -> drawApple(c, p)
            43 -> drawPineapple(c, p)
            44 -> drawGrapes(c, p)
            45 -> drawWatermelon(c, p, f)
            46 -> drawParrot(c, p, f)
            47 -> drawPeacock(c, p, f)
            48 -> drawDove(c, p, f)
            49 -> drawOwl(c, p, f)
            50 -> drawButterfly(c, p)
            51 -> drawCat(c, p, f)
            52 -> drawPuppy(c, p, f)
            else -> drawFish(c, p, f)
        }
        return bmp
    }

    private fun poly(c: Canvas, p: Paint, close: Boolean, vararg pts: Float) {
        val path = Path()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i + 1 < pts.size) { path.lineTo(pts[i], pts[i + 1]); i += 2 }
        if (close) path.close()
        c.drawPath(path, p)
    }

    private fun qCurve(c: Canvas, p: Paint, x1: Float, y1: Float, cx: Float, cy: Float, x2: Float, y2: Float) {
        c.drawPath(Path().apply { moveTo(x1, y1); quadTo(cx, cy, x2, y2) }, p)
    }

    private fun drawNinja(c: Canvas, p: Paint, f: Paint) {
        // Spiky hair + headband + ninja mask
        poly(c, p, false, 150f, 240f, 110f, 160f, 195f, 180f, 220f, 95f, 295f, 165f, 355f, 90f, 390f, 175f, 480f, 150f, 445f, 240f)
        c.drawRect(160f, 220f, 440f, 275f, p)
        c.drawRoundRect(RectF(230f, 232f, 370f, 265f), 8f, 8f, p)
        c.drawLine(255f, 248f, 345f, 248f, p)
        qCurve(c, p, 175f, 275f, 185f, 435f, 300f, 465f)
        qCurve(c, p, 425f, 275f, 415f, 435f, 300f, 465f)
        // Mask + eyes
        qCurve(c, p, 185f, 345f, 300f, 330f, 415f, 345f)
        c.drawOval(RectF(215f, 295f, 270f, 325f), p); c.drawCircle(245f, 310f, 8f, f)
        c.drawOval(RectF(330f, 295f, 385f, 325f), p); c.drawCircle(355f, 310f, 8f, f)
        poly(c, p, false, 200f, 440f, 160f, 540f, 440f, 540f, 400f, 440f)
        // Headband ribbons
        poly(c, p, false, 440f, 245f, 530f, 220f, 510f, 265f, 440f, 265f)
    }

    private fun drawAnimeHero(c: Canvas, p: Paint, f: Paint) {
        poly(c, p, false, 160f, 260f, 125f, 170f, 210f, 190f, 270f, 100f, 325f, 185f, 415f, 115f, 430f, 200f, 475f, 250f, 430f, 280f)
        qCurve(c, p, 180f, 260f, 195f, 430f, 300f, 455f)
        qCurve(c, p, 420f, 260f, 405f, 430f, 300f, 455f)
        c.drawOval(RectF(215f, 285f, 272f, 330f), p); c.drawCircle(246f, 310f, 10f, f)
        c.drawOval(RectF(328f, 285f, 385f, 330f), p); c.drawCircle(354f, 310f, 10f, f)
        c.drawLine(215f, 272f, 275f, 282f, p); c.drawLine(325f, 282f, 385f, 272f, p)
        c.drawLine(295f, 345f, 302f, 365f, p)
        qCurve(c, p, 265f, 400f, 300f, 420f, 335f, 400f)
        poly(c, p, false, 245f, 445f, 220f, 540f, 150f, 540f)
        poly(c, p, false, 355f, 445f, 380f, 540f, 450f, 540f)
    }

    private fun drawMagicalGirl(c: Canvas, p: Paint, f: Paint) {
        c.drawCircle(300f, 260f, 115f, p)
        qCurve(c, p, 185f, 240f, 110f, 290f, 120f, 440f)
        qCurve(c, p, 415f, 240f, 490f, 290f, 480f, 440f)
        c.drawCircle(255f, 265f, 18f, p); c.drawCircle(255f, 265f, 8f, f)
        c.drawCircle(345f, 265f, 18f, p); c.drawCircle(345f, 265f, 8f, f)
        qCurve(c, p, 275f, 325f, 300f, 345f, 325f, 325f)
        poly(c, p, true, 235f, 385f, 365f, 385f, 400f, 530f, 200f, 530f)
        // Star wand
        c.drawLine(460f, 480f, 520f, 220f, p)
        c.drawCircle(522f, 200f, 25f, p)
    }

    private fun drawChibi(c: Canvas, p: Paint, f: Paint) {
        c.drawOval(RectF(170f, 130f, 430f, 350f), p)
        c.drawCircle(245f, 245f, 22f, p); c.drawCircle(248f, 242f, 10f, f)
        c.drawCircle(355f, 245f, 22f, p); c.drawCircle(352f, 242f, 10f, f)
        qCurve(c, p, 275f, 295f, 300f, 315f, 325f, 295f)
        c.drawRoundRect(RectF(220f, 350f, 380f, 500f), 30f, 30f, p)
        c.drawLine(255f, 500f, 255f, 550f, p); c.drawLine(345f, 500f, 345f, 550f, p)
    }

    private fun drawSamurai(c: Canvas, p: Paint) {
        poly(c, p, true, 160f, 250f, 200f, 150f, 400f, 150f, 440f, 250f, 470f, 320f, 130f, 320f)
        poly(c, p, false, 300f, 150f, 230f, 70f, 300f, 110f, 370f, 70f, 300f, 150f)
        c.drawRect(210f, 280f, 390f, 430f, p)
        c.drawLine(235f, 330f, 280f, 340f, p); c.drawLine(365f, 330f, 320f, 340f, p)
        poly(c, p, false, 235f, 385f, 300f, 405f, 365f, 385f)
    }

    private fun drawNekoGirl(c: Canvas, p: Paint, f: Paint) {
        c.drawCircle(300f, 290f, 115f, p)
        poly(c, p, true, 200f, 210f, 180f, 110f, 265f, 175f)
        poly(c, p, true, 400f, 210f, 420f, 110f, 335f, 175f)
        c.drawCircle(255f, 285f, 16f, p); c.drawCircle(255f, 285f, 7f, f)
        c.drawCircle(345f, 285f, 16f, p); c.drawCircle(345f, 285f, 7f, f)
        qCurve(c, p, 280f, 340f, 300f, 360f, 320f, 340f)
        poly(c, p, false, 230f, 405f, 200f, 530f, 400f, 530f, 370f, 405f)
    }

    private fun drawRobot(c: Canvas, p: Paint, f: Paint) {
        c.drawLine(300f, 80f, 300f, 140f, p); c.drawCircle(300f, 70f, 14f, f)
        c.drawRoundRect(RectF(180f, 140f, 420f, 290f), 28f, 28f, p)
        c.drawCircle(245f, 210f, 20f, p); c.drawCircle(355f, 210f, 20f, p)
        c.drawRect(250f, 245f, 350f, 265f, p)
        c.drawRoundRect(RectF(190f, 310f, 410f, 470f), 20f, 20f, p)
        c.drawRect(130f, 330f, 190f, 430f, p); c.drawRect(410f, 330f, 470f, 430f, p)
        c.drawRect(225f, 470f, 275f, 540f, p); c.drawRect(325f, 470f, 375f, 540f, p)
    }

    private fun drawMecha(c: Canvas, p: Paint) {
        poly(c, p, true, 170f, 220f, 220f, 130f, 380f, 130f, 430f, 220f, 390f, 430f, 210f, 430f)
        poly(c, p, false, 300f, 130f, 140f, 70f, 270f, 160f)
        poly(c, p, false, 300f, 130f, 460f, 70f, 330f, 160f)
        poly(c, p, true, 215f, 240f, 285f, 260f, 270f, 290f, 220f, 280f)
        poly(c, p, true, 385f, 240f, 315f, 260f, 330f, 290f, 380f, 280f)
        poly(c, p, true, 240f, 350f, 360f, 350f, 340f, 400f, 260f, 400f)
    }

    private fun drawPanda(c: Canvas, p: Paint, f: Paint) {
        c.drawCircle(185f, 150f, 45f, f); c.drawCircle(415f, 150f, 45f, f)
        c.drawCircle(300f, 240f, 125f, p)
        c.drawOval(RectF(215f, 200f, 275f, 265f), f); c.drawOval(RectF(325f, 200f, 385f, 265f), f)
        c.drawCircle(295f, 275f, 14f, f)
        qCurve(c, p, 270f, 305f, 300f, 325f, 330f, 305f)
        c.drawOval(RectF(195f, 355f, 405f, 520f), p)
    }

    private fun drawBear(c: Canvas, p: Paint, f: Paint) {
        c.drawCircle(185f, 145f, 42f, p); c.drawCircle(415f, 145f, 42f, p)
        c.drawCircle(300f, 240f, 120f, p)
        c.drawCircle(250f, 215f, 12f, f); c.drawCircle(350f, 215f, 12f, f)
        c.drawOval(RectF(260f, 245f, 340f, 305f), p); c.drawCircle(300f, 265f, 12f, f)
        c.drawOval(RectF(190f, 355f, 410f, 530f), p)
    }

    private fun drawDino(c: Canvas, p: Paint, f: Paint) {
        c.drawOval(RectF(170f, 140f, 390f, 310f), p)
        c.drawCircle(240f, 200f, 12f, f)
        qCurve(c, p, 175f, 250f, 230f, 275f, 285f, 255f)
        qCurve(c, p, 240f, 310f, 220f, 470f, 380f, 480f)
        qCurve(c, p, 350f, 290f, 490f, 360f, 480f, 480f)
        c.drawRect(250f, 470f, 290f, 535f, p); c.drawRect(330f, 470f, 370f, 535f, p)
    }

    private fun drawWizard(c: Canvas, p: Paint) {
        poly(c, p, true, 160f, 320f, 300f, 90f, 440f, 320f)
        c.drawOval(RectF(95f, 290f, 505f, 350f), p)
        c.drawCircle(300f, 430f, 80f, p)
        c.drawLine(270f, 410f, 270f, 425f, p); c.drawLine(330f, 410f, 330f, 425f, p)
        qCurve(c, p, 275f, 460f, 300f, 480f, 325f, 460f)
    }

    private fun drawBunny(c: Canvas, p: Paint, f: Paint) {
        c.drawOval(RectF(205f, 70f, 270f, 240f), p); c.drawOval(RectF(330f, 70f, 395f, 240f), p)
        c.drawCircle(300f, 320f, 115f, p)
        c.drawCircle(255f, 300f, 12f, f); c.drawCircle(345f, 300f, 12f, f)
        c.drawCircle(300f, 335f, 10f, f)
        qCurve(c, p, 270f, 365f, 300f, 385f, 330f, 365f)
        c.drawOval(RectF(220f, 430f, 380f, 545f), p)
    }

    private fun drawKunai(c: Canvas, p: Paint) {
        poly(c, p, true, 300f, 80f, 365f, 230f, 300f, 330f, 235f, 230f)
        c.drawLine(300f, 80f, 300f, 330f, p)
        c.drawRect(285f, 330f, 315f, 450f, p)
        c.drawCircle(300f, 485f, 35f, p)
    }

    private fun drawRiverBoat(c: Canvas, p: Paint) {
        // Mountains + sun + river + wooden boat + oar
        poly(c, p, false, 60f, 250f, 170f, 120f, 280f, 240f, 390f, 140f, 540f, 250f)
        c.drawCircle(450f, 120f, 40f, p)
        c.drawLine(60f, 250f, 540f, 250f, p)
        qCurve(c, p, 200f, 250f, 120f, 370f, 60f, 520f)
        qCurve(c, p, 390f, 250f, 460f, 370f, 540f, 520f)
        // Boat
        poly(c, p, true, 165f, 380f, 435f, 380f, 385f, 435f, 215f, 435f)
        qCurve(c, p, 240f, 380f, 300f, 315f, 360f, 380f)
        c.drawLine(320f, 395f, 425f, 470f, p)
    }

    private fun drawMountainSun(c: Canvas, p: Paint) {
        poly(c, p, false, 50f, 420f, 200f, 160f, 340f, 420f)
        poly(c, p, false, 260f, 420f, 400f, 190f, 550f, 420f)
        c.drawCircle(300f, 160f, 55f, p)
        c.drawLine(50f, 420f, 550f, 420f, p)
        c.drawLine(140f, 460f, 460f, 460f, p); c.drawLine(200f, 500f, 400f, 500f, p)
    }

    private fun drawVillageHut(c: Canvas, p: Paint) {
        poly(c, p, true, 120f, 300f, 270f, 160f, 420f, 300f)
        c.drawRect(150f, 300f, 390f, 470f, p); c.drawRect(240f, 360f, 300f, 470f, p)
        qCurve(c, p, 450f, 470f, 470f, 320f, 440f, 190f)
        qCurve(c, p, 440f, 190f, 370f, 160f, 350f, 210f)
        qCurve(c, p, 440f, 190f, 510f, 160f, 530f, 220f)
        c.drawLine(60f, 470f, 540f, 470f, p)
    }

    private fun drawSailboat(c: Canvas, p: Paint) {
        poly(c, p, true, 120f, 390f, 480f, 390f, 430f, 460f, 170f, 460f)
        c.drawLine(290f, 390f, 290f, 120f, p)
        poly(c, p, true, 290f, 130f, 440f, 360f, 290f, 360f)
        poly(c, p, true, 280f, 160f, 160f, 360f, 280f, 360f)
        qCurve(c, p, 70f, 485f, 180f, 465f, 300f, 485f)
        qCurve(c, p, 300f, 485f, 420f, 505f, 530f, 485f)
    }

    private fun drawBridge(c: Canvas, p: Paint) {
        c.drawLine(60f, 280f, 540f, 280f, p)
        qCurve(c, p, 80f, 400f, 300f, 260f, 520f, 400f)
        for (x in 100..500 step 50) c.drawLine(x.toFloat(), 280f, x.toFloat(), 320f, p)
        c.drawLine(60f, 320f, 540f, 320f, p)
        c.drawLine(120f, 450f, 480f, 450f, p)
    }

    private fun drawLighthouse(c: Canvas, p: Paint) {
        poly(c, p, true, 250f, 470f, 270f, 210f, 330f, 210f, 350f, 470f)
        c.drawRect(265f, 160f, 335f, 210f, p)
        poly(c, p, true, 255f, 160f, 300f, 110f, 345f, 160f)
        c.drawLine(345f, 175f, 500f, 130f, p); c.drawLine(345f, 195f, 500f, 235f, p)
        c.drawLine(90f, 470f, 510f, 470f, p)
    }

    private fun drawCampNight(c: Canvas, p: Paint) {
        poly(c, p, true, 120f, 440f, 300f, 210f, 480f, 440f)
        poly(c, p, true, 240f, 440f, 300f, 320f, 360f, 440f)
        c.drawCircle(450f, 130f, 45f, p)
        c.drawLine(80f, 440f, 520f, 440f, p)
    }

    private fun drawIsland(c: Canvas, p: Paint) {
        qCurve(c, p, 120f, 430f, 300f, 350f, 480f, 430f)
        qCurve(c, p, 290f, 390f, 310f, 250f, 270f, 170f)
        qCurve(c, p, 270f, 170f, 180f, 150f, 150f, 210f)
        qCurve(c, p, 270f, 170f, 360f, 140f, 400f, 210f)
        c.drawCircle(460f, 130f, 40f, p)
        c.drawLine(60f, 450f, 540f, 450f, p)
    }

    private fun drawSportsCar(c: Canvas, p: Paint) {
        poly(c, p, false, 90f, 360f, 110f, 295f, 200f, 275f, 270f, 210f, 410f, 210f, 475f, 285f, 520f, 305f, 520f, 360f)
        c.drawLine(90f, 360f, 150f, 360f, p); c.drawLine(230f, 360f, 370f, 360f, p); c.drawLine(450f, 360f, 520f, 360f, p)
        c.drawCircle(190f, 360f, 42f, p); c.drawCircle(190f, 360f, 18f, p)
        c.drawCircle(410f, 360f, 42f, p); c.drawCircle(410f, 360f, 18f, p)
        poly(c, p, true, 265f, 225f, 395f, 225f, 445f, 280f, 220f, 280f)
    }

    private fun drawMotorbike(c: Canvas, p: Paint) {
        c.drawCircle(155f, 385f, 60f, p); c.drawCircle(155f, 385f, 22f, p)
        c.drawCircle(445f, 385f, 60f, p); c.drawCircle(445f, 385f, 22f, p)
        poly(c, p, false, 155f, 385f, 250f, 280f, 365f, 275f, 445f, 385f)
        c.drawOval(RectF(240f, 240f, 375f, 295f), p)
        c.drawLine(350f, 250f, 395f, 205f, p)
    }

    private fun drawAirplane(c: Canvas, p: Paint) {
        c.drawOval(RectF(110f, 250f, 510f, 335f), p)
        poly(c, p, true, 260f, 275f, 320f, 120f, 370f, 120f, 340f, 275f)
        poly(c, p, true, 260f, 310f, 320f, 470f, 370f, 470f, 340f, 310f)
        poly(c, p, true, 120f, 285f, 90f, 185f, 145f, 185f, 175f, 275f)
    }

    private fun drawRocket(c: Canvas, p: Paint) {
        qCurve(c, p, 300f, 70f, 210f, 200f, 235f, 390f)
        qCurve(c, p, 300f, 70f, 390f, 200f, 365f, 390f)
        c.drawLine(235f, 390f, 365f, 390f, p)
        c.drawCircle(300f, 230f, 38f, p)
        poly(c, p, true, 235f, 330f, 170f, 420f, 235f, 390f)
        poly(c, p, true, 365f, 330f, 430f, 420f, 365f, 390f)
        poly(c, p, false, 260f, 390f, 300f, 510f, 340f, 390f)
    }

    private fun drawBicycle(c: Canvas, p: Paint) {
        c.drawCircle(165f, 380f, 70f, p); c.drawCircle(435f, 380f, 70f, p)
        poly(c, p, true, 165f, 380f, 275f, 380f, 395f, 260f, 240f, 260f)
        c.drawLine(240f, 225f, 275f, 380f, p)
        c.drawLine(385f, 220f, 435f, 380f, p)
        c.drawLine(215f, 225f, 265f, 225f, p); c.drawLine(365f, 220f, 415f, 220f, p)
    }

    private fun drawTrain(c: Canvas, p: Paint) {
        c.drawRect(130f, 230f, 360f, 390f, p)
        c.drawRect(360f, 170f, 480f, 390f, p)
        c.drawRect(160f, 160f, 205f, 230f, p)
        c.drawCircle(185f, 425f, 35f, p); c.drawCircle(285f, 425f, 35f, p); c.drawCircle(415f, 425f, 45f, p)
        c.drawLine(80f, 470f, 520f, 470f, p)
    }

    private fun drawShip(c: Canvas, p: Paint) {
        poly(c, p, true, 90f, 350f, 510f, 350f, 450f, 440f, 140f, 440f)
        c.drawRect(180f, 260f, 410f, 350f, p)
        c.drawRect(240f, 190f, 340f, 260f, p)
        c.drawRect(265f, 130f, 305f, 190f, p)
        c.drawLine(70f, 465f, 530f, 465f, p)
    }

    private fun drawJeep(c: Canvas, p: Paint) {
        poly(c, p, true, 120f, 370f, 120f, 250f, 220f, 250f, 250f, 185f, 445f, 185f, 475f, 285f, 475f, 370f)
        c.drawCircle(195f, 385f, 48f, p); c.drawCircle(405f, 385f, 48f, p)
        c.drawRect(260f, 205f, 345f, 265f, p); c.drawRect(365f, 205f, 435f, 265f, p)
    }

    private fun drawGirlPortrait(c: Canvas, p: Paint, f: Paint) {
        qCurve(c, p, 175f, 240f, 140f, 420f, 190f, 510f)
        qCurve(c, p, 425f, 240f, 460f, 420f, 410f, 510f)
        c.drawOval(RectF(200f, 140f, 400f, 390f), p)
        qCurve(c, p, 200f, 230f, 300f, 160f, 400f, 230f)
        c.drawCircle(255f, 265f, 10f, f); c.drawCircle(345f, 265f, 10f, f)
        qCurve(c, p, 270f, 330f, 300f, 355f, 330f, 330f)
    }

    private fun drawBoyPortrait(c: Canvas, p: Paint, f: Paint) {
        c.drawOval(RectF(200f, 150f, 400f, 400f), p)
        poly(c, p, false, 195f, 230f, 210f, 130f, 300f, 110f, 390f, 130f, 405f, 230f)
        c.drawCircle(255f, 265f, 10f, f); c.drawCircle(345f, 265f, 10f, f)
        qCurve(c, p, 265f, 335f, 300f, 360f, 335f, 335f)
        poly(c, p, false, 235f, 400f, 210f, 520f, 390f, 520f, 365f, 400f)
    }

    private fun drawPrincess(c: Canvas, p: Paint, f: Paint) {
        poly(c, p, true, 230f, 160f, 245f, 95f, 280f, 135f, 300f, 80f, 320f, 135f, 355f, 95f, 370f, 160f)
        c.drawCircle(300f, 270f, 110f, p)
        c.drawCircle(255f, 265f, 10f, f); c.drawCircle(345f, 265f, 10f, f)
        qCurve(c, p, 275f, 320f, 300f, 340f, 325f, 320f)
        poly(c, p, true, 230f, 380f, 370f, 380f, 420f, 535f, 180f, 535f)
    }

    private fun drawKing(c: Canvas, p: Paint, f: Paint) {
        poly(c, p, true, 210f, 170f, 220f, 85f, 265f, 135f, 300f, 70f, 335f, 135f, 380f, 85f, 390f, 170f)
        c.drawCircle(300f, 285f, 110f, p)
        c.drawCircle(255f, 275f, 10f, f); c.drawCircle(345f, 275f, 10f, f)
        qCurve(c, p, 250f, 325f, 300f, 345f, 350f, 325f)
    }

    private fun drawDancer(c: Canvas, p: Paint, f: Paint) {
        c.drawCircle(300f, 135f, 42f, f)
        qCurve(c, p, 170f, 210f, 300f, 160f, 430f, 210f)
        poly(c, p, true, 275f, 185f, 325f, 185f, 395f, 360f, 205f, 360f)
        c.drawLine(275f, 360f, 255f, 520f, p); c.drawLine(325f, 360f, 385f, 495f, p)
    }

    private fun drawHappyKid(c: Canvas, p: Paint, f: Paint) {
        c.drawCircle(300f, 165f, 65f, p)
        c.drawCircle(275f, 155f, 7f, f); c.drawCircle(325f, 155f, 7f, f)
        qCurve(c, p, 275f, 190f, 300f, 215f, 325f, 190f)
        c.drawLine(300f, 230f, 300f, 380f, p)
        c.drawLine(300f, 275f, 185f, 205f, p); c.drawLine(300f, 275f, 415f, 205f, p)
        c.drawLine(300f, 380f, 220f, 505f, p); c.drawLine(300f, 380f, 380f, 505f, p)
    }

    private fun drawRose(c: Canvas, p: Paint) {
        c.drawCircle(300f, 210f, 95f, p)
        c.drawCircle(300f, 210f, 55f, p); c.drawCircle(300f, 210f, 22f, p)
        c.drawLine(300f, 305f, 300f, 520f, p)
        c.drawOval(300f, 370f, 405f, 420f, p); c.drawOval(195f, 405f, 300f, 455f, p)
    }

    private fun drawLotus(c: Canvas, p: Paint) {
        qCurve(c, p, 300f, 110f, 215f, 230f, 300f, 355f)
        qCurve(c, p, 300f, 110f, 385f, 230f, 300f, 355f)
        qCurve(c, p, 145f, 210f, 175f, 330f, 300f, 355f)
        qCurve(c, p, 455f, 210f, 425f, 330f, 300f, 355f)
        c.drawOval(RectF(120f, 360f, 480f, 440f), p)
    }

    private fun drawSunflower(c: Canvas, p: Paint) {
        for (i in 0 until 12) {
            val a = i * PI / 6.0
            c.drawCircle(300f + (cos(a) * 110).toFloat(), 240f + (sin(a) * 110).toFloat(), 42f, p)
        }
        c.drawCircle(300f, 240f, 70f, p)
        c.drawLine(300f, 340f, 300f, 530f, p)
    }

    private fun drawTulip(c: Canvas, p: Paint) {
        qCurve(c, p, 190f, 160f, 180f, 340f, 300f, 340f)
        qCurve(c, p, 410f, 160f, 420f, 340f, 300f, 340f)
        poly(c, p, false, 190f, 160f, 250f, 225f, 300f, 140f, 350f, 225f, 410f, 160f)
        c.drawLine(300f, 340f, 300f, 530f, p)
        c.drawOval(300f, 390f, 395f, 445f, p)
    }

    private fun drawHibiscus(c: Canvas, p: Paint) {
        for (i in 0 until 5) {
            val a = i * 2 * PI / 5.0
            c.drawCircle(300f + (cos(a) * 85).toFloat(), 270f + (sin(a) * 85).toFloat(), 75f, p)
        }
        c.drawLine(300f, 270f, 370f, 150f, p); c.drawCircle(375f, 145f, 12f, p)
    }

    private fun drawMango(c: Canvas, p: Paint) {
        qCurve(c, p, 270f, 130f, 455f, 160f, 415f, 385f)
        qCurve(c, p, 415f, 385f, 330f, 515f, 225f, 455f)
        qCurve(c, p, 225f, 455f, 155f, 340f, 270f, 130f)
        c.drawLine(270f, 130f, 285f, 80f, p)
        c.drawOval(RectF(285f, 85f, 405f, 145f), p)
    }

    private fun drawApple(c: Canvas, p: Paint) {
        qCurve(c, p, 300f, 190f, 130f, 140f, 145f, 345f)
        qCurve(c, p, 145f, 345f, 175f, 495f, 300f, 470f)
        qCurve(c, p, 300f, 190f, 470f, 140f, 455f, 345f)
        qCurve(c, p, 455f, 345f, 425f, 495f, 300f, 470f)
        c.drawLine(300f, 190f, 320f, 110f, p)
        c.drawOval(RectF(315f, 100f, 425f, 165f), p)
    }

    private fun drawPineapple(c: Canvas, p: Paint) {
        c.drawOval(RectF(195f, 225f, 405f, 505f), p)
        poly(c, p, false, 235f, 235f, 210f, 120f, 270f, 195f, 300f, 80f, 330f, 195f, 390f, 120f, 365f, 235f)
        c.drawLine(215f, 300f, 385f, 420f, p); c.drawLine(385f, 300f, 215f, 420f, p)
    }

    private fun drawGrapes(c: Canvas, p: Paint) {
        c.drawLine(300f, 100f, 300f, 175f, p)
        for (x in listOf(220f, 300f, 380f)) c.drawCircle(x, 215f, 42f, p)
        for (x in listOf(255f, 345f)) c.drawCircle(x, 290f, 42f, p)
        c.drawCircle(300f, 365f, 42f, p)
    }

    private fun drawWatermelon(c: Canvas, p: Paint, f: Paint) {
        poly(c, p, false, 100f, 220f, 500f, 220f)
        qCurve(c, p, 100f, 220f, 300f, 480f, 500f, 220f)
        qCurve(c, p, 130f, 220f, 300f, 435f, 470f, 220f)
        for (x in listOf(235f, 300f, 365f)) c.drawCircle(x, 285f, 10f, f)
    }

    private fun drawParrot(c: Canvas, p: Paint, f: Paint) {
        c.drawCircle(285f, 180f, 65f, p); c.drawCircle(265f, 170f, 10f, f)
        qCurve(c, p, 220f, 170f, 175f, 190f, 210f, 225f)
        c.drawOval(RectF(235f, 235f, 375f, 420f), p)
        poly(c, p, true, 305f, 415f, 365f, 540f, 335f, 415f)
        c.drawLine(140f, 420f, 460f, 420f, p)
    }

    private fun drawPeacock(c: Canvas, p: Paint, f: Paint) {
        for (i in 0..6) {
            val a = -PI + i * PI / 6.0
            val tx = 300f + (cos(a) * 185).toFloat(); val ty = 290f + (sin(a) * 185).toFloat()
            c.drawLine(300f, 290f, tx, ty, p); c.drawCircle(tx, ty, 20f, p)
        }
        c.drawOval(RectF(255f, 260f, 345f, 440f), p)
        c.drawCircle(300f, 230f, 38f, p); c.drawCircle(290f, 222f, 6f, f)
    }

    private fun drawDove(c: Canvas, p: Paint, f: Paint) {
        c.drawCircle(215f, 230f, 45f, p); c.drawCircle(200f, 222f, 7f, f)
        poly(c, p, true, 170f, 230f, 130f, 240f, 175f, 250f)
        qCurve(c, p, 250f, 245f, 340f, 100f, 460f, 160f)
        qCurve(c, p, 460f, 160f, 395f, 315f, 245f, 340f)
        poly(c, p, true, 355f, 310f, 470f, 390f, 390f, 420f)
    }

    private fun drawOwl(c: Canvas, p: Paint, f: Paint) {
        c.drawOval(RectF(175f, 140f, 425f, 450f), p)
        poly(c, p, false, 190f, 175f, 175f, 110f, 235f, 150f)
        poly(c, p, false, 410f, 175f, 425f, 110f, 365f, 150f)
        c.drawCircle(245f, 225f, 38f, p); c.drawCircle(245f, 225f, 12f, f)
        c.drawCircle(355f, 225f, 38f, p); c.drawCircle(355f, 225f, 12f, f)
        poly(c, p, true, 285f, 255f, 315f, 255f, 300f, 285f)
        c.drawLine(140f, 460f, 460f, 460f, p)
    }

    private fun drawButterfly(c: Canvas) { }

    private fun drawButterfly(c: Canvas, p: Paint) {
        c.drawOval(RectF(282f, 180f, 318f, 430f), p)
        qCurve(c, p, 285f, 210f, 110f, 100f, 130f, 270f)
        qCurve(c, p, 130f, 270f, 195f, 320f, 285f, 290f)
        qCurve(c, p, 315f, 210f, 490f, 100f, 470f, 270f)
        qCurve(c, p, 470f, 270f, 405f, 320f, 315f, 290f)
        c.drawCircle(175f, 365f, 65f, p); c.drawCircle(425f, 365f, 65f, p)
    }

    private fun drawCat(c: Canvas, p: Paint, f: Paint) {
        c.drawCircle(300f, 330f, 145f, p)
        poly(c, p, false, 180f, 235f, 190f, 110f, 280f, 190f)
        poly(c, p, false, 420f, 235f, 410f, 110f, 320f, 190f)
        c.drawCircle(250f, 305f, 15f, f); c.drawCircle(350f, 305f, 15f, f)
        poly(c, p, true, 285f, 345f, 315f, 345f, 300f, 365f)
        c.drawLine(130f, 335f, 215f, 345f, p); c.drawLine(130f, 375f, 215f, 365f, p)
        c.drawLine(470f, 335f, 385f, 345f, p); c.drawLine(470f, 375f, 385f, 365f, p)
    }

    private fun drawPuppy(c: Canvas, p: Paint, f: Paint) {
        c.drawCircle(300f, 300f, 130f, p)
        c.drawOval(RectF(120f, 220f, 200f, 385f), p); c.drawOval(RectF(400f, 220f, 480f, 385f), p)
        c.drawCircle(255f, 275f, 15f, f); c.drawCircle(345f, 275f, 15f, f)
        c.drawOval(RectF(275f, 320f, 325f, 355f), f)
        qCurve(c, p, 265f, 380f, 300f, 410f, 335f, 380f)
    }

    private fun drawFish(c: Canvas, p: Paint, f: Paint) {
        c.drawOval(RectF(120f, 210f, 420f, 390f), p)
        poly(c, p, true, 420f, 300f, 510f, 220f, 510f, 380f)
        c.drawCircle(200f, 275f, 14f, f)
        c.drawArc(RectF(230f, 250f, 310f, 350f), -60f, 120f, false, p)
    }
}
