package com.ardrawing.sketch

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.animation.OvershootInterpolator
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

    var selectedCat: Samples.Cat = Samples.Cat.ANIME
    val selectedFiles = HashSet<String>()
    private var storeCoinsTab = true

    private val extraTopics = mapOf(
        Samples.Cat.CAR to listOf("Sports Car", "Lamborghini", "Superbike", "Fighter Jet", "Airplane", "Space Rocket"),
        Samples.Cat.FOOD to listOf("Pizza Slice", "Burger", "Ice Cream", "Cupcake", "Mango", "Pineapple"),
        Samples.Cat.FLOWER to listOf("Rose Flower", "Lotus Flower", "Sunflower", "Tulip Flower", "Sakura Blossom", "Butterfly on Flower"),
        Samples.Cat.SCENERY to listOf("Mountain River Boat", "Village House", "Sunset Beach", "Waterfall Landscape", "River Bridge"),
        Samples.Cat.ANIMALS to listOf("Cute Cat", "Lion Face", "Peacock", "Parrot", "Cute Puppy", "Dragon")
    )

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
        onBrowseOnline: (String) -> Unit,
        onOpenAnime: (AnimeCatalog.Series) -> Unit,
        onRefresh: () -> Unit
    ): View {
        val dp = { v: Int -> UiTheme.dp(act, v) }
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(20)) }

        // Microsoft Bing Live Search Bar + Character Auto-Suggest
        val searchBoxWrap = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.round(act, UiTheme.surface, 20, UiTheme.accent)
            setPadding(dp(12), dp(4), dp(6), dp(4)); elevation = dp(4).toFloat()
        }
        val searchInput = EditText(act).apply {
            hint = "Search any character or drawing (e.g. T, N)..."
            setTextColor(UiTheme.textMain); setHintTextColor(UiTheme.textSub)
            textSize = 14f; background = null; setSingleLine(true)
        }
        val goBtn = Button(act).apply {
            text = "🔍 Bing"; setTextColor(Color.WHITE); textSize = 12f; isAllCaps = false; setTypeface(null, Typeface.BOLD)
            background = UiTheme.grad(act, "#0EA5E9", "#2563EB", 16)
            UiTheme.bounce(this) {
                val q = searchInput.text.toString().trim()
                onBrowseOnline(if (q.isEmpty()) "Anime easy line drawing" else "$q easy line drawing")
            }
        }
        searchBoxWrap.addView(searchInput, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        searchBoxWrap.addView(goBtn, LinearLayout.LayoutParams(WRAP_CONTENT, dp(38)))
        col.addView(searchBoxWrap, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })

        val suggestCol = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        col.addView(suggestCol, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                suggestCol.removeAllViews()
                val q = s?.toString()?.trim() ?: ""
                if (q.isEmpty()) return
                val hits = AnimeCatalog.searchAll(q)
                hits.forEach { (ch, anime) ->
                    val item = LinearLayout(act).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                        background = UiTheme.round(act, UiTheme.surface, 12, UiTheme.stroke)
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                        addView(TextView(act).apply { text = "🔍 $ch"; textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(UiTheme.textMain) },
                            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                        addView(TextView(act).apply { text = anime; textSize = 11f; setTextColor(UiTheme.accent) })
                        UiTheme.bounce(this) { onBrowseOnline("$ch $anime easy line drawing") }
                    }
                    suggestCol.addView(item, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(4) })
                }
            }
        })

        // Drawing Create Card
        val createCard = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.grad(act, "#8B5CF6", "#6D28D9", 20); setPadding(dp(16), dp(14), dp(16), dp(14)); elevation = dp(5).toFloat()
            addView(TextView(act).apply {
                text = "✏️"; textSize = 20f; gravity = Gravity.CENTER; background = UiTheme.round(act, Color.parseColor("#33FFFFFF"), 14)
            }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })
            val tc = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
            tc.addView(TextView(act).apply { text = L.t("drawing_create"); textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE) })
            tc.addView(TextView(act).apply { text = "${L.t("take_photo")} / ${L.t("upload_gallery")}"; textSize = 12f; setTextColor(Color.parseColor("#EDE9FE")) })
            addView(tc, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(TextView(act).apply { text = "›"; textSize = 24f; setTextColor(Color.WHITE) })
        }
        UiTheme.bounce(createCard, onCreateClick)
        col.addView(createCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6); bottomMargin = dp(12) })

        // 3 Quick Cards
        val quickRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        fun qCard(emoji: String, label: String, lHex: String, dHex: String, onTap: () -> Unit) = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; background = UiTheme.round(act, Color.parseColor(if (UiTheme.isDark) dHex else lHex), 18)
            setPadding(dp(12), dp(12), dp(12), dp(12)); elevation = dp(2).toFloat()
            addView(TextView(act).apply { text = emoji; textSize = 22f })
            addView(TextView(act).apply { text = label; textSize = 12f; setTypeface(null, Typeface.BOLD); setTextColor(UiTheme.textMain); maxLines = 1; setPadding(0, dp(6), 0, 0) })
            UiTheme.bounce(this, onTap)
        }
        quickRow.addView(qCard("📷", L.t("take_photo"), "#E0E7FF", "#1E293B", onCameraClick), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        quickRow.addView(qCard("🖼️", L.t("upload_gallery"), "#FEF3C7", "#292524", onGalleryPickClick), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        quickRow.addView(qCard("📁", L.t("my_activity"), "#FCE7F3", "#2E1A2B", onMyActivityClick), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        col.addView(quickRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(16) })

        // Category Chips
        col.addView(TextView(act).apply {
            text = "Categories"; textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(UiTheme.textMain); setPadding(0, 0, 0, dp(10))
        })
        val catRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        Samples.Cat.values().forEach { cat ->
            val active = selectedCat == cat
            val chip = TextView(act).apply {
                text = cat.title; textSize = 13f; setTypeface(null, Typeface.BOLD)
                setTextColor(if (active) Color.WHITE else UiTheme.textMain)
                background = if (active) UiTheme.grad(act, "#8B5CF6", "#6D28D9", 16) else UiTheme.round(act, UiTheme.surface, 16, UiTheme.stroke)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                UiTheme.bounce(this) { selectedCat = cat; onRefresh() }
            }
            catRow.addView(chip, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) })
        }
        col.addView(HorizontalScrollView(act).apply { isHorizontalScrollBarEnabled = false; addView(catRow) },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })

        // Offline Assets (if uploaded in assets/anime/naruto/)
        val assetFolder = when {
            selectedCat == Samples.Cat.ANIME || selectedCat == Samples.Cat.ALL -> "anime/naruto"
            selectedCat.assetFolder.isNotEmpty() -> selectedCat.assetFolder
            else -> ""
        }
        val assetPaths = Samples.listAssetPaths(act, assetFolder)
        if (assetPaths.isNotEmpty()) {
            val cardW = (act.resources.displayMetrics.widthPixels - dp(44)) / 2
            var gridRow: LinearLayout? = null
            assetPaths.forEachIndexed { idx, path ->
                if (idx % 2 == 0) {
                    gridRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
                    col.addView(gridRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })
                }
                val bmp = Samples.loadAssetBitmap(act, path, 480)
                val title = path.substringAfterLast("/").substringBeforeLast(".")
                val card = LinearLayout(act).apply {
                    orientation = LinearLayout.VERTICAL; background = UiTheme.round(act, Color.WHITE, 18, UiTheme.stroke)
                    setPadding(dp(8), dp(8), dp(8), dp(8)); elevation = dp(2).toFloat()
                    addView(ImageView(act).apply { setImageBitmap(bmp); scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(MATCH_PARENT, cardW - dp(36)))
                    addView(TextView(act).apply { text = title; textSize = 12f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER; setTextColor(Color.parseColor("#1E1B4B")); maxLines = 1 })
                    UiTheme.bounce(this) { Samples.loadAssetBitmap(act, path, 1080)?.let(onSampleClick) }
                }
                gridRow?.addView(card, LinearLayout.LayoutParams(cardW, WRAP_CONTENT).apply { if (idx % 2 == 0) marginEnd = dp(12) })
            }
        }

        // 20 Anime Series Grid (Tap any Anime -> opens all its 20-25 Characters!)
        if (selectedCat == Samples.Cat.ALL || selectedCat == Samples.Cat.ANIME) {
            col.addView(TextView(act).apply {
                text = "🔥  20 Popular Anime (Tap for Characters)"; textSize = 16f; setTypeface(null, Typeface.BOLD)
                setTextColor(UiTheme.textMain); setPadding(0, dp(4), 0, dp(10))
            })
            val cardW = (act.resources.displayMetrics.widthPixels - dp(44)) / 2
            var aRow: LinearLayout? = null
            AnimeCatalog.list.forEachIndexed { idx, series ->
                if (idx % 2 == 0) {
                    aRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
                    col.addView(aRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })
                }
                val aCard = LinearLayout(act).apply {
                    orientation = LinearLayout.VERTICAL
                    background = UiTheme.round(act, UiTheme.surface, 16, UiTheme.stroke)
                    setPadding(dp(12), dp(14), dp(12), dp(14)); elevation = dp(3).toFloat()
                    addView(TextView(act).apply { text = series.name; textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(UiTheme.textMain); maxLines = 1 })
                    addView(TextView(act).apply { text = "${series.characters.size} Characters ›"; textSize = 12f; setTextColor(UiTheme.accent); setPadding(0, dp(4), 0, 0) })
                    UiTheme.bounce(this) { onOpenAnime(series) }
                }
                aRow?.addView(aCard, LinearLayout.LayoutParams(cardW, WRAP_CONTENT).apply { if (idx % 2 == 0) marginEnd = dp(12) })
            }
        } else {
            val topics = extraTopics[selectedCat] ?: emptyList()
            topics.forEach { t ->
                val row = LinearLayout(act).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    background = UiTheme.round(act, UiTheme.surface, 16, UiTheme.stroke)
                    setPadding(dp(14), dp(14), dp(14), dp(14)); elevation = dp(2).toFloat()
                    addView(TextView(act).apply { text = "🔍  $t"; textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(UiTheme.textMain) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    addView(TextView(act).apply { text = "›"; textSize = 20f; setTextColor(UiTheme.textSub) })
                    UiTheme.bounce(this) { onBrowseOnline("$t easy line drawing") }
                }
                col.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })
            }
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

        val topBtns = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        val impBtn = Button(act).apply {
            text = "📥  ${L.t("import_photo")}"; setTextColor(Color.WHITE); isAllCaps = false; textSize = 14f; setTypeface(null, Typeface.BOLD)
            background = UiTheme.grad(act, "#3B82F6", "#2563EB", 16); elevation = dp(3).toFloat(); UiTheme.bounce(this, onImportClick)
        }
        val expBtn = Button(act).apply {
            val cnt = selectedFiles.size
            text = if (cnt > 0) "📤  ${L.t("export_zip")} ($cnt)" else "📤  ${L.t("export_zip")}"
            setTextColor(Color.WHITE); isAllCaps = false; textSize = 14f; setTypeface(null, Typeface.BOLD)
            background = UiTheme.grad(act, "#8B5CF6", "#6D28D9", 16); elevation = dp(3).toFloat()
            UiTheme.bounce(this) {
                val chosen = files.filter { selectedFiles.contains(it.name) }
                if (chosen.isEmpty()) Toast.makeText(act, L.t("select_items"), Toast.LENGTH_SHORT).show() else onExportZip(chosen)
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
                orientation = LinearLayout.VERTICAL; background = UiTheme.round(act, UiTheme.surface, 18, if (isSel) UiTheme.accent else UiTheme.stroke)
                setPadding(dp(8), dp(8), dp(8), dp(8)); elevation = dp(3).toFloat()
                val imgWrap = FrameLayout(act)
                val iv = ImageView(act).apply { setImageBitmap(bmp); scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(UiTheme.surfaceAlt) }
                UiTheme.bounce(iv) { if (bmp != null) onDraw(bmp) }
                imgWrap.addView(iv, FrameLayout.LayoutParams(MATCH_PARENT, cardSize - dp(16)))
                val selBadge = TextView(act).apply {
                    text = if (isSel) "✓" else ""; textSize = 14f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                    background = UiTheme.round(act, if (isSel) UiTheme.accent else Color.parseColor("#88000000"), 14, Color.WHITE)
                    setOnClickListener { if (selectedFiles.contains(file.name)) selectedFiles.remove(file.name) else selectedFiles.add(file.name); onRefresh() }
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
                    showCuteDeletePopup(act, savedName, bmp) {
                        file.delete()
                        selectedFiles.remove(file.name)
                        prefs.edit().remove("gname_${file.name}").apply()
                        onRefresh()
                    }
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(br)
            }
            row?.addView(card, LinearLayout.LayoutParams(cardSize, WRAP_CONTENT).apply { if (idx % 2 == 0) marginEnd = dp(12) })
        }
        return col
    }

    private fun formatRemainingTime(millis: Long): String {
        val totalMin = ((millis + 59_999L) / 60_000L).coerceAtLeast(1L)
        val hrs = totalMin / 60L
        val mins = totalMin % 60L
        return if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
    }

    fun buildStore(act: Activity, billingAds: BillingAdsManager): View {
        val dp = { v: Int -> UiTheme.dp(act, v) }
        val rootCol = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(24))
        }

        // Hero Balance & Status Banner
        val heroCard = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.grad(act, if (UiTheme.isDark) "#312E81" else "#6D28D9", if (UiTheme.isDark) "#4C1D95" else "#4F46E5", 22)
            setPadding(dp(18), dp(16), dp(18), dp(16))
            elevation = dp(6).toFloat()
        }
        val heroLeft = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        heroLeft.addView(TextView(act).apply {
            text = L.t("your_balance")
            textSize = 12f
            setTextColor(Color.parseColor("#DDD6FE"))
        })
        heroLeft.addView(TextView(act).apply {
            text = "🪙  ${billingAds.coins} ${L.t("coins_label")}"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, dp(2), 0, 0)
        })
        heroCard.addView(heroLeft, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        val remMillis = billingAds.remainingAdFreeMillis()
        val statusText = when {
            billingAds.permanentAdsRemoved -> "👑  ${L.t("adfree_status_perm")}"
            remMillis > 0L -> "⏳  ${formatRemainingTime(remMillis)}"
            else -> "🛡️  ${L.t("adfree_status_none")}"
        }
        val statusBgColor = when {
            billingAds.permanentAdsRemoved -> Color.parseColor("#10B981")
            remMillis > 0L -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#33FFFFFF")
        }
        heroCard.addView(TextView(act).apply {
            text = statusText
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (remMillis > 0L && !billingAds.permanentAdsRemoved) Color.parseColor("#1E1B4B") else Color.WHITE)
            background = UiTheme.round(act, statusBgColor, 14)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        })
        rootCol.addView(heroCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(14) })

        // Segmented Pill Switcher
        val toggleBar = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            background = UiTheme.round(act, UiTheme.surface, 22, UiTheme.stroke)
            setPadding(dp(5), dp(5), dp(5), dp(5))
            elevation = dp(2).toFloat()
        }
        val coinsTab = TextView(act).apply {
            text = "🪙  ${L.t("tab_coins")}"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(10))
        }
        val adFreeTab = TextView(act).apply {
            text = "🛡️  ${L.t("tab_adfree")}"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(10))
        }
        toggleBar.addView(coinsTab, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        toggleBar.addView(adFreeTab, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        rootCol.addView(toggleBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(16) })

        val content = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        rootCol.addView(content)

        fun renderStoreContent() {
            coinsTab.background = if (storeCoinsTab) UiTheme.grad(act, "#FBBF24", "#F59E0B", 18) else null
            coinsTab.setTextColor(if (storeCoinsTab) Color.parseColor("#1E1B4B") else UiTheme.textSub)
            adFreeTab.background = if (!storeCoinsTab) UiTheme.grad(act, "#FBBF24", "#F59E0B", 18) else null
            adFreeTab.setTextColor(if (!storeCoinsTab) Color.parseColor("#1E1B4B") else UiTheme.textSub)
            content.removeAllViews()

            if (storeCoinsTab) {
                content.addView(TextView(act).apply {
                    text = L.t("buy_coins_title")
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(UiTheme.textMain)
                })
                content.addView(TextView(act).apply {
                    text = L.t("buy_coins_sub")
                    textSize = 12f
                    setTextColor(UiTheme.textSub)
                    setPadding(0, dp(2), 0, dp(12))
                })

                val coinPacks = BillingAdsManager.PACKS.filter { !it.isRemoveAds }
                val cardW = (act.resources.displayMetrics.widthPixels - dp(40)) / 2
                var row: LinearLayout? = null
                coinPacks.forEachIndexed { idx, pack ->
                    if (idx % 2 == 0) {
                        row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
                        content.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })
                    }
                    val badgeText = when (pack.coins) {
                        500 -> L.t("pass_badge_popular")
                        10000 -> L.t("pass_badge_best")
                        else -> null
                    }
                    val card = LinearLayout(act).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        background = UiTheme.round(
                            act,
                            UiTheme.surface,
                            20,
                            if (badgeText != null) Color.parseColor("#F59E0B") else UiTheme.stroke
                        )
                        setPadding(dp(12), dp(14), dp(12), dp(12))
                        elevation = dp(3).toFloat()

                        if (badgeText != null) {
                            addView(TextView(act).apply {
                                text = badgeText
                                textSize = 10f
                                setTypeface(null, Typeface.BOLD)
                                setTextColor(Color.parseColor("#1E1B4B"))
                                background = UiTheme.round(act, Color.parseColor("#FDE047"), 10)
                                setPadding(dp(8), dp(2), dp(8), dp(2))
                            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })
                        } else {
                            addView(View(act), LinearLayout.LayoutParams(1, dp(4)))
                        }

                        addView(TextView(act).apply {
                            text = "🪙"
                            textSize = 24f
                            gravity = Gravity.CENTER
                            background = UiTheme.round(
                                act,
                                Color.parseColor(if (UiTheme.isDark) "#292524" else "#FEF3C7"),
                                24,
                                Color.parseColor("#F59E0B")
                            )
                        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { bottomMargin = dp(8) })

                        addView(TextView(act).apply {
                            text = "${pack.coins} ${L.t("coins_label")}"
                            textSize = 16f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(UiTheme.textMain)
                            maxLines = 1
                        })
                        addView(TextView(act).apply {
                            text = pack.title
                            textSize = 12f
                            setTextColor(UiTheme.textSub)
                            maxLines = 1
                            setPadding(0, dp(2), 0, dp(10))
                        })
                        addView(Button(act).apply {
                            text = pack.priceLabel
                            setTextColor(Color.WHITE)
                            textSize = 14f
                            isAllCaps = false
                            setTypeface(null, Typeface.BOLD)
                            background = UiTheme.grad(act, "#10B981", "#059669", 14)
                            elevation = dp(2).toFloat()
                            UiTheme.bounce(this) { billingAds.purchase(act, pack) }
                        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(40)))
                    }
                    row?.addView(card, LinearLayout.LayoutParams(cardW, WRAP_CONTENT).apply { if (idx % 2 == 0) marginEnd = dp(12) })
                }
            } else {
                // Timed Coin-based Ad-Free Section
                content.addView(TextView(act).apply {
                    text = "⏳  ${L.t("timed_adfree_title")}"
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(UiTheme.textMain)
                })
                content.addView(TextView(act).apply {
                    text = L.t("timed_adfree_sub")
                    textSize = 12f
                    setTextColor(UiTheme.textSub)
                    setPadding(0, dp(2), 0, dp(12))
                })

                BillingAdsManager.AD_FREE_PASSES.forEach { pass ->
                    val canAfford = billingAds.coins >= pass.coinsCost
                    val isPerm = billingAds.permanentAdsRemoved
                    val passCard = LinearLayout(act).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = UiTheme.round(
                            act,
                            UiTheme.surface,
                            18,
                            if (pass.badgeKey != null) UiTheme.accent else UiTheme.stroke
                        )
                        setPadding(dp(12), dp(12), dp(12), dp(12))
                        elevation = dp(3).toFloat()
                    }

                    passCard.addView(TextView(act).apply {
                        text = "⏱️"
                        textSize = 20f
                        gravity = Gravity.CENTER
                        background = UiTheme.round(act, UiTheme.surfaceAlt, 14)
                    }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })

                    val infoCol = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
                    val titleRow = LinearLayout(act).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    titleRow.addView(TextView(act).apply {
                        text = L.t(pass.labelKey)
                        textSize = 15f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(UiTheme.textMain)
                    })
                    if (pass.badgeKey != null) {
                        titleRow.addView(TextView(act).apply {
                            text = L.t(pass.badgeKey)
                            textSize = 10f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.WHITE)
                            background = UiTheme.grad(act, "#EC4899", "#DB2777", 10)
                            setPadding(dp(7), dp(2), dp(7), dp(2))
                        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(8) })
                    }
                    infoCol.addView(titleRow)
                    infoCol.addView(TextView(act).apply {
                        text = L.t("adfree_pass_desc")
                        textSize = 11f
                        setTextColor(UiTheme.textSub)
                        setPadding(0, dp(2), 0, 0)
                    })
                    passCard.addView(infoCol, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })

                    val actionBtn = Button(act).apply {
                        text = if (isPerm) "✓" else "🪙 ${pass.coinsCost}"
                        textSize = 13f
                        isAllCaps = false
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(
                            when {
                                isPerm -> UiTheme.textSub
                                canAfford -> Color.WHITE
                                else -> UiTheme.accent
                            }
                        )
                        background = when {
                            isPerm -> UiTheme.round(act, UiTheme.surfaceAlt, 14)
                            canAfford -> UiTheme.grad(act, "#8B5CF6", "#6D28D9", 14)
                            else -> UiTheme.round(act, UiTheme.surfaceAlt, 14, UiTheme.accent)
                        }
                        setPadding(dp(12), 0, dp(12), 0)
                        UiTheme.bounce(this) {
                            billingAds.redeemAdFreeWithCoins(pass)
                        }
                    }
                    passCard.addView(actionBtn, LinearLayout.LayoutParams(WRAP_CONTENT, dp(40)))
                    content.addView(passCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })
                }

                // Permanent Ad-Free ($499.99) VIP Card
                val adPack = BillingAdsManager.PACKS.first { it.isRemoveAds }
                val permDone = billingAds.permanentAdsRemoved
                val vipCard = LinearLayout(act).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    background = UiTheme.grad(act, "#4C1D95", "#7C3AED", 22)
                    setPadding(dp(20), dp(20), dp(20), dp(20))
                    elevation = dp(6).toFloat()

                    addView(TextView(act).apply {
                        text = "👑  LIFETIME VIP"
                        textSize = 11f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#1E1B4B"))
                        background = UiTheme.round(act, Color.parseColor("#FDE047"), 12)
                        setPadding(dp(10), dp(4), dp(10), dp(4))
                    }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })

                    addView(TextView(act).apply {
                        text = L.t("perm_adfree_title")
                        textSize = 20f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                    })
                    addView(TextView(act).apply {
                        text = L.t("perm_adfree_desc")
                        textSize = 13f
                        setTextColor(Color.parseColor("#EDE9FE"))
                        gravity = Gravity.CENTER
                        setPadding(0, dp(6), 0, dp(16))
                    })
                    addView(Button(act).apply {
                        text = if (permDone) L.t("ads_removed") else "👑  ${adPack.priceLabel} — ${L.t("unlock_forever")}"
                        setTextColor(if (permDone) Color.WHITE else Color.parseColor("#1E1B4B"))
                        textSize = 15f
                        isAllCaps = false
                        setTypeface(null, Typeface.BOLD)
                        background = if (permDone) {
                            UiTheme.round(act, Color.parseColor("#10B981"), 16)
                        } else {
                            UiTheme.grad(act, "#FDE047", "#F59E0B", 16)
                        }
                        elevation = dp(3).toFloat()
                        UiTheme.bounce(this) { if (!permDone) billingAds.purchase(act, adPack) }
                    }, LinearLayout.LayoutParams(MATCH_PARENT, dp(48)))
                }
                content.addView(vipCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
            }
        }
        UiTheme.bounce(coinsTab) { storeCoinsTab = true; renderStoreContent() }
        UiTheme.bounce(adFreeTab) { storeCoinsTab = false; renderStoreContent() }
        renderStoreContent()
        return rootCol
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
            try { act.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply { putExtra(Intent.EXTRA_SUBJECT, "Advance Drawing Studio Feedback") }) } catch (_: Exception) {}
        }
        return col
    }

    private fun showCuteDeletePopup(
        act: Activity,
        drawingName: String,
        bmp: Bitmap?,
        onConfirm: () -> Unit
    ) {
        val dp = { v: Int -> UiTheme.dp(act, v) }
        val dialog = Dialog(act)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(true)

        val card = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = UiTheme.round(act, UiTheme.surface, 26, UiTheme.stroke)
            setPadding(dp(22), dp(22), dp(22), dp(20))
            elevation = dp(14).toFloat()
        }

        val iconBox = FrameLayout(act)
        if (bmp != null) {
            val thumbWrap = FrameLayout(act).apply {
                background = UiTheme.round(act, UiTheme.surfaceAlt, 22, UiTheme.stroke)
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }
            val thumbIv = ImageView(act).apply {
                setImageBitmap(bmp)
                scaleType = ImageView.ScaleType.CENTER_CROP
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(v: View, o: Outline) {
                        o.setRoundRect(0, 0, v.width, v.height, dp(18).toFloat())
                    }
                }
                clipToOutline = true
            }
            thumbWrap.addView(thumbIv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            iconBox.addView(thumbWrap, FrameLayout.LayoutParams(dp(78), dp(78), Gravity.CENTER))
        } else {
            val softCircle = TextView(act).apply {
                text = "🎨"
                textSize = 32f
                gravity = Gravity.CENTER
                background = UiTheme.round(
                    act,
                    Color.parseColor(if (UiTheme.isDark) "#2E1A2B" else "#FFE4E6"),
                    36
                )
            }
            iconBox.addView(softCircle, FrameLayout.LayoutParams(dp(74), dp(74), Gravity.CENTER))
        }

        val badge = TextView(act).apply {
            text = "🗑️"
            textSize = 14f
            gravity = Gravity.CENTER
            background = UiTheme.grad(act, "#FB7185", "#E11D48", 16)
            elevation = dp(4).toFloat()
        }
        iconBox.addView(badge, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.BOTTOM or Gravity.END))
        card.addView(iconBox, LinearLayout.LayoutParams(dp(90), dp(88)).apply { bottomMargin = dp(14) })

        card.addView(TextView(act).apply {
            text = L.t("delete_confirm")
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textMain)
            gravity = Gravity.CENTER
        })

        card.addView(TextView(act).apply {
            text = "$drawingName — ${L.t("delete_sub")}"
            textSize = 13f
            setTextColor(UiTheme.textSub)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(18))
        })

        val btnRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        val cancelBtn = Button(act).apply {
            text = L.t("cancel")
            textSize = 14f
            isAllCaps = false
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textMain)
            background = UiTheme.round(act, UiTheme.surfaceAlt, 16, UiTheme.stroke)
            UiTheme.bounce(this) { dialog.dismiss() }
        }
        val deleteBtn = Button(act).apply {
            text = "🗑️  ${L.t("delete")}"
            textSize = 14f
            isAllCaps = false
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = UiTheme.grad(act, "#FB7185", "#E11D48", 16)
            elevation = dp(3).toFloat()
            UiTheme.bounce(this) {
                dialog.dismiss()
                onConfirm()
            }
        }
        btnRow.addView(cancelBtn, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(10) })
        btnRow.addView(deleteBtn, LinearLayout.LayoutParams(0, dp(48), 1f))
        card.addView(btnRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val dialogWidth = (act.resources.displayMetrics.widthPixels - dp(48)).coerceAtMost(dp(360))
        val container = FrameLayout(act)
        container.addView(card, FrameLayout.LayoutParams(dialogWidth, WRAP_CONTENT, Gravity.CENTER))

        dialog.setContentView(container)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.55f)
            setLayout(WRAP_CONTENT, WRAP_CONTENT)
        }
        dialog.show()

        card.scaleX = 0.82f
        card.scaleY = 0.82f
        card.alpha = 0f
        card.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(220)
            .setInterpolator(OvershootInterpolator(1.3f))
            .start()
    }

    private fun openPlayStore(act: Activity) {
        try { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${act.packageName}"))) }
        catch (_: Exception) { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${act.packageName}"))) }
    }
}
