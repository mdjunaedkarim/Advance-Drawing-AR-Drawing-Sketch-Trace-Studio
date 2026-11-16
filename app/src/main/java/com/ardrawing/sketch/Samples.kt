package com.ardrawing.sketch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache

object Samples {

    enum class Cat(val labelKey: String, val assetFolder: String = "") {
        ALL("cat_all"),
        ANIME("cat_anime", "anime"),
        CAR("cat_car", "car"),
        FOOD("cat_food", "food"),
        FLOWER("cat_flower", "flower"),
        SCENERY("cat_scenery", "scenery"),
        ANIMALS("cat_animals", "animals");

        fun label(): String = L.t(labelKey)
    }

    data class Item(
        val id: Int,
        val title: String,
        val cat: Cat,
        val assetPath: String,
        val series: String = ""
    )

    // User's curated sketch photos organized by Anime / Category name
    val items = listOf(
        // Naruto
        Item(0, "Naruto Uzumaki", Cat.ANIME, "anime/naruto_uzumaki.jpg", "Naruto"),
        Item(1, "Naruto Standing Scroll", Cat.ANIME, "anime/naruto_scroll.jpg", "Naruto"),
        Item(2, "Naruto Tree Branch", Cat.ANIME, "anime/naruto_tree_branch.jpg", "Naruto"),
        Item(3, "Naruto Eating Ramen", Cat.ANIME, "anime/naruto_ramen.jpg", "Naruto"),
        Item(4, "Naruto Arms Crossed", Cat.ANIME, "anime/naruto_arms_crossed.jpg", "Naruto"),
        Item(5, "Naruto Chakra Mode", Cat.ANIME, "anime/naruto_chakra_mode.jpg", "Naruto"),
        Item(6, "Naruto on Giant Toad", Cat.ANIME, "anime/naruto_giant_toad.jpg", "Naruto"),
        Item(7, "Naruto Smiling", Cat.ANIME, "anime/naruto_smiling.jpg", "Naruto"),
        Item(8, "Naruto Six Paths Mode", Cat.ANIME, "anime/naruto_six_paths.jpg", "Naruto"),
        Item(9, "Naruto Kunai Dash", Cat.ANIME, "anime/naruto_kunai_dash.jpg", "Naruto"),
        Item(10, "Naruto Battle Stance", Cat.ANIME, "anime/naruto_battle_stance.jpg", "Naruto"),
        Item(11, "Naruto Sage Mode", Cat.ANIME, "anime/naruto_sage_mode.jpg", "Naruto"),
        Item(12, "Naruto Portrait", Cat.ANIME, "anime/naruto_portrait.jpg", "Naruto"),
        Item(13, "Naruto Rasengan", Cat.ANIME, "anime/naruto_rasengan.jpg", "Naruto")
    )

    fun bySeries(seriesName: String): List<Item> =
        items.filter { it.series.equals(seriesName, ignoreCase = true) }

    private val thumbCache = LruCache<Int, Bitmap>(96)

    fun loadThumb(ctx: Context, item: Item, size: Int = 360): Bitmap? {
        thumbCache.get(item.id)?.let { return it }
        return loadAssetBitmap(ctx, item.assetPath, size)?.also {
            thumbCache.put(item.id, it)
        }
    }

    fun loadFull(ctx: Context, item: Item): Bitmap? {
        return loadAssetBitmap(ctx, item.assetPath, 1440)
    }

    fun loadAssetBitmap(ctx: Context, assetPath: String, maxPx: Int = 1080): Bitmap? {
        if (assetPath.isEmpty()) return null
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, opts) }
            var s = 1
            while (opts.outWidth / s > maxPx || opts.outHeight / s > maxPx) s *= 2
            val dec = BitmapFactory.Options().apply {
                inSampleSize = s
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            ctx.assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, dec) }
        } catch (_: Exception) {
            null
        }
    }
}
