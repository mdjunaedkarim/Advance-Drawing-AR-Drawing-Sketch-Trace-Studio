package com.ardrawing.sketch

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object UiTabs {

    var selectedCat: Samples.Cat = Samples.Cat.ALL
    val selectedFiles = HashSet<String>()

    fun cuteBtn(act: Activity, label: String, bg1: String, bg2: String, hDp: Int, onClick: () -> Unit) = Button(act).apply {
        text = label; setTextColor(Color.WHITE); textSize = 15f; isAllCaps = false
        setTypeface(null, Typeface.BOLD); background = UiTheme.grad(act, bg1, bg2, 18)
        elevation = UiTheme.dp(act, 4).toFloat()
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, UiTheme.dp(act, hDp)).apply { bottomMargin = UiTheme.dp(act, 12) }
        UiTheme.bounce(this, onClick)
    }

    fun buildHome(
        act: Activity,
        onCreateClick: () -> Unit,
        onCameraClick: () -> Unit,
        onGalleryPickClick: () -> Unit,
        onMyActivityClick: () -> Unit,
        onSampleClick: (Bitmap) -> Unit,
        onRefresh: () -> Unit
    ): View {
        val dp = { v: Int -> UiTheme.dp(act, v) }
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(20)) }

        // Hero Banner
        val hero = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            background = UiTheme.grad(act, if (UiTheme.isDark) "#2E1065" else "#EDE9FE", if (UiTheme.isDark) "#1E1B4B" else "#DDD6FE", 22)
            setPadding(dp(18), dp(16), dp(18), dp(16)); elevation = dp(3).toFloat()
            addView(TextView(act).apply {
                text = "Trace  •  Create  •  Improve"; textSize = 19f; setTypeface(null, Typeface.BOLD)
                setTextColor(if (UiTheme.isDark) Color.WHITE else Color.parseColor("#4C1D95"))
            })
            addView(TextView(act).apply {
                text = L.t("home_sub"); textSize = 13f
                setTextColor(if (UiTheme.isDark) Color.parseColor("#DDD6FE") else Color.parseColor("#5B21B6"))
                setPadding(0, dp(4), 0, 0)
            })
        }
        col.addView(hero, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(14) })

        // Main Drawing Create Card
        val createCard = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.grad(act, "#8B5CF6", "#6D28D9", 20); setPadding(dp(16), dp(16), dp(16), dp(16)); elevation = dp(5).toFloat()
            addView(TextView(act).apply {
                text = "✏️"; textSize = 22f; gravity = Gravity.CENTER; background = UiTheme.round(act, Color.parseColor("#33FFFFFF"), 14)
            }, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(12) })
            val tc = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
            tc.addView(TextView(act).apply { text = L.t("drawing_create"); textSize = 17f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE) })
            tc.addView(TextView(act).apply { text = "${L.t("take_photo")} / ${L.t("upload_gallery")}"; textSize = 12f; setTextColor(Color.parseColor("#EDE9FE")) })
            addView(tc, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(TextView(act).apply { text = "›"; textSize = 24f; setTextColor(Color.WHITE) })
        }
        UiTheme.bounce(createCard, onCreateClick)
        col.addView(createCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(14) })

        // 3 Pastel Quick Cards
        val quickRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        fun qCard(emoji: String, label: String, lHex: String, dHex: String, onTap: () -> Unit) = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; background = UiTheme.round(act, Color.parseColor(if (UiTheme.isDark) dHex else lHex), 18)
            setPadding(dp(12), dp(14), dp(12), dp(14)); elevation = dp(2).toFloat()
            addView(TextView(act).apply { text = emoji; textSize = 22f })
            addView(TextView(act).apply {
                text = label; textSize = 12f; setTypeface(null, Typeface.BOLD); setTextColor(UiTheme.textMain)
                maxLines = 1; setPadding(0, dp(8), 0, 0)
            })
            UiTheme.bounce(this, onTap)
        }
        quickRow.addView(qCard("📷", L.t("take_photo"), "#E0E7FF", "#1E293B", onCameraClick), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        quickRow.addView(qCard("🖼️", L.t("upload_gallery"), "#FEF3C7", "#292524", onGalleryPickClick), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        quickRow.addView(qCard("📁", L.t("my_activity"), "#FCE7F3", "#2E1A2B", onMyActivityClick), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        col.addView(quickRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(16) })

        // Categories & 54 Drawings
        col.addView(TextView(act).apply {
            text = "${L.t("samples")} (${Samples.items.size})"; textSize = 16f; setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textMain); setPadding(0, 0, 0, dp(10))
        })

        val catRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        Samples.Cat.values().forEach { cat ->
            val active = selectedCat == cat
            val chip = TextView(act).apply {
                text = L.t(cat.key); textSize = 13f; setTypeface(null, Typeface.BOLD)
                setTextColor(if (active) Color.WHITE else UiTheme.textMain)
                background = if (active) UiTheme.grad(act, "#8B5CF6", "#6D28D9", 16) else UiTheme.round(act, UiTheme.surface, 16, UiTheme.stroke)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                UiTheme.bounce(this) { selectedCat = cat; onRefresh() }
            }
            catRow.addView(chip, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) })
        }
        col.addView(HorizontalScrollView(act).apply {
            isHorizontalScrollBarEnabled = false; addView(catRow)
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })

        val filtered = if (selectedCat == Samples.Cat.ALL) Samples.items else Samples.items.filter { it.cat == selectedCat }
        val cardW = (act.resources.displayMetrics.widthPixels - dp(44)) / 2
        var gridRow: LinearLayout? = null
        filtered.forEachIndexed { idx, item ->
            if (idx % 2 == 0) {
                gridRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
                col.addView(gridRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })
            }
            val card = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL; background = UiTheme.round(act, Color.WHITE, 18, UiTheme.stroke)
                setPadding(dp(8), dp(8), dp(8), dp(8)); elevation = dp(2).toFloat()
                addView(ImageView(act).apply {
                    setImageBitmap(Samples.render(item.id, 300)); scaleType = ImageView.ScaleType.FIT_CENTER
                }, LinearLayout.LayoutParams(MATCH_PARENT, cardW - dp(36)))
                addView(TextView(act).apply {
                    text = item.title; textSize = 12f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#1E1B4B")); maxLines = 1; setPadding(0, dp(4), 0, 0)
                })
                UiTheme.bounce(this) { onSampleClick(Samples.render(item.id, 960)) }
            }
            gridRow?.addView(card, LinearLayout.LayoutParams(cardW, WRAP_CONTENT).apply { if (idx % 2 == 0) marginEnd = dp(12) })
        }
        return col
    }

    fun buildGallery(
        act: Activity,
        galleryDir: File,
        prefs: SharedPreferences,
        onImportClick: () -> Unit,
        onExportZip: (List<File>) -> Unit,
        onDraw: (Bitmap) -> Unit,
        onRefresh: () -> Unit
    ): View {
        val dp = { v: Int -> UiTheme.dp(act, v) }
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(20)) }

        val files = (galleryDir.listFiles()?.toList() ?: emptyList()).filter { it.name.endsWith(".png") }.sortedByDescending { it.lastModified() }

        // Top Import & Export ZIP buttons
        val topBtns = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        val impBtn = Button(act).apply {
            text = "📥  ${L.t("import_photo")}"; setTextColor(Color.WHITE); isAllCaps = false; textSize = 14f; setTypeface(null, Typeface.BOLD)
            background = UiTheme.grad(act, "#3B82F6", "#2563EB", 16); elevation = dp(3).toFloat()
            UiTheme.bounce(this, onImportClick)
        }
        val expBtn = Button(act).apply {
            val cnt = selectedFiles.size
            text = if (cnt > 0) "📤  ${L.t("export_zip")} ($cnt)" else "📤  ${L.t("export_zip")}"
            setTextColor(Color.WHITE); isAllCaps = false; textSize = 14f; setTypeface(null, Typeface.BOLD)
            background = UiTheme.grad(act, "#8B5CF6", "#6D28D9", 16); elevation = dp(3).toFloat()
            UiTheme.bounce(this) {
                val chosen = files.filter { selectedFiles.contains(it.name) }
                if (chosen.isEmpty()) Toast.makeText(act, L.t("select_items"), Toast.LENGTH_SHORT).show()
                else onExportZip(chosen)
            }
        }
        topBtns.addView(impBtn, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        topBtns.addView(expBtn, LinearLayout.LayoutParams(0, dp(48), 1f))
        col.addView(topBtns, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(14) })

        if (files.isEmpty()) {
            col.addView(TextView(act).apply { text = L.t("empty_gallery"); setTextColor(UiTheme.textSub); gravity = Gravity.CENTER; setPadding(0, dp(48), 0, 0) })
            return col
        }

        val cardSize = (act.resources.displayMetrics.widthPixels - dp(44)) / 2
        var row: LinearLayout? = null
        files.forEachIndexed { idx, file ->
            if (idx % 2 == 0) {
                row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
                col.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })
            }
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
            val savedName = prefs.getString("gname_${file.name}", "Art #${files.size - idx}") ?: "Art"
            val isSel = selectedFiles.contains(file.name)

            val card = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                background = UiTheme.round(act, UiTheme.surface, 18, if (isSel) UiTheme.accent else UiTheme.stroke)
                setPadding(dp(8), dp(8), dp(8), dp(8)); elevation = dp(3).toFloat()

                val imgWrap = FrameLayout(act)
                val iv = ImageView(act).apply { setImageBitmap(bmp); scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(UiTheme.surfaceAlt) }
                UiTheme.bounce(iv) { if (bmp != null) onDraw(bmp) }
                imgWrap.addView(iv, FrameLayout.LayoutParams(MATCH_PARENT, cardSize - dp(16)))

                // Selection Checkbox badge for Export ZIP
                val selBadge = TextView(act).apply {
                    text = if (isSel) "✓" else ""; textSize = 14f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                    background = UiTheme.round(act, if (isSel) UiTheme.accent else Color.parseColor("#88000000"), 14, Color.WHITE)
                    setOnClickListener {
                        if (selectedFiles.contains(file.name)) selectedFiles.remove(file.name) else selectedFiles.add(file.name)
                        onRefresh()
                    }
                }
                imgWrap.addView(selBadge, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(6), dp(6), 0) })
                addView(imgWrap)

                addView(TextView(act).apply {
                    text = savedName; setTextColor(UiTheme.textMain); setTypeface(null, Typeface.BOLD); textSize = 13f; maxLines = 1
                    setPadding(dp(2), dp(8), dp(2), dp(6))
                })

                val br = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
                fun miniBtn(t: String, onTap: () -> Unit) = TextView(act).apply {
                    text = t; textSize = 12f; gravity = Gravity.CENTER; setTextColor(UiTheme.textMain)
                    background = UiTheme.round(act, UiTheme.surfaceAlt, 10); setPadding(0, dp(6), 0, dp(6)); UiTheme.bounce(this, onTap)
                }
                br.addView(miniBtn("✏️") {
                    val inp = EditText(act).apply { setText(savedName); hint = L.t("rename_hint") }
                    AlertDialog.Builder(act).setTitle(L.t("rename")).setView(inp)
                        .setPositiveButton(L.t("save")) { _, _ ->
                            val nn = inp.text.toString().trim()
                            if (nn.isNotEmpty()) { prefs.edit().putString("gname_${file.name}", nn).apply(); onRefresh() }
                        }.setNegativeButton(L.t("cancel"), null).show()
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })

                br.addView(miniBtn("🔗") {
                    try {
                        val u = FileProvider.getUriForFile(act, "${act.packageName}.fileprovider", file)
                        act.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"; putExtra(Intent.EXTRA_STREAM, u); putExtra(Intent.EXTRA_TEXT, "$savedName — ${L.t("share_text")}")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, L.t("share")))
                    } catch (_: Exception) {}
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })

                br.addView(miniBtn("🗑️") {
                    AlertDialog.Builder(act).setMessage(L.t("delete_confirm"))
                        .setPositiveButton(L.t("delete")) { _, _ ->
                            file.delete(); selectedFiles.remove(file.name); prefs.edit().remove("gname_${file.name}").apply(); onRefresh()
                        }.setNegativeButton(L.t("cancel"), null).show()
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(br)
            }
            row?.addView(card, LinearLayout.LayoutParams(cardSize, WRAP_CONTENT).apply { if (idx % 2 == 0) marginEnd = dp(12) })
        }
        return col
    }

    fun buildStore(act: Activity, billingAds: BillingAdsManager): View {
        val dp = { v: Int -> UiTheme.dp(act, v) }
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(20)) }
        BillingAdsManager.PACKS.forEach { pack ->
            val card = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = if (pack.isRemoveAds) UiTheme.grad(act, "#6D28D9", "#4F46E5", 18) else UiTheme.round(act, UiTheme.surface, 18, UiTheme.stroke)
                setPadding(dp(14), dp(14), dp(14), dp(14)); elevation = dp(3).toFloat()
            }
            val info = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
            info.addView(TextView(act).apply {
                text = if (pack.isRemoveAds) "🛡️  ${L.t("remove_ads")}" else "🪙  ${pack.coins} ${L.t("coins_label")}"
                textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(if (pack.isRemoveAds) Color.WHITE else UiTheme.textMain)
            })
            if (pack.isRemoveAds) info.addView(TextView(act).apply { text = L.t("remove_ads_desc"); textSize = 12f; setTextColor(Color.parseColor("#EDE9FE")) })
            val done = pack.isRemoveAds && billingAds.adsRemoved
            card.addView(info, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            card.addView(Button(act).apply {
                text = if (done) L.t("ads_removed") else pack.priceLabel; setTextColor(Color.WHITE); isAllCaps = false
                background = UiTheme.grad(act, if (done) "#10B981" else "#7C3AED", if (done) "#059669" else "#5B21B6", 14)
                UiTheme.bounce(this) { if (!done) billingAds.purchase(act, pack) }
            }, LinearLayout.LayoutParams(WRAP_CONTENT, dp(42)))
            col.addView(card, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })
        }
        return col
    }

    fun buildSettings(act: Activity, prefs: SharedPreferences, onOpenLanguagePage: () -> Unit, onRefresh: () -> Unit): View {
        val dp = { v: Int -> UiTheme.dp(act, v) }
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(24)) }

        val themeCard = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.round(act, UiTheme.surface, 18, UiTheme.stroke); setPadding(dp(16), dp(12), dp(16), dp(12)); elevation = dp(2).toFloat()
            addView(TextView(act).apply {
                text = if (UiTheme.isDark) "🌙  Dark Mode (Black)" else "☀️  Light Mode (White)"
                textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(UiTheme.textMain)
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(Switch(act).apply {
                isChecked = UiTheme.isDark
                setOnCheckedChangeListener { _, checked -> UiTheme.isDark = checked; prefs.edit().putBoolean("dark_mode", checked).apply(); onRefresh() }
            })
        }
        col.addView(themeCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })

        fun addSetting(title: String, onTap: () -> Unit) {
            val row = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = UiTheme.round(act, UiTheme.surface, 16, UiTheme.stroke); setPadding(dp(16), dp(15), dp(16), dp(15)); elevation = dp(2).toFloat()
                addView(TextView(act).apply { text = title; textSize = 15f; setTextColor(UiTheme.textMain) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(TextView(act).apply { text = "›"; textSize = 20f; setTextColor(UiTheme.textSub) })
                UiTheme.bounce(this, onTap)
            }
            col.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })
        }

        val cur = L.languages.find { it.code == L.lang }?.let { "${it.label} (${it.sub})" } ?: "English"
        addSetting("🌐  ${L.t("language")}: $cur", onOpenLanguagePage)
        addSetting("🔒  ${L.t("privacy_policy")}") {
            try { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(L.PRIVACY_URL))) } catch (_: Exception) {}
        }
        addSetting("ℹ️  ${L.t("about")}") { openPlayStore(act) }
        addSetting("📤  ${L.t("share_friend")}") {
            val url = "https://play.google.com/store/apps/details?id=${act.packageName}"
            (act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Link", url))
            Toast.makeText(act, L.t("link_copied"), Toast.LENGTH_SHORT).show()
            act.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, L.t("share_text")) }, L.t("share_friend")))
        }
        addSetting("⭐  ${L.t("rate_us")}") { openPlayStore(act) }
        addSetting("✉️  ${L.t("feedback")}") {
            try { act.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply { putExtra(Intent.EXTRA_SUBJECT, "Free Art Center Feedback") }) } catch (_: Exception) {}
        }
        return col
    }

    private fun openPlayStore(act: Activity) {
        try { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${act.packageName}"))) }
        catch (_: Exception) { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${act.packageName}"))) }
    }
}
