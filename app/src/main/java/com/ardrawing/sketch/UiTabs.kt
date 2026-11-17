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
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
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

    private fun catIcon(cat: Samples.Cat): StudioIcons.Type = when (cat) {
        Samples.Cat.ALL -> StudioIcons.Type.CAT_ALL
        Samples.Cat.ANIME -> StudioIcons.Type.CAT_ANIME
        Samples.Cat.CAR -> StudioIcons.Type.CAT_CAR
        Samples.Cat.FOOD -> StudioIcons.Type.CAT_FOOD
        Samples.Cat.FLOWER -> StudioIcons.Type.CAT_FLOWER
        Samples.Cat.SCENERY -> StudioIcons.Type.CAT_SCENERY
        Samples.Cat.ANIMALS -> StudioIcons.Type.CAT_ANIMALS
    }

    private fun clipRounded(v: View, radiusPx: Float) {
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
            }
        }
        v.clipToOutline = true
    }

    private fun buildTracePill(act: Activity): LinearLayout {
        val dp = { v: Int -> UiTheme.dp(act, v) }
        return StudioIcons.iconPill(
            context = act,
            iconType = StudioIcons.Type.ARROW_RIGHT,
            label = L.t("trace_now"),
            textColor = Color.WHITE,
            iconColor = Color.WHITE,
            iconSizeDp = 12,
            textSizeSp = 10.5f
        ).apply {
            background = UiTheme.grad(act, "#7C3AED", "#4F46E5", 10)
            setPadding(dp(9), dp(4), dp(9), dp(4))
        }
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
        onOpenCharacterGallery: (character: String, series: String) -> Unit,
        onRefresh: () -> Unit
    ): View {
        val dp = { v: Int -> UiTheme.dp(act, v) }
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(28))
        }

        // Studio Command Search Bar
        val searchBoxWrap = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.round(act, UiTheme.surface, 20, UiTheme.stroke)
            setPadding(dp(14), dp(6), dp(6), dp(6))
            elevation = dp(5).toFloat()
        }
        searchBoxWrap.addView(
            StudioIcons.imageView(act, StudioIcons.Type.SEARCH, 18, UiTheme.textSub),
            LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(10) }
        )
        val searchInput = EditText(act).apply {
            hint = L.t("search_hint_all")
            UiTheme.applyUiFont(this, bold = false)
            setTextColor(UiTheme.textMain)
            setHintTextColor(UiTheme.textSub)
            textSize = 13.5f
            background = null
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        val goBtn = StudioIcons.iconPill(
            context = act,
            iconType = StudioIcons.Type.GLOBE,
            label = L.t("web_btn"),
            textColor = Color.WHITE,
            iconColor = Color.WHITE,
            iconSizeDp = 14,
            textSizeSp = 12.5f
        ).apply {
            background = UiTheme.grad(act, "#0EA5E9", "#2563EB", 14)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            UiTheme.bounce(this) {
                val raw = searchInput.text.toString().trim()
                val norm = AnimeCatalog.normalizeQuery(raw).trim()
                onBrowseOnline(if (norm.isEmpty()) "Anime black and white sketch drawing" else "$norm black and white sketch drawing")
            }
        }
        searchBoxWrap.addView(searchInput, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        searchBoxWrap.addView(goBtn, LinearLayout.LayoutParams(WRAP_CONTENT, dp(38)))
        col.addView(searchBoxWrap, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })

        val suggestCol = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        col.addView(suggestCol, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val searchHandler = Handler(Looper.getMainLooper())
        var pendingSearchRunnable: Runnable? = null
        var activeQueryToken = 0

        fun addMorePhotosButton(targetQuery: String) {
            val clean = targetQuery.trim().ifEmpty { "Anime" }
            val moreBtn = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                background = UiTheme.grad(act, "#0EA5E9", "#2563EB", 15)
                setPadding(dp(14), dp(11), dp(14), dp(11))
                elevation = dp(4).toFloat()
                addView(
                    StudioIcons.imageView(act, StudioIcons.Type.GLOBE, 16, Color.WHITE),
                    LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(8) }
                )
                addView(TextView(act).apply {
                    text = L.t("more_photos")
                    textSize = 13.5f
                    UiTheme.applyDisplayFont(this, bold = true)
                    setTextColor(Color.WHITE)
                })
                addView(
                    StudioIcons.imageView(act, StudioIcons.Type.ARROW_RIGHT, 14, Color.WHITE),
                    LinearLayout.LayoutParams(dp(14), dp(14)).apply { marginStart = dp(8) }
                )
                UiTheme.bounce(this) {
                    onBrowseOnline("$clean black and white sketch drawing")
                }
            }
            suggestCol.addView(moreBtn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(2)
                bottomMargin = dp(10)
            })
        }

        fun addSketchResultRow(item: SketchSearchRepository.SketchResult, seriesLabel: String = "") {
            val row = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = UiTheme.round(act, UiTheme.surface, 16, UiTheme.accentBright)
                setPadding(dp(10), dp(8), dp(12), dp(8))
                elevation = dp(3).toFloat()

                val thumbFrame = FrameLayout(act).apply {
                    background = UiTheme.round(act, Color.WHITE, 10, UiTheme.stroke)
                    setPadding(dp(3), dp(3), dp(3), dp(3))
                    val thumb = ImageView(act).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        clipRounded(this, dp(8).toFloat())
                    }
                    addView(thumb, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                    val loadBar = ProgressBar(act).apply { isIndeterminate = true }
                    addView(loadBar, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
                    SketchSearchRepository.loadThumbBitmap(act, item) { bmp ->
                        loadBar.visibility = View.GONE
                        if (bmp != null) thumb.setImageBitmap(bmp)
                    }
                }
                addView(thumbFrame, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(10) })

                val tc = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
                tc.addView(TextView(act).apply {
                    text = item.title
                    textSize = 13.5f
                    UiTheme.applyDisplayFont(this, bold = true)
                    setTextColor(UiTheme.textMain)
                    maxLines = 1
                })
                val subRow = LinearLayout(act).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(2), 0, 0)
                }
                subRow.addView(TextView(act).apply {
                    text = item.mood
                    textSize = 10f
                    UiTheme.applyUiFont(this, bold = true)
                    setTextColor(UiTheme.accentBright)
                    background = UiTheme.round(act, UiTheme.accentSoft, 6)
                    setPadding(dp(6), dp(1), dp(6), dp(1))
                }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(6) })
                val seriesText = item.localSample?.series?.ifEmpty { item.localSample.cat.label() }
                    ?: seriesLabel.ifEmpty { "B&W Sketch" }
                subRow.addView(TextView(act).apply {
                    text = seriesText
                    textSize = 11f
                    UiTheme.applyUiFont(this)
                    setTextColor(UiTheme.textSub)
                    maxLines = 1
                })
                tc.addView(subRow)
                addView(tc, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
                addView(buildTracePill(act))

                UiTheme.bounce(this) {
                    val local = item.localSample
                    if (local != null) {
                        Samples.loadFull(act, local)?.let(onSampleClick)
                    } else {
                        Toast.makeText(act, L.t("saving_image"), Toast.LENGTH_SHORT).show()
                        SketchSearchRepository.loadFullForEditor(act, item) { bmp ->
                            if (bmp != null) onSampleClick(bmp)
                            else Toast.makeText(act, L.t("no_sketches_found"), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            suggestCol.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) })
        }

        fun addCatalogChips(hits: List<Pair<String, String>>) {
            hits.take(3).forEach { (ch, anime) ->
                val item = LinearLayout(act).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = UiTheme.round(act, UiTheme.surface, 14, UiTheme.stroke)
                    setPadding(dp(12), dp(9), dp(12), dp(9))
                    val well = FrameLayout(act).apply {
                        background = UiTheme.round(act, UiTheme.accentSoft, 9)
                        addView(
                            StudioIcons.imageView(act, StudioIcons.Type.ANIME_STAR, 15, UiTheme.accentBright),
                            FrameLayout.LayoutParams(dp(15), dp(15), Gravity.CENTER)
                        )
                    }
                    addView(well, LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(10) })
                    addView(TextView(act).apply {
                        text = ch
                        textSize = 13.5f
                        UiTheme.applyUiFont(this, bold = true)
                        setTextColor(UiTheme.textMain)
                    }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    addView(TextView(act).apply {
                        text = anime
                        textSize = 11f
                        UiTheme.applyUiFont(this, bold = true)
                        setTextColor(UiTheme.accentBright)
                        background = UiTheme.round(act, UiTheme.accentSoft, 8)
                        setPadding(dp(8), dp(4), dp(8), dp(4))
                    })
                    UiTheme.bounce(this) { onOpenCharacterGallery(ch, anime) }
                }
                suggestCol.addView(item, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(5) })
            }
        }

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val raw = searchInput.text.toString().trim()
                val norm = AnimeCatalog.normalizeQuery(raw).trim()
                if (norm.isNotEmpty()) {
                    val topHit = AnimeCatalog.searchAll(norm).firstOrNull()
                    onOpenCharacterGallery(topHit?.first ?: norm, topHit?.second ?: "")
                }
                true
            } else false
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                pendingSearchRunnable?.let { searchHandler.removeCallbacks(it) }
                val token = ++activeQueryToken
                suggestCol.removeAllViews()

                val rawQ = s?.toString()?.trim() ?: ""
                if (rawQ.isEmpty()) return

                val normQ = AnimeCatalog.normalizeQuery(rawQ).trim()
                val q = normQ.lowercase()
                val catHits = AnimeCatalog.searchAll(normQ)

                val directHits = Samples.items.filter {
                    it.character.lowercase().contains(q) ||
                        it.title.lowercase().contains(q)
                }
                val bestLocalChar = directHits
                    .sortedWith(
                        compareByDescending<Samples.Item> { it.character.equals(normQ, ignoreCase = true) }
                            .thenByDescending { it.character.lowercase().startsWith(q) }
                            .thenByDescending { it.character.lowercase().split(" ").any { w -> w.startsWith(q) } }
                    )
                    .firstOrNull()
                    ?.character
                    ?.takeIf { it.isNotEmpty() }

                val prioritizedLocal = if (bestLocalChar != null) {
                    Samples.byCharacter(bestLocalChar)
                } else {
                    emptyList()
                }

                if (prioritizedLocal.isNotEmpty()) {
                    val fiveLocal = SketchSearchRepository.pickDiverseLocalFive(prioritizedLocal)
                    fiveLocal.forEach { addSketchResultRow(it) }
                    val targetMore = catHits.firstOrNull()?.let { "${it.first} ${it.second}" }
                        ?: bestLocalChar
                        ?: normQ
                    addMorePhotosButton(targetMore)
                    val remainingCatalog = catHits.filter { (ch, _) ->
                        !ch.equals(bestLocalChar, ignoreCase = true)
                    }
                    addCatalogChips(remainingCatalog)
                    return
                }

                // Auto-search strictly 5 B&W sketches from web for any anime character or topic
                val targetChar = catHits.firstOrNull()?.first ?: normQ
                val targetSeries = catHits.firstOrNull()?.second ?: ""

                addCatalogChips(catHits)
                val moreQuery = SketchSearchRepository.cleanSearchQuery(targetChar, targetSeries)
                if (q.length < 2) {
                    addMorePhotosButton(moreQuery)
                    return
                }

                val loadingRow = LinearLayout(act).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = UiTheme.round(act, UiTheme.surface, 16, UiTheme.stroke)
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    addView(ProgressBar(act).apply { isIndeterminate = true }, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(10) })
                    addView(TextView(act).apply {
                        text = L.t("searching_sketches")
                        textSize = 13f
                        UiTheme.applyUiFont(this, bold = true)
                        setTextColor(UiTheme.textSub)
                    })
                }
                suggestCol.addView(loadingRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) })
                addMorePhotosButton(moreQuery)

                val runnable = Runnable {
                    SketchSearchRepository.getFiveSketches(targetChar, targetSeries) { results ->
                        if (token != activeQueryToken) return@getFiveSketches
                        suggestCol.removeAllViews()
                        addCatalogChips(catHits)
                        if (results.isEmpty()) {
                            suggestCol.addView(TextView(act).apply {
                                text = L.t("no_sketches_found")
                                textSize = 12.5f
                                UiTheme.applyUiFont(this)
                                setTextColor(UiTheme.textSub)
                                background = UiTheme.round(act, UiTheme.surface, 14, UiTheme.stroke)
                                setPadding(dp(12), dp(10), dp(12), dp(10))
                            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) })
                        } else {
                            results.take(5).forEach { addSketchResultRow(it, targetSeries) }
                        }
                        addMorePhotosButton(moreQuery)
                    }
                }
                pendingSearchRunnable = runnable
                searchHandler.postDelayed(runnable, 380)
            }
        })

        // Flagship Atelier Hero Card with Asymmetric AR Projection Illustration
        val heroFrame = FrameLayout(act).apply {
            background = UiTheme.grad(
                act,
                if (UiTheme.isDark) "#16123B" else "#312E81",
                if (UiTheme.isDark) "#2E1065" else "#6D28D9",
                26,
                Color.parseColor("#3DFFFFFF")
            )
            elevation = dp(10).toFloat()
            clipRounded(this, dp(26).toFloat())
        }
        heroFrame.addView(
            StudioIcons.HeroStudioBackdropView(act),
            FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        )
        heroFrame.addView(
            StudioIcons.HeroProjectionArtView(act),
            FrameLayout.LayoutParams(dp(150), dp(150), Gravity.TOP or Gravity.END).apply {
                setMargins(0, dp(22), dp(2), 0)
            }
        )

        val heroCard = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        val heroTopRow = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val studioBadge = StudioIcons.iconPill(
            context = act,
            iconType = StudioIcons.Type.SPARK,
            label = L.t("studio_badge"),
            textColor = Color.parseColor("#1E1B4B"),
            iconColor = Color.parseColor("#1E1B4B"),
            iconSizeDp = 12,
            textSizeSp = 10.5f
        ).apply {
            background = UiTheme.grad(act, "#FDE047", "#F59E0B", 10)
            setPadding(dp(9), dp(4), dp(9), dp(4))
        }
        heroTopRow.addView(studioBadge)
        heroTopRow.addView(View(act), LinearLayout.LayoutParams(0, 1, 1f))

        val readyChip = StudioIcons.iconPill(
            context = act,
            iconType = StudioIcons.Type.COLLECTION,
            label = L.t("sketches_ready").replace("{n}", Samples.items.size.toString()),
            textColor = Color.parseColor("#EDE9FE"),
            iconColor = Color.parseColor("#C4B5FD"),
            iconSizeDp = 12,
            textSizeSp = 11f
        ).apply {
            background = UiTheme.round(act, Color.parseColor("#2AFFFFFF"), 10, Color.parseColor("#33FFFFFF"))
            setPadding(dp(9), dp(4), dp(9), dp(4))
        }
        heroTopRow.addView(readyChip)
        heroCard.addView(heroTopRow)

        heroCard.addView(TextView(act).apply {
            text = L.t("studio_hero_title")
            textSize = 23f
            UiTheme.applyDisplayFont(this, bold = true)
            setTextColor(Color.WHITE)
            setPadding(0, dp(14), dp(84), dp(4))
        })
        heroCard.addView(TextView(act).apply {
            text = L.t("studio_hero_sub")
            textSize = 13f
            UiTheme.applyUiFont(this)
            setTextColor(Color.parseColor("#DDD6FE"))
            setPadding(0, 0, dp(72), dp(16))
        })

        val createHeroBtn = UiTheme.studioGradientAction(
            act = act,
            icon = StudioIcons.Type.CREATE_PEN,
            label = L.t("drawing_create"),
            hex1 = "#EC4899",
            hex2 = "#DB2777",
            rDp = 16,
            trailingIcon = StudioIcons.Type.ARROW_RIGHT,
            iconSizeDp = 18,
            textSizeSp = 15f,
            onClick = onCreateClick
        )
        heroCard.addView(createHeroBtn, LinearLayout.LayoutParams(MATCH_PARENT, dp(52)).apply { bottomMargin = dp(12) })

        val quickTileRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        fun heroTile(icon: StudioIcons.Type, label: String, onTap: () -> Unit): LinearLayout {
            return LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = UiTheme.round(act, Color.parseColor("#1DFFFFFF"), 16, Color.parseColor("#33FFFFFF"))
                setPadding(dp(8), dp(10), dp(8), dp(10))
                val well = FrameLayout(act).apply {
                    background = UiTheme.round(act, Color.parseColor("#2EFFFFFF"), 11)
                    addView(
                        StudioIcons.imageView(act, icon, 18, Color.WHITE),
                        FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER)
                    )
                }
                addView(well, LinearLayout.LayoutParams(dp(34), dp(34)).apply { bottomMargin = dp(6) })
                addView(TextView(act).apply {
                    text = label
                    textSize = 11.5f
                    UiTheme.applyUiFont(this, bold = true)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    maxLines = 1
                })
                UiTheme.bounce(this, onTap)
            }
        }
        quickTileRow.addView(heroTile(StudioIcons.Type.CAMERA, L.t("take_photo"), onCameraClick), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        quickTileRow.addView(heroTile(StudioIcons.Type.PHOTO, L.t("upload_gallery"), onGalleryPickClick), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        quickTileRow.addView(heroTile(StudioIcons.Type.FOLDER, L.t("my_activity"), onMyActivityClick), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        heroCard.addView(quickTileRow)

        heroFrame.addView(heroCard, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        col.addView(heroFrame, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4); bottomMargin = dp(14) })

        // Studio Workflow Mini Pipeline Bar (Interactive Guided Flow)
        val pipeRow = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.round(act, UiTheme.surface, 18, UiTheme.stroke)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            elevation = dp(2).toFloat()
        }
        fun pipeStep(num: String, icon: StudioIcons.Type, label: String, onTap: () -> Unit): LinearLayout {
            return LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                background = UiTheme.round(act, UiTheme.surfaceAlt, 12)
                setPadding(dp(7), dp(7), dp(7), dp(7))
                addView(TextView(act).apply {
                    text = num
                    fontFeatureSettings = "tnum"
                    textSize = 10f
                    UiTheme.applyDisplayFont(this, bold = true)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    background = UiTheme.grad(act, "#7C3AED", "#4F46E5", 7)
                }, LinearLayout.LayoutParams(dp(19), dp(19)).apply { marginEnd = dp(5) })
                addView(
                    StudioIcons.imageView(act, icon, 13, UiTheme.accentBright),
                    LinearLayout.LayoutParams(dp(13), dp(13)).apply { marginEnd = dp(4) }
                )
                addView(TextView(act).apply {
                    text = label
                    textSize = 10.5f
                    UiTheme.applyUiFont(this, bold = true)
                    setTextColor(UiTheme.textMain)
                    maxLines = 1
                })
                UiTheme.bounce(this, onTap)
            }
        }
        pipeRow.addView(pipeStep("1", StudioIcons.Type.PHOTO, L.t("upload_gallery"), onGalleryPickClick), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        pipeRow.addView(
            StudioIcons.imageView(act, StudioIcons.Type.CHEVRON_RIGHT, 12, UiTheme.textSub),
            LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginStart = dp(3); marginEnd = dp(3) }
        )
        pipeRow.addView(pipeStep("2", StudioIcons.Type.CROP, L.t("crop_resize"), onCreateClick), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        pipeRow.addView(
            StudioIcons.imageView(act, StudioIcons.Type.CHEVRON_RIGHT, 12, UiTheme.textSub),
            LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginStart = dp(3); marginEnd = dp(3) }
        )
        pipeRow.addView(pipeStep("3", StudioIcons.Type.LOCK, L.t("trace_now"), onCreateClick), LinearLayout.LayoutParams(0, WRAP_CONTENT, 0.85f))
        col.addView(pipeRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(18) })

        // Categories Strip
        col.addView(
            UiTheme.sectionHeader(
                act = act,
                icon = StudioIcons.Type.COLLECTION,
                title = L.t("ready_sketches"),
                badgeText = "${Samples.items.size}",
                c1 = "#7C3AED",
                c2 = "#4F46E5"
            )
        )

        val catRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        Samples.Cat.values().forEach { cat ->
            val active = selectedCat == cat
            val chip = StudioIcons.iconPill(
                context = act,
                iconType = catIcon(cat),
                label = cat.label(),
                textColor = if (active) Color.WHITE else UiTheme.textMain,
                iconColor = if (active) Color.WHITE else UiTheme.accentBright,
                iconSizeDp = 15,
                textSizeSp = 13f
            ).apply {
                background = if (active) {
                    UiTheme.grad(act, "#7C3AED", "#4F46E5", 16)
                } else {
                    UiTheme.round(act, UiTheme.surface, 16, UiTheme.stroke)
                }
                setPadding(dp(13), dp(9), dp(14), dp(9))
                elevation = if (active) dp(4).toFloat() else dp(1).toFloat()
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

        val builtInList = if (selectedCat == Samples.Cat.ALL) {
            Samples.items
        } else {
            Samples.items.filter { it.cat == selectedCat }
        }

        // Featured Horizontal Spotlight Carousel (1 flagship pose per top viral character)
        if ((selectedCat == Samples.Cat.ALL || selectedCat == Samples.Cat.ANIME) && builtInList.size > 3) {
            val spotRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
            val spotlightItems = builtInList
                .distinctBy { it.character.ifEmpty { it.id.toString() } }
                .take(10)
            spotlightItems.forEach { sample ->
                val thumbBmp = Samples.loadThumb(act, sample, 320)
                val spotCard = LinearLayout(act).apply {
                    orientation = LinearLayout.VERTICAL
                    background = UiTheme.round(act, UiTheme.surface, 20, UiTheme.stroke)
                    setPadding(dp(9), dp(9), dp(9), dp(11))
                    elevation = dp(4).toFloat()
                    val imgFrame = FrameLayout(act).apply {
                        background = UiTheme.round(act, Color.WHITE, 14, UiTheme.stroke)
                        setPadding(dp(6), dp(6), dp(6), dp(6))
                        addView(ImageView(act).apply {
                            setImageBitmap(thumbBmp)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                        addView(TextView(act).apply {
                            text = sample.series.ifEmpty { sample.cat.label() }
                            textSize = 9f
                            UiTheme.applyUiFont(this, bold = true)
                            setTextColor(Color.WHITE)
                            background = UiTheme.grad(act, "#EC4899", "#DB2777", 7)
                            setPadding(dp(6), dp(2), dp(6), dp(2))
                        }, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START))
                    }
                    addView(imgFrame, LinearLayout.LayoutParams(MATCH_PARENT, dp(122)))
                    addView(TextView(act).apply {
                        text = sample.character.ifEmpty { sample.title }
                        textSize = 12.5f
                        UiTheme.applyDisplayFont(this, bold = true)
                        setTextColor(UiTheme.textMain)
                        maxLines = 1
                        setPadding(dp(2), dp(8), dp(2), dp(6))
                    })
                    addView(buildTracePill(act), LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
                    UiTheme.bounce(this) { Samples.loadFull(act, sample)?.let(onSampleClick) }
                }
                spotRow.addView(spotCard, LinearLayout.LayoutParams(dp(146), WRAP_CONTENT).apply { marginEnd = dp(10) })
            }
            col.addView(
                HorizontalScrollView(act).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(spotRow)
                },
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(16) }
            )
        }

        // Grouped Built-in Viral Character Showcase Grid (1 card per hero -> opens 5 sketches)
        val cardW = (act.resources.displayMetrics.widthPixels - dp(44)) / 2
        if (builtInList.isNotEmpty()) {
            val grouped = builtInList.groupBy { it.series.ifEmpty { it.cat.label() } }
            grouped.forEach { (groupName, groupItems) ->
                val heroShowcase = groupItems
                    .distinctBy { it.character.ifEmpty { it.id.toString() } }
                    .take(10)
                val groupHeader = UiTheme.sectionHeader(
                    act = act,
                    icon = StudioIcons.Type.SPARK,
                    title = groupName,
                    badgeText = "${groupItems.size}",
                    c1 = "#EC4899",
                    c2 = "#DB2777"
                )
                col.addView(groupHeader)

                var sRow: LinearLayout? = null
                heroShowcase.forEachIndexed { idx, item ->
                    if (idx % 2 == 0) {
                        sRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
                        col.addView(sRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })
                    }
                    val thumbBmp = Samples.loadThumb(act, item, 360)
                    val charName = item.character.ifEmpty { item.title }
                    val charCount = Samples.byCharacter(charName, item.series).size.coerceAtLeast(1)
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
                                UiTheme.applyUiFont(this, bold = true)
                                setTextColor(UiTheme.accent)
                                background = UiTheme.round(act, UiTheme.accentSoft, 8)
                                setPadding(dp(7), dp(2), dp(7), dp(2))
                            }, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START))
                            addView(TextView(act).apply {
                                text = "$charCount"
                                fontFeatureSettings = "tnum"
                                textSize = 10f
                                UiTheme.applyDisplayFont(this, bold = true)
                                setTextColor(Color.WHITE)
                                background = UiTheme.grad(act, "#7C3AED", "#4F46E5", 8)
                                setPadding(dp(7), dp(2), dp(7), dp(2))
                            }, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END))
                        }
                        addView(imgBox, LinearLayout.LayoutParams(MATCH_PARENT, cardW + dp(10)))

                        val footRow = LinearLayout(act).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(dp(2), dp(8), dp(2), 0)
                        }
                        footRow.addView(TextView(act).apply {
                            text = charName
                            textSize = 12.5f
                            UiTheme.applyDisplayFont(this, bold = true)
                            setTextColor(UiTheme.textMain)
                            maxLines = 1
                        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
                        footRow.addView(buildTracePill(act))
                        addView(footRow)
                        UiTheme.bounce(this) {
                            if (item.character.isNotEmpty()) {
                                onOpenCharacterGallery(item.character, item.series)
                            } else {
                                Samples.loadFull(act, item)?.let(onSampleClick)
                            }
                        }
                    }
                    sRow?.addView(sCard, LinearLayout.LayoutParams(cardW, WRAP_CONTENT).apply {
                        if (idx % 2 == 0) marginEnd = dp(12)
                    })
                }
            }
        }

        // 20 Popular Anime Worlds Grid with Geometric Vector Crest Watermarks
        if (selectedCat == Samples.Cat.ALL || selectedCat == Samples.Cat.ANIME) {
            col.addView(
                UiTheme.sectionHeader(
                    act = act,
                    icon = StudioIcons.Type.ANIME_STAR,
                    title = L.t("anime_worlds"),
                    badgeText = "${AnimeCatalog.list.size}",
                    c1 = "#F97316",
                    c2 = "#EA580C"
                ).apply { setPadding(0, dp(10), 0, dp(10)) }
            )
            var aRow: LinearLayout? = null
            AnimeCatalog.list.forEachIndexed { idx, series ->
                if (idx % 2 == 0) {
                    aRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
                    col.addView(aRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })
                }
                val bundledCount = Samples.bySeries(series.name).size
                val (c1, c2) = UiTheme.seriesPalette(idx)
                val aCardWrap = FrameLayout(act).apply {
                    background = UiTheme.round(
                        act,
                        UiTheme.surface,
                        18,
                        if (bundledCount > 0) UiTheme.accentBright else UiTheme.stroke,
                        if (bundledCount > 0) 2 else 1
                    )
                    elevation = dp(4).toFloat()
                    clipRounded(this, dp(18).toFloat())
                }
                aCardWrap.addView(
                    StudioIcons.AnimeWorldCrestView(act, c1, c2, idx),
                    FrameLayout.LayoutParams(dp(72), dp(72), Gravity.TOP or Gravity.END)
                )

                val aCard = LinearLayout(act).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(12), dp(12), dp(13))

                    addView(
                        View(act).apply { background = UiTheme.grad(act, c1, c2, 4) },
                        LinearLayout.LayoutParams(dp(34), dp(4)).apply { bottomMargin = dp(10) }
                    )

                    addView(TextView(act).apply {
                        text = series.name
                        textSize = 15f
                        UiTheme.applyDisplayFont(this, bold = true)
                        setTextColor(UiTheme.textMain)
                        maxLines = 1
                    })
                    val badgesRow = LinearLayout(act).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    if (bundledCount > 0) {
                        val bPill = StudioIcons.iconPill(
                            context = act,
                            iconType = StudioIcons.Type.COLLECTION,
                            label = "$bundledCount",
                            textColor = Color.WHITE,
                            iconColor = Color.WHITE,
                            iconSizeDp = 11,
                            textSizeSp = 11f
                        ).apply {
                            background = UiTheme.grad(act, "#10B981", "#059669", 8)
                            setPadding(dp(7), dp(3), dp(7), dp(3))
                        }
                        badgesRow.addView(bPill, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(6) })
                    }
                    val charPill = StudioIcons.iconPill(
                        context = act,
                        iconType = StudioIcons.Type.CHEVRON_RIGHT,
                        label = L.t("characters_count").replace("{n}", series.characters.size.toString()).replace("›", "").trim(),
                        textColor = UiTheme.accentBright,
                        iconColor = UiTheme.accentBright,
                        iconSizeDp = 12,
                        textSizeSp = 11f
                    ).apply {
                        background = UiTheme.round(act, UiTheme.accentSoft, 8)
                        setPadding(dp(7), dp(3), dp(7), dp(3))
                    }
                    badgesRow.addView(charPill)
                    addView(badgesRow, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(8) })
                }
                aCardWrap.addView(aCard, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
                UiTheme.bounce(aCardWrap) { onOpenAnime(series) }
                aRow?.addView(aCardWrap, LinearLayout.LayoutParams(cardW, WRAP_CONTENT).apply { if (idx % 2 == 0) marginEnd = dp(12) })
            }
        } else {
            val topics = extraTopics[selectedCat] ?: emptyList()
            if (topics.isNotEmpty()) {
                col.addView(
                    UiTheme.sectionHeader(
                        act = act,
                        icon = StudioIcons.Type.COMPASS,
                        title = "${L.t("explore_more_ideas")} (${selectedCat.label()})",
                        c1 = "#0EA5E9",
                        c2 = "#2563EB"
                    ).apply { setPadding(0, dp(8), 0, dp(10)) }
                )
                topics.forEach { t ->
                    val row = LinearLayout(act).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = UiTheme.round(act, UiTheme.surface, 16, UiTheme.stroke)
                        setPadding(dp(14), dp(12), dp(14), dp(12))
                        elevation = dp(2).toFloat()
                        val well = FrameLayout(act).apply {
                            background = UiTheme.round(act, UiTheme.accentSoft, 10)
                            addView(
                                StudioIcons.imageView(act, StudioIcons.Type.SEARCH, 16, UiTheme.accentBright),
                                FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER)
                            )
                        }
                        addView(well, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(12) })
                        addView(TextView(act).apply {
                            text = t
                            textSize = 14.5f
                            UiTheme.applyUiFont(this, bold = true)
                            setTextColor(UiTheme.textMain)
                        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                        addView(
                            StudioIcons.iconPill(
                                context = act,
                                iconType = StudioIcons.Type.CHEVRON_RIGHT,
                                label = L.t("browse_btn").replace("›", "").trim(),
                                textColor = UiTheme.accentBright,
                                iconColor = UiTheme.accentBright,
                                iconSizeDp = 13,
                                textSizeSp = 12f
                            )
                        )
                        UiTheme.bounce(this) { onBrowseOnline("$t black and white sketch drawing") }
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
            setPadding(dp(14), dp(14), dp(14), dp(28))
        }
        val files = (galleryDir.listFiles()?.toList() ?: emptyList())
            .filter { it.name.endsWith(".png") }
            .sortedByDescending { it.lastModified() }

        val topBtns = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        val impBtn = UiTheme.studioGradientAction(
            act = act,
            icon = StudioIcons.Type.IMPORT,
            label = L.t("import_photo"),
            hex1 = "#3B82F6",
            hex2 = "#2563EB",
            rDp = 16,
            textSizeSp = 14f,
            onClick = onImportClick
        )
        val cnt = selectedFiles.size
        val expLabel = if (cnt > 0) "${L.t("export_zip")} ($cnt)" else L.t("export_zip")
        val expBtn = UiTheme.studioGradientAction(
            act = act,
            icon = StudioIcons.Type.EXPORT_ZIP,
            label = expLabel,
            hex1 = "#7C3AED",
            hex2 = "#4F46E5",
            rDp = 16,
            textSizeSp = 14f
        ) {
            val chosen = files.filter { selectedFiles.contains(it.name) }
            if (chosen.isEmpty()) Toast.makeText(act, L.t("select_items"), Toast.LENGTH_SHORT).show()
            else onExportZip(chosen)
        }
        topBtns.addView(impBtn, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        topBtns.addView(expBtn, LinearLayout.LayoutParams(0, dp(48), 1f))
        col.addView(topBtns, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(14) })

        if (files.isEmpty()) {
            val emptyCard = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = UiTheme.round(act, UiTheme.surface, 24, UiTheme.stroke)
                setPadding(dp(24), dp(36), dp(24), dp(34))
                elevation = dp(4).toFloat()
                addView(
                    StudioIcons.EmptyGalleryArtView(act),
                    LinearLayout.LayoutParams(dp(104), dp(104)).apply { bottomMargin = dp(14) }
                )
                addView(TextView(act).apply {
                    text = L.t("empty_gallery")
                    textSize = 14.5f
                    UiTheme.applyDisplayFont(this, bold = true)
                    setTextColor(UiTheme.textMain)
                    gravity = Gravity.CENTER
                })
            }
            col.addView(emptyCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(20) })
            return col
        }

        col.addView(
            UiTheme.sectionHeader(
                act = act,
                icon = StudioIcons.Type.GALLERY,
                title = L.t("my_activity"),
                badgeText = "${files.size}",
                c1 = "#3B82F6",
                c2 = "#2563EB"
            )
        )

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
                background = UiTheme.round(act, UiTheme.surface, 20, if (isSel) UiTheme.accentBright else UiTheme.stroke, if (isSel) 2 else 1)
                setPadding(dp(8), dp(8), dp(8), dp(10))
                elevation = dp(4).toFloat()

                val imgWrap = FrameLayout(act)
                val iv = ImageView(act).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    background = UiTheme.round(act, Color.WHITE, 14, UiTheme.stroke)
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                    clipRounded(this, dp(14).toFloat())
                }
                UiTheme.bounce(iv) { if (bmp != null) onDraw(bmp) }
                imgWrap.addView(iv, FrameLayout.LayoutParams(MATCH_PARENT, cardSize - dp(16)))
                val selBadge = FrameLayout(act).apply {
                    background = UiTheme.round(act, if (isSel) UiTheme.accentBright else Color.parseColor("#88000000"), 15, Color.WHITE)
                    if (isSel) {
                        addView(
                            StudioIcons.imageView(act, StudioIcons.Type.CHECK, 16, Color.WHITE),
                            FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER)
                        )
                    }
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
                    UiTheme.applyDisplayFont(this, bold = true)
                    textSize = 13.5f
                    maxLines = 1
                    setPadding(dp(2), dp(8), dp(2), dp(8))
                })

                val br = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
                fun miniIconBtn(
                    icon: StudioIcons.Type,
                    tintBg: Int = UiTheme.surfaceAlt,
                    iconTint: Int = UiTheme.textMain,
                    onTap: () -> Unit
                ) = UiTheme.studioIconBtn(
                    act = act,
                    icon = icon,
                    sizeDp = 38,
                    iconSizeDp = 16,
                    rDp = 12,
                    fillColor = tintBg,
                    strokeColor = UiTheme.stroke,
                    iconTint = iconTint,
                    onClick = onTap
                )

                br.addView(miniIconBtn(StudioIcons.Type.EDIT) {
                    val inp = EditText(act).apply { setText(savedName); hint = L.t("rename_hint") }
                    AlertDialog.Builder(act).setTitle(L.t("rename")).setView(inp)
                        .setPositiveButton(L.t("save")) { _, _ ->
                            val nn = inp.text.toString().trim()
                            if (nn.isNotEmpty()) { prefs.edit().putString("gname_${file.name}", nn).apply(); onRefresh() }
                        }.setNegativeButton(L.t("cancel"), null).show()
                }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(5) })

                br.addView(miniIconBtn(StudioIcons.Type.SHARE) {
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

                br.addView(
                    miniIconBtn(
                        StudioIcons.Type.TRASH,
                        Color.parseColor(if (UiTheme.isDark) "#2E1A2B" else "#FFF1F2"),
                        Color.parseColor("#E11D48")
                    ) {
                        showCuteDeletePopup(act, savedName, bmp) {
                            file.delete()
                            selectedFiles.remove(file.name)
                            prefs.edit().remove("gname_${file.name}").apply()
                            onRefresh()
                        }
                    },
                    LinearLayout.LayoutParams(0, dp(38), 1f)
                )
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
            setPadding(dp(14), dp(14), dp(14), dp(28))
        }

        // Hero Balance & Status Banner
        val heroCard = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.grad(act, if (UiTheme.isDark) "#1E1B4B" else "#4F46E5", if (UiTheme.isDark) "#4C1D95" else "#7C3AED", 24, Color.parseColor("#38FFFFFF"))
            setPadding(dp(18), dp(18), dp(18), dp(18))
            elevation = dp(8).toFloat()
        }
        heroCard.addView(
            StudioIcons.imageView(act, StudioIcons.Type.COIN, 40, Color.WHITE),
            LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) }
        )
        val heroLeft = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        heroLeft.addView(TextView(act).apply {
            text = L.t("your_balance")
            textSize = 12f
            UiTheme.applyUiFont(this)
            setTextColor(Color.parseColor("#DDD6FE"))
        })
        heroLeft.addView(TextView(act).apply {
            text = "${billingAds.coins} ${L.t("coins_label")}"
            fontFeatureSettings = "tnum"
            textSize = 22f
            UiTheme.applyDisplayFont(this, bold = true)
            setTextColor(Color.WHITE)
            setPadding(0, dp(2), 0, 0)
        })
        heroCard.addView(heroLeft, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        val remMillis = billingAds.remainingAdFreeMillis()
        val (statusIcon, statusText, statusBgColor) = when {
            billingAds.permanentAdsRemoved -> Triple(StudioIcons.Type.CROWN, L.t("adfree_status_perm"), Color.parseColor("#10B981"))
            remMillis > 0L -> Triple(StudioIcons.Type.TIMER, formatRemainingTime(remMillis), Color.parseColor("#F59E0B"))
            else -> Triple(StudioIcons.Type.SHIELD, L.t("adfree_status_none"), Color.parseColor("#33FFFFFF"))
        }
        val statusTextColor = if (remMillis > 0L && !billingAds.permanentAdsRemoved) Color.parseColor("#1E1B4B") else Color.WHITE
        val statusPill = StudioIcons.iconPill(
            context = act,
            iconType = statusIcon,
            label = statusText,
            textColor = statusTextColor,
            iconColor = statusTextColor,
            iconSizeDp = 13,
            textSizeSp = 11f
        ).apply {
            background = UiTheme.round(act, statusBgColor, 14)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        heroCard.addView(statusPill)
        rootCol.addView(heroCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(14) })

        // Segmented Pill Switcher
        val toggleBar = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            background = UiTheme.round(act, UiTheme.surface, 22, UiTheme.stroke)
            setPadding(dp(5), dp(5), dp(5), dp(5))
            elevation = dp(2).toFloat()
        }
        val coinsTab = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10)) }
        val adFreeTab = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10)) }
        toggleBar.addView(coinsTab, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        toggleBar.addView(adFreeTab, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        rootCol.addView(toggleBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(16) })

        val content = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        rootCol.addView(content)

        fun populateTab(tab: LinearLayout, icon: StudioIcons.Type, label: String, active: Boolean) {
            tab.removeAllViews()
            tab.background = if (active) UiTheme.grad(act, "#FBBF24", "#F59E0B", 18) else null
            val colTxt = if (active) Color.parseColor("#1E1B4B") else UiTheme.textSub
            tab.addView(
                StudioIcons.imageView(act, icon, 16, colTxt),
                LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(6) }
            )
            tab.addView(TextView(act).apply {
                text = label
                textSize = 13.5f
                UiTheme.applyDisplayFont(this, bold = true)
                setTextColor(colTxt)
            })
        }

        fun renderStoreContent() {
            populateTab(coinsTab, StudioIcons.Type.COIN, L.t("tab_coins"), storeCoinsTab)
            populateTab(adFreeTab, StudioIcons.Type.SHIELD, L.t("tab_adfree"), !storeCoinsTab)
            content.removeAllViews()

            if (storeCoinsTab) {
                content.addView(TextView(act).apply {
                    text = L.t("buy_coins_title")
                    textSize = 17f
                    UiTheme.applyDisplayFont(this, bold = true)
                    setTextColor(UiTheme.textMain)
                })
                content.addView(TextView(act).apply {
                    text = L.t("buy_coins_sub")
                    textSize = 12f
                    UiTheme.applyUiFont(this)
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
                            if (badgeText != null) Color.parseColor("#F59E0B") else UiTheme.stroke,
                            if (badgeText != null) 2 else 1
                        )
                        setPadding(dp(12), dp(14), dp(12), dp(12))
                        elevation = dp(4).toFloat()

                        if (badgeText != null) {
                            addView(TextView(act).apply {
                                text = badgeText
                                textSize = 10f
                                UiTheme.applyUiFont(this, bold = true)
                                setTextColor(Color.parseColor("#1E1B4B"))
                                background = UiTheme.round(act, Color.parseColor("#FDE047"), 10)
                                setPadding(dp(8), dp(2), dp(8), dp(2))
                            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })
                        } else {
                            addView(View(act), LinearLayout.LayoutParams(1, dp(6)))
                        }

                        val coinWell = FrameLayout(act).apply {
                            background = UiTheme.round(
                                act,
                                Color.parseColor(if (UiTheme.isDark) "#292524" else "#FEF3C7"),
                                24,
                                Color.parseColor("#F59E0B")
                            )
                            addView(
                                StudioIcons.imageView(act, StudioIcons.Type.COIN, 28, Color.WHITE),
                                FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER)
                            )
                        }
                        addView(coinWell, LinearLayout.LayoutParams(dp(48), dp(48)).apply { bottomMargin = dp(8) })

                        addView(TextView(act).apply {
                            text = "${pack.coins} ${L.t("coins_label")}"
                            fontFeatureSettings = "tnum"
                            textSize = 16f
                            UiTheme.applyDisplayFont(this, bold = true)
                            setTextColor(UiTheme.textMain)
                            maxLines = 1
                        })
                        addView(TextView(act).apply {
                            text = L.t("coin_pack_sub").replace("{n}", pack.coins.toString())
                            textSize = 12f
                            UiTheme.applyUiFont(this)
                            setTextColor(UiTheme.textSub)
                            maxLines = 1
                            setPadding(0, dp(2), 0, dp(10))
                        })
                        val buyBtn = TextView(act).apply {
                            text = pack.priceLabel
                            fontFeatureSettings = "tnum"
                            textSize = 14f
                            UiTheme.applyDisplayFont(this, bold = true)
                            setTextColor(Color.WHITE)
                            gravity = Gravity.CENTER
                            background = UiTheme.grad(act, "#10B981", "#059669", 14)
                            UiTheme.bounce(this) { billingAds.purchase(act, pack) }
                        }
                        addView(buyBtn, LinearLayout.LayoutParams(MATCH_PARENT, dp(42)))
                    }
                    row?.addView(card, LinearLayout.LayoutParams(cardW, WRAP_CONTENT).apply { if (idx % 2 == 0) marginEnd = dp(12) })
                }
            } else {
                content.addView(
                    UiTheme.sectionHeader(
                        act = act,
                        icon = StudioIcons.Type.TIMER,
                        title = L.t("timed_adfree_title"),
                        c1 = "#7C3AED",
                        c2 = "#4F46E5"
                    )
                )
                content.addView(TextView(act).apply {
                    text = L.t("timed_adfree_sub")
                    textSize = 12f
                    UiTheme.applyUiFont(this)
                    setTextColor(UiTheme.textSub)
                    setPadding(0, 0, 0, dp(12))
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
                            if (pass.badgeKey != null) UiTheme.accentBright else UiTheme.stroke
                        )
                        setPadding(dp(12), dp(12), dp(12), dp(12))
                        elevation = dp(3).toFloat()
                    }

                    val iconWell = FrameLayout(act).apply {
                        background = UiTheme.round(act, UiTheme.accentSoft, 14)
                        addView(
                            StudioIcons.imageView(act, StudioIcons.Type.TIMER, 20, UiTheme.accentBright),
                            FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER)
                        )
                    }
                    passCard.addView(iconWell, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(12) })

                    val infoCol = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
                    val titleRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    titleRow.addView(TextView(act).apply {
                        text = L.t(pass.labelKey)
                        textSize = 15f
                        UiTheme.applyDisplayFont(this, bold = true)
                        setTextColor(UiTheme.textMain)
                    })
                    if (pass.badgeKey != null) {
                        titleRow.addView(TextView(act).apply {
                            text = L.t(pass.badgeKey)
                            textSize = 10f
                            UiTheme.applyUiFont(this, bold = true)
                            setTextColor(Color.WHITE)
                            background = UiTheme.grad(act, "#EC4899", "#DB2777", 8)
                            setPadding(dp(7), dp(2), dp(7), dp(2))
                        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(8) })
                    }
                    infoCol.addView(titleRow)
                    infoCol.addView(TextView(act).apply {
                        text = L.t("adfree_pass_desc")
                        textSize = 11.5f
                        UiTheme.applyUiFont(this)
                        setTextColor(UiTheme.textSub)
                        setPadding(0, dp(2), 0, 0)
                    })
                    passCard.addView(infoCol, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })

                    val actionPill = if (isPerm) {
                        StudioIcons.iconPill(
                            context = act,
                            iconType = StudioIcons.Type.CHECK,
                            label = "",
                            textColor = UiTheme.textSub,
                            iconColor = UiTheme.emeraldAccent,
                            iconSizeDp = 18
                        ).apply {
                            background = UiTheme.round(act, UiTheme.surfaceAlt, 14, UiTheme.stroke)
                            setPadding(dp(12), dp(8), dp(12), dp(8))
                            UiTheme.bounce(this) { billingAds.redeemAdFreeWithCoins(pass) }
                        }
                    } else {
                        StudioIcons.iconPill(
                            context = act,
                            iconType = StudioIcons.Type.COIN,
                            label = "${pass.coinsCost}",
                            textColor = if (canAfford) Color.WHITE else UiTheme.accentBright,
                            iconColor = Color.WHITE,
                            iconSizeDp = 15,
                            textSizeSp = 13f
                        ).apply {
                            background = if (canAfford) {
                                UiTheme.grad(act, "#7C3AED", "#4F46E5", 14)
                            } else {
                                UiTheme.round(act, UiTheme.surfaceAlt, 14, UiTheme.accentBright)
                            }
                            setPadding(dp(12), dp(8), dp(12), dp(8))
                            UiTheme.bounce(this) { billingAds.redeemAdFreeWithCoins(pass) }
                        }
                    }
                    passCard.addView(actionPill, LinearLayout.LayoutParams(WRAP_CONTENT, dp(42)))
                    content.addView(passCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })
                }

                // Permanent Ad-Free ($499.99) VIP Card
                val adPack = BillingAdsManager.PACKS.first { it.isRemoveAds }
                val permDone = billingAds.permanentAdsRemoved
                val vipCard = LinearLayout(act).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    background = UiTheme.grad(act, "#1E1B4B", "#6D28D9", 24, Color.parseColor("#44FFFFFF"))
                    setPadding(dp(20), dp(22), dp(20), dp(22))
                    elevation = dp(8).toFloat()

                    val vipBadge = StudioIcons.iconPill(
                        context = act,
                        iconType = StudioIcons.Type.CROWN,
                        label = L.t("lifetime_vip"),
                        textColor = Color.parseColor("#1E1B4B"),
                        iconColor = Color.parseColor("#1E1B4B"),
                        iconSizeDp = 13,
                        textSizeSp = 11f,
                        filled = true
                    ).apply {
                        background = UiTheme.round(act, Color.parseColor("#FDE047"), 12)
                        setPadding(dp(10), dp(4), dp(10), dp(4))
                    }
                    addView(vipBadge, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })

                    addView(TextView(act).apply {
                        text = L.t("perm_adfree_title")
                        textSize = 21f
                        UiTheme.applyDisplayFont(this, bold = true)
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                    })
                    addView(TextView(act).apply {
                        text = L.t("perm_adfree_desc")
                        textSize = 13f
                        UiTheme.applyUiFont(this)
                        setTextColor(Color.parseColor("#EDE9FE"))
                        gravity = Gravity.CENTER
                        setPadding(0, dp(6), 0, dp(16))
                    })
                    val ctaBtn = if (permDone) {
                        UiTheme.studioGradientAction(
                            act = act,
                            icon = StudioIcons.Type.CHECK,
                            label = L.t("ads_removed"),
                            hex1 = "#10B981",
                            hex2 = "#059669",
                            rDp = 16
                        ) {}
                    } else {
                        UiTheme.studioGradientAction(
                            act = act,
                            icon = StudioIcons.Type.CROWN,
                            label = "${adPack.priceLabel} — ${L.t("unlock_forever")}",
                            hex1 = "#FDE047",
                            hex2 = "#F59E0B",
                            rDp = 16,
                            textColor = Color.parseColor("#1E1B4B")
                        ) { billingAds.purchase(act, adPack) }
                    }
                    addView(ctaBtn, LinearLayout.LayoutParams(MATCH_PARENT, dp(50)))
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
            setPadding(dp(16), dp(14), dp(16), dp(28))
        }

        val themeCard = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.round(act, UiTheme.surface, 20, UiTheme.stroke)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            elevation = dp(3).toFloat()
            val well = FrameLayout(act).apply {
                background = UiTheme.round(act, UiTheme.accentSoft, 14)
                addView(
                    StudioIcons.imageView(
                        act,
                        if (UiTheme.isDark) StudioIcons.Type.MOON else StudioIcons.Type.SUN,
                        20,
                        UiTheme.accentBright,
                        filled = UiTheme.isDark
                    ),
                    FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER)
                )
            }
            addView(well, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(12) })
            addView(TextView(act).apply {
                text = if (UiTheme.isDark) L.t("dark_mode") else L.t("light_mode")
                textSize = 15f
                UiTheme.applyDisplayFont(this, bold = true)
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

        fun addSetting(
            icon: StudioIcons.Type,
            title: String,
            trailingBadge: String? = null,
            onTap: () -> Unit
        ) {
            val row = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = UiTheme.round(act, UiTheme.surface, 18, UiTheme.stroke)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                elevation = dp(2).toFloat()
                val well = FrameLayout(act).apply {
                    background = UiTheme.round(act, UiTheme.surfaceAlt, 12)
                    addView(
                        StudioIcons.imageView(act, icon, 19, UiTheme.accentBright),
                        FrameLayout.LayoutParams(dp(19), dp(19), Gravity.CENTER)
                    )
                }
                addView(well, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) })
                addView(TextView(act).apply {
                    text = title
                    textSize = 14.5f
                    UiTheme.applyUiFont(this, bold = true)
                    setTextColor(UiTheme.textMain)
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                if (trailingBadge != null) {
                    addView(TextView(act).apply {
                        text = trailingBadge
                        textSize = 11f
                        UiTheme.applyDisplayFont(this, bold = true)
                        setTextColor(UiTheme.accentBright)
                        background = UiTheme.round(act, UiTheme.accentSoft, 8)
                        setPadding(dp(8), dp(3), dp(8), dp(3))
                    }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) })
                }
                addView(
                    StudioIcons.imageView(act, StudioIcons.Type.CHEVRON_RIGHT, 18, UiTheme.textSub),
                    LinearLayout.LayoutParams(dp(18), dp(18))
                )
                UiTheme.bounce(this, onTap)
            }
            col.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })
        }

        val curInfo = L.languages.find { it.code == L.lang }
        val curLabel = curInfo?.let { "${it.label} (${it.sub})" } ?: "English"
        addSetting(StudioIcons.Type.GLOBE, "${L.t("language")}: $curLabel", curInfo?.badge ?: "EN", onOpenLanguagePage)
        addSetting(StudioIcons.Type.SHIELD_LOCK, L.t("privacy_policy")) {
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
        addSetting(StudioIcons.Type.INFO, L.t("about")) { openPlayStore(act) }
        addSetting(StudioIcons.Type.SHARE, L.t("share_friend")) {
            val url = "https://play.google.com/store/apps/details?id=${act.packageName}"
            (act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Link", url))
            Toast.makeText(act, L.t("link_copied"), Toast.LENGTH_SHORT).show()
            act.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, L.t("share_text"))
            }, L.t("share_friend")))
        }
        addSetting(StudioIcons.Type.STAR, L.t("rate_us")) { openPlayStore(act) }
        addSetting(StudioIcons.Type.MAIL, L.t("feedback")) {
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
                background = UiTheme.round(act, Color.WHITE, 22, UiTheme.stroke)
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }
            val thumbIv = ImageView(act).apply {
                setImageBitmap(bmp)
                scaleType = ImageView.ScaleType.FIT_CENTER
                clipRounded(this, dp(18).toFloat())
            }
            thumbWrap.addView(thumbIv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            iconBox.addView(thumbWrap, FrameLayout.LayoutParams(dp(78), dp(78), Gravity.CENTER))
        } else {
            val softCircle = FrameLayout(act).apply {
                background = UiTheme.round(act, Color.parseColor(if (UiTheme.isDark) "#2E1A2B" else "#FFE4E6"), 36)
                addView(
                    StudioIcons.imageView(act, StudioIcons.Type.PHOTO, 30, Color.parseColor("#E11D48")),
                    FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER)
                )
            }
            iconBox.addView(softCircle, FrameLayout.LayoutParams(dp(74), dp(74), Gravity.CENTER))
        }

        val badge = FrameLayout(act).apply {
            background = UiTheme.grad(act, "#FB7185", "#E11D48", 16)
            elevation = dp(4).toFloat()
            addView(
                StudioIcons.imageView(act, StudioIcons.Type.TRASH, 16, Color.WHITE),
                FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER)
            )
        }
        iconBox.addView(badge, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.BOTTOM or Gravity.END))
        card.addView(iconBox, LinearLayout.LayoutParams(dp(90), dp(88)).apply { bottomMargin = dp(14) })

        card.addView(TextView(act).apply {
            text = L.t("delete_confirm")
            textSize = 18f
            UiTheme.applyDisplayFont(this, bold = true)
            setTextColor(UiTheme.textMain)
            gravity = Gravity.CENTER
        })

        card.addView(TextView(act).apply {
            text = "$drawingName — ${L.t("delete_sub")}"
            textSize = 13f
            UiTheme.applyUiFont(this)
            setTextColor(UiTheme.textSub)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(18))
        })

        val btnRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        val cancelBtn = UiTheme.studioSoftAction(
            act = act,
            icon = StudioIcons.Type.CLOSE,
            label = L.t("cancel"),
            rDp = 16
        ) { dialog.dismiss() }
        val deleteBtn = UiTheme.studioGradientAction(
            act = act,
            icon = StudioIcons.Type.TRASH,
            label = L.t("delete"),
            hex1 = "#FB7185",
            hex2 = "#E11D48",
            rDp = 16
        ) {
            dialog.dismiss()
            onConfirm()
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
