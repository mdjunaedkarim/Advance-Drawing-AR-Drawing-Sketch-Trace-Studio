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
        val assetPath: String
    )

    // User's curated sketch photos (added serial by serial)
    val items = listOf(
        Item(0, "Naruto Uzumaki", Cat.ANIME, "anime/naruto_uzumaki.jpg")
    )

    private val thumbCache = LruCache<Int, Bitmap>(48)

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
