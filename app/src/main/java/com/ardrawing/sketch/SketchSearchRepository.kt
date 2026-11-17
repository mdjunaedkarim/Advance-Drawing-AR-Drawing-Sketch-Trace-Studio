package com.ardrawing.sketch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.util.LruCache
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.Future

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

    private val searchExecutor = Executors.newFixedThreadPool(4)
    private val imgExecutor = Executors.newFixedThreadPool(6)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val queryCache = LruCache<String, List<SketchResult>>(80)
    private val thumbBitmapCache = LruCache<String, Bitmap>(180)

    private val iuscRegex = Regex("""class="iusc"[^>]*?m="([^"]+)"""")
    private val murlRegex = Regex(""""murl"\s*:\s*"(https?://[^"]+)"""")
    private val turlRegex = Regex(""""turl"\s*:\s*"(https?://[^"]+)"""")
    private val titleRegex = Regex(""""t"\s*:\s*"([^"]*)"""")
    private val purlRegex = Regex(""""purl"\s*:\s*"(https?://[^"]+)"""")

    // Google Images script / HTML patterns
    private val gTbnUrlRegex = Regex("""https://encrypted-tbn0\.gstatic\.com/images\?q=tbn:[^"'\s\\<>]+""")
    private val gImgPairRegex = Regex("""\["(https?://[^"\\]+)",\s*(\d{3,4}),\s*(\d{3,4})\]""")

    private val blockedHosts = listOf(
        "shutterstock", "alamy", "istockphoto", "dreamstime", "123rf",
        "depositphotos", "gettyimages", "vecteezy", "freepik", "vectorstock",
        "favicon", "logo", "sprite", "doubleclick", "tenor.com", "giphy.com",
        "gstatic.com/images?q=tbn:ANd9GcQ_blocked"
    )

    private val offTopicTerms = listOf(
        "gta", "grand theft", "gtav", "gta5", "gta 5", "san andreas", "vice city",
        "rockstar games", "franklin clinton", "michael de santa", "trevor philips",
        "fortnite", "minecraft", "roblox", "valorant", "pubg", "free fire",
        "call of duty", "cyberpunk 2077", "genshin impact",
        "cosplay", "action figure", "funko", "plush", "t-shirt", "hoodie"
    )

    private val ambiguousCharWords = setOf(
        "king", "pain", "power", "franklin", "michael", "tank", "iron", "panda",
        "twice", "stain", "scar", "greed", "envy", "lust", "sloth", "beam",
        "bill", "camilla", "millie", "sharon", "uta", "rem", "near", "mello",
        "light", "law", "kid", "cell", "angel", "eri", "ace", "sabo", "hawks",
        "franky", "brook", "nami", "robin", "kite", "grey", "luna", "tusk",
        "symbol", "statue", "ant", "beast", "dragon", "rose", "lotus", "lion"
    )

    private val drawingContextWords = listOf(
        "anime", "manga", "sketch", "drawing", "draw", "lineart", "line art",
        "coloring", "colouring", "pencil", "ink", "outline", "chibi", "fanart", "how to draw"
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

        // Check curated local sketches first for bundled Naruto characters
        val localFive = getLocalFiveIfExactHero(cleanChar, cleanSeries)
        if (localFive.isNotEmpty()) {
            mainHandler.post { onComplete(localFive) }
            return
        }

        val cacheKey = "${cleanChar.lowercase()}|${cleanSeries.lowercase()}"
        if (!forceRefresh) {
            queryCache.get(cacheKey)?.let { cached ->
                mainHandler.post { onComplete(cached) }
                return
            }
        }

        searchExecutor.execute {
            val subject = cleanSearchQuery(cleanChar, cleanSeries)
            val qSketch = "$subject black and white sketch drawing"
            val qLineArt = "$subject line art coloring drawing"

            val googleFuture = searchExecutor.submit<List<Candidate>> {
                fetchGoogleBwCandidates(qSketch, cleanChar, cleanSeries)
            }
            val bingSketchFuture = searchExecutor.submit<List<Candidate>> {
                fetchBingBwCandidates(qSketch, cleanChar, cleanSeries, lineArtFilter = true)
            }
            val bingLineFuture = searchExecutor.submit<List<Candidate>> {
                fetchBingBwCandidates(qLineArt, cleanChar, cleanSeries, lineArtFilter = false)
            }

            val googleList = try { googleFuture.get() } catch (_: Throwable) { emptyList() }
            val bingA = try { bingSketchFuture.get() } catch (_: Throwable) { emptyList() }
            val bingB = try { bingLineFuture.get() } catch (_: Throwable) { emptyList() }

            val allCandidates = mergeAndRankCandidates(googleList + bingA + bingB)
            val verified = validateAndPreloadFive(allCandidates, targetCount = 5)

            // Fallback broader B&W drawing query if fewer than 5 verified sketches passed
            if (verified.size < 5) {
                val extraGoogle = fetchGoogleBwCandidates("$subject drawing", cleanChar, cleanSeries)
                val extraBing = fetchBingBwCandidates("$subject pencil drawing", cleanChar, cleanSeries, lineArtFilter = false)
                val alreadyUrls = verified.mapTo(HashSet()) { it.fullUrl }
                val extraCandidates = mergeAndRankCandidates(extraGoogle + extraBing)
                    .filter { it.fullUrl !in alreadyUrls }
                val moreVerified = validateAndPreloadFive(extraCandidates, targetCount = 5 - verified.size)
                verified.addAll(moreVerified)
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
            }
            mainHandler.post { onComplete(results) }
        }
    }

    private fun mergeAndRankCandidates(raw: List<Candidate>): List<Candidate> {
        val seen = HashSet<String>()
        val deduped = ArrayList<Candidate>(raw.size)
        for (c in raw.sortedByDescending { it.score }) {
            val key = extractOipKey(c.thumbUrl).ifEmpty { c.fullUrl.substringBefore("?") }
            if (seen.add(key)) {
                deduped.add(c)
            }
        }
        return deduped
    }

    private fun validateAndPreloadFive(candidates: List<Candidate>, targetCount: Int): ArrayList<Candidate> {
        val verified = ArrayList<Candidate>(targetCount)
        if (candidates.isEmpty() || targetCount <= 0) return verified

        val pool = candidates.take(18)
        var idx = 0
        while (idx < pool.size && verified.size < targetCount) {
            val batch = pool.subList(idx, minOf(idx + 4, pool.size))
            val futures = ArrayList<Pair<Candidate, Future<Bitmap?>>>(batch.size)
            for (cand in batch) {
                val f = imgExecutor.submit<Bitmap?> {
                    val rawBmp = fetchBitmapFromUrl(cand.thumbUrl, maxDim = 560)
                        ?: fetchBitmapFromUrl(cand.fullUrl, maxDim = 560)
                        ?: return@submit null
                    if (rawBmp.width < 100 || rawBmp.height < 100) return@submit null
                    val ratio = maxOf(rawBmp.width, rawBmp.height).toFloat() / minOf(rawBmp.width, rawBmp.height).toFloat()
                    if (ratio > 3.5f) return@submit null
                    if (!ImageProcessor.isLikelyBwDrawing(rawBmp)) return@submit null
                    ImageProcessor.toCleanBwSketch(rawBmp)
                }
                futures.add(cand to f)
            }
            for ((cand, future) in futures) {
                if (verified.size >= targetCount) break
                val cleanBmp = try { future.get() } catch (_: Throwable) { null }
                if (cleanBmp != null) {
                    val cacheKey = cand.thumbUrl.ifEmpty { cand.fullUrl }
                    if (cacheKey.isNotEmpty()) {
                        thumbBitmapCache.put(cacheKey, cleanBmp)
                    }
                    verified.add(cand)
                }
            }
            idx += 4
        }
        return verified
    }

    private fun fetchGoogleBwCandidates(query: String, character: String, series: String): List<Candidate> {
        return try {
            val url = "https://www.google.com/search?tbm=isch&q=${Uri.encode(query)}&tbs=ic:gray"
            val html = fetchHtml(url) ?: return emptyList()
            val out = ArrayList<Candidate>()

            // Extract embedded high-res image arrays ["https://...", width, height] from Google script blocks
            val fullUrls = ArrayList<Pair<String, Int>>()
            for (m in gImgPairRegex.findAll(html)) {
                val imgUrl = unescapeJsonUrl(m.groupValues[1])
                val w = m.groupValues[2].toIntOrNull() ?: 0
                val h = m.groupValues[3].toIntOrNull() ?: 0
                if (w < 180 || h < 180) continue
                val low = imgUrl.lowercase()
                if (low.contains("gstatic.com") || low.contains("google.com")) continue
                if (blockedHosts.any { low.contains(it) }) continue
                if (low.endsWith(".svg") || low.endsWith(".gif")) continue
                fullUrls.add(imgUrl to m.range.first)
            }

            // Pair nearby encrypted-tbn thumbnails with fullUrls when available
            val tbnMatches = gTbnUrlRegex.findAll(html).toList()
            for ((uUrl, pos) in fullUrls) {
                val nearTbn = tbnMatches.minByOrNull { kotlin.math.abs(it.range.first - pos) }
                    ?.takeIf { kotlin.math.abs(it.range.first - pos) < 600 }
                    ?.value ?: uUrl
                val windowStart = (pos - 400).coerceAtLeast(0)
                val windowEnd = (pos + 600).coerceAtMost(html.length)
                val snippet = html.substring(windowStart, windowEnd)
                val score = scoreCandidate(uUrl, snippet, "", character, series)
                if (score >= 0) {
                    out.add(Candidate(uUrl, nearTbn, snippet, "", score + 10))
                }
                if (out.size >= 14) break
            }

            // If Google only gave encrypted-tbn thumbnails in basic HTML, collect those with surrounding alt text
            if (out.isEmpty()) {
                for (m in tbnMatches) {
                    val tbn = Html.fromHtml(m.value, Html.FROM_HTML_MODE_LEGACY).toString()
                    val windowStart = (m.range.first - 300).coerceAtLeast(0)
                    val windowEnd = (m.range.last + 300).coerceAtMost(html.length)
                    val snippet = html.substring(windowStart, windowEnd)
                    val score = scoreCandidate(tbn, snippet, "", character, series)
                    if (score >= 0) {
                        out.add(Candidate(tbn, tbn, snippet, "", score))
                    }
                    if (out.size >= 12) break
                }
            }
            out
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun fetchBingBwCandidates(
        query: String,
        character: String,
        series: String,
        lineArtFilter: Boolean
    ): List<Candidate> {
        return try {
            val filter = if (lineArtFilter) "+filterui:color2-bw+filterui:photo-linedrawing" else "+filterui:color2-bw"
            val url = "https://www.bing.com/images/search?q=${Uri.encode(query)}&qft=${Uri.encode(filter)}&first=1&count=35"
            val html = fetchHtml(url) ?: return emptyList()
            val out = ArrayList<Candidate>()

            for (match in iuscRegex.findAll(html)) {
                val decoded = Html.fromHtml(match.groupValues[1], Html.FROM_HTML_MODE_LEGACY).toString()
                val mUrl = murlRegex.find(decoded)?.groupValues?.get(1) ?: continue
                // Strictly require turl from INSIDE this same iusc JSON block so images never mismatch
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
                    .substringBefore("&h=") + "&w=480&rs=1&pid=ImgDetMain"
                out.add(Candidate(mUrl, cleanThumb, title, pUrl, score))
                if (out.size >= 24) break
            }
            out
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Returns -1 to reject off-topic / wrong-character / GTA results,
     * or a positive relevance score for matching B&W sketch candidates.
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

        // Hard reject GTA V and unrelated video games / merch unless explicitly searched
        for (term in offTopicTerms) {
            if (!queryLower.contains(term) && combined.contains(term)) {
                return -1
            }
        }

        // Reject explicitly colored artwork (allow "coloring"/"colouring" pages which are B&W outlines)
        val noColoring = combined.replace("coloring", "").replace("colouring", "")
        if (Regex("""\b(full\s*color|colored|coloured)\b""").containsMatchIn(noColoring)) {
            return -1
        }

        val charTokens = extractNameTokens(character)
        val seriesTokens = extractNameTokens(series)

        val matchedCharTokens = charTokens.filter { tok ->
            Regex("""\b${Regex.escape(tok)}\b""").containsMatchIn(combined) ||
                (tok.length >= 4 && combined.contains(tok))
        }

        // If title or pageUrl metadata is present, enforce character token relevance!
        val hasMetadata = title.isNotBlank() || pageUrl.isNotBlank()
        if (hasMetadata && charTokens.isNotEmpty() && matchedCharTokens.isEmpty()) {
            return -1
        }

        // Guard ambiguous names (e.g. Franklin, Michael, King, Pain, Power, Tank, Scar, etc.)
        val allMatchedAmbiguous = matchedCharTokens.isNotEmpty() && matchedCharTokens.all { it in ambiguousCharWords }
        val hasSeriesMatch = seriesTokens.any { combined.contains(it) }
        val hasDrawingContext = drawingContextWords.any { combined.contains(it) }

        if (allMatchedAmbiguous && !hasSeriesMatch && !hasDrawingContext) {
            return -1
        }
        // For characters whose full name includes a common game/pop-culture word (e.g. Franky Franklin, Michael Kaiser),
        // require the series or anime/drawing context if only the ambiguous word matched.
        if (allMatchedAmbiguous && seriesTokens.isNotEmpty() && !hasSeriesMatch && !combined.contains("anime") && !combined.contains("manga")) {
            return -1
        }

        var score = 0
        score += matchedCharTokens.size * 15
        if (hasSeriesMatch) score += 20
        if (combined.contains("sketch") || combined.contains("lineart") || combined.contains("line art")) score += 12
        if (combined.contains("drawing") || combined.contains("coloring")) score += 8
        if (combined.contains("black and white") || combined.contains("b&w") || combined.contains("pencil")) score += 8
        return score
    }

    private fun extractNameTokens(raw: String): List<String> {
        val stop = setOf("the", "and", "of", "for", "with", "devil", "face", "smug", "symbol", "statue", "king", "man")
        val cleaned = raw.lowercase().replace(Regex("""[^a-z0-9\s]"""), " ")
        val words = cleaned.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val strong = words.filter { it.length >= 3 && it !in stop }
        return if (strong.isNotEmpty()) strong else words.filter { it.length >= 2 }
    }

    private fun fetchHtml(urlStr: String): String? {
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6500
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                )
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun unescapeJsonUrl(raw: String): String {
        return raw
            .replace("\\u003d", "=")
            .replace("\\u0026", "&")
            .replace("\\/", "/")
    }

    private fun extractOipKey(url: String): String {
        val idx = url.indexOf("OIP.")
        return if (idx >= 0) url.substring(idx).substringBefore("&") else ""
    }

    fun loadThumbBitmap(ctx: Context, item: SketchResult, onLoaded: (Bitmap?) -> Unit) {
        if (item.localSample != null) {
            val bmp = Samples.loadThumb(ctx, item.localSample, 420)
            onLoaded(bmp)
            return
        }
        val key = item.thumbUrl.ifEmpty { item.fullUrl }
        if (key.isEmpty()) {
            onLoaded(null)
            return
        }
        thumbBitmapCache.get(key)?.let {
            onLoaded(it)
            return
        }
        imgExecutor.execute {
            val raw = fetchBitmapFromUrl(item.thumbUrl, maxDim = 560) ?: fetchBitmapFromUrl(item.fullUrl, maxDim = 560)
            val bmp = raw?.let { ImageProcessor.toCleanBwSketch(it) }
            if (bmp != null) {
                thumbBitmapCache.put(key, bmp)
            }
            mainHandler.post { onLoaded(bmp) }
        }
    }

    fun loadFullForEditor(ctx: Context, item: SketchResult, onLoaded: (Bitmap?) -> Unit) {
        if (item.localSample != null) {
            val bmp = Samples.loadFull(ctx, item.localSample)
            onLoaded(bmp)
            return
        }
        imgExecutor.execute {
            val hdThumb = item.thumbUrl.replace("&w=480", "&w=960")
            val raw = fetchBitmapFromUrl(item.fullUrl, maxDim = 1440)
                ?: fetchBitmapFromUrl(hdThumb, maxDim = 1440)
                ?: fetchBitmapFromUrl(item.thumbUrl, maxDim = 1440)
            val clean = raw?.let {
                val argb = if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false)
                ImageProcessor.toCleanBwSketch(argb)
            }
            mainHandler.post { onLoaded(clean) }
        }
    }

    private fun fetchBitmapFromUrl(urlStr: String, maxDim: Int = 1280): Bitmap? {
        if (!urlStr.startsWith("http")) return null
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 8000
                instanceFollowRedirects = true
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
