package com.ardrawing.sketch

object L {
    var lang = "en"

    data class LangInfo(val code: String, val label: String)

    val languages = listOf(
        LangInfo("en", "English (US/UK/CA)"),
        LangInfo("bn", "বাংলা (BD/IN)"),
        LangInfo("hi", "हिन्दी (India)"),
        LangInfo("ur", "اردو (Pakistan)"),
        LangInfo("ar", "العربية (Saudi)"),
        LangInfo("ja", "日本語 (Japan)"),
        LangInfo("zh", "中文 (China)"),
        LangInfo("es", "Español"),
        LangInfo("fr", "Français"),
        LangInfo("de", "Deutsch"),
        LangInfo("pt", "Português"),
        LangInfo("id", "Indonesia"),
        LangInfo("tr", "Türkçe"),
        LangInfo("ko", "한국어"),
        LangInfo("ru", "Русский")
    )

    fun t(key: String): String = all[lang]?.get(key) ?: en[key] ?: key

    private val en = mapOf(
        "app" to "AR Drawing",
        "loading" to "Loading",
        "home" to "Home",
        "my_gallery" to "My Gallery",
        "store" to "Store",
        "settings" to "Settings",
        "home_sub" to "Place your phone over paper and trace any photo easily",
        "drawing_create" to "Drawing Create",
        "samples" to "Quick Samples",
        "choose_source" to "Choose Photo Source",
        "take_photo" to "Take Photo with Camera",
        "upload_gallery" to "Upload from Gallery",
        "remove_bg" to "Remove Background",
        "restore_orig" to "Restore Original",
        "removing_bg" to "Removing background...",
        "opacity" to "Opacity",
        "brightness" to "Brightness",
        "flash" to "Flash",
        "lock" to "Lock",
        "unlock" to "Unlock",
        "next" to "Next",
        "back" to "Back",
        "cancel" to "Cancel",
        "save" to "Save",
        "no_photo" to "Selected photo will appear here",
        "need_image" to "Please select or capture a photo first",
        "hint_place" to "Drag, pinch to zoom & rotate. Tap Lock when ready",
        "hint_locked" to "Locked ✓ Keep drawing! Move closer to zoom in",
        "hint_lost" to "Tracking paused — move phone back over your paper",
        "congrats" to "Congratulations! 🎉",
        "good_luck" to "Good Luck with your next masterpiece!",
        "go_home" to "Go Home",
        "my_activity" to "My Activity",
        "import_photo" to "Import / Upload",
        "empty_gallery" to "No drawings yet. Create your first drawing!",
        "rename" to "Rename",
        "share" to "Share",
        "delete" to "Delete",
        "delete_confirm" to "Delete this drawing?",
        "rename_hint" to "Enter drawing name",
        "draw_again" to "Draw",
        "store_title" to "Coin & Ad-Free Store",
        "coins_label" to "Coins",
        "remove_ads" to "Remove All Ads",
        "remove_ads_desc" to "Enjoy uninterrupted AR drawing forever",
        "ads_removed" to "Ads Removed ✓",
        "buy" to "Buy",
        "play_pending" to "Play Store billing becomes active after publishing on Google Play Console.",
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
        "privacy_text" to "AR Drawing processes your photos and camera feed locally on your device. Your saved drawings stay in your phone storage and are never uploaded to any external server. Advertising and in-app purchases are handled via Google Play Services.",
        "about_text" to "AR Drawing v1.0\nTrace any photo onto real paper with stable camera anchor locking and smart background removal.",
        "share_text" to "Check out AR Drawing! Draw any photo onto paper easily: https://play.google.com/store/apps/details?id=com.ardrawing.sketch"
    )

    private val bn = mapOf(
        "loading" to "লোডিং",
        "home" to "হোম",
        "my_gallery" to "মাই গ্যালারি",
        "store" to "স্টোর",
        "settings" to "সেটিংস",
        "home_sub" to "খাতার ওপর ফোন রেখে যেকোনো ছবি সহজে আঁকুন",
        "drawing_create" to "Drawing Create",
        "samples" to "নমুনা ছবি",
        "choose_source" to "ছবি নেওয়ার মাধ্যম বাছুন",
        "take_photo" to "ক্যামেরা দিয়ে ছবি তুলুন",
        "upload_gallery" to "গ্যালারি থেকে আপলোড",
        "remove_bg" to "ব্যাকগ্রাউন্ড রিমুভ",
        "restore_orig" to "আসল ছবি ফিরান",
        "removing_bg" to "ব্যাকগ্রাউন্ড সরানো হচ্ছে...",
        "opacity" to "স্বচ্ছতা (Opacity)",
        "brightness" to "আলো (Brightness)",
        "flash" to "ফ্ল্যাশ",
        "lock" to "লক",
        "unlock" to "আনলক",
        "next" to "পরবর্তী",
        "back" to "ফিরুন",
        "cancel" to "বাতিল",
        "save" to "সেভ",
        "congrats" to "অভিনন্দন! 🎉",
        "good_luck" to "শুভকামনা! সুন্দর হয়েছে আপনার আঁকা",
        "go_home" to "হোমে যান",
        "my_activity" to "মাই অ্যাক্টিভিটি",
        "import_photo" to "ইমপোর্ট / আপলোড",
        "empty_gallery" to "এখনো কোনো ড্রয়িং নেই। নতুন ড্রয়িং শুরু করুন!",
        "rename" to "নাম বদলান",
        "share" to "শেয়ার",
        "delete" to "ডিলিট",
        "remove_ads" to "সব অ্যাড রিমুভ করুন",
        "ads_removed" to "অ্যাড রিমুভ হয়েছে ✓",
        "language" to "ভাষা নির্বাচন",
        "privacy_policy" to "প্রাইভেসি পলিসি",
        "about" to "অ্যাপ সম্পর্কে",
        "share_friend" to "বন্ধুদের শেয়ার করুন",
        "rate_us" to "রেটিং দিন",
        "feedback" to "ফিডব্যাক পাঠান",
        "link_copied" to "অ্যাপ লিংক কপি হয়েছে!"
    )

    private val hi = mapOf(
        "loading" to "लोडिंग",
        "home" to "होम",
        "my_gallery" to "मेरी गैलरी",
        "store" to "स्टोर",
        "settings" to "सेटिंग्स",
        "take_photo" to "कैमरा से फोटो लें",
        "upload_gallery" to "गैलरी से अपलोड करें",
        "remove_bg" to "बैकग्राउंड हटाएं",
        "opacity" to "अपारदर्शिता",
        "brightness" to "चमक",
        "flash" to "फ्लैश",
        "lock" to "लॉक",
        "unlock" to "अनलॉक",
        "next" to "आगे",
        "congrats" to "बधाई हो! 🎉",
        "good_luck" to "शुभकामनाएं!",
        "go_home" to "होम पर जाएं",
        "language" to "भाषा"
    )

    private val ur = mapOf(
        "loading" to "لوڈنگ", "home" to "ہوم", "my_gallery" to "میری گیلری",
        "store" to "اسٹور", "settings" to "سیٹنگز", "lock" to "لاک",
        "unlock" to "ان لاک", "next" to "اگلا", "congrats" to "مبارک ہو! 🎉",
        "language" to "زبان"
    )

    private val ar = mapOf(
        "loading" to "جاري التحميل", "home" to "الرئيسية", "my_gallery" to "معرضي",
        "store" to "المتجر", "settings" to "الإعدادات", "lock" to "قفل",
        "unlock" to "فتح القفل", "next" to "التالي", "congrats" to "تهانينا! 🎉",
        "language" to "اللغة"
    )

    private val ja = mapOf(
        "loading" to "読み込み中", "home" to "ホーム", "my_gallery" to "ギャラリー",
        "store" to "ストア", "settings" to "設定", "lock" to "ロック",
        "unlock" to "解除", "next" to "次へ", "congrats" to "おめでとう！ 🎉",
        "language" to "言語"
    )

    private val zh = mapOf(
        "loading" to "加载中", "home" to "首页", "my_gallery" to "我的画廊",
        "store" to "商店", "settings" to "设置", "lock" to "锁定",
        "unlock" to "解锁", "next" to "下一步", "congrats" to "恭喜！ 🎉",
        "language" to "语言"
    )

    private val es = mapOf(
        "loading" to "Cargando", "home" to "Inicio", "my_gallery" to "Mi Galería",
        "store" to "Tienda", "settings" to "Ajustes", "lock" to "Bloquear",
        "unlock" to "Desbloquear", "next" to "Siguiente", "congrats" to "¡Felicidades! 🎉",
        "language" to "Idioma"
    )

    private val fr = mapOf(
        "loading" to "Chargement", "home" to "Accueil", "my_gallery" to "Ma Galerie",
        "store" to "Boutique", "settings" to "Paramètres", "lock" to "Verrouiller",
        "unlock" to "Déverrouiller", "next" to "Suivant", "congrats" to "Félicitations ! 🎉",
        "language" to "Langue"
    )

    private val de = mapOf(
        "loading" to "Laden", "home" to "Start", "my_gallery" to "Galerie",
        "store" to "Shop", "settings" to "Einstellungen", "lock" to "Sperren",
        "unlock" to "Entsperren", "next" to "Weiter", "congrats" to "Glückwunsch! 🎉",
        "language" to "Sprache"
    )

    private val pt = mapOf(
        "loading" to "Carregando", "home" to "Início", "my_gallery" to "Galeria",
        "store" to "Loja", "settings" to "Configurações", "lock" to "Travar",
        "unlock" to "Destravar", "next" to "Próximo", "congrats" to "Parabéns! 🎉",
        "language" to "Idioma"
    )

    private val id = mapOf(
        "loading" to "Memuat", "home" to "Beranda", "my_gallery" to "Galeri Saya",
        "store" to "Toko", "settings" to "Pengaturan", "lock" to "Kunci",
        "unlock" to "Buka", "next" to "Lanjut", "congrats" to "Selamat! 🎉",
        "language" to "Bahasa"
    )

    private val tr = mapOf(
        "loading" to "Yükleniyor", "home" to "Ana Sayfa", "my_gallery" to "Galerim",
        "store" to "Mağaza", "settings" to "Ayarlar", "lock" to "Kilitle",
        "unlock" to "Kilidi Aç", "next" to "İleri", "congrats" to "Tebrikler! 🎉",
        "language" to "Dil"
    )

    private val ko = mapOf(
        "loading" to "로딩 중", "home" to "홈", "my_gallery" to "내 갤러리",
        "store" to "스토어", "settings" to "설정", "lock" to "잠금",
        "unlock" to "해제", "next" to "다음", "congrats" to "축하합니다! 🎉",
        "language" to "언어"
    )

    private val ru = mapOf(
        "loading" to "Загрузка", "home" to "Главная", "my_gallery" to "Галерея",
        "store" to "Магазин", "settings" to "Настройки", "lock" to "Закрепить",
        "unlock" to "Открепить", "next" to "Далее", "congrats" to "Поздравляем! 🎉",
        "language" to "Язык"
    )

    private val all = mapOf(
        "en" to en, "bn" to bn, "hi" to hi, "ur" to ur, "ar" to ar,
        "ja" to ja, "zh" to zh, "es" to es, "fr" to fr, "de" to de,
        "pt" to pt, "id" to id, "tr" to tr, "ko" to ko, "ru" to ru
    )
}
