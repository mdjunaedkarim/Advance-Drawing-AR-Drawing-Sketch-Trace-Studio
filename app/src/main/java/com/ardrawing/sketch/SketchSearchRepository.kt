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
    private val thumbBitmapCache = LruCache<String, Bitmap>(200)

    private val iuscRegex = Regex("""class="iusc"[^>]*?m="([^"]+)"""")
    private val murlRegex = Regex(""""murl"\s*:\s*"(https?://[^"]+)"""")
    private val turlRegex = Regex(""""turl"\s*:\s*"(https?://[^"]+)"""")
    private val titleRegex = Regex(""""t"\s*:\s*"([^"]*)"""")
    private val purlRegex = Regex(""""purl"\s*:\s*"(https?://[^"]+)"""")

    private val gTbnUrlRegex = Regex("""https://encrypted-tbn\d*\.gstatic\.com/images\?q=tbn:[^"'\s\\<>]+""")
    private val gImgPairRegex = Regex("""\["(https?://[^"\\]+)",\s*(\d{3,4}),\s*(\d{3,4})\]""")
    private val gImgTagRegex = Regex("""<img[^>]+src="(https://encrypted-tbn\d*\.gstatic\.com/images\?q=tbn:[^"]+)"[^>]*>""")
    private val altAttrRegex = Regex("""alt="([^"]*)"""")

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
        "re:zero", "re zero", "iron man", "tony stark", "avengers", "playing card"
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
            val verified = ArrayList<Candidate>(5)
            val seenKeys = HashSet<String>()

            fun absorbPool(rawCandidates: List<Candidate>) {
                if (verified.size >= 5) return
                val fresh = mergeAndRankCandidates(rawCandidates).filter { c ->
                    val k = candidateKey(c)
                    k !in seenKeys
                }
                val passed = validateAndPreload(fresh, targetCount = 5 - verified.size)
                passed.forEach {
                    seenKeys.add(candidateKey(it))
                    verified.add(it)
                }
            }

            // Tier 1: Pure Google + Bing black-and-white line art / coloring drawings
            val fG1 = searchExecutor.submit<List<Candidate>> {
                fetchGoogleBasicBwCandidates("$subject anime black and white line art drawing", cleanChar, cleanSeries)
            }
            val fG2 = searchExecutor.submit<List<Candidate>> {
                fetchGoogleFullBwCandidates("$subject anime coloring page black and white", cleanChar, cleanSeries)
            }
            val fB1 = searchExecutor.submit<List<Candidate>> {
                fetchBingBwCandidates("$subject anime line art drawing", cleanChar, cleanSeries, lineArtFilter = true)
            }
            val fB2 = searchExecutor.submit<List<Candidate>> {
                fetchBingBwCandidates("$subject anime black and white sketch drawing", cleanChar, cleanSeries, lineArtFilter = false)
            }

            val tier1 = ArrayList<Candidate>()
            try { tier1.addAll(fG1.get()) } catch (_: Throwable) {}
            try { tier1.addAll(fG2.get()) } catch (_: Throwable) {}
            try { tier1.addAll(fB1.get()) } catch (_: Throwable) {}
            try { tier1.addAll(fB2.get()) } catch (_: Throwable) {}
            absorbPool(tier1)

            // Tier 2: Pencil sketch / outline drawings if fewer than 5 verified
            if (verified.size < 5) {
                val fG3 = searchExecutor.submit<List<Candidate>> {
                    fetchGoogleBasicBwCandidates("$subject anime sketch drawing", cleanChar, cleanSeries)
                }
                val fB3 = searchExecutor.submit<List<Candidate>> {
                    fetchBingBwCandidates("$subject coloring page outline", cleanChar, cleanSeries, lineArtFilter = false)
                }
                val tier2 = ArrayList<Candidate>()
                try { tier2.addAll(fG3.get()) } catch (_: Throwable) {}
                try { tier2.addAll(fB3.get()) } catch (_: Throwable) {}
                absorbPool(tier2)
            }

            // Tier 3: Broader anime drawing / manga panel fallback if still under 5
            if (verified.size < 5) {
                val fG4 = searchExecutor.submit<List<Candidate>> {
                    fetchGoogleBasicBwCandidates("$subject anime drawing", cleanChar, cleanSeries)
                }
                val fB4 = searchExecutor.submit<List<Candidate>> {
                    fetchBingBwCandidates("$subject manga sketch", cleanChar, cleanSeries, lineArtFilter = false)
                }
                val tier3 = ArrayList<Candidate>()
                try { tier3.addAll(fG4.get()) } catch (_: Throwable) {}
                try { tier3.addAll(fB4.get()) } catch (_: Throwable) {}
                absorbPool(tier3)
            }

            // Tier 4: Character-locked pencil / fanart line fallback for rare side characters to hit 5
            if (verified.size < 5) {
                val fG5 = searchExecutor.submit<List<Candidate>> {
                    fetchGoogleBasicBwCandidates("$subject pencil drawing", cleanChar, cleanSeries)
                }
                val fG6 = searchExecutor.submit<List<Candidate>> {
                    fetchGoogleFullBwCandidates("$subject anime sketch", cleanChar, cleanSeries)
                }
                val tier4 = ArrayList<Candidate>()
                try { tier4.addAll(fG5.get()) } catch (_: Throwable) {}
                try { tier4.addAll(fG6.get()) } catch (_: Throwable) {}
                absorbPool(tier4)
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

    private fun candidateKey(c: Candidate): String {
        return extractOipKey(c.thumbUrl).ifEmpty { c.fullUrl.substringBefore("?") }
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
            val batch = pool.subList(idx, minOf(idx + 5, pool.size))
            val futures = ArrayList<Pair<Candidate, Future<Bitmap?>>>(batch.size)
            for (cand in batch) {
                val f = imgExecutor.submit<Bitmap?> {
                    val rawBmp = fetchBitmapFromUrl(cand.thumbUrl, maxDim = 640)
                        ?: fetchBitmapFromUrl(cand.fullUrl, maxDim = 640)
                        ?: return@submit null
                    if (rawBmp.width < 95 || rawBmp.height < 95) return@submit null
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
            idx += 5
        }
        return verified
    }

    /**
     * Queries Google Images Basic HTML (gbv=1) which returns static <img> tags + context
     * without requiring JavaScript execution.
     */
    private fun fetchGoogleBasicBwCandidates(query: String, character: String, series: String): List<Candidate> {
        return try {
            val url = "https://www.google.com/search?tbm=isch&gbv=1&q=${Uri.encode(query)}&tbs=ic:gray"
            val html = fetchHtml(url, mobileUa = false) ?: return emptyList()
            val out = ArrayList<Candidate>()

            for (m in gImgTagRegex.findAll(html)) {
                val imgTag = m.value
                val tbn = Html.fromHtml(m.groupValues[1], Html.FROM_HTML_MODE_LEGACY).toString()
                val alt = altAttrRegex.find(imgTag)?.groupValues?.get(1) ?: ""
                val winStart = (m.range.first - 120).coerceAtLeast(0)
                val winEnd = (m.range.last + 360).coerceAtMost(html.length)
                val contextText = Html.fromHtml(
                    "$alt " + html.substring(winStart, winEnd).replace(Regex("<[^>]+>"), " "),
                    Html.FROM_HTML_MODE_LEGACY
                ).toString()

                val score = scoreCandidate(tbn, contextText, "", character, series)
                if (score >= 0) {
                    out.add(Candidate(tbn, tbn, contextText, "", score + 14))
                }
                if (out.size >= 18) break
            }
            out
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun fetchGoogleFullBwCandidates(query: String, character: String, series: String): List<Candidate> {
        return try {
            val url = "https://www.google.com/search?tbm=isch&q=${Uri.encode(query)}&tbs=ic:gray,itp:lineart"
            val html = fetchHtml(url, mobileUa = false) ?: return emptyList()
            val out = ArrayList<Candidate>()

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

            val tbnMatches = gTbnUrlRegex.findAll(html).toList()
            for ((uUrl, pos) in fullUrls) {
                val nearTbn = tbnMatches.minByOrNull { kotlin.math.abs(it.range.first - pos) }
                    ?.takeIf { kotlin.math.abs(it.range.first - pos) < 600 }
                    ?.value ?: uUrl
                val windowStart = (pos - 350).coerceAtLeast(0)
                val windowEnd = (pos + 550).coerceAtMost(html.length)
                val snippet = html.substring(windowStart, windowEnd)
                val score = scoreCandidate(uUrl, snippet, "", character, series)
                if (score >= 0) {
                    out.add(Candidate(uUrl, nearTbn, snippet, "", score + 12))
                }
                if (out.size >= 14) break
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
            val html = fetchHtml(url, mobileUa = false) ?: return emptyList()
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
                    .substringBefore("&h=") + "&w=540&rs=1&pid=ImgDetMain"
                out.add(Candidate(mUrl, cleanThumb, title, pUrl, score))
                if (out.size >= 24) break
            }
            out
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Strict same-character relevance scoring:
     * Returns -1 to reject GTA V, unrelated games, other anime franchises, or wrong characters.
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

        // Reject explicitly colored results (allow "coloring"/"colouring" outline sheets)
        val noColoring = combined.replace("coloring", "").replace("colouring", "")
        if (Regex("""\b(full\s*color|colored|coloured|watercolor)\b""").containsMatchIn(noColoring)) {
            return -1
        }

        // Cross-franchise negative guard: reject results clearly belonging to a DIFFERENT anime series
        val seriesKey = series.lowercase().trim()
        if (seriesKey.isNotEmpty()) {
            val ownAliases = seriesAliases[seriesKey] ?: listOf(seriesKey)
            val mentionsOwnSeries = ownAliases.any { combined.contains(it) }
            if (!mentionsOwnSeries) {
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
        }

        val charTokens = extractNameTokens(character)
        val matchedCharTokens = charTokens.filter { tok ->
            Regex("""\b${Regex.escape(tok)}\b""").containsMatchIn(combined) ||
                (tok.length >= 4 && combined.contains(tok))
        }

        val hasMetadata = title.isNotBlank() || pageUrl.isNotBlank()
        if (hasMetadata && charTokens.isNotEmpty() && matchedCharTokens.isEmpty()) {
            return -1
        }

        val ownSeriesAliases = seriesAliases[seriesKey] ?: extractNameTokens(series)
        val hasSeriesMatch = ownSeriesAliases.any { combined.contains(it) }

        // If all matched character tokens are common/ambiguous words (King, Scar, Rem, Tank, Power, Franklin, etc.),
        // strictly require the anime series name to appear in title/URL so wrong characters never slip in.
        val allMatchedAmbiguous = matchedCharTokens.isNotEmpty() && matchedCharTokens.all { it in ambiguousCharWords }
        if (allMatchedAmbiguous && seriesKey.isNotEmpty() && !hasSeriesMatch) {
            return -1
        }

        // Specific Hinata guard: Haikyuu's Shoyo Hinata must not pull Naruto's Hinata Hyuga
        if (seriesKey == "haikyuu" && (combined.contains("hyuga") || combined.contains("byakugan"))) {
            return -1
        }
        if (seriesKey == "naruto" && (combined.contains("shoyo") || combined.contains("volleyball") || combined.contains("karasuno"))) {
            return -1
        }

        var score = 0
        score += matchedCharTokens.size * 18
        if (hasSeriesMatch) score += 24
        if (combined.contains("line art") || combined.contains("lineart") || combined.contains("coloring")) score += 14
        if (combined.contains("sketch") || combined.contains("outline")) score += 10
        if (combined.contains("black and white") || combined.contains("b&w") || combined.contains("drawing")) score += 8
        return score
    }

    private fun extractNameTokens(raw: String): List<String> {
        val stop = setOf("the", "and", "of", "for", "with", "devil", "face", "smug", "symbol", "statue", "man")
        val cleaned = raw.lowercase().replace(Regex("""[^a-z0-9\s]"""), " ")
        val words = cleaned.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val strong = words.filter { it.length >= 3 && it !in stop }
        return if (strong.isNotEmpty()) strong else words.filter { it.length >= 2 }
    }

    private fun fetchHtml(urlStr: String, mobileUa: Boolean): String? {
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6500
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    if (mobileUa) {
                        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                    } else {
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                    }
                )
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                setRequestProperty("Cookie", "CONSENT=YES+cb.20210328-17-p0.en+FX+667")
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
            val raw = fetchBitmapFromUrl(item.thumbUrl, maxDim = 640) ?: fetchBitmapFromUrl(item.fullUrl, maxDim = 640)
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
            val hdThumb = item.thumbUrl.replace("&w=540", "&w=960").replace("&w=480", "&w=960")
            val raw = fetchBitmapFromUrl(item.fullUrl, maxDim = 1440)
                ?: fetchBitmapFromUrl(hdThumb, maxDim = 1440)
                ?: fetchBitmapFromUrl(item.thumbUrl, maxDim = 1440)
            val clean = raw?.let {
                val argb = if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false)
                ImageProcessor.toCleanBwSketch(argb)
            } ?: thumbBitmapCache.get(item.thumbUrl.ifEmpty { item.fullUrl })
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
