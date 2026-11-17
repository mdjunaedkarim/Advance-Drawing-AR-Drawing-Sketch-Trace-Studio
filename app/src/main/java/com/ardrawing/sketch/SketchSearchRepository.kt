package com.ardrawing.sketch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors

object SketchSearchRepository {

    data class SketchResult(
        val id: String,
        val title: String,
        val mood: String,
        val fullUrl: String,
        val thumbUrl: String,
        val localSample: Samples.Item? = null
    )

    private data class Candidate(
        val fullUrl: String,
        val thumbUrl: String,
        val title: String,
        val pageUrl: String,
        val score: Int
    )

    private val searchExecutor = Executors.newFixedThreadPool(6)
    private val imgExecutor = Executors.newFixedThreadPool(10)
    private val previewExecutor = Executors.newFixedThreadPool(10)
    private val diskWriteExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var appCtx: Context? = null

    @Volatile
    private var diskPreviewIndexLoaded = false

    @Volatile
    private var legacyCacheCleaned = false

    private val queryCache = LruCache<String, List<SketchResult>>(120)
    private val thumbBitmapCache = LruCache<String, Bitmap>(240)
    private val previewCache = LruCache<String, Bitmap>(220)
    private val previewUrlCache = ConcurrentHashMap<String, Pair<String, String>>()
    private val inFlightPreviews = ConcurrentHashMap<String, MutableList<(Bitmap?) -> Unit>>()

    private val iuscRegex = Regex("""class="iusc"[^>]*?m="([^"]+)"""")
    private val murlRegex = Regex(""""murl"\s*:\s*"(https?://[^"]+)"""")
    private val turlRegex = Regex(""""turl"\s*:\s*"(https?://[^"]+)"""")
    private val titleRegex = Regex(""""t"\s*:\s*"([^"]*)"""")
    private val purlRegex = Regex(""""purl"\s*:\s*"(https?://[^"]+)"""")

    private val blockedHosts = listOf(
        "shutterstock", "alamy", "istockphoto", "dreamstime", "123rf",
        "depositphotos", "gettyimages", "vecteezy", "freepik", "vectorstock",
        "favicon", "logo", "sprite", "doubleclick", "tenor.com", "giphy.com"
    )

    private val offTopicTerms = listOf(
        "gta", "grand theft", "gtav", "gta5", "gta 5", "san andreas", "vice city",
        "rockstar games", "franklin clinton", "michael de santa", "trevor philips",
        "fortnite", "minecraft", "roblox", "valorant", "pubg", "free fire",
        "call of duty", "cyberpunk 2077", "genshin impact",
        "cosplay", "action figure", "funko", "plush", "t-shirt", "hoodie",
        "lion king", "simba", "mufasa", "scarface", "kung fu panda", "giant panda",
        "re:zero", "re zero", "iron man", "tony stark", "avengers", "playing card",
        "wallpaper", "screenshot", "figurine", "sticker", "tattoo", "amv", "episode"
    )

    private val ambiguousCharWords = setOf(
        "king", "pain", "power", "franklin", "michael", "tank", "iron", "panda",
        "twice", "stain", "scar", "greed", "envy", "lust", "sloth", "gluttony",
        "beam", "bill", "camilla", "millie", "sharon", "uta", "rem", "near",
        "mello", "light", "law", "kid", "cell", "angel", "eri", "ace", "sabo",
        "hawks", "franky", "brook", "nami", "robin", "kite", "grey", "luna",
        "tusk", "symbol", "statue", "ant", "beast", "dragon", "rose", "lotus",
        "lion", "overhaul", "smiley", "watkins", "gordon", "henry", "martha",
        "hinata", "android"
    )

    private val genericTopicStopWords = setOf(
        "the", "and", "of", "for", "with", "devil", "face", "smug", "symbol", "statue", "man",
        "fruit", "fruits", "car", "cars", "supercar", "sports", "food", "foods", "dessert",
        "flower", "flowers", "landscape", "scenery", "animal", "animals", "bird", "insect",
        "cute", "running", "anime", "classic", "vintage", "tree", "slice", "bowl", "cup", "cone"
    )

    private val seriesAliases = mapOf(
        "demon slayer" to listOf("demon slayer", "kimetsu", "kny"),
        "jujutsu kaisen" to listOf("jujutsu kaisen", "jujutsu", "jjk"),
        "one piece" to listOf("one piece"),
        "dragon ball" to listOf("dragon ball", "dbz", "dbs", "dragonball"),
        "attack on titan" to listOf("attack on titan", "shingeki", "aot"),
        "my hero academia" to listOf("my hero academia", "hero academia", "mha", "bnha"),
        "chainsaw man" to listOf("chainsaw man", "csm"),
        "solo leveling" to listOf("solo leveling"),
        "spy x family" to listOf("spy x family", "spy family", "spyxfamily"),
        "tokyo revengers" to listOf("tokyo revengers", "tokrev"),
        "death note" to listOf("death note"),
        "bleach" to listOf("bleach"),
        "hunter x hunter" to listOf("hunter x hunter", "hxh"),
        "black clover" to listOf("black clover"),
        "blue lock" to listOf("blue lock", "bluelock"),
        "one punch man" to listOf("one punch man", "one punch", "opm"),
        "tokyo ghoul" to listOf("tokyo ghoul"),
        "fullmetal alchemist" to listOf("fullmetal alchemist", "fullmetal", "fma", "fmab"),
        "haikyuu" to listOf("haikyuu", "haikyu", "karasuno"),
        "naruto" to listOf("naruto", "shippuden", "boruto")
    )

    private val characterSearchOverrides = mapOf(
        "izuku midoriya deku" to "Izuku Midoriya Deku",
        "manjiro sano mikey" to "Mikey Manjiro Sano",
        "ken ryuguji draken" to "Draken Ken Ryuguji",
        "loid forger twilight" to "Loid Forger",
        "anya smug face" to "Anya Forger",
        "bang silver fang" to "Silver Fang Bang",
        "chainsaw man devil" to "Chainsaw Man Denji",
        "yoru war devil" to "Yoru Chainsaw Man",
        "kira symbol" to "Death Note L symbol",
        "ant king" to "Beru Solo Leveling",
        "statue of god" to "Statue of God Solo Leveling",
        "franky franklin" to "Franky Franklin Spy x Family",
        "michael kaiser" to "Michael Kaiser Blue Lock",
        "king" to "King One Punch Man"
    )

    private fun safeFileName(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_]"), "_")

    private fun hashUrlKey(url: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }

    private fun ensureCleanCache(ctx: Context) {
        if (legacyCacheCleaned) return
        legacyCacheCleaned = true
        diskWriteExecutor.execute {
            try {
                listOf("bw_previews", "bw_thumbs", "bw_queries").forEach { oldName ->
                    val oldDir = File(ctx.filesDir, oldName)
                    if (oldDir.exists()) oldDir.deleteRecursively()
                }
            } catch (_: Throwable) {}
        }
    }

    private fun previewFile(ctx: Context, id: String): File {
        ensureCleanCache(ctx)
        val dir = File(ctx.filesDir, "bw_previews_v2").apply { if (!exists()) mkdirs() }
        return File(dir, "${safeFileName(id)}.png")
    }

    private fun thumbFile(ctx: Context, urlKey: String): File {
        ensureCleanCache(ctx)
        val dir = File(ctx.filesDir, "bw_thumbs_v2").apply { if (!exists()) mkdirs() }
        return File(dir, "${hashUrlKey(urlKey)}.png")
    }

    private fun queryFile(ctx: Context, cacheKey: String): File {
        ensureCleanCache(ctx)
        val dir = File(ctx.filesDir, "bw_queries_v2").apply { if (!exists()) mkdirs() }
        return File(dir, "${safeFileName(cacheKey)}.txt")
    }

    fun pickDiverseLocalFive(matches: List<Samples.Item>): List<SketchResult> {
        if (matches.isEmpty()) return emptyList()
        val picked = if (matches.size <= 5) {
            matches
        } else {
            val preferredKeywords = listOf(
                listOf("smiling", "uchiha", "classic"),
                listOf("ramen", "classic blue", "scroll"),
                listOf("kunai", "toad", "leaf"),
                listOf("sage", "shuriken", "shippuden"),
                listOf("rasengan", "chidori", "six paths", "chakra")
            )
            val chosen = ArrayList<Samples.Item>(5)
            preferredKeywords.forEach { kws ->
                val hit = matches.firstOrNull { item ->
                    item !in chosen && kws.any { kw -> item.title.lowercase().contains(kw) }
                }
                if (hit != null) chosen.add(hit)
            }
            for (item in matches) {
                if (chosen.size >= 5) break
                if (item !in chosen) chosen.add(item)
            }
            chosen.take(5)
        }
        val moods = listOf(
            L.t("mood_normal"),
            L.t("mood_cute"),
            L.t("mood_lineart"),
            L.t("mood_action"),
            L.t("mood_aggressive")
        )
        return picked.mapIndexed { idx, item ->
            SketchResult(
                id = "local_${item.id}",
                title = item.title,
                mood = moods[idx % moods.size],
                fullUrl = "",
                thumbUrl = "",
                localSample = item
            )
        }
    }

    fun getLocalFiveIfExactHero(character: String, series: String): List<SketchResult> {
        val norm = AnimeCatalog.normalizeQuery(character).trim()
        if (norm.isEmpty()) return emptyList()
        val matched = Samples.byCharacter(norm, series)
        return pickDiverseLocalFive(matched)
    }

    fun cleanSearchQuery(character: String, series: String): String {
        val cleanChar = AnimeCatalog.normalizeQuery(character).trim()
        val cleanSeries = series.trim()
        val mapped = characterSearchOverrides[cleanChar.lowercase()] ?: cleanChar
        return if (cleanSeries.isNotEmpty() && !mapped.lowercase().contains(cleanSeries.lowercase())) {
            "$mapped $cleanSeries"
        } else {
            mapped
        }
    }

    fun getCachedFiveSync(ctx: Context, character: String, series: String): List<SketchResult>? {
        appCtx = ctx.applicationContext
        val cleanChar = AnimeCatalog.normalizeQuery(character).trim()
        val cleanSeries = series.trim()
        if (cleanChar.isEmpty()) return null
        val local = getLocalFiveIfExactHero(cleanChar, cleanSeries)
        if (local.isNotEmpty()) return local

        val cacheKey = "${cleanChar.lowercase()}|${cleanSeries.lowercase()}"
        queryCache.get(cacheKey)?.let { return it }

        val qFile = queryFile(ctx, cacheKey)
        if (qFile.exists() && qFile.length() > 10L) {
            try {
                val parsed = qFile.readLines().mapIndexedNotNull { idx, line ->
                    val parts = line.split("\t")
                    if (parts.size < 4) return@mapIndexedNotNull null
                    SketchResult(
                        id = "web_${cacheKey}_$idx",
                        title = parts[2],
                        mood = parts[3],
                        fullUrl = parts[0],
                        thumbUrl = parts[1],
                        localSample = null
                    )
                }
                if (parsed.isNotEmpty()) {
                    queryCache.put(cacheKey, parsed)
                    return parsed
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    fun getCachedPreviewSync(ctx: Context, item: CategoryCatalog.TopicItem): Bitmap? {
        appCtx = ctx.applicationContext
        if (item.localSample != null) {
            return Samples.loadThumb(ctx, item.localSample, 320)
        }
        previewCache.get(item.id)?.let { return it }

        val file = previewFile(ctx, item.id)
        if (file.exists() && file.length() > 100L) {
            val decoded = try { BitmapFactory.decodeFile(file.absolutePath) } catch (_: Throwable) { null }
            if (decoded != null) {
                previewCache.put(item.id, decoded)
                return decoded
            }
        }
        return null
    }

    fun getCachedThumbSync(ctx: Context, item: SketchResult): Bitmap? {
        appCtx = ctx.applicationContext
        if (item.localSample != null) {
            return Samples.loadThumb(ctx, item.localSample, 420)
        }
        val key = item.thumbUrl.ifEmpty { item.fullUrl }
        if (key.isEmpty()) return null
        thumbBitmapCache.get(key)?.let { return it }

        val file = thumbFile(ctx, key)
        if (file.exists() && file.length() > 100L) {
            val decoded = try { BitmapFactory.decodeFile(file.absolutePath) } catch (_: Throwable) { null }
            if (decoded != null) {
                thumbBitmapCache.put(key, decoded)
                return decoded
            }
        }
        return null
    }

    fun warmupHomePreviews(ctx: Context, homeSeed: Long) {
        val c = ctx.applicationContext
        appCtx = c
        ensureCleanCache(c)

        previewExecutor.execute {
            if (!diskPreviewIndexLoaded) {
                diskPreviewIndexLoaded = true
                try {
                    val dir = File(c.filesDir, "bw_previews_v2")
                    if (dir.exists()) {
                        dir.listFiles()
                            ?.asSequence()
                            ?.filter { it.extension == "png" && it.length() > 100L }
                            ?.sortedByDescending { it.lastModified() }
                            ?.take(120)
                            ?.forEach { file ->
                                val key = file.nameWithoutExtension
                                if (previewCache.get(key) == null) {
                                    BitmapFactory.decodeFile(file.absolutePath)?.let { bmp ->
                                        previewCache.put(key, bmp)
                                    }
                                }
                            }
                    }
                } catch (_: Throwable) {}
            }

            val covers = CategoryCatalog.animeSeriesCovers()
            covers.filter { it.localSample != null }.forEach {
                it.localSample?.let { s -> Samples.loadThumb(c, s, 320) }
            }
        }

        val covers = CategoryCatalog.animeSeriesCovers()
        val spotlight = CategoryCatalog.randomMixedSpotlight(homeSeed, 14)
        val grid = CategoryCatalog.randomMixedGrid(homeSeed, 16)

        val seen = HashSet<String>()
        val priorityQueue = ArrayList<CategoryCatalog.TopicItem>(60)
        (covers + spotlight + grid + CategoryCatalog.fruits.take(6) + CategoryCatalog.cars.take(6)).forEach { item ->
            if (seen.add(item.id)) priorityQueue.add(item)
        }
        priorityQueue.forEach { item ->
            if (getCachedPreviewSync(c, item) == null) {
                loadTopicPreviewBitmap(c, item) {}
            }
        }
    }

    fun prewarmTopics(ctx: Context, items: List<CategoryCatalog.TopicItem>) {
        val c = ctx.applicationContext
        appCtx = c
        items.forEach { item ->
            if (getCachedPreviewSync(c, item) == null) {
                loadTopicPreviewBitmap(c, item) {}
            }
        }
    }

    private fun saveThumbToDiskAsync(urlKey: String, bmp: Bitmap) {
        val c = appCtx ?: return
        diskWriteExecutor.execute {
            try {
                val f = thumbFile(c, urlKey)
                if (!f.exists() || f.length() < 100L) {
                    FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
                }
            } catch (_: Throwable) {}
        }
    }

    private fun saveQueryToDiskAsync(cacheKey: String, results: List<SketchResult>) {
        val c = appCtx ?: return
        diskWriteExecutor.execute {
            try {
                val f = queryFile(c, cacheKey)
                val body = results.joinToString("\n") { r ->
                    listOf(
                        r.fullUrl.replace("\t", " "),
                        r.thumbUrl.replace("\t", " "),
                        r.title.replace("\t", " "),
                        r.mood.replace("\t", " ")
                    ).joinToString("\t")
                }
                f.writeText(body)
            } catch (_: Throwable) {}
        }
    }

    /**
     * Fetches strictly 5 clean black-and-white line-art sketches exclusively via Microsoft Bing Images
     * (never Google, never converting colorful pictures into fake B&W).
     */
    fun getFiveSketches(
        character: String,
        series: String,
        forceRefresh: Boolean = false,
        onComplete: (List<SketchResult>) -> Unit
    ) {
        val cleanChar = AnimeCatalog.normalizeQuery(character).trim()
        val cleanSeries = series.trim()
        if (cleanChar.isEmpty()) {
            mainHandler.post { onComplete(emptyList()) }
            return
        }

        val localFive = getLocalFiveIfExactHero(cleanChar, cleanSeries)
        if (localFive.isNotEmpty()) {
            mainHandler.post { onComplete(localFive) }
            return
        }

        val cacheKey = "${cleanChar.lowercase()}|${cleanSeries.lowercase()}"
        if (!forceRefresh) {
            appCtx?.let { c ->
                getCachedFiveSync(c, cleanChar, cleanSeries)?.let { cached ->
                    mainHandler.post { onComplete(cached) }
                    return
                }
            } ?: queryCache.get(cacheKey)?.let { cached ->
                mainHandler.post { onComplete(cached) }
                return
            }
        }

        searchExecutor.execute {
            val subject = cleanSearchQuery(cleanChar, cleanSeries)
            val verified = ArrayList<Candidate>(5)
            val seenKeys = HashSet<String>()

            fun absorbPool(rawCandidates: List<Candidate>) {
                if (verified.size >= 5 || rawCandidates.isEmpty()) return
                val fresh = mergeAndRankCandidates(rawCandidates).filter { c ->
                    candidateKey(c) !in seenKeys
                }
                val passed = validateAndPreload(fresh, targetCount = 5 - verified.size)
                passed.forEach {
                    seenKeys.add(candidateKey(it))
                    verified.add(it)
                }
            }

            // Tier 1: Microsoft Bing Line-Drawing filter (color2-bw + photo-linedrawing)
            val fB1 = searchExecutor.submit<List<Candidate>> {
                fetchBingBwCandidates(
                    "$subject anime coloring page black and white line art",
                    cleanChar,
                    cleanSeries,
                    lineArtFilter = true,
                    maxCount = 28,
                    thumbW = 420,
                    fast = true
                )
            }
            val fB2 = searchExecutor.submit<List<Candidate>> {
                fetchBingBwCandidates(
                    "$subject anime sketch drawing outline",
                    cleanChar,
                    cleanSeries,
                    lineArtFilter = true,
                    maxCount = 24,
                    thumbW = 420,
                    fast = true
                )
            }

            absorbPool(try { fB1.get() } catch (_: Throwable) { emptyList() })
            if (verified.size < 5) {
                absorbPool(try { fB2.get() } catch (_: Throwable) { emptyList() })
            } else {
                fB2.cancel(false)
            }

            // Tier 2: Microsoft Bing B&W filter for additional verified white-paper drawings
            if (verified.size < 5) {
                val b3 = fetchBingBwCandidates(
                    "$subject black and white drawing sketch",
                    cleanChar,
                    cleanSeries,
                    lineArtFilter = false,
                    maxCount = 24,
                    thumbW = 420,
                    fast = true
                )
                absorbPool(b3)
            }

            // Tier 3: Microsoft Bing manga/pencil line fallback for rare characters
            if (verified.size < 5) {
                val b4 = fetchBingBwCandidates(
                    "$subject manga pencil line art",
                    cleanChar,
                    cleanSeries,
                    lineArtFilter = false,
                    maxCount = 24,
                    thumbW = 420,
                    fast = false
                )
                absorbPool(b4)
            }

            val moodLabels = listOf(
                L.t("mood_normal"),
                L.t("mood_lineart"),
                L.t("mood_action"),
                L.t("mood_cute"),
                L.t("mood_aggressive")
            )
            val displayTitle = cleanChar.replaceFirstChar { it.uppercase() }
            val results = verified.take(5).mapIndexed { idx, cand ->
                SketchResult(
                    id = "web_${cacheKey}_$idx",
                    title = displayTitle,
                    mood = moodLabels[idx % moodLabels.size],
                    fullUrl = cand.fullUrl,
                    thumbUrl = cand.thumbUrl,
                    localSample = null
                )
            }

            if (results.isNotEmpty()) {
                queryCache.put(cacheKey, results)
                saveQueryToDiskAsync(cacheKey, results)
            }
            mainHandler.post { onComplete(results) }
        }
    }

    private fun candidateKey(c: Candidate): String {
        return extractOipKey(c.thumbUrl).ifEmpty { c.thumbUrl.ifEmpty { c.fullUrl }.substringBefore("?") }
    }

    private fun mergeAndRankCandidates(raw: List<Candidate>): List<Candidate> {
        val seen = HashSet<String>()
        val deduped = ArrayList<Candidate>(raw.size)
        for (c in raw.sortedByDescending { it.score }) {
            val key = candidateKey(c)
            if (seen.add(key)) {
                deduped.add(c)
            }
        }
        return deduped
    }

    private fun validateAndPreload(candidates: List<Candidate>, targetCount: Int): ArrayList<Candidate> {
        val verified = ArrayList<Candidate>(targetCount)
        if (candidates.isEmpty() || targetCount <= 0) return verified

        val pool = candidates.take(28)
        var idx = 0
        while (idx < pool.size && verified.size < targetCount) {
            val needed = targetCount - verified.size
            val batchSize = (needed + 5).coerceAtMost(10)
            val batch = pool.subList(idx, minOf(idx + batchSize, pool.size))
            val ecs = ExecutorCompletionService<Pair<Candidate, Bitmap?>?>(imgExecutor)

            for (cand in batch) {
                ecs.submit {
                    val fetchUrl = cand.thumbUrl.ifEmpty { cand.fullUrl }
                    val rawBmp = fetchBitmapFromUrl(fetchUrl, maxDim = 420, fast = true)
                        ?: return@submit null
                    if (rawBmp.width < 85 || rawBmp.height < 85) return@submit null
                    val ratio = maxOf(rawBmp.width, rawBmp.height).toFloat() / minOf(rawBmp.width, rawBmp.height).toFloat()
                    if (ratio > 3.2f) return@submit null
                    if (!ImageProcessor.isLikelyBwDrawing(rawBmp)) return@submit null
                    cand to ImageProcessor.toCleanBwSketch(rawBmp)
                }
            }

            for (i in 0 until batch.size) {
                val res = try { ecs.take().get() } catch (_: Throwable) { null }
                if (res != null) {
                    val (cand, cleanBmp) = res
                    if (cleanBmp != null) {
                        val cacheKey = cand.thumbUrl.ifEmpty { cand.fullUrl }
                        if (cacheKey.isNotEmpty()) {
                            thumbBitmapCache.put(cacheKey, cleanBmp)
                            saveThumbToDiskAsync(cacheKey, cleanBmp)
                        }
                        verified.add(cand)
                        if (verified.size >= targetCount) break
                    }
                }
            }
            idx += batchSize
        }
        return verified
    }

    private fun fetchBingBwCandidates(
        query: String,
        character: String,
        series: String,
        lineArtFilter: Boolean,
        maxCount: Int = 24,
        thumbW: Int = 360,
        fast: Boolean = true
    ): List<Candidate> {
        return try {
            val filter = if (lineArtFilter) "+filterui:color2-bw+filterui:photo-linedrawing" else "+filterui:color2-bw"
            val url = "https://www.bing.com/images/search?q=${Uri.encode(query)}&qft=${Uri.encode(filter)}&first=1&count=$maxCount"
            val html = fetchHtml(url, mobileUa = false, fast = fast) ?: return emptyList()
            val out = ArrayList<Candidate>()

            for (match in iuscRegex.findAll(html)) {
                val decoded = Html.fromHtml(match.groupValues[1], Html.FROM_HTML_MODE_LEGACY).toString()
                val mUrl = murlRegex.find(decoded)?.groupValues?.get(1) ?: continue
                val rawTurl = turlRegex.find(decoded)?.groupValues?.get(1) ?: continue
                val title = titleRegex.find(decoded)?.groupValues?.get(1) ?: ""
                val pUrl = purlRegex.find(decoded)?.groupValues?.get(1) ?: ""

                val lowM = mUrl.lowercase()
                if (blockedHosts.any { lowM.contains(it) }) continue
                if (lowM.endsWith(".svg") || lowM.endsWith(".gif")) continue

                val score = scoreCandidate(mUrl, title, pUrl, character, series)
                if (score < 0) continue

                val cleanThumb = rawTurl
                    .substringBefore("&w=")
                    .substringBefore("&h=") + "&w=$thumbW&rs=1&pid=ImgDetMain"
                out.add(Candidate(mUrl, cleanThumb, title, pUrl, score))
                if (out.size >= maxCount) break
            }
            out
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Strictly scores Bing candidate relevance:
     * - Rejects wrong characters, cross-franchise anime results, wallpapers, screenshots, colored artwork
     * - Requires core subject tokens (and series validation for anime/ambiguous names) to match.
     */
    private fun scoreCandidate(
        imgUrl: String,
        title: String,
        pageUrl: String,
        character: String,
        series: String
    ): Int {
        val combined = "$title $pageUrl $imgUrl".lowercase()
        val queryLower = "$character $series".lowercase()

        for (term in offTopicTerms) {
            if (!queryLower.contains(term) && combined.contains(term)) {
                return -1
            }
        }

        // Reject explicitly colored or painterly artwork (allow "coloring"/"colouring" outline pages)
        val noColoring = combined.replace("coloring", "").replace("colouring", "")
        if (Regex("""\b(full\s*color|colored|coloured|watercolor|oil\s*painting|acrylic)\b""").containsMatchIn(noColoring)) {
            return -1
        }

        val seriesKey = series.lowercase().trim()
        val isKnownAnimeSeries = seriesKey.isNotEmpty() && seriesAliases.containsKey(seriesKey)
        val ownSeriesAliases = if (isKnownAnimeSeries) {
            seriesAliases[seriesKey] ?: extractNameTokens(series)
        } else {
            emptyList()
        }
        val hasSeriesMatch = ownSeriesAliases.any { combined.contains(it) }

        if (isKnownAnimeSeries && !hasSeriesMatch) {
            for ((otherSeries, otherAliases) in seriesAliases) {
                if (otherSeries == seriesKey) continue
                if (otherAliases.any { alias ->
                        alias.length >= 4 && Regex("""\b${Regex.escape(alias)}\b""").containsMatchIn(combined)
                    }
                ) {
                    return -1
                }
            }
        }

        val charTokens = extractNameTokens(character)
        val matchedCharTokens = charTokens.filter { tok ->
            Regex("""\b${Regex.escape(tok)}\b""").containsMatchIn(combined) ||
                (tok.length >= 4 && combined.contains(tok))
        }

        val hasMetadata = title.isNotBlank() || pageUrl.isNotBlank()
        if (charTokens.isNotEmpty()) {
            if (isKnownAnimeSeries) {
                if (hasMetadata && matchedCharTokens.isEmpty()) return -1
                if (!hasMetadata && matchedCharTokens.isEmpty() && !hasSeriesMatch) return -1
            } else {
                // For single-word topics (e.g. "Mango", "Lamborghini", "Cat"), that word MUST match.
                // For multi-word non-anime topics (e.g. "Ford Mustang", "Mountain River Boat"),
                // require at least one core token (or last distinctive token like "mustang"/"wagon").
                if (charTokens.size == 1) {
                    if (hasMetadata && matchedCharTokens.isEmpty()) return -1
                    if (!hasMetadata && matchedCharTokens.isEmpty()) return -1
                } else {
                    if (hasMetadata && matchedCharTokens.isEmpty()) return -1
                    if (!hasMetadata && matchedCharTokens.isEmpty()) return -1
                }
            }
        }

        val allMatchedAmbiguous = matchedCharTokens.isNotEmpty() && matchedCharTokens.all { it in ambiguousCharWords }
        if (isKnownAnimeSeries && allMatchedAmbiguous && !hasSeriesMatch) {
            return -1
        }

        if (seriesKey == "haikyuu" && (combined.contains("hyuga") || combined.contains("byakugan"))) {
            return -1
        }
        if (seriesKey == "naruto" && (combined.contains("shoyo") || combined.contains("volleyball") || combined.contains("karasuno"))) {
            return -1
        }

        var score = 0
        score += matchedCharTokens.size * 20
        if (hasSeriesMatch) score += 26
        if (combined.contains("coloring page") || combined.contains("coloring sheet") || combined.contains("line art") || combined.contains("lineart")) score += 18
        if (combined.contains("sketch") || combined.contains("outline") || combined.contains("how to draw")) score += 12
        if (combined.contains("black and white") || combined.contains("b&w") || combined.contains("drawing")) score += 8
        return score
    }

    private fun extractNameTokens(raw: String): List<String> {
        val cleaned = raw.lowercase().replace(Regex("""[^a-z0-9\s]"""), " ")
        val words = cleaned.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val strong = words.filter { it.length >= 3 && it !in genericTopicStopWords }
        return if (strong.isNotEmpty()) strong else words.filter { it.length >= 2 }
    }

    private fun fetchHtml(urlStr: String, mobileUa: Boolean, fast: Boolean = true): String? {
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = if (fast) 2600 else 4800
                readTimeout = if (fast) 3400 else 5800
                instanceFollowRedirects = true
                setRequestProperty("Connection", "keep-alive")
                setRequestProperty(
                    "User-Agent",
                    if (mobileUa) {
                        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                    } else {
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                    }
                )
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractOipKey(url: String): String {
        val idx = url.indexOf("OIP.")
        return if (idx >= 0) url.substring(idx).substringBefore("&") else ""
    }

    fun loadThumbBitmap(ctx: Context, item: SketchResult, onLoaded: (Bitmap?) -> Unit) {
        appCtx = ctx.applicationContext
        getCachedThumbSync(ctx, item)?.let {
            onLoaded(it)
            return
        }
        val key = item.thumbUrl.ifEmpty { item.fullUrl }
        if (key.isEmpty()) {
            onLoaded(null)
            return
        }
        imgExecutor.execute {
            val raw = fetchBitmapFromUrl(item.thumbUrl, maxDim = 420, fast = true)
                ?: fetchBitmapFromUrl(item.fullUrl, maxDim = 420, fast = true)
            val valid = raw?.takeIf { ImageProcessor.isLikelyBwDrawing(it) }
            val bmp = valid?.let { ImageProcessor.toCleanBwSketch(it) }
            if (bmp != null) {
                thumbBitmapCache.put(key, bmp)
                saveThumbToDiskAsync(key, bmp)
            }
            mainHandler.post { onLoaded(bmp) }
        }
    }

    fun loadFullForEditor(ctx: Context, item: SketchResult, onLoaded: (Bitmap?) -> Unit) {
        appCtx = ctx.applicationContext
        if (item.localSample != null) {
            onLoaded(Samples.loadFull(ctx, item.localSample))
            return
        }
        imgExecutor.execute {
            val hdThumb = item.thumbUrl
                .replace("&w=540", "&w=960")
                .replace("&w=480", "&w=960")
                .replace("&w=420", "&w=960")
                .replace("&w=320", "&w=960")
                .replace("&w=280", "&w=960")
            val raw = fetchBitmapFromUrl(item.fullUrl, maxDim = 1440, fast = false)
                ?: fetchBitmapFromUrl(hdThumb, maxDim = 1280, fast = true)
                ?: fetchBitmapFromUrl(item.thumbUrl, maxDim = 960, fast = true)
            val clean = raw?.let {
                val argb = if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false)
                ImageProcessor.toCleanBwSketch(argb)
            } ?: getCachedThumbSync(ctx, item)
            mainHandler.post { onLoaded(clean) }
        }
    }

    fun loadTopicPreviewBitmap(
        ctx: Context,
        item: CategoryCatalog.TopicItem,
        onLoaded: (Bitmap?) -> Unit
    ) {
        appCtx = ctx.applicationContext
        getCachedPreviewSync(ctx, item)?.let {
            onLoaded(it)
            return
        }

        val key = item.id
        val cacheFile = previewFile(ctx, key)

        val waiters = inFlightPreviews.computeIfAbsent(key) { java.util.Collections.synchronizedList(ArrayList()) }
        val isFirst = synchronized(waiters) {
            val empty = waiters.isEmpty()
            waiters.add(onLoaded)
            empty
        }
        if (!isFirst) return

        previewExecutor.execute {
            var result: Bitmap? = null
            try {
                if (cacheFile.exists() && cacheFile.length() > 100L) {
                    BitmapFactory.decodeFile(cacheFile.absolutePath)?.let { decoded ->
                        result = decoded
                    }
                }
                if (result == null) {
                    val subjectChar = item.characterName.ifEmpty { item.title }
                    val subjectSeries = item.seriesName
                    val q = item.searchQuery.trim()

                    var candidates = fetchBingBwCandidates(
                        "$q black and white coloring page line art",
                        subjectChar,
                        subjectSeries,
                        lineArtFilter = true,
                        maxCount = 20,
                        thumbW = 320,
                        fast = true
                    )
                    if (candidates.size < 4) {
                        candidates = candidates + fetchBingBwCandidates(
                            "$q sketch drawing black and white",
                            subjectChar,
                            subjectSeries,
                            lineArtFilter = true,
                            maxCount = 16,
                            thumbW = 320,
                            fast = true
                        )
                    }
                    if (candidates.isEmpty()) {
                        candidates = fetchBingBwCandidates(
                            "$q drawing outline",
                            subjectChar,
                            subjectSeries,
                            lineArtFilter = false,
                            maxCount = 16,
                            thumbW = 320,
                            fast = true
                        )
                    }

                    val ranked = mergeAndRankCandidates(candidates).take(10)
                    for (cand in ranked) {
                        val previewUrl = cand.thumbUrl.ifEmpty { cand.fullUrl }
                        val raw = fetchBitmapFromUrl(previewUrl, maxDim = 320, fast = true) ?: continue
                        if (raw.width < 75 || raw.height < 75) continue
                        val ratio = maxOf(raw.width, raw.height).toFloat() / minOf(raw.width, raw.height).toFloat()
                        if (ratio > 3.0f) continue
                        if (!ImageProcessor.isLikelyBwDrawing(raw)) continue
                        val clean = ImageProcessor.toCleanBwSketch(raw)
                        previewUrlCache[key] = cand.fullUrl to cand.thumbUrl
                        result = clean
                        diskWriteExecutor.execute {
                            try {
                                FileOutputStream(cacheFile).use { clean.compress(Bitmap.CompressFormat.PNG, 90, it) }
                            } catch (_: Throwable) {}
                        }
                        break
                    }
                }
            } catch (_: Throwable) {}

            result?.let { previewCache.put(key, it) }
            val callbacks = inFlightPreviews.remove(key)?.let { list -> synchronized(list) { list.toList() } } ?: emptyList()
            mainHandler.post {
                callbacks.forEach { cb -> cb(result) }
            }
        }
    }

    fun loadTopicFullForEditor(
        ctx: Context,
        item: CategoryCatalog.TopicItem,
        onLoaded: (Bitmap?) -> Unit
    ) {
        appCtx = ctx.applicationContext
        if (item.localSample != null) {
            onLoaded(Samples.loadFull(ctx, item.localSample))
            return
        }
        imgExecutor.execute {
            val urls = previewUrlCache[item.id]
            var fullBmp: Bitmap? = null
            if (urls != null) {
                val (fullUrl, thumbUrl) = urls
                val hdThumb = thumbUrl.replace("&w=540", "&w=960").replace("&w=480", "&w=960")
                    .replace("&w=320", "&w=960").replace("&w=280", "&w=960")
                val raw = fetchBitmapFromUrl(fullUrl, maxDim = 1440, fast = false)
                    ?: fetchBitmapFromUrl(hdThumb, maxDim = 1280, fast = true)
                    ?: fetchBitmapFromUrl(thumbUrl, maxDim = 960, fast = true)
                fullBmp = raw?.let {
                    val argb = if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false)
                    ImageProcessor.toCleanBwSketch(argb)
                }
            }
            if (fullBmp == null) {
                val subjectChar = item.characterName.ifEmpty { item.title }
                val subjectSeries = item.seriesName
                val q = item.searchQuery.trim()
                val pool = fetchBingBwCandidates(
                    "$q black and white coloring page line art",
                    subjectChar,
                    subjectSeries,
                    lineArtFilter = true,
                    maxCount = 16,
                    thumbW = 640,
                    fast = false
                )
                for (cand in mergeAndRankCandidates(pool).take(8)) {
                    val raw = fetchBitmapFromUrl(cand.fullUrl, maxDim = 1280, fast = false)
                        ?: fetchBitmapFromUrl(cand.thumbUrl, maxDim = 960, fast = true)
                        ?: continue
                    if (!ImageProcessor.isLikelyBwDrawing(raw)) continue
                    fullBmp = ImageProcessor.toCleanBwSketch(raw)
                    previewUrlCache[item.id] = cand.fullUrl to cand.thumbUrl
                    break
                }
            }
            if (fullBmp == null) {
                fullBmp = getCachedPreviewSync(ctx, item)
            }
            mainHandler.post { onLoaded(fullBmp) }
        }
    }

    private fun fetchBitmapFromUrl(urlStr: String, maxDim: Int = 1280, fast: Boolean = false): Bitmap? {
        if (!urlStr.startsWith("http")) return null
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = if (fast) 2500 else 5000
                readTimeout = if (fast) 3300 else 6500
                instanceFollowRedirects = true
                setRequestProperty("Connection", "keep-alive")
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                )
            }
            if (conn.responseCode !in 200..299) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) return null
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
            var sample = 1
            while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) sample *= 2
            val dec = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, dec)
        } catch (_: Throwable) {
            null
        }
    }
}
