package com.ardrawing.sketch

object L {
    var lang = "en"

    // এখানে আপনার ওয়েবসাইটের Privacy Policy লিংকটি বসবে (লিংক দিলে অটো ওয়েবসাইটে নিয়ে যাবে)
    const val PRIVACY_URL = "https://sites.google.com/view/advance-drawing-studio-/home"

    data class LangInfo(
        val code: String,
        val flag: String,
        val label: String,
        val sub: String
    )

    // বিশ্বের জনবহুল ও সর্বাধিক ব্যবহৃত ২৮টি দেশ ও ভাষা
    val languages = listOf(
        LangInfo("en", "🇺🇸", "English", "United States / UK / Canada"),
        LangInfo("bn", "🇧🇩", "বাংলা", "Bangladesh / India"),
        LangInfo("hi", "🇮🇳", "हिन्दी", "India"),
        LangInfo("zh", "🇨🇳", "中文 (简体)", "China"),
        LangInfo("es", "🇪🇸", "Español", "Spain / Mexico / LatAm"),
        LangInfo("ar", "🇸🇦", "العربية", "Saudi Arabia / Egypt / UAE"),
        LangInfo("pt", "🇧🇷", "Português", "Brazil / Portugal"),
        LangInfo("id", "🇮🇩", "Bahasa Indonesia", "Indonesia"),
        LangInfo("ur", "🇵🇰", "اردو", "Pakistan"),
        LangInfo("ru", "🇷🇺", "Русский", "Russia"),
        LangInfo("ja", "🇯🇵", "日本語", "Japan"),
        LangInfo("fr", "🇫🇷", "Français", "France / Canada"),
        LangInfo("de", "🇩🇪", "Deutsch", "Germany"),
        LangInfo("tr", "🇹🇷", "Türkçe", "Turkey"),
        LangInfo("ko", "🇰🇷", "한국어", "South Korea"),
        LangInfo("vi", "🇻🇳", "Tiếng Việt", "Vietnam"),
        LangInfo("th", "🇹🇭", "ไทย", "Thailand"),
        LangInfo("it", "🇮🇹", "Italiano", "Italy"),
        LangInfo("pl", "🇵🇱", "Polski", "Poland"),
        LangInfo("uk", "🇺🇦", "Українська", "Ukraine"),
        LangInfo("fa", "🇮🇷", "فارسی", "Iran"),
        LangInfo("ms", "🇲🇾", "Bahasa Melayu", "Malaysia"),
        LangInfo("tl", "🇵🇭", "Filipino / Tagalog", "Philippines"),
        LangInfo("sw", "🇰🇪", "Kiswahili", "Kenya / Tanzania"),
        LangInfo("ha", "🇳🇬", "Hausa", "Nigeria"),
        LangInfo("nl", "🇳🇱", "Nederlands", "Netherlands"),
        LangInfo("ta", "🇮🇳", "தமிழ்", "India / Sri Lanka"),
        LangInfo("zh_hk", "🇹🇼", "中文 (繁體)", "Taiwan / Hong Kong")
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

    fun t(key: String): String = all[lang]?.get(key) ?: LTranslationsA.en[key] ?: key
}
