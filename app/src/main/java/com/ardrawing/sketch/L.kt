package com.ardrawing.sketch

object L {
    var lang = "en"

    // এখানে আপনার ওয়েবসাইটের Privacy Policy লিংকটি বসবে (লিংক দিলে অটো ওয়েবসাইটে নিয়ে যাবে)
    const val PRIVACY_URL = "https://sites.google.com/view/advance-drawing-studio-/home"

    data class LangInfo(
        val code: String,
        val badge: String,
        val label: String,
        val sub: String
    )

    // বিশ্বের জনবহুল ও সর্বাধিক ব্যবহৃত ২৮টি দেশ ও ভাষা
    val languages = listOf(
        LangInfo("en", "EN", "English", "United States / UK / Canada"),
        LangInfo("bn", "BN", "বাংলা", "Bangladesh / India"),
        LangInfo("hi", "HI", "हिन्दी", "India"),
        LangInfo("zh", "ZH", "中文 (简体)", "China"),
        LangInfo("es", "ES", "Español", "Spain / Mexico / LatAm"),
        LangInfo("ar", "AR", "العربية", "Saudi Arabia / Egypt / UAE"),
        LangInfo("pt", "PT", "Português", "Brazil / Portugal"),
        LangInfo("id", "ID", "Bahasa Indonesia", "Indonesia"),
        LangInfo("ur", "UR", "اردو", "Pakistan"),
        LangInfo("ru", "RU", "Русский", "Russia"),
        LangInfo("ja", "JA", "日本語", "Japan"),
        LangInfo("fr", "FR", "Français", "France / Canada"),
        LangInfo("de", "DE", "Deutsch", "Germany"),
        LangInfo("tr", "TR", "Türkçe", "Turkey"),
        LangInfo("ko", "KO", "한국어", "South Korea"),
        LangInfo("vi", "VI", "Tiếng Việt", "Vietnam"),
        LangInfo("th", "TH", "ไทย", "Thailand"),
        LangInfo("it", "IT", "Italiano", "Italy"),
        LangInfo("pl", "PL", "Polski", "Poland"),
        LangInfo("uk", "UK", "Українська", "Ukraine"),
        LangInfo("fa", "FA", "فارسی", "Iran"),
        LangInfo("ms", "MS", "Bahasa Melayu", "Malaysia"),
        LangInfo("tl", "TL", "Filipino / Tagalog", "Philippines"),
        LangInfo("sw", "SW", "Kiswahili", "Kenya / Tanzania"),
        LangInfo("ha", "HA", "Hausa", "Nigeria"),
        LangInfo("nl", "NL", "Nederlands", "Netherlands"),
        LangInfo("ta", "TA", "தமிழ்", "India / Sri Lanka"),
        LangInfo("zh_hk", "TW", "中文 (繁體)", "Taiwan / Hong Kong")
    )

    private val all: Map<String, Map<String, String>> = mapOf(
        "en" to LTranslationsA.en,
        "bn" to LTranslationsA.bn,
        "hi" to LTranslationsA.hi,
        "zh" to LTranslationsA.zh,
        "es" to LTranslationsA.es,
        "ar" to LTranslationsA.ar,
        "pt" to LTranslationsA.pt,
        "id" to LTranslationsA.id,
        "ur" to LTranslationsA.ur,
        "ru" to LTranslationsA.ru,
        "ja" to LTranslationsA.ja,
        "fr" to LTranslationsA.fr,
        "de" to LTranslationsA.de,
        "tr" to LTranslationsA.tr,
        "ko" to LTranslationsB.ko,
        "vi" to LTranslationsB.vi,
        "th" to LTranslationsB.th,
        "it" to LTranslationsB.it,
        "pl" to LTranslationsB.pl,
        "uk" to LTranslationsB.uk,
        "fa" to LTranslationsB.fa,
        "ms" to LTranslationsB.ms,
        "tl" to LTranslationsB.tl,
        "sw" to LTranslationsB.sw,
        "ha" to LTranslationsB.ha,
        "nl" to LTranslationsB.nl,
        "ta" to LTranslationsB.ta,
        "zh_hk" to LTranslationsB.zhHk
    )

    private val extraEn = mapOf(
        "more_photos" to "More Photos",
        "searching_sketches" to "Auto-searching 5 B&W sketches...",
        "bw_sketch_badge" to "B&W Sketch Auto-Search • Top 5",
        "open_chrome" to "Chrome",
        "retry_search" to "Retry Search",
        "no_sketches_found" to "Could not load sketches right now. Tap More Photos to browse online.",
        "mood_normal" to "Normal",
        "mood_cute" to "Cute",
        "mood_action" to "Action",
        "mood_aggressive" to "Aggressive",
        "mood_lineart" to "Line Art",
        "cat_fruit" to "Fruits",
        "random_mix_title" to "Featured Random Mix",
        "all_anime_title" to "All 20 Anime Series",
        "discover_feed_title" to "Random Sketches Feed",
        "shuffle_btn" to "Shuffle",
        "browse_more_cat" to "More on Microsoft Bing",
        "explore_chars" to "Characters",
        "choose_draw_system" to "Choose Drawing System",
        "mode_camera" to "Camera AR Drawing",
        "mode_camera_sub" to "Look through camera & draw",
        "mode_lightbox" to "Screen Lightbox",
        "mode_lightbox_sub" to "Place paper on phone display",
        "screen_trace_short" to "Screen Draw",
        "lightbox_hint" to "Adjust sketch, then tap Lock before placing paper",
        "lightbox_locked" to "Screen Locked • Only Unlock button works",
        "brightness_overdrive" to "2× BOOST",
        "bing_pill" to "Bing",
        "hide_btn" to "Hide",
        "show_btn" to "Show"
    )

    private val extraBn = mapOf(
        "more_photos" to "More Photos",
        "searching_sketches" to "সাদা-কালো ৫টি স্কেচ খোঁজা হচ্ছে...",
        "bw_sketch_badge" to "অটো সার্চ • সেরা ৫টি সাদা-কালো স্কেচ",
        "open_chrome" to "Chrome",
        "retry_search" to "আবার খুঁজুন",
        "no_sketches_found" to "এই মুহূর্তে ছবি লোড করা যায়নি। নিচের More Photos-এ ট্যাপ করুন।",
        "mood_normal" to "নরমাল",
        "mood_cute" to "কিউট",
        "mood_action" to "অ্যাকশন",
        "mood_aggressive" to "এগ্রেসিভ",
        "mood_lineart" to "সাদা-কালো স্কেচ",
        "cat_fruit" to "ফল",
        "random_mix_title" to "র্যান্ডম স্কেচ মিক্স",
        "all_anime_title" to "সব ২০টি অ্যানিমে সিরিজ",
        "discover_feed_title" to "সব ক্যাটাগরির র্যান্ডম ছবি",
        "shuffle_btn" to "র্যান্ডম করুন",
        "browse_more_cat" to "Microsoft Bing-এ আরও ছবি দেখুন",
        "explore_chars" to "চরিত্র",
        "choose_draw_system" to "ড্রয়িং সিস্টেম বেছে নিন",
        "mode_camera" to "ক্যামেরা ড্রয়িং",
        "mode_camera_sub" to "মোবাইলের ক্যামেরা দেখে কাগজে আঁকুন",
        "mode_lightbox" to "ডিসপ্লে লাইটবক্স",
        "mode_lightbox_sub" to "ডিসপ্লের উপরে কাগজ রেখে আঁকুন",
        "screen_trace_short" to "ডিসপ্লে আঁকা",
        "lightbox_hint" to "ছবি ঠিক করে কাগজ রাখার আগে লক বাটনে ক্লিক করুন",
        "lightbox_locked" to "স্ক্রিন লক • লক বাটন ছাড়া কোথাও টাচ কাজ করবে না",
        "brightness_overdrive" to "২× ডাবল আলো",
        "bing_pill" to "Bing",
        "hide_btn" to "হাইড",
        "show_btn" to "শো"
    )

    fun t(key: String): String {
        val raw = all[lang]?.get(key)
            ?: (if (lang == "bn") extraBn[key] else null)
            ?: extraEn[key]
            ?: LTranslationsA.en[key]
            ?: key
        return raw.replace("🎉", "").replace("✓", "").replace("  ", " ").trim()
    }
}
