package com.ardrawing.sketch

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.opencv.android.OpenCVLoader
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private enum class Tab { HOME, GALLERY, STORE, SETTINGS }
    private enum class Flow { SPLASH, MAIN, EDITOR, CAMERA, CONGRATS }

    private var flow = Flow.SPLASH
    private var currentTab = Tab.HOME
    private lateinit var root: FrameLayout
    private lateinit var analysisExecutor: ExecutorService
    private val handler = Handler(Looper.getMainLooper())
    private val tracker = PlaneTracker()

    private lateinit var billingAds: BillingAdsManager
    private val prefs by lazy { getSharedPreferences("ar_draw_prefs", MODE_PRIVATE) }
    private val galleryDir by lazy { File(filesDir, "gallery").apply { mkdirs() } }

    private var originalBitmap: Bitmap? = null
    private var cutoutBitmap: Bitmap? = null
    private var bgRemoved = false
    private var isProcessingBg = false
    private var opacityPercent = 70
    private var brightness = 0

    private var cameraProvider: ProcessCameraProvider? = null
    private var boundCamera: Camera? = null
    private var torchOn = false
    private var overlayView: OverlayView? = null
    private var cameraHintView: TextView? = null

    private var pendingCameraUri: Uri? = null
    private var importToGalleryMode = false
    private var openCameraAfterPermission = false

    private val pickGalleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) handlePickedUri(uri)
    }

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val uri = pendingCameraUri
        if (ok && uri != null) handlePickedUri(uri)
    }

    private val cameraPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, L.t("camera_denied"), Toast.LENGTH_LONG).show()
        } else if (openCameraAfterPermission) {
            openCameraAfterPermission = false
            launchTakePhoto()
        } else {
            showFlow(Flow.CAMERA)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OpenCVLoader.initLocal()
        L.lang = prefs.getString("lang", "en") ?: "en"
        analysisExecutor = Executors.newSingleThreadExecutor()

        billingAds = BillingAdsManager(
            context = this,
            onCoinsOrAdsChanged = { runOnUiThread { if (flow == Flow.MAIN) renderMain() } },
            onMessage = { msg -> runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() } }
        )
        billingAds.init()

        root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#0B0F19")) }
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (flow) {
                    Flow.SPLASH -> { /* লোডিং শেষ না হওয়া পর্যন্ত বের হওয়া যাবে না */ }
                    Flow.CAMERA -> {
                        stopCamera()
                        showFlow(Flow.EDITOR)
                    }
                    Flow.EDITOR, Flow.CONGRATS -> showFlow(Flow.MAIN)
                    Flow.MAIN -> {
                        if (currentTab != Tab.HOME) {
                            currentTab = Tab.HOME
                            renderMain()
                        } else {
                            finish()
                        }
                    }
                }
            }
        })

        showSplash()
    }

    // ---------------- 1. SPLASH (1% -> 100%) ----------------
    private fun showSplash() {
        flow = Flow.SPLASH
        root.removeAllViews()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }

        val ring = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(4), Color.parseColor("#4F46E5"))
                setColor(Color.parseColor("#161D2F"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val logoIv = ImageView(this).apply {
            setImageResource(R.drawable.app_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setOval(0, 0, v.width, v.height) }
            }
            clipToOutline = true
        }
        ring.addView(logoIv, FrameLayout.LayoutParams(dp(120), dp(120)))

        val titleTv = TextView(this).apply {
            text = L.t("app")
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, dp(18), 0, dp(24))
        }

        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 1
        }
        val pctTv = TextView(this).apply {
            text = "${L.t("loading")}... 1%"
            textSize = 15f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(10), 0, 0)
        }

        col.addView(ring)
        col.addView(titleTv)
        col.addView(bar, LinearLayout.LayoutParams(dp(220), dp(10)))
        col.addView(pctTv)
        root.addView(col, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        var p = 1
        val tick = object : Runnable {
            override fun run() {
                p = (p + 3).coerceAtMost(100)
                bar.progress = p
                pctTv.text = "${L.t("loading")}... $p%"
                if (p < 100) {
                    handler.postDelayed(this, 55)
                } else {
                    handler.postDelayed({ showFlow(Flow.MAIN) }, 220)
                }
            }
        }
        handler.postDelayed(tick, 60)
    }

    private fun showFlow(target: Flow) {
        if (flow == Flow.CAMERA && target != Flow.CAMERA) stopCamera()
        flow = target
        when (target) {
            Flow.MAIN -> renderMain()
            Flow.EDITOR -> renderEditor()
            Flow.CAMERA -> renderCamera()
            Flow.CONGRATS -> renderCongrats()
            Flow.SPLASH -> showSplash()
        }
    }

    // ---------------- 2. MAIN SHELL (TOP COINS + TAB + AD + BOTTOM NAV) ----------------
    private fun renderMain() {
        root.removeAllViews()
        val mainCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Top Bar + Coin Balance
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#121826"))
            setPadding(dp(16), dp(34), dp(16), dp(12))
        }
        val titleTv = TextView(this).apply {
            text = when (currentTab) {
                Tab.HOME -> L.t("app")
                Tab.GALLERY -> L.t("my_activity")
                Tab.STORE -> L.t("store_title")
                Tab.SETTINGS -> L.t("settings")
            }
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        val coinBtn = Button(this).apply {
            text = "🪙 ${billingAds.coins}"
            setTextColor(Color.parseColor("#FBBF24"))
            textSize = 14f
            isAllCaps = false
            background = roundedBg("#1E293B", 16)
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener {
                currentTab = Tab.STORE
                renderMain()
            }
        }
        topBar.addView(titleTv, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        topBar.addView(coinBtn, LinearLayout.LayoutParams(WRAP_CONTENT, dp(36)))
        mainCol.addView(topBar)

        // Content Scroll Area
        val contentScroll = ScrollView(this)
        val body = when (currentTab) {
            Tab.HOME -> buildHomeTab()
            Tab.GALLERY -> buildGalleryTab()
            Tab.STORE -> buildStoreTab()
            Tab.SETTINGS -> buildSettingsTab()
        }
        contentScroll.addView(body)
        mainCol.addView(contentScroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        // Banner Ad (hide automatically when ads removed)
        if (!billingAds.adsRemoved) {
            val adBox = FrameLayout(this).apply {
                setBackgroundColor(Color.parseColor("#0B0F19"))
            }
            billingAds.attachBanner(adBox)
            mainCol.addView(adBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        // Bottom Navigation Bar (4 Tabs)
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#121826"))
            setPadding(dp(4), dp(6), dp(4), dp(14))
        }
        val tabs = listOf(
            Triple(Tab.HOME, "🏠", L.t("home")),
            Triple(Tab.GALLERY, "🖼️", L.t("my_gallery")),
            Triple(Tab.STORE, "🛒", L.t("store")),
            Triple(Tab.SETTINGS, "⚙️", L.t("settings"))
        )
        tabs.forEach { (tab, icon, label) ->
            val selected = currentTab == tab
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(6), dp(2), dp(6))
                background = if (selected) roundedBg("#1E293B", 12) else null
                addView(TextView(this@MainActivity).apply {
                    text = icon
                    textSize = 18f
                    gravity = Gravity.CENTER
                })
                addView(TextView(this@MainActivity).apply {
                    text = label
                    textSize = 11f
                    gravity = Gravity.CENTER
                    maxLines = 1
                    setTextColor(if (selected) Color.parseColor("#818CF8") else Color.parseColor("#94A3B8"))
                })
                setOnClickListener {
                    currentTab = tab
                    renderMain()
                }
            }
            nav.addView(item, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        mainCol.addView(nav)
        root.addView(mainCol, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    // ---------------- TAB 1: HOME ----------------
    private fun buildHomeTab(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(20))
        }

        col.addView(TextView(this).apply {
            text = L.t("home_sub")
            textSize = 14f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 0, 0, dp(16))
        })

        // Drawing Create Button
        col.addView(actionBtn("✏️  ${L.t("drawing_create")}", "#4F46E5", 58) {
            showSourceChooser()
        })

        col.addView(TextView(this).apply {
            text = L.t("samples")
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, dp(14), 0, dp(10))
        })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (i in 0 until Samples.COUNT) {
            val thumb = Samples.render(i, 240)
            val card = FrameLayout(this).apply {
                background = roundedBg("#FFFFFF", 14)
                setPadding(dp(6), dp(6), dp(6), dp(6))
                addView(ImageView(this@MainActivity).apply {
                    setImageBitmap(thumb)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }, FrameLayout.LayoutParams(dp(100), dp(100)))
                setOnClickListener {
                    openEditorWithBitmap(Samples.render(i, 960))
                }
            }
            row.addView(card, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                marginEnd = dp(10)
            })
        }
        col.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        })
        return col
    }

    private fun showSourceChooser() {
        val options = arrayOf("📷  ${L.t("take_photo")}", "🖼️  ${L.t("upload_gallery")}")
        AlertDialog.Builder(this)
            .setTitle(L.t("choose_source"))
            .setItems(options) { _, which ->
                importToGalleryMode = false
                if (which == 0) {
                    val ok = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                    if (ok) launchTakePhoto()
                    else {
                        openCameraAfterPermission = true
                        cameraPermLauncher.launch(Manifest.permission.CAMERA)
                    }
                } else {
                    pickGalleryLauncher.launch("image/*")
                }
            }
            .setNegativeButton(L.t("cancel"), null)
            .show()
    }

    // ---------------- TAB 2: MY GALLERY (1:1 SQUARE) ----------------
    private fun buildGalleryTab(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(20))
        }

        col.addView(actionBtn("➕  ${L.t("import_photo")}", "#2563EB", 48) {
            importToGalleryMode = true
            pickGalleryLauncher.launch("image/*")
        })

        val files = (galleryDir.listFiles()?.toList() ?: emptyList())
            .filter { it.name.endsWith(".png") }
            .sortedByDescending { it.lastModified() }

        if (files.isEmpty()) {
            col.addView(TextView(this).apply {
                text = L.t("empty_gallery")
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
                setPadding(0, dp(48), 0, 0)
            })
            return col
        }

        val cardSize = (resources.displayMetrics.widthPixels - dp(44)) / 2
        var row: LinearLayout? = null
        files.forEachIndexed { idx, file ->
            if (idx % 2 == 0) {
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                col.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                    bottomMargin = dp(12)
                })
            }

            val bmp = BitmapFactory.decodeFile(file.absolutePath)
            val savedName = prefs.getString("gname_${file.name}", "Drawing #${files.size - idx}") ?: "Drawing"

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = roundedBg("#161E31", 14)
                setPadding(dp(8), dp(8), dp(8), dp(8))

                // ১:১ সাইজের বর্গাকার ছবি
                addView(ImageView(this@MainActivity).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(Color.parseColor("#0B0F19"))
                    setOnClickListener {
                        if (bmp != null) openEditorWithBitmap(bmp)
                    }
                }, LinearLayout.LayoutParams(MATCH_PARENT, cardSize - dp(16)))

                addView(TextView(this@MainActivity).apply {
                    text = savedName
                    setTextColor(Color.WHITE)
                    textSize = 13f
                    maxLines = 1
                    setPadding(0, dp(6), 0, dp(4))
                })

                val btnRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
                fun miniBtn(txt: String, onTap: () -> Unit) = TextView(this@MainActivity).apply {
                    text = txt
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#CBD5E1"))
                    background = roundedBg("#1E293B", 8)
                    setPadding(0, dp(5), 0, dp(5))
                    setOnClickListener { onTap() }
                }
                btnRow.addView(miniBtn("✏️") { renameGalleryItem(file, savedName) },
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
                btnRow.addView(miniBtn("🔗") { shareGalleryItem(file, savedName) },
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
                btnRow.addView(miniBtn("🗑️") { deleteGalleryItem(file) },
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(btnRow)
            }

            row?.addView(card, LinearLayout.LayoutParams(cardSize, WRAP_CONTENT).apply {
                if (idx % 2 == 0) marginEnd = dp(12)
            })
        }
        return col
    }

    private fun renameGalleryItem(file: File, currentName: String) {
        val input = EditText(this).apply {
            setText(currentName)
            hint = L.t("rename_hint")
        }
        AlertDialog.Builder(this)
            .setTitle(L.t("rename"))
            .setView(input)
            .setPositiveButton(L.t("save")) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    prefs.edit().putString("gname_${file.name}", newName).apply()
                    renderMain()
                }
            }
            .setNegativeButton(L.t("cancel"), null)
            .show()
    }

    private fun shareGalleryItem(file: File, name: String) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "$name — ${L.t("share_text")}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, L.t("share")))
        } catch (_: Exception) {}
    }

    private fun deleteGalleryItem(file: File) {
        AlertDialog.Builder(this)
            .setMessage(L.t("delete_confirm"))
            .setPositiveButton(L.t("delete")) { _, _ ->
                file.delete()
                prefs.edit().remove("gname_${file.name}").apply()
                renderMain()
            }
            .setNegativeButton(L.t("cancel"), null)
            .show()
    }

    // ---------------- TAB 3: STORE (COINS $0.10-$10 + REMOVE ADS) ----------------
    private fun buildStoreTab(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(20))
        }

        BillingAdsManager.PACKS.forEach { pack ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = roundedBg(if (pack.isRemoveAds) "#312E81" else "#161E31", 14)
                setPadding(dp(14), dp(14), dp(14), dp(14))
            }
            val infoCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            infoCol.addView(TextView(this).apply {
                text = if (pack.isRemoveAds) "🚫  ${L.t("remove_ads")}" else "🪙  ${pack.coins} ${L.t("coins_label")}"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            if (pack.isRemoveAds) {
                infoCol.addView(TextView(this).apply {
                    text = L.t("remove_ads_desc")
                    textSize = 12f
                    setTextColor(Color.parseColor("#CBD5E1"))
                })
            }

            val alreadyRemoved = pack.isRemoveAds && billingAds.adsRemoved
            val buyBtn = Button(this).apply {
                text = if (alreadyRemoved) L.t("ads_removed") else pack.priceLabel
                setTextColor(Color.WHITE)
                isAllCaps = false
                background = roundedBg(if (alreadyRemoved) "#10B981" else "#4F46E5", 10)
                setOnClickListener {
                    if (!alreadyRemoved) billingAds.purchase(this@MainActivity, pack)
                }
            }

            card.addView(infoCol, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            card.addView(buyBtn, LinearLayout.LayoutParams(WRAP_CONTENT, dp(42)))
            col.addView(card, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            })
        }
        return col
    }

    // ---------------- TAB 4: SETTINGS (15 LANGUAGES + PLAY STORE LINKS) ----------------
    private fun buildSettingsTab(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }

        val curLangLabel = L.languages.find { it.code == L.lang }?.label ?: "English"
        col.addView(actionBtn("🌐  ${L.t("language")}: $curLangLabel", "#1E293B", 50) {
            val labels = L.languages.map { it.label }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle(L.t("language"))
                .setItems(labels) { _, idx ->
                    val chosen = L.languages[idx].code
                    L.lang = chosen
                    prefs.edit().putString("lang", chosen).apply()
                    renderMain()
                }
                .show()
        })

        col.addView(actionBtn("🔒  ${L.t("privacy_policy")}", "#1E293B", 50) {
            AlertDialog.Builder(this)
                .setTitle(L.t("privacy_policy"))
                .setMessage(L.t("privacy_text"))
                .setPositiveButton("OK", null)
                .show()
        })

        col.addView(actionBtn("ℹ️  ${L.t("about")}", "#1E293B", 50) {
            openPlayStorePage()
        })

        col.addView(actionBtn("📤  ${L.t("share_friend")}", "#2563EB", 50) {
            val url = "https://play.google.com/store/apps/details?id=$packageName"
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("App Link", url))
            Toast.makeText(this, L.t("link_copied"), Toast.LENGTH_SHORT).show()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, L.t("share_text"))
            }
            startActivity(Intent.createChooser(intent, L.t("share_friend")))
        })

        col.addView(actionBtn("⭐  ${L.t("rate_us")}", "#1E293B", 50) {
            openPlayStorePage()
        })

        col.addView(actionBtn("✉️  ${L.t("feedback")}", "#1E293B", 50) {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                putExtra(Intent.EXTRA_SUBJECT, "AR Drawing Feedback")
            }
            try { startActivity(intent) } catch (_: Exception) {}
        })

        return col
    }

    private fun openPlayStorePage() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
        }
    }

    // ---------------- 3. EDITOR (REMOVE BG + OPACITY + NEXT) ----------------
    private fun openEditorWithBitmap(bmp: Bitmap) {
        originalBitmap = bmp
        cutoutBitmap = null
        bgRemoved = false
        brightness = 0
        opacityPercent = 70
        showFlow(Flow.EDITOR)
    }

    private fun renderEditor() {
        root.removeAllViews()
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(36), dp(18), dp(24))
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(14))
        }
        topRow.addView(Button(this).apply {
            text = L.t("back")
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = roundedBg("#1E293B", 10)
            setOnClickListener { showFlow(Flow.MAIN) }
        }, LinearLayout.LayoutParams(WRAP_CONTENT, dp(40)).apply { marginEnd = dp(12) })
        topRow.addView(TextView(this).apply {
            text = L.t("drawing_create")
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        col.addView(topRow)

        val previewIv = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = roundedBg("#161E31", 16)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        col.addView(previewIv, LinearLayout.LayoutParams(MATCH_PARENT, dp(280)).apply {
            bottomMargin = dp(16)
        })

        val bgBtn = Button(this).apply {
            setTextColor(Color.WHITE)
            isAllCaps = false
            textSize = 15f
        }

        fun updateEditorVisual() {
            val active = if (bgRemoved && cutoutBitmap != null) cutoutBitmap else originalBitmap
            previewIv.setImageBitmap(active)
            previewIv.alpha = opacityPercent / 100f
            bgBtn.text = when {
                isProcessingBg -> L.t("removing_bg")
                bgRemoved -> "↩️  ${L.t("restore_orig")}"
                else -> "✂️  ${L.t("remove_bg")}"
            }
            bgBtn.background = roundedBg(if (bgRemoved) "#D97706" else "#2563EB", 12)
        }
        updateEditorVisual()

        bgBtn.setOnClickListener {
            if (isProcessingBg) return@setOnClickListener
            val orig = originalBitmap ?: return@setOnClickListener
            if (bgRemoved) {
                bgRemoved = false
                updateEditorVisual()
            } else if (cutoutBitmap != null) {
                bgRemoved = true
                updateEditorVisual()
            } else {
                isProcessingBg = true
                updateEditorVisual()
                ImageProcessor.removeBackgroundAsync(orig) { res ->
                    runOnUiThread {
                        isProcessingBg = false
                        cutoutBitmap = res
                        bgRemoved = true
                        updateEditorVisual()
                    }
                }
            }
        }
        col.addView(bgBtn, LinearLayout.LayoutParams(MATCH_PARENT, dp(50)).apply { bottomMargin = dp(14) })

        col.addView(TextView(this).apply {
            text = L.t("opacity")
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 14f
        })
        col.addView(SeekBar(this).apply {
            max = 100
            progress = opacityPercent
            setOnSeekBarChangeListener(simpleSeek { p ->
                opacityPercent = p.coerceAtLeast(10)
                previewIv.alpha = opacityPercent / 100f
            })
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(18) })

        col.addView(actionBtn("${L.t("next")} →", "#10B981", 54) {
            val active = if (bgRemoved && cutoutBitmap != null) cutoutBitmap else originalBitmap
            if (active == null) {
                Toast.makeText(this, L.t("need_image"), Toast.LENGTH_SHORT).show()
            } else {
                val ok = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                if (ok) showFlow(Flow.CAMERA)
                else {
                    openCameraAfterPermission = false
                    cameraPermLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        })

        scroll.addView(col)
        root.addView(scroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    // ---------------- 4. AR CAMERA (BRIGHTNESS + FLASH + LOCK + NEXT) ----------------
    private fun renderCamera() {
        root.removeAllViews()
        torchOn = false

        val baseBmp = (if (bgRemoved && cutoutBitmap != null) cutoutBitmap else originalBitmap) ?: return
        val previewView = PreviewView(this)
        val overlay = OverlayView(this).apply {
            setBitmap(ImageProcessor.applyBrightness(baseBmp, brightness), resetPos = true)
            setOpacity(opacityPercent)
        }
        overlayView = overlay

        val hintTv = TextView(this).apply {
            text = L.t("hint_place")
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            background = roundedBg("#AA000000", 12)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        cameraHintView = hintTv

        val backBtn = Button(this).apply {
            text = "←"
            setTextColor(Color.WHITE)
            background = roundedBg("#AA111827", 20)
            setOnClickListener {
                stopCamera()
                showFlow(Flow.EDITOR)
            }
        }
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(36), dp(12), dp(8))
            addView(backBtn, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(8) })
            addView(hintTv, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }

        val bottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg("#D90F172A", 18)
            setPadding(dp(14), dp(10), dp(14), dp(14))
        }

        bottomPanel.addView(TextView(this).apply {
            text = L.t("brightness")
            setTextColor(Color.WHITE)
            textSize = 12f
        })
        bottomPanel.addView(SeekBar(this).apply {
            max = 100
            progress = brightness + 50
            setOnSeekBarChangeListener(simpleSeek { p ->
                brightness = p - 50
                overlay.setBitmap(ImageProcessor.applyBrightness(baseBmp, brightness), resetPos = false)
            })
        })

        bottomPanel.addView(TextView(this).apply {
            text = L.t("opacity")
            setTextColor(Color.WHITE)
            textSize = 12f
        })
        bottomPanel.addView(SeekBar(this).apply {
            max = 100
            progress = opacityPercent
            setOnSeekBarChangeListener(simpleSeek { p ->
                opacityPercent = p.coerceAtLeast(10)
                overlay.setOpacity(opacityPercent)
            })
        })

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }

        val flashBtn = Button(this).apply {
            text = "🔦 ${L.t("flash")}"
            setTextColor(Color.WHITE)
            isAllCaps = false
            textSize = 13f
            background = roundedBg("#334155", 10)
            setOnClickListener {
                torchOn = !torchOn
                boundCamera?.cameraControl?.enableTorch(torchOn)
                background = roundedBg(if (torchOn) "#F59E0B" else "#334155", 10)
            }
        }

        val lockBtn = Button(this).apply {
            text = "🔒 ${L.t("lock")}"
            setTextColor(Color.WHITE)
            isAllCaps = false
            textSize = 13f
            background = roundedBg("#2563EB", 10)
            setOnClickListener {
                if (!overlay.isLocked) {
                    tracker.startLock()
                    overlay.lock()
                    text = "🔓 ${L.t("unlock")}"
                    background = roundedBg("#D97706", 10)
                    hintTv.text = L.t("hint_locked")
                } else {
                    tracker.stopLock()
                    overlay.unlock()
                    text = "🔒 ${L.t("lock")}"
                    background = roundedBg("#2563EB", 10)
                    hintTv.text = L.t("hint_place")
                }
            }
        }

        val nextBtn = Button(this).apply {
            text = "${L.t("next")} ✓"
            setTextColor(Color.WHITE)
            isAllCaps = false
            textSize = 13f
            background = roundedBg("#10B981", 10)
            setOnClickListener {
                saveToGallery(baseBmp, "Drawing")
                stopCamera()
                billingAds.showInterstitial(this@MainActivity) {
                    runOnUiThread { showFlow(Flow.CONGRATS) }
                }
            }
        }

        btnRow.addView(flashBtn, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        btnRow.addView(lockBtn, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        btnRow.addView(nextBtn, LinearLayout.LayoutParams(0, dp(44), 1f))
        bottomPanel.addView(btnRow)

        root.addView(previewView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(topRow, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))
        root.addView(bottomPanel, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM).apply {
            setMargins(dp(12), 0, dp(12), dp(18))
        })

        startCamera(previewView)
    }

    // ---------------- 5. CONGRATS ----------------
    private fun renderCongrats() {
        root.removeAllViews()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
        }
        col.addView(TextView(this).apply {
            text = L.t("congrats")
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        col.addView(TextView(this).apply {
            text = L.t("good_luck")
            textSize = 16f
            setTextColor(Color.parseColor("#CBD5E1"))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(28))
        })
        col.addView(actionBtn(L.t("go_home"), "#4F46E5", 52) { showFlow(Flow.MAIN) })
        root.addView(col, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    // ---------------- CAMERA X + IMAGE IO ----------------
    private fun startCamera(previewView: PreviewView) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(analysisExecutor) { image ->
                    try {
                        val out = tracker.process(image)
                        val ov = overlayView
                        if (ov != null && ov.isLocked) {
                            runOnUiThread {
                                ov.updateTracking(out)
                                cameraHintView?.text =
                                    if (out.good) L.t("hint_locked") else L.t("hint_lost")
                            }
                        }
                    } finally {
                        image.close()
                    }
                }

                provider.unbindAll()
                boundCamera = provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                )
            } catch (_: Exception) {
                Toast.makeText(this, L.t("camera_error"), Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        try { boundCamera?.cameraControl?.enableTorch(false) } catch (_: Exception) {}
        boundCamera = null
        torchOn = false
        tracker.stopLock()
        cameraProvider?.unbindAll()
        overlayView = null
        cameraHintView = null
    }

    private fun launchTakePhoto() {
        val file = File(cacheDir, "cam_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        pendingCameraUri = uri
        takePhotoLauncher.launch(uri)
    }

    private fun handlePickedUri(uri: Uri) {
        try {
            val source = ImageDecoder.createSource(contentResolver, uri)
            val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val maxSide = maxOf(info.size.width, info.size.height)
                if (maxSide > 1080) {
                    val s = 1080f / maxSide
                    decoder.setTargetSize(
                        (info.size.width * s).toInt().coerceAtLeast(1),
                        (info.size.height * s).toInt().coerceAtLeast(1)
                    )
                }
            }
            val clean = if (bmp.config == Bitmap.Config.ARGB_8888) bmp else bmp.copy(Bitmap.Config.ARGB_8888, false)
            if (importToGalleryMode) {
                saveToGallery(clean, "Imported")
                renderMain()
            } else {
                openEditorWithBitmap(clean)
            }
        } catch (_: Exception) {
            Toast.makeText(this, L.t("image_failed"), Toast.LENGTH_LONG).show()
        }
    }

    private fun saveToGallery(bmp: Bitmap?, prefix: String) {
        if (bmp == null) return
        try {
            val scale = 512f / maxOf(bmp.width, bmp.height).coerceAtLeast(1)
            val thumb = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * scale).toInt().coerceAtLeast(1),
                (bmp.height * scale).toInt().coerceAtLeast(1),
                true
            )
            val name = "art_${System.currentTimeMillis()}.png"
            val file = File(galleryDir, name)
            FileOutputStream(file).use { thumb.compress(Bitmap.CompressFormat.PNG, 92, it) }
            val count = (galleryDir.listFiles()?.size ?: 1)
            prefs.edit().putString("gname_$name", "$prefix #$count").apply()
        } catch (_: Exception) {}
    }

    private fun actionBtn(label: String, hex: String, heightDp: Int, onClick: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 15f
        isAllCaps = false
        background = roundedBg(hex, 12)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(heightDp)).apply {
            bottomMargin = dp(10)
        }
        setOnClickListener { onClick() }
    }

    private fun roundedBg(hex: String, radiusDp: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(hex))
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun simpleSeek(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
            if (fromUser) onChange(p)
        }
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) {}
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        stopCamera()
        billingAds.destroy()
        if (::analysisExecutor.isInitialized) analysisExecutor.shutdown()
    }
}
