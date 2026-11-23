package com.ardrawing.sketch

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

object PrivacyPolicyContent {

    const val EFFECTIVE_DATE = "November 23, 2026"
    const val SUPPORT_EMAIL = "md.junaed.karim@gmail.com"

    private data class Section(val icon: StudioIcons.Type, val title: String, val body: String)

    private fun sections(isBn: Boolean): List<Section> = if (isBn) {
        listOf(
            Section(
                StudioIcons.Type.SHIELD_LOCK,
                "১. সংক্ষিপ্ত বিবরণ ও কোনো অ্যাকাউন্ট ছাড়াই ব্যবহার",
                "Advance Drawing : AR Sketch & Trace Studio (com.advancedrawing.com) ব্যবহারকারীর গোপনীয়তাকে সর্বোচ্চ গুরুত্ব দেয়। কোনো অ্যাকাউন্ট বা লগইন ছাড়াই আপনি সরাসরি অ্যাপটি ব্যবহার করতে পারবেন। আমরা আপনার নাম, ইমেইল, ফোন নম্বর বা আঁকা ছবি আমাদের কোনো সার্ভারে সংগ্রহ বা সংরক্ষণ করি না।"
            ),
            Section(
                StudioIcons.Type.CREATE_PEN,
                "২. ডিভাইসের ভেতরে প্রসেসিং ও লোকাল স্টোরেজ",
                "আপনার তোলা বা বাছাই করা ছবি, ক্রপ করা ছবি, ব্যাকগ্রাউন্ড রিমুভ এবং লাইন আর্ট প্রসেসিং সম্পূর্ণভাবে আপনার ফোনের ভেতরেই (OpenCV ও Google ML Kit-এর মাধ্যমে) সম্পন্ন হয়। 'My Gallery'-তে সেভ করা আঁকা ছবি, কয়েন ব্যালেন্স ও সেটিংস শুধুমাত্র আপনার ডিভাইসের প্রাইভেট স্টোরেজে থাকে এবং কোনো সার্ভারে আপলোড হয় না।"
            ),
            Section(
                StudioIcons.Type.CAMERA,
                "৩. অ্যাপের অনুমতিসমূহ (Permissions)",
                "• ক্যামেরা (CAMERA): শুধুমাত্র আপনার অনুমতি নিয়ে কাগজের উপর AR স্কেচ দেখানো ও রেফারেন্স ছবি তোলার জন্য ব্যবহৃত হয়। ক্যামেরার ভিডিও বা ফ্রেম কখনোই রেকর্ড বা আপলোড করা হয় না।\n• ছবি ও ফাইল নির্বাচন: কোনো স্টোরেজ পারমিশন ছাড়াই Android-এর নিরাপদ Photo Picker ও সিস্টেম ফাইল পিকার ব্যবহার করা হয়।\n• ইন্টারনেট ও নেটওয়ার্ক: Google AdMob বিজ্ঞাপন, আপনার সার্চ করা রেফারেন্স ছবি এবং Google Play কেনাকাটা যাচাইয়ের জন্য ব্যবহৃত হয়।"
            ),
            Section(
                StudioIcons.Type.GLOBE,
                "৪. তৃতীয় পক্ষের সেবা (Third-Party Services)",
                "• Google AdMob: অ্যাপ ওপেন, ব্যানার এবং রিওয়ার্ডেড অ্যাড দেখানোর জন্য ব্যবহৃত হয় (অ্যাড-ফ্রি পাস বা লাইফটাইম VIP নিলে অ্যাড বন্ধ থাকে)। Google তাদের Privacy Policy অনুযায়ী Advertising ID ও অ্যাড পারফরম্যান্স তথ্য ব্যবহার করতে পারে।\n• Google Play Billing: কয়েন প্যাক ও লাইফটাইম অ্যাড-ফ্রি কেনাকাটা নিরাপদে Google Play-র মাধ্যমে সম্পন্ন হয়। আমরা আপনার কার্ড বা পেমেন্ট তথ্য সংগ্রহ করি না।\n• Microsoft Bing Images: আপনি যদি অ্যাপের ভেতরের ওয়েব সার্চ দিয়ে ছবি খোঁজেন, তবে সার্চ শব্দগুলো Bing-এ পাঠানো হয় এবং Microsoft-এর প্রাইভেসি নীতি প্রযোজ্য হয়।"
            ),
            Section(
                StudioIcons.Type.TRASH,
                "৫. ডেটা শেয়ারিং ও মুছে ফেলার অধিকার",
                "আমরা কারো ব্যক্তিগত তথ্য বিক্রি বা ভাড়া দিই না। গ্যালারিতে থাকা যেকোনো ছবি আপনি নিজেই ডিলিট করতে পারবেন অথবা অ্যাপ আনইনস্টল করলে সব লোকাল ডেটা স্বয়ংক্রিয়ভাবে মুছে যাবে। আপনি নিজে Share বা Export ZIP না করলে কোনো ছবি বাইরে যায় না।"
            ),
            Section(
                StudioIcons.Type.CHILD_SHIELD,
                "৬. শিশুদের গোপনীয়তা (Children's Privacy)",
                "Advance Drawing : AR Sketch & Trace Studio জেনেশুনে ১৩ বছরের কম বয়সী শিশুদের কোনো ব্যক্তিগত তথ্য সংগ্রহ করে না। কোনো অভিভাবক মনে করলে যে তৃতীয় পক্ষের সেবার মাধ্যমে শিশুর তথ্য শেয়ার হয়েছে, আমাদের ইমেইলে জানালে দ্রুত ব্যবস্থা নেওয়া হবে।"
            ),
            Section(
                StudioIcons.Type.MAIL,
                "৭. নীতিমালা পরিবর্তন ও যোগাযোগ",
                "অ্যাপের নতুন ফিচার যুক্ত হলে এই Privacy Policy সময়ে সময়ে আপডেট হতে পারে। কার্যকর তারিখ: $EFFECTIVE_DATE।\n\nযেকোনো প্রশ্ন বা সহায়তার জন্য যোগাযোগ করুন:\n$SUPPORT_EMAIL"
            )
        )
    } else {
        listOf(
            Section(
                StudioIcons.Type.SHIELD_LOCK,
                "1. Overview & No Account Required",
                "Advance Drawing : AR Sketch & Trace Studio (com.advancedrawing.com) respects your privacy. You can use the entire app directly without creating an account or signing in. We do not operate developer servers that collect or store your name, email, phone number, or drawings."
            ),
            Section(
                StudioIcons.Type.CREATE_PEN,
                "2. On-Device Processing & Local Storage",
                "Photos you capture, select, crop, edit, or trace are processed locally on your device using OpenCV and Google ML Kit. Drawings saved in My Gallery, along with your coin balance, language, and theme settings, stay strictly on your device in private app storage and are never uploaded to developer servers."
            ),
            Section(
                StudioIcons.Type.CAMERA,
                "3. App Permissions We Use",
                "• Camera (CAMERA): Used solely with your permission to project reference sketches over paper in AR Drawing mode or take a reference photo. Live camera frames are processed in memory for surface tracking and are never recorded or uploaded.\n• Photos & Files: Uses Android's zero-permission Photo Picker and system file picker only when you explicitly choose to import an image or ZIP archive.\n• Internet & Network State: Used for Google AdMob ads, optional web sketch searches initiated by you, and Google Play purchases."
            ),
            Section(
                StudioIcons.Type.GLOBE,
                "4. Third-Party Services & SDKs",
                "• Google AdMob: Displays App Open, Banner, and Rewarded Interstitial ads (unless paused with Coin Ad-Free passes or Lifetime VIP). Google may process advertising identifiers (such as Google Advertising ID) and ad metrics under Google's Privacy Policy.\n• Google Play Billing: Processes Coin packs and Lifetime Ad-Free purchases securely via Google Play. We never collect or store payment card details.\n• Microsoft Bing Images (In-App Search): When you choose to search the web for sketches, search queries are sent directly to Bing Images under Microsoft's Privacy Statement."
            ),
            Section(
                StudioIcons.Type.TRASH,
                "5. Data Sharing, Retention & Deletion",
                "We never sell, rent, or trade user data. Saved artworks stay on your device until you delete them in My Gallery or uninstall the app. Content is shared externally only when you explicitly tap Share or Export ZIP."
            ),
            Section(
                StudioIcons.Type.CHILD_SHIELD,
                "6. Children's Privacy",
                "Advance Drawing : AR Sketch & Trace Studio does not knowingly collect personal information from children under 13 (or the minimum applicable age in your region). Please contact us if you believe any information was inadvertently collected."
            ),
            Section(
                StudioIcons.Type.MAIL,
                "7. Policy Updates & Contact Us",
                "We may update this Privacy Policy periodically as the App evolves. Effective Date: $EFFECTIVE_DATE.\n\nDeveloper Contact: $SUPPORT_EMAIL"
            )
        )
    }

    fun buildPlainText(isBn: Boolean): String {
        val header = if (isBn) {
            "Privacy Policy — Advance Drawing : AR Sketch & Trace Studio (com.advancedrawing.com)\nকার্যকর তারিখ: $EFFECTIVE_DATE\n\n"
        } else {
            "Privacy Policy — Advance Drawing : AR Sketch & Trace Studio (com.advancedrawing.com)\nEffective Date: $EFFECTIVE_DATE\n\n"
        }
        return header + sections(isBn).joinToString("\n\n") { "${it.title}\n${it.body}" }
    }

    fun buildView(act: Activity, onBack: () -> Unit): View {
        val dp = { v: Int -> UiTheme.dp(act, v) }
        val isBn = L.lang == "bn"

        val rootCol = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiTheme.bg)
        }

        // Top Bar
        val topBar = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(UiTheme.surface)
            setPadding(dp(16), dp(34), dp(16), dp(12))
            elevation = dp(3).toFloat()
        }
        topBar.addView(
            UiTheme.studioIconBtn(
                act = act,
                icon = StudioIcons.Type.ARROW_LEFT,
                sizeDp = 44,
                iconSizeDp = 20,
                fillColor = UiTheme.surfaceAlt,
                strokeColor = UiTheme.stroke,
                iconTint = UiTheme.textMain,
                contentDesc = L.t("back"),
                onClick = onBack
            ),
            LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) }
        )
        topBar.addView(TextView(act).apply {
            text = L.t("privacy_policy")
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textMain)
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        rootCol.addView(topBar)

        val bodyCol = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(28))
        }

        // Hero Card
        val hero = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.grad(act, "#4C1D95", "#312E81", 22, Color.parseColor("#33FFFFFF"))
            setPadding(dp(18), dp(18), dp(18), dp(18))
            elevation = dp(6).toFloat()
        }
        val heroIconWell = FrameLayout(act).apply {
            background = UiTheme.round(act, Color.parseColor("#28FFFFFF"), 14)
            addView(
                StudioIcons.imageView(act, StudioIcons.Type.SHIELD_LOCK, 24, Color.WHITE),
                FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER)
            )
        }
        hero.addView(heroIconWell, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(14) })
        val heroTextCol = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        heroTextCol.addView(TextView(act).apply {
            text = "Advance Drawing : AR Sketch & Trace Studio"
            textSize = 16.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        heroTextCol.addView(TextView(act).apply {
            text = "com.advancedrawing.com • $EFFECTIVE_DATE"
            textSize = 12f
            setTextColor(Color.parseColor("#DDD6FE"))
            setPadding(0, dp(3), 0, 0)
        })
        hero.addView(heroTextCol, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        bodyCol.addView(hero, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(14) })

        // Sections
        sections(isBn).forEach { sec ->
            val card = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                background = UiTheme.round(act, UiTheme.surface, 18, UiTheme.stroke)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                elevation = dp(2).toFloat()
            }
            val titleRow = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val secIconBox = FrameLayout(act).apply {
                background = UiTheme.round(act, UiTheme.accentSoft, 10)
                addView(
                    StudioIcons.imageView(act, sec.icon, 18, UiTheme.accentBright),
                    FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER)
                )
            }
            titleRow.addView(secIconBox, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(10) })
            titleRow.addView(TextView(act).apply {
                text = sec.title
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(UiTheme.textMain)
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            card.addView(titleRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })
            card.addView(TextView(act).apply {
                text = sec.body
                textSize = 13.5f
                setTextColor(UiTheme.textSub)
                setLineSpacing(0f, 1.32f)
            })
            bodyCol.addView(card, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })
        }

        // Action Buttons (Copy & Email)
        val btnRow = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, 0)
        }
        val copyBtn = UiTheme.studioSoftAction(
            act = act,
            icon = StudioIcons.Type.COPY,
            label = L.t("copy_policy"),
            fillColor = UiTheme.surface,
            strokeColor = UiTheme.stroke,
            textColor = UiTheme.textMain,
            iconColor = UiTheme.accentBright,
            rDp = 15
        ) {
            val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Privacy Policy", buildPlainText(isBn)))
            Toast.makeText(act, L.t("policy_copied"), Toast.LENGTH_SHORT).show()
        }
        val mailBtn = UiTheme.studioGradientAction(
            act = act,
            icon = StudioIcons.Type.MAIL,
            label = L.t("contact_dev"),
            hex1 = "#8B5CF6",
            hex2 = "#6D28D9",
            rDp = 15
        ) {
            try {
                act.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL")).apply {
                    putExtra(Intent.EXTRA_SUBJECT, "Advance Drawing : AR Sketch & Trace Studio — Privacy Inquiry")
                })
            } catch (_: Exception) {}
        }
        btnRow.addView(copyBtn, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        btnRow.addView(mailBtn, LinearLayout.LayoutParams(0, dp(48), 1f))
        bodyCol.addView(btnRow)

        rootCol.addView(ScrollView(act).apply { addView(bodyCol) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        return rootCol
    }
}
