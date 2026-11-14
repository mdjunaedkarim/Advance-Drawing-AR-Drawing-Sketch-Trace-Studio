package com.ardrawing.sketch

object L {
    var lang = "en"

    // এখানে পরবর্তীতে আপনার আসল Privacy Policy ওয়েবসাইট লিংক বসাবেন
    const val PRIVACY_URL = "https://policies.google.com/privacy"

    data class LangInfo(val code: String, val label: String, val sub: String)

    val languages = listOf(
        LangInfo("en", "English", "United States / UK / Canada"),
        LangInfo("bn", "বাংলা", "Bangladesh / India"),
        LangInfo("hi", "हिन्दी", "India"),
        LangInfo("ur", "اردو", "Pakistan"),
        LangInfo("ar", "العربية", "Saudi Arabia"),
        LangInfo("ja", "日本語", "Japan"),
        LangInfo("zh", "中文 (简体)", "China"),
        LangInfo("zh_hk", "中文 (繁體)", "Hong Kong / Taiwan"),
        LangInfo("es", "Español", "Spain / LatAm"),
        LangInfo("fr", "Français", "France / Canada"),
        LangInfo("de", "Deutsch", "Germany"),
        LangInfo("pt", "Português", "Brazil / Portugal"),
        LangInfo("id", "Bahasa Indonesia", "Indonesia"),
        LangInfo("tr", "Türkçe", "Turkey"),
        LangInfo("ko", "한국어", "South Korea"),
        LangInfo("ru", "Русский", "Russia")
    )

    fun t(key: String): String = all[lang]?.get(key) ?: en[key] ?: key

    private val en = mapOf(
        "app" to "Advance Drawing Studio",
        "loading" to "Loading",
        "home" to "Home",
        "my_gallery" to "My Gallery",
        "store" to "Store",
        "settings" to "Settings",
        "home_sub" to "Place your phone over paper and trace any photo easily",
        "drawing_create" to "Drawing Create",
        "samples" to "Drawing Collection",
        "choose_source" to "Choose Photo Source",
        "take_photo" to "Take Photo",
        "upload_gallery" to "Upload Photo",
        "remove_bg" to "Remove Background",
        "restore_orig" to "Restore Original",
        "removing_bg" to "Removing background...",
        "opacity" to "Transparency",
        "brightness" to "Brightness",
        "flash" to "Flash",
        "lock" to "Lock",
        "unlock" to "Unlock",
        "next" to "Next",
        "back" to "Back",
        "cancel" to "Cancel",
        "save" to "Save",
        "need_image" to "Please select or capture a photo first",
        "hint_place" to "Drag, pinch to zoom & rotate. Tap Lock when ready",
        "hint_locked" to "Locked ✓ Keep drawing! Move closer to zoom in",
        "hint_lost" to "Move phone back over your paper",
        "congrats" to "Congratulations! 🎉",
        "good_luck" to "Good Luck with your next masterpiece!",
        "go_home" to "Go Home",
        "my_activity" to "My Activity",
        "import_photo" to "Import",
        "export_zip" to "Export ZIP",
        "import_title" to "Import to My Gallery",
        "choose_file" to "Choose File (Image / ZIP)",
        "paste_link" to "Import from Link (URL)",
        "enter_url" to "Paste Image or ZIP link (https://...)",
        "select_items" to "Select drawings first!",
        "export_ok" to "ZIP saved to Downloads!",
        "import_ok" to "Imported to My Gallery!",
        "import_fail" to "Could not import from link/file",
        "empty_gallery" to "No drawings yet. Create or import your first drawing!",
        "rename" to "Rename",
        "share" to "Share",
        "delete" to "Delete",
        "delete_confirm" to "Delete this drawing?",
        "delete_sub" to "It will be removed from your gallery.",
        "rename_hint" to "Enter drawing name",
        "store_title" to "Coin & Ad-Free Store",
        "coins_label" to "Coins",
        "your_balance" to "Your Coin Balance",
        "tab_coins" to "Coins",
        "tab_adfree" to "Ad-Free",
        "buy_coins_title" to "Top Up Coins",
        "buy_coins_sub" to "Use coins to unlock timed ad-free drawing",
        "timed_adfree_title" to "Ad-Free Time Passes (Coins)",
        "timed_adfree_sub" to "Redeem coins for uninterrupted drawing time",
        "adfree_pass_desc" to "No banner or popup ads",
        "pass_30m" to "30 Minutes",
        "pass_1h" to "1 Hour",
        "pass_2h" to "2 Hours",
        "pass_4h" to "4 Hours",
        "pass_8h" to "8 Hours",
        "pass_12h" to "12 Hours",
        "pass_24h" to "24 Hours",
        "pass_badge_starter" to "Quick",
        "pass_badge_popular" to "Popular",
        "pass_badge_best" to "Best Value",
        "perm_adfree_title" to "Permanent Ad-Free",
        "perm_adfree_desc" to "One-time $499.99 purchase removes all ads forever — no ads will ever appear again.",
        "unlock_forever" to "Unlock Forever",
        "adfree_status_perm" to "Permanent Ad-Free",
        "adfree_status_timed" to "Ad-Free Active",
        "adfree_status_none" to "Standard Mode",
        "not_enough_coins" to "You need {n} coins for this pass. Top up in the Coins tab!",
        "adfree_activated" to "{t} Ad-Free activated! Enjoy drawing.",
        "permanent_already_active" to "Permanent Ad-Free is already active!",
        "reward_coins_earned" to "🎉 +5 Bonus Coins earned!",
        "remove_ads" to "Permanent Ad-Free",
        "remove_ads_desc" to "Enjoy uninterrupted AR drawing forever",
        "ads_removed" to "Permanent Ad-Free Active ✓",
        "play_pending" to "Play Store billing activates after publishing on Google Play Console.",
        "language" to "Language",
        "privacy_policy" to "Privacy Policy",
        "about" to "About App",
        "share_friend" to "Share with Friends",
        "rate_us" to "Rate Us",
        "feedback" to "Send Feedback",
        "link_copied" to "App link copied!",
        "camera_denied" to "Camera permission is required",
        "camera_error" to "Could not start camera",
        "image_failed" to "Could not open image",
        "cat_all" to "All",
        "cat_anime" to "Anime",
        "cat_scenery" to "Scenery",
        "cat_vehicles" to "Vehicles",
        "cat_people" to "People",
        "cat_nature" to "Flowers & Fruits",
        "cat_animals" to "Birds & Animals",
        "share_text" to "Draw anything onto paper with Advance Drawing Studio! https://play.google.com/store/apps/details?id=com.advancedrawing.studio"
    )

    private val bn = mapOf(
        "loading" to "লোডিং", "home" to "হোম", "my_gallery" to "মাই গ্যালারি",
        "store" to "স্টোর", "settings" to "সেটিংস", "take_photo" to "Take Photo",
        "upload_gallery" to "Upload Photo", "remove_bg" to "ব্যাকগ্রাউন্ড রিমুভ",
        "restore_orig" to "আসল ছবি ফিরান", "opacity" to "স্বচ্ছতা", "brightness" to "আলো",
        "next" to "পরবর্তী", "back" to "ফিরুন", "cancel" to "বাতিল", "save" to "সেভ",
        "congrats" to "অভিনন্দন! 🎉", "good_luck" to "শুভকামনা! আপনার আঁকা সুন্দর হয়েছে",
        "go_home" to "হোমে যান", "my_activity" to "মাই অ্যাক্টিভিটি",
        "import_photo" to "ইনপুট / Import", "export_zip" to "আউটপুট ZIP",
        "delete" to "ডিলিট", "delete_confirm" to "এই ছবিটি কি ডিলিট করবেন?",
        "delete_sub" to "ডিলিট করলে গ্যালারি থেকে এটি মুছে যাবে।",
        "store_title" to "কয়েন ও অ্যাড-ফ্রি স্টোর",
        "coins_label" to "কয়েন",
        "your_balance" to "আপনার কয়েন ব্যালেন্স",
        "tab_coins" to "কয়েন",
        "tab_adfree" to "অ্যাড-ফ্রি",
        "buy_coins_title" to "কয়েন কিনুন",
        "buy_coins_sub" to "কয়েন দিয়ে নির্দিষ্ট সময়ের জন্য অ্যাড বন্ধ রাখুন",
        "timed_adfree_title" to "কয়েনে অ্যাড-ফ্রি সময় নিন",
        "timed_adfree_sub" to "ঘণ্টা ও মিনিট অনুযায়ী কয়েন দিয়ে অ্যাড বন্ধ করুন",
        "adfree_pass_desc" to "কোনো ব্যানার বা পপআপ অ্যাড থাকবে না",
        "pass_30m" to "৩০ মিনিট",
        "pass_1h" to "১ ঘণ্টা",
        "pass_2h" to "২ ঘণ্টা",
        "pass_4h" to "৪ ঘণ্টা",
        "pass_8h" to "৮ ঘণ্টা",
        "pass_12h" to "১২ ঘণ্টা",
        "pass_24h" to "২৪ ঘণ্টা",
        "pass_badge_starter" to "সহজ",
        "pass_badge_popular" to "জনপ্রিয়",
        "pass_badge_best" to "সেরা",
        "perm_adfree_title" to "পার্মানেন্ট অ্যাড-ফ্রি",
        "perm_adfree_desc" to "$499.99 দিয়ে একবার পারচেস করলে অ্যাড স্থায়ীভাবে বন্ধ থাকবে — আর কখনো কোনো অ্যাড আসবে না।",
        "unlock_forever" to "চিরতরে আনলক করুন",
        "adfree_status_perm" to "পার্মানেন্ট অ্যাড-ফ্রি চালু",
        "adfree_status_timed" to "অ্যাড-ফ্রি চালু আছে",
        "adfree_status_none" to "সাধারণ মোড",
        "not_enough_coins" to "এটির জন্য {n} কয়েন প্রয়োজন। কয়েন ট্যাব থেকে কয়েন নিন!",
        "adfree_activated" to "{t} এর জন্য অ্যাড-ফ্রি চালু হয়েছে!",
        "permanent_already_active" to "পার্মানেন্ট অ্যাড-ফ্রি ইতিমধ্যেই চালু আছে!",
        "reward_coins_earned" to "🎉 ড্রয়িং শেষ করায় +৫ কয়েন বোনাস পেলেন!",
        "ads_removed" to "পার্মানেন্ট অ্যাড-ফ্রি চালু ✓",
        "language" to "ভাষা", "privacy_policy" to "প্রাইভেসি পলিসি", "about" to "অ্যাপ সম্পর্কে",
        "share_friend" to "শেয়ার করুন", "rate_us" to "রেটিং দিন", "feedback" to "ফিডব্যাক"
    )

    private val hi = mapOf("loading" to "लोडिंग", "home" to "होम", "my_gallery" to "गैलरी", "store" to "स्टोर", "settings" to "सेटिंग्स", "congrats" to "बधाई हो! 🎉", "language" to "भाषा")
    private val ur = mapOf("loading" to "لوڈنگ", "home" to "ہوم", "my_gallery" to "گیلری", "store" to "اسٹور", "settings" to "سیٹنگز", "language" to "زبان")
    private val ar = mapOf("loading" to "جاري التحميل", "home" to "الرئيسية", "my_gallery" to "معرضي", "store" to "المتجر", "settings" to "الإعدادات", "language" to "اللغة")
    private val ja = mapOf("loading" to "読み込み中", "home" to "ホーム", "my_gallery" to "ギャラリー", "store" to "ストア", "settings" to "設定", "language" to "言語")
    private val zh = mapOf("loading" to "加载中", "home" to "首页", "my_gallery" to "我的画廊", "store" to "商店", "settings" to "设置", "language" to "语言")
    private val zhHk = mapOf("loading" to "載入中", "home" to "首頁", "my_gallery" to "我的畫廊", "store" to "商店", "settings" to "設定", "language" to "語言")
    private val es = mapOf("loading" to "Cargando", "home" to "Inicio", "my_gallery" to "Galería", "store" to "Tienda", "settings" to "Ajustes", "language" to "Idioma")
    private val fr = mapOf("loading" to "Chargement", "home" to "Accueil", "my_gallery" to "Galerie", "store" to "Boutique", "settings" to "Paramètres", "language" to "Langue")
    private val de = mapOf("loading" to "Laden", "home" to "Start", "my_gallery" to "Galerie", "store" to "Shop", "settings" to "Einstellungen", "language" to "Sprache")
    private val pt = mapOf("loading" to "Carregando", "home" to "Início", "my_gallery" to "Galeria", "store" to "Loja", "settings" to "Configurações", "language" to "Idioma")
    private val id = mapOf("loading" to "Memuat", "home" to "Beranda", "my_gallery" to "Galeri", "store" to "Toko", "settings" to "Pengaturan", "language" to "Bahasa")
    private val tr = mapOf("loading" to "Yükleniyor", "home" to "Ana Sayfa", "my_gallery" to "Galerim", "store" to "Mağaza", "settings" to "Ayarlar", "language" to "Dil")
    private val ko = mapOf("loading" to "로딩 중", "home" to "홈", "my_gallery" to "갤러리", "store" to "스토어", "settings" to "설정", "language" to "언어")
    private val ru = mapOf("loading" to "Загрузка", "home" to "Главная", "my_gallery" to "Галерея", "store" to "Магазин", "settings" to "Настройки", "language" to "Язык")

    private val all = mapOf(
        "en" to en, "bn" to bn, "hi" to hi, "ur" to ur, "ar" to ar, "ja" to ja,
        "zh" to zh, "zh_hk" to zhHk, "es" to es, "fr" to fr, "de" to de,
        "pt" to pt, "id" to id, "tr" to tr, "ko" to ko, "ru" to ru
    )
}
