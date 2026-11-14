package com.ardrawing.sketch

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
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
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
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
    private val tracker by lazy { PlaneTracker() }

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

    private val pickGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) handlePickedUri(uri)
    }
    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri
        if (ok && uri != null) handlePickedUri(uri)
    }
    private val cameraPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) Toast.makeText(this, L.t("camera_denied"), Toast.LENGTH_LONG).show()
        else if (openCameraAfterPermission) { openCameraAfterPermission = false; launchTakePhoto() }
        else showFlow(Flow.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { OpenCVLoader.initLocal() } catch (_: Throwable) {}
        L.lang = prefs.getString("lang", "en") ?: "en"
        analysisExecutor = Executors.newSingleThreadExecutor()

        billingAds = BillingAdsManager(
            context = this,
            onCoinsOrAdsChanged = { runOnUiThread { if (flow == Flow.MAIN) renderMain() } },
            onMessage = { msg -> runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() } }
        )
        try { billingAds.init() } catch (_: Throwable) {}

        root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#0B0F19")) }
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (flow) {
                    Flow.SPLASH -> {}
                    Flow.CAMERA -> { stopCamera(); showFlow(Flow.EDITOR) }
                    Flow.EDITOR, Flow.CONGRATS -> showFlow(Flow.MAIN)
                    Flow.MAIN -> if (currentTab != Tab.HOME) { currentTab = Tab.HOME; renderMain() } else finish()
                }
            }
        })
        showSplash()
    }

    private fun loadSafeLogo(maxPx: Int): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(resources, R.drawable.app_logo, o)
        var s = 1
        while (o.outWidth / s > maxPx || o.outHeight / s > maxPx) s *= 2
        BitmapFactory.decodeResource(resources, R.drawable.app_logo, BitmapFactory.Options().apply { inSampleSize = s })
    } catch (_: Throwable) { null }

    private fun showSplash() {
        flow = Flow.SPLASH
        root.removeAllViews()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        val ring = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setStroke(dp(4), Color.parseColor("#4F46E5")); setColor(Color.parseColor("#161D2F"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val logoIv = ImageView(this).apply {
            loadSafeLogo(320)?.let { setImageBitmap(it) } ?: setImageResource(R.drawable.app_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setOval(0, 0, v.width, v.height) }
            }
            clipToOutline = true
        }
        ring.addView(logoIv, FrameLayout.LayoutParams(dp(120), dp(120)))
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 1 }
        val pctTv = TextView(this).apply {
            text = "${L.t("loading")}... 1%"; textSize = 15f; setTextColor(Color.parseColor("#94A3B8")); setPadding(0, dp(10), 0, 0)
        }
        col.addView(ring)
        col.addView(TextView(this).apply {
            text = L.t("app"); textSize = 24f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE); setPadding(0, dp(18), 0, dp(24))
        })
        col.addView(bar, LinearLayout.LayoutParams(dp(220), dp(10)))
        col.addView(pctTv)
        root.addView(col, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        var p = 1
        handler.postDelayed(object : Runnable {
            override fun run() {
                p = (p + 3).coerceAtMost(100)
                bar.progress = p; pctTv.text = "${L.t("loading")}... $p%"
                if (p < 100) handler.postDelayed(this, 55) else handler.postDelayed({ showFlow(Flow.MAIN) }, 220)
            }
        }, 60)
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

    private fun renderMain() {
        root.removeAllViews()
        val mainCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#121826")); setPadding(dp(16), dp(34), dp(16), dp(12))
        }
        topBar.addView(TextView(this).apply {
            text = when (currentTab) {
                Tab.HOME -> L.t("app"); Tab.GALLERY -> L.t("my_activity"); Tab.STORE -> L.t("store_title"); Tab.SETTINGS -> L.t("settings")
            }
            textSize = 20f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        topBar.addView(Button(this).apply {
            text = "🪙 ${billingAds.coins}"; setTextColor(Color.parseColor("#FBBF24")); textSize = 14f; isAllCaps = false
            background = roundedBg("#1E293B", 16); setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { currentTab = Tab.STORE; renderMain() }
        }, LinearLayout.LayoutParams(WRAP_CONTENT, dp(36)))
        mainCol.addView(topBar)

        val scroll = ScrollView(this)
        scroll.addView(when (currentTab) {
            Tab.HOME -> buildHomeTab(); Tab.GALLERY -> buildGalleryTab(); Tab.STORE -> buildStoreTab(); Tab.SETTINGS -> buildSettingsTab()
        })
        mainCol.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        if (!billingAds.adsRemoved) {
            val adBox = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#0B0F19")) }
            billingAds.attachBanner(adBox)
            mainCol.addView(adBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.parseColor("#121826")); setPadding(dp(4), dp(6), dp(4), dp(14))
        }
        listOf(
            Triple(Tab.HOME, "🏠", L.t("home")), Triple(Tab.GALLERY, "🖼️", L.t("my_gallery")),
            Triple(Tab.STORE, "🛒", L.t("store")), Triple(Tab.SETTINGS, "⚙️", L.t("settings"))
        ).forEach { (tab, icon, label) ->
            val sel = currentTab == tab
            nav.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(2), dp(6), dp(2), dp(6))
                background = if (sel) roundedBg("#1E293B", 12) else null
                addView(TextView(this@MainActivity).apply { text = icon; textSize = 18f; gravity = Gravity.CENTER })
                addView(TextView(this@MainActivity).apply {
                    text = label; textSize = 11f; gravity = Gravity.CENTER; maxLines = 1
                    setTextColor(if (sel) Color.parseColor("#818CF8") else Color.parseColor("#94A3B8"))
                })
                setOnClickListener { currentTab = tab; renderMain() }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        mainCol.addView(nav)
        root.addView(mainCol, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    private fun buildHomeTab(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(20)) }
        col.addView(TextView(this).apply { text = L.t("home_sub"); textSize = 14f; setTextColor(Color.parseColor("#94A3B8")); setPadding(0, 0, 0, dp(16)) })
        col.addView(actionBtn("✏️  ${L.t("drawing_create")}", "#4F46E5", 58) { showSourceChooser() })
        col.addView(TextView(this).apply { text = L.t("samples"); textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE); setPadding(0, dp(14), 0, dp(10)) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (i in 0 until Samples.COUNT) {
            row.addView(FrameLayout(this).apply {
                background = roundedBg("#FFFFFF", 14); setPadding(dp(6), dp(6), dp(6), dp(6))
                addView(ImageView(this@MainActivity).apply { setImageBitmap(Samples.render(i, 240)); scaleType = ImageView.ScaleType.FIT_CENTER }, FrameLayout.LayoutParams(dp(100), dp(100)))
                setOnClickListener { openEditorWithBitmap(Samples.render(i, 960)) }
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(10) })
        }
        col.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) })
        return col
    }

    private fun showSourceChooser() {
        AlertDialog.Builder(this).setTitle(L.t("choose_source"))
            .setItems(arrayOf("📷  ${L.t("take_photo")}", "🖼️  ${L.t("upload_gallery")}")) { _, w ->
                importToGalleryMode = false
                if (w == 0) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchTakePhoto()
                    else { openCameraAfterPermission = true; cameraPermLauncher.launch(Manifest.permission.CAMERA) }
                } else pickGalleryLauncher.launch("image/*")
            }.setNegativeButton(L.t("cancel"), null).show()
    }

    private fun buildGalleryTab(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(20)) }
        col.addView(actionBtn("➕  ${L.t("import_photo")}", "#2563EB", 48) { importToGalleryMode = true; pickGalleryLauncher.launch("image/*") })
        val files = (galleryDir.listFiles()?.toList() ?: emptyList()).filter { it.name.endsWith(".png") }.sortedByDescending { it.lastModified() }
        if (files.isEmpty()) {
            col.addView(TextView(this).apply { text = L.t("empty_gallery"); setTextColor(Color.parseColor("#94A3B8")); gravity = Gravity.CENTER; setPadding(0, dp(48), 0, 0) })
            return col
        }
        val cardSize = (resources.displayMetrics.widthPixels - dp(44)) / 2
        var row: LinearLayout? = null
        files.forEachIndexed { idx, file ->
            if (idx % 2 == 0) {
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                col.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })
            }
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
            val savedName = prefs.getString("gname_${file.name}", "Drawing #${files.size - idx}") ?: "Drawing"
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = roundedBg("#161E31", 14); setPadding(dp(8), dp(8), dp(8), dp(8))
                addView(ImageView(this@MainActivity).apply {
                    setImageBitmap(bmp); scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(Color.parseColor("#0B0F19"))
                    setOnClickListener { if (bmp != null) openEditorWithBitmap(bmp) }
                }, LinearLayout.LayoutParams(MATCH_PARENT, cardSize - dp(16)))
                addView(TextView(this@MainActivity).apply { text = savedName; setTextColor(Color.WHITE); textSize = 13f; maxLines = 1; setPadding(0, dp(6), 0, dp(4)) })
                val br = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
                fun mb(t: String, f: () -> Unit) = TextView(this@MainActivity).apply {
                    text = t; textSize = 12f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#CBD5E1"))
                    background = roundedBg("#1E293B", 8); setPadding(0, dp(5), 0, dp(5)); setOnClickListener { f() }
                }
                br.addView(mb("✏️") {
                    val inp = EditText(this@MainActivity).apply { setText(savedName); hint = L.t("rename_hint") }
                    AlertDialog.Builder(this@MainActivity).setTitle(L.t("rename")).setView(inp)
                        .setPositiveButton(L.t("save")) { _, _ ->
                            val nn = inp.text.toString().trim()
                            if (nn.isNotEmpty()) { prefs.edit().putString("gname_${file.name}", nn).apply(); renderMain() }
                        }.setNegativeButton(L.t("cancel"), null).show()
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
                br.addView(mb("🔗") {
                    try {
                        val u = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"; putExtra(Intent.EXTRA_STREAM, u); putExtra(Intent.EXTRA_TEXT, "$savedName — ${L.t("share_text")}"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, L.t("share")))
                    } catch (_: Exception) {}
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
                br.addView(mb("🗑️") {
                    AlertDialog.Builder(this@MainActivity).setMessage(L.t("delete_confirm"))
                        .setPositiveButton(L.t("delete")) { _, _ -> file.delete(); prefs.edit().remove("gname_${file.name}").apply(); renderMain() }
                        .setNegativeButton(L.t("cancel"), null).show()
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(br)
            }
            row?.addView(card, LinearLayout.LayoutParams(cardSize, WRAP_CONTENT).apply { if (idx % 2 == 0) marginEnd = dp(12) })
        }
        return col
    }

    private fun buildStoreTab(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(20)) }
        BillingAdsManager.PACKS.forEach { pack ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = roundedBg(if (pack.isRemoveAds) "#312E81" else "#161E31", 14); setPadding(dp(14), dp(14), dp(14), dp(14))
            }
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            info.addView(TextView(this).apply {
                text = if (pack.isRemoveAds) "🚫  ${L.t("remove_ads")}" else "🪙  ${pack.coins} ${L.t("coins_label")}"
                textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE)
            })
            if (pack.isRemoveAds) info.addView(TextView(this).apply { text = L.t("remove_ads_desc"); textSize = 12f; setTextColor(Color.parseColor("#CBD5E1")) })
            val done = pack.isRemoveAds && billingAds.adsRemoved
            card.addView(info, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            card.addView(Button(this).apply {
                text = if (done) L.t("ads_removed") else pack.priceLabel; setTextColor(Color.WHITE); isAllCaps = false
                background = roundedBg(if (done) "#10B981" else "#4F46E5", 10)
                setOnClickListener { if (!done) billingAds.purchase(this@MainActivity, pack) }
            }, LinearLayout.LayoutParams(WRAP_CONTENT, dp(42)))
            col.addView(card, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })
        }
        return col
    }

    private fun buildSettingsTab(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24)) }
        val cur = L.languages.find { it.code == L.lang }?.label ?: "English"
        col.addView(actionBtn("🌐  ${L.t("language")}: $cur", "#1E293B", 50) {
            AlertDialog.Builder(this).setTitle(L.t("language")).setItems(L.languages.map { it.label }.toTypedArray()) { _, i ->
                L.lang = L.languages[i].code; prefs.edit().putString("lang", L.lang).apply(); renderMain()
            }.show()
        })
        col.addView(actionBtn("🔒  ${L.t("privacy_policy")}", "#1E293B", 50) {
            AlertDialog.Builder(this).setTitle(L.t("privacy_policy")).setMessage(L.t("privacy_text")).setPositiveButton("OK", null).show()
        })
        col.addView(actionBtn("ℹ️  ${L.t("about")}", "#1E293B", 50) { openPlayStore() })
        col.addView(actionBtn("📤  ${L.t("share_friend")}", "#2563EB", 50) {
            val url = "https://play.google.com/store/apps/details?id=$packageName"
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("App Link", url))
            Toast.makeText(this, L.t("link_copied"), Toast.LENGTH_SHORT).show()
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, L.t("share_text")) }, L.t("share_friend")))
        })
        col.addView(actionBtn("⭐  ${L.t("rate_us")}", "#1E293B", 50) { openPlayStore() })
        col.addView(actionBtn("✉️  ${L.t("feedback")}", "#1E293B", 50) {
            try { startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply { putExtra(Intent.EXTRA_SUBJECT, "AR Drawing Feedback") }) } catch (_: Exception) {}
        })
        return col
    }

    private fun openPlayStore() {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))) }
        catch (_: Exception) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))) }
    }

    private fun openEditorWithBitmap(bmp: Bitmap) {
        originalBitmap = bmp; cutoutBitmap = null; bgRemoved = false; brightness = 0; opacityPercent = 70
        showFlow(Flow.EDITOR)
    }

    private fun renderEditor() {
        root.removeAllViews()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(36), dp(18), dp(24)) }
        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(14)) }
        topRow.addView(Button(this).apply {
            text = L.t("back"); setTextColor(Color.WHITE); isAllCaps = false; background = roundedBg("#1E293B", 10); setOnClickListener { showFlow(Flow.MAIN) }
        }, LinearLayout.LayoutParams(WRAP_CONTENT, dp(40)).apply { marginEnd = dp(12) })
        topRow.addView(TextView(this).apply { text = L.t("drawing_create"); textSize = 20f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE) })
        col.addView(topRow)

        val previewIv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; background = roundedBg("#161E31", 16); setPadding(dp(8), dp(8), dp(8), dp(8)) }
        col.addView(previewIv, LinearLayout.LayoutParams(MATCH_PARENT, dp(280)).apply { bottomMargin = dp(16) })

        val bgBtn = Button(this).apply { setTextColor(Color.WHITE); isAllCaps = false; textSize = 15f }
        fun sync() {
            previewIv.setImageBitmap(if (bgRemoved && cutoutBitmap != null) cutoutBitmap else originalBitmap)
            previewIv.alpha = opacityPercent / 100f
            bgBtn.text = when { isProcessingBg -> L.t("removing_bg"); bgRemoved -> "↩️  ${L.t("restore_orig")}"; else -> "✂️  ${L.t("remove_bg")}" }
            bgBtn.background = roundedBg(if (bgRemoved) "#D97706" else "#2563EB", 12)
        }
        sync()
        bgBtn.setOnClickListener {
            if (isProcessingBg) return@setOnClickListener
            val orig = originalBitmap ?: return@setOnClickListener
            if (bgRemoved) { bgRemoved = false; sync() }
            else if (cutoutBitmap != null) { bgRemoved = true; sync() }
            else {
                isProcessingBg = true; sync()
                ImageProcessor.removeBackgroundAsync(orig) { res -> runOnUiThread { isProcessingBg = false; cutoutBitmap = res; bgRemoved = true; sync() } }
            }
        }
        col.addView(bgBtn, LinearLayout.LayoutParams(MATCH_PARENT, dp(50)).apply { bottomMargin = dp(14) })
        col.addView(TextView(this).apply { text = L.t("opacity"); setTextColor(Color.parseColor("#CBD5E1")); textSize = 14f })
        col.addView(SeekBar(this).apply {
            max = 100; progress = opacityPercent
            setOnSeekBarChangeListener(simpleSeek { p -> opacityPercent = p.coerceAtLeast(10); previewIv.alpha = opacityPercent / 100f })
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(18) })
        col.addView(actionBtn("${L.t("next")} →", "#10B981", 54) {
            if (originalBitmap == null) Toast.makeText(this, L.t("need_image"), Toast.LENGTH_SHORT).show()
            else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) showFlow(Flow.CAMERA)
            else { openCameraAfterPermission = false; cameraPermLauncher.launch(Manifest.permission.CAMERA) }
        })
        root.addView(ScrollView(this).apply { addView(col) }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    private fun renderCamera() {
        root.removeAllViews(); torchOn = false
        val baseBmp = (if (bgRemoved && cutoutBitmap != null) cutoutBitmap else originalBitmap) ?: return
        val previewView = PreviewView(this)
        val overlay = OverlayView(this).apply { setBitmap(ImageProcessor.applyBrightness(baseBmp, brightness), true); setOpacity(opacityPercent) }
        overlayView = overlay
        val hintTv = TextView(this).apply {
            text = L.t("hint_place"); setTextColor(Color.WHITE); textSize = 13f; gravity = Gravity.CENTER
            background = roundedBg("#AA000000", 12); setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        cameraHintView = hintTv

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(36), dp(12), dp(8))
            addView(Button(this@MainActivity).apply {
                text = "←"; setTextColor(Color.WHITE); background = roundedBg("#AA111827", 20)
                setOnClickListener { stopCamera(); showFlow(Flow.EDITOR) }
            }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(8) })
            addView(hintTv, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }

        val bp = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = roundedBg("#D90F172A", 18); setPadding(dp(14), dp(10), dp(14), dp(14)) }
        bp.addView(TextView(this).apply { text = L.t("brightness"); setTextColor(Color.WHITE); textSize = 12f })
        bp.addView(SeekBar(this).apply {
            max = 100; progress = brightness + 50
            setOnSeekBarChangeListener(simpleSeek { p -> brightness = p - 50; overlay.setBitmap(ImageProcessor.applyBrightness(baseBmp, brightness), false) })
        })
        bp.addView(TextView(this).apply { text = L.t("opacity"); setTextColor(Color.WHITE); textSize = 12f })
        bp.addView(SeekBar(this).apply {
            max = 100; progress = opacityPercent
            setOnSeekBarChangeListener(simpleSeek { p -> opacityPercent = p.coerceAtLeast(10); overlay.setOpacity(opacityPercent) })
        })

        val br = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        br.addView(Button(this).apply {
            text = "🔦 ${L.t("flash")}"; setTextColor(Color.WHITE); isAllCaps = false; textSize = 13f; background = roundedBg("#334155", 10)
            setOnClickListener { torchOn = !torchOn; boundCamera?.cameraControl?.enableTorch(torchOn); background = roundedBg(if (torchOn) "#F59E0B" else "#334155", 10) }
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        br.addView(Button(this).apply {
            text = "🔒 ${L.t("lock")}"; setTextColor(Color.WHITE); isAllCaps = false; textSize = 13f; background = roundedBg("#2563EB", 10)
            setOnClickListener {
                if (!overlay.isLocked) { tracker.startLock(); overlay.lock(); text = "🔓 ${L.t("unlock")}"; background = roundedBg("#D97706", 10); hintTv.text = L.t("hint_locked") }
                else { tracker.stopLock(); overlay.unlock(); text = "🔒 ${L.t("lock")}"; background = roundedBg("#2563EB", 10); hintTv.text = L.t("hint_place") }
            }
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        br.addView(Button(this).apply {
            text = "${L.t("next")} ✓"; setTextColor(Color.WHITE); isAllCaps = false; textSize = 13f; background = roundedBg("#10B981", 10)
            setOnClickListener { saveToGallery(baseBmp, "Drawing"); stopCamera(); billingAds.showInterstitial(this@MainActivity) { runOnUiThread { showFlow(Flow.CONGRATS) } } }
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        bp.addView(br)

        root.addView(previewView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(topRow, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))
        root.addView(bp, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM).apply { setMargins(dp(12), 0, dp(12), dp(18)) })
        startCamera(previewView)
    }

    private fun renderCongrats() {
        root.removeAllViews()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(28), dp(28), dp(28), dp(28)) }
        col
