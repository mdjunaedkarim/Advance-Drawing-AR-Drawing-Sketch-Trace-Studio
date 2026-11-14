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

    var selectedCat: Samples.Cat = Samples.Cat.ANIME
    var selectedAnimeFolder: Samples.AnimeFolder = Samples.AnimeFolder.NARUTO
    val selectedFiles = HashSet<String>()
    private var storeCoinsTab = true

    private val onlineTopics = mapOf(
        Samples.Cat.ALL to listOf(
            "Naruto easy line drawing", "Gojo Satoru line drawing", "Tanjiro Kamado line drawing",
            "Anime Girl easy drawing", "Anime Boy easy line drawing", "Sports Car outline drawing",
            "Rose flower easy line drawing", "Cute Cat line drawing"
        ),
        Samples.Cat.ANIME to listOf(
            "Naruto Uzumaki line drawing", "Sasuke Uchiha line drawing", "Itachi Uchiha line drawing",
            "Kakashi Hatake line drawing", "Gojo Satoru easy drawing", "Tanjiro Kamado line drawing",
            "Luffy One Piece line drawing", "Goku Dragon Ball line drawing", "Levi Ackerman line drawing",
            "Anime Girl cute line drawing", "Anime Boy easy line drawing", "Anime Eyes easy drawing"
        ),
        Samples.Cat.CAR to listOf(
            "Sports Car line drawing", "Lamborghini easy line drawing", "Superbike motorcycle line drawing",
            "Fighter Jet line drawing", "Passenger Airplane line drawing", "Space Rocket easy drawing"
        ),
        Samples.Cat.FOOD to listOf(
            "Pizza Slice cute line drawing", "Burger and Fries line drawing", "Ice Cream Cone line drawing",
            "Cupcake cute outline drawing", "Mango Fruit line drawing", "Pineapple easy line drawing"
        ),
        Samples.Cat.FLOWER to listOf(
            "Rose Flower easy line drawing", "Lotus Flower outline drawing", "Sunflower easy line drawing",
            "Tulip Flower line drawing", "Sakura Blossom line drawing", "Butterfly on Flower line art"
        ),
        Samples.Cat.SCENERY to listOf(
            "Mountain River Boat line drawing", "Village House scenery sketch", "Sunset Beach palm tree line art",
            "Waterfall landscape easy drawing", "Bridge over River line drawing"
        ),
        Samples.Cat.ANIMALS to listOf(
            "Cute Cat easy line drawing", "Lion Face line art drawing", "Peacock easy line drawing",
            "Parrot on branch line drawing", "Cute Puppy outline drawing", "Dragon easy line drawing"
        )
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
        col.addView(hero, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })

        // Browse Online Card (Search any Anime/Car/Photo online)
        val browseCard = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.grad(act, "#A855F7", "#7C3AED", 20); setPadding(dp(16), dp(15), dp(16), dp(15)); elevation = dp(5).toFloat()
            addView(TextView(act).apply {
                text = "🔍"; textSize = 20f; gravity = Gravity.CENTER; background = UiTheme.round(act, Color.parseColor("#33FFFFFF"), 14)
            }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })
            val tc = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
            tc.addView(TextView(act).apply { text = "Browse Online"; textSize = 17f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE) })
            tc.addView(TextView(act).apply { text = "Search & tap any drawing to auto-save & trace"; textSize = 12f; setTextColor(Color.parseColor("#F3E8FF")) })
            addView(tc, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(TextView(act).apply { text = "›"; textSize = 24f; setTextColor(Color.WHITE) })
        }
        UiTheme.bounce(browseCard) { onBrowseOnline("Anime easy line drawing") }
        col.addView(browseCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })

        // Drawing Create Card
        val createCard = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.grad(act, "#6366F1", "#4F46E5", 20); setPadding(dp(16), dp(14), dp(16), dp(14)); elevation = dp(4).toFloat()
            addView(TextView(act).apply {
                text = "✏️"; textSize = 20f; gravity = Gravity.CENTER; background = UiTheme.round(act, Color.parseColor("#33FFFFFF"), 14)
            }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })
            val tc = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
            tc.addView(TextView(act).apply { text = L.t("drawing_create"); textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE) })
            tc.addView(TextView(act).apply { text = "${L.t("take_photo")} / ${L.t("upload_gallery")}"; textSize = 12f; setTextColor(Color.parseColor("#E0E7FF")) })
            addView(tc, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(TextView(act).apply { text = "›"; textSize = 24f; setTextColor(Color.WHITE) })
        }
        UiTheme.bounce(createCard, onCreateClick)
        col.addView(createCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })

        // 3 Pastel Quick Cards
        val quickRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        fun qCard(emoji: String, label: String, lHex: String, dHex: String, onTap: () -> Unit) = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; background = UiTheme.round(act, Color.parseColor(if (UiTheme.isDark) dHex else lHex), 18)
            setPadding(dp(12), dp(12), dp(12), dp(12)); elevation = dp(2).toFloat()
            addView(TextView(act).apply { text = emoji; textSize = 22f })
            addView(TextView(act).apply {
                text = label; textSize = 12f; setTypeface(null, Typeface.BOLD); setTextColor(UiTheme.textMain)
                maxLines = 1; setPadding(0, dp(6), 0, 0)
            })
            UiTheme.bounce(this, onTap)
        }
        quickRow.addView(qCard("📷", L.t("take_photo"), "#E0E7FF", "#1E293B", onCameraClick), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        quickRow.addView(qCard("🖼️", L.t("upload_gallery"), "#FEF3C7", "#292524", onGalleryPickClick), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        quickRow.addView(qCard("📁", L.t("my_activity"), "#FCE7F3", "#2E1A2B", onMyActivityClick), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        col.addView(quickRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(16) })

        // Categories Row
        col.addView(TextView(act).apply {
            text = "Browse by Category"; textSize = 16f; setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textMain); setPadding(0, 0, 0, dp(10))
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
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })

        // Anime Sub-folders
        if (selectedCat == Samples.Cat.ANIME) {
            val subRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
            Samples.AnimeFolder.values().forEach { af ->
                val actSub = selectedAnimeFolder == af
                val subBtn = TextView(act).apply {
                    text = "📂  ${af.title}"; textSize = 12f; setTypeface(null, Typeface.BOLD)
                    setTextColor(if (actSub) Color.WHITE else UiTheme.accent)
                    background = if (actSub) UiTheme.grad(act, "#EC4899", "#DB2777", 14) else UiTheme.round(act, UiTheme.surfaceAlt, 14, UiTheme.stroke)
                    setPadding(dp(12), dp(7), dp(12), dp(7))
                    UiTheme.bounce(this) { selectedAnimeFolder = af; onRefresh() }
                }
                subRow.addView(subBtn, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) })
            }
            col.addView(HorizontalScrollView(act).apply { isHorizontalScrollBarEnabled = false; addView(subRow) },
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })
        }

        // Offline Asset Photos (e.g. uploaded Naruto folder)
        val assetFolder = when {
            selectedCat == Samples.Cat.ANIME && selectedAnimeFolder != Samples.AnimeFolder.ALL -> selectedAnimeFolder.assetFolder
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
                    addView(ImageView(act).apply { setImageBitmap(bmp); scaleType = ImageView.ScaleType.FIT_CENTER },
                        LinearLayout.LayoutParams(MATCH_PARENT, cardW - dp(36)))
                    addView(TextView(act).apply {
                        text = title; textSize = 12f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#1E1B4B")); maxLines = 1; setPadding(0, dp(4), 0, 0)
                    })
                    UiTheme.bounce(this) { Samples.loadAssetBitmap(act, path, 1080)?.let(onSampleClick) }
                }
                gridRow?.addView(card, LinearLayout.LayoutParams(cardW, WRAP_CONTENT).apply { if (idx % 2 == 0) marginEnd = dp(12) })
            }
        }

        // Popular Online Drawings to Tap & Auto-Save (like video!)
        col.addView(TextView(act).apply {
            text = "✨  More ${selectedCat.title} to Draw (Online)"; textSize = 16f; setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textMain); setPadding(0, dp(8), 0, dp(4))
        })
        col.addView(TextView(act).apply {
            text = "Tap any name → tap any photo to auto-save & start drawing"; textSize = 12f; setTextColor(UiTheme.textSub); setPadding(0, 0, 0, dp(10))
        })

        val topics = onlineTopics[selectedCat] ?: onlineTopics[Samples.Cat.ALL]!!
        topics.forEach { query ->
            val label = query.replace(" line drawing", "").replace(" easy", "").replace(" outline drawing", "")
            val row = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = UiTheme.round(act, UiTheme.surface, 16, UiTheme.stroke)
                setPadding(dp(14), dp(13), dp(14), dp(13)); elevation = dp(2).toFloat()
                addView(TextView(act).apply {
                    text = "🔍"; textSize = 15f; gravity = Gravity.CENTER
                    background = UiTheme.round(act, UiTheme.surfaceAlt, 12)
                }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(12) })
                addView(TextView(act).apply {
                    text = label; textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(UiTheme.textMain)
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(TextView(act).apply { text = "›"; textSize = 20f; setTextColor(UiTheme.textSub) })
                UiTheme.bounce(this) { onBrowseOnline(query) }
            }
            col.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })
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
        val rootCol = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(24)) }
        val toggleBar = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            background = UiTheme.round(act, Color.parseColor(if (UiTheme.isDark) "#0F172A" else "#E2E8F0"), 24, UiTheme.stroke)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val coinsTab = TextView(act).apply { text = "COINS"; textSize = 13f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10)) }
        val adFreeTab = TextView(act).apply { text = "AD-FREE"; textSize = 13f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10)) }
        toggleBar.addView(coinsTab, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        toggleBar.addView(adFreeTab, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        rootCol.addView(toggleBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(14) })

        val content = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        rootCol.addView(content)

        fun renderStoreContent() {
            coinsTab.background = if (storeCoinsTab) UiTheme.round(act, Color.parseColor("#FBBF24"), 20) else null
            coinsTab.setTextColor(if (storeCoinsTab) Color.parseColor("#1E1B4B") else UiTheme.textSub)
            adFreeTab.background = if (!storeCoinsTab) UiTheme.round(act, Color.parseColor("#FBBF24"), 20) else null
            adFreeTab.setTextColor(if (!storeCoinsTab) Color.parseColor("#1E1B4B") else UiTheme.textSub)
            content.removeAllViews()

            if (storeCoinsTab) {
                val coinPacks = BillingAdsManager.PACKS.filter { !it.isRemoveAds }
                val cardW = (act.resources.displayMetrics.widthPixels - dp(40)) / 2
                var row: LinearLayout? = null
                coinPacks.forEachIndexed { idx, pack ->
                    if (idx % 2 == 0) {
                        row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
                        content.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })
                    }
                    val card = LinearLayout(act).apply {
                        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                        background = UiTheme.round(act, Color.parseColor(if (UiTheme.isDark) "#162A4F" else "#1D4ED8"), 20)
                        setPadding(dp(12), dp(14), dp(12), dp(12)); elevation = dp(4).toFloat()
                        addView(TextView(act).apply {
                            text = "$"; textSize = 18f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
                            setTextColor(Color.parseColor("#1D4ED8")); background = UiTheme.round(act, Color.parseColor("#FACC15"), 20)
                        }, LinearLayout.LayoutParams(dp(38), dp(38)).apply { bottomMargin = dp(8) })
                        addView(TextView(act).apply { text = pack.title; textSize = 13f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE); maxLines = 1 })
                        addView(TextView(act).apply {
                            text = "${pack.coins} ${L.t("coins_label")}"; textSize = 15f; setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#FDE047")); setPadding(0, dp(4), 0, dp(10))
                        })
                        addView(Button(act).apply {
                            text = pack.priceLabel; setTextColor(Color.WHITE); textSize = 14f; isAllCaps = false; setTypeface(null, Typeface.BOLD)
                            background = UiTheme.round(act, Color.parseColor("#4CAF50"), 18)
                            UiTheme.bounce(this) { billingAds.purchase(act, pack) }
                        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(38)))
                    }
                    row?.addView(card, LinearLayout.LayoutParams(cardW, WRAP_CONTENT).apply { if (idx % 2 == 0) marginEnd = dp(12) })
                }
            } else {
                val adPack = BillingAdsManager.PACKS.first { it.isRemoveAds }
                val done = billingAds.adsRemoved
                val card = LinearLayout(act).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                    background = UiTheme.grad(act, "#6D28D9", "#4F46E5", 22); setPadding(dp(20), dp(24), dp(20), dp(20)); elevation = dp(5).toFloat()
                    addView(TextView(act).apply { text = "🛡️"; textSize = 38f })
                    addView(TextView(act).apply { text = L.t("remove_ads"); textSize = 20f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE); setPadding(0, dp(10), 0, dp(6)) })
                    addView(TextView(act).apply { text = L.t("remove_ads_desc"); textSize = 13f; setTextColor(Color.parseColor("#EDE9FE")); gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(18)) })
                    addView(Button(act).apply {
                        text = if (done) L.t("ads_removed") else adPack.priceLabel; setTextColor(Color.WHITE); textSize = 16f; isAllCaps = false; setTypeface(null, Typeface.BOLD)
                        background = UiTheme.round(act, Color.parseColor(if (done) "#10B981" else "#4CAF50"), 20)
                        UiTheme.bounce(this) { if (!done) billingAds.purchase(act, adPack) }
                    }, LinearLayout.LayoutParams(MATCH_PARENT, dp(46)))
                }
                content.addView(card)
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
            try { act.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply { putExtra(Intent.EXTRA_SUBJECT, "Free Art Center Feedback") }) } catch (_: Exception) {}
        }
        return col
    }

    private fun openPlayStore(act: Activity) {
        try { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${act.packageName}"))) }
        catch (_: Exception) { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${act.packageName}"))) }
    }
}
