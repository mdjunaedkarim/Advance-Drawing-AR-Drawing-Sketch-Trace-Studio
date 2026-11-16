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
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.animation.OvershootInterpolator
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
    private var storeCoinsTab = true

    private val extraTopics = mapOf(
        Samples.Cat.CAR to listOf("Sports Car", "Lamborghini", "Superbike", "Fighter Jet", "Airplane", "Space Rocket"),
        Samples.Cat.FOOD to listOf("Pizza Slice", "Burger", "Ice Cream", "Cupcake", "Mango", "Pineapple"),
        Samples.Cat.FLOWER to listOf("Rose Flower", "Lotus Flower", "Sunflower", "Tulip Flower", "Sakura Blossom", "Butterfly on Flower"),
        Samples.Cat.SCENERY to listOf("Mountain River Boat", "Village House", "Sunset Beach", "Waterfall Landscape", "River Bridge"),
        Samples.Cat.ANIMALS to listOf("Cute Cat", "Lion Face", "Peacock", "Parrot", "Cute Puppy", "Dragon")
    )

    fun cuteBtn(act: Activity, label: String, bg1: String, bg2: String, hDp: Int, onClick: () -> Unit) = Button(act).apply {
        text = label
        textSize = 15f
        setTypeface(null, Typeface.BOLD)
        UiTheme.styleGradientBtn(act, this, bg1, bg2, 18)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, UiTheme.dp(act, hDp)).apply {
            bottomMargin = UiTheme.dp(act, 12)
        }
        UiTheme.bounce(this, onClick)
    }

    private fun clipRounded(v: View, radiusPx: Float) {
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
            }
        }
        v.clipToOutline = true
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
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(24))
        }

        // Studio Search Bar (filters built-in sketches + anime catalog + Bing Web fallback)
        val searchBoxWrap = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.round(act, UiTheme.surface, 20, UiTheme.stroke)
            setPadding(dp(14), dp(5), dp(6), dp(5))
            elevation = dp(4).toFloat()
        }
        searchBoxWrap.addView(TextView(act).apply {
            text = "🔎"
            textSize = 15f
            setPadding(0, 0, dp(8), 0)
        })
        val searchInput = EditText(act).apply {
            hint = L.t("search_hint_all")
            setTextColor(UiTheme.textMain)
            setHintTextColor(UiTheme.textSub)
            textSize = 13.5f
            background = null
            setSingleLine(true)
        }
        val goBtn = Button(act).apply {
            text = L.t("web_btn")
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            UiTheme.styleGradientBtn(act, this, "#0EA5E9", "#2563EB", 15)
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
                val q = s?.toString()?.trim()?.lowercase() ?: ""
                if (q.isEmpty()) return

                // 1) In-App Built-in Templates Match (instant launch inside app!)
                val localHits = Samples.items.filter {
                    it.title.lowercase().contains(q) ||
                        it.series.lowercase().contains(q) ||
                        it.cat.label().lowercase().contains(q) ||
                        it.cat.name.lowercase().contains(q)
                }.take(8)
                localHits.forEach { sample ->
                    val row = LinearLayout(act).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = UiTheme.round(act, UiTheme.surface, 14, UiTheme.accent)
                        setPadding(dp(10), dp(8), dp(12), dp(8))
                        elevation = dp(2).toFloat()
                        val thumb = ImageView(act).apply {
                            Samples.loadThumb(act, sample, 120)?.let { setImageBitmap(it) }
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setBackgroundColor(Color.WHITE)
                            clipRounded(this, dp(8).toFloat())
                        }
                        addView(thumb, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(10) })
                        val tc = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
                        tc.addView(TextView(act).apply {
                            text = sample.title
                            textSize = 14f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(UiTheme.textMain)
                        })
                        tc.addView(TextView(act).apply {
                            val subTag = if (sample.series.isNotEmpty()) "${sample.series} • ${sample.cat.label()}" else "${sample.cat.label()} • ${L.t("collection_tag")}"
                            text = subTag
                            textSize = 11f
                            setTextColor(UiTheme.textSub)
                        })
                        addView(tc, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                        addView(TextView(act).apply {
                            text = "${L.t("trace_now")} →"
                            textSize = 11.5f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.WHITE)
                            background = UiTheme.grad(act, "#8B5CF6", "#6D28D9", 10)
                            setPadding(dp(10), dp(5), dp(10), dp(5))
                        })
                        UiTheme.bounce(this) { Samples.loadFull(act, sample)?.let(onSampleClick) }
                    }
                    suggestCol.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) })
                }

                // 2) Anime Catalog Match
                val hits = AnimeCatalog.searchAll(q).take(6)
                hits.forEach { (ch, anime) ->
                    val item = LinearLayout(act).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = UiTheme.round(act, UiTheme.surface, 14, UiTheme.stroke)
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                        addView(TextView(act).apply {
                            text = "🌐  $ch"
                            textSize = 14f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(UiTheme.textMain)
                        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                        addView(TextView(act).apply {
                            text = anime
                            textSize = 11f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(UiTheme.accent)
                            background = UiTheme.round(act, UiTheme.accentSoft, 8)
                            setPadding(dp(8), dp(3), dp(8), dp(3))
                        })
                        UiTheme.bounce(this) { onBrowseOnline("$ch $anime easy line drawing") }
                    }
                    suggestCol.addView(item, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(5) })
                }
            }
        })

        // Studio Hero Card with Embedded Quick Studio Controls
        val heroCard = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            background = UiTheme.grad(
                act,
                if (UiTheme.isDark) "#3B0764" else "#6D28D9",
                if (UiTheme.isDark) "#1E1B4B" else "#4338CA",
                24,
                Color.parseColor("#33FFFFFF")
            )
            setPadding(dp(18), dp(16), dp(18), dp(16))
            elevation = dp(8).toFloat()
        }
        val heroTopRow = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heroTopRow.addView(TextView(act).apply {
            text = "✨  ${L.t("studio_badge")}"
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1E1B4B"))
            background = UiTheme.grad(act, "#FDE047", "#F59E0B", 10)
            setPadding(dp(9), dp(3), dp(9), dp(3))
        })
        heroTopRow.addView(View(act), LinearLayout.LayoutParams(0, 1, 1f))
        heroTopRow.addView(TextView(act).apply {
            text = L.t("sketches_ready").replace("{n}", Samples.items.size.toString())
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#EDE9FE"))
            background = UiTheme.round(act, Color.parseColor("#2AFFFFFF"), 10)
            setPadding(dp(9), dp(3), dp(9), dp(3))
        })
        heroCard.addView(heroTopRow)

        heroCard.addView(TextView(act).apply {
            text = L.t("studio_hero_title")
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, dp(10), 0, dp(4))
        })
        heroCard.addView(TextView(act).apply {
            text = L.t("studio_hero_sub")
            textSize = 12.5f
            setTextColor(Color.parseColor("#DDD6FE"))
            setPadding(0, 0, 0, dp(14))
        })

        val createHeroBtn = Button(act).apply {
            text = "✏️  ${L.t("drawing_create")}  →"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            UiTheme.styleGradientBtn(act, this, "#EC4899", "#DB2777", 16)
            UiTheme.bounce(this, onCreateClick)
        }
        heroCard.addView(createHeroBtn, LinearLayout.LayoutParams(MATCH_PARENT, dp(48)).apply { bottomMargin = dp(10) })

        val quickPillRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        fun heroQuickBtn(label: String, onTap: () -> Unit) = Button(act).apply {
            text = label
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            UiTheme.styleSoftBtn(
                act,
                this,
                14,
                fillColor = Color.parseColor("#26FFFFFF"),
                strokeColor = Color.parseColor("#33FFFFFF"),
                textColor = Color.WHITE
            )
            UiTheme.bounce(this, onTap)
        }
        quickPillRow.addView(heroQuickBtn("📷 ${L.t("take_photo")}", onCameraClick), LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) })
        quickPillRow.addView(heroQuickBtn("🖼️ ${L.t("upload_gallery")}", onGalleryPickClick), LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) })
        quickPillRow.addView(heroQuickBtn("📁 ${L.t("my_activity")}", onMyActivityClick), LinearLayout.LayoutParams(0, dp(42), 1f))
        heroCard.addView(quickPillRow)

        col.addView(heroCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4); bottomMargin = dp(18) })

        // Categories Header & Chips
        col.addView(TextView(act).apply {
            text = L.t("ready_sketches")
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textMain)
            setPadding(0, 0, 0, dp(10))
        })

        val catRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        Samples.Cat.values().forEach { cat ->
            val active = selectedCat == cat
            val chip = TextView(act).apply {
                text = cat.label()
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (active) Color.WHITE else UiTheme.textMain)
                background = if (active) {
                    UiTheme.grad(act, "#8B5CF6", "#6D28D9", 16)
                } else {
                    UiTheme.round(act, UiTheme.surface, 16, UiTheme.stroke)
                }
                setPadding(dp(15), dp(8), dp(15), dp(8))
                elevation = if (active) dp(3).toFloat() else dp(1).toFloat()
                UiTheme.bounce(this) { selectedCat = cat; onRefresh() }
            }
            catRow.addView(chip, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) })
        }
        col.addView(
            HorizontalScrollView(act).apply {
                isHorizontalScrollBarEnabled = false
                addView(catRow)
            },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(14) }
        )

        // User-Curated Ready-to-Trace Sketches Grid
        val builtInList = if (selectedCat == Samples.Cat.ALL) {
            Samples.items
        } else {
            Samples.items.filter { it.cat == selectedCat }
        }

        val cardW = (act.resources.displayMetrics.widthPixels - dp(44)) / 2
        if (builtInList.isNotEmpty()) {
            val grouped = builtInList.groupBy { it.series.ifEmpty { it.cat.label() } }
            grouped.forEach { (groupName, groupItems) ->
                val groupHeader = LinearLayout(act).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4), 0, dp(8))
                    addView(TextView(act).apply {
                        text = "🎬  $groupName"
                        textSize = 15.5f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(UiTheme.textMain)
                    }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    addView(TextView(act).apply {
                        text = "${groupItems.size}"
                        textSize = 11.5f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.WHITE)
                        background = UiTheme.grad(act, "#8B5CF6", "#6D28D9", 10)
                        setPadding(dp(9), dp(3), dp(9), dp(3))
                    })
                }
                col.addView(groupHeader)

                var sRow: LinearLayout? = null
                groupItems.forEachIndexed { idx, item ->
                    if (idx % 2 == 0) {
                        sRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
                        col.addView(sRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })
                    }
                    val thumbBmp = Samples.loadThumb(act, item, 420)
                    val sCard = LinearLayout(act).apply {
                        orientation = LinearLayout.VERTICAL
                        background = UiTheme.round(act, UiTheme.surface, 20, UiTheme.stroke)
                        setPadding(dp(8), dp(8), dp(8), dp(10))
                        elevation = dp(4).toFloat()

                        val imgBox = FrameLayout(act).apply {
                            background = UiTheme.round(act, Color.WHITE, 14, UiTheme.stroke)
                            setPadding(dp(6), dp(6), dp(6), dp(6))
                            addView(ImageView(act).apply {
                                setImageBitmap(thumbBmp)
                                scaleType = ImageView.ScaleType.FIT_CENTER
                            }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                            addView(TextView(act).apply {
                                text = item.series.ifEmpty { item.cat.label() }
                                textSize = 9.5f
                                setTypeface(null, Typeface.BOLD)
                                setTextColor(UiTheme.accent)
                                background = UiTheme.round(act, UiTheme.accentSoft, 8)
                                setPadding(dp(7), dp(2), dp(7), dp(2))
                            }, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START))
                        }
                        addView(imgBox, LinearLayout.LayoutParams(MATCH_PARENT, cardW + dp(10)))

                        val footRow = LinearLayout(act).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(dp(2), dp(8), dp(2), 0)
                        }
                        footRow.addView(TextView(act).apply {
                            text = item.title
                            textSize = 12.5f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(UiTheme.textMain)
                            maxLines = 1
                        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
                        footRow.addView(TextView(act).apply {
                            text = "${L.t("trace_now")} →"
                            textSize = 10.5f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.WHITE)
                            background = UiTheme.grad(act, "#8B5CF6", "#6D28D9", 10)
                            setPadding(dp(8), dp(4), dp(8), dp(4))
                        })
                        addView(footRow)
                        UiTheme.bounce(this) { Samples.loadFull(act, item)?.let(onSampleClick) }
                    }
                    sRow?.addView(sCard, LinearLayout.LayoutParams(cardW, WRAP_CONTENT).apply {
                        if (idx % 2 == 0) marginEnd = dp(12)
                    })
                }
            }
        }

        // 20 Anime Series Grid
        if (selectedCat == Samples.Cat.ALL || selectedCat == Samples.Cat.ANIME) {
            col.addView(TextView(act).apply {
                text = "🔥  ${L.t("anime_worlds")}"
                textSize = 17f
                setTypeface(null, Typeface.BOLD)
                setTextColor(UiTheme.textMain)
                setPadding(0, dp(8), 0, dp(10))
            })
            var aRow: LinearLayout? = null
            AnimeCatalog.list.forEachIndexed { idx, series ->
                if (idx % 2 == 0) {
                    aRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
                    col.addView(aRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })
                }
                val bundledCount = Samples.bySeries(series.name).size
                val aCard = LinearLayout(act).apply {
                    orientation = LinearLayout.VERTICAL
                    background = UiTheme.round(
                        act,
                        UiTheme.surface,
                        18,
                        if (bundledCount > 0) UiTheme.accent else UiTheme.stroke
                    )
                    setPadding(dp(12), dp(14), dp(12), dp(14))
                    elevation = dp(3).toFloat()
                    addView(TextView(act).apply {
                        text = series.name
                        textSize = 14.5f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(UiTheme.textMain)
                        maxLines = 1
                    })
                    val badgesRow = LinearLayout(act).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    if (bundledCount > 0) {
                        badgesRow.addView(TextView(act).apply {
                            text = "🎨 $bundledCount"
                            textSize = 11f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.WHITE)
                            background = UiTheme.grad(act, "#10B981", "#059669", 8)
                            setPadding(dp(7), dp(3), dp(7), dp(3))
                        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(6) })
                    }
                    badgesRow.addView(TextView(act).apply {
                        text = L.t("characters_count").replace("{n}", series.characters.size.toString())
                        textSize = 11f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(UiTheme.accent)
                        background = UiTheme.round(act, UiTheme.accentSoft, 8)
                        setPadding(dp(8), dp(3), dp(8), dp(3))
                    })
                    addView(badgesRow, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(8) })
                    UiTheme.bounce(this) { onOpenAnime(series) }
                }
                aRow?.addView(aCard, LinearLayout.LayoutParams(cardW, WRAP_CONTENT).apply { if (idx % 2 == 0) marginEnd = dp(12) })
            }
        } else {
            val topics = extraTopics[selectedCat] ?: emptyList()
            if (topics.isNotEmpty()) {
                col.addView(TextView(act).apply {
                    text = "🌐  ${L.t("explore_more_ideas")} (${selectedCat.label()})"
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(UiTheme.textMain)
                    setPadding(0, dp(8), 0, dp(10))
                })
                topics.forEach { t ->
                    val row = LinearLayout(act).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = UiTheme.round(act, UiTheme.surface, 16, UiTheme.stroke)
                        setPadding(dp(14), dp(13), dp(14), dp(13))
                        elevation = dp(2).toFloat()
                        addView(TextView(act).apply {
                            text = "🔎  $t"
                            textSize = 14.5f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(UiTheme.textMain)
                        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                        addView(TextView(act).apply {
                            text = L.t("browse_btn")
                            textSize = 12f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(UiTheme.accent)
                        })
                        UiTheme.bounce(this) { onBrowseOnline("$t easy line drawing") }
                    }
                    col.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })
                }
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
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(24))
        }
        val files = (galleryDir.listFiles()?.toList() ?: emptyList())
            .filter { it.name.endsWith(".png") }
            .sortedByDescending { it.lastModified() }

        val topBtns = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        val impBtn = Button(act).apply {
            text = "📥  ${L.t("import_photo")}"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            UiTheme.styleGradientBtn(act, this, "#3B82F6", "#2563EB", 16)
            UiTheme.bounce(this, onImportClick)
        }
        val expBtn = Button(act).apply {
            val cnt = selectedFiles.size
            text = if (cnt > 0) "📤  ${L.t("export_zip")} ($cnt)" else "📤  ${L.t("export_zip")}"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            UiTheme.styleGradientBtn(act, this, "#8B5CF6", "#6D28D9", 16)
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
            val emptyCard = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = UiTheme.round(act, UiTheme.surface, 22, UiTheme.stroke)
                setPadding(dp(24), dp(36), dp(24), dp(32))
                elevation = dp(3).toFloat()
                addView(TextView(act).apply {
                    text = "🎨"
                    textSize = 36f
                    gravity = Gravity.CENTER
                    background = UiTheme.round(act, UiTheme.accentSoft, 34)
                }, LinearLayout.LayoutParams(dp(68), dp(68)).apply { bottomMargin = dp(14) })
                addView(TextView(act).apply {
                    text = L.t("empty_gallery")
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(UiTheme.textMain)
                    gravity = Gravity.CENTER
                })
            }
            col.addView(emptyCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(24) })
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
            val savedName = prefs.getString("gname_${file.name}", "${L.t("art_prefix")} #${files.size - idx}") ?: L.t("art_prefix")
            val isSel = selectedFiles.contains(file.name)
            val card = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                background = UiTheme.round(act, UiTheme.surface, 20, if (isSel) UiTheme.accent else UiTheme.stroke, if (isSel) 2 else 1)
                setPadding(dp(8), dp(8), dp(8), dp(10))
                elevation = dp(4).toFloat()

                val imgWrap = FrameLayout(act)
                val iv = ImageView(act).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(UiTheme.surfaceAlt)
                    clipRounded(this, dp(14).toFloat())
                }
                UiTheme.bounce(iv) { if (bmp != null) onDraw(bmp) }
                imgWrap.addView(iv, FrameLayout.LayoutParams(MATCH_PARENT, cardSize - dp(16)))
                val selBadge = TextView(act).apply {
                    text = if (isSel) "✓" else ""
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    background = UiTheme.round(act, if (isSel) UiTheme.accent else Color.parseColor("#88000000"), 14, Color.WHITE)
                    setOnClickListener {
                        if (selectedFiles.contains(file.name)) selectedFiles.remove(file.name) else selectedFiles.add(file.name)
                        onRefresh()
                    }
                }
                imgWrap.addView(selBadge, FrameLayout.LayoutParams(dp(30), dp(30), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(6), dp(6), 0) })
                addView(imgWrap)

                addView(TextView(act).apply {
                    text = savedName
                    setTextColor(UiTheme.textMain)
                    setTypeface(null, Typeface.BOLD)
                    textSize = 13.5f
                    maxLines = 1
                    setPadding(dp(2), dp(8), dp(2), dp(8))
                })

                val br = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
                fun miniBtn(t: String, tintBg: Int = UiTheme.surfaceAlt, onTap: () -> Unit) = Button(act).apply {
                    text = t
                    textSize = 13f
                    UiTheme.styleSoftBtn(act, this, 12, fillColor = tintBg, strokeColor = UiTheme.stroke, textColor = UiTheme.textMain)
                    setPadding(0, 0, 0, 0)
                    UiTheme.bounce(this, onTap)
                }
                br.addView(miniBtn("✏️") {
                    val inp = EditText(act).apply { setText(savedName); hint = L.t("rename_hint") }
                    AlertDialog.Builder(act).setTitle(L.t("rename")).setView(inp)
                        .setPositiveButton(L.t("save")) { _, _ ->
                            val nn = inp.text.toString().trim()
                            if (nn.isNotEmpty()) { prefs.edit().putString("gname_${file.name}", nn).apply(); onRefresh() }
                        }.setNegativeButton(L.t("cancel"), null).show()
                }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(5) })
                br.addView(miniBtn("🔗") {
                    try {
                        val u = FileProvider.getUriForFile(act, "${act.packageName}.fileprovider", file)
                        act.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, u)
                            putExtra(Intent.EXTRA_TEXT, "$savedName — ${L.t("share_text")}")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, L.t("share")))
                    } catch (_: Exception) {}
                }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(5) })
                br.addView(miniBtn("🗑️", Color.parseColor(if (UiTheme.isDark) "#2E1A2B" else "#FFF1F2")) {
                    showCuteDeletePopup(act, savedName, bmp) {
                        file.delete()
                        selectedFiles.remove(file.name)
                        prefs.edit().remove("gname_${file.name}").apply()
                        onRefresh()
                    }
                }, LinearLayout.LayoutParams(0, dp(38), 1f))
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
            background = UiTheme.grad(act, if (UiTheme.isDark) "#312E81" else "#6D28D9", if (UiTheme.isDark) "#4C1D95" else "#4F46E5", 22, Color.parseColor("#33FFFFFF"))
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
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(10))
        }
        val adFreeTab = TextView(act).apply {
            text = "🛡️  ${L.t("tab_adfree")}"
            textSize = 13.5f
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
                            text = L.t("coin_pack_sub").replace("{n}", pack.coins.toString())
                            textSize = 12f
                            setTextColor(UiTheme.textSub)
                            maxLines = 1
                            setPadding(0, dp(2), 0, dp(10))
                        })
                        addView(Button(act).apply {
                            text = pack.priceLabel
                            textSize = 14f
                            setTypeface(null, Typeface.BOLD)
                            UiTheme.styleGradientBtn(act, this, "#10B981", "#059669", 14)
                            UiTheme.bounce(this) { billingAds.purchase(act, pack) }
                        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(42)))
                    }
                    row?.addView(card, LinearLayout.LayoutParams(cardW, WRAP_CONTENT).apply { if (idx % 2 == 0) marginEnd = dp(12) })
                }
            } else {
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
                        setTypeface(null, Typeface.BOLD)
                        when {
                            isPerm -> UiTheme.styleSoftBtn(act, this, 14, UiTheme.surfaceAlt, UiTheme.stroke, UiTheme.textSub)
                            canAfford -> UiTheme.styleGradientBtn(act, this, "#8B5CF6", "#6D28D9", 14)
                            else -> UiTheme.styleSoftBtn(act, this, 14, UiTheme.surfaceAlt, UiTheme.accent, UiTheme.accent)
                        }
                        UiTheme.bounce(this) { billingAds.redeemAdFreeWithCoins(pass) }
                    }
                    passCard.addView(actionBtn, LinearLayout.LayoutParams(WRAP_CONTENT, dp(42)))
                    content.addView(passCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })
                }

                // Permanent Ad-Free ($499.99) VIP Card
                val adPack = BillingAdsManager.PACKS.first { it.isRemoveAds }
                val permDone = billingAds.permanentAdsRemoved
                val vipCard = LinearLayout(act).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    background = UiTheme.grad(act, "#4C1D95", "#7C3AED", 22, Color.parseColor("#44FFFFFF"))
                    setPadding(dp(20), dp(20), dp(20), dp(20))
                    elevation = dp(6).toFloat()

                    addView(TextView(act).apply {
                        text = "👑  ${L.t("lifetime_vip")}"
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
                        textSize = 15f
                        setTypeface(null, Typeface.BOLD)
                        if (permDone) {
                            UiTheme.styleGradientBtn(act, this, "#10B981", "#059669", 16)
                        } else {
                            UiTheme.styleGradientBtn(act, this, "#FDE047", "#F59E0B", 16, textColor = Color.parseColor("#1E1B4B"))
                        }
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

    fun buildSettings(
        act: Activity,
        prefs: SharedPreferences,
        onOpenLanguagePage: () -> Unit,
        onOpenPrivacyPage: () -> Unit,
        onRefresh: () -> Unit
    ): View {
        val dp = { v: Int -> UiTheme.dp(act, v) }
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(24))
        }
        val themeCard = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.round(act, UiTheme.surface, 20, UiTheme.stroke)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            elevation = dp(3).toFloat()
            addView(TextView(act).apply {
                text = if (UiTheme.isDark) "🌙" else "☀️"
                textSize = 18f
                gravity = Gravity.CENTER
                background = UiTheme.round(act, UiTheme.accentSoft, 14)
            }, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(12) })
            addView(TextView(act).apply {
                text = if (UiTheme.isDark) L.t("dark_mode") else L.t("light_mode")
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(UiTheme.textMain)
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(Switch(act).apply {
                isChecked = UiTheme.isDark
                setOnCheckedChangeListener { _, checked ->
                    UiTheme.isDark = checked
                    prefs.edit().putBoolean("dark_mode", checked).apply()
                    onRefresh()
                }
            })
        }
        col.addView(themeCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })

        fun addSetting(icon: String, title: String, onTap: () -> Unit) {
            val row = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = UiTheme.round(act, UiTheme.surface, 18, UiTheme.stroke)
                setPadding(dp(14), dp(12), dp(16), dp(12))
                elevation = dp(2).toFloat()
                addView(TextView(act).apply {
                    text = icon
                    textSize = 17f
                    gravity = Gravity.CENTER
                    background = UiTheme.round(act, UiTheme.surfaceAlt, 12)
                }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) })
                addView(TextView(act).apply {
                    text = title
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(UiTheme.textMain)
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(TextView(act).apply {
                    text = "›"
                    textSize = 22f
                    setTextColor(UiTheme.textSub)
                })
                UiTheme.bounce(this, onTap)
            }
            col.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })
        }

        val cur = L.languages.find { it.code == L.lang }?.let { "${it.flag} ${it.label} (${it.sub})" } ?: "🇺🇸 English"
        addSetting("🌐", "${L.t("language")}: $cur", onOpenLanguagePage)
        addSetting("🔒", L.t("privacy_policy")) {
            if (L.PRIVACY_URL.startsWith("http")) {
                try {
                    act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(L.PRIVACY_URL)))
                } catch (_: Exception) {
                    onOpenPrivacyPage()
                }
            } else {
                onOpenPrivacyPage()
            }
        }
        addSetting("ℹ️", L.t("about")) { openPlayStore(act) }
        addSetting("📤", L.t("share_friend")) {
            val url = "https://play.google.com/store/apps/details?id=${act.packageName}"
            (act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Link", url))
            Toast.makeText(act, L.t("link_copied"), Toast.LENGTH_SHORT).show()
            act.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, L.t("share_text"))
            }, L.t("share_friend")))
        }
        addSetting("⭐", L.t("rate_us")) { openPlayStore(act) }
        addSetting("✉️", L.t("feedback")) {
            try {
                act.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                    putExtra(Intent.EXTRA_SUBJECT, "Advance Drawing Studio Feedback")
                })
            } catch (_: Exception) {}
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
                clipRounded(this, dp(18).toFloat())
            }
            thumbWrap.addView(thumbIv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            iconBox.addView(thumbWrap, FrameLayout.LayoutParams(dp(78), dp(78), Gravity.CENTER))
        } else {
            val softCircle = TextView(act).apply {
                text = "🎨"
                textSize = 32f
                gravity = Gravity.CENTER
                background = UiTheme.round(act, Color.parseColor(if (UiTheme.isDark) "#2E1A2B" else "#FFE4E6"), 36)
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
            setTypeface(null, Typeface.BOLD)
            UiTheme.styleSoftBtn(act, this, 16)
            UiTheme.bounce(this) { dialog.dismiss() }
        }
        val deleteBtn = Button(act).apply {
            text = "🗑️  ${L.t("delete")}"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            UiTheme.styleGradientBtn(act, this, "#FB7185", "#E11D48", 16)
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
        try {
            act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${act.packageName}")))
        } catch (_: Exception) {
            act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${act.packageName}")))
        }
    }
}
