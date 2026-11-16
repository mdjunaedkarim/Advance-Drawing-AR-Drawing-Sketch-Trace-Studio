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

object SketchSearchRepository {

    data class SketchResult(
        val id: String,
        val title: String,
        val mood: String,
        val fullUrl: String,
        val thumbUrl: String,
        val localSample: Samples.Item? = null
    )

    private val searchExecutor = Executors.newFixedThreadPool(3)
    private val imgExecutor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val queryCache = LruCache<String, List<SketchResult>>(80)
    private val thumbBitmapCache = LruCache<String, Bitmap>(160)

    private val iuscRegex = Regex("""class="iusc"[^>]*?m="([^"]+)"""")
    private val murlRegex = Regex(""""murl"\s*:\s*"(https?://[^"]+)"""")
    private val turlRegex = Regex(""""turl"\s*:\s*"(https?://[^"]+)"""")
    private val oipRegex = Regex("""https://ts[0-9e]+\.mm\.bing\.net/th[^"&\\?]+\?id=OIP\.[^"&\\]+""")

    private val blockedHosts = listOf(
        "shutterstock", "alamy", "istockphoto", "dreamstime", "123rf",
        "depositphotos", "gettyimages", "vecteezy", "freepik", "vectorstock",
        "favicon", "logo", "sprite", "doubleclick"
    )

    fun pickDiverseLocalFive(matches: List<Samples.Item>): List<SketchResult> {
        if (matches.isEmpty()) return emptyList()
        val picked = if (matches.size <= 5) {
            matches
        } else {
            // Select up to 5 varied poses spanning Cute -> Normal -> Action -> Epic -> Aggressive
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

        // Check curated local sketches first for Naruto Uzumaki & Sasuke Uchiha
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
            val subject = if (cleanSeries.isNotEmpty() && !cleanChar.lowercase().contains(cleanSeries.lowercase())) {
                "\"$cleanChar\" $cleanSeries"
            } else {
                "\"$cleanChar\""
            }

            val qNormal = "$subject anime black and white sketch line art drawing"
            val qMood = "$subject anime cute aggressive black and white pencil sketch"

            val normalFuture = searchExecutor.submit<List<Pair<String, String>>> { fetchBingBwPairs(qNormal) }
            val moodFuture = searchExecutor.submit<List<Pair<String, String>>> { fetchBingBwPairs(qMood) }

            val listA = try { normalFuture.get() } catch (_: Throwable) { emptyList() }
            val listB = try { moodFuture.get() } catch (_: Throwable) { emptyList() }

            val seenIds = HashSet<String>()
            val merged = ArrayList<Pair<String, String>>(5)

            fun addUnique(pair: Pair<String, String>) {
                if (merged.size >= 5) return
                val idKey = extractOipKey(pair.second).ifEmpty { pair.first }
                if (seenIds.add(idKey)) merged.add(pair)
            }

            // Balance 3 classic/line-art + 2 cute/aggressive B&W sketches
            listA.take(8).forEach { if (merged.size < 3) addUnique(it) }
            listB.take(10).forEach { if (merged.size < 5) addUnique(it) }
            listA.forEach { if (merged.size < 5) addUnique(it) }
            listB.forEach { if (merged.size < 5) addUnique(it) }

            // Fallback broader query if fewer than 5 found
            if (merged.size < 5) {
                val fallback = fetchBingBwPairs("$cleanChar $cleanSeries black and white drawing")
                fallback.forEach { if (merged.size < 5) addUnique(it) }
            }

            val moodLabels = listOf(
                L.t("mood_normal"),
                L.t("mood_lineart"),
                L.t("mood_action"),
                L.t("mood_cute"),
                L.t("mood_aggressive")
            )
            val results = merged.take(5).mapIndexed { idx, (fullUrl, thumbUrl) ->
                SketchResult(
                    id = "web_${cacheKey}_$idx",
                    title = cleanChar.replaceFirstChar { it.uppercase() },
                    mood = moodLabels[idx % moodLabels.size],
                    fullUrl = fullUrl,
                    thumbUrl = thumbUrl,
                    localSample = null
                )
            }

            if (results.isNotEmpty()) {
                queryCache.put(cacheKey, results)
            }
            mainHandler.post { onComplete(results) }
        }
    }

    private fun fetchBingBwPairs(query: String): List<Pair<String, String>> {
        return try {
            val url = "https://www.bing.com/images/search?q=${Uri.encode(query)}&qft=+filterui:color2-bw&first=1&count=35"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6500
                readTimeout = 8000
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                )
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            }
            val html = conn.inputStream.bufferedReader().use { it.readText() }
            val out = ArrayList<Pair<String, String>>()

            for (match in iuscRegex.findAll(html)) {
                val decoded = Html.fromHtml(match.groupValues[1], Html.FROM_HTML_MODE_LEGACY).toString()
                val mUrl = murlRegex.find(decoded)?.groupValues?.get(1) ?: continue
                val rawTurl = turlRegex.find(decoded)?.groupValues?.get(1)
                    ?: oipRegex.find(html, match.range.first)?.value
                    ?: continue

                val lowM = mUrl.lowercase()
                if (blockedHosts.any { lowM.contains(it) }) continue
                if (lowM.endsWith(".svg") || lowM.endsWith(".gif")) continue

                val cleanThumb = rawTurl
                    .substringBefore("&w=")
                    .substringBefore("&h=") + "&w=480&rs=1&pid=ImgDetMain"
                out.add(mUrl to cleanThumb)
                if (out.size >= 24) break
            }
            out
        } catch (_: Throwable) {
            emptyList()
        }
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
        val key = item.thumbUrl
        if (key.isEmpty()) {
            onLoaded(null)
            return
        }
        thumbBitmapCache.get(key)?.let {
            onLoaded(it)
            return
        }
        imgExecutor.execute {
            val bmp = fetchBitmapFromUrl(item.thumbUrl) ?: fetchBitmapFromUrl(item.fullUrl)
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
            val raw = fetchBitmapFromUrl(item.fullUrl)
                ?: fetchBitmapFromUrl(hdThumb)
                ?: fetchBitmapFromUrl(item.thumbUrl)
            val clean = raw?.let {
                if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false)
            }
            mainHandler.post { onLoaded(clean) }
        }
    }

    private fun fetchBitmapFromUrl(urlStr: String): Bitmap? {
        if (!urlStr.startsWith("http")) return null
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                )
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) return null
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
            var sample = 1
            while (opts.outWidth / sample > 1280 || opts.outHeight / sample > 1280) sample *= 2
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
