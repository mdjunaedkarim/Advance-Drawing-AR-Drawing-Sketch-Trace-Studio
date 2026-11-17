package com.ardrawing.sketch

import java.util.Random

object CategoryCatalog {

    data class TopicItem(
        val id: String,
        val title: String,
        val cat: Samples.Cat,
        val tag: String,
        val searchQuery: String,
        val characterName: String = "",
        val seriesName: String = "",
        val localSample: Samples.Item? = null
    )

    private val flagshipLeadBySeries = mapOf(
        "Naruto" to "Naruto Uzumaki",
        "Demon Slayer" to "Tanjiro Kamado",
        "Jujutsu Kaisen" to "Satoru Gojo",
        "One Piece" to "Monkey D Luffy",
        "Dragon Ball" to "Son Goku",
        "Attack on Titan" to "Levi Ackerman",
        "My Hero Academia" to "Izuku Midoriya Deku",
        "Chainsaw Man" to "Denji",
        "Solo Leveling" to "Sung Jinwoo",
        "Spy x Family" to "Anya Forger",
        "Tokyo Revengers" to "Manjiro Sano Mikey",
        "Death Note" to "L Lawliet",
        "Bleach" to "Ichigo Kurosaki",
        "Hunter x Hunter" to "Killua Zoldyck",
        "Black Clover" to "Asta",
        "Blue Lock" to "Yoichi Isagi",
        "One Punch Man" to "Saitama",
        "Tokyo Ghoul" to "Ken Kaneki",
        "Fullmetal Alchemist" to "Edward Elric",
        "Haikyuu" to "Shoyo Hinata"
    )

    val fruits: List<TopicItem> = listOf(
        topic("fruit_mango", "Mango", Samples.Cat.FRUIT, "Mango fruit"),
        topic("fruit_apple", "Apple", Samples.Cat.FRUIT, "Apple fruit"),
        topic("fruit_pineapple", "Pineapple", Samples.Cat.FRUIT, "Pineapple fruit"),
        topic("fruit_strawberry", "Strawberry", Samples.Cat.FRUIT, "Strawberry fruit"),
        topic("fruit_watermelon", "Watermelon", Samples.Cat.FRUIT, "Watermelon fruit"),
        topic("fruit_banana", "Banana", Samples.Cat.FRUIT, "Banana fruit"),
        topic("fruit_grapes", "Grapes", Samples.Cat.FRUIT, "Grapes fruit bunch"),
        topic("fruit_orange", "Orange", Samples.Cat.FRUIT, "Orange fruit"),
        topic("fruit_cherry", "Cherry", Samples.Cat.FRUIT, "Cherry fruit"),
        topic("fruit_papaya", "Papaya", Samples.Cat.FRUIT, "Papaya fruit"),
        topic("fruit_pomegranate", "Pomegranate", Samples.Cat.FRUIT, "Pomegranate fruit"),
        topic("fruit_coconut", "Coconut", Samples.Cat.FRUIT, "Coconut fruit"),
        topic("fruit_avocado", "Avocado", Samples.Cat.FRUIT, "Avocado fruit"),
        topic("fruit_kiwi", "Kiwi", Samples.Cat.FRUIT, "Kiwi fruit")
    )

    val cars: List<TopicItem> = listOf(
        topic("car_sports", "Sports Car", Samples.Cat.CAR, "Sports car"),
        topic("car_lambo", "Lamborghini", Samples.Cat.CAR, "Lamborghini supercar"),
        topic("car_ferrari", "Ferrari", Samples.Cat.CAR, "Ferrari sports car"),
        topic("car_bugatti", "Bugatti", Samples.Cat.CAR, "Bugatti supercar"),
        topic("car_porsche", "Porsche 911", Samples.Cat.CAR, "Porsche 911 car"),
        topic("car_bmw", "BMW M4", Samples.Cat.CAR, "BMW M4 car"),
        topic("car_mustang", "Ford Mustang", Samples.Cat.CAR, "Ford Mustang car"),
        topic("car_gwagon", "Mercedes G-Wagon", Samples.Cat.CAR, "Mercedes G Wagon car"),
        topic("car_f1", "Formula 1", Samples.Cat.CAR, "Formula 1 race car"),
        topic("car_monster", "Monster Truck", Samples.Cat.CAR, "Monster truck vehicle"),
        topic("car_superbike", "Superbike", Samples.Cat.CAR, "Superbike motorcycle"),
        topic("car_vintage", "Classic Car", Samples.Cat.CAR, "Classic vintage car"),
        topic("car_jet", "Fighter Jet", Samples.Cat.CAR, "Fighter jet aircraft"),
        topic("car_rocket", "Space Rocket", Samples.Cat.CAR, "Space rocket")
    )

    val foods: List<TopicItem> = listOf(
        topic("food_pizza", "Pizza Slice", Samples.Cat.FOOD, "Pizza slice food"),
        topic("food_burger", "Burger", Samples.Cat.FOOD, "Burger food"),
        topic("food_icecream", "Ice Cream", Samples.Cat.FOOD, "Ice cream cone"),
        topic("food_cupcake", "Cupcake", Samples.Cat.FOOD, "Cupcake dessert"),
        topic("food_donut", "Donut", Samples.Cat.FOOD, "Donut dessert"),
        topic("food_cake", "Birthday Cake", Samples.Cat.FOOD, "Birthday cake"),
        topic("food_sushi", "Sushi Roll", Samples.Cat.FOOD, "Sushi roll food"),
        topic("food_fries", "French Fries", Samples.Cat.FOOD, "French fries"),
        topic("food_ramen", "Ramen Bowl", Samples.Cat.FOOD, "Ramen noodle bowl"),
        topic("food_coffee", "Coffee Cup", Samples.Cat.FOOD, "Coffee cup latte")
    )

    val flowers: List<TopicItem> = listOf(
        topic("flower_rose", "Rose Flower", Samples.Cat.FLOWER, "Rose flower"),
        topic("flower_lotus", "Lotus Flower", Samples.Cat.FLOWER, "Lotus flower"),
        topic("flower_sunflower", "Sunflower", Samples.Cat.FLOWER, "Sunflower flower"),
        topic("flower_tulip", "Tulip Flower", Samples.Cat.FLOWER, "Tulip flower"),
        topic("flower_sakura", "Sakura Blossom", Samples.Cat.FLOWER, "Sakura cherry blossom flower"),
        topic("flower_hibiscus", "Hibiscus Flower", Samples.Cat.FLOWER, "Hibiscus flower"),
        topic("flower_lily", "Lily Flower", Samples.Cat.FLOWER, "Lily flower"),
        topic("flower_orchid", "Orchid Flower", Samples.Cat.FLOWER, "Orchid flower"),
        topic("flower_jasmine", "Jasmine Flower", Samples.Cat.FLOWER, "Jasmine flower"),
        topic("flower_butterfly", "Flower & Butterfly", Samples.Cat.FLOWER, "Butterfly on flower")
    )

    val scenery: List<TopicItem> = listOf(
        topic("scenery_boat", "Mountain River Boat", Samples.Cat.SCENERY, "Mountain river boat landscape"),
        topic("scenery_village", "Village House", Samples.Cat.SCENERY, "Village house landscape"),
        topic("scenery_sunset", "Sunset Beach", Samples.Cat.SCENERY, "Sunset beach palm tree landscape"),
        topic("scenery_waterfall", "Waterfall", Samples.Cat.SCENERY, "Waterfall nature landscape"),
        topic("scenery_bridge", "River Bridge", Samples.Cat.SCENERY, "River bridge landscape"),
        topic("scenery_cabin", "Forest Cabin", Samples.Cat.SCENERY, "Pine forest cabin landscape"),
        topic("scenery_night", "Moonlight Sky", Samples.Cat.SCENERY, "Moonlight night sky landscape"),
        topic("scenery_lighthouse", "Lighthouse Coast", Samples.Cat.SCENERY, "Lighthouse sea coast landscape")
    )

    val animals: List<TopicItem> = listOf(
        topic("animal_cat", "Cute Cat", Samples.Cat.ANIMALS, "Cute cat animal"),
        topic("animal_lion", "Lion Face", Samples.Cat.ANIMALS, "Lion animal"),
        topic("animal_peacock", "Peacock", Samples.Cat.ANIMALS, "Peacock bird"),
        topic("animal_parrot", "Parrot", Samples.Cat.ANIMALS, "Parrot bird"),
        topic("animal_puppy", "Cute Puppy", Samples.Cat.ANIMALS, "Cute puppy dog"),
        topic("animal_tiger", "Bengal Tiger", Samples.Cat.ANIMALS, "Tiger animal"),
        topic("animal_elephant", "Elephant", Samples.Cat.ANIMALS, "Elephant animal"),
        topic("animal_butterfly", "Butterfly", Samples.Cat.ANIMALS, "Butterfly insect"),
        topic("animal_horse", "Running Horse", Samples.Cat.ANIMALS, "Running horse animal"),
        topic("animal_panda", "Cute Panda", Samples.Cat.ANIMALS, "Cute panda animal")
    )

    private fun topic(
        id: String,
        title: String,
        cat: Samples.Cat,
        searchQuery: String
    ): TopicItem = TopicItem(
        id = id,
        title = title,
        cat = cat,
        tag = cat.label(),
        searchQuery = searchQuery
    )

    fun itemsForCategory(cat: Samples.Cat): List<TopicItem> = when (cat) {
        Samples.Cat.FRUIT -> fruits
        Samples.Cat.CAR -> cars
        Samples.Cat.FOOD -> foods
        Samples.Cat.FLOWER -> flowers
        Samples.Cat.SCENERY -> scenery
        Samples.Cat.ANIMALS -> animals
        else -> emptyList()
    }

    /**
     * Returns 1 showcase card for EVERY anime series in AnimeCatalog (all 20 anime),
     * serial-by-serial with each series tag and lead hero sketch.
     */
    fun animeSeriesCovers(): List<TopicItem> {
        return AnimeCatalog.list.map { series ->
            val lead = flagshipLeadBySeries[series.name] ?: series.characters.first()
            val localMatch = if (series.name.equals("Naruto", ignoreCase = true)) {
                Samples.byCharacter(lead, series.name).firstOrNull()
                    ?: Samples.bySeries("Naruto").firstOrNull()
            } else {
                null
            }
            val cleanLead = SketchSearchRepository.cleanSearchQuery(lead, series.name)
            TopicItem(
                id = "anime_series_${series.name.lowercase().replace(" ", "_")}",
                title = series.name,
                cat = Samples.Cat.ANIME,
                tag = series.name,
                searchQuery = "$cleanLead anime",
                characterName = lead,
                seriesName = series.name,
                localSample = localMatch
            )
        }
    }

    /**
     * Builds a random, diverse pool of anime hero cards spanning all 20 series (never only Naruto).
     */
    fun randomAnimeSpotlight(seed: Long, count: Int = 12): List<TopicItem> {
        val rng = Random(seed)
        val heroes = AnimeCatalog.list.map { series ->
            val pool = series.characters.take(8)
            val chosenChar = pool[rng.nextInt(pool.size)]
            val local = if (series.name.equals("Naruto", ignoreCase = true)) {
                Samples.byCharacter(chosenChar, series.name).let { list ->
                    if (list.isNotEmpty()) list[rng.nextInt(list.size)] else null
                }
            } else {
                null
            }
            val q = SketchSearchRepository.cleanSearchQuery(chosenChar, series.name)
            TopicItem(
                id = "hero_${series.name.lowercase().replace(" ", "_")}_${chosenChar.lowercase().replace(" ", "_")}",
                title = chosenChar,
                cat = Samples.Cat.ANIME,
                tag = series.name,
                searchQuery = "$q anime",
                characterName = chosenChar,
                seriesName = series.name,
                localSample = local
            )
        }.shuffled(rng)
        return heroes.take(count)
    }

    /**
     * Builds a shuffled, serial-by-serial mixed spotlight across ALL anime series AND
     * Fruits, Cars, Flowers, Food, Animals, and Scenery.
     */
    fun randomMixedSpotlight(seed: Long, count: Int = 14): List<TopicItem> {
        val rng = Random(seed)
        val animePool = randomAnimeSpotlight(seed + 7, 10)
        val catPools = listOf(fruits, cars, flowers, foods, animals, scenery).map { it.shuffled(rng) }

        val interleaved = ArrayList<TopicItem>(count + 4)
        var aIdx = 0
        var cIdx = 0
        var round = 0
        while (interleaved.size < count && (aIdx < animePool.size || round < 6)) {
            if (aIdx < animePool.size) {
                interleaved.add(animePool[aIdx++])
            }
            val catList = catPools[cIdx % catPools.size]
            val pickIdx = round % catList.size
            if (interleaved.size < count) {
                interleaved.add(catList[pickIdx])
            }
            cIdx++
            if (cIdx % catPools.size == 0) round++
            if (aIdx >= animePool.size && round >= 3) break
        }
        return interleaved.take(count)
    }

    /**
     * Builds a 2-column random mixed grid serial-by-serial across Fruits, Cars, Flowers,
     * Food, Animals, Scenery, and diverse Anime characters.
     */
    fun randomMixedGrid(seed: Long, count: Int = 16): List<TopicItem> {
        val rng = Random(seed xor 0x5A5A5A5AL)
        val animeList = randomAnimeSpotlight(seed + 19, 10)
        val nonAnimeBuckets = listOf(
            fruits.shuffled(rng),
            cars.shuffled(rng),
            flowers.shuffled(rng),
            animals.shuffled(rng),
            foods.shuffled(rng),
            scenery.shuffled(rng)
        )

        val out = ArrayList<TopicItem>(count)
        var round = 0
        var animeCursor = 0
        while (out.size < count) {
            for (bucket in nonAnimeBuckets) {
                if (out.size >= count) break
                if (round < bucket.size) {
                    out.add(bucket[round])
                }
                if (out.size < count && animeCursor < animeList.size) {
                    out.add(animeList[animeCursor++])
                }
            }
            round++
            if (round > 6) break
        }
        return out.take(count)
    }
}
