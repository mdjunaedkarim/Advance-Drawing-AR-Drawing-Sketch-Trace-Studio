package com.ardrawing.sketch

import android.Manifest
import android.app.AlertDialog
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
import android.widget.FrameLayout
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
            background = UiTabs.roundedBg(this@MainActivity, "#1E293B", 16); setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { currentTab = Tab.STORE; renderMain() }
        }, LinearLayout.LayoutParams(WRAP_CONTENT, dp(36)))
        mainCol.addView(topBar)

        val body = when (currentTab) {
            Tab.HOME -> UiTabs.buildHome(this, { showSourceChooser() }, { openEditorWithBitmap(it) })
            Tab.GALLERY -> UiTabs.buildGallery(this, galleryDir, prefs, { importToGalleryMode = true; pickGalleryLauncher.launch("image/*") }, { openEditorWithBitmap(it) }, { renderMain() })
            Tab.STORE -> UiTabs.buildStore(this, billingAds)
            Tab.SETTINGS -> UiTabs.buildSettings(this, prefs) { renderMain() }
        }
        mainCol.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

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
                background = if (sel) UiTabs.roundedBg(this@MainActivity, "#1E293B", 12) else null
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

    private fun openEditorWithBitmap(bmp: Bitmap) {
        originalBitmap = bmp; cutoutBitmap = null; bgRemoved = false; brightness = 0; opacityPercent = 70
        showFlow(Flow.EDITOR)
    }

    private fun renderEditor() {
        root.removeAllViews()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(36), dp(18), dp(24)) }
        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(14)) }
        topRow.addView(Button(this).apply {
            text = L.t("back"); setTextColor(Color.WHITE); isAllCaps = false; background = UiTabs.roundedBg(this@MainActivity, "#1E293B", 10)
            setOnClickListener { showFlow(Flow.MAIN) }
        }, LinearLayout.LayoutParams(WRAP_CONTENT, dp(40)).apply { marginEnd = dp(12) })
        topRow.addView(TextView(this).apply { text = L.t("drawing_create"); textSize = 20f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE) })
        col.addView(topRow)

        val previewIv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; background = UiTabs.roundedBg(this@MainActivity, "#161E31", 16); setPadding(dp(8), dp(8), dp(8), dp(8)) }
        col.addView(previewIv, LinearLayout.LayoutParams(MATCH_PARENT, dp(280)).apply { bottomMargin = dp(16) })

        val bgBtn = Button(this).apply { setTextColor(Color.WHITE); isAllCaps = false; textSize = 15f }
        fun sync() {
            previewIv.setImageBitmap(if (bgRemoved && cutoutBitmap != null) cutoutBitmap else originalBitmap)
            previewIv.alpha = opacityPercent / 100f
            bgBtn.text = when { isProcessingBg -> L.t("removing_bg"); bgRemoved -> "↩️  ${L.t("restore_orig")}"; else -> "✂️  ${L.t("remove_bg")}" }
            bgBtn.background = UiTabs.roundedBg(this, if (bgRemoved) "#D97706" else "#2563EB", 12)
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
        col.addView(UiTabs.actionBtn(this, "${L.t("next")} →", "#10B981", 54) {
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
            background = UiTabs.roundedBg(this@MainActivity, "#AA000000", 12); setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        cameraHintView = hintTv

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(36), dp(12), dp(8))
            addView(Button(this@MainActivity).apply {
                text = "←"; setTextColor(Color.WHITE); background = UiTabs.roundedBg(this@MainActivity, "#AA111827", 20)
                setOnClickListener { stopCamera(); showFlow(Flow.EDITOR) }
            }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(8) })
            addView(hintTv, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }

        val bp = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = UiTabs.roundedBg(this@MainActivity, "#D90F172A", 18); setPadding(dp(14), dp(10), dp(14), dp(14)) }
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
            text = "🔦 ${L.t("flash")}"; setTextColor(Color.WHITE); isAllCaps = false; textSize = 13f; background = UiTabs.roundedBg(this@MainActivity, "#334155", 10)
            setOnClickListener { torchOn = !torchOn; boundCamera?.cameraControl?.enableTorch(torchOn); background = UiTabs.roundedBg(this@MainActivity, if (torchOn) "#F59E0B" else "#334155", 10) }
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        br.addView(Button(this).apply {
            text = "🔒 ${L.t("lock")}"; setTextColor(Color.WHITE); isAllCaps = false; textSize = 13f; background = UiTabs.roundedBg(this@MainActivity, "#2563EB", 10)
            setOnClickListener {
                if (!overlay.isLocked) { tracker.startLock(); overlay.lock(); text = "🔓 ${L.t("unlock")}"; background = UiTabs.roundedBg(this@MainActivity, "#D97706", 10); hintTv.text = L.t("hint_locked") }
                else { tracker.stopLock(); overlay.unlock(); text = "🔒 ${L.t("lock")}"; background = UiTabs.roundedBg(this@MainActivity, "#2563EB", 10); hintTv.text = L.t("hint_place") }
            }
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        br.addView(Button(this).apply {
            text = "${L.t("next")} ✓"; setTextColor(Color.WHITE); isAllCaps = false; textSize = 13f; background = UiTabs.roundedBg(this@MainActivity, "#10B981", 10)
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
        col.addView(TextView(this).apply { text = L.t("congrats"); textSize = 28f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE); gravity = Gravity.CENTER })
        col.addView(TextView(this).apply { text = L.t("good_luck"); textSize = 16f; setTextColor(Color.parseColor("#CBD5E1")); gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(28)) })
        col.addView(UiTabs.actionBtn(this, L.t("go_home"), "#4F46E5", 52) { showFlow(Flow.MAIN) })
        root.addView(col, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    private fun startCamera(previewView: PreviewView) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get(); cameraProvider = provider
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(analysisExecutor) { image ->
                    try {
                        val out = tracker.process(image); val ov = overlayView
                        if (ov != null && ov.isLocked) runOnUiThread { ov.updateTracking(out); cameraHintView?.text = if (out.good) L.t("hint_locked") else L.t("hint_lost") }
                    } finally { image.close() }
                }
                provider.unbindAll()
                boundCamera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (_: Exception) { Toast.makeText(this, L.t("camera_error"), Toast.LENGTH_LONG).show() }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        try { boundCamera?.cameraControl?.enableTorch(false) } catch (_: Exception) {}
        boundCamera = null; torchOn = false; tracker.stopLock(); cameraProvider?.unbindAll(); overlayView = null; cameraHintView = null
    }

    private fun launchTakePhoto() {
        val file = File(cacheDir, "cam_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        pendingCameraUri = uri; takePhotoLauncher.launch(uri)
    }

    private fun handlePickedUri(uri: Uri) {
        try {
            val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val maxS = maxOf(info.size.width, info.size.height)
                if (maxS > 1080) { val s = 1080f / maxS; d.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1)) }
            }
            val clean = if (bmp.config == Bitmap.Config.ARGB_8888) bmp else bmp.copy(Bitmap.Config.ARGB_8888, false)
            if (importToGalleryMode) { saveToGallery(clean, "Imported"); renderMain() } else openEditorWithBitmap(clean)
        } catch (_: Exception) { Toast.makeText(this, L.t("image_failed"), Toast.LENGTH_LONG).show() }
    }

    private fun saveToGallery(bmp: Bitmap?, prefix: String) {
        if (bmp == null) return
        try {
            val s = 512f / maxOf(bmp.width, bmp.height).coerceAtLeast(1)
            val thumb = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), true)
            val name = "art_${System.currentTimeMillis()}.png"
            FileOutputStream(File(galleryDir, name)).use { thumb.compress(Bitmap.CompressFormat.PNG, 92, it) }
            prefs.edit().putString("gname_$name", "$prefix #${galleryDir.listFiles()?.size ?: 1}").apply()
        } catch (_: Exception) {}
    }

    private fun simpleSeek(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) onChange(p) }
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) {}
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() { super.onDestroy(); stopCamera(); billingAds.destroy(); if (::analysisExecutor.isInitialized) analysisExecutor.shutdown() }
}
