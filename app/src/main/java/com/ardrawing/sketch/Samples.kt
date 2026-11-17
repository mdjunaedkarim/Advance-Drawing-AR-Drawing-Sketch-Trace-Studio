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
        val series: String = "",
        val character: String = ""
    )

    // 25 viral Naruto characters x 5 clean B&W sketches = 125 bundled sketches
    val items = listOf(
        // 1. Naruto Uzumaki (5)
        Item(0, "Naruto Arms Crossed", Cat.ANIME, "anime/naruto_arms_crossed.png", "Naruto", "Naruto Uzumaki"),
        Item(1, "Naruto Smiling", Cat.ANIME, "anime/naruto_smiling.png", "Naruto", "Naruto Uzumaki"),
        Item(2, "Naruto Eating Ramen", Cat.ANIME, "anime/naruto_eating_ramen.png", "Naruto", "Naruto Uzumaki"),
        Item(3, "Naruto Kunai Dash", Cat.ANIME, "anime/naruto_kunai_dash.png", "Naruto", "Naruto Uzumaki"),
        Item(4, "Naruto Chakra Mode", Cat.ANIME, "anime/naruto_chakra_mode.png", "Naruto", "Naruto Uzumaki"),

        // 2. Sasuke Uchiha (5)
        Item(5, "Sasuke Uchiha", Cat.ANIME, "anime/sasuke_uchiha.png", "Naruto", "Sasuke Uchiha"),
        Item(6, "Sasuke Classic Blue", Cat.ANIME, "anime/sasuke_classic_blue.png", "Naruto", "Sasuke Uchiha"),
        Item(7, "Sasuke Kunai Dash", Cat.ANIME, "anime/sasuke_kunai_dash.png", "Naruto", "Sasuke Uchiha"),
        Item(8, "Sasuke Shippuden", Cat.ANIME, "anime/sasuke_shippuden.png", "Naruto", "Sasuke Uchiha"),
        Item(9, "Sasuke Chidori", Cat.ANIME, "anime/sasuke_chidori.png", "Naruto", "Sasuke Uchiha"),

        // 3. Hinata Hyuga (5)
        Item(10, "Hinata Smiling", Cat.ANIME, "anime/hinata_1.png", "Naruto", "Hinata Hyuga"),
        Item(11, "Hinata Cute Chibi", Cat.ANIME, "anime/hinata_2.png", "Naruto", "Hinata Hyuga"),
        Item(12, "Hinata Standing", Cat.ANIME, "anime/hinata_3.png", "Naruto", "Hinata Hyuga"),
        Item(13, "Hinata Byakugan", Cat.ANIME, "anime/hinata_4.png", "Naruto", "Hinata Hyuga"),
        Item(14, "Hinata Gentle Fist", Cat.ANIME, "anime/hinata_5.png", "Naruto", "Hinata Hyuga"),

        // 4. Jiraiya / Master Jiraiya (5)
        Item(15, "Jiraiya Toad Sage", Cat.ANIME, "anime/jiraiya_1.png", "Naruto", "Jiraiya"),
        Item(16, "Jiraiya Smirking", Cat.ANIME, "anime/jiraiya_2.png", "Naruto", "Jiraiya"),
        Item(17, "Jiraiya Arms Crossed", Cat.ANIME, "anime/jiraiya_3.png", "Naruto", "Jiraiya"),
        Item(18, "Jiraiya & Young Naruto", Cat.ANIME, "anime/jiraiya_4.png", "Naruto", "Jiraiya"),
        Item(19, "Jiraiya Meditating", Cat.ANIME, "anime/jiraiya_5.png", "Naruto", "Jiraiya"),

        // 5. Kakashi Hatake (5)
        Item(20, "Kakashi Chidori Aura", Cat.ANIME, "anime/kakashi_1.png", "Naruto", "Kakashi Hatake"),
        Item(21, "Kakashi Cool Sit", Cat.ANIME, "anime/kakashi_2.png", "Naruto", "Kakashi Hatake"),
        Item(22, "Kakashi Portrait", Cat.ANIME, "anime/kakashi_3.png", "Naruto", "Kakashi Hatake"),
        Item(23, "Kakashi Kunai Leap", Cat.ANIME, "anime/kakashi_4.png", "Naruto", "Kakashi Hatake"),
        Item(24, "Kakashi Reading Book", Cat.ANIME, "anime/kakashi_5.png", "Naruto", "Kakashi Hatake"),

        // 6. Itachi Uchiha (5)
        Item(25, "Itachi Susanoo", Cat.ANIME, "anime/itachi_1.png", "Naruto", "Itachi Uchiha"),
        Item(26, "Itachi Genjutsu Point", Cat.ANIME, "anime/itachi_2.png", "Naruto", "Itachi Uchiha"),
        Item(27, "Itachi Crow", Cat.ANIME, "anime/itachi_3.png", "Naruto", "Itachi Uchiha"),
        Item(28, "Itachi Uchiha Crest", Cat.ANIME, "anime/itachi_4.png", "Naruto", "Itachi Uchiha"),
        Item(29, "Itachi Cute Chibi", Cat.ANIME, "anime/itachi_5.png", "Naruto", "Itachi Uchiha"),

        // 7. Sakura Haruno (5)
        Item(30, "Sakura Blossom", Cat.ANIME, "anime/sakura_1.png", "Naruto", "Sakura Haruno"),
        Item(31, "Sakura Smiling", Cat.ANIME, "anime/sakura_2.png", "Naruto", "Sakura Haruno"),
        Item(32, "Sakura Cheerful Wink", Cat.ANIME, "anime/sakura_3.png", "Naruto", "Sakura Haruno"),
        Item(33, "Sakura Happy Pose", Cat.ANIME, "anime/sakura_4.png", "Naruto", "Sakura Haruno"),
        Item(34, "Sakura Portrait", Cat.ANIME, "anime/sakura_5.png", "Naruto", "Sakura Haruno"),

        // 8. Minato Namikaze (5)
        Item(35, "Minato Rasengan", Cat.ANIME, "anime/minato_1.png", "Naruto", "Minato Namikaze"),
        Item(36, "Minato Fourth Hokage", Cat.ANIME, "anime/minato_2.png", "Naruto", "Minato Namikaze"),
        Item(37, "Minato Calm Portrait", Cat.ANIME, "anime/minato_3.png", "Naruto", "Minato Namikaze"),
        Item(38, "Minato & Baby Naruto", Cat.ANIME, "anime/minato_4.png", "Naruto", "Minato Namikaze"),
        Item(39, "Minato Kunai Stance", Cat.ANIME, "anime/minato_5.png", "Naruto", "Minato Namikaze"),

        // 9. Gaara (5)
        Item(40, "Gaara Sand Reach", Cat.ANIME, "anime/gaara_1.png", "Naruto", "Gaara"),
        Item(41, "Gaara Sand Shield", Cat.ANIME, "anime/gaara_2.png", "Naruto", "Gaara"),
        Item(42, "Gaara Portrait", Cat.ANIME, "anime/gaara_3.png", "Naruto", "Gaara"),
        Item(43, "Gaara Cute Chibi", Cat.ANIME, "anime/gaara_4.png", "Naruto", "Gaara"),
        Item(44, "Gaara Arms Crossed", Cat.ANIME, "anime/gaara_5.png", "Naruto", "Gaara"),

        // 10. Madara Uchiha (5)
        Item(45, "Madara Arms Crossed", Cat.ANIME, "anime/madara_1.png", "Naruto", "Madara Uchiha"),
        Item(46, "Madara Armored Action", Cat.ANIME, "anime/madara_2.png", "Naruto", "Madara Uchiha"),
        Item(47, "Madara Intense Portrait", Cat.ANIME, "anime/madara_3.png", "Naruto", "Madara Uchiha"),
        Item(48, "Madara Full Armor", Cat.ANIME, "anime/madara_4.png", "Naruto", "Madara Uchiha"),
        Item(49, "Madara Rinnegan Eyes", Cat.ANIME, "anime/madara_5.png", "Naruto", "Madara Uchiha"),

        // 11. Obito Uchiha (5)
        Item(50, "Obito Akatsuki Mask", Cat.ANIME, "anime/obito_1.png", "Naruto", "Obito Uchiha"),
        Item(51, "Obito Broken Mask", Cat.ANIME, "anime/obito_2.png", "Naruto", "Obito Uchiha"),
        Item(52, "Obito War Stance", Cat.ANIME, "anime/obito_3.png", "Naruto", "Obito Uchiha"),
        Item(53, "Obito Tobi Bust", Cat.ANIME, "anime/obito_4.png", "Naruto", "Obito Uchiha"),
        Item(54, "Obito Unmasked Portrait", Cat.ANIME, "anime/obito_5.png", "Naruto", "Obito Uchiha"),

        // 12. Pain Nagato (5)
        Item(55, "Pain Almighty Push", Cat.ANIME, "anime/pain_1.png", "Naruto", "Pain Nagato"),
        Item(56, "Pain Rinnegan Portrait", Cat.ANIME, "anime/pain_2.png", "Naruto", "Pain Nagato"),
        Item(57, "Pain Akatsuki Cloak", Cat.ANIME, "anime/pain_3.png", "Naruto", "Pain Nagato"),
        Item(58, "Pain Standing Full", Cat.ANIME, "anime/pain_4.png", "Naruto", "Pain Nagato"),
        Item(59, "Pain Intense Eyes", Cat.ANIME, "anime/pain_5.png", "Naruto", "Pain Nagato"),

        // 13. Tsunade (5)
        Item(60, "Tsunade Chakra Smash", Cat.ANIME, "anime/tsunade_1.png", "Naruto", "Tsunade"),
        Item(61, "Tsunade Young Smile", Cat.ANIME, "anime/tsunade_2.png", "Naruto", "Tsunade"),
        Item(62, "Tsunade Fifth Hokage", Cat.ANIME, "anime/tsunade_3.png", "Naruto", "Tsunade"),
        Item(63, "Tsunade Dynamic Pose", Cat.ANIME, "anime/tsunade_4.png", "Naruto", "Tsunade"),
        Item(64, "Tsunade Cute Chibi", Cat.ANIME, "anime/tsunade_5.png", "Naruto", "Tsunade"),

        // 14. Orochimaru (5)
        Item(65, "Orochimaru Coiled Snake", Cat.ANIME, "anime/orochimaru_1.png", "Naruto", "Orochimaru"),
        Item(66, "Orochimaru Snake Strike", Cat.ANIME, "anime/orochimaru_2.png", "Naruto", "Orochimaru"),
        Item(67, "Orochimaru Dash", Cat.ANIME, "anime/orochimaru_3.png", "Naruto", "Orochimaru"),
        Item(68, "Orochimaru Sword Pose", Cat.ANIME, "anime/orochimaru_4.png", "Naruto", "Orochimaru"),
        Item(69, "Orochimaru Battle", Cat.ANIME, "anime/orochimaru_5.png", "Naruto", "Orochimaru"),

        // 15. Rock Lee (5)
        Item(70, "Rock Lee Leaf Hurricane", Cat.ANIME, "anime/rock_lee_1.png", "Naruto", "Rock Lee"),
        Item(71, "Rock Lee Stance", Cat.ANIME, "anime/rock_lee_2.png", "Naruto", "Rock Lee"),
        Item(72, "Rock Lee Taijutsu", Cat.ANIME, "anime/rock_lee_3.png", "Naruto", "Rock Lee"),
        Item(73, "Rock Lee Primary Lotus", Cat.ANIME, "anime/rock_lee_4.png", "Naruto", "Rock Lee"),
        Item(74, "Rock Lee Inner Gates", Cat.ANIME, "anime/rock_lee_5.png", "Naruto", "Rock Lee"),

        // 16. Shikamaru Nara (5)
        Item(75, "Shikamaru Shadow Crouch", Cat.ANIME, "anime/shikamaru_1.png", "Naruto", "Shikamaru Nara"),
        Item(76, "Shikamaru Hand Seal", Cat.ANIME, "anime/shikamaru_2.png", "Naruto", "Shikamaru Nara"),
        Item(77, "Shikamaru Jutsu Ready", Cat.ANIME, "anime/shikamaru_3.png", "Naruto", "Shikamaru Nara"),
        Item(78, "Shikamaru Calm Portrait", Cat.ANIME, "anime/shikamaru_4.png", "Naruto", "Shikamaru Nara"),
        Item(79, "Shikamaru Kunai Leap", Cat.ANIME, "anime/shikamaru_5.png", "Naruto", "Shikamaru Nara"),

        // 17. Kurama Nine Tails (5)
        Item(80, "Kurama Fierce Roar", Cat.ANIME, "anime/kurama_1.png", "Naruto", "Kurama Nine Tails"),
        Item(81, "Kurama Unleashed", Cat.ANIME, "anime/kurama_2.png", "Naruto", "Kurama Nine Tails"),
        Item(82, "Kurama Crouching", Cat.ANIME, "anime/kurama_3.png", "Naruto", "Kurama Nine Tails"),
        Item(83, "Kurama Howling Sky", Cat.ANIME, "anime/kurama_4.png", "Naruto", "Kurama Nine Tails"),
        Item(84, "Kurama Nine Tails", Cat.ANIME, "anime/kurama_5.png", "Naruto", "Kurama Nine Tails"),

        // 18. Neji Hyuga (5)
        Item(85, "Neji Cute Chibi", Cat.ANIME, "anime/neji_1.png", "Naruto", "Neji Hyuga"),
        Item(86, "Neji Shippuden Stance", Cat.ANIME, "anime/neji_2.png", "Naruto", "Neji Hyuga"),
        Item(87, "Neji Eight Trigrams", Cat.ANIME, "anime/neji_3.png", "Naruto", "Neji Hyuga"),
        Item(88, "Neji Byakugan Eyes", Cat.ANIME, "anime/neji_4.png", "Naruto", "Neji Hyuga"),
        Item(89, "Neji Calm Portrait", Cat.ANIME, "anime/neji_5.png", "Naruto", "Neji Hyuga"),

        // 19. Might Guy (5)
        Item(90, "Might Guy Eight Gates", Cat.ANIME, "anime/might_guy_1.png", "Naruto", "Might Guy"),
        Item(91, "Might Guy & Rock Lee", Cat.ANIME, "anime/might_guy_2.png", "Naruto", "Might Guy"),
        Item(92, "Might Guy Dynamic Entry", Cat.ANIME, "anime/might_guy_3.png", "Naruto", "Might Guy"),
        Item(93, "Might Guy Nice Guy Pose", Cat.ANIME, "anime/might_guy_4.png", "Naruto", "Might Guy"),
        Item(94, "Might Guy Portrait", Cat.ANIME, "anime/might_guy_5.png", "Naruto", "Might Guy"),

        // 20. Boruto Uzumaki (5)
        Item(95, "Boruto Cheerful Fist", Cat.ANIME, "anime/boruto_1.png", "Naruto", "Boruto Uzumaki"),
        Item(96, "Boruto Two Blue Vortex", Cat.ANIME, "anime/boruto_2.png", "Naruto", "Boruto Uzumaki"),
        Item(97, "Boruto Smiling Grin", Cat.ANIME, "anime/boruto_3.png", "Naruto", "Boruto Uzumaki"),
        Item(98, "Boruto Standing Full", Cat.ANIME, "anime/boruto_4.png", "Naruto", "Boruto Uzumaki"),
        Item(99, "Boruto Karma Sword", Cat.ANIME, "anime/boruto_5.png", "Naruto", "Boruto Uzumaki"),

        // 21. Hashirama Senju (5)
        Item(100, "Hashirama Sage Stance", Cat.ANIME, "anime/hashirama_1.png", "Naruto", "Hashirama Senju"),
        Item(101, "Hashirama Wood Dragon", Cat.ANIME, "anime/hashirama_2.png", "Naruto", "Hashirama Senju"),
        Item(102, "Hashirama Hand Seal", Cat.ANIME, "anime/hashirama_3.png", "Naruto", "Hashirama Senju"),
        Item(103, "Hashirama Wood Style", Cat.ANIME, "anime/hashirama_4.png", "Naruto", "Hashirama Senju"),
        Item(104, "Hashirama Hokage Hat", Cat.ANIME, "anime/hashirama_5.png", "Naruto", "Hashirama Senju"),

        // 22. Konan (5)
        Item(105, "Konan Akatsuki Standing", Cat.ANIME, "anime/konan_1.png", "Naruto", "Konan"),
        Item(106, "Konan Paper Wings", Cat.ANIME, "anime/konan_2.png", "Naruto", "Konan"),
        Item(107, "Konan Origami Reach", Cat.ANIME, "anime/konan_3.png", "Naruto", "Konan"),
        Item(108, "Konan Butterflies", Cat.ANIME, "anime/konan_4.png", "Naruto", "Konan"),
        Item(109, "Konan Calm Portrait", Cat.ANIME, "anime/konan_5.png", "Naruto", "Konan"),

        // 23. Killer Bee (5)
        Item(110, "Killer Bee Eight Swords", Cat.ANIME, "anime/killer_bee_1.png", "Naruto", "Killer Bee"),
        Item(111, "Killer Bee Cute Chibi", Cat.ANIME, "anime/killer_bee_2.png", "Naruto", "Killer Bee"),
        Item(112, "Killer Bee Rap Pose", Cat.ANIME, "anime/killer_bee_3.png", "Naruto", "Killer Bee"),
        Item(113, "Killer Bee Lariat", Cat.ANIME, "anime/killer_bee_4.png", "Naruto", "Killer Bee"),
        Item(114, "Killer Bee Rap Bust", Cat.ANIME, "anime/killer_bee_5.png", "Naruto", "Killer Bee"),

        // 24. Tobirama Senju (5)
        Item(115, "Tobirama Second Hokage", Cat.ANIME, "anime/tobirama_1.png", "Naruto", "Tobirama Senju"),
        Item(116, "Tobirama Flying Raijin", Cat.ANIME, "anime/tobirama_2.png", "Naruto", "Tobirama Senju"),
        Item(117, "Tobirama Sharp Portrait", Cat.ANIME, "anime/tobirama_3.png", "Naruto", "Tobirama Senju"),
        Item(118, "Tobirama Water Style", Cat.ANIME, "anime/tobirama_4.png", "Naruto", "Tobirama Senju"),
        Item(119, "Tobirama Chakra Aura", Cat.ANIME, "anime/tobirama_5.png", "Naruto", "Tobirama Senju"),

        // 25. Sarada Uchiha (5)
        Item(120, "Sarada Standing Full", Cat.ANIME, "anime/sarada_1.png", "Naruto", "Sarada Uchiha"),
        Item(121, "Sarada Cute Hoodie", Cat.ANIME, "anime/sarada_2.png", "Naruto", "Sarada Uchiha"),
        Item(122, "Sarada Glasses Pose", Cat.ANIME, "anime/sarada_3.png", "Naruto", "Sarada Uchiha"),
        Item(123, "Sarada Hand Sign", Cat.ANIME, "anime/sarada_4.png", "Naruto", "Sarada Uchiha"),
        Item(124, "Sarada Sharingan Eyes", Cat.ANIME, "anime/sarada_5.png", "Naruto", "Sarada Uchiha")
    )

    fun bySeries(seriesName: String): List<Item> =
        items.filter { it.series.equals(seriesName, ignoreCase = true) }

    fun byCharacter(characterName: String, seriesName: String = ""): List<Item> {
        val norm = AnimeCatalog.normalizeQuery(characterName).trim().lowercase()
        if (norm.isEmpty()) return emptyList()
        val seriesFiltered = if (seriesName.isEmpty()) items else bySeries(seriesName)
        val exact = seriesFiltered.filter { it.character.equals(norm, ignoreCase = true) }
        if (exact.isNotEmpty()) return exact
        return seriesFiltered.filter {
            it.character.lowercase().startsWith(norm) ||
                it.character.lowercase().split(" ").any { w -> w == norm } ||
                (norm.length >= 3 && it.character.lowercase().contains(norm))
        }
    }

    private val thumbCache = LruCache<Int, Bitmap>(160)

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
            val raw = ctx.assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, dec) } ?: return null
            ImageProcessor.toCleanBwSketch(raw)
        } catch (_: Exception) {
            null
        }
    }
}
